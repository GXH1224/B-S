import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

/**
 * 使用 hash 模式：离线部署到 Nginx 任意子路径时无需额外 rewrite 规则。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '综合驾驶舱', icon: 'Odometer', menu: 'dashboard' }
      },
      {
        path: 'projects',
        name: 'Projects',
        component: () => import('@/views/project/ProjectList.vue'),
        meta: { title: '项目管理', icon: 'Folder', menu: 'project' }
      },
      {
        path: 'indicators',
        name: 'Indicators',
        component: () => import('@/views/indicator/IndicatorVersionList.vue'),
        meta: { title: '指标体系', icon: 'Grid', menu: 'indicator' }
      },
      {
        path: 'indicators/:id',
        name: 'IndicatorDetail',
        component: () => import('@/views/indicator/IndicatorVersionDetail.vue'),
        meta: { title: '指标详情', menu: 'indicator', hidden: true }
      },
      {
        path: 'data-sources',
        name: 'DataSources',
        component: () => import('@/views/data/DataSourceList.vue'),
        meta: { title: '数据源管理', icon: 'Coin', menu: 'dataSource' }
      },
      {
        path: 'data-batches',
        name: 'DataBatches',
        component: () => import('@/views/data/DataBatchList.vue'),
        meta: { title: '数据接入', icon: 'Upload', menu: 'dataBatch' }
      },
      {
        path: 'snapshots',
        name: 'Snapshots',
        component: () => import('@/views/data/SnapshotList.vue'),
        meta: { title: '数据快照', icon: 'Camera', menu: 'snapshot' }
      },
      {
        path: 'workbench',
        name: 'Workbench',
        component: () => import('@/views/task/EvaluationWorkbench.vue'),
        meta: { title: '评估工作台', icon: 'MagicStick', menu: 'task' }
      },
      {
        path: 'tasks',
        name: 'Tasks',
        component: () => import('@/views/task/TaskList.vue'),
        meta: { title: '评估任务', icon: 'List', menu: 'task' }
      },
      {
        path: 'tasks/:id',
        name: 'TaskDetail',
        component: () => import('@/views/task/TaskDetail.vue'),
        meta: { title: '任务详情', menu: 'task', hidden: true }
      },
      {
        path: 'results',
        name: 'Results',
        component: () => import('@/views/result/ResultList.vue'),
        meta: { title: '评估结果', icon: 'DataAnalysis', menu: 'result' }
      },
      {
        path: 'results/:id',
        name: 'ResultDetail',
        component: () => import('@/views/result/ResultDetail.vue'),
        meta: { title: '结果分析', menu: 'result', hidden: true }
      },
      {
        path: 'reports',
        name: 'Reports',
        component: () => import('@/views/report/ReportList.vue'),
        meta: { title: '报告导出', icon: 'Document', menu: 'report' }
      },
      {
        path: 'baselines',
        name: 'Baselines',
        component: () => import('@/views/baseline/BaselineList.vue'),
        meta: { title: '算法基准', icon: 'Compass', menu: 'baseline' }
      },
      {
        path: 'system/users',
        name: 'Users',
        component: () => import('@/views/system/UserList.vue'),
        meta: { title: '用户管理', icon: 'User', menu: 'user' }
      },
      {
        path: 'system/roles',
        name: 'Roles',
        component: () => import('@/views/system/RoleList.vue'),
        meta: { title: '角色权限', icon: 'Avatar', menu: 'role' }
      },
      {
        path: 'system/audit',
        name: 'AuditLogs',
        component: () => import('@/views/system/AuditLogList.vue'),
        meta: { title: '审计日志', icon: 'Tickets', menu: 'audit' }
      },
      {
        path: 'system/config',
        name: 'SystemConfig',
        component: () => import('@/views/system/SystemConfig.vue'),
        meta: { title: '系统参数', icon: 'Setting', menu: 'config' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面不存在', public: true }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

/**
 * 路由懒加载失败的兜底处理。
 *
 * 典型场景：前端重新构建后 chunk 文件名（带内容 hash）会变化。如果浏览器里还停留着
 * 构建之前的旧页面，点击菜单时 Vue Router 会去加载一个**已经不存在的旧 chunk**，
 * 请求失败 → 动态导入被拒绝 → 路由组件渲染不出来，界面只剩下白屏，且没有任何提示，
 * 使用者完全不知道该怎么办。
 *
 * 这里统一识别这类错误：先自动刷新一次（刷新后拿到的 index.html 会引用新的 chunk），
 * 并用 sessionStorage 做 10 秒节流 —— 万一刷新后仍然失败，就不再反复刷新，
 * 而是给出明确的人工指引，避免陷入无限刷新。
 */
const CHUNK_ERROR_HINTS = [
  'Failed to fetch dynamically imported module',
  'Importing a module script failed',
  'error loading dynamically imported module',
  'Loading chunk',
  'Loading CSS chunk',
  'Unable to preload CSS'
]

function isChunkLoadError(error: unknown): boolean {
  const msg = String((error as { message?: string } | null)?.message ?? error ?? '')
  return CHUNK_ERROR_HINTS.some((hint) => msg.includes(hint))
}

let chunkReloading = false
const CHUNK_RELOAD_KEY = 'ees:chunk-reload-at'

router.onError((error) => {
  if (chunkReloading || !isChunkLoadError(error)) return
  chunkReloading = true

  const last = Number(sessionStorage.getItem(CHUNK_RELOAD_KEY) || 0)
  if (Date.now() - last < 10000) {
    ElMessage.error('页面资源加载失败，请按 Ctrl + F5 强制刷新后重试')
    return
  }

  sessionStorage.setItem(CHUNK_RELOAD_KEY, String(Date.now()))
  ElMessage.warning('检测到系统已更新，正在自动刷新页面…')
  window.setTimeout(() => window.location.reload(), 400)
})

router.beforeEach(async (to, _from, next) => {
  const title = (to.meta?.title as string) || ''
  document.title = title ? `${title} - 效能评估系统` : '效能评估系统'

  const userStore = useUserStore()

  if (to.meta?.public) {
    next()
    return
  }

  if (!userStore.isLogin) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  if (!userStore.loaded) {
    try {
      await userStore.loadUser()
    } catch {
      userStore.resetLocal()
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
  }

  // 菜单级授权：无该模块权限时回到驾驶舱。
  //
  // 这里必须防止「重定向到自身」：驾驶舱路由自己也带 meta.menu = 'dashboard'。
  // 如果菜单集合为空（例如后端接口漏返回 menus，前端就会认为当前角色没有任何菜单权限），
  // 那么访问驾驶舱时守卫会把驾驶舱重定向到驾驶舱，守卫被反复触发 → 无限重定向 → 整页白屏，
  // 而且地址栏会一直停在进入前的地址上，使用者完全看不出发生了什么。
  // 因此当目标已经是驾驶舱时直接放行：菜单数据异常最多表现为「少几个菜单」，而不会白屏。
  const menu = to.meta?.menu as string | undefined
  if (menu && !userStore.hasMenu(menu)) {
    if (to.path === '/dashboard') {
      console.warn('[EES] 当前用户菜单集合为空或缺少该菜单，已跳过菜单级授权以避免无限重定向；'
        + '请检查 /api/auth/me 是否返回 menus 字段。')
    } else {
      next({ path: '/dashboard' })
      return
    }
  }

  next()
})

export default router
