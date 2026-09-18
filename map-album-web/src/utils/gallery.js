/**
 * 微信式应用内相册：原生 Gallery 插件桥接。
 * 直读 MediaStore 分页返回真实文件路径，不拉起系统文件管理器。
 */
import { Capacitor } from '@capacitor/core'

const Gallery = registerPlugin()

function registerPlugin() {
  try {
    return Capacitor.registerPlugin('Gallery')
  } catch (e) {
    return null
  }
}

/** 原生插件是否可用（仅 APK 且插件已注册时） */
export function hasGalleryPlugin() {
  return Capacitor.isNativePlatform() && !!Gallery
}

/**
 * 分页读取系统相册。
 * @param {number} page 从 0 开始
 * @param {number} pageSize 每页数量
 * @returns {Promise<{items: Array<{id:number,path:string,name:string,dateAdded:number,size:number}>, total:number, hasMore:boolean}>}
 */
export async function galleryList(page = 0, pageSize = 60) {
  if (!hasGalleryPlugin()) throw new Error('Gallery 插件不可用')
  const res = await Gallery.list({ page, pageSize })
  return {
    items: (res && res.items) || [],
    total: (res && res.total) || 0,
    hasMore: !!(res && res.hasMore)
  }
}

/**
 * 把选中的相册文件路径转成 File 对象（走 WebView 本地文件协议读取）。
 * @param {string[]} paths
 * @returns {Promise<File[]>}
 */
export async function galleryFiles(paths) {
  const out = []
  for (const p of paths || []) {
    try {
      const url = Capacitor.convertFileSrc(p)
      const resp = await fetch(url)
      if (!resp.ok) throw new Error('HTTP ' + resp.status)
      const blob = await resp.blob()
      const name = (p.split('/').pop() || 'photo').trim()
      const ext = (name.split('.').pop() || 'jpg').toLowerCase()
      const type = blob.type && blob.type.startsWith('image/') ? blob.type
        : (ext === 'png' ? 'image/png' : ext === 'gif' ? 'image/gif' : ext === 'webp' ? 'image/webp' : 'image/jpeg')
      out.push(new File([blob], name, { type }))
    } catch (e) {
      console.warn('[gallery] 读取所选照片失败:', p, e)
    }
  }
  return out
}
