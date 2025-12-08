package com.wrbug.polymarketbot.service.copytrading.configs

import com.wrbug.polymarketbot.api.OrderbookResponse
import com.wrbug.polymarketbot.entity.CopyTrading
import com.wrbug.polymarketbot.util.gt
import com.wrbug.polymarketbot.util.lt
import com.wrbug.polymarketbot.util.multi
import com.wrbug.polymarketbot.util.toSafeBigDecimal
import org.slf4j.LoggerFactory
import com.wrbug.polymarketbot.service.common.PolymarketClobService
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * 跟单过滤条件检查服务
 */
@Service
class CopyTradingFilterService(
    private val clobService: PolymarketClobService
) {
    
    private val logger = LoggerFactory.getLogger(CopyTradingFilterService::class.java)
    
    /**
     * 检查过滤条件
     * @param copyTrading 跟单配置
     * @param tokenId token ID（用于获取订单簿）
     * @param tradePrice Leader 交易价格，用于价格区间检查
     * @return 过滤结果
     */
    suspend fun checkFilters(
        copyTrading: CopyTrading,
        tokenId: String,
        tradePrice: BigDecimal? = null  // Leader 交易价格，用于价格区间检查
    ): FilterResult {
        // 1. 价格区间检查（如果配置了价格区间）
        if (tradePrice != null) {
            val priceRangeCheck = checkPriceRange(copyTrading, tradePrice)
            if (!priceRangeCheck.isPassed) {
                return FilterResult.priceRangeFailed(priceRangeCheck.reason)
            }
        }
        
        // 2. 检查是否需要获取订单簿
        // 只有在配置了需要订单簿的过滤条件时才获取
        val needOrderbook = copyTrading.maxSpread != null || copyTrading.minOrderDepth != null
        
        if (!needOrderbook) {
            // 不需要订单簿，直接通过
            return FilterResult.passed()
        }
        
        // 3. 获取订单簿（仅在需要时，只请求一次）
        val orderbookResult = clobService.getOrderbookByTokenId(tokenId)
        if (!orderbookResult.isSuccess) {
            val error = orderbookResult.exceptionOrNull()
            return FilterResult.orderbookError("获取订单簿失败: ${error?.message ?: "未知错误"}")
        }
        
        val orderbook = orderbookResult.getOrNull()
            ?: return FilterResult.orderbookEmpty()
        
        // 4. 买一卖一价差过滤（如果配置了）
        if (copyTrading.maxSpread != null) {
            val spreadCheck = checkSpread(copyTrading, orderbook)
            if (!spreadCheck.isPassed) {
                return FilterResult.spreadFailed(spreadCheck.reason, orderbook)
            }
        }
        
        // 5. 订单深度过滤（如果配置了，检查所有方向）
        if (copyTrading.minOrderDepth != null) {
            val depthCheck = checkOrderDepth(copyTrading, orderbook)
            if (!depthCheck.isPassed) {
                return FilterResult.orderDepthFailed(depthCheck.reason, orderbook)
            }
        }
        
        return FilterResult.passed(orderbook)
    }
    
    /**
     * 检查价格区间
     * @param copyTrading 跟单配置
     * @param tradePrice Leader 交易价格
     * @return 过滤结果
     */
    private fun checkPriceRange(
        copyTrading: CopyTrading,
        tradePrice: BigDecimal
    ): FilterResult {
        // 如果未配置价格区间，直接通过
        if (copyTrading.minPrice == null && copyTrading.maxPrice == null) {
            return FilterResult.passed()
        }
        
        // 检查最低价格
        if (copyTrading.minPrice != null && tradePrice.lt(copyTrading.minPrice)) {
            return FilterResult.priceRangeFailed("价格低于最低限制: $tradePrice < ${copyTrading.minPrice}")
        }
        
        // 检查最高价格
        if (copyTrading.maxPrice != null && tradePrice.gt(copyTrading.maxPrice)) {
            return FilterResult.priceRangeFailed("价格高于最高限制: $tradePrice > ${copyTrading.maxPrice}")
        }
        
        return FilterResult.passed()
    }
    
    /**
     * 检查买一卖一价差
     * bestBid: 买盘中的最高价格（最大值）
     * bestAsk: 卖盘中的最低价格（最小值）
     */
    private fun checkSpread(
        copyTrading: CopyTrading,
        orderbook: OrderbookResponse
    ): FilterResult {
        // 如果未启用价差过滤，直接通过
        if (copyTrading.maxSpread == null) {
            return FilterResult.passed()
        }
        
        // 获取买盘中的最高价格（bestBid = bids 中的最大值）
        val bestBid = orderbook.bids
            .mapNotNull { it.price.toSafeBigDecimal() }
            .maxOrNull()
        
        // 获取卖盘中的最低价格（bestAsk = asks 中的最小值）
        val bestAsk = orderbook.asks
            .mapNotNull { it.price.toSafeBigDecimal() }
            .minOrNull()
        
        if (bestBid == null || bestAsk == null) {
            return FilterResult.spreadFailed("订单簿缺少买一或卖一价格", orderbook)
        }
        
        // 计算价差（绝对价格）
        val spread = bestAsk.subtract(bestBid)
        
        if (spread.gt(copyTrading.maxSpread)) {
            return FilterResult.spreadFailed("价差过大: $spread > ${copyTrading.maxSpread}", orderbook)
        }
        
        return FilterResult.passed()
    }
    
    /**
     * 检查订单深度（检查所有方向：买盘和卖盘的总深度）
     */
    private fun checkOrderDepth(
        copyTrading: CopyTrading,
        orderbook: OrderbookResponse
    ): FilterResult {
        // 如果未启用订单深度过滤，直接通过
        if (copyTrading.minOrderDepth == null) {
            return FilterResult.passed()
        }
        
        // 计算买盘（bids）总深度
        var bidsDepth = BigDecimal.ZERO
        for (order in orderbook.bids) {
            val price = order.price.toSafeBigDecimal()
            val size = order.size.toSafeBigDecimal()
            val orderAmount = price.multi(size)
            bidsDepth = bidsDepth.add(orderAmount)
        }
        
        // 计算卖盘（asks）总深度
        var asksDepth = BigDecimal.ZERO
        for (order in orderbook.asks) {
            val price = order.price.toSafeBigDecimal()
            val size = order.size.toSafeBigDecimal()
            val orderAmount = price.multi(size)
            asksDepth = asksDepth.add(orderAmount)
        }
        
        // 计算总深度（买盘 + 卖盘）
        val totalDepth = bidsDepth.add(asksDepth)
        
        if (totalDepth.lt(copyTrading.minOrderDepth)) {
            return FilterResult.orderDepthFailed("订单深度不足: $totalDepth < ${copyTrading.minOrderDepth}", orderbook)
        }
        
        return FilterResult.passed()
    }
}

