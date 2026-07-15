<template>
  <div
    class="upload-zone"
    @click="triggerUpload"
    @dragover.prevent="dragover = true"
    @dragleave.prevent="dragover = false"
    @drop.prevent="onDrop"
    :class="{ active: dragover }"
  >
    <span class="plus">+</span>
    <span>点击或拖拽上传照片</span>
  </div>
  <input ref="fileInput" type="file" accept="image/*" multiple style="display:none" @change="onFileChange">
</template>

<script setup>
import { ref } from 'vue'

const emit = defineEmits(['upload'])
const fileInput = ref(null)
const dragover = ref(false)

function triggerUpload() { fileInput.value?.click() }
function onFileChange(e) { handleFiles(e.target.files); e.target.value = '' }
function onDrop(e) {
  dragover.value = false
  handleFiles(e.dataTransfer.files)
}
function handleFiles(files) {
  if (!files || files.length === 0) return
  const images = Array.from(files).filter(f => f.type.startsWith('image/'))
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
.plus { font-size: 28px; display: block; margin-bottom: 4px; }
</style>
