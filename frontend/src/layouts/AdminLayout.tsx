import { useMemo } from 'react'
import { Layout, Menu, Avatar, Dropdown, App } from 'antd'
import type { MenuProps } from 'antd'
import {
  DashboardOutlined,
  UserOutlined,
  BookOutlined,
  ReadOutlined,
  CalendarOutlined,
  FormOutlined,
  TrophyOutlined,
  BarChartOutlined,
  LogoutOutlined,
  ProfileOutlined,
  CrownOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import useAuthStore from '@/store/auth'
import styles from './AdminLayout.module.scss'

const { Sider, Header, Content } = Layout

const roleLabel: Record<string, string> = {
  ADMIN: '系统管理员',
  TEACHER: '教师',
  STUDENT: '考生',
}

export default function AdminLayout() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const user = useAuthStore((s) => s.user)

  const menuItems = useMemo(() => {
    const items: MenuProps['items'] = [
      { key: '/admin/dashboard', icon: <DashboardOutlined />, label: '仪表盘' },
    ]
    if (user?.role === 'ADMIN') {
      items.push(
        { key: '/admin/users', icon: <UserOutlined />, label: '用户管理' },
        { key: '/admin/registrations', icon: <FormOutlined />, label: '报名管理' },
      )
    }
    items.push(
      { key: '/admin/majors', icon: <BookOutlined />, label: '专业管理' },
      { key: '/admin/courses', icon: <ReadOutlined />, label: '课程管理' },
      { key: '/admin/plans', icon: <CalendarOutlined />, label: '考试计划' },
      { key: '/admin/scores', icon: <TrophyOutlined />, label: '成绩管理' },
      { key: '/admin/statistics', icon: <BarChartOutlined />, label: '统计分析' },
    )
    return items
  }, [user?.role])

  const selectedKey = useMemo(() => {
    const match = menuItems?.find((item) => item && location.pathname.startsWith(item.key as string))
    return match ? (match.key as string) : '/admin/dashboard'
  }, [menuItems, location.pathname])

  const handleLogout = () => {
    useAuthStore.getState().logout()
    message.success('已退出登录')
    navigate('/login')
  }

  const dropdownItems: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <ProfileOutlined />,
      label: '个人信息',
      onClick: () => message.info('个人信息页待开发'),
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
      <Sider width={240} className={styles.sider}>
        <div className={styles.logo}>
          <div className={styles.logoIcon}>
            <CrownOutlined />
          </div>
          <div className={styles.logoText}>考试院管理后台</div>
        </div>
        <Menu
          mode="inline"
          theme="dark"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ paddingTop: 12 }}
        />
      </Sider>

      <Layout>
        <Header className={styles.header}>
          <div className={styles.headerTitle}>自学考试综合管理平台</div>
          <Dropdown menu={{ items: dropdownItems }} placement="bottomRight" trigger={['click']}>
            <div className={styles.userBlock}>
              <Avatar
                size={36}
                style={{ background: 'linear-gradient(135deg, #1e40af, #2563eb)' }}
                icon={<UserOutlined />}
              />
              <div className="flex flex-col leading-tight">
                <span className={styles.userName}>{user?.realName || user?.username || '用户'}</span>
                <span className={styles.userRole}>
                  {user?.role ? roleLabel[user.role] : ''}
                </span>
              </div>
            </div>
          </Dropdown>
        </Header>

        <Content className={styles.content}>
          <div className={styles.contentCard}>
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  )
}
