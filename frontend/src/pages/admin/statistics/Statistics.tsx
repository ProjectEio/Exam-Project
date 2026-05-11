import { useEffect, useState } from 'react'
import { Skeleton } from 'antd'
import ReactECharts from 'echarts-for-react'
import {
  overview,
  registrationTrend,
  passRate,
  majorDistribution,
  scoreStatusDist,
} from '@/api/statistics'
import type { Overview, ChartItem } from '@/types'

export default function Statistics() {
  const [loading, setLoading] = useState(true)
  const [stats, setStats] = useState<Overview | null>(null)
  const [trend, setTrend] = useState<ChartItem[]>([])
  const [pass, setPass] = useState<ChartItem[]>([])
  const [dist, setDist] = useState<ChartItem[]>([])
  const [scoreDist, setScoreDist] = useState<ChartItem[]>([])

  useEffect(() => {
    Promise.all([
      overview(),
      registrationTrend(),
      passRate(),
      majorDistribution(),
      scoreStatusDist(),
    ])
      .then(([r1, r2, r3, r4, r5]) => {
        setStats(r1.data)
        setTrend(r2.data)
        setPass(r3.data)
        setDist(r4.data)
        setScoreDist(r5.data)
      })
      .finally(() => setLoading(false))
  }, [])

  const asNumber = (value: unknown) => {
    const num = Number(value ?? 0)
    return Number.isFinite(num) ? num : 0
  }

  const cards: { label: string; value: number | string; color: string }[] = [
    { label: '用户总数', value: stats?.userCount ?? 0, color: '#1e40af' },
    { label: '学生总数', value: stats?.studentCount ?? 0, color: '#10b981' },
    { label: '专业总数', value: stats?.majorCount ?? 0, color: '#f59e0b' },
    { label: '课程总数', value: stats?.courseCount ?? 0, color: '#7c3aed' },
    { label: '考试计划', value: stats?.planCount ?? 0, color: '#0ea5e9' },
    { label: '已发布计划', value: stats?.publishedPlanCount ?? 0, color: '#06b6d4' },
    { label: '报名总数', value: stats?.registrationCount ?? 0, color: '#ec4899' },
    { label: '总合格率', value: `${(stats?.passRate ?? 0).toFixed(1)}%`, color: '#ef4444' },
  ]

  const trendOption = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: trend.map((i) => i.label), boundaryGap: false },
    yAxis: { type: 'value' },
    grid: { left: 40, right: 20, top: 30, bottom: 40 },
    series: [{
      data: trend.map((i) => asNumber(i.value)),
      type: 'line',
      smooth: true,
      areaStyle: { color: 'rgba(30,64,175,0.15)' },
      itemStyle: { color: '#1e40af' },
      lineStyle: { width: 3 },
    }],
  }

  const passOption = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'category', data: pass.map((i) => i.label), axisLabel: { rotate: 30, interval: 0 } },
    yAxis: { type: 'value', max: 100, name: '%' },
    grid: { left: 50, right: 20, top: 30, bottom: 80 },
    series: [{
      data: pass.map((i) => asNumber(i.value)),
      type: 'bar',
      itemStyle: { color: '#f59e0b', borderRadius: [4, 4, 0, 0] },
    }],
  }

  const distOption = {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: dist.map((i) => ({ name: i.label, value: asNumber(i.value) })),
      label: { formatter: '{b}: {c}' },
    }],
  }

  const scoreColorMap: Record<string, string> = { PASS: '#10b981', FAIL: '#ef4444', ABSENT: '#f59e0b' }
  const scoreLabelMap: Record<string, string> = { PASS: '合格', FAIL: '不合格', ABSENT: '缺考' }
  const scoreOption = {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: '70%',
      data: scoreDist.map((i) => ({
        name: scoreLabelMap[i.label] || i.label,
        value: asNumber(i.value),
        itemStyle: { color: scoreColorMap[i.label] },
      })),
    }],
  }

  const chartSkeleton = (
    <div style={{ minHeight: 320, display: 'flex', alignItems: 'center' }}>
      <Skeleton active title={false} paragraph={{ rows: 9 }} style={{ width: '100%' }} />
    </div>
  )

  return (
    <div>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4">
        {cards.map((c) => (
          <div key={c.label}
            className="bg-white rounded-lg shadow-soft p-4 border-l-4"
            style={{ borderLeftColor: c.color }}
          >
            {loading ? (
              <Skeleton active title={{ width: '46%' }} paragraph={{ rows: 1 }} />
            ) : (
              <>
                <div className="text-gray-500 text-sm">{c.label}</div>
                <div className="text-2xl font-bold mt-1" style={{ color: c.color }}>{c.value}</div>
              </>
            )}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-white rounded-lg shadow-soft p-4">
          <h3 className="text-base font-semibold m-0 mb-2">报名趋势</h3>
          {loading ? chartSkeleton : <ReactECharts option={trendOption} style={{ height: 320 }} />}
        </div>
        <div className="bg-white rounded-lg shadow-soft p-4">
          <h3 className="text-base font-semibold m-0 mb-2">各课程合格率</h3>
          {loading ? chartSkeleton : <ReactECharts option={passOption} style={{ height: 320 }} />}
        </div>
        <div className="bg-white rounded-lg shadow-soft p-4">
          <h3 className="text-base font-semibold m-0 mb-2">专业-计划数分布</h3>
          {loading ? chartSkeleton : <ReactECharts option={distOption} style={{ height: 320 }} />}
        </div>
        <div className="bg-white rounded-lg shadow-soft p-4">
          <h3 className="text-base font-semibold m-0 mb-2">成绩状态分布</h3>
          {loading ? chartSkeleton : <ReactECharts option={scoreOption} style={{ height: 320 }} />}
        </div>
      </div>
    </div>
  )
}
