// API 基础配置
// H5：使用构建注入的 VITE_API_BASE（同源 nginx 反代 /api）。
// APK：启动时探测 HTTPS（59.110.53.169 自签证书，端上 network_security_config 信任），
//      探测成功缓存 24h；失败自动回退 HTTP 明文（443 未放行时的兜底），运行中失效会重试降级。
import { hasCapacitor } from '../utils/capacitor.js'

const FALLBACK_BASE = import.meta.env.VITE_API_BASE || '/api'
const HTTPS_BASE = 'https://59.110.53.169/api'
const BASE_CACHE_KEY = 'map_album_api_base'
const BASE_CACHE_TTL = 24 * 60 * 60 * 1000

let API_BASE = FALLBACK_BASE
let probePromise = null

function readCachedBase() {
  try {
    const v = JSON.parse(localStorage.getItem(BASE_CACHE_KEY) || 'null')
    if (v && v.base && Date.now() - v.ts < BASE_CACHE_TTL) return v.base
  } catch (e) { /* ignore */ }
  return null
}

function cacheBase(base) {
  try { localStorage.setItem(BASE_CACHE_KEY, JSON.stringify({ base, ts: Date.now() })) } catch (e) { /* ignore */ }
}

/** 确定 API 基地址（APK 端异步探测 HTTPS，优先加密通道） */
export function resolveApiBase() {
  if (!hasCapacitor()) { API_BASE = FALLBACK_BASE; return Promise.resolve(API_BASE) }
  const cached = readCachedBase()
  if (cached) { API_BASE = cached; return Promise.resolve(API_BASE) }
  if (!probePromise) {
    probePromise = (async () => {
      try {
        const ctrl = new AbortController()
        const timer = setTimeout(() => ctrl.abort(), 3000)
        const res = await fetch(HTTPS_BASE + '/app/version', { signal: ctrl.signal, cache: 'no-store' })
        clearTimeout(timer)
        API_BASE = res.ok ? HTTPS_BASE : FALLBACK_BASE
      } catch (e) {
        API_BASE = FALLBACK_BASE
      }
      cacheBase(API_BASE)
      console.log('[api] API_BASE =', API_BASE)
      return API_BASE
    })()
  }
  return probePromise
}

/** 当前基地址（fileUrl 等 <img src> 场景同步使用；首个请求前已被探测更新） */
export function getApiBase() { return API_BASE }

/** 把后端相对资源路径(/uploads/...)转为可直接访问 URL；WebView(APK)内必须绝对地址 */
export function fileUrl(url) {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return API_BASE + url
}

// ===== 登录态管理 =====
// 全端统一：登录与注册分离 —— 登录仅校验已有账号；新账号走 registerAccount 先注册（≥6 位密码）
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

/** 注册"需要登录"回调（令牌缺失/过期时触发，用于弹出登录门） */
export function setNeedLoginListener(cb) { needLoginListener = cb }

function needLoginError() {
  const e = new Error('请先登录')
  e.needLogin = true
  return e
}

function notifyNeedLogin() {
  currentUserId = null
  loginPromise = null
  if (needLoginListener) needLoginListener()
}

/**
 * 账号登录/注册（登录门调用）：账号 + 密码（≥6 位）+ 昵称。
 * ssoUserId 统一加 app- 前缀；新账号自动注册（密码即初始密码），
 * 老账号若历史无密码，本次所填密码即被设置。同一账号在 App 与网页通用。
 */
export async function loginAccount(account, password) {
  const acc = (account || '').trim()
  if (!acc) throw new Error('请输入账号')
  if (!/^[\w@.-]{2,32}$/.test(acc)) throw new Error('账号需为 2-32 位字母/数字/下划线')
  const pwd = (password || '').trim()
  if (!pwd) throw new Error('请输入密码（至少 6 位）')
  if (pwd.length < 6) throw new Error('密码至少 6 位')
  const ssoUserId = 'app-' + acc.toLowerCase()
  const data = await rawPost('/user/login', {
    ssoProvider: 'dev',
    ssoUserId: ssoUserId,
    password: pwd
  })
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(SSO_KEY, ssoUserId)
  localStorage.setItem(NAME_KEY, data.user.nickname || acc)
  currentUserId = data.user.id
  currentNickname = data.user.nickname || acc
  return data.user
}

