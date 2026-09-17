<template>
  <div class="app-root" :class="{ mobile: isMobile }">
    <SearchBar @select="onSearchSelect" />
    <MapContainer
      @map-ready="onMapReady"
      @spot-click="onSpotClick"
    />
    <!-- 浮动创建按钮 + 菜单（PC右上 / 手机右下） -->
    <div class="fab-wrapper" :class="{ open: fabOpen }">
      <button class="fab-add" @click="fabOpen = !fabOpen" title="添加">
        <span>{{ fabOpen ? '✕' : '+' }}</span>
      </button>
      <div class="fab-menu">
        <button class="fab-menu-item" @click="onFabAddSpot">📌 添加点位</button>
        <button class="fab-menu-item" @click="onFabUploadPhoto">📷 上传图片</button>
        <button class="fab-menu-item" @click="onFabTakePhoto" v-if="hasCapacitor">🤳 拍照</button>
      </div>
    </div>
    <!-- 上传图片的隐藏 input -->
    <input type="file" id="exifFileInput" accept="image/*" multiple style="display:none" @change="onExifFilesSelected">

    <!-- 点位面板 -->
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
      @preview-photo="onPreviewPhoto"
    />

    <!-- 光箱（支持连续翻页） -->
    <PhotoLightbox
      :photos="lightboxPhotos"
      v-model:index="lightboxIndex"
      :visible="lightboxVisible"
      @close="lightboxVisible = false"
    />
    <ToastMessage />
    <UserLogin v-if="needLogin" @logged-in="onLoggedIn" />
  </div>
</template>

<script setup>
import { ref, computed, provide, onMounted, onBeforeUnmount } from 'vue'
import exifr from 'exifr'
import { uploadPhoto, hasToken, setNeedLoginListener } from './api/index.js'
import { wgs84ToGcj02 } from './utils/coord.js'
import { hasCapacitor, takePhoto } from './utils/capacitor.js'
import SearchBar from './components/SearchBar.vue'
import MapContainer from './components/MapContainer.vue'
import SpotPanel from './components/SpotPanel.vue'
import PhotoLightbox from './components/PhotoLightbox.vue'
import ToastMessage from './components/ToastMessage.vue'
import UserLogin from './components/UserLogin.vue'
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

// ===== 登录门（APK 原生端必须登录/注册；网页 H5 测试免登录直通） =====
const needLogin = ref(hasCapacitor() && !hasToken())
setNeedLoginListener(() => { needLogin.value = true })

async function onLoggedIn() {
  needLogin.value = false
  try {
    await spotsStore.loadSpots()
    await photosStore.loadAllPhotos()
    spotsStore.renderAllMarkers()
    console.log('[App] 登录后数据加载完成')
  } catch (e) {
    console.error('[App] 登录后加载数据失败:', e)
    showToast('加载数据失败: ' + e.message, 'error')
  }
}

// ===== 移动端检测 =====
const isMobile = ref(false)
function detectMobile() {
  isMobile.value = window.innerWidth <= 768
}
onMounted(() => { detectMobile(); window.addEventListener('resize', detectMobile) })
onBeforeUnmount(() => window.removeEventListener('resize', detectMobile))

// ===== 面板状态 =====
const panelVisible = ref(false)
const isCreating = ref(false)
const createCoord = ref({ lng: 0, lat: 0 })
const currentSpot = ref(null)
const autoEdit = ref(false)

// ===== 光箱状态 =====
const lightboxVisible = ref(false)
const lightboxPhotos = ref([])   // [{ url, thumbUrl }, ...]
const lightboxIndex = ref(0)

// ===== 当前点位照片 =====
const currentPhotos = computed(() => {
  if (!currentSpot.value) return []
  return photosStore.photosBySpot.value[currentSpot.value.id] || []
})

// ===== Provide =====
provide('map', map)
provide('getAMap', getAMap)
provide('showToast', showToast)
provide('toastState', toastState)
provide('spotsStore', spotsStore)
provide('photosStore', photosStore)
// 新的光箱方式：通过 provide 让 PhotoGrid 等子组件打开光箱
provide('openLightbox', (photos, idx) => {
  lightboxPhotos.value = photos || []
  lightboxIndex.value = idx || 0
  lightboxVisible.value = true
})

