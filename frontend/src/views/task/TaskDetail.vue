<template>
  <div class="ees-page">
    <PageHeader :title="pageTitle" :desc="pageDesc">
      <template #actions>
        <el-button :icon="'Back'" @click="goList">返回列表</el-button>
        <el-button :icon="'Refresh'" :loading="loading" @click="refreshAll">刷新</el-button>
        <el-button
          v-if="canCancel && isCancelable"
          type="danger"
          plain
          :icon="'CircleClose'"
          @click="cancelTask"
        >取消</el-button>
        <el-button
          v-if="canRetry && isRetryable"
          type="warning"
          plain
          :icon="'RefreshRight'"
          :loading="retrying"
          @click="retryTask"
        >重试</el-button>
        <el-button v-if="hasResults" :icon="'DataAnalysis'" @click="goResults">查看结果</el-button>
        <template v-if="canExport">
          <span class="td-export-label">导出报告</span>
          <el-button :loading="exporting === 'EXCEL'" @click="exportReport('EXCEL')">Excel</el-button>
          <el-button :loading="exporting === 'WORD'" @click="exportReport('WORD')">Word</el-button>
          <el-button :loading="exporting === 'PDF'" @click="exportReport('PDF')">PDF</el-button>
        </template>
      </template>
    </PageHeader>

    <div v-if="invalidId" class="ees-card">
      <div class="ees-empty">任务编号无效，请从任务列表进入任务详情</div>
    </div>

    <template v-else>
      <!-- 指标卡 -->
      <div class="ees-stat-grid">
        <StatCard label="任务状态" :value="statusLabel" :variant="statVariant" :foot="statusFoot" />
        <StatCard label="执行进度" :value="progressPercent" unit="%" :variant="progressVariant" />
        <StatCard label="结果数" :value="fmtNum(resultCount, 0)" variant="primary" foot="条对象评估结果" />
        <StatCard label="耗时" :value="fmtDuration(statusInfo?.durationMs ?? task?.durationMs)" />
        <StatCard label="被评估对象数" :value="fmtNum(task?.objectCount, 0)" />
        <StatCard
          label="算法版本"
          :value="task?.algorithmVersion || '未记录'"
          :foot="algorithmText"
        />
      </div>

      <!-- 失败原因 -->
      <div v-if="isErrorStatus" class="ees-card">
        <el-alert
          type="error"
          show-icon
          :closable="false"
          :title="`任务未成功完成：${statusLabel}${errorCode ? `（${errorCode}）` : ''}`"
          :description="errorMessage"
        />
        <el-collapse v-if="stackTrace" class="td-stack">
          <el-collapse-item name="stack" title="查看技术堆栈（供运维排查）">
            <pre class="ees-pre">{{ stackTrace }}</pre>
          </el-collapse-item>
        </el-collapse>
      </div>

      <!-- 详情标签页 -->
      <div class="ees-card">
        <el-tabs v-model="activeTab" @tab-change="onTabChange">
          <!-- 1. 基本信息 -->
          <el-tab-pane label="基本信息" name="base">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="任务编号">
                <span class="ees-mono">{{ task?.taskCode || '-' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="任务名称">{{ task?.taskName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="项目编码">
                <span class="ees-mono">{{ task?.projectCode || '-' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="项目名称">{{ task?.projectName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="指标体系版本">
                <span class="ees-mono">{{ task?.indicatorVersionCode || '-' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="数据快照">
                <span class="ees-mono">{{ task?.snapshotCode || '-' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="评估算法">
                {{ ALGORITHM_LABEL[String(task?.algorithm)] || task?.algorithm || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="算法版本">
                <span class="ees-mono">{{ task?.algorithmVersion || '未记录' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="提交人">{{ task?.submitBy || '-' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ fmtDateTime(task?.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="入队时间">{{ fmtDateTime(task?.queuedAt) }}</el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ fmtDateTime(task?.startedAt) }}</el-descriptions-item>
              <el-descriptions-item label="结束时间">{{ fmtDateTime(task?.finishedAt) }}</el-descriptions-item>
              <el-descriptions-item label="耗时">
                {{ fmtDuration(statusInfo?.durationMs ?? task?.durationMs) }}
              </el-descriptions-item>
              <el-descriptions-item label="重试来源">
                <el-link v-if="task?.retryOf" type="primary" @click="openTask(task.retryOf)">
                  任务 #{{ task.retryOf }}
                </el-link>
                <span v-else class="ees-hint">非重试任务</span>
              </el-descriptions-item>
              <el-descriptions-item label="重试次数">
                {{ fmtNum(task?.retryCount, 0) }}
              </el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <!-- 2. 算法参数与输入摘要 -->
          <el-tab-pane label="算法参数与输入摘要" name="params">
            <div class="td-block-title">算法参数 parameters</div>
            <pre class="ees-pre">{{ pretty(parameters) }}</pre>
            <div class="td-block-title">输入摘要 inputSummary</div>
            <pre class="ees-pre">{{ pretty(inputSummary) }}</pre>
            <div class="ees-hint">
              输入摘要保存了提交时的项目、快照、指标体系版本与算法版本，用于结果可追溯与可复算
            </div>
          </el-tab-pane>

          <!-- 3. 状态历史 -->
          <el-tab-pane label="状态历史" name="history">
            <el-timeline v-if="history.length">
              <el-timeline-item
                v-for="(h, i) in history"
                :key="h?.id || `h-${i}`"
                :timestamp="fmtDateTime(h?.createdAt)"
                placement="top"
                :type="timelineType(h?.toStatus)"
              >
                <div class="td-timeline-line">
                  <span class="td-from-status">{{ fromStatusLabel(h?.fromStatus) }}</span>
                  <el-icon class="td-arrow"><Right /></el-icon>
                  <el-tag size="small" :type="statusType(h?.toStatus)">
                    {{ statusLabel(h?.toStatus) }}
                  </el-tag>
                </div>
                <div class="ees-hint">变更原因：{{ h?.reason || '未记录' }}</div>
                <div class="ees-hint">操作者：{{ h?.operator || '系统' }}</div>
              </el-timeline-item>
            </el-timeline>
            <div v-else class="ees-empty">暂无状态变更记录</div>
          </el-tab-pane>

          <!-- 4. AHP 一致性检验 -->
          <el-tab-pane label="AHP 一致性检验" name="ahp">
            <el-table v-if="consistency.length" :data="consistency" size="small" border>
              <el-table-column type="expand">
                <template #default="{ row }">
                  <div class="td-expand">
                    <div class="td-expand-title">判断矩阵 matrixData</div>
                    <pre class="ees-pre">{{ pretty(row?.matrixData) }}</pre>
                    <div class="td-expand-title">特征向量 eigenVector</div>
                    <pre class="ees-pre">{{ pretty(row?.eigenVector) }}</pre>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="节点" min-width="190">
                <template #default="{ row }">
                  <div>
                    <span class="ees-mono">{{ row?.nodeCode || '-' }}</span>
                    <span v-if="row?.nodeName" class="td-node-name">{{ row.nodeName }}</span>
                  </div>
                  <div v-if="row?.objectCode" class="ees-hint ees-mono">对象：{{ row.objectCode }}</div>
                </template>
              </el-table-column>
              <el-table-column label="阶数" width="80" align="right">
                <template #default="{ row }">{{ fmtNum(row?.matrixSize, 0) }}</template>
              </el-table-column>
              <el-table-column label="λmax" width="110" align="right">
                <template #default="{ row }">{{ fmtNum(row?.lambdaMax, 6) }}</template>
              </el-table-column>
              <el-table-column label="CI" width="110" align="right">
                <template #default="{ row }">{{ fmtNum(row?.ci, 6) }}</template>
              </el-table-column>
              <el-table-column label="RI" width="110" align="right">
                <template #default="{ row }">{{ fmtNum(row?.ri, 6) }}</template>
              </el-table-column>
              <el-table-column label="CR" width="110" align="right">
                <template #default="{ row }">
                  <span :class="row?.passed ? '' : 'td-danger-text'">{{ fmtNum(row?.cr, 6) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="是否通过" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row?.passed ? 'success' : 'danger'">
                    {{ row?.passed ? '通过' : '未通过' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="告警" min-width="200">
                <template #default="{ row }">
                  <span :class="row?.warning ? 'td-warning-text' : 'ees-hint'">
                    {{ row?.warning || '无' }}
                  </span>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="本任务未执行 AHP 一致性检验" :image-size="90">
              <div class="ees-hint">
                仅 AHP 与 COMBO 算法会执行一致性检验，结果将展示各层级判断矩阵的 λmax、CI、RI 与 CR
              </div>
            </el-empty>
          </el-tab-pane>

          <!-- 5. 结果摘要 -->
          <el-tab-pane label="结果摘要" name="results">
            <el-table v-loading="resultLoading" :data="resultRows" size="small" border max-height="520">
              <el-table-column label="排名" width="70" align="center">
                <template #default="{ row }">{{ fmtNum(row?.ranking, 0) }}</template>
              </el-table-column>
              <el-table-column label="对象编码" min-width="130">
                <template #default="{ row }">
                  <span class="ees-mono">{{ row?.objectCode || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="对象名称" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ row?.objectName || '-' }}</template>
              </el-table-column>
              <el-table-column label="综合得分" width="110" align="right">
                <template #default="{ row }">
                  <span class="ees-score">{{ fmtScore(row?.totalScore) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="评判等级" width="100" align="center">
                <template #default="{ row }">
                  <LevelTag :level="row?.level" />
                </template>
              </el-table-column>
              <el-table-column label="审核状态" width="100" align="center">
                <template #default="{ row }">{{ reviewLabel(row?.reviewed) }}</template>
              </el-table-column>
              <el-table-column label="发布状态" width="100" align="center">
                <template #default="{ row }">{{ publishLabel(row?.publishStatus) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-link type="primary" @click="openResult(row?.id)">结果分析</el-link>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="!resultLoading && !resultRows.length" class="ees-empty">
              该任务暂无评估结果；任务成功执行后结果将在此展示
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import LevelTag from '@/components/LevelTag.vue'
import { reportApi, resultApi, taskApi } from '@/api'
import { useUserStore } from '@/stores/user'
import {
  ALGORITHM_LABEL,
  PUBLISH_LABEL,
  REVIEW_LABEL,
  TASK_STATUS_LABEL,
  TASK_STATUS_TYPE,
  fmtDateTime,
  fmtDuration,
  fmtNum,
  fmtScore,
  parseJson
} from '@/utils/format'

defineOptions({ name: 'TaskDetail' })

/** 轮询间隔与终态（终态不再自动刷新：成功 / 失败 / 已取消 / 已超时 + 后续审核发布状态） */
const POLL_INTERVAL_MS = 2000
const TERMINAL_STATUS = ['SUCCESS', 'FAILED', 'CANCELED', 'TIMEOUT', 'PENDING_REVIEW', 'PUBLISHED', 'ARCHIVED']
const CANCELABLE_STATUS = ['DRAFT', 'VALIDATING', 'QUEUED', 'RUNNING']
const RETRYABLE_STATUS = ['FAILED', 'CANCELED', 'TIMEOUT']
const ERROR_STATUS = ['FAILED', 'CANCELED', 'TIMEOUT']

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const canCancel = computed(() => userStore.has('task:cancel'))
const canRetry = computed(() => userStore.has('task:retry'))
// 该按钮走 POST /api/reports（生成报告），因此按 report:create 判权；
// 原先写成 has(['report:create','report:download']) 是「任一满足」，与真实依赖不符
const canExport = computed(() => userStore.has('report:create'))

/** ---------------- 任务标识 ---------------- */

const taskId = computed(() => {
  const raw = route.params?.id
  const n = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(n) && n > 0 ? n : 0
})
const invalidId = computed(() => taskId.value <= 0)

/** ---------------- 数据状态 ---------------- */

const loading = ref(false)
const task = ref<any | null>(null)
const parameters = ref<any>(null)
const inputSummary = ref<any>(null)
const history = ref<any[]>([])
const consistency = ref<any[]>([])
const statusInfo = ref<any | null>(null)
const resultCount = ref(0)

const activeTab = ref('base')
const resultRows = ref<any[]>([])
const resultLoading = ref(false)
const retrying = ref(false)
const exporting = ref('')

let timer: number | undefined

/** ---------------- 展示派生 ---------------- */

const taskStatus = computed(() => String(statusInfo.value?.status || task.value?.status || ''))

function statusLabel(status: unknown): string {
  const key = String(status ?? '')
  return TASK_STATUS_LABEL[key] || key || '-'
}

function statusType(status: unknown): string {
  return TASK_STATUS_TYPE[String(status ?? '')] || 'info'
}

function fromStatusLabel(status: unknown): string {
  const key = String(status ?? '')
  if (!key) return '（创建）'
  return TASK_STATUS_LABEL[key] || key
}

function timelineType(status: unknown): string {
  const key = String(status ?? '')
  if (key === 'SUCCESS' || key === 'PUBLISHED') return 'success'
  if (key === 'FAILED' || key === 'TIMEOUT') return 'danger'
  if (key === 'CANCELED' || key === 'ARCHIVED') return 'info'
  return 'primary'
}

function reviewLabel(v: unknown): string {
  const key = String(v ?? 'NONE')
  return REVIEW_LABEL[key] || key
}

function publishLabel(v: unknown): string {
  const key = String(v ?? 'DRAFT')
  return PUBLISH_LABEL[key] || key
}

const pageTitle = computed(() => {
  const code = task.value?.taskCode
  const name = task.value?.taskName
  if (!code && !name) return `任务详情 #${taskId.value || '-'}`
  return [code, name].filter(Boolean).join(' · ')
})

const pageDesc = computed(() => {
  const project = task.value?.projectName || task.value?.projectCode
  const algorithm = ALGORITHM_LABEL[String(task.value?.algorithm)] || task.value?.algorithm
  const parts = [
    project ? `项目：${project}` : '',
    algorithm ? `算法：${algorithm}` : '',
    `状态：${statusLabel(taskStatus.value)}`
  ].filter(Boolean)
  return parts.join('；')
})

const algorithmText = computed(() => {
  const label = ALGORITHM_LABEL[String(task.value?.algorithm)] || task.value?.algorithm || '未记录'
  return `算法：${label}`
})

const progressPercent = computed(() => {
  const n = Number(statusInfo.value?.progress ?? task.value?.progress)
  if (!Number.isFinite(n)) return 0
  return Math.max(0, Math.min(100, Math.round(n)))
})

const statVariant = computed<'primary' | 'success' | 'warning' | 'danger' | ''>(() => {
  if (taskStatus.value === 'SUCCESS' || taskStatus.value === 'PUBLISHED') return 'success'
  if (taskStatus.value === 'FAILED' || taskStatus.value === 'TIMEOUT') return 'danger'
  if (taskStatus.value === 'RUNNING') return 'primary'
  if (taskStatus.value === 'QUEUED' || taskStatus.value === 'VALIDATING' || taskStatus.value === 'PENDING_REVIEW') {
    return 'warning'
  }
  return ''
})

const progressVariant = computed<'primary' | 'success' | 'warning' | 'danger' | ''>(() => {
  if (taskStatus.value === 'SUCCESS') return 'success'
  if (taskStatus.value === 'FAILED' || taskStatus.value === 'TIMEOUT') return 'danger'
  return 'primary'
})

const isErrorStatus = computed(() => ERROR_STATUS.includes(taskStatus.value))
const isCancelable = computed(() => CANCELABLE_STATUS.includes(taskStatus.value))
const isRetryable = computed(() => RETRYABLE_STATUS.includes(taskStatus.value))
const isTrackable = computed(
  () => taskId.value > 0 && !!taskStatus.value && !TERMINAL_STATUS.includes(taskStatus.value)
)

const statusFoot = computed(() => {
  if (isTrackable.value) {
    const step = statusInfo.value?.currentStep
    return step ? `当前步骤：${step}（每 2 秒自动刷新）` : '任务执行中，状态每 2 秒自动刷新'
  }
  return '任务已进入终态，停止自动刷新'
})
const errorCode = computed(() => String(statusInfo.value?.errorCode || task.value?.errorCode || ''))
const errorMessage = computed(() => {
  const msg = statusInfo.value?.errorMsg || task.value?.errorMsg
  return msg || '后端未返回具体失败原因，可在「状态历史」中查看变更记录'
})
const stackTrace = computed(() => String(task.value?.stackTrace || ''))

const hasResults = computed(() => Number(resultCount.value) > 0 || taskStatus.value === 'SUCCESS')

/** ---------------- 加载 ---------------- */

async function loadDetail() {
  if (invalidId.value) return
  loading.value = true
  try {
    const data: any = await taskApi.detail(taskId.value)
    task.value = data?.task ?? null
    parameters.value = parseJson<any>(data?.parameters, data?.parameters ?? null)
    inputSummary.value = parseJson<any>(data?.inputSummary, data?.inputSummary ?? null)
    history.value = Array.isArray(data?.history) ? data.history : []
    consistency.value = Array.isArray(data?.consistency) ? data.consistency : []
    resultCount.value = Number(data?.resultCount ?? data?.task?.resultCount ?? 0) || 0
  } catch {
    task.value = null
    history.value = []
    consistency.value = []
  } finally {
    loading.value = false
    syncPolling()
  }
}

async function loadStatus() {
  if (invalidId.value) return
  try {
    const s: any = await taskApi.status(taskId.value)
    if (!s) return
    const changed = String(s.status) !== taskStatus.value
    statusInfo.value = s
    resultCount.value = Number(s.resultCount ?? resultCount.value) || 0
    // 状态发生变化时同步刷新详情（含状态历史与一致性检验记录）
    if (changed) await loadDetail()
  } catch {
    // 轮询失败保留上一次状态
  } finally {
    syncPolling()
  }
}

async function loadResults() {
  if (invalidId.value) return
  resultLoading.value = true
  try {
    const rows = await resultApi.byTask(taskId.value)
    resultRows.value = Array.isArray(rows) ? rows : []
  } catch {
    resultRows.value = []
  } finally {
    resultLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadDetail(), loadStatus()])
  if (activeTab.value === 'results') await loadResults()
}

function syncPolling() {
  if (isTrackable.value) {
    startPolling()
  } else {
    stopPolling()
  }
}

function startPolling() {
  if (timer !== undefined) return
  timer = window.setInterval(() => {
    void loadStatus()
  }, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
}

function onTabChange(name: unknown) {
  if (String(name) === 'results' && !resultRows.value.length) void loadResults()
}

/** ---------------- 内容格式化 ---------------- */

function pretty(value: any): string {
  if (value === null || value === undefined || value === '') return '（无数据）'
  const parsed = parseJson<any>(value, null)
  if (parsed === null) return String(value)
  try {
    return JSON.stringify(parsed, null, 2)
  } catch {
    return String(value)
  }
}

/** ---------------- 操作 ---------------- */

function goList() {
  router.push('/tasks')
}

function goResults() {
  router.push({ path: '/results', query: { taskId: String(taskId.value) } })
}

function openResult(id: unknown) {
  const rid = Number(id)
  if (!Number.isFinite(rid) || rid <= 0) return
  router.push(`/results/${rid}`)
}

function openTask(id: unknown) {
  const tid = Number(id)
  if (!Number.isFinite(tid) || tid <= 0) return
  router.push(`/tasks/${tid}`)
}

async function cancelTask() {
  try {
    await ElMessageBox.confirm(
      `确认取消任务「${task.value?.taskCode || taskId.value}」？取消后已产生的中间计算结果不会保留。`,
      '取消评估任务',
      { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '返回' }
    )
  } catch {
    return
  }
  try {
    await taskApi.cancel(taskId.value)
    ElMessage.success('任务已取消')
    await refreshAll()
  } catch {
    // 请求拦截器已给出中文提示
  }
}

async function retryTask() {
  try {
    await ElMessageBox.confirm(
      '重试将基于当前任务参数生成新的执行实例，是否继续？',
      '重试评估任务',
      { type: 'info', confirmButtonText: '确定重试', cancelButtonText: '返回' }
    )
  } catch {
    return
  }
  retrying.value = true
  try {
    const res: any = await taskApi.retry(taskId.value)
    const newId = Number(res?.taskId)
    ElMessage.success(`已生成新的执行实例：${res?.taskCode || newId || ''}`)
    if (Number.isFinite(newId) && newId > 0) {
      await router.push(`/tasks/${newId}`)
    } else {
      await refreshAll()
    }
  } catch {
    // 请求拦截器已给出中文提示
  } finally {
    retrying.value = false
  }
}

async function exportReport(type: 'EXCEL' | 'WORD' | 'PDF') {
  if (exporting.value) return
  exporting.value = type
  try {
    const res: any = await reportApi.generate({ taskId: taskId.value, type })
    const reportId = Number(res?.reportId)
    if (!Number.isFinite(reportId) || reportId <= 0) {
      ElMessage.warning('报告已生成，但未返回报告编号，请前往「报告导出」查看')
      return
    }
    const fileName = String(res?.fileName || `评估任务报告-${task.value?.taskCode || taskId.value}`)
    await reportApi.download(reportId, fileName)
    ElMessage.success('报告已开始下载')
  } catch {
    // 请求拦截器已给出中文提示
  } finally {
    exporting.value = ''
  }
}

/** ---------------- 生命周期 ---------------- */

watch(
  taskId,
  async (id) => {
    stopPolling()
    task.value = null
    statusInfo.value = null
    parameters.value = null
    inputSummary.value = null
    history.value = []
    consistency.value = []
    resultRows.value = []
    resultCount.value = 0
    activeTab.value = 'base'
    if (id > 0) {
      await loadDetail()
      await loadStatus()
    }
  },
  { immediate: true }
)

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.td-export-label {
  font-size: 13px;
  color: var(--ees-text-secondary);
  margin-left: 4px;
}

.td-stack {
  margin-top: 10px;
}

.td-block-title {
  font-size: 14px;
  font-weight: 600;
  margin: 12px 0 8px;
}

.td-block-title:first-child {
  margin-top: 0;
}

.td-timeline-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.td-from-status {
  font-size: 13px;
  color: var(--ees-text-secondary);
}

.td-arrow {
  color: var(--ees-text-muted);
}

.td-expand {
  padding: 8px 12px 12px;
}

.td-expand-title {
  font-size: 13px;
  font-weight: 600;
  margin: 8px 0 6px;
}

.td-node-name {
  margin-left: 8px;
  color: var(--ees-text-secondary);
}

.td-danger-text {
  color: var(--ees-danger);
  font-weight: 600;
}

.td-warning-text {
  color: var(--ees-warning);
}
</style>
