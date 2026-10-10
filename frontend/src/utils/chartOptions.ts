import type { ChartData } from '@/api'

/** ECharts 主题色板（本地固定，不依赖任何外部资源） */
export const PALETTE = [
  '#1f6feb',
  '#17a673',
  '#e6a23c',
  '#e04b4b',
  '#8b5cf6',
  '#0ea5e9',
  '#f97316',
  '#14b8a6',
  '#a855f7',
  '#64748b'
]

const BASE_TEXT = { color: '#1f2a3d', fontSize: 12 }
const AXIS_LINE = { lineStyle: { color: '#e3e9f2' } }
const SPLIT_LINE = { lineStyle: { color: '#eef2f8', type: 'dashed' as const } }

export const GRADE_COLOR: Record<string, string> = {
  优: '#17a673',
  良: '#1f6feb',
  中: '#e6a23c',
  差: '#e04b4b',
  未评级: '#8b98ad'
}

function num(v: any): number | null {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

/**
 * 由后端图形数据生成 ECharts option。
 * 数据全部来自后端评估结果数据集，前端不做任何数值重算（方案 7.3 结果一致性）。
 */
export function buildChartOption(chart: ChartData | undefined): any {
  if (!chart || chart.emptyReason) return null
  switch (chart.chartType) {
    case 'bar':
      return barOption(chart)
    case 'line':
      return lineOption(chart)
    case 'pie':
      // 饼图由 buildPieChartOptions 拆分为两个独立图形
      return pieOptionPlaceholder()
    case 'radar':
      return radarOption(chart)
    case 'scatter':
      return scatterOption(chart)
    case 'heatmap':
      return heatmapOption(chart)
    case 'boxplot':
      return boxplotOption(chart)
    case 'rankBar':
      return rankBarOption(chart)
    default:
      return null
  }
}

/** 1. 柱状图：指标得分 / 贡献值 / 权重 */
function barOption(chart: ChartData) {
  const series = (chart.series || []).map((s: any, i: number) => ({
    name: s.name,
    type: 'bar',
    barMaxWidth: 34,
    itemStyle: { color: PALETTE[i % PALETTE.length], borderRadius: [3, 3, 0, 0] },
    emphasis: { focus: 'series' },
    data: (s.data || []).map(num)
  }))
  return {
    color: PALETTE,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, textStyle: BASE_TEXT },
    grid: { left: 48, right: 20, top: 42, bottom: 62 },
    xAxis: {
      type: 'category',
      data: chart.categories || [],
      axisLabel: { ...BASE_TEXT, interval: 0, rotate: (chart.categories || []).length > 5 ? 28 : 0, width: 110, overflow: 'truncate' },
      axisLine: AXIS_LINE,
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      name: chart.unit || '',
      nameTextStyle: BASE_TEXT,
      axisLabel: BASE_TEXT,
      axisLine: { show: false },
      splitLine: SPLIT_LINE
    },
    dataZoom: (chart.categories || []).length > 12
      ? [{ type: 'slider', height: 14, bottom: 12, start: 0, end: 60 }]
      : undefined,
    series
  }
}

/** 2. 折线图：多批次 / 多时间点趋势 */
function lineOption(chart: ChartData) {
  const series = (chart.series || []).map((s: any, i: number) => ({
    name: s.name,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 7,
    lineStyle: { width: 2.4, color: PALETTE[i % PALETTE.length] },
    itemStyle: { color: PALETTE[i % PALETTE.length] },
    areaStyle: {
      color: {
        type: 'linear',
        x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: 'rgba(31,111,235,0.22)' },
          { offset: 1, color: 'rgba(31,111,235,0.02)' }
        ]
      }
    },
    data: (s.data || []).map(num)
  }))
  return {
    color: PALETTE,
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: BASE_TEXT },
    grid: { left: 48, right: 24, top: 42, bottom: 46 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: chart.categories || [],
      axisLabel: { ...BASE_TEXT, rotate: (chart.categories || []).length > 8 ? 26 : 0 },
      axisLine: AXIS_LINE,
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      name: chart.unit || '',
      nameTextStyle: BASE_TEXT,
      axisLabel: BASE_TEXT,
      axisLine: { show: false },
      splitLine: SPLIT_LINE
    },
    dataZoom: (chart.categories || []).length > 14
      ? [{ type: 'slider', height: 14, bottom: 8 }]
      : undefined,
    series
  }
}

