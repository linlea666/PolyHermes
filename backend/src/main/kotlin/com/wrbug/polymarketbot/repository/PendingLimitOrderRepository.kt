package com.wrbug.polymarketbot.repository

import com.wrbug.polymarketbot.entity.PendingLimitOrder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * 待处理限价单 Repository
 */
@Repository
interface PendingLimitOrderRepository : JpaRepository<PendingLimitOrder, Long> {
    
    /**
     * 查询所有待处理的限价单
     */
    fun findByStatus(status: String): List<PendingLimitOrder>
    
    /**
     * 查询指定账户的待处理限价单
     */
    fun findByAccountIdAndStatus(accountId: Long, status: String): List<PendingLimitOrder>
    
    /**
     * 根据订单ID查询
     */
    fun findByOrderId(orderId: String): PendingLimitOrder?
    
    /**
     * 查询已过期的待处理订单
     */
    fun findByStatusAndExpireAtLessThan(status: String, expireAt: Long): List<PendingLimitOrder>
    
    /**
     * 检查指定市场是否有待处理订单（避免重复下单）
     */
    fun existsByAccountIdAndMarketIdAndOutcomeAndStatus(
        accountId: Long,
        marketId: String,
        outcome: String,
        status: String
    ): Boolean
    
    /**
     * 清理已完成或已取消的订单（超过指定时间）
     */
    @Modifying
    @Query("DELETE FROM PendingLimitOrder p WHERE p.status IN ('FILLED', 'CANCELLED', 'FAILED') AND p.updatedAt < :beforeTime")
    fun deleteCompletedOrdersBefore(beforeTime: Long): Int
    
    /**
     * 根据配置ID删除所有订单
     */
    fun deleteByConfigId(configId: Long)
}
