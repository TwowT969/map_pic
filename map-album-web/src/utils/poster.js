/**
 * 分享海报生成（纯前端 canvas，1080x1440 / 3:4）
 *
 * 设计主张：这是我生活的一部分 / 我生活的样子 —— 小资、松弛、有质感。
 *  - 米白奶油底 + 暖棕 + 衬线（宋体）标题，数据退为小字注脚
 *  - 照片：拍立得白框（微倾斜）；邮票（齿孔）+ 邮戳（城市/年月）
 *  - 风格 A「生活地图」：一张寄给自己的明信片，照片钉在真实坐标，虚线细迹
 *  - 风格 B「胶片墙」：杂志生活页，照片按拍摄位置落位，编号像胶片
 *
 * 文案池随「换一版」轮换；数据不可伪造性仍是差异化根基。
 * APK 内缩略图走 CapacitorHttp 转 base64，规避 canvas 跨域污染。
 */
import QRCode from 'qrcode'
import { fileUrl } from '../api/index.js'
import { hasCapacitor } from './capacitor.js'

const W = 1080
const H = 1440
const APP_URL = (import.meta.env.VITE_API_BASE || '').replace(/\/api\/?$/, '') || window.location.origin

const SERIF_STACK = '"Noto Serif CJK SC","Source Han Serif SC","Songti SC","STSong","SimSun",serif'
const SANS_STACK = '"PingFang SC","Noto Sans CJK SC","Microsoft YaHei",system-ui,sans-serif'
const serifFont = (size, bold) => `${bold ? 'bold ' : ''}${size}px ${SERIF_STACK}`
const font = (size, bold) => `${bold ? 'bold ' : ''}${size}px ${SANS_STACK}`

// 文案池（variant 轮换）
const COPY_MAP = ['这是我生活的样子', '把日子过成喜欢的样子', '我的城，装着我的生活', '慢慢走，把这里走成生活']
const COPY_WALL = ['这是我生活的样子', '生活里那些发光的瞬间', '认真生活，认真记录', '一些值得记住的日常']

const PALETTES = [
  ['255,183,94', '255,120,70'],
  ['130,190,255', '40,90,160'],
  ['168,230,163', '30,110,80'],
  ['255,214,150', '200,120,90'],
  ['180,170,255', '80,70,150'],
  ['200,230,240', '110,160,190']
]

function hashStr(s) {
  let h = 2166136261
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619) }
  return h >>> 0
}

function mulberry32(seed) {
  let a = seed >>> 0
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function roundRectPath(ctx, x, y, w, h, r) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

/** 缩略图兜底"照片"：暖色渐变 + 太阳 + 山影 */
function drawFallbackTile(ctx, x, y, s, hue) {
  const [c1, c2] = PALETTES[hue % PALETTES.length]
  ctx.save()
  roundRectPath(ctx, x, y, s, s, 10)
  ctx.clip()
  const g = ctx.createLinearGradient(0, y, 0, y + s)
  g.addColorStop(0, 'rgb(' + c1 + ')')
  g.addColorStop(1, 'rgb(' + c2 + ')')
  ctx.fillStyle = g
  ctx.fillRect(x, y, s, s)
  ctx.fillStyle = 'rgba(255,250,235,0.9)'
  ctx.beginPath(); ctx.arc(x + s * 0.68, y + s * 0.28, s * 0.13, 0, Math.PI * 2); ctx.fill()
  ctx.fillStyle = 'rgba(0,0,0,0.22)'
  ctx.beginPath()
  ctx.moveTo(x, y + s)
  ctx.lineTo(x + s * 0.5, y + s * 0.55)
  ctx.lineTo(x + s * 0.78, y + s * 0.82)
  ctx.lineTo(x + s, y + s * 0.6)
  ctx.lineTo(x + s, y + s)
  ctx.closePath(); ctx.fill()
  ctx.restore()
}

function imgFromSrc(src) {
  return new Promise((resolve) => {
    const im = new Image()
    im.onload = () => resolve(im)
    im.onerror = () => resolve(null)
    im.src = src
  })
}

function withTimeout(p, ms) {
  return Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), ms))])
}

