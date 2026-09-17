<template>
  <!-- 遮罩层 -->
  <div class="overlay" :class="{ show: show }" @click="handleClose"></div>

  <!-- 面板容器：PC 右侧滑出 / 手机底部抽屉 -->
  <div class="spot-panel" :class="{ open: show, mobile: isMobile }">
    <!-- 拖拽手柄（仅移动端） -->
    <div v-if="isMobile" class="drag-handle" @touchstart="onDragStart" @touchmove="onDragMove" @touchend="onDragEnd">
      <div class="handle-bar"></div>
    </div>

    <!-- 头部 -->
    <div class="panel-header">
      <h3>{{ title }}</h3>
      <button class="close-btn" @click="handleClose">✕</button>
    </div>

    <!-- 内容区 -->
    <div class="panel-body" ref="panelBody">
      <!-- 创建模式 -->
      <SpotEditForm
        v-if="isCreating"
        :initial="createFormData"
        mode="create"
        @submit="onCreate"
        @cancel="handleClose"
      />

      <!-- 详情 + 编辑模式 -->
      <template v-else-if="spot">
        <SpotInfo v-if="!editing" :spot="spot" @edit="editing = true" />
        <SpotEditForm
          v-else
          :initial="editFormData"
          mode="edit"
          @submit="onUpdate"
          @cancel="editing = false"
        />

        <!-- 操作按钮（非编辑态） -->
        <div v-if="!editing" style="display:flex;gap:8px;margin:12px 0;">
          <button class="btn btn-danger btn-sm" @click="onDelete">🗑 删除打卡点</button>
        </div>

        <!-- 照片区域 -->
        <div class="section-title">🖼️ 照片列表 <span class="count">{{ photos.length }} 张</span></div>
        <PhotoUploader v-if="!editing" :spot-id="spot.id" @upload="onUpload" />
        <PhotoGrid
          v-if="!editing && photos.length > 0"
          :photos="photos"
          @delete="onDeletePhoto"
          @preview="onPreviewPhoto"
        />
        <div v-else-if="!editing && photos.length === 0" class="empty-state">
          <div class="icon">📷</div>
          <p>还没有照片，上传第一张吧</p>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import SpotInfo from './SpotInfo.vue'
import SpotEditForm from './SpotEditForm.vue'
import PhotoGrid from './PhotoGrid.vue'
import PhotoUploader from './PhotoUploader.vue'

const props = defineProps({
  spot: { type: Object, default: null },
  photos: { type: Array, default: () => [] },
  isCreating: { type: Boolean, default: false },
  initialCoord: { type: Object, default: () => ({}) },
  autoEdit: { type: Boolean, default: false }
})

const emit = defineEmits([
  'close', 'created', 'updated', 'deleted',
  'upload', 'deletePhoto', 'previewPhoto'
])

const editing = ref(false)
const show = ref(false)
const panelBody = ref(null)

// ===== 移动端检测 =====
const isMobile = ref(false)
function detectMobile() {
  isMobile.value = window.innerWidth <= 768
}
onMounted(() => { detectMobile(); window.addEventListener('resize', detectMobile) })
onBeforeUnmount(() => window.removeEventListener('resize', detectMobile))

// 挂载后触发动画
onMounted(() => {
  nextTick(() => { show.value = true })
})

watch([() => props.spot, () => props.isCreating], () => { editing.value = false })

// ===== 拖拽关闭（仅移动端底部抽屉） =====
let dragStartY = 0
let dragStartTranslate = 0
let isDragging = false

function onDragStart(e) {
  if (!isMobile.value) return
  isDragging = true
  dragStartY = e.touches[0].clientY
  // 读取当前 translateY
  const panel = e.currentTarget.closest('.spot-panel')
  const style = window.getComputedStyle(panel)
  const matrix = new DOMMatrix(style.transform)
  dragStartTranslate = matrix.m42
}

function onDragMove(e) {
  if (!isDragging) return
  const dy = e.touches[0].clientY - dragStartY
  if (dy < 0) return // 不允许向上拖出屏幕
  const panel = e.currentTarget.closest('.spot-panel')
  panel.style.transform = `translateY(${dy}px)`
}

function onDragEnd(e) {
  if (!isDragging) return
  isDragging = false
  const dy = e.changedTouches[0].clientY - dragStartY
  const panel = e.currentTarget.closest('.spot-panel')
  // 拖拽超过 100px 关闭，否则回弹
  if (dy > 100) {
    handleClose()
  } else {
    panel.style.transition = 'transform 0.3s cubic-bezier(0.4, 0, 0.2, 1)'
    panel.style.transform = 'translateY(0)'
    setTimeout(() => {
      panel.style.transition = ''
      panel.style.transform = ''
    }, 300)
  }
}

function handleClose() {
  show.value = false
  // 动画结束后通知父组件
  setTimeout(() => emit('close'), 350)
}

// Computed
const title = computed(() => {
  if (props.isCreating) return '新建打卡点'
  return props.spot?.name || '打卡点详情'
})

