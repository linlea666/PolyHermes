import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  Card,
  Form,
  InputNumber,
  Switch,
  Button,
  Space,
  message,
  Spin,
  Typography,
  Tooltip,
  Collapse,
  Table,
  Tag
} from 'antd'
import {
  ArrowLeftOutlined,
  QuestionCircleOutlined,
  SaveOutlined,
  ReloadOutlined,
  HistoryOutlined
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useMediaQuery } from 'react-responsive'
import apiService from '../services/api'
import type { SmartTakeProfitConfig, SmartTakeProfitLog, PositionRiskAssessment } from '../types'
import { formatUSDC } from '../utils'

const { Title, Text } = Typography
const { Panel } = Collapse

const SmartTakeProfitSettings: React.FC = () => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const isMobile = useMediaQuery({ maxWidth: 768 })
  const accountId = searchParams.get('accountId')
  
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [, setConfig] = useState<SmartTakeProfitConfig | null>(null)
  const [logs, setLogs] = useState<SmartTakeProfitLog[]>([])
  const [logsLoading, setLogsLoading] = useState(false)
  const [riskAssessments, setRiskAssessments] = useState<PositionRiskAssessment[]>([])
  const [riskLoading, setRiskLoading] = useState(false)
  const [accountName, setAccountName] = useState<string>('')
  
  useEffect(() => {
    if (!accountId) {
      message.error(t('smartTakeProfit.accountIdRequired'))
      navigate('/accounts')
      return
    }
    
    loadConfig()
    loadLogs()
    loadRiskAssessment()
  }, [accountId])
  
  const loadConfig = async () => {
    if (!accountId) return
    
    setLoading(true)
    try {
      const response = await apiService.smartTakeProfit.getConfig({ accountId: Number(accountId) })
      if (response.data.code === 0) {
        const configData = response.data.data
        setConfig(configData)
        setAccountName(configData?.accountName || '')
        
        if (configData) {
          form.setFieldsValue({
            enabled: configData.enabled,
            takeProfitEnabled: configData.takeProfitEnabled,
            takeProfitBaseThreshold: parseFloat(configData.takeProfitBaseThreshold),
            takeProfitRatio: parseFloat(configData.takeProfitRatio),
            takeProfitKeepRatio: parseFloat(configData.takeProfitKeepRatio),
            stopLossEnabled: configData.stopLossEnabled,
            stopLossThreshold: parseFloat(configData.stopLossThreshold),
            stopLossRatio: parseFloat(configData.stopLossRatio),
            liquidityAdjustEnabled: configData.liquidityAdjustEnabled,
            liquidityDangerRatio: parseFloat(configData.liquidityDangerRatio),
            liquidityWarningRatio: parseFloat(configData.liquidityWarningRatio),
            liquiditySafeRatio: parseFloat(configData.liquiditySafeRatio),
            timeDecayEnabled: configData.timeDecayEnabled,
            timeDecayStartMinutes: configData.timeDecayStartMinutes,
            timeDecayUrgentMinutes: configData.timeDecayUrgentMinutes,
            timeDecayCriticalMinutes: configData.timeDecayCriticalMinutes,
            useLimitOrder: configData.useLimitOrder,
            limitOrderPremium: parseFloat(configData.limitOrderPremium),
            limitOrderWaitSeconds: configData.limitOrderWaitSeconds,
            priceRetryEnabled: configData.priceRetryEnabled,
            priceRetryStep: parseFloat(configData.priceRetryStep),
            maxPriceSlippage: parseFloat(configData.maxPriceSlippage),
            maxRetryCount: configData.maxRetryCount
          })
        } else {
          // 设置默认值
          form.setFieldsValue({
            enabled: false,
            takeProfitEnabled: true,
            takeProfitBaseThreshold: 10,
            takeProfitRatio: 30,
            takeProfitKeepRatio: 20,
            stopLossEnabled: false,
            stopLossThreshold: -20,
            stopLossRatio: 100,
            liquidityAdjustEnabled: true,
            liquidityDangerRatio: 0.3,
            liquidityWarningRatio: 1.0,
            liquiditySafeRatio: 3.0,
            timeDecayEnabled: true,
            timeDecayStartMinutes: 30,
            timeDecayUrgentMinutes: 5,
            timeDecayCriticalMinutes: 2,
            useLimitOrder: true,
            limitOrderPremium: 1,
            limitOrderWaitSeconds: 60,
            priceRetryEnabled: true,
            priceRetryStep: 1,
            maxPriceSlippage: 5,
            maxRetryCount: 3
          })
        }
      } else {
        message.error(response.data.msg || t('smartTakeProfit.loadFailed'))
      }
    } catch (error) {
      console.error('加载配置失败:', error)
      message.error(t('smartTakeProfit.loadFailed'))
    } finally {
      setLoading(false)
    }
  }
  
  const loadLogs = async () => {
    if (!accountId) return
    
    setLogsLoading(true)
    try {
      const response = await apiService.smartTakeProfit.getLogs({
        accountId: Number(accountId),
        page: 0,
        size: 10
      })
      if (response.data.code === 0 && response.data.data) {
        setLogs(response.data.data.content || [])
      }
    } catch (error) {
      console.error('加载日志失败:', error)
    } finally {
      setLogsLoading(false)
    }
  }
  
  const loadRiskAssessment = async () => {
    if (!accountId) return
    
    setRiskLoading(true)
    try {
      const response = await apiService.smartTakeProfit.getRiskAssessment({ accountId: Number(accountId) })
      if (response.data.code === 0 && response.data.data) {
        setRiskAssessments(response.data.data)
      }
    } catch (error) {
      console.error('加载风险评估失败:', error)
    } finally {
      setRiskLoading(false)
    }
  }
  
  const handleSave = async (values: Record<string, unknown>) => {
    if (!accountId) return
    
    setSaving(true)
    try {
      const response = await apiService.smartTakeProfit.saveConfig({
        accountId: Number(accountId),
        enabled: values.enabled as boolean,
        takeProfitEnabled: values.takeProfitEnabled as boolean,
        takeProfitBaseThreshold: String(values.takeProfitBaseThreshold),
        takeProfitRatio: String(values.takeProfitRatio),
        takeProfitKeepRatio: String(values.takeProfitKeepRatio),
        stopLossEnabled: values.stopLossEnabled as boolean,
        stopLossThreshold: String(values.stopLossThreshold),
        stopLossRatio: String(values.stopLossRatio),
        liquidityAdjustEnabled: values.liquidityAdjustEnabled as boolean,
        liquidityDangerRatio: String(values.liquidityDangerRatio),
        liquidityWarningRatio: String(values.liquidityWarningRatio),
        liquiditySafeRatio: String(values.liquiditySafeRatio),
        timeDecayEnabled: values.timeDecayEnabled as boolean,
        timeDecayStartMinutes: values.timeDecayStartMinutes as number,
        timeDecayUrgentMinutes: values.timeDecayUrgentMinutes as number,
        timeDecayCriticalMinutes: values.timeDecayCriticalMinutes as number,
        useLimitOrder: values.useLimitOrder as boolean,
        limitOrderPremium: String(values.limitOrderPremium),
        limitOrderWaitSeconds: values.limitOrderWaitSeconds as number,
        priceRetryEnabled: values.priceRetryEnabled as boolean,
        priceRetryStep: String(values.priceRetryStep),
        maxPriceSlippage: String(values.maxPriceSlippage),
        maxRetryCount: values.maxRetryCount as number
      })
      
      if (response.data.code === 0) {
        message.success(t('smartTakeProfit.saveSuccess'))
        setConfig(response.data.data)
      } else {
        message.error(response.data.msg || t('smartTakeProfit.saveFailed'))
      }
    } catch (error) {
      console.error('保存配置失败:', error)
      message.error(t('smartTakeProfit.saveFailed'))
    } finally {
      setSaving(false)
    }
  }
  
  const getRiskLevelColor = (level: string) => {
    switch (level) {
      case 'SAFE': return 'success'
      case 'WARNING': return 'warning'
      case 'DANGER': case 'URGENT': return 'error'
      case 'CRITICAL': return 'error'
      default: return 'default'
    }
  }
  
  const getRiskLevelText = (level: string) => {
    switch (level) {
      case 'SAFE': return t('smartTakeProfit.riskLevel.safe')
      case 'WARNING': return t('smartTakeProfit.riskLevel.warning')
      case 'DANGER': return t('smartTakeProfit.riskLevel.danger')
      case 'URGENT': return t('smartTakeProfit.riskLevel.urgent')
      case 'CRITICAL': return t('smartTakeProfit.riskLevel.critical')
      default: return t('smartTakeProfit.riskLevel.unknown')
    }
  }
  
  const getTriggerTypeText = (type: string) => {
    switch (type) {
      case 'TAKE_PROFIT': return t('smartTakeProfit.triggerType.takeProfit')
      case 'STOP_LOSS': return t('smartTakeProfit.triggerType.stopLoss')
      case 'FORCED_LIQUIDITY': return t('smartTakeProfit.triggerType.forcedLiquidity')
      case 'FORCED_TIME': return t('smartTakeProfit.triggerType.forcedTime')
      default: return type
    }
  }
  
  const logColumns = [
    {
      title: t('smartTakeProfit.logs.market'),
      dataIndex: 'marketTitle',
      key: 'marketTitle',
      ellipsis: true,
      width: 200
    },
    {
      title: t('smartTakeProfit.logs.outcome'),
      dataIndex: 'outcome',
      key: 'outcome',
      width: 80
    },
    {
      title: t('smartTakeProfit.logs.triggerType'),
      dataIndex: 'triggerType',
      key: 'triggerType',
      width: 120,
      render: (type: string) => (
        <Tag color={type === 'STOP_LOSS' ? 'error' : 'success'}>
          {getTriggerTypeText(type)}
        </Tag>
      )
    },
    {
      title: t('smartTakeProfit.logs.pnl'),
      dataIndex: 'triggerPnlPercent',
      key: 'triggerPnlPercent',
      width: 100,
      render: (pnl: string) => (
        <Text type={parseFloat(pnl) >= 0 ? 'success' : 'danger'}>
          {parseFloat(pnl).toFixed(2)}%
        </Text>
      )
    },
    {
      title: t('smartTakeProfit.logs.soldAmount'),
      dataIndex: 'soldAmount',
      key: 'soldAmount',
      width: 120,
      render: (amount: string) => `$${formatUSDC(amount)}`
    },
    {
      title: t('smartTakeProfit.logs.status'),
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (status: string) => (
        <Tag color={status === 'SUCCESS' ? 'success' : status === 'PENDING' ? 'processing' : 'error'}>
          {status}
        </Tag>
      )
    },
    {
      title: t('smartTakeProfit.logs.time'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (time: number) => new Date(time).toLocaleString()
    }
  ]
  
  const riskColumns = [
    {
      title: t('smartTakeProfit.risk.market'),
      dataIndex: 'marketId',
      key: 'marketId',
      ellipsis: true,
      width: 150
    },
    {
      title: t('smartTakeProfit.risk.outcome'),
      dataIndex: 'outcome',
      key: 'outcome',
      width: 80
    },
    {
      title: t('smartTakeProfit.risk.positionValue'),
      dataIndex: 'positionValue',
      key: 'positionValue',
      width: 120,
      render: (value: string) => `$${formatUSDC(value)}`
    },
    {
      title: t('smartTakeProfit.risk.liquidity'),
      key: 'liquidity',
      width: 150,
      render: (_: unknown, record: PositionRiskAssessment) => (
        <Space direction="vertical" size={0}>
          <Tag color={getRiskLevelColor(record.liquidityLevel)}>
            {getRiskLevelText(record.liquidityLevel)}
          </Tag>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {t('smartTakeProfit.risk.ratio')}: {record.liquidityRatio}x
          </Text>
        </Space>
      )
    },
    {
      title: t('smartTakeProfit.risk.time'),
      key: 'time',
      width: 150,
      render: (_: unknown, record: PositionRiskAssessment) => (
        <Space direction="vertical" size={0}>
          <Tag color={getRiskLevelColor(record.timeLevel)}>
            {getRiskLevelText(record.timeLevel)}
          </Tag>
          {record.remainingMinutes !== null && record.remainingMinutes !== undefined && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {record.remainingMinutes} {t('smartTakeProfit.risk.minutesLeft')}
            </Text>
          )}
        </Space>
      )
    },
    {
      title: t('smartTakeProfit.risk.pnl'),
      dataIndex: 'currentPnlPercent',
      key: 'currentPnlPercent',
      width: 100,
      render: (pnl: string) => (
        <Text type={parseFloat(pnl) >= 0 ? 'success' : 'danger'}>
          {parseFloat(pnl).toFixed(2)}%
        </Text>
      )
    },
    {
      title: t('smartTakeProfit.risk.wouldTrigger'),
      dataIndex: 'wouldTrigger',
      key: 'wouldTrigger',
      width: 100,
      render: (trigger: boolean) => (
        <Tag color={trigger ? 'warning' : 'default'}>
          {trigger ? t('smartTakeProfit.risk.yes') : t('smartTakeProfit.risk.no')}
        </Tag>
      )
    }
  ]
  
  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '50px' }}>
        <Spin size="large" />
      </div>
    )
  }
  
  return (
    <div style={{ padding: isMobile ? '16px' : '24px' }}>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>
          {t('common.back')}
        </Button>
        <Title level={4} style={{ margin: 0 }}>
          {t('smartTakeProfit.title')} - {accountName || `Account #${accountId}`}
        </Title>
      </Space>
      
      <Form
        form={form}
        layout="vertical"
        onFinish={handleSave}
      >
        {/* 主开关 */}
        <Card title={t('smartTakeProfit.mainSwitch')} style={{ marginBottom: 16 }}>
          <Form.Item
            name="enabled"
            label={t('smartTakeProfit.enableSmartTP')}
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
        </Card>
        
        <Collapse defaultActiveKey={['takeProfit', 'stopLoss']} style={{ marginBottom: 16 }}>
          {/* 止盈配置 */}
          <Panel header={t('smartTakeProfit.takeProfitConfig')} key="takeProfit">
            <Form.Item
              name="takeProfitEnabled"
              label={t('smartTakeProfit.takeProfitEnabled')}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
            
            <Form.Item
              name="takeProfitBaseThreshold"
              label={
                <Space>
                  {t('smartTakeProfit.takeProfitBaseThreshold')}
                  <Tooltip title={t('smartTakeProfit.takeProfitBaseThresholdTip')}>
                    <QuestionCircleOutlined />
                  </Tooltip>
                </Space>
              }
            >
              <InputNumber
                min={0}
                max={100}
                step={1}
                addonAfter="%"
                style={{ width: '100%' }}
              />
            </Form.Item>
            
            <Form.Item
              name="takeProfitRatio"
              label={
                <Space>
                  {t('smartTakeProfit.takeProfitRatio')}
                  <Tooltip title={t('smartTakeProfit.takeProfitRatioTip')}>
                    <QuestionCircleOutlined />
                  </Tooltip>
                </Space>
              }
            >
              <InputNumber
                min={1}
                max={100}
                step={5}
                addonAfter="%"
                style={{ width: '100%' }}
              />
            </Form.Item>
            
            <Form.Item
              name="takeProfitKeepRatio"
              label={
                <Space>
                  {t('smartTakeProfit.takeProfitKeepRatio')}
                  <Tooltip title={t('smartTakeProfit.takeProfitKeepRatioTip')}>
                    <QuestionCircleOutlined />
                  </Tooltip>
                </Space>
              }
            >
              <InputNumber
                min={0}
                max={100}
                step={5}
                addonAfter="%"
                style={{ width: '100%' }}
              />
            </Form.Item>
          </Panel>
          
          {/* 止损配置 */}
          <Panel header={t('smartTakeProfit.stopLossConfig')} key="stopLoss">
            <Form.Item
              name="stopLossEnabled"
              label={t('smartTakeProfit.stopLossEnabled')}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
            
            <Form.Item
              name="stopLossThreshold"
              label={t('smartTakeProfit.stopLossThreshold')}
            >
              <InputNumber
                min={-100}
                max={0}
                step={5}
                addonAfter="%"
                style={{ width: '100%' }}
              />
            </Form.Item>
            
            <Form.Item
              name="stopLossRatio"
              label={t('smartTakeProfit.stopLossRatio')}
            >
              <InputNumber
                min={1}
                max={100}
                step={10}
                addonAfter="%"
                style={{ width: '100%' }}
              />
            </Form.Item>
          </Panel>
          
          {/* 流动性动态调整 */}
          <Panel header={t('smartTakeProfit.liquidityConfig')} key="liquidity">
            <Form.Item
              name="liquidityAdjustEnabled"
              label={t('smartTakeProfit.liquidityAdjustEnabled')}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
            
            <Form.Item
              name="liquidityDangerRatio"
              label={
                <Space>
                  {t('smartTakeProfit.liquidityDangerRatio')}
                  <Tooltip title={t('smartTakeProfit.liquidityDangerRatioTip')}>
                    <QuestionCircleOutlined />
                  </Tooltip>
                </Space>
              }
            >
              <InputNumber
                min={0}
                max={1}
                step={0.1}
                style={{ width: '100%' }}
              />
            </Form.Item>
            
            <Form.Item
              name="liquidityWarningRatio"
              label={t('smartTakeProfit.liquidityWarningRatio')}
            >
              <InputNumber
                min={0}
                max={10}
                step={0.5}
                style={{ width: '100%' }}
              />
            </Form.Item>
            
            <Form.Item
              name="liquiditySafeRatio"
              label={t('smartTakeProfit.liquiditySafeRatio')}
            >
              <InputNumber
                min={1}
                max={10}
                step={0.5}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </Panel>
          
          {/* 时间衰减 */}
          <Panel header={t('smartTakeProfit.timeDecayConfig')} key="timeDecay">
            <Form.Item
              name="timeDecayEnabled"
              label={t('smartTakeProfit.timeDecayEnabled')}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
            
            <Form.Item
              name="timeDecayStartMinutes"
              label={t('smartTakeProfit.timeDecayStartMinutes')}
            >
              <InputNumber
                min={5}
                max={120}
                step={5}
                addonAfter={t('smartTakeProfit.minutes')}
                style={{ width: '100%' }}
              />
            </Form.Item>
            
            <Form.Item
              name="timeDecayUrgentMinutes"
              label={t('smartTakeProfit.timeDecayUrgentMinutes')}
            >
              <InputNumber
                min={1}
                max={30}
                step={1}
                addonAfter={t('smartTakeProfit.minutes')}
                style={{ width: '100%' }}
              />
            </Form.Item>
            
            <Form.Item
              name="timeDecayCriticalMinutes"
              label={t('smartTakeProfit.timeDecayCriticalMinutes')}
            >
              <InputNumber
                min={1}
                max={10}
                step={1}
                addonAfter={t('smartTakeProfit.minutes')}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </Panel>
          
          {/* 卖出执行策略 */}
          <Panel header={t('smartTakeProfit.executionConfig')} key="execution">
            <Form.Item
              name="useLimitOrder"
              label={
                <Space>
                  {t('smartTakeProfit.useLimitOrder')}
                  <Tooltip title={t('smartTakeProfit.useLimitOrderTip')}>
                    <QuestionCircleOutlined />
                  </Tooltip>
                </Space>
              }
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
            
            <Form.Item
              name="limitOrderPremium"
              label={t('smartTakeProfit.limitOrderPremium')}
            >
              <InputNumber
                min={0}
                max={5}
                step={0.5}
                addonAfter="%"
                style={{ width: '100%' }}
              />
            </Form.Item>
            
            <Form.Item
              name="limitOrderWaitSeconds"
              label={t('smartTakeProfit.limitOrderWaitSeconds')}
            >
              <InputNumber
                min={10}
                max={300}
                step={10}
                addonAfter={t('smartTakeProfit.seconds')}
                style={{ width: '100%' }}
              />
            </Form.Item>
            
            <Form.Item
              name="priceRetryEnabled"
              label={t('smartTakeProfit.priceRetryEnabled')}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
            
            <Form.Item
              name="priceRetryStep"
              label={t('smartTakeProfit.priceRetryStep')}
            >
              <InputNumber
                min={0.5}
                max={5}
                step={0.5}
                addonAfter="%"
                style={{ width: '100%' }}
              />
            </Form.Item>
            
            <Form.Item
              name="maxPriceSlippage"
              label={t('smartTakeProfit.maxPriceSlippage')}
            >
              <InputNumber
                min={1}
                max={20}
                step={1}
                addonAfter="%"
                style={{ width: '100%' }}
              />
            </Form.Item>
            
            <Form.Item
              name="maxRetryCount"
              label={t('smartTakeProfit.maxRetryCount')}
            >
              <InputNumber
                min={1}
                max={10}
                step={1}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </Panel>
        </Collapse>
        
        <Card style={{ marginBottom: 16 }}>
          <Space>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              htmlType="submit"
              loading={saving}
            >
              {t('common.save')}
            </Button>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => {
                loadConfig()
                loadRiskAssessment()
              }}
            >
              {t('common.refresh')}
            </Button>
          </Space>
        </Card>
      </Form>
      
      {/* 仓位风险评估 */}
      <Card
        title={t('smartTakeProfit.riskAssessment')}
        style={{ marginBottom: 16 }}
        extra={
          <Button
            icon={<ReloadOutlined />}
            onClick={loadRiskAssessment}
            loading={riskLoading}
          >
            {t('common.refresh')}
          </Button>
        }
      >
        <Table
          columns={riskColumns}
          dataSource={riskAssessments}
          rowKey={(record) => `${record.marketId}-${record.outcome}`}
          loading={riskLoading}
          pagination={false}
          scroll={{ x: 800 }}
          size="small"
        />
      </Card>
      
      {/* 执行日志 */}
      <Card
        title={
          <Space>
            <HistoryOutlined />
            {t('smartTakeProfit.executionLogs')}
          </Space>
        }
        extra={
          <Button
            icon={<ReloadOutlined />}
            onClick={loadLogs}
            loading={logsLoading}
          >
            {t('common.refresh')}
          </Button>
        }
      >
        <Table
          columns={logColumns}
          dataSource={logs}
          rowKey="id"
          loading={logsLoading}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 900 }}
          size="small"
        />
      </Card>
    </div>
  )
}

export default SmartTakeProfitSettings