async function loadThumb(url) {
  try {
    // 本地资源（blob:/data:/capacitor file）直接加载，不走 CapacitorHttp
    if (/^(blob:|data:|file:)/.test(url) || url.indexOf('_capacitor_file_') >= 0) {
      return await imgFromSrc(url)
    }
    if (hasCapacitor()) {
      const { CapacitorHttp } = await import('@capacitor/core')
      const res = await CapacitorHttp.get({ url, responseType: 'BASE64', connectTimeout: 8000, readTimeout: 8000 })
      const mime = (res.headers && (res.headers['Content-Type'] || res.headers['content-type'])) || 'image/jpeg'
      const raw = res.data || ''
      const b64 = raw.indexOf('base64,') >= 0 ? raw.split('base64,')[1] : raw
      return await imgFromSrc('data:' + mime + ';base64,' + b64)
    }
    return await imgFromSrc(url)
  } catch (e) {
    return null
  }
}

async function loadQR() {
  try {
    const dataUrl = await QRCode.toDataURL(APP_URL, { margin: 1, width: 220 })
    return await imgFromSrc(dataUrl)
  } catch (e) {
    return null
  }
}

function drawQR(ctx, img, x, y, s) {
  if (img) { ctx.drawImage(img, x, y, s, s); return }
  ctx.fillStyle = '#fffdf8'
  ctx.fillRect(x, y, s, s)
  const rnd = mulberry32(42)
  ctx.fillStyle = '#4a4132'
  const cell = (s - 20) / 11
  for (let i = 0; i < 11; i++) {
    for (let j = 0; j < 11; j++) {
      const finder = (i < 3 && j < 3) || (i < 3 && j > 7) || (i > 7 && j < 3)
      if (!finder && rnd() < 0.42) ctx.fillRect(x + 10 + j * cell, y + 10 + i * cell, cell, cell)
    }
  }
}

// ==================== 数据准备 ====================

function prepareData(spots, photosBySpot, variant) {
  const spotList = (spots || []).filter(s => s && s.lng != null && s.lat != null)
  const counts = new Map()
  let nPhotos = 0
  spotList.forEach(s => {
    const c = (photosBySpot[s.id] || []).length
    counts.set(s.id, c); nPhotos += c
  })
  const cityCount = {}
  spotList.forEach(s => {
    const c = (s.city || '').trim()
    if (c) cityCount[c] = (cityCount[c] || 0) + 1
  })
  let topCity = ''
  let best = 0
  Object.keys(cityCount).forEach(c => { if (cityCount[c] > best) { best = cityCount[c]; topCity = c } })

  const now = new Date()
  const month = now.getMonth() + 1
  const ym = now.getFullYear() + '.' + String(month).padStart(2, '0')
  const season = ['冬', '冬', '春', '春', '春', '夏', '夏', '夏', '秋', '秋', '秋', '冬'][now.getMonth()]
  const dateLine = now.getFullYear() + ' 年 ' + month + ' 月'

  const lons = spotList.map(s => s.lng)
  const lats = spotList.map(s => s.lat)
  const minLng = Math.min.apply(null, lons), maxLng = Math.max.apply(null, lons)
  const minLat = Math.min.apply(null, lats), maxLat = Math.max.apply(null, lats)
  const spanLng = maxLng - minLng, spanLat = maxLat - minLat
  const deg = {
    lng: spanLng > 0.0005 ? [0, 1, 2, 3].map(k => minLng + (spanLng * k) / 3) : [120.30, 120.45, 120.60, 120.75],
    lat: spanLat > 0.0005 ? [0, 1, 2].map(k => maxLat - (spanLat * k) / 2) : [31.35, 31.25, 31.15]
  }
  const pos = normalizePositions(spotList, variant)
  return {
    spotList, counts, pos,
    topCity, ym, season, dateLine,
    stats: { nSpots: spotList.length, nPhotos }
  }
}

