<template>
  <Transition name="toast">
    <div v-if="state.show" class="toast" :class="state.type">{{ state.message }}</div>
  </Transition>
</template>

<script setup>
import { inject } from 'vue'
const state = inject('toastState', { show: false, message: '', type: 'info' })
</script>

<style scoped>
.toast {
  position: fixed; top: 80px; left: 50%; transform: translateX(-50%);
  padding: 10px 24px; border-radius: 20px; color: #fff; font-size: 14px;
  z-index: 999; pointer-events: none; white-space: nowrap; max-width: 90vw;
}
.toast.info    { background: rgba(74,144,217,0.9); }
.toast.success { background: rgba(39,174,96,0.9); }
.toast.error   { background: rgba(231,76,60,0.9); }
.toast-enter-active { animation: toast-in 0.3s ease; }
.toast-leave-active { animation: toast-out 0.3s ease; }
@keyframes toast-in  { from { opacity: 0; transform: translateX(-50%) translateY(-10px); } to { opacity: 1; transform: translateX(-50%) translateY(0); } }
@keyframes toast-out { from { opacity: 1; } to { opacity: 0; } }

/* 移动端：避开安全区域 */
@media (max-width: 768px) {
  .toast {
    top: max(60px, env(safe-area-inset-top));
    bottom: auto;
  }
}
</style>
