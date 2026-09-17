/**
 * 全屏浏览的"按距离临时链表"（需求4）
 *
 * 思路：以用户当前位置为基准，把图片按"到用户的距离"逐张加入一条临时链表：
 * - 比链表头更近   → 头插（insertHead）
 * - 比链表尾更远   → 尾插（insertTail）
 * - 介于头尾之间   → 在链表中段按距离顺序插入
 * 链表建好后即是一条"从最近到最远"的浏览序列；全屏光箱沿链表方向
 * 左右滑动即可切换"更近的一张 / 更远的一张"。
 *
 * 距离基准坐标统一使用 GCJ-02（与点位 / 地图坐标一致），
 * 哈弗辛公式只用于局部相对排序，不受坐标系小扰动影响。
 */

/** 哈弗辛球面距离（米） */
export function distanceMeters(a, b) {
  if (!a || !b) return Number.POSITIVE_INFINITY
  const R = 6371000
  const rad = Math.PI / 180
  const dLat = (Number(b.lat) - Number(a.lat)) * rad
  const dLng = (Number(b.lng) - Number(a.lng)) * rad
  const s =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(Number(a.lat) * rad) * Math.cos(Number(b.lat) * rad) *
    Math.sin(dLng / 2) * Math.sin(dLng / 2)
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(s)))
}

/** 照片时间（无定位时排序兜底），与 usePhotos.photoTimeOf 同逻辑 */
function photoTimeOf(p) {
  const t = (p && (p.shotTime || p.createTime)) || ''
  return t ? (new Date(t).getTime() || 0) : 0
}

/**
 * 临时双向链表：支持头插 / 尾插 / 按距离中段有序插入。
 */
export class TempLinkedList {
  constructor() {
    this.head = null
    this.tail = null
    this.size = 0
  }

  _node(photo, dist) {
    return { photo, dist, prev: null, next: null }
  }

  /** 头插 */
  insertHead(photo, dist) {
    const n = this._node(photo, dist)
    n.next = this.head
    if (this.head) this.head.prev = n
    this.head = n
    if (!this.tail) this.tail = n
    this.size++
    return n
  }

  /** 尾插 */
  insertTail(photo, dist) {
    const n = this._node(photo, dist)
    n.prev = this.tail
    if (this.tail) this.tail.next = n
    this.tail = n
    if (!this.head) this.head = n
    this.size++
    return n
  }

  /** 中段有序插入（从头找第一个比 dist 大的节点，插到它前面） */
  insertMiddle(photo, dist) {
    let cur = this.head
    while (cur && cur.next && cur.next.dist < dist) cur = cur.next
    const n = this._node(photo, dist)
    n.prev = cur
    n.next = cur.next
    if (cur.next) cur.next.prev = n
    cur.next = n
    this.size++
    return n
  }

  /**
   * 按距离加入链表（头 / 尾 / 中段自动选择）。
   * 距离相等时后来者排在后面（稳定）。
   */
  insertByDistance(photo, dist) {
    if (this.size === 0) {
      const n = this._node(photo, dist)
      this.head = n
      this.tail = n
      this.size++
      return n
    }
    if (dist <= this.head.dist) return this.insertHead(photo, dist)
    if (dist >= this.tail.dist) return this.insertTail(photo, dist)
    return this.insertMiddle(photo, dist)
  }

  /** 链表 → 数组（从头到尾，即从最近到最远） */
  toArray() {
    const out = []
    let cur = this.head
    while (cur) {
      out.push(cur.photo)
      cur = cur.next
    }
    return out
  }
}

/**
 * 构建全屏浏览链。
 *
 * @param {Array} photos 全部候选照片（含 spotId）
 * @param {Object} spotsMap { [spotId]: { lng, lat } } 点位坐标（照片自身无坐标时使用）
 * @param {Object|null} userLoc 用户当前位置 { lng, lat }（GCJ-02）；为空时按拍摄时间降序兜底
 * @param {string|number} startPhotoId 起始照片 id（用户点击的那张）
 * @returns {{ photos: Array, startIndex: number, orderedBy: 'distance'|'time' }}
 */
export function buildBrowseChain(photos, spotsMap, userLoc, startPhotoId) {
  const list = (photos || []).filter(Boolean)
  if (list.length === 0) {
    return { photos: [], startIndex: 0, orderedBy: userLoc ? 'distance' : 'time' }
  }

  let arr
  let orderedBy
  if (userLoc && userLoc.lng != null && userLoc.lat != null) {
    const ll = new TempLinkedList()
    for (const p of list) {
      const spotCoord = spotsMap && spotsMap[p.spotId]
      const coord =
        p.lng != null && p.lat != null
          ? { lng: Number(p.lng), lat: Number(p.lat) }
          : spotCoord || null
      ll.insertByDistance(p, coord ? distanceMeters(userLoc, coord) : Number.POSITIVE_INFINITY)
    }
    arr = ll.toArray()
    orderedBy = 'distance'
  } else {
    // 无定位：按拍摄时间 新→旧 兜底
    arr = [...list].sort((a, b) => photoTimeOf(b) - photoTimeOf(a))
    orderedBy = 'time'
  }

  let startIndex = arr.findIndex(p => p.id === startPhotoId)
  if (startIndex < 0) startIndex = 0
  return { photos: arr, startIndex, orderedBy }
}
