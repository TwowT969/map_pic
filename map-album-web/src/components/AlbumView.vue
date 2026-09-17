<template>
  <div class="album-view">
    <div class="av-header">
      <button class="av-back" @click="$emit('back')">🗺️ 地图</button>
      <div class="av-title">相册 <span class="av-count" v-if="total">共 {{ total }} 张</span></div>
    </div>
    <div class="av-body">
      <div v-for="m in months" :key="m.key" class="av-month">
        <div class="av-month-title">{{ m.label }} <span class="av-month-count">{{ m.photos.length }} 张</span></div>
        <div class="av-grid">
          <div class="av-item" v-for="(p, i) in m.photos" :key="p.id" @click="open(m.photos, i)">
            <img :src="thumbOf(p)" loading="lazy" @error="onImgError" />
            <span class="av-date">{{ dayOf(p) }}</span>
          </div>
        </div>
      </div>
      <div v-if="!months.length" class="av-empty">
        <div class="av-empty-icon">📷</div>
        <p>还没有照片</p>
        <span>去地图拍下第一张吧</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, inject } from 'vue'
import { fileUrl } from '../api/index.js'
import { photoTimeOf } from '../composables/usePhotos.js'

defineEmits(['back'])

const photosStore = inject('photosStore')
const openLightbox = inject('openLightbox', null)

const total = computed(() => photosStore.allPhotos.value.length)

/** 按月分组（拍摄时间优先，倒序） */
const months = computed(() => {
  const groups = new Map()
  for (const p of photosStore.allPhotos.value) {
    const t = new Date(photoTimeOf(p))
    const key = `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}`
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(p)
  }
  return Array.from(groups.entries())
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([key, photos]) => ({
      key,
      label: key.replace('-', '年') + '月',
      photos
    }))
})

function thumbOf(p) { return fileUrl(p.thumbUrl || p.url) }
function onImgError(e) { e.target.style.display = 'none' }

function dayOf(p) {
  const t = new Date(photoTimeOf(p))
  if (isNaN(t.getTime())) return ''
  return `${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`
}

/** 点击 → 大图浏览（当月照片集合） */
function open(photos, i) {
  if (openLightbox) openLightbox(photos, i)
}
</script>

<style scoped>
.album-view {
  position: fixed; inset: 0; z-index: 120;
  background: #f7f9fc;
  display: flex; flex-direction: column;
}
.av-header {
  display: flex; align-items: center; gap: 12px;
  padding: max(10px, env(safe-area-inset-top)) 16px 10px;
  background: #fff; border-bottom: 1px solid #eef1f6;
  flex-shrink: 0;
}
.av-back {
  border: none; background: #f2f4f7; color: #333;
  border-radius: 10px; padding: 8px 14px;
  font-size: 14px; cursor: pointer; min-height: 40px;
  -webkit-tap-highlight-color: transparent;
}
.av-back:active { background: #e7eaf0; }
.av-title { font-size: 17px; font-weight: 600; color: #222; }
.av-count { font-size: 12px; color: #999; font-weight: 400; margin-left: 4px; }

.av-body {
  flex: 1; overflow-y: auto; padding: 12px 14px calc(20px + env(safe-area-inset-bottom));
  -webkit-overflow-scrolling: touch;
}
.av-month { margin-bottom: 18px; }
.av-month-title {
  font-size: 15px; font-weight: 600; color: #333;
  margin: 4px 2px 10px;
}
.av-month-count { font-size: 12px; color: #999; font-weight: 400; margin-left: 6px; }

.av-grid {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px;
}
@media (max-width: 768px) {
  .av-grid { grid-template-columns: repeat(3, 1fr); gap: 6px; }
}
.av-item {
  position: relative; aspect-ratio: 1;
  border-radius: 10px; overflow: hidden; background: #eef1f5;
  cursor: pointer; -webkit-tap-highlight-color: transparent;
}
.av-item img { width: 100%; height: 100%; object-fit: cover; display: block; }
.av-item:active img { opacity: 0.85; }
.av-date {
  position: absolute; right: 5px; bottom: 4px;
  font-size: 10px; color: #fff;
  background: rgba(0,0,0,0.45); border-radius: 4px;
  padding: 0 4px; line-height: 14px;
  pointer-events: none;
}

.av-empty { text-align: center; padding: 80px 0 40px; color: #9aa4b2; font-size: 14px; }
.av-empty-icon { font-size: 52px; margin-bottom: 10px; }
.av-empty p { margin: 0 0 6px; color: #555; font-size: 15px; }
</style>
