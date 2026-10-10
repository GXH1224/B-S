<template>
  <div class="ees-page">
    <PageHeader
      title="审计日志"
      desc="导出、发布、数据源测试、权限变更和算法参数变更全部纳入审计，日志不得由普通用户删除"
    >
      <template #actions>
        <el-button v-if="userStore.isSuperAdmin" type="danger" plain @click="openPurge">
          <el-icon><Delete /></el-icon>
          <span>清理过期日志</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="ees-card">
      <div class="ees-filter-bar">
        <el-input
          v-model="query.username"
          placeholder="用户名"
          clearable
          style="width: 160px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.module" placeholder="全部模块" clearable style="width: 150px">
          <el-option
            v-for="item in moduleOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="query.success" placeholder="全部结果" clearable style="width: 130px">
          <el-option label="成功" value="true" />
          <el-option label="失败" value="false" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          :default-time="defaultTime"
          style="width: 360px"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <div class="ees-hint hint-line">
        审计日志为追加写入（append-only），普通用户无法删除或修改任何记录；仅系统管理员可按保留天数清理过期日志。
      </div>

      <el-table v-loading="loading" :data="rows" border stripe size="default">
        <template #empty>
          <div class="ees-empty">暂无审计日志</div>
        </template>

        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="用户" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.username || 'anonymous' }}</template>
        </el-table-column>
        <el-table-column label="模块" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ moduleLabel(row.module) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.action || '-' }}</template>
        </el-table-column>
        <el-table-column label="请求方法" width="100" align="center">
          <template #default="{ row }">
            <span class="ees-mono">{{ row.httpMethod || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="请求路径" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ees-mono">{{ row.requestUri || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="IP" min-width="130">
          <template #default="{ row }">
            <span class="ees-mono">{{ row.ip || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success === false ? 'danger' : 'success'" size="small">
              {{ row.success === false ? '失败' : '成功' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100" align="right">
          <template #default="{ row }">
            <span class="ees-mono">{{ costText(row.costMs) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
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
          @size-change="handleSizeChange"
          @current-change="loadLogs"
        />
      </div>
    </div>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="审计日志详情" width="820px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-alert
            v-if="detail.errorMsg"
            type="error"
            :closable="false"
            show-icon
            :title="detail.errorMsg"
            style="margin-bottom: 12px"
          />

          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="时间">
              {{ fmtDateTime(detail.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="用户">
              {{ detail.username || 'anonymous' }}
              <span v-if="detail.userId !== null && detail.userId !== undefined" class="ees-hint">
                （ID {{ detail.userId }}）
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="模块">{{ moduleLabel(detail.module) }}</el-descriptions-item>
            <el-descriptions-item label="操作">{{ detail.action || '-' }}</el-descriptions-item>
            <el-descriptions-item label="请求方法">
              <span class="ees-mono">{{ detail.httpMethod || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="请求路径">
              <span class="ees-mono">{{ detail.requestUri || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="目标类型">
              {{ detail.targetType || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="目标ID">
              <span class="ees-mono">{{ detail.targetId || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="IP">
              <span class="ees-mono">{{ detail.ip || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="结果">
              <el-tag :type="detail.success === false ? 'danger' : 'success'" size="small">
                {{ detail.success === false ? '失败' : '成功' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="耗时">{{ costText(detail.costMs) }}</el-descriptions-item>
            <el-descriptions-item label="User-Agent" :span="2">
              <span class="ees-mono">{{ detail.userAgent || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="错误信息" :span="2">
              {{ detail.errorMsg || '无' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="detail-sub">请求明细（detailMap）</div>
          <pre v-if="detailText" class="ees-pre">{{ detailText }}</pre>
          <div v-else class="ees-empty">该记录未保存请求明细</div>
        </template>
        <div v-else-if="!detailLoading" class="ees-empty">未获取到审计详情</div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 清理过期日志（仅系统管理员） -->
    <el-dialog v-model="purgeVisible" title="清理过期审计日志" width="460px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="清理后不可恢复，请确认保留天数符合审计留存要求。"
        style="margin-bottom: 12px"
      />
      <el-form label-width="96px">
        <el-form-item label="保留天数">
          <el-input-number v-model="retentionDays" :min="1" :max="3650" :step="30" />
        </el-form-item>
      </el-form>
      <div class="ees-hint">将删除 {{ retentionDays }} 天之前的全部审计日志记录。</div>
      <template #footer>
        <el-button @click="purgeVisible = false">取消</el-button>
        <el-button type="danger" :loading="purging" @click="submitPurge">确认清理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import { useUserStore } from '@/stores/user'
import { AUDIT_MODULE_LABEL, fmtDateTime, parseJson } from '@/utils/format'

interface AuditRow {
  id: number
  userId?: number | null
  username?: string
  module?: string
  action?: string
  httpMethod?: string
  requestUri?: string
  targetType?: string
  targetId?: string
  ip?: string
  userAgent?: string
  success?: boolean
  errorMsg?: string
  costMs?: number
  createdAt?: string
}

const userStore = useUserStore()

const moduleOptions = computed(() =>
  Object.keys(AUDIT_MODULE_LABEL).map((key) => ({
    value: key,
    label: AUDIT_MODULE_LABEL[key] || key
  }))
)

/** 时间范围默认从 00:00:00 到 23:59:59 */
const defaultTime: [Date, Date] = [
  new Date(2000, 0, 1, 0, 0, 0),
  new Date(2000, 0, 1, 23, 59, 59)
]

const loading = ref(false)
const rows = ref<AuditRow[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const dateRange = ref<[Date, Date] | null>(null)
const query = reactive({ username: '', module: '', success: '' })

function moduleLabel(module?: string): string {
  if (!module) return '-'
  return AUDIT_MODULE_LABEL[module] || module
}

function costText(costMs?: number | null): string {
  if (costMs === null || costMs === undefined) return '-'
  const n = Number(costMs)
  return Number.isFinite(n) ? `${n} ms` : '-'
}

async function loadLogs() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: page.value, size: size.value }
    const username = query.username.trim()
    if (username) params.username = username
    if (query.module) params.module = query.module
    if (query.success === 'true' || query.success === 'false') {
      params.success = query.success === 'true'
    }
    const range = dateRange.value
    if (range && range.length === 2 && range[0] && range[1]) {
      params.from = fmtDateTime(range[0], 'YYYY-MM-DDTHH:mm:ss')
      params.to = fmtDateTime(range[1], 'YYYY-MM-DDTHH:mm:ss')
    }
    const res = await systemApi.auditLogs(params)
    rows.value = Array.isArray(res?.records) ? (res.records as AuditRow[]) : []
    total.value = Number(res?.total || 0)
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadLogs()
}

function handleReset() {
  query.username = ''
  query.module = ''
  query.success = ''
  dateRange.value = null
  page.value = 1
  loadLogs()
}

function handleSizeChange() {
  page.value = 1
  loadLogs()
}

/** ---------------- 详情 ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AuditRow | null>(null)
const detailMap = ref<Record<string, any> | null>(null)

const detailText = computed(() => {
  const map = detailMap.value
  if (!map || typeof map !== 'object') return ''
  const keys = Object.keys(map)
  if (!keys.length) return ''
  try {
    return JSON.stringify(map, null, 2)
  } catch {
    return String(map)
  }
})

function pickDetailMap(raw: any): Record<string, any> | null {
  const map = raw?.detailMap
  if (map && typeof map === 'object' && Object.keys(map).length) {
    return parseJson<Record<string, any>>(map, {})
  }
  if (typeof map === 'string' && map.trim()) {
    return parseJson<Record<string, any>>(map, {})
  }
  // 兼容直接返回日志实体 detail 字段的情况
  const text = raw?.log?.detail
  if (typeof text === 'string' && text.trim()) {
    return parseJson<Record<string, any>>(text, {})
  }
  return null
}

async function openDetail(row: AuditRow) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  detailMap.value = null
  try {
    const res = await systemApi.auditDetail(row.id)
    detail.value = (res?.log as AuditRow) || row
    detailMap.value = pickDetailMap(res)
  } catch {
    detail.value = row
    detailMap.value = null
  } finally {
    detailLoading.value = false
  }
}

/** ---------------- 清理过期日志 ---------------- */
const purgeVisible = ref(false)
const purging = ref(false)
const retentionDays = ref(365)

function openPurge() {
  retentionDays.value = 365
  purgeVisible.value = true
}

async function submitPurge() {
  const days = Number(retentionDays.value)
  if (!Number.isFinite(days) || days < 1) {
    ElMessage.warning('请输入有效的保留天数')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要清理 ${days} 天之前的审计日志吗？该操作不可恢复。`,
      '清理确认',
      { type: 'warning', confirmButtonText: '确定清理', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  purging.value = true
  try {
    const res: any = await systemApi.purgeAudit(days)
    const removed = Number(res?.removed || 0)
    ElMessage.success(`清理完成，共删除 ${removed} 条 ${days} 天前的审计日志`)
    purgeVisible.value = false
    page.value = 1
    await loadLogs()
  } catch {
    // 失败提示由请求拦截器统一展示
  } finally {
    purging.value = false
  }
}

onMounted(loadLogs)
</script>

<style scoped>
.hint-line {
  margin-bottom: 12px;
}

.detail-sub {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ees-text);
  margin: 14px 0 8px;
}
</style>
