<template>
  <div class="app-root">
    <SearchBar @select="onSearchSelect" />
    <MapContainer
      @map-ready="onMapReady"
      @spot-click="onSpotClick"
    />
    <!-- 浮动创建按钮 -->
    <button class="fab-add" @click="startCreateSpot" title="添加点位">
      <span>+</span>
    </button>
    <SpotPanel
      v-if="panelVisible"
      :spot="currentSpot"
      :photos="currentPhotos"
      :is-creating="isCreating"
      :initial-coord="createCoord"
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
    spotsStore.bind(m, getAMap(), () => photosStore.latestPhotoMap.value)
    m.on('click', async (e) => {
      if (!spotsStore.isCreating.value) return
      // 1. 放置标记
      const coord = spotsStore.placeCreateMarker(e.lnglat)

      // 2. 立即打开创建面板（不等 geocoder）
      createCoord.value = { lng: coord.lng, lat: coord.lat, address: '', province: '', city: '', district: '' }
      currentSpot.value = null
      isCreating.value = true
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

// 浮动按钮 → 进入创建模式
function startCreateSpot() {
  if (panelVisible.value) panelVisible.value = false
  currentSpot.value = null
  isCreating.value = false
  spotsStore.cancelCreateMode()
  spotsStore.startCreateMode()
  showToast('点击地图放置点位标记')
}

// 点位点击 → 打开面板
function onSpotClick(spot) {
  currentSpot.value = spot
  isCreating.value = false
  spotsStore.isCreating.value = false
  spotsStore.cancelCreateMode()
  panelVisible.value = true
  photosStore.loadPhotos(spot.id)
}

function closePanel() {
  panelVisible.value = false
  currentSpot.value = null
  isCreating.value = false
  spotsStore.cancelCreateMode()
}

async function onSpotCreated(spotData) {
  try {
    const spot = await spotsStore.create({ ...spotData, userId: 2 })
    // 清理创建模式的 marker 和状态
    spotsStore.cancelCreateMode()
    photosStore.photosBySpot.value = { ...photosStore.photosBySpot.value, [spot.id]: [] }
    currentSpot.value = spot
    isCreating.value = false
    showToast('点位创建成功', 'success')
    console.log('[App] 点位创建完成 id=' + spot.id + ' name=' + spot.name)
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

.fab-add {
  position: fixed; top: 64px; right: 20px; z-index: 150;
  width: 48px; height: 48px; border-radius: 50%;
  background: #4a90d9; color: #fff;
  border: none; cursor: pointer; font-size: 26px;
  box-shadow: 0 4px 16px rgba(74,144,217,0.4);
  display: flex; align-items: center; justify-content: center;
  transition: transform 0.2s, box-shadow 0.2s;
  user-select: none;
}
.fab-add:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 20px rgba(74,144,217,0.55);
}
.fab-add:active { transform: scale(0.95); }
</style>
