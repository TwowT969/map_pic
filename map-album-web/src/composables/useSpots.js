import { ref } from 'vue'
import { fetchSpots, createSpot as apiCreateSpot, updateSpot as apiUpdateSpot, deleteSpot as apiDeleteSpot } from '../api/index.js'
import { createMarkerElement, createSimpleMarkerElement, createPickMarkerElement } from '../utils/marker.js'
import { track } from '../utils/applog.js'

// 比例尺阈值（需求3）：比例尺 < 10km 时点位标记展示图片拼贴（最多 3 张、本地加载）；
// 更粗的比例尺只渲染"📍图标 + 照片数"简化标记（不含 <img>，零图片请求，防止大量加载卡顿）。
// 实现按 web 墨卡托估算每像素米数 × 100px 作为"比例尺距离"，
// 并加滞回区间，避免在 10km 临界值附近来回缩放时反复重渲染。
const SCALE_SHOW_M = 10000    // 显示图片：比例尺 < 10km
const SCALE_HYSTER_M = 11500  // 滞回：已显示时超过该值才切回简化标记
// 长按触发时长（毫秒）：长按标记 → 弹出操作菜单（编辑 / 拖动 / 备注）
const LONG_PRESS_MS = 450
// 聚合点点击节流（毫秒）：防止连续点按聚簇在缩放动画期间重复触发导致卡顿
const CLUSTER_CLICK_THROTTLE_MS = 600
// 聚合展开前的重渲染防抖（毫秒）：等缩放动画结束后再统一刷新标记形态，
// 避免在 zoomend 回调里同步 setData 重入聚合组件（Android WebView 卡死根因）
const REFRESH_DEBOUNCE_MS = 120

