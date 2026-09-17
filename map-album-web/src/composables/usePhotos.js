import { ref, computed } from 'vue'
import {
  fetchAllPhotos, fetchPhotosBySpot,
  uploadPhoto as apiUpload, deletePhoto as apiDelete,
  updatePhotoDescription as apiUpdateDesc, fileUrl
} from '../api/index.js'

/** 照片时间：优先拍摄时间（EXIF），回退上传时间 */
export function photoTimeOf(p) {
  const t = p && (p.shotTime || p.createTime)
  const d = t ? new Date(t) : null
  return d && !isNaN(d.getTime()) ? d.getTime() : 0
}

export function usePhotos() {
  const photosBySpot = ref({})  // { spotId: Photo[] }
  const allPhotos = ref([])     // 扁平全量（按拍摄时间倒序），相册/统计用

  const latestPhotoMap = computed(() => {
    const map = {}
    Object.entries(photosBySpot.value).forEach(([spotId, list]) => {
      if (list.length > 0) {
        map[spotId] = list.reduce((a, b) => (photoTimeOf(a) >= photoTimeOf(b) ? a : b))
      }
    })
    return map
  })

  function getLatestPhoto(spotId) {
    return latestPhotoMap.value[spotId] || null
  }

  function sortByTime(photos) {
    return (photos || []).slice().sort((a, b) => photoTimeOf(b) - photoTimeOf(a))
  }

  async function loadAllPhotos() {
    const data = await fetchAllPhotos()
    const map = {}
    ;(data || []).forEach(p => {
      if (!map[p.spotId]) map[p.spotId] = []
      map[p.spotId].push(p)
    })
    Object.keys(map).forEach(k => { map[k] = sortByTime(map[k]) })
    photosBySpot.value = map
    allPhotos.value = sortByTime(data)
    return data
  }

  async function loadPhotos(spotId) {
    const data = await fetchPhotosBySpot(spotId)
    const sorted = sortByTime(data)
    photosBySpot.value = {
      ...photosBySpot.value,
      [spotId]: sorted
    }
    // 同步扁平列表中该点位的部分
    allPhotos.value = sortByTime(
      allPhotos.value.filter(p => p.spotId !== Number(spotId) && p.spotId !== spotId).concat(sorted)
    )
    return data
  }

  async function upload(files, spotId) {
    const results = []
    for (const file of files) {
      if (!file.type.startsWith('image/')) continue
      const photo = await apiUpload(spotId, file, '')
      results.push(photo)
    }
    await loadPhotos(spotId)
    return results
  }

  async function remove(photoId, spotId) {
    await apiDelete(photoId)
    if (photosBySpot.value[spotId]) {
      photosBySpot.value = {
        ...photosBySpot.value,
        [spotId]: photosBySpot.value[spotId].filter(p => p.id !== photoId)
      }
    }
    allPhotos.value = allPhotos.value.filter(p => p.id !== photoId)
  }

  /** 修改照片备注（PUT /photos） */
  async function updateDescription(photoId, description) {
    await apiUpdateDesc(photoId, description)
    const patch = list => list.map(p => (p.id === photoId ? { ...p, description } : p))
    const map = { ...photosBySpot.value }
    Object.keys(map).forEach(k => { map[k] = patch(map[k]) })
    photosBySpot.value = map
    allPhotos.value = patch(allPhotos.value)
  }

  function photoUrl(url) {
    return fileUrl(url)
  }

  return {
    photosBySpot, allPhotos, latestPhotoMap,
    loadAllPhotos, loadPhotos,
    upload, remove, updateDescription,
    getLatestPhoto, photoUrl
  }
}
