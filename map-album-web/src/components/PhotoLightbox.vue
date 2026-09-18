<template>
  <Transition name="lightbox">
    <div v-if="visible" class="photo-lightbox" @click.self="$emit('close')">
      <!-- 关闭按钮 -->
      <button class="lb-close" @click="$emit('close')">&times;</button>

      <!-- 上一张按钮 -->
      <button v-if="hasPrev" class="lb-nav lb-prev" @click.stop="goPrev">
        <span class="arrow">‹</span>
      </button>

      <!-- 下一张按钮 -->
      <button v-if="hasNext" class="lb-nav lb-next" @click.stop="goNext">
        <span class="arrow">›</span>
      </button>

      <!-- 图片容器（手势：滑动翻页 / 双击缩放 / 双指捏合 / 缩放后拖动 / 下滑关闭） -->
      <div
        class="lb-image-wrapper"
        ref="wrapper"
        @touchstart="onTouchStart"
        @touchmove="onTouchMove"
        @touchend="onTouchEnd"
      >
        <img v-if="!imgBroken" :src="currentSrc" alt="大图" :style="imgStyle" @click.stop draggable="false" @error="imgBroken = true" />
        <div v-else style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:#9aa7b5;font-size:14px;">🖼️ 图片无法显示</div>
      </div>

      <!-- 备注编辑条（可编辑模式） -->
      <div class="lb-editbar" v-if="editable && current && current.id" @click.stop>
        <template v-if="!editing">
          <div class="lb-desc" @click="startEdit">{{ current.description || '（无备注，点击添加）' }}</div>
          <button class="lb-act" @click="startEdit" title="编辑备注">✎</button>
          <button class="lb-act danger" @click="onDelete" title="删除照片">🗑</button>
        </template>
        <template v-else>
          <input
            ref="editInput"
            v-model="editText"
            class="lb-edit-input"
            maxlength="200"
            placeholder="输入备注（200 字内）"
            @keyup.enter="saveEdit"
          />
          <button class="lb-act primary" @click="saveEdit">保存</button>
          <button class="lb-act" @click="cancelEdit">取消</button>
        </template>
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
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { fileUrl } from '../api/index.js'

const props = defineProps({
  photos: { type: Array, default: () => [] },   // 完整照片对象 [{ id, url, thumbUrl, description, ... }]
  index: { type: Number, default: 0 },
  visible: { type: Boolean, default: false },
  editable: { type: Boolean, default: false }   // 显示备注编辑/删除
})

const emit = defineEmits(['close', 'update:index', 'save-desc', 'delete-photo'])
const wrapper = ref(null)

// 当前照片
const current = computed(() => props.photos[props.index] || null)

// 当前图片 URL
const currentSrc = computed(() => {
  const photo = props.photos[props.index]
  if (!photo) return ''
  if (typeof photo === 'string') return fileUrl(photo)
  return photo.src || photo.thumbSrc || fileUrl(photo.url || photo.thumbUrl || '')
})

// 主图加载失败兜底（本地文件丢失且无远程 URL 时避免破图）
const imgBroken = ref(false)
watch(currentSrc, () => { imgBroken.value = false })

// 缩略图 URL
function thumbnailOf(photo) {
  if (!photo) return ''
  if (typeof photo === 'string') return fileUrl(photo)
  return photo.thumbSrc || photo.src || fileUrl(photo.thumbUrl || photo.url || '')
}

const total = computed(() => props.photos.length)
const hasPrev = computed(() => props.index > 0)
const hasNext = computed(() => props.index < total.value - 1)

function goPrev() {
  if (hasPrev.value) emit('update:index', props.index - 1)
}

function goNext() {
  if (hasNext.value) emit('update:index', props.index + 1)
}

// ===== 缩放/拖动状态 =====
const scale = ref(1)
const tx = ref(0)
const ty = ref(0)
const transitioning = ref(false)
let gesture = null
let lastTapTime = 0
let lastTapX = 0
let lastTapY = 0

