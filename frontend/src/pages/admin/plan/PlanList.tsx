import { useEffect, useState } from 'react'
import {
  Table, Button, Input, Space, Tag, Modal, Form, Popconfirm, App,
  InputNumber, Select, DatePicker, TimePicker, Progress,
  Card, Row, Col, Typography,
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { pagePlan, addPlan, updPlan, delPlan, planStatus } from '@/api/plan'
import { allCourse } from '@/api/course'
import { allMajor } from '@/api/major'
import type { ExamPlan, Course, Major } from '@/types'

type Status = 'DRAFT' | 'PUBLISHED' | 'FINISHED'
const statusColor: Record<Status, string> = { DRAFT: 'default', PUBLISHED: 'green', FINISHED: 'gray' }
const statusText: Record<Status, string> = { DRAFT: '草稿', PUBLISHED: '已发布', FINISHED: '已结束' }

export default function PlanList() {
  const { message } = App.useApp()
  const [data, setData] = useState<ExamPlan[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [query, setQuery] = useState<any>({
    current: 1, size: 10, keyword: '', examYear: undefined, examTerm: undefined, status: undefined,
  })
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<ExamPlan | null>(null)
  const [courses, setCourses] = useState<Course[]>([])
  const [majors, setMajors] = useState<Major[]>([])
  const [form] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const res = await pagePlan(query)
      setData(res.data.records)
      setTotal(res.data.total)
    } finally {
      setLoading(false)
    }
  }
  useEffect(() => { load() }, [query])
  useEffect(() => {
    allCourse().then((r) => setCourses(r.data))
    allMajor().then((r) => setMajors(r.data))
  }, [])

  const onAdd = () => { setEditing(null); form.resetFields(); setOpen(true) }
  const onEdit = (p: ExamPlan) => {
    setEditing(p)
    form.setFieldsValue({
      ...p,
      examDate: p.examDate ? dayjs(p.examDate) : null,
      startTime: p.startTime ? dayjs(p.startTime, 'HH:mm:ss') : null,
      endTime: p.endTime ? dayjs(p.endTime, 'HH:mm:ss') : null,
      registerStart: p.registerStart ? dayjs(p.registerStart) : null,
      registerEnd: p.registerEnd ? dayjs(p.registerEnd) : null,
    })
    setOpen(true)
  }
  const onSave = async () => {
    const v = await form.validateFields()
    const payload: any = {
      ...v,
      examDate: v.examDate ? v.examDate.format('YYYY-MM-DD') : undefined,
      startTime: v.startTime ? v.startTime.format('HH:mm:ss') : undefined,
      endTime: v.endTime ? v.endTime.format('HH:mm:ss') : undefined,
      registerStart: v.registerStart ? v.registerStart.format('YYYY-MM-DD HH:mm:ss') : undefined,
      registerEnd: v.registerEnd ? v.registerEnd.format('YYYY-MM-DD HH:mm:ss') : undefined,
    }
    if (editing) await updPlan({ ...editing, ...payload })
    else await addPlan({ status: 'DRAFT', ...payload })
    message.success(editing ? '更新成功' : '新增成功')
    setOpen(false)
    load()
  }
  const onDel = async (id: number) => { await delPlan(id); message.success('删除成功'); load() }
  const onStatus = async (id: number, st: string) => {
    await planStatus(id, st)
    message.success('状态已更新')
    load()
  }

  const columns = [
    { title: '计划代码', dataIndex: 'planCode', width: 130, fixed: 'left' as const },
    { title: '计划名称', dataIndex: 'planName', width: 180 },
    {
      title: '年份学期', width: 110,
      render: (_: any, r: ExamPlan) => <Tag color="blue">{r.examYear} {r.examTerm}</Tag>,
    },
    { title: '课程', dataIndex: 'courseName', width: 160 },
    { title: '专业', dataIndex: 'majorName', width: 140 },
    { title: '考试日期', dataIndex: 'examDate', width: 110 },
    {
      title: '时间', width: 160,
      render: (_: any, r: ExamPlan) => `${r.startTime || ''} ~ ${r.endTime || ''}`,
    },
    { title: '地点', dataIndex: 'location', width: 120 },
    {
      title: '容量', width: 150,
      render: (_: any, r: ExamPlan) => (
        <div>
          <div className="text-xs mb-1">{r.registeredCount || 0} / {r.capacity}</div>
          <Progress
            percent={Math.min(100, Math.round(((r.registeredCount || 0) / (r.capacity || 1)) * 100))}
            size="small"
            showInfo={false}
          />
        </div>
      ),
    },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (s: Status) => <Tag color={statusColor[s]}>{statusText[s]}</Tag>,
    },
    {
      title: '操作', width: 280, fixed: 'right' as const,
      render: (_: any, r: ExamPlan) => (
        <Space wrap>
          <Button size="small" icon={<EditOutlined />} onClick={() => onEdit(r)}>编辑</Button>
          {r.status === 'DRAFT' && (
            <Button size="small" type="primary" onClick={() => onStatus(r.id!, 'PUBLISHED')}>发布</Button>
          )}
          {r.status === 'PUBLISHED' && (
            <Button size="small" onClick={() => onStatus(r.id!, 'FINISHED')}>结束</Button>
          )}
          <Popconfirm title="确认删除该计划？" onConfirm={() => onDel(r.id!)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Card bordered={false} className="mb-4 rounded-2xl shadow-soft">
        <Row gutter={[24, 24]} align="middle" justify="space-between">
          <Col xs={24} xl={14}>
            <Typography.Title level={3} style={{ margin: 0 }}>
              考试计划管理
            </Typography.Title>
            <Typography.Paragraph style={{ margin: '8px 0 0', color: '#64748b' }}>
              管理计划创建、发布、容量和考试安排。筛选区与表格区已统一为更轻量的白色信息面板。
            </Typography.Paragraph>
          </Col>
          <Col xs={24} xl={10}>
            <div className="flex flex-wrap gap-3 items-center justify-end">
        <Select placeholder="年份" allowClear style={{ width: 110 }} value={query.examYear}
          onChange={(v) => setQuery({ ...query, examYear: v, current: 1 })}
          options={[{ value: 2024 }, { value: 2025 }, { value: 2026 }]} />
        <Select placeholder="学期" allowClear style={{ width: 100 }} value={query.examTerm}
          onChange={(v) => setQuery({ ...query, examTerm: v, current: 1 })}
          options={[{ value: '上' }, { value: '下' }]} />
        <Select placeholder="状态" allowClear style={{ width: 130 }} value={query.status}
          onChange={(v) => setQuery({ ...query, status: v, current: 1 })}
          options={[
            { value: 'DRAFT', label: '草稿' },
            { value: 'PUBLISHED', label: '已发布' },
            { value: 'FINISHED', label: '已结束' },
          ]} />
        <Input.Search placeholder="搜索代码/名称" allowClear style={{ width: 220 }}
          onSearch={(v) => setQuery({ ...query, keyword: v, current: 1 })} />
        <Button type="primary" icon={<PlusOutlined />} onClick={onAdd}>新增计划</Button>
            </div>
          </Col>
        </Row>
      </Card>

      <Card bordered={false} className="rounded-2xl shadow-soft">
        <Table rowKey="id" dataSource={data} columns={columns as any} loading={loading}
          scroll={{ x: 1700 }}
          pagination={{
            current: query.current, pageSize: query.size, total, showSizeChanger: true,
            onChange: (page, size) => setQuery({ ...query, current: page, size }),
          }} />
      </Card>

      <Modal title={editing ? '编辑考试计划' : '新增考试计划'} open={open} width={720}
        onCancel={() => setOpen(false)} onOk={onSave} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false}>
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item label="计划代码" name="planCode" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item label="计划名称" name="planName" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item label="年份" name="examYear" rules={[{ required: true }]}>
              <Select options={[{ value: 2024 }, { value: 2025 }, { value: 2026 }]} />
            </Form.Item>
            <Form.Item label="学期" name="examTerm" rules={[{ required: true }]}>
              <Select options={[{ value: '上' }, { value: '下' }]} />
            </Form.Item>
            <Form.Item label="课程" name="courseId" rules={[{ required: true }]}>
              <Select showSearch optionFilterProp="label"
                options={courses.map((c) => ({ value: c.id, label: `${c.courseCode} ${c.courseName}` }))} />
            </Form.Item>
            <Form.Item label="专业" name="majorId">
              <Select allowClear showSearch optionFilterProp="label"
                options={majors.map((m) => ({ value: m.id, label: `${m.majorCode} ${m.majorName}` }))} />
            </Form.Item>
            <Form.Item label="考试日期" name="examDate"><DatePicker className="w-full" /></Form.Item>
            <Form.Item label="地点" name="location"><Input /></Form.Item>
            <Form.Item label="开始时间" name="startTime">
              <TimePicker className="w-full" format="HH:mm:ss" />
            </Form.Item>
            <Form.Item label="结束时间" name="endTime">
              <TimePicker className="w-full" format="HH:mm:ss" />
            </Form.Item>
            <Form.Item label="报名开始" name="registerStart">
              <DatePicker showTime className="w-full" />
            </Form.Item>
            <Form.Item label="报名结束" name="registerEnd">
              <DatePicker showTime className="w-full" />
            </Form.Item>
            <Form.Item label="容量" name="capacity" rules={[{ required: true }]}>
              <InputNumber min={0} max={9999} className="w-full" />
            </Form.Item>
          </div>
          <Form.Item label="备注" name="remark"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
