import { ref } from 'vue'
import { fetchSpots, createSpot as apiCreateSpot, updateSpot as apiUpdateSpot, deleteSpot as apiDeleteSpot } from '../api/index.js'
import { createMarkerElement } from '../utils/marker.js'

export function useSpots() {
  const spots = ref([])
  const currentSpot = ref(null)
  const isCreating = ref(false)
  const createMarker = ref(null)

  // 内部维护 markers Map（非响应式，AMap 实例）
  const _markers = new Map()
  let _map = null
  let _amap = null
  let _latestPhotoMap = () => ({})

  function bind(mapInstance, amapInstance, latestPhotoMapGetter) {
    _map = mapInstance
    _amap = amapInstance
    _latestPhotoMap = latestPhotoMapGetter
  }

  function clearAllMarkers() {
    _markers.forEach(m => _map.remove(m))
    _markers.clear()
  }

  function addMarker(spot) {
    const latestPhoto = _latestPhotoMap()[spot.id] || null
    const content = createMarkerElement(spot, latestPhoto)
    const marker = new _amap.Marker({
      position: [spot.lng, spot.lat],
      content,
      anchor: 'bottom-center',
      offset: new _amap.Pixel(0, -8),
      zIndex: latestPhoto ? 110 : 100
    })
    // 官方 v2.0: map.add(marker) 替代 marker.setMap(map)
    _map.add(marker)
    marker.on('click', () => {
      currentSpot.value = spot
      isCreating.value = false
      if (createMarker.value) {
        _map.remove(createMarker.value)
        createMarker.value = null
      }
    })
    _markers.set(spot.id, marker)
  }

  function updateMarker(spot) {
    const old = _markers.get(spot.id)
    if (old) _map.remove(old)
    _markers.delete(spot.id)
    addMarker(spot)
  }

  function renderAllMarkers() {
    clearAllMarkers()
    spots.value.forEach(s => addMarker(s))
  }

  async function loadSpots() {
    const data = await fetchSpots()
    spots.value = data || []
  }

  async function create(spotData) {
    const newSpot = await apiCreateSpot(spotData)
    spots.value.unshift(newSpot)
    addMarker(newSpot)
    return newSpot
  }

  async function update(spotData) {
    const updated = await apiUpdateSpot(spotData)
    const idx = spots.value.findIndex(s => s.id === updated.id)
    if (idx >= 0) spots.value[idx] = updated
    updateMarker(updated)
    return updated
  }

  async function remove(id) {
    await apiDeleteSpot(id)
    const old = _markers.get(id)
    if (old) { _map.remove(old); _markers.delete(id) }
    spots.value = spots.value.filter(s => s.id !== id)
  }

  // 创建模式
  function startCreateMode() {
    isCreating.value = true
    if (_map) {
      _map.getContainer().style.cursor = 'crosshair'
    }
  }

  function cancelCreateMode() {
    isCreating.value = false
    if (_map) {
      _map.getContainer().style.cursor = ''
    }
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
