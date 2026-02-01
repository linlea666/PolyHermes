package com.wrbug.polymarketbot.entity

import jakarta.persistence.*
import java.math.BigDecimal

/**
 * 智能止盈止损执行日志
 * 
 * 记录每次止盈止损的执行详情，用于：
 * - 追溯执行历史
 * - 分析策略效果
 * - 问题排查
 */
@Entity
@Table(
    name = "smart_take_profit_logs",
    indexes = [
        Index(name = "idx_stpl_config_id", columnList = "config_id"),
        Index(name = "idx_stpl_account_id", columnList = "account_id"),
        Index(name = "idx_stpl_created_at", columnList = "created_at")
    ]
)
data class SmartTakeProfitLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    /** 关联配置ID */
    @Column(name = "config_id", nullable = false)
    val configId: Long,
    
    /** 账户ID */
    @Column(name = "account_id", nullable = false)
    val accountId: Long,
    
    /** 市场ID */
    @Column(name = "market_id", nullable = false, length = 100)
    val marketId: String,
    
    /** 市场名称 */
    @Column(name = "market_title", length = 500)
    val marketTitle: String? = null,
    
    /** 结果名称（如 YES, NO, Pakistan 等） */
    @Column(name = "outcome", nullable = false, length = 100)
    val outcome: String,
    
    /** 结果索引 */
    @Column(name = "outcome_index")
    val outcomeIndex: Int? = null,
    
    /** 触发类型：TAKE_PROFIT / STOP_LOSS / FORCED_LIQUIDITY / FORCED_TIME */
    @Column(name = "trigger_type", nullable = false, length = 30)
    val triggerType: String,
    
    /** 触发时的盈亏比例 */
    @Column(name = "trigger_pnl_percent", nullable = false, precision = 10, scale = 4)
    val triggerPnlPercent: BigDecimal,
    
    /** 触发时计算的动态阈值 */
    @Column(name = "dynamic_threshold", precision = 10, scale = 4)
    val dynamicThreshold: BigDecimal? = null,
    
    /** 触发时的流动性系数 */
    @Column(name = "liquidity_coef", precision = 10, scale = 4)
    val liquidityCoef: BigDecimal? = null,
    
    /** 触发时的时间系数 */
    @Column(name = "time_coef", precision = 10, scale = 4)
    val timeCoef: BigDecimal? = null,
    
    /** 触发时的紧急程度 (0-1) */
    @Column(name = "urgency_level", precision = 10, scale = 4)
    val urgencyLevel: BigDecimal? = null,
    
    /** 订单簿买盘深度（美元） */
    @Column(name = "orderbook_bid_depth", precision = 20, scale = 8)
    val orderbookBidDepth: BigDecimal? = null,
    
    /** 持仓价值（美元） */
    @Column(name = "position_value", precision = 20, scale = 8)
    val positionValue: BigDecimal? = null,
    
    /** 市场剩余时间（分钟） */
    @Column(name = "remaining_minutes")
    val remainingMinutes: Int? = null,
    
    /** 卖出数量 */
    @Column(name = "sold_quantity", nullable = false, precision = 20, scale = 8)
    val soldQuantity: BigDecimal,
    
    /** 卖出价格 */
    @Column(name = "sold_price", nullable = false, precision = 10, scale = 4)
    val soldPrice: BigDecimal,
    
    /** 卖出金额（美元） */
    @Column(name = "sold_amount", nullable = false, precision = 20, scale = 8)
    val soldAmount: BigDecimal,
    
    /** 订单类型：MARKET / LIMIT */
    @Column(name = "order_type", nullable = false, length = 20)
    val orderType: String,
    
    /** 订单ID */
    @Column(name = "order_id", length = 100)
    val orderId: String? = null,
    
    /** 执行状态：SUCCESS / FAILED / PENDING */
    @Column(name = "status", nullable = false, length = 20)
    val status: String,
    
    /** 重试次数 */
    @Column(name = "retry_count", nullable = false)
    val retryCount: Int = 0,
    
    /** 错误信息 */
    @Column(name = "error_message", length = 1000)
    val errorMessage: String? = null,
    
    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    val createdAt: Long = System.currentTimeMillis()
)
