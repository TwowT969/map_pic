import { fileUrl } from '../api/index.js'

// 构建自定义 Marker 的 HTMLElement
// createMarkerElement：详情视图（>=14 级）—— 照片缩略图 + 圆点 + 名称
// createSimpleMarkerElement：远视图（<14 级）—— 纯图标 + 照片数，不加载任何图片
export function createMarkerElement(spot, photo) {
  const wrapper = document.createElement('div')
  wrapper.className = 'custom-marker'

  // 照片缩略图区域
  const photoWrap = document.createElement('div')
  photoWrap.className = 'marker-photo-wrap'

  if (photo && (photo.thumbUrl || photo.url)) {
    const img = document.createElement('img')
    img.src = fileUrl(photo.thumbUrl || photo.url)
    img.style.cssText = 'width:100%;height:100%;object-fit:cover'
    img.onerror = () => {
      img.style.display = 'none'
      photoWrap.innerHTML = '<div class="marker-no-photo">📍</div>'
    }
    photoWrap.appendChild(img)
  } else {
    photoWrap.innerHTML = '<div class="marker-no-photo">📍</div>'
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

/**
 * 远视图简化标记：📍 图标 + 照片数角标（无 <img>，零图片请求）。
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
    }
    .custom-marker:hover { transform: scale(1.08); z-index: 10; }
    .marker-photo-wrap {
      width: 48px; height: 48px; border-radius: 8px; overflow: hidden;
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
  `
  document.head.appendChild(style)
}
