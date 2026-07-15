<template>
  <Transition name="panel">
    <div v-if="visible" class="side-panel" :class="{ open: visible }">
      <div class="panel-header">
        <h3>{{ title }}</h3>
        <button class="close-btn" @click="$emit('close')">✕</button>
      </div>
      <div class="panel-body">
        <!-- 创建模式 -->
        <SpotEditForm
          v-if="isCreating"
          :initial="createFormData"
          mode="create"
          @submit="onCreate"
          @cancel="$emit('close')"
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

          <!-- 删除按钮（非编辑态） -->
          <div v-if="!editing" style="display:flex;gap:8px;margin:12px 0;">
            <button class="btn btn-danger btn-sm" @click="onDelete">🗑 删除点位</button>
          </div>

          <!-- 照片区域 -->
          <div class="section-title">🖼️ 照片列表 <span class="count">{{ photos.length }} 张</span></div>
          <PhotoUploader v-if="!editing" :spot-id="spot.id" @upload="onUpload" />
          <PhotoGrid
            v-if="!editing && photos.length > 0"
            :photos="photos"
            @delete="onDeletePhoto"
          />
          <div v-else-if="!editing && photos.length === 0" class="empty-state">
            <div class="icon">📷</div>
            <p>还没有照片，上传第一张吧</p>
          </div>
        </template>
      </div>
    </div>
  </Transition>
  <!-- 遮罩 -->
  <div v-if="visible" class="overlay" @click="$emit('close')"></div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import SpotInfo from './SpotInfo.vue'
import SpotEditForm from './SpotEditForm.vue'
import PhotoGrid from './PhotoGrid.vue'
import PhotoUploader from './PhotoUploader.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  spot: { type: Object, default: null },
  photos: { type: Array, default: () => [] },
  isCreating: { type: Boolean, default: false },
  initialCoord: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['close', 'created', 'updated', 'deleted', 'upload', 'deletePhoto'])

const editing = ref(false)

// spot 或 isCreating 变化时退出编辑模式
watch(() => [props.spot, props.isCreating], () => { editing.value = false })

const title = computed(() => {
  if (props.isCreating) return '新建点位'
  return props.spot?.name || '点位详情'
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

function onCreate(data) { emit('created', data) }
function onUpdate(data) { emit('updated', data); editing.value = false }
function onDelete() {
  if (confirm(`确定删除点位「${props.spot?.name}」及其所有照片吗？此操作不可恢复。`)) {
    emit('deleted', props.spot.id)
  }
}
function onUpload(files) { emit('upload', files, props.spot.id) }
function onDeletePhoto(photoId) {
  if (confirm('确定删除这张照片吗？')) {
    emit('deletePhoto', photoId)
  }
}
</script>

<style scoped>
.side-panel {
  position: fixed; top: 0; right: -420px; width: 400px; height: 100vh;
  background: #fff; box-shadow: -4px 0 24px rgba(0,0,0,0.12);
  z-index: 200; transition: right 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex; flex-direction: column;
}
.side-panel.open { right: 0; }
.panel-header {
  padding: 16px 20px; border-bottom: 1px solid #eee;
  display: flex; align-items: center; justify-content: space-between;
  flex-shrink: 0;
}
.panel-header h3 { font-size: 17px; font-weight: 600; color: #222; }
.close-btn {
  width: 32px; height: 32px; border-radius: 50%; border: none; background: #f5f5f5;
  cursor: pointer; font-size: 18px; color: #666;
  display: flex; align-items: center; justify-content: center;
}
.close-btn:hover { background: #e0e0e0; }
.panel-body {
  flex: 1; overflow-y: auto; padding: 16px 20px;
}
.panel-body::-webkit-scrollbar { width: 4px; }
.panel-body::-webkit-scrollbar-thumb { background: #ddd; border-radius: 2px; }

.section-title {
  font-size: 14px; font-weight: 600; color: #333; margin: 16px 0 8px;
  padding-bottom: 6px; border-bottom: 1.5px solid #f0f0f0;
  display: flex; align-items: center; justify-content: space-between;
}
.section-title .count { font-size: 12px; color: #999; font-weight: 400; }

.btn {
  padding: 8px 18px; border-radius: 8px; border: none; cursor: pointer;
  font-size: 14px; font-weight: 500; display: inline-flex; align-items: center; gap: 4px;
}
.btn-primary { background: #4a90d9; color: #fff; }
.btn-primary:hover { background: #3a7bc8; }
.btn-danger { background: #fff; color: #e74c3c; border: 1px solid #e74c3c; }
.btn-danger:hover { background: #e74c3c; color: #fff; }
.btn-sm { padding: 5px 12px; font-size: 12px; }
.btn-block { width: 100%; justify-content: center; margin-top: 6px; }

.overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.3); z-index: 199;
}
.empty-state { text-align: center; padding: 30px 20px; color: #ccc; }
.empty-state .icon { font-size: 48px; margin-bottom: 8px; }

.panel-enter-active { animation: panel-in 0.35s cubic-bezier(0.4, 0, 0.2, 1); }
@keyframes panel-in { from { right: -420px; } to { right: 0; } }
</style>