const imgStyle = computed(() => ({
  transform: `translate(${tx.value}px, ${ty.value}px) scale(${scale.value})`,
  transition: transitioning.value ? 'transform 0.2s ease' : 'none'
}))

function resetTransform() {
  transitioning.value = true
  scale.value = 1
  tx.value = 0
  ty.value = 0
  setTimeout(() => { transitioning.value = false }, 220)
}

function clamp(v, min, max) { return Math.min(max, Math.max(min, v)) }

function touchDist(a, b) {
  const dx = a.clientX - b.clientX
  const dy = a.clientY - b.clientY
  return Math.sqrt(dx * dx + dy * dy) || 1
}

function toggleZoom() {
  if (scale.value > 1) {
    resetTransform()
  } else {
    transitioning.value = true
    scale.value = 2.5
    tx.value = 0
    ty.value = 0
    setTimeout(() => { transitioning.value = false }, 220)
  }
}

// ===== 触摸手势（翻页 + 缩放 + 拖动 + 下滑关闭） =====
let touchStartX = 0
let touchStartY = 0
let touchMoved = false
let touchSwiped = false

function onTouchStart(e) {
  if (e.touches.length === 2) {
    const a = e.touches[0]
    const b = e.touches[1]
    gesture = {
      type: 'pinch',
      startDist: touchDist(a, b),
      startScale: scale.value,
      startMidX: (a.clientX + b.clientX) / 2,
      startMidY: (a.clientY + b.clientY) / 2
    }
    transitioning.value = false
    return
  }
  const t = e.touches[0]
  // 双击检测 → 缩放切换
  const now = Date.now()
  if (now - lastTapTime < 320 && Math.abs(t.clientX - lastTapX) < 36 && Math.abs(t.clientY - lastTapY) < 36) {
    lastTapTime = 0
    toggleZoom()
    gesture = null
    return
  }
  lastTapTime = now
  lastTapX = t.clientX
  lastTapY = t.clientY

  touchStartX = t.clientX
  touchStartY = t.clientY
  touchMoved = false
  touchSwiped = false
  gesture = { type: scale.value > 1 ? 'pan' : 'swipe', lastX: t.clientX, lastY: t.clientY }
  transitioning.value = false
}

function onTouchMove(e) {
  if (!gesture) return
  if (gesture.type === 'pinch') {
    if (e.touches.length === 2) {
      const a = e.touches[0]
      const b = e.touches[1]
      const d = touchDist(a, b)
      scale.value = clamp(gesture.startScale * (d / gesture.startDist), 1, 4)
      const mx = (a.clientX + b.clientX) / 2
      const my = (a.clientY + b.clientY) / 2
      tx.value += (mx - gesture.startMidX) * 0.6
      ty.value += (my - gesture.startMidY) * 0.6
      gesture.startMidX = mx
      gesture.startMidY = my
    }
    return
  }
  if (e.touches.length !== 1) return
  const t = e.touches[0]
  if (gesture.type === 'pan') {
    tx.value += t.clientX - gesture.lastX
    ty.value += t.clientY - gesture.lastY
    gesture.lastX = t.clientX
    gesture.lastY = t.clientY
    return
  }
  // swipe 检测
  const dx = t.clientX - touchStartX
  const dy = t.clientY - touchStartY
  if (Math.abs(dx) > 10 || Math.abs(dy) > 10) touchMoved = true
}

