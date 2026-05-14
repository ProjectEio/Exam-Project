import { useEffect, useState } from 'react'
import {
  Table, Button, Input, Space, Tag, Modal, Form, Popconfirm, App,
  InputNumber, Select, DatePicker, Upload, Card,
} from 'antd'
import {
  PlusOutlined, EditOutlined, DeleteOutlined, UploadOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import { pageScore, addScore, updScore, delScore, importScore } from '@/api/score'
import type { ImportResult } from '@/api/score'
import { pageUser } from '@/api/user'
import { allCourse } from '@/api/course'
import type { Score, Course, User } from '@/types'

type ScoreStatus = 'PASS' | 'FAIL' | 'ABSENT'
const statusColor: Record<ScoreStatus, string> = { PASS: 'green', FAIL: 'red', ABSENT: 'orange' }
const statusText: Record<ScoreStatus, string> = { PASS: '合格', FAIL: '不合格', ABSENT: '缺考' }

export default function ScoreList() {
  const { message } = App.useApp()
  const [data, setData] = useState<Score[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [query, setQuery] = useState<any>({
    current: 1, size: 10, keyword: '',
    examYear: undefined, examTerm: undefined, status: undefined,
  })
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Score | null>(null)
  const [students, setStudents] = useState<User[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [importOpen, setImportOpen] = useState(false)
  const [importRes, setImportRes] = useState<ImportResult | null>(null)
  const [form] = Form.useForm()
  const initialLoading = loading && data.length === 0

  const load = async () => {
    setLoading(true)
    try {
      const res = await pageScore(query)
      setData(res.data.records)
      setTotal(res.data.total)
    } finally {
      setLoading(false)
    }
  }
  useEffect(() => { load() }, [query])
  useEffect(() => {
    pageUser({ current: 1, size: 1000, role: 'STUDENT' }).then((r) => setStudents(r.data.records))
    allCourse().then((r) => setCourses(r.data))
  }, [])

  const onAdd = () => { setEditing(null); form.resetFields(); setOpen(true) }
  const onEdit = (s: Score) => {
    setEditing(s)
    form.setFieldsValue({ ...s, examDate: s.examDate ? dayjs(s.examDate) : null })
    setOpen(true)
  }
  const onSave = async () => {
    const v = await form.validateFields()
    const payload: any = { ...v, examDate: v.examDate ? v.examDate.format('YYYY-MM-DD') : undefined }
    if (editing) await updScore({ ...editing, ...payload })
    else await addScore(payload)
    message.success(editing ? '更新成功' : '录入成功')
    setOpen(false)
    load()
  }
  const onDel = async (id: number) => { await delScore(id); message.success('删除成功'); load() }

  const columns = [
    { title: '学生姓名', dataIndex: 'studentName', width: 110 },
    { title: '课程代码', dataIndex: 'courseCode', width: 130 },
    { title: '课程名称', dataIndex: 'courseName' },
    {
      title: '年份学期', width: 110,
      render: (_: any, r: Score) => <Tag color="blue">{r.examYear} {r.examTerm}</Tag>,
    },
    {
      title: '分数', dataIndex: 'score', width: 90,
      render: (v: number) => <span className="font-semibold text-base">{v}</span>,
    },
    {
      title: '状态', dataIndex: 'status', width: 90,
      render: (s: ScoreStatus) => s ? <Tag color={statusColor[s]}>{statusText[s]}</Tag> : '-',
    },
    { title: '考试日期', dataIndex: 'examDate', width: 110 },
    {
      title: '操作', width: 180,
      render: (_: any, r: Score) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => onEdit(r)}>编辑</Button>
          <Popconfirm title="确认删除该成绩？" onConfirm={() => onDel(r.id!)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Card bordered={false} className="rounded-2xl shadow-soft mb-4" loading={initialLoading}>
        <div className="flex flex-wrap gap-3 items-center">
        <Select placeholder="年份" allowClear style={{ width: 110 }} value={query.examYear}
          onChange={(v) => setQuery({ ...query, examYear: v, current: 1 })}
          options={[{ value: 2024 }, { value: 2025 }, { value: 2026 }]} />
        <Select placeholder="学期" allowClear style={{ width: 100 }} value={query.examTerm}
          onChange={(v) => setQuery({ ...query, examTerm: v, current: 1 })}
          options={[{ value: '上' }, { value: '下' }]} />
        <Select placeholder="状态" allowClear style={{ width: 120 }} value={query.status}
          onChange={(v) => setQuery({ ...query, status: v, current: 1 })}
          options={[
            { value: 'PASS', label: '合格' },
            { value: 'FAIL', label: '不合格' },
            { value: 'ABSENT', label: '缺考' },
          ]} />
        <Input.Search placeholder="搜索学生/课程" allowClear style={{ width: 220 }}
          onSearch={(v) => setQuery({ ...query, keyword: v, current: 1 })} />
        <Button type="primary" icon={<PlusOutlined />} onClick={onAdd}>录入成绩</Button>
        <Upload accept=".xls,.xlsx" showUploadList={false}
          customRequest={(opts) => {
            const f = opts.file as File
            importScore(f)
              .then((res) => {
                setImportRes(res.data)
                setImportOpen(true)
                load()
                opts.onSuccess?.({})
              })
              .catch((e) => { opts.onError?.(e); message.error('导入失败') })
          }}>
          <Button icon={<UploadOutlined />}>批量导入</Button>
        </Upload>
        </div>
      </Card>

      <Card bordered={false} className="rounded-2xl shadow-soft" loading={initialLoading}>
        <Table rowKey="id" dataSource={data} columns={columns as any} loading={!initialLoading && loading}
          pagination={{
            current: query.current, pageSize: query.size, total, showSizeChanger: true,
            onChange: (page, size) => setQuery({ ...query, current: page, size }),
          }} />
      </Card>

      <Modal title={editing ? '编辑成绩' : '录入成绩'} open={open}
        onCancel={() => setOpen(false)} onOk={onSave} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item label="学生" name="studentId" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label"
              options={students.map((u) => ({ value: u.id, label: `${u.username} ${u.realName || ''}` }))} />
          </Form.Item>
          <Form.Item label="课程" name="courseId" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label"
              options={courses.map((c) => ({ value: c.id, label: `${c.courseCode} ${c.courseName}` }))} />
          </Form.Item>
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item label="年份" name="examYear" rules={[{ required: true }]}>
              <Select options={[{ value: 2024 }, { value: 2025 }, { value: 2026 }]} />
            </Form.Item>
            <Form.Item label="学期" name="examTerm" rules={[{ required: true }]}>
              <Select options={[{ value: '上' }, { value: '下' }]} />
            </Form.Item>
            <Form.Item label="分数" name="score" rules={[{ required: true }]}>
              <InputNumber min={0} max={100} className="w-full" />
            </Form.Item>
            <Form.Item label="考试日期" name="examDate">
              <DatePicker className="w-full" />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      <Modal title="批量导入结果" open={importOpen}
        onCancel={() => setImportOpen(false)} onOk={() => setImportOpen(false)}
        cancelButtonProps={{ style: { display: 'none' } }} destroyOnClose>
        <p>
          成功：<Tag color="green">{importRes?.success ?? 0}</Tag>
          失败：<Tag color="red">{importRes?.fail ?? 0}</Tag>
        </p>
        {!!importRes?.errors?.length && (
          <div className="max-h-60 overflow-auto bg-gray-50 p-2 rounded text-xs border">
            {importRes.errors.map((e, i) => (
              <div key={i} className="text-red-600 leading-6">• {e}</div>
            ))}
          </div>
        )}
      </Modal>
    </div>
  )
}
