<template>
  <div class="app-root">
    <SearchBar @select="onSearchSelect" />
    <MapContainer
      @map-ready="onMapReady"
      @spot-click="onSpotClick"
    />
    <!-- 浮动创建按钮 + 菜单 -->
    <div class="fab-wrapper" :class="{ open: fabOpen }">
      <button class="fab-add" @click="fabOpen = !fabOpen" title="添加">
        <span>{{ fabOpen ? '✕' : '+' }}</span>
      </button>
      <div class="fab-menu">
        <button class="fab-menu-item" @click="onFabAddSpot">📌 添加点位</button>
        <button class="fab-menu-item" @click="onFabUploadPhoto">📷 上传图片</button>
      </div>
    </div>
    <!-- 上传图片的隐藏 input -->
    <input type="file" id="exifFileInput" accept="image/*" multiple style="display:none" @change="onExifFilesSelected">
    <SpotPanel
      v-if="panelVisible"
      :spot="currentSpot"
      :photos="currentPhotos"
      :is-creating="isCreating"
      :initial-coord="createCoord"
      :auto-edit="autoEdit"
      @close="closePanel"
      @created="onSpotCreated"
      @updated="onSpotUpdated"
      @deleted="onSpotDeleted"
      @upload="onPhotoUpload"
      @delete-photo="onPhotoDelete"
    />
    <PhotoLightbox
      v-if="lightboxSrc"
      :src="lightboxSrc"
      @close="lightboxSrc = null"
    />
    <ToastMessage />
  </div>
</template>

<script setup>
import { ref, computed, provide, onMounted, onBeforeUnmount } from 'vue'
import exifr from 'exifr'
import { uploadPhoto } from './api/index.js'
import { wgs84ToGcj02 } from './utils/coord.js'
import SearchBar from './components/SearchBar.vue'
import MapContainer from './components/MapContainer.vue'
import SpotPanel from './components/SpotPanel.vue'
import PhotoLightbox from './components/PhotoLightbox.vue'
import ToastMessage from './components/ToastMessage.vue'
import { useAmap } from './composables/useAmap.js'
import { useSpots } from './composables/useSpots.js'
import { usePhotos } from './composables/usePhotos.js'
import { useToast } from './composables/useToast.js'
import { injectMarkerStyles } from './utils/marker.js'

// 初始化
injectMarkerStyles()

const { map, initMap, getAMap, createGeocoder } = useAmap()
const spotsStore = useSpots()
const photosStore = usePhotos()
const { toastState, showToast } = useToast()

// 面板状态
const panelVisible = ref(false)
const isCreating = ref(false)
const createCoord = ref({ lng: 0, lat: 0 })
const currentSpot = ref(null)
const lightboxSrc = ref(null)

// 当前点位的照片
const currentPhotos = computed(() => {
  if (!currentSpot.value) return []
  return photosStore.photosBySpot.value[currentSpot.value.id] || []
})

// Provide 给子组件
provide('map', map)
provide('getAMap', getAMap)
provide('showToast', showToast)
provide('toastState', toastState)
provide('spotsStore', spotsStore)
provide('photosStore', photosStore)
provide('lightboxSrc', lightboxSrc)
provide('openLightbox', (src) => { lightboxSrc.value = src })

