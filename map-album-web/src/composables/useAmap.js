import { shallowRef } from 'vue'
import { fetchAmapConfig, fetchIpLocation } from '../api/index.js'
import { hasCapacitor, getCurrentPosition } from '../utils/capacitor.js'
import { wgs84ToGcj02 } from '../utils/coord.js'

const map = shallowRef(null)
let AMapGlobal = null
let _geolocation = null

export function useAmap() {

  /**
   * 按官方 v2.0 规范加载高德 JS API（参考快速上手 §1.3）。
   *
   * 关键顺序：
   *   1) _AMapSecurityConfig 对象在 index.html 的 <script> 中创建（在 loader.js 之前）
   *   2) 获取配置后更新 securityJsCode
   *   3) 动态加载 loader.js → AMapLoader.load({ key, version:"2.0" })
   */
  async function loadAmapSDK() {
    if (AMapGlobal) return AMapGlobal

    // 1. 获取配置
    let config
    try {
      config = await fetchAmapConfig()
      console.log('[useAmap] 配置获取成功 key=', mask(config.key), 'securityJsCode=', mask(config.securityCode))
    } catch (e) {
      console.error('[useAmap] 获取高德配置失败:', e)
      throw new Error('获取高德地图配置失败，请确认后端已启动（端口 48081）')
    }

    if (!config.key) {
      throw new Error('高德 JS API Key 为空，请检查 application-local.yml 中 amap.js.key 配置')
    }
    if (!config.securityCode) {
      throw new Error('高德安全密钥为空，请检查 application-local.yml 中 amap.js.security-code 配置')
    }

    // 2. 更新安全密钥（对象已由 index.html 创建，这里只改值）
    window._AMapSecurityConfig.securityJsCode = config.securityCode

    // 3. 加载 loader.js（官方步骤）
    if (!window.AMapLoader) {
      await new Promise((resolve, reject) => {
        const script = document.createElement('script')
        script.src = 'https://webapi.amap.com/loader.js'
        script.onload = () => {
          console.log('[useAmap] loader.js 加载完成')
          resolve()
        }
        script.onerror = (evt) => {
          console.error('[useAmap] loader.js 加载失败:', evt)
          reject(new Error('高德地图 JS SDK 加载失败，请检查网络连接'))
        }
        document.head.appendChild(script)
      })
    }

    // 4. AMapLoader.load() —— 加载指定版本的 JS API
    try {
      AMapGlobal = await window.AMapLoader.load({
        key: config.key,
        version: '2.0'
      })
      console.log('[useAmap] AMap JS API v2.0 加载完成')
      return AMapGlobal
    } catch (e) {
      console.error('[useAmap] AMapLoader.load 失败:', e)
      throw new Error('高德地图 JS API 加载失败: ' + (e.message || ''))
    }
  }

  function createMap(containerId, options = {}) {
    if (!AMapGlobal) throw new Error('AMap SDK not loaded')
    const container = document.getElementById(containerId)
    if (!container) throw new Error('地图容器 #' + containerId + ' 不存在')
    if (container.offsetWidth === 0 || container.offsetHeight === 0) {
      console.warn('[useAmap] 地图容器尺寸为 0，可能影响地图渲染')
    }

    const m = new AMapGlobal.Map(containerId, {
      zoom: 12,
      center: [120.619585, 31.299379],
      viewMode: '2D',
      resizeEnable: true,
      ...options
    })
    map.value = m
    console.log('[useAmap] 地图实例创建完成 container=', containerId,
      'size=', container.offsetWidth + 'x' + container.offsetHeight)

    // 异步加载并添加地图控件（官方示例写法）
    addControls(m)

    return m
  }

  /**
   * 添加地图控件：缩放工具条 ToolBar、比例尺 Scale、定位 Geolocation。
   * 参考官方示例：AMap.plugin(name, callback) -> new -> map.addControl
   */
  function addControls(m) {
    // 检测移动端调整控件位置
    const isMobile = window.innerWidth <= 768

    // 1. 缩放工具条
    AMapGlobal.plugin('AMap.ToolBar', () => {
      const toolbar = new AMapGlobal.ToolBar({
        position: {
          right: '20px',
          bottom: isMobile ? '170px' : '90px'
        }
      })
      m.addControl(toolbar)
    })

    // 2. 比例尺
    AMapGlobal.plugin('AMap.Scale', () => {
      const scale = new AMapGlobal.Scale()
      m.addControl(scale)
    })

    // 3. 定位控件
    AMapGlobal.plugin('AMap.Geolocation', () => {
      const geolocation = new AMapGlobal.Geolocation({
        enableHighAccuracy: true,
        timeout: 10000,
        buttonPosition: 'RB',
        buttonOffset: new AMapGlobal.Pixel(20, isMobile ? 230 : 140),
        zoomToAccuracy: true,
        showMarker: true,
        showCircle: true,
        panToLocation: true
      })
      m.addControl(geolocation)
      _geolocation = geolocation
    })
    console.log('[useAmap] 地图控件已添加（ToolBar / Scale / Geolocation）')
  }

  /**
   * 初始化地图：中心/缩放由调用方传入（App 的定位链决定：
   * 权限定位 → 历史定位 → IP → 苏州），这里不再自动 IP 定位。
   */
  async function initMap(containerId, options = {}) {
    await loadAmapSDK()
    const m = createMap(containerId, options)
    return m
  }

  function getAMap() { return AMapGlobal }

  /** 加载 AMap 插件（Promise 模式，插件就绪后 resolve） */
  function loadPlugin(name) {
    return new Promise((resolve, reject) => {
      if (!AMapGlobal) return reject(new Error('AMap SDK not loaded'))
      AMapGlobal.plugin(name, () => {
        console.log('[useAmap] 插件加载完成:', name)
        resolve()
      })
    })
  }

  /** 创建 Geocoder 实例（自动加载插件） */
  async function createGeocoder() {
    await loadPlugin('AMap.Geocoder')
    return new AMapGlobal.Geocoder()
  }

  /** 主动触发浏览器高精度定位（返回 Promise） */
  function locate() {
    return new Promise((resolve, reject) => {
      if (!_geolocation) return reject(new Error('Geolocation 控件未就绪'))
      _geolocation.getCurrentPosition((status, result) => {
        if (status === 'complete') resolve(result)
        else reject(new Error(result?.message || '定位失败'))
      })
    })
  }

  /**
   * 使用原生 GPS 定位（Capacitor），更精准。
   * 自动将 WGS-84 转为 GCJ-02，然后设置地图中心。
   */
  async function locateNative() {
    if (!hasCapacitor()) {
      // 降级为浏览器定位
      try {
        const result = await locate()
        return result
      } catch (e) {
        throw new Error('浏览器定位失败: ' + e.message)
      }
    }

    try {
      const pos = await getCurrentPosition()
      // Capcitor 返回的是 WGS-84，转为 GCJ-02
      const gcj = wgs84ToGcj02({ lng: pos.lng, lat: pos.lat })
      if (map.value) {
        map.value.setCenter([gcj.lng, gcj.lat])
        map.value.setZoom(15)
      }
      return { position: { lng: gcj.lng, lat: gcj.lat } }
    } catch (e) {
      console.warn('[useAmap] 原生定位失败，降级 IP 定位:', e)
      try {
        const loc = await fetchIpLocation()
        if (map.value) {
          map.value.setCenter([loc.lng, loc.lat])
          map.value.setZoom(13)
        }
        return { position: { lng: loc.lng, lat: loc.lat } }
      } catch (e2) {
        throw new Error('定位失败: ' + (e.message || ''))
      }
    }
  }

  /** 脱敏输出：只显示前后各 4 位 */
  function mask(s) {
    if (!s || s.length <= 8) return s || '(空)'
    return s.substring(0, 4) + '****' + s.substring(s.length - 4)
  }

  return { map, initMap, getAMap, loadPlugin, createGeocoder, locate, locateNative }
}
