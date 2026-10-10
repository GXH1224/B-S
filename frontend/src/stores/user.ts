import { defineStore } from 'pinia'
import { authApi, type LoginUser } from '@/api'
import { clearToken, getToken, setToken } from '@/api/request'
import { useAppStore } from './app'

interface UserState {
  token: string
  user: (LoginUser & { superAdmin?: boolean }) | null
  loaded: boolean
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: getToken(),
    user: null,
    loaded: false
  }),

  getters: {
    isLogin: (state) => !!state.token,
    permissions: (state): string[] => state.user?.permissions || [],
    menus: (state): string[] => state.user?.menus || [],
    roleCode: (state): string => state.user?.roleCode || '',
    isSuperAdmin: (state): boolean =>
      !!state.user && (state.user.superAdmin === true || state.user.roleCode === 'SYSTEM_ADMIN'),
    displayName: (state): string => state.user?.realName || state.user?.username || '未登录'
  },

  actions: {
    /** 权限判定：超级管理员放行；通配符 * 放行 */
    has(permission: string | string[]): boolean {
      if (this.isSuperAdmin) return true
      const perms = this.user?.permissions || []
      if (perms.includes('*')) return true
      const list = Array.isArray(permission) ? permission : [permission]
      return list.some((p) => perms.includes(p))
    },

    /** 菜单可见性 */
    hasMenu(menu: string): boolean {
      if (this.isSuperAdmin) return true
      return this.menus.includes(menu)
    },

    async login(username: string, password: string) {
      const res = await authApi.login({ username, password })
      this.token = res.token
      setToken(res.token)
      this.user = res.user
      this.loaded = true
      // 换账号时必须清掉上一个账号留下的「当前项目」上下文：
      // 该值存在 localStorage，会跨浏览器会话保留。若不清，新账号仍带着旧账号选的项目去请求，
      // 而后端对不可见项目会返回 403（见 DashboardService / ProjectService.checkVisible），
      // 驾驶舱会因此加载失败；同时这也是「上一个人的上下文泄漏给下一个人」的来源。
      useAppStore().setCurrentProject(undefined)
      return res.user
    },

    async loadUser(force = false) {
      if (!this.token) return null
      if (this.user && !force) return this.user
      const me = await authApi.me()
      this.user = me
      this.loaded = true
      return me
    },

    async logout() {
      try {
        await authApi.logout()
      } catch {
        // 忽略登出接口异常，本地状态仍需清理
      }
      this.token = ''
      this.user = null
      this.loaded = false
      clearToken()
      // 一并清掉当前项目上下文，避免下一个登录的人继承上一个账号选的项目
      useAppStore().setCurrentProject(undefined)
    },

    resetLocal() {
      this.token = ''
      this.user = null
      this.loaded = false
      clearToken()
      useAppStore().setCurrentProject(undefined)
    }
  }
})
