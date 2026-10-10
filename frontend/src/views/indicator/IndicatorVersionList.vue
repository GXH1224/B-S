<template>
  <div class="ees-page">
    <PageHeader
      title="指标体系"
      desc="系统只负责指标体系的导入、校验、版本化和使用，不负责重新设计指标体系；导入后的原始版本只读保存"
    >
      <template #actions>
        <el-button :loading="templateLoading" @click="downloadTemplate">
          <el-icon><Download /></el-icon>
          下载导入模板
        </el-button>
        <el-button v-if="canImport" type="primary" @click="openImport">
          <el-icon><Upload /></el-icon>
          导入指标体系
        </el-button>
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="ees-card">
      <div class="ees-filter-bar">
        <el-input
          v-model="query.keyword"
          class="filter-input"
          placeholder="版本编号 / 版本名称 / 来源文件"
          clearable
          @keyup.enter="doSearch"
        />
        <el-select v-model="query.status" class="filter-select" placeholder="版本状态" clearable>
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="query.size" class="filter-size" placeholder="每页条数" @change="doSearch">
          <el-option v-for="n in PAGE_SIZES" :key="n" :label="`每页 ${n} 条`" :value="n" />
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

      <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无指标体系版本，请先导入">
        <el-table-column prop="versionCode" label="版本编号" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.versionCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="versionName" label="版本名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.versionName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sourceFile" label="来源文件" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.sourceFile || '-' }}</template>
        </el-table-column>
        <el-table-column label="指标数" width="90" align="right">
          <template #default="{ row }">{{ row?.indicatorCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="叶子数" width="90" align="right">
          <template #default="{ row }">{{ row?.leafCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="最大层级" width="96" align="center">
          <template #default="{ row }">{{ row?.maxLevel ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="versionStatusType(row?.status)" effect="plain" size="small">
              {{ VERSION_STATUS_LABEL[row?.status] || row?.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="导入人" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.createdBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="导入时间" width="170">
          <template #default="{ row }">
            <span class="ees-mono">{{ fmtDateTime(row?.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看详情</el-button>
            <el-button link type="primary" size="small" @click="openValidateResult(row)">校验结果</el-button>
            <el-button v-if="canImport" link type="primary" size="small" @click="revalidate(row)">重新校验</el-button>
            <el-button v-if="canImport" link type="primary" size="small" @click="publish(row)">发布</el-button>
            <el-button v-if="canImport" link type="warning" size="small" @click="archive(row)">归档</el-button>
            <el-button v-if="canImport" link type="danger" size="small" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="ees-pagination">
        <el-pagination
          v-model:current-page="query.page"
          :page-size="query.size"
          :total="total"
          background
          layout="total, prev, pager, next, jumper"
          @current-change="load"
        />
      </div>
    </div>

    <!-- 导入指标体系 -->
    <el-dialog
      v-model="importVisible"
      title="导入指标体系"
      width="860px"
      :close-on-click-modal="false"
      @closed="onImportClosed"
    >
      <template v-if="!importResult">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="导入即校验"
          description="支持 .xlsx / .xls / .csv；系统按模板列解析指标的编码、名称、层级、方向、权重、阈值与评分规则，并保留原始文件与校验结果。导入后的原始版本只读保存。"
          style="margin-bottom: 14px"
        />

        <el-form ref="importFormRef" :model="importForm" label-width="92px">
          <el-form-item label="指标体系文件" required>
            <el-upload
              ref="uploadRef"
              v-model:file-list="fileList"
              class="upload-block"
              drag
              :auto-upload="false"
              :limit="1"
              accept=".xlsx,.xls,.csv"
              :on-change="onFileChange"
              :on-exceed="onFileExceed"
              :on-remove="onFileRemove"
            >
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-text">将文件拖到此处，或<em>点击选择文件</em></div>
              <template #tip>
                <div class="ees-hint">仅支持 .xlsx / .xls / .csv，单次只能上传 1 个文件；文件不会被上传到外网。</div>
              </template>
            </el-upload>
          </el-form-item>
          <el-form-item label="关联项目" prop="projectId">
            <el-select v-model="importForm.projectId" placeholder="可不选，留空表示公共指标体系" clearable filterable style="width: 100%">
              <el-option v-for="p in projectOptions" :key="p.id" :label="projectLabel(p)" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="版本编号" prop="versionCode">
            <el-input v-model="importForm.versionCode" placeholder="可留空" clearable />
            <div class="ees-hint">留空则系统自动生成，格式为 IND-yyyy-###</div>
          </el-form-item>
          <el-form-item label="版本名称" prop="versionName">
            <el-input v-model="importForm.versionName" placeholder="如：2026 版装备效能指标体系" clearable />
          </el-form-item>
          <el-form-item label="版本说明" prop="description">
            <el-input v-model="importForm.description" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="版本来源、适用范围等说明" />
          </el-form-item>
        </el-form>
      </template>

      <template v-else>
        <el-alert
          :type="importResult.success ? 'success' : 'error'"
          :closable="false"
          show-icon
          :title="importResult.success ? '导入成功，指标体系版本已创建' : '导入完成，但存在错误行'"
          :description="
            importResult.success
              ? `版本编号：${importResult.versionCode || '-'}；版本状态：${
                  VERSION_STATUS_LABEL[importResult.status as string] || importResult.status || '-'
                }`
              : importResult.versionId
                ? '部分数据行未通过校验，已创建草稿版本，请修正后重新校验或重新导入。'
                : '所有数据行均未通过校验，未创建版本，请修正文件后重新导入。'
          "
          style="margin-bottom: 14px"
        />

        <div class="ees-stat-grid">
          <StatCard label="总行数" :value="importResult.totalRows ?? 0" icon="Files" />
          <StatCard label="成功行数" :value="importResult.successRows ?? 0" icon="CircleCheck" variant="success" />
          <StatCard label="错误行数" :value="importResult.errorRows ?? 0" icon="WarningFilled" :variant="(importResult.errorRows ?? 0) > 0 ? 'danger' : ''" />
          <StatCard label="指标数" :value="importResult.indicatorCount ?? 0" icon="Grid" variant="primary" />
          <StatCard label="叶子数" :value="importResult.leafCount ?? 0" icon="Document" />
          <StatCard label="最大层级" :value="importResult.maxLevel ?? 0" icon="Sort" variant="warning" />
        </div>

        <div class="issue-title">错误明细（{{ importErrors.length }}）</div>
        <el-table :data="importErrors" border size="small" empty-text="没有错误行">
          <el-table-column prop="row" label="行号" width="80" align="center">
            <template #default="{ row }">{{ row?.row ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="field" label="字段" width="160">
            <template #default="{ row }">
              <span class="ees-mono">{{ row?.field || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="320" show-overflow-tooltip>
            <template #default="{ row }">{{ row?.message || '-' }}</template>
          </el-table-column>
          <el-table-column prop="severity" label="级别" width="90" align="center">
            <template #default="{ row }">
              <el-tag type="danger" effect="plain" size="small">{{ severityLabel(row?.severity) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="issue-title">警告明细（{{ importWarnings.length }}）</div>
        <el-table :data="importWarnings" border size="small" empty-text="没有警告">
          <el-table-column prop="row" label="行号" width="80" align="center">
            <template #default="{ row }">{{ row?.row ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="field" label="字段" width="160">
            <template #default="{ row }">
              <span class="ees-mono">{{ row?.field || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="320" show-overflow-tooltip>
            <template #default="{ row }">{{ row?.message || '-' }}</template>
          </el-table-column>
          <el-table-column prop="severity" label="级别" width="90" align="center">
            <template #default="{ row }">
              <el-tag type="warning" effect="plain" size="small">{{ severityLabel(row?.severity) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template #footer>
        <template v-if="!importResult">
          <el-button @click="importVisible = false">取消</el-button>
          <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
        </template>
        <template v-else>
          <el-button @click="resetImport">继续导入</el-button>
          <el-button v-if="importResult.versionId" type="primary" @click="openDetail(importResult)">查看版本详情</el-button>
          <el-button v-else @click="importVisible = false">关闭</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 校验结果 -->
    <el-dialog v-model="validateVisible" :title="validateTitle" width="760px">
      <template v-if="validateData">
        <div class="ees-stat-grid">
          <StatCard label="总行数" :value="validateData?.totalRows ?? 0" icon="Files" />
          <StatCard label="校验通过" :value="validateData?.successRows ?? 0" icon="CircleCheck" variant="success" />
          <StatCard
            label="错误数"
            :value="validateData?.errors.length ?? 0"
            icon="WarningFilled"
            :variant="(validateData?.errors.length ?? 0) > 0 ? 'danger' : ''"
          />
          <StatCard
            label="警告数"
            :value="validateData?.warnings.length ?? 0"
            icon="InfoFilled"
            variant="warning"
          />
        </div>
        <div class="ees-hint" style="margin-bottom: 12px">校验时间：{{ fmtDateTime(validateData?.validatedAt) }}</div>

        <div class="issue-title">错误明细（{{ validateData?.errors.length ?? 0 }}）</div>
        <el-table :data="validateData?.errors ?? []" border size="small" max-height="240" empty-text="没有错误">
          <el-table-column prop="row" label="行号" width="80" align="center">
            <template #default="{ row }">{{ row?.row ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="field" label="字段" width="150">
            <template #default="{ row }">
              <span class="ees-mono">{{ row?.field || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="300" show-overflow-tooltip>
            <template #default="{ row }">{{ row?.message || '-' }}</template>
          </el-table-column>
        </el-table>

        <div class="issue-title">警告明细（{{ validateData?.warnings.length ?? 0 }}）</div>
        <el-table :data="validateData?.warnings ?? []" border size="small" max-height="240" empty-text="没有警告">
          <el-table-column prop="row" label="行号" width="80" align="center">
            <template #default="{ row }">{{ row?.row ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="field" label="字段" width="150">
            <template #default="{ row }">
              <span class="ees-mono">{{ row?.field || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="300" show-overflow-tooltip>
            <template #default="{ row }">{{ row?.message || '-' }}</template>
          </el-table-column>
        </el-table>
      </template>
      <div v-else class="ees-empty">该版本暂无校验结果</div>
      <template #footer>
        <el-button @click="validateVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type UploadFile,
  type UploadInstance,
  type UploadUserFile
} from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { indicatorApi, projectApi } from '@/api'
import { useUserStore } from '@/stores/user'
import {
  fmtDateTime,
  parseJson,
  VERSION_STATUS_LABEL,
  VERSION_STATUS_TYPE
} from '@/utils/format'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

interface VersionRow {
  id: number
  versionCode?: string
  versionName?: string
  sourceFile?: string
  indicatorCount?: number
  leafCount?: number
  maxLevel?: number
  status?: string
  createdBy?: string
  createdAt?: string
  validateResult?: string
}

interface ValidateIssue {
  row?: number
  field?: string
  message?: string
  severity?: string
}

interface ValidateData {
  totalRows: number
  successRows: number
  errors: ValidateIssue[]
  warnings: ValidateIssue[]
  validatedAt?: string
}

interface ImportResult {
  success?: boolean
  versionId?: number | null
  versionCode?: string | null
  versionName?: string | null
  status?: string
  totalRows?: number
  successRows?: number
  errorRows?: number
  indicatorCount?: number
  leafCount?: number
  maxLevel?: number
  errors?: ValidateIssue[]
  warnings?: ValidateIssue[]
}

interface ProjectOption {
  id: number
  code?: string
  name?: string
  status?: string
}

const router = useRouter()
const userStore = useUserStore()

const canImport = computed(() => userStore.has('indicator:import'))
const statusOptions = Object.entries(VERSION_STATUS_LABEL).map(([value, label]) => ({ value, label }))
const PAGE_SIZES = [10, 20, 50, 100]

function versionStatusType(status?: string): TagType {
  const t = status ? VERSION_STATUS_TYPE[status] : ''
  if (t === 'primary' || t === 'success' || t === 'info' || t === 'warning' || t === 'danger') return t
  return 'info'
}

function severityLabel(severity?: string): string {
  return severity === 'WARN' || severity === 'WARNING' ? '警告' : '错误'
}

/** ---------------- 列表 ---------------- */

const loading = ref(false)
const rows = ref<VersionRow[]>([])
const total = ref(0)
const query = reactive({ keyword: '', status: '', page: 1, size: 20 })

async function load() {
  loading.value = true
  try {
    const res = await indicatorApi.list({
      keyword: query.keyword ? query.keyword.trim() : undefined,
      status: query.status || undefined,
      page: query.page,
      size: query.size
    })
    rows.value = Array.isArray(res?.records) ? (res.records as VersionRow[]) : []
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
  query.keyword = ''
  query.status = ''
  query.page = 1
  query.size = 20
  load()
}

/** ---------------- 模板下载 ---------------- */

const templateLoading = ref(false)

async function downloadTemplate() {
  templateLoading.value = true
  try {
    await indicatorApi.downloadTemplate()
    ElMessage.success('模板下载已开始')
  } catch {
    // 统一响应拦截器已提示
  } finally {
    templateLoading.value = false
  }
}

/** ---------------- 打开详情 ---------------- */

function openDetail(row: any) {
  const id = row ? Number(row.id ?? row.versionId ?? 0) : 0
  if (!id) {
    ElMessage.warning('该版本未创建，无法查看详情')
    return
  }
  router.push(`/indicators/${id}`)
}

/** ---------------- 导入 ---------------- */

const importVisible = ref(false)
const importing = ref(false)
const importFormRef = ref<FormInstance>()
const uploadRef = ref<UploadInstance>()
const fileList = ref<UploadUserFile[]>([])
const projectOptions = ref<ProjectOption[]>([])
const importResult = ref<ImportResult | null>(null)
const importForm = reactive({ projectId: null as number | null, versionCode: '', versionName: '', description: '' })

const importErrors = computed<ValidateIssue[]>(() =>
  Array.isArray(importResult.value?.errors) ? (importResult.value?.errors as ValidateIssue[]) : []
)
const importWarnings = computed<ValidateIssue[]>(() =>
  Array.isArray(importResult.value?.warnings) ? (importResult.value?.warnings as ValidateIssue[]) : []
)

function projectLabel(p: ProjectOption | undefined): string {
  if (!p) return '-'
  const name = p.name || `项目#${p.id}`
  return p.code ? `${name}（${p.code}）` : name
}

async function loadProjectOptions() {
  try {
    const list = await projectApi.options()
    projectOptions.value = Array.isArray(list) ? (list as ProjectOption[]) : []
  } catch {
    projectOptions.value = []
  }
}

function isAcceptedFile(name: string | undefined): boolean {
  if (!name) return false
  const lower = name.toLowerCase()
  return lower.endsWith('.xlsx') || lower.endsWith('.xls') || lower.endsWith('.csv')
}

function onFileChange(file: UploadFile) {
  if (!isAcceptedFile(file?.name)) {
    ElMessage.warning('仅支持 .xlsx、.xls、.csv 格式的指标体系文件')
    fileList.value = []
    return
  }
  fileList.value = file ? [file as UploadUserFile] : []
}

function onFileExceed() {
  ElMessage.warning('单次只能导入 1 个文件，请先移除已选择的文件')
}

function onFileRemove() {
  fileList.value = []
}

function openImport() {
  importResult.value = null
  fileList.value = []
  importForm.projectId = null
  importForm.versionCode = ''
  importForm.versionName = ''
  importForm.description = ''
  importVisible.value = true
  loadProjectOptions()
}

function resetImport() {
  importResult.value = null
  fileList.value = []
  uploadRef.value?.clearFiles()
  importForm.versionCode = ''
  importForm.versionName = ''
  importForm.description = ''
}

function onImportClosed() {
  uploadRef.value?.clearFiles()
  fileList.value = []
  importResult.value = null
  importFormRef.value?.clearValidate()
  load()
}

async function submitImport() {
  const raw = fileList.value[0]?.raw
  if (!raw) {
    ElMessage.warning('请选择要导入的指标体系文件')
    return
  }
  const data = new FormData()
  data.append('file', raw)
  if (importForm.projectId !== null && importForm.projectId !== undefined) {
    data.append('projectId', String(importForm.projectId))
  }
  if (importForm.versionCode.trim()) {
    data.append('versionCode', importForm.versionCode.trim())
  }
  if (importForm.versionName.trim()) {
    data.append('versionName', importForm.versionName.trim())
  }
  if (importForm.description.trim()) {
    data.append('description', importForm.description.trim())
  }

  importing.value = true
  try {
    const res = await indicatorApi.import(data)
    importResult.value = (res || { success: false }) as ImportResult
    if (importResult.value.success) {
      ElMessage.success('导入成功')
    } else {
      ElMessage.warning('导入完成，存在未通过校验的数据行')
    }
    load()
  } catch {
    // 统一响应拦截器已提示（如文件解析失败、版本编号重复）
  } finally {
    importing.value = false
  }
}

/** ---------------- 校验结果 ---------------- */

const validateVisible = ref(false)
const validateTitle = ref('校验结果')
const validateData = ref<ValidateData | null>(null)

function parseValidate(raw: any): ValidateData | null {
  const vr = parseJson<any>(raw, null)
  if (!vr || typeof vr !== 'object' || Array.isArray(vr)) return null
  return {
    totalRows: Number(vr.totalRows ?? 0),
    successRows: Number(vr.successRows ?? 0),
    errors: Array.isArray(vr.errors) ? (vr.errors as ValidateIssue[]) : [],
    warnings: Array.isArray(vr.warnings) ? (vr.warnings as ValidateIssue[]) : [],
    validatedAt: vr.validatedAt || vr.importedAt || ''
  }
}

function openValidateResult(row: VersionRow | null) {
  const data = parseValidate(row?.validateResult)
  if (!data) {
    ElMessage.warning('该版本暂无校验结果，请先执行「重新校验」')
    return
  }
  validateData.value = data
  validateTitle.value = `校验结果 - ${row?.versionCode || ''}`
  validateVisible.value = true
}

/** ---------------- 重新校验 / 发布 / 归档 / 删除 ---------------- */

async function revalidate(row: VersionRow | null) {
  if (!row) return
  try {
    const res = await indicatorApi.revalidate(row.id)
    ElMessage.success('校验完成')
    await load()
    const data = parseValidate(res)
    if (data) {
      validateData.value = data
      validateTitle.value = `校验结果 - ${row.versionCode || ''}`
      validateVisible.value = true
    }
  } catch {
    // 统一响应拦截器已提示
  }
}

async function publish(row: VersionRow | null) {
  if (!row) return
  try {
    await ElMessageBox.confirm(
      `发布后版本「${row.versionCode || ''}」将可用于创建评估任务，原始指标数据保持只读。确定发布吗？`,
      '发布确认',
      { type: 'warning', confirmButtonText: '确定发布', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await indicatorApi.publish(row.id)
    ElMessage.success('版本已发布')
    await load()
  } catch {
    // 统一响应拦截器已提示
  }
}

async function archive(row: VersionRow | null) {
  if (!row) return
  try {
    await ElMessageBox.confirm(`确定归档版本「${row.versionCode || ''}」吗？归档后不再参与新的评估任务。`, '归档确认', {
      type: 'warning',
      confirmButtonText: '确定归档',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await indicatorApi.archive(row.id)
    ElMessage.success('版本已归档')
    await load()
  } catch {
    // 统一响应拦截器已提示
  }
}

async function confirmDelete(row: VersionRow | null) {
  if (!row) return
  if (row.status === 'PUBLISHED') {
    ElMessageBox.alert('已发布的指标体系版本不允许删除，请先归档', '无法删除', {
      type: 'warning',
      confirmButtonText: '知道了'
    }).catch(() => undefined)
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定删除版本「${row.versionCode || ''}」吗？该版本下的指标数据将一并删除，且不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await indicatorApi.remove(row.id)
    ElMessage.success('版本已删除')
    if (rows.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    await load()
  } catch (e) {
    const backendMsg = e instanceof Error ? e.message : ''
    ElMessageBox.alert(backendMsg || '删除失败，已发布的版本不允许删除，请先归档', '无法删除', {
      type: 'warning',
      confirmButtonText: '知道了'
    }).catch(() => undefined)
  }
}

onMounted(() => {
  load()
  loadProjectOptions()
})
</script>

<style scoped>
.filter-input {
  width: 260px;
}

.filter-select {
  width: 160px;
}

.filter-size {
  width: 140px;
}

.upload-block {
  width: 100%;
}

.upload-icon {
  font-size: 42px;
  color: var(--ees-primary-light);
  margin-bottom: 6px;
}

.upload-text {
  font-size: 13px;
  color: var(--ees-text-secondary);
}

.upload-text em {
  color: var(--ees-primary);
  font-style: normal;
}

.issue-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ees-text);
  margin: 14px 0 8px;
}
</style>