// 地图就绪
async function onMapReady(containerId) {
  try {
    const m = await initMap(containerId)
    await spotsStore.bind(m, getAMap(), () => photosStore.latestPhotoMap.value)
    m.on('click', async (e) => {
      if (!spotsStore.isCreating.value) return
      // 1. 放置标记
      const coord = spotsStore.placeCreateMarker(e.lnglat)

      // 2. 立即打开创建面板（不等 geocoder）
      createCoord.value = { lng: coord.lng, lat: coord.lat, address: '', province: '', city: '', district: '' }
      currentSpot.value = null
      isCreating.value = true
      autoEdit.value = false
      panelVisible.value = true

      // 3. 后台逆地理编码，回填地址
      try {
        const geocoder = await createGeocoder()
        geocoder.getAddress([coord.lng, coord.lat], (status, result) => {
          if (status === 'complete' && result.regeocode) {
            createCoord.value = {
              lng: coord.lng, lat: coord.lat,
              address: result.regeocode.formattedAddress || '',
              province: (result.regeocode.addressComponent || {}).province || '',
              city: (result.regeocode.addressComponent || {}).city || '',
              district: (result.regeocode.addressComponent || {}).district || ''
            }
          }
        })
      } catch (err) {
        console.warn('[App] 逆地理编码失败:', err)
      }
    })
    m.on('rightclick', () => {
      // 先关闭当前面板（如果开着），重置所有状态
      if (panelVisible.value) {
        panelVisible.value = false
      }
      currentSpot.value = null
      isCreating.value = false
      spotsStore.cancelCreateMode()
      // 再进入创建模式
      spotsStore.startCreateMode()
      // 延迟关闭面板确保 Vue 响应式更新
      setTimeout(() => {
        showToast('点击地图放置标记以创建点位')
      }, 50)
    })

    // 加载数据
    try {
      await spotsStore.loadSpots()
      await photosStore.loadAllPhotos()
      spotsStore.renderAllMarkers()
      console.log('[App] 数据加载完成，共 ' + spotsStore.spots.value.length + ' 个点位')
    } catch (e) {
      console.error('[App] 加载数据失败:', e)
      showToast('加载数据失败: ' + e.message, 'error')
    }
  } catch (e) {
    console.error('[App] 地图初始化失败:', e)
    showToast('地图初始化失败: ' + e.message, 'error')
  }
}

// 搜索选择 → 地图定位
function onSearchSelect({ lng, lat, name }) {
  if (map.value) {
    map.value.setCenter([lng, lat])
    map.value.setZoom(16)
  }
  showToast('已定位到: ' + name)
}

const fabOpen = ref(false)
const autoEdit = ref(false)

// FAB 菜单：添加点位
function onFabAddSpot() {
  fabOpen.value = false
  if (panelVisible.value) panelVisible.value = false
  currentSpot.value = null
  isCreating.value = false
  spotsStore.cancelCreateMode()
  spotsStore.startCreateMode()
  showToast('点击地图放置点位标记')
}

// FAB 菜单：上传图片（含 EXIF 解析）
function onFabUploadPhoto() {
  fabOpen.value = false
  document.getElementById('exifFileInput').click()
}

// 全局点击关闭 FAB 菜单
document.addEventListener('click', (e) => {
  if (!e.target.closest('.fab-wrapper')) fabOpen.value = false
})

// 待上传文件缓存（无 EXIF 的文件等待用户点击地图后上传）
let _pendingFilesNoGps = []

