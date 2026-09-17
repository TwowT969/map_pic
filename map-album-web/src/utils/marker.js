import { fileUrl } from '../api/index.js'

// 构建自定义 Marker 的 HTMLElement
// createMarkerElement：图片形态（比例尺 < 10km）—— 最多 3 张本地缩略图拼贴 + 圆点 + 名称，
//   缩略图可点击（onThumbClick 回调），点击直接进入全屏浏览（需求3/4）
// createSimpleMarkerElement：粗比例尺 —— 纯图标 + 照片数，不加载任何图片
// createPickMarkerElement：上传选点模式 —— 蓝色圆点 + 扩散光圈（可拖动）
export function createMarkerElement(spot, photos, onThumbClick) {
  const wrapper = document.createElement('div')
  wrapper.className = 'custom-marker'

  // 图片拼贴区域：只保留能解析出显示源的图片，最多 3 张（需求3：最多展示三张）
  const photoWrap = document.createElement('div')
  photoWrap.className = 'marker-photo-wrap'

  const list = (Array.isArray(photos) ? photos : (photos ? [photos] : []))
    .filter(p => p && (p.thumbSrc || p.src))
    .slice(0, 3)

  if (list.length === 0) {
    photoWrap.innerHTML = '<div class="marker-no-photo">📍</div>'
  } else if (list.length === 1) {
    photoWrap.appendChild(_thumb(list[0], onThumbClick))
  } else {
    photoWrap.classList.add('marker-collage', 'n' + list.length)
    list.forEach(p => photoWrap.appendChild(_thumb(p, onThumbClick)))
  }
  wrapper.appendChild(photoWrap)

  // 小圆点
  const dot = document.createElement('div')
  dot.className = 'marker-dot'
  wrapper.appendChild(dot)

  // 名称标签
  const label = document.createElement('div')
  label.className = 'marker-label'
  label.textContent = spot.name || '未命名'
  wrapper.appendChild(label)

  return wrapper
}

/** 单张缩略图（可点击 → 全屏浏览） */
function _thumb(photo, onThumbClick) {
  const btn = document.createElement('div')
  btn.className = 'marker-thumb'
  btn.setAttribute('data-pid', photo.id != null ? String(photo.id) : '')
  const img = document.createElement('img')
  img.src = photo.thumbSrc || photo.src || fileUrl(photo.thumbUrl || photo.url || '')
  img.alt = ''
  img.setAttribute('draggable', 'false')
  img.onerror = () => { btn.style.display = 'none' }
  btn.appendChild(img)
  if (typeof onThumbClick === 'function') {
    btn.addEventListener('click', (e) => {
      e.stopPropagation()
      e.preventDefault()
      onThumbClick(photo)
    })
  }
  return btn
}

/**
 * 粗比例尺简化标记：📍 图标 + 照片数角标（无 <img>，零图片请求）。
 */
export function createSimpleMarkerElement(spot, photoCount) {
  const wrapper = document.createElement('div')
  wrapper.className = 'custom-marker'

  const photoWrap = document.createElement('div')
  photoWrap.className = 'marker-photo-wrap marker-simple-wrap'
  const count = photoCount > 0 ? String(photoCount) : ''
  photoWrap.innerHTML =
    '<div class="marker-no-photo">📍' +
    (count ? '<span class="marker-count">' + count + '</span>' : '') +
    '</div>'
  wrapper.appendChild(photoWrap)

  const dot = document.createElement('div')
  dot.className = 'marker-dot'
  wrapper.appendChild(dot)

  const label = document.createElement('div')
  label.className = 'marker-label'
  label.textContent = spot.name || '未命名'
  wrapper.appendChild(label)

  return wrapper
}

/**
 * 上传选点模式的蓝色标记：蓝色圆点 + 扩散光圈（anchor=center）。
 */
