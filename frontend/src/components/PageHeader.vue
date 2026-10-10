<template>
  <div class="ees-page-head">
    <div>
      <h2 class="ees-page-title">{{ title }}</h2>
      <p v-if="desc" class="ees-page-desc">{{ desc }}</p>
    </div>
    <div class="ees-page-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ title: string; desc?: string }>()
</script>