/** 3. 饼图 / 环形图：拆分为“权重构成”和“等级分布”两个独立 option 并排展示 */
export function buildPieChartOptions(chart: ChartData | undefined): { weight: any; level: any } {
  if (!chart) return { weight: null, level: null }
  const weightPie = (chart.weightPie || []).map((x: any, i: number) => ({
    name: x.name,
    value: num(x.value) ?? 0,
    itemStyle: { color: PALETTE[i % PALETTE.length] }
  }))
  const levelPie = (chart.levelPie || []).map((x: any) => ({
    name: x.name,
    value: num(x.value) ?? 0,
    itemStyle: { color: GRADE_COLOR[x.name] || '#8b98ad' }
  }))

  const make = (data: any[], title: string) => ({
    title: {
      text: title,
      left: 'center',
      top: 2,
      textStyle: { ...BASE_TEXT, fontSize: 12.5, fontWeight: 600 }
    },
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 2, textStyle: BASE_TEXT, type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '64%'],
        center: ['50%', '52%'],
        avoidLabelOverlap: true,
        label: { show: true, formatter: '{b}\n{d}%', ...BASE_TEXT },
        labelLine: { length: 8, length2: 8 },
        data
      }
    ]
  })

  return {
    weight: weightPie.length ? make(weightPie, '指标权重构成') : null,
    level: levelPie.length ? make(levelPie, '评判等级分布') : null
  }
}

function pieOptionPlaceholder() {
  return null
}

/** 4. 雷达图：多维度能力对比 */
function radarOption(chart: ChartData) {
  const names = chart.indicatorNames || []
  const max = num(chart.max) ?? 100
  return {
    color: PALETTE,
    tooltip: { trigger: 'item' },
    legend: { top: 0, textStyle: BASE_TEXT, type: 'scroll' },
    radar: {
      indicator: names.map((n: string) => ({ name: n, max })),
      center: ['50%', '56%'],
      radius: '62%',
      axisName: { ...BASE_TEXT, fontSize: 11.5 },
      splitLine: { lineStyle: { color: '#e3e9f2' } },
      splitArea: { areaStyle: { color: ['#ffffff', '#f8fbff'] } },
      axisLine: { lineStyle: { color: '#e3e9f2' } }
    },
    series: [
      {
        type: 'radar',
        symbolSize: 5,
        areaStyle: { opacity: 0.14 },
        data: (chart.series || []).map((s: any, i: number) => ({
          name: s.name,
          value: (s.value || []).map(num),
          lineStyle: { width: 2, color: PALETTE[i % PALETTE.length] },
          itemStyle: { color: PALETTE[i % PALETTE.length] }
        }))
      }
    ]
  }
}

/** 5. 散点图：两项指标关系 */
function scatterOption(chart: ChartData) {
  const points = (chart.points || []).map((p: any[]) => [num(p[0]), num(p[1]), p[2]])
  return {
    color: PALETTE,
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => {
        const v = p.value || []
        return `${v[2] || '数据点'}<br/>${chart.xIndicatorName || chart.xIndicatorCode}: ${v[0]}<br/>${
          chart.yIndicatorName || chart.yIndicatorCode
        }: ${v[1]}`
      }
    },
    grid: { left: 56, right: 24, top: 30, bottom: 46 },
    xAxis: {
      type: 'value',
      name: chart.xIndicatorName || chart.xIndicatorCode,
      nameLocation: 'middle',
      nameGap: 28,
      nameTextStyle: BASE_TEXT,
      axisLabel: BASE_TEXT,
      splitLine: SPLIT_LINE,
      axisLine: AXIS_LINE
    },
    yAxis: {
      type: 'value',
      name: chart.yIndicatorName || chart.yIndicatorCode,
      nameTextStyle: BASE_TEXT,
      axisLabel: BASE_TEXT,
      splitLine: SPLIT_LINE,
      axisLine: { show: false }
    },
    series: [
      {
        type: 'scatter',
        symbolSize: 14,
        itemStyle: { color: 'rgba(31,111,235,0.75)', borderColor: '#fff', borderWidth: 1.2 },
        data: points
      }
    ]
  }
}

