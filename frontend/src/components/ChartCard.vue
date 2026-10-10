<template>
  <div class="ees-chart-card">
    <div class="ees-chart-head">
      <div>
        <div class="ees-chart-title">{{ chart?.title || title }}</div>
        <div v-if="chart?.description" class="ees-chart-sub">{{ chart.description }}</div>
      </div>
      <div class="chart-actions">
        <slot name="actions" />
        <el-tooltip v-if="showExport && !empty" content="导出图片 PNG" placement="top">
          <el-button text circle size="small" @click="exportImage">
            <el-icon><Download /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <!-- 数据不足时显示原因，不显示误导性空图（方案 7.3） -->
    <div v-if="empty" class="ees-chart-empty">
      <el-icon :size="26"><InfoFilled /></el-icon>
      <div>{{ chart?.emptyReason || '暂无可用数据，无法生成该图形' }}</div>
      <div class="ees-hint">图形数据全部来自后端评估结果数据集，与表格同源</div>
    </div>

    <!-- 饼图：权重构成 + 等级分布 双图并排 -->
    <div v-else-if="isPie" class="pie-pair">
      <EesChart ref="pieLeftRef" :option="pieOptions.weight" :height="height" />
      <EesChart ref="pieRightRef" :option="pieOptions.level" :height="height" />
    </div>

    <EesChart v-else ref="chartRef" :option="option" :height="height" />

    <div class="chart-foot">
      <span class="ees-hint">{{ chart?.unit ? `单位：${chart.unit}` : '' }}</span>
      <span class="ees-hint">数据来源：{{ chart?.dataSource || '后端评估结果' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import EesChart from './EesChart.vue'
import { buildChartOption, buildPieChartOptions } from '@/utils/chartOptions'
import type { ChartData } from '@/api'

const props = withDefaults(
  defineProps<{
    chart?: ChartData
    title?: string
    height?: string
    showExport?: boolean
  }>(),
  { height: '290px', showExport: true }
)

const chartRef = ref<InstanceType<typeof EesChart>>()
const pieLeftRef = ref<InstanceType<typeof EesChart>>()
const pieRightRef = ref<InstanceType<typeof EesChart>>()

const isPie = computed(() => props.chart?.chartType === 'pie' && !props.chart?.emptyReason)
const option = computed(() => buildChartOption(props.chart))
const pieOptions = computed(() => buildPieChartOptions(props.chart))
const empty = computed(() => {
  if (!props.chart || props.chart.emptyReason) return true
  if (isPie.value) {
    const p = pieOptions.value
    return !p.weight && !p.level
  }
  return !option.value
})

function exportImage() {
  const url = isPie.value
    ? pieLeftRef.value?.getDataURL() || pieRightRef.value?.getDataURL()
    : chartRef.value?.getDataURL()
  if (!url) {
    ElMessage.warning('当前图形尚未渲染完成')
    return
  }
  const link = document.createElement('a')
  link.href = url
  link.download = `${props.chart?.title || 'chart'}.png`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}
</script>

<style scoped>
.chart-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.pie-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

@media (max-width: 720px) {
  .pie-pair {
    grid-template-columns: 1fr;
  }
}

.chart-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 2px 2px 6px;
}
</style>
