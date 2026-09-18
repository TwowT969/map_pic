<template>
  <div class="app-root" :class="{ mobile: isMobile }">
    <!-- 隐私合规（原生端首次启动必须先同意） -->
    <PrivacyConsent v-if="!privacyReady" @agree="onPrivacyAgree" />

    <template v-if="privacyReady">
      <!-- 地图视图 -->
      <SearchBar
        v-show="activeView === 'map'"
        @select="onSearchSelect"
        @select-own-spot="onSearchOwnSpot"
        @select-own-photo="onSearchOwnPhoto"
      />
      <div v-show="activeView === 'map'" class="map-host">
        <MapContainer @map-ready="onMapReady" />
      </div>

      <!-- 相册视图（时间轴） -->
      <AlbumView v-if="activeView === 'album'" />
      <!-- 我的视图 -->
      <ProfileView
        v-if="activeView === 'profile'"
        :pending-count="pendingCount"
        @check-update="checkUpdate(true)"
      />

      <!-- 底部标签栏：地图 / 相册 / 我的 -->
      <nav class="tab-bar">
        <button class="tab-item" :class="{ active: activeView === 'map' }" @click="switchView('map')">
          <span class="tab-icon">🗺️</span>
          <span class="tab-label">地图</span>
        </button>
        <button class="tab-item" :class="{ active: activeView === 'album' }" @click="switchView('album')">
          <span class="tab-icon">🖼️</span>
          <span class="tab-label">相册</span>
          <span class="tab-badge" v-if="totalPhotos">{{ totalPhotos > 99 ? '99+' : totalPhotos }}</span>
        </button>
        <button class="tab-item" :class="{ active: activeView === 'profile' }" @click="switchView('profile')">
          <span class="tab-icon">👤</span>
          <span class="tab-label">我的</span>
        </button>
      </nav>

      <!-- 离线横幅 -->
      <div class="offline-banner" v-if="isOffline && activeView === 'map'">
        📴 当前无网络，拍摄的照片将自动保存待传
      </div>

      <!-- 待上传横幅 -->
      <div
        class="pending-banner"
        v-if="pendingCount > 0 && activeView === 'map' && !pickVisible && !pickBusy"
        @click="resumePending"
      >
        <span>⏳ {{ pendingCount }} 张照片待上传</span>
        <b>{{ resuming ? '上传中…' : '立即上传' }}</b>
      </div>

      <!-- 底部中间蓝色加号：上传入口 -->
      <button class="upload-fab" v-if="activeView === 'map'" @click.stop="openSheet" aria-label="添加照片">＋</button>


      <!-- 上传方式选择：拍照 / 从相册选择 -->
      <Transition name="fade">
        <div v-if="sheetOpen" class="sheet-mask" @click="sheetOpen = false"></div>
      </Transition>
      <div class="action-sheet" :class="{ open: sheetOpen }">
        <div class="sheet-head">
          <b>添加照片</b>
          <span>选择照片后会落到地图当前位置，可拖动蓝色标记校准</span>
        </div>
        <button class="sheet-item" @click="onTakePhoto">
          <span class="sheet-emoji">📷</span>
          <span class="sheet-text"><b>拍照</b><small>拍摄新照片，自动记录位置与拍摄时间</small></span>
        </button>
        <button class="sheet-item" @click="onPickGallery">
          <span class="sheet-emoji">🖼️</span>
          <span class="sheet-text"><b>从相册选择</b><small>批量选择已有照片（一次最多 20 张）</small></span>
        </button>
        <button class="sheet-item cancel" @click="sheetOpen = false">取消</button>
      </div>

      <!-- 长按点位操作菜单：编辑 / 拖动 / 备注 -->
      <Transition name="fade">
        <div v-if="spotMenuOpen" class="sheet-mask" @click="closeSpotMenu"></div>
      </Transition>
      <div class="action-sheet" :class="{ open: spotMenuOpen }">
        <div class="sheet-head">
          <b>{{ spotMenuSpot ? spotMenuSpot.name : '点位' }}</b>
          <span>长按点位标记可快速操作</span>
        </div>
        <button class="sheet-item" @click="onSpotMenu('edit')">
          <span class="sheet-emoji">✏️</span>
          <span class="sheet-text"><b>编辑点位</b><small>修改名称、分类、标签与描述</small></span>
        </button>
        <button class="sheet-item" @click="onSpotMenu('drag')">
          <span class="sheet-emoji">✋</span>
          <span class="sheet-text"><b>拖动点位</b><small>拖动标记到新位置，松手自动保存</small></span>
        </button>
        <button class="sheet-item" @click="onSpotMenu('remark')">
          <span class="sheet-emoji">📝</span>
          <span class="sheet-text"><b>备注</b><small>记录这个打卡点的说明信息</small></span>
        </button>
        <button class="sheet-item cancel" @click="closeSpotMenu">取消</button>
      </div>

      <!-- 点位备注编辑 -->
      <div class="upd-mask" v-if="remarkVisible" @click.self="remarkVisible = false">
        <div class="upd-card">
          <div class="upd-title">点位备注</div>
          <textarea
            v-model="remarkDraft"
            class="remark-input"
            rows="4"
            maxlength="200"
            placeholder="记录这个打卡点的说明（最多 200 字）"
          ></textarea>
          <div class="upd-actions">
            <button class="upd-btn ghost" @click="remarkVisible = false">取消</button>
            <button class="upd-btn primary" :disabled="remarkBusy" @click="onSaveRemark">{{ remarkBusy ? '保存中…' : '保存' }}</button>
          </div>
        </div>
      </div>

      <!-- 更新弹窗 -->
      <div class="upd-mask" v-if="updateInfo" @click.self="updateInfo = null">
        <div class="upd-card">
          <div class="upd-title">发现新版本 v{{ updateInfo.versionName }}</div>
          <div class="upd-notes" v-if="updateInfo.notes">{{ updateInfo.notes }}</div>
          <div class="upd-actions">
            <button class="upd-btn ghost" @click="updateInfo = null">稍后</button>
            <button class="upd-btn primary" @click="goUpdate">立即更新</button>
          </div>
        </div>
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

      <!-- 点位详情弹窗：图片列表（编辑/拖动/备注走长按菜单） -->
      <SpotDetailPopup
        v-if="popupSpot"
        :spot="popupSpot"
        :photos="popupPhotos"
        @close="popupSpot = null"
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

      <!-- 光箱（大图浏览：缩放手势 + 备注编辑/删除） -->
      <PhotoLightbox
        :photos="lightboxPhotos"
        v-model:index="lightboxIndex"
        :visible="lightboxVisible"
        editable
        @close="lightboxVisible = false"
        @save-desc="onSavePhotoDesc"
        @delete-photo="onDeletePhoto"
      />

    <ToastMessage />
      <UserLogin v-if="needLogin" @logged-in="onLoggedIn" />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, provide, nextTick, onMounted, onBeforeUnmount } from 'vue'
