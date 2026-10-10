import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

/** 统一响应结构 */
export interface ApiResult<T = any> {
  code: number
  message: string
  data: T
  timestamp?: number
}

/** 分页结构 */
export interface PageResult<T = any> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
  hasNext: boolean
}

const TOKEN_KEY = 'ees_token'

/**
 * 登录令牌存放在 sessionStorage，而不是 localStorage。
 *
 * <p>localStorage 没有有效期：关掉浏览器、重启电脑后它仍在，于是「打开网址就自动以上次那个账号进入系统」，
 * 共用电脑上等于把上一个人的身份（可能还是管理员）交给了下一个人。
 * sessionStorage 随浏览器会话结束而清空，关闭浏览器后再打开就必须重新登录。</p>
 *
 * <p>代价：sessionStorage 按标签页隔离，**新开标签页需要重新登录**；同一标签页内刷新不受影响。
 * 令牌本身的有效期仍由后端 `ees.security.jwt-expire-minutes`（默认 8 小时）控制。</p>
 */
export function getToken(): string {
  return sessionStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  sessionStorage.removeItem(TOKEN_KEY)
}

/**
 * 清理历史版本遗留在 localStorage 里的令牌。
 *
 * <p>此前令牌存在 localStorage，升级后若不清理，那个令牌会一直留在本机（且最长生效 8 小时），
 * 与「不再持久保存登录态」的意图相悖。应用启动时调用一次即可。</p>
 */
export function dropLegacyPersistentToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    // 浏览器禁用本地存储（隐私模式等）时忽略
  }
}

const service: AxiosInstance = axios.create({
  baseURL: '/',
  timeout: 120000,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' }
})

let redirecting = false

service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers = config.headers || {}
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response: AxiosResponse) => {
    // 二进制下载直接返回原始响应
    if (response.config.responseType === 'blob') {
      return response
    }
    const body = response.data as ApiResult
    if (body && typeof body.code === 'number') {
      if (body.code === 0) {
        return body
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response.data
  },
  (error) => {
    const status = error?.response?.status
    const body = error?.response?.data as ApiResult | undefined
    const message = body?.message || error?.message || '网络异常'

    if (status === 401) {
      if (!redirecting) {
        redirecting = true
        clearToken()
        ElMessageBox.alert('登录状态已失效，请重新登录', '提示', {
          confirmButtonText: '重新登录',
          type: 'warning',
          callback: () => {
            redirecting = false
            window.location.href = '#/login'
            window.location.reload()
          }
        }).catch(() => {
          redirecting = false
        })
      }
      return Promise.reject(new Error(message))
    }
    if (status === 403) {
      ElMessage.error('无权访问该资源')
      return Promise.reject(new Error(message))
    }
    if (error?.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试或检查后端服务状态')
      return Promise.reject(new Error('请求超时'))
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

/** 统一请求入口，返回 data 部分 */
export async function request<T = any>(config: AxiosRequestConfig): Promise<T> {
  const res: any = await service(config)
  return res.data as T
}

export const http = {
  get<T = any>(url: string, params?: Record<string, any>): Promise<T> {
    return request<T>({ url, method: 'get', params })
  },
  post<T = any>(url: string, data?: any): Promise<T> {
    return request<T>({ url, method: 'post', data })
  },
  put<T = any>(url: string, data?: any): Promise<T> {
    return request<T>({ url, method: 'put', data })
  },
  delete<T = any>(url: string, params?: Record<string, any>): Promise<T> {
    return request<T>({ url, method: 'delete', params })
  },
  /** multipart 上传 */
  upload<T = any>(url: string, formData: FormData): Promise<T> {
    return request<T>({
      url,
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 600000
    })
  },
  /** 下载文件（自动携带令牌，文件名从响应头解析） */
  async download(url: string, params?: Record<string, any>, fallbackName = 'download'): Promise<void> {
    const res: any = await service({
      url,
      method: 'get',
      params,
      responseType: 'blob',
      timeout: 600000
    })
    const disposition: string = res.headers?.['content-disposition'] || ''
    let fileName = fallbackName
    const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
    const plain = /filename="?([^";]+)"?/i.exec(disposition)
    if (star && star[1]) {
      fileName = decodeURIComponent(star[1])
    } else if (plain && plain[1]) {
      fileName = decodeURIComponent(plain[1])
    }
    const blob = new Blob([res.data])
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
  }
}

export default service
