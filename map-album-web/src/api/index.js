// API 基础配置
// 浏览器开发：未设 VITE_API_BASE 时回退 /api，走 vite.config.js 的 proxy -> localhost:48081
// APK 打包：构建时注入 VITE_API_BASE=http://<PC局域网IP>:48081/api（见 .env.production.example）
const API_BASE = import.meta.env.VITE_API_BASE || '/api'

// ===== 登录态管理（测试阶段：设备级 dev 登录，SSO 待接入） =====
const TOKEN_KEY = 'map_album_token'
const SSO_KEY = 'map_album_sso_user_id'

let currentUserId = null
let loginPromise = null

function getToken() { return localStorage.getItem(TOKEN_KEY) }

/**
 * dev 登录：设备侧生成固定 ssoUserId，首次登录自动注册；
 * token 与 userId 持久化到 localStorage，后续请求自动携带。
 */
async function login() {
  let ssoUserId = localStorage.getItem(SSO_KEY)
  if (!ssoUserId) {
    ssoUserId = 'dev-' + Math.random().toString(36).slice(2, 10)
    localStorage.setItem(SSO_KEY, ssoUserId)
  }
  const data = await rawPost('/user/login', {
    ssoProvider: 'dev',
    ssoUserId: ssoUserId,
    nickname: '测试用户'
  })
  localStorage.setItem(TOKEN_KEY, data.token)
  currentUserId = data.user.id
  return currentUserId
}

/** 确保已登录（幂等；失败抛错由调用方提示） */
export function ensureLogin() {
  if (currentUserId) return Promise.resolve(currentUserId)
  if (!loginPromise) loginPromise = login().catch((e) => { loginPromise = null; throw e })
  return loginPromise
}

/** 当前登录用户 ID（未登录返回 null） */
export function getCurrentUserId() { return currentUserId }

/** 登出：清除本地会话（下次请求重新 dev 登录） */
export function logout() {
  localStorage.removeItem(TOKEN_KEY)
  currentUserId = null
  loginPromise = null
}

async function rawPost(url, body) {
  const res = await fetch(API_BASE + url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  const data = await res.json()
  if (data.code !== 0) throw new Error(data.msg || 'Server error')
  return data.data
}

async function request(url, options = {}) {
  // 无令牌时先自动登录（登录接口本身除外，由 rawPost 直接调用）
  if (!getToken()) {
    await ensureLogin()
  }
  const headers = { ...(options.headers || {}) }
  const token = getToken()
  if (token) headers['Authorization'] = 'Bearer ' + token
  const res = await fetch(API_BASE + url, { ...options, headers })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  const data = await res.json()
  if (data.code === 401) {
    // 会话过期：清令牌，下次请求重新登录
    localStorage.removeItem(TOKEN_KEY)
    currentUserId = null
    loginPromise = null
    throw new Error(data.msg || 'Not logged in')
  }
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
  if (description) fd.append('description', description)
  return request('/photos', { method: 'POST', body: fd })
}

// ===== 具体业务 API =====

// 定位 & 搜索
export function fetchAmapConfig() { return apiGet('/location/amap-config') }
export function fetchIpLocation() { return apiGet('/location/ip') }
export function fetchSuggestions(keywords) { return apiGet('/location/suggest?keywords=' + encodeURIComponent(keywords)) }

// 点位（userId 已由后端从登录态解析，参数仅为兼容保留）
export function fetchSpots() { return apiGet('/spots/mine') }
export function createSpot(body) { return apiPost('/spots', body) }
export function updateSpot(body) { return apiPut('/spots', body) }
export function deleteSpot(id) { return apiDelete('/spots/' + id) }

// 照片
export function fetchAllPhotos() { return apiGet('/photos/mine') }
export function fetchPhotosBySpot(spotId) { return apiGet('/photos/spot/' + spotId) }
export function uploadPhoto(spotId, file, desc) { return apiUpload(spotId, file, desc) }
export function deletePhoto(id) { return apiDelete('/photos/' + id) }
