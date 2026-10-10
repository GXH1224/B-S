<template>
  <div class="ees-page">
    <PageHeader
      title="项目管理"
      desc="创建和维护评估项目，绑定指标体系版本、数据源和可用算法；已产生评估结果的项目只能归档，不能删除"
    >
      <template #actions>
        <el-button v-if="canCreate" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建项目
        </el-button>
      </template>
    </PageHeader>

    <div class="ees-card">
      <div class="ees-filter-bar">
        <el-input
          v-model="query.keyword"
          class="filter-input"
          placeholder="项目名称 / 项目编码"
          clearable
          @keyup.enter="doSearch"
        />
        <el-select v-model="query.status" class="filter-select" placeholder="项目状态" clearable>
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button type="primary" @click="doSearch">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="doReset">
          <el-icon><RefreshLeft /></el-icon>
          重置
        </el-button>
        <div class="filter-tail">
          <el-button text :loading="loading" @click="load">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <template #empty>
          <div class="ees-empty">
            <template v-if="scopeRestricted">
              <div class="scope-empty-title">暂无可见项目</div>
              <div class="scope-empty-desc">
                当前账号角色为「{{ userStore.user?.roleName || userStore.roleCode || '-' }}」，
                数据范围为「仅本人负责或参与的项目」，因此不会显示你尚未参与的项目。
                请联系管理员在对应项目的「成员配置」中把你加入，或点击右上角「新建项目」自行创建
                （创建者与负责人会自动成为项目成员）。
              </div>
            </template>
            <template v-else>暂无项目数据</template>
          </div>
        </template>
        <el-table-column prop="projectCode" label="项目编码" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ees-mono">{{ row?.projectCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.projectName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="projectType" label="项目类型" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.projectType || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.ownerName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="orgName" label="所属单位" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row?.orgName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row?.status)" effect="plain" size="small">
              {{ PROJECT_STATUS_LABEL[row?.status] || row?.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始日期" width="120" align="center">
          <template #default="{ row }">{{ fmtDate(row?.startDate) }}</template>
        </el-table-column>
        <el-table-column label="结束日期" width="120" align="center">
          <template #default="{ row }">{{ fmtDate(row?.endDate) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span class="ees-mono">{{ fmtDateTime(row?.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openStatistics(row)">查看统计</el-button>
            <el-button v-if="canUpdate" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canUpdate" link type="primary" size="small" @click="toggleArchive(row)">
              {{ row?.status === 'ARCHIVED' ? '启用' : '归档' }}
            </el-button>
            <el-button v-if="canMember" link type="primary" size="small" @click="openMembers(row)">成员配置</el-button>
            <el-button v-if="canUpdate" link type="danger" size="small" @click="confirmDelete(row)">删除</el-button>
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

    <!-- 新建 / 编辑项目 -->
    <el-dialog
      v-model="formVisible"
      :title="form.id ? '编辑项目' : '新建项目'"
      width="760px"
      :close-on-click-modal="false"
      @closed="onFormClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目编码" prop="projectCode">
              <el-input v-model="form.projectCode" placeholder="可留空由系统自动生成" clearable />
              <div class="ees-hint">留空时系统自动生成唯一编码，格式为 PRJ-yyyyMMdd-####</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目名称" prop="projectName">
              <el-input v-model="form.projectName" placeholder="请输入项目名称" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目类型" prop="projectType">
              <el-input v-model="form.projectType" placeholder="如：装备效能评估" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人" prop="ownerId">
              <el-select v-model="form.ownerId" placeholder="请选择负责人" clearable filterable style="width: 100%" @change="onOwnerChange">
                <el-option v-for="u in userOptions" :key="u.id" :label="userLabel(u)" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属单位" prop="orgName">
              <el-input v-model="form.orgName" placeholder="请输入所属单位" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开始日期" prop="startDate">
              <el-date-picker
                v-model="form.startDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择开始日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker
                v-model="form.endDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择结束日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="项目说明" prop="description">
              <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="项目背景与评估目标说明" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" maxlength="500" show-word-limit placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 成员配置 -->
    <el-dialog v-model="memberVisible" :title="`成员配置 - ${memberProjectName}`" width="820px" :close-on-click-modal="false">
      <div class="ees-hint" style="margin-bottom: 10px">
        成员角色决定该用户在项目内的数据可见范围与可执行操作：项目负责人 / 数据管理员 / 评估人员 / 审核人员 / 只读查看。
      </div>
      <el-table v-loading="memberLoading" :data="memberRows" border size="small" empty-text="暂无成员，请添加">
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <span>{{ memberLabel(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成员角色" width="180">
          <template #default="{ row }">
            <el-select v-model="row.memberRole" size="small" style="width: 100%">
              <el-option v-for="opt in MEMBER_ROLE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeMemberRow($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="member-add">
        <el-select v-model="newMemberUserId" placeholder="选择要添加的用户" filterable clearable class="member-add-select">
          <el-option v-for="u in addableUsers" :key="u.id" :label="userLabel(u)" :value="u.id" />
        </el-select>
        <el-select v-model="newMemberRole" class="member-add-role">
          <el-option v-for="opt in MEMBER_ROLE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button type="primary" plain :disabled="newMemberUserId === null" @click="addMemberRow">添加成员</el-button>
      </div>

      <template #footer>
        <el-button @click="memberVisible = false">取消</el-button>
        <el-button type="primary" :loading="memberSaving" @click="saveMembers">保存成员</el-button>
      </template>
    </el-dialog>

    <!-- 查看统计 -->
    <el-dialog v-model="statVisible" :title="`项目统计 - ${statProjectName}`" width="880px">
      <div v-loading="statLoading">
        <div class="ees-stat-grid">
          <StatCard label="任务数" :value="stat?.taskCount ?? 0" icon="List" variant="primary" />
          <StatCard label="结果数" :value="stat?.resultCount ?? 0" icon="DataAnalysis" />
          <StatCard label="数据批次" :value="stat?.batchCount ?? 0" icon="Files" />
          <StatCard label="已发布结果" :value="stat?.publishedCount ?? 0" icon="CircleCheck" variant="success" />
          <StatCard label="平均得分" :value="fmtScore(stat?.avgScore)" icon="TrendCharts" variant="warning" />
        </div>

        <div class="stat-sub-title">最近评估任务</div>
        <el-table v-loading="taskLoading" :data="statTasks" border size="small" empty-text="该项目暂无评估任务">
          <el-table-column prop="taskCode" label="任务编号" width="180">
            <template #default="{ row }">
              <span class="ees-mono">{{ row?.taskCode || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="taskName" label="任务名称" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row?.taskName || '-' }}</template>
          </el-table-column>
          <el-table-column label="算法" width="130">
            <template #default="{ row }">
              {{ ALGORITHM_LABEL[row?.algorithm] || row?.algorithm || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="taskStatusTagType(row?.status)" effect="plain" size="small">
                {{ TASK_STATUS_LABEL[row?.status] || row?.status || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="结果数" width="90" align="center">
            <template #default="{ row }">{{ row?.resultCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">
              <span class="ees-mono">{{ fmtDateTime(row?.createdAt) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="statVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { projectApi, taskApi, userApi } from '@/api'
import { useUserStore } from '@/stores/user'
import {
  ALGORITHM_LABEL,
  fmtDate,
  fmtDateTime,
  fmtScore,
  PROJECT_STATUS_LABEL,
  TASK_STATUS_LABEL,
  TASK_STATUS_TYPE
} from '@/utils/format'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

interface ProjectRow {
  id: number
  projectCode?: string
  projectName?: string
  projectType?: string
  ownerId?: number | null
  ownerName?: string
  orgName?: string
  status?: string
  startDate?: string
  endDate?: string
  description?: string
  remark?: string
  createdAt?: string
}

interface UserOption {
  id: number
  username?: string
  realName?: string
  roleCode?: string
  orgName?: string
}

interface MemberRow {
  userId: number
  username?: string
  realName?: string
  memberRole: string
}

const userStore = useUserStore()

const canCreate = computed(() => userStore.has('project:create'))
const canUpdate = computed(() => userStore.has('project:update'))
const canMember = computed(() => userStore.has('project:member'))

/**
 * 数据范围是否受限（非超管、且不是 ALL）。
 * 受限时项目列表只会显示"本人负责或参与"的项目，列表为空必须解释原因，
 * 否则使用者会误以为系统没有数据或功能坏了（实测非管理员角色会看到空列表）。
 */
const scopeRestricted = computed(() => {
  if (userStore.isSuperAdmin) return false
  return String(userStore.user?.dataScope || 'PROJECT').toUpperCase() !== 'ALL'
})

const statusOptions = Object.entries(PROJECT_STATUS_LABEL).map(([value, label]) => ({ value, label }))

const MEMBER_ROLE_OPTIONS = [
  { value: 'MANAGER', label: '项目负责人' },
  { value: 'DATA', label: '数据管理员' },
  { value: 'EVALUATOR', label: '评估人员' },
  { value: 'REVIEWER', label: '审核人员' },
  { value: 'VIEWER', label: '只读查看' }
] as const

function statusTagType(status?: string): TagType {
  if (status === 'ACTIVE') return 'success'
  if (status === 'DRAFT') return 'warning'
  if (status === 'ARCHIVED') return 'info'
  return 'info'
}

function taskStatusTagType(status?: string): TagType {
  const t = status ? TASK_STATUS_TYPE[status] : ''
  if (t === 'primary' || t === 'success' || t === 'info' || t === 'warning' || t === 'danger') return t
  return 'info'
}

/** ---------------- 列表 ---------------- */

const loading = ref(false)
const rows = ref<ProjectRow[]>([])
const total = ref(0)
const query = reactive({ keyword: '', status: '', page: 1, size: 20 })

async function load() {
  loading.value = true
  try {
    const res = await projectApi.list({
      keyword: query.keyword ? query.keyword.trim() : undefined,
      status: query.status || undefined,
      page: query.page,
      size: query.size
    })
    rows.value = Array.isArray(res?.records) ? (res.records as ProjectRow[]) : []
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
  load()
}

function onSizeChange() {
  query.page = 1
  load()
}

/** ---------------- 新建 / 编辑 ---------------- */

interface ProjectFormModel {
  id: number | null
  projectCode: string
  projectName: string
  projectType: string
  ownerId: number | null
  ownerName: string
  orgName: string
  status: string
  startDate: string
  endDate: string
  description: string
  remark: string
}

function emptyForm(): ProjectFormModel {
  return {
    id: null,
    projectCode: '',
    projectName: '',
    projectType: '',
    ownerId: null,
    ownerName: '',
    orgName: '',
    status: 'DRAFT',
    startDate: '',
    endDate: '',
    description: '',
    remark: ''
  }
}

const formVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = ref<ProjectFormModel>(emptyForm())
const userOptions = ref<UserOption[]>([])

const rules: FormRules = {
  projectName: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { max: 200, message: '项目名称长度不能超过 200 个字符', trigger: 'blur' }
  ],
  projectCode: [{ max: 64, message: '项目编码长度不能超过 64 个字符', trigger: 'blur' }],
  projectType: [{ max: 50, message: '项目类型长度不能超过 50 个字符', trigger: 'blur' }],
  orgName: [{ max: 128, message: '所属单位长度不能超过 128 个字符', trigger: 'blur' }],
  endDate: [
    {
      validator: (_rule, value, callback) => {
        const start = form.value.startDate
        if (value && start && String(value) < String(start)) {
          callback(new Error('结束日期不能早于开始日期'))
          return
        }
        callback()
      },
      trigger: 'change'
    }
  ]
}

function userLabel(u: UserOption | undefined): string {
  if (!u) return '-'
  const name = u.realName || u.username || `用户#${u.id}`
  return u.orgName ? `${name}（${u.orgName}）` : name
}

async function loadUserOptions() {
  try {
    const list = await userApi.options()
    userOptions.value = Array.isArray(list) ? (list as UserOption[]) : []
  } catch {
    userOptions.value = []
  }
}

function openCreate() {
  form.value = emptyForm()
  // 默认把「负责人」填为当前登录用户。
  // 原因：非管理员的数据范围是"本人负责或参与的项目"，负责人留空会导致刚创建的项目
  // 在自己列表里不可见；后端也有同样的兜底，这里提前填好更直观。
  const me = userStore.user
  if (me?.id) {
    form.value.ownerId = me.id
    const u = userOptions.value.find((item) => item.id === me.id)
    form.value.ownerName = u?.realName || u?.username || me.realName || me.username || ''
    const org = u?.orgName || me.orgName
    if (org && !form.value.orgName) {
      form.value.orgName = org
    }
  }
  formVisible.value = true
}

function openEdit(row: ProjectRow | null) {
  if (!row) return
  form.value = {
    id: row.id ?? null,
    projectCode: row.projectCode || '',
    projectName: row.projectName || '',
    projectType: row.projectType || '',
    ownerId: row.ownerId ?? null,
    ownerName: row.ownerName || '',
    orgName: row.orgName || '',
    status: row.status || 'DRAFT',
    startDate: row.startDate ? String(row.startDate).slice(0, 10) : '',
    endDate: row.endDate ? String(row.endDate).slice(0, 10) : '',
    description: row.description || '',
    remark: row.remark || ''
  }
  formVisible.value = true
}

function onOwnerChange(id: number | null) {
  const u = userOptions.value.find((item) => item.id === id)
  form.value.ownerName = u ? u.realName || u.username || '' : ''
  if (u?.orgName && !form.value.orgName) {
    form.value.orgName = u.orgName
  }
}

function onFormClosed() {
  formRef.value?.clearValidate()
}

async function submitForm() {
  const inst = formRef.value
  if (!inst) return
  const valid = await inst.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = {
      projectCode: form.value.projectCode.trim() || null,
      projectName: form.value.projectName.trim(),
      projectType: form.value.projectType.trim() || null,
      ownerId: form.value.ownerId,
      ownerName: form.value.ownerName || null,
      orgName: form.value.orgName.trim() || null,
      status: form.value.status,
      startDate: form.value.startDate || null,
      endDate: form.value.endDate || null,
      description: form.value.description || null,
      remark: form.value.remark || null
    }
    if (form.value.id) {
      await projectApi.update(form.value.id, payload)
      ElMessage.success('项目已更新')
    } else {
      await projectApi.create(payload)
      ElMessage.success('项目已创建')
    }
    formVisible.value = false
    await load()
  } catch {
    // 统一响应拦截器已提示具体失败原因
  } finally {
    saving.value = false
  }
}

/** ---------------- 归档 / 启用 ---------------- */

async function toggleArchive(row: ProjectRow | null) {
  if (!row) return
  const toArchive = row.status !== 'ARCHIVED'
  const actionText = toArchive ? '归档' : '启用'
  try {
    await ElMessageBox.confirm(
      toArchive
        ? `归档后项目「${row.projectName || row.projectCode}」将不再参与新的评估任务，可随时重新启用。确定归档吗？`
        : `确定启用项目「${row.projectName || row.projectCode}」吗？`,
      `${actionText}确认`,
      { type: 'warning', confirmButtonText: `确定${actionText}`, cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    if (toArchive) {
      await projectApi.archive(row.id)
    } else {
      await projectApi.activate(row.id)
    }
    ElMessage.success(`项目已${actionText}`)
    await load()
  } catch {
    // 统一响应拦截器已提示
  }
}

/** ---------------- 删除 ---------------- */

async function confirmDelete(row: ProjectRow | null) {
  if (!row) return
  try {
    await ElMessageBox.confirm(
      `确定删除项目「${row.projectName || row.projectCode}」吗？已产生评估结果的项目无法删除，请改用归档。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await projectApi.remove(row.id)
    ElMessage.success('项目已删除')
    if (rows.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    await load()
  } catch (e) {
    const backendMsg = e instanceof Error ? e.message : ''
    const content = backendMsg.includes('评估结果')
      ? backendMsg
      : '已产生评估结果的项目无法删除，请改用归档'
    ElMessageBox.alert(content, '无法删除', { type: 'warning', confirmButtonText: '知道了' }).catch(() => undefined)
  }
}

/** ---------------- 成员配置 ---------------- */

const memberVisible = ref(false)
const memberLoading = ref(false)
const memberSaving = ref(false)
const memberProjectId = ref<number | null>(null)
const memberProjectName = ref('')
const memberRows = ref<MemberRow[]>([])
const newMemberUserId = ref<number | null>(null)
const newMemberRole = ref<string>('EVALUATOR')

const addableUsers = computed(() => {
  const used = new Set(memberRows.value.map((m) => m.userId))
  return userOptions.value.filter((u) => !used.has(u.id))
})

function memberLabel(row: MemberRow): string {
  const name = row.realName || row.username || `用户#${row.userId}`
  return name
}

async function openMembers(row: ProjectRow | null) {
  if (!row) return
  memberProjectId.value = row.id
  memberProjectName.value = row.projectName || row.projectCode || ''
  memberRows.value = []
  newMemberUserId.value = null
  newMemberRole.value = 'EVALUATOR'
  memberVisible.value = true
  memberLoading.value = true
  try {
    const list = await projectApi.members(row.id)
    const arr = Array.isArray(list) ? (list as any[]) : []
    memberRows.value = arr.map((m) => {
      const userId = Number(m?.userId ?? m?.id ?? 0)
      const u = userOptions.value.find((item) => item.id === userId)
      return {
        userId,
        username: m?.username || u?.username || '',
        realName: m?.realName || u?.realName || '',
        memberRole: m?.memberRole || 'VIEWER'
      }
    })
  } catch {
    memberRows.value = []
  } finally {
    memberLoading.value = false
  }
}

function addMemberRow() {
  const id = newMemberUserId.value
  if (id === null || id === undefined) {
    ElMessage.warning('请先选择要添加的用户')
    return
  }
  if (memberRows.value.some((m) => m.userId === id)) {
    ElMessage.warning('该用户已在项目成员中')
    return
  }
  const u = userOptions.value.find((item) => item.id === id)
  memberRows.value.push({
    userId: id,
    username: u?.username || '',
    realName: u?.realName || '',
    memberRole: newMemberRole.value
  })
  newMemberUserId.value = null
}

function removeMemberRow(index: number) {
  memberRows.value.splice(index, 1)
}

async function saveMembers() {
  if (memberProjectId.value === null) return
  memberSaving.value = true
  try {
    const payload = memberRows.value.map((m) => ({
      userId: m.userId,
      username: m.username || null,
      memberRole: m.memberRole || 'VIEWER'
    }))
    await projectApi.saveMembers(memberProjectId.value, payload)
    ElMessage.success('项目成员已保存')
    memberVisible.value = false
  } catch {
    // 统一响应拦截器已提示
  } finally {
    memberSaving.value = false
  }
}

/** ---------------- 查看统计 ---------------- */

const statVisible = ref(false)
const statLoading = ref(false)
const taskLoading = ref(false)
const statProjectName = ref('')
const stat = ref<any>(null)
const statTasks = ref<any[]>([])

async function openStatistics(row: ProjectRow | null) {
  if (!row) return
  statProjectName.value = row.projectName || row.projectCode || ''
  stat.value = null
  statTasks.value = []
  statVisible.value = true
  statLoading.value = true
  taskLoading.value = true

  try {
    stat.value = (await projectApi.statistics(row.id)) || null
  } catch {
    stat.value = null
  } finally {
    statLoading.value = false
  }

  try {
    const res = await taskApi.list({ projectId: row.id, page: 1, size: 10 })
    statTasks.value = Array.isArray(res?.records) ? res.records : []
  } catch {
    statTasks.value = []
  } finally {
    taskLoading.value = false
  }
}

onMounted(async () => {
  await loadUserOptions()
  await load()
})
</script>

<style scoped>
.filter-input {
  width: 240px;
}

.filter-select {
  width: 160px;
}

.filter-tail {
  margin-left: auto;
}

.member-add {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--ees-border);
}

.member-add-select {
  width: 280px;
}

.member-add-role {
  width: 150px;
}

.stat-sub-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ees-text);
  margin: 4px 0 10px;
}

/* 列表为空时按数据范围给出的说明（见 scopeRestricted） */
.scope-empty-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ees-text);
  margin-bottom: 6px;
}

.scope-empty-desc {
  max-width: 620px;
  margin: 0 auto;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ees-text-secondary, #909399);
}
</style>
