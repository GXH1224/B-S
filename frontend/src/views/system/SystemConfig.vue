<template>
  <div class="ees-page">
    <PageHeader
      title="系统参数"
      desc="参数可在页面维护；算法阈值、缺失值策略、导出上限等从数据库读取，避免硬编码"
    >
      <template #actions>
        <el-tag v-if="dirtyItems.length" type="warning" effect="plain" size="small">
          {{ dirtyItems.length }} 项待保存
        </el-tag>
        <el-button type="primary" :loading="savingAll" :disabled="!dirtyItems.length" @click="saveAll">
          全部保存
        </el-button>
        <el-button :loading="loading" @click="reload">刷新</el-button>
      </template>
    </PageHeader>

    <div v-loading="loading">
      <div v-if="!groups.length && !loading" class="ees-card">
        <div class="ees-empty">暂无系统参数数据</div>
      </div>

      <div v-for="group in groups" :key="group.code" class="ees-card">
        <div class="card-title">
          <span>{{ group.title }}</span>
          <span class="ees-mono group-code">{{ group.code }}</span>
          <span class="ees-hint">{{ group.items.length }} 项</span>
        </div>

        <div v-if="!group.items.length" class="ees-empty">该分组暂无参数</div>

        <div v-else class="cfg-list">
          <div
            v-for="item in group.items"
            :key="item.key"
            class="cfg-row"
            :class="{ 'cfg-row--locked': !item.editable }"
          >
            <div class="cfg-label">
              <div class="cfg-desc">
                <span>{{ item.description || item.key }}</span>
                <el-tag v-if="!item.editable" type="warning" size="small" effect="plain">
                  系统保留项
                </el-tag>
                <el-tag v-else-if="isDirty(item)" type="primary" size="small" effect="plain">
                  未保存
                </el-tag>
              </div>
              <div class="ees-mono cfg-key">{{ item.key }}</div>
            </div>

            <div class="cfg-control">
              <el-input v-if="!item.editable" :model-value="item.value" disabled style="width: 240px" />
              <el-switch
                v-else-if="item.kind === 'boolean'"
                v-model="item.value"
                active-value="true"
                inactive-value="false"
                active-text="是"
                inactive-text="否"
              />
              <el-input-number
                v-else-if="item.kind === 'number'"
                :model-value="toNumber(item.value)"
                :step="1"
                controls-position="right"
                style="width: 180px"
                @update:model-value="(v: any) => setNumber(item, v)"
              />
              <el-input v-else v-model="item.value" style="width: 320px" />
            </div>

            <div class="cfg-action">
              <el-button
                v-if="item.editable"
                type="primary"
                size="small"
                plain
                :disabled="!isDirty(item)"
                :loading="savingKeys.has(item.key)"
                @click="saveItem(item)"
              >
                保存
              </el-button>
              <span v-else class="ees-hint">只读</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 数据质量规则 -->
    <div class="ees-card">
      <div class="card-title">
        <span>数据质量规则</span>
        <span class="ees-mono group-code">data_quality_rule</span>
        <span class="ees-hint">{{ rules.length }} 条</span>
      </div>

      <div class="ees-hint hint-line">
        阻断级（阻止）规则会导致校验不通过并阻止任务提交，需要先修正数据；告警级（告警 / 提示）只提示，不阻断后续流程。
        修改严重级别或启用状态后立即生效。
      </div>

      <el-table v-loading="rulesLoading" :data="rules" border stripe size="default">
        <template #empty>
          <div class="ees-empty">暂无数据质量规则</div>
        </template>

        <el-table-column label="规则编码" min-width="130">
          <template #default="{ row }">
            <span class="ees-mono">{{ row.ruleCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="规则名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ruleName || '-' }}</template>
        </el-table-column>
        <el-table-column label="规则类型" width="110">
          <template #default="{ row }">{{ ruleTypeLabel(row.ruleType) }}</template>
        </el-table-column>
        <el-table-column label="目标字段" min-width="140">
          <template #default="{ row }">
            <span class="ees-mono">{{ row.targetField || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="严重级别" width="130">
          <template #default="{ row }">
            <el-select
              :model-value="row.severity || 'WARN'"
              size="small"
              style="width: 112px"
              @change="(v: any) => changeSeverity(row, v)"
            >
              <el-option
                v-for="s in SEVERITY_OPTIONS"
                :key="s.value"
                :label="s.label"
                :value="s.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled !== false"
              @change="(v: any) => changeEnabled(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { systemApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'

type ConfigKind = 'boolean' | 'number' | 'text'

interface ConfigItem {
  key: string
  id?: number
  /** 页面编辑值（后端以字符串保存） */
  value: string
  /** 后端原始值，用于判断是否有改动 */
  original: string
  description: string
  editable: boolean
  kind: ConfigKind
}

interface ConfigGroup {
  code: string
  title: string
  items: ConfigItem[]
}

interface QualityRuleRow {
  id: number
  ruleCode?: string
  ruleName?: string
  ruleType?: string
  targetField?: string
  severity?: string
  enabled?: boolean
  description?: string
}

const GROUP_TITLE: Record<string, string> = {
  SYSTEM: '系统信息',
  ALGORITHM: '算法参数',
  DATA: '数据策略',
  EXPORT: '导出设置',
  REPORT: '报告设置',
  SECURITY: '安全设置'
}

const GROUP_ORDER = ['SYSTEM', 'ALGORITHM', 'DATA', 'EXPORT', 'REPORT', 'SECURITY']

const SEVERITY_OPTIONS = [
  { value: 'BLOCK', label: '阻止' },
  { value: 'WARN', label: '告警' },
  { value: 'INFO', label: '提示' }
]

const SEVERITY_LABEL: Record<string, string> = {
  BLOCK: '阻止',
  WARN: '告警',
  INFO: '提示'
}

const RULE_TYPE_LABEL: Record<string, string> = {
  REQUIRED: '必填校验',
  RANGE: '范围校验',
  FORMAT: '格式校验',
  DIRECTION: '方向校验',
  DUPLICATE: '重复校验',
  MISSING: '缺失值校验',
  UNIT: '单位校验'
}

const loading = ref(false)
const savingAll = ref(false)
const savingKeys = ref<Set<string>>(new Set())
const groups = ref<ConfigGroup[]>([])
const rulesLoading = ref(false)
const rules = ref<QualityRuleRow[]>([])

const dirtyItems = computed<ConfigItem[]>(() => {
  const out: ConfigItem[] = []
  for (const group of groups.value) {
    for (const item of group.items) {
      if (item.editable && isDirty(item)) out.push(item)
    }
  }
  return out
})

function isDirty(item: ConfigItem): boolean {
  return item.value !== item.original
}

function detectKind(value: string): ConfigKind {
  const v = (value || '').trim().toLowerCase()
  if (v === 'true' || v === 'false') return 'boolean'
  if (/^-?\d+(\.\d+)?$/.test(v)) return 'number'
  return 'text'
}

function toNumber(value: string): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

function setNumber(item: ConfigItem, value: any) {
  if (value === null || value === undefined || value === '') {
    item.value = ''
    return
  }
  item.value = String(value)
}

function buildGroup(code: string, raw: Record<string, any>): ConfigGroup {
  const items: ConfigItem[] = []
  const entries = raw && typeof raw === 'object' ? Object.entries(raw) : []
  for (const [key, cfg] of entries) {
    const rawValue = cfg?.value
    const value = rawValue === null || rawValue === undefined ? '' : String(rawValue)
    items.push({
      key,
      id: cfg?.id,
      value,
      original: value,
      description: cfg?.description || key,
      editable: cfg?.editable !== false,
      kind: detectKind(value)
    })
  }
  return { code, title: GROUP_TITLE[code] || code, items }
}

async function loadConfigs() {
  loading.value = true
  try {
    const res = await systemApi.configs()
    const map: Record<string, Record<string, any>> = res && typeof res === 'object' ? res : {}
    const out: ConfigGroup[] = []
    for (const code of GROUP_ORDER) {
      const raw = map[code]
      if (raw && typeof raw === 'object') out.push(buildGroup(code, raw))
    }
    for (const code of Object.keys(map)) {
      const raw = map[code]
      if (!GROUP_ORDER.includes(code) && raw && typeof raw === 'object') {
        out.push(buildGroup(code, raw))
      }
    }
    groups.value = out
  } catch {
    groups.value = []
  } finally {
    loading.value = false
  }
}

function markSaving(key: string, on: boolean) {
  const next = new Set(savingKeys.value)
  if (on) next.add(key)
  else next.delete(key)
  savingKeys.value = next
}

async function saveItem(item: ConfigItem) {
  if (!item.editable || !isDirty(item) || savingKeys.value.has(item.key)) return
  markSaving(item.key, true)
  try {
    await systemApi.updateConfig(item.key, item.value)
    item.original = item.value
    ElMessage.success(`参数「${item.description || item.key}」已保存`)
  } catch {
    // 失败提示由请求拦截器统一展示
  } finally {
    markSaving(item.key, false)
  }
}

async function saveAll() {
  const list = dirtyItems.value
  if (!list.length) {
    ElMessage.info('没有需要保存的参数')
    return
  }
  savingAll.value = true
  let ok = 0
  let fail = 0
  for (const item of list) {
    try {
      await systemApi.updateConfig(item.key, item.value)
      item.original = item.value
      ok += 1
    } catch {
      fail += 1
    }
  }
  savingAll.value = false
  if (ok) {
    ElMessage.success(fail ? `已保存 ${ok} 项参数，${fail} 项保存失败` : `已保存 ${ok} 项参数`)
  }
}

async function reload() {
  await Promise.all([loadConfigs(), loadRules()])
}

/** ---------------- 数据质量规则 ---------------- */
function ruleTypeLabel(type?: string): string {
  if (!type) return '-'
  return RULE_TYPE_LABEL[type] || type
}

async function loadRules() {
  rulesLoading.value = true
  try {
    const list = await systemApi.qualityRules()
    rules.value = Array.isArray(list) ? (list as QualityRuleRow[]) : []
  } catch {
    rules.value = []
  } finally {
    rulesLoading.value = false
  }
}

async function changeSeverity(row: QualityRuleRow, value: any) {
  const next = String(value || '')
  if (!next || next === row.severity) return
  const prev = row.severity
  row.severity = next
  try {
    await systemApi.updateQualityRule(row.id, { severity: next, enabled: row.enabled !== false })
    ElMessage.success(
      `规则「${row.ruleName || row.ruleCode}」严重级别已更新为${SEVERITY_LABEL[next] || next}`
    )
  } catch {
    row.severity = prev
  }
}

async function changeEnabled(row: QualityRuleRow, value: any) {
  const next = value === true || value === 'true'
  if (next === (row.enabled !== false)) return
  const prev = row.enabled !== false
  row.enabled = next
  try {
    await systemApi.updateQualityRule(row.id, {
      severity: row.severity || 'WARN',
      enabled: next
    })
    ElMessage.success(`规则「${row.ruleName || row.ruleCode}」已${next ? '启用' : '停用'}`)
  } catch {
    row.enabled = prev
  }
}

onMounted(async () => {
  await loadConfigs()
  await loadRules()
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

.group-code {
  color: var(--ees-text-muted);
  font-weight: 400;
}

.hint-line {
  margin-bottom: 12px;
}

.cfg-list {
  display: flex;
  flex-direction: column;
}

.cfg-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 2px;
  border-bottom: 1px dashed var(--ees-border);
  flex-wrap: wrap;
}

.cfg-row:last-child {
  border-bottom: none;
}

.cfg-row--locked {
  background: #fafbfe;
}

.cfg-label {
  flex: 1 1 260px;
  min-width: 220px;
}

.cfg-desc {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  color: var(--ees-text);
  line-height: 1.5;
  flex-wrap: wrap;
}

.cfg-key {
  color: var(--ees-text-muted);
  margin-top: 2px;
}

.cfg-control {
  flex: 0 0 auto;
}

.cfg-action {
  flex: 0 0 auto;
  min-width: 72px;
  text-align: right;
}
</style>
