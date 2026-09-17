<template>
  <div class="search-bar">
    <div class="inner">
      <input
        type="text"
        v-model="keyword"
        placeholder="搜索地点或我的点位/备注…"
        autocomplete="off"
        @input="onInput"
        @keydown.enter="onSearch"
        @focus="onInput"
      />
      <button class="search-icon" @click="onSearch">🔍</button>
    </div>
    <div class="suggest-dropdown" :class="{ show: showDropdown }">
      <!-- 高德地点建议 -->
      <template v-if="suggestions.length > 0">
        <div class="dropdown-section">📍 地点</div>
        <div
          v-for="item in suggestions"
          :key="'amap-' + item.id"
          class="suggest-item"
          @click="onSelect(item)"
        >
          <span class="pin-icon">📍</span>
          <div class="info">
            <div class="name">{{ item.name }}</div>
            <div class="district">{{ item.district || '' }}</div>
          </div>
        </div>
      </template>

      <!-- 我的内容（点位名/标签/地址、照片备注） -->
      <template v-if="ownSpots.length > 0 || ownPhotos.length > 0">
        <div class="dropdown-section">⭐ 我的内容</div>
        <div
          v-for="spot in ownSpots"
          :key="'spot-' + spot.id"
          class="suggest-item"
          @click="onSelectOwnSpot(spot)"
        >
          <span class="pin-icon">⭐</span>
          <div class="info">
            <div class="name">{{ spot.name }}</div>
            <div class="district">{{ spot.address || `${Number(spot.lat).toFixed(4)}, ${Number(spot.lng).toFixed(4)}` }}</div>
          </div>
        </div>
        <div
          v-for="photo in ownPhotos"
          :key="'photo-' + photo.id"
          class="suggest-item"
          @click="onSelectOwnPhoto(photo)"
        >
          <span class="pin-icon">🖼️</span>
          <div class="info">
            <div class="name">{{ photo.description || '（无备注）' }}</div>
            <div class="district">照片 · {{ spotNameOf(photo) }}</div>
          </div>
        </div>
      </template>

      <div v-if="noResult" class="dropdown-empty">没有匹配的地点或内容</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, inject } from 'vue'
import { fetchSuggestions } from '../api/index.js'

const emit = defineEmits(['select', 'select-own-spot', 'select-own-photo'])

const spotsStore = inject('spotsStore')
const photosStore = inject('photosStore')

const keyword = ref('')
const suggestions = ref([])
const focused = ref(false)
let timer = null

const showDropdown = computed(() => focused.value &&
  (suggestions.value.length > 0 || ownSpots.value.length > 0 || ownPhotos.value.length > 0 || noResult.value))

const noResult = computed(() => focused.value && keyword.value.trim().length >= 1 &&
  suggestions.value.length === 0 && ownSpots.value.length === 0 && ownPhotos.value.length === 0)

/** 我的内容检索：点位名/标签/地址 + 照片备注（纯前端过滤） */
const ownSpots = computed(() => {
  const kw = keyword.value.trim()
  if (kw.length < 1 || !spotsStore) return []
  return (spotsStore.spots.value || [])
    .filter(s => (s.name || '').includes(kw) || (s.tags || '').includes(kw) || (s.address || '').includes(kw))
    .slice(0, 4)
})

const ownPhotos = computed(() => {
  const kw = keyword.value.trim()
  if (kw.length < 1 || !photosStore) return []
  return (photosStore.allPhotos.value || [])
    .filter(p => (p.description || '').includes(kw))
    .slice(0, 3)
})

function spotNameOf(photo) {
  const spot = spotsStore && spotsStore.spots.value.find(s => s.id === photo.spotId)
  return spot ? spot.name : ''
}

function onInput() {
  focused.value = true
  const kw = keyword.value.trim()
  if (kw.length < 1) { suggestions.value = []; return }
  clearTimeout(timer)
  timer = setTimeout(async () => {
    try {
      suggestions.value = (await fetchSuggestions(kw)) || []
    } catch { suggestions.value = [] }
  }, 300)
}

function onSearch() {
  focused.value = false
  // 优先匹配我的内容
  if (ownSpots.value.length > 0) { onSelectOwnSpot(ownSpots.value[0]); return }
  if (ownPhotos.value.length > 0) { onSelectOwnPhoto(ownPhotos.value[0]); return }
  const kw = keyword.value.trim()
  if (!kw) return
  fetchSuggestions(kw).then(data => {
    if (data && data.length > 0) {
      const first = data[0]
      keyword.value = first.name
      emit('select', { lng: first.lng, lat: first.lat, name: first.name })
    }
  }).catch(() => {})
}

function onSelect(item) {
  focused.value = false
  suggestions.value = []
  keyword.value = item.name
  emit('select', { lng: item.lng, lat: item.lat, name: item.name })
}

function onSelectOwnSpot(spot) {
  focused.value = false
  suggestions.value = []
  keyword.value = spot.name
  emit('select-own-spot', spot)
}

function onSelectOwnPhoto(photo) {
  focused.value = false
  suggestions.value = []
  keyword.value = photo.description || ''
  emit('select-own-photo', photo)
}

// 点击外部关闭
document.addEventListener('click', (e) => {
  const el = e.target
  if (!el.closest('.search-bar')) {
    focused.value = false
  }
})
</script>

<style scoped>
.search-bar {
  position: fixed; top: 0; left: 0; right: 0; z-index: 100;
  padding: 10px 16px;
  background: transparent;
  pointer-events: none;
  padding-top: max(10px, env(safe-area-inset-top));
}
.inner { max-width: 600px; margin: 0 auto; position: relative; pointer-events: auto; }
.search-bar input {
  width: 100%; height: 42px; padding: 0 44px 0 16px;
  border: 1.5px solid rgba(255,255,255,0.6); border-radius: 21px;
  font-size: 15px; outline: none;
  background: rgba(255,255,255,0.75);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  transition: all 0.2s;
  color: #333;
}
.search-bar input::placeholder { color: #888; }
.search-bar input:focus {
  border-color: #4a90d9; background: rgba(255,255,255,0.95);
  box-shadow: 0 2px 12px rgba(74,144,217,0.25);
}
.search-icon {
  position: absolute; right: 6px; top: 50%; transform: translateY(-50%);
  width: 32px; height: 32px; border-radius: 50%; background: #4a90d9;
  display: flex; align-items: center; justify-content: center; cursor: pointer;
  border: none; color: #fff; font-size: 16px;
}
.search-icon:hover { background: #3a7bc8; }

.suggest-dropdown {
  position: absolute; top: 52px; left: 16px; right: 16px;
  max-width: 600px; margin: 0 auto;
  background: #fff; border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.12);
  max-height: 380px; overflow-y: auto; display: none; z-index: 101;
  pointer-events: auto;
}
.suggest-dropdown.show { display: block; }
.dropdown-section {
  padding: 8px 16px 4px;
  font-size: 11px; color: #999; font-weight: 600;
  letter-spacing: 0.5px;
}
.dropdown-empty {
  padding: 16px; text-align: center;
  font-size: 13px; color: #bbb;
}
.suggest-item {
  padding: 12px 16px; cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  display: flex; align-items: center; gap: 10px;
}
.suggest-item:last-child { border-bottom: none; }
.suggest-item:hover { background: #f5f8fc; }
.pin-icon { color: #e74c3c; font-size: 18px; flex-shrink: 0; }
.info { flex: 1; min-width: 0; }
.name { font-size: 14px; font-weight: 500; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.district { font-size: 12px; color: #999; margin-top: 2px; }
</style>
