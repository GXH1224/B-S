<template>
  <div class="ees-page">
    <PageHeader
      title="角色权限"
      desc="角色按授权访问页面和项目数据；结果、数据源和项目同时按角色与项目范围授权控制"
    />

    <el-row :gutter="14">
      <!-- 角色列表 -->
      <el-col :xs="24" :lg="11">
        <div class="ees-card">
          <div class="card-title">角色列表</div>
          <el-table
            v-loading="loadingRoles"
            :data="roles"
            border
            stripe
            size="default"
            highlight-current-row
            :current-row-key="selectedCode"
            row-key="roleCode"
            @row-click="selectRole"
          >
            <template #empty>
              <div class="ees-empty">暂无角色数据</div>
            </template>

            <el-table-column label="角色编码" min-width="130">
              <template #default="{ row }">
                <span class="ees-mono">{{ row.roleCode || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="角色名称" min-width="110">
              <template #default="{ row }">{{ row.roleName || '-' }}</template>
            </el-table-column>
            <el-table-column label="说明" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row.description || '-' }}</template>
            </el-table-column>
            <el-table-column label="数据范围" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="scopeType(row.dataScope)" size="small" effect="plain">
                  {{ scopeLabel(row.dataScope) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="用户数" width="80" align="center">
              <template #default="{ row }">{{ Number(row.userCount || 0) }}</template>
            </el-table-column>
            <el-table-column label="内置" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.builtIn ? 'info' : 'success'" size="small" effect="plain">
                  {{ row.builtIn ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="selectRole(row)">
                  查看权限
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <!-- 权限明细 -->
      <el-col :xs="24" :lg="13">
        <div v-loading="loadingDetail" class="ees-card permission-panel">
          <template v-if="selectedRole">
            <div class="card-title">
              权限明细
              <span class="head-role">
                {{ selectedRole.roleName || selectedRole.roleCode }}
                <span class="ees-mono head-role-code">{{ selectedRole.roleCode }}</span>
              </span>
              <el-tag size="small" effect="plain">{{ scopeLabel(selectedRole.dataScope) }}</el-tag>
            </div>

            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="以下权限为只读展示"
              description="角色权限由系统预置，如需调整请通过变更管理流程"
              style="margin-bottom: 12px"
            />

            <el-alert
              v-if="hasAll"
              type="success"
              :closable="false"
              show-icon
              title="全部权限（*）"
              description="该角色拥有通配权限，可访问系统全部页面与操作。"
              style="margin-bottom: 12px"
            />

            <div class="section-title">页面域访问范围</div>
            <el-table :data="domainRows" border size="small" class="domain-table">
              <el-table-column label="页面域" width="120">
                <template #default="{ row }">{{ row.name }}</template>
              </el-table-column>
              <el-table-column label="是否可访问" width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.allowed ? 'success' : 'info'" size="small">
                    {{ row.allowed ? '允许' : '不允许' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="依据权限" min-width="220">
                <template #default="{ row }">
                  <span v-if="row.codes.length" class="ees-mono code-list">
                    {{ row.codes.join('、') }}
                  </span>
                  <span v-else class="ees-hint">无相关权限</span>
                </template>
              </el-table-column>
            </el-table>

            <div class="section-title">
              权限清单
              <span class="ees-hint">
                已授权 {{ grantedCount }} 项 / 共 {{ totalCodeCount }} 项（只读）
              </span>
            </div>

            <div v-if="!groupedPermissions.length" class="ees-empty">暂无权限字典数据</div>
            <div v-else class="perm-groups">
              <div v-for="group in groupedPermissions" :key="group.key" class="perm-group">
                <div class="perm-group-head">
                  <span class="perm-group-title">{{ group.title }}</span>
                  <span class="ees-mono perm-group-prefix">{{ group.key }}:</span>
                  <span class="ees-hint">
                    {{ group.items.filter((i) => i.granted).length }} / {{ group.items.length }}
                  </span>
                </div>
                <div class="perm-items">
                  <el-checkbox
                    v-for="item in group.items"
                    :key="item.code"
                    :model-value="item.granted"
                    disabled
                    class="perm-item"
                  >
                    <span class="perm-label">{{ item.label }}</span>
                    <span class="ees-mono perm-code">{{ item.code }}</span>
                  </el-checkbox>
                </div>
              </div>
            </div>
          </template>

          <div v-else class="ees-empty">请在左侧选择一个角色以查看其权限范围</div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { systemApi, userApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'

interface RoleRow {
  id?: number
  roleCode: string
  roleName?: string
  description?: string
  dataScope?: string
  builtIn?: boolean
  userCount?: number
}

interface PermissionDictItem {
  code: string
  label?: string
}

interface PermissionGroup {
  key: string
  title: string
  items: { code: string; label: string; granted: boolean }[]
}

interface DomainRow {
  name: string
  allowed: boolean
  codes: string[]
}

const SCOPE_LABEL: Record<string, string> = {
  ALL: '全部数据',
  PROJECT: '项目范围',
  SELF: '仅本人'
}

const SCOPE_TYPE: Record<string, 'success' | 'warning' | 'info'> = {
  ALL: 'success',
  PROJECT: 'warning',
  SELF: 'info'
}

/** 模块前缀中文名（与权限字典中的 code 前缀一致） */
const GROUP_LABEL: Record<string, string> = {
  dashboard: '综合驾驶舱',
  project: '项目管理',
  indicator: '指标体系',
  data: '数据接入',
  task: '评估任务',
  result: '评估结果',
  report: '报告导出',
  baseline: '算法基准',
  system: '系统管理'
}

const GROUP_ORDER = [
  'dashboard',
  'project',
  'indicator',
  'data',
  'task',
  'result',
  'report',
  'baseline',
  'system'
]

/** 四个页面域由权限前缀推导 */
const DOMAIN_DEFS: { name: string; prefixes: string[] }[] = [
  { name: '综合驾驶舱', prefixes: ['dashboard:'] },
  { name: '评估工作台', prefixes: ['task:', 'indicator:', 'data:'] },
  { name: '结果分析', prefixes: ['result:', 'report:'] },
  { name: '管理后台', prefixes: ['system:'] }
]

const loadingRoles = ref(false)
const loadingDetail = ref(false)
const roles = ref<RoleRow[]>([])
const dict = ref<PermissionDictItem[]>([])
const selectedCode = ref<string>('')
const grantedCodes = ref<string[]>([])

const selectedRole = computed<RoleRow | null>(
  () => roles.value.find((r) => r.roleCode === selectedCode.value) || null
)

const hasAll = computed(() => grantedCodes.value.includes('*'))

const grantedCount = computed(() =>
  hasAll.value ? totalCodeCount.value : grantedCodes.value.filter((c) => c && c !== '*').length
)

/** 权限字典与角色实际授权码的并集，避免遗漏字典之外的授权项 */
const allCodes = computed<string[]>(() => {
  const set = new Set<string>()
  for (const d of dict.value) {
    if (d?.code) set.add(d.code)
  }
  for (const c of grantedCodes.value) {
    if (c && c !== '*') set.add(c)
  }
  return Array.from(set)
})

const totalCodeCount = computed(() => allCodes.value.length)

const labelMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  for (const d of dict.value) {
    if (d?.code) map[d.code] = d.label || d.code
  }
  return map
})

const groupedPermissions = computed<PermissionGroup[]>(() => {
  const buckets: Record<string, { code: string; label: string; granted: boolean }[]> = {}
  for (const code of allCodes.value) {
    const prefix = code.includes(':') ? code.slice(0, code.indexOf(':')) : 'other'
    if (!buckets[prefix]) buckets[prefix] = []
    buckets[prefix].push({
      code,
      label: labelMap.value[code] || code,
      granted: hasAll.value || grantedCodes.value.includes(code)
    })
  }
  const out: PermissionGroup[] = []
  for (const key of GROUP_ORDER) {
    const items = buckets[key]
    if (items && items.length) out.push({ key, title: GROUP_LABEL[key] || key, items })
  }
  for (const key of Object.keys(buckets)) {
    if (!GROUP_ORDER.includes(key)) {
      out.push({ key, title: GROUP_LABEL[key] || key, items: buckets[key] })
    }
  }
  return out
})

const domainRows = computed<DomainRow[]>(() =>
  DOMAIN_DEFS.map((d) => {
    const codes = allCodes.value.filter((code) =>
      d.prefixes.some((prefix) => code.startsWith(prefix))
    )
    return {
      name: d.name,
      allowed: hasAll.value || codes.length > 0,
      codes: hasAll.value ? ['*'] : codes
    }
  })
)

function scopeLabel(scope?: string): string {
  if (!scope) return '未配置'
  return SCOPE_LABEL[scope] || scope
}

function scopeType(scope?: string): 'success' | 'warning' | 'info' {
  if (!scope) return 'info'
  return SCOPE_TYPE[scope] || 'info'
}

async function loadRoles() {
  loadingRoles.value = true
  try {
    const list = await userApi.roles()
    roles.value = Array.isArray(list) ? (list as RoleRow[]) : []
    if (roles.value.length && !selectedCode.value) {
      await selectRole(roles.value[0])
    }
  } catch {
    roles.value = []
  } finally {
    loadingRoles.value = false
  }
}

async function loadDict() {
  try {
    const list = await systemApi.permissions()
    dict.value = Array.isArray(list) ? (list as PermissionDictItem[]) : []
  } catch {
    dict.value = []
  }
}

async function selectRole(row: RoleRow) {
  if (!row?.roleCode) return
  selectedCode.value = row.roleCode
  grantedCodes.value = []
  loadingDetail.value = true
  try {
    const res = await userApi.rolePermissions(row.roleCode)
    const list = res?.permissions
    grantedCodes.value = Array.isArray(list)
      ? (list as any[]).map((p) => String(p)).filter((p) => !!p)
      : []
  } catch {
    grantedCodes.value = []
  } finally {
    loadingDetail.value = false
  }
}

onMounted(async () => {
  await loadDict()
  await loadRoles()
})
</script>

<style scoped>
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14.5px;
  font-weight: 600;
  color: var(--ees-text);
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.head-role {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ees-primary);
}

.head-role-code {
  color: var(--ees-text-muted);
}

.permission-panel {
  min-height: 320px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ees-text);
  margin: 14px 0 8px;
  flex-wrap: wrap;
}

.domain-table {
  margin-bottom: 4px;
}

.code-list {
  color: var(--ees-text-secondary);
  word-break: break-all;
}

.perm-groups {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 460px;
  overflow-y: auto;
  padding-right: 4px;
}

.perm-group {
  border: 1px solid var(--ees-border);
  border-radius: var(--ees-radius-sm);
  padding: 10px 12px;
  background: #fbfcff;
}

.perm-group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.perm-group-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ees-text);
}

.perm-group-prefix {
  color: var(--ees-text-muted);
}

.perm-items {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(238px, 1fr));
  gap: 2px 10px;
}

.perm-item {
  height: 26px;
}

.perm-label {
  color: var(--ees-text-secondary);
}

.perm-code {
  color: var(--ees-text-muted);
  margin-left: 6px;
}
</style>
