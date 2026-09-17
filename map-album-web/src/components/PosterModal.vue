<template>
  <div class="pm-mask" @click.self="$emit('close')">
    <div class="pm-card">
      <div class="pm-header">
        <span class="pm-title">分享海报</span>
        <div class="pm-tabs">
          <button :class="{ active: style === 'map' }" @click="switchStyle('map')">生活地图</button>
          <button :class="{ active: style === 'wall' }" @click="switchStyle('wall')">胶片墙</button>
        </div>
        <button class="pm-close" @click="$emit('close')">✕</button>
      </div>
      <div class="pm-body">
        <img v-if="previewSrc" :src="previewSrc" class="pm-preview" alt="海报预览" />
        <div v-if="rendering" class="pm-busy">生成中…</div>
      </div>
      <div class="pm-actions">
        <button class="pm-btn ghost" :disabled="rendering || saving" @click="regenerate">换一版</button>
        <button class="pm-btn primary" :disabled="rendering || saving || !previewSrc" @click="save">
          {{ saving ? '处理中…' : '保存 / 分享' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, inject, onMounted } from 'vue'
import { renderPoster } from '../utils/poster.js'
import { hasCapacitor } from '../utils/capacitor.js'

const emit = defineEmits(['close'])

const spotsStore = inject('spotsStore')
const photosStore = inject('photosStore')
const showToast = inject('showToast')

const style = ref('map')
const variant = ref(0)
const previewSrc = ref('')
const rendering = ref(false)
const saving = ref(false)
let canvas = null

async function render() {
  rendering.value = true
  try {
    canvas = await renderPoster(style.value, spotsStore.spots.value, photosStore.photosBySpot.value, variant.value)
    previewSrc.value = canvas.toDataURL('image/png')
  } catch (e) {
    showToast('海报生成失败: ' + (e?.message || e), 'error')
  } finally {
    rendering.value = false
  }
}

function switchStyle(s) {
  if (s === style.value) return
  style.value = s
  render()
}

function regenerate() {
  variant.value++
  render()
}

/** 保存 / 分享：原生走文件+系统分享面板；H5 走 Web Share 或下载 */
async function save() {
  if (!canvas) return
  saving.value = true
  try {
    const dataUrl = canvas.toDataURL('image/png')
    if (hasCapacitor()) {
      const base64 = dataUrl.split(',')[1]
      const { Filesystem, Directory } = await import('@capacitor/filesystem')
      const name = 'poster_' + Date.now() + '.png'
      const w = await Filesystem.writeFile({ path: name, data: base64, directory: Directory.Cache, recursive: true })
      try {
        const { Share } = await import('@capacitor/share')
        await Share.share({
          title: '我的地图相册海报',
          text: '这是我生活的样子',
          files: [w.uri],
          dialogTitle: '分享海报'
        })
      } catch (e) {
        showToast('海报已生成（' + name + '）', 'success')
      }
    } else {
      let shared = false
      try {
        const blob = await (await fetch(dataUrl)).blob()
        const file = new File([blob], 'poster.png', { type: 'image/png' })
        if (navigator.canShare && navigator.canShare({ files: [file] })) {
          await navigator.share({ files: [file], title: '我的地图相册海报' })
          shared = true
        }
      } catch (e) {
        if (e && e.name === 'AbortError') shared = true
      }
      if (!shared) {
        const a = document.createElement('a')
        a.href = dataUrl
        a.download = 'poster_' + Date.now() + '.png'
        document.body.appendChild(a)
        a.click()
        a.remove()
        showToast('海报已保存', 'success')
      }
    }
  } catch (e) {
    showToast('保存失败: ' + (e?.message || e), 'error')
  } finally {
    saving.value = false
  }
}

onMounted(render)
</script>

<style scoped>
.pm-mask {
  position: fixed; inset: 0; z-index: 260;
  background: rgba(0,0,0,0.55);
  display: flex; align-items: center; justify-content: center;
  padding: 16px;
  animation: pmFade 0.2s ease;
}
@keyframes pmFade { from { opacity: 0; } to { opacity: 1; } }

.pm-card {
  background: #fff; border-radius: 16px;
  width: min(400px, 100%);
  max-height: 88vh;
  display: flex; flex-direction: column;
  overflow: hidden;
  box-shadow: 0 12px 40px rgba(0,0,0,0.3);
  animation: pmUp 0.22s ease;
}
@keyframes pmUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }

.pm-header {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}
.pm-title { font-size: 16px; font-weight: 600; color: #222; }

.pm-tabs { display: flex; flex: 1; justify-content: center; gap: 6px; }
.pm-tabs button {
  border: 1.5px solid #e3e8ef; background: #fff; color: #667;
  border-radius: 16px; padding: 5px 13px; font-size: 13px; cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.pm-tabs button.active { background: #1a73e8; border-color: #1a73e8; color: #fff; font-weight: 500; }

.pm-close {
  width: 32px; height: 32px; border-radius: 8px; border: none;
  background: #f2f4f7; color: #555; font-size: 15px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.pm-close:active { background: #e7eaf0; }

.pm-body {
  position: relative; overflow-y: auto;
  padding: 12px 14px;
  -webkit-overflow-scrolling: touch;
}
.pm-preview { width: 100%; border-radius: 12px; display: block; border: 1px solid #eef2f7; }
.pm-busy {
  position: absolute; inset: 12px 14px;
  background: rgba(255,255,255,0.72);
  display: flex; align-items: center; justify-content: center;
  border-radius: 12px; color: #1a73e8; font-size: 15px; font-weight: 500;
}

.pm-actions { display: flex; gap: 10px; padding: 10px 14px calc(12px + env(safe-area-inset-bottom)); flex-shrink: 0; }
.pm-btn {
  flex: 1; min-height: 44px; border-radius: 10px; border: none;
  font-size: 15px; font-weight: 500; cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.pm-btn.ghost { background: #f2f4f7; color: #555; }
.pm-btn.ghost:active { background: #e7eaf0; }
.pm-btn.primary { background: #1a73e8; color: #fff; }
.pm-btn.primary:active { background: #1660c4; }
.pm-btn:disabled { opacity: 0.55; cursor: default; }
</style>
