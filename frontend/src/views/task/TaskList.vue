<template>
  <div class="ees-page">
    <PageHeader
      title="评估任务"
      desc="状态机：草稿 → 校验中 → 已排队 → 运行中 → 成功、失败或已停止 → 待审核 → 已发布 → 已归档；状态变化记录操作者、时间、原因和前后状态"
    >
      <template #actions>
        <el-button :icon="'Refresh'" @click="loadList()">刷新</el-button>
        <el-button v-if="canCreate" type="primary" :icon="'Plus'" @click="goWorkbench">新建评估任务</el-button>
      </template>
    </PageHeader>

    <!-- 筛选区 -->
    <div class="ees-card ees-card--flat">
      <div class="ees-filter-bar">
        <el-select
          v-model="query.projectId"
          filterable
          clearable
          placeholder="全部项目"
          class="tl-filter-select"
          :loading="projectLoading"
          @change="search"
        >
          <el-option v-for="p in projectOptions" :key="p.id" :label="projectLabel(p)" :value="p.id" />
        </el-select>

        <el-select
          v-model="query.algorithm"
          clearable
          placeholder="全部算法"
          class="tl-filter-select"
          @change="search"
        >
          <el-option v-for="a in ALGORITHM_OPTIONS" :key="a.value" :label="a.label" :value="a.value" />
        </el-select>

        <el-select
          v-model="query.status"
          clearable
          placeholder="全部状态"
          class="tl-filter-select"
          @change="search"
        >
          <el-option v-for="(label, code) in TASK_STATUS_LABEL" :key="code" :label="label" :value="code" />
        </el-select>

        <el-input
          v-model="query.keyword"
          clearable
          placeholder="任务编号 / 任务名称"
          class="tl-filter-input"
          @keyup.enter="search"
          @clear="search"
        />

        <el-date-picker
          v-model="query.dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="提交开始时间"
          end-placeholder="提交结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
          class="tl-filter-date"
          @change="search"
        />

        <el-button type="primary" :icon="'Search'" @click="search">查询</el-button>
        <el-button :icon="'RefreshLeft'" @click="resetQuery">重置</el-button>
      </div>

      <div class="ees-hint">
        <span v-if="autoRefreshing" class="tl-auto-hint">
          <el-icon class="is-loading"><Loading /></el-icon>
          当前列表存在运行中的任务，已开启自动刷新（每 5 秒）
        </span>
        <span v-else>共 {{ total }} 条任务记录；存在「已排队 / 运行中」任务时列表将自动刷新</span>
      </div>
    </div>

    <!-- 列表 -->
    <div class="ees-card">
      <el-table v-loading="loading" :data="rows" size="small" border row-key="id">
        <el-table-column label="任务编号" min-width="170" fixed="left">
          <template #default="{ row }">
            <el-link type="primary" class="ees-mono" @click="goDetail(row)">
              {{ row.taskCode || '-' }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="taskName" label="任务名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taskName || '-' }}</template>
        </el-table-column>
        <el-table-column label="项目" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.projectName || '-' }}</div>
            <div class="ees-hint ees-mono">{{ row.projectCode || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="算法" min-width="150">
          <template #default="{ row }">
            <div>{{ ALGORITHM_LABEL[row.algorithm] || row.algorithm || '-' }}</div>
            <div class="ees-hint ees-mono">{{ row.algorithmVersion || '版本未记录' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="140">
          <template #default="{ row }">
            <el-progress
              v-if="isActiveStatus(row.status)"
              :percentage="progressOf(row)"
              :stroke-width="10"
              :color="row.status === 'RUNNING' ? 'var(--ees-primary)' : 'var(--ees-warning)'"
            />
            <span v-else class="ees-hint">-</span>
          </template>
        </el-table-column>
        <el-table-column label="结果数" width="80" align="right">
          <template #default="{ row }">{{ fmtNum(row.resultCount, 0) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="110" align="right">
          <template #default="{ row }">{{ fmtDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="提交人" width="110">
          <template #default="{ row }">{{ row.submitBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goDetail(row)">查看详情</el-button>
            <el-button
              link
              type="primary"
              size="small"
              :disabled="!row.resultCount"
              @click="goResults(row)"
            >查看结果</el-button>
            <el-button
              v-if="canCancel && isCancelableStatus(row.status)"
              link
              type="danger"
              size="small"
              @click="cancelTask(row)"
            >取消</el-button>
            <el-button
              v-if="canRetry && isRetryableStatus(row.status)"
              link
              type="warning"
              size="small"
              @click="retryTask(row)"
            >重试</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!loading && !rows.length" class="ees-empty">
        没有符合条件的评估任务，可调整筛选条件或点击「新建评估任务」
      </div>

      <div class="ees-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="onSizeChange"
          @current-change="loadList()"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { projectApi, taskApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import {
  ALGORITHM_LABEL,
  ALGORITHM_OPTIONS,
  TASK_STATUS_LABEL,
  TASK_STATUS_TYPE,
  fmtDateTime,
  fmtDuration,
  fmtNum
} from '@/utils/format'

defineOptions({ name: 'TaskList' })

/** 自动刷新间隔与触发状态 */
const AUTO_REFRESH_MS = 5000
const ACTIVE_STATUS = ['QUEUED', 'RUNNING']
const CANCELABLE_STATUS = ['DRAFT', 'VALIDATING', 'QUEUED', 'RUNNING']
const RETRYABLE_STATUS = ['FAILED', 'CANCELED', 'TIMEOUT']

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const canCreate = computed(() => userStore.has('task:create'))
const canCancel = computed(() => userStore.has('task:cancel'))
const canRetry = computed(() => userStore.has('task:retry'))

/** ---------------- 查询条件 ---------------- */

const query = reactive<{
  projectId: number | undefined
  algorithm: string | undefined
  status: string | undefined
  keyword: string
  dateRange: [string, string] | null
}>({
  projectId: appStore.currentProjectId,
  algorithm: undefined,
  status: undefined,
  keyword: '',
  dateRange: null
})

const page = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref<any[]>([])
const loading = ref(false)
const projectLoading = ref(false)
const projectOptions = ref<any[]>([])

function projectLabel(p: any): string {
  const name = p?.name || p?.projectName || '未命名项目'
  const code = p?.code || p?.projectCode
  return code ? `${name}（${code}）` : name
}

function statusLabel(status: unknown): string {
  const key = String(status ?? '')
  return TASK_STATUS_LABEL[key] || key || '-'
}

function statusType(status: unknown): string {
  return TASK_STATUS_TYPE[String(status ?? '')] || 'info'
}

function isActiveStatus(status: unknown): boolean {
  return ACTIVE_STATUS.includes(String(status ?? ''))
}

function isCancelableStatus(status: unknown): boolean {
  return CANCELABLE_STATUS.includes(String(status ?? ''))
}

function isRetryableStatus(status: unknown): boolean {
  return RETRYABLE_STATUS.includes(String(status ?? ''))
}

function progressOf(row: any): number {
  const n = Number(row?.progress)
  if (!Number.isFinite(n)) return 0
  return Math.max(0, Math.min(100, Math.round(n)))
}

/** 日期区间统一转为 ISO 日期时间，匹配后端 LocalDateTime 入参 */
function toIsoTime(value?: string | null): string | undefined {
  if (!value) return undefined
  return value.includes(' ') ? value.replace(' ', 'T') : value
}

async function loadProjects() {
  projectLoading.value = true
  try {
    const list = await projectApi.options()
    projectOptions.value = Array.isArray(list) ? list : []
  } catch {
    projectOptions.value = []
  } finally {
    projectLoading.value = false
  }
}

async function loadList(silent = false) {
  if (!silent) loading.value = true
  try {
    const params: Record<string, any> = {
      page: page.value,
      size: size.value
    }
    if (query.projectId) params.projectId = query.projectId
    if (query.algorithm) params.algorithm = query.algorithm
    if (query.status) params.status = query.status
    if (query.keyword && query.keyword.trim()) params.keyword = query.keyword.trim()
    const from = toIsoTime(query.dateRange?.[0])
    const to = toIsoTime(query.dateRange?.[1])
    if (from) params.from = from
    if (to) params.to = to

    const result: any = await taskApi.list(params)
    rows.value = Array.isArray(result?.records) ? result.records : []
    total.value = Number(result?.total) || 0
  } catch {
    if (!silent) {
      rows.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  return loadList()
}

function onSizeChange() {
  page.value = 1
  return loadList()
}

function resetQuery() {
  query.projectId = undefined
  query.algorithm = undefined
  query.status = undefined
  query.keyword = ''
  query.dateRange = null
  page.value = 1
  return loadList()
}

/** ---------------- 自动刷新 ---------------- */

const hasActiveTask = computed(() => rows.value.some((r) => isActiveStatus(r?.status)))
const autoRefreshing = ref(false)
let timer: number | undefined

function startAutoRefresh() {
  stopAutoRefresh()
  timer = window.setInterval(() => {
    void loadList(true)
  }, AUTO_REFRESH_MS)
  autoRefreshing.value = true
}

function stopAutoRefresh() {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
  autoRefreshing.value = false
}

watch(hasActiveTask, (active) => {
  if (active) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
})

onUnmounted(() => {
  stopAutoRefresh()
})

/** ---------------- 行操作 ---------------- */

function goWorkbench() {
  router.push('/workbench')
}

function goDetail(row: any) {
  if (!row?.id) return
  router.push(`/tasks/${row.id}`)
}

function goResults(row: any) {
  if (!row?.id) return
  router.push({ path: '/results', query: { taskId: String(row.id) } })
}

async function cancelTask(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认取消任务「${row?.taskName || row?.taskCode || row?.id}」？取消后已产生的中间计算结果不会保留。`,
      '取消评估任务',
      { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '返回' }
    )
  } catch {
    return
  }
  try {
    await taskApi.cancel(Number(row.id))
    ElMessage.success('任务已取消')
    await loadList()
  } catch {
    // 请求拦截器已给出中文提示
  }
}

async function retryTask(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认基于任务「${row?.taskCode || row?.id}」重新执行？重试将生成一个新的任务实例。`,
      '重试评估任务',
      { type: 'info', confirmButtonText: '确定重试', cancelButtonText: '返回' }
    )
  } catch {
    return
  }
  try {
    const res: any = await taskApi.retry(Number(row.id))
    ElMessage.success(`已生成新的执行实例：${res?.taskCode || res?.taskId || ''}`)
    await loadList()
  } catch {
    // 请求拦截器已给出中文提示
  }
}

loadProjects()
loadList()
</script>

<style scoped>
.tl-filter-select {
  width: 190px;
}

.tl-filter-input {
  width: 240px;
}

.tl-filter-date {
  width: 400px;
}

.tl-auto-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ees-primary);
}
</style>
