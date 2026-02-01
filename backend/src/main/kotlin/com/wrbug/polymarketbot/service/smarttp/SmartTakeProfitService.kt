package com.wrbug.polymarketbot.service.smarttp

import com.wrbug.polymarketbot.dto.*
import com.wrbug.polymarketbot.entity.*
import com.wrbug.polymarketbot.repository.*
import com.wrbug.polymarketbot.service.accounts.AccountService
import com.wrbug.polymarketbot.service.common.BlockchainService
import com.wrbug.polymarketbot.service.common.PolymarketClobService
import com.wrbug.polymarketbot.service.common.MarketService
import com.wrbug.polymarketbot.util.toSafeBigDecimal
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 智能止盈止损服务
 * 
 * 核心功能：
 * 1. 动态阈值计算（基于流动性和时间）
 * 2. 自动止盈止损执行
 * 3. 限价单执行策略
 * 
 * 设计原则：
 * - 独立于现有跟单系统，插件式架构
 * - 复用现有卖出逻辑，避免重复实现
 * - 账户级别配置，与多账户系统保持一致
 */
@Service
class SmartTakeProfitService(
    private val configRepository: SmartTakeProfitConfigRepository,
    private val logRepository: SmartTakeProfitLogRepository,
    private val pendingOrderRepository: PendingLimitOrderRepository,
    private val accountRepository: AccountRepository,
    private val accountService: AccountService,
    private val clobService: PolymarketClobService,
    private val blockchainService: BlockchainService,
    private val marketService: MarketService
) {
    private val logger = LoggerFactory.getLogger(SmartTakeProfitService::class.java)
    
    /** 检查间隔（毫秒），可通过 smart-take-profit.check-interval-ms 配置 */
    @Value("\${smart-take-profit.check-interval-ms:30000}")
    private var checkIntervalMs: Long = 30000L
    
    /** 冷却时间（毫秒），可通过 smart-take-profit.cooldown-ms 配置 */
    @Value("\${smart-take-profit.cooldown-ms:300000}")
    private var cooldownMs: Long = 300000L
    
    companion object {
        // 流动性系数常量
        private val LIQUIDITY_COEF_SAFE = BigDecimal("1.5")      // 流动性充足：可等待
        private val LIQUIDITY_COEF_NORMAL = BigDecimal("1.0")    // 流动性正常
        private val LIQUIDITY_COEF_WARNING = BigDecimal("0.5")   // 流动性警告：降低阈值
        private val LIQUIDITY_COEF_DANGER = BigDecimal.ZERO      // 流动性危险：立即出场
        
        // 时间系数常量（针对短期市场）
        private val TIME_COEF_NORMAL = BigDecimal("1.0")
        private val TIME_COEF_WARNING = BigDecimal("0.7")
        private val TIME_COEF_URGENT = BigDecimal("0.3")
        private val TIME_COEF_CRITICAL = BigDecimal.ZERO
        private val TIME_COEF_EMERGENCY = BigDecimal("-0.5")  // 接受亏损
        
        // 紧急程度阈值
        private val URGENCY_THRESHOLD_MARKET_ORDER = BigDecimal("0.7")
    }
    
    // ==================== 配置管理 ====================
    
    /**
     * 获取账户的止盈止损配置
     */
    fun getConfig(accountId: Long): SmartTakeProfitConfigResponse? {
        val config = configRepository.findByAccountId(accountId) ?: return null
        val account = accountRepository.findById(accountId).orElse(null)
        return mapToResponse(config, account?.accountName)
    }
    
    /**
     * 创建或更新配置
     */
    @Transactional
    fun saveConfig(request: SmartTakeProfitConfigRequest): SmartTakeProfitConfigResponse {
        val existingConfig = configRepository.findByAccountId(request.accountId)
        
        val config = if (existingConfig != null) {
            // 更新现有配置（直接修改 managed 实体，利用 JPA 脏检查自动更新）
            existingConfig.apply {
                enabled = request.enabled
                takeProfitEnabled = request.takeProfitEnabled
                takeProfitBaseThreshold = request.takeProfitBaseThreshold
                takeProfitRatio = request.takeProfitRatio
                takeProfitKeepRatio = request.takeProfitKeepRatio
                stopLossEnabled = request.stopLossEnabled
                stopLossThreshold = request.stopLossThreshold
                stopLossRatio = request.stopLossRatio
                liquidityAdjustEnabled = request.liquidityAdjustEnabled
                liquidityDangerRatio = request.liquidityDangerRatio
                liquidityWarningRatio = request.liquidityWarningRatio
                liquiditySafeRatio = request.liquiditySafeRatio
                timeDecayEnabled = request.timeDecayEnabled
                timeDecayStartMinutes = request.timeDecayStartMinutes
                timeDecayUrgentMinutes = request.timeDecayUrgentMinutes
                timeDecayCriticalMinutes = request.timeDecayCriticalMinutes
                useLimitOrder = request.useLimitOrder
                limitOrderPremium = request.limitOrderPremium
                limitOrderWaitSeconds = request.limitOrderWaitSeconds
                priceRetryEnabled = request.priceRetryEnabled
                priceRetryStep = request.priceRetryStep
                maxPriceSlippage = request.maxPriceSlippage
                maxRetryCount = request.maxRetryCount
                updatedAt = System.currentTimeMillis()
            }
        } else {
            // 创建新配置
            SmartTakeProfitConfig(
                accountId = request.accountId,
                enabled = request.enabled,
                takeProfitEnabled = request.takeProfitEnabled,
                takeProfitBaseThreshold = request.takeProfitBaseThreshold,
                takeProfitRatio = request.takeProfitRatio,
                takeProfitKeepRatio = request.takeProfitKeepRatio,
                stopLossEnabled = request.stopLossEnabled,
                stopLossThreshold = request.stopLossThreshold,
                stopLossRatio = request.stopLossRatio,
                liquidityAdjustEnabled = request.liquidityAdjustEnabled,
                liquidityDangerRatio = request.liquidityDangerRatio,
                liquidityWarningRatio = request.liquidityWarningRatio,
                liquiditySafeRatio = request.liquiditySafeRatio,
                timeDecayEnabled = request.timeDecayEnabled,
                timeDecayStartMinutes = request.timeDecayStartMinutes,
                timeDecayUrgentMinutes = request.timeDecayUrgentMinutes,
                timeDecayCriticalMinutes = request.timeDecayCriticalMinutes,
                useLimitOrder = request.useLimitOrder,
                limitOrderPremium = request.limitOrderPremium,
                limitOrderWaitSeconds = request.limitOrderWaitSeconds,
                priceRetryEnabled = request.priceRetryEnabled,
                priceRetryStep = request.priceRetryStep,
                maxPriceSlippage = request.maxPriceSlippage,
                maxRetryCount = request.maxRetryCount
            )
        }
        
        val savedConfig = configRepository.save(config)
        val account = accountRepository.findById(request.accountId).orElse(null)
        
        logger.info("保存智能止盈止损配置: accountId=${request.accountId}, enabled=${request.enabled}")
        
        return mapToResponse(savedConfig, account?.accountName)
    }
    
    /**
     * 快速开启/关闭
     */
    @Transactional
    fun toggleEnabled(request: SmartTakeProfitToggleRequest): Boolean {
        val config = configRepository.findByAccountId(request.accountId)
        
        if (config != null) {
            // 直接修改 managed 实体，利用 JPA 脏检查自动更新
            config.enabled = request.enabled
            config.updatedAt = System.currentTimeMillis()
            configRepository.save(config)
        } else if (request.enabled) {
            // 如果配置不存在且要启用，创建默认配置
            val newConfig = SmartTakeProfitConfig(
                accountId = request.accountId,
                enabled = true
            )
            configRepository.save(newConfig)
        }
        
        logger.info("切换智能止盈止损状态: accountId=${request.accountId}, enabled=${request.enabled}")
        
        return request.enabled
    }
    
    /**
     * 删除配置
     */
    @Transactional
    fun deleteConfig(accountId: Long) {
        pendingOrderRepository.deleteByConfigId(
            configRepository.findByAccountId(accountId)?.id ?: return
        )
        logRepository.deleteByConfigId(
            configRepository.findByAccountId(accountId)?.id ?: return
        )
        configRepository.deleteByAccountId(accountId)
        
        logger.info("删除智能止盈止损配置: accountId=$accountId")
    }
    
    // ==================== 日志查询 ====================
    
    /**
     * 查询执行日志
     */
    fun getLogs(request: SmartTakeProfitLogQueryRequest): Page<SmartTakeProfitLogResponse> {
        val pageable = PageRequest.of(request.page, request.size)
        
        val logs = when {
            request.accountId != null && request.marketId != null -> {
                logRepository.findByAccountIdAndMarketIdOrderByCreatedAtDesc(
                    request.accountId, request.marketId, pageable
                )
            }
            request.accountId != null && request.startTime != null && request.endTime != null -> {
                logRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    request.accountId, request.startTime, request.endTime, pageable
                )
            }
            request.accountId != null -> {
                logRepository.findByAccountIdOrderByCreatedAtDesc(request.accountId, pageable)
            }
            else -> {
                logRepository.findAll(pageable)
            }
        }
        
        return logs.map { mapLogToResponse(it) }
    }
    
    // ==================== 定时检查 ====================
    
    /**
     * 定时检查所有启用的配置，执行止盈止损
     * 检查间隔可通过 smart-take-profit.check-interval-ms 配置，默认30秒
     */
    @Scheduled(fixedDelayString = "\${smart-take-profit.check-interval-ms:30000}")
    fun checkAndExecute() {
        try {
            // 1. 查询所有启用的配置
            val enabledConfigs = configRepository.findByEnabledTrue()
            if (enabledConfigs.isEmpty()) {
                return
            }
            
            logger.debug("开始检查智能止盈止损: 共 ${enabledConfigs.size} 个启用的配置")
            
            // 2. 获取所有仓位
            val allPositions = runBlocking {
                accountService.getAllPositions()
            }.getOrNull()?.currentPositions ?: emptyList()
            
            if (allPositions.isEmpty()) {
                return
            }
            
            // 3. 对每个配置进行检查
            for (config in enabledConfigs) {
                try {
                    checkConfigPositions(config, allPositions)
                } catch (e: Exception) {
                    logger.error("检查配置失败: configId=${config.id}, accountId=${config.accountId}, error=${e.message}", e)
                }
            }
            
            // 4. 检查待处理的限价单
            checkPendingLimitOrders()
            
        } catch (e: Exception) {
            logger.error("智能止盈止损检查异常: ${e.message}", e)
        }
    }
    
    /**
     * 检查单个配置的所有仓位
     */
    private fun checkConfigPositions(
        config: SmartTakeProfitConfig,
        allPositions: List<AccountPositionDto>
    ) {
        // 筛选该账户的仓位
        val accountPositions = allPositions.filter { it.accountId == config.accountId }
        if (accountPositions.isEmpty()) {
            return
        }
        
        for (position in accountPositions) {
            try {
                checkPosition(config, position)
            } catch (e: Exception) {
                logger.error(
                    "检查仓位失败: configId=${config.id}, marketId=${position.marketId}, " +
                    "outcome=${position.side}, error=${e.message}", e
                )
            }
        }
    }
    
    /**
     * 检查单个仓位是否需要止盈止损
     */
    private fun checkPosition(config: SmartTakeProfitConfig, position: AccountPositionDto) {
        // 1. 检查是否在冷却期内（避免重复执行）
        val recentLogs = logRepository.findByAccountIdAndMarketIdAndOutcomeAndCreatedAtGreaterThan(
            config.accountId,
            position.marketId,
            position.side,
            System.currentTimeMillis() - cooldownMs
        )
        if (recentLogs.isNotEmpty()) {
            logger.debug(
                "仓位在冷却期内，跳过: accountId=${config.accountId}, " +
                "marketId=${position.marketId}, outcome=${position.side}"
            )
            return
        }
        
        // 2. 检查是否有待处理的限价单
        val hasPendingOrder = pendingOrderRepository.existsByAccountIdAndMarketIdAndOutcomeAndStatus(
            config.accountId,
            position.marketId,
            position.side,
            "PENDING"
        )
        if (hasPendingOrder) {
            logger.debug(
                "仓位有待处理限价单，跳过: accountId=${config.accountId}, " +
                "marketId=${position.marketId}, outcome=${position.side}"
            )
            return
        }
        
        // 3. 获取评估数据
        val assessment = assessPosition(config, position)
        
        // 4. 判断是否触发止盈止损
        val triggerResult = checkTrigger(config, position, assessment)
        
        if (triggerResult.triggered) {
            logger.info(
                "触发${triggerResult.triggerType}: accountId=${config.accountId}, " +
                "marketId=${position.marketId}, outcome=${position.side}, " +
                "pnl=${position.percentPnl}%, threshold=${triggerResult.dynamicThreshold}%"
            )
            
            // 5. 执行卖出
            executeSell(config, position, assessment, triggerResult)
        }
    }
    
    /**
     * 评估仓位的风险状况
     */
    private fun assessPosition(
        config: SmartTakeProfitConfig,
        position: AccountPositionDto
    ): PositionAssessmentData {
        val positionValue = position.currentValue.toSafeBigDecimal()
        
        // 获取订单簿深度
        var orderbookBidDepth = BigDecimal.ZERO
        var liquidityCoef = LIQUIDITY_COEF_NORMAL
        
        if (config.liquidityAdjustEnabled && position.outcomeIndex != null) {
            try {
                val tokenIdResult = runBlocking {
                    blockchainService.getTokenId(position.marketId, position.outcomeIndex)
                }
                val tokenId = tokenIdResult.getOrNull()
                
                if (tokenId != null) {
                    val orderbookResult = runBlocking {
                        clobService.getOrderbookByTokenId(tokenId)
                    }
                    
                    if (orderbookResult.isSuccess) {
                        val orderbook = orderbookResult.getOrNull()
                        // 计算买盘深度（所有买单的价值总和）
                        orderbookBidDepth = orderbook?.bids?.sumOf {
                            it.price.toSafeBigDecimal().multiply(it.size.toSafeBigDecimal())
                        } ?: BigDecimal.ZERO
                    }
                }
            } catch (e: Exception) {
                logger.warn("获取订单簿深度失败: marketId=${position.marketId}, error=${e.message}")
            }
            
            // 计算流动性系数
            liquidityCoef = calculateLiquidityCoef(config, positionValue, orderbookBidDepth)
        }
        
        // 获取市场结束时间和时间系数
        var remainingMinutes: Int? = null
        var timeCoef = TIME_COEF_NORMAL
        
        if (config.timeDecayEnabled) {
            val market = marketService.getMarket(position.marketId)
            if (market?.endDate != null) {
                val remainingMs = market.endDate - System.currentTimeMillis()
                remainingMinutes = (remainingMs / 60000).toInt()
                
                if (remainingMinutes >= 0) {
                    timeCoef = calculateTimeCoef(config, remainingMinutes)
                }
            }
        }
        
        // 计算紧急程度
        val urgencyLevel = BigDecimal.ONE.subtract(
            liquidityCoef.min(timeCoef).coerceAtLeast(BigDecimal.ZERO)
        )
        
        return PositionAssessmentData(
            positionValue = positionValue,
            orderbookBidDepth = orderbookBidDepth,
            liquidityCoef = liquidityCoef,
            remainingMinutes = remainingMinutes,
            timeCoef = timeCoef,
            urgencyLevel = urgencyLevel
        )
    }
    
    /**
     * 计算流动性系数
     */
    private fun calculateLiquidityCoef(
        config: SmartTakeProfitConfig,
        positionValue: BigDecimal,
        orderbookBidDepth: BigDecimal
    ): BigDecimal {
        if (positionValue <= BigDecimal.ZERO) {
            return LIQUIDITY_COEF_NORMAL
        }
        
        val liquidityRatio = orderbookBidDepth.divide(positionValue, 4, RoundingMode.HALF_UP)
        
        return when {
            liquidityRatio < config.liquidityDangerRatio -> LIQUIDITY_COEF_DANGER
            liquidityRatio < config.liquidityWarningRatio -> LIQUIDITY_COEF_WARNING
            liquidityRatio >= config.liquiditySafeRatio -> LIQUIDITY_COEF_SAFE
            else -> LIQUIDITY_COEF_NORMAL
        }
    }
    
    /**
     * 计算时间系数（针对短期市场）
     */
    private fun calculateTimeCoef(config: SmartTakeProfitConfig, remainingMinutes: Int): BigDecimal {
        return when {
            remainingMinutes < config.timeDecayCriticalMinutes -> TIME_COEF_EMERGENCY
            remainingMinutes < config.timeDecayUrgentMinutes -> TIME_COEF_CRITICAL
            remainingMinutes < config.timeDecayStartMinutes / 2 -> TIME_COEF_URGENT
            remainingMinutes < config.timeDecayStartMinutes -> TIME_COEF_WARNING
            else -> TIME_COEF_NORMAL
        }
    }
    
    /**
     * 检查是否触发止盈止损
     */
    private fun checkTrigger(
        config: SmartTakeProfitConfig,
        position: AccountPositionDto,
        assessment: PositionAssessmentData
    ): TriggerResult {
        val currentPnlPercent = position.percentPnl.toSafeBigDecimal()
        
        // 计算动态止盈阈值
        val dynamicThreshold = config.takeProfitBaseThreshold.multiply(
            assessment.liquidityCoef.min(assessment.timeCoef).coerceAtLeast(BigDecimal.ZERO)
        )
        
        // 1. 检查强制触发条件（流动性危险或时间紧迫）
        if (assessment.liquidityCoef <= BigDecimal.ZERO) {
            // 流动性危险，有盈利就触发
            if (currentPnlPercent > BigDecimal.ZERO) {
                return TriggerResult(
                    triggered = true,
                    triggerType = "FORCED_LIQUIDITY",
                    dynamicThreshold = BigDecimal.ZERO
                )
            }
        }
        
        if (assessment.timeCoef <= BigDecimal.ZERO) {
            // 时间紧迫，有盈利就触发（或接受小亏损）
            val emergencyThreshold = if (assessment.timeCoef < BigDecimal.ZERO) {
                // 极端紧迫：接受亏损
                assessment.timeCoef.multiply(BigDecimal("10"))  // 如 -0.5 × 10 = -5%
            } else {
                BigDecimal.ZERO
            }
            
            if (currentPnlPercent >= emergencyThreshold) {
                return TriggerResult(
                    triggered = true,
                    triggerType = "FORCED_TIME",
                    dynamicThreshold = emergencyThreshold
                )
            }
        }
        
        // 2. 检查止损条件
        if (config.stopLossEnabled && currentPnlPercent <= config.stopLossThreshold) {
            return TriggerResult(
                triggered = true,
                triggerType = "STOP_LOSS",
                dynamicThreshold = config.stopLossThreshold
            )
        }
        
        // 3. 检查止盈条件
        if (config.takeProfitEnabled && currentPnlPercent >= dynamicThreshold) {
            return TriggerResult(
                triggered = true,
                triggerType = "TAKE_PROFIT",
                dynamicThreshold = dynamicThreshold
            )
        }
        
        return TriggerResult(triggered = false, triggerType = "", dynamicThreshold = dynamicThreshold)
    }
    
    /**
     * 执行卖出
     */
    private fun executeSell(
        config: SmartTakeProfitConfig,
        position: AccountPositionDto,
        assessment: PositionAssessmentData,
        triggerResult: TriggerResult
    ) {
        // 计算卖出比例
        val sellRatio = when (triggerResult.triggerType) {
            "STOP_LOSS" -> config.stopLossRatio
            "FORCED_LIQUIDITY", "FORCED_TIME" -> BigDecimal("100")  // 强制全部卖出
            else -> {
                // 止盈：考虑保留底仓
                val maxSellRatio = BigDecimal("100").subtract(config.takeProfitKeepRatio)
                config.takeProfitRatio.min(maxSellRatio)
            }
        }
        
        // 计算卖出数量
        val totalQuantity = position.originalQuantity?.toSafeBigDecimal() 
            ?: position.quantity.toSafeBigDecimal()
        val sellQuantity = totalQuantity.multiply(sellRatio)
            .divide(BigDecimal("100"), 8, RoundingMode.DOWN)
        
        if (sellQuantity <= BigDecimal.ZERO) {
            logger.warn(
                "计算的卖出数量为0，跳过: accountId=${config.accountId}, " +
                "marketId=${position.marketId}, totalQuantity=$totalQuantity, sellRatio=$sellRatio"
            )
            return
        }
        
        // 决定使用市价单还是限价单
        val useMarketOrder = !config.useLimitOrder || 
            assessment.urgencyLevel >= URGENCY_THRESHOLD_MARKET_ORDER
        
        if (useMarketOrder) {
            executeMarketOrder(config, position, assessment, triggerResult, sellQuantity)
        } else {
            executeLimitOrder(config, position, assessment, triggerResult, sellQuantity)
        }
    }
    
    /**
     * 执行市价单卖出
     */
    private fun executeMarketOrder(
        config: SmartTakeProfitConfig,
        position: AccountPositionDto,
        assessment: PositionAssessmentData,
        triggerResult: TriggerResult,
        sellQuantity: BigDecimal
    ) {
        val sellRequest = PositionSellRequest(
            accountId = config.accountId,
            marketId = position.marketId,
            side = position.side,
            outcomeIndex = position.outcomeIndex,
            orderType = "MARKET",
            quantity = sellQuantity.toPlainString()
        )
        
        try {
            val result = runBlocking {
                accountService.sellPosition(sellRequest)
            }
            
            val log = SmartTakeProfitLog(
                configId = config.id!!,
                accountId = config.accountId,
                marketId = position.marketId,
                marketTitle = position.marketTitle,
                outcome = position.side,
                outcomeIndex = position.outcomeIndex,
                triggerType = triggerResult.triggerType,
                triggerPnlPercent = position.percentPnl.toSafeBigDecimal(),
                dynamicThreshold = triggerResult.dynamicThreshold,
                liquidityCoef = assessment.liquidityCoef,
                timeCoef = assessment.timeCoef,
                urgencyLevel = assessment.urgencyLevel,
                orderbookBidDepth = assessment.orderbookBidDepth,
                positionValue = assessment.positionValue,
                remainingMinutes = assessment.remainingMinutes,
                soldQuantity = sellQuantity,
                soldPrice = position.currentPrice.toSafeBigDecimal(),
                soldAmount = sellQuantity.multiply(position.currentPrice.toSafeBigDecimal()),
                orderType = "MARKET",
                orderId = result.getOrNull()?.orderId,
                status = if (result.isSuccess) "SUCCESS" else "FAILED",
                errorMessage = result.exceptionOrNull()?.message
            )
            
            logRepository.save(log)
            
            if (result.isSuccess) {
                logger.info(
                    "市价单卖出成功: accountId=${config.accountId}, marketId=${position.marketId}, " +
                    "outcome=${position.side}, quantity=$sellQuantity, orderId=${result.getOrNull()?.orderId}"
                )
            } else {
                logger.error(
                    "市价单卖出失败: accountId=${config.accountId}, marketId=${position.marketId}, " +
                    "outcome=${position.side}, error=${result.exceptionOrNull()?.message}"
                )
            }
            
        } catch (e: Exception) {
            logger.error(
                "市价单卖出异常: accountId=${config.accountId}, marketId=${position.marketId}, " +
                "outcome=${position.side}, error=${e.message}", e
            )
            
            // 记录失败日志
            val log = SmartTakeProfitLog(
                configId = config.id!!,
                accountId = config.accountId,
                marketId = position.marketId,
                marketTitle = position.marketTitle,
                outcome = position.side,
                outcomeIndex = position.outcomeIndex,
                triggerType = triggerResult.triggerType,
                triggerPnlPercent = position.percentPnl.toSafeBigDecimal(),
                dynamicThreshold = triggerResult.dynamicThreshold,
                liquidityCoef = assessment.liquidityCoef,
                timeCoef = assessment.timeCoef,
                urgencyLevel = assessment.urgencyLevel,
                orderbookBidDepth = assessment.orderbookBidDepth,
                positionValue = assessment.positionValue,
                remainingMinutes = assessment.remainingMinutes,
                soldQuantity = sellQuantity,
                soldPrice = position.currentPrice.toSafeBigDecimal(),
                soldAmount = sellQuantity.multiply(position.currentPrice.toSafeBigDecimal()),
                orderType = "MARKET",
                status = "FAILED",
                errorMessage = e.message
            )
            logRepository.save(log)
        }
    }
    
    /**
     * 执行限价单卖出
     */
    private fun executeLimitOrder(
        config: SmartTakeProfitConfig,
        position: AccountPositionDto,
        assessment: PositionAssessmentData,
        triggerResult: TriggerResult,
        sellQuantity: BigDecimal
    ) {
        // 计算限价单价格（当前价格 + 溢价）
        val currentPrice = position.currentPrice.toSafeBigDecimal()
        val premium = config.limitOrderPremium.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP)
        
        // 根据紧急程度调整溢价
        val adjustedPremium = premium.multiply(
            BigDecimal.ONE.subtract(assessment.urgencyLevel).coerceAtLeast(BigDecimal.ZERO)
        )
        
        var limitPrice = currentPrice.multiply(BigDecimal.ONE.add(adjustedPremium))
        
        // 确保价格在有效范围内 (0.01 - 0.99)
        limitPrice = limitPrice.max(BigDecimal("0.01")).min(BigDecimal("0.99"))
        
        // 获取 tokenId
        val tokenId = if (position.outcomeIndex != null) {
            runBlocking {
                blockchainService.getTokenId(position.marketId, position.outcomeIndex)
            }.getOrNull()
        } else {
            null
        }
        
        if (tokenId == null) {
            logger.error(
                "无法获取 tokenId，回退到市价单: accountId=${config.accountId}, " +
                "marketId=${position.marketId}, outcomeIndex=${position.outcomeIndex}"
            )
            executeMarketOrder(config, position, assessment, triggerResult, sellQuantity)
            return
        }
        
        val sellRequest = PositionSellRequest(
            accountId = config.accountId,
            marketId = position.marketId,
            side = position.side,
            outcomeIndex = position.outcomeIndex,
            orderType = "LIMIT",
            quantity = sellQuantity.toPlainString(),
            price = limitPrice.setScale(2, RoundingMode.DOWN).toPlainString()
        )
        
        try {
            val result = runBlocking {
                accountService.sellPosition(sellRequest)
            }
            
            if (result.isSuccess) {
                val orderId = result.getOrNull()?.orderId ?: ""
                
                // 保存待处理限价单记录
                val pendingOrder = PendingLimitOrder(
                    configId = config.id!!,
                    accountId = config.accountId,
                    marketId = position.marketId,
                    tokenId = tokenId,
                    outcome = position.side,
                    outcomeIndex = position.outcomeIndex,
                    orderId = orderId,
                    quantity = sellQuantity,
                    price = limitPrice,
                    initialPrice = currentPrice,
                    maxSlippage = config.maxPriceSlippage,
                    triggerType = triggerResult.triggerType,
                    expireAt = System.currentTimeMillis() + (config.limitOrderWaitSeconds * 1000L),
                    maxRetryCount = config.maxRetryCount
                )
                pendingOrderRepository.save(pendingOrder)
                
                logger.info(
                    "限价单创建成功: accountId=${config.accountId}, marketId=${position.marketId}, " +
                    "outcome=${position.side}, quantity=$sellQuantity, price=$limitPrice, orderId=$orderId"
                )
            } else {
                logger.error(
                    "限价单创建失败，回退到市价单: accountId=${config.accountId}, " +
                    "marketId=${position.marketId}, error=${result.exceptionOrNull()?.message}"
                )
                executeMarketOrder(config, position, assessment, triggerResult, sellQuantity)
            }
            
        } catch (e: Exception) {
            logger.error(
                "限价单创建异常，回退到市价单: accountId=${config.accountId}, " +
                "marketId=${position.marketId}, error=${e.message}", e
            )
            executeMarketOrder(config, position, assessment, triggerResult, sellQuantity)
        }
    }
    
    // ==================== 限价单跟踪 ====================
    
    /**
     * 检查待处理的限价单
     */
    private fun checkPendingLimitOrders() {
        val pendingOrders = pendingOrderRepository.findByStatus("PENDING")
        if (pendingOrders.isEmpty()) {
            return
        }
        
        val now = System.currentTimeMillis()
        
        for (order in pendingOrders) {
            try {
                checkPendingOrder(order, now)
            } catch (e: Exception) {
                logger.error("检查待处理限价单失败: orderId=${order.orderId}, error=${e.message}", e)
            }
        }
    }
    
    /**
     * 检查单个待处理限价单
     */
    private fun checkPendingOrder(order: PendingLimitOrder, now: Long) {
        val config = configRepository.findById(order.configId).orElse(null)
        if (config == null) {
            order.status = "CANCELLED"
            order.updatedAt = now
            pendingOrderRepository.save(order)
            return
        }
        
        val account = accountRepository.findById(order.accountId).orElse(null)
        if (account == null || account.apiKey == null || account.apiSecret == null) {
            order.status = "CANCELLED"
            order.updatedAt = now
            pendingOrderRepository.save(order)
            return
        }
        
        // 查询订单状态（这里需要调用 CLOB API）
        // 由于需要账户凭证，这部分逻辑较复杂，暂时使用超时机制
        
        // 检查是否超时
        if (now >= order.expireAt) {
            handleExpiredOrder(order, config, now)
        }
    }
    
    /**
     * 处理过期的限价单
     */
    private fun handleExpiredOrder(
        order: PendingLimitOrder,
        config: SmartTakeProfitConfig,
        now: Long
    ) {
        // 检查是否可以重试
        if (config.priceRetryEnabled && order.retryCount < order.maxRetryCount) {
            // 降价重试
            retryWithLowerPrice(order, config, now)
        } else {
            // 回退到市价单
            fallbackToMarketOrder(order, config, now)
        }
    }
    
    /**
     * 降价重试
     */
    private fun retryWithLowerPrice(
        order: PendingLimitOrder,
        config: SmartTakeProfitConfig,
        now: Long
    ) {
        // 计算新价格
        val priceStep = config.priceRetryStep.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP)
        var newPrice = order.price.multiply(BigDecimal.ONE.subtract(priceStep))
        
        // 检查是否超过最大滑点
        val maxSlippagePrice = order.initialPrice.multiply(
            BigDecimal.ONE.subtract(order.maxSlippage.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP))
        )
        
        if (newPrice < maxSlippagePrice) {
            logger.info(
                "降价后超过最大滑点，回退到市价单: orderId=${order.orderId}, " +
                "newPrice=$newPrice, maxSlippagePrice=$maxSlippagePrice"
            )
            fallbackToMarketOrder(order, config, now)
            return
        }
        
        // 确保价格在有效范围内
        newPrice = newPrice.max(BigDecimal("0.01")).min(BigDecimal("0.99"))
        
        // 先取消原订单
        // TODO: 调用 clobService.cancelOrder(order.orderId)
        
        // 创建新的限价单
        val sellRequest = PositionSellRequest(
            accountId = order.accountId,
            marketId = order.marketId,
            side = order.outcome,
            outcomeIndex = order.outcomeIndex,
            orderType = "LIMIT",
            quantity = order.quantity.toPlainString(),
            price = newPrice.setScale(2, RoundingMode.DOWN).toPlainString()
        )
        
        try {
            val result = runBlocking {
                accountService.sellPosition(sellRequest)
            }
            
            if (result.isSuccess) {
                // 更新订单记录
                order.retryCount = order.retryCount + 1
                order.expireAt = now + (config.limitOrderWaitSeconds * 1000L)
                order.updatedAt = now
                // 注意：这里应该更新 orderId，但 PendingLimitOrder.orderId 是 val
                // 实际实现中可能需要创建新记录并删除旧记录
                pendingOrderRepository.save(order)
                
                logger.info(
                    "限价单降价重试成功: orderId=${order.orderId}, " +
                    "oldPrice=${order.price}, newPrice=$newPrice, retryCount=${order.retryCount}"
                )
            } else {
                logger.error(
                    "限价单降价重试失败，回退到市价单: orderId=${order.orderId}, " +
                    "error=${result.exceptionOrNull()?.message}"
                )
                fallbackToMarketOrder(order, config, now)
            }
            
        } catch (e: Exception) {
            logger.error(
                "限价单降价重试异常，回退到市价单: orderId=${order.orderId}, error=${e.message}", e
            )
            fallbackToMarketOrder(order, config, now)
        }
    }
    
    /**
     * 回退到市价单
     */
    private fun fallbackToMarketOrder(
        order: PendingLimitOrder,
        config: SmartTakeProfitConfig,
        now: Long
    ) {
        // 先取消原限价单
        // TODO: 调用 clobService.cancelOrder(order.orderId)
        
        val sellRequest = PositionSellRequest(
            accountId = order.accountId,
            marketId = order.marketId,
            side = order.outcome,
            outcomeIndex = order.outcomeIndex,
            orderType = "MARKET",
            quantity = order.quantity.toPlainString()
        )
        
        try {
            val result = runBlocking {
                accountService.sellPosition(sellRequest)
            }
            
            // 更新订单状态
            order.status = if (result.isSuccess) "FILLED" else "FAILED"
            order.updatedAt = now
            pendingOrderRepository.save(order)
            
            // 记录日志
            val log = SmartTakeProfitLog(
                configId = config.id!!,
                accountId = order.accountId,
                marketId = order.marketId,
                outcome = order.outcome,
                outcomeIndex = order.outcomeIndex,
                triggerType = order.triggerType,
                triggerPnlPercent = BigDecimal.ZERO,  // 回退时无法获取实时盈亏
                soldQuantity = order.quantity,
                soldPrice = order.price,  // 使用最后一次尝试的价格
                soldAmount = order.quantity.multiply(order.price),
                orderType = "MARKET_FALLBACK",
                orderId = result.getOrNull()?.orderId,
                status = if (result.isSuccess) "SUCCESS" else "FAILED",
                retryCount = order.retryCount,
                errorMessage = result.exceptionOrNull()?.message
            )
            logRepository.save(log)
            
            if (result.isSuccess) {
                logger.info(
                    "回退市价单成功: orderId=${order.orderId}, " +
                    "newOrderId=${result.getOrNull()?.orderId}"
                )
            } else {
                logger.error(
                    "回退市价单失败: orderId=${order.orderId}, " +
                    "error=${result.exceptionOrNull()?.message}"
                )
            }
            
        } catch (e: Exception) {
            logger.error("回退市价单异常: orderId=${order.orderId}, error=${e.message}", e)
            
            order.status = "FAILED"
            order.updatedAt = now
            pendingOrderRepository.save(order)
        }
    }
    
    // ==================== 仓位风险评估（供前端调用）====================
    
    /**
     * 评估账户所有仓位的风险
     */
    suspend fun assessAccountPositions(accountId: Long): List<PositionRiskAssessment> {
        val config = configRepository.findByAccountId(accountId)
        
        val positions = accountService.getAllPositions()
            .getOrNull()?.currentPositions
            ?.filter { it.accountId == accountId }
            ?: emptyList()
        
        return positions.map { position ->
            assessPositionRisk(position, config)
        }
    }
    
    /**
     * 评估单个仓位的风险
     */
    private suspend fun assessPositionRisk(
        position: AccountPositionDto,
        config: SmartTakeProfitConfig?
    ): PositionRiskAssessment {
        val positionValue = position.currentValue.toSafeBigDecimal()
        var orderbookBidDepth = BigDecimal.ZERO
        var liquidityRatio = BigDecimal.ZERO
        var liquidityLevel = "UNKNOWN"
        
        // 获取订单簿深度
        if (position.outcomeIndex != null) {
            try {
                val tokenIdResult = blockchainService.getTokenId(position.marketId, position.outcomeIndex)
                val tokenId = tokenIdResult.getOrNull()
                
                if (tokenId != null) {
                    val orderbookResult = clobService.getOrderbookByTokenId(tokenId)
                    if (orderbookResult.isSuccess) {
                        val orderbook = orderbookResult.getOrNull()
                        orderbookBidDepth = orderbook?.bids?.sumOf {
                            it.price.toSafeBigDecimal().multiply(it.size.toSafeBigDecimal())
                        } ?: BigDecimal.ZERO
                    }
                }
            } catch (e: Exception) {
                logger.warn("获取订单簿深度失败: marketId=${position.marketId}, error=${e.message}")
            }
            
            if (positionValue > BigDecimal.ZERO) {
                liquidityRatio = orderbookBidDepth.divide(positionValue, 4, RoundingMode.HALF_UP)
                liquidityLevel = when {
                    liquidityRatio < BigDecimal("0.3") -> "CRITICAL"
                    liquidityRatio < BigDecimal("1.0") -> "DANGER"
                    liquidityRatio < BigDecimal("3.0") -> "WARNING"
                    else -> "SAFE"
                }
            }
        }
        
        // 获取市场结束时间
        val market = marketService.getMarket(position.marketId)
        val marketEndDate = market?.endDate
        var remainingMinutes: Int? = null
        var timeLevel = "UNKNOWN"
        
        if (marketEndDate != null) {
            val remainingMs = marketEndDate - System.currentTimeMillis()
            remainingMinutes = (remainingMs / 60000).toInt()
            
            if (remainingMinutes >= 0) {
                timeLevel = when {
                    remainingMinutes < 2 -> "CRITICAL"
                    remainingMinutes < 5 -> "URGENT"
                    remainingMinutes < 30 -> "WARNING"
                    else -> "SAFE"
                }
            }
        }
        
        // 综合风险评估
        val overallRisk = when {
            liquidityLevel == "CRITICAL" || timeLevel == "CRITICAL" -> "CRITICAL"
            liquidityLevel == "DANGER" || timeLevel == "URGENT" -> "HIGH"
            liquidityLevel == "WARNING" || timeLevel == "WARNING" -> "MEDIUM"
            else -> "LOW"
        }
        
        // 计算动态阈值（如果启用了智能止盈止损）
        var dynamicThreshold: String? = null
        var wouldTrigger = false
        
        if (config != null && config.enabled && config.takeProfitEnabled) {
            val liquidityCoef = when (liquidityLevel) {
                "CRITICAL" -> LIQUIDITY_COEF_DANGER
                "DANGER" -> LIQUIDITY_COEF_WARNING
                "WARNING" -> LIQUIDITY_COEF_NORMAL
                else -> LIQUIDITY_COEF_SAFE
            }
            
            val timeCoef = when (timeLevel) {
                "CRITICAL" -> TIME_COEF_EMERGENCY
                "URGENT" -> TIME_COEF_CRITICAL
                "WARNING" -> TIME_COEF_URGENT
                else -> TIME_COEF_NORMAL
            }
            
            val threshold = config.takeProfitBaseThreshold.multiply(
                liquidityCoef.min(timeCoef).coerceAtLeast(BigDecimal.ZERO)
            )
            dynamicThreshold = threshold.setScale(2, RoundingMode.HALF_UP).toPlainString()
            
            val currentPnl = position.percentPnl.toSafeBigDecimal()
            wouldTrigger = currentPnl >= threshold || liquidityCoef <= BigDecimal.ZERO || timeCoef <= BigDecimal.ZERO
        }
        
        return PositionRiskAssessment(
            accountId = position.accountId,
            marketId = position.marketId,
            outcome = position.side,
            outcomeIndex = position.outcomeIndex,
            positionValue = positionValue.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            orderbookBidDepth = orderbookBidDepth.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            liquidityRatio = liquidityRatio.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            liquidityLevel = liquidityLevel,
            marketEndDate = marketEndDate,
            remainingMinutes = remainingMinutes,
            timeLevel = timeLevel,
            overallRisk = overallRisk,
            currentPnlPercent = position.percentPnl,
            dynamicThreshold = dynamicThreshold,
            wouldTrigger = wouldTrigger
        )
    }
    
    // ==================== 辅助方法 ====================
    
    private fun mapToResponse(config: SmartTakeProfitConfig, accountName: String?): SmartTakeProfitConfigResponse {
        return SmartTakeProfitConfigResponse(
            id = config.id ?: 0,
            accountId = config.accountId,
            accountName = accountName,
            enabled = config.enabled,
            takeProfitEnabled = config.takeProfitEnabled,
            takeProfitBaseThreshold = config.takeProfitBaseThreshold.toPlainString(),
            takeProfitRatio = config.takeProfitRatio.toPlainString(),
            takeProfitKeepRatio = config.takeProfitKeepRatio.toPlainString(),
            stopLossEnabled = config.stopLossEnabled,
            stopLossThreshold = config.stopLossThreshold.toPlainString(),
            stopLossRatio = config.stopLossRatio.toPlainString(),
            liquidityAdjustEnabled = config.liquidityAdjustEnabled,
            liquidityDangerRatio = config.liquidityDangerRatio.toPlainString(),
            liquidityWarningRatio = config.liquidityWarningRatio.toPlainString(),
            liquiditySafeRatio = config.liquiditySafeRatio.toPlainString(),
            timeDecayEnabled = config.timeDecayEnabled,
            timeDecayStartMinutes = config.timeDecayStartMinutes,
            timeDecayUrgentMinutes = config.timeDecayUrgentMinutes,
            timeDecayCriticalMinutes = config.timeDecayCriticalMinutes,
            useLimitOrder = config.useLimitOrder,
            limitOrderPremium = config.limitOrderPremium.toPlainString(),
            limitOrderWaitSeconds = config.limitOrderWaitSeconds,
            priceRetryEnabled = config.priceRetryEnabled,
            priceRetryStep = config.priceRetryStep.toPlainString(),
            maxPriceSlippage = config.maxPriceSlippage.toPlainString(),
            maxRetryCount = config.maxRetryCount,
            createdAt = config.createdAt,
            updatedAt = config.updatedAt
        )
    }
    
    private fun mapLogToResponse(log: SmartTakeProfitLog): SmartTakeProfitLogResponse {
        return SmartTakeProfitLogResponse(
            id = log.id ?: 0,
            configId = log.configId,
            accountId = log.accountId,
            marketId = log.marketId,
            marketTitle = log.marketTitle,
            outcome = log.outcome,
            outcomeIndex = log.outcomeIndex,
            triggerType = log.triggerType,
            triggerPnlPercent = log.triggerPnlPercent.toPlainString(),
            dynamicThreshold = log.dynamicThreshold?.toPlainString(),
            liquidityCoef = log.liquidityCoef?.toPlainString(),
            timeCoef = log.timeCoef?.toPlainString(),
            urgencyLevel = log.urgencyLevel?.toPlainString(),
            orderbookBidDepth = log.orderbookBidDepth?.toPlainString(),
            positionValue = log.positionValue?.toPlainString(),
            remainingMinutes = log.remainingMinutes,
            soldQuantity = log.soldQuantity.toPlainString(),
            soldPrice = log.soldPrice.toPlainString(),
            soldAmount = log.soldAmount.toPlainString(),
            orderType = log.orderType,
            orderId = log.orderId,
            status = log.status,
            retryCount = log.retryCount,
            errorMessage = log.errorMessage,
            createdAt = log.createdAt
        )
    }
    
    // ==================== 内部数据类 ====================
    
    /**
     * 仓位评估数据
     */
    private data class PositionAssessmentData(
        val positionValue: BigDecimal,
        val orderbookBidDepth: BigDecimal,
        val liquidityCoef: BigDecimal,
        val remainingMinutes: Int?,
        val timeCoef: BigDecimal,
        val urgencyLevel: BigDecimal
    )
    
    /**
     * 触发结果
     */
    private data class TriggerResult(
        val triggered: Boolean,
        val triggerType: String,
        val dynamicThreshold: BigDecimal
    )
}
