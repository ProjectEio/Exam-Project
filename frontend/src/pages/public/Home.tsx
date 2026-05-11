import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Avatar,
  Button,
  Card,
  Col,
  Divider,
  Empty,
  Layout,
  List,
  Row,
  Skeleton,
  Space,
  Statistic,
  Steps,
  Tag,
  Typography,
} from 'antd'
import {
  CalendarOutlined,
  ClockCircleOutlined,
  EnvironmentOutlined,
  LoginOutlined,
  ReadOutlined,
  SafetyCertificateOutlined,
  ScheduleOutlined,
  TeamOutlined,
  UserAddOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import { useNavigate } from 'react-router-dom'
import { publishedPlan } from '@/api/plan'
import useAuthStore from '@/store/auth'
import type { ExamPlan, Role } from '@/types'
import styles from './Home.module.scss'

const { Header, Content } = Layout

const planStatusText: Record<ExamPlan['status'], string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  FINISHED: '已结束',
}

function getWorkbenchPath(role: Role) {
  return role === 'STUDENT' ? '/student/home' : '/admin/dashboard'
}

export default function PublicHome() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)

  const [plans, setPlans] = useState<ExamPlan[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    let cancelled = false

    const load = async () => {
      setLoading(true)
      setLoadError('')
      try {
        const res = await publishedPlan()
        if (cancelled) return

        const records = [...(res.data || [])].sort((left, right) => {
          const leftDate = left.examDate ? dayjs(left.examDate).valueOf() : Number.MAX_SAFE_INTEGER
          const rightDate = right.examDate ? dayjs(right.examDate).valueOf() : Number.MAX_SAFE_INTEGER
          return leftDate - rightDate
        })

        setPlans(records)
      } catch {
        if (!cancelled) {
          setLoadError('公开首页暂时无法加载考试计划，请稍后再试。')
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    load()

    return () => {
      cancelled = true
    }
  }, [])

  const previewPlans = useMemo(() => plans.slice(0, 6), [plans])

  const upcomingCount = useMemo(() => {
    const today = dayjs().startOf('day')
    return plans.filter((plan) => !plan.examDate || dayjs(plan.examDate).isAfter(today.subtract(1, 'day'))).length
  }, [plans])

  const majorCount = useMemo(() => {
    return new Set(plans.map((plan) => plan.majorName).filter(Boolean)).size
  }, [plans])

  const termCount = useMemo(() => {
    return new Set(plans.map((plan) => `${plan.examYear}-${plan.examTerm}`)).size
  }, [plans])

  const nextExamDate = useMemo(() => {
    const today = dayjs().startOf('day')
    const nextPlan = plans.find((plan) => plan.examDate && dayjs(plan.examDate).isAfter(today.subtract(1, 'day')))
    return nextPlan?.examDate || '待公布'
  }, [plans])

  const actionTitle = user
    ? user.role === 'STUDENT'
      ? '进入考生中心'
      : '进入管理工作台'
    : '登录办理业务'

  const actionPath = user ? getWorkbenchPath(user.role) : '/login'

  return (
    <Layout className={styles.page}>
      <Header className={styles.header}>
        <div className={styles.brand}>
          <Avatar size={42} className={styles.brandAvatar} icon={<SafetyCertificateOutlined />} />
          <div>
            <Typography.Title level={5} className={styles.brandTitle}>
              省考试院 · 自学考试信息公开
            </Typography.Title>
            <Typography.Text className={styles.brandSubtitle}>
              首页默认公开，先浏览，再决定是否登录
            </Typography.Text>
          </div>
        </div>

        <Space wrap size={12}>
          {user ? (
            <Button type="primary" size="large" onClick={() => navigate(actionPath)}>
              {actionTitle}
            </Button>
          ) : (
            <>
              <Button size="large" icon={<LoginOutlined />} onClick={() => navigate('/login')}>
                登录
              </Button>
              <Button
                type="primary"
                size="large"
                icon={<UserAddOutlined />}
                onClick={() => navigate('/register')}
              >
                考生注册
              </Button>
            </>
          )}
        </Space>
      </Header>

      <Content className={styles.content}>
        <div className={styles.container}>
          <Row gutter={[24, 24]}>
            <Col xs={24} xl={15}>
              <Card bordered={false} className={styles.heroCard}>
                <Space direction="vertical" size={16} style={{ width: '100%' }}>
                  <Tag color="blue" className={styles.heroTag}>
                    公开访问
                  </Tag>
                  <Typography.Title level={1} className={styles.heroTitle}>
                    所有人都可以先看首页，再选择登录
                  </Typography.Title>
                  <Typography.Paragraph className={styles.heroDescription}>
                    已发布考试计划、考试时间和考点安排在首页直接公开展示。考生登录后可继续报名和查分，教师与管理员登录后进入管理工作台。
                  </Typography.Paragraph>
                  <Space wrap size={12}>
                    <Button
                      type="primary"
                      size="large"
                      icon={<ReadOutlined />}
                      onClick={() => navigate(actionPath)}
                    >
                      {actionTitle}
                    </Button>
                    {!user && (
                      <Button size="large" onClick={() => navigate('/register')}>
                        先注册账号
                      </Button>
                    )}
                  </Space>

                  {user && (
                    <Alert
                      showIcon
                      type="success"
                      message={`欢迎回来，${user.realName || user.username}`}
                      description="你可以继续浏览公开信息，也可以直接进入已登录工作台。"
                    />
                  )}

                  <Divider className={styles.heroDivider} />

                  <Row gutter={[16, 16]}>
                    <Col xs={12} md={6}>
                      <Statistic title="已发布计划" value={plans.length} />
                    </Col>
                    <Col xs={12} md={6}>
                      <Statistic title="近期可查" value={upcomingCount} />
                    </Col>
                    <Col xs={12} md={6}>
                      <Statistic title="覆盖专业" value={majorCount} />
                    </Col>
                    <Col xs={12} md={6}>
                      <Statistic title="最近考试日" value={nextExamDate} />
                    </Col>
                  </Row>
                </Space>
              </Card>
            </Col>

            <Col xs={24} xl={9}>
              <Card title="你现在可以做什么" bordered={false} className={styles.sideCard}>
                <List
                  dataSource={[
                    {
                      icon: <CalendarOutlined />,
                      title: '公开查看考试计划',
                      description: '无需登录即可查看已发布考试计划、考试日期、时段和地点。',
                    },
                    {
                      icon: <LoginOutlined />,
                      title: '按需登录办理业务',
                      description: '需要报名、查成绩或管理数据时，再进入登录页即可。',
                    },
                    {
                      icon: <TeamOutlined />,
                      title: '区分角色进入工作台',
                      description: '考生进入服务中心，教师和管理员进入统一管理后台。',
                    },
                  ]}
                  renderItem={(item) => (
                    <List.Item className={styles.actionItem}>
                      <Space align="start" size={14}>
                        <Avatar className={styles.actionAvatar} icon={item.icon} />
                        <div>
                          <div className={styles.actionTitle}>{item.title}</div>
                          <Typography.Text type="secondary">{item.description}</Typography.Text>
                        </div>
                      </Space>
                    </List.Item>
                  )}
                />
              </Card>
            </Col>
          </Row>

          {loadError && (
            <Alert
              showIcon
              type="warning"
              className={styles.loadAlert}
              message="公开考试计划加载失败"
              description={loadError}
            />
          )}

          <Row gutter={[24, 24]}>
            <Col xs={24} lg={15}>
              <Card
                title="已发布考试计划"
                bordered={false}
                className={styles.listCard}
                extra={
                  <Button type="link" onClick={() => navigate(user ? actionPath : '/login')}>
                    {user ? '进入工作台' : '登录后继续办理'}
                  </Button>
                }
              >
                {loading ? (
                  <Skeleton active paragraph={{ rows: 6 }} />
                ) : previewPlans.length === 0 ? (
                  <Empty description="暂无已发布考试计划" />
                ) : (
                  <List
                    dataSource={previewPlans}
                    renderItem={(plan) => (
                      <List.Item className={styles.planItem}>
                        <Row gutter={[16, 12]} align="middle" style={{ width: '100%' }}>
                          <Col flex="auto">
                            <Space wrap size={[8, 8]}>
                              <Tag color={plan.examTerm === '上' ? 'blue' : 'cyan'}>
                                {plan.examYear} {plan.examTerm}
                              </Tag>
                              {plan.majorName && <Tag>{plan.majorName}</Tag>}
                              <Tag color={plan.status === 'PUBLISHED' ? 'processing' : 'default'}>
                                {planStatusText[plan.status]}
                              </Tag>
                            </Space>
                            <Typography.Title level={5} className={styles.planTitle}>
                              {plan.planName}
                            </Typography.Title>
                            <Space wrap size="large" className={styles.planMeta}>
                              <span>
                                <CalendarOutlined /> {plan.examDate || '待定'}
                              </span>
                              <span>
                                <ClockCircleOutlined /> {plan.startTime || '待定'} - {plan.endTime || '待定'}
                              </span>
                              <span>
                                <EnvironmentOutlined /> {plan.location || '地点待公布'}
                              </span>
                            </Space>
                          </Col>
                          <Col>
                            <Button onClick={() => navigate(user ? actionPath : '/login')}>
                              {user ? '继续办理' : '登录后报名'}
                            </Button>
                          </Col>
                        </Row>
                      </List.Item>
                    )}
                  />
                )}
              </Card>
            </Col>

            <Col xs={24} lg={9}>
              <Card title="使用流程" bordered={false} className={styles.sideCard}>
                <Steps
                  direction="vertical"
                  size="small"
                  items={[
                    {
                      title: '先浏览公开信息',
                      description: '访客可以直接查看已发布考试计划与考试安排。',
                      icon: <ReadOutlined />,
                    },
                    {
                      title: '按需登录',
                      description: '需要报名、查分或管理数据时，再进入登录。',
                      icon: <LoginOutlined />,
                    },
                    {
                      title: '进入对应工作台',
                      description: '考生进入服务中心，教师和管理员进入管理工作台。',
                      icon: <ScheduleOutlined />,
                    },
                  ]}
                />

                <Divider />

                <Row gutter={[16, 16]}>
                  <Col span={12}>
                    <Statistic title="学期批次" value={termCount} />
                  </Col>
                  <Col span={12}>
                    <Statistic title="公开说明" value="即时可看" />
                  </Col>
                </Row>
              </Card>
            </Col>
          </Row>
        </div>
      </Content>
    </Layout>
  )
}