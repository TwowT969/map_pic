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

/**
 * 调用原生相机拍照，返回 File-like 对象（包含 blob 和文件名）。
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
      resultType: 'base64'
    })
    // 转成 Blob
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
      file ? resolve(file) : reject(new Error('未选择照片'))
    }
    input.oncancel = () => reject(new Error('取消拍照'))
    input.click()
  })
}

/**
 * 从相册选择多张照片
 * Web 降级: <input type="file" multiple>
 * Native: Capacitor Camera 插件（多次调用 pickImages 或者用 photo library）
 */
export async function pickFromGallery(multiple = true) {
  if (hasCapacitor()) {
    const { Camera, CameraSource } = await import('@capacitor/camera')
    const files = []
    // Capacitor Camera 单次只能选一张，多次循环
    // 或者使用 @capacitor-community/camera-preview （略）
    // 这里先用单张模式，多张需用户多次操作
    const photo = await Camera.getPhoto({
      quality: 90,
      allowEditing: false,
      source: CameraSource.Photos,
      resultType: 'base64'
    })
    const base64 = photo.base64String
    const mime = `image/${photo.format || 'jpeg'}`
    const byteChars = atob(base64)
    const byteNums = new Array(byteChars.length)
    for (let i = 0; i < byteChars.length; i++) {
      byteNums[i] = byteChars.charCodeAt(i)
    }
    const byteArr = new Uint8Array(byteNums)
    files.push(new File([byteArr], `photo_${Date.now()}.${photo.format || 'jpg'}`, { type: mime }))
    return files
  }
  // Web 降级
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

// ==================== Geolocation ====================

/**
 * 获取当前位置（高精度）。
 * 返回 { lat, lng } (GCJ-02 不可用原生直接获取，返回 WGS-84)。
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

// ==================== Filesystem ====================

/**
 * 将图片 URL 缓存到本地文件系统（离线浏览用）。
 * 返回本地文件路径。
 */
export async function cacheImage(url, key) {
  if (!hasCapacitor()) return null
  try {
    const { Filesystem, Directory } = await import('@capacitor/filesystem')
    // 检查是否已缓存
    try {
      await Filesystem.stat({ path: `cache/${key}`, directory: Directory.Cache })
      // 已存在，返回已有路径
      const { uri } = await Filesystem.getUri({
        path: `cache/${key}`,
        directory: Directory.Cache
      })
      return uri
    } catch {
      // 未缓存，下载
    }
    // 下载并写入
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
      // data:image/...;base64,xxxx → return xxxx
      resolve(result.split(',')[1])
    }
    reader.onerror = reject
    reader.readAsDataURL(blob)
  })
}

// ==================== Export utilities ====================

export { hasCapacitor, getPlatform }
