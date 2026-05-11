import { useEffect, useMemo, useState } from 'react'
import { Avatar, Card, Col, Empty, Row, Space, Statistic, Tag, Typography } from 'antd'
import ReactECharts from 'echarts-for-react'
import {
  UserOutlined,
  ScheduleOutlined,
  FileTextOutlined,
  TrophyOutlined,
} from '@ant-design/icons'
import useAuthStore from '@/store/auth'
import {
  overview,
  registrationTrend,
  passRate,
  majorDistribution,
} from '@/api/statistics'
import type { Overview, ChartItem } from '@/types'
import s from './dashboard.module.scss'

export default function Dashboard() {
  const user = useAuthStore((st) => st.user)
  const [loading, setLoading] = useState(true)
  const [stats, setStats] = useState<Overview | null>(null)
  const [trend, setTrend] = useState<ChartItem[]>([])
  const [pass, setPass] = useState<ChartItem[]>([])
  const [dist, setDist] = useState<ChartItem[]>([])

  useEffect(() => {
    Promise.all([overview(), registrationTrend(), passRate(), majorDistribution()])
      .then(([r1, r2, r3, r4]) => {
        setStats(r1.data)
        setTrend(r2.data)
        setPass(r3.data)
        setDist(r4.data)
      })
      .finally(() => setLoading(false))
  }, [])

  const today = new Date().toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  })

  const asNumber = (value: unknown) => {
    const num = Number(value ?? 0)
    return Number.isFinite(num) ? num : 0
  }

  const summaryItems = useMemo(
    () => [
      {
        key: 'users',
        label: '用户总数',
        value: stats?.userCount ?? 0,
        hint: '当前平台账号',
        icon: <UserOutlined />,
        className: s.blue,
        suffix: '人',
      },
      {
        key: 'plans',
        label: '考试计划数',
        value: stats?.planCount ?? 0,
        hint: '已建立计划',
        icon: <ScheduleOutlined />,
        className: s.green,
        suffix: '项',
      },
      {
        key: 'registrations',
        label: '报名总数',
        value: stats?.registrationCount ?? 0,
        hint: '累计提交记录',
        icon: <FileTextOutlined />,
        className: s.orange,
        suffix: '条',
      },
      {
        key: 'pass',
        label: '总合格率',
        value: Number((stats?.passRate ?? 0).toFixed(1)),
        hint: '所有成绩统计',
        icon: <TrophyOutlined />,
        className: s.purple,
        suffix: '%',
      },
    ],
    [stats]
  )

  const trendOption = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: trend.map((i) => i.label), boundaryGap: false },
    yAxis: { type: 'value' },
    grid: { left: 40, right: 20, top: 20, bottom: 40 },
    series: [
      {
        data: trend.map((i) => asNumber(i.value)),
        type: 'line',
        smooth: true,
        areaStyle: { color: 'rgba(30,64,175,0.15)' },
        itemStyle: { color: '#1e40af' },
        lineStyle: { width: 3 },
      },
    ],
  }

  const passTop = pass.slice(0, 10)
  const passOption = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', max: 100 },
    yAxis: { type: 'category', data: passTop.map((i) => i.label) },
    grid: { left: 110, right: 30, top: 10, bottom: 30 },
    series: [
      {
        data: passTop.map((i) => asNumber(i.value)),
        type: 'bar',
        itemStyle: { color: '#f59e0b', borderRadius: [0, 4, 4, 0] },
      },
    ],
  }

  const distOption = {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        data: dist.map((i) => ({ name: i.label, value: asNumber(i.value) })),
        label: { formatter: '{b}: {c}' },
      },
    ],
  }

  return (
    <div className={s.page}>
      <Card bordered={false} className={s.heroCard} loading={loading}>
        <Row gutter={[24, 24]} align="middle" justify="space-between">
          <Col xs={24} lg={16}>
            <Space direction="vertical" size={8}>
              <Tag color="blue" className={s.heroTag}>管理工作台</Tag>
              <Typography.Title level={2} className={s.heroTitle}>
                你好，{user?.realName || user?.username}
              </Typography.Title>
              <Typography.Paragraph className={s.heroDesc}>
                这里集中查看报名趋势、课程通过率和专业分布。界面已统一为更轻量的白色卡片风格，便于日常巡检和管理操作。
              </Typography.Paragraph>
            </Space>
          </Col>
          <Col xs={24} lg={8}>
            <div className={s.heroMeta}>
              <div className={s.heroMetaLabel}>当前日期</div>
              <div className={s.heroMetaValue}>{today}</div>
            </div>
          </Col>
        </Row>
      </Card>

      <Row gutter={[16, 16]}>
        {summaryItems.map((item) => (
          <Col xs={24} sm={12} xl={6} key={item.key}>
            <Card bordered={false} className={`${s.statCard} ${item.className}`} loading={loading}>
              <div className={s.statHeader}>
                <Avatar size={46} className={s.statAvatar} icon={item.icon} />
                <Tag className={s.statHint}>{item.hint}</Tag>
              </div>
              <Statistic title={item.label} value={item.value} suffix={item.suffix} />
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={24}>
          <Card bordered={false} title="报名趋势" className={s.chartCard} loading={loading}>
            {trend.length ? <ReactECharts option={trendOption} style={{ height: 300 }} /> : <Empty description="暂无趋势数据" />}
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card bordered={false} title="课程合格率 Top10" className={s.chartCard} loading={loading}>
            {passTop.length ? <ReactECharts option={passOption} style={{ height: 320 }} /> : <Empty description="暂无成绩数据" />}
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card bordered={false} title="专业计划分布" className={s.chartCard} loading={loading}>
            {dist.length ? <ReactECharts option={distOption} style={{ height: 320 }} /> : <Empty description="暂无分布数据" />}
          </Card>
        </Col>
      </Row>
    </div>
  )
}
