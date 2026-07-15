<template>
  <Transition name="lightbox">
    <div v-if="src" class="photo-lightbox" @click="$emit('close')">
      <button class="lb-close">&times;</button>
      <img :src="src" alt="大图" @click.stop />
    </div>
  </Transition>
</template>

<script setup>
defineProps({ src: { type: String, default: null } })
defineEmits(['close'])
</script>

<style scoped>
.photo-lightbox {
  position: fixed; inset: 0; background: rgba(0,0,0,0.9); z-index: 300;
  display: flex; align-items: center; justify-content: center; cursor: pointer;
}
.photo-lightbox img { max-width: 90vw; max-height: 90vh; object-fit: contain; border-radius: 4px; }
.lb-close {
  position: absolute; top: 16px; right: 20px; color: #fff; font-size: 32px;
  cursor: pointer; background: none; border: none;
}
.lightbox-enter-active { animation: lb-in 0.3s ease; }
.lightbox-leave-active { animation: lb-out 0.3s ease; }
@keyframes lb-in { from { opacity: 0; } to { opacity: 1; } }
@keyframes lb-out { from { opacity: 1; } to { opacity: 0; } }
</style>