/** 经纬度 → 画布坐标；数据聚在一团时环形铺开 */
function normalizePositions(spotList, variant) {
  const pos = new Map()
  const n = spotList.length
  if (!n) return pos
  const X0 = 160, X1 = 920, Y0 = 360, Y1 = 960
  if (n === 1) { pos.set(spotList[0].id, { x: (X0 + X1) / 2, y: (Y0 + Y1) / 2 }); return pos }
  const lons = spotList.map(s => s.lng), lats = spotList.map(s => s.lat)
  const minLng = Math.min.apply(null, lons), maxLng = Math.max.apply(null, lons)
  const minLat = Math.min.apply(null, lats), maxLat = Math.max.apply(null, lats)
  const spanLng = maxLng - minLng, spanLat = maxLat - minLat
  if (spanLng < 0.008 && spanLat < 0.008) {
    const cx = (X0 + X1) / 2, cy = (Y0 + Y1) / 2
    const R = Math.min(280, 110 + n * 16)
    spotList.forEach((s, i) => {
      const a = (i / n) * Math.PI * 2 + (variant % 7) * 0.35
      pos.set(s.id, {
        x: Math.min(X1, Math.max(X0, cx + Math.cos(a) * R * 1.15)),
        y: Math.min(Y1, Math.max(Y0, cy + Math.sin(a) * R * 0.8))
      })
    })
    return pos
  }
  const plng = spanLng || 0.01, plat = spanLat || 0.01
  spotList.forEach(s => {
    pos.set(s.id, {
      x: Math.min(X1, Math.max(X0, X0 + ((s.lng - minLng) / plng) * (X1 - X0))),
      y: Math.min(Y1, Math.max(Y0, Y1 - ((s.lat - minLat) / plat) * (Y1 - Y0)))
    })
  })
  return pos
}

/** 胶片墙瓦片：每点最近 3 张，全局按时间倒序，最多 14 张 */
function collectPhotoTiles(data, photosBySpot) {
  const raw = []
  data.spotList.forEach(s => {
    ;(photosBySpot[s.id] || []).slice(0, 3).forEach(p => raw.push({ p, spot: s }))
  })
  raw.sort((a, b) => new Date(b.p.createTime || 0) - new Date(a.p.createTime || 0))
  return raw.slice(0, 14).map((r, i) => {
    const p0 = data.pos.get(r.spot.id) || { x: 540, y: 660 }
    const s = i === 0 ? 176 : 100 + (hashStr(String(r.p.id)) % 5) * 9
    const jx = ((hashStr(String(r.p.id)) % 13) - 6) * 7
    const jy = ((hashStr(String(r.p.id * 7 + 3)) % 13) - 6) * 7
    const angle = (((hashStr(String(r.p.id)) % 7) - 3) * 0.9) * Math.PI / 180
    return {
      p: r.p, idx: i,
      x: Math.min(940 - s, Math.max(120, p0.x - s / 2 + jx)),
      y: Math.min(1080 - s, Math.max(330, p0.y - s / 2 + jy)),
      s, hue: i, img: null, isHero: i === 0, angle
    }
  })
}

// ==================== 小资元素 ====================

/** 拍立得：白框 + 下沿厚边，微倾斜 */
function drawPolaroid(ctx, img, cx, cy, pw, angle, hue) {
  const fw = pw + 18, fh = pw + 40
  ctx.save()
  ctx.translate(cx, cy); ctx.rotate(angle)
  ctx.shadowColor = 'rgba(90,70,40,0.20)'; ctx.shadowBlur = 16; ctx.shadowOffsetY = 7
  ctx.fillStyle = '#fffdf8'
  ctx.fillRect(-fw / 2, -fh / 2, fw, fh)
  ctx.shadowColor = 'transparent'
  const ix = -pw / 2, iy = -fh / 2 + 9
  if (img) ctx.drawImage(img, ix, iy, pw, pw)
  else drawFallbackTile(ctx, ix, iy, pw, hue)
  ctx.restore()
}

