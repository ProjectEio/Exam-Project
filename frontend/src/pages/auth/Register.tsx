import { useState } from 'react'
import { Form, Input, Button, Select, App } from 'antd'
import { BookOutlined } from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { register } from '@/api/auth'
import type { RegisterDTO } from '@/api/auth'
import styles from './Login.module.scss'

export default function Register() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)

  const onFinish = async (values: RegisterDTO) => {
    setLoading(true)
    try {
      await register(values)
      message.success('注册成功，请登录')
      navigate('/login')
    } catch {
      // 错误消息已由请求拦截器统一处理，这里仅捕获以阻止 unhandled rejection
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.wrapper}>
      <div className={styles.glow} />
      <div className={styles.card} style={{ maxWidth: 460 }}>
        <div className={styles.header}>
          <div className={styles.iconBox}>
            <BookOutlined />
          </div>
          <div className={styles.title}>考生注册</div>
          <div className={styles.subtitle}>填写真实信息以创建您的考生账号</div>
        </div>

        <Form layout="vertical" size="middle" onFinish={onFinish} autoComplete="off">
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item
              label="用户名"
              name="username"
              rules={[
                { required: true, message: '请输入用户名' },
                { min: 4, max: 20, message: '用户名长度 4-20 位' },
              ]}
            >
              <Input placeholder="登录账号" />
            </Form.Item>

            <Form.Item
              label="密码"
              name="password"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码至少 6 位' },
              ]}
            >
              <Input.Password placeholder="请输入密码" />
            </Form.Item>

            <Form.Item
              label="真实姓名"
              name="realName"
              rules={[{ required: true, message: '请输入真实姓名' }]}
            >
              <Input placeholder="请输入真实姓名" />
            </Form.Item>

            <Form.Item
              label="性别"
              name="gender"
              rules={[{ required: true, message: '请选择性别' }]}
            >
              <Select
                placeholder="请选择性别"
                options={[
                  { label: '男', value: '男' },
                  { label: '女', value: '女' },
                ]}
              />
            </Form.Item>

            <Form.Item
              label="身份证号"
              name="idCard"
              rules={[
                { required: true, message: '请输入身份证号' },
                { pattern: /^\d{17}[\dXx]$/, message: '身份证号格式不正确' },
              ]}
            >
              <Input placeholder="18 位身份证号" maxLength={18} />
            </Form.Item>

            <Form.Item
              label="手机号"
              name="phone"
              rules={[
                { required: true, message: '请输入手机号' },
                { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' },
              ]}
            >
              <Input placeholder="11 位手机号" maxLength={11} />
            </Form.Item>

            <Form.Item
              label="邮箱"
              name="email"
              className="col-span-2"
              rules={[{ type: 'email', message: '邮箱格式不正确' }]}
            >
              <Input placeholder="选填，用于接收通知" />
            </Form.Item>
          </div>

          <Form.Item className="!mb-2">
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              block
              size="large"
              className={styles.submitBtn}
            >
              立即注册
            </Button>
          </Form.Item>
        </Form>

        <div className={styles.footerLink}>
          已有账号？
          <Link to="/login" className="text-blue-600 font-medium hover:underline">
            返回登录
          </Link>
        </div>
      </div>
    </div>
  )
}
