import request from './request'
import type { ApiResp, PageResult, Course } from '@/types'

export const pageCourse = (params: any) => request.get<any, ApiResp<PageResult<Course>>>('/courses', { params })
export const allCourse  = () => request.get<any, ApiResp<Course[]>>('/courses/all')
export const byMajor    = (majorId: number) => request.get<any, ApiResp<Course[]>>(`/courses/by-major/${majorId}`)
export const addCourse  = (d: Course) => request.post<any, ApiResp<void>>('/courses', d)
export const updCourse  = (d: Course) => request.put<any, ApiResp<void>>('/courses', d)
export const delCourse  = (id: number) => request.delete<any, ApiResp<void>>(`/courses/${id}`)
export const majorLinks = (majorId: number) => request.get<any, ApiResp<{ majorId: number; courseId: number }[]>>(`/courses/links/${majorId}`)
export const saveLinks  = (majorId: number, ids: number[]) => request.post<any, ApiResp<void>>(`/courses/links/${majorId}`, ids)