/** 邮票：白票 + 齿孔 + 照片 + 「城市 · 80分」 */
function drawStamp(ctx, img, cx, cy, size, angle, city, hue) {
  ctx.save()
  ctx.translate(cx, cy); ctx.rotate(angle)
  ctx.shadowColor = 'rgba(90,70,40,0.18)'; ctx.shadowBlur = 10; ctx.shadowOffsetY = 5
  ctx.fillStyle = '#fffdf8'
  ctx.fillRect(-size / 2, -size / 2, size, size)
  ctx.shadowColor = 'transparent'
  const pad = 12, cap = 32
  const inner = size - pad * 2 - cap
  if (img) ctx.drawImage(img, -size / 2 + pad, -size / 2 + pad, inner, inner)
  else drawFallbackTile(ctx, -size / 2 + pad, -size / 2 + pad, inner, hue)
  ctx.fillStyle = '#8a7a5e'; ctx.font = serifFont(16); ctx.textAlign = 'center'
  ctx.fillText((city || '我的城') + ' · 80分', 0, size / 2 - 10)
  ctx.fillStyle = '#f2ecdf' // 齿孔用底色打穿
  const step = 22
  for (let k = -size / 2 + 10; k <= size / 2 - 10; k += step) {
    for (const [px, py] of [[k, -size / 2], [k, size / 2], [-size / 2, k], [size / 2, k]]) {
      ctx.beginPath(); ctx.arc(px, py, 5, 0, Math.PI * 2); ctx.fill()
    }
  }
  ctx.restore()
}

/** 邮戳：双环 + 城市/年月，微倾斜 */
function drawPostmark(ctx, cx, cy, r, city, ym) {
  ctx.save()
  ctx.translate(cx, cy); ctx.rotate(-0.12)
  ctx.strokeStyle = 'rgba(150,130,95,0.7)'; ctx.lineWidth = 2
  ctx.beginPath(); ctx.arc(0, 0, r, 0, Math.PI * 2); ctx.stroke()
  ctx.beginPath(); ctx.arc(0, 0, r - 8, 0, Math.PI * 2); ctx.stroke()
  ctx.fillStyle = 'rgba(122,104,72,0.85)'; ctx.textAlign = 'center'
  ctx.font = serifFont(20, true)
  ctx.fillText(city || '我的城', 0, -7)
  ctx.font = serifFont(15)
  ctx.fillText(ym, 0, 19)
  ctx.restore()
}

// ==================== 风格 A：生活地图（明信片） ====================

