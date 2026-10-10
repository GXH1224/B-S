<template>
  <span :class="levelClass(level)">{{ level || '未评级' }}</span>
</template>

<script setup lang="ts">
import { levelClass } from '@/utils/format'

defineProps<{ level?: string }>()
</script>
