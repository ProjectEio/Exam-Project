import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { App, Card, Empty, List, Spin, Tag } from 'antd'
import {
  CalendarOutlined,
  FileTextOutlined,
  TrophyOutlined,
  UserOutlined,
  RightOutlined,
  EnvironmentOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import useAuthStore from '@/store/auth'
import { publishedPlan } from '@/api/plan'
import { myReg } from '@/api/registration'
import type { ExamPlan, Registration } from '@/types'

const STATUS_COLOR: Record<Registration['status'], string> = {
  PENDING: 'blue',
  APPROVED: 'green',
  REJECTED: 'red',
}
const STATUS_TEXT: Record<Registration['status'], string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
}

const MOTTOS = [
  '行而不辍，未来可期 —— 加油！',
  '每一次坚持，都在靠近梦想。',
  '今日不学，明日不会，唯有努力是最好的回答。',
  '路漫漫其修远兮，吾将上下而求索。',
  '愿你眼里有光，心中有梦，脚下有路。',
]

export default function StudentHome() {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const user = useAuthStore((s) => s.user)

  const [plans, setPlans] = useState<ExamPlan[]>([])
  const [regs, setRegs] = useState<Registration[]>([])
  const [loading, setLoading] = useState(false)

  const motto = useMemo(() => MOTTOS[Math.floor(Math.random() * MOTTOS.length)], [])
  const today = dayjs().format('YYYY 年 MM 月 DD 日 dddd')

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      setLoading(true)
      try {
        const [pRes, rRes] = await Promise.all([publishedPlan(), myReg()])
        if (cancelled) return
        setPlans((pRes.data || []).slice(0, 3))
        setRegs((rRes.data || []).slice(0, 5))
      } catch {
        if (!cancelled) message.error('数据加载失败')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [message])

  const shortcuts = [
    {
      key: 'plans',
      title: '考试计划',
      desc: '查看已发布的考试',
      icon: <CalendarOutlined />,
      color: 'from-blue-500 to-blue-700',
      path: '/student/plans',
    },
    {
      key: 'reg',
      title: '我的报名',
      desc: '查看报名审核进度',
      icon: <FileTextOutlined />,
      color: 'from-amber-400 to-orange-500',
      path: '/student/my-registrations',
    },
    {
      key: 'score',
      title: '我的成绩',
      desc: '历次考试成绩查询',
      icon: <TrophyOutlined />,
      color: 'from-emerald-400 to-emerald-600',
      path: '/student/my-scores',
    },
    {
      key: 'profile',
      title: '个人中心',
      desc: '维护个人资料',
      icon: <UserOutlined />,
      color: 'from-purple-400 to-purple-600',
      path: '/student/profile',
    },
  ]

  return (
    <div className="space-y-6">
      {/* 欢迎卡片 */}
      <div
        className="rounded-2xl shadow-soft p-8 text-white relative overflow-hidden"
        style={{
          background:
            'linear-gradient(120deg, #1e40af 0%, #2563eb 50%, #3b82f6 100%)',
        }}
      >
        <div className="absolute -right-8 -top-8 w-48 h-48 rounded-full bg-white/10" />
        <div className="absolute right-20 -bottom-12 w-40 h-40 rounded-full bg-white/10" />
        <div className="relative">
          <div className="text-3xl font-bold mb-2">
            你好，{user?.realName || user?.username || '同学'}！欢迎来到自学考试服务平台
          </div>
          <div className="text-white/90 text-base mb-1">{today}</div>
          <div className="text-white/80 text-sm italic">{motto}</div>
        </div>
      </div>

      {/* 快捷卡片 */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {shortcuts.map((s) => (
          <div
            key={s.key}
            onClick={() => navigate(s.path)}
            className={`cursor-pointer rounded-2xl shadow-soft p-5 bg-gradient-to-br ${s.color} text-white transition-all duration-200 hover:-translate-y-1 hover:shadow-lg`}
          >
            <div className="text-3xl mb-3 opacity-90">{s.icon}</div>
            <div className="text-lg font-semibold">{s.title}</div>
            <div className="text-white/80 text-xs mt-1">{s.desc}</div>
          </div>
        ))}
      </div>

      {/* 双栏 */}
      <Spin spinning={loading}>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* 最近发布的考试计划 */}
          <Card
            className="rounded-2xl shadow-soft"
            title={
              <span className="flex items-center gap-2">
                <CalendarOutlined className="text-primary" />
                最近发布的考试计划
              </span>
            }
            extra={
              <a onClick={() => navigate('/student/plans')} className="text-primary">
                查看全部 <RightOutlined />
              </a>
            }
          >
            {plans.length === 0 ? (
              <Empty description="暂无已发布的考试计划" />
            ) : (
              <List
                dataSource={plans}
                renderItem={(p) => (
                  <List.Item className="hover:bg-blue-50/40 rounded-lg px-2 transition">
                    <div className="w-full">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-gray-800">{p.planName}</span>
                        <Tag color="blue">
                          {p.examYear} {p.examTerm}
                        </Tag>
                      </div>
                      <div className="text-xs text-gray-500 mt-1 flex flex-wrap gap-3">
                        <span>
                          <CalendarOutlined /> {p.examDate || '—'}
                        </span>
                        <span>
                          <ClockCircleOutlined /> {p.startTime || '—'}~{p.endTime || '—'}
                        </span>
                        <span>
                          <EnvironmentOutlined /> {p.location || '—'}
                        </span>
                      </div>
                    </div>
                  </List.Item>
                )}
              />
            )}
          </Card>

          {/* 我的最近报名 */}
          <Card
            className="rounded-2xl shadow-soft"
            title={
              <span className="flex items-center gap-2">
                <FileTextOutlined className="text-accent" />
                我的最近报名
              </span>
            }
            extra={
              <a
                onClick={() => navigate('/student/my-registrations')}
                className="text-primary"
              >
                查看全部 <RightOutlined />
              </a>
            }
          >
            {regs.length === 0 ? (
              <Empty description="你还没有报名记录，去 看看考试计划 吧" />
            ) : (
              <List
                dataSource={regs}
                renderItem={(r) => (
                  <List.Item className="hover:bg-amber-50/40 rounded-lg px-2 transition">
                    <div className="w-full">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-gray-800">
                          {r.planName || `报名 #${r.registrationNo}`}
                        </span>
                        <Tag color={STATUS_COLOR[r.status]}>{STATUS_TEXT[r.status]}</Tag>
                      </div>
                      <div className="text-xs text-gray-500 mt-1 flex flex-wrap gap-3">
                        <span>课程：{r.courseName || '—'}</span>
                        <span>
                          <CalendarOutlined /> {r.examDate || '—'}
                        </span>
                        <span>
                          缴费：
                          <Tag
                            color={r.paymentStatus === 'PAID' ? 'green' : 'orange'}
                            className="!mr-0"
                          >
                            {r.paymentStatus === 'PAID' ? '已缴' : '未缴'}
                          </Tag>
                        </span>
                      </div>
                    </div>
                  </List.Item>
                )}
              />
            )}
          </Card>
        </div>
      </Spin>
    </div>
  )
}
