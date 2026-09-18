<template>
  <div class="gp-root">
    <!-- 顶栏 -->
    <div class="gp-top">
      <button class="gp-x" @click="onClose">✕</button>
      <span class="gp-title">相册</span>
      <button class="gp-ok" :disabled="selected.length === 0" @click="onConfirm">
        {{ selected.length ? '确定(' + selected.length + ')' : '确定' }}
      </button>
    </div>

    <!-- 主体网格 -->
    <div class="gp-grid-wrap" ref="scrollEl" @scroll="onScroll">
      <div class="gp-grid" v-if="items.length">
        <div
          v-for="(it, i) in items"
          :key="it.path"
          class="gp-cell"
          :class="{ picked: pickIndex.has(it.path) }"
          @click="toggle(it)"
        >
          <img :src="srcOf(it)" loading="lazy" decoding="async" @error="onImgErr($event)" />
          <span class="gp-badge" v-if="pickIndex.has(it.path)">{{ pickIndex.get(it.path) }}</span>
        </div>
      </div>

      <!-- 首屏骨架 -->
      <div class="gp-grid" v-if="loading && !items.length">
        <div class="gp-cell sk" v-for="n in 15" :key="'sk' + n"></div>
      </div>
      <div class="gp-more" v-if="loading && items.length">加载中…</div>
      <div class="gp-more" v-else-if="!hasMore && items.length">— 没有更多了 —</div>

      <!-- 空态 / 无权限 -->
      <div class="gp-empty" v-if="!loading && !items.length">
        <div class="gp-empty-icon">🖼️</div>
        <p v-if="denied">没有相册权限，无法读取照片</p>
        <p v-else>系统相册里还没有照片</p>
        <button class="gp-retry" v-if="denied" @click="init">重试</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Capacitor } from '@capacitor/core'
import { galleryList, galleryFiles } from '../utils/gallery.js'

const emit = defineEmits(['close', 'confirm'])

const items = ref([])
const selected = ref([])          // 已选 path，按选择顺序（无上限）
const pickIndex = ref(new Map())  // path → 序号(1..n)
const loading = ref(false)
const denied = ref(false)
const hasMore = ref(true)
const page = ref(0)
const PAGE = 60
const scrollEl = ref(null)
let _loadSeq = 0

function srcOf(it) { return it.thumb ? Capacitor.convertFileSrc(it.thumb) : Capacitor.convertFileSrc(it.path) }
function onImgErr(e) { e.target.style.opacity = '0.15' }

/** 相册读取权限（复用 Camera 插件权限通道；与首屏查询并行，不阻塞） */
function ensurePermission() {
  return (async () => {
    try {
      const { Camera } = await import('@capacitor/camera')
      const perm = await Promise.race([
        Camera.requestPermissions({ permissions: ['photos'] }),
        new Promise((_, rej) => setTimeout(() => rej(new Error('权限请求超时')), 5000))
      ])
      return !(perm && perm.photos === 'denied')
    } catch (e) {
      // 请求失败不判负：部分 ROM 无需该权限也能读 MediaStore
      return true
    }
  })()
}

async function loadPage(p) {
  const seq = ++_loadSeq
  loading.value = true
  try {
    const res = await galleryList(p, PAGE)
    if (seq !== _loadSeq) return
    if (p === 0) {
      items.value = res.items
    } else {
      const known = new Set(items.value.map(x => x.path))
      items.value = items.value.concat(res.items.filter(x => !known.has(x.path)))
    }
    hasMore.value = res.hasMore
    page.value = p + 1
  } finally {
    if (seq === _loadSeq) loading.value = false
  }
}

async function init() {
  denied.value = false
  items.value = []
  page.value = 0
  hasMore.value = true

  // 权限与首屏查询并行：权限对话框不阻塞网格渲染
  const permP = ensurePermission()
  permP.then(ok => {
    if (!ok) denied.value = true
    else if (items.value.length === 0 && !loading.value) loadPage(0).catch(() => {})
  })
  try {
    await loadPage(0)
  } catch (e) {
    // 无权限导致查询失败：等权限结果，授予后由上方自动重载
    const ok = await permP
    if (!ok) denied.value = true
  }
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  try { await loadPage(page.value) } catch (e) { /* 下次滚动再试 */ }
}

function onScroll() {
  const el = scrollEl.value
  if (!el) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 320) loadMore()
}

function toggle(it) {
  const sel = new Map(pickIndex.value)
  if (sel.has(it.path)) {
    const idx = sel.get(it.path)
    sel.delete(it.path)
    for (const [k, v] of sel) if (v > idx) sel.set(k, v - 1)
    selected.value = selected.value.filter(p => p !== it.path)
  } else {
    sel.set(it.path, sel.size + 1)
    selected.value = selected.value.concat([it.path])
  }
  pickIndex.value = sel
}

function onClose() { emit('close') }

async function onConfirm() {
  if (!selected.value.length) return
  emit('confirm', await galleryFiles(selected.value))
}

onMounted(init)
</script>

<style scoped>
.gp-root {
  position: fixed; inset: 0; z-index: 360;
  background: #101418; color: #e8edf4;
  display: flex; flex-direction: column;
}
.gp-top {
  flex-shrink: 0; display: flex; align-items: center; gap: 12px;
  padding: max(10px, env(safe-area-inset-top)) 14px 10px;
  background: #171c23; border-bottom: 1px solid #232a33;
}
.gp-x {
  width: 34px; height: 34px; border: none; border-radius: 8px;
  background: #232a33; color: #cfd8e3; font-size: 16px; cursor: pointer;
}
.gp-title { flex: 1; text-align: center; font-size: 16.5px; font-weight: 600; }
.gp-ok {
  min-width: 76px; height: 34px; border: none; border-radius: 17px;
  background: #1a73e8; color: #fff; font-size: 13.5px; font-weight: 600; cursor: pointer;
}
.gp-ok:disabled { background: #2a3340; color: #6b7684; }
.gp-grid-wrap { flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; }
.gp-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 3px; padding: 3px; }
.gp-cell {
  position: relative; width: 100%; padding-top: 100%;
  background: #1b2129; overflow: hidden; cursor: pointer;
}
.gp-cell img {
  position: absolute; inset: 0; width: 100%; height: 100%;
  object-fit: cover; display: block;
}
.gp-cell.picked img { opacity: .45; }
.gp-cell.picked::after {
  content: ''; position: absolute; inset: 0;
  border: 2.5px solid #1a73e8;
}
.gp-badge {
  position: absolute; right: 6px; top: 6px;
  width: 24px; height: 24px; border-radius: 50%;
  background: #1a73e8; color: #fff; font-size: 12.5px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  border: 2px solid rgba(255,255,255,.9);
}
.gp-cell.sk { animation: skPulse 1.1s ease-in-out infinite; }
@keyframes skPulse { 0%,100% { opacity:.45 } 50% { opacity:.9 } }
.gp-more { text-align: center; color: #66707d; font-size: 12px; padding: 14px 0 18px; }
.gp-empty { text-align: center; padding: 90px 0 40px; color: #7d8896; }
.gp-empty-icon { font-size: 46px; margin-bottom: 12px; }
.gp-empty p { margin: 4px 0; font-size: 14.5px; }
.gp-retry {
  margin-top: 14px; padding: 9px 26px; border: none; border-radius: 18px;
  background: #1a73e8; color: #fff; font-size: 14px; cursor: pointer;
}
</style>
