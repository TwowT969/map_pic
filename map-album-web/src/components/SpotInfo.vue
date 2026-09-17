<template>
  <div>
    <div class="section-title">📌 打卡点信息</div>
    <div class="spot-info">
      <div class="info-row"><span class="label">名称</span><span class="value">{{ spot.name || '-' }}</span></div>
      <div v-if="spot.category" class="info-row"><span class="label">分类</span><span class="value">{{ catLabel(spot.category) }}</span></div>
      <div v-if="spot.tags" class="info-row"><span class="label">标签</span><span class="value">{{ spot.tags }}</span></div>
      <div v-if="spot.description" class="info-row"><span class="label">描述</span><span class="value">{{ spot.description }}</span></div>
      <div class="info-row"><span class="label">坐标</span><span class="value">{{ lat(spot) }}, {{ lng(spot) }}</span></div>
      <div v-if="spot.address" class="info-row"><span class="label">地址</span><span class="value">{{ spot.address }}</span></div>
      <div v-if="spot.city" class="info-row"><span class="label">城市</span><span class="value">{{ spot.city }}</span></div>
      <div class="info-row"><span class="label">照片数</span><span class="value">{{ spot.photoCount || 0 }} 张</span></div>
      <div class="info-row"><span class="label">创建时间</span><span class="value">{{ formatTime(spot.createTime) }}</span></div>
    </div>
    <div class="btn-row">
      <button class="btn btn-primary btn-sm" @click="$emit('edit')">✏️ 编辑</button>
    </div>
  </div>
</template>

<script setup>
defineProps({ spot: { type: Object, required: true } })
defineEmits(['edit'])

function lat(s) { try { return parseFloat(s.lat).toFixed(6) } catch { return '-' } }
function lng(s) { try { return parseFloat(s.lng).toFixed(6) } catch { return '-' } }
function catLabel(c) {
  const map = { scenic: '🏞️ 景点', viewpoint: '👀 观景台', restaurant: '🍽️ 餐厅', activity: '🎉 活动', other: '📌 其他' }
  return map[c] || c
}
function formatTime(t) { if (!t) return '-'; return t.replace('T', ' ').substring(0, 19) }
</script>

<style scoped>
.section-title {
  font-size: 14px; font-weight: 600; color: #333; margin: 0 0 8px;
  padding-bottom: 6px; border-bottom: 1.5px solid #f0f0f0;
}
.spot-info { margin-bottom: 12px; }
.info-row { display: flex; padding: 6px 0; font-size: 13px; border-bottom: 1px solid #f9f9f9; }
.label { color: #999; width: 60px; flex-shrink: 0; }
.value { color: #333; flex: 1; word-break: break-all; }
.btn {
  padding: 8px 18px; border-radius: 8px; border: none; cursor: pointer; font-size: 14px; font-weight: 500;
}
.btn-primary { background: #4a90d9; color: #fff; }
.btn-primary:hover { background: #3a7bc8; }
.btn-sm { padding: 5px 12px; font-size: 12px; }
.btn-row { display: flex; gap: 8px; }
.btn-outline { background: #fff; color: #4a90d9; border: 1px solid #4a90d9; }
.btn-outline:hover { background: #f0f6ff; }
</style>
