package com.wrbug.polymarketbot.dto

import java.math.BigDecimal

/**
 * 智能止盈止损配置请求（创建/更新）
 */
data class SmartTakeProfitConfigRequest(
    val accountId: Long = 0,
    
    // 基础配置
    val enabled: Boolean = false,
    
    // 止盈配置
    val takeProfitEnabled: Boolean = true,
    val takeProfitBaseThreshold: BigDecimal = BigDecimal("10"),
    val takeProfitRatio: BigDecimal = BigDecimal("30"),
    val takeProfitKeepRatio: BigDecimal = BigDecimal("20"),
    
    // 止损配置
    val stopLossEnabled: Boolean = false,
    val stopLossThreshold: BigDecimal = BigDecimal("-20"),
    val stopLossRatio: BigDecimal = BigDecimal("100"),
    
    // 流动性动态调整
    val liquidityAdjustEnabled: Boolean = true,
    val liquidityDangerRatio: BigDecimal = BigDecimal("0.3"),
    val liquidityWarningRatio: BigDecimal = BigDecimal("1.0"),
    val liquiditySafeRatio: BigDecimal = BigDecimal("3.0"),
    
    // 时间衰减
    val timeDecayEnabled: Boolean = true,
    val timeDecayStartMinutes: Int = 30,
    val timeDecayUrgentMinutes: Int = 5,
    val timeDecayCriticalMinutes: Int = 2,
    
    // 卖出执行策略
    val useLimitOrder: Boolean = true,
    val limitOrderPremium: BigDecimal = BigDecimal("1"),
    val limitOrderWaitSeconds: Int = 60,
    val priceRetryEnabled: Boolean = true,
    val priceRetryStep: BigDecimal = BigDecimal("1"),
    val maxPriceSlippage: BigDecimal = BigDecimal("5"),
    val maxRetryCount: Int = 3
)

/**
 * 智能止盈止损配置响应
 */
data class SmartTakeProfitConfigResponse(
    val id: Long = 0,
    val accountId: Long = 0,
    val accountName: String? = null,
    
    // 基础配置
    val enabled: Boolean = false,
    
    // 止盈配置
    val takeProfitEnabled: Boolean = true,
    val takeProfitBaseThreshold: String = "10",
    val takeProfitRatio: String = "30",
    val takeProfitKeepRatio: String = "20",
    
    // 止损配置
    val stopLossEnabled: Boolean = false,
    val stopLossThreshold: String = "-20",
    val stopLossRatio: String = "100",
    
    // 流动性动态调整
    val liquidityAdjustEnabled: Boolean = true,
    val liquidityDangerRatio: String = "0.3",
    val liquidityWarningRatio: String = "1.0",
    val liquiditySafeRatio: String = "3.0",
    
    // 时间衰减
    val timeDecayEnabled: Boolean = true,
    val timeDecayStartMinutes: Int = 30,
    val timeDecayUrgentMinutes: Int = 5,
    val timeDecayCriticalMinutes: Int = 2,
    
    // 卖出执行策略
    val useLimitOrder: Boolean = true,
    val limitOrderPremium: String = "1",
    val limitOrderWaitSeconds: Int = 60,
    val priceRetryEnabled: Boolean = true,
    val priceRetryStep: String = "1",
    val maxPriceSlippage: String = "5",
    val maxRetryCount: Int = 3,
    
    // 时间戳
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

/**
 * 执行日志响应
 */
data class SmartTakeProfitLogResponse(
    val id: Long = 0,
    val configId: Long = 0,
    val accountId: Long = 0,
    val marketId: String = "",
    val marketTitle: String? = null,
    val outcome: String = "",
    val outcomeIndex: Int? = null,
    val triggerType: String = "",
    val triggerPnlPercent: String = "0",
    val dynamicThreshold: String? = null,
    val liquidityCoef: String? = null,
    val timeCoef: String? = null,
    val urgencyLevel: String? = null,
    val orderbookBidDepth: String? = null,
    val positionValue: String? = null,
    val remainingMinutes: Int? = null,
    val soldQuantity: String = "0",
    val soldPrice: String = "0",
    val soldAmount: String = "0",
    val orderType: String = "",
    val orderId: String? = null,
    val status: String = "",
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = 0
)

/**
 * 日志查询请求
 */
data class SmartTakeProfitLogQueryRequest(
    val accountId: Long? = null,
    val marketId: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val page: Int = 0,
    val size: Int = 20
)

/**
 * 仓位风险评估响应
 * 用于前端显示仓位的流动性和时间风险
 */
data class PositionRiskAssessment(
    val accountId: Long = 0,
    val marketId: String = "",
    val outcome: String = "",
    val outcomeIndex: Int? = null,
    
    // 流动性评估
    val positionValue: String = "0",
    val orderbookBidDepth: String = "0",
    val liquidityRatio: String = "0",
    val liquidityLevel: String = "UNKNOWN",  // SAFE / WARNING / DANGER / CRITICAL
    
    // 时间评估（仅短期市场）
    val marketEndDate: Long? = null,
    val remainingMinutes: Int? = null,
    val timeLevel: String = "UNKNOWN",  // SAFE / WARNING / URGENT / CRITICAL
    
    // 综合评估
    val overallRisk: String = "UNKNOWN",  // LOW / MEDIUM / HIGH / CRITICAL
    
    // 当前盈亏
    val currentPnlPercent: String = "0",
    
    // 动态阈值（如果启用了智能止盈止损）
    val dynamicThreshold: String? = null,
    val wouldTrigger: Boolean = false
)

/**
 * 仓位风险评估请求
 */
data class PositionRiskAssessmentRequest(
    val accountId: Long = 0
)

/**
 * 快速开启/关闭请求
 */
data class SmartTakeProfitToggleRequest(
    val accountId: Long = 0,
    val enabled: Boolean = false
)