function onTouchEnd(e) {
  if (!gesture) return
  if (gesture.type === 'pinch') {
    gesture = null
    if (scale.value <= 1.02) resetTransform()
    return
  }
  if (gesture.type === 'pan') {
    gesture = null
    if (scale.value <= 1.02) resetTransform()
    return
  }
  // swipe
  gesture = null
  if (!touchMoved || scale.value > 1) return
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

// ===== 备注编辑 / 删除 =====
const editing = ref(false)
const editText = ref('')
const editInput = ref(null)

function startEdit() {
  editText.value = current.value?.description || ''
  editing.value = true
  nextTick(() => editInput.value && editInput.value.focus())
}

function cancelEdit() {
  editing.value = false
}

function saveEdit() {
  if (!current.value) return
  emit('save-desc', current.value, editText.value.trim())
  editing.value = false
}

function onDelete() {
  if (!current.value) return
  if (confirm('确定删除这张照片吗？删除后不可恢复。')) {
    emit('delete-photo', current.value)
  }
}

// 切换照片时退出编辑态并复位缩放
watch(() => props.index, () => {
  editing.value = false
  resetTransform()
})
watch(() => props.visible, (v) => {
  if (!v) { editing.value = false; resetTransform() }
})

// ===== 键盘事件 =====
function onKeyDown(e) {
  if (!props.visible) return
  if (editing.value) return
  if (e.key === 'ArrowLeft') { e.preventDefault(); goPrev() }
  if (e.key === 'ArrowRight') { e.preventDefault(); goNext() }
  if (e.key === 'Escape') { emit('close') }
}

onMounted(() => document.addEventListener('keydown', onKeyDown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeyDown))
</script>

<style scoped>
.photo-lightbox {
  position: fixed; inset: 0; background: rgba(0,0,0,0.92);
  z-index: 300; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
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
.lb-prev { left: max(12px, env(safe-area-inset-left)); }
.lb-next { right: max(12px, env(safe-area-inset-right)); }
.arrow { font-size: 36px; line-height: 1; }

/* 图片容器 */
.lb-image-wrapper {
  flex: 1; display: flex; align-items: center; justify-content: center;
  overflow: hidden; max-width: 100%; width: 100%;
  touch-action: none; /* 手势全部接管（缩放/拖动/翻页） */
}
.lb-image-wrapper img {
  max-width: 95vw; max-height: 70vh;
  object-fit: contain; border-radius: 4px;
  user-select: none; -webkit-user-drag: none;
  will-change: transform;
}

/* 备注编辑条 */
.lb-editbar {
  position: absolute;
  bottom: max(120px, calc(env(safe-area-inset-bottom) + 100px));
  left: 50%; transform: translateX(-50%);
  width: min(560px, 92vw);
  display: flex; align-items: center; gap: 8px;
  background: rgba(0,0,0,0.55); border-radius: 10px;
  padding: 8px 12px; z-index: 3;
}
.lb-desc {
  flex: 1; min-width: 0; color: #eee; font-size: 13px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  cursor: text;
}
.lb-act {
  flex-shrink: 0; width: 34px; height: 34px; border-radius: 8px;
  border: none; background: rgba(255,255,255,0.16); color: #fff;
  font-size: 15px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  -webkit-tap-highlight-color: transparent;
}
.lb-act:active { background: rgba(255,255,255,0.3); }
.lb-act.danger { color: #ff8f8f; }
.lb-act.primary { background: #1a73e8; width: auto; padding: 0 12px; font-size: 13px; }
.lb-edit-input {
  flex: 1; min-width: 0; height: 34px;
  border: 1px solid rgba(255,255,255,0.3); border-radius: 8px;
  background: rgba(255,255,255,0.12); color: #fff;
  font-size: 14px; padding: 0 10px; outline: none;
}
.lb-edit-input::placeholder { color: rgba(255,255,255,0.5); }

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
.lb-thumb img { width: 100%; height: 100%; object-fit: cover; }

/* 动画 */
.lightbox-enter-active { animation: lb-in 0.3s ease; }
.lightbox-leave-active { animation: lb-out 0.3s ease; }
@keyframes lb-in { from { opacity: 0; } to { opacity: 1; } }
@keyframes lb-out { from { opacity: 1; } to { opacity: 0; } }

/* 移动端隐藏左右箭头（用手势滑动） */
@media (max-width: 768px) {
  .lb-nav { display: none; }
}
</style>
