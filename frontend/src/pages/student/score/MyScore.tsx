import { useEffect, useMemo, useState } from 'react'
import { App, Card, Collapse, Empty, Statistic, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  BookOutlined,
  CheckCircleOutlined,
  LineChartOutlined,
} from '@ant-design/icons'
import { myScore } from '@/api/score'
import type { Score } from '@/types'

const STATUS_COLOR: Record<NonNullable<Score['status']>, string> = {
  PASS: 'green',
  FAIL: 'red',
  ABSENT: 'orange',
}
const STATUS_TEXT: Record<NonNullable<Score['status']>, string> = {
  PASS: '合格',
  FAIL: '不合格',
  ABSENT: '缺考',
}

interface Group {
  key: string
  year: number
  term: '上' | '下'
  items: Score[]
}

export default function MyScore() {
  const { message } = App.useApp()
  const [data, setData] = useState<Score[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    const load = async () => {
      setLoading(true)
      try {
        const res = await myScore()
        setData(res.data || [])
      } catch {
        message.error('成绩加载失败')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [message])

  const initialLoading = loading && data.length === 0

  const stats = useMemo(() => {
    const total = data.length
    const pass = data.filter((s) => s.status === 'PASS').length
    const validScores = data.filter(
      (s) => s.status !== 'ABSENT' && typeof s.score === 'number'
    )
    const avg =
      validScores.length === 0
        ? 0
        : validScores.reduce((sum, s) => sum + (s.score || 0), 0) /
          validScores.length
    return { total, pass, avg: Math.round(avg * 10) / 10 }
  }, [data])

  const groups = useMemo<Group[]>(() => {
    const map = new Map<string, Group>()
    data.forEach((s) => {
      const key = `${s.examYear}-${s.examTerm}`
      if (!map.has(key)) {
        map.set(key, {
          key,
          year: s.examYear,
          term: s.examTerm,
          items: [],
        })
      }
      map.get(key)!.items.push(s)
    })
    return Array.from(map.values()).sort((a, b) => {
      if (a.year !== b.year) return b.year - a.year
      // 下 在 上 之后，但显示时让较新的（下）在前
      if (a.term === b.term) return 0
      return a.term === '下' ? -1 : 1
    })
  }, [data])

  const columns: ColumnsType<Score> = [
    {
      title: '课程代码',
      dataIndex: 'courseCode',
      key: 'courseCode',
      width: 140,
      render: (v?: string) => (
        <span className="font-mono text-xs">{v || '—'}</span>
      ),
    },
    {
      title: '课程名称',
      dataIndex: 'courseName',
      key: 'courseName',
      render: (v?: string) => v || '—',
    },
    {
      title: '分数',
      dataIndex: 'score',
      key: 'score',
      width: 100,
      render: (v: number, r) =>
        r.status === 'ABSENT' ? (
          <span className="text-gray-400">缺考</span>
        ) : (
          <span
            className={`font-bold ${
              v >= 60 ? 'text-emerald-600' : 'text-red-500'
            }`}
          >
            {v}
          </span>
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (s?: Score['status']) =>
        s ? <Tag color={STATUS_COLOR[s]}>{STATUS_TEXT[s]}</Tag> : '—',
    },
    {
      title: '考试日期',
      dataIndex: 'examDate',
      key: 'examDate',
      width: 130,
      render: (v?: string) => v || '—',
    },
  ]

  return (
    <div className="space-y-4">
      {/* 统计卡 */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card className="rounded-2xl shadow-soft" loading={initialLoading}>
          <Statistic
            title={
              <span className="flex items-center gap-2 text-gray-600">
                <BookOutlined /> 已考课程数
              </span>
            }
            value={stats.total}
            valueStyle={{ color: '#1e40af' }}
            suffix="门"
          />
        </Card>
        <Card className="rounded-2xl shadow-soft" loading={initialLoading}>
          <Statistic
            title={
              <span className="flex items-center gap-2 text-gray-600">
                <CheckCircleOutlined /> 合格课程数
              </span>
            }
            value={stats.pass}
            valueStyle={{ color: '#10b981' }}
            suffix={`/ ${stats.total} 门`}
          />
        </Card>
        <Card className="rounded-2xl shadow-soft" loading={initialLoading}>
          <Statistic
            title={
              <span className="flex items-center gap-2 text-gray-600">
                <LineChartOutlined /> 平均分
              </span>
            }
            value={stats.avg}
            precision={1}
            valueStyle={{ color: '#f59e0b' }}
            suffix="分"
          />
        </Card>
      </div>

      {/* 分组成绩 */}
      {initialLoading ? (
        <Card className="rounded-2xl shadow-soft" loading={initialLoading} />
      ) : groups.length === 0 ? (
          <Card className="rounded-2xl shadow-soft">
            <Empty description="暂无成绩记录" />
          </Card>
        ) : (
          <Collapse
            defaultActiveKey={groups.slice(0, 2).map((g) => g.key)}
            className="rounded-2xl shadow-soft bg-white"
            items={groups.map((g) => ({
              key: g.key,
              label: (
                <div className="flex items-center justify-between pr-2">
                  <span className="font-semibold text-gray-800">
                    {g.year} 年 {g.term}半年
                  </span>
                  <span className="text-xs text-gray-500">
                    共 {g.items.length} 门
                  </span>
                </div>
              ),
              children: (
                <Table<Score>
                  rowKey={(r) => r.id ?? `${r.courseCode}-${r.examYear}-${r.examTerm}`}
                  dataSource={g.items}
                  columns={columns}
                  pagination={false}
                  size="middle"
                />
              ),
            }))}
          />
        )}
    </div>
  )
}
