<template>
  <div class="ees-page">
    <PageHeader
      title="用户管理"
      desc="密码使用安全哈希保存，不明文存储；连续登录失败达到阈值自动锁定"
    >
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新建用户</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="ees-card">
      <div class="ees-filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="用户名 / 姓名 / 单位"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.roleCode" placeholder="全部角色" clearable style="width: 172px">
          <el-option
            v-for="r in roles"
            :key="r.roleCode"
            :label="r.roleName || r.roleCode"
            :value="r.roleCode"
          />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe size="default">
        <template #empty>
          <div class="ees-empty">暂无用户数据</div>
        </template>

        <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
        <el-table-column label="姓名" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.realName || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="120">
          <template #default="{ row }">{{ roleNameOf(row.roleCode) }}</template>
        </el-table-column>
        <el-table-column label="所属单位" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.orgName || '-' }}</template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column label="手机" min-width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="showFailCount" label="连续失败次数" width="110" align="center">
          <template #default="{ row }">
            <span :class="{ 'fail-count': Number(row.loginFailCount) > 0 }">
              {{ Number(row.loginFailCount || 0) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最后登录时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="最后登录IP" min-width="130">
          <template #default="{ row }">
            <span class="ees-mono">{{ row.lastLoginIp || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openResetPassword(row)">
              重置密码
            </el-button>
            <el-button link type="warning" size="small" @click="toggleStatus(row)">
              {{ row.status === 'ENABLED' ? '停用' : row.status === 'LOCKED' ? '解锁' : '启用' }}
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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
          @current-change="loadUsers"
        />
      </div>
    </div>

    <!-- 新建 / 编辑用户 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="form.username"
                :disabled="editingId !== null"
                placeholder="登录账号，创建后不可修改"
                autocomplete="off"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                autocomplete="new-password"
                :placeholder="editingId === null ? '请输入初始密码' : '留空表示不修改密码'"
              />
              <div class="ees-hint">修改时留空表示不修改密码</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="form.realName" placeholder="真实姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色" prop="roleCode">
              <el-select v-model="form.roleCode" placeholder="请选择角色" style="width: 100%">
                <el-option
                  v-for="r in roles"
                  :key="r.roleCode"
                  :label="r.roleName || r.roleCode"
                  :value="r.roleCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属单位" prop="orgName">
              <el-input v-model="form.orgName" placeholder="所属单位 / 部门" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option
                  v-for="s in STATUS_OPTIONS"
                  :key="s.value"
                  :label="s.label"
                  :value="s.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="用于通知与审计" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机" prop="phone">
              <el-input v-model="form.phone" placeholder="联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="form.remark"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
                placeholder="备注信息"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwdVisible" title="重置密码" width="460px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="重置后密码立即生效，请通过安全渠道告知用户并及时修改。"
        style="margin-bottom: 12px"
      />
      <el-form label-width="96px">
        <el-form-item label="用户">
          <span class="ees-mono">{{ pwdTarget?.username || '-' }}</span>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password autocomplete="new-password" />
          <div class="ees-hint">默认 123456；长度 6 ~ 64 位，建议包含字母与数字</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitResetPassword">
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { userApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import { fmtDateTime } from '@/utils/format'

interface RoleRow {
  id?: number
  roleCode: string
  roleName?: string
  description?: string
  dataScope?: string
  builtIn?: boolean
  userCount?: number
}

interface UserRow {
  id: number
  username: string
  realName?: string
  roleCode?: string
  orgName?: string
  email?: string
  phone?: string
  status?: string
  lastLoginAt?: string
  lastLoginIp?: string
  remark?: string
  loginFailCount?: number
}

interface UserForm {
  username: string
  password: string
  realName: string
  roleCode: string
  orgName: string
  email: string
  phone: string
  status: string
  remark: string
}

const STATUS_OPTIONS = [
  { value: 'ENABLED', label: '启用' },
  { value: 'DISABLED', label: '停用' },
  { value: 'LOCKED', label: '锁定' }
]

const STATUS_LABEL: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
  LOCKED: '锁定'
}

const STATUS_TYPE: Record<string, 'success' | 'info' | 'danger'> = {
  ENABLED: 'success',
  DISABLED: 'info',
  LOCKED: 'danger'
}

const loading = ref(false)
const rows = ref<UserRow[]>([])
const roles = ref<RoleRow[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const query = reactive({ keyword: '', roleCode: '', status: '' })

const roleNameMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  for (const r of roles.value) {
    if (r?.roleCode) map[r.roleCode] = r.roleName || r.roleCode
  }
  return map
})

/** 后端不返回连续失败次数时隐藏该列 */
const showFailCount = computed(() =>
  rows.value.some((r) => r.loginFailCount !== undefined && r.loginFailCount !== null)
)

function roleNameOf(code?: string): string {
  if (!code) return '-'
  return roleNameMap.value[code] || code
}

function statusLabel(status?: string): string {
  if (!status) return '-'
  return STATUS_LABEL[status] || status
}

function statusType(status?: string): 'success' | 'info' | 'danger' {
  if (!status) return 'info'
  return STATUS_TYPE[status] || 'info'
}

async function loadRoles() {
  try {
    const list = await userApi.roles()
    roles.value = Array.isArray(list) ? (list as RoleRow[]) : []
  } catch {
    roles.value = []
  }
}

async function loadUsers() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: page.value, size: size.value }
    const keyword = query.keyword.trim()
    if (keyword) params.keyword = keyword
    if (query.roleCode) params.roleCode = query.roleCode
    if (query.status) params.status = query.status
    const res = await userApi.list(params)
    rows.value = Array.isArray(res?.records) ? (res.records as UserRow[]) : []
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
  loadUsers()
}

function handleReset() {
  query.keyword = ''
  query.roleCode = ''
  query.status = ''
  page.value = 1
  loadUsers()
}

function handleSizeChange() {
  page.value = 1
  loadUsers()
}

/** ---------------- 新建 / 编辑 ---------------- */
const dialogVisible = ref(false)
const dialogTitle = ref('新建用户')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<UserForm>({
  username: '',
  password: '',
  realName: '',
  roleCode: '',
  orgName: '',
  email: '',
  phone: '',
  status: 'ENABLED',
  remark: ''
})

const formRules = computed<FormRules>(() => {
  const rules: FormRules = {
    username: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      { max: 64, message: '用户名长度不能超过 64 个字符', trigger: 'blur' }
    ],
    roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
    email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
    phone: [{ pattern: /^[0-9+\-() ]{5,32}$/, message: '手机号格式不正确', trigger: 'blur' }]
  }
  rules.password =
    editingId.value === null
      ? [
          { required: true, message: '请输入初始密码', trigger: 'blur' },
          { min: 6, max: 64, message: '密码长度需在 6 到 64 之间', trigger: 'blur' }
        ]
      : [{ min: 6, max: 64, message: '密码长度需在 6 到 64 之间', trigger: 'blur' }]
  return rules
})

