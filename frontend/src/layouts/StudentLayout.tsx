import { useMemo } from 'react'
import { Layout, Menu, Avatar, Dropdown, App } from 'antd'
import type { MenuProps } from 'antd'
import {
  HomeOutlined,
  CalendarOutlined,
  FormOutlined,
  TrophyOutlined,
  UserOutlined,
  LogoutOutlined,
  ProfileOutlined,
  BookOutlined,
  DownOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import useAuthStore from '@/store/auth'
import styles from './StudentLayout.module.scss'

const { Header, Content } = Layout

export default function StudentLayout() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const user = useAuthStore((s) => s.user)

  const menuItems: MenuProps['items'] = [
    { key: '/student/home', icon: <HomeOutlined />, label: '首页' },
    { key: '/student/plans', icon: <CalendarOutlined />, label: '考试计划' },
    { key: '/student/my-registrations', icon: <FormOutlined />, label: '我的报名' },
    { key: '/student/my-scores', icon: <TrophyOutlined />, label: '我的成绩' },
    { key: '/student/profile', icon: <UserOutlined />, label: '个人中心' },
  ]

  const selectedKey = useMemo(() => {
    const match = menuItems.find((item) => item && location.pathname.startsWith(item.key as string))
    return match ? (match.key as string) : '/student/home'
  }, [location.pathname])

  const handleLogout = () => {
    useAuthStore.getState().logout()
    message.success('已退出登录')
    navigate('/login')
  }

  const dropdownItems: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <ProfileOutlined />,
      label: '个人中心',
      onClick: () => navigate('/student/profile'),
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ]

  return (
    <Layout className={styles.layout}>
      <Header className={styles.header}>
        <div className={styles.brand}>
          <div className={styles.brandIcon}>
            <BookOutlined />
          </div>
          <div className={styles.brandText}>省考试院 · 自学考试服务</div>
        </div>

        <Menu
          mode="horizontal"
          theme="dark"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          className={styles.menu}
        />

        <Dropdown menu={{ items: dropdownItems }} placement="bottomRight" trigger={['click']}>
          <div className={styles.userBlock}>
            <Avatar
              size={32}
              style={{ background: 'linear-gradient(135deg, #f59e0b, #fbbf24)', color: '#1e293b' }}
              icon={<UserOutlined />}
            />
            <span className={styles.userName}>{user?.realName || user?.username || '考生'}</span>
            <DownOutlined style={{ fontSize: 10 }} />
          </div>
        </Dropdown>
      </Header>

      <Content className={styles.content}>
        <div className={styles.contentInner}>
          <div className={styles.contentCard}>
            <Outlet />
          </div>
        </div>
      </Content>
    </Layout>
  )
}