// EXIF 文件选择处理
async function onExifFilesSelected(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (files.length === 0) return

  showToast('正在解析照片位置信息…')

  // 1. 逐个解析 EXIF GPS
  const withGps = []    // { file, lng, lat }
  const withoutGps = []

  for (const file of files) {
    try {
      const gps = await exifr.parse(file, { gps: true })
      if (gps && gps.latitude != null && gps.longitude != null) {
        // EXIF GPS 是 WGS84，转 GCJ-02 对齐高德底图
        const gcj = wgs84ToGcj02({ lng: gps.longitude, lat: gps.latitude })
        withGps.push({ file, lng: gcj.lng, lat: gcj.lat })
      } else {
        withoutGps.push(file)
      }
    } catch {
      withoutGps.push(file)
    }
  }

  // 2. 有 GPS 的文件：分组（同坐标 ±0.0003° ≈ 30m 内合并），创建/匹配点位并上传
  let uploadedCount = 0
  const processedLocs = []  // 避免重复创建
  let lastSpotId = null      // 最后操作的点位，用于跳转编辑
  let lastSpotIsNew = false  // 是否新创建

  for (const item of withGps) {
    const roundLng = Math.round(item.lng * 10000) / 10000
    const roundLat = Math.round(item.lat * 10000) / 10000

    if (processedLocs.some(p => p.lng === roundLng && p.lat === roundLat)) continue
    processedLocs.push({ lng: roundLng, lat: roundLat })

    // 查找 50m 范围内的已有点位
    const nearby = spotsStore.spots.value.find(s =>
      Math.abs(s.lng - item.lng) < 0.0005 && Math.abs(s.lat - item.lat) < 0.0005
    )

    let spotId
    if (nearby) {
      spotId = nearby.id
      lastSpotIsNew = false
    } else {
      // 自动创建点位
      let address = '', province = '', city = '', district = ''
      try {
        const geocoder = await createGeocoder()
        const addrResult = await new Promise((resolve) => {
          geocoder.getAddress([item.lng, item.lat], (status, result) => {
            resolve(status === 'complete' && result.regeocode ? result.regeocode : null)
          })
        })
        if (addrResult) {
          address = addrResult.formattedAddress || ''
          province = (addrResult.addressComponent || {}).province || ''
          city = (addrResult.addressComponent || {}).city || ''
          district = (addrResult.addressComponent || {}).district || ''
        }
      } catch (ex) { /* ignore */ }

      try {
        const newSpot = await spotsStore.create({
          name: '相机拍摄 ' + item.lat.toFixed(4) + ', ' + item.lng.toFixed(4),
          lat: item.lat, lng: item.lng,
          address, province, city, district,
          category: 'scenic',
          userId: 2
        })
        spotId = newSpot.id
        lastSpotIsNew = true
        photosStore.photosBySpot.value = { ...photosStore.photosBySpot.value, [spotId]: [] }
      } catch (ex) {
        console.error('[exif] spot create failed:', ex)
        continue
      }
    }

    lastSpotId = spotId

    // 上传该坐标下的所有文件
    const groupFiles = withGps.filter(f =>
      Math.abs(f.lng - item.lng) < 0.0003 && Math.abs(f.lat - item.lat) < 0.0003
    )
    for (const gf of groupFiles) {
      try {
        await uploadPhoto(spotId, gf.file, '')
        uploadedCount++
      } catch (ex) {
        console.error('[exif] upload failed:', gf.file.name, ex)
      }
    }
  }

  // 3. 刷新数据
  if (uploadedCount > 0) {
    await photosStore.loadAllPhotos()
    spotsStore.renderAllMarkers()
  }

  // 4. 跳转到最后一个点位的位置，弹出编辑面板
  if (lastSpotId && uploadedCount > 0) {
    const lastSpot = spotsStore.spots.value.find(s => s.id === lastSpotId)
    if (lastSpot) {
      // 地图跳转
      map.value?.setCenter([lastSpot.lng, lastSpot.lat])
      map.value?.setZoom(16)

      // 弹出编辑面板（自动进入编辑模式）
      autoEdit.value = true
      currentSpot.value = lastSpot
      isCreating.value = false
      spotsStore.isCreating.value = false
      spotsStore.cancelCreateMode()
      panelVisible.value = true

      // 加载新点位照片
      await photosStore.loadPhotos(lastSpotId)

      showToast(`已上传 ${uploadedCount} 张照片，请编辑点位信息`, 'success')
    }
  }

  // 5. 无 GPS 的文件 → 进入创建模式手动放置
  if (withoutGps.length > 0) {
    _pendingFilesNoGps = withoutGps
    fabOpen.value = false
    if (panelVisible.value) panelVisible.value = false
    currentSpot.value = null
    isCreating.value = false
    spotsStore.cancelCreateMode()
    spotsStore.startCreateMode()
    showToast(`${withoutGps.length} 张照片无位置信息，请点击地图放置点位`, 'info')
  }

  if (uploadedCount === 0 && withoutGps.length === 0) {
    showToast('未找到可用的照片', 'error')
  }
}



// 点位点击 → 打开面板
function onSpotClick(spot) {
  currentSpot.value = spot
  isCreating.value = false
  spotsStore.isCreating.value = false
  spotsStore.cancelCreateMode()
  autoEdit.value = false
  panelVisible.value = true
  photosStore.loadPhotos(spot.id)
}

function closePanel() {
  _pendingFilesNoGps = []
  autoEdit.value = false
  panelVisible.value = false
  currentSpot.value = null
  isCreating.value = false
  spotsStore.cancelCreateMode()
}

async function onSpotCreated(spotData) {
  try {
    const spot = await spotsStore.create({ ...spotData, userId: 2 })
    spotsStore.cancelCreateMode()
    photosStore.photosBySpot.value = { ...photosStore.photosBySpot.value, [spot.id]: [] }
    currentSpot.value = spot
    isCreating.value = false

    // 如果有待上传的无 GPS 照片，自动上传到新点位
    if (_pendingFilesNoGps.length > 0) {
      let up = 0
      for (const file of _pendingFilesNoGps) {
        try {
          await uploadPhoto(spot.id, file, '')
          up++
        } catch (ex) { console.error('[exif] pending upload failed:', file.name, ex) }
      }
      _pendingFilesNoGps = []
      await photosStore.loadPhotos(spot.id)
      await photosStore.loadAllPhotos()
      spotsStore.renderAllMarkers()
      currentSpot.value = { ...spot, photoCount: up }
      showToast(`点位创建成功，已上传 ${up} 张照片`, 'success')
      console.log('[App] 点位创建完成(含照片) id=' + spot.id + ' photos=' + up)
    } else {
      showToast('点位创建成功', 'success')
      console.log('[App] 点位创建完成 id=' + spot.id + ' name=' + spot.name)
    }
  } catch (e) {
    showToast('创建失败: ' + e.message, 'error')
  }
}

