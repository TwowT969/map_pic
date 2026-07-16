// WGS84 → GCJ-02 坐标转换（公开标准算法）
// EXIF GPS 存的是 WGS84，高德地图用 GCJ-02，国内偏移量 100-700m

const PI = Math.PI
const A = 6378245.0
const EE = 0.00669342162296594323

function _transformLat(lng, lat) {
  let ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat + 0.1 * lng * lat + 0.2 * Math.sqrt(Math.abs(lng))
  ret += (20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0 / 3.0
  ret += (20.0 * Math.sin(lat * PI) + 40.0 * Math.sin(lat / 3.0 * PI)) * 2.0 / 3.0
  ret += (160.0 * Math.sin(lat / 12.0 * PI) + 320 * Math.sin(lat * PI / 30.0)) * 2.0 / 3.0
  return ret
}

function _transformLng(lng, lat) {
  let ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng + 0.1 * lng * lat + 0.1 * Math.sqrt(Math.abs(lng))
  ret += (20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0 / 3.0
  ret += (20.0 * Math.sin(lng * PI) + 40.0 * Math.sin(lng / 3.0 * PI)) * 2.0 / 3.0
  ret += (150.0 * Math.sin(lng / 12.0 * PI) + 300.0 * Math.sin(lng / 30.0 * PI)) * 2.0 / 3.0
  return ret
}

/**
 * WGS84 转 GCJ-02（火星坐标系）
 * @param {{ lng: number, lat: number } | [number, number]} input
 * @returns {{ lng: number, lat: number }}
 */
export function wgs84ToGcj02(input) {
  let wgsLng, wgsLat
  if (Array.isArray(input)) {
    [wgsLng, wgsLat] = input
  } else {
    wgsLng = input.lng
    wgsLat = input.lat
  }

  // 中国境外不做转换
  if (wgsLng < 72.004 || wgsLng > 137.8347 || wgsLat < 0.8293 || wgsLat > 55.8271) {
    return { lng: wgsLng, lat: wgsLat }
  }

  const dLat = _transformLat(wgsLng - 105.0, wgsLat - 35.0)
  const dLng = _transformLng(wgsLng - 105.0, wgsLat - 35.0)
  const radLat = wgsLat / 180.0 * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  const finalLat = wgsLat + (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
  const finalLng = wgsLng + (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI)

  return { lng: finalLng, lat: finalLat }
}