function drawMapPoster(ctx, data, variant, thumbs, qrImg) {
  const { spotList, stats } = data

  const bg = ctx.createLinearGradient(0, 0, 0, H)
  bg.addColorStop(0, '#f7f2e8')
  bg.addColorStop(1, '#efe6d5')
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, W, H)

  // 明信片细框
  ctx.strokeStyle = '#c9b998'; ctx.lineWidth = 2
  ctx.strokeRect(40, 40, W - 80, H - 80)
  ctx.strokeStyle = '#d8ccb2'; ctx.lineWidth = 1
  ctx.strokeRect(56, 56, W - 112, H - 112)

  // 日期 / 标题 / 引句
  ctx.fillStyle = '#8a7a5e'; ctx.font = serifFont(28); ctx.textAlign = 'left'
  ctx.fillText(data.dateLine + ' · ' + data.season, 104, 152)
  ctx.fillStyle = '#3f3728'; ctx.font = serifFont(60, true)
  ctx.fillText(COPY_MAP[variant % COPY_MAP.length], 102, 240)
  ctx.fillStyle = '#a5987f'; ctx.font = serifFont(25)
  ctx.fillText('把走过的路，收进一张卡片', 104, 292)

  // 风格化地图（浅棕路网 + 灰绿水系）
  const rnd = mulberry32(hashStr(spotList.map(s => s.id).join(',')) + variant * 7919)
  ctx.strokeStyle = '#ded1b8'; ctx.lineWidth = 2
  for (let i = 0; i < 14; i++) {
    let x = rnd() * W, y = 330 + rnd() * 700
    ctx.beginPath(); ctx.moveTo(x, y)
    const seg = 2 + Math.floor(rnd() * 3)
    for (let k = 0; k < seg; k++) {
      x += (rnd() - 0.5) * 500; y += (rnd() - 0.5) * 280
      ctx.lineTo(x, y)
    }
    ctx.stroke()
  }
  ctx.strokeStyle = '#d3ded9'; ctx.lineWidth = 26
  ctx.lineJoin = 'round'; ctx.lineCap = 'round'
  ctx.beginPath(); ctx.moveTo(0, 720)
  ctx.quadraticCurveTo(260, 660, 520, 745)
  ctx.quadraticCurveTo(820, 815, 1080, 705)
  ctx.stroke()
  ctx.fillStyle = '#d9e2dc'
  ctx.beginPath(); ctx.ellipse(935, 560, 110, 68, 0, 0, Math.PI * 2); ctx.fill()
  ctx.beginPath(); ctx.ellipse(195, 915, 85, 52, 0, 0, Math.PI * 2); ctx.fill()

  // 虚线细迹（按创建时间）
  const pts = spotList.map(s => data.pos.get(s.id)).filter(Boolean)
  if (pts.length > 1) {
    ctx.save()
    ctx.setLineDash([1, 13]); ctx.lineCap = 'round'
    ctx.strokeStyle = '#c5b795'; ctx.lineWidth = 3
    ctx.beginPath()
    pts.forEach((p, i) => (i ? ctx.lineTo(p.x, p.y) : ctx.moveTo(p.x, p.y)))
    ctx.stroke()
    ctx.restore()
  }

  // 拍立得照片钉在真实坐标
  const ANGLES = [-0.05, 0.035, -0.03, 0.05, -0.04, 0.03, -0.025, 0.045, -0.035, 0.02, 0.045, -0.045]
  spotList.slice(0, 12).forEach((s, i) => {
    const p = data.pos.get(s.id)
    if (!p) return
    const pw = Math.max(78, Math.min(108, 76 + (data.counts.get(s.id) || 0) * 3))
    drawPolaroid(ctx, thumbs[i] || null, p.x, p.y, pw, ANGLES[i % ANGLES.length], i)
  })

  // 邮票（贴最新一张照片）+ 邮戳
  drawStamp(ctx, thumbs[0] || null, 906, 224, 170, 0.05, data.topCity, 1)
  drawPostmark(ctx, 768, 318, 54, data.topCity, data.ym)

  // 底部：寄语 + 小注 + 品牌 + 二维码
  ctx.fillStyle = '#6f644f'; ctx.font = serifFont(38, true); ctx.textAlign = 'left'
  ctx.fillText('—— 寄自' + (data.topCity || '这座城'), 104, 1214)
  ctx.fillStyle = '#a5987f'; ctx.font = font(22)
  ctx.fillText(stats.nSpots + ' 个地方 · ' + stats.nPhotos + ' 个瞬间', 106, 1266)
  ctx.fillStyle = '#b5a888'; ctx.font = font(18)
  ctx.fillText('地图相册 · ' + data.ym, 106, 1358)
  drawQR(ctx, qrImg, 920, 1246, 90)
  ctx.fillStyle = '#a5987f'; ctx.font = font(16); ctx.textAlign = 'right'
  ctx.fillText('扫码，看我的生活', 1005, 1362)
}

// ==================== 风格 B：胶片墙（杂志生活页） ====================

function drawFilmTile(ctx, t) {
  const pad = 9, strip = 26
  const fw = t.s + pad * 2, fh = t.s + pad + strip
  ctx.save()
  ctx.translate(t.x + fw / 2, t.y + fh / 2)
  ctx.rotate(t.angle)
  ctx.shadowColor = 'rgba(90,70,40,0.16)'; ctx.shadowBlur = 12; ctx.shadowOffsetY = 5
  ctx.fillStyle = '#fffdf8'
  ctx.fillRect(-fw / 2, -fh / 2, fw, fh)
  ctx.shadowColor = 'transparent'
  if (t.img) ctx.drawImage(t.img, -t.s / 2, -fh / 2 + pad, t.s, t.s)
  else drawFallbackTile(ctx, -t.s / 2, -fh / 2 + pad, t.s, t.hue)
  ctx.fillStyle = '#b0a284'; ctx.font = font(13); ctx.textAlign = 'left'
  ctx.fillText('No.' + String(t.idx + 1).padStart(2, '0'), -t.s / 2, fh / 2 - 8)
  ctx.restore()
}

