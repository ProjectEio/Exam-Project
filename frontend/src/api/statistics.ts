import request from './request'
import type { ApiResp, ChartItem, Overview } from '@/types'

export const overview          = () => request.get<any, ApiResp<Overview>>('/statistics/overview')
export const registrationTrend = () => request.get<any, ApiResp<ChartItem[]>>('/statistics/registration-trend')
export const passRate          = () => request.get<any, ApiResp<ChartItem[]>>('/statistics/pass-rate')
export const majorDistribution = () => request.get<any, ApiResp<ChartItem[]>>('/statistics/major-distribution')
export const scoreStatusDist   = () => request.get<any, ApiResp<{ label: string; value: number }[]>>('/statistics/score-status')