export function useSpots() {
  const spots = ref([])
  const currentSpot = ref(null)
  const isCreating = ref(false)
  const isPicking = ref(false)      // 上传选点模式
  const createMarker = ref(null)

  let _map = null
  let _amap = null
  let _photoListMap = () => ({})    // spotId → 照片数组（thumbSrc 已解析为本地可显示源）
  let _handlers = {}                // { onMarkerClick, onMarkerPhotoClick, onMarkerLongPress, onMarkerMoved, onPickMove }
  let _cluster = null
  let _photosOn = false             // 当前标记形态是否为"展示图片拼贴"
  let _pickMarker = null
  let _justDragged = false          // 长按/拖动刚结束的短暂时间片内屏蔽 click
  let _lastClick = { id: 0, ts: 0 } // 点击去重（集群 click 与标记 click 可能同时触发）
  let _lastClusterClick = 0         // 聚合点点击节流
  let _refreshTimer = null          // 防抖合并的刷新定时器
  let _markerBySpot = new Map()     // spotId → 当前渲染的 AMap.Marker（拖动用）

  async function bind(mapInstance, amapInstance, latestPhotoMapGetter, photoListMapGetter, handlers = {}) {
    _map = mapInstance
    _amap = amapInstance
    _photoListMap = photoListMapGetter || (() => ({}))
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

    // ===== 聚合点（汇聚图标）点击逻辑 =====
    // - 单点：直接打开该点位详情
    // - 多点：按成员包围盒展开（setBounds），展开后所有成员可见；
    //   成员几乎重合或已接近最大缩放、无法再展开时 → 打开簇内最近更新的点位详情
    // - 600ms 节流：缩放动画期间忽略重复点按
    _cluster.on('click', (item) => {
      const now = Date.now()
      if (now - _lastClusterClick < CLUSTER_CLICK_THROTTLE_MS) return
      _lastClusterClick = now

      const members = item.clusterData || []
      if (members.length <= 1) {
        const sid = members[0] && members[0].spotId
        const s = spots.value.find(x => x.id === sid)
        if (s) _dispatchMarkerClick(s)
        return
      }
      _expandCluster(members)
    })

    // 缩放跨过比例尺阈值 → 延迟合并刷新标记形态（切换 简化图标 / 图片拼贴）
    _map.on('zoomend', _onZoomEnd)
    _photosOn = _computeShow()
  }

  // ===== 比例尺估算：web 墨卡托 每像素米数 × 100px =====
  function _metersPerPixel() {
    try {
      const z = _map.getZoom()
      const c = _map.getCenter()
      const lat = typeof c.getLat === 'function' ? c.getLat() : Number(c.lat)
      return 156543.03392804097 * Math.cos((lat * Math.PI) / 180) / Math.pow(2, z)
    } catch (e) { return 1e9 }
  }

  function _scaleMeters() {
    return _metersPerPixel() * 100
  }

  /** 是否展示图片（带滞回：进入 <10km，退出 >11.5km） */
  function _computeShow() {
    if (!_map) return false
    const s = _scaleMeters()
    return _photosOn ? s < SCALE_HYSTER_M : s < SCALE_SHOW_M
  }

  /** 跨阈值后延迟刷新：等缩放动画收尾，合并多次触发，避免同步 setData 卡死 */
  function _onZoomEnd() {
    const should = _computeShow()
    if (should !== _photosOn) {
      _photosOn = should
      _scheduleRefresh()
    }
  }

  function _scheduleRefresh() {
    if (_refreshTimer) return // 已有排程，合并
    _refreshTimer = setTimeout(() => {
      _refreshTimer = null
      _refreshCluster()
    }, REFRESH_DEBOUNCE_MS)
  }

  /** 标记点击统一入口（去重 + 长按后短暂屏蔽） */
  function _dispatchMarkerClick(spot) {
    if (_justDragged) return
    const now = Date.now()
    if (spot && spot.id === _lastClick.id && now - _lastClick.ts < 400) return
    _lastClick = { id: spot ? spot.id : 0, ts: now }
    if (spot && _handlers.onMarkerClick) _handlers.onMarkerClick(spot)
  }

  // ===== 聚合展开 =====

  /**
   * 展开聚合：按成员包围盒适配视野（外扩 30% 保证不贴边）。
   * 成员几乎重合（<100m）或已在高倍缩放（>=17 级）时无法再展开，
   * 直接打开簇内最近更新的点位详情，避免"点了没反应/反复缩放"。
   */
  function _expandCluster(members) {
    try {
      let minLng = Infinity, maxLng = -Infinity, minLat = Infinity, maxLat = -Infinity
      members.forEach(d => {
        const lng = d.lnglat[0]
        const lat = d.lnglat[1]
        if (lng < minLng) minLng = lng
        if (lng > maxLng) maxLng = lng
        if (lat < minLat) minLat = lat
        if (lat > maxLat) maxLat = lat
      })
      const spanLng = maxLng - minLng
      const spanLat = maxLat - minLat

      if ((spanLng < 0.001 && spanLat < 0.001) || _map.getZoom() >= 17) {
        const s = _pickSpotInCluster(members)
        if (s) {
          _dispatchMarkerClick(s)
          track('cluster_open_latest', String(members.length))
        }
        return
      }

      const padLng = Math.max(spanLng * 0.3, 0.0005)
      const padLat = Math.max(spanLat * 0.3, 0.0004)
      const bounds = new _amap.Bounds(
        [minLng - padLng, minLat - padLat],
        [maxLng + padLng, maxLat + padLat]
      )
      _map.setBounds(bounds)
      track('cluster_expand', String(members.length))
    } catch (e) {
      // 兜底：一次性放大 2 级
      try {
        const c = members[0].lnglat
        _map.setZoomAndCenter(Math.min(_map.getZoom() + 2, 18), [c[0], c[1]])
      } catch (e2) { /* ignore */ }
    }
  }

  /** 簇内挑"最近更新"的点位（无时间则取第一个可找到的） */
  function _pickSpotInCluster(members) {
    let best = null
    members.forEach(d => {
      const s = spots.value.find(x => x.id === d.spotId)
      if (!s) return
      if (!best || _timeOf(s) > _timeOf(best)) best = s
    })
    return best || spots.value.find(x => x.id === (members[0] && members[0].spotId)) || null
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
  function _timeOf(p) {
    const t = (p && (p.shotTime || p.createTime)) || ''
    return t ? (new Date(t).getTime() || 0) : 0
  }

  function _renderMarker(context) {
    const d = context.data[0] // 原始数据
    const spot = spots.value.find(s => s.id === d.spotId)
    const name = d.name || (spot && spot.name) || '未命名'

    // 记录 spotId → 当前标记实例（长按菜单"拖动"用）
    _markerBySpot.set(d.spotId, context.marker)

    let content
    if (_photosOn) {
      // 比例尺 < 10km：最多 3 张图片拼贴（本地缩略图，按拍摄时间新→旧取前 3），
      // 点击缩略图 → 全屏浏览（按距离临时链表排序）
      const list = [...(_photoListMap()[d.spotId] || [])]
        .sort((a, b) => _timeOf(b) - _timeOf(a))
        .slice(0, 3)
      content = createMarkerElement(
        { name },
        list,
        (photo) => {
          const s = spots.value.find(x => x.id === d.spotId)
          if (s && _handlers.onMarkerPhotoClick) _handlers.onMarkerPhotoClick(s, photo)
        }
      )
    } else {
      // 粗比例尺：纯图标 + 照片数，不加载任何图片
      const count = (_photoListMap()[d.spotId] || []).length
      content = createSimpleMarkerElement({ name }, count)
    }
    context.marker.setContent(content)
    context.marker.setAnchor('bottom-center')
    // 针尖在 wrapper 底部，offset (0,0) 即可精准扎在坐标上
    context.marker.setOffset(new _amap.Pixel(0, 0))

    // 长按标记 → 操作菜单（编辑 / 拖动 / 备注）
    _attachLongPressMenu(content, spot)

    // 点击 → 打开点位详情弹窗（图片列表）。
    // 先 off('click') 清旧监听：MarkerCluster 会复用 marker 实例反复触发 renderMarker，
    // 不清理会累积大量带过期闭包的监听器。
    try { context.marker.off && context.marker.off('click') } catch (e) { /* ignore */ }
    context.marker.on('click', () => {
      if (_justDragged) return
      const s = spots.value.find(x => x.id === d.spotId)
      _dispatchMarkerClick(s)
    })
  }

  /**
   * 长按标记（450ms）→ 震动反馈 → 弹出操作菜单（onMarkerLongPress）。
   * 未满时长抬起 = 普通点击（交给 marker click）；手指明显移动 = 拖动地图（取消）。
   */
  function _attachLongPressMenu(contentEl, spot) {
    if (!contentEl || contentEl.__lpMenuAttached) return
    contentEl.__lpMenuAttached = true

    let pressTimer = null
    let startX = 0
    let startY = 0

    const cancel = () => {
      if (pressTimer) { clearTimeout(pressTimer); pressTimer = null }
    }

    const onDown = (e) => {
      if (e.button != null && e.button !== 0) return // 仅左键/触摸
      if (isPicking.value || isCreating.value) return  // 选点/创建模式不响应
      // 缩略图上不触发长按菜单（点击缩略图 = 看大图）
      if (e.target && e.target.closest && e.target.closest('.marker-thumb')) return
      startX = e.clientX
      startY = e.clientY
      pressTimer = setTimeout(() => {
        pressTimer = null
        try { navigator.vibrate && navigator.vibrate(30) } catch (err) { /* ignore */ }
        // 抑制随后的 click（长按不打开详情弹窗）
        _justDragged = true
        setTimeout(() => { _justDragged = false }, 500)
        const s = spots.value.find(x => x.id === (spot && spot.id)) || spot
        if (s && _handlers.onMarkerLongPress) _handlers.onMarkerLongPress(s)
      }, LONG_PRESS_MS)
    }

    const onMove = (e) => {
      // 手指明显移动（横向/纵向任一）→ 是拖动地图，取消长按
      if (pressTimer && (Math.abs(e.clientX - startX) > 10 || Math.abs(e.clientY - startY) > 10)) cancel()
    }

    contentEl.addEventListener('pointerdown', onDown)
    contentEl.addEventListener('pointermove', onMove)
    contentEl.addEventListener('pointerup', cancel)
    contentEl.addEventListener('pointercancel', cancel)
  }

  /**
   * 启用某个点位的原生拖拽（长按菜单"拖动点位"动作）。
   * 拖动结束自动关闭拖拽并回调 onMarkerMoved(spot, {lng,lat}) 保存新位置。
   * @returns {boolean} 是否成功进入拖动态
   */
  function startSpotDrag(spotId) {
    const m = _markerBySpot.get(spotId)
    if (!m || !_map) return false
    try {
      m.setDraggable(true)
      const onDragEnd = () => {
        try { m.off('dragend', onDragEnd) } catch (e) { /* ignore */ }
        if (m.__dragTimeout) { clearTimeout(m.__dragTimeout); m.__dragTimeout = null }
        try { m.setDraggable(false) } catch (e) { /* ignore */ }
        _justDragged = true
        setTimeout(() => { _justDragged = false }, 450)
        try {
          const p = m.getPosition()
          const s = spots.value.find(x => x.id === spotId)
          if (p && s && _handlers.onMarkerMoved) {
            _handlers.onMarkerMoved(s, { lng: p.getLng(), lat: p.getLat() })
          }
        } catch (e) { /* ignore */ }
      }
      m.on('dragend', onDragEnd)
      // 15s 内未拖动则自动退出可拖动态：避免之后误拖标记静默改位
      if (m.__dragTimeout) clearTimeout(m.__dragTimeout)
      m.__dragTimeout = setTimeout(() => {
        m.__dragTimeout = null
        try { m.off('dragend', onDragEnd) } catch (e) { /* ignore */ }
        try { m.setDraggable(false) } catch (e) { /* ignore */ }
      }, 15000)
      return true
    } catch (e) {
      return false
    }
  }

  // ===== 构建聚合数据并刷新 =====
  function _refreshCluster() {
    if (!_cluster || !_map) return
    // 先清空标记索引：renderMarker 会随 setData 重新填充，
    // 避免已删除点位的旧 marker 残留（泄漏）或复用失效实例
    _markerBySpot.clear()
    const photoMap = _photoListMap()
    const points = spots.value.map(s => ({
      lnglat: [s.lng, s.lat],
      spotId: s.id,
      name: s.name,
      // 有照片的给更高权重，优先作为聚合代表
      weight: photoMap[s.id] && photoMap[s.id].length > 0 ? 2 : 1
    }))
    _cluster.setData(points)
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
    updateMarker, startSpotDrag,
    startCreateMode, cancelCreateMode, placeCreateMarker,
    startPickMode, movePickMarker, stopPickMode
  }
}
