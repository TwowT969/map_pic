<template>
  <div class="map-wrapper" ref="mapWrapper">
    <div id="amap-container" class="map-inner"></div>
  </div>
</template>

<script setup>
import { ref, inject, onMounted, onBeforeUnmount } from 'vue'

const emit = defineEmits(['mapReady', 'spotClick'])

const mapWrapper = ref(null)
const spotsStore = inject('spotsStore')
const showToast = inject('showToast')

onMounted(() => {
  emit('mapReady', 'amap-container')
})

// 监听 spotsStore 的 spot click
// MapContainer 通过 provide 的 spotsStore 间接处理 marker 点击
// marker 点击在 useSpots 中设置 currentSpot，这里需要监听
const map = inject('map')

// 轮询检测 currentSpot 变化来触发 spot-click 事件
let _currentSpotId = null
const checkInterval = setInterval(() => {
  if (!spotsStore) return
  const cs = spotsStore.currentSpot.value
  if (cs && cs.id !== _currentSpotId) {
    _currentSpotId = cs.id
    emit('spotClick', cs)
  }
}, 200)

onBeforeUnmount(() => {
  clearInterval(checkInterval)
})
</script>

<style scoped>
.map-wrapper { width: 100%; height: 100%; }
.map-inner { width: 100%; height: 100%; }
</style>
