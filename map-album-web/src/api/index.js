// API 基础配置
// 浏览器开发：未设 VITE_API_BASE 时回退 /api，走 vite.config.js 的 proxy -> localhost:48081
// APK 打包：构建时注入 VITE_API_BASE=http://<服务器IP>/api（见 .env.production.example）
export const API_BASE = import.meta.env.VITE_API_BASE || '/api'

/** 把后端相对资源路径(/uploads/...)转为可直接访问 URL；WebView(APK)内必须绝对地址 */
export function fileUrl(url) {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return API_BASE + url
}

import { hasCapacitor } from '../utils/capacitor.js'

// ===== 登录态管理 =====
// 网页端（H5 测试）：设备级 dev 自动登录，直接放行
// 原生端（APK）：必须显式登录/注册（账号即身份，首次自动注册）
const TOKEN_KEY = 'map_album_token'
const SSO_KEY = 'map_album_sso_user_id'
const NAME_KEY = 'map_album_nickname'

let currentUserId = null
let currentNickname = localStorage.getItem(NAME_KEY) || null
let loginPromise = null
let needLoginListener = null

function getToken() { return localStorage.getItem(TOKEN_KEY) }

function isNative() { return hasCapacitor() }

/** 本地是否已有登录令牌（同步判断，用于决定是否显示登录门） */
export function hasToken() { return !!getToken() }

/** 注册"需要登录"回调（原生端令牌缺失/过期时触发，用于弹出登录门） */
export function setNeedLoginListener(cb) { needLoginListener = cb }

function needLoginError() {
  const e = new Error('请先登录')
  e.needLogin = true
  return e
}

function notifyNeedLogin() {
  currentUserId = null
  loginPromise = null
  if (isNative() && needLoginListener) needLoginListener()
}

/** 网页端 dev 登录：设备侧生成固定 ssoUserId，首次自动注册 */
async function devLogin() {
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

/**
 * 账号登录/注册（原生端登录门调用）：账号 + 昵称，新账号自动注册（测试阶段 dev 通道）。
 * ssoUserId 加 app- 前缀，与网页端随机 dev- 账号隔离。
 */
export async function loginAccount(account, nickname) {
  const acc = (account || '').trim()
  if (!acc) throw new Error('请输入账号')
  if (!/^[\w@.-]{2,32}$/.test(acc)) throw new Error('账号需为 2-32 位字母/数字/下划线')
  const ssoUserId = 'app-' + acc.toLowerCase()
  const name = (nickname || '').trim() || acc
  const data = await rawPost('/user/login', {
    ssoProvider: 'dev',
    ssoUserId: ssoUserId,
    nickname: name
  })
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(SSO_KEY, ssoUserId)
  localStorage.setItem(NAME_KEY, name)
  currentUserId = data.user.id
  currentNickname = name
  return data.user
}

/** 当前昵称（原生端登录门显示用） */
export function getCurrentNickname() { return currentNickname }

/**
 * 确保已登录（幂等）。
 * 网页端：自动 dev 登录；原生端：必须先经登录门 loginAccount()，否则抛 needLogin 错误。
 */
export function ensureLogin() {
  if (currentUserId) return Promise.resolve(currentUserId)
  if (isNative()) {
    notifyNeedLogin()
    return Promise.reject(needLoginError())
  }
  if (!loginPromise) loginPromise = devLogin().catch((e) => { loginPromise = null; throw e })
  return loginPromise
}

/** 当前登录用户 ID（未登录返回 null） */
export function getCurrentUserId() { return currentUserId }

/** 登出：清除本地会话（保留账号 ID，下次登录同账号） */
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
  // 无令牌：网页端自动登录直通；原生端弹登录门并中断请求
  if (!getToken()) {
    if (isNative()) {
      notifyNeedLogin()
      throw needLoginError()
    }
    await ensureLogin()
  }
  const headers = { ...(options.headers || {}) }
  const token = getToken()
  if (token) headers['Authorization'] = 'Bearer ' + token
  const res = await fetch(API_BASE + url, { ...options, headers })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  const data = await res.json()
  if (data.code === 401) {
    // 会话过期：清令牌；网页端下次请求重新自动登录，原生端弹登录门
    localStorage.removeItem(TOKEN_KEY)
    currentUserId = null
    loginPromise = null
    if (isNative() && needLoginListener) needLoginListener()
    const e = new Error(data.msg || '登录已过期')
    e.needLogin = true
    throw e
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
