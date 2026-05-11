import request from './request'
import type { ApiResp, PageResult, User } from '@/types'

export const pageUser   = (params: any) => request.get<any, ApiResp<PageResult<User>>>('/users', { params })
export const detailUser = (id: number) => request.get<any, ApiResp<User>>(`/users/${id}`)
export const addUser    = (data: Partial<User> & { password?: string }) => request.post<any, ApiResp<void>>('/users', data)
export const updateUser = (data: Partial<User> & { password?: string }) => request.put<any, ApiResp<void>>('/users', data)
export const deleteUser = (id: number) => request.delete<any, ApiResp<void>>(`/users/${id}`)
export const resetPwd   = (id: number, newPassword: string) =>
  request.put<any, ApiResp<void>>(`/users/${id}/reset-password`, null, { params: { newPassword } })
