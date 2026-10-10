<template>
  <div class="ees-page">
    <PageHeader
      title="算法基准测试"
      desc="使用标准样例验证算法实现与统一接口转换结果，浮点结果按绝对误差或相对误差判定，所有差异都有测试记录"
    >
      <template #actions>
        <el-button v-if="canRun" type="primary" :loading="runningAll" @click="runAll">
          <el-icon><VideoPlay /></el-icon>
          全部运行
        </el-button>
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="lastRunAll.length"
      class="summary-alert"
      :type="passRatio.type"
      :closable="false"
      show-icon
      :title="`最近一次全部运行：通过 ${passRatio.passed} / ${passRatio.total}`"
    >
      <div class="ees-hint">
        判定规则：按基准样例登记的绝对容差或相对容差比对，任一项在容差内即视为通过；全部差异均记录在下方明细中。
      </div>
    </el-alert>

    <div class="ees-card">
      <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无算法基准样例数据">
        <el-table-column prop="baselineCode" label="基准编号" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.baselineCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="baselineName" label="基准名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.baselineName || '-' }}</template>
        </el-table-column>
        <el-table-column label="算法" width="140">
          <template #default="{ row }">
            {{ ALGORITHM_LABEL[row?.algorithm] || row?.algorithm || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="绝对容差" width="120" align="right">
          <template #default="{ row }">
            <span class="ees-mono">{{ fmtNum(row?.absTolerance, 8) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="相对容差" width="120" align="right">
          <template #default="{ row }">
            <span class="ees-mono">{{ fmtNum(row?.relTolerance, 8) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row?.enabled ? 'success' : 'info'" effect="plain" size="small">
              {{ row?.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="最近运行时间" width="170">
          <template #default="{ row }">
            <span class="ees-mono">{{ fmtDateTime(row?.lastRunAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近运行结果" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="resultClass(row?.lastRunResult)">{{ row?.lastRunResult || '尚未运行' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canRun" link type="primary" size="small" @click="runOne(row)">运行</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 单个基准运行结果 -->
    <el-dialog v-model="runVisible" :title="`基准运行结果 - ${runResult?.baselineName || runBaselineName}`" width="900px">
      <div v-loading="running">
        <template v-if="runResult">
          <div class="run-head">
            <el-tag :type="runResult.passed ? 'success' : 'danger'" size="large" effect="dark">
              {{ runResult.passed ? '通过' : '未通过' }}
            </el-tag>
            <span class="run-code ees-mono">{{ runResult.baselineCode || runBaselineCode }}</span>
            <span class="ees-hint">运行结果：{{ runResult.result || (runResult.passed ? 'PASS' : 'FAIL') }}</span>
          </div>

          <el-descriptions :column="3" border size="small" class="run-desc">
            <el-descriptions-item label="算法">
              {{ ALGORITHM_LABEL[runResult.algorithm || ''] || runResult.algorithm || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="算法版本">
              <span class="ees-mono">{{ runResult.algorithmVersion || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="运行时间">{{ fmtDateTime(runResult.runAt) }}</el-descriptions-item>
            <el-descriptions-item label="绝对容差">
              <span class="ees-mono">{{ fmtNum(runResult.absTolerance, 8) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="相对容差">
              <span class="ees-mono">{{ fmtNum(runResult.relTolerance, 8) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="比对项数">{{ comparisons.length }}</el-descriptions-item>
          </el-descriptions>

          <div class="sub-title">逐项比对结果</div>
          <el-table :data="comparisons" border size="small" max-height="360" empty-text="无比对项">
            <el-table-column prop="field" label="比对字段" width="170" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="ees-mono">{{ row?.field || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="期望值" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="ees-mono">{{ fmtCell(row?.expected) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="实际值" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="ees-mono">{{ fmtCell(row?.actual) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="判定" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row?.passed ? 'success' : 'danger'" effect="plain" size="small">
                  {{ row?.passed ? '通过' : '未通过' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="差异说明" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">{{ row?.note || '-' }}</template>
            </el-table-column>
          </el-table>

          <el-collapse class="run-collapse">
            <el-collapse-item name="expected">
              <template #title>
                <span class="collapse-title">期望值（基准样例登记值）</span>
              </template>
              <pre class="ees-pre">{{ prettyJson(runResult.expected) }}</pre>
            </el-collapse-item>
            <el-collapse-item name="actualRaw">
              <template #title>
                <span class="collapse-title">实际算法原始输出（actualRaw，内容可能较大）</span>
              </template>
              <pre class="ees-pre">{{ prettyJson(runResult.actualRaw) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </template>
        <div v-else class="ees-empty">尚未获得运行结果</div>
      </div>
      <template #footer>
        <el-button @click="runVisible = false">关闭</el-button>
        <el-button v-if="canRun && runBaselineId" type="primary" :loading="running" @click="runOneById(runBaselineId)">
          重新运行
        </el-button>
      </template>
    </el-dialog>

    <!-- 全部运行结果 -->
    <el-dialog v-model="runAllVisible" title="全部基准运行结果" width="960px">
      <el-alert
        :type="passRatio.type"
        :closable="false"
        show-icon
        :title="`通过 ${passRatio.passed} / ${passRatio.total}`"
      >
        <div class="ees-hint">
          判定规则：浮点结果按绝对容差或相对容差判定，任一满足即视为通过；未通过项在「差异说明」中给出具体偏差。
        </div>
      </el-alert>

      <el-table :data="lastRunAll" border size="small" class="run-all-table" empty-text="暂无运行结果">
        <el-table-column prop="baselineCode" label="基准编号" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.baselineCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="baselineName" label="基准名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.baselineName || '-' }}</template>
        </el-table-column>
        <el-table-column label="算法" width="140">
          <template #default="{ row }">
            {{ ALGORITHM_LABEL[row?.algorithm] || row?.algorithm || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="运行结果" width="110" align="center">
          <template #default="{ row }">
            <span :class="resultClass(row?.result)">{{ row?.result || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="判定" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row?.passed ? 'success' : 'danger'" effect="plain" size="small">
              {{ row?.passed ? '通过' : '未通过' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="差异说明" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ diffText(row) }}</template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="runAllVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { baselineApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ALGORITHM_LABEL, fmtDateTime, fmtNum, parseJson } from '@/utils/format'

interface BaselineRow {
  id: number
  baselineCode?: string
  baselineName?: string
  algorithm?: string
  absTolerance?: number
  relTolerance?: number
  description?: string
  enabled?: boolean
  lastRunAt?: string
  lastRunResult?: string
}

interface ComparisonRow {
  field?: string
  expected?: any
  actual?: any
  passed?: boolean
  note?: string
}

interface RunResult {
  baselineCode?: string
  baselineName?: string
  algorithm?: string
  algorithmVersion?: string
  passed?: boolean
  result?: string
  absTolerance?: number
  relTolerance?: number
  comparisons?: ComparisonRow[]
  actualRaw?: any
  expected?: any
  runAt?: string
  error?: string
}

const userStore = useUserStore()
const canRun = computed(() => userStore.has('baseline:list'))

function resultClass(text?: string | null): string {
  const t = String(text || '').trim().toUpperCase()
  if (t.startsWith('PASS')) return 'run-text run-text--pass'
  if (t.startsWith('FAIL') || t.startsWith('ERROR')) return 'run-text run-text--fail'
  return 'run-text'
}

/** 比对值可能是数组 / 对象，统一以紧凑 JSON 展示，长内容由列的自适应提示承载 */
function fmtCell(v: any): string {
  if (v === null || v === undefined || v === '') return '-'
  if (typeof v === 'number') return String(v)
  if (typeof v === 'string') return v
  try {
    return JSON.stringify(v)
  } catch {
    return String(v)
  }
}

function prettyJson(v: any): string {
  if (v === null || v === undefined || v === '') return '（无数据）'
  const obj = typeof v === 'string' ? parseJson<any>(v, v) : v
  if (obj === null || obj === undefined) return '（无数据）'
  if (typeof obj === 'string') return obj
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

/** ---------------- 列表 ---------------- */

const loading = ref(false)
const rows = ref<BaselineRow[]>([])

async function load() {
  loading.value = true
  try {
    const list = await baselineApi.list()
    rows.value = Array.isArray(list) ? (list as BaselineRow[]) : []
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

/** ---------------- 单个基准运行 ---------------- */

const runVisible = ref(false)
const running = ref(false)
const runBaselineId = ref<number | null>(null)
const runBaselineCode = ref('')
const runBaselineName = ref('')
const runResult = ref<RunResult | null>(null)

const comparisons = computed<ComparisonRow[]>(() =>
  Array.isArray(runResult.value?.comparisons) ? (runResult.value?.comparisons as ComparisonRow[]) : []
)

async function runOne(row: BaselineRow | null | undefined) {
  if (!row?.id) return
  runBaselineId.value = row.id
  runBaselineCode.value = row.baselineCode || ''
  runBaselineName.value = row.baselineName || ''
  runResult.value = null
  runVisible.value = true
  await runOneById(row.id)
}

async function runOneById(id: number) {
  running.value = true
  try {
    const res = await baselineApi.run(id)
    runResult.value = (res as RunResult) || null
    if (runResult.value?.passed) {
      ElMessage.success('基准测试通过')
    } else {
      ElMessage.warning('基准测试未通过，请查看逐项比对差异')
    }
    await load()
  } catch {
    runResult.value = null
    // 统一响应拦截器已提示
  } finally {
    running.value = false
  }
}

/** ---------------- 全部运行 ---------------- */

const runningAll = ref(false)
const runAllVisible = ref(false)
const lastRunAll = ref<RunResult[]>([])

const passRatio = computed(() => {
  const total = lastRunAll.value.length
  const passed = lastRunAll.value.filter((r) => r?.passed === true).length
  return {
    total,
    passed,
    type: (total > 0 && passed === total ? 'success' : 'warning') as 'success' | 'warning'
  }
})

function diffText(row: RunResult | null | undefined): string {
  if (!row) return '-'
  if (row.error) return row.error
  const list = Array.isArray(row.comparisons) ? row.comparisons : []
  const failed = list.filter((c) => c?.passed !== true)
  if (!failed.length) return '全部比对项在容差内通过'
  return failed.map((c) => `${c?.field || '-'}: ${c?.note || '未通过'}`).join('；')
}

async function runAll() {
  runningAll.value = true
  try {
    const list = await baselineApi.runAll()
    lastRunAll.value = Array.isArray(list) ? (list as RunResult[]) : []
    runAllVisible.value = true
    const passed = lastRunAll.value.filter((r) => r?.passed === true).length
    if (lastRunAll.value.length && passed === lastRunAll.value.length) {
      ElMessage.success(`全部基准测试通过：通过 ${passed} / ${lastRunAll.value.length}`)
    } else {
      ElMessage.warning(`基准测试完成：通过 ${passed} / ${lastRunAll.value.length}`)
    }
    await load()
  } catch {
    // 统一响应拦截器已提示
  } finally {
    runningAll.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.summary-alert {
  margin-bottom: 14px;
}

.run-text {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12.5px;
}

.run-text--pass {
  color: var(--ees-success);
  font-weight: 600;
}

.run-text--fail {
  color: var(--ees-danger);
  font-weight: 600;
}

.run-head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.run-code {
  font-size: 13px;
  color: var(--ees-text-secondary);
}

.run-desc {
  margin-bottom: 14px;
}

.sub-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ees-text);
  margin: 2px 0 10px;
}

.run-collapse {
  margin-top: 12px;
  border-top: 1px solid var(--ees-border);
}

.collapse-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ees-text);
}

.run-all-table {
  margin-top: 12px;
}
</style>
