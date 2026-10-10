<template>
  <div v-loading="loading" class="ees-page">
    <PageHeader
      title="综合驾驶舱"
      desc="数据来自当前账号的授权范围：项目、任务、快照与结果均按数据权限过滤，仅展示可见的汇总信息。"
    >
      <template #actions>
        <el-select
          v-model="projectId"
          class="project-select"
          placeholder="全部项目"
          clearable
          filterable
          @change="onProjectChange"
        >
          <el-option
            v-for="item in projectOptions"
            :key="item.id"
            :label="item.name || item.code"
            :value="item.id"
          >
            <span>{{ item.name || item.code }}</span>
            <span class="option-code ees-mono">{{ item.code }}</span>
          </el-option>
        </el-select>
        <el-button type="primary" plain :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </template>
    </PageHeader>

    <!-- 1. 指标卡片 -->
    <div class="ees-stat-grid">
      <StatCard
        label="项目数"
        icon="Folder"
        :value="num(overview.projectCount)"
        :foot="`进行中 ${num(overview.activeProjectCount)} / 已归档 ${num(overview.archivedProjectCount)}`"
      />
      <StatCard
        label="评估任务"
        icon="List"
        :value="num(overview.taskTotal)"
        :foot="`成功 ${num(overview.taskSuccess)} · 失败 ${num(overview.taskFailed)} · 运行中 ${num(overview.taskRunning)}`"
      />
      <StatCard
        label="数据快照"
        icon="Camera"
        :value="num(overview.snapshotCount)"
        :foot="`数据批次 ${num(overview.batchCount)}`"
      />
      <StatCard
        label="评估结果"
        icon="DataAnalysis"
        :value="num(overview.resultCount)"
        :foot="`已发布 ${num(overview.publishedResultCount)}`"
      />
      <StatCard
        label="平均得分"
        icon="TrendCharts"
        variant="primary"
        unit="分"
        :value="fmtScore(overview.avgScore)"
      />
      <StatCard
        label="可用算法"
        icon="MagicStick"
        :value="num(overview.algorithmCount)"
        :foot="algorithmFoot"
      />
    </div>

    <!-- 2. 异常任务告警 -->
    <div v-if="alerts.length" class="ees-card alert-card">
      <div class="alert-head">
        <el-icon class="alert-icon" :size="17"><WarningFilled /></el-icon>
        <span class="alert-title">异常任务告警</span>
        <el-tag type="danger" size="small" effect="dark">{{ alerts.length }} 项</el-tag>
        <span class="ees-hint">以下任务执行失败或超时，点击任务编号查看失败原因并重试</span>
      </div>
      <el-table :data="alerts" size="small" class="alert-table" @row-click="openAlertTask">
        <el-table-column label="任务编号" min-width="150">
          <template #default="{ row }">
            <a
              v-if="row?.taskId"
              class="dash-link ees-mono"
              :href="`#/tasks/${row.taskId}`"
            >{{ row.taskCode || `#${row.taskId}` }}</a>
            <span v-else class="ees-mono">{{ row?.taskCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="taskName" label="任务名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.taskName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="taskStatusType(row?.status)" size="small" effect="plain">
              {{ TASK_STATUS_LABEL[row?.status] || row?.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="错误码" width="110">
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.errorCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="错误原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.errorMsg || '未记录错误原因' }}</template>
        </el-table-column>
        <el-table-column label="发生时间" width="165">
          <template #default="{ row }">{{ fmtDateTime(row?.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 3. 趋势与等级分布 -->
    <div class="ees-chart-grid">
      <div class="ees-chart-card">
        <div class="ees-chart-head">
          <div>
            <div class="ees-chart-title">任务趋势（{{ rangeTitle }}）</div>
            <div class="ees-chart-sub">按日期统计的评估任务创建数量 · 与右侧等级分布同区间</div>
          </div>
          <!-- 统计区间：快捷区间 + 起止日期。两者共用同一区间，改动后两张图一起刷新 -->
          <div class="chart-range-bar">
            <el-select
              v-model="rangePreset"
              size="small"
              class="range-preset"
              placeholder="快捷区间"
              @change="onPresetChange"
            >
              <el-option v-for="p in RANGE_PRESETS" :key="p.value" :label="p.label" :value="p.value" />
              <el-option label="自定义" value="CUSTOM" />
            </el-select>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              size="small"
              class="range-picker"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :clearable="false"
              @change="onRangePick"
            />
          </div>
        </div>
        <EesChart v-if="trendOption" :option="trendOption" height="280px" />
        <div v-else class="ees-chart-empty">
          <el-icon :size="26"><InfoFilled /></el-icon>
          <div>暂无任务趋势数据</div>
          <div class="ees-hint">{{ rangeTitle }}内没有评估任务记录</div>
        </div>
      </div>

      <div class="ees-chart-card">
        <div class="ees-chart-head">
          <div>
            <div class="ees-chart-title">评判等级分布</div>
            <div class="ees-chart-sub">{{ rangeTitle }}内全部评估结果按评判等级统计</div>
          </div>
        </div>
        <EesChart v-if="levelOption" :option="levelOption" height="280px" />
        <div v-else class="ees-chart-empty">
          <el-icon :size="26"><InfoFilled /></el-icon>
          <div>暂无等级分布数据</div>
          <div class="ees-hint">{{ rangeTitle }}内尚未产生评估结果</div>
        </div>
      </div>
    </div>

    <!-- 4. 最新评估结果 -->
    <div class="ees-card">
      <div class="card-head">
        <span class="card-title">最新评估结果</span>
        <el-button text type="primary" @click="goResults">
          查看全部<el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
      <el-table :data="latestResults" size="small" @row-click="openResult">
        <el-table-column label="任务编号" min-width="150">
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.taskCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="项目编码" min-width="120">
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.projectCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="对象编码" min-width="130">
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.objectCode || '-' }}</span>
            <span v-if="row?.objectName" class="cell-sub">{{ row.objectName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="综合得分" width="105" align="right">
          <template #default="{ row }">
            <span class="ees-score">{{ fmtScore(row?.totalScore) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评判等级" width="100" align="center">
          <template #default="{ row }">
            <LevelTag :level="row?.level" />
          </template>
        </el-table-column>
        <el-table-column label="排名" width="80" align="center">
          <template #default="{ row }">{{ row?.ranking ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="算法" min-width="120">
          <template #default="{ row }">
            {{ ALGORITHM_LABEL[row?.algorithm] || row?.algorithmLabel || row?.algorithm || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="时间" width="165">
          <template #default="{ row }">{{ fmtDateTime(row?.createdAt) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty :image-size="60" description="暂无评估结果" />
        </template>
      </el-table>
    </div>

    <!-- 5. 项目态势 -->
    <div class="ees-card">
      <div class="card-head">
        <span class="card-title">项目态势</span>
        <span class="ees-hint">点击任一行可将该行设为当前项目上下文</span>
      </div>
      <el-table
        :data="projectRows"
        size="small"
        highlight-current-row
        :current-row-key="projectId"
        row-key="id"
        @row-click="onProjectRowClick"
      >
        <el-table-column label="项目编码" min-width="130">
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.projectCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="项目名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.projectName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="projectStatusType(row?.status)" size="small" effect="plain">
              {{ PROJECT_STATUS_LABEL[row?.status] || row?.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="任务数" width="90" align="right">
          <template #default="{ row }">{{ num(row?.taskCount) }}</template>
        </el-table-column>
        <el-table-column label="结果数" width="90" align="right">
          <template #default="{ row }">{{ num(row?.resultCount) }}</template>
        </el-table-column>
        <el-table-column label="平均得分" width="110" align="right">
          <template #default="{ row }">
            <span class="ees-score">{{ fmtScore(row?.avgScore) }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :image-size="60" description="暂无可见项目" />
        </template>
      </el-table>
    </div>

    <!-- 6. 算法使用情况 + 最近任务 -->
    <div class="dash-two-col">
      <div class="ees-card">
        <div class="card-head">
          <span class="card-title">算法使用情况</span>
          <span class="ees-hint">按评估任务统计</span>
        </div>
        <el-table :data="algorithmUsage" size="small">
          <el-table-column label="算法" min-width="140">
            <template #default="{ row }">
              {{ row?.algorithmLabel || ALGORITHM_LABEL[row?.algorithm] || row?.algorithm || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="算法版本" min-width="150">
            <template #default="{ row }">
              <span class="ees-mono">{{ row?.algorithmVersion || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="任务数" width="90" align="right">
            <template #default="{ row }">{{ num(row?.count) }}</template>
          </el-table-column>
          <template #empty>
            <el-empty :image-size="60" description="暂无算法使用记录" />
          </template>
        </el-table>
      </div>

      <div class="ees-card">
        <div class="card-head">
          <span class="card-title">最近评估任务</span>
          <el-button text type="primary" @click="goTasks">
            查看全部<el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <el-table :data="recentTasks" size="small" @row-click="openRecentTask">
          <el-table-column label="任务编号" min-width="150">
            <template #default="{ row }">
              <span class="ees-mono">{{ row?.taskCode || row?.code || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="任务名称" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row?.taskName || row?.name || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="taskStatusType(row?.status)" size="small" effect="plain">
                {{ TASK_STATUS_LABEL[row?.status] || row?.status || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="165">
            <template #default="{ row }">{{ fmtDateTime(row?.createdAt || row?.startedAt) }}</template>
          </el-table-column>
          <template #empty>
            <el-empty :image-size="60" description="暂无评估任务" />
          </template>
        </el-table>
      </div>
    </div>

    <div class="ees-hint dash-foot">
      统计生成时间：{{ fmtDateTime(overview.generatedAt) }}　·　结果与图形数据均来自后端评估数据集，前端不做二次计算
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import LevelTag from '@/components/LevelTag.vue'
import EesChart from '@/components/EesChart.vue'
import { projectApi, systemApi, taskApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { GRADE_COLOR } from '@/utils/chartOptions'
import {
  ALGORITHM_LABEL,
  PROJECT_STATUS_LABEL,
  TASK_STATUS_LABEL,
  TASK_STATUS_TYPE,
  fmtDateTime,
  fmtScore
} from '@/utils/format'

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const router = useRouter()
const appStore = useAppStore()

const loading = ref(false)
const raw = ref<any>({})
const projectOptions = ref<any[]>([])
const recentTasks = ref<any[]>([])
const projectId = ref<number | undefined>(undefined)

const overview = computed<any>(() => raw.value || {})

/** 后端可能返回 null / 缺失字段，统一收敛为空数组 */
function arr<T = any>(value: T[] | null | undefined): T[] {
  return Array.isArray(value) ? value : []
}

/** 数值兜底：非法值按 0 展示，避免出现 NaN */
function num(value: any): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

/** el-tag 的 type 需为固定字面量联合类型 */
function taskStatusType(status?: string): TagType {
  const hit = TASK_STATUS_TYPE[String(status ?? '')]
  const allowed: TagType[] = ['primary', 'success', 'warning', 'info', 'danger']
  return hit && (allowed as string[]).includes(hit) ? (hit as TagType) : 'info'
}

function projectStatusType(status?: string): TagType {
  const map: Record<string, TagType> = { DRAFT: 'info', ACTIVE: 'success', ARCHIVED: 'warning' }
  return map[String(status ?? '')] || 'info'
}

const alerts = computed<any[]>(() => arr<any>(overview.value?.alerts))
const latestResults = computed<any[]>(() => arr<any>(overview.value?.latestResults))
const projectRows = computed<any[]>(() => arr<any>(overview.value?.projects))
const algorithmUsage = computed<any[]>(() => arr<any>(overview.value?.algorithmUsage))
const taskTrend = computed<any[]>(() => arr<any>(overview.value?.taskTrend))
const levelDistribution = computed<any[]>(() => arr<any>(overview.value?.levelDistribution))

const algorithmKeys = computed<string[]>(() => {
  const map = overview.value?.algorithms
  if (!map) return []
  if (Array.isArray(map)) {
    return map
      .map((item: any) => (typeof item === 'string' ? item : item?.algorithm))
      .filter((item: any) => !!item)
  }
  if (typeof map === 'object') return Object.keys(map)
  return []
})

const algorithmFoot = computed<string>(() =>
  algorithmKeys.value.length ? algorithmKeys.value.join(' / ') : '暂无算法信息'
)

/** 任务趋势：折线图（数据来自 taskTrend） */
const trendOption = computed<any>(() => {
  const rows = taskTrend.value
  if (!rows.length) return null
  return {
    tooltip: { trigger: 'axis' },
    // top 必须留够 y 轴名称（「任务数」）的高度：名称默认画在网格上沿之上，
    // 且再往上偏移 nameGap（默认 15）。原先 top 只有 26，名称顶到画布外，
    // 上半截被裁掉，看起来像「显示不全」。这里给到 46 并收紧 nameGap。
    grid: { left: 48, right: 24, top: 46, bottom: 42 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: rows.map((item) => fmtDateTime(item?.date, 'MM-DD')),
      // 区间拉到「近一年」时会有 300+ 个点，交给 ECharts 自动抽稀标签并隐藏重叠
      axisLabel: { color: '#5b6b86', fontSize: 12, hideOverlap: true },
      axisLine: { lineStyle: { color: '#e3e9f2' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      name: '任务数',
      nameGap: 10,
      nameTextStyle: { color: '#8b98ad', fontSize: 12 },
      axisLabel: { color: '#5b6b86', fontSize: 12 },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#eef2f8', type: 'dashed' } }
    },
    series: [
      {
        name: '任务数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 2.4, color: '#1f6feb' },
        itemStyle: { color: '#1f6feb' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(31,111,235,0.24)' },
              { offset: 1, color: 'rgba(31,111,235,0.02)' }
            ]
          }
        },
        data: rows.map((item) => num(item?.taskCount))
      }
    ]
  }
})

/** 等级分布：环形图（等级配色取自 chartOptions 的 GRADE_COLOR） */
const levelOption = computed<any>(() => {
  const rows = levelDistribution.value
  if (!rows.length) return null
  const data = rows.map((item) => {
    const level = String(item?.level ?? '未评级')
    return {
      name: level,
      value: num(item?.count),
      itemStyle: { color: GRADE_COLOR[level] || GRADE_COLOR['未评级'] }
    }
  })
  return {
    tooltip: { trigger: 'item', formatter: '{b}：{c} 项（{d}%）' },
    legend: { bottom: 0, textStyle: { color: '#5b6b86', fontSize: 12 }, type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['46%', '70%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
        label: { show: true, formatter: '{b} {d}%', color: '#5b6b86', fontSize: 12 },
        labelLine: { length: 8, length2: 8 },
        data
      }
    ]
  }
})

/** ---------------- 任务趋势 / 评判等级分布 的统计区间 ---------------- */

/** 快捷区间（两者都不传时后端按近 7 天处理）。days 为「含今天在内」的天数。 */
const RANGE_PRESETS = [
  { value: 'WEEK', label: '近一周', days: 7 },
  { value: 'MONTH', label: '近一个月', days: 30 },
  { value: 'YEAR', label: '近一年', days: 365 }
]

function fmtDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 取「含今天在内、共 days 天」的区间 [开始, 结束] */
function buildRange(days: number): [string, string] {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - (days - 1))
  return [fmtDate(start), fmtDate(end)]
}

const rangePreset = ref<string>('WEEK')
const dateRange = ref<[string, string]>(buildRange(7))

/** 图表标题里的区间描述：命中快捷区间显示名称，否则显示具体日期 */
const rangeTitle = computed<string>(() => {
  const hit = RANGE_PRESETS.find((p) => p.value === rangePreset.value)
  if (hit) return hit.label
  const [f, t] = dateRange.value || []
  return f && t ? `${f} ~ ${t}` : '近期'
})

function onPresetChange(value: string | number | boolean | undefined): void {
  const hit = RANGE_PRESETS.find((p) => p.value === value)
  if (!hit) return   // 选到「自定义」：保留当前日期，等用户自己挑起止日期
  rangePreset.value = hit.value
  dateRange.value = buildRange(hit.days)
  load()
}

function onRangePick(value: any): void {
  if (!Array.isArray(value) || value.length !== 2 || !value[0] || !value[1]) return
  let [f, t] = value as [string, string]
  // 与后端同一上限（366 天）：超出时按结束日期往前截断，否则请求会被后端拒绝、整页数据被清空
  const spanDays = Math.round((new Date(t).getTime() - new Date(f).getTime()) / 86400000) + 1
  if (spanDays > 366) {
    const end = new Date(t)
    const start = new Date(end)
    start.setDate(end.getDate() - 365)
    f = fmtDate(start)
    ElMessage.warning('统计区间最长支持 366 天，已自动调整为该日期往前 366 天')
  }
  const hit = RANGE_PRESETS.find((p) => p.value === rangePreset.value)
  if (hit) {
    // onPresetChange 会程序化改写 dateRange 并触发一次 change；
    // 与快捷区间完全一致时直接忽略，避免重复请求、也避免被误标成「自定义」。
    const [pf, pt] = buildRange(hit.days)
    if (pf === f && pt === t) return
  }
  dateRange.value = [f, t]
  rangePreset.value = 'CUSTOM'
  load()
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [from, to] = dateRange.value || []
    const [overviewRes, recentRes] = await Promise.all([
      systemApi.dashboard(projectId.value, from, to),
      taskApi.recent(8).catch(() => [])
    ])
    raw.value = overviewRes || {}
    recentTasks.value = arr<any>(recentRes as any)
  } catch {
    raw.value = {}
    recentTasks.value = []
  } finally {
    loading.value = false
  }
}

async function loadProjects(): Promise<void> {
  try {
    projectOptions.value = arr<any>(await projectApi.options())
  } catch {
    projectOptions.value = []
  }
  // 安全网：本地保留的项目上下文若已不在可见项目里（项目被删除/归档、或换人后权限不同），
  // 继续带着它请求会被后端判为「无权访问该项目」并返回 403，因此这里直接回退到「全部项目」。
  if (projectId.value != null
      && !projectOptions.value.some((p: any) => Number(p?.id) === Number(projectId.value))) {
    projectId.value = undefined
    appStore.setCurrentProject(undefined)
  }
}

function onProjectChange(value: any): void {
  const id = typeof value === 'number' && Number.isFinite(value) ? value : undefined
  projectId.value = id
  appStore.setCurrentProject(id)
  load()
}

function openResult(row: any): void {
  if (!row?.id) return
  router.push(`/results/${row.id}`)
}

function openAlertTask(row: any): void {
  if (!row?.taskId) return
  router.push(`/tasks/${row.taskId}`)
}

function openRecentTask(row: any): void {
  const id = row?.id ?? row?.taskId
  if (!id) return
  router.push(`/tasks/${id}`)
}

function onProjectRowClick(row: any): void {
  if (!row?.id) return
  projectId.value = row.id
  appStore.setCurrentProject(row.id)
  load()
}

function goResults(): void {
  router.push('/results')
}

function goTasks(): void {
  router.push('/tasks')
}

onMounted(() => {
  projectId.value = appStore.currentProjectId
  loadProjects()
  load()
})
</script>

<style scoped>
.project-select {
  width: 220px;
}

/* 图表卡头右侧的「快捷区间 + 起止日期」。卡头本身是 flex + space-between，
   窄屏（卡片被压到 430px 左右）时靠 flex-wrap 换到标题下面一行。 */
.chart-range-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.range-preset {
  width: 110px;
}

.range-picker {
  width: 232px;
}

.option-code {
  float: right;
  color: var(--ees-text-muted);
  margin-left: 12px;
}

/* ---------------- 告警卡片 ---------------- */

.alert-card {
  border-left: 4px solid var(--ees-danger);
  background: linear-gradient(135deg, #fff8f8 0%, #ffffff 62%);
}

.alert-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.alert-icon {
  color: var(--ees-danger);
}

.alert-title {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--ees-danger);
}

.alert-table {
  width: 100%;
}

/* ---------------- 表格卡片 ---------------- */

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.card-title {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--ees-text);
}

.dash-link {
  color: var(--ees-primary);
  font-weight: 600;
}

.dash-link:hover {
  text-decoration: underline;
}

.cell-sub {
  display: block;
  font-size: 11.5px;
  color: var(--ees-text-muted);
  line-height: 1.5;
}

/* ---------------- 底部双列 ---------------- */

.dash-two-col {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(430px, 1fr));
  gap: 14px;
}

.dash-two-col .ees-card {
  margin-bottom: 14px;
}

.dash-foot {
  text-align: right;
  padding: 0 2px 4px;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
