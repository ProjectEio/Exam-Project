import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { App, Avatar, Button, Card, Col, Empty, List, Row, Space, Statistic, Tag, Typography } from 'antd'
import {
  CalendarOutlined,
  CheckCircleOutlined,
  FileTextOutlined,
  TrophyOutlined,
  UserOutlined,
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
  const approvedCount = useMemo(
    () => regs.filter((item) => item.status === 'APPROVED').length,
    [regs]
  )
  const nextExamDate = useMemo(() => plans.find((item) => item.examDate)?.examDate || '待定', [plans])
  const initialLoading = loading && plans.length === 0 && regs.length === 0

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
      avatarStyle: { background: '#e8f1ff', color: '#1677ff' },
      path: '/student/plans',
    },
    {
      key: 'reg',
      title: '我的报名',
      desc: '查看报名审核进度',
      icon: <FileTextOutlined />,
      avatarStyle: { background: '#fff7e6', color: '#fa8c16' },
      path: '/student/my-registrations',
    },
    {
      key: 'score',
      title: '我的成绩',
      desc: '历次考试成绩查询',
      icon: <TrophyOutlined />,
      avatarStyle: { background: '#edfdf3', color: '#389e0d' },
      path: '/student/my-scores',
    },
    {
      key: 'profile',
      title: '个人中心',
      desc: '维护个人资料',
      icon: <UserOutlined />,
      avatarStyle: { background: '#f4f0ff', color: '#531dab' },
      path: '/student/profile',
    },
  ]

  return (
    <div className="space-y-6">
      <Card
        bordered={false}
        loading={initialLoading}
        style={{ borderRadius: 24, boxShadow: '0 18px 40px rgba(15, 23, 42, 0.06)' }}
      >
        <Row gutter={[24, 24]} align="middle">
          <Col xs={24} lg={16}>
            <Space direction="vertical" size={10}>
              <Tag color="blue">考生首页</Tag>
              <Typography.Title level={2} style={{ margin: 0 }}>
                你好，{user?.realName || user?.username || '同学'}
              </Typography.Title>
              <Typography.Text type="secondary">{today}</Typography.Text>
              <Typography.Paragraph style={{ marginBottom: 0, color: '#475569' }}>
                {motto}
              </Typography.Paragraph>
            </Space>
          </Col>
          <Col xs={24} lg={8}>
            <Row gutter={[16, 16]}>
              <Col span={12}>
                <Statistic title="已发布计划" value={plans.length} />
              </Col>
              <Col span={12}>
                <Statistic title="我的报名" value={regs.length} />
              </Col>
              <Col span={12}>
                <Statistic title="已通过" value={approvedCount} />
              </Col>
              <Col span={12}>
                <Statistic title="最近考试日" value={nextExamDate} />
              </Col>
            </Row>
          </Col>
        </Row>
      </Card>

      <Row gutter={[16, 16]}>
        {shortcuts.map((item) => (
          <Col xs={24} sm={12} xl={6} key={item.key}>
            <Card
              hoverable
              bordered={false}
              onClick={() => navigate(item.path)}
              style={{ borderRadius: 20, height: '100%', boxShadow: '0 12px 30px rgba(15, 23, 42, 0.05)' }}
            >
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Avatar size={52} style={item.avatarStyle} icon={item.icon} />
                <div>
                  <Typography.Title level={5} style={{ margin: '0 0 6px 0' }}>
                    {item.title}
                  </Typography.Title>
                  <Typography.Text type="secondary">{item.desc}</Typography.Text>
                </div>
                <Button type="link" style={{ padding: 0 }}>
                  进入
                </Button>
              </Space>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[24, 24]}>
          <Col xs={24} lg={12}>
          <Card
            bordered={false}
            loading={initialLoading}
            style={{ borderRadius: 20, height: '100%', boxShadow: '0 18px 40px rgba(15, 23, 42, 0.06)' }}
            title={
              <span className="flex items-center gap-2">
                <CalendarOutlined className="text-primary" />
                最近发布的考试计划
              </span>
            }
            extra={
              <Button type="link" onClick={() => navigate('/student/plans')}>
                查看全部
              </Button>
            }
          >
            {plans.length === 0 ? (
              <Empty description="暂无已发布的考试计划" />
            ) : (
              <List
                dataSource={plans}
                renderItem={(p) => (
                  <List.Item>
                    <Space direction="vertical" size={8} style={{ width: '100%' }}>
                      <Space wrap size={[8, 8]}>
                        <Tag color="blue">
                          {p.examYear} {p.examTerm}
                        </Tag>
                        {p.majorName && <Tag>{p.majorName}</Tag>}
                      </Space>
                      <Typography.Text strong>{p.planName}</Typography.Text>
                      <Space wrap size="large" style={{ color: '#64748b' }}>
                        <span>
                          <CalendarOutlined /> {p.examDate || '—'}
                        </span>
                        <span>
                          <ClockCircleOutlined /> {p.startTime || '—'} ~ {p.endTime || '—'}
                        </span>
                        <span>
                          <EnvironmentOutlined /> {p.location || '—'}
                        </span>
                      </Space>
                    </Space>
                  </List.Item>
                )}
              />
            )}
          </Card>
          </Col>
          <Col xs={24} lg={12}>
          <Card
            bordered={false}
            loading={initialLoading}
            style={{ borderRadius: 20, height: '100%', boxShadow: '0 18px 40px rgba(15, 23, 42, 0.06)' }}
            title={
              <span className="flex items-center gap-2">
                <FileTextOutlined className="text-accent" />
                我的最近报名
              </span>
            }
            extra={
              <Button type="link" onClick={() => navigate('/student/my-registrations')}>
                查看全部
              </Button>
            }
          >
            {regs.length === 0 ? (
              <Empty description="你还没有报名记录，去 看看考试计划 吧" />
            ) : (
              <List
                dataSource={regs}
                renderItem={(r) => (
                  <List.Item>
                    <div className="w-full">
                      <div className="flex items-center justify-between gap-3">
                        <Typography.Text strong>
                          {r.planName || `报名 #${r.registrationNo}`}
                        </Typography.Text>
                        <Tag color={STATUS_COLOR[r.status]}>{STATUS_TEXT[r.status]}</Tag>
                      </div>
                      <div className="text-xs text-gray-500 mt-2 flex flex-wrap gap-3">
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
          </Col>
        </Row>
    </div>
  )
}
