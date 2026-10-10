<template>
  <el-container class="layout-root">
    <el-aside :width="appStore.sidebarWidth" class="ees-sidebar layout-aside">
      <div class="brand" :class="{ 'brand--mini': appStore.sidebarCollapsed }">
        <div class="brand-logo">EES</div>
        <div v-if="!appStore.sidebarCollapsed" class="brand-text">
          <div class="brand-title">效能评估系统</div>
          <div class="brand-sub">Efficiency Evaluation System</div>
        </div>
      </div>

      <el-scrollbar class="menu-scroll">
        <el-menu
          :default-active="activeMenu"
          :collapse="appStore.sidebarCollapsed"
          :collapse-transition="false"
          background-color="transparent"
          router
        >
          <template v-for="item in visibleMenus" :key="item.path">
            <el-menu-item v-if="!item.children" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.title }}</template>
            </el-menu-item>

            <el-sub-menu v-else :index="item.path">
              <template #title>
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
                <el-icon><component :is="child.icon" /></el-icon>
                <template #title>{{ child.title }}</template>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="appStore.toggleSidebar()">
            <Fold v-if="!appStore.sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-tag v-if="health" :type="health.status === 'UP' ? 'success' : 'warning'" effect="plain" size="small">
            <el-icon style="vertical-align: -2px; margin-right: 3px"><CircleCheck /></el-icon>
            服务 {{ health.status === 'UP' ? '正常' : '异常' }}
          </el-tag>

          <el-tooltip content="刷新当前页面数据" placement="bottom">
            <el-button text circle @click="reloadPage">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </el-tooltip>

          <el-dropdown @command="onUserCommand">
            <span class="user-chip">
              <el-avatar :size="26" class="user-avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ userStore.displayName }}</span>
              <el-tag size="small" effect="plain" class="user-role">{{ userStore.user?.roleName || userStore.roleCode }}</el-tag>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon> 当前账号：{{ userStore.user?.username }}
                </el-dropdown-item>
                <el-dropdown-item command="password">
                  <el-icon><Lock /></el-icon> 修改密码
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <keep-alive :max="6">
            <component :is="Component" :key="route.fullPath" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>

    <el-dialog v-model="pwdVisible" title="修改密码" width="430px">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="96px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="pwdForm.confirm" type="password" show-password autocomplete="off" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="submitPassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { authApi, systemApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const health = ref<any>(null)
let healthTimer: number | undefined

interface MenuNode {
  path: string
  title: string
  icon: string
  menu?: string
  children?: MenuNode[]
}

/** 侧栏菜单：按后端返回的 menus 做可见性过滤 */
const menuTree: MenuNode[] = [
  { path: '/dashboard', title: '综合驾驶舱', icon: 'Odometer', menu: 'dashboard' },
  { path: '/projects', title: '项目管理', icon: 'Folder', menu: 'project' },
  { path: '/indicators', title: '指标体系', icon: 'Grid', menu: 'indicator' },
  {
    path: '/data',
    title: '数据接入',
    icon: 'Coin',
    children: [
      { path: '/data-sources', title: '数据源管理', icon: 'Link', menu: 'dataSource' },
      { path: '/data-batches', title: '数据批次', icon: 'Upload', menu: 'dataBatch' },
      { path: '/snapshots', title: '数据快照', icon: 'Camera', menu: 'snapshot' }
    ]
  },
  { path: '/workbench', title: '评估工作台', icon: 'MagicStick', menu: 'task' },
  { path: '/tasks', title: '评估任务', icon: 'List', menu: 'task' },
  { path: '/results', title: '评估结果', icon: 'DataAnalysis', menu: 'result' },
  { path: '/reports', title: '报告导出', icon: 'Document', menu: 'report' },
  { path: '/baselines', title: '算法基准', icon: 'Compass', menu: 'baseline' },
  {
    path: '/system',
    title: '系统管理',
    icon: 'Setting',
    children: [
      { path: '/system/users', title: '用户管理', icon: 'User', menu: 'user' },
      { path: '/system/roles', title: '角色权限', icon: 'Avatar', menu: 'role' },
      { path: '/system/audit', title: '审计日志', icon: 'Tickets', menu: 'audit' },
      { path: '/system/config', title: '系统参数', icon: 'Tools', menu: 'config' }
    ]
  }
]

const visibleMenus = computed<MenuNode[]>(() => {
  const result: MenuNode[] = []
  for (const node of menuTree) {
    if (node.children) {
      const children = node.children.filter((c) => !c.menu || userStore.hasMenu(c.menu))
      if (children.length) result.push({ ...node, children })
    } else if (!node.menu || userStore.hasMenu(node.menu)) {
      result.push(node)
    }
  }
  return result
})

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/tasks/')) return '/tasks'
  if (path.startsWith('/results/')) return '/results'
  if (path.startsWith('/indicators/')) return '/indicators'
  return path
})

