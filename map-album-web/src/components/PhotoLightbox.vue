<template>
  <Transition name="lightbox">
    <div v-if="visible" class="photo-lightbox" @click.self="$emit('close')">
      <!-- 关闭按钮 -->
      <button class="lb-close" @click="$emit('close')">&times;</button>

      <!-- 上一张按钮 -->
      <button
        v-if="hasPrev"
        class="lb-nav lb-prev"
        @click.stop="goPrev"
      >
        <span class="arrow">‹</span>
      </button>

      <!-- 下一张按钮 -->
      <button
        v-if="hasNext"
        class="lb-nav lb-next"
        @click.stop="goNext"
      >
        <span class="arrow">›</span>
      </button>

      <!-- 图片容器（支持手势滑动） -->
      <div
        class="lb-image-wrapper"
        ref="wrapper"
        @touchstart="onTouchStart"
        @touchmove="onTouchMove"
        @touchend="onTouchEnd"
      >
        <img :src="currentSrc" alt="大图" @click.stop />
      </div>

      <!-- 底部计数 -->
      <div class="lb-counter" v-if="total > 0">
        {{ index + 1 }} / {{ total }}
      </div>

      <!-- 缩略图列表（横滑） -->
      <div class="lb-thumb-strip" v-if="total > 1">
        <div
          v-for="(photo, i) in photos"
          :key="i"
          class="lb-thumb"
          :class="{ active: i === index }"
          @click="index = i"
        >
          <img :src="thumbnailOf(photo)" />
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  photos: { type: Array, default: () => [] },   // [{ url, thumbUrl }] 或字符串数组
  index: { type: Number, default: 0 },
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['close', 'update:index'])
const wrapper = ref(null)

// 当前图片 URL
const currentSrc = computed(() => {
  const photo = props.photos[props.index]
  if (!photo) return ''
  if (typeof photo === 'string') return photo
  return photo.url || photo.thumbUrl || ''
})

// 缩略图 URL
function thumbnailOf(photo) {
  if (!photo) return ''
  if (typeof photo === 'string') return photo
  return photo.thumbUrl || photo.url || ''
}

const total = computed(() => props.photos.length)
const hasPrev = computed(() => props.index > 0)
const hasNext = computed(() => props.index < total.value - 1)

function goPrev() {
  if (hasPrev.value) {
    emit('update:index', props.index - 1)
  }
}

function goNext() {
  if (hasNext.value) {
    emit('update:index', props.index + 1)
  }
}

// ===== 键盘事件 =====
function onKeyDown(e) {
  if (!props.visible) return
  if (e.key === 'ArrowLeft') { e.preventDefault(); goPrev() }
  if (e.key === 'ArrowRight') { e.preventDefault(); goNext() }
  if (e.key === 'Escape') { emit('close') }
}

onMounted(() => document.addEventListener('keydown', onKeyDown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeyDown))

// ===== 触摸手势 =====
let touchStartX = 0
let touchStartY = 0
let touchMoved = false
let touchSwiped = false

function onTouchStart(e) {
  touchStartX = e.touches[0].clientX
  touchStartY = e.touches[0].clientY
  touchMoved = false
  touchSwiped = false
}

function onTouchMove(e) {
  if (!touchSwiped) {
    const dx = e.touches[0].clientX - touchStartX
    const dy = e.touches[0].clientY - touchStartY
    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
      touchMoved = true
    }
  }
}

function onTouchEnd(e) {
  if (!touchMoved) return
  const dx = e.changedTouches[0].clientX - touchStartX
  const dy = e.changedTouches[0].clientY - touchStartY

  // 水平滑动 > 50px → 翻页
  if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 50) {
    touchSwiped = true
    if (dx > 0) goPrev()
    else goNext()
    return
  }

  // 向下滑动 > 80px → 关闭
  if (dy > 80 && Math.abs(dy) > Math.abs(dx) * 1.5) {
    emit('close')
    return
  }
}
</script>

<style scoped>
.photo-lightbox {
  position: fixed; inset: 0; background: rgba(0,0,0,0.92);
  z-index: 300; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  /* 安全区域 */
  padding: env(safe-area-inset-top) env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left);
}

.lb-close {
  position: absolute; top: max(16px, env(safe-area-inset-top));
  right: max(20px, env(safe-area-inset-right));
  color: #fff; font-size: 32px;
  cursor: pointer; background: none; border: none;
  z-index: 2; opacity: 0.8; width: 44px; height: 44px;
  display: flex; align-items: center; justify-content: center;
}
.lb-close:hover { opacity: 1; }

/* 左/右箭头 */
.lb-nav {
  position: absolute; top: 50%; transform: translateY(-50%);
  background: rgba(255,255,255,0.15);
  color: #fff; border: none; cursor: pointer;
  z-index: 2; border-radius: 50%;
  width: 48px; height: 48px;
  display: flex; align-items: center; justify-content: center;
  transition: background 0.2s;
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}
.lb-nav:hover { background: rgba(255,255,255,0.3); }
.lb-nav:active { background: rgba(255,255,255,0.5); }
.lb-prev { left: max(12px, env(safe-area-inset-left)); }
.lb-next { right: max(12px, env(safe-area-inset-right)); }
.arrow { font-size: 36px; line-height: 1; }

/* 图片容器 */
.lb-image-wrapper {
  flex: 1; display: flex; align-items: center; justify-content: center;
  touch-action: pan-y; /* 允许纵向滑动关闭，禁止横向缩放 */
  overflow: hidden; max-width: 100%;
}
.lb-image-wrapper img {
  max-width: 95vw; max-height: 75vh;
  object-fit: contain; border-radius: 4px;
  user-select: none; -webkit-user-drag: none;
}

/* 计数指示器 */
.lb-counter {
  position: absolute; bottom: max(80px, calc(env(safe-area-inset-bottom) + 60px));
  left: 50%; transform: translateX(-50%);
  color: rgba(255,255,255,0.8); font-size: 14px;
  background: rgba(0,0,0,0.5); padding: 4px 14px;
  border-radius: 12px; z-index: 2;
}

/* 底部缩略图条 */
.lb-thumb-strip {
  position: absolute; bottom: max(16px, env(safe-area-inset-bottom));
  left: 0; right: 0;
  display: flex; gap: 6px; justify-content: center;
  padding: 8px 16px; overflow-x: auto;
  z-index: 2; -webkit-overflow-scrolling: touch;
}
.lb-thumb {
  width: 48px; height: 48px; flex-shrink: 0;
  border-radius: 6px; overflow: hidden; cursor: pointer;
  border: 2px solid transparent; transition: border-color 0.2s;
  -webkit-tap-highlight-color: transparent;
}
.lb-thumb.active { border-color: #4a90d9; }
.lb-thumb img {
  width: 100%; height: 100%; object-fit: cover;
}

/* 动画 */
.lightbox-enter-active { animation: lb-in 0.3s ease; }
.lightbox-leave-active { animation: lb-out 0.3s ease; }
@keyframes lb-in { from { opacity: 0; } to { opacity: 1; } }
@keyframes lb-out { from { opacity: 1; } to { opacity: 0; } }

/* 移动端隐藏左右箭头（用手势滑动） */
@media (max-width: 768px) {
  .lb-nav { display: none; }
  .lb-image-wrapper { touch-action: pan-x pan-y; }
}
</style>
