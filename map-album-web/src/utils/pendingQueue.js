/**
 * 待上传照片队列（IndexedDB 持久化）。
 *
 * 用途：上传失败重试 / 离线拍照先存后传。App 被杀、断网、切后台都不丢。
 * 每条记录：{ id, blob, name, meta: { lng, lat, address, province, city, district, desc, shotTime, device } }
 */
const DB_NAME = 'map_album_db'
const STORE = 'pending_uploads'
let _dbPromise = null

function openDb() {
  if (_dbPromise) return _dbPromise
  _dbPromise = new Promise((resolve, reject) => {
    if (!('indexedDB' in window)) { reject(new Error('indexedDB 不可用')); return }
    const req = indexedDB.open(DB_NAME, 1)
    req.onupgradeneeded = () => {
      const db = req.result
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE, { keyPath: 'id' })
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
  return _dbPromise
}

function runTx(mode, action) {
  return openDb().then(db => new Promise((resolve, reject) => {
    const t = db.transaction(STORE, mode)
    const result = action(t.objectStore(STORE))
    t.oncomplete = () => resolve(typeof result === 'object' && result && 'result' in result ? result.result : result)
    t.onerror = () => reject(t.error)
  }))
}

/** 新增/更新待上传记录 */
export async function addPending(item) {
  try { await runTx('readwrite', s => s.put(item)); return true } catch (e) { console.warn('[queue] add fail:', e); return false }
}

/** 全量列表（失败返回空数组） */
export async function listPending() {
  try {
    return await openDb().then(db => new Promise((resolve, reject) => {
      const t = db.transaction(STORE, 'readonly')
      const req = t.objectStore(STORE).getAll()
      req.onsuccess = () => resolve(req.result || [])
      req.onerror = () => reject(req.error)
    }))
  } catch (e) { return [] }
}

/** 删除记录 */
export async function removePending(id) {
  try { await runTx('readwrite', s => s.delete(id)); return true } catch (e) { return false }
}

/** 待上传数量 */
export async function countPending() {
  return (await listPending()).length
}
