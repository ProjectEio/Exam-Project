import ReactDOM from 'react-dom/client'
import { ConfigProvider, App as AntdApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import App from './App'
import './styles/global.scss'

dayjs.locale('zh-cn')

ReactDOM.createRoot(document.getElementById('root')!).render(
  <ConfigProvider
    locale={zhCN}
    theme={{
      token: {
        colorPrimary: '#1677ff',
        colorInfo: '#1677ff',
        colorSuccess: '#52c41a',
        colorWarning: '#faad14',
        colorError: '#ff4d4f',
        colorText: '#1f2937',
        colorTextSecondary: '#667085',
        colorBorderSecondary: '#e5eaf1',
        colorBgLayout: '#f3f6f9',
        colorBgContainer: '#ffffff',
        fontFamily: 'PingFang SC, Microsoft YaHei, Helvetica Neue, Arial, sans-serif',
        borderRadius: 12,
        borderRadiusLG: 20,
        boxShadowSecondary: '0 18px 40px rgba(15, 23, 42, 0.06)',
        boxShadowTertiary: '0 12px 28px rgba(15, 23, 42, 0.05)',
      },
      components: {
        Button: {
          controlHeight: 40,
          borderRadius: 12,
          primaryShadow: 'none',
        },
        Card: {
          borderRadiusLG: 20,
          headerFontSize: 16,
        },
        Input: {
          controlHeight: 40,
        },
        Select: {
          controlHeight: 40,
        },
        Table: {
          headerBg: '#f8fafc',
          headerColor: '#334155',
          rowHoverBg: '#f8fbff',
          borderColor: '#edf2f7',
        },
        Modal: {
          borderRadiusLG: 20,
        },
        Collapse: {
          borderRadiusLG: 20,
          headerBg: '#f8fafc',
          contentBg: '#ffffff',
        },
      },
    }}
  >
    <AntdApp>
      <App />
    </AntdApp>
  </ConfigProvider>
)
