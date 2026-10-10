<template>
  <div class="ees-stat" :class="variant ? `ees-stat--${variant}` : ''">
    <div class="ees-stat-label">
      <el-icon v-if="icon" style="vertical-align: -2px; margin-right: 5px"><component :is="icon" /></el-icon>
      {{ label }}
    </div>
    <div class="ees-stat-value">
      {{ value }}<small v-if="unit">{{ unit }}</small>
    </div>
    <div v-if="foot" class="ees-stat-foot">{{ foot }}</div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
  unit?: string
  foot?: string
  icon?: string
  variant?: 'primary' | 'success' | 'warning' | 'danger' | ''
}>()
</script>
