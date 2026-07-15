<template>
  <div class="app-root">
    <SearchBar @select="onSearchSelect" />
    <MapContainer
      @map-ready="onMapReady"
      @spot-click="onSpotClick"
    />
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
import { ref, computed, provide } from 'vue'
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

const { map, initMap, getAMap } = useAmap()
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
    m.on('click', (e) => {
      if (!spotsStore.isCreating.value) return
      const coord = spotsStore.placeCreateMarker(e.lnglat)
      createCoord.value = coord
      // 逆地理编码
      const geocoder = new (getAMap()).Geocoder()
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
        spotsStore.isCreating.value = true
        isCreating.value = true
        currentSpot.value = null
        panelVisible.value = true
      })
    })
    m.on('rightclick', () => {
      showToast('点击地图放置标记以创建点位')
      spotsStore.startCreateMode()
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
    photosStore.photosBySpot.value = { ...photosStore.photosBySpot.value, [spot.id]: [] }
    currentSpot.value = spot
    isCreating.value = false
    showToast('点位创建成功', 'success')
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
</script>

<style>
/* 全局基础样式 */
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { width: 100%; height: 100%; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; overflow: hidden; }
.app-root { width: 100%; height: 100%; position: relative; }
</style>
