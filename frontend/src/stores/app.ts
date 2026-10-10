import { defineStore } from 'pinia'

/** 全局 UI 状态：侧栏折叠、当前项目上下文、面包屑 */
export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: false,
    currentProjectId: (localStorage.getItem('ees_project_id')
      ? Number(localStorage.getItem('ees_project_id'))
      : undefined) as number | undefined,
    systemInfo: null as any
  }),

  getters: {
    sidebarWidth: (state) => (state.sidebarCollapsed ? '64px' : '232px')
  },

  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
    },
    setCurrentProject(id?: number) {
      this.currentProjectId = id
      if (id === undefined || id === null) {
        localStorage.removeItem('ees_project_id')
      } else {
        localStorage.setItem('ees_project_id', String(id))
      }
    }
  }
})
