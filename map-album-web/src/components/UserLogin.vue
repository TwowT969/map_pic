<template>
  <div class="login-overlay">
    <!-- 背景装饰：浮动光斑 -->
    <div class="bg-blob b1"></div>
    <div class="bg-blob b2"></div>
    <div class="bg-blob b3"></div>
    <div class="bg-grid"></div>

    <div class="login-card">
      <div class="brand">
        <div class="brand-logo">🗺️</div>
        <h1 class="login-title">地图相册</h1>
        <p class="login-sub">把每一次足迹，落在地图上</p>
      </div>

      <!-- 登录 / 注册 分离切换 -->
      <div class="mode-tabs" role="tablist">
        <button
          class="tab"
          :class="{ active: mode === 'login' }"
          type="button"
          role="tab"
          :aria-selected="mode === 'login'"
          @click="switchMode('login')"
        >登录</button>
        <button
          class="tab"
          :class="{ active: mode === 'register' }"
          type="button"
          role="tab"
          :aria-selected="mode === 'register'"
          @click="switchMode('register')"
        >注册</button>
        <span class="tab-slider" :class="{ right: mode === 'register' }"></span>
      </div>

      <!-- 账号 -->
      <div class="field" :class="{ focus: focusField === 'account', filled: account }">
        <span class="f-icon">👤</span>
        <input
          v-model.trim="account"
          type="text"
          placeholder="账号（字母 / 数字，2-32 位）"
          autocapitalize="none"
          autocomplete="username"
          maxlength="32"
          @focus="focusField = 'account'"
          @blur="focusField = ''"
          @keyup.enter="submit"
        />
      </div>

      <!-- 注册：昵称 -->
      <div v-if="mode === 'register'" class="field" :class="{ focus: focusField === 'nickname', filled: nickname }">
        <span class="f-icon">✏️</span>
        <input
          v-model.trim="nickname"
          type="text"
          placeholder="昵称（选填，默认同账号）"
          autocomplete="nickname"
          maxlength="20"
          @focus="focusField = 'nickname'"
          @blur="focusField = ''"
          @keyup.enter="submit"
        />
      </div>

      <!-- 密码 -->
      <div class="field" :class="{ focus: focusField === 'password', filled: password }">
        <span class="f-icon">🔒</span>
        <input
          v-model="password"
          :type="showPwd ? 'text' : 'password'"
          :placeholder="mode === 'login' ? '密码（至少 6 位）' : '设置密码（至少 6 位）'"
          :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
          maxlength="64"
          @focus="focusField = 'password'"
          @blur="focusField = ''"
          @keyup.enter="submit"
        />
        <button class="f-toggle" type="button" tabindex="-1" @click="showPwd = !showPwd">
          {{ showPwd ? '🙈' : '👁' }}
        </button>
      </div>

      <!-- 注册：确认密码 -->
      <div v-if="mode === 'register'" class="field" :class="{ focus: focusField === 'confirm', filled: confirmPwd }">
        <span class="f-icon">🔑</span>
        <input
          v-model="confirmPwd"
          :type="showPwd ? 'text' : 'password'"
          placeholder="确认密码"
          autocomplete="new-password"
          maxlength="64"
          @focus="focusField = 'confirm'"
          @blur="focusField = ''"
          @keyup.enter="submit"
        />
      </div>

      <button class="login-btn" :disabled="loading" @click="submit">
        <span v-if="loading" class="spinner" aria-hidden="true"></span>
        <span>{{ btnText }}</span>
      </button>

      <Transition name="err">
        <p v-if="error" class="login-error">⚠️ {{ error }}</p>
      </Transition>

      <button class="switch-link" type="button" @click="switchMode(mode === 'login' ? 'register' : 'login')">
        {{ mode === 'login' ? '没有账号？立即注册' : '已有账号？直接登录' }}
      </button>

      <p class="login-tip">{{ tipText }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { loginAccount, registerAccount } from '../api/index.js'

const emit = defineEmits(['logged-in'])
const mode = ref('login') // 'login' | 'register' —— 注册登录彻底分离
const account = ref('')
const password = ref('')
const confirmPwd = ref('')
const nickname = ref('')
const showPwd = ref(false)
const loading = ref(false)
const error = ref('')
const focusField = ref('')

const btnText = computed(() => {
  if (loading.value) return mode.value === 'login' ? '登录中…' : '注册中…'
  return mode.value === 'login' ? '登 录' : '注册并登录'
})
const tipText = computed(() => mode.value === 'login'
  ? '使用已有账号密码登录，数据与网页端互通'
  : '注册即创建账号并自动登录')

function switchMode(m) {
  if (mode.value === m) return
  mode.value = m
  error.value = ''
}

async function submit() {
  if (loading.value) return
  error.value = ''
  if (!account.value) { error.value = '请输入账号'; return }
  if (!/^[\w@.-]{2,32}$/.test(account.value)) { error.value = '账号需为 2-32 位字母 / 数字 / 下划线'; return }
  if (!password.value) { error.value = '请输入密码（至少 6 位）'; return }
  if (password.value.length < 6) { error.value = '密码至少 6 位'; return }
  if (mode.value === 'register' && password.value !== confirmPwd.value) {
    error.value = '两次输入的密码不一致'
    return
  }

  loading.value = true
  try {
    if (mode.value === 'login') {
      await loginAccount(account.value, password.value)
    } else {
      await registerAccount(account.value, nickname.value, password.value)
    }
    emit('logged-in')
  } catch (e) {
    error.value = e.message || (mode.value === 'login' ? '登录失败，请重试' : '注册失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-overlay {
  position: fixed;
  top: 0; right: 0; bottom: 0; left: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(140deg, #3b82d6 0%, #2563a8 52%, #1b3a70 100%);
  padding: 24px;
  overflow: hidden;
}

/* 浮动光斑 */
.bg-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(64px);
  pointer-events: none;
  animation: float 10s ease-in-out infinite;
}
.b1 { width: 340px; height: 340px; background: #7db4e8; top: -90px; left: -90px; opacity: 0.4; }
.b2 { width: 300px; height: 300px; background: #34d399; bottom: -80px; right: -70px; opacity: 0.2; animation-delay: -3.5s; }
.b3 { width: 190px; height: 190px; background: #fbbf24; bottom: 18%; left: 6%; opacity: 0.16; animation-delay: -7s; }
@keyframes float {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-26px) scale(1.07); }
}
/* 细网格纹理 */
.bg-grid {
  position: absolute; inset: 0; pointer-events: none;
  background-image:
    linear-gradient(rgba(255,255,255,0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse at center, black 30%, transparent 75%);
  -webkit-mask-image: radial-gradient(ellipse at center, black 30%, transparent 75%);
}

.login-card {
  position: relative;
  width: 100%;
  max-width: 360px;
  background: rgba(255, 255, 255, 0.98);
  border-radius: 22px;
  padding: 30px 28px 22px;
  box-shadow: 0 24px 64px rgba(9, 30, 66, 0.4);
  animation: cardIn 0.45s cubic-bezier(0.22, 1, 0.36, 1);
}
@keyframes cardIn {
  from { opacity: 0; transform: translateY(26px) scale(0.97); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.brand { text-align: center; margin-bottom: 20px; }
.brand-logo {
  width: 70px; height: 70px;
  margin: 0 auto 14px;
  background: linear-gradient(135deg, #4a90d9, #2563a8);
  border-radius: 21px;
  display: flex; align-items: center; justify-content: center;
  font-size: 36px;
  box-shadow: 0 12px 28px rgba(37, 99, 168, 0.38), inset 0 1px 0 rgba(255,255,255,0.25);
}
.login-title {
  margin: 0 0 5px;
  font-size: 23px;
  color: #16283f;
  letter-spacing: 2px;
}
.login-sub { margin: 0; font-size: 13px; color: #93a3b8; letter-spacing: 0.5px; }

/* ===== 登录 / 注册 分离 Tab ===== */
.mode-tabs {
  position: relative;
  display: flex;
  background: #eef2f7;
  border-radius: 12px;
  padding: 4px;
  margin-bottom: 18px;
}
.mode-tabs .tab {
  position: relative;
  z-index: 1;
  flex: 1;
  height: 40px;
  border: none;
  background: transparent;
  border-radius: 9px;
  font-size: 15px;
  font-weight: 600;
  color: #8a97a8;
  cursor: pointer;
  letter-spacing: 4px;
  transition: color 0.2s;
  -webkit-tap-highlight-color: transparent;
}
.mode-tabs .tab.active { color: #fff; }
.tab-slider {
  position: absolute;
  top: 4px; left: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  background: linear-gradient(90deg, #4a90d9, #2563a8);
  border-radius: 9px;
  box-shadow: 0 4px 12px rgba(37, 99, 168, 0.35);
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.tab-slider.right { transform: translateX(100%); }

/* 输入框 */
.field {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 50px;
  padding: 0 14px;
  margin-bottom: 12px;
  background: #f3f6fa;
  border: 1.5px solid transparent;
  border-radius: 13px;
  transition: border-color 0.2s, background 0.2s, box-shadow 0.2s;
}
.field.focus {
  background: #fff;
  border-color: #4a90d9;
  box-shadow: 0 0 0 3.5px rgba(74, 144, 217, 0.15);
}
.f-icon { font-size: 16px; opacity: 0.72; flex-shrink: 0; }
.field input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
  color: #1f2d3d;
}
.field input::placeholder { color: #a9b5c4; }
.f-toggle {
  flex-shrink: 0;
  border: none;
  background: transparent;
  font-size: 17px;
  line-height: 1;
  padding: 6px;
  margin-right: -6px;
  cursor: pointer;
  opacity: 0.55;
  -webkit-tap-highlight-color: transparent;
}
.f-toggle:active { opacity: 1; }

/* 登录/注册按钮 + spinner */
.login-btn {
  width: 100%;
  height: 50px;
  margin-top: 8px;
  border: none;
  border-radius: 13px;
  cursor: pointer;
  background: linear-gradient(90deg, #4a90d9, #2563a8);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  box-shadow: 0 10px 22px rgba(37, 99, 168, 0.35);
  transition: transform 0.15s, box-shadow 0.2s, opacity 0.2s;
  -webkit-tap-highlight-color: transparent;
}
.login-btn:active { transform: scale(0.97); }
.login-btn:disabled { opacity: 0.72; box-shadow: none; cursor: default; }
.spinner {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2.5px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  animation: spin 0.8s linear infinite;
  flex-shrink: 0;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* 错误提示 */
.login-error {
  margin: 14px 0 0;
  padding: 9px 12px;
  background: #fdecec;
  color: #d64541;
  border-radius: 10px;
  font-size: 13px;
  text-align: center;
}
.err-enter-active { animation: errIn 0.28s ease; }
@keyframes errIn {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 切换链接 */
.switch-link {
  display: block;
  width: 100%;
  margin: 14px 0 0;
  border: none;
  background: transparent;
  color: #4a90d9;
  font-size: 13.5px;
  font-weight: 500;
  text-align: center;
  cursor: pointer;
  padding: 6px;
  -webkit-tap-highlight-color: transparent;
}
.switch-link:active { opacity: 0.7; }

.login-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: #a9b5c4;
  text-align: center;
}
</style>
