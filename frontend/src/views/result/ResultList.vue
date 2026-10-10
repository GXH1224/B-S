<template>
  <div class="ees-page">
    <PageHeader
      title="评估结果"
      desc="按项目、任务、算法、等级与发布状态检索评估结果；数值与等级全部来自后端结果数据集，支持复核、发布与 Excel / Word / PDF 导出"
    >
      <template #actions>
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="ees-card">
      <div class="ees-filter-bar">
        <el-select
          v-model="query.projectId"
          class="filter-select filter-select--wide"
          placeholder="全部项目"
          clearable
          filterable
          @change="onProjectChange"
        >
          <el-option
            v-for="p in projects"
            :key="p.id"
            :label="projectLabel(p)"
            :value="p.id"
          />
        </el-select>

        <el-select
          v-model="query.taskId"
          class="filter-select filter-select--wide"
          placeholder="全部任务"
          clearable
          filterable
        >
          <el-option
            v-for="t in tasks"
            :key="t.id"
            :label="taskLabel(t)"
            :value="t.id"
          />
        </el-select>

        <el-select v-model="query.algorithm" class="filter-select" placeholder="全部算法" clearable>
          <el-option v-for="opt in ALGORITHM_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>

        <el-select v-model="query.level" class="filter-select filter-select--mini" placeholder="全部等级" clearable>
          <el-option v-for="lv in LEVEL_OPTIONS" :key="lv" :label="lv" :value="lv" />
        </el-select>

        <el-select v-model="query.publishStatus" class="filter-select" placeholder="发布状态" clearable>
          <el-option
            v-for="opt in publishOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>

        <el-input
          v-model="query.objectCode"
          class="filter-input"
          placeholder="对象编码关键字"
          clearable
          @keyup.enter="doSearch"
        />

        <el-button type="primary" @click="doSearch">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="doReset">
          <el-icon><RefreshLeft /></el-icon>
          重置
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        border
        stripe
        empty-text="暂无评估结果数据"
        @row-dblclick="goDetail"
      >
        <el-table-column prop="ranking" label="排名" width="82" align="center" sortable>
          <template #default="{ row }">
            <el-tag v-if="row?.ranking" size="small" effect="plain" type="info">第 {{ row.ranking }} 名</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="taskCode" label="任务编号" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.taskCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="projectCode" label="项目编码" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.projectCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="objectCode" label="对象编码" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.objectCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="objectName" label="对象名称" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.objectName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="totalScore" label="综合得分" width="120" align="right" sortable>
          <template #default="{ row }">
            <span class="ees-score">{{ fmtScore(row?.totalScore) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评判等级" width="100" align="center">
          <template #default="{ row }">
            <LevelTag :level="row?.level" />
          </template>
        </el-table-column>
        <el-table-column label="算法" width="130">
          <template #default="{ row }">
            {{ ALGORITHM_LABEL[row?.algorithm] || row?.algorithm || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="算法版本" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.algorithmVersion || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="reviewTagType(row?.reviewed)" effect="plain" size="small">
              {{ REVIEW_LABEL[row?.reviewed] || row?.reviewed || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="publishTagType(row?.publishStatus)" effect="plain" size="small">
              {{ PUBLISH_LABEL[row?.publishStatus] || row?.publishStatus || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span class="ees-mono">{{ fmtDateTime(row?.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <!-- 操作列统一用 .op-cell 包裹：el-dropdown 自带 line-height:1 且为 inline-flex，
                 直接与 el-button 平铺会与按钮不在同一基线上（「导出」会整体下沉）。
                 flex + align-items:center 让所有操作项在同一行居中对齐，与数据接入三个页面写法一致。 -->
            <div class="op-cell">
              <el-button link type="primary" size="small" @click="goDetail(row)">查看分析</el-button>
              <el-button v-if="canReview" link type="primary" size="small" @click="openReview(row)">复核</el-button>
              <el-button v-if="canPublish" link type="primary" size="small" @click="publishRow(row)">发布</el-button>
              <el-dropdown v-if="canExport" trigger="click" @command="onExportCommand(row, $event)">
                <el-button link type="primary" size="small" :loading="exportingId === row?.id">
                  导出<el-icon><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="EXCEL">导出 Excel</el-dropdown-item>
                    <el-dropdown-item command="WORD">导出 Word</el-dropdown-item>
                    <el-dropdown-item command="PDF">导出 PDF</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="list-foot">
        <span class="ees-hint">
          排序作用于当前页数据；结果列表由后端按创建顺序返回，图形与表格数值同源（方案 7.3）。
        </span>
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
    </div>

    <!-- 结果复核 -->
    <el-dialog v-model="reviewVisible" title="结果复核" width="560px" :close-on-click-modal="false">
      <div class="review-target">
        <div class="review-line">
          <span class="review-label">对象</span>
          <span class="ees-mono">{{ reviewRow?.objectCode || '-' }}</span>
          <span class="review-name">{{ reviewRow?.objectName || '' }}</span>
        </div>
        <div class="review-line">
          <span class="review-label">任务</span>
          <span class="ees-mono">{{ reviewRow?.taskCode || '-' }}</span>
        </div>
        <div class="review-line">
          <span class="review-label">当前状态</span>
          <el-tag :type="reviewTagType(reviewRow?.reviewed)" effect="plain" size="small">
            {{ REVIEW_LABEL[reviewRow?.reviewed || 'NONE'] || '未提交' }}
          </el-tag>
        </div>
      </div>

      <el-form label-width="88px" style="margin-top: 12px">
        <el-form-item label="复核动作">
          <el-radio-group v-model="reviewForm.action">
            <el-radio-button value="APPROVE">通过</el-radio-button>
            <el-radio-button value="REJECT">退回</el-radio-button>
            <el-radio-button value="SUBMIT">提交审核</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复核备注">
          <el-input
            v-model="reviewForm.comment"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="填写复核意见；退回时必须填写具体原因"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewSaving" @click="submitReview">提交复核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import LevelTag from '@/components/LevelTag.vue'
import { projectApi, reportApi, resultApi, taskApi } from '@/api'
import { useUserStore } from '@/stores/user'
import {
  ALGORITHM_LABEL,
  ALGORITHM_OPTIONS,
  fmtDateTime,
  fmtScore,
  PUBLISH_LABEL,
  REVIEW_LABEL
} from '@/utils/format'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'
type ExportType = 'EXCEL' | 'WORD' | 'PDF'

interface ResultRow {
  id: number
  taskId?: number
  taskCode?: string
  projectId?: number
  projectCode?: string
  projectName?: string
  objectCode?: string
  objectName?: string
  algorithm?: string
  algorithmVersion?: string
  totalScore?: number
  level?: string
  ranking?: number
  reviewed?: string
  reviewComment?: string
  publishStatus?: string
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
  projectId?: number
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const canReview = computed(() => userStore.has('result:review'))
const canPublish = computed(() => userStore.has('result:publish'))
const canExport = computed(() => userStore.has('result:export'))

const LEVEL_OPTIONS = ['优', '良', '中', '差']
const EXPORT_LABEL: Record<ExportType, string> = { EXCEL: 'Excel', WORD: 'Word', PDF: 'PDF' }

const publishOptions = Object.entries(PUBLISH_LABEL).map(([value, label]) => ({ value, label }))

function reviewTagType(status?: string): TagType {
  if (status === 'APPROVED') return 'success'
  if (status === 'PENDING') return 'warning'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

function publishTagType(status?: string): TagType {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'warning'
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
    const res = await taskApi.list({
      projectId: query.projectId || undefined,
      page: 1,
      size: 100
    })
    tasks.value = Array.isArray(res?.records) ? (res.records as TaskOption[]) : []
  } catch {
    tasks.value = []
  }
}

/** 深链进入时任务可能不在前 100 条中，单独补齐下拉项 */
async function ensureTaskOption() {
  const taskId = query.taskId
  if (!taskId || tasks.value.some((t) => t.id === taskId)) return
  try {
    const detail = await taskApi.detail(taskId)
    const task = detail?.task as TaskOption | undefined
    if (task && task.id) tasks.value = [task, ...tasks.value]
  } catch {
    // 忽略：下拉仅用于展示，不影响按 taskId 查询
  }
}

function onProjectChange() {
  query.taskId = undefined
  loadTasks()
}

/** ---------------- 列表 ---------------- */

const loading = ref(false)
const rows = ref<ResultRow[]>([])
const total = ref(0)
const exportingId = ref<number | null>(null)

const query = reactive({
  projectId: undefined as number | undefined,
  taskId: undefined as number | undefined,
  algorithm: '' as string,
  level: '' as string,
  publishStatus: '' as string,
  objectCode: '' as string,
  page: 1,
  size: 20
})

async function load() {
  loading.value = true
  try {
    const res = await resultApi.list({
      projectId: query.projectId || undefined,
      taskId: query.taskId || undefined,
      algorithm: query.algorithm || undefined,
      level: query.level || undefined,
      publishStatus: query.publishStatus || undefined,
      objectCode: query.objectCode ? query.objectCode.trim() : undefined,
      page: query.page,
      size: query.size
    })
    rows.value = Array.isArray(res?.records) ? (res.records as ResultRow[]) : []
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
  query.taskId = undefined
  query.algorithm = ''
  query.level = ''
  query.publishStatus = ''
  query.objectCode = ''
  query.page = 1
  loadTasks()
  load()
}

function onSizeChange() {
  query.page = 1
  load()
}

function goDetail(row: ResultRow | null | undefined) {
  if (!row?.id) return
  router.push(`/results/${row.id}`)
}

/** ---------------- 复核 ---------------- */

const reviewVisible = ref(false)
const reviewSaving = ref(false)
const reviewRow = ref<ResultRow | null>(null)
const reviewForm = reactive({ action: 'APPROVE' as string, comment: '' })

function openReview(row: ResultRow | null | undefined) {
  if (!row?.id) return
  reviewRow.value = row
  reviewForm.action = row.reviewed === 'PENDING' ? 'APPROVE' : 'SUBMIT'
  reviewForm.comment = row.reviewComment || ''
  reviewVisible.value = true
}

async function submitReview() {
  const row = reviewRow.value
  if (!row?.id) return
  if (reviewForm.action === 'REJECT' && !reviewForm.comment.trim()) {
    ElMessage.warning('退回操作必须填写复核备注，便于评估人员定位问题')
    return
  }
  reviewSaving.value = true
  try {
    await resultApi.review(row.id, reviewForm.action, reviewForm.comment.trim() || undefined)
    ElMessage.success('复核操作完成')
    reviewVisible.value = false
    await load()
  } catch {
    // 统一响应拦截器已提示具体失败原因
  } finally {
    reviewSaving.value = false
  }
}

/** ---------------- 发布 ---------------- */

async function publishRow(row: ResultRow | null | undefined) {
  if (!row?.id) return
  try {
    await ElMessageBox.confirm(
      `发布后「${row.objectCode || '该对象'}」的评估结果将作为正式结果对外提供，确定发布吗？`,
      '发布确认',
      { type: 'warning', confirmButtonText: '确定发布', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await resultApi.publish(row.id)
    ElMessage.success('结果已发布')
    await load()
  } catch {
    // 统一响应拦截器已提示
  }
}

/** ---------------- 导出 ---------------- */

async function onExportCommand(row: ResultRow | null | undefined, command: string) {
  if (command !== 'EXCEL' && command !== 'WORD' && command !== 'PDF') return
  await exportResult(row, command)
}

async function exportResult(row: ResultRow | null | undefined, type: ExportType) {
  if (!row?.id) return
  exportingId.value = row.id
  try {
    const res = await resultApi.exportAs(row.id, type)
    if (!res?.reportId) {
      ElMessage.warning('导出未返回报告编号，请稍后在报告导出页面查看')
      return
    }
    await reportApi.download(res.reportId, res.fileName || `评估结果_${row.objectCode || row.id}`)
    ElMessage.success(`已导出 ${EXPORT_LABEL[type]} 文件：${res.fileName || '报告'}`)
  } catch {
    // 统一响应拦截器已提示
  } finally {
    exportingId.value = null
  }
}

/** ---------------- 初始化（支持任务页深链 ?taskId=） ---------------- */

onMounted(async () => {
  const q = route.query
  const qProject = Number(q.projectId)
  if (q.projectId !== undefined && Number.isFinite(qProject)) query.projectId = qProject
  const qTask = Number(q.taskId)
  if (q.taskId !== undefined && Number.isFinite(qTask)) {
    query.taskId = qTask
    query.page = 1
  }
  if (q.objectCode !== undefined) query.objectCode = String(q.objectCode)

  await loadProjects()
  await loadTasks()
  await ensureTaskOption()
  await load()
})
</script>

<style scoped>
.filter-select {
  width: 150px;
}

.filter-select--wide {
  width: 210px;
}

.filter-select--mini {
  width: 110px;
}

.filter-input {
  width: 190px;
}

/* 操作列：让文字按钮与「导出」下拉在同一行居中对齐。
   不加这段时 el-dropdown（inline-flex + line-height:1）会与 el-button 基线不一致，
   表现为「导出」比前面的按钮低一截。间距用 gap 统一，故把 el-button 的默认左外边距归零。 */
.op-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 6px;
}

.op-cell :deep(.el-button + .el-button) {
  margin-left: 0;
}

.op-cell :deep(.el-dropdown) {
  display: inline-flex;
  align-items: center;
}

.list-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.list-foot .ees-pagination {
  margin-top: 14px;
}

.review-target {
  background: #f7f9fd;
  border: 1px solid var(--ees-border);
  border-radius: var(--ees-radius-sm);
  padding: 10px 14px;
}

.review-line {
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 2;
  font-size: 13px;
}

.review-label {
  width: 62px;
  color: var(--ees-text-secondary);
  flex-shrink: 0;
}

.review-name {
  color: var(--ees-text-secondary);
}
</style>
