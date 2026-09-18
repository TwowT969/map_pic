<template>
  <div class="sdp-mask" @click.self="$emit('close')">
    <div class="sdp-card" ref="cardEl">
      <!-- 头部：仅名称 + 张数 + 关闭（点位信息不在此展示；编辑/拖动/备注走长按菜单） -->
      <div
        class="sdp-header"
        @touchstart.passive="onDragStart"
        @touchmove.passive="onDragMove"
        @touchend.passive="onDragEnd"
      >
        <span class="sdp-name" :title="spot?.name">{{ spot?.name || '未命名打卡点' }}</span>
        <span class="sdp-count" v-if="photos.length">{{ photos.length }} 张</span>
        <button class="sdp-icon-btn" title="关闭" @click="$emit('close')">✕</button>
      </div>

      <div class="sdp-body">
        <!-- 直接展示图片列表 -->
        <div v-if="photos.length" class="sdp-grid">
          <div class="sdp-item" v-for="(p, i) in photos" :key="p.id" @click="openPhoto(i)">
            <div class="sdp-imgwrap">
              <img :src="imgOf(p)" loading="lazy" @error="onImgError" />
              <span class="sdp-imgtag">图片</span>
            </div>
            <div class="sdp-remark" :class="{ none: !p.description }">{{ p.description || '（无备注）' }}</div>
          </div>
        </div>
        <div v-else class="sdp-empty">
          <div class="sdp-empty-icon">📷</div>
          <p>这个打卡点还没有照片</p>
          <p class="sdp-empty-hint">长按地图上的点位标记可编辑 / 拖动 / 备注</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { inject, ref } from 'vue'

const props = defineProps({
  spot: { type: Object, required: true },
  photos: { type: Array, default: () => [] }
})

const emit = defineEmits(['close'])

const openLightbox = inject('openLightbox', null)

function imgOf(p) { return p.thumbSrc || p.src || '' }
function onImgError(e) { e.target.style.display = 'none' }

/** 点击图片 → 大图浏览（可缩放、改备注、删除） */
function openPhoto(i) {
  if (openLightbox) openLightbox(props.photos, i)
}

// ===== 下拉关闭（头部区域拖拽） =====
const cardEl = ref(null)
let dragStartY = 0
let dragging = false
let dy = 0

function onDragStart(e) {
  dragging = true
  dragStartY = e.touches[0].clientY
  dy = 0
}

function onDragMove(e) {
  if (!dragging) return
  dy = e.touches[0].clientY - dragStartY
  if (dy > 0 && cardEl.value) {
    cardEl.value.style.transition = 'none'
    cardEl.value.style.transform = `translateY(${dy}px)`
  }
}

function onDragEnd() {
  if (!dragging) return
  dragging = false
  if (dy > 90) {
    emit('close')
    if (cardEl.value) { cardEl.value.style.transform = '' }
  } else if (cardEl.value && dy > 0) {
    cardEl.value.style.transition = 'transform 0.25s ease'
    cardEl.value.style.transform = ''
    setTimeout(() => { if (cardEl.value) cardEl.value.style.transition = '' }, 260)
  }
  dy = 0
}
</script>

<style scoped>
.sdp-mask {
  position: fixed; inset: 0; z-index: 240;
  background: rgba(0,0,0,0.45);
  display: flex; align-items: center; justify-content: center;
  padding: max(16px, env(safe-area-inset-top)) 16px max(16px, env(safe-area-inset-bottom));
  animation: sdpFade 0.2s ease;
}
@keyframes sdpFade { from { opacity: 0; } to { opacity: 1; } }

.sdp-card {
  background: #fff; border-radius: 14px;
  width: min(560px, 100%);
  max-height: 72vh;
  display: flex; flex-direction: column;
  overflow: hidden;
  box-shadow: 0 12px 40px rgba(0,0,0,0.25);
  animation: sdpUp 0.22s ease;
}
@keyframes sdpUp { from { transform: translateY(24px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }

.sdp-header {
  display: flex; align-items: center; gap: 8px;
  padding: 14px 16px 10px;
  flex-shrink: 0;
  touch-action: none; /* 头部接管下拉手势 */
  cursor: grab;
}
.sdp-name {
  flex: 1; min-width: 0;
  font-size: 16px; font-weight: 600; color: #222;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.sdp-count {
  flex-shrink: 0;
  font-size: 12px; color: #98a4b3;
  background: #f1f4f8; border-radius: 10px;
  padding: 2px 8px; line-height: 18px;
}
.sdp-icon-btn {
  flex-shrink: 0;
  width: 32px; height: 32px;
  border: none; border-radius: 50%;
  background: #f1f4f8; color: #5a6572;
  font-size: 15px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  -webkit-tap-highlight-color: transparent;
}
.sdp-icon-btn:active { background: #e3e9f0; }

.sdp-body {
  padding: 4px 16px 16px;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.sdp-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.sdp-item { cursor: pointer; }
.sdp-imgwrap {
  position: relative;
  width: 100%; padding-top: 100%;
  border-radius: 10px; overflow: hidden;
  background: #f1f4f8;
}
.sdp-imgwrap img {
  position: absolute; inset: 0;
  width: 100%; height: 100%; object-fit: cover;
}
.sdp-imgtag {
  position: absolute; left: 6px; top: 6px;
  font-size: 10px; color: #fff;
  background: rgba(26,115,232,0.85);
  border-radius: 4px; padding: 1px 5px;
}
.sdp-remark {
  margin-top: 4px;
  font-size: 12px; color: #5a6572;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.sdp-remark.none { color: #b9c3cf; }

.sdp-empty { text-align: center; padding: 36px 0 28px; color: #98a4b3; }
.sdp-empty-icon { font-size: 44px; margin-bottom: 10px; }
.sdp-empty p { margin: 4px 0; font-size: 14px; }
.sdp-empty-hint { font-size: 12px; color: #b9c3cf; }
</style>
