import { useState } from 'react'
import { Form, Input, Button, App } from 'antd'
import { BookOutlined, UserOutlined, LockOutlined } from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { login } from '@/api/auth'
import useAuthStore from '@/store/auth'
import styles from './Login.module.scss'

interface LoginForm {
  username: string
  password: string
}

export default function Login() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)
  const [loading, setLoading] = useState(false)

  const onFinish = async (values: LoginForm) => {
    setLoading(true)
    try {
      const res = await login(values)
      const { token, userId, username, realName, role } = res.data
      setAuth(token, { userId, username, realName, role })
      message.success('登录成功，欢迎回来！')
      if (role === 'STUDENT') {
        navigate('/student/home')
      } else {
        navigate('/admin/dashboard')
      }
    } catch {
      // 错误消息已由请求拦截器统一处理，这里仅捕获以阻止 unhandled rejection
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.wrapper}>
      <div className={styles.glow} />
      <div className={styles.card} style={{ maxWidth: 420 }}>
        <div className={styles.header}>
          <div className={styles.iconBox}>
            <BookOutlined />
          </div>
          <div className={styles.title}>省考试院 · 自学考试管理系统</div>
          <div className={styles.subtitle}>Provincial Examination Authority</div>
        </div>

        <Form
          layout="vertical"
          size="large"
          initialValues={{ username: 'admin', password: '123456' }}
          onFinish={onFinish}
          autoComplete="off"
        >
          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<UserOutlined className="text-slate-400" />} placeholder="请输入用户名" />
          </Form.Item>

          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined className="text-slate-400" />}
              placeholder="请输入密码"
            />
          </Form.Item>

          <Form.Item className="!mb-2">
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              block
              className={styles.submitBtn}
            >
              登 录
            </Button>
          </Form.Item>
        </Form>

        <div className={styles.tips}>
          <div className={styles.tipsTitle}>演示账号</div>
          <div>管理员：admin / 123456</div>
          <div>教师：teacher / 123456</div>
          <div>考生：student1 / 123456</div>
        </div>

        <div className={styles.footerLink}>
          还没有账号？
          <Link to="/register" className="text-blue-600 font-medium hover:underline">
            考生注册
          </Link>
        </div>
      </div>
    </div>
  )
}
