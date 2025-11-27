/**
 * API 统一响应格式
 */
export interface ApiResponse<T> {
  code: number
  data: T | null
  msg: string
}

/**
 * 账户信息
 */
export interface Account {
  id: number
  walletAddress: string
  accountName?: string
  isDefault: boolean
  apiKeyConfigured: boolean
  apiSecretConfigured: boolean
  apiPassphraseConfigured: boolean
  balance?: string
  totalOrders?: number
  totalPnl?: string
  activeOrders?: number
  completedOrders?: number
  positionCount?: number
}

/**
 * 账户列表响应
 */
export interface AccountListResponse {
  list: Account[]
  total: number
}

/**
 * 账户导入请求
 */
export interface AccountImportRequest {
  privateKey: string
  walletAddress: string
  accountName?: string
  isDefault?: boolean
}

/**
 * 账户更新请求
 */
export interface AccountUpdateRequest {
  accountId: number
  accountName?: string
  isDefault?: boolean
}

/**
 * Leader 信息
 */
export interface Leader {
  id: number
  leaderAddress: string
  leaderName?: string
  accountId?: number
  category?: string
  enabled: boolean
  copyRatio: string
  totalOrders?: number
  totalPnl?: string
}

/**
 * Leader 列表响应
 */
export interface LeaderListResponse {
  list: Leader[]
  total: number
}

/**
 * Leader 添加请求
 */
export interface LeaderAddRequest {
  leaderAddress: string
  leaderName?: string
  accountId?: number
  category?: string
  enabled?: boolean
}

/**
 * 跟单配置
 */
export interface CopyTradingConfig {
  copyMode: 'RATIO' | 'FIXED'
  copyRatio: string
  fixedAmount?: string
  maxOrderSize: string
  minOrderSize: string
  maxDailyLoss: string
  maxDailyOrders: number
  priceTolerance: string
  delaySeconds: number
  pollIntervalSeconds: number
  useWebSocket: boolean
  websocketReconnectInterval: number
  websocketMaxRetries: number
  enabled: boolean
}

/**
 * 跟单订单
 */
export interface CopyOrder {
  id: number
  accountId: number
  leaderId: number
  leaderAddress: string
  leaderName?: string
  marketId: string
  category: string
  side: 'BUY' | 'SELL'
  price: string
  size: string
  copyRatio: string
  orderId?: string
  status: string
  filledSize: string
  pnl?: string
  createdAt: number
}

/**
 * 订单列表响应
 */
export interface OrderListResponse {
  list: CopyOrder[]
  total: number
  page: number
  limit: number
}

/**
 * 统计信息
 */
export interface Statistics {
  totalOrders: number
  totalPnl: string
  winRate: string
  avgPnl: string
  maxProfit: string
  maxLoss: string
}

/**
 * 账户仓位信息
 */
export interface AccountPosition {
  accountId: number
  accountName?: string
  walletAddress: string
  proxyAddress: string
  marketId: string
  marketTitle?: string
  marketSlug?: string
  marketIcon?: string  // 市场图标 URL
  side: string  // YES 或 NO
  quantity: string
  avgPrice: string
  currentPrice: string
  currentValue: string
  initialValue: string
  pnl: string
  percentPnl: string
  realizedPnl?: string
  percentRealizedPnl?: string
  redeemable: boolean
  mergeable: boolean
  endDate?: string
  isCurrent: boolean  // true: 当前仓位（有持仓），false: 历史仓位（已平仓）
}

/**
 * 仓位列表响应
 */
export interface PositionListResponse {
  currentPositions: AccountPosition[]
  historyPositions: AccountPosition[]
}

/**
 * 仓位卖出请求
 */
export interface PositionSellRequest {
  accountId: number
  marketId: string
  side: 'YES' | 'NO'
  orderType: 'MARKET' | 'LIMIT'
  quantity: string
  price?: string  // 限价订单必需
}

/**
 * 仓位卖出响应
 */
export interface PositionSellResponse {
  orderId: string
  marketId: string
  side: string
  orderType: string
  quantity: string
  price?: string
  status: string
  createdAt: number
}

/**
 * 市场价格请求
 */
export interface MarketPriceRequest {
  marketId: string
}

/**
 * 市场价格响应
 */
export interface MarketPriceResponse {
  marketId: string
  lastPrice?: string
  bestBid?: string
  bestAsk?: string
  midpoint?: string
}

/**
 * 仓位推送消息类型
 */
export type PositionPushMessageType = 'FULL' | 'INCREMENTAL'

/**
 * 仓位推送消息
 */
export interface PositionPushMessage {
  type: PositionPushMessageType  // 消息类型：FULL（全量）或 INCREMENTAL（增量）
  timestamp: number  // 消息时间戳
  currentPositions?: AccountPosition[]  // 当前仓位列表（全量或增量）
  historyPositions?: AccountPosition[]  // 历史仓位列表（全量或增量）
  removedPositionKeys?: string[]  // 已删除的仓位键（仅增量推送时使用）
}

/**
 * 获取仓位唯一键
 */
export function getPositionKey(position: AccountPosition): string {
  return `${position.accountId}-${position.marketId}-${position.side}`
}

/**
 * Polymarket 订单消息（来自 WebSocket User Channel）
 */
export interface OrderMessage {
  asset_id: string
  associate_trades?: string[]
  event_type: string  // "order"
  id: string  // order id
  market: string  // condition ID of market
  order_owner: string  // owner of order
  original_size: string  // original order size
  outcome: string  // outcome
  owner: string  // owner of orders
  price: string  // price of order
  side: string  // BUY/SELL
  size_matched: string  // size of order that has been matched
  timestamp: string  // time of event
  type: string  // PLACEMENT/UPDATE/CANCELLATION
}

/**
 * 订单详情（通过 API 获取）
 */
export interface OrderDetail {
  id: string  // 订单 ID
  market: string  // 市场 ID (condition ID)
  side: string  // BUY/SELL
  price: string  // 价格
  size: string  // 订单大小
  filled: string  // 已成交数量
  status: string  // 订单状态
  createdAt: string  // 创建时间（ISO 8601 格式）
  marketName?: string  // 市场名称
  marketSlug?: string  // 市场 slug
  marketIcon?: string  // 市场图标
}

/**
 * 订单推送消息
 */
export interface OrderPushMessage {
  accountId: number
  accountName: string
  order: OrderMessage  // 订单信息（来自 WebSocket）
  orderDetail?: OrderDetail  // 订单详情（通过 API 获取）
  timestamp?: number  // 推送时间戳
}

