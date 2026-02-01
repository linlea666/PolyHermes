package com.wrbug.polymarketbot.repository

import com.wrbug.polymarketbot.entity.SmartTakeProfitConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 智能止盈止损配置 Repository
 */
@Repository
interface SmartTakeProfitConfigRepository : JpaRepository<SmartTakeProfitConfig, Long> {
    
    /**
     * 根据账户ID查询配置
     */
    fun findByAccountId(accountId: Long): SmartTakeProfitConfig?
    
    /**
     * 查询所有启用的配置
     */
    fun findByEnabledTrue(): List<SmartTakeProfitConfig>
    
    /**
     * 检查账户是否已存在配置
     */
    fun existsByAccountId(accountId: Long): Boolean
    
    /**
     * 根据账户ID删除配置
     */
    fun deleteByAccountId(accountId: Long)
}
