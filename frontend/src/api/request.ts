import axios, { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { message } from 'antd'
import useAuthStore from '@/store/auth'
import type { ApiResp } from '@/types'

const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = useAuthStore.getState().token
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (err) => Promise.reject(err)
)

request.interceptors.response.use(
  (resp: AxiosResponse) => {
    if (resp.config.responseType === 'blob') return resp
    const data = resp.data as ApiResp
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 200) return data as any
      message.error(data.msg || '请求失败')
      return Promise.reject(data)
    }
    return resp as any
  },
  (err) => {
    const status = err?.response?.status
    if (status === 401) {
      message.error('登录已过期，请重新登录')
      useAuthStore.getState().logout()
      const path = window.location.pathname
      if (path !== '/login' && path !== '/register') {
        window.location.href = '/login'
      }
    } else {
      message.error(err?.response?.data?.msg || err.message || '网络错误')
    }
    return Promise.reject(err)
  }
)

export default request
