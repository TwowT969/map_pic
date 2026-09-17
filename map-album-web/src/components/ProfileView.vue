<template>
  <div class="profile-view">
    <div class="pv-header">
      <div class="pv-title">我的</div>
    </div>
    <div class="pv-body">
      <!-- 用户卡片 -->
      <div class="pv-card pv-user">
        <div class="pv-avatar">👤</div>
        <div class="pv-user-info">
          <div class="pv-account">{{ account || '未登录' }}</div>
          <div class="pv-nick-row" v-if="!editingNick">
            <span class="pv-nick">{{ nick || '未设置昵称' }}</span>
            <button class="pv-mini-btn" @click="startEditNick">改昵称</button>
          </div>
          <div class="pv-nick-edit" v-else>
            <input v-model="nickDraft" maxlength="20" placeholder="输入新昵称" @keyup.enter="saveNick" />
            <button class="pv-mini-btn primary" :disabled="savingNick" @click="saveNick">{{ savingNick ? '保存中…' : '保存' }}</button>
            <button class="pv-mini-btn" @click="editingNick = false">取消</button>
          </div>
        </div>
      </div>

      <!-- 统计 -->
      <div class="pv-stats">
        <div class="pv-stat">
          <div class="pv-stat-num">{{ stats.spots }}</div>
          <div class="pv-stat-label">点位</div>
        </div>
        <div class="pv-stat">
          <div class="pv-stat-num">{{ stats.photos }}</div>
          <div class="pv-stat-label">照片</div>
        </div>
        <div class="pv-stat">
          <div class="pv-stat-num">{{ stats.cities }}</div>
          <div class="pv-stat-label">城市</div>
        </div>
        <div class="pv-stat" v-if="pendingCount > 0">
          <div class="pv-stat-num warn">{{ pendingCount }}</div>
          <div class="pv-stat-label">待上传</div>
        </div>
      </div>

      <!-- 功能项 -->
      <div class="pv-card pv-menu">
        <button class="pv-menu-item" @click="openPoster">
          <span>🎨 生成分享海报</span><span class="pv-menu-extra">生活地图 / 胶片墙 ›</span>
        </button>
        <button class="pv-menu-item" @click="$emit('check-update')">
          <span>🔄 检查更新</span><span class="pv-menu-extra">当前 v{{ versionName }}</span>
        </button>
        <button class="pv-menu-item" @click="openPrivacy">
          <span>📜 隐私政策与权限说明</span><span class="pv-menu-extra">›</span>
        </button>
        <button class="pv-menu-item danger" @click="onLogout">
          <span>🚪 退出登录</span>
        </button>
      </div>
    </div>

    <!-- 分享海报弹窗（两种风格可选） -->
    <PosterModal v-if="posterVisible" @close="posterVisible = false" />
  </div>
</template>

<script setup>
import { ref, computed, inject, onMounted, onBeforeUnmount } from 'vue'
import { getApiBase, updateUserProfile, getCurrentNickname, logout } from '../api/index.js'
import PosterModal from './PosterModal.vue'

const props = defineProps({
  pendingCount: { type: Number, default: 0 }
})

defineEmits(['check-update'])

const showToast = inject('showToast')
const spotsStore = inject('spotsStore')
const photosStore = inject('photosStore')

const nick = ref(getCurrentNickname() || '')
const editingNick = ref(false)
const nickDraft = ref('')
const savingNick = ref(false)

const account = computed(() => {
  const sso = localStorage.getItem('map_album_sso_user_id') || ''
  return sso.replace(/^app-/, '').replace(/^dev-/, 'dev:').slice(0, 24)
})

const versionName = import.meta.env.VITE_APP_VERSION_NAME || import.meta.env.VITE_APP_VERSION_CODE || '1.0'

// ===== 分享海报（两种风格可选） =====
const posterVisible = ref(false)
function openPoster() {
  if (!photosStore.allPhotos.value.length) {
    showToast('先上传照片，生成你的第一张海报')
    return
  }
  posterVisible.value = true
}

const stats = computed(() => {
  const spots = spotsStore.spots.value || []
  const cities = new Set(spots.map(s => (s.city || '').trim()).filter(Boolean))
  return {
    spots: spots.length,
    photos: photosStore.allPhotos.value.length,
    cities: cities.size
  }
})

function startEditNick() {
  nickDraft.value = nick.value
  editingNick.value = true
}

