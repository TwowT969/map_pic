import { ref } from 'vue'
import { fetchSpots, createSpot as apiCreateSpot, updateSpot as apiUpdateSpot, deleteSpot as apiDeleteSpot } from '../api/index.js'
import { createMarkerElement, createSimpleMarkerElement } from '../utils/marker.js'

// 缩放分级阈值：地图缩放 >= DETAIL_ZOOM 级才渲染照片缩略图标记；
// 更小时只渲染"📍图标 + 照片数"简化标记（不含 <img>，零图片请求，防止大量加载卡顿）
const DETAIL_ZOOM = 14

export function useSpots() {
  const spots = ref([])
  const currentSpot = ref(null)
  const isCreating = ref(false)
  const createMarker = ref(null)

  let _map = null
  let _amap = null
  let _latestPhotoMap = () => ({})
  let _photoCountMap = () => ({})
  let _cluster = null
  let _lastDetail = null

  async function bind(mapInstance, amapInstance, latestPhotoMapGetter, photoCountMapGetter) {
    _map = mapInstance
    _amap = amapInstance
    _latestPhotoMap = latestPhotoMapGetter
    _photoCountMap = photoCountMapGetter || (() => ({}))

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

    // 点击 → 选中点位
    context.marker.on('click', () => {
      if (!spot) return
      currentSpot.value = spot
      isCreating.value = false
      if (createMarker.value) {
        _map.remove(createMarker.value)
        createMarker.value = null
      }
    })
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

  // ===== 创建模式（不受聚合影响，独立 marker） =====
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

  return {
    spots, currentSpot, isCreating, createMarker,
    bind, loadSpots, renderAllMarkers,
    create, update, remove,
    updateMarker,
    startCreateMode, cancelCreateMode, placeCreateMarker
  }
}
