<template>
  <div class="photo-grid">
    <div v-for="photo in photos" :key="photo.id" class="photo-item" @click="onPhotoClick(photo)">
      <img :src="photoUrl(photo.thumbUrl || photo.url)" loading="lazy" @error="onImgError" />
      <button class="photo-delete" @click.stop="$emit('delete', photo.id)" title="删除此照片">✕</button>
    </div>
  </div>
</template>

<script setup>
import { inject } from 'vue'

defineProps({ photos: { type: Array, default: () => [] } })
const emit = defineEmits(['delete', 'preview'])
const openLightbox = inject('openLightbox', (src) => {})

function photoUrl(url) {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return url
}

function onPhotoClick(photo) {
  const src = photoUrl(photo.url || photo.thumbUrl)
  openLightbox(src)
}

function onImgError(e) {
  e.target.style.display = 'none'
}
</script>

<style scoped>
.photo-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 10px; }
.photo-item {
  aspect-ratio: 1; border-radius: 8px; overflow: hidden;
  position: relative; cursor: pointer;
  border: 2px solid transparent; transition: border-color 0.2s;
  background: #f0f0f0;
}
.photo-item:hover { border-color: #4a90d9; }
.photo-item img { width: 100%; height: 100%; object-fit: cover; }
.photo-delete {
  position: absolute; top: 4px; right: 4px; width: 22px; height: 22px;
  border-radius: 50%; background: rgba(231,76,60,0.85); color: #fff;
  border: none; cursor: pointer; font-size: 12px;
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: opacity 0.2s;
}
.photo-item:hover .photo-delete { opacity: 1; }
</style>
