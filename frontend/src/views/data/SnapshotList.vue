<template>
  <div class="ees-page">
    <PageHeader
      title="数据快照"
      desc="快照在任务执行完成后保持不变，历史结果不因源数据变化而被覆盖；原始数据与标准化数据分别保存"
    >
      <template #actions>
        <el-button v-if="canCreate" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>生成快照</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="ees-card">
      <div class="ees-filter-bar">
        <el-select v-model="query.projectId" placeholder="项目" clearable filterable style="width: 220px">
          <el-option v-for="p in projects" :key="p.id" :label="`${p.code || ''} ${p.name || ''}`.trim()" :value="p.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="快照状态" clearable style="width: 160px">
          <el-option v-for="(label, value) in SNAPSHOT_STATUS_LABEL" :key="value" :label="label" :value="value" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="快照编号 / 批次编号"
          clearable
          style="width: 220px"
          @keyup.enter="search()"
        />
        <el-button type="primary" @click="search()">查询</el-button>
        <el-button @click="reset()">重置</el-button>
        <span class="ees-hint">快照一经生成即固化数据与标准化结果，已被评估任务使用的快照不允许删除。</span>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <template #empty>
          <div class="ees-empty">
            暂无数据快照，可在数据批次页面点击“生成数据快照”，或使用右上角的“生成快照”。
          </div>
        </template>
        <el-table-column prop="snapshotCode" label="快照编号" width="190" fixed="left" show-overflow-tooltip />
        <el-table-column prop="projectCode" label="项目编码" width="130" show-overflow-tooltip />
        <el-table-column label="指标体系版本" min-width="160">
          <template #default="{ row }">
            <el-tooltip :content="row.indicatorVersionCode || '未绑定版本'" placement="top" :show-after="300">
              <span class="ees-mono ellipsis">{{ row.indicatorVersionCode || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="来源批次" width="180">
          <template #default="{ row }">
            <el-tooltip :content="row.batchCode || '未知批次'" placement="top" :show-after="300">
              <span class="ees-mono ellipsis">{{ row.batchCode || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="记录数" width="95" align="right">
          <template #default="{ row }">{{ fmtNum(row.recordCount, 0) }}</template>
        </el-table-column>
        <el-table-column label="对象数" width="90" align="right">
          <template #default="{ row }">{{ fmtNum(row.objectCount, 0) }}</template>
        </el-table-column>
        <el-table-column label="指标数" width="90" align="right">
          <template #default="{ row }">{{ fmtNum(row.indicatorCount, 0) }}</template>
        </el-table-column>
        <el-table-column label="缺失数" width="90" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-warn': Number(row.missingCount) > 0 }">{{ fmtNum(row.missingCount, 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="越界数" width="90" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-warn': Number(row.outlierCount) > 0 }">{{ fmtNum(row.outlierCount, 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="125" align="center">
          <template #default="{ row }">
            <el-tag :type="snapshotStatusType(row.status)" size="small" effect="plain">
              {{ snapshotStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数据时间范围" width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.dataRange || '-' }}</template>
        </el-table-column>
        <el-table-column label="生成时间" width="165">
          <template #default="{ row }">{{ fmtDateTime(row.generatedAt) }}</template>
        </el-table-column>
        <el-table-column prop="createdBy" label="生成人" width="110" show-overflow-tooltip />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button link type="primary" size="small" @click="openOverview(row)">快照概览</el-button>
              <el-tooltip
                :disabled="row.status !== 'USED'"
                content="该快照已被评估任务使用，为保证可追溯性不允许删除"
                placement="top"
              >
                <span>
                  <el-button
                    v-if="canCreate"
                    link
                    type="danger"
                    size="small"
                    :disabled="row.status === 'USED'"
                    @click="remove(row)"
                  >
                    删除
                  </el-button>
                </span>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="ees-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="load()"
          @size-change="search()"
        />
      </div>
    </div>

    <!-- 快照概览 -->
    <el-drawer v-model="overviewVisible" title="快照概览" size="82%" destroy-on-close>
      <div v-loading="overviewLoading" class="drawer-body">
        <div class="ees-stat-grid">
          <StatCard label="记录数" :value="fmtNum(currentSnapshot?.recordCount, 0)" variant="primary" foot="原始记录与快照值一一对应" />
          <StatCard label="对象数" :value="fmtNum(currentSnapshot?.objectCount, 0)" />
          <StatCard label="指标数" :value="fmtNum(currentSnapshot?.indicatorCount, 0)" foot="参与评估的叶子指标" />
          <StatCard label="缺失数" :value="fmtNum(currentSnapshot?.missingCount, 0)" variant="warning" />
          <StatCard label="越界数" :value="fmtNum(currentSnapshot?.outlierCount, 0)" variant="warning" />
          <StatCard
            label="已使用次数"
            :value="fmtNum(currentSnapshot?.usedCount, 0)"
            :variant="currentSnapshot?.status === 'USED' ? 'success' : ''"
          />
        </div>

        <div class="ees-card ees-card--flat">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="快照编号">
              <span class="ees-mono">{{ currentSnapshot?.snapshotCode || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="项目编码">{{ currentSnapshot?.projectCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="snapshotStatusType(currentSnapshot?.status)" size="small" effect="plain">
                {{ snapshotStatusLabel(currentSnapshot?.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="指标体系版本">
              <span class="ees-mono">{{ currentSnapshot?.indicatorVersionCode || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="来源批次">
              <span class="ees-mono">{{ currentSnapshot?.batchCode || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="数据时间范围">{{ currentSnapshot?.dataRange || '-' }}</el-descriptions-item>
            <el-descriptions-item label="生成时间">{{ fmtDateTime(currentSnapshot?.generatedAt) }}</el-descriptions-item>
            <el-descriptions-item label="生成人">{{ currentSnapshot?.createdBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ currentSnapshot?.remark || '-' }}</el-descriptions-item>
            <el-descriptions-item label="数据校验和(checksum)" :span="3">
              <el-tooltip :content="currentSnapshot?.checksum || '未计算'" placement="top" :show-after="300">
                <span class="ees-mono ellipsis">{{ currentSnapshot?.checksum || '-' }}</span>
              </el-tooltip>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="ees-card ees-card--flat">
          <div class="section-title">质量报告</div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="记录数">
              {{ fmtNum(qualityReport?.recordCount ?? currentSnapshot?.recordCount, 0) }}
            </el-descriptions-item>
            <el-descriptions-item label="对象数">
              {{ fmtNum(qualityReport?.objectCount ?? currentSnapshot?.objectCount, 0) }}
            </el-descriptions-item>
            <el-descriptions-item label="指标数">
              {{ fmtNum(qualityReport?.indicatorCount ?? currentSnapshot?.indicatorCount, 0) }}
            </el-descriptions-item>
            <el-descriptions-item label="缺失数">
              {{ fmtNum(qualityReport?.missingCount ?? currentSnapshot?.missingCount, 0) }}
            </el-descriptions-item>
            <el-descriptions-item label="越界数">
              {{ fmtNum(qualityReport?.outlierCount ?? currentSnapshot?.outlierCount, 0) }}
            </el-descriptions-item>
            <el-descriptions-item label="重复记录处理数">{{ fmtNum(qualityReport?.duplicateResolved, 0) }}</el-descriptions-item>
            <el-descriptions-item label="来源批次">
              <span class="ees-mono">{{ qualityReport?.batchCode || currentSnapshot?.batchCode || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="指标体系版本">
              <span class="ees-mono">{{ qualityReport?.indicatorVersionCode || currentSnapshot?.indicatorVersionCode || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="报告生成时间">{{ fmtDateTime(qualityReport?.generatedAt) }}</el-descriptions-item>
          </el-descriptions>

          <div class="section-title section-title--sub">标准化取值范围（normalizationRanges）</div>
          <el-table :data="rangeRows" border size="small" max-height="260" empty-text="该快照未记录标准化取值范围">
            <el-table-column prop="code" label="指标编码" min-width="150" show-overflow-tooltip />
            <el-table-column label="取值范围下限" width="150" align="right">
              <template #default="{ row }">{{ fmtNum(row.min, 4) }}</template>
            </el-table-column>
            <el-table-column label="取值范围上限" width="150" align="right">
              <template #default="{ row }">{{ fmtNum(row.max, 4) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="ees-card ees-card--flat">
          <div class="section-title">对象列表（{{ fmtNum(objects.length, 0) }}）与指标列表（{{ fmtNum(indicators.length, 0) }}）</div>
          <div class="tag-block">
            <span class="tag-label">对象</span>
            <el-tag v-for="o in objects" :key="o" size="small" effect="plain" class="tag-item">{{ o }}</el-tag>
            <span v-if="!objects.length" class="ees-hint">暂无对象</span>
          </div>
          <div class="tag-block">
            <span class="tag-label">指标</span>
            <el-tag v-for="i in indicators" :key="i" size="small" type="info" effect="plain" class="tag-item">{{ i }}</el-tag>
            <span v-if="!indicators.length" class="ees-hint">暂无指标</span>
          </div>
        </div>

        <div class="ees-card ees-card--flat">
          <div class="filter-head">
            <div class="section-title">快照指标值（按对象分组）</div>
            <el-select
              v-model="selectedObject"
              placeholder="请选择对象"
              filterable
              clearable
              style="width: 300px"
            >
              <el-option v-for="o in objects" :key="o" :label="o" :value="o" />
            </el-select>
          </div>
          <el-table
            :data="currentValues"
            border
            size="small"
            max-height="460"
            empty-text="没有可展示的快照值"
          >
            <el-table-column prop="indicatorCode" label="指标编码" width="140" show-overflow-tooltip />
            <el-table-column prop="indicatorName" label="指标名称" min-width="170" show-overflow-tooltip />
            <el-table-column label="原始值" width="130" align="right">
              <template #default="{ row }">{{ fmtNum(row.rawValue, 4) }}</template>
            </el-table-column>
            <el-table-column label="标准化值" width="130" align="right">
              <template #default="{ row }">{{ fmtNum(row.normalizedValue, 4) }}</template>
            </el-table-column>
            <el-table-column label="缺失/越界标记" width="130" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.missing" type="danger" size="small" effect="plain">缺失</el-tag>
                <el-tag v-else-if="row.outlier" type="warning" size="small" effect="plain">越界</el-tag>
                <el-tag v-else-if="row.valid === false" type="info" size="small" effect="plain">无效</el-tag>
                <el-tag v-else type="success" size="small" effect="plain">正常</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="数据时间" width="165">
              <template #default="{ row }">{{ fmtDateTime(row.dataTime) }}</template>
            </el-table-column>
            <el-table-column label="备注" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.remark || '-' }}</template>
            </el-table-column>
          </el-table>
          <div v-if="!selectedObject" class="ees-hint">请先选择对象，即可查看该对象全部指标的原始值与标准化值。</div>
        </div>
      </div>
    </el-drawer>

    <!-- 生成快照 -->
    <el-dialog v-model="createVisible" title="生成数据快照" width="820px" top="7vh">
      <div class="dialog-body">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="快照由数据批次生成"
          description="选择数据批次与指标体系版本后生成快照；快照固化该批次的原始数据与标准化数据，生成后保持不变。校验未通过的批次无法生成快照。"
        />

        <template v-if="!createResult">
          <el-form label-width="150px" class="create-form">
            <el-form-item label="数据批次" required>
              <el-select
                v-model="createForm.batchId"
                placeholder="请选择数据批次"
                filterable
                style="width: 420px"
                :loading="batchLoading"
                @change="onBatchChange"
              >
                <el-option
                  v-for="b in batches"
                  :key="b.id"
                  :label="batchOptionLabel(b)"
                  :value="b.id"
                  :disabled="b.status === 'FAILED'"
                />
              </el-select>
              <div v-if="selectedBatch" class="ees-hint">
                批次 {{ selectedBatch.batchCode || '-' }} · 状态 {{ batchStatusLabel(selectedBatch.status) }} ·
                有效 {{ fmtNum(selectedBatch.validRows, 0) }} 行 / 错误 {{ fmtNum(selectedBatch.errorRows, 0) }} 行
              </div>
            </el-form-item>
            <el-form-item label="指标体系版本" required>
              <el-select v-model="createForm.indicatorVersionId" placeholder="请选择指标体系版本" filterable style="width: 320px">
                <el-option
                  v-for="v in versions"
                  :key="v.id"
                  :label="`${v.versionCode || ''} ${v.versionName || ''}`.trim()"
                  :value="v.id"
                />
              </el-select>
              <div class="ees-hint">默认取所选批次导入时绑定的指标体系版本，可切换为其它版本进行对比。</div>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-form>
        </template>

        <template v-else>
          <el-alert
            type="success"
            :closable="false"
            show-icon
            title="数据快照生成成功"
            :description="`快照编号：${createResult.snapshotCode || '-'}；数据时间范围：${createResult.dataRange || '-'}`"
          />
          <div class="ees-stat-grid result-grid">
            <StatCard label="记录数" :value="fmtNum(createResult.recordCount, 0)" variant="primary" />
            <StatCard label="对象数" :value="fmtNum(createResult.objectCount, 0)" />
            <StatCard label="指标数" :value="fmtNum(createResult.indicatorCount, 0)" />
            <StatCard label="缺失数" :value="fmtNum(createResult.missingCount, 0)" variant="warning" />
            <StatCard label="越界数" :value="fmtNum(createResult.outlierCount, 0)" variant="warning" />
          </div>
          <el-link type="primary" :underline="false" href="#/snapshots">刷新数据快照列表查看概览 →</el-link>
        </template>
      </div>
      <template #footer>
        <el-button @click="createVisible = false">{{ createResult ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!createResult" type="primary" :loading="createSaving" @click="submitCreate">生成快照</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dataBatchApi, indicatorApi, projectApi, snapshotApi } from '@/api'
import { useUserStore } from '@/stores/user'
import {
  BATCH_STATUS_LABEL,
  fmtDateTime,
  fmtNum,
  parseJson,
  SNAPSHOT_STATUS_LABEL
} from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'

/** ---------------- 类型 ---------------- */
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

interface ProjectOption {
  id: number
  code?: string
  name?: string
  status?: string
}

interface VersionOption {
  id: number
  versionCode?: string
  versionName?: string
  status?: string
  indicatorCount?: number
}

interface SnapshotRow {
  id: number
  snapshotCode?: string
  projectId?: number | null
  projectCode?: string
  indicatorVersionId?: number | null
  indicatorVersionCode?: string
  batchId?: number
  batchCode?: string
  recordCount?: number
  objectCount?: number
  indicatorCount?: number
  missingCount?: number
  outlierCount?: number
  checksum?: string
  status?: string
  qualityReport?: string
  dataRange?: string
  generatedAt?: string
  usedCount?: number
  createdBy?: string
  remark?: string
}

interface SnapshotValueRow {
  id?: number
  objectCode?: string
  objectName?: string
  indicatorCode?: string
  indicatorName?: string
  rawValue?: number | string | null
  normalizedValue?: number | string | null
  normalized?: boolean
  valid?: boolean
  outlier?: boolean
  missing?: boolean
  dataTime?: string
  remark?: string
}

interface OverviewData {
  snapshot?: SnapshotRow
  objects?: string[]
  indicators?: string[]
  missingCount?: number
  outlierCount?: number
  recordCount?: number
  values?: SnapshotValueRow[]
}

interface BatchRow {
  id: number
  batchCode?: string
  projectId?: number | null
  projectCode?: string
  targetVersionId?: number | null
  totalRows?: number
  validRows?: number
  errorRows?: number
  status?: string
}

interface SnapshotCreateResult {
  snapshotId?: number
  snapshotCode?: string
  recordCount?: number
  objectCount?: number
  indicatorCount?: number
  missingCount?: number
  outlierCount?: number
  dataRange?: string
  qualityReport?: Record<string, any>
}

/** ---------------- 权限 ---------------- */
const userStore = useUserStore()
const canCreate = computed(() => userStore.has('data:snapshot:create'))

/** ---------------- 字典 ---------------- */
const SNAPSHOT_STATUS_TYPE: Record<string, TagType> = {
  GENERATED: 'success',
  USED: 'primary',
  INVALID: 'danger'
}

function snapshotStatusLabel(status?: string): string {
  if (!status) return '-'
  return SNAPSHOT_STATUS_LABEL[status] || status
}

function snapshotStatusType(status?: string): TagType {
  return SNAPSHOT_STATUS_TYPE[String(status || 'GENERATED')] || 'info'
}

function batchStatusLabel(status?: string): string {
  if (!status) return '-'
  return BATCH_STATUS_LABEL[status] || status
}

/** ---------------- 列表 ---------------- */
const loading = ref(false)
const rows = ref<SnapshotRow[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const query = reactive<{ projectId: number | null; status: string; keyword: string }>({
  projectId: null,
  status: '',
  keyword: ''
})

const projects = ref<ProjectOption[]>([])
const versions = ref<VersionOption[]>([])

async function load() {
  loading.value = true
  try {
    const res = await snapshotApi.list({
      projectId: query.projectId ?? undefined,
      status: query.status || undefined,
      keyword: query.keyword || undefined,
      page: page.value,
      size: size.value
    })
    rows.value = Array.isArray(res?.records) ? res.records : []
    total.value = Number(res?.total || 0)
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  query.projectId = null
  query.status = ''
  query.keyword = ''
  search()
}

async function loadProjects() {
  try {
    projects.value = (await projectApi.options()) || []
  } catch {
    projects.value = []
  }
}

async function loadVersions(projectId?: number | null) {
  try {
    versions.value = (await indicatorApi.options(projectId ?? undefined)) || []
  } catch {
    versions.value = []
  }
}

/** ---------------- 快照概览 ---------------- */
const overviewVisible = ref(false)
const overviewLoading = ref(false)
const currentSnapshot = ref<SnapshotRow | null>(null)
const objects = ref<string[]>([])
const indicators = ref<string[]>([])
const allValues = ref<SnapshotValueRow[]>([])
const selectedObject = ref<string>('')

const qualityReport = computed<Record<string, any>>(() =>
  parseJson<Record<string, any>>(currentSnapshot.value?.qualityReport, {})
)

const rangeRows = computed<Array<{ code: string; min?: number; max?: number }>>(() => {
  const ranges = qualityReport.value?.normalizationRanges
  if (!ranges || typeof ranges !== 'object') return []
  return Object.keys(ranges).map((code) => {
    const item = (ranges as Record<string, any>)[code] || {}
    return { code, min: item.min, max: item.max }
  })
})

const currentValues = computed<SnapshotValueRow[]>(() => {
  if (!selectedObject.value) return []
  return allValues.value.filter((v) => v.objectCode === selectedObject.value)
})

async function openOverview(row: SnapshotRow) {
  currentSnapshot.value = row
  objects.value = []
  indicators.value = []
  allValues.value = []
  selectedObject.value = ''
  overviewVisible.value = true
  overviewLoading.value = true
  try {
    // 概览只返回前若干对象的快照值，同时按需拉取完整值列表用于逐对象查看
    const [overview, full] = await Promise.all([
      snapshotApi.overview(row.id, 100),
      snapshotApi.values(row.id).catch(() => null)
    ])
    const data: OverviewData = overview || {}
    currentSnapshot.value = data.snapshot || row
    objects.value = Array.isArray(data.objects) ? data.objects : []
    indicators.value = Array.isArray(data.indicators) ? data.indicators : []
    const previewValues = Array.isArray(data.values) ? data.values : []
    allValues.value = Array.isArray(full) && full.length ? full : previewValues
    selectedObject.value = objects.value[0] || ''
  } catch {
    objects.value = []
    indicators.value = []
    allValues.value = []
  } finally {
    overviewLoading.value = false
  }
}

async function remove(row: SnapshotRow) {
  if (row.status === 'USED') {
    ElMessage.warning('该快照已被评估任务使用，为保证可追溯性不允许删除')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除数据快照「${row.snapshotCode || row.id}」吗？删除后该快照的标准化数据将不可追溯，已被任务使用的快照不允许删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await snapshotApi.remove(row.id)
    ElMessage.success('数据快照已删除')
    if (rows.value.length <= 1 && page.value > 1) page.value -= 1
    await load()
  } catch {
    // 错误提示由统一请求层给出
  }
}

/** ---------------- 生成快照 ---------------- */
const createVisible = ref(false)
const createSaving = ref(false)
const batchLoading = ref(false)
const batches = ref<BatchRow[]>([])
const createResult = ref<SnapshotCreateResult | null>(null)
const createForm = reactive<{ batchId: number | null; indicatorVersionId: number | null; remark: string }>({
  batchId: null,
  indicatorVersionId: null,
  remark: ''
})

const selectedBatch = computed<BatchRow | null>(
  () => batches.value.find((b) => b.id === createForm.batchId) || null
)

function batchOptionLabel(b: BatchRow): string {
  const parts = [b.batchCode || `#${b.id}`]
  if (b.projectCode) parts.push(b.projectCode)
  parts.push(`${batchStatusLabel(b.status)}·有效${fmtNum(b.validRows, 0)}行`)
  return parts.join(' · ')
}

async function openCreate() {
  createResult.value = null
  createForm.batchId = null
  createForm.indicatorVersionId = null
  createForm.remark = ''
  createVisible.value = true
  batchLoading.value = true
  try {
    const res = await dataBatchApi.list({ page: 1, size: 100 })
    batches.value = Array.isArray(res?.records) ? res.records : []
  } catch {
    batches.value = []
  } finally {
    batchLoading.value = false
  }
  await loadVersions()
}

async function onBatchChange(batchId?: number | null) {
  const batch = batches.value.find((b) => b.id === batchId) || null
  createForm.indicatorVersionId = batch?.targetVersionId ?? null
  await loadVersions(batch?.projectId ?? null)
  if (createForm.indicatorVersionId == null && versions.value.length === 1) {
    createForm.indicatorVersionId = versions.value[0].id
  }
}

async function submitCreate() {
  if (createForm.batchId == null) {
    ElMessage.warning('请选择数据批次')
    return
  }
  if (createForm.indicatorVersionId == null) {
    ElMessage.warning('请选择指标体系版本')
    return
  }
  createSaving.value = true
  try {
    const res = await snapshotApi.create({
      batchId: createForm.batchId,
      indicatorVersionId: createForm.indicatorVersionId,
      remark: createForm.remark.trim() || undefined
    })
    createResult.value = res || null
    ElMessage.success(`数据快照生成成功：${createResult.value?.snapshotCode || '-'}`)
    await load()
  } catch {
    // 错误提示由统一请求层给出
  } finally {
    createSaving.value = false
  }
}

onMounted(async () => {
  await loadProjects()
  await load()
})
</script>

<style scoped>
.ellipsis {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-warn {
  color: var(--ees-warning);
  font-weight: 600;
}

.op-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 6px;
}

.op-cell :deep(.el-button + .el-button) {
  margin-left: 0;
}

.drawer-body {
  padding-bottom: 24px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ees-text);
  margin-bottom: 10px;
}

.section-title--sub {
  margin-top: 16px;
  font-size: 13px;
  color: var(--ees-text-secondary);
}

.filter-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-head .section-title {
  margin-bottom: 0;
}

.tag-block {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.tag-label {
  font-size: 12.5px;
  color: var(--ees-text-secondary);
  min-width: 36px;
}

.tag-item {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dialog-body {
  max-height: 68vh;
  overflow-y: auto;
  padding-right: 4px;
}

.create-form {
  margin-top: 14px;
}

.result-grid {
  margin-top: 14px;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
}

.result-grid :deep(.ees-stat) {
  padding: 12px 14px;
}

.result-grid :deep(.ees-stat-value) {
  font-size: 21px;
}
</style>
