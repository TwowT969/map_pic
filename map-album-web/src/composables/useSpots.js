import { ref } from 'vue'
import { fetchSpots, createSpot as apiCreateSpot, updateSpot as apiUpdateSpot, deleteSpot as apiDeleteSpot } from '../api/index.js'
import { createMarkerElement, createSimpleMarkerElement, createPickMarkerElement } from '../utils/marker.js'

// 缩放分级阈值：地图缩放 >= DETAIL_ZOOM 级才渲染照片缩略图标记；
// 更小时只渲染"📍图标 + 照片数"简化标记（不含 <img>，零图片请求，防止大量加载卡顿）
const DETAIL_ZOOM = 14
// 长按触发时长（毫秒）：长按标记后进入拖动，松手保存新位置
const LONG_PRESS_MS = 450

export function useSpots() {
  const spots = ref([])
  const currentSpot = ref(null)
  const isCreating = ref(false)
  const isPicking = ref(false)      // 上传选点模式
  const createMarker = ref(null)

  let _map = null
  let _amap = null
  let _latestPhotoMap = () => ({})
  let _photoCountMap = () => ({})
  let _cluster = null
  let _lastDetail = null
  let _handlers = {}                // { onMarkerClick, onMarkerMoved, onPickMove }
  let _pickMarker = null
  let _justDragged = false          // 拖动刚结束的短暂时间片内屏蔽 click

  async function bind(mapInstance, amapInstance, latestPhotoMapGetter, photoCountMapGetter, handlers = {}) {
    _map = mapInstance
    _amap = amapInstance
    _latestPhotoMap = latestPhotoMapGetter
    _photoCountMap = photoCountMapGetter || (() => ({}))
    _handlers = handlers

    // 加载点聚合插件
    await new Promise((resolve) => {
      _map.plugin('AMap.MarkerCluster', resolve)
    })

    // 创建聚合实例（初始空数据，后续 setData 填充）
    _cluster = new _amap.MarkerCluster(_map, [], {
      gridSize: 80,
      maxZoom: 18,
      renderClusterMarker: _renderClusterMarker,
      renderMarker: _renderMarker
    })

    // 聚合点点击 → 放大展开
    _cluster.on('click', (item) => {
      if (item.clusterData.length <= 1) return // 单个标记不处理
      let lngSum = 0, latSum = 0
      item.clusterData.forEach(d => {
        lngSum += d.lnglat[0]
        latSum += d.lnglat[1]
      })
      _map.setZoomAndCenter(Math.min(_map.getZoom() + 3, 18),
        [lngSum / item.clusterData.length, latSum / item.clusterData.length])
    })

    // 缩放跨过详情阈值 → 重渲染标记（切换 简化图标 / 照片缩略图 两种形态）
    _map.on('zoomend', _onZoomEnd)
  }

  function _isDetail() {
    return !!(_map && _map.getZoom() >= DETAIL_ZOOM)
  }

  function _onZoomEnd() {
    if (_lastDetail !== null && _isDetail() !== _lastDetail) {
      _refreshCluster()
    }
  }

  // ===== 自定义聚合点样式 =====
  function _renderClusterMarker(context) {
    const clusterCount = context.count
    const size = Math.min(30 + clusterCount * 3, 70)
    const div = document.createElement('div')
    div.className = 'cluster-marker'
    div.style.cssText =
      `width:${size}px;height:${size}px;line-height:${size}px;` +
      'background:rgba(74,144,217,0.85);color:#fff;font-size:14px;' +
      'font-weight:bold;text-align:center;border-radius:50%;' +
      'box-shadow:0 2px 8px rgba(74,144,217,0.5);' +
      'border:2px solid #fff;'
    div.textContent = clusterCount
    context.marker.setContent(div)
    context.marker.setOffset(new _amap.Pixel(-size / 2, -size / 2))
  }

  // ===== 自定义单个标记样式 =====
  function _renderMarker(context) {
    const d = context.data[0] // 原始数据
    const spot = spots.value.find(s => s.id === d.spotId)
    const name = d.name || (spot && spot.name) || '未命名'

    let content
    if (_isDetail()) {
      // 近景：照片缩略图标记（thumbUrl 为服务端压缩小图）
      const latestPhoto = _latestPhotoMap()[d.spotId] || null
      content = createMarkerElement(
        { name },
        latestPhoto ? { thumbUrl: latestPhoto.thumbUrl || latestPhoto.url } : null
      )
    } else {
      // 远景：纯图标 + 照片数，不加载任何图片
      const count = (_photoCountMap()[d.spotId] || []).length
      content = createSimpleMarkerElement({ name }, count)
    }
    context.marker.setContent(content)
    context.marker.setAnchor('bottom-center')
    // 针尖在 wrapper 底部，offset (0,0) 即可精准扎在坐标上
    context.marker.setOffset(new _amap.Pixel(0, 0))

    // 长按标记 → 拖动 → 松手保存新位置
    _attachLongPressDrag(context.marker, content, spot)

    // 点击 → 打开点位详情弹窗（图片列表 + 备注）
    context.marker.on('click', () => {
      if (_justDragged) return
      const s = spots.value.find(x => x.id === d.spotId)
      if (s && _handlers.onMarkerClick) _handlers.onMarkerClick(s)
    })
  }

  /**
   * 长按拖动：长按 450ms 进入拖动态（地图禁平移），移动手指实时更新标记位置，
   * 松手回调 onMarkerMoved(spot, {lng,lat}) 由上层保存；未移动则视为普通长按（无操作）。
   */
  function _attachLongPressDrag(marker, contentEl, spot) {
    if (!contentEl || contentEl.__lpDragAttached) return
    contentEl.__lpDragAttached = true

    let pressTimer = null
    let dragging = false
    let moved = false
    let startX = 0
    let startY = 0

    const canDrag = () => !!(_map && spot && !_map.setStatusDisabled && typeof _map.containerToLngLat === 'function')

    const onDown = (e) => {
      if (e.button != null && e.button !== 0) return // 仅左键/触摸
      if (isPicking.value) return                    // 选点模式不拖已有标记
      if (!canDrag()) return
      moved = false
      startX = e.clientX
      startY = e.clientY
      try { contentEl.setPointerCapture && contentEl.setPointerCapture(e.pointerId) } catch (err) { /* ignore */ }
      pressTimer = setTimeout(() => {
        pressTimer = null
        dragging = true
        contentEl.classList.add('marker-dragging')
        try { _map.setStatus({ dragEnable: false }) } catch (err) { /* ignore */ }
        if (_handlers.onDragStart) _handlers.onDragStart(spot)
      }, LONG_PRESS_MS)
    }

    const onMove = (e) => {
      if (dragging) {
        moved = true
        try { e.preventDefault() } catch (err) { /* ignore */ }
        try {
          const rect = _map.getContainer().getBoundingClientRect()
          const pixel = new _amap.Pixel(e.clientX - rect.left, e.clientY - rect.top)
          const ll = _map.containerToLngLat(pixel)
          if (ll) marker.setPosition([ll.lng, ll.lat])
        } catch (err) { /* ignore */ }
        return
      }
      if (pressTimer) {
        // 未满长按时长就移动 → 是拖动地图，取消长按
        if (Math.abs(e.clientX - startX) > 8 || Math.abs(e.clientY - startY) > 8) {
          clearTimeout(pressTimer)
          pressTimer = null
        }
      }
    }

    const onUp = () => {
      if (pressTimer) { clearTimeout(pressTimer); pressTimer = null }
      if (!dragging) return
      dragging = false
      contentEl.classList.remove('marker-dragging')
      try { _map.setStatus({ dragEnable: true }) } catch (err) { /* ignore */ }
      if (moved) {
        _justDragged = true
        setTimeout(() => { _justDragged = false }, 450)
        try {
          const p = marker.getPosition()
          if (p && _handlers.onMarkerMoved) {
            _handlers.onMarkerMoved(spot, { lng: p.getLng(), lat: p.getLat() })
          }
        } catch (err) { /* ignore */ }
      }
    }

    contentEl.addEventListener('pointerdown', onDown)
    contentEl.addEventListener('pointermove', onMove)
    contentEl.addEventListener('pointerup', onUp)
    contentEl.addEventListener('pointercancel', onUp)
  }

  // ===== 构建聚合数据并刷新 =====
  function _refreshCluster() {
    if (!_cluster) return
    const photoMap = _latestPhotoMap()
    const points = spots.value.map(s => ({
      lnglat: [s.lng, s.lat],
      spotId: s.id,
      name: s.name,
      // 有照片的给更高权重，优先作为聚合代表
      weight: photoMap[s.id] ? 2 : 1
    }))
    _cluster.setData(points)
    _lastDetail = _isDetail()
  }

  function renderAllMarkers() {
    _refreshCluster()
  }

  function updateMarker() {
    _refreshCluster()
  }

  // ===== CRUD =====
  async function loadSpots() {
    const data = await fetchSpots()
    spots.value = data || []
  }

  async function create(spotData) {
    const newSpot = await apiCreateSpot(spotData)
    spots.value.unshift(newSpot)
    _refreshCluster()
    return newSpot
  }

  async function update(spotData) {
    const updated = await apiUpdateSpot(spotData)
    const idx = spots.value.findIndex(s => s.id === updated.id)
    if (idx >= 0) spots.value[idx] = updated
    _refreshCluster()
    return updated
  }

  async function remove(id) {
    await apiDeleteSpot(id)
    spots.value = spots.value.filter(s => s.id !== id)
    _refreshCluster()
  }

  // ===== 创建点位模式（不受聚合影响，独立 marker） =====
  function startCreateMode() {
    isCreating.value = true
    if (_map) _map.getContainer().style.cursor = 'crosshair'
  }

  function cancelCreateMode() {
    isCreating.value = false
    if (_map) _map.getContainer().style.cursor = ''
    if (createMarker.value) {
      _map.remove(createMarker.value)
      createMarker.value = null
    }
  }

  function placeCreateMarker(lnglat) {
    if (createMarker.value) _map.remove(createMarker.value)
    const m = new _amap.Marker({
      position: [lnglat.lng, lnglat.lat],
      draggable: true,
      animation: 'AMAP_ANIMATION_DROP'
    })
    _map.add(m)
    createMarker.value = m
    return { lng: lnglat.lng, lat: lnglat.lat }
  }

  // ===== 上传选点模式：蓝色可拖动标记 =====
  function _normLngLat(ll) {
    if (!ll) return null
    return {
      lng: typeof ll.getLng === 'function' ? ll.getLng() : ll.lng,
      lat: typeof ll.getLat === 'function' ? ll.getLat() : ll.lat
    }
  }

  function startPickMode(lnglat) {
    isPicking.value = true
    isCreating.value = false
    cancelCreateMode()
    if (_map) _map.getContainer().style.cursor = ''
    _ensurePickMarker(lnglat)
  }

  function _ensurePickMarker(lnglat) {
    if (!_map || !_amap) return
    const ll = _normLngLat(lnglat)
    if (!ll || ll.lng == null || ll.lat == null) return
    if (_pickMarker) {
      _pickMarker.setPosition([ll.lng, ll.lat])
      return
    }
    const m = new _amap.Marker({
      position: [ll.lng, ll.lat],
      content: createPickMarkerElement(),
      anchor: 'center',
      offset: new _amap.Pixel(0, 0),
      draggable: true,
      cursor: 'grab',
      zIndex: 220
    })
    m.on('dragend', () => {
      try {
        const p = m.getPosition()
        if (p && _handlers.onPickMove) _handlers.onPickMove({ lng: p.getLng(), lat: p.getLat() })
      } catch (err) { /* ignore */ }
    })
    _map.add(m)
    _pickMarker = m
  }

  /** 点击地图 / 长按地图：把蓝色标记移动到指定位置 */
  function movePickMarker(lnglat) {
    const ll = _normLngLat(lnglat)
    if (!ll || ll.lng == null || ll.lat == null) return
    _ensurePickMarker(ll)
    if (_pickMarker) {
      _pickMarker.setPosition([ll.lng, ll.lat])
      if (_handlers.onPickMove) _handlers.onPickMove(ll)
    }
  }

  function stopPickMode() {
    isPicking.value = false
    if (_pickMarker && _map) {
      _map.remove(_pickMarker)
      _pickMarker = null
    }
  }

  return {
    spots, currentSpot, isCreating, isPicking, createMarker,
    bind, loadSpots, renderAllMarkers,
    create, update, remove,
    updateMarker,
    startCreateMode, cancelCreateMode, placeCreateMarker,
    startPickMode, movePickMarker, stopPickMode
  }
}
