<template>
  <div class="ees-page">
    <PageHeader
      title="数据源管理"
      desc="通过只读账号接入甲方业务数据库；只允许单条 SELECT 查询，禁止任何写操作，连接具有超时与行数上限"
    >
      <template #actions>
        <el-button v-if="canCreate" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新建数据源</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="ees-card">
      <div class="ees-filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="数据源名称 / 编码 / 地址"
          clearable
          style="width: 240px"
          @keyup.enter="search()"
        />
        <el-select v-model="query.dbType" placeholder="数据库类型" clearable style="width: 180px">
          <el-option v-for="t in dbTypes" :key="t.dbType" :label="t.label" :value="t.dbType" />
        </el-select>
        <el-button type="primary" @click="search()">查询</el-button>
        <el-button @click="reset()">重置</el-button>
        <span class="ees-hint">数据源密码加密保存，页面仅显示掩码，不会回显明文。</span>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <template #empty>
          <div class="ees-empty">暂无数据源，请点击“新建数据源”接入甲方业务库。</div>
        </template>
        <el-table-column prop="sourceCode" label="数据源编码" width="160" fixed="left" show-overflow-tooltip />
        <el-table-column prop="sourceName" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="数据库类型" width="140">
          <template #default="{ row }">
            <el-tooltip :content="`dbType：${row.dbType || '未知'}`" placement="top" :show-after="300">
              <el-tag size="small" effect="plain">{{ dbTypeLabel(row.dbType) }}</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="JDBC 地址" min-width="230">
          <template #default="{ row }">
            <el-tooltip :content="row.jdbcUrl || '未配置'" placement="top" :show-after="300">
              <span class="ees-mono ellipsis">{{ row.jdbcUrl || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="dbUsername" label="账号" width="120" show-overflow-tooltip />
        <el-table-column label="只读" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.readOnly === false ? 'warning' : 'success'" size="small" effect="plain">
              {{ row.readOnly === false ? '否' : '是' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" size="small" effect="plain">
              {{ statusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近测试时间" width="165">
          <template #default="{ row }">{{ fmtDateTime(row.lastTestAt) }}</template>
        </el-table-column>
        <el-table-column label="最近测试结果" min-width="180">
          <template #default="{ row }">
            <el-tooltip v-if="row.lastTestResult" :content="row.lastTestResult" placement="top" :show-after="300">
              <span class="ellipsis">{{ row.lastTestResult }}</span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button v-if="canTest" link type="primary" size="small" @click="openTest(row)">连接测试</el-button>
              <el-button link type="primary" size="small" @click="openTables(row)">表清单</el-button>
              <el-button v-if="canExtract" link type="primary" size="small" @click="openExtract(row)">数据抽取</el-button>
              <el-button v-if="canUpdate" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="canUpdate" link type="danger" size="small" @click="remove(row)">删除</el-button>
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

    <!-- 连接测试结果 -->
    <el-dialog v-model="testVisible" title="连接测试结果" width="640px">
      <div v-loading="testLoading" class="dialog-body">
        <el-alert
          v-if="testResult"
          :type="testResult.success ? 'success' : 'error'"
          :closable="false"
          show-icon
          :title="testResult.success ? '连接成功' : '连接失败'"
          :description="testResult.message || ''"
        />
        <el-descriptions v-if="testResult" :column="1" border size="small">
          <el-descriptions-item label="数据源">{{ testTarget?.sourceName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="数据库产品">{{ testResult.productName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="产品版本">{{ testResult.productVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="驱动">{{ testResult.driverName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="URL">
            <span class="ees-mono">{{ testResult.url || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ fmtDuration(testResult.costMs) }}</el-descriptions-item>
          <el-descriptions-item label="是否只读">{{ readOnlyText(testResult.readOnly) }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">{{ testResult.success ? '无' : testResult.message || '未知错误' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="!testResult && !testLoading" class="ees-empty">暂无测试结果</div>
      </div>
      <template #footer>
        <el-button @click="testVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 表清单与字段预览 -->
    <el-dialog v-model="tablesVisible" title="表 / 视图清单与字段预览" width="900px" top="6vh">
      <div class="dialog-body">
        <div class="ees-filter-bar">
          <el-select
            v-model="selectedTable"
            filterable
            clearable
            placeholder="请选择表或视图"
            style="width: 320px"
            :loading="tablesLoading"
            @change="onTableChange"
          >
            <el-option v-for="t in tables" :key="t" :label="t" :value="t" />
          </el-select>
          <span class="ees-hint">共 {{ fmtNum(tables.length, 0) }} 张表 / 视图；字段预览最多读取一行样例数据。</span>
        </div>
        <el-table
          v-loading="fieldsLoading"
          :data="fields"
          border
          size="small"
          max-height="420"
          empty-text="请先选择表或视图"
        >
          <el-table-column prop="columnName" label="字段名" min-width="160" show-overflow-tooltip />
          <el-table-column prop="dataType" label="数据类型" width="130" show-overflow-tooltip />
          <el-table-column label="长度" width="90" align="right">
            <template #default="{ row }">{{ row.columnSize ? fmtNum(row.columnSize, 0) : '-' }}</template>
          </el-table-column>
          <el-table-column label="是否可空" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.nullable ? 'info' : 'warning'" size="small" effect="plain">
                {{ row.nullable ? '可空' : '非空' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="字段说明" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.columnComment || '-' }}</template>
          </el-table-column>
          <el-table-column label="样例值" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.sampleValue ?? '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="tablesVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 数据抽取 -->
    <el-dialog v-model="extractVisible" title="数据抽取（只读）" width="900px" top="6vh">
      <div class="dialog-body">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="只读抽取约束"
          description="仅允许单条 SELECT / WITH 查询，禁止任何写操作关键字与 SQL 注释；查询受连接超时与行数上限约束，超出部分会被截断。抽取完成后自动生成数据批次并执行质量校验。"
        />

        <el-form label-width="170px" class="extract-form">
          <el-form-item label="目标项目">
            <el-select
              v-model="extractForm.projectId"
              placeholder="请选择数据归属项目"
              clearable
              filterable
              style="width: 280px"
              @change="onExtractProjectChange"
            >
              <el-option
                v-for="p in projects"
                :key="p.id"
                :label="`${p.code || ''} ${p.name || ''}`.trim()"
                :value="p.id"
              />
            </el-select>
            <span class="ees-hint">用于生成数据批次的项目归属与数据权限判定。</span>
          </el-form-item>
          <el-form-item label="校验依据的指标体系版本">
            <el-select
              v-model="extractForm.targetVersionId"
              placeholder="请选择指标体系版本"
              clearable
              filterable
              style="width: 320px"
            >
              <el-option
                v-for="v in versions"
                :key="v.id"
                :label="`${v.versionCode || ''} ${v.versionName || ''}`.trim()"
                :value="v.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="抽取 SQL">
            <el-input
              v-model="extractForm.querySql"
              type="textarea"
              :rows="7"
              :placeholder="SQL_EXAMPLE"
              class="sql-textarea"
            />
            <div class="ees-hint">示例（请按实际业务表替换表名与字段）：{{ SQL_EXAMPLE }}</div>
          </el-form-item>
        </el-form>

        <el-alert
          v-if="sqlCheck"
          :type="sqlCheck.valid ? 'success' : 'error'"
          :closable="false"
          show-icon
          :title="sqlCheck.valid ? 'SQL 校验通过（只读）' : 'SQL 校验未通过'"
          :description="sqlCheck.message || ''"
        />
        <div v-if="sqlCheck && sqlCheck.valid && sqlCheck.normalizedSql" class="ees-hint">
          规范化语句：<span class="ees-mono">{{ sqlCheck.normalizedSql }}</span>
        </div>

        <div v-if="extractResult" class="extract-result">
          <el-divider content-position="left">抽取结果</el-divider>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="数据批次">
              <span class="ees-mono">{{ extractResult.batchCode || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="抽取行数">{{ fmtNum(extractResult.extractedRows, 0) }}</el-descriptions-item>
            <el-descriptions-item label="是否被截断">
              <el-tag :type="extractResult.truncated ? 'warning' : 'success'" size="small" effect="plain">
                {{ extractResult.truncated ? '已截断' : '完整' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="批次状态">{{ batchStatusLabel(extractResult.status) }}</el-descriptions-item>
            <el-descriptions-item label="有效行数">{{ fmtNum(extractResult.validRows, 0) }}</el-descriptions-item>
            <el-descriptions-item label="错误行数">{{ fmtNum(extractResult.errorRows, 0) }}</el-descriptions-item>
            <el-descriptions-item label="返回列" :span="3">
              <span class="ees-mono">{{ (extractResult.headers || []).join(', ') || '-' }}</span>
            </el-descriptions-item>
          </el-descriptions>
          <el-alert
            type="success"
            :closable="false"
            show-icon
            title="已生成数据批次"
            description="抽取结果已登记为一个数据批次并完成质量校验，可继续查看质量报告与错误明细，随后生成数据快照。"
          />
          <el-link type="primary" :underline="false" href="#/data-batches">前往数据批次查看质量报告 →</el-link>

          <div class="result-title">数据预览（最多 20 行）</div>
          <el-table :data="extractResult.preview || []" border size="small" max-height="300" empty-text="没有可预览的数据">
            <el-table-column
              v-for="h in extractResult.headers || []"
              :key="h"
              :label="h"
              min-width="130"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{ row[h] ?? '-' }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="extractVisible = false">关闭</el-button>
        <el-button :loading="sqlChecking" :disabled="extracting" @click="validateSql">校验 SQL</el-button>
        <el-button v-if="canExtract" type="primary" :loading="extracting" @click="submitExtract">执行抽取</el-button>
      </template>
    </el-dialog>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新建数据源' : '编辑数据源'" width="720px" top="6vh">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="150px">
        <el-form-item label="数据源编码" prop="sourceCode">
          <el-input v-model="form.sourceCode" placeholder="留空则由系统自动生成，例如 DS-20260101-0001" />
        </el-form-item>
        <el-form-item label="数据源名称" prop="sourceName">
          <el-input v-model="form.sourceName" maxlength="200" show-word-limit placeholder="例如：甲方试验数据中心库" />
        </el-form-item>
        <el-form-item label="数据库类型" prop="dbType">
          <el-select v-model="form.dbType" placeholder="请选择数据库类型" style="width: 280px" @change="onDbTypeChange">
            <el-option v-for="t in dbTypes" :key="t.dbType" :label="t.label" :value="t.dbType" />
          </el-select>
        </el-form-item>
        <el-form-item label="驱动类" prop="driverClass">
          <el-input v-model="form.driverClass" placeholder="选择数据库类型后自动填充，可手工调整" />
          <div class="ees-hint">驱动需随部署包一并提供，离线环境不下载任何外部依赖。</div>
        </el-form-item>
        <el-form-item label="JDBC 地址" prop="jdbcUrl">
          <el-input v-model="form.jdbcUrl" placeholder="例如：jdbc:mysql://host:3306/db?useSSL=false" />
          <div v-if="currentDbType" class="ees-hint">示例：{{ currentDbType.urlExample }}</div>
        </el-form-item>
        <el-form-item label="只读账号" prop="dbUsername">
          <el-input v-model="form.dbUsername" autocomplete="off" placeholder="仅具备 SELECT 权限的账号" />
        </el-form-item>
        <el-form-item label="账号密码" prop="dbPassword">
          <el-input
            v-model="form.dbPassword"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="formMode === 'create' ? '请输入数据库密码' : '保持 ****** 不变即可沿用原密码'"
          />
          <div class="ees-hint">密码加密存储且不回显；编辑时若不修改密码，请勿改动此处的 ******。</div>
        </el-form-item>
        <el-form-item label="目标项目" prop="projectId">
          <el-select v-model="form.projectId" placeholder="请选择项目" clearable filterable style="width: 280px">
            <el-option v-for="p in projects" :key="p.id" :label="`${p.code || ''} ${p.name || ''}`.trim()" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="行数上限" prop="maxRows">
          <el-input-number v-model="form.maxRows" :min="1" :max="1000000" :step="1000" controls-position="right" />
          <div class="ees-hint">单次抽取允许返回的最大行数，超出部分会被截断并给出提示。</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { dataSourceApi, indicatorApi, projectApi } from '@/api'
import { useUserStore } from '@/stores/user'
import {
  BATCH_STATUS_LABEL,
  fmtDateTime,
  fmtDuration,
  fmtNum
} from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'

/** ---------------- 类型 ---------------- */
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

interface DbTypeOption {
  dbType: string
  label: string
  driverClass: string
  urlExample: string
}

interface DataSourceRow {
  id: number
  sourceCode?: string
  sourceName?: string
  dbType?: string
  jdbcUrl?: string
  driverClass?: string
  dbUsername?: string
  projectId?: number | null
  readOnly?: boolean
  maxRows?: number
  status?: string
  lastTestAt?: string
  lastTestResult?: string
  remark?: string
}

interface TestResult {
  success?: boolean
  productName?: string
  productVersion?: string
  driverName?: string
  driverVersion?: string
  url?: string
  readOnly?: boolean
  costMs?: number
  message?: string
}

interface FieldRow {
  columnName?: string
  dataType?: string
  columnSize?: number
  nullable?: boolean
  columnComment?: string
  sampleValue?: string | number | null
}

interface SqlCheck {
  valid?: boolean
  normalizedSql?: string
  message?: string
}

interface ExtractResult {
  dataSourceId?: number
  batchId?: number
  batchCode?: string
  headers?: string[]
  extractedRows?: number
  truncated?: boolean
  maxRows?: number
  status?: string
  validRows?: number
  errorRows?: number
  preview?: Array<Record<string, any>>
}

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

interface SourceForm {
  sourceCode: string
  sourceName: string
  dbType: string
  jdbcUrl: string
  driverClass: string
  dbUsername: string
  dbPassword: string
  maxRows: number
  projectId: number | null
  remark: string
}

/** ---------------- 权限 ---------------- */
const userStore = useUserStore()
const canCreate = computed(() => userStore.has('data:source:create'))
const canUpdate = computed(() => userStore.has('data:source:update'))
const canTest = computed(() => userStore.has('data:source:test'))
const canExtract = computed(() => userStore.has('data:source:extract'))

/** ---------------- 列表 ---------------- */
const loading = ref(false)
const rows = ref<DataSourceRow[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const query = reactive<{ keyword: string; dbType: string }>({ keyword: '', dbType: '' })

const dbTypes = ref<DbTypeOption[]>([])
const projects = ref<ProjectOption[]>([])
const versions = ref<VersionOption[]>([])

const SQL_EXAMPLE = `SELECT object_code, object_name, data_time, indicator_code, indicator_value, unit
FROM fact_test_metric
WHERE data_time >= '2026-01-01'`

const STATUS_META: Record<string, { label: string; type: TagType }> = {
  UNKNOWN: { label: '未测试', type: 'info' },
  OK: { label: '连接正常', type: 'success' },
  FAIL: { label: '连接失败', type: 'danger' }
}

function statusMeta(status?: string): { label: string; type: TagType } {
  return STATUS_META[String(status || 'UNKNOWN').toUpperCase()] || STATUS_META.UNKNOWN
}

function dbTypeLabel(dbType?: string): string {
  if (!dbType) return '-'
  const hit = dbTypes.value.find((t) => t.dbType === dbType)
  return hit ? hit.label : dbType
}

function readOnlyText(v?: boolean): string {
  if (v === true) return '是'
  if (v === false) return '否'
  return '未知'
}

function batchStatusLabel(status?: string): string {
  if (!status) return '-'
  return BATCH_STATUS_LABEL[status] || status
}

function errMsg(e: unknown): string {
  if (e instanceof Error && e.message) return e.message
  return String(e ?? '操作失败')
}

async function load() {
  loading.value = true
  try {
    const res = await dataSourceApi.list({
      keyword: query.keyword || undefined,
      dbType: query.dbType || undefined,
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
  query.keyword = ''
  query.dbType = ''
  search()
}

async function loadDbTypes() {
  try {
    dbTypes.value = (await dataSourceApi.dbTypes()) || []
  } catch {
    dbTypes.value = []
  }
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

/** ---------------- 连接测试 ---------------- */
const testVisible = ref(false)
const testLoading = ref(false)
const testResult = ref<TestResult | null>(null)
const testTarget = ref<DataSourceRow | null>(null)

async function openTest(row: DataSourceRow) {
  testTarget.value = row
  testResult.value = null
  testVisible.value = true
  testLoading.value = true
  try {
    const res = (await dataSourceApi.test(row.id)) || { success: false, message: '未返回测试结果' }
    const result: TestResult = res
    testResult.value = result
    if (result.success) ElMessage.success(result.message || '连接成功')
    else ElMessage.error(result.message || '连接失败')
    await load()
  } catch (e) {
    testResult.value = { success: false, message: errMsg(e) }
  } finally {
    testLoading.value = false
  }
}

/** ---------------- 表清单 / 字段预览 ---------------- */
const tablesVisible = ref(false)
const tablesLoading = ref(false)
const tables = ref<string[]>([])
const selectedTable = ref<string>('')
const fields = ref<FieldRow[]>([])
const fieldsLoading = ref(false)
const tablesTarget = ref<DataSourceRow | null>(null)

async function openTables(row: DataSourceRow) {
  tablesTarget.value = row
  tables.value = []
  selectedTable.value = ''
  fields.value = []
  tablesVisible.value = true
  tablesLoading.value = true
  try {
    tables.value = (await dataSourceApi.tables(row.id)) || []
    if (!tables.value.length) ElMessage.warning('该数据源下未读取到任何表或视图')
  } catch {
    tables.value = []
  } finally {
    tablesLoading.value = false
  }
}

async function onTableChange(table?: string) {
  fields.value = []
  const target = tablesTarget.value
  if (!target || !table) return
  fieldsLoading.value = true
  try {
    fields.value = (await dataSourceApi.fields(target.id, table)) || []
  } catch {
    fields.value = []
  } finally {
    fieldsLoading.value = false
  }
}

/** ---------------- 数据抽取 ---------------- */
const extractVisible = ref(false)
const extracting = ref(false)
const sqlChecking = ref(false)
const sqlCheck = ref<SqlCheck | null>(null)
const extractResult = ref<ExtractResult | null>(null)
const extractTarget = ref<DataSourceRow | null>(null)
const extractForm = reactive<{ projectId: number | null; targetVersionId: number | null; querySql: string }>({
  projectId: null,
  targetVersionId: null,
  querySql: ''
})

async function openExtract(row: DataSourceRow) {
  extractTarget.value = row
  extractForm.projectId = row.projectId ?? null
  extractForm.targetVersionId = null
  extractForm.querySql = ''
  sqlCheck.value = null
  extractResult.value = null
  extractVisible.value = true
  await loadVersions(extractForm.projectId)
}

async function onExtractProjectChange(projectId?: number | null) {
  extractForm.targetVersionId = null
  await loadVersions(projectId ?? null)
}

async function validateSql() {
  if (!extractForm.querySql.trim()) {
    ElMessage.warning('请先填写抽取 SQL')
    return
  }
  sqlChecking.value = true
  try {
    sqlCheck.value = (await dataSourceApi.validateSql(extractForm.querySql.trim())) || null
    if (sqlCheck.value?.valid) ElMessage.success(sqlCheck.value.message || 'SQL 校验通过')
    else ElMessage.error(sqlCheck.value?.message || 'SQL 校验未通过')
  } catch (e) {
    sqlCheck.value = { valid: false, message: errMsg(e) }
  } finally {
    sqlChecking.value = false
  }
}

async function submitExtract() {
  const target = extractTarget.value
  if (!target) return
  if (!extractForm.querySql.trim()) {
    ElMessage.warning('请先填写抽取 SQL')
    return
  }
  extracting.value = true
  try {
    const res = await dataSourceApi.extract(target.id, {
      querySql: extractForm.querySql.trim(),
      projectId: extractForm.projectId ?? undefined,
      targetVersionId: extractForm.targetVersionId ?? undefined
    })
    extractResult.value = res || null
    if (extractResult.value?.truncated) {
      ElMessage.warning(`抽取完成，已达到行数上限并截断，批次号：${extractResult.value.batchCode || '-'}`)
    } else {
      ElMessage.success(`抽取完成，批次号：${extractResult.value?.batchCode || '-'}`)
    }
    await load()
  } catch {
    // 错误提示由统一请求层给出
  } finally {
    extracting.value = false
  }
}

/** ---------------- 新建 / 编辑 ---------------- */
const formVisible = ref(false)
const saving = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<SourceForm>({
  sourceCode: '',
  sourceName: '',
  dbType: 'MYSQL',
  jdbcUrl: '',
  driverClass: '',
  dbUsername: '',
  dbPassword: '',
  maxRows: 100000,
  projectId: null,
  remark: ''
})

const currentDbType = computed(() => dbTypes.value.find((t) => t.dbType === form.dbType) || null)

const formRules = computed<FormRules>(() => ({
  sourceName: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  dbType: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  jdbcUrl: [{ required: true, message: '请输入 JDBC 连接地址', trigger: 'blur' }],
  dbUsername: [{ required: true, message: '请输入只读账号', trigger: 'blur' }],
  dbPassword:
    formMode.value === 'create'
      ? [{ required: true, message: '请输入数据库密码', trigger: 'blur' }]
      : []
}))

function resetForm() {
  form.sourceCode = ''
  form.sourceName = ''
  form.dbType = dbTypes.value[0]?.dbType || 'MYSQL'
  form.jdbcUrl = ''
  form.driverClass = dbTypes.value[0]?.driverClass || ''
  form.dbUsername = ''
  form.dbPassword = ''
  form.maxRows = 100000
  form.projectId = null
  form.remark = ''
}

function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  resetForm()
  formVisible.value = true
  formRef.value?.clearValidate()
}

async function openEdit(row: DataSourceRow) {
  formMode.value = 'edit'
  editingId.value = row.id
  resetForm()
  form.sourceCode = row.sourceCode || ''
  form.sourceName = row.sourceName || ''
  form.dbType = row.dbType || form.dbType
  form.jdbcUrl = row.jdbcUrl || ''
  form.driverClass = row.driverClass || currentDbType.value?.driverClass || ''
  form.dbUsername = row.dbUsername || ''
  // 列表接口不回显密码，统一使用掩码；保持 ****** 不变即沿用原密码
  form.dbPassword = '******'
  form.maxRows = Number(row.maxRows ?? 100000)
  form.projectId = row.projectId ?? null
  form.remark = row.remark || ''
  formVisible.value = true
  formRef.value?.clearValidate()
  try {
    const detail = await dataSourceApi.get(row.id)
    if (detail) {
      form.sourceCode = detail.sourceCode || form.sourceCode
      form.sourceName = detail.sourceName || form.sourceName
      form.dbType = detail.dbType || form.dbType
      form.jdbcUrl = detail.jdbcUrl || form.jdbcUrl
      form.driverClass = detail.driverClass || form.driverClass
      form.dbUsername = detail.dbUsername || form.dbUsername
      form.maxRows = Number(detail.maxRows ?? form.maxRows)
      form.projectId = detail.projectId ?? form.projectId
      form.remark = detail.remark ?? form.remark
      const masked = detail.dbPassword
      form.dbPassword = masked && masked !== '' ? String(masked) : '******'
    }
  } catch {
    // 详情读取失败时沿用列表数据
  }
}

function onDbTypeChange(dbType?: string) {
  const hit = dbTypes.value.find((t) => t.dbType === dbType)
  if (hit && hit.driverClass) form.driverClass = hit.driverClass
  if (hit) form.jdbcUrl = ''
}

async function submitForm() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  const payload = {
    sourceCode: form.sourceCode.trim() || undefined,
    sourceName: form.sourceName.trim(),
    dbType: form.dbType,
    jdbcUrl: form.jdbcUrl.trim(),
    driverClass: form.driverClass.trim() || undefined,
    dbUsername: form.dbUsername.trim(),
    dbPassword: form.dbPassword,
    projectId: form.projectId ?? undefined,
    maxRows: form.maxRows,
    remark: form.remark.trim() || undefined
  }
  try {
    if (formMode.value === 'create') {
      await dataSourceApi.create(payload)
      ElMessage.success('数据源创建成功')
    } else if (editingId.value != null) {
      await dataSourceApi.update(editingId.value, payload)
      ElMessage.success('数据源更新成功')
    }
    formVisible.value = false
    await load()
  } catch {
    // 错误提示由统一请求层给出
  } finally {
    saving.value = false
  }
}

async function remove(row: DataSourceRow) {
  try {
    await ElMessageBox.confirm(
      `确定要删除数据源「${row.sourceName || row.sourceCode || row.id}」吗？删除后该数据源的字段缓存会被清理，已抽取的数据批次不受影响。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await dataSourceApi.remove(row.id)
    ElMessage.success('数据源已删除')
    if (rows.value.length <= 1 && page.value > 1) page.value -= 1
    await load()
  } catch {
    // 错误提示由统一请求层给出
  }
}

onMounted(async () => {
  await Promise.all([loadDbTypes(), loadProjects()])
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

.op-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 6px;
}

.op-cell :deep(.el-button + .el-button) {
  margin-left: 0;
}

.dialog-body {
  max-height: 66vh;
  overflow-y: auto;
  padding-right: 4px;
}

.dialog-body :deep(.el-descriptions) {
  margin-top: 12px;
}

.extract-form {
  margin-top: 12px;
}

.sql-textarea :deep(.el-textarea__inner) {
  font-family: Consolas, "Courier New", monospace;
  font-size: 12.5px;
  line-height: 1.7;
}

.extract-result {
  margin-top: 12px;
}

.extract-result :deep(.el-alert) {
  margin-top: 10px;
}

.result-title {
  margin: 12px 0 8px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ees-text);
}
</style>
