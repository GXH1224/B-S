<template>
  <div ref="hostRef" class="ees-chart-host" :style="{ height: height }"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

/**
 * 通用 ECharts 容器：
 * - 依赖本地打包的 echarts，不使用任何 CDN（离线部署要求）
 * - 自动响应容器尺寸变化
 * - 暴露 getDataURL 供“导出图片”使用
 */
const props = withDefaults(
  defineProps<{
    option: any
    height?: string
  }>(),
  { height: '300px' }
)

const hostRef = ref<HTMLDivElement>()
const chart = shallowRef<echarts.ECharts>()
let observer: ResizeObserver | undefined

function render() {
  if (!hostRef.value || !props.option) return
  if (!chart.value) {
    chart.value = echarts.init(hostRef.value, undefined, { renderer: 'canvas' })
    observer = new ResizeObserver(() => chart.value?.resize())
    observer.observe(hostRef.value)
  }
  chart.value.setOption(props.option, true)
  chart.value.resize()
}

function resize() {
  chart.value?.resize()
}

onMounted(async () => {
  await nextTick()
  render()
  window.addEventListener('resize', resize)
})

watch(
  () => props.option,
  async () => {
    await nextTick()
    render()
  },
  { deep: true }
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  observer?.disconnect()
  chart.value?.dispose()
})

/** 导出当前图形为 PNG DataURL */
function getDataURL(): string {
  return chart.value?.getDataURL({ pixelRatio: 2, backgroundColor: '#ffffff' }) || ''
}

defineExpose({ getDataURL, resize })
</script>

<style scoped>
.ees-chart-host {
  width: 100%;
}
</style>
