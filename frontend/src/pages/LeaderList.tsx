import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Table, Button, Space, Tag, Popconfirm, message, List, Empty, Spin, Divider, Typography } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, LinkOutlined, GlobalOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { apiService } from '../services/api'
import type { Leader } from '../types'
import { useMediaQuery } from 'react-responsive'

const { Text } = Typography

const LeaderList: React.FC = () => {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const isMobile = useMediaQuery({ maxWidth: 768 })
  const [leaders, setLeaders] = useState<Leader[]>([])
  const [loading, setLoading] = useState(false)
  
  useEffect(() => {
    fetchLeaders()
  }, [])
  
  const fetchLeaders = async () => {
    setLoading(true)
    try {
      const response = await apiService.leaders.list()
      if (response.data.code === 0 && response.data.data) {
        setLeaders(response.data.data.list || [])
      } else {
        message.error(response.data.msg || t('leaderList.fetchFailed') || '获取 Leader 列表失败')
      }
    } catch (error: any) {
      message.error(error.message || t('leaderList.fetchFailed') || '获取 Leader 列表失败')
    } finally {
      setLoading(false)
    }
  }
  
  const handleDelete = async (leaderId: number) => {
    try {
      const response = await apiService.leaders.delete({ leaderId })
      if (response.data.code === 0) {
        message.success(t('leaderList.deleteSuccess') || '删除 Leader 成功')
        fetchLeaders()
      } else {
        message.error(response.data.msg || t('leaderList.deleteFailed') || '删除 Leader 失败')
      }
    } catch (error: any) {
      message.error(error.message || t('leaderList.deleteFailed') || '删除 Leader 失败')
    }
  }
  
  const columns = [
    {
      title: t('leaderList.leaderName') || 'Leader 名称',
      dataIndex: 'leaderName',
      key: 'leaderName',
      render: (text: string, record: Leader) => text || `Leader ${record.id}`
    },
    {
      title: t('leaderList.walletAddress') || '钱包地址',
      dataIndex: 'leaderAddress',
      key: 'leaderAddress',
      render: (address: string) => (
        <span style={{ fontFamily: 'monospace', fontSize: isMobile ? '12px' : '14px' }}>
          {isMobile ? `${address.slice(0, 6)}...${address.slice(-4)}` : address}
        </span>
      )
    },
    {
      title: t('leaderList.remark') || '备注',
      dataIndex: 'remark',
      key: 'remark',
      render: (remark: string | undefined) => remark ? (
        <Text ellipsis={{ tooltip: remark }} style={{ maxWidth: isMobile ? 100 : 200 }}>
          {remark}
        </Text>
      ) : <Text type="secondary">-</Text>
    },
    {
      title: t('leaderList.copyTradingCount') || '跟单关系数',
      dataIndex: 'copyTradingCount',
      key: 'copyTradingCount',
      render: (count: number) => <Tag>{count}</Tag>
    },
    {
      title: t('leaderList.createdAt') || '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (timestamp: number) => {
        const date = new Date(timestamp)
        return date.toLocaleString(i18n.language || 'zh-CN', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit'
        })
      }
    },
    {
      title: t('common.actions') || '操作',
      key: 'action',
      width: isMobile ? 150 : 200,
      render: (_: any, record: Leader) => (
        <Space size="small" wrap>
          {record.website && (
            <Button
              type="link"
              size="small"
              icon={<GlobalOutlined />}
              onClick={() => window.open(record.website, '_blank', 'noopener,noreferrer')}
            >
              {t('leaderList.openWebsite') || '打开网页'}
            </Button>
          )}
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => navigate(`/leaders/edit?id=${record.id}`)}
          >
            {t('common.edit') || '编辑'}
          </Button>
          <Popconfirm
            title={t('leaderList.deleteConfirm') || '确定要删除这个 Leader 吗？'}
            description={record.copyTradingCount > 0 ? t('leaderList.deleteConfirmDesc', { count: record.copyTradingCount }) || `该 Leader 还有 ${record.copyTradingCount} 个跟单关系，请先删除跟单关系` : undefined}
            onConfirm={() => handleDelete(record.id)}
            okText={t('common.confirm') || '确定'}
            cancelText={t('common.cancel') || '取消'}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              {t('common.delete') || '删除'}
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ]
  
  return (
    <div>
      <div style={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'center',
        marginBottom: '16px',
        flexWrap: 'wrap',
        gap: '12px'
      }}>
        <h2 style={{ margin: 0 }}>{t('leaderList.title') || 'Leader 管理'}</h2>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => navigate('/leaders/add')}
          size={isMobile ? 'middle' : 'large'}
        >
          {t('leaderList.addLeader') || '添加 Leader'}
        </Button>
      </div>
      
      <Card>
        {isMobile ? (
          // 移动端卡片布局
          <div>
            {loading ? (
              <div style={{ textAlign: 'center', padding: '40px' }}>
                <Spin size="large" />
              </div>
            ) : leaders.length === 0 ? (
              <Empty description={t('leaderList.noData') || '暂无 Leader 数据'} />
            ) : (
              <List
                dataSource={leaders}
                renderItem={(leader) => {
                  const date = new Date(leader.createdAt)
                  const formattedDate = date.toLocaleString(i18n.language || 'zh-CN', {
                    year: 'numeric',
                    month: '2-digit',
                    day: '2-digit',
                    hour: '2-digit',
                    minute: '2-digit'
                  })
                  
                  return (
                    <Card
                      key={leader.id}
                      style={{
                        marginBottom: '12px',
                        borderRadius: '12px',
                        boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
                        border: '1px solid #e8e8e8'
                      }}
                      bodyStyle={{ padding: '16px' }}
                    >
                      {/* Leader 名称和地址 */}
                      <div style={{ marginBottom: '12px' }}>
                        <div style={{ 
                          fontSize: '16px', 
                          fontWeight: 'bold', 
                          marginBottom: '8px',
                          color: '#1890ff'
                        }}>
                          {leader.leaderName || `Leader ${leader.id}`}
                        </div>
                        <div style={{ 
                          fontSize: '12px', 
                          color: '#666',
                          fontFamily: 'monospace',
                          wordBreak: 'break-all'
                        }}>
                          {leader.leaderAddress}
                        </div>
                      </div>
                      
                      {/* 备注 */}
                      {leader.remark && (
                        <div style={{ marginBottom: '12px' }}>
                          <Text type="secondary" style={{ fontSize: '12px' }}>
                            {t('leaderList.remark') || '备注'}：
                          </Text>
                          <Text style={{ fontSize: '12px', marginLeft: '4px' }}>
                            {leader.remark}
                          </Text>
                        </div>
                      )}
                      
                      {/* 网站 */}
                      {leader.website && (
                        <div style={{ marginBottom: '12px' }}>
                          <Text type="secondary" style={{ fontSize: '12px' }}>
                            {t('leaderList.website') || '网站'}：
                          </Text>
                          <a 
                            href={leader.website} 
                            target="_blank" 
                            rel="noopener noreferrer"
                            style={{ fontSize: '12px', marginLeft: '4px' }}
                          >
                            <LinkOutlined /> {leader.website}
                          </a>
                        </div>
                      )}
                      
                      <Divider style={{ margin: '12px 0' }} />
                      
                      {/* 跟单关系数 */}
                      <div style={{ marginBottom: '12px' }}>
                        <Tag>{t('leaderList.copyTradingRelations', { count: leader.copyTradingCount }) || `${leader.copyTradingCount} 个跟单关系`}</Tag>
                      </div>
                      
                      {/* 创建时间 */}
                      <div style={{ marginBottom: '12px', fontSize: '12px', color: '#999' }}>
                        {t('leaderList.createdAt') || '创建时间'}: {formattedDate}
                      </div>
                      
                      {/* 操作按钮 */}
                      <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                        {leader.website && (
                          <Button
                            type="link"
                            size="small"
                            icon={<GlobalOutlined />}
                            onClick={() => window.open(leader.website, '_blank', 'noopener,noreferrer')}
                            style={{ flex: 1, minWidth: '80px' }}
                          >
                            {t('leaderList.openWebsite') || '打开网页'}
                          </Button>
                        )}
                        <Button
                          type="link"
                          size="small"
                          icon={<EditOutlined />}
                          onClick={() => navigate(`/leaders/edit?id=${leader.id}`)}
                          style={{ flex: 1, minWidth: '80px' }}
                        >
                          {t('common.edit') || '编辑'}
                        </Button>
                        <Popconfirm
                          title={t('leaderList.deleteConfirm') || '确定要删除这个 Leader 吗？'}
                          description={leader.copyTradingCount > 0 ? t('leaderList.deleteConfirmDesc', { count: leader.copyTradingCount }) || `该 Leader 还有 ${leader.copyTradingCount} 个跟单关系，请先删除跟单关系` : undefined}
                          onConfirm={() => handleDelete(leader.id)}
                          okText={t('common.confirm') || '确定'}
                          cancelText={t('common.cancel') || '取消'}
                        >
                          <Button 
                            type="link" 
                            size="small" 
                            danger 
                            icon={<DeleteOutlined />}
                            style={{ flex: 1, minWidth: '80px' }}
                          >
                            {t('common.delete') || '删除'}
                          </Button>
                        </Popconfirm>
                      </div>
                    </Card>
                  )
                }}
              />
            )}
          </div>
        ) : (
          // 桌面端表格布局
          <Table
            dataSource={leaders}
            columns={columns}
            rowKey="id"
            loading={loading}
            pagination={{
              pageSize: 20,
              showSizeChanger: true
            }}
          />
        )}
      </Card>
    </div>
  )
}

export default LeaderList