function resetForm() {
  form.username = ''
  form.password = ''
  form.realName = ''
  form.roleCode = ''
  form.orgName = ''
  form.email = ''
  form.phone = ''
  form.status = 'ENABLED'
  form.remark = ''
  formRef.value?.clearValidate()
}

function openCreate() {
  editingId.value = null
  dialogTitle.value = '新建用户'
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: UserRow) {
  editingId.value = row.id
  dialogTitle.value = `编辑用户 - ${row.username}`
  resetForm()
  form.username = row.username || ''
  form.realName = row.realName || ''
  form.roleCode = row.roleCode || ''
  form.orgName = row.orgName || ''
  form.email = row.email || ''
  form.phone = row.phone || ''
  form.status = row.status || 'ENABLED'
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload: Record<string, any> = {
      username: form.username.trim(),
      realName: form.realName.trim(),
      roleCode: form.roleCode,
      orgName: form.orgName.trim(),
      email: form.email.trim(),
      phone: form.phone.trim(),
      status: form.status,
      remark: form.remark.trim()
    }
    if (editingId.value === null) {
      payload.password = form.password
      await userApi.create(payload)
      ElMessage.success('用户创建成功')
    } else {
      if (form.password) payload.password = form.password
      await userApi.update(editingId.value, payload)
      ElMessage.success('用户更新成功')
    }
    dialogVisible.value = false
    await loadUsers()
  } catch {
    // 失败提示由请求拦截器统一展示
  } finally {
    submitting.value = false
  }
}

/** ---------------- 重置密码 ---------------- */
const pwdVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdTarget = ref<UserRow | null>(null)
const newPassword = ref('123456')

function openResetPassword(row: UserRow) {
  pwdTarget.value = row
  newPassword.value = '123456'
  pwdVisible.value = true
}

async function submitResetPassword() {
  const target = pwdTarget.value
  if (!target) return
  const password = newPassword.value.trim()
  if (password.length < 6) {
    ElMessage.warning('密码长度至少 6 位')
    return
  }
  pwdSubmitting.value = true
  try {
    await userApi.resetPassword(target.id, password)
    ElMessage.success(`用户 ${target.username} 的密码已重置为：${password}，请及时告知用户并提示修改`)
    pwdVisible.value = false
  } catch {
    // 失败提示由请求拦截器统一展示
  } finally {
    pwdSubmitting.value = false
  }
}

/** ---------------- 启用 / 停用 / 解锁 ---------------- */
async function toggleStatus(row: UserRow) {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const actionText = next === 'ENABLED' ? (row.status === 'LOCKED' ? '解锁' : '启用') : '停用'
  try {
    await ElMessageBox.confirm(
      `确定要${actionText}用户「${row.username}」吗？`,
      '操作确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await userApi.changeStatus(row.id, next)
    ElMessage.success(`已${actionText}用户 ${row.username}`)
    await loadUsers()
  } catch {
    // 失败提示由请求拦截器统一展示
  }
}

/** ---------------- 删除 ---------------- */
async function handleDelete(row: UserRow) {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户「${row.username}」吗？删除为逻辑删除，该账号将无法登录。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await userApi.remove(row.id)
    ElMessage.success('用户已删除')
    await loadUsers()
  } catch {
    // 内置管理员账号（admin）不可删除，后端返回的中文原因由请求拦截器展示
  }
}

onMounted(async () => {
  await loadRoles()
  await loadUsers()
})
</script>

<style scoped>
.fail-count {
  color: var(--ees-danger);
  font-weight: 600;
}
</style>
