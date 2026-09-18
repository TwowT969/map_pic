/**
 * 崩溃上报 + 轻量埋点。
 *
 * 端上缓存队列（内存），登录后批量 POST /applog；未登录暂存（上限 100 条，丢最旧）。
 * window.onerror / unhandledrejection / Vue errorHandler 三路兜底。
 */
import { hasToken, postApplog } from '../api/index.js'

const BUFFER_MAX = 100
let buffer = []
let flushTimer = null
let _meta = null

function meta() {
  if (_meta) return _meta
  _meta = {
    appVersion: String(import.meta.env.VITE_APP_VERSION_NAME || '') + '+' + String(import.meta.env.VITE_APP_VERSION_CODE || '1'),
    device: (navigator.userAgent || '').slice(0, 180)
  }
  return _meta
}

function push(entry) {
  buffer.push({ ts: Date.now(), ...meta(), ...entry })
  if (buffer.length > BUFFER_MAX) buffer = buffer.slice(-BUFFER_MAX)
}

function scheduleFlush(delay) {
  clearTimeout(flushTimer)
  flushTimer = setTimeout(() => { flush() }, delay)
}

/** 轻量行为埋点 */
export function track(tag, message) {
  push({ level: 'info', tag: 'track:' + tag, message: String(message || '').slice(0, 300) })
  scheduleFlush(5000)
}

/** 错误上报 */
export function reportError(err, tag) {
  const e = err || {}
  push({
    level: 'error',
    tag: tag || 'error',
    message: String(e.message || e).slice(0, 300),
    stack: String(e.stack || '').slice(0, 3500)
  })
  scheduleFlush(2000)
}

/** 批量上报（登录后调用；失败回滚缓冲区） */
export async function flush() {
  clearTimeout(flushTimer)
  if (buffer.length === 0) return
  if (!hasToken()) return
  const batch = buffer.splice(0, 20)
  try {
    await postApplog(batch)
  } catch (e) {
    buffer = batch.concat(buffer).slice(-BUFFER_MAX)
  }
}

/** 安装全局错误兜底 */
export function installGlobalErrorHandlers() {
  window.addEventListener('error', (event) => {
    reportError(
      { message: event.message, stack: (event.error && event.error.stack) || '' },
      'window.onerror'
    )
  })
  window.addEventListener('unhandledrejection', (event) => {
    reportError(event.reason, 'unhandledrejection')
  })
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flush()
  })
}

/** Vue errorHandler */
export function installVueErrorHandler(app) {
  app.config.errorHandler = (err, instance, info) => {
    reportError(err, 'vue:' + info)
  }
}
