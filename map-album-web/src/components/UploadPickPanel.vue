<template>
  <div class="upp-sheet" :class="{ pc: !isMobile }">
    <div class="upp-handle"><div class="upp-bar"></div></div>
    <div class="upp-main">
      <img v-if="previewUrl" :src="previewUrl" class="upp-thumb" alt="待上传照片" />
      <div class="upp-info">
        <div class="upp-tip">拖动蓝色标记或点击地图调整位置</div>
        <div class="upp-loc">📍 {{ locText }}</div>
        <textarea
          v-model="desc"
          class="upp-input"
          rows="2"
          maxlength="200"
          placeholder="添加备注…"
          :disabled="busy"
        ></textarea>
      </div>
    </div>
    <div class="upp-actions">
      <button class="upp-btn ghost" :disabled="busy" @click="$emit('cancel')">取消</button>
      <button class="upp-btn primary" :disabled="busy" @click="$emit('confirm', desc.trim())">
        {{ busy ? '上传中…' : '确认上传' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  previewUrl: { type: String, default: '' },
  coord: { type: Object, default: () => ({ lng: 0, lat: 0, address: '' }) },
  busy: { type: Boolean, default: false }
})

defineEmits(['confirm', 'cancel'])

const desc = ref('')

const isMobile = ref(window.innerWidth <= 768)
function detectMobile() { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', detectMobile))
onBeforeUnmount(() => window.removeEventListener('resize', detectMobile))

const locText = computed(() => {
  const c = props.coord || {}
  if (c.address) return c.address
  const lat = Number(c.lat || 0).toFixed(5)
  const lng = Number(c.lng || 0).toFixed(5)
  return `${lat}, ${lng}`
})
</script>

<style scoped>
.upp-sheet {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 210;
  background: #fff; border-radius: 16px 16px 0 0;
  box-shadow: 0 -6px 24px rgba(0,0,0,0.15);
  padding: 0 16px calc(12px + env(safe-area-inset-bottom));
  animation: uppIn 0.25s ease;
}
@keyframes uppIn { from { transform: translateY(100%); } to { transform: translateY(0); } }

.upp-handle { display: flex; justify-content: center; padding: 10px 0 6px; }
.upp-bar { width: 36px; height: 4px; background: #ddd; border-radius: 2px; }

.upp-main { display: flex; gap: 12px; align-items: flex-start; }
.upp-thumb {
  width: 76px; height: 76px; object-fit: cover;
  border-radius: 10px; border: 2px solid #eef2f7; background: #f0f0f0;
  flex-shrink: 0;
}
.upp-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.upp-tip { font-size: 12px; color: #8a97a8; }
.upp-loc {
  font-size: 13px; color: #333;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.upp-input {
  width: 100%; border: 1px solid #e3e8ef; border-radius: 8px;
  padding: 8px 10px; font-size: 14px; font-family: inherit;
  outline: none; resize: none; color: #333;
}
.upp-input:focus { border-color: #4a90d9; }

.upp-actions { display: flex; gap: 10px; margin-top: 10px; }
.upp-btn {
  flex: 1; min-height: 44px; border-radius: 10px; border: none;
  font-size: 15px; font-weight: 500; cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.upp-btn.ghost { background: #f2f4f7; color: #555; }
.upp-btn.ghost:active { background: #e7eaf0; }
.upp-btn.primary { background: #1a73e8; color: #fff; }
.upp-btn.primary:active { background: #1660c4; }
.upp-btn:disabled { opacity: 0.6; cursor: default; }

/* PC：底部居中卡片 */
.upp-sheet.pc {
  left: 50%; right: auto; bottom: 24px;
  width: 480px; transform: translateX(-50%);
  border-radius: 14px;
  padding: 4px 16px 16px;
  animation: uppInPc 0.25s ease;
}
@keyframes uppInPc { from { transform: translate(-50%, 40px); opacity: 0; } to { transform: translate(-50%, 0); opacity: 1; } }
.upp-sheet.pc .upp-handle { display: none; }
</style>