export function createPickMarkerElement() {
  const wrapper = document.createElement('div')
  wrapper.className = 'pick-marker'

  const pulse = document.createElement('div')
  pulse.className = 'pick-marker-pulse'
  wrapper.appendChild(pulse)

  const dot = document.createElement('div')
  dot.className = 'pick-marker-dot'
  wrapper.appendChild(dot)

  return wrapper
}

// 动态注入 marker 样式（一次性）
let _injected = false
export function injectMarkerStyles() {
  if (_injected) return
  _injected = true
  const style = document.createElement('style')
  style.textContent = `
    .custom-marker {
      position: relative; cursor: pointer;
      display: flex; flex-direction: column; align-items: center;
      transition: transform 0.2s;
      touch-action: none;
    }
    .custom-marker:hover { transform: scale(1.08); z-index: 10; }
    .custom-marker.marker-dragging { transform: scale(1.18); z-index: 30; }
    .marker-photo-wrap {
      width: 52px; height: 52px; border-radius: 8px; overflow: hidden;
      border: 2.5px solid #fff; box-shadow: 0 3px 10px rgba(0,0,0,0.25);
      background: #f0f0f0;
    }
    .marker-no-photo {
      width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
      background: linear-gradient(135deg, #4a90d9, #357abd); color: #fff; font-size: 20px;
    }
    .marker-simple-wrap .marker-no-photo { position: relative; background: linear-gradient(135deg, #5aa2e0, #357abd); }
    .marker-count {
      position: absolute; top: -5px; right: -5px;
      min-width: 18px; height: 18px; line-height: 15px; padding: 0 4px;
      border-radius: 9px; background: #e74c3c; color: #fff;
      font-size: 11px; font-weight: bold; text-align: center;
      border: 2px solid #fff; box-sizing: border-box;
    }

    /* ===== 图片拼贴（1 / 2 / 3 张） ===== */
    .marker-collage {
      display: grid; gap: 1px; background: #dfe6ee;
    }
    .marker-collage.n2 { grid-template-columns: 1fr 1fr; }
    .marker-collage.n3 { grid-template-columns: 1.6fr 1fr; grid-template-rows: 1fr 1fr; }
    .marker-collage.n3 .marker-thumb:first-child { grid-row: 1 / 3; }
    .marker-thumb {
      position: relative; overflow: hidden; cursor: pointer;
      background: #e8edf3; padding: 0; border: none;
      -webkit-tap-highlight-color: transparent;
    }
    .marker-thumb img {
      width: 100%; height: 100%; object-fit: cover; display: block;
      pointer-events: none; user-select: none; -webkit-user-drag: none;
    }
    .marker-thumb:active { filter: brightness(0.85); }

    .marker-dot {
      width: 10px; height: 10px; border-radius: 50%;
      background: #4a90d9; border: 2px solid #fff;
      box-shadow: 0 2px 4px rgba(0,0,0,0.3);
      margin-top: -3px;
    }
    .marker-label {
      margin-top: 2px; padding: 2px 8px; border-radius: 10px;
      background: rgba(0,0,0,0.7); color: #fff; font-size: 11px;
      white-space: nowrap; max-width: 100px; overflow: hidden; text-overflow: ellipsis;
    }

    /* ===== 上传选点：蓝色标记 ===== */
    .pick-marker {
      position: relative; width: 0; height: 0;
      touch-action: none; cursor: grab;
    }
    .pick-marker-dot {
      position: absolute; left: -12px; top: -12px;
      width: 24px; height: 24px; border-radius: 50%;
      background: #1a73e8; border: 3px solid #fff;
      box-shadow: 0 2px 10px rgba(26,115,232,0.55);
      z-index: 2; box-sizing: border-box;
    }
    .pick-marker-pulse {
      position: absolute; left: -24px; top: -24px;
      width: 48px; height: 48px; border-radius: 50%;
      background: rgba(26,115,232,0.30);
      animation: pickPulse 1.6s ease-out infinite;
    }
    @keyframes pickPulse {
      0% { transform: scale(0.45); opacity: 1; }
      100% { transform: scale(1.35); opacity: 0; }
    }
  `
  document.head.appendChild(style)
}
