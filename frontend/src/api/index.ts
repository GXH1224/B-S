import { http, type PageResult } from './request'

/** ---------------- 认证 ---------------- */
export interface LoginUser {
  id: number
  username: string
  realName?: string
  roleCode: string
  roleName?: string
  orgName?: string
  dataScope?: string
  permissions: string[]
  menus?: string[]
  lastLoginAt?: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  user: LoginUser
}

export const authApi = {
  login: (data: { username: string; password: string }) =>
    http.post<LoginResponse>('/api/auth/login', data),
  logout: () => http.post('/api/auth/logout'),
  me: () => http.get<LoginUser & { superAdmin: boolean }>('/api/auth/me'),
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    http.post('/api/auth/change-password', data)
}

/** ---------------- 系统 ---------------- */
export const systemApi = {
  health: () => http.get<any>('/api/health'),
  info: () => http.get<any>('/api/info'),
  /**
   * 驾驶舱总览。
   * from/to 为统计区间（YYYY-MM-DD，含首尾两天），只影响「任务趋势」与「评判等级分布」两张图，
   * 上方指标卡始终是当前总量；两者都不传时后端按近 7 天处理。
   */
  dashboard: (projectId?: number, from?: string, to?: string) => {
    const params: Record<string, any> = {}
    if (projectId) params.projectId = projectId
    if (from) params.from = from
    if (to) params.to = to
    return http.get<any>('/api/dashboard/overview', Object.keys(params).length ? params : undefined)
  },
  permissions: () => http.get<any[]>('/api/permissions'),
  auditLogs: (params: Record<string, any>) => http.get<PageResult>('/api/audit-logs', params),
  auditDetail: (id: number) => http.get<any>(`/api/audit-logs/${id}`),
  purgeAudit: (retentionDays: number) => http.post('/api/audit-logs/purge', { retentionDays }),
  configs: () => http.get<Record<string, Record<string, any>>>('/api/configs'),
  updateConfig: (key: string, value: string) => http.put(`/api/configs/${key}`, { value }),
  qualityRules: (projectId?: number) =>
    http.get<any[]>('/api/quality-rules', projectId ? { projectId } : undefined),
  updateQualityRule: (id: number, data: any) => http.put(`/api/quality-rules/${id}`, data)
}

/** ---------------- 用户与角色 ---------------- */
export const userApi = {
  list: (params: Record<string, any>) => http.get<PageResult>('/api/users', params),
  options: () => http.get<any[]>('/api/users/options'),
  get: (id: number) => http.get<any>(`/api/users/${id}`),
  create: (data: any) => http.post('/api/users', data),
  update: (id: number, data: any) => http.put(`/api/users/${id}`, data),
  resetPassword: (id: number, newPassword: string) =>
    http.post(`/api/users/${id}/reset-password`, { newPassword }),
  changeStatus: (id: number, status: string) =>
    http.put(`/api/users/${id}/status?status=${status}`),
  remove: (id: number) => http.delete(`/api/users/${id}`),
  roles: () => http.get<any[]>('/api/roles'),
  rolePermissions: (roleCode: string) => http.get<any>(`/api/roles/${roleCode}/permissions`)
}

/** ---------------- 项目 ---------------- */
export const projectApi = {
  list: (params: Record<string, any>) => http.get<PageResult>('/api/projects', params),
  options: () => http.get<any[]>('/api/projects/options'),
  all: () => http.get<any[]>('/api/projects/all'),
  get: (id: number) => http.get<any>(`/api/projects/${id}`),
  statistics: (id: number) => http.get<any>(`/api/projects/${id}/statistics`),
  create: (data: any) => http.post('/api/projects', data),
  update: (id: number, data: any) => http.put(`/api/projects/${id}`, data),
  archive: (id: number) => http.post(`/api/projects/${id}/archive`),
  activate: (id: number) => http.post(`/api/projects/${id}/activate`),
  remove: (id: number) => http.delete(`/api/projects/${id}`),
  members: (id: number) => http.get<any[]>(`/api/projects/${id}/members`),
  saveMembers: (id: number, members: any[]) => http.post(`/api/projects/${id}/members`, members)
}

