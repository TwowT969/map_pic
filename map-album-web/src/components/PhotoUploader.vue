<template>
  <div
    class="upload-zone"
    :class="{ active: dragover, picking }"
    @click="triggerUpload"
    @dragover.prevent="dragover = true"
    @dragleave="dragover = false"
    @drop.prevent="onDrop"
  >
    <span class="plus">{{ picking ? '⏳' : '＋' }}</span>
    {{ picking ? '正在打开相册…' : '点击从相册选择照片（可多选）' }}
    <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="onFileChange" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { hasCapacitor } from '../utils/capacitor.js'
import { pickPhotos } from '../utils/gallery.js'

const emit = defineEmits(['upload'])
const fileInput = ref(null)
const dragover = ref(false)
const picking = ref(false)

async function triggerUpload() {
  // 原生端（APK）：系统 Photo Picker（秒开、零权限、系统进程渲染与管理内存）
  if (hasCapacitor()) {
    if (picking.value) return
    picking.value = true
    try {
      const paths = await pickPhotos()
      if (paths && paths.length > 0) emit('upload', paths)
    } catch (e) { /* 用户取消，静默 */ }
    finally { picking.value = false }
    return
  }
  // Web：文件选择（手机浏览器 accept=image/* 会拉起相册/图库）
  fileInput.value?.click()
}

function onFileChange(e) { handleFiles(e.target.files); e.target.value = '' }

function onDrop(e) {
  dragover.value = false
  handleFiles(e.dataTransfer.files)
}

function handleFiles(files) {
  if (!files || files.length === 0) return
  const images = Array.from(files).filter(f => f.type && f.type.startsWith('image/'))
  if (images.length > 0) emit('upload', images)
}
</script>

<style scoped>
.upload-zone {
  border: 2px dashed #ddd; border-radius: 10px; padding: 20px;
  text-align: center; cursor: pointer; transition: all 0.2s; color: #999;
  font-size: 13px; margin-top: 8px;
}
.upload-zone:hover, .upload-zone.active { border-color: #4a90d9; color: #4a90d9; background: #f8fbff; }
.upload-zone.picking { opacity: 0.7; pointer-events: none; }
.plus { font-size: 28px; display: block; margin-bottom: 4px; }
</style>
