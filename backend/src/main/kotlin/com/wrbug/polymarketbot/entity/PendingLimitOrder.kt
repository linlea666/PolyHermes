package com.wrbug.polymarketbot.entity

import jakarta.persistence.*
import java.math.BigDecimal

/**
 * 待处理限价单实体
 * 
 * 用于跟踪智能止盈止损系统创建的限价单状态
 * 支持超时检测、降价重试、回退市价单
 */
@Entity
@Table(
    name = "pending_limit_orders",
    indexes = [
        Index(name = "idx_plo_config_id", columnList = "config_id"),
        Index(name = "idx_plo_status", columnList = "status"),
        Index(name = "idx_plo_expire_at", columnList = "expire_at")
    ]
)
data class PendingLimitOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    /** 关联的止盈止损配置ID */
    @Column(name = "config_id", nullable = false)
    val configId: Long,
    
    /** 账户ID */
    @Column(name = "account_id", nullable = false)
    val accountId: Long,
    
    /** 市场ID */
    @Column(name = "market_id", nullable = false, length = 100)
    val marketId: String,
    
    /** Token ID */
    @Column(name = "token_id", nullable = false, length = 100)
    val tokenId: String,
    
    /** 结果名称 */
    @Column(name = "outcome", nullable = false, length = 100)
    val outcome: String,
    
    /** 结果索引 */
    @Column(name = "outcome_index")
    val outcomeIndex: Int? = null,
    
    /** Polymarket 订单ID */
    @Column(name = "order_id", nullable = false, length = 100)
    val orderId: String,
    
    /** 订单数量 */
    @Column(name = "quantity", nullable = false, precision = 20, scale = 8)
    val quantity: BigDecimal,
    
    /** 订单价格 */
    @Column(name = "price", nullable = false, precision = 10, scale = 4)
    val price: BigDecimal,
    
    /** 订单状态：PENDING / FILLED / PARTIALLY_FILLED / CANCELLED / FAILED */
    @Column(name = "status", nullable = false, length = 30)
    var status: String = "PENDING",
    
    /** 已成交数量 */
    @Column(name = "filled_quantity", nullable = false, precision = 20, scale = 8)
    var filledQuantity: BigDecimal = BigDecimal.ZERO,
    
    /** 重试次数 */
    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,
    
    /** 最大重试次数 */
    @Column(name = "max_retry_count", nullable = false)
    val maxRetryCount: Int = 3,
    
    /** 初始价格（用于计算滑点） */
    @Column(name = "initial_price", nullable = false, precision = 10, scale = 4)
    val initialPrice: BigDecimal,
    
    /** 最大价格滑点 */
    @Column(name = "max_slippage", nullable = false, precision = 10, scale = 4)
    val maxSlippage: BigDecimal,
    
    /** 触发类型：TAKE_PROFIT / STOP_LOSS */
    @Column(name = "trigger_type", nullable = false, length = 30)
    val triggerType: String,
    
    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    val createdAt: Long = System.currentTimeMillis(),
    
    /** 过期时间（超过此时间未成交则降价重试或取消） */
    @Column(name = "expire_at", nullable = false)
    val expireAt: Long,
    
    /** 最后更新时间 */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = System.currentTimeMillis()
)