async function onSpotUpdated(spotData) {
  try {
    const updated = await spotsStore.update(spotData)
    currentSpot.value = updated
    showToast('更新成功', 'success')
  } catch (e) {
    showToast('更新失败: ' + e.message, 'error')
  }
}

async function onSpotDeleted(id) {
  try {
    await spotsStore.remove(id)
    panelVisible.value = false
    currentSpot.value = null
    showToast('点位已删除', 'success')
  } catch (e) {
    showToast('删除失败: ' + e.message, 'error')
  }
}

async function onPhotoUpload(files, spotId) {
  try {
    const ok = await photosStore.upload(files, spotId)
    showToast(`成功上传 ${ok.length} 张照片`, 'success')
    spotsStore.updateMarker(currentSpot.value)
    // 更新 currentSpot 的 photoCount
    if (currentSpot.value) {
      currentSpot.value = {
        ...currentSpot.value,
        photoCount: (currentSpot.value.photoCount || 0) + ok.length
      }
    }
  } catch (e) {
    showToast('上传失败: ' + e.message, 'error')
  }
}

async function onPhotoDelete(photoId) {
  try {
    const spotId = currentSpot.value?.id
    await photosStore.remove(photoId, spotId)
    if (currentSpot.value) {
      currentSpot.value = {
        ...currentSpot.value,
        photoCount: Math.max(0, (currentSpot.value.photoCount || 1) - 1)
      }
    }
    spotsStore.updateMarker(currentSpot.value)
    showToast('照片已删除', 'success')
  } catch (e) {
    showToast('删除失败: ' + e.message, 'error')
  }
}

// Esc 退出创建模式 / 关闭面板 / 关闭灯箱
function onKeyDown(e) {
  if (e.key !== 'Escape') return
  if (lightboxSrc.value) {
    lightboxSrc.value = null
    e.preventDefault()
    return
  }
  if (panelVisible.value) {
    closePanel()
    e.preventDefault()
    return
  }
  if (spotsStore.isCreating.value) {
    spotsStore.cancelCreateMode()
    showToast('已退出创建模式')
    e.preventDefault()
  }
}

onMounted(() => document.addEventListener('keydown', onKeyDown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeyDown))
</script>

<style>
/* 全局基础样式 */
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { width: 100%; height: 100%; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; overflow: hidden; }
.app-root { width: 100%; height: 100%; position: relative; }

.fab-wrapper {
  position: fixed; top: 64px; right: 20px; z-index: 150;
}
.fab-add {
  width: 48px; height: 48px; border-radius: 50%;
  background: #4a90d9; color: #fff;
  border: none; cursor: pointer; font-size: 26px;
  box-shadow: 0 4px 16px rgba(74,144,217,0.4);
  display: flex; align-items: center; justify-content: center;
  transition: transform 0.2s, box-shadow 0.2s;
  user-select: none;
}
.fab-add:hover { transform: scale(1.1); box-shadow: 0 6px 20px rgba(74,144,217,0.55); }
.fab-add:active { transform: scale(0.95); }

.fab-menu {
  position: absolute; top: 56px; right: 0;
  background: #fff; border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.15);
  overflow: hidden;
  opacity: 0; transform: translateY(-8px);
  pointer-events: none;
  transition: opacity 0.2s, transform 0.2s;
  min-width: 160px;
}
.fab-wrapper.open .fab-menu {
  opacity: 1; transform: translateY(0);
  pointer-events: auto;
}

.fab-menu-item {
  display: block; width: 100%; padding: 12px 18px;
  border: none; background: #fff; cursor: pointer;
  font-size: 14px; text-align: left; white-space: nowrap;
  transition: background 0.15s;
}
.fab-menu-item:hover { background: #f5f8fc; }
.fab-menu-item + .fab-menu-item { border-top: 1px solid #f0f0f0; }
</style>
