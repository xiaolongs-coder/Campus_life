import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // GET 防缓存
    if (config.method === 'get') {
      config.params = { ...config.params, _t: Date.now() }
    }

    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
  (response) => response.data ?? response,
  (error) => {
    const status = error?.response?.status
    const message = error?.response?.data?.message || error.message || '请求失败'

    if (status === 401 || status === 403) {
      window.dispatchEvent(new CustomEvent('auth:unauthorized'))
      return Promise.reject(new Error('没有权限访问'))
    }

    window.dispatchEvent(new CustomEvent('api:error', { detail: { message } }))
    return Promise.reject(error)
  }
)

// 重试装饰器
export function withRetry(requestFn, retries = 3, delay = 1000) {
  return async (...args) => {
    for (let i = 0; i < retries; i++) {
      try {
        return await requestFn(...args)
      } catch (error) {
        if (i === retries - 1) throw error
        await new Promise(resolve => setTimeout(resolve, delay * (i + 1)))
      }
    }
  }
}

export default request
