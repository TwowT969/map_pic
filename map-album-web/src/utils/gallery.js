/**
 * 系统 Photo Picker 桥接（Android 13+ / 系统组件回移机型）。
 * 按产品决策不做旧机型与 H5 兜底：不支持的设备由插件 reject，调用方提示。
 */
import { Capacitor } from '@capacitor/core'

const Gallery = Capacitor.registerPlugin('Gallery')

/** 当前是否为原生环境（APK） */
export function hasPhotoPicker() {
  return Capacitor.isNativePlatform()
}

/**
 * 唤起系统相册 Picker（网格/内存全由系统接管）。
 * @returns {Promise<string[]>} 选中照片在应用缓存中的文件路径数组
 */
export async function pickPhotos(max = 999999) {
  const res = await Gallery.pick({ max })
  return (res && res.paths) || []
}

/**
 * 把缓存路径物化为 File。
 * 由上传循环逐张调用（当前要处理哪张才读哪张），内存峰值恒为单张。
 */
export async function fileFromPath(p) {
  const resp = await fetch(Capacitor.convertFileSrc(p))
  if (!resp.ok) throw new Error('读取照片失败: HTTP ' + resp.status)
  const blob = await resp.blob()
  const name = (p.split('/').pop() || 'photo.jpg').replace(/^\d+_\d+_/, '')
  const ext = (name.split('.').pop() || 'jpg').toLowerCase()
  const type = blob.type && blob.type.startsWith('image/') ? blob.type
    : (ext === 'png' ? 'image/png' : ext === 'gif' ? 'image/gif' : ext === 'webp' ? 'image/webp' : 'image/jpeg')
  return new File([blob], name, { type })
}
