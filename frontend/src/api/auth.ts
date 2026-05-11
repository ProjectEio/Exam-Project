import request from './request'
import type { ApiResp, UserInfo, User } from '@/types'

export interface LoginDTO { username: string; password: string }
export interface LoginVO extends UserInfo { token: string }
export interface RegisterDTO {
  username: string
  password: string
  realName: string
  idCard?: string
  phone?: string
  email?: string
  gender?: string
}

export const login = (data: LoginDTO) => request.post<any, ApiResp<LoginVO>>('/auth/login', data)
export const register = (data: RegisterDTO) => request.post<any, ApiResp<void>>('/auth/register', data)
export const getInfo = () => request.get<any, ApiResp<User>>('/auth/info')
