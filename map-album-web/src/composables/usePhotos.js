import { ref, computed } from 'vue'
import { fetchAllPhotos, fetchPhotosBySpot, uploadPhoto as apiUpload, deletePhoto as apiDelete } from '../api/index.js'

export function usePhotos() {
  const photosBySpot = ref({})  // { spotId: Photo[] }

  const latestPhotoMap = computed(() => {
    const map = {}
    Object.entries(photosBySpot.value).forEach(([spotId, list]) => {
      if (list.length > 0) {
        map[spotId] = list.reduce((a, b) =>
          new Date(a.createTime) > new Date(b.createTime) ? a : b
        )
      }
    })
    return map
  })

  function getLatestPhoto(spotId) {
    return latestPhotoMap.value[spotId] || null
  }

  function sortByTime(photos) {
    return (photos || []).sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
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
    return data
  }

  async function loadPhotos(spotId) {
    const data = await fetchPhotosBySpot(spotId)
    photosBySpot.value = {
      ...photosBySpot.value,
      [spotId]: sortByTime(data)
    }
    return data
  }

  async function upload(files, spotId) {
    const results = []
    for (const file of files) {
      if (!file.type.startsWith('image/')) continue
      const photo = await apiUpload(spotId, file, '')
      results.push(photo)
    }
    // 重新加载该点位照片
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
  }

  function photoUrl(url) {
    if (!url) return ''
    if (url.startsWith('http')) return url
    // 后端 context-path=/api，资源映射 /uploads/** 实际服务于 /api/uploads/**
    // 加 /api 前缀走 Vite proxy -> http://localhost:48081/api/uploads/...
    return '/api' + url
  }

  return {
    photosBySpot, latestPhotoMap,
    loadAllPhotos, loadPhotos,
    upload, remove,
    getLatestPhoto, photoUrl
  }
}
