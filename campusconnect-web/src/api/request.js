import axios from 'axios'

// Create axios instance
const request = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

// ==================== 全局请求去重 ====================
// 相同 POST/PUT/DELETE 请求在 pending 期间，后续重复请求自动取消
const pendingMap = new Map() // key → timestamp

function getRequestKey(config) {
  const { method, url, data } = config
  // GET 请求不去重（页面切换、列表刷新都需要正常发送）
  if (method === 'get' || method === 'options') return null
  return [method, url, JSON.stringify(data || '')].join('|')
}

// ==================== 请求拦截器 ====================
request.interceptors.request.use(
  (config) => {
    // 1. Token 注入
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // 2. 请求去重：同一请求 pending 期间禁止重复发送
    const key = getRequestKey(config)
    if (key) {
      if (pendingMap.has(key)) {
        const elapsed = Date.now() - pendingMap.get(key)
        if (elapsed < 3000) {
          // 3 秒内的重复请求直接取消，返回 { rejected: true } 让调用方静默处理
          const controller = new AbortController()
          config.signal = controller.signal
          controller.abort()
          console.warn(`[去重] 重复请求已拦截: ${config.method.toUpperCase()} ${config.url}`)
          return Promise.reject({ __dedup: true, message: '请勿重复提交' })
        }
        // 超过 3 秒可能是上次请求超时了，清除旧记录放行
        pendingMap.delete(key)
      }
      pendingMap.set(key, Date.now())
      // 存储 key 到 config 以便响应拦截器清理
      config.__dedupKey = key
    }

    // 3. GET 请求防缓存
    if (config.method === 'get') {
      config.params = { ...config.params, _t: Date.now() }
    }

    console.log(`[Request] ${config.method.toUpperCase()} ${config.url}`, config.data)
    return config
  },
  (error) => Promise.reject(error)
)

// ==================== 响应拦截器 ====================
request.interceptors.response.use(
  (response) => {
    // 请求完成，清理去重记录
    if (response.config.__dedupKey) {
      pendingMap.delete(response.config.__dedupKey)
    }
    console.log(`[Response] ${response.config.url}`, response.status)
    return response.data ?? response
  },
  (error) => {
    // 去重取消的请求，静默返回
    if (error?.__dedup) {
      return Promise.resolve({ code: 0, __dedup: true })
    }

    // 请求失败也清理去重记录，允许重试
    if (error?.config?.__dedupKey) {
      pendingMap.delete(error.config.__dedupKey)
    }

    const status = error?.response?.status
    const message = error?.response?.data?.message || error.message || '请求失败'

    if (status === 401 || status === 403) {
      console.warn('Access Denied:', status)
      window.dispatchEvent(new CustomEvent('auth:unauthorized'))
      return Promise.reject(new Error('没有权限访问'))
    }

    window.dispatchEvent(new CustomEvent('api:error', { detail: { message } }))
    return Promise.reject(error)
  }
)

export default request
