<template>
  <div class="pc-overlay">
    <div class="pc-card">
      <div class="pc-logo">🗺️</div>
      <h1 class="pc-title">隐私政策与权限说明</h1>
      <div class="pc-text">
        <p>欢迎使用地图相册。在你使用前，请了解并同意以下内容：</p>
        <p>1. <b>位置信息</b>：用于地图定位和记录照片拍摄位置。你可以在系统设置中随时关闭定位权限。</p>
        <p>2. <b>相机 / 相册</b>：仅在你主动拍照或选择照片时访问，用于上传记录，不会在后台读取。</p>
        <p>3. <b>设备信息与日志</b>：匿名设备描述与崩溃日志，用于排查问题和改进产品。</p>
        <p>4. <b>数据存储</b>：你上传的照片与备注存储在运营者服务器，仅用于向你提供地图相册服务。</p>
        <p>点击「同意并继续」即表示你已阅读并同意上述内容。</p>
      </div>
      <p class="pc-refused" v-if="refused">不同意将无法使用地图相册</p>
      <button class="pc-btn primary" @click="$emit('agree')">同意并继续</button>
      <button class="pc-btn ghost" @click="refused = true">不同意</button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineEmits(['agree'])
const refused = ref(false)
</script>

<style scoped>
.pc-overlay {
  position: fixed; inset: 0; z-index: 9998;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(160deg, #4a90d9 0%, #2c5f9e 100%);
  padding: 24px;
}
.pc-card {
  width: 100%; max-width: 380px;
  background: #fff; border-radius: 16px;
  padding: 28px 24px 20px;
  box-shadow: 0 12px 40px rgba(0,0,0,0.25);
  text-align: center;
  max-height: 86vh; display: flex; flex-direction: column;
}
.pc-logo { font-size: 40px; margin-bottom: 6px; }
.pc-title { font-size: 19px; color: #222; margin: 0 0 12px; }
.pc-text {
  flex: 1; overflow-y: auto; text-align: left;
  font-size: 12.5px; line-height: 1.7; color: #666;
  background: #f8fafc; border-radius: 10px;
  padding: 12px; margin-bottom: 12px;
}
.pc-text p { margin: 0 0 8px; }
.pc-text b { color: #333; }
.pc-refused { font-size: 12px; color: #e74c3c; margin: 0 0 8px; }
.pc-btn {
  width: 100%; padding: 12px 0; margin-top: 6px;
  border: none; border-radius: 10px;
  font-size: 15px; font-weight: 600; cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.pc-btn.primary { background: #1a73e8; color: #fff; }
.pc-btn.primary:active { background: #1660c4; }
.pc-btn.ghost { background: #f2f4f7; color: #888; font-weight: 400; }
</style>
