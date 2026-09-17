<template>
  <div class="app-root" :class="{ mobile: isMobile }">
    <SearchBar @select="onSearchSelect" />
    <MapContainer
      @map-ready="onMapReady"
      @spot-click="onSpotClick"
    />

    <!-- 底部中间蓝色加号：上传入口 -->
    <button class="upload-fab" @click.stop="sheetOpen = true" aria-label="上传照片">＋</button>

    <!-- 上传方式选择：拍照 / 从相册选择 -->
    <Transition name="fade">
      <div v-if="sheetOpen" class="sheet-mask" @click="sheetOpen = false"></div>
    </Transition>
    <div class="action-sheet" :class="{ open: sheetOpen }">
      <button class="sheet-item" @click="onTakePhoto">📷 拍照</button>
      <button class="sheet-item" @click="onPickGallery">🖼️ 从相册选择</button>
      <button class="sheet-item cancel" @click="sheetOpen = false">取消</button>
    </div>

    <!-- 上传选点面板（底部滑出，地图保持可操作） -->
    <UploadPickPanel
      v-if="pickVisible && currentFile"
      :key="uploadSeq"
      :preview-url="previewUrl"
      :coord="pickCoord"
      :busy="pickBusy"
      @confirm="onUploadConfirm"
      @cancel="onUploadCancel"
    />

    <!-- 点位详情弹窗：图片列表 + 一一对应备注 + 图片/点位标签 -->
    <SpotDetailPopup
      v-if="popupSpot"
      :spot="popupSpot"
      :photos="popupPhotos"
      @close="popupSpot = null"
      @manage="onManageSpot"
    />

    <!-- 点位管理面板 -->
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

    <!-- 光箱（照片大图浏览，支持连续翻页） -->
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
import { uploadPhoto, hasToken, setNeedLoginListener, fetchIpLocation } from './api/index.js'
import { wgs84ToGcj02 } from './utils/coord.js'
import { hasCapacitor, takePhoto, pickFromGallery, getCurrentPosition } from './utils/capacitor.js'
import SearchBar from './components/SearchBar.vue'
import MapContainer from './components/MapContainer.vue'
import SpotPanel from './components/SpotPanel.vue'
import PhotoLightbox from './components/PhotoLightbox.vue'
import UploadPickPanel from './components/UploadPickPanel.vue'
import SpotDetailPopup from './components/SpotDetailPopup.vue'
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

// ===== 点位详情弹窗状态 =====
const popupSpot = ref(null)
const popupPhotos = computed(() => {
  if (!popupSpot.value) return []
  return photosStore.photosBySpot.value[popupSpot.value.id] || []
})

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
// 光箱：通过 provide 让 PhotoGrid / SpotDetailPopup 打开大图浏览
provide('openLightbox', (photos, idx) => {
  lightboxPhotos.value = photos || []
  lightboxIndex.value = idx || 0
  lightboxVisible.value = true
})

// ===== 定位链（需求1）：权限定位 → 历史定位 → IP → 苏州 =====
const SUZHOU_CENTER = { lng: 120.619585, lat: 31.299379 }
const HISTORY_KEY = 'map_album_last_location'

function readHistory() {
  try {
    const s = JSON.parse(localStorage.getItem(HISTORY_KEY) || 'null')
    if (s && s.lng != null && s.lat != null) return s
  } catch (e) { /* ignore */ }
  return null
}

function saveHistory(coord, src) {
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify({ lng: coord.lng, lat: coord.lat, src, ts: Date.now() }))
  } catch (e) { /* ignore */ }
}

function withTimeout(promise, ms) {
  return Promise.race([
    promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error('定位超时')), ms))
  ])
}

