<template>
  <div class="photo-grid">
    <div v-for="photo in photos" :key="photo.id" class="photo-item" @click="$emit('preview', photo)">
      <img :src="photoUrl(photo.thumbUrl || photo.url)" loading="lazy" @error="onImgError" />
      <button class="photo-delete" @click.stop="$emit('delete', photo.id)" title="删除此照片">✕</button>
    </div>
  </div>
</template>

<script setup>
defineProps({ photos: { type: Array, default: () => [] } })
defineEmits(['delete', 'preview'])

function photoUrl(url) {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return '/api' + url
}

function onImgError(e) {
  e.target.style.display = 'none'
}
</script>

<style scoped>
.photo-grid {
  display: grid; grid-template-columns: repeat(3, 1fr);
  gap: 8px; margin-top: 10px;
}

/* 移动端 2 列 */
@media (max-width: 768px) {
  .photo-grid { grid-template-columns: repeat(2, 1fr); gap: 6px; }
}

.photo-item {
  aspect-ratio: 1; border-radius: 8px; overflow: hidden;
  position: relative; cursor: pointer;
  border: 2px solid transparent; transition: border-color 0.2s;
  background: #f0f0f0;
  -webkit-tap-highlight-color: transparent;
}
.photo-item:hover { border-color: #4a90d9; }
.photo-item:active { border-color: #3a7bc8; }
.photo-item img { width: 100%; height: 100%; object-fit: cover; }

.photo-delete {
  position: absolute; top: 4px; right: 4px;
  width: 28px; height: 28px;
  border-radius: 50%; background: rgba(231,76,60,0.85); color: #fff;
  border: none; cursor: pointer; font-size: 12px;
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: opacity 0.2s;
  /* 移动端一直显示删除按钮（无 hover） */
}
.photo-item:hover .photo-delete,
@media (max-width: 768px) { .photo-delete { opacity: 0.85; width: 28px; height: 28px; } }
</style>