/** ---------------- 指标体系 ---------------- */
export const indicatorApi = {
  list: (params: Record<string, any>) => http.get<PageResult>('/api/indicator-versions', params),
  options: (projectId?: number) =>
    http.get<any[]>('/api/indicator-versions/options', projectId ? { projectId } : undefined),
  get: (id: number) => http.get<any>(`/api/indicator-versions/${id}`),
  indicators: (id: number) => http.get<any[]>(`/api/indicator-versions/${id}/indicators`),
  tree: (id: number) => http.get<any[]>(`/api/indicator-versions/${id}/tree`),
  import: (formData: FormData) => http.upload<any>('/api/indicator-versions/import', formData),
  revalidate: (id: number) => http.post<any>(`/api/indicator-versions/${id}/revalidate`),
  publish: (id: number) => http.post(`/api/indicator-versions/${id}/publish`),
  archive: (id: number) => http.post(`/api/indicator-versions/${id}/archive`),
  remove: (id: number) => http.delete(`/api/indicator-versions/${id}`),
  importBatches: (versionId?: number) =>
    http.get<any[]>('/api/indicator-versions/import-batches', versionId ? { versionId } : undefined),
  downloadTemplate: () => http.download('/api/indicator-versions/template', undefined, '指标体系导入模板.xlsx')
}

/** ---------------- 数据源 ---------------- */
export const dataSourceApi = {
  list: (params: Record<string, any>) => http.get<PageResult>('/api/data-sources', params),
  get: (id: number) => http.get<any>(`/api/data-sources/${id}`),
  dbTypes: () => http.get<any[]>('/api/data-sources/db-types'),
  create: (data: any) => http.post('/api/data-sources', data),
  update: (id: number, data: any) => http.put(`/api/data-sources/${id}`, data),
  remove: (id: number) => http.delete(`/api/data-sources/${id}`),
  test: (id: number) => http.post<any>(`/api/data-sources/${id}/test`),
  tables: (id: number, schema?: string) =>
    http.get<string[]>(`/api/data-sources/${id}/tables`, schema ? { schema } : undefined),
  fields: (id: number, table: string) => http.get<any[]>(`/api/data-sources/${id}/fields`, { table }),
  extract: (id: number, data: any) => http.post<any>(`/api/data-sources/${id}/extract`, data),
  validateSql: (querySql: string) => http.post<any>('/api/data-sources/validate-sql', { querySql })
}

/** ---------------- 数据批次 ---------------- */
export const dataBatchApi = {
  list: (params: Record<string, any>) => http.get<PageResult>('/api/data-batches', params),
  get: (id: number) => http.get<any>(`/api/data-batches/${id}`),
  import: (formData: FormData) => http.upload<any>('/api/data-batches/import', formData),
  records: (id: number, limit = 200) => http.get<any[]>(`/api/data-batches/${id}/records`, { limit }),
  errors: (id: number) => http.get<any[]>(`/api/data-batches/${id}/errors`),
  qualityReport: (id: number) => http.get<any>(`/api/data-batches/${id}/quality-report`),
  revalidate: (id: number) => http.post<any>(`/api/data-batches/${id}/revalidate`),
  remove: (id: number) => http.delete(`/api/data-batches/${id}`),
  downloadTemplate: () => http.download('/api/data-batches/template', undefined, '试验数据导入模板.xlsx'),
  exportErrors: (id: number) => http.download(`/api/data-batches/${id}/errors/export`, undefined, '错误明细.xlsx')
}