import exifr from 'exifr'
import {
  createPhotoMeta, hasToken, setNeedLoginListener, fetchIpLocation,
  fetchAppVersion, getCurrentNickname
} from './api/index.js'
import { saveLocalPhoto, deleteLocalPhoto } from './utils/photoStore.js'
import { wgs84ToGcj02 } from './utils/coord.js'
import {
  hasCapacitor, takePhoto, pickFromGallery, getCurrentPosition,
  requestLocationPermission, compressImage, onNetworkChange
} from './utils/capacitor.js'
import { addPending, listPending, removePending, countPending } from './utils/pendingQueue.js'
import { buildBrowseChain } from './utils/browseChain.js'
import { track, reportError, flush, installGlobalErrorHandlers } from './utils/applog.js'
import SearchBar from './components/SearchBar.vue'
import MapContainer from './components/MapContainer.vue'
import SpotPanel from './components/SpotPanel.vue'
import PhotoLightbox from './components/PhotoLightbox.vue'
import UploadPickPanel from './components/UploadPickPanel.vue'
import SpotDetailPopup from './components/SpotDetailPopup.vue'
import AlbumView from './components/AlbumView.vue'
import ProfileView from './components/ProfileView.vue'
import PrivacyConsent from './components/PrivacyConsent.vue'
import ToastMessage from './components/ToastMessage.vue'
import UserLogin from './components/UserLogin.vue'
import { useAmap } from './composables/useAmap.js'
import { useSpots } from './composables/useSpots.js'
import { usePhotos } from './composables/usePhotos.js'
import { useToast } from './composables/useToast.js'
import { injectMarkerStyles } from './utils/marker.js'

// 初始化
injectMarkerStyles()
installGlobalErrorHandlers()

const { map, initMap, getAMap, createGeocoder } = useAmap()
const spotsStore = useSpots()
const photosStore = usePhotos()
const { toastState, showToast } = useToast()

// ===== 隐私合规（原生端首次启动门） =====
const PRIVACY_KEY = 'map_album_privacy_consent_v1'
const privacyReady = ref(!hasCapacitor() || !!localStorage.getItem(PRIVACY_KEY))

function onPrivacyAgree() {
  localStorage.setItem(PRIVACY_KEY, '1')
  privacyReady.value = true
  track('consent', 'agree')
  checkUpdate(false)
}