const currentTitle = computed(() => (route.meta?.title as string) || '综合驾驶舱')

const avatarText = computed(() => {
  const name = userStore.user?.realName || userStore.user?.username || '?'
  return name.slice(0, 1).toUpperCase()
})

function reloadPage() {
  router.go(0)
}

async function loadHealth() {
  try {
    const res = await systemApi.health()
    health.value = res
  } catch {
    health.value = { status: 'DOWN' }
  }
}

onMounted(() => {
  loadHealth()
  healthTimer = window.setInterval(loadHealth, 60000)
})

onUnmounted(() => {
  if (healthTimer) window.clearInterval(healthTimer)
})

/** ---------------- 修改密码 ---------------- */
const pwdVisible = ref(false)
const pwdLoading = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = ref({ oldPassword: '', newPassword: '', confirm: '' })
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '新密码长度需在 6 到 64 之间', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_r, value, cb) => {
        if (value !== pwdForm.value.newPassword) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

async function submitPassword() {
  if (!pwdFormRef.value) return
  await pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    pwdLoading.value = true
    try {
      await authApi.changePassword({
        oldPassword: pwdForm.value.oldPassword,
        newPassword: pwdForm.value.newPassword
      })
      ElMessage.success('密码修改成功，请重新登录')
      pwdVisible.value = false
      await userStore.logout()
      router.push('/login')
    } finally {
      pwdLoading.value = false
    }
  })
}

async function onUserCommand(command: string) {
  if (command === 'password') {
    pwdForm.value = { oldPassword: '', newPassword: '', confirm: '' }
    pwdVisible.value = true
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    } catch {
      return
    }
    await userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-root {
  height: 100vh;
}

.layout-aside {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.2s ease;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 58px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  flex-shrink: 0;
}

.brand--mini {
  justify-content: center;
  padding: 0;
}

.brand-logo {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 7px;
  background: linear-gradient(135deg, #1f6feb, #4f92f7);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 0.5px;
}

.brand-title {
  color: #fff;
  font-size: 14.5px;
  font-weight: 600;
  line-height: 1.25;
}

.brand-sub {
  color: #6f7f99;
  font-size: 10px;
  letter-spacing: 0.2px;
}

.menu-scroll {
  flex: 1;
  min-height: 0;
}

.layout-header {
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--ees-border);
  padding: 0 18px;
  flex-shrink: 0;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  font-size: 18px;
  cursor: pointer;
  color: var(--ees-text-secondary);
}

.collapse-btn:hover {
  color: var(--ees-primary);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 18px;
  outline: none;
}

.user-chip:hover {
  background: #f2f6fd;
}

.user-avatar {
  background: linear-gradient(135deg, #1f6feb, #4f92f7);
  color: #fff;
  font-size: 13px;
}

.user-name {
  font-size: 13.5px;
  color: var(--ees-text);
}

.user-role {
  font-size: 11px;
}

.layout-main {
  background: var(--ees-workbench-bg);
  padding: 0;
  overflow-y: auto;
}
</style>