/** ---------------- 快照 ---------------- */
export const snapshotApi = {
  list: (params: Record<string, any>) => http.get<PageResult>('/api/snapshots', params),
  get: (id: number) => http.get<any>(`/api/snapshots/${id}`),
  overview: (id: number, maxObjects = 20) =>
    http.get<any>(`/api/snapshots/${id}/overview`, { maxObjects }),
  values: (id: number) => http.get<any[]>(`/api/snapshots/${id}/values`),
  create: (data: { batchId: number; indicatorVersionId: number; remark?: string }) =>
    http.post<any>('/api/snapshots', data),
  remove: (id: number) => http.delete(`/api/snapshots/${id}`)
}

/** ---------------- 评估任务 ---------------- */
export const taskApi = {
  list: (params: Record<string, any>) => http.get<PageResult>('/api/evaluation-tasks', params),
  recent: (limit = 10) => http.get<any[]>('/api/evaluation-tasks/recent', { limit }),
  create: (data: any) => http.post<any>('/api/evaluation-tasks', data),
  detail: (id: number) => http.get<any>(`/api/evaluation-tasks/${id}`),
  status: (id: number) => http.get<any>(`/api/evaluation-tasks/${id}/status`),
  history: (id: number) => http.get<any[]>(`/api/evaluation-tasks/${id}/history`),
  cancel: (id: number) => http.post(`/api/evaluation-tasks/${id}/cancel`),
  retry: (id: number) => http.post<any>(`/api/evaluation-tasks/${id}/retry`)
}

/** ---------------- 评估结果 ---------------- */
export interface ChartData {
  chartType: string
  title: string
  unit?: string
  description?: string
  dataSource?: string
  emptyReason?: string
  [key: string]: any
}

export const resultApi = {
  list: (params: Record<string, any>) => http.get<PageResult>('/api/evaluation-results', params),
  byTask: (taskId: number) => http.get<any[]>(`/api/evaluation-results/by-task/${taskId}`),
  full: (id: number, projectId?: number) =>
    http.get<any>(`/api/evaluation-results/${id}`, projectId ? { projectId } : undefined),
  text: (id: number) => http.get<any>(`/api/evaluation-results/${id}/text`),
  details: (id: number, params: Record<string, any>) =>
    http.get<PageResult>(`/api/evaluation-results/${id}/details`, params),
  charts: (params: { taskId?: number; resultId?: number; projectId?: number; chartType?: string }) =>
    http.get<Record<string, ChartData>>('/api/evaluation-results/charts', params),
  chart: (chartType: string, params: Record<string, any>) =>
    http.get<Record<string, ChartData>>(`/api/evaluation-results/charts/${chartType}`, params),
  compare: (params: { projectId?: number; taskId?: number }) =>
    http.get<any>('/api/evaluation-results/compare', params),
  review: (id: number, action: string, comment?: string) =>
    http.post(`/api/evaluation-results/${id}/review`, { action, comment }),
  publish: (id: number) => http.post(`/api/evaluation-results/${id}/publish`),
  archive: (id: number) => http.post(`/api/evaluation-results/${id}/archive`),
  exportAs: (id: number, type: 'EXCEL' | 'WORD' | 'PDF') =>
    http.post<any>(`/api/evaluation-results/${id}/exports`, { type })
}

/** ---------------- 报告 ---------------- */
export const reportApi = {
  list: (params: Record<string, any>) => http.get<PageResult>('/api/reports', params),
  get: (id: number) => http.get<any>(`/api/reports/${id}`),
  generate: (data: { taskId: number; resultId?: number; type: string }) =>
    http.post<any>('/api/reports', data),
  download: (id: number, fileName = '报告') => http.download(`/api/reports/${id}/download`, undefined, fileName)
}

/** ---------------- 算法基准 ---------------- */
export const baselineApi = {
  list: () => http.get<any[]>('/api/baselines'),
  run: (id: number) => http.post<any>(`/api/baselines/${id}/run`),
  runAll: () => http.post<any[]>('/api/baselines/run-all')
}