/**
 * 注册（与登录分离）：创建账号并直接登录（后端注册成功即签发令牌）。
 */
export async function registerAccount(account, nickname, password) {
  const acc = (account || '').trim()
  if (!acc) throw new Error('请输入账号')
  if (!/^[\w@.-]{2,32}$/.test(acc)) throw new Error('账号需为 2-32 位字母/数字/下划线')
  const pwd = (password || '').trim()
  if (!pwd) throw new Error('请输入密码（至少 6 位）')
  if (pwd.length < 6) throw new Error('密码至少 6 位')
  const name = (nickname || '').trim() || acc
  const data = await rawPost('/user/register', {
    account: acc,
    nickname: name,
    password: pwd
  })
  const ssoUserId = 'app-' + acc.toLowerCase()
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(SSO_KEY, ssoUserId)
  localStorage.setItem(NAME_KEY, data.user.nickname || name)
  currentUserId = data.user.id
  currentNickname = data.user.nickname || name
  return data.user
}

/** 当前昵称 */
export function getCurrentNickname() { return currentNickname }

/**
 * 确保已登录（幂等）。未登录时触发登录门并抛 needLogin 错误，由登录页完成 loginAccount()。
 */
export function ensureLogin() {
  if (currentUserId) return Promise.resolve(currentUserId)
  notifyNeedLogin()
  return Promise.reject(needLoginError())
}

/** 当前登录用户 ID（未登录返回 null） */
export function getCurrentUserId() { return currentUserId }

/** 登出：清除本地会话 */
export function logout() {
  localStorage.removeItem(TOKEN_KEY)
  currentUserId = null
  loginPromise = null
}

async function rawPost(url, body) {
  await resolveApiBase()
  const res = await fetch(getApiBase() + url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  const data = await res.json()
  if (data.code !== 0) throw new Error(data.msg || 'Server error')
  return data.data
}

async function requestOnce(url, options = {}) {
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
  const res = await fetch(getApiBase() + url, { ...options, headers })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  const data = await res.json()
  if (data.code === 401) {
    // 会话过期：清令牌；网页端下次请求重新自动登录，原生端弹登录门
    localStorage.removeItem(TOKEN_KEY)
    currentUserId = null
    loginPromise = null
    if (needLoginListener) needLoginListener()
    const e = new Error(data.msg || '登录已过期')
    e.needLogin = true
    throw e
  }
  if (data.code !== 0) throw new Error(data.msg || 'Server error')
  return data.data
}

async function request(url, options = {}) {
  await resolveApiBase()
  try {
    return await requestOnce(url, options)
  } catch (e) {
    // HTTPS 缓存失效（网络层失败）→ 回退 HTTP 重试一次
    if (API_BASE === HTTPS_BASE && e instanceof TypeError && !e.needLogin) {
      API_BASE = FALLBACK_BASE
      cacheBase(API_BASE)
      console.warn('[api] HTTPS 不可用，回退 HTTP:', url)
      return await requestOnce(url, options)
    }
    throw e
  }
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

/** 公开 GET（不要求登录，不触发登录门；用于版本检查等启动期接口） */
export async function apiGetPublic(url) {
  await resolveApiBase()
  const res = await fetch(getApiBase() + url, { cache: 'no-store' })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  const data = await res.json()
  if (data.code !== 0) throw new Error(data.msg || 'Server error')
  return data.data
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
export function createPhotoMeta(body) { return apiPost('/photos', body) }
export function deletePhoto(id) { return apiDelete('/photos/' + id) }
export function updatePhotoDescription(id, description) { return apiPut('/photos', { id, description }) }

// 用户
export function updateUserProfile(nickname) { return apiPut('/user/profile', { id: 0, nickname }) }

// 版本检查（公开接口）
export function fetchAppVersion() { return apiGetPublic('/app/version') }

// 崩溃/埋点上报（需登录；未登录时由调用方缓存补报）
export function postApplog(entries) { return apiPost('/applog', entries) }
