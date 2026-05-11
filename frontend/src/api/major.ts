import request from './request'
import type { ApiResp, PageResult, Major } from '@/types'

export const pageMajor = (params: any) => request.get<any, ApiResp<PageResult<Major>>>('/majors', { params })
export const allMajor  = () => request.get<any, ApiResp<Major[]>>('/majors/all')
export const addMajor  = (d: Major) => request.post<any, ApiResp<void>>('/majors', d)
export const updMajor  = (d: Major) => request.put<any, ApiResp<void>>('/majors', d)
export const delMajor  = (id: number) => request.delete<any, ApiResp<void>>(`/majors/${id}`)
