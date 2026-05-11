import { useEffect, useMemo, useState } from 'react'
import {
  App,
  Button,
  Card,
  Empty,
  Input,
  Progress,
  Select,
  Spin,
  Tag,
} from 'antd'
import {
  CalendarOutlined,
  ClockCircleOutlined,
  EnvironmentOutlined,
  SearchOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { publishedPlan } from '@/api/plan'
import { doReg, myReg } from '@/api/registration'
import type { ExamPlan, Registration } from '@/types'

export default function StudentPlanList() {
  const { message, modal } = App.useApp()

  const [plans, setPlans] = useState<ExamPlan[]>([])
  const [regs, setRegs] = useState<Registration[]>([])
  const [loading, setLoading] = useState(false)
  const [regging, setRegging] = useState<number | null>(null)

  const [year, setYear] = useState<number | undefined>(undefined)
  const [term, setTerm] = useState<'上' | '下' | undefined>(undefined)
  const [keyword, setKeyword] = useState('')

  const regPlanIds = useMemo(
    () => new Set(regs.map((r) => r.planId)),
    [regs]
  )

  const load = async () => {
    setLoading(true)
    try {
      const [pRes, rRes] = await Promise.all([publishedPlan(), myReg()])
      setPlans(pRes.data || [])
      setRegs(rRes.data || [])
    } catch {
      // request 拦截器已处理
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const filtered = useMemo(() => {
    return plans.filter((p) => {
      if (year && p.examYear !== year) return false
      if (term && p.examTerm !== term) return false
      if (keyword) {
        const k = keyword.toLowerCase()
        const hit =
          p.planName?.toLowerCase().includes(k) ||
          p.planCode?.toLowerCase().includes(k) ||
          p.courseName?.toLowerCase().includes(k) ||
          p.location?.toLowerCase().includes(k)
        if (!hit) return false
      }
      return true
    })
  }, [plans, year, term, keyword])

  const handleReg = (p: ExamPlan) => {
    if (!p.id) return
    modal.confirm({
      title: '确认报名',
      content: (
        <div>
          确定要报名 <b>{p.planName}</b> 吗？
          <div className="text-xs text-gray-500 mt-2">
            提交后需要管理员审核，审核通过后可下载准考证。
          </div>
        </div>
      ),
      okText: '确认报名',
      cancelText: '取消',
      onOk: async () => {
        setRegging(p.id!)
        try {
          await doReg(p.id!)
          message.success('报名提交成功，请等待审核')
          await load()
        } catch {
          // ignore
        } finally {
          setRegging(null)
        }
      },
    })
  }

  return (
    <div className="space-y-4">
      {/* 顶部筛选 */}
      <Card className="rounded-2xl shadow-soft">
        <div className="flex flex-wrap items-center gap-3">
          <span className="font-semibold text-gray-700">筛选：</span>
          <Select
            allowClear
            placeholder="年份"
            value={year}
            onChange={setYear}
            style={{ width: 120 }}
            options={[
              { label: '2024', value: 2024 },
              { label: '2025', value: 2025 },
              { label: '2026', value: 2026 },
            ]}
          />
          <Select
            allowClear
            placeholder="学期"
            value={term}
            onChange={setTerm}
            style={{ width: 120 }}
            options={[
              { label: '上半年', value: '上' },
              { label: '下半年', value: '下' },
            ]}
          />
          <Input
            allowClear
            placeholder="搜索计划名 / 课程 / 地点"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 280 }}
          />
          <div className="ml-auto text-sm text-gray-500">
            共 <span className="text-primary font-semibold">{filtered.length}</span> 个计划
          </div>
        </div>
      </Card>

      {/* 卡片网格 */}
      <Spin spinning={loading}>
        {filtered.length === 0 ? (
          <Card className="rounded-2xl shadow-soft">
            <Empty description="未找到符合条件的考试计划" />
          </Card>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filtered.map((p) => {
              const registered = p.id ? regPlanIds.has(p.id) : false
              const cap = p.capacity || 0
              const used = p.registeredCount || 0
              const percent = cap > 0 ? Math.round((used / cap) * 100) : 0
              const full = cap > 0 && used >= cap

              return (
                <Card
                  key={p.id}
                  className="rounded-2xl shadow-soft transition-all duration-200 hover:-translate-y-1 hover:shadow-lg"
                  styles={{ body: { padding: 20 } }}
                >
                  <div className="flex items-start justify-between mb-3">
                    <div className="font-bold text-base text-gray-800 line-clamp-2">
                      {p.planName}
                    </div>
                    <Tag color="blue" className="!mr-0 shrink-0">
                      {p.examYear} {p.examTerm}
                    </Tag>
                  </div>

                  <div className="space-y-2 text-sm text-gray-600 mb-4">
                    <div className="flex items-center gap-2">
                      <span className="text-gray-400 shrink-0">课程：</span>
                      <span>{p.courseName || '—'}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <CalendarOutlined className="text-primary" />
                      <span>{p.examDate || '待定'}</span>
                      <ClockCircleOutlined className="text-primary ml-2" />
                      <span>
                        {p.startTime || '—'} ~ {p.endTime || '—'}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <EnvironmentOutlined className="text-primary" />
                      <span>{p.location || '待定'}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <TeamOutlined className="text-primary" />
                      <span>
                        已报名 {used} / {cap}
                      </span>
                    </div>
                    <Progress
                      percent={percent}
                      size="small"
                      strokeColor={full ? '#f5222d' : '#1e40af'}
                      showInfo={false}
                    />
                    <div className="text-xs text-gray-400">
                      报名期：{p.registerStart || '—'} ~ {p.registerEnd || '—'}
                    </div>
                  </div>

                  <Button
                    type="primary"
                    block
                    disabled={registered || full}
                    loading={regging === p.id}
                    onClick={() => handleReg(p)}
                    style={{
                      background: registered || full ? undefined : '#1e40af',
                    }}
                  >
                    {registered ? '已报名' : full ? '已满员' : '立即报名'}
                  </Button>
                </Card>
              )
            })}
          </div>
        )}
      </Spin>
    </div>
  )
}
