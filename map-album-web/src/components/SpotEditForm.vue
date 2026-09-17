<template>
  <div>
    <div class="section-title">{{ mode === 'create' ? '📝 新建打卡点' : '✏️ 编辑打卡点' }}</div>
    <div class="form-group">
      <label>打卡点名称 <span v-if="mode==='create'" style="color:red">*</span></label>
      <input v-model="form.name" placeholder="给这个位置起个名字">
    </div>
    <div class="form-row">
      <div class="form-group"><label>经度</label><input :value="form.lng" readonly></div>
      <div class="form-group"><label>纬度</label><input :value="form.lat" readonly></div>
    </div>
    <div class="form-group">
      <label>分类</label>
      <select v-model="form.category">
        <option value="scenic">景点</option>
        <option value="viewpoint">观景台</option>
        <option value="restaurant">餐厅</option>
        <option value="activity">活动</option>
        <option value="other">其他</option>
      </select>
    </div>
    <div class="form-group"><label>标签（逗号分隔）</label><input v-model="form.tags" placeholder="例如：花海,日出,徒步"></div>
    <div class="form-group"><label>描述</label><textarea v-model="form.description" placeholder="简单描述一下这个地方…"></textarea></div>
    <div class="form-group"><label>地址</label><input v-model="form.address"></div>
    <div class="form-row">
      <div class="form-group"><label>省</label><input v-model="form.province"></div>
      <div class="form-group"><label>市</label><input v-model="form.city"></div>
      <div class="form-group"><label>区</label><input v-model="form.district"></div>
    </div>
    <button class="btn btn-primary btn-block" @click="onSubmit">
      {{ mode === 'create' ? '创建打卡点' : '保存修改' }}
    </button>
    <button class="btn btn-block btn-cancel" @click="$emit('cancel')">
      {{ mode === 'create' ? '取消' : '返回详情' }}
    </button>
  </div>
</template>

<script setup>
import { reactive } from 'vue'

const props = defineProps({
  initial: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'create' } // 'create' | 'edit'
})

const emit = defineEmits(['submit', 'cancel'])

const form = reactive({
  id: props.initial.id,
  name: props.initial.name || '',
  lat: props.initial.lat || 0,
  lng: props.initial.lng || 0,
  category: props.initial.category || 'scenic',
  tags: props.initial.tags || '',
  description: props.initial.description || '',
  address: props.initial.address || '',
  province: props.initial.province || '',
  city: props.initial.city || '',
  district: props.initial.district || ''
})

function onSubmit() {
  if (!form.name.trim()) {
    alert('请输入打卡点名称')
    return
  }
  const data = {
    ...form,
    name: form.name.trim(),
    tags: form.tags.trim(),
    description: form.description.trim(),
    address: form.address.trim(),
    province: form.province.trim(),
    city: form.city.trim(),
    district: form.district.trim(),
    lat: parseFloat(form.lat),
    lng: parseFloat(form.lng)
  }
  emit('submit', data)
}
</script>

<style scoped>
.section-title { font-size: 14px; font-weight: 600; color: #333; margin: 0 0 12px; padding-bottom: 6px; border-bottom: 1.5px solid #f0f0f0; }
.form-group { margin-bottom: 14px; }
.form-group label { display: block; font-size: 13px; font-weight: 500; color: #555; margin-bottom: 4px; }
.form-group input, .form-group textarea, .form-group select {
  width: 100%; padding: 8px 12px; border: 1px solid #ddd; border-radius: 8px;
  font-size: 14px; outline: none; font-family: inherit;
}
.form-group input:focus, .form-group textarea:focus, .form-group select:focus { border-color: #4a90d9; }
.form-group textarea { resize: vertical; min-height: 60px; }
.form-row { display: flex; gap: 10px; }
.form-row .form-group { flex: 1; }
.btn {
  padding: 8px 18px; border-radius: 8px; border: none; cursor: pointer;
  font-size: 14px; font-weight: 500; display: inline-flex; align-items: center; gap: 4px;
  min-height: 44px; /* 移动端最低触摸尺寸 */
  -webkit-tap-highlight-color: transparent;
}
.btn-primary { background: #4a90d9; color: #fff; }
.btn-primary:hover { background: #3a7bc8; }
.btn-primary:active { background: #2e6cb8; }
.btn-block { width: 100%; justify-content: center; margin-top: 6px; }
.btn-cancel { background: #f5f5f5; color: #666; margin-top: 8px; }

/* 移动端放大表单控件 */
@media (max-width: 768px) {
  .form-group input, .form-group textarea, .form-group select {
    padding: 10px 12px; font-size: 16px; /* 16px 防止 iOS 缩放 */
    min-height: 44px;
  }
  .form-group textarea { min-height: 80px; }
  .form-group label { font-size: 14px; margin-bottom: 6px; }
  .form-row { gap: 8px; }
}
</style>
