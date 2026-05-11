import request from './request'
import type { ApiResp, PageResult, ExamPlan } from '@/types'

export const pagePlan      = (params: any) => request.get<any, ApiResp<PageResult<ExamPlan>>>('/exam-plans', { params })
export const publishedPlan = (params?: any) => request.get<any, ApiResp<ExamPlan[]>>('/exam-plans/published', { params })
export const detailPlan    = (id: number) => request.get<any, ApiResp<ExamPlan>>(`/exam-plans/${id}`)
export const addPlan       = (d: ExamPlan) => request.post<any, ApiResp<void>>('/exam-plans', d)
export const updPlan       = (d: ExamPlan) => request.put<any, ApiResp<void>>('/exam-plans', d)
export const delPlan       = (id: number) => request.delete<any, ApiResp<void>>(`/exam-plans/${id}`)
export const planStatus    = (id: number, status: string) =>
  request.put<any, ApiResp<void>>(`/exam-plans/${id}/status`, null, { params: { status } })
