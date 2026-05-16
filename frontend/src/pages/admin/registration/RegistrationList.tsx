import { useEffect, useRef, useState } from 'react'
import {
  Table, Button, Input, Space, Tag, Modal, Form, Popconfirm, App, Select, Card,
} from 'antd'
import {
  ExportOutlined, CheckOutlined, CloseOutlined, DownloadOutlined,
} from '@ant-design/icons'
import { pageReg, auditReg, cancelReg, ticketFile } from '@/api/registration'
import useAuthStore from '@/store/auth'
import type { Registration } from '@/types'

type Status = 'PENDING' | 'APPROVED' | 'REJECTED'
const statusColor: Record<Status, string> = { PENDING: 'blue', APPROVED: 'green', REJECTED: 'red' }
const statusText: Record<Status, string> = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }

export default function RegistrationList() {
  const { message } = App.useApp()
  const token = useAuthStore((st) => st.token)
  const [data, setData] = useState<Registration[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null)
  const [query, setQuery] = useState<any>({
    current: 1, size: 10, keyword: '', status: undefined, paymentStatus: undefined,
  })
  const [rejectOpen, setRejectOpen] = useState(false)
  const [rejectTarget, setRejectTarget] = useState<Registration | null>(null)
  const [form] = Form.useForm()
  const latestLoadIdRef = useRef(0)
  const initialLoading = loading && data.length === 0

  const load = async (nextQuery = query) => {
    const loadId = ++latestLoadIdRef.current
    setLoading(true)
    try {
      const res = await pageReg(nextQuery)
      if (loadId !== latestLoadIdRef.current) return
      setData(res.data.records)
      setTotal(res.data.total)
    } finally {
      if (loadId === latestLoadIdRef.current) {
        setLoading(false)
      }
    }
  }
  useEffect(() => { void load(query) }, [query])

  const applyAuditLocally = (id: number, status: Status) => {
    setData((prev) => {
      if (query.status === 'PENDING') {
        return prev.filter((item) => item.id !== id)
      }
      return prev.map((item) => {
        if (item.id !== id) return item
        return {
          ...item,
          status,
          paymentStatus: status === 'APPROVED' ? 'PAID' : item.paymentStatus,
        }
      })
    })
    if (query.status === 'PENDING') {
      setTotal((prev) => Math.max(0, prev - 1))
    }
  }

  const onApprove = async (id: number) => {
    setActionLoadingId(id)
    try {
      await auditReg(id, 'APPROVED')
      // 先本地乐观更新状态，保证 UI 立即响应
      applyAuditLocally(id, 'APPROVED')
      message.success('已通过')
      // 稍微延迟拉取，确保后端缓存清理和分片数据库更新彻底对外可见
      setTimeout(() => {
        void load(query)
      }, 300)
    } catch {
      message.error('审核失败')
    } finally {
      setActionLoadingId(null)
    }
  }
  const onRejectOpen = (r: Registration) => {
    setRejectTarget(r)
    form.resetFields()
    setRejectOpen(true)
  }
  const onRejectOk = async () => {
    const v = await form.validateFields()
    if (!rejectTarget) return
    setActionLoadingId(rejectTarget.id)
    try {
      await auditReg(rejectTarget.id, 'REJECTED', v.remark)
      applyAuditLocally(rejectTarget.id, 'REJECTED')
      message.success('已拒绝')
      setRejectOpen(false)
      // 稍微延迟拉取，确保后端状态同步
      setTimeout(() => {
        void load(query)
      }, 300)
    } catch {
      message.error('审核失败')
    } finally {
      setActionLoadingId(null)
    }
  }
  const onCancel = async (id: number) => {
    await cancelReg(id)
    message.success('已取消')
    load()
  }
  const onExport = () => {
    const params = new URLSearchParams()
    if (query.keyword) params.set('keyword', query.keyword)
    if (query.status) params.set('status', query.status)
    if (query.paymentStatus) params.set('paymentStatus', query.paymentStatus)
    fetch(`/api/registrations/export?${params.toString()}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
      .then((r) => {
        if (!r.ok) throw new Error('导出失败')
        return r.blob()
      })
      .then((blob) => {
        const a = document.createElement('a')
        a.href = URL.createObjectURL(blob)
        a.download = `registrations_${Date.now()}.xlsx`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(a.href)
        message.success('导出成功')
      })
      .catch(() => message.error('导出失败'))
  }
  const onTicket = (id: number) => {
    ticketFile(id)
      .then((resp) => {
        const blob = resp.data as Blob
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.target = '_blank'
        link.rel = 'noopener noreferrer'
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.setTimeout(() => URL.revokeObjectURL(url), 1000)
      })
      .catch(() => message.error('准考证下载失败'))
  }

  const columns = [
    { title: '报名编号', dataIndex: 'registrationNo', width: 160 },
    { title: '学生姓名', dataIndex: 'studentName', width: 110 },
    { title: '计划名称', dataIndex: 'planName' },
    { title: '课程', dataIndex: 'courseName' },
    { title: '考试日期', dataIndex: 'examDate', width: 110 },
    {
      title: '审核状态', dataIndex: 'status', width: 100,
      render: (s: Status) => <Tag color={statusColor[s]}>{statusText[s]}</Tag>,
    },
    {
      title: '缴费状态', dataIndex: 'paymentStatus', width: 100,
      render: (p: string) => <Tag color={p === 'PAID' ? 'green' : 'orange'}>{p === 'PAID' ? '已缴费' : '未缴费'}</Tag>,
    },
    { title: '准考证号', dataIndex: 'admissionTicketNo', width: 160 },
    {
      title: '操作', width: 320, fixed: 'right' as const,
      render: (_: any, r: Registration) => (
        <Space wrap>
          {r.status === 'PENDING' && (
            <>
              <Button size="small" type="primary" icon={<CheckOutlined />} loading={actionLoadingId === r.id} onClick={() => onApprove(r.id)}>通过</Button>
              <Button size="small" danger icon={<CloseOutlined />} disabled={actionLoadingId === r.id} onClick={() => onRejectOpen(r)}>拒绝</Button>
            </>
          )}
          {r.status === 'APPROVED' && (
            <Button size="small" icon={<DownloadOutlined />} onClick={() => onTicket(r.id)}>准考证</Button>
          )}
          <Popconfirm title="确认取消该报名？" onConfirm={() => onCancel(r.id)}>
            <Button size="small" danger>取消</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Card bordered={false} className="rounded-2xl shadow-soft mb-4" loading={initialLoading}>
        <div className="flex flex-wrap gap-3 items-center">
        <Select placeholder="审核状态" allowClear style={{ width: 130 }} value={query.status}
          onChange={(v) => setQuery({ ...query, status: v, current: 1 })}
          options={[
            { value: 'PENDING', label: '待审核' },
            { value: 'APPROVED', label: '已通过' },
            { value: 'REJECTED', label: '已拒绝' },
          ]} />
        <Select placeholder="缴费状态" allowClear style={{ width: 130 }} value={query.paymentStatus}
          onChange={(v) => setQuery({ ...query, paymentStatus: v, current: 1 })}
          options={[
            { value: 'PAID', label: '已缴费' },
            { value: 'UNPAID', label: '未缴费' },
          ]} />
        <Input.Search placeholder="搜索报名编号/学生" allowClear style={{ width: 240 }}
          onSearch={(v) => setQuery({ ...query, keyword: v, current: 1 })} />
        <Button icon={<ExportOutlined />} onClick={onExport}>导出 Excel</Button>
        </div>
      </Card>

      <Card bordered={false} className="rounded-2xl shadow-soft" loading={initialLoading}>
        <Table rowKey="id" dataSource={data} columns={columns as any} loading={!initialLoading && loading}
          scroll={{ x: 1400 }}
          pagination={{
            current: query.current, pageSize: query.size, total, showSizeChanger: true,
            onChange: (page, size) => setQuery({ ...query, current: page, size }),
          }} />
      </Card>

      <Modal title="填写拒绝原因" open={rejectOpen}
        onCancel={() => setRejectOpen(false)} onOk={onRejectOk} confirmLoading={rejectTarget != null && actionLoadingId === rejectTarget.id} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item label="审核备注" name="remark" rules={[{ required: true, message: '请填写拒绝原因' }]}>
            <Input.TextArea rows={3} placeholder="请说明拒绝该报名的原因" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
