import { useEffect, useState } from 'react'
import {
  App,
  Avatar,
  Button,
  Card,
  Form,
  Input,
  Radio,
  Skeleton,
  Tag,
} from 'antd'
import { SaveOutlined, UserOutlined } from '@ant-design/icons'
import { getInfo } from '@/api/auth'
import { updateUser } from '@/api/user'
import useAuthStore from '@/store/auth'
import type { Role, User } from '@/types'

const ROLE_TEXT: Record<Role, string> = {
  ADMIN: '管理员',
  TEACHER: '教师',
  STUDENT: '学生',
}
const ROLE_COLOR: Record<Role, string> = {
  ADMIN: 'red',
  TEACHER: 'blue',
  STUDENT: 'green',
}

interface FormValues {
  realName?: string
  gender?: string
  idCard?: string
  phone?: string
  email?: string
}

export default function Profile() {
  const { message } = App.useApp()
  const setStoreUser = useAuthStore((s) => s.setUser)

  const [form] = Form.useForm<FormValues>()
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const res = await getInfo()
      const u = res.data
      setUser(u)
      form.setFieldsValue({
        realName: u.realName,
        gender: u.gender,
        idCard: u.idCard,
        phone: u.phone,
        email: u.email,
      })
    } catch {
      // ignore
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onSave = async () => {
    if (!user) return
    try {
      const values = await form.validateFields()
      setSaving(true)
      const payload: User = {
        ...user,
        ...values,
      }
      await updateUser(payload)
      message.success('个人资料已更新')
      // 同步到全局 store（用户名+角色不变，但 realName 可能变了）
      setStoreUser({
        userId: user.id,
        username: user.username,
        role: user.role,
        realName: values.realName,
      })
      await load()
    } catch (e: any) {
      if (e?.errorFields) {
        // 表单校验未通过，不弹消息
        return
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-4 max-w-3xl mx-auto">
      {/* 头像 + 基本信息 */}
      <Card className="rounded-2xl shadow-soft">
        {loading || !user ? (
          <Skeleton avatar paragraph={{ rows: 2 }} active />
        ) : (
          <div className="flex items-center gap-5">
            <Avatar
              size={84}
              style={{
                background:
                  'linear-gradient(135deg, #1e40af 0%, #3b82f6 100%)',
                fontSize: 36,
              }}
              icon={<UserOutlined />}
            >
              {user.realName?.charAt(0) || user.username.charAt(0).toUpperCase()}
            </Avatar>
            <div>
              <div className="text-2xl font-bold text-gray-800">
                {user.realName || user.username}
              </div>
              <div className="text-sm text-gray-500 mt-1">
                账号：<span className="font-mono">{user.username}</span>
              </div>
              <div className="mt-2">
                <Tag color={ROLE_COLOR[user.role]} className="!px-3 !py-0.5">
                  {ROLE_TEXT[user.role]}
                </Tag>
              </div>
            </div>
          </div>
        )}
      </Card>

      {/* 表单 */}
      <Card
        className="rounded-2xl shadow-soft"
        title={<span className="font-semibold text-gray-800">编辑个人资料</span>}
      >
        {loading || !user ? (
          <Skeleton active paragraph={{ rows: 6 }} />
        ) : (
          <Form
            form={form}
            layout="vertical"
            requiredMark
            className="max-w-xl"
          >
            <Form.Item label="用户名">
              <Input value={user.username} disabled />
            </Form.Item>

            <Form.Item label="角色">
              <Input value={ROLE_TEXT[user.role]} disabled />
            </Form.Item>

            <Form.Item
              name="realName"
              label="真实姓名"
              rules={[{ required: true, message: '请输入真实姓名' }]}
            >
              <Input placeholder="请输入真实姓名" />
            </Form.Item>

            <Form.Item name="gender" label="性别">
              <Radio.Group>
                <Radio value="男">男</Radio>
                <Radio value="女">女</Radio>
              </Radio.Group>
            </Form.Item>

            <Form.Item
              name="idCard"
              label="身份证号"
              rules={[
                {
                  pattern: /^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]$/,
                  message: '身份证号格式不正确',
                },
              ]}
            >
              <Input placeholder="请输入 18 位身份证号" maxLength={18} />
            </Form.Item>

            <Form.Item
              name="phone"
              label="手机号"
              rules={[
                {
                  pattern: /^1[3-9]\d{9}$/,
                  message: '手机号格式不正确',
                },
              ]}
            >
              <Input placeholder="请输入手机号" maxLength={11} />
            </Form.Item>

            <Form.Item
              name="email"
              label="邮箱"
              rules={[{ type: 'email', message: '邮箱格式不正确' }]}
            >
              <Input placeholder="请输入邮箱" />
            </Form.Item>

            <Form.Item>
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={saving}
                onClick={onSave}
                style={{ background: '#1e40af' }}
              >
                保存修改
              </Button>
              <Button className="ml-2" onClick={load} disabled={saving}>
                重置
              </Button>
            </Form.Item>
          </Form>
        )}
      </Card>
    </div>
  )
}
