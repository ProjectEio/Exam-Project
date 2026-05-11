import { useEffect, useState } from 'react'
import {
  Table, Button, Input, Select, Space, Tag, Modal, Form, Popconfirm, App,
} from 'antd'
import {
  PlusOutlined, KeyOutlined, EditOutlined, DeleteOutlined,
} from '@ant-design/icons'
import {
  pageUser, addUser, updateUser, deleteUser, resetPwd,
} from '@/api/user'
import type { User, Role } from '@/types'

const roleColor: Record<Role, string> = { ADMIN: 'red', TEACHER: 'blue', STUDENT: 'green' }
const roleText: Record<Role, string> = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }

export default function UserList() {
  const { message } = App.useApp()
  const [data, setData] = useState<User[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [query, setQuery] = useState<any>({ current: 1, size: 10, keyword: '', role: undefined })

  const [editOpen, setEditOpen] = useState(false)
  const [editing, setEditing] = useState<User | null>(null)
  const [form] = Form.useForm()

  const [pwdOpen, setPwdOpen] = useState(false)
  const [pwdTarget, setPwdTarget] = useState<User | null>(null)
  const [pwdForm] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const res = await pageUser(query)
      setData(res.data.records)
      setTotal(res.data.total)
    } finally {
      setLoading(false)
    }
  }
  useEffect(() => { load() }, [query])

  const onAdd = () => { setEditing(null); form.resetFields(); setEditOpen(true) }
  const onEdit = (u: User) => { setEditing(u); form.setFieldsValue(u); setEditOpen(true) }
  const onSave = async () => {
    const v = await form.validateFields()
    if (editing) await updateUser({ ...editing, ...v })
    else await addUser(v)
    message.success(editing ? '更新成功' : '新增成功')
    setEditOpen(false)
    load()
  }
  const onDel = async (id: number) => { await deleteUser(id); message.success('删除成功'); load() }
  const onResetOpen = (u: User) => { setPwdTarget(u); pwdForm.resetFields(); setPwdOpen(true) }
  const onResetOk = async () => {
    const v = await pwdForm.validateFields()
    await resetPwd(pwdTarget!.id, v.newPassword)
    message.success('密码已重置')
    setPwdOpen(false)
  }

  const columns = [
    { title: '用户名', dataIndex: 'username' },
    { title: '角色', dataIndex: 'role', render: (r: Role) => <Tag color={roleColor[r]}>{roleText[r]}</Tag> },
    { title: '真实姓名', dataIndex: 'realName' },
    { title: '手机', dataIndex: 'phone' },
    { title: '邮箱', dataIndex: 'email' },
    { title: '状态', dataIndex: 'status', render: (st: number) => <Tag color={st === 1 ? 'green' : 'red'}>{st === 1 ? '启用' : '禁用'}</Tag> },
    { title: '创建时间', dataIndex: 'createTime' },
    {
      title: '操作',
      width: 280,
      render: (_: any, r: User) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => onEdit(r)}>编辑</Button>
          <Button size="small" icon={<KeyOutlined />} onClick={() => onResetOpen(r)}>重置密码</Button>
          <Popconfirm title="确认删除该用户？" onConfirm={() => onDel(r.id)}>
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
          placeholder="搜索用户名/姓名"
          allowClear
          style={{ width: 240 }}
          onSearch={(v) => setQuery({ ...query, keyword: v, current: 1 })}
        />
        <Select
          placeholder="角色筛选"
          allowClear
          style={{ width: 140 }}
          value={query.role}
          onChange={(v) => setQuery({ ...query, role: v, current: 1 })}
          options={[
            { value: 'ADMIN', label: '管理员' },
            { value: 'TEACHER', label: '教师' },
            { value: 'STUDENT', label: '学生' },
          ]}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={onAdd}>新增用户</Button>
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
        title={editing ? '编辑用户' : '新增用户'}
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={onSave}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item label="用户名" name="username" rules={[{ required: true }]}>
            <Input disabled={!!editing} />
          </Form.Item>
          {!editing && (
            <Form.Item label="密码" name="password" rules={[{ required: true, min: 6 }]}>
              <Input.Password />
            </Form.Item>
          )}
          <Form.Item label="角色" name="role" rules={[{ required: true }]}>
            <Select options={[
              { value: 'ADMIN', label: '管理员' },
              { value: 'TEACHER', label: '教师' },
              { value: 'STUDENT', label: '学生' },
            ]} />
          </Form.Item>
          <Form.Item label="真实姓名" name="realName"><Input /></Form.Item>
          <Form.Item label="身份证号" name="idCard"><Input /></Form.Item>
          <Form.Item label="手机" name="phone"><Input /></Form.Item>
          <Form.Item label="邮箱" name="email"><Input /></Form.Item>
          <Form.Item label="性别" name="gender">
            <Select allowClear options={[{ value: '男' }, { value: '女' }]} />
          </Form.Item>
          <Form.Item label="状态" name="status" initialValue={1}>
            <Select options={[{ value: 1, label: '启用' }, { value: 0, label: '禁用' }]} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`重置密码 - ${pwdTarget?.username || ''}`}
        open={pwdOpen}
        onCancel={() => setPwdOpen(false)}
        onOk={onResetOk}
        destroyOnClose
      >
        <Form form={pwdForm} layout="vertical" preserve={false}>
          <Form.Item label="新密码" name="newPassword" rules={[{ required: true, min: 6 }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