// ===== 登录门（全端统一：账号 + 密码登录/注册） =====
const needLogin = ref(!hasToken())
setNeedLoginListener(() => { needLogin.value = true })

async function onLoggedIn() {
  needLogin.value = false
  try { await flush() } catch (e) { /* ignore */ }
  try {
    if (!map.value) {
      // 登录前地图初始化因未登录被中断 → 重新初始化
      await onMapReady('amap-container')
    } else {
      await spotsStore.loadSpots()
      await photosStore.loadAllPhotos()
      spotsStore.renderAllMarkers()
    }
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

// ===== 视图切换（地图 / 相册 / 我的） =====
const activeView = ref('map')

function switchView(v) {
  if (activeView.value === v) return
  activeView.value = v
  track('view_switch', v)
  if (v === 'map') {
    nextTick(() => { if (map.value && map.value.resize) map.value.resize() })
  }
}

// ===== 面板状态 =====
const panelVisible = ref(false)
const isCreating = ref(false)
const createCoord = ref({ lng: 0, lat: 0 })
const currentSpot = ref(null)
const autoEdit = ref(false)

// ===== 光箱状态 =====
const lightboxVisible = ref(false)
const lightboxPhotos = ref([])
const lightboxIndex = ref(0)

// ===== 点位详情弹窗状态 =====
const popupSpot = ref(null)
const popupPhotos = computed(() => {
  if (!popupSpot.value) return []
  return photosStore.photosBySpot.value[popupSpot.value.id] || []
})

// 全部照片数（相册标签角标）
const totalPhotos = computed(() => photosStore.allPhotos.value.length)

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
  // 1) 请求用户位置权限定位（先说明用途，再触发系统权限弹窗）
  try {
    showToast('正在获取定位…')
    if (hasCapacitor()) {
      showToast('需要定位权限，用于地图定位与记录照片位置')
      await requestLocationPermission()
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

  // 2) 历史定位（上次成功定位缓存，创建地图时已应用）
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

  // 4) 默认：苏州
  showToast('默认定位：苏州')
}

// ===== 地图就绪 =====
async function onMapReady(containerId) {
  try {
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
        onMarkerPhotoClick,
        onMarkerLongPress,
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

    // --- 右键（PC） ---
    m.on('rightclick', (e) => {
      if (spotsStore.isPicking.value) {
        if (e && e.lnglat) spotsStore.movePickMarker(e.lnglat)
        return
      }
      startCreateMode()
    })

    // --- 移动端长按地图 ---
    if (isMobile.value) {
      let longPressTimer = null
      const mapContainer = document.getElementById('amap-container')
      if (mapContainer) {
        mapContainer.addEventListener('touchstart', (e) => {
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

    // --- 定位链 ---
    locateChain(m)

    // --- 加载数据 ---
    try {
      await spotsStore.loadSpots()
      await photosStore.loadAllPhotos()
      spotsStore.renderAllMarkers()
      console.log('[App] 数据加载完成，共 ' + spotsStore.spots.value.length + ' 个点位')
    } catch (e) {
      console.error('[App] 加载数据失败:', e)
      if (e && e.needLogin) return // 未登录：等待登录门，登录后补建
      showToast('加载数据失败: ' + e.message, 'error')
    }
  } catch (e) {
    console.error('[App] 地图初始化失败:', e)
    if (e && e.needLogin) return // 未登录：等登录门，登录后 onLoggedIn 重新初始化
    reportError(e, 'map-init')
    showToast('地图初始化失败: ' + e.message, 'error')
  }
}

/** 屏幕坐标 → 地图经纬度 */
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

function onSearchOwnSpot(spot) {
  if (map.value) {
    map.value.setCenter([spot.lng, spot.lat])
    map.value.setZoom(17)
  }
  onMarkerClick(spot)
}

function onSearchOwnPhoto(photo) {
  const spot = spotsStore.spots.value.find(s => s.id === photo.spotId)
  if (!spot) { showToast('照片所属点位不存在'); return }
  if (map.value) {
    map.value.setCenter([spot.lng, spot.lat])
    map.value.setZoom(17)
  }
  const list = photosStore.photosBySpot.value[spot.id] || []
  const idx = list.findIndex(p => p.id === photo.id)
  lightboxPhotos.value = list
  lightboxIndex.value = idx >= 0 ? idx : 0
  lightboxVisible.value = true
}

// ===== 上传入口：底部中间蓝色加号 → 拍照 / 从相册选择 =====
const HINT_KEY = 'map_album_upload_hint_v1'
const sheetOpen = ref(false)

function openSheet() {
  sheetOpen.value = true
  if (!localStorage.getItem(HINT_KEY)) {
    localStorage.setItem(HINT_KEY, '1')
    setTimeout(() => showToast('添加后可拖动地图上的蓝色标记校准位置'), 400)
  }
}


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

// ===== 上传选点流程：选图 → 底部面板 + 蓝色可拖动标记 → 备注 → 确认上传 =====
const pickVisible = ref(false)
const pickBusy = ref(false)
const pickCoord = ref({ lng: 0, lat: 0, address: '', province: '', city: '', district: '' })
const currentFile = ref(null)
const previewUrl = ref('')
const uploadSeq = ref(0)
let uploadQueue = []
const currentMeta = ref({ shotTime: '', device: '' })

function enqueueFiles(files) {
  uploadQueue.push(...files)
  if (pickVisible.value && currentFile.value) return
  nextUploadFile(false)
}

function fmtDateTime(d) {
  if (!d) return ''
  const t = new Date(d)
  if (isNaN(t.getTime())) return ''
  const p = n => String(n).padStart(2, '0')
  return `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())} ${p(t.getHours())}:${p(t.getMinutes())}:${p(t.getSeconds())}`
}

/**
 * 取下一张待上传照片。
 * 始终解析 EXIF 元数据（拍摄时间/设备）；仅首张（或换点）时使用 EXIF GPS 预定位。
 * @param {boolean} useCurrentPos true=沿用当前位置（批量连传）
 */
async function nextUploadFile(useCurrentPos) {
  const file = uploadQueue.shift()
  if (!file) { finishPick(); return }
  currentFile.value = file
  releasePreview()
  previewUrl.value = URL.createObjectURL(file)
  uploadSeq.value++

  // EXIF 元数据（拍摄时间 / 设备 / GPS）
  currentMeta.value = { shotTime: '', device: '' }
  let exifGps = null
  try {
    const exif = await exifr.parse(file)
    if (exif) {
      if (exif.latitude != null && exif.longitude != null) {
        exifGps = { lng: exif.longitude, lat: exif.latitude }
      }
      const dto = exif.DateTimeOriginal || exif.CreateDate
      if (dto) currentMeta.value.shotTime = fmtDateTime(dto)
      const dev = [exif.Make, exif.Model].filter(Boolean).join(' ').trim()
      if (dev) currentMeta.value.device = dev.slice(0, 100)
    }
  } catch (e) { /* 无 EXIF，忽略 */ }

  let ll = null
  if (!useCurrentPos) {
    if (exifGps) ll = wgs84ToGcj02(exifGps)
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

/** 选点变化 → 更新面板坐标 + 防抖逆地理编码 */
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
        if (seq !== _regeoSeq) return
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

/** 找 50m 内已有点位，没有则创建 */
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
    // 1. 压缩原图 + 生成缩略图（EXIF 元数据已在选图时解析，随参数显式携带）
    const full = await compressImage(currentFile.value, 2048, 0.85)
    const thumb = await compressImage(currentFile.value, 256, 0.72)
    // 2. 图片只存设备本地（不传服务器，离线也可保存）
    const loc = await saveLocalPhoto(full, thumb)
    // 3. 远程只登记元数据（本地路径 + 备注 + 拍摄信息）；失败进待同步队列
    let spotForView = null
    let registered = false
    try {
      if (isOffline.value) throw new Error('当前无网络')
      const spot = await ensureSpotAt({ ...pickCoord.value })
      spotForView = spot
      await createPhotoMeta({
        spotId: spot.id,
        description: description || '',
        shotTime: currentMeta.value.shotTime || null,
        device: currentMeta.value.device || null,
        localPath: loc.localPath,
        localThumbPath: loc.localThumbPath
      })
      registered = true
    } catch (e) {
      if (!registered) {
        const saved = await addPending({
          id: 'p_' + Date.now() + '_' + Math.random().toString(36).slice(2, 6),
          meta: {
            ...pickCoord.value,
            desc: description || '',
            shotTime: currentMeta.value.shotTime,
            device: currentMeta.value.device,
            localPath: loc.localPath,
            localThumbPath: loc.localThumbPath
          }
        })
        if (saved) {
          showToast('已存到本地，联网后自动同步记录', 'success')
          track('upload_pending', e?.message || '')
        } else {
          showToast('保存失败: ' + (e?.message || e), 'error')
          track('upload_fail', e?.message || '')
        }
      }
    }
    if (registered) {
      // 登记已成功：此处刷新失败只忽略，绝不能落入待传队列（否则补传会重复登记）
      try {
        await photosStore.loadAllPhotos()
        spotsStore.renderAllMarkers()
      } catch (e) { /* ignore */ }
      showToast('已保存', 'success')
      track('upload_success')
    }
    if (uploadQueue.length > 0) {
      await nextUploadFile(true)
    } else {
      if (spotForView && map.value) {
        map.value.setCenter([spotForView.lng, spotForView.lat])
        map.value.setZoom(16)
      }
      finishPick()
    }
  } catch (e) {
    showToast('保存失败: ' + (e?.message || e), 'error')
    track('upload_fail', e?.message || '')
    if (uploadQueue.length > 0) {
      await nextUploadFile(true)
    } else {
      finishPick()
    }
  } finally {
    pickBusy.value = false
    refreshPendingCount()
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

// ===== 待上传队列（重试/离线补传） =====
const pendingCount = ref(0)
const resuming = ref(false)
const isOffline = ref(typeof navigator !== 'undefined' ? !navigator.onLine : false)
let _resumeTimer = null

async function refreshPendingCount() {
  pendingCount.value = await countPending()
}

async function resumePending() {
  if (resuming.value || pickBusy.value) return
  resuming.value = true
  try {
    const items = await listPending()
    let ok = 0
    for (const item of items) {
      if (isOffline.value) break
      if (item.meta && item.meta.registeredAt) {
        // 上次已登记成功但队列删除失败：不再重复登记，仅清理队列
        await removePending(item.id)
        continue
      }
      try {
        // 旧版本队列的 blob 记录：先补存本地再登记
        let localPath = item.meta && item.meta.localPath
        let localThumbPath = item.meta && item.meta.localThumbPath
        if (!localPath && item.blob) {
          const thumb = await compressImage(item.blob, 256, 0.72)
          const loc = await saveLocalPhoto(item.blob, thumb)
          localPath = loc.localPath
          localThumbPath = loc.localThumbPath
        }
        if (!localPath) { await removePending(item.id); continue }
        const spot = await ensureSpotAt(item.meta)
        await createPhotoMeta({
          spotId: spot.id,
          description: item.meta.desc || '',
          shotTime: item.meta.shotTime || null,
          device: item.meta.device || null,
          localPath,
          localThumbPath
        })
        const removed = await removePending(item.id)
        if (!removed) {
          // 登记成功但队列删除失败：打标防止下次补传重复登记
          item.meta = item.meta || {}
          item.meta.registeredAt = Date.now()
          await addPending(item)
        }
        ok++
      } catch (e) { /* 保留队列下次再试 */ }
    }
    if (ok > 0) {
      await photosStore.loadAllPhotos()
      spotsStore.renderAllMarkers()
      showToast(`已同步 ${ok} 张照片记录`, 'success')
      track('upload_resume', String(ok))
    } else if (items.length > 0) {
      showToast('同步未完成，稍后再试')
    }
  } finally {
    resuming.value = false
    await refreshPendingCount()
  }
}

// ===== 点位详情弹窗 =====
function onMarkerClick(spot) {
  if (spotsStore.isPicking.value || spotsStore.isCreating.value) return
  popupSpot.value = spot
  if (!photosStore.photosBySpot.value[spot.id]) {
    photosStore.loadPhotos(spot.id).catch(() => {})
  }
}

/**
 * 点击点位标记上的图片 → 全屏浏览（需求4）：
 * 以当前位置为基准，把全部图片按"到用户的距离"加入临时链表头/尾
 * （更近 → 头插，更远 → 尾插），从被点图片开始沿链表左右滑动切换。
 */
async function onMarkerPhotoClick(spot, photo) {
  let userLoc = null
  try {
    const pos = await Promise.race([
      getCurrentPosition(),
      new Promise((_, reject) => setTimeout(() => reject(new Error('定位超时')), 2500))
    ])
    userLoc = wgs84ToGcj02({ lng: pos.lng, lat: pos.lat })
  } catch (e) {
    userLoc = readHistory() || null
  }
  if (!userLoc && map.value) {
    try {
      const c = map.value.getCenter()
      userLoc = { lng: c.getLng(), lat: c.getLat() }
    } catch (err) { /* ignore */ }
  }
  const spotsMap = {}
  spotsStore.spots.value.forEach(s => { spotsMap[s.id] = { lng: Number(s.lng), lat: Number(s.lat) } })
  const chain = buildBrowseChain(photosStore.allPhotos.value, spotsMap, userLoc, photo && photo.id)
  if (!chain.photos.length) { showToast('没有可浏览的图片'); return }
  lightboxPhotos.value = chain.photos
  lightboxIndex.value = chain.startIndex
  lightboxVisible.value = true
  track('marker_photo_open', (chain.orderedBy === 'distance' ? 'dist:' : 'time:') + chain.photos.length)
}

// ===== 长按点位操作菜单（编辑 / 拖动 / 备注） =====
const spotMenuOpen = ref(false)
const spotMenuSpot = ref(null)
const remarkVisible = ref(false)
const remarkDraft = ref('')
const remarkBusy = ref(false)

/** 长按点位标记 → 弹出操作菜单 */
function onMarkerLongPress(spot) {
  if (spotsStore.isPicking.value || spotsStore.isCreating.value) return
  spotMenuSpot.value = spot
  spotMenuOpen.value = true
  track('spot_longpress')
}

function closeSpotMenu() {
  spotMenuOpen.value = false
}

/** 菜单动作分发 */
function onSpotMenu(action) {
  const spot = spotMenuSpot.value
  closeSpotMenu()
  if (!spot) return
  if (action === 'edit') {
    openSpotEdit(spot)
  } else if (action === 'drag') {
    const ok = spotsStore.startSpotDrag(spot.id)
    showToast(ok ? '拖动标记到新位置，松手自动保存' : '当前标记暂不可拖动，请稍后再试', ok ? 'success' : 'error')
  } else if (action === 'remark') {
    remarkDraft.value = spot.description || ''
    remarkVisible.value = true
  }
}

/** 直接进入点位编辑态（管理面板 + 编辑表单） */
function openSpotEdit(spot) {
  popupSpot.value = null
  currentSpot.value = spot
  isCreating.value = false
  spotsStore.cancelCreateMode()
  autoEdit.value = true
  panelVisible.value = true
  photosStore.loadPhotos(spot.id)
}

/** 保存点位备注 */
async function onSaveRemark() {
  const spot = spotMenuSpot.value
  if (!spot || remarkBusy.value) return
  remarkBusy.value = true
  try {
    const updated = await spotsStore.update({ id: spot.id, description: remarkDraft.value })
    if (popupSpot.value && popupSpot.value.id === spot.id) popupSpot.value = updated
    if (currentSpot.value && currentSpot.value.id === spot.id) currentSpot.value = updated
    remarkVisible.value = false
    showToast('备注已保存', 'success')
    track('spot_remark')
  } catch (e) {
    showToast('保存备注失败: ' + (e?.message || e), 'error')
  } finally {
    remarkBusy.value = false
  }
}

// ===== 光箱：备注编辑 / 删除 =====
async function onSavePhotoDesc(photo, description) {
  try {
    await photosStore.updateDescription(photo.id, description)
    lightboxPhotos.value = lightboxPhotos.value.map(p =>
      p.id === photo.id ? { ...p, description } : p
    )
    showToast('备注已更新', 'success')
    track('photo_desc_edit')
  } catch (e) {
    showToast('更新失败: ' + (e?.message || e), 'error')
  }
}

async function onDeletePhoto(photo) {
  try {
    await photosStore.remove(photo.id, photo.spotId)
    lightboxPhotos.value = lightboxPhotos.value.filter(p => p.id !== photo.id)
    if (lightboxPhotos.value.length === 0) {
      lightboxVisible.value = false
    } else if (lightboxIndex.value >= lightboxPhotos.value.length) {
      lightboxIndex.value = lightboxPhotos.value.length - 1
    }
    if (currentSpot.value && currentSpot.value.id === photo.spotId) {
      currentSpot.value = {
        ...currentSpot.value,
        photoCount: Math.max(0, (currentSpot.value.photoCount || 1) - 1)
      }
    }
    spotsStore.updateMarker(currentSpot.value)
    showToast('照片已删除', 'success')
    track('photo_delete')
  } catch (e) {
    showToast('删除失败: ' + (e?.message || e), 'error')
  }
}

// ===== 长按标记拖动：松手保存新位置 =====
async function onMarkerMoved(spot, coord) {
  try {
    const updated = await spotsStore.update({ id: spot.id, lat: coord.lat, lng: coord.lng })
    showToast('位置已保存', 'success')
    track('spot_move')
    if (popupSpot.value && popupSpot.value.id === spot.id) popupSpot.value = updated
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
    spotsStore.renderAllMarkers()
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
    // 服务端已级联删除照片：同步清理本地照片状态与设备文件，避免相册"幽灵照片"
    const orphans = photosStore.allPhotos.value.filter(p => p.spotId === id)
    photosStore.allPhotos.value = photosStore.allPhotos.value.filter(p => p.spotId !== id)
    const photoMap = { ...photosStore.photosBySpot.value }
    delete photoMap[id]
    photosStore.photosBySpot.value = photoMap
    for (const p of orphans) { deleteLocalPhoto(p).catch(() => {}) }
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
    const spot = spotsStore.spots.value.find(sp => sp.id === spotId) || { id: spotId }
    const ok = await photosStore.upload(files, spot)
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

function onPreviewPhoto(photo) {
  const list = currentPhotos.value
  const idx = list.findIndex(p => p.id === photo.id)
  lightboxPhotos.value = list
  lightboxIndex.value = idx >= 0 ? idx : 0
  lightboxVisible.value = true
}

// ===== 应用内更新检查 =====
const APP_VERSION_CODE = Number(import.meta.env.VITE_APP_VERSION_CODE || 1)
const updateInfo = ref(null)

async function checkUpdate(manual) {
  try {
    const data = await fetchAppVersion()
    if (data && Number(data.versionCode) > APP_VERSION_CODE) {
      updateInfo.value = data
    } else if (manual) {
      showToast('已是最新版本', 'success')
    }
  } catch (e) {
    if (manual) showToast('检查更新失败: ' + (e?.message || e), 'error')
  }
}

function goUpdate() {
  if (updateInfo.value && updateInfo.value.downloadUrl) {
    window.open(updateInfo.value.downloadUrl, '_blank')
  }
  updateInfo.value = null
}

// ===== 键盘快捷键 =====
function onKeyDown(e) {
  if (e.key !== 'Escape') return
  if (remarkVisible.value) { remarkVisible.value = false; e.preventDefault(); return }
  if (spotMenuOpen.value) { closeSpotMenu(); e.preventDefault(); return }
  if (updateInfo.value) { updateInfo.value = null; e.preventDefault(); return }
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

/** 安卓系统返回键/手势：逐层关闭浮层，再切回地图，地图无浮层则退出 */
function onAndroidBack(CapApp) {
  if (remarkVisible.value) { remarkVisible.value = false; return }
  if (spotMenuOpen.value) { closeSpotMenu(); return }
  if (updateInfo.value) { updateInfo.value = null; return }
  if (sheetOpen.value) { sheetOpen.value = false; return }
  if (popupSpot.value) { popupSpot.value = null; return }
  if (lightboxVisible.value) { lightboxVisible.value = false; return }
  if (pickVisible.value) { finishPick(); showToast('已取消上传'); return }
  if (panelVisible.value) { closePanel(); return }
  if (spotsStore.isCreating.value) { spotsStore.cancelCreateMode(); showToast('已退出创建模式'); return }
  if (activeView.value !== 'map') { switchView('map'); return }
  CapApp.exitApp()
}

// ===== 生命周期 =====
onMounted(() => {
  document.addEventListener('keydown', onKeyDown)
  track('app_open', getCurrentNickname() || '')
  refreshPendingCount()
  onNetworkChange((connected) => {
    isOffline.value = !connected
    if (connected && pendingCount.value > 0 && privacyReady.value) {
      clearTimeout(_resumeTimer)
      _resumeTimer = setTimeout(() => resumePending(), 2000)
    }
  })
  if (hasCapacitor() && privacyReady.value) checkUpdate(false)
  if (hasCapacitor()) {
    import('@capacitor/app').then(({ App: CapApp }) => {
      CapApp.addListener('backButton', () => onAndroidBack(CapApp))
    }).catch(() => {})
  }
})
onBeforeUnmount(() => document.removeEventListener('keydown', onKeyDown))
</script>

<style>
/* ===== 全局基础 ===== */
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app {
  width: 100%; height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  overflow: hidden;
  position: fixed;
  overscroll-behavior: none;
  -webkit-overflow-scrolling: touch;
}
.app-root { width: 100%; height: 100%; position: relative; }
.map-host { position: absolute; inset: 0; }

/* ===== 底部标签栏（地图 / 相册 / 我的） ===== */
.tab-bar {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 150;
  display: flex;
  background: rgba(255,255,255,0.97);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-top: 1px solid #eef1f6;
  padding-bottom: env(safe-area-inset-bottom);
}
.tab-item {
  flex: 1; border: none; background: none; cursor: pointer;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px;
  padding: 7px 0 6px; min-height: 56px; position: relative;
  font-size: 11px; color: #8a97a8;
  -webkit-tap-highlight-color: transparent;
  transition: color 0.15s;
}
.tab-item.active { color: #1a73e8; font-weight: 600; }
.tab-icon { font-size: 22px; line-height: 1; filter: grayscale(1) opacity(0.5); transition: filter 0.15s, transform 0.15s; }
.tab-item.active .tab-icon { filter: none; transform: translateY(-1px) scale(1.05); }
.tab-badge {
  position: absolute; top: 4px; left: calc(50% + 9px);
  background: #1a73e8; color: #fff;
  font-size: 9px; line-height: 14px; min-width: 14px;
  border-radius: 8px; padding: 0 4px;
  pointer-events: none;
}
/* 高德 logo/版权抬到底部标签栏之上 */
.amap-logo, .amap-copyright { bottom: calc(58px + env(safe-area-inset-bottom, 0px)) !important; }

/* ===== 离线 / 待上传横幅 ===== */
.offline-banner {
  position: fixed; top: max(64px, calc(env(safe-area-inset-top) + 56px));
  left: 50%; transform: translateX(-50%);
  z-index: 140;
  background: rgba(230,126,34,0.95); color: #fff;
  font-size: 12.5px; padding: 8px 16px; border-radius: 18px;
  box-shadow: 0 3px 10px rgba(0,0,0,0.2);
  white-space: nowrap; max-width: 92vw; overflow: hidden; text-overflow: ellipsis;
}
.pending-banner {
  position: fixed;
  bottom: calc(140px + env(safe-area-inset-bottom, 0px));
  left: 50%; transform: translateX(-50%);
  z-index: 140;
  background: rgba(255,255,255,0.97); color: #333;
  font-size: 13px; padding: 9px 16px; border-radius: 20px;
  box-shadow: 0 4px 14px rgba(0,0,0,0.18);
  display: flex; align-items: center; gap: 10px;
  cursor: pointer; white-space: nowrap;
  -webkit-tap-highlight-color: transparent;
}
.pending-banner b { color: #1a73e8; font-weight: 600; }
.pending-banner:active { background: #f0f4fa; }

/* ===== 底部中间上传按钮 ===== */
.upload-fab {
  position: fixed; left: 50%; transform: translateX(-50%);
  bottom: calc(70px + env(safe-area-inset-bottom, 0px));
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
  padding-bottom: 4px;
}
.upload-fab:hover { transform: translateX(-50%) scale(1.08); box-shadow: 0 8px 24px rgba(26,115,232,0.6); }
.upload-fab:active { transform: translateX(-50%) scale(0.94); }


/* ===== 更新弹窗 ===== */
.upd-mask {
  position: fixed; inset: 0; z-index: 320;
  background: rgba(0,0,0,0.45);
  display: flex; align-items: center; justify-content: center;
  padding: 24px;
}
.remark-input {
  width: 100%; margin: 12px 0 4px; padding: 10px 12px;
  border: 1.5px solid #e3e8ef; border-radius: 10px;
  font-size: 14px; color: #1f2d3d; font-family: inherit;
  resize: none; outline: none; box-sizing: border-box;
  background: #fafbfc;
}
.remark-input:focus { border-color: #4a90d9; background: #fff; }

.upd-card {
  background: #fff; border-radius: 14px;
  width: min(360px, 100%);
  padding: 22px 20px 16px;
  box-shadow: 0 12px 40px rgba(0,0,0,0.25);
}
.upd-title { font-size: 17px; font-weight: 600; color: #222; margin-bottom: 10px; }
.upd-notes {
  font-size: 13px; color: #666; line-height: 1.6;
  background: #f7f9fc; border-radius: 10px;
  padding: 10px 12px; margin-bottom: 16px;
  max-height: 120px; overflow-y: auto;
}
.upd-actions { display: flex; gap: 10px; }
.upd-btn {
  flex: 1; min-height: 42px; border-radius: 10px;
  border: none; font-size: 15px; font-weight: 500; cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.upd-btn.ghost { background: #f2f4f7; color: #555; }
.upd-btn.primary { background: #1a73e8; color: #fff; }

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
  padding-bottom: calc(10px + env(safe-area-inset-bottom, 0px));
}
.action-sheet.open { transform: translate(-50%, 0); pointer-events: auto; }
.sheet-head {
  background: #fff; border-radius: 14px 14px 0 0;
  padding: 15px 18px 11px;
  display: flex; flex-direction: column; gap: 3px;
  border-bottom: 1px solid #f0f0f0;
}
.sheet-head b { font-size: 16px; color: #222; }
.sheet-head span { font-size: 12px; color: #98a4b3; }
.sheet-item {
  display: flex; align-items: center; gap: 12px;
  width: 100%; padding: 13px 18px; min-height: 62px;
  border: none; background: #fff; cursor: pointer;
  text-align: left; color: #222;
  -webkit-tap-highlight-color: transparent;
}
.sheet-item:active { background: #f5f8fc; }
.sheet-item + .sheet-item { border-top: 1px solid #f5f6f8; }
.sheet-emoji {
  width: 42px; height: 42px; border-radius: 12px;
  background: #eaf2ff; font-size: 20px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.sheet-text { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.sheet-text b { font-size: 15px; font-weight: 600; }
.sheet-text small { font-size: 12px; color: #98a4b3; }
.sheet-item.cancel {
  margin-top: 8px; border-radius: 14px; min-height: 50px;
  justify-content: center; color: #8a97a8; font-weight: 500;
}
</style>
