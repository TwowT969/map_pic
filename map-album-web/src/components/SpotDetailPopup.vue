<template>
  <div class="sdp-mask" @click.self="$emit('close')">
    <div class="sdp-card">
      <div class="sdp-header">
        <span class="sdp-type">点位</span>
        <span class="sdp-name" :title="spot?.name">{{ spot?.name || '未命名点位' }}</span>
        <button class="sdp-icon-btn" title="管理点位" @click="$emit('manage')">✎</button>
        <button class="sdp-icon-btn" title="关闭" @click="$emit('close')">✕</button>
      </div>
      <div class="sdp-tags" v-if="tagList.length">
        <span class="sdp-tag" v-for="(t, i) in tagList" :key="i">{{ t }}</span>
      </div>

      <div class="sdp-body">
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
          <p>这个点位还没有照片</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, inject } from 'vue'
import { fileUrl } from '../api/index.js'

const props = defineProps({
  spot: { type: Object, required: true },
  photos: { type: Array, default: () => [] }
})

const emit = defineEmits(['close', 'manage'])

const openLightbox = inject('openLightbox', null)

const tagList = computed(() =>
  (props.spot?.tags || '').split(/[,，、]/).map(t => t.trim()).filter(Boolean)
)

function imgOf(p) { return fileUrl(p.thumbUrl || p.url) }
function onImgError(e) { e.target.style.display = 'none' }

/** 点击图片 → 大图浏览（可左右滑动，当前点位照片集合） */
function openPhoto(i) {
  if (openLightbox) openLightbox(props.photos, i)
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
}
.sdp-type {
  flex-shrink: 0;
  font-size: 12px; font-weight: 600; color: #fff;
  background: #1a73e8; border-radius: 6px;
  padding: 2px 8px; line-height: 18px;
}
.sdp-name {
  flex: 1; min-width: 0;
  font-size: 16px; font-weight: 600; color: #222;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.sdp-icon-btn {
  flex-shrink: 0;
  width: 34px; height: 34px; border-radius: 8px; border: none;
  background: #f2f4f7; color: #555; font-size: 15px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  -webkit-tap-highlight-color: transparent;
}
.sdp-icon-btn:active { background: #e7eaf0; }

.sdp-tags {
  display: flex; flex-wrap: wrap; gap: 6px;
  padding: 0 16px 8px; flex-shrink: 0;
}
.sdp-tag {
  font-size: 11px; color: #1a73e8;
  background: #eaf2ff; border-radius: 10px;
  padding: 2px 9px; line-height: 16px;
}

.sdp-body { overflow-y: auto; padding: 4px 16px 16px; -webkit-overflow-scrolling: touch; }

.sdp-grid {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px;
}
@media (max-width: 768px) {
  .sdp-grid { grid-template-columns: repeat(2, 1fr); }
}

.sdp-item { cursor: pointer; -webkit-tap-highlight-color: transparent; }
.sdp-imgwrap {
  position: relative; aspect-ratio: 1;
  border-radius: 10px; overflow: hidden; background: #f0f0f0;
  border: 1.5px solid #eef2f7;
}
.sdp-imgwrap img {
  width: 100%; height: 100%; object-fit: cover; display: block;
}
.sdp-imgtag {
  position: absolute; top: 5px; left: 5px;
  font-size: 10px; line-height: 14px;
  color: #fff; background: rgba(26,115,232,0.85);
  border-radius: 4px; padding: 0 5px;
  pointer-events: none;
}
.sdp-item:active .sdp-imgwrap { border-color: #1a73e8; }

.sdp-remark {
  margin-top: 4px;
  font-size: 12px; line-height: 16px; color: #444;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden; word-break: break-all;
}
.sdp-remark.none { color: #b9c0cb; }

.sdp-empty { text-align: center; padding: 34px 0 26px; color: #9aa4b2; font-size: 13px; }
.sdp-empty-icon { font-size: 42px; margin-bottom: 8px; }
</style>
