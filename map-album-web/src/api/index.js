// API 基础配置
// 浏览器开发：未设 VITE_API_BASE 时回退 /api，走 vite.config.js 的 proxy -> localhost:48081
// APK 打包：构建时注入 VITE_API_BASE=http://<PC局域网IP>:48081/api（见 .env.production.example）
const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const DEFAULT_USER_ID = 2

async function request(url, options = {}) {
  const res = await fetch(API_BASE + url, options)
  if (!res.ok) throw new Error('HTTP ' + res.status)
  const data = await res.json()
  if (data.code !== 0) throw new Error(data.msg || 'Server error')
  return data.data
}

export function apiGet(url) {
  return request(url)
}

export function apiPost(url, body) {
  return request(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
}

export function apiPut(url, body) {
  return request(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
}

export function apiDelete(url) {
  return request(url, { method: 'DELETE' })
}

export function apiUpload(spotId, file, description = '') {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('spotId', spotId)
  fd.append('userId', DEFAULT_USER_ID)
  if (description) fd.append('description', description)
  return request('/photos', { method: 'POST', body: fd })
}

// ===== 具体业务 API =====

// 定位 & 搜索
export function fetchAmapConfig() { return apiGet('/location/amap-config') }
export function fetchIpLocation() { return apiGet('/location/ip') }
export function fetchSuggestions(keywords) { return apiGet('/location/suggest?keywords=' + encodeURIComponent(keywords)) }

// 点位
export function fetchSpots(userId = DEFAULT_USER_ID) { return apiGet('/spots/mine?userId=' + userId) }
export function createSpot(body) { return apiPost('/spots', body) }
export function updateSpot(body) { return apiPut('/spots', body) }
export function deleteSpot(id) { return apiDelete('/spots/' + id) }

// 照片
export function fetchAllPhotos(userId = DEFAULT_USER_ID) { return apiGet('/photos/mine?userId=' + userId) }
export function fetchPhotosBySpot(spotId) { return apiGet('/photos/spot/' + spotId) }
export function uploadPhoto(spotId, file, desc) { return apiUpload(spotId, file, desc) }
export function deletePhoto(id) { return apiDelete('/photos/' + id) }
