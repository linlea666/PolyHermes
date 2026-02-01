package com.wrbug.polymarketbot.entity

import jakarta.persistence.*
import java.math.BigDecimal

/**
 * 智能止盈止损配置实体
 * 
 * 账户级别的止盈止损配置，支持：
 * - 动态阈值计算（基于流动性和时间）
 * - 限价单执行策略
 * - 价格阶梯重试
 * 
 * 设计原则：
 * - 独立于现有跟单系统，插件式架构
 * - 复用现有卖出逻辑，避免重复实现
 * - 账户级别配置，与多账户系统保持一致
 */
@Entity
@Table(
    name = "smart_take_profit_configs",
    indexes = [
        Index(name = "idx_stp_account_id", columnList = "account_id"),
        Index(name = "idx_stp_enabled", columnList = "enabled")
    ]
)
data class SmartTakeProfitConfig(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    // ===== 基础配置 =====
    
    /** 关联账户ID（与 Account.id 对应） */
    @Column(name = "account_id", nullable = false, unique = true)
    val accountId: Long,
    
    /** 总开关：是否启用智能止盈止损 */
    @Column(name = "enabled", nullable = false)
    val enabled: Boolean = false,
    
    // ===== 止盈配置 =====
    
    /** 止盈开关 */
    @Column(name = "take_profit_enabled", nullable = false)
    val takeProfitEnabled: Boolean = true,
    
    /** 止盈基础阈值（百分比，如 10 表示 10%） */
    @Column(name = "take_profit_base_threshold", nullable = false, precision = 10, scale = 4)
    val takeProfitBaseThreshold: BigDecimal = BigDecimal("10"),
    
    /** 每次止盈卖出比例（百分比，如 30 表示卖出持仓的 30%） */
    @Column(name = "take_profit_ratio", nullable = false, precision = 10, scale = 4)
    val takeProfitRatio: BigDecimal = BigDecimal("30"),
    
    /** 保留底仓比例（百分比，如 20 表示至少保留 20% 仓位等待领航员信号） */
    @Column(name = "take_profit_keep_ratio", nullable = false, precision = 10, scale = 4)
    val takeProfitKeepRatio: BigDecimal = BigDecimal("20"),
    
    // ===== 止损配置 =====
    
    /** 止损开关（默认关闭） */
    @Column(name = "stop_loss_enabled", nullable = false)
    val stopLossEnabled: Boolean = false,
    
    /** 止损阈值（百分比，如 -20 表示亏损 20% 触发） */
    @Column(name = "stop_loss_threshold", nullable = false, precision = 10, scale = 4)
    val stopLossThreshold: BigDecimal = BigDecimal("-20"),
    
    /** 止损卖出比例（百分比，如 100 表示全部卖出） */
    @Column(name = "stop_loss_ratio", nullable = false, precision = 10, scale = 4)
    val stopLossRatio: BigDecimal = BigDecimal("100"),
    
    // ===== 流动性动态调整 =====
    
    /** 流动性调整开关 */
    @Column(name = "liquidity_adjust_enabled", nullable = false)
    val liquidityAdjustEnabled: Boolean = true,
    
    /** 
     * 流动性危险阈值
     * 当 订单簿买盘深度 < 持仓价值 × 此值 时，立即触发止盈
     */
    @Column(name = "liquidity_danger_ratio", nullable = false, precision = 10, scale = 4)
    val liquidityDangerRatio: BigDecimal = BigDecimal("0.3"),
    
    /** 
     * 流动性警告阈值
     * 当 订单簿买盘深度 < 持仓价值 × 此值 时，降低止盈阈值
     */
    @Column(name = "liquidity_warning_ratio", nullable = false, precision = 10, scale = 4)
    val liquidityWarningRatio: BigDecimal = BigDecimal("1.0"),
    
    /** 
     * 流动性安全阈值
     * 当 订单簿买盘深度 >= 持仓价值 × 此值 时，可以提高止盈阈值
     */
    @Column(name = "liquidity_safe_ratio", nullable = false, precision = 10, scale = 4)
    val liquiditySafeRatio: BigDecimal = BigDecimal("3.0"),
    
    // ===== 时间衰减（短期市场）=====
    
    /** 时间衰减开关 */
    @Column(name = "time_decay_enabled", nullable = false)
    val timeDecayEnabled: Boolean = true,
    
    /** 开始衰减的剩余时间（分钟） */
    @Column(name = "time_decay_start_minutes", nullable = false)
    val timeDecayStartMinutes: Int = 30,
    
    /** 紧急阈值（分钟）：低于此时间，止盈阈值大幅降低 */
    @Column(name = "time_decay_urgent_minutes", nullable = false)
    val timeDecayUrgentMinutes: Int = 5,
    
    /** 危险阈值（分钟）：低于此时间，接受亏损也要出场 */
    @Column(name = "time_decay_critical_minutes", nullable = false)
    val timeDecayCriticalMinutes: Int = 2,
    
    // ===== 卖出执行策略 =====
    
    /** 是否使用限价单（否则使用市价单） */
    @Column(name = "use_limit_order", nullable = false)
    val useLimitOrder: Boolean = true,
    
    /** 限价单溢价（百分比，如 1 表示比当前价高 1%） */
    @Column(name = "limit_order_premium", nullable = false, precision = 10, scale = 4)
    val limitOrderPremium: BigDecimal = BigDecimal("1"),
    
    /** 限价单等待时间（秒） */
    @Column(name = "limit_order_wait_seconds", nullable = false)
    val limitOrderWaitSeconds: Int = 60,
    
    /** 是否启用价格阶梯重试 */
    @Column(name = "price_retry_enabled", nullable = false)
    val priceRetryEnabled: Boolean = true,
    
    /** 每次重试降价幅度（百分比，如 1 表示降价 1%） */
    @Column(name = "price_retry_step", nullable = false, precision = 10, scale = 4)
    val priceRetryStep: BigDecimal = BigDecimal("1"),
    
    /** 最大价格滑点（百分比，如 5 表示最多接受 5% 滑点） */
    @Column(name = "max_price_slippage", nullable = false, precision = 10, scale = 4)
    val maxPriceSlippage: BigDecimal = BigDecimal("5"),
    
    /** 最大重试次数 */
    @Column(name = "max_retry_count", nullable = false)
    val maxRetryCount: Int = 3,
    
    // ===== 时间戳 =====
    
    @Column(name = "created_at", nullable = false)
    val createdAt: Long = System.currentTimeMillis(),
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = System.currentTimeMillis()
)
