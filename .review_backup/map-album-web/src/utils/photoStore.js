/**
 * 本地照片存储层（v1.2.0 本地化存储）
 *
 * 图片文件只存设备本地，远程仅登记元数据路径：
 * - Android APK：Capacitor Filesystem → Directory.External（应用外部专属目录，无需存储权限）
 *   路径形如 album/p_xxx.jpg / album/p_xxx_t.jpg（原图 / 缩略图）
 * - Web(H5)：IndexedDB map_album_db/local_photos（Blob）
 *
 * 显示源解析：
 * - APK：Filesystem.getUri + Capacitor.convertFileSrc → WebView 可直接加载
 * - H5：URL.createObjectURL（带缓存）
 */
const DIR = 'album'
const DB_NAME = 'map_album_db'
const STORE = 'local_photos'

let _dbPromise = null

function openDb() {
  if (_dbPromise) return _dbPromise
  _dbPromise = new Promise((resolve, reject) => {
    if (!('indexedDB' in window)) { reject(new Error('indexedDB 不可用')); return }
    // v2：在 pending_uploads 基础上新增 local_photos（与 pendingQueue 同库共用）
    const req = indexedDB.open(DB_NAME, 2)
    req.onupgradeneeded = () => {
      const db = req.result
      if (!db.objectStoreNames.contains('pending_uploads')) {
        db.createObjectStore('pending_uploads', { keyPath: 'id' })
      }
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE, { keyPath: 'key' })
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
  return _dbPromise
}

function idbPut(record) {
  return openDb().then(db => new Promise((resolve, reject) => {
    const t = db.transaction(STORE, 'readwrite')
    t.objectStore(STORE).put(record)
    t.oncomplete = () => resolve(true)
    t.onerror = () => reject(t.error)
  }))
}

function idbGet(key) {
  return openDb().then(db => new Promise((resolve, reject) => {
    const t = db.transaction(STORE, 'readonly')
    const req = t.objectStore(STORE).get(key)
    req.onsuccess = () => resolve(req.result || null)
    req.onerror = () => reject(req.error)
  }))
}

function idbDelete(key) {
  return openDb().then(db => new Promise((resolve, reject) => {
    const t = db.transaction(STORE, 'readwrite')
    t.objectStore(STORE).delete(key)
    t.oncomplete = () => resolve(true)
    t.onerror = () => reject(t.error)
  }))
}

function isNative() {
  return !!(window.Capacitor && window.Capacitor.isNativePlatform())
}

function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => resolve(String(r.result).split(',')[1] || '')
    r.onerror = () => reject(r.error)
    r.readAsDataURL(blob)
  })
}

/**
 * 保存一张照片（原图 + 缩略图）到设备本地。
 * @returns {Promise<{localPath: string, localThumbPath: string}>} 本地路径（登记到远程元数据）
 */
export async function saveLocalPhoto(blob, thumbBlob) {
  if (!blob) throw new Error('没有可保存的图片')
  const stamp = Date.now().toString(36) + Math.random().toString(36).slice(2, 6)
  if (isNative()) {
    const { Filesystem, Directory } = await import('@capacitor/filesystem')
    const name = `p_${stamp}.jpg`
    const tname = `p_${stamp}_t.jpg`
    await Filesystem.writeFile({
      path: `${DIR}/${name}`,
      data: await blobToBase64(blob),
      directory: Directory.External,
      recursive: true
    })
    await Filesystem.writeFile({
      path: `${DIR}/${tname}`,
      data: await blobToBase64(thumbBlob || blob),
      directory: Directory.External,
      recursive: true
    })
    return { localPath: `${DIR}/${name}`, localThumbPath: `${DIR}/${tname}` }
  }
  // Web：IndexedDB
  const key = `k_${stamp}`
  await idbPut({ key, blob })
  await idbPut({ key: `${key}_t`, blob: thumbBlob || blob })
  return { localPath: key, localThumbPath: `${key}_t` }
}

const srcCache = new Map()

/** 本地路径 → 可显示的 src（APK: capacitor file URL；H5: objectURL），带缓存 */
export async function photoLocalSrc(localPath) {
  if (!localPath) return ''
  if (srcCache.has(localPath)) return srcCache.get(localPath)
  let src = ''
  if (isNative()) {
    const { Filesystem, Directory } = await import('@capacitor/filesystem')
    const res = await Filesystem.getUri({ directory: Directory.External, path: localPath })
    src = window.Capacitor.convertFileSrc(res.uri)
  } else {
    const rec = await idbGet(localPath)
    src = rec && rec.blob ? URL.createObjectURL(rec.blob) : ''
  }
  if (src) srcCache.set(localPath, src)
  return src
}

/** 删除一张照片的本地文件（原图 + 缩略图，尽力而为） */
export async function deleteLocalPhoto(photo) {
  const paths = [photo && photo.localPath, photo && photo.localThumbPath].filter(Boolean)
  for (const path of paths) {
    try {
      if (isNative()) {
        const { Filesystem, Directory } = await import('@capacitor/filesystem')
        await Filesystem.deleteFile({ directory: Directory.External, path })
      } else {
        await idbDelete(path)
        if (srcCache.has(path)) {
          try { URL.revokeObjectURL(srcCache.get(path)) } catch (e) { /* ignore */ }
          srcCache.delete(path)
        }
      }
    } catch (e) { /* 尽力而为 */ }
  }
}
