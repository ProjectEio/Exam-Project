import { useEffect, useState } from 'react'
import { Spin } from 'antd'
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

  const trendOption = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: trend.map((i) => i.label), boundaryGap: false },
    yAxis: { type: 'value' },
    grid: { left: 40, right: 20, top: 20, bottom: 40 },
    series: [
      {
        data: trend.map((i) => i.value),
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
        data: passTop.map((i) => i.value),
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
        data: dist.map((i) => ({ name: i.label, value: i.value })),
        label: { formatter: '{b}: {c}' },
      },
    ],
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center h-96">
        <Spin size="large" tip="加载中..." />
      </div>
    )
  }

  return (
    <div>
      <div className="mb-4">
        <h2 className="text-xl font-semibold m-0">
          你好，{user?.realName || user?.username}！
        </h2>
        <p className="text-gray-500 mt-1 mb-0">今天是 {today}</p>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
        <div className={`${s.statCard} ${s.blue}`}>
          <div className={s.label}>用户总数</div>
          <div className={s.value}>{stats?.userCount ?? 0}</div>
          <UserOutlined className={s.icon} />
        </div>
        <div className={`${s.statCard} ${s.green}`}>
          <div className={s.label}>考试计划数</div>
          <div className={s.value}>{stats?.planCount ?? 0}</div>
          <ScheduleOutlined className={s.icon} />
        </div>
        <div className={`${s.statCard} ${s.orange}`}>
          <div className={s.label}>报名总数</div>
          <div className={s.value}>{stats?.registrationCount ?? 0}</div>
          <FileTextOutlined className={s.icon} />
        </div>
        <div className={`${s.statCard} ${s.purple}`}>
          <div className={s.label}>总合格率</div>
          <div className={s.value}>{(stats?.passRate ?? 0).toFixed(1)}%</div>
          <TrophyOutlined className={s.icon} />
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="md:col-span-2 bg-white rounded-lg shadow-soft p-4">
          <h3 className="text-base font-semibold m-0 mb-2">报名趋势</h3>
          <ReactECharts option={trendOption} style={{ height: 280 }} />
        </div>
        <div className="bg-white rounded-lg shadow-soft p-4">
          <h3 className="text-base font-semibold m-0 mb-2">课程合格率 Top10</h3>
          <ReactECharts option={passOption} style={{ height: 320 }} />
        </div>
        <div className="bg-white rounded-lg shadow-soft p-4">
          <h3 className="text-base font-semibold m-0 mb-2">专业-计划数分布</h3>
          <ReactECharts option={distOption} style={{ height: 320 }} />
        </div>
      </div>
    </div>
  )
}
