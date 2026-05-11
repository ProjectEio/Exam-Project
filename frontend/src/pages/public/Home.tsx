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

const { Header, Content, Footer } = Layout

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
      {/* ====== Header ====== */}
      <Header className={styles.header}>
        <div className={styles.headerInner}>
          <div className={styles.brand}>
            <Avatar size={40} className={styles.brandAvatar} icon={<SafetyCertificateOutlined />} />
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
                <Button type="primary" size="large" icon={<UserAddOutlined />} onClick={() => navigate('/register')}>
                  考生注册
                </Button>
              </>
            )}
          </Space>
        </div>
      </Header>

      <Content className={styles.content}>
        {/* ====== Hero Banner ====== */}
        <section className={styles.hero}>
          <div className={styles.heroInner}>
            <Tag color="blue" className={styles.heroTag}>
              公开访问
            </Tag>
            <Typography.Title level={1} className={styles.heroTitle}>
              自学考试信息公开展示
            </Typography.Title>
            <Typography.Paragraph className={styles.heroDesc}>
              已发布考试计划、考试时间和考点安排在首页直接公开展示。考生登录后可继续报名和查分，教师与管理员登录后进入管理工作台。
            </Typography.Paragraph>

            <Space wrap size={12} className={styles.heroActions}>
              <Button type="primary" size="large" icon={<ReadOutlined />} onClick={() => navigate(actionPath)}>
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
                className={styles.welcomeAlert}
                message={`欢迎回来，${user.realName || user.username}`}
                description="你可以继续浏览公开信息，也可以直接进入已登录工作台。"
              />
            )}

            {/* Stats strip */}
            <Row gutter={[24, 16]} className={styles.statsRow}>
              <Col xs={12} sm={6}>
                <Card bordered={false} className={styles.statCard}>
                  <Statistic title="已发布计划" value={plans.length} suffix="个" />
                </Card>
              </Col>
              <Col xs={12} sm={6}>
                <Card bordered={false} className={styles.statCard}>
                  <Statistic title="近期可查" value={upcomingCount} suffix="个" />
                </Card>
              </Col>
              <Col xs={12} sm={6}>
                <Card bordered={false} className={styles.statCard}>
                  <Statistic title="覆盖专业" value={majorCount} suffix="个" />
                </Card>
              </Col>
              <Col xs={12} sm={6}>
                <Card bordered={false} className={styles.statCard}>
                  <Statistic title="最近考试日" value={nextExamDate} />
                </Card>
              </Col>
            </Row>
          </div>
        </section>

        {/* ====== Main Content ====== */}
        <div className={styles.mainContainer}>
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
            {/* Exam Plans */}
            <Col xs={24} lg={17}>
              <Card
                title="已发布考试计划"
                bordered={false}
                className={styles.planCard}
                extra={
                  <Button type="link" onClick={() => navigate(user ? actionPath : '/login')}>
                    {user ? '进入工作台' : '登录后继续办理'}
                  </Button>
                }
              >
                {loading ? (
                  <Skeleton active paragraph={{ rows: 5 }} />
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

            {/* Sidebar */}
            <Col xs={24} lg={7}>
              <Card title="快速入门" bordered={false} className={styles.sideCard}>
                <Steps
                  direction="vertical"
                  size="small"
                  current={-1}
                  items={[
                    {
                      title: '浏览公开信息',
                      description: '无需登录，直接查看已发布的考试计划与安排。',
                      icon: <ReadOutlined />,
                    },
                    {
                      title: '按需登录',
                      description: '需要报名、查分或管理数据时再登录。',
                      icon: <LoginOutlined />,
                    },
                    {
                      title: '进入工作台',
                      description: '考生进入服务中心，教师和管理员进入后台。',
                      icon: <ScheduleOutlined />,
                    },
                  ]}
                />

                <Divider style={{ margin: '20px 0' }} />

                <Row gutter={[16, 16]}>
                  <Col span={12}>
                    <Statistic title="学期批次" value={termCount} />
                  </Col>
                  <Col span={12}>
                    <Statistic title="公开说明" value="即时可看" />
                  </Col>
                </Row>

                <Divider style={{ margin: '20px 0' }} />

                <List
                  split={false}
                  dataSource={[
                    {
                      icon: <CalendarOutlined />,
                      text: '公开查看考试计划',
                    },
                    {
                      icon: <LoginOutlined />,
                      text: '按需登录办理业务',
                    },
                    {
                      icon: <TeamOutlined />,
                      text: '区分角色进入工作台',
                    },
                  ]}
                  renderItem={(item) => (
                    <List.Item className={styles.quickItem}>
                      <Space size={10}>
                        <Avatar size={28} className={styles.quickAvatar} icon={item.icon} />
                        <span className={styles.quickText}>{item.text}</span>
                      </Space>
                    </List.Item>
                  )}
                />
              </Card>
            </Col>
          </Row>
        </div>
      </Content>

      {/* ====== Footer ====== */}
      <Footer className={styles.footer}>
        <div className={styles.footerInner}>
          <Space split={<Divider type="vertical" />} size={16}>
            <span>省考试院 · 自学考试信息公开平台</span>
            <span>首页默认公开，无需登录即可浏览</span>
          </Space>
          <Typography.Text type="secondary">
            © {new Date().getFullYear()} 自学考试信息管理系统
          </Typography.Text>
        </div>
      </Footer>
    </Layout>
  )
}
