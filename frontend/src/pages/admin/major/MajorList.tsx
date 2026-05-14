import { useEffect, useState } from 'react'
import {
  Table, Button, Input, Space, Tag, Modal, Form, Popconfirm, App, InputNumber, Select, Card,
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import { pageMajor, addMajor, updMajor, delMajor } from '@/api/major'
import type { Major } from '@/types'

export default function MajorList() {
  const { message } = App.useApp()
  const [data, setData] = useState<Major[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [query, setQuery] = useState({ current: 1, size: 10, keyword: '' })
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Major | null>(null)
  const [form] = Form.useForm()
  const initialLoading = loading && data.length === 0

  const load = async () => {
    setLoading(true)
    try {
      const res = await pageMajor(query)
      setData(res.data.records)
      setTotal(res.data.total)
    } finally {
      setLoading(false)
    }
  }
  useEffect(() => { load() }, [query])

  const onAdd = () => { setEditing(null); form.resetFields(); setOpen(true) }
  const onEdit = (m: Major) => { setEditing(m); form.setFieldsValue(m); setOpen(true) }
  const onSave = async () => {
    const v = await form.validateFields()
    if (editing) await updMajor({ ...editing, ...v })
    else await addMajor(v)
    message.success(editing ? '更新成功' : '新增成功')
    setOpen(false)
    load()
  }
  const onDel = async (id: number) => { await delMajor(id); message.success('删除成功'); load() }

  const columns = [
    { title: '专业代码', dataIndex: 'majorCode', width: 140 },
    { title: '专业名称', dataIndex: 'majorName' },
    {
      title: '层次',
      dataIndex: 'level',
      width: 90,
      render: (l: string) => <Tag color={l === '本科' ? 'blue' : 'green'}>{l}</Tag>,
    },
    { title: '总学分', dataIndex: 'totalCredits', width: 90 },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (st: number) => <Tag color={st === 1 ? 'green' : 'red'}>{st === 1 ? '启用' : '禁用'}</Tag>,
    },
    {
      title: '操作',
      width: 180,
      render: (_: any, r: Major) => (
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
      <Card bordered={false} className="rounded-2xl shadow-soft mb-4" loading={initialLoading}>
        <div className="flex flex-wrap gap-3 items-center">
        <Input.Search
          placeholder="搜索专业代码/名称"
          allowClear
          style={{ width: 240 }}
          onSearch={(v) => setQuery({ ...query, keyword: v, current: 1 })}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={onAdd}>新增专业</Button>
        </div>
      </Card>

      <Card bordered={false} className="rounded-2xl shadow-soft" loading={initialLoading}>
        <Table
          rowKey="id"
          dataSource={data}
          columns={columns as any}
          loading={!initialLoading && loading}
          pagination={{
            current: query.current,
            pageSize: query.size,
            total,
            showSizeChanger: true,
            onChange: (page, size) => setQuery({ ...query, current: page, size }),
          }}
        />
      </Card>

      <Modal
        title={editing ? '编辑专业' : '新增专业'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={onSave}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item label="专业代码" name="majorCode" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label="专业名称" name="majorName" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label="层次" name="level" rules={[{ required: true }]}>
            <Select options={[{ value: '专科' }, { value: '本科' }]} />
          </Form.Item>
          <Form.Item label="总学分" name="totalCredits">
            <InputNumber min={0} max={500} className="w-full" />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label="状态" name="status" initialValue={1}>
            <Select options={[{ value: 1, label: '启用' }, { value: 0, label: '禁用' }]} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