async function locateChain(m) {
  // 1) 请求用户位置权限定位（原生 Capacitor GPS / 浏览器定位）
  try {
    showToast('正在获取定位…')
    if (hasCapacitor()) {
      try {
        const { Geolocation } = await import('@capacitor/geolocation')
        await Geolocation.requestPermissions(['location', 'coarseLocation'])
      } catch (e) { /* 权限请求失败则直接尝试定位 */ }
    }
    const pos = await withTimeout(getCurrentPosition(), 12000)
    const gcj = wgs84ToGcj02({ lng: pos.lng, lat: pos.lat })
    m.setCenter([gcj.lng, gcj.lat])
    m.setZoom(15)
    saveHistory(gcj, 'gps')
    showToast('已定位到当前位置', 'success')
    return
  } catch (e) {
    console.warn('[App] 权限定位失败，尝试历史定位:', e?.message || e)
  }

  // 2) 历史定位（上次成功定位缓存，创建地图时已应用，这里确保中心一致）
  const hist = readHistory()
  if (hist) {
    m.setCenter([hist.lng, hist.lat])
    m.setZoom(14)
    showToast('使用上次定位')
    return
  }

  // 3) IP 网络定位（无需权限的兜底）
  try {
    const loc = await withTimeout(fetchIpLocation(), 6000)
    m.setCenter([loc.lng, loc.lat])
    m.setZoom(13)
    saveHistory({ lng: loc.lng, lat: loc.lat }, 'ip')
    showToast('使用网络定位')
    return
  } catch (e) { /* ignore */ }

  // 4) 默认：苏州（创建地图时已应用）
  showToast('默认定位：苏州')
}

