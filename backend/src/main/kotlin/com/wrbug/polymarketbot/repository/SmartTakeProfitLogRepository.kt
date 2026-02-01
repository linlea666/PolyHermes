package com.wrbug.polymarketbot.repository

import com.wrbug.polymarketbot.entity.SmartTakeProfitLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 智能止盈止损日志 Repository
 */
@Repository
interface SmartTakeProfitLogRepository : JpaRepository<SmartTakeProfitLog, Long> {
    
    /**
     * 根据配置ID分页查询日志
     */
    fun findByConfigIdOrderByCreatedAtDesc(configId: Long, pageable: Pageable): Page<SmartTakeProfitLog>
    
    /**
     * 根据账户ID分页查询日志
     */
    fun findByAccountIdOrderByCreatedAtDesc(accountId: Long, pageable: Pageable): Page<SmartTakeProfitLog>
    
    /**
     * 根据账户ID和市场ID查询日志
     */
    fun findByAccountIdAndMarketIdOrderByCreatedAtDesc(
        accountId: Long,
        marketId: String,
        pageable: Pageable
    ): Page<SmartTakeProfitLog>
    
    /**
     * 查询指定时间范围内的日志
     */
    fun findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        accountId: Long,
        startTime: Long,
        endTime: Long,
        pageable: Pageable
    ): Page<SmartTakeProfitLog>
    
    /**
     * 查询最近N分钟内是否有对该市场的止盈止损执行记录
     * 用于避免短时间内重复执行
     */
    fun findByAccountIdAndMarketIdAndOutcomeAndCreatedAtGreaterThan(
        accountId: Long,
        marketId: String,
        outcome: String,
        createdAfter: Long
    ): List<SmartTakeProfitLog>
    
    /**
     * 根据配置ID删除所有日志
     */
    fun deleteByConfigId(configId: Long)
    
    /**
     * 统计成功执行次数
     */
    fun countByConfigIdAndStatus(configId: Long, status: String): Long
}