/** 6. 热力图：指标 × 对象 得分矩阵 */
function heatmapOption(chart: ChartData) {
  const data = (chart.data || []).map((d: any) => d.value)
  return {
    tooltip: {
      position: 'top',
      formatter: (p: any) => {
        const [x, y, v] = p.value
        return `${chart.yLabels?.[y] || ''}<br/>${chart.xLabels?.[x] || ''}: ${v}`
      }
    },
    grid: { left: 96, right: 26, top: 22, bottom: 78 },
    xAxis: {
      type: 'category',
      data: chart.xLabels || [],
      splitArea: { show: true },
      axisLabel: { ...BASE_TEXT, rotate: (chart.xLabels || []).length > 6 ? 30 : 0 },
      axisLine: AXIS_LINE
    },
    yAxis: {
      type: 'category',
      data: chart.yLabels || [],
      splitArea: { show: true },
      axisLabel: BASE_TEXT,
      axisLine: { show: false }
    },
    visualMap: {
      min: num(chart.min) ?? 0,
      max: num(chart.max) ?? 100,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 8,
      inRange: { color: ['#fdeaea', '#fdf3e3', '#e8f1fe', '#e6f7f0', '#17a673'] },
      textStyle: BASE_TEXT
    },
    series: [
      {
        type: 'heatmap',
        data,
        label: { show: (chart.xLabels || []).length * (chart.yLabels || []).length <= 40, fontSize: 11 },
        emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.25)' } }
      }
    ]
  }
}

/** 7. 箱线图：分布、离群值与稳定性 */
function boxplotOption(chart: ChartData) {
  const boxData = (chart.boxData || []).map((b: number[]) => b.map(num))
  const outliers: any[] = []
  ;(chart.outliers || []).forEach((arr: number[], idx: number) => {
    ;(arr || []).forEach((v) => outliers.push([idx, num(v)]))
  })
  return {
    color: PALETTE,
    tooltip: { trigger: 'item' },
    grid: { left: 52, right: 22, top: 28, bottom: 72 },
    xAxis: {
      type: 'category',
      data: chart.categories || [],
      axisLabel: { ...BASE_TEXT, rotate: (chart.categories || []).length > 5 ? 26 : 0, width: 110, overflow: 'truncate' },
      axisLine: AXIS_LINE,
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      name: chart.unit || '',
      nameTextStyle: BASE_TEXT,
      axisLabel: BASE_TEXT,
      axisLine: { show: false },
      splitLine: SPLIT_LINE
    },
    series: [
      {
        name: '得分分布',
        type: 'boxplot',
        boxWidth: [10, 38],
        itemStyle: { color: 'rgba(31,111,235,0.16)', borderColor: '#1f6feb', borderWidth: 1.6 },
        data: boxData
      },
      {
        name: '离群点',
        type: 'scatter',
        symbolSize: 7,
        itemStyle: { color: '#e04b4b' },
        data: outliers
      }
    ]
  }
}

/** 8. 排名条形图：对象综合得分排序 */
function rankBarOption(chart: ChartData) {
  const cats: string[] = chart.categories || []
  const data = (chart.series?.[0]?.data || []).map(num)
  // 横向条形图自下而上绘制，反转数组使排名第一位于顶部
  const revCats = [...cats].reverse()
  const revData = [...data].reverse()
  const rankings: number[] = [...(chart.rankings || [])].reverse()
  const levels: string[] = [...(chart.levels || [])].reverse()

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (ps: any) => {
        const p = Array.isArray(ps) ? ps[0] : ps
        const i = p.dataIndex
        return `第 ${rankings[i] ?? '-'} 名　${p.name}<br/>综合得分：${p.value}　等级：${levels[i] ?? '-'}`
      }
    },
    grid: { left: 128, right: 58, top: 22, bottom: 34 },
    xAxis: {
      type: 'value',
      name: chart.unit || '',
      nameTextStyle: BASE_TEXT,
      axisLabel: BASE_TEXT,
      axisLine: { show: false },
      splitLine: SPLIT_LINE
    },
    yAxis: {
      type: 'category',
      data: revCats,
      axisLabel: { ...BASE_TEXT, width: 118, overflow: 'truncate' },
      axisLine: AXIS_LINE,
      axisTick: { show: false }
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 22,
        label: { show: true, position: 'right', formatter: '{c}', ...BASE_TEXT, fontWeight: 600 },
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          color: (p: any) => GRADE_COLOR[levels[p.dataIndex]] || '#1f6feb'
        },
        data: revData
      }
    ]
  }
}