// ===== 地图就绪 =====
async function onMapReady(containerId) {
  try {
    // 初始中心：历史定位 → 苏州（定位链稍后继续）
    const hist = readHistory()
    const initOpts = hist
      ? { center: [hist.lng, hist.lat], zoom: 14 }
      : { center: [SUZHOU_CENTER.lng, SUZHOU_CENTER.lat], zoom: 12 }
    const m = await initMap(containerId, initOpts)
    await spotsStore.bind(m, getAMap(),
      () => photosStore.latestPhotoMap.value,
      () => photosStore.photosBySpot.value,
      {
        onMarkerClick,
        onMarkerMoved,
        onPickMove
      }
    )

    // --- 地图点击：选点模式移动蓝色标记 / 创建模式放置标记 ---
    m.on('click', async (e) => {
      if (spotsStore.isPicking.value) {
        spotsStore.movePickMarker(e.lnglat)
        return
      }
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

    // --- 右键（PC）：选点模式移动标记 / 进入创建模式 ---
    m.on('rightclick', (e) => {
      if (spotsStore.isPicking.value) {
        if (e && e.lnglat) spotsStore.movePickMarker(e.lnglat)
        return
      }
      startCreateMode()
    })

    // 移动端：长按地图 → 选点模式移动标记 / 进入创建模式
    if (isMobile.value) {
      let longPressTimer = null
      const mapContainer = document.getElementById('amap-container')
      if (mapContainer) {
        mapContainer.addEventListener('touchstart', (e) => {
          // 多指不触发；触点在标记上不触发（标记有自己的长按拖动）
          if (e.touches.length > 1) return
          const target = e.target
          if (target && target.closest && target.closest('.custom-marker, .pick-marker')) return
          const t = e.touches[0]
          longPressTimer = setTimeout(() => {
            if (spotsStore.isPicking.value) {
              const ll = clientToLngLat(t.clientX, t.clientY)
              if (ll) spotsStore.movePickMarker(ll)
            } else {
              startCreateMode()
            }
          }, 600)
        }, { passive: true })
        mapContainer.addEventListener('touchend', () => clearTimeout(longPressTimer))
        mapContainer.addEventListener('touchmove', () => clearTimeout(longPressTimer))
      }
    }

    // --- 定位链：权限定位 → 历史定位 → IP → 苏州 ---
    locateChain(m)

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

/** 屏幕坐标 → 地图经纬度（长按选点用） */
function clientToLngLat(clientX, clientY) {
  const m = map.value
  if (!m || typeof m.containerToLngLat !== 'function') return null
  try {
    const rect = m.getContainer().getBoundingClientRect()
    const AMap = getAMap()
    const pixel = new AMap.Pixel(clientX - rect.left, clientY - rect.top)
    return m.containerToLngLat(pixel)
  } catch (e) { return null }
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

// ===== 上传入口（需求2）：底部中间蓝色加号 → 拍照 / 从相册选择 =====
const sheetOpen = ref(false)

async function onTakePhoto() {
  sheetOpen.value = false
  try {
    const file = await takePhoto()
    if (file) enqueueFiles([file])
  } catch (e) {
    if (e && e.message !== '取消拍照') showToast('拍照失败: ' + (e?.message || e), 'error')
  }
}

async function onPickGallery() {
  sheetOpen.value = false
  try {
    const files = await pickFromGallery(true)
    if (files && files.length) enqueueFiles(files)
  } catch (e) {
    if (e && e.message !== '未选择照片') showToast('选择照片失败: ' + (e?.message || e), 'error')
  }
}

// ===== 上传选点流程（需求3/6）：选图 → 底部面板 + 蓝色可拖动标记 → 备注 → 确认上传 =====
const pickVisible = ref(false)
const pickBusy = ref(false)
const pickCoord = ref({ lng: 0, lat: 0, address: '', province: '', city: '', district: '' })
const currentFile = ref(null)
const previewUrl = ref('')
const uploadSeq = ref(0)
let uploadQueue = []

function enqueueFiles(files) {
  uploadQueue.push(...files)
  if (pickVisible.value && currentFile.value) return // 已在选点流程中（追加到队列）
  nextUploadFile(false)
}

/**
 * 取下一张待上传照片。
 * @param {boolean} useCurrentPos true=沿用当前位置（批量连传）；false=解析 EXIF GPS / 地图中心
 */
async function nextUploadFile(useCurrentPos) {
  const file = uploadQueue.shift()
  if (!file) { finishPick(); return }
  currentFile.value = file
  releasePreview()
  previewUrl.value = URL.createObjectURL(file)
  uploadSeq.value++

  let ll = null
  if (!useCurrentPos) {
    try {
      const gps = await exifr.parse(file, { gps: true })
      if (gps && gps.latitude != null && gps.longitude != null) {
        ll = wgs84ToGcj02({ lng: gps.longitude, lat: gps.latitude })
      }
    } catch (e) { /* 无 EXIF */ }
    if (!ll && map.value) {
      const c = map.value.getCenter()
      ll = { lng: c.getLng(), lat: c.getLat() }
    }
  }
  if (!ll) ll = { lng: pickCoord.value.lng, lat: pickCoord.value.lat }
  if (!ll.lng || !ll.lat) {
    const c = map.value ? map.value.getCenter() : { getLng: () => SUZHOU_CENTER.lng, getLat: () => SUZHOU_CENTER.lat }
    ll = { lng: c.getLng(), lat: c.getLat() }
  }

  pickCoord.value = { lng: ll.lng, lat: ll.lat, address: '', province: '', city: '', district: '' }
  spotsStore.startPickMode(ll)
  if (!useCurrentPos && map.value) {
    map.value.setCenter([ll.lng, ll.lat])
    map.value.setZoom(16)
  }
  pickVisible.value = true
  queueRegeo(ll)
}

function releasePreview() {
  if (previewUrl.value) {
    try { URL.revokeObjectURL(previewUrl.value) } catch (e) { /* ignore */ }
    previewUrl.value = ''
  }
}

/** 选点变化（拖动标记 / 点击地图 / 长按地图）→ 更新面板坐标 + 防抖逆地理编码 */
function onPickMove(coord) {
  pickCoord.value = { ...pickCoord.value, lng: coord.lng, lat: coord.lat, address: '' }
  queueRegeo(coord)
}

let _regeoTimer = null
let _regeoSeq = 0
function queueRegeo(coord) {
  clearTimeout(_regeoTimer)
  const seq = ++_regeoSeq
  _regeoTimer = setTimeout(async () => {
    try {
      const geocoder = await createGeocoder()
      geocoder.getAddress([coord.lng, coord.lat], (status, result) => {
        if (seq !== _regeoSeq) return // 已有更新的选点，丢弃旧结果
        if (status === 'complete' && result.regeocode) {
          const ac = result.regeocode.addressComponent || {}
          pickCoord.value = {
            ...pickCoord.value,
            address: result.regeocode.formattedAddress || '',
            province: ac.province || '',
            city: ac.city || '',
            district: ac.district || ''
          }
        }
      })
    } catch (e) { /* ignore */ }
  }, 300)
}

/** 找 50m 内已有点位，没有则创建（需求4：确认上传后地图出现方形缩略图标记） */
async function ensureSpotAt(coord) {
  const near = spotsStore.spots.value.find(s =>
    Math.abs(s.lng - coord.lng) < 0.0005 && Math.abs(s.lat - coord.lat) < 0.0005
  )
  if (near) return near
  const created = await spotsStore.create({
    name: coord.address || `位置 ${Number(coord.lat).toFixed(4)}, ${Number(coord.lng).toFixed(4)}`,
    lat: coord.lat,
    lng: coord.lng,
    address: coord.address || '',
    province: coord.province || '',
    city: coord.city || '',
    district: coord.district || '',
    category: 'scenic'
  })
  photosStore.photosBySpot.value = { ...photosStore.photosBySpot.value, [created.id]: [] }
  return created
}

async function onUploadConfirm(description) {
  if (pickBusy.value || !currentFile.value) return
  pickBusy.value = true
  try {
    const coord = { ...pickCoord.value }
    const spot = await ensureSpotAt(coord)
    await uploadPhoto(spot.id, currentFile.value, description || '')
    await photosStore.loadAllPhotos()
    spotsStore.renderAllMarkers()
    showToast('上传成功', 'success')

    if (uploadQueue.length > 0) {
      // 下一张沿用当前位置（批量连传同一地点）
      await nextUploadFile(true)
    } else {
      const s = spotsStore.spots.value.find(x => x.id === spot.id)
      if (s && map.value) {
        map.value.setCenter([s.lng, s.lat])
        map.value.setZoom(16)
      }
      finishPick()
    }
  } catch (e) {
    showToast('上传失败: ' + (e?.message || e), 'error')
  } finally {
    pickBusy.value = false
  }
}

async function onUploadCancel() {
  releasePreview()
  currentFile.value = null
  if (uploadQueue.length > 0) {
    await nextUploadFile(true)
  } else {
    finishPick()
  }
}

function finishPick() {
  spotsStore.stopPickMode()
  pickVisible.value = false
  currentFile.value = null
  releasePreview()
  uploadQueue = []
}

// ===== 点位详情弹窗（需求5）：点击缩略图标记 → 图片列表 + 一一对应备注 =====
function onMarkerClick(spot) {
  if (spotsStore.isPicking.value || spotsStore.isCreating.value) return
  popupSpot.value = spot
  if (!photosStore.photosBySpot.value[spot.id]) {
    photosStore.loadPhotos(spot.id).catch(() => {})
  }
}

function onManageSpot() {
  const spot = popupSpot.value
  popupSpot.value = null
  if (spot) onSpotClick(spot) // 打开点位管理面板
}

// ===== 长按标记拖动（需求7）：松手保存点位新位置 =====
async function onMarkerMoved(spot, coord) {
  try {
    const updated = await spotsStore.update({ id: spot.id, lat: coord.lat, lng: coord.lng })
    showToast('位置已保存', 'success')
    if (popupSpot.value && popupSpot.value.id === spot.id) popupSpot.value = updated
    // 后台逆地理编码，静默更新地址
    try {
      const geocoder = await createGeocoder()
      geocoder.getAddress([coord.lng, coord.lat], async (status, result) => {
        if (status === 'complete' && result.regeocode) {
          const ac = result.regeocode.addressComponent || {}
          try {
            const u2 = await spotsStore.update({
              id: spot.id,
              address: result.regeocode.formattedAddress || '',
              province: ac.province || '',
              city: ac.city || '',
              district: ac.district || ''
            })
            if (popupSpot.value && popupSpot.value.id === spot.id) popupSpot.value = u2
          } catch (e) { /* ignore */ }
        }
      })
    } catch (e) { /* ignore */ }
  } catch (e) {
    showToast('保存位置失败: ' + (e?.message || e), 'error')
    spotsStore.renderAllMarkers() // 回弹到原位置
  }
}

// ===== 点位面板交互 =====
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
  autoEdit.value = false
  panelVisible.value = false
  currentSpot.value = null
  isCreating.value = false
  spotsStore.cancelCreateMode()
}

async function onSpotCreated(spotData) {
  try {
    const spot = await spotsStore.create({ ...spotData })
    spotsStore.cancelCreateMode()
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
    if (popupSpot.value && popupSpot.value.id === updated.id) popupSpot.value = updated
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
    if (popupSpot.value && popupSpot.value.id === id) popupSpot.value = null
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
  if (sheetOpen.value) { sheetOpen.value = false; e.preventDefault(); return }
  if (popupSpot.value) { popupSpot.value = null; e.preventDefault(); return }
  if (lightboxVisible.value) { lightboxVisible.value = false; e.preventDefault(); return }
  if (pickVisible.value) { finishPick(); showToast('已取消上传'); e.preventDefault(); return }
  if (panelVisible.value) { closePanel(); e.preventDefault(); return }
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

/* ===== 底部中间上传按钮 ===== */
.upload-fab {
  position: fixed; left: 50%; transform: translateX(-50%);
  bottom: max(24px, env(safe-area-inset-bottom));
  z-index: 150;
  width: 60px; height: 60px; border-radius: 50%;
  background: #1a73e8; color: #fff;
  border: none; cursor: pointer;
  font-size: 32px; line-height: 1;
  box-shadow: 0 6px 20px rgba(26,115,232,0.45);
  display: flex; align-items: center; justify-content: center;
  transition: transform 0.2s, box-shadow 0.2s;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  padding-bottom: 4px; /* 视觉居中 ＋ */
}
.upload-fab:hover { transform: translateX(-50%) scale(1.08); box-shadow: 0 8px 24px rgba(26,115,232,0.6); }
.upload-fab:active { transform: translateX(-50%) scale(0.94); }

/* ===== 上传方式选择（动作面板） ===== */
.sheet-mask {
  position: fixed; inset: 0; z-index: 180;
  background: rgba(0,0,0,0.35);
}
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

.action-sheet {
  position: fixed; left: 50%; bottom: 0; z-index: 190;
  width: min(420px, 94vw);
  transform: translate(-50%, 110%);
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  pointer-events: none;
  padding-bottom: calc(10px + env(safe-area-inset-bottom));
}
.action-sheet.open { transform: translate(-50%, 0); pointer-events: auto; }
.sheet-item {
  display: block; width: 100%;
  padding: 16px; min-height: 54px;
  border: none; background: #fff; cursor: pointer;
  font-size: 16px; text-align: center; color: #222;
  -webkit-tap-highlight-color: transparent;
}
.sheet-item:active { background: #f5f8fc; }
.sheet-item + .sheet-item { border-top: 1px solid #f0f0f0; }
.sheet-item:first-child { border-radius: 14px 14px 0 0; }
.sheet-item.cancel {
  margin-top: 8px; border-radius: 14px;
  color: #8a97a8; font-weight: 500;
}
</style>
