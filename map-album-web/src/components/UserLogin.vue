<template>
  <div class="login-overlay">
    <div class="login-card">
      <div class="login-logo">🗺️</div>
      <h1 class="login-title">地图相册</h1>
      <p class="login-sub">登录 / 注册后开始记录你的足迹</p>
      <input
        v-model.trim="account"
        class="login-input"
        type="text"
        placeholder="账号（字母/数字，2-32 位）"
        autocapitalize="none"
        autocomplete="username"
        @keyup.enter="submit"
      >
      <input
        v-model.trim="nickname"
        class="login-input"
        type="text"
        placeholder="昵称（可选，默认同账号）"
        autocomplete="nickname"
        @keyup.enter="submit"
      >
      <button class="login-btn" :disabled="loading" @click="submit">
        {{ loading ? '登录中…' : '登录 / 注册' }}
      </button>
      <p class="login-tip">新账号将自动注册（测试阶段免密码）</p>
      <p v-if="error" class="login-error">{{ error }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { loginAccount } from '../api/index.js'

const emit = defineEmits(['logged-in'])
const account = ref('')
const nickname = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  if (loading.value) return
  error.value = ''
  loading.value = true
  try {
    await loginAccount(account.value, nickname.value)
    emit('logged-in')
  } catch (e) {
    error.value = e.message || '登录失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-overlay {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #4a90d9 0%, #2c5f9e 100%);
  padding: 24px;
}
.login-card {
  width: 100%;
  max-width: 360px;
  background: #fff;
  border-radius: 16px;
  padding: 36px 28px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
  text-align: center;
}
.login-logo {
  font-size: 44px;
  margin-bottom: 8px;
}
.login-title {
  margin: 0 0 6px;
  font-size: 22px;
  color: #222;
}
.login-sub {
  margin: 0 0 24px;
  font-size: 13px;
  color: #888;
}
.login-input {
  width: 100%;
  box-sizing: border-box;
  padding: 12px 14px;
  margin-bottom: 12px;
  border: 1.5px solid #ddd;
  border-radius: 10px;
  font-size: 15px;
  outline: none;
  transition: border-color 0.2s;
}
.login-input:focus {
  border-color: #4a90d9;
}
.login-btn {
  width: 100%;
  padding: 13px 0;
  margin-top: 4px;
  border: none;
  border-radius: 10px;
  background: #4a90d9;
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  transition: background 0.2s;
}
.login-btn:disabled {
  background: #a8c6e8;
}
.login-btn:active {
  background: #357abd;
}
.login-tip {
  margin: 14px 0 0;
  font-size: 12px;
  color: #aaa;
}
.login-error {
  margin: 10px 0 0;
  font-size: 13px;
  color: #e74c3c;
}
</style>