function drawWallPoster(ctx, data, tiles, qrImg, variant) {
  ctx.fillStyle = '#faf6ee'
  ctx.fillRect(0, 0, W, H)

  // 杂志刊头
  ctx.fillStyle = '#b5a888'; ctx.font = font(20); ctx.textAlign = 'left'
  ctx.fillText('LIFE IN FRAMES'.split('').join(' '), 102, 142)
  ctx.strokeStyle = '#e5dcc8'; ctx.lineWidth = 1
  ctx.beginPath(); ctx.moveTo(100, 162); ctx.lineTo(980, 162); ctx.stroke()

  ctx.fillStyle = '#3f3728'; ctx.font = serifFont(56, true)
  ctx.fillText(COPY_WALL[variant % COPY_WALL.length], 100, 252)
  ctx.fillStyle = '#a5987f'; ctx.font = serifFont(24)
  ctx.fillText('—— 一些值得慢慢翻的日常', 102, 304)

  // 区域标签：照片最多的 3 个打卡点（小字，克制）
  const top = data.spotList
    .filter(s => (data.counts.get(s.id) || 0) > 0)
    .sort((a, b) => (data.counts.get(b.id) || 0) - (data.counts.get(a.id) || 0))
    .slice(0, 3)
  top.forEach(s => {
    const p = data.pos.get(s.id)
    if (!p) return
    ctx.fillStyle = '#b58a4a'
    ctx.beginPath(); ctx.arc(p.x - 12, p.y - 38, 4, 0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = '#a5987f'; ctx.font = serifFont(19); ctx.textAlign = 'left'
    ctx.fillText(s.name || '未命名', p.x - 2, p.y - 30)
  })

  // 胶片瓦片
  tiles.forEach(t => drawFilmTile(ctx, t))

  // 底部
  ctx.fillStyle = '#a5987f'; ctx.font = font(22); ctx.textAlign = 'left'
  ctx.fillText(data.stats.nSpots + ' 个地方 · ' + data.stats.nPhotos + ' 个瞬间', 100, 1252)
  ctx.fillStyle = '#b5a888'; ctx.font = font(18)
  ctx.fillText('地图相册 · ' + (data.topCity || '我的城') + ' · ' + data.ym, 100, 1298)
  drawQR(ctx, qrImg, 930, 1240, 90)
  ctx.fillStyle = '#a5987f'; ctx.font = font(16); ctx.textAlign = 'right'
  ctx.fillText('扫码，看我的生活', 1015, 1360)
}

// ==================== 对外入口 ====================

/**
 * 渲染海报
 * @param {'map'|'wall'} style 生活地图 / 胶片墙
 * @param {Array} spots 打卡点列表
 * @param {Object} photosBySpot { spotId: Photo[] }（时间倒序）
 * @param {number} variant 换一版计数（换文案 + 构图种子）
 * @returns {Promise<HTMLCanvasElement>}
 */
export async function renderPoster(style, spots, photosBySpot, variant = 0) {
  const data = prepareData(spots, photosBySpot, variant)
  if (!data.spotList.length) throw new Error('还没有打卡点数据')
  const canvas = document.createElement('canvas')
  canvas.width = W; canvas.height = H
  const ctx = canvas.getContext('2d')
  const qrImg = await loadQR()

  if (style === 'map') {
    const list = data.spotList.slice(0, 12)
    const thumbs = await Promise.all(list.map(s => {
      const p = (photosBySpot[s.id] || [])[0]
      if (!p) return Promise.resolve(null)
      return withTimeout(loadThumb(p.thumbSrc || p.src || fileUrl(p.thumbUrl || p.url)), 8000).catch(() => null)
    }))
    drawMapPoster(ctx, data, variant, thumbs, qrImg)
  } else {
    const tiles = collectPhotoTiles(data, photosBySpot)
    await Promise.all(tiles.map(t =>
      withTimeout(loadThumb(t.p.thumbSrc || t.p.src || fileUrl(t.p.thumbUrl || t.p.url)), 8000)
        .then(im => { t.img = im })
        .catch(() => {})
    ))
    drawWallPoster(ctx, data, tiles, qrImg, variant)
  }
  return canvas
}
