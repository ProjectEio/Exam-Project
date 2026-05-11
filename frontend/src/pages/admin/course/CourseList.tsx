import { useEffect, useState } from 'react'
import {
  Table, Button, Input, Space, Tag, Modal, Form, Popconfirm, App, InputNumber, Select,
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import { pageCourse, addCourse, updCourse, delCourse } from '@/api/course'
import type { Course } from '@/types'

const typeColor: Record<string, string> = {
  公共课: 'blue',
  专业课: 'purple',
  实践: 'orange',
}

export default function CourseList() {
  const { message } = App.useApp()
  const [data, setData] = useState<Course[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [query, setQuery] = useState({ current: 1, size: 10, keyword: '' })
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Course | null>(null)
  const [form] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const res = await pageCourse(query)
      setData(res.data.records)
      setTotal(res.data.total)
    } finally {
      setLoading(false)
    }
  }
  useEffect(() => { load() }, [query])

  const onAdd = () => { setEditing(null); form.resetFields(); setOpen(true) }
  const onEdit = (c: Course) => { setEditing(c); form.setFieldsValue(c); setOpen(true) }
  const onSave = async () => {
    const v = await form.validateFields()
    if (editing) await updCourse({ ...editing, ...v })
    else await addCourse(v)
    message.success(editing ? '更新成功' : '新增成功')
    setOpen(false)
    load()
  }
  const onDel = async (id: number) => { await delCourse(id); message.success('删除成功'); load() }

  const columns = [
    { title: '课程代码', dataIndex: 'courseCode', width: 140 },
    { title: '课程名称', dataIndex: 'courseName' },
    { title: '学分', dataIndex: 'credit', width: 80 },
    {
      title: '类型',
      dataIndex: 'courseType',
      width: 100,
      render: (t: string) => <Tag color={typeColor[t] || 'default'}>{t}</Tag>,
    },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '操作',
      width: 180,
      render: (_: any, r: Course) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => onEdit(r)}>编辑</Button>
          <Popconfirm title="确认删除？" onConfirm={() => onDel(r.id!)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <div className="bg-white p-4 rounded-lg shadow-soft mb-4 flex flex-wrap gap-3 items-center">
        <Input.Search
          placeholder="搜索课程代码/名称"
          allowClear
          style={{ width: 240 }}
          onSearch={(v) => setQuery({ ...query, keyword: v, current: 1 })}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={onAdd}>新增课程</Button>
      </div>

      <div className="bg-white rounded-lg shadow-soft p-4">
        <Table
          rowKey="id"
          dataSource={data}
          columns={columns as any}
          loading={loading}
          pagination={{
            current: query.current,
            pageSize: query.size,
            total,
            showSizeChanger: true,
            onChange: (page, size) => setQuery({ ...query, current: page, size }),
          }}
        />
      </div>

      <Modal
        title={editing ? '编辑课程' : '新增课程'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={onSave}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item label="课程代码" name="courseCode" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label="课程名称" name="courseName" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label="学分" name="credit">
            <InputNumber min={0} step={0.5} max={20} className="w-full" />
          </Form.Item>
          <Form.Item label="课程类型" name="courseType" rules={[{ required: true }]}>
            <Select options={[{ value: '公共课' }, { value: '专业课' }, { value: '实践' }]} />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
