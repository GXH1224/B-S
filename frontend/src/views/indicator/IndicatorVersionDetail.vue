<template>
  <div class="ees-page">
    <PageHeader :title="headTitle" :desc="headDesc">
      <template #actions>
        <el-button @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回列表
        </el-button>
        <el-button v-if="canImport" :loading="revalidating" @click="revalidate">
          <el-icon><RefreshRight /></el-icon>
          重新校验
        </el-button>
        <el-button v-if="canImport" type="primary" @click="publish">
          <el-icon><Promotion /></el-icon>
          发布
        </el-button>
        <el-button :disabled="indicators.length === 0" @click="exportIndicators">
          <el-icon><Download /></el-icon>
          导出指标
        </el-button>
        <el-button :loading="loading" @click="loadAll">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="ees-card">
      <el-descriptions v-loading="loading" :column="3" border size="small">
        <el-descriptions-item label="版本编号">
          <span class="ees-mono">{{ version?.versionCode || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="版本名称">{{ version?.versionName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本状态">
          <el-tag :type="versionStatusType(version?.status)" effect="plain" size="small">
            {{ VERSION_STATUS_LABEL[version?.status as string] || version?.status || '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源文件">{{ version?.sourceFile || '-' }}</el-descriptions-item>
        <el-descriptions-item label="导入人">{{ version?.createdBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="导入时间">
          <span class="ees-mono">{{ fmtDateTime(version?.createdAt) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="关联项目">
          {{ projectName || (version?.projectId ? `项目#${version.projectId}` : '公共指标体系（未关联项目）') }}
        </el-descriptions-item>
        <el-descriptions-item label="发布人">{{ version?.publishedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">
          <span class="ees-mono">{{ fmtDateTime(version?.publishedAt) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="版本说明" :span="3">{{ version?.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="ees-stat-grid">
      <StatCard label="指标总数" :value="version?.indicatorCount ?? indicators.length" icon="Grid" variant="primary" />
      <StatCard label="叶子指标" :value="version?.leafCount ?? leafCount" icon="Document" />
      <StatCard label="最大层级" :value="version?.maxLevel ?? 0" icon="Sort" />
      <StatCard label="错误数" :value="errorCount" icon="WarningFilled" :variant="errorCount > 0 ? 'danger' : ''" />
      <StatCard label="警告数" :value="warningCount" icon="InfoFilled" :variant="warningCount > 0 ? 'warning' : ''" />
      <StatCard label="状态" :value="VERSION_STATUS_LABEL[version?.status as string] || version?.status || '-'" icon="CircleCheck" :variant="statusVariant(version?.status)" />
    </div>

    <div class="ees-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="指标树" name="tree">
          <div class="ees-hint" style="margin-bottom: 8px">
            树结构由后端按 parent_code 关系生成，点击任一节点可在「指标列表」中查看该节点下的指标明细。
          </div>
          <el-tree
            v-if="treeData.length > 0"
            :data="treeData"
            node-key="code"
            default-expand-all
            :expand-on-click-node="false"
            :props="{ label: 'name', children: 'children' }"
            @node-click="onNodeClick"
          >
            <template #default="{ data }">
              <span class="tree-node">
                <el-icon class="tree-icon">
                  <FolderOpened v-if="hasChildren(data)" />
                  <Document v-else />
                </el-icon>
                <span class="tree-name">{{ data?.name || '-' }}</span>
                <el-tag size="small" type="info" effect="plain" class="ees-mono">{{ data?.code || '-' }}</el-tag>
                <el-tag
                  v-if="data?.direction"
                  size="small"
                  :type="directionTagType(data.direction)"
                  effect="plain"
                >
                  {{ DIRECTION_LABEL[data.direction] || data.direction }}
                </el-tag>
                <span v-if="data?.unit" class="tree-extra">单位：{{ data.unit }}</span>
                <span class="tree-extra">权重：{{ fmtWeight(data?.weight) }}</span>
              </span>
            </template>
          </el-tree>
          <div v-else class="ees-empty">该版本暂无指标数据，请检查导入文件或重新导入</div>
        </el-tab-pane>

        <el-tab-pane label="指标列表" name="list">
          <div class="ees-filter-bar">
            <el-input
              v-model="listKeyword"
              class="list-filter-input"
              placeholder="指标编码 / 名称 / 上级编码"
              clearable
            />
            <el-tag v-if="nodeFilter" type="primary" effect="plain" closable @close="clearNodeFilter">
              已按树节点筛选：{{ nodeFilter }}
            </el-tag>
            <span class="ees-hint">共 {{ filteredIndicators.length }} 条</span>
          </div>
          <el-table
            v-loading="loading"
            :data="filteredIndicators"
            border
            stripe
            size="small"
            max-height="560"
            empty-text="没有匹配的指标"
          >
            <el-table-column prop="indicatorCode" label="指标编码" width="150" fixed="left" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="ees-mono">{{ row?.indicatorCode || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="indicatorName" label="指标名称" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row?.indicatorName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="parentCode" label="上级编码" width="130" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="ees-mono">{{ row?.parentCode || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="level" label="层级" width="86" align="center" sortable>
              <template #default="{ row }">{{ row?.level ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="90" show-overflow-tooltip>
              <template #default="{ row }">{{ row?.unit || '-' }}</template>
            </el-table-column>
            <el-table-column prop="direction" label="方向" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row?.direction" size="small" :type="directionTagType(row.direction)" effect="plain">
                  {{ DIRECTION_LABEL[row.direction] || row.direction }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="weight" label="权重" width="100" align="right" sortable>
              <template #default="{ row }">{{ fmtWeight(row?.weight) }}</template>
            </el-table-column>
            <el-table-column label="最小值" width="100" align="right">
              <template #default="{ row }">{{ fmtNum(row?.minValue, 4) }}</template>
            </el-table-column>
            <el-table-column label="最大值" width="100" align="right">
              <template #default="{ row }">{{ fmtNum(row?.maxValue, 4) }}</template>
            </el-table-column>
            <el-table-column label="区间下界" width="110" align="right">
              <template #default="{ row }">{{ fmtNum(row?.intervalLower, 4) }}</template>
            </el-table-column>
            <el-table-column label="区间上界" width="110" align="right">
              <template #default="{ row }">{{ fmtNum(row?.intervalUpper, 4) }}</template>
            </el-table-column>
            <el-table-column label="评分规则" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row?.scoreRule || '-' }}</template>
            </el-table-column>
            <el-table-column label="备注" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row?.remark || '-' }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="导入批次" name="batch">
          <el-table
            v-loading="batchLoading"
            :data="batches"
            border
            stripe
            size="small"
            empty-text="该版本暂无导入批次记录"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="batch-detail">
                  <div class="issue-title">错误明细（{{ batchErrors(row).length }}）</div>
                  <el-table :data="batchErrors(row)" border size="small" empty-text="该批次没有错误明细">
                    <el-table-column prop="row" label="行号" width="80" align="center">
                      <template #default="scope">{{ scope.row?.row ?? '-' }}</template>
                    </el-table-column>
                    <el-table-column prop="field" label="字段" width="160">
                      <template #default="scope">
                        <span class="ees-mono">{{ scope.row?.field || '-' }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column prop="message" label="说明" min-width="320" show-overflow-tooltip>
                      <template #default="scope">{{ scope.row?.message || '-' }}</template>
                    </el-table-column>
                    <el-table-column prop="severity" label="级别" width="90" align="center">
                      <template #default="scope">
                        <el-tag type="danger" effect="plain" size="small">
                          {{ severityLabel(scope.row?.severity) }}
                        </el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row?.fileName || '-' }}</template>
            </el-table-column>
            <el-table-column label="文件大小" width="110" align="right">
              <template #default="{ row }">{{ fmtFileSize(row?.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="总行数" width="90" align="right">
              <template #default="{ row }">{{ row?.totalRows ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="成功行数" width="100" align="right">
              <template #default="{ row }">{{ row?.successRows ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="错误行数" width="100" align="right">
              <template #default="{ row }">{{ row?.errorRows ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="批次状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="batchStatusType(row?.status)" effect="plain" size="small">
                  {{ batchStatusLabel(row?.status) }}
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
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <div class="ees-card">
      <div class="card-head">
        <div class="card-title">校验结果</div>
        <span class="ees-hint">
          {{ version?.validateResult ? `校验时间：${fmtDateTime(validateInfo?.validatedAt)}` : '该版本暂无校验结果，可点击「重新校验」生成' }}
        </span>
      </div>

      <template v-if="validateInfo">
        <div class="ees-stat-grid">
          <StatCard label="总行数" :value="validateInfo?.totalRows ?? 0" icon="Files" />
          <StatCard label="校验通过" :value="validateInfo?.successRows ?? 0" icon="CircleCheck" variant="success" />
          <StatCard
            label="错误数"
            :value="validateInfo?.errors.length ?? 0"
            icon="WarningFilled"
            :variant="(validateInfo?.errors.length ?? 0) > 0 ? 'danger' : ''"
          />
          <StatCard
            label="警告数"
            :value="validateInfo?.warnings.length ?? 0"
            icon="InfoFilled"
            :variant="(validateInfo?.warnings.length ?? 0) > 0 ? 'warning' : ''"
          />
        </div>

        <div class="issue-title">错误明细（{{ validateInfo?.errors.length ?? 0 }}）</div>
        <el-table :data="validateInfo?.errors ?? []" border size="small" max-height="260" empty-text="没有错误">
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
        </el-table>

        <div class="issue-title">警告明细（{{ validateInfo?.warnings.length ?? 0 }}）</div>
        <el-table :data="validateInfo?.warnings ?? []" border size="small" max-height="260" empty-text="没有警告">
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
        </el-table>
      </template>
      <div v-else class="ees-empty">暂无校验结果</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { indicatorApi, projectApi } from '@/api'
import { useUserStore } from '@/stores/user'
import {
  DIRECTION_LABEL,
  fmtDateTime,
  fmtFileSize,
  fmtNum,
  fmtWeight,
  parseJson,
  VERSION_STATUS_LABEL,
  VERSION_STATUS_TYPE
} from '@/utils/format'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'
type StatVariant = 'primary' | 'success' | 'warning' | 'danger' | ''

interface IndicatorRow {
  id?: number
  indicatorCode?: string
  indicatorName?: string
  parentCode?: string
  level?: number
  unit?: string
  direction?: string
  weight?: number | string | null
  minValue?: number | string | null
  maxValue?: number | string | null
  intervalLower?: number | string | null
  intervalUpper?: number | string | null
  optimalValue?: number | string | null
  scoreRule?: string
  remark?: string
}

interface TreeNode {
  code?: string
  name?: string
  direction?: string
  unit?: string
  weight?: number | string | null
  children?: TreeNode[]
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

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const canImport = computed(() => userStore.has('indicator:import'))

const versionId = computed(() => {
  const raw = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  return Number(raw ?? 0)
})

const loading = ref(false)
const batchLoading = ref(false)
const revalidating = ref(false)
const version = ref<any>(null)
const treeData = ref<TreeNode[]>([])
const indicators = ref<IndicatorRow[]>([])
const batches = ref<any[]>([])
const projectName = ref('')
const activeTab = ref('tree')
const listKeyword = ref('')
const nodeFilter = ref('')

const headTitle = computed(() => {
  const code = version.value?.versionCode
  const name = version.value?.versionName
  if (code && name) return `指标体系 ${code} · ${name}`
  if (code) return `指标体系 ${code}`
  return '指标体系详情'
})

const headDesc = computed(
  () => '系统只负责指标体系的导入、校验、版本化和使用，不负责重新设计指标体系；导入后的原始版本只读保存'
)

const leafCount = computed(() => indicators.value.filter((i) => isLeaf(i)).length)

const validateInfo = computed<ValidateData | null>(() => parseValidate(version.value?.validateResult))
const errorCount = computed(() => validateInfo.value?.errors.length ?? 0)
const warningCount = computed(() => validateInfo.value?.warnings.length ?? 0)

const filteredIndicators = computed<IndicatorRow[]>(() => {
  const kw = listKeyword.value.trim().toLowerCase()
  if (!kw) return indicators.value
  return indicators.value.filter((i) => {
    const code = String(i.indicatorCode ?? '').toLowerCase()
    const name = String(i.indicatorName ?? '').toLowerCase()
    const parent = String(i.parentCode ?? '').toLowerCase()
    return code.includes(kw) || name.includes(kw) || parent.includes(kw)
  })
})

function isLeaf(row: IndicatorRow): boolean {
  const code = row.indicatorCode
  if (!code) return true
  return !indicators.value.some((i) => i.parentCode === code)
}

function versionStatusType(status?: string): TagType {
  const t = status ? VERSION_STATUS_TYPE[status] : ''
  if (t === 'primary' || t === 'success' || t === 'info' || t === 'warning' || t === 'danger') return t
  return 'info'
}

function statusVariant(status?: string): StatVariant {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'INVALID') return 'danger'
  if (status === 'ARCHIVED') return 'warning'
  if (status === 'VALIDATED') return 'primary'
  return ''
}

function directionTagType(direction?: string): TagType {
  if (direction === 'POSITIVE') return 'success'
  if (direction === 'NEGATIVE') return 'warning'
  if (direction === 'INTERVAL') return 'primary'
  return 'info'
}

function batchStatusLabel(status?: string): string {
  if (status === 'SUCCESS') return '导入成功'
  if (status === 'PARTIAL') return '部分失败'
  if (status === 'FAILED') return '导入失败'
  return status || '-'
}

function batchStatusType(status?: string): TagType {
  if (status === 'SUCCESS') return 'success'
  if (status === 'PARTIAL') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function severityLabel(severity?: string): string {
  return severity === 'WARN' || severity === 'WARNING' ? '警告' : '错误'
}

function hasChildren(node: TreeNode | null | undefined): boolean {
  return Array.isArray(node?.children) && node.children.length > 0
}

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

function batchErrors(row: any): ValidateIssue[] {
  const parsed = parseJson<any>(row?.errorDetail, [])
  if (Array.isArray(parsed)) return parsed as ValidateIssue[]
  if (parsed && Array.isArray(parsed.errors)) return parsed.errors as ValidateIssue[]
  return []
}

function onNodeClick(data: TreeNode) {
  if (!data?.code) return
  nodeFilter.value = data.code
  listKeyword.value = data.code
  activeTab.value = 'list'
}

function clearNodeFilter() {
  nodeFilter.value = ''
  listKeyword.value = ''
}

/** ---------------- 数据加载 ---------------- */

async function loadVersion() {
  try {
    version.value = (await indicatorApi.get(versionId.value)) || null
  } catch {
    version.value = null
  }
}

async function loadTree() {
  try {
    const list = await indicatorApi.tree(versionId.value)
    treeData.value = Array.isArray(list) ? (list as TreeNode[]) : []
  } catch {
    treeData.value = []
  }
}

async function loadIndicators() {
  try {
    const list = await indicatorApi.indicators(versionId.value)
    indicators.value = Array.isArray(list) ? (list as IndicatorRow[]) : []
  } catch {
    indicators.value = []
  }
}

async function loadBatches() {
  batchLoading.value = true
  try {
    const list = await indicatorApi.importBatches(versionId.value)
    batches.value = Array.isArray(list) ? list : []
  } catch {
    batches.value = []
  } finally {
    batchLoading.value = false
  }
}

async function loadProjectName() {
  const pid = Number(version.value?.projectId ?? 0)
  if (!pid) {
    projectName.value = ''
    return
  }
  try {
    const detail = await projectApi.get(pid)
    projectName.value = detail?.projectName || `项目#${pid}`
  } catch {
    projectName.value = ''
  }
}

async function loadAll() {
  if (!Number.isFinite(versionId.value) || versionId.value <= 0) {
    ElMessage.error('无效的指标体系版本编号')
    return
  }
  loading.value = true
  try {
    await Promise.all([loadVersion(), loadTree(), loadIndicators(), loadBatches()])
    await loadProjectName()
  } finally {
    loading.value = false
  }
}

/** ---------------- 操作 ---------------- */

function goBack() {
  router.push('/indicators')
}

async function revalidate() {
  revalidating.value = true
  try {
    await indicatorApi.revalidate(versionId.value)
    ElMessage.success('校验完成')
    await loadVersion()
  } catch {
    // 统一响应拦截器已提示
  } finally {
    revalidating.value = false
  }
}

async function publish() {
  try {
    await ElMessageBox.confirm(
      `发布后版本「${version.value?.versionCode || ''}」将可用于创建评估任务，原始指标数据保持只读。确定发布吗？`,
      '发布确认',
      { type: 'warning', confirmButtonText: '确定发布', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await indicatorApi.publish(versionId.value)
    ElMessage.success('版本已发布')
    await loadVersion()
  } catch {
    // 统一响应拦截器已提示
  }
}

/** ---------------- 前端导出（不调用后端接口） ---------------- */

function csvCell(value: any): string {
  if (value === null || value === undefined) return ''
  const text = String(value)
  if (/[",\r\n]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`
  }
  return text
}

function exportIndicators() {
  const list = indicators.value
  if (list.length === 0) {
    ElMessage.warning('当前版本没有可导出的指标数据')
    return
  }
  const headers = [
    '指标编码',
    '指标名称',
    '上级编码',
    '层级',
    '单位',
    '方向',
    '权重',
    '最小值',
    '最大值',
    '区间下界',
    '区间上界',
    '最优值',
    '是否叶子',
    '评分规则',
    '备注'
  ]
  const lines: string[] = [headers.join(',')]
  for (const row of list) {
    const direction = row.direction ? DIRECTION_LABEL[row.direction] || row.direction : ''
    lines.push(
      [
        csvCell(row.indicatorCode),
        csvCell(row.indicatorName),
        csvCell(row.parentCode),
        csvCell(row.level),
        csvCell(row.unit),
        csvCell(direction),
        csvCell(fmtWeight(row.weight)),
        csvCell(fmtNum(row.minValue, 4, '')),
        csvCell(fmtNum(row.maxValue, 4, '')),
        csvCell(fmtNum(row.intervalLower, 4, '')),
        csvCell(fmtNum(row.intervalUpper, 4, '')),
        csvCell(fmtNum(row.optimalValue, 4, '')),
        csvCell(isLeaf(row) ? '是' : '否'),
        csvCell(row.scoreRule),
        csvCell(row.remark)
      ].join(',')
    )
  }
  // 加 BOM，保证 Excel 直接打开时中文不乱码
  const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${version.value?.versionCode || 'indicators'}-指标清单.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${list.length} 条指标`)
}

onMounted(loadAll)
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.card-title {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--ees-text);
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
}

.tree-icon {
  color: var(--ees-primary-light);
}

.tree-name {
  font-weight: 500;
}

.tree-extra {
  font-size: 12px;
  color: var(--ees-text-muted);
}

.list-filter-input {
  width: 260px;
}

.batch-detail {
  padding: 8px 14px 14px;
  background: #fbfcff;
}

.issue-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ees-text);
  margin: 14px 0 8px;
}
</style>
