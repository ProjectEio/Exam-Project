import request from './request'
import type { ApiResp, PageResult, Registration } from '@/types'

export const pageReg   = (params: any) => request.get<any, ApiResp<PageResult<Registration>>>('/registrations', { params })
export const myReg     = () => request.get<any, ApiResp<Registration[]>>('/registrations/mine')
export const detailReg = (id: number, studentId?: number) => request.get<any, ApiResp<Registration>>(`/registrations/${id}`, { params: { studentId } })
export const doReg     = (planId: number) => request.post<any, ApiResp<Registration>>(`/registrations/register/${planId}`)
export const auditReg  = (id: number, status: string, remark?: string, studentId?: number) =>
  request.put<any, ApiResp<Registration>>(`/registrations/${id}/audit`, null, { params: { status, remark, studentId } })
export const cancelReg = (id: number, studentId?: number) => request.delete<any, ApiResp<void>>(`/registrations/${id}`, { params: { studentId } })
export const ticketFile = (id: number, studentId?: number) =>
  request.get<any, any>(`/registrations/${id}/ticket`, { params: { studentId }, responseType: 'blob' })