async function saveNick() {
  const v = nickDraft.value.trim()
  if (!v) { showToast('昵称不能为空', 'error'); return }
  if (v === nick.value) { editingNick.value = false; return }
  savingNick.value = true
  try {
    const updated = await updateUserProfile(v)
    nick.value = (updated && updated.nickname) || v
    localStorage.setItem('map_album_nickname', nick.value)
    editingNick.value = false
    showToast('昵称已更新', 'success')
  } catch (e) {
    showToast('更新失败: ' + (e?.message || e), 'error')
  } finally {
    savingNick.value = false
  }
}

function openPrivacy() {
  const origin = getApiBase().replace(/\/api\/?$/, '')
  window.open(origin + '/privacy.html', '_blank')
}

function onLogout() {
  if (confirm('确定退出登录吗？')) {
    logout()
    location.reload()
  }
}

// 移动端检测（返回按钮在小屏不显示，用户用底部切换）
const isMobile = ref(window.innerWidth <= 768)
function detectMobile() { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', detectMobile))
onBeforeUnmount(() => window.removeEventListener('resize', detectMobile))
</script>

<style scoped>
.profile-view {
  position: fixed; inset: 0; z-index: 120;
  background: #f7f9fc;
  display: flex; flex-direction: column;
}
.pv-header {
  display: flex; align-items: center; gap: 12px;
  padding: max(10px, env(safe-area-inset-top)) 16px 10px;
  background: #fff; border-bottom: 1px solid #eef1f6;
  flex-shrink: 0;
}
.pv-title { font-size: 17px; font-weight: 600; color: #222; }

.pv-body {
  flex: 1; overflow-y: auto;
  padding: 14px 16px calc(84px + env(safe-area-inset-bottom, 0px));
  -webkit-overflow-scrolling: touch;
}

.pv-card {
  background: #fff; border-radius: 14px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05);
  margin-bottom: 14px;
}

.pv-user { display: flex; align-items: center; gap: 14px; padding: 18px 16px; }
.pv-avatar {
  width: 54px; height: 54px; border-radius: 50%;
  background: linear-gradient(135deg, #4a90d9, #357abd);
  display: flex; align-items: center; justify-content: center;
  font-size: 26px; flex-shrink: 0;
}
.pv-user-info { flex: 1; min-width: 0; }
.pv-account { font-size: 15px; font-weight: 600; color: #222; margin-bottom: 4px; word-break: break-all; }
.pv-nick-row { display: flex; align-items: center; gap: 8px; }
.pv-nick { font-size: 13px; color: #888; }
.pv-nick-edit { display: flex; align-items: center; gap: 6px; }
.pv-nick-edit input {
  flex: 1; min-width: 0; height: 34px;
  border: 1px solid #e3e8ef; border-radius: 8px;
  padding: 0 10px; font-size: 14px; outline: none;
}
.pv-nick-edit input:focus { border-color: #4a90d9; }
.pv-mini-btn {
  border: none; background: #f2f4f7; color: #555;
  border-radius: 8px; padding: 7px 12px; font-size: 13px;
  cursor: pointer; -webkit-tap-highlight-color: transparent;
  flex-shrink: 0;
}
.pv-mini-btn.primary { background: #1a73e8; color: #fff; }
.pv-mini-btn:disabled { opacity: 0.6; }

.pv-stats {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px;
  margin-bottom: 14px;
}
.pv-stat {
  background: #fff; border-radius: 14px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05);
  text-align: center; padding: 14px 4px;
}
.pv-stat-num { font-size: 22px; font-weight: 700; color: #1a73e8; }
.pv-stat-num.warn { color: #e67e22; }
.pv-stat-label { font-size: 12px; color: #999; margin-top: 2px; }

.pv-menu { overflow: hidden; }
.pv-menu-item {
  display: flex; align-items: center; justify-content: space-between;
  width: 100%; padding: 15px 16px; min-height: 50px;
  border: none; background: #fff; cursor: pointer;
  font-size: 15px; color: #333; text-align: left;
  -webkit-tap-highlight-color: transparent;
}
.pv-menu-item:active { background: #f7f9fc; }
.pv-menu-item + .pv-menu-item { border-top: 1px solid #f2f4f7; }
.pv-menu-item.danger { color: #e74c3c; }
.pv-menu-extra { font-size: 12px; color: #aaa; }
</style>
