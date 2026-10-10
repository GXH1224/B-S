import dayjs from 'dayjs'

/** 数值格式化：保留指定小数位，空值显示占位符 */
export function fmtNum(v: any, scale = 2, placeholder = '-'): string {
  if (v === null || v === undefined || v === '') return placeholder
  const n = Number(v)
  if (!Number.isFinite(n)) return placeholder
  return n.toFixed(scale)
}

/** 分数展示：0~100，保留 2 位 */
export function fmtScore(v: any): string {
  return fmtNum(v, 2)
}

/** 权重 / 贡献值展示：保留 4 位 */
export function fmtWeight(v: any): string {
  const n = Number(v)
  if (!Number.isFinite(n)) return '-'
  // 权重常为小数，去掉无意义的末尾零
  return String(Number(n.toFixed(4)))
}

export function fmtPercent(v: any, scale = 2): string {
  const n = Number(v)
  if (!Number.isFinite(n)) return '-'
  return `${(n * 100).toFixed(scale)}%`
}

export function fmtDateTime(v: any, pattern = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!v) return '-'
  const d = dayjs(v)
  return d.isValid() ? d.format(pattern) : String(v)
}

export function fmtDate(v: any): string {
  return fmtDateTime(v, 'YYYY-MM-DD')
}

export function fmtDuration(ms: any): string {
  const n = Number(ms)
  if (!Number.isFinite(n) || n <= 0) return '-'
  if (n < 1000) return `${n} ms`
  if (n < 60000) return `${(n / 1000).toFixed(2)} s`
  return `${Math.floor(n / 60000)} 分 ${((n % 60000) / 1000).toFixed(0)} 秒`
}

export function fmtFileSize(bytes: any): string {
  const n = Number(bytes)
  if (!Number.isFinite(n) || n <= 0) return '-'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(2)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 安全解析后端以 TEXT 保存的 JSON 字段 */
export function parseJson<T = any>(text: any, fallback: T): T {
  if (text === null || text === undefined || text === '') return fallback
  if (typeof text === 'object') return text as T
  try {
    const v = JSON.parse(String(text))
    return (v ?? fallback) as T
  } catch {
    return fallback
  }
}

/** ---------------- 字典 ---------------- */

export const ALGORITHM_OPTIONS = [
  { value: 'WEIGHTED', label: '加权平均法', desc: '对标准化指标进行综合评分和排序，输出每项指标的标准化值、权重与贡献值' },
  { value: 'FUZZY', label: '模糊综合评判', desc: '基于权重和隶属度矩阵输出完整综合评判向量与评判等级' },
  { value: 'AHP', label: '层次分析法（AHP）', desc: '计算层级指标权重并进行一致性检验（CR < 0.1）' },
  { value: 'COMBO', label: '组合评估流程', desc: '先由 AHP 计算权重，再驱动加权平均法或模糊综合评判' }
]

export const ALGORITHM_LABEL: Record<string, string> = {
  WEIGHTED: '加权平均法',
  FUZZY: '模糊综合评判',
  AHP: '层次分析法',
  COMBO: '组合评估流程'
}

export const TASK_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  VALIDATING: '校验中',
  QUEUED: '已排队',
  RUNNING: '运行中',
  SUCCESS: '成功',
  FAILED: '失败',
  CANCELED: '已取消',
  TIMEOUT: '已超时',
  PENDING_REVIEW: '待审核',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档'
}

export const TASK_STATUS_TYPE: Record<string, string> = {
  DRAFT: 'info',
  VALIDATING: 'warning',
  QUEUED: 'warning',
  RUNNING: 'primary',
  SUCCESS: 'success',
  FAILED: 'danger',
  CANCELED: 'info',
  TIMEOUT: 'danger',
  PENDING_REVIEW: 'warning',
  PUBLISHED: 'success',
  ARCHIVED: 'info'
}

export const DIRECTION_LABEL: Record<string, string> = {
  POSITIVE: '正向',
  NEGATIVE: '负向',
  INTERVAL: '区间型'
}

export const VERSION_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  VALIDATED: '已校验',
  PUBLISHED: '已发布',
  INVALID: '校验不通过',
  ARCHIVED: '已归档'
}

export const VERSION_STATUS_TYPE: Record<string, string> = {
  DRAFT: 'info',
  VALIDATED: 'primary',
  PUBLISHED: 'success',
  INVALID: 'danger',
  ARCHIVED: 'warning'
}

export const BATCH_STATUS_LABEL: Record<string, string> = {
  IMPORTED: '已导入',
  VALIDATED: '校验通过',
  FAILED: '校验未通过',
  SNAPSHOTTED: '已生成快照'
}

export const SNAPSHOT_STATUS_LABEL: Record<string, string> = {
  GENERATED: '已生成',
  USED: '已被任务使用',
  INVALID: '已失效'
}

export const PROJECT_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '进行中',
  ARCHIVED: '已归档'
}

export const REVIEW_LABEL: Record<string, string> = {
  NONE: '未提交',
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已退回'
}

export const PUBLISH_LABEL: Record<string, string> = {
  DRAFT: '未发布',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档'
}

export const AUDIT_MODULE_LABEL: Record<string, string> = {
  AUTH: '登录审计',
  DATA: '数据审计',
  TASK: '任务审计',
  CONFIG: '配置审计',
  EXPORT: '导出审计',
  SYSTEM: '系统审计'
}

/** 等级标签样式类 */
export function levelClass(level?: string): string {
  const key = level && ['优', '良', '中', '差'].includes(level) ? level : '未评级'
  return `ees-level-tag ees-level-${key}`
}

/** 按风险排序的质量报告条目 */
export function qualityBlockingText(report: any): string {
  const reasons = report?.blockingReasons
  if (!reasons || !reasons.length) return ''
  return reasons.join('；')
}
