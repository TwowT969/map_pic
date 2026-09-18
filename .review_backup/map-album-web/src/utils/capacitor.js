/**
 * 原生平台桥接层
 *
 * 在 Capacitor WebView 中调用原生插件；在普通浏览器中降级为浏览器 API。
 * 上层业务代码只依赖此模块，不直接 import Capacitor 插件。
 */

// Capacitor 是否可用（运行时检测，避免硬依赖）
function hasCapacitor() {
  return !!(window.Capacitor && window.Capacitor.isNativePlatform())
}

function getPlatform() {
  if (!hasCapacitor()) return 'web'
  return window.Capacitor.getPlatform() // 'ios' | 'android'
}

// ==================== Camera ====================

function blobToFile(blob, name) {
  return new File([blob], name, { type: blob.type || 'image/jpeg' })
}

/**
 * 调用原生相机拍照，返回 File-like 对象。
 * Web 降级: <input type="file" capture="camera">
 * Native: Capacitor Camera 插件。
 */
export async function takePhoto() {
  if (hasCapacitor()) {
    const { Camera, CameraSource } = await import('@capacitor/camera')
    const photo = await Camera.getPhoto({
      quality: 90,
      allowEditing: false,
      source: CameraSource.Camera,
      resultType: 'base64',
      // 拍摄的原图同时保存到系统相册（本地化存储：相册即留存入口）
      saveToGallery: true
    })
    const base64 = photo.base64String
    const mime = `image/${photo.format || 'jpeg'}`
    const byteChars = atob(base64)
    const byteNums = new Array(byteChars.length)
    for (let i = 0; i < byteChars.length; i++) {
      byteNums[i] = byteChars.charCodeAt(i)
    }
    const byteArr = new Uint8Array(byteNums)
    return new File([byteArr], `photo_${Date.now()}.${photo.format || 'jpg'}`, { type: mime })
  }
  // Web 降级
  return new Promise((resolve, reject) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/*'
    input.capture = 'environment'
    input.onchange = () => {
      const file = input.files?.[0]
      file ? resolve(file) : reject(new Error('取消拍照'))
    }
    input.oncancel = () => reject(new Error('取消拍照'))
    input.click()
  })
}

/**
 * 从相册选择照片（原生端支持一次多选，最多 20 张）。
 * 三级降级，保证"点击必有响应"：
 *   1) Camera.pickImages —— Android 13+ 系统照片选择器 / Android 12- 系统相册（ACTION_PICK）
 *   2) Camera.getPhoto(Photos) —— 单选系统相册兜底
 *   3) <input type="file" accept="image/*"> —— WebView 文件选择（系统选择器含相册入口）
 * 每一步失败都带上错误信息上抛（调用方 toast 提示），不再静默无响应。
 */
export async function pickFromGallery(multiple = true) {
  if (hasCapacitor()) {
    const { Camera, CameraSource } = await import('@capacitor/camera')
    // 相册读取权限（Android 13+ READ_MEDIA_IMAGES；12- READ_EXTERNAL_STORAGE）。
    // 被拒也继续尝试：ACTION_PICK / 文件选择器不需要该权限也能选图。
    let permDenied = false
    try {
      const perm = await Camera.requestPermissions({ permissions: ['photos'] })
      permDenied = !!(perm && perm.photos === 'denied')
    } catch (e) { /* ignore */ }

    // 1) 多选系统相册
    try {
      const result = await Camera.pickImages({
        quality: 90,
        limit: multiple ? 20 : 1
      })
      const files = []
      for (let i = 0; i < (result.photos || []).length; i++) {
        const p = result.photos[i]
        try {
          const resp = await fetch(p.webPath)
          const blob = await resp.blob()
          files.push(blobToFile(blob, `photo_${Date.now()}_${i}.${p.format || 'jpg'}`))
        } catch (e) {
          console.warn('[capacitor] 读取所选照片失败:', e)
        }
      }
      if (files.length > 0) return files
      throw new Error('未选择照片')
    } catch (e) {
      if (e && e.message === '未选择照片') throw e
      console.warn('[capacitor] pickImages 不可用，降级单选:', e?.message || e)
    }

    // 2) 单选系统相册兜底
    try {
      const photo = await Camera.getPhoto({
        quality: 90,
        allowEditing: false,
        source: CameraSource.Photos,
        resultType: 'base64'
      })
      if (photo && photo.base64String) {
        return [base64ToFile(photo, `photo_${Date.now()}.${photo.format || 'jpg'}`)]
      }
      throw new Error('未选择照片')
    } catch (e) {
      if (e && /取消|cancel/i.test(e?.message || '')) throw new Error('未选择照片')
      console.warn('[capacitor] getPhoto 不可用，降级文件选择:', e?.message || e)
    }

    // 3) 最终兜底：WebView 文件选择（系统选择器带相册入口）
    try {
      const files = await pickViaFileInput(multiple)
      if (files.length > 0) return files
      throw new Error('未选择照片')
    } catch (e) {
      if (e && e.message === '未选择照片') throw new Error('未选择照片')
      throw new Error(permDenied ? '相册权限被拒绝，请在系统设置中允许后重试' : '打开相册失败: ' + (e?.message || e))
    }
  }
  // Web 降级
  return pickViaFileInput(multiple)
}

function base64ToFile(photo, name) {
  const byteChars = atob(photo.base64String)
  const byteNums = new Array(byteChars.length)
  for (let i = 0; i < byteChars.length; i++) {
    byteNums[i] = byteChars.charCodeAt(i)
  }
  return new File([new Uint8Array(byteNums)], name, { type: `image/${photo.format || 'jpeg'}` })
}

function pickViaFileInput(multiple) {
  return new Promise((resolve, reject) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/*'
    input.multiple = multiple
    input.onchange = () => {
      const fileList = input.files
      if (!fileList || fileList.length === 0) {
        reject(new Error('未选择照片'))
        return
      }
      resolve(Array.from(fileList))
    }
    input.click()
  })
}

// ==================== 图片压缩 ====================

/**
 * 客户端图片压缩：最长边限制 + JPEG 质量；小图直接返回原文件。
 * 注意：压缩会丢弃 EXIF（含 GPS/拍摄时间），调用方必须先解析元数据再压缩，
 * 并通过接口参数显式携带元数据。
 * @returns {Promise<File>}
 */
export async function compressImage(file, maxSide = 2048, quality = 0.85) {
  if (!file || !file.type || !file.type.startsWith('image/')) return file
  if (file.type === 'image/gif') return file // 动图不压
  try {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
    const longSide = Math.max(bitmap.width, bitmap.height)
    const scale = Math.min(1, maxSide / longSide)
    // 小图且体积不大：不压（避免重编码损失）
    if (scale >= 1 && file.size < 1.5 * 1024 * 1024) {
      bitmap.close && bitmap.close()
      return file
    }
    const w = Math.max(1, Math.round(bitmap.width * scale))
    const h = Math.max(1, Math.round(bitmap.height * scale))
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    ctx.drawImage(bitmap, 0, 0, w, h)
    bitmap.close && bitmap.close()
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
    if (!blob || blob.size >= file.size) return file
    const baseName = (file.name || 'photo').replace(/\.[^.]+$/, '')
    return new File([blob], baseName + '_c.jpg', { type: 'image/jpeg' })
  } catch (e) {
    console.warn('[capacitor] 压缩失败，使用原图:', e?.message || e)
    return file
  }
}

// ==================== Geolocation ====================

/**
 * 获取当前位置（高精度）。
 * 返回 { lat, lng }（WGS-84，由调用方转 GCJ-02）。
 */
export async function getCurrentPosition() {
  if (hasCapacitor()) {
    const { Geolocation } = await import('@capacitor/geolocation')
    const pos = await Geolocation.getCurrentPosition({
      enableHighAccuracy: true,
      timeout: 10000
    })
    return {
      lat: pos.coords.latitude,
      lng: pos.coords.longitude
    }
  }
  // Web 降级
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('浏览器不支持定位'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      (err) => reject(err),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
    )
  })
}

/**
 * 请求定位权限（原生端；先于权限弹窗给出用途说明）。
 * @returns {Promise<boolean>} 是否已授权
 */
export async function requestLocationPermission() {
  if (!hasCapacitor()) return true
  try {
    const { Geolocation } = await import('@capacitor/geolocation')
    const status = await Geolocation.requestPermissions(['location', 'coarseLocation'])
    return status && status.location === 'granted'
  } catch (e) {
    return false
  }
}

// ==================== Filesystem ====================

/**
 * 将图片 URL 缓存到本地文件系统（离线浏览用）。
 * 返回本地文件路径。
 */
export async function cacheImage(url, key) {
  if (!hasCapacitor()) return null
  try {
    const { Filesystem, Directory } = await import('@capacitor/filesystem')
    try {
      const { uri } = await Filesystem.getUri({
        path: `cache/${key}`,
        directory: Directory.Cache
      })
      await Filesystem.stat({ path: `cache/${key}`, directory: Directory.Cache })
      return uri
    } catch {
      // 未缓存，下载
    }
    const response = await fetch(url)
    const blob = await response.blob()
    const base64 = await blobToBase64(blob)
    await Filesystem.writeFile({
      path: `cache/${key}`,
      data: base64,
      directory: Directory.Cache,
      recursive: true
    })
    const { uri } = await Filesystem.getUri({
      path: `cache/${key}`,
      directory: Directory.Cache
    })
    return uri
  } catch (e) {
    console.warn('[capacitor] cacheImage failed:', e)
    return null
  }
}

/**
 * 删除缓存的图片
 */
export async function removeCachedImage(key) {
  if (!hasCapacitor()) return
  try {
    const { Filesystem, Directory } = await import('@capacitor/filesystem')
    await Filesystem.deleteFile({ path: `cache/${key}`, directory: Directory.Cache })
  } catch { /* 忽略 */ }
}

/**
 * 清除所有图片缓存
 */
export async function clearImageCache() {
  if (!hasCapacitor()) return
  try {
    const { Filesystem, Directory } = await import('@capacitor/filesystem')
    await Filesystem.rmdir({ path: 'cache', directory: Directory.Cache, recursive: true })
  } catch { /* 忽略 */ }
}

// ==================== Network ====================

/**
 * 监听网络状态变化
 */
export function onNetworkChange(callback) {
  if (hasCapacitor()) {
    import('@capacitor/network').then(({ Network }) => {
      Network.addListener('networkStatusChange', (status) => {
        callback(status.connected)
      })
    })
    return
  }
  // Web 降级
  window.addEventListener('online', () => callback(true))
  window.addEventListener('offline', () => callback(false))
}

// ==================== Keyboard ====================

/**
 * 监听键盘显示/隐藏
 */
export function onKeyboardChange(callback) {
  if (hasCapacitor()) {
    import('@capacitor/keyboard').then(({ Keyboard }) => {
      Keyboard.addListener('keyboardWillShow', (info) => callback(true, info.keyboardHeight))
      Keyboard.addListener('keyboardWillHide', () => callback(false, 0))
    })
  }
}

// ==================== StatusBar ====================

/**
 * 设置状态栏样式
 */
export async function setStatusBarStyle(style = 'dark') {
  if (!hasCapacitor()) return
  const { StatusBar } = await import('@capacitor/status-bar')
  await StatusBar.setStyle({ style: style === 'dark' ? 'Dark' : 'Light' })
  await StatusBar.setOverlaysWebView({ overlay: true })
}

// ==================== Helpers ====================

function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onloadend = () => {
      const result = reader.result
      resolve(result.split(',')[1])
    }
    reader.onerror = reject
    reader.readAsDataURL(blob)
  })
}

// ==================== Export utilities ====================

export { hasCapacitor, getPlatform }