const createFormData = computed(() => ({
  name: '',
  lng: props.initialCoord.lng || 0,
  lat: props.initialCoord.lat || 0,
  category: 'scenic',
  tags: '',
  description: '',
  address: props.initialCoord.address || '',
  province: props.initialCoord.province || '',
  city: props.initialCoord.city || '',
  district: props.initialCoord.district || ''
}))

const editFormData = computed(() => {
  if (!props.spot) return {}
  return {
    id: props.spot.id,
    name: props.spot.name || '',
    lng: props.spot.lng || 0,
    lat: props.spot.lat || 0,
    category: props.spot.category || 'scenic',
    tags: props.spot.tags || '',
    description: props.spot.description || '',
    address: props.spot.address || '',
    province: props.spot.province || '',
    city: props.spot.city || '',
    district: props.spot.district || ''
  }
})

// Events
function onCreate(data) { emit('created', data) }
function onUpdate(data) { emit('updated', data); editing.value = false }
function onDelete() {
  if (confirm(`确定删除打卡点「${props.spot?.name}」及其所有照片吗？此操作不可恢复。`)) {
    emit('deleted', props.spot.id)
  }
}
function onUpload(files) { emit('upload', files, props.spot.id) }
function onDeletePhoto(photoId) {
  if (confirm('确定删除这张照片吗？')) {
    emit('deletePhoto', photoId)
  }
}
function onPreviewPhoto(photo) {
  emit('previewPhoto', photo)
}
</script>

<style scoped>
/* ===== 遮罩 ===== */
.overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.3); z-index: 199;
  opacity: 0; pointer-events: none; transition: opacity 0.35s;
}
.overlay.show { opacity: 1; pointer-events: auto; }

/* ===== 面板基础 ===== */
.spot-panel {
  position: fixed; top: 0; height: 100vh;
  background: #fff; box-shadow: -4px 0 24px rgba(0,0,0,0.12);
  z-index: 200; display: flex; flex-direction: column;
  transition: right 0.35s cubic-bezier(0.4, 0, 0.2, 1),
              bottom 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

/* ===== 拖拽手柄（移动端） ===== */
.drag-handle {
  display: flex; justify-content: center; padding: 10px 0 4px;
  cursor: grab; flex-shrink: 0; touch-action: none;
}
.handle-bar {
  width: 36px; height: 4px; background: #ddd; border-radius: 2px;
}

/* ===== PC 端：右侧滑出 ===== */
.spot-panel:not(.mobile) {
  right: -420px; width: 400px;
}
.spot-panel:not(.mobile).open { right: 0; }

/* ===== 移动端：底部抽屉 ===== */
.spot-panel.mobile {
  right: 0; left: 0; bottom: -90vh; height: 90vh; top: auto;
  border-radius: 16px 16px 0 0;
  /* 安全区域 */
  padding-bottom: env(safe-area-inset-bottom);
}
.spot-panel.mobile.open { bottom: 0; }

.panel-header {
  padding: 12px 20px;
  border-bottom: 1px solid #eee;
  display: flex; align-items: center; justify-content: space-between;
  flex-shrink: 0;
}
.spot-panel.mobile .panel-header { padding: 8px 20px 12px; }

.panel-header h3 { font-size: 17px; font-weight: 600; color: #222; }
.close-btn {
  width: 32px; height: 32px; border-radius: 50%; border: none; background: #f5f5f5;
  cursor: pointer; font-size: 18px; color: #666;
  display: flex; align-items: center; justify-content: center;
  /* 移动端增大触摸区域 */
  min-width: 44px; min-height: 44px;
}
.close-btn:hover { background: #e0e0e0; }

.panel-body {
  flex: 1; overflow-y: auto; padding: 16px 20px;
  -webkit-overflow-scrolling: touch;
}
.panel-body::-webkit-scrollbar { width: 4px; }
.panel-body::-webkit-scrollbar-thumb { background: #ddd; border-radius: 2px; }

/* ===== 共用样式 ===== */
.section-title {
  font-size: 14px; font-weight: 600; color: #333; margin: 16px 0 8px;
  padding-bottom: 6px; border-bottom: 1.5px solid #f0f0f0;
  display: flex; align-items: center; justify-content: space-between;
}
.section-title .count { font-size: 12px; color: #999; font-weight: 400; }

.btn {
  padding: 8px 18px; border-radius: 8px; border: none; cursor: pointer;
  font-size: 14px; font-weight: 500; display: inline-flex; align-items: center; gap: 4px;
  /* 移动端最低触摸尺寸 */
  min-height: 44px;
}
.btn-primary { background: #4a90d9; color: #fff; }
.btn-primary:hover { background: #3a7bc8; }
.btn-danger { background: #fff; color: #e74c3c; border: 1px solid #e74c3c; }
.btn-danger:hover { background: #e74c3c; color: #fff; }
.btn-sm { padding: 5px 12px; font-size: 12px; min-height: 36px; }

.empty-state { text-align: center; padding: 30px 20px; color: #ccc; }
.empty-state .icon { font-size: 48px; margin-bottom: 8px; }
</style>
