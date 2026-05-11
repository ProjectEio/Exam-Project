import { useEffect, useState } from 'react'
import {
  App,
  Button,
  Card,
  Empty,
  Popconfirm,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  DownloadOutlined,
  ReloadOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons'
import { cancelReg, myReg, ticketUrl } from '@/api/registration'
import type { Registration } from '@/types'

const STATUS_COLOR: Record<Registration['status'], string> = {
  PENDING: 'blue',
  APPROVED: 'green',
  REJECTED: 'red',
}
const STATUS_TEXT: Record<Registration['status'], string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
}

export default function MyRegistration() {
  const { message } = App.useApp()
  const [data, setData] = useState<Registration[]>([])
  const [loading, setLoading] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const res = await myReg()
      setData(res.data || [])
    } catch {
      // ignore
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const handleDownload = (id: number) => {
    window.open(ticketUrl(id))
  }

  const handleCancel = async (id: number) => {
    try {
      await cancelReg(id)
      message.success('已取消报名')
      await load()
    } catch {
      // ignore
    }
  }

  const columns: ColumnsType<Registration> = [
    {
      title: '报名编号',
      dataIndex: 'registrationNo',
      key: 'registrationNo',
      width: 180,
      render: (v: string) => <span className="font-mono text-xs">{v}</span>,
    },
    {
      title: '计划名称',
      dataIndex: 'planName',
      key: 'planName',
      render: (v?: string) => v || '—',
    },
    {
      title: '课程',
      dataIndex: 'courseName',
      key: 'courseName',
      width: 140,
      render: (v?: string) => v || '—',
    },
    {
      title: '考试日期',
      dataIndex: 'examDate',
      key: 'examDate',
      width: 130,
      render: (v?: string) => v || '—',
    },
    {
      title: '审核状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (s: Registration['status'], record) => (
        <Tooltip title={record.auditRemark || ''}>
          <Tag color={STATUS_COLOR[s]}>{STATUS_TEXT[s]}</Tag>
        </Tooltip>
      ),
    },
    {
      title: '缴费状态',
      dataIndex: 'paymentStatus',
      key: 'paymentStatus',
      width: 100,
      render: (s: Registration['paymentStatus']) => (
        <Tag color={s === 'PAID' ? 'green' : 'orange'}>
          {s === 'PAID' ? '已缴费' : '未缴费'}
        </Tag>
      ),
    },
    {
      title: '准考证号',
      dataIndex: 'admissionTicketNo',
      key: 'admissionTicketNo',
      width: 160,
      render: (v?: string) =>
        v ? <span className="font-mono text-xs">{v}</span> : <span className="text-gray-300">—</span>,
    },
    {
      title: '操作',
      key: 'op',
      width: 200,
      fixed: 'right',
      render: (_, r) => (
        <Space size="small">
          <Button
            type="link"
            icon={<DownloadOutlined />}
            disabled={r.status !== 'APPROVED'}
            onClick={() => handleDownload(r.id)}
          >
            准考证
          </Button>
          {r.status === 'PENDING' ? (
            <Popconfirm
              title="确认取消报名？"
              description="取消后报名记录将被删除，需重新提交"
              okText="确认"
              cancelText="再想想"
              onConfirm={() => handleCancel(r.id)}
            >
              <Button type="link" danger icon={<CloseCircleOutlined />}>
                取消
              </Button>
            </Popconfirm>
          ) : (
            <Button type="link" disabled icon={<CloseCircleOutlined />}>
              取消
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Card
      className="rounded-2xl shadow-soft"
      title={
        <span className="font-semibold text-gray-800">
          我的报名记录
          <span className="text-sm text-gray-400 ml-3">
            共 {data.length} 条
          </span>
        </span>
      }
      extra={
        <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
          刷新
        </Button>
      }
    >
      <Table<Registration>
        rowKey="id"
        loading={loading}
        dataSource={data}
        columns={columns}
        scroll={{ x: 1100 }}
        pagination={{ pageSize: 10, showSizeChanger: false }}
        locale={{
          emptyText: <Empty description="暂无报名记录" />,
        }}
      />
    </Card>
  )
}