// ===== 地图就绪 =====
async function onMapReady(containerId) {
  try {
    const m = await initMap(containerId)
    await spotsStore.bind(m, getAMap(), () => photosStore.latestPhotoMap.value, () => photosStore.photosBySpot.value)

    // --- 地图点击：创建模式下放置标记 ---
    m.on('click', async (e) => {
      if (!spotsStore.isCreating.value) return
      const coord = spotsStore.placeCreateMarker(e.lnglat)
      createCoord.value = {
        lng: coord.lng, lat: coord.lat,
        address: '', province: '', city: '', district: ''
      }
      currentSpot.value = null
      isCreating.value = true
      autoEdit.value = false
      panelVisible.value = true

      // 后台逆地理编码
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
      } catch (err) { console.warn('[App] 逆地理编码失败:', err) }
    })

    // --- 右键（PC）/ 长按（移动端）→ 进入创建模式 ---
    m.on('rightclick', () => startCreateMode())

    // 移动端：模拟长按（touchstart + 1s）
    if (isMobile.value) {
      let longPressTimer = null
      const mapContainer = document.getElementById('amap-container')
      if (mapContainer) {
        mapContainer.addEventListener('touchstart', (e) => {
          // 多指不触发
          if (e.touches.length > 1) return
          longPressTimer = setTimeout(() => {
            // 取地图中心附近（长按无法获取精确坐标，提示用户点击放置）
            startCreateMode()
          }, 800)
        }, { passive: true })
        mapContainer.addEventListener('touchend', () => clearTimeout(longPressTimer))
        mapContainer.addEventListener('touchmove', () => clearTimeout(longPressTimer))
      }
    }

    // --- 加载数据 ---
    try {
      await spotsStore.loadSpots()
      await photosStore.loadAllPhotos()
      spotsStore.renderAllMarkers()
      console.log('[App] 数据加载完成，共 ' + spotsStore.spots.value.length + ' 个点位')
    } catch (e) {
      console.error('[App] 加载数据失败:', e)
      if (e && e.needLogin) return // 原生端未登录：等待登录门，登录后重载
      showToast('加载数据失败: ' + e.message, 'error')
    }
  } catch (e) {
    console.error('[App] 地图初始化失败:', e)
    showToast('地图初始化失败: ' + e.message, 'error')
  }
}

function startCreateMode() {
  if (panelVisible.value) { panelVisible.value = false }
  currentSpot.value = null
  isCreating.value = false
  spotsStore.cancelCreateMode()
  spotsStore.startCreateMode()
  setTimeout(() => showToast('点击地图放置标记以创建点位'), 50)
}

// ===== 搜索 =====
function onSearchSelect({ lng, lat, name }) {
  if (map.value) {
    map.value.setCenter([lng, lat])
    map.value.setZoom(16)
  }
  showToast('已定位到: ' + name)
}
// ===== FAB =====
const fabOpen = ref(false)

function onFabAddSpot() {
  fabOpen.value = false
  startCreateMode()
}

function onFabUploadPhoto() {
  fabOpen.value = false
  document.getElementById('exifFileInput').click()
}

async function onFabTakePhoto() {
  fabOpen.value = false
  try {
    const file = await takePhoto()
    // 复用 EXIF 解析流程
    await processExifPhoto(file)
  } catch (e) {
    if (e.message !== '取消拍照') {
      showToast('拍照失败: ' + e.message, 'error')
    }
  }
}

// 全局点击关闭 FAB
document.addEventListener('click', (e) => {
  if (!e.target.closest('.fab-wrapper')) fabOpen.value = false
})

// ===== EXIF 处理 =====
let _pendingFilesNoGps = []

async function onExifFilesSelected(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (files.length === 0) return
  showToast('正在解析照片位置信息…')

  for (const file of files) {
    await processExifPhoto(file)
  }

  // 无 GPS 文件的处理
  if (_pendingFilesNoGps.length > 0) {
    startCreateMode()
    showToast(`${_pendingFilesNoGps.length} 张照片无位置信息，请点击地图放置点位`, 'info')
  }
}

async function processExifPhoto(file) {
  try {
    const gps = await exifr.parse(file, { gps: true })
    if (gps && gps.latitude != null && gps.longitude != null) {
      const gcj = wgs84ToGcj02({ lng: gps.longitude, lat: gps.latitude })
      await uploadWithGps(file, gcj.lng, gcj.lat)
    } else {
      _pendingFilesNoGps.push(file)
    }
  } catch {
    _pendingFilesNoGps.push(file)
  }
}

