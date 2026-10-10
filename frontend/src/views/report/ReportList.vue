<template>
  <div class="ees-page">
    <PageHeader
      title="报告导出"
      desc="导出内容带项目、任务、版本和生成时间；支持 Excel、Word、PDF 三种格式，全部依赖本地库，运行时不访问互联网"
    >
      <template #actions>
        <el-button v-if="canCreate" type="primary" @click="openGenerate">
          <el-icon><Plus /></el-icon>
          生成报告
        </el-button>
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="ees-card">
      <div class="ees-filter-bar">
        <el-select v-model="query.projectId" class="filter-select filter-select--wide" placeholder="全部项目" clearable filterable>
          <el-option v-for="p in projects" :key="p.id" :label="projectLabel(p)" :value="p.id" />
        </el-select>

        <el-select v-model="query.reportType" class="filter-select" placeholder="全部格式" clearable>
          <el-option v-for="opt in REPORT_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>

        <el-select v-model="query.taskId" class="filter-select filter-select--wide" placeholder="全部任务" clearable filterable>
          <el-option v-for="t in tasks" :key="t.id" :label="taskLabel(t)" :value="t.id" />
        </el-select>

        <el-button type="primary" @click="doSearch">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="doReset">
          <el-icon><RefreshLeft /></el-icon>
          重置
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无报告记录">
        <el-table-column prop="reportCode" label="报表编号" width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.reportCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reportName" label="报表名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.reportName || '-' }}</template>
        </el-table-column>
        <el-table-column label="关联任务编号" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ees-mono">{{ taskCodeOf(row?.taskId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="关联结果" width="120">
          <template #default="{ row }">
            <span v-if="row?.resultId" class="ees-mono">结果 #{{ row.resultId }}</span>
            <span v-else>整任务汇总</span>
          </template>
        </el-table-column>
        <el-table-column label="格式" width="96" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row?.reportType)" effect="plain" size="small">
              {{ REPORT_TYPE_LABEL[row?.reportType] || row?.reportType || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件名" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.fileName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="文件大小" width="110" align="right">
          <template #default="{ row }">{{ fmtFileSize(row?.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row?.status)" effect="plain" size="small">
              {{ STATUS_LABEL[row?.status] || row?.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下载次数" width="100" align="center">
          <template #default="{ row }">{{ row?.downloadCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="生成人" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.createdBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="生成时间" width="170">
          <template #default="{ row }">
            <span class="ees-mono">{{ fmtDateTime(row?.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canDownload"
              link
              type="primary"
              size="small"
              :disabled="row?.status === 'FAILED'"
              :loading="downloadingId === row?.id"
              @click="downloadReport(row)"
            >
              下载
            </el-button>
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="ees-pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <!-- 生成报告 -->
    <el-dialog v-model="generateVisible" title="生成报告" width="580px" :close-on-click-modal="false">
      <el-form label-width="92px">
        <el-form-item label="选择任务" required>
          <el-select
            v-model="generateForm.taskId"
            placeholder="请选择评估任务"
            filterable
            style="width: 100%"
            @change="onGenerateTaskChange"
          >
            <el-option v-for="t in tasks" :key="t.id" :label="taskLabel(t)" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="可选结果">
          <el-select
            v-model="generateForm.resultId"
            placeholder="不选则导出该任务全部对象（整任务汇总）"
            clearable
            filterable
            :loading="resultLoading"
            style="width: 100%"
          >
            <el-option v-for="r in resultOptions" :key="r.id" :label="resultLabel(r)" :value="r.id" />
          </el-select>
          <div class="ees-hint">选择单个结果时，报告聚焦该对象；不选择时导出任务下全部对象的汇总报告。</div>
        </el-form-item>
        <el-form-item label="导出格式">
          <el-radio-group v-model="generateForm.type">
            <el-radio-button value="EXCEL">Excel</el-radio-button>
            <el-radio-button value="WORD">Word</el-radio-button>
            <el-radio-button value="PDF">PDF</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div class="ees-hint">
        报告内容包含项目、任务、指标体系版本、算法版本、生成时间与全部指标明细，文件在服务器本地生成后下载。
      </div>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="submitGenerate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 报告详情 -->
    <el-dialog v-model="detailVisible" title="报告详情" width="720px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="2" border size="small">
          <el-descriptions-item label="报表编号">
            <span class="ees-mono">{{ detail.reportCode || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="报表名称">{{ detail.reportName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="报表格式">{{ REPORT_TYPE_LABEL[detail.reportType ?? ''] || detail.reportType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.status)" effect="plain" size="small">
              {{ STATUS_LABEL[detail.status ?? ''] || detail.status || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="关联任务编号">
            <span class="ees-mono">{{ taskCodeOf(detail.taskId) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="关联结果">
            {{ detail.resultId ? `结果 #${detail.resultId}` : '整任务汇总' }}
          </el-descriptions-item>
          <el-descriptions-item label="文件名" :span="2">
            <span class="ees-mono">{{ detail.fileName || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="文件路径" :span="2">
            <span class="ees-mono">{{ detail.filePath || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ fmtFileSize(detail.fileSize) }}</el-descriptions-item>
          <el-descriptions-item label="下载次数">{{ detail.downloadCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="生成人">{{ detail.createdBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ fmtDateTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.errorMsg" label="失败原因" :span="2">
            <span class="error-msg">{{ detail.errorMsg }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <div v-else class="ees-empty">未获取到报告详情</div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="canDownload && detail"
          type="primary"
          :disabled="detail.status === 'FAILED'"
          @click="downloadReport(detail)"
        >
          下载
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { projectApi, reportApi, resultApi, taskApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { fmtDateTime, fmtFileSize } from '@/utils/format'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'
type ExportType = 'EXCEL' | 'WORD' | 'PDF'

interface ReportRow {
  id: number
  reportCode?: string
  reportName?: string
  taskId?: number
  resultId?: number
  projectId?: number
  reportType?: string
  fileName?: string
  filePath?: string
  fileSize?: number
  status?: string
  errorMsg?: string
  downloadCount?: number
  createdBy?: string
  createdAt?: string
}

interface ProjectOption {
  id: number
  code?: string
  name?: string
}

interface TaskOption {
  id: number
  taskCode?: string
  taskName?: string
}

interface ResultOption {
  id: number
  objectCode?: string
  objectName?: string
  totalScore?: number
  level?: string
}

const userStore = useUserStore()
const canCreate = computed(() => userStore.has('report:create'))
const canDownload = computed(() => userStore.has('report:download'))

const REPORT_TYPE_LABEL: Record<string, string> = { EXCEL: 'Excel', WORD: 'Word', PDF: 'PDF', JSON: 'JSON' }
const STATUS_LABEL: Record<string, string> = { GENERATING: '生成中', READY: '可下载', FAILED: '生成失败' }
const REPORT_TYPE_OPTIONS = [
  { value: 'EXCEL', label: 'Excel' },
  { value: 'WORD', label: 'Word' },
  { value: 'PDF', label: 'PDF' },
  { value: 'JSON', label: 'JSON' }
]

function typeTagType(type?: string): TagType {
  if (type === 'EXCEL') return 'success'
  if (type === 'WORD') return 'primary'
  if (type === 'PDF') return 'danger'
  return 'info'
}

function statusTagType(status?: string): TagType {
  if (status === 'READY') return 'success'
  if (status === 'GENERATING') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

/** ---------------- 下拉数据 ---------------- */

const projects = ref<ProjectOption[]>([])
const tasks = ref<TaskOption[]>([])

function projectLabel(p: ProjectOption): string {
  return `${p.code || '未编码'} ${p.name || ''}`.trim()
}

function taskLabel(t: TaskOption): string {
  return `${t.taskCode || '任务'} ${t.taskName || ''}`.trim()
}

function taskCodeOf(taskId?: number | null): string {
  if (!taskId) return '-'
  const hit = tasks.value.find((t) => t.id === taskId)
  return hit?.taskCode || `任务 #${taskId}`
}

function resultLabel(r: ResultOption): string {
  const name = r.objectName ? `（${r.objectName}）` : ''
  const score = r.totalScore === null || r.totalScore === undefined ? '' : ` · ${Number(r.totalScore).toFixed(2)} 分`
  return `${r.objectCode || '结果'}${name}${score}`
}

async function loadProjects() {
  try {
    const list = await projectApi.options()
    projects.value = Array.isArray(list) ? (list as ProjectOption[]) : []
  } catch {
    projects.value = []
  }
}

async function loadTasks() {
  try {
    const res = await taskApi.list({ page: 1, size: 100 })
    tasks.value = Array.isArray(res?.records) ? (res.records as TaskOption[]) : []
  } catch {
    tasks.value = []
  }
}

/** ---------------- 列表 ---------------- */

const loading = ref(false)
const rows = ref<ReportRow[]>([])
const total = ref(0)
const downloadingId = ref<number | null>(null)

const query = reactive({
  projectId: undefined as number | undefined,
  reportType: '' as string,
  taskId: undefined as number | undefined,
  page: 1,
  size: 20
})

async function load() {
  loading.value = true
  try {
    const res = await reportApi.list({
      projectId: query.projectId || undefined,
      reportType: query.reportType || undefined,
      taskId: query.taskId || undefined,
      page: query.page,
      size: query.size
    })
    rows.value = Array.isArray(res?.records) ? (res.records as ReportRow[]) : []
    total.value = Number(res?.total ?? 0)
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function doSearch() {
  query.page = 1
  load()
}

function doReset() {
  query.projectId = undefined
  query.reportType = ''
  query.taskId = undefined
  query.page = 1
  load()
}

function onSizeChange() {
  query.page = 1
  load()
}

/** ---------------- 下载 ---------------- */

async function downloadReport(row: ReportRow | null | undefined) {
  if (!row?.id) return
  downloadingId.value = row.id
  try {
    await reportApi.download(row.id, row.fileName || row.reportCode || '报告')
    ElMessage.success('报告下载已开始')
    await load()
  } catch {
    // 统一响应拦截器已提示
  } finally {
    downloadingId.value = null
  }
}

/** ---------------- 生成报告 ---------------- */

const generateVisible = ref(false)
const generating = ref(false)
const resultLoading = ref(false)
const resultOptions = ref<ResultOption[]>([])

const generateForm = reactive({
  taskId: undefined as number | undefined,
  resultId: undefined as number | undefined,
  type: 'EXCEL' as ExportType
})

function openGenerate() {
  generateForm.taskId = undefined
  generateForm.resultId = undefined
  generateForm.type = 'EXCEL'
  resultOptions.value = []
  generateVisible.value = true
}

async function onGenerateTaskChange(taskId: number | undefined) {
  generateForm.resultId = undefined
  resultOptions.value = []
  if (!taskId) return
  resultLoading.value = true
  try {
    const list = await resultApi.byTask(taskId)
    resultOptions.value = Array.isArray(list) ? (list as ResultOption[]) : []
  } catch {
    resultOptions.value = []
  } finally {
    resultLoading.value = false
  }
}

async function submitGenerate() {
  const taskId = generateForm.taskId
  if (!taskId) {
    ElMessage.warning('请先选择要导出报告的评估任务')
    return
  }
  generating.value = true
  try {
    const res = await reportApi.generate({
      taskId,
      resultId: generateForm.resultId || undefined,
      type: generateForm.type
    })
    generateVisible.value = false
    ElMessage.success('报告生成成功')
    await load()

    if (!res?.reportId) return
    try {
      await ElMessageBox.confirm(
        `报告文件「${res.fileName || res.reportCode || '报告'}」已生成，是否立即下载？`,
        '下载确认',
        { type: 'success', confirmButtonText: '立即下载', cancelButtonText: '稍后下载' }
      )
    } catch {
      return
    }
    await reportApi.download(res.reportId, res.fileName || '报告')
    ElMessage.success('报告下载已开始')
    await load()
  } catch {
    // 统一响应拦截器已提示
  } finally {
    generating.value = false
  }
}

/** ---------------- 详情 ---------------- */

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ReportRow | null>(null)

async function openDetail(row: ReportRow | null | undefined) {
  if (!row?.id) return
  detail.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await reportApi.get(row.id)
    detail.value = res ? (res as ReportRow) : row
  } catch {
    detail.value = row
  } finally {
    detailLoading.value = false
  }
}

/** ---------------- 初始化 ---------------- */

onMounted(async () => {
  await Promise.all([loadProjects(), loadTasks()])
  await load()
})
</script>

<style scoped>
.filter-select {
  width: 150px;
}

.filter-select--wide {
  width: 220px;
}

.error-msg {
  color: var(--ees-danger);
}
</style>
