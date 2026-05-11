// 全局类型定义

export type Role = 'ADMIN' | 'TEACHER' | 'STUDENT'

export interface UserInfo {
  userId: number
  username: string
  realName?: string
  role: Role
}

export interface ApiResp<T = unknown> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface PageQuery {
  current?: number
  size?: number
  keyword?: string
}

export interface User {
  id: number
  username: string
  role: Role
  realName?: string
  idCard?: string
  phone?: string
  email?: string
  gender?: string
  status: number
  createTime?: string
}

export interface Major {
  id?: number
  majorCode: string
  majorName: string
  level: string
  totalCredits?: number
  description?: string
  status?: number
}

export interface Course {
  id?: number
  courseCode: string
  courseName: string
  credit?: number
  courseType: string
  description?: string
}

export interface ExamPlan {
  id?: number
  planCode: string
  planName: string
  examYear: number
  examTerm: '上' | '下'
  courseId: number
  majorId?: number
  examDate?: string
  startTime?: string
  endTime?: string
  location?: string
  capacity: number
  registeredCount?: number
  registerStart?: string
  registerEnd?: string
  status: 'DRAFT' | 'PUBLISHED' | 'FINISHED'
  remark?: string
  courseName?: string
  majorName?: string
}

export interface Registration {
  id: number
  studentId: number
  planId: number
  registrationNo: string
  admissionTicketNo?: string
  paymentStatus: 'PAID' | 'UNPAID'
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  auditRemark?: string
  registerTime?: string
  studentName?: string
  studentIdCard?: string
  planName?: string
  planCode?: string
  courseName?: string
  examDate?: string
  examLocation?: string
  startTime?: string
  endTime?: string
}

export interface Score {
  id?: number
  studentId: number
  courseId: number
  planId?: number
  examYear: number
  examTerm: '上' | '下'
  score: number
  status?: 'PASS' | 'FAIL' | 'ABSENT'
  examDate?: string
  studentName?: string
  courseCode?: string
  courseName?: string
}

export interface Overview {
  userCount: number
  studentCount: number
  majorCount: number
  courseCount: number
  planCount: number
  publishedPlanCount: number
  registrationCount: number
  approvedCount: number
  scoreCount: number
  passRate: number
}

export interface ChartItem {
  label: string
  value: number
}