async function uploadWithGps(file, lng, lat) {
  // 查找 50m 内的已有点位
  const nearby = spotsStore.spots.value.find(s =>
    Math.abs(s.lng - lng) < 0.0005 && Math.abs(s.lat - lat) < 0.0005
  )

  let spotId
  if (nearby) {
    spotId = nearby.id
  } else {
    let address = '', province = '', city = '', district = ''
    try {
      const geocoder = await createGeocoder()
      const addrResult = await new Promise((resolve) => {
        geocoder.getAddress([lng, lat], (status, result) => {
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
        name: '相机拍摄 ' + lat.toFixed(4) + ', ' + lng.toFixed(4),
        lat, lng, address, province, city, district,
        category: 'scenic', userId: 2
      })
      spotId = newSpot.id
      photosStore.photosBySpot.value = { ...photosStore.photosBySpot.value, [spotId]: [] }
    } catch (ex) {
      console.error('[exif] spot create failed:', ex)
      return
    }
  }

  try {
    await uploadPhoto(spotId, file, '')
  } catch (ex) {
    console.error('[exif] upload failed:', file.name, ex)
  }

  // 刷新
  await photosStore.loadAllPhotos()
  spotsStore.renderAllMarkers()

  // 跳转到操作点位
  const spot = spotsStore.spots.value.find(s => s.id === spotId)
  if (spot) {
    map.value?.setCenter([spot.lng, spot.lat])
    map.value?.setZoom(16)
    autoEdit.value = true
    currentSpot.value = spot
    isCreating.value = false
    spotsStore.isCreating.value = false
    spotsStore.cancelCreateMode()
    panelVisible.value = true
    await photosStore.loadPhotos(spotId)
    showToast(`照片已上传，请编辑点位信息`, 'success')
  }
}

// ===== 点位交互 =====
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

    if (_pendingFilesNoGps.length > 0) {
      let up = 0
      for (const file of _pendingFilesNoGps) {
        try { await uploadPhoto(spot.id, file, ''); up++ }
        catch (ex) { console.error('[exif] pending upload failed:', file.name, ex) }
      }
      _pendingFilesNoGps = []
      await photosStore.loadPhotos(spot.id)
      await photosStore.loadAllPhotos()
      spotsStore.renderAllMarkers()
      currentSpot.value = { ...spot, photoCount: up }
      showToast(`点位创建成功，已上传 ${up} 张照片`, 'success')
    } else {
      showToast('点位创建成功', 'success')
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

// 点击照片网格中的某张 → 打开光箱（当前点位照片列表）
function onPreviewPhoto(photo) {
  const list = currentPhotos.value
  const idx = list.findIndex(p => p.id === photo.id)
  lightboxPhotos.value = list
  lightboxIndex.value = idx >= 0 ? idx : 0
  lightboxVisible.value = true
}

// ===== 键盘快捷键 =====
function onKeyDown(e) {
  if (e.key !== 'Escape') return
  if (lightboxVisible.value) {
    lightboxVisible.value = false
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
/* ===== 全局基础 ===== */
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app {
  width: 100%; height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  overflow: hidden;
  /* 防止 iOS 橡皮筋效果导致误关闭 */
  position: fixed;
  overscroll-behavior: none;
  -webkit-overflow-scrolling: touch;
}
.app-root { width: 100%; height: 100%; position: relative; }

/* ===== FAB ===== */
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
  -webkit-tap-highlight-color: transparent;
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
  opacity: 1; transform: translateY(0); pointer-events: auto;
}
.fab-menu-item {
  display: block; width: 100%; padding: 14px 18px;
  border: none; background: #fff; cursor: pointer;
  font-size: 14px; text-align: left; white-space: nowrap;
  transition: background 0.15s;
  -webkit-tap-highlight-color: transparent;
  min-height: 48px;
}
.fab-menu-item:hover { background: #f5f8fc; }
.fab-menu-item + .fab-menu-item { border-top: 1px solid #f0f0f0; }

/* ===== 移动端适配 ===== */
@media (max-width: 768px) {
  .fab-wrapper {
    top: auto; bottom: max(24px, env(safe-area-inset-bottom));
    right: 20px;
  }
  .fab-add {
    width: 56px; height: 56px; font-size: 30px;
    box-shadow: 0 4px 20px rgba(74,144,217,0.5);
  }
  .fab-menu {
    top: auto; bottom: 64px;
  }
}
</style>
