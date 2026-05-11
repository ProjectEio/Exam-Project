import request from './request'
import type { ApiResp, PageResult, Score } from '@/types'

export interface ImportResult { success: number; fail: number; errors: string[] }

export const pageScore = (params: any) => request.get<any, ApiResp<PageResult<Score>>>('/scores', { params })
export const myScore   = () => request.get<any, ApiResp<Score[]>>('/scores/mine')
export const addScore  = (d: Score) => request.post<any, ApiResp<void>>('/scores', d)
export const updScore  = (d: Score) => request.put<any, ApiResp<void>>('/scores', d)
export const delScore  = (id: number) => request.delete<any, ApiResp<void>>(`/scores/${id}`)
export const importScore = (file: File) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post<any, ApiResp<ImportResult>>('/scores/import', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
