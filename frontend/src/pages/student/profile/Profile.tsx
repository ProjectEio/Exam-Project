import { useEffect, useState } from 'react'
import {
  App,
  Avatar,
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  Progress,
  Radio,
  Row,
  Skeleton,
  Space,
  Tag,
  Typography,
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

  const completion = user
    ? Math.round(([
        user.realName,
        user.gender,
        user.idCard,
        user.phone,
        user.email,
      ].filter(Boolean).length / 5) * 100)
    : 0

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

  const onSave = async (values: FormValues) => {
    if (!user) return
    try {
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
    <div className="space-y-4 max-w-4xl mx-auto">
      <Card bordered={false} className="rounded-2xl shadow-soft">
        {loading || !user ? (
          <Skeleton avatar paragraph={{ rows: 2 }} active />
        ) : (
          <Row gutter={[24, 24]} align="middle">
            <Col xs={24} lg={14}>
              <Space size={18} align="start">
                <Avatar
                  size={88}
                  style={{ background: '#e8f1ff', color: '#1677ff', fontSize: 34 }}
                  icon={<UserOutlined />}
                >
                  {user.realName?.charAt(0) || user.username.charAt(0).toUpperCase()}
                </Avatar>
                <div>
                  <Typography.Title level={3} style={{ margin: 0 }}>
                    {user.realName || user.username}
                  </Typography.Title>
                  <Typography.Paragraph style={{ margin: '8px 0 12px', color: '#64748b' }}>
                    这里集中维护你的身份、联系方式和报名所需的个人资料信息。
                  </Typography.Paragraph>
                  <Space wrap>
                    <Tag color={ROLE_COLOR[user.role]}>{ROLE_TEXT[user.role]}</Tag>
                    <Tag>{user.username}</Tag>
                  </Space>
                </div>
              </Space>
            </Col>
            <Col xs={24} lg={10}>
              <Descriptions
                column={1}
                size="small"
                items={[
                  { label: '资料完整度', children: <Progress percent={completion} size="small" /> },
                  { label: '手机号', children: user.phone || '未填写' },
                  { label: '邮箱', children: user.email || '未填写' },
                ]}
              />
            </Col>
          </Row>
        )}
      </Card>

      <Card
        bordered={false}
        className="rounded-2xl shadow-soft"
        title={<span className="font-semibold text-gray-800">编辑个人资料</span>}
        extra={
          <Space>
            <Button onClick={load} disabled={saving}>重置</Button>
            <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => form.submit()}>
              保存修改
            </Button>
          </Space>
        }
      >
        {loading || !user ? (
          <Skeleton active paragraph={{ rows: 6 }} />
        ) : (
          <Form
            form={form}
            layout="vertical"
            requiredMark
            className="max-w-3xl"
            onFinish={onSave}
          >
            <Row gutter={[16, 0]}>
              <Col xs={24} md={12}>
                <Form.Item label="用户名">
                  <Input value={user.username} disabled />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item label="角色">
                  <Input value={ROLE_TEXT[user.role]} disabled />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item
                  name="realName"
                  label="真实姓名"
                  rules={[{ required: true, message: '请输入真实姓名' }]}
                >
                  <Input placeholder="请输入真实姓名" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="gender" label="性别">
                  <Radio.Group>
                    <Radio value="男">男</Radio>
                    <Radio value="女">女</Radio>
                  </Radio.Group>
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
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
              </Col>
              <Col xs={24} md={12}>
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
              </Col>
              <Col xs={24}>
                <Form.Item
                  name="email"
                  label="邮箱"
                  rules={[{ type: 'email', message: '邮箱格式不正确' }]}
                >
                  <Input placeholder="请输入邮箱" />
                </Form.Item>
              </Col>
            </Row>
          </Form>
        )}
      </Card>
    </div>
  )
}
