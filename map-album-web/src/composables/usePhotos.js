/**
 * 照片状态与操作（v1.2.0 本地化存储）：
 * 图片文件只存设备本地（photoStore），远程仅登记元数据；
 * 旧版本照片（有 url/thumbUrl）继续走远程地址展示。
 */
import { computed, ref } from 'vue'
import {
  fetchAllPhotos, fetchPhotosBySpot,
  createPhotoMeta, deletePhoto as apiDelete,
  updatePhotoDescription as apiUpdateDesc, fileUrl
} from '../api/index.js'
import { compressImage } from '../utils/capacitor.js'
import { saveLocalPhoto, deleteLocalPhoto, photoLocalSrc } from '../utils/photoStore.js'
import { addPending } from '../utils/pendingQueue.js'

export function photoTimeOf(p) {
  const t = p.shotTime || p.createTime
  return t ? (new Date(t).getTime() || 0) : 0
}

/** 给照片对象解析显示资源：本地文件优先，旧数据回退远程 URL */
async function resolvePhotoSrcs(list) {
  for (const p of (list || [])) {
    if (p.thumbSrc || p.src) continue
    if (p.localPath || p.localThumbPath) {
      try {
        p.thumbSrc = await photoLocalSrc(p.localThumbPath || p.localPath)
        p.src = await photoLocalSrc(p.localPath || p.localThumbPath)
      } catch (e) { /* 本地读取失败 → 走远程兜底 */ }
    }
    if (!p.thumbSrc && (p.thumbUrl || p.url)) {
      p.thumbSrc = fileUrl(p.thumbUrl || p.url)
      p.src = fileUrl(p.url || p.thumbUrl || '')
    }
  }
}

export function usePhotos() {
  const photosBySpot = ref({})
  const allPhotos = ref([])

  const latestPhotoMap = computed(() => {
    const map = {}
    allPhotos.value.forEach(p => {
      const cur = map[p.spotId]
      if (!cur || photoTimeOf(p) > photoTimeOf(cur)) map[p.spotId] = p
    })
    return map
  })

  async function loadAllPhotos() {
    const data = await fetchAllPhotos()
    await resolvePhotoSrcs(data || [])
    allPhotos.value = data || []
    const map = {}
    for (const p of allPhotos.value) {
      (map[p.spotId] = map[p.spotId] || []).push(p)
    }
    photosBySpot.value = map
  }

  async function loadPhotos(spotId) {
    const data = await fetchPhotosBySpot(spotId)
    await resolvePhotoSrcs(data || [])
    photosBySpot.value = { ...photosBySpot.value, [spotId]: data || [] }
    const known = new Set(allPhotos.value.map(p => p.id))
    allPhotos.value = [...allPhotos.value, ...(data || []).filter(p => !known.has(p.id))]
  }

  /**
   * 上传（本地化）：压缩原图 + 生成缩略图 → 存设备本地 → 远程登记元数据。
   * 元数据同步失败时照片仍在本地，并进入待同步队列（联网后自动补登）。
   */
  async function upload(files, spot) {
    const isObj = spot && typeof spot === 'object'
    const spotId = isObj ? spot.id : spot
    const results = []
    for (const file of files) {
      if (!file || !file.type || !file.type.startsWith('image/')) continue
      const full = await compressImage(file, 2048, 0.85)
      const thumb = await compressImage(file, 256, 0.72)
      const loc = await saveLocalPhoto(full, thumb)
      try {
        const photo = await createPhotoMeta({
          spotId,
          description: '',
          shotTime: null,
          device: null,
          localPath: loc.localPath,
          localThumbPath: loc.localThumbPath
        })
        results.push(photo)
      } catch (e) {
        await addPending({
          id: 'q_' + Date.now() + '_' + Math.random().toString(36).slice(2, 6),
          meta: {
            lng: isObj ? spot.lng : null,
            lat: isObj ? spot.lat : null,
            address: isObj ? (spot.address || '') : '',
            province: isObj ? (spot.province || '') : '',
            city: isObj ? (spot.city || '') : '',
            district: isObj ? (spot.district || '') : '',
            desc: '',
            shotTime: '',
            device: '',
            localPath: loc.localPath,
            localThumbPath: loc.localThumbPath
          }
        })
        throw new Error('照片已存本地，网络恢复后自动同步记录')
      }
    }
    if (results.length > 0) {
      await loadPhotos(spotId)
    }
    return results
  }

  async function remove(photoId, spotId) {
    const photo = allPhotos.value.find(p => p.id === photoId) ||
      (photosBySpot.value[spotId] || []).find(p => p.id === photoId)
    await apiDelete(photoId)
    if (photo) {
      // 同步清理设备本地图片文件（尽力而为）
      deleteLocalPhoto(photo).catch(() => {})
    }
    if (photosBySpot.value[spotId]) {
      photosBySpot.value = {
        ...photosBySpot.value,
        [spotId]: photosBySpot.value[spotId].filter(p => p.id !== photoId)
      }
    }
    allPhotos.value = allPhotos.value.filter(p => p.id !== photoId)
  }

  async function updateDescription(photoId, description) {
    await apiUpdateDesc(photoId, description)
    const patch = list => list.map(p => (p.id === photoId ? { ...p, description } : p))
    const map = { ...photosBySpot.value }
    Object.keys(map).forEach(k => { map[k] = patch(map[k]) })
    photosBySpot.value = map
    allPhotos.value = patch(allPhotos.value)
  }

  function getLatestPhoto(spotId) {
    return latestPhotoMap.value[spotId] || null
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
