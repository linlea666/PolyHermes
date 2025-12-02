package com.wrbug.polymarketbot.service

import com.wrbug.polymarketbot.dto.*
import com.wrbug.polymarketbot.entity.CopyTrading
import com.wrbug.polymarketbot.repository.AccountRepository
import com.wrbug.polymarketbot.repository.CopyTradingRepository
import com.wrbug.polymarketbot.repository.CopyTradingTemplateRepository
import com.wrbug.polymarketbot.repository.LeaderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 跟单配置管理服务（钱包-模板关联）
 */
@Service
class CopyTradingService(
    private val copyTradingRepository: CopyTradingRepository,
    private val accountRepository: AccountRepository,
    private val templateRepository: CopyTradingTemplateRepository,
    private val leaderRepository: LeaderRepository,
    private val monitorService: CopyTradingMonitorService
) {
    
    private val logger = LoggerFactory.getLogger(CopyTradingService::class.java)
    
    /**
     * 创建跟单
     */
    @Transactional
    fun createCopyTrading(request: CopyTradingCreateRequest): Result<CopyTradingDto> {
        return try {
            // 1. 验证账户是否存在
            val account = accountRepository.findById(request.accountId).orElse(null)
                ?: return Result.failure(IllegalArgumentException("账户不存在"))
            
            // 2. 验证模板是否存在
            val template = templateRepository.findById(request.templateId).orElse(null)
                ?: return Result.failure(IllegalArgumentException("模板不存在"))
            
            // 3. 验证 Leader 是否存在
            val leader = leaderRepository.findById(request.leaderId).orElse(null)
                ?: return Result.failure(IllegalArgumentException("Leader 不存在"))
            
            // 4. 检查是否已存在相同的跟单关系
            val existing = copyTradingRepository.findByAccountIdAndTemplateIdAndLeaderId(
                request.accountId,
                request.templateId,
                request.leaderId
            )
            if (existing != null) {
                return Result.failure(IllegalArgumentException("该跟单关系已存在"))
            }
            
            // 5. 创建跟单关系
            val copyTrading = CopyTrading(
                accountId = request.accountId,
                templateId = request.templateId,
                leaderId = request.leaderId,
                enabled = request.enabled
            )
            
            val saved = copyTradingRepository.save(copyTrading)
            
            // 如果跟单已启用，启动Leader监听
            if (saved.enabled) {
                kotlinx.coroutines.runBlocking {
                    try {
                        monitorService.addLeaderMonitoring(saved.leaderId)
                    } catch (e: Exception) {
                        logger.error("启动Leader监听失败: leaderId=${saved.leaderId}", e)
                    }
                }
            }
            
            Result.success(toDto(saved, account, template, leader))
        } catch (e: Exception) {
            logger.error("创建跟单失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 查询跟单列表
     */
    fun getCopyTradingList(request: CopyTradingListRequest): Result<CopyTradingListResponse> {
        return try {
            val copyTradings = when {
                request.accountId != null && request.templateId != null && request.leaderId != null -> {
                    val found = copyTradingRepository.findByAccountIdAndTemplateIdAndLeaderId(
                        request.accountId,
                        request.templateId,
                        request.leaderId
                    )
                    if (found != null) listOf(found) else emptyList()
                }
                request.accountId != null && request.templateId != null -> {
                    copyTradingRepository.findByAccountIdAndTemplateId(request.accountId, request.templateId)
                }
                request.accountId != null -> {
                    copyTradingRepository.findByAccountId(request.accountId)
                }
                request.templateId != null -> {
                    copyTradingRepository.findByTemplateId(request.templateId)
                }
                request.leaderId != null -> {
                    copyTradingRepository.findByLeaderId(request.leaderId)
                }
                request.enabled != null && request.enabled -> {
                    copyTradingRepository.findByEnabledTrue()
                }
                else -> {
                    copyTradingRepository.findAll()
                }
            }
            
            // 过滤启用状态
            val filtered = if (request.enabled != null) {
                copyTradings.filter { it.enabled == request.enabled }
            } else {
                copyTradings
            }
            
            val dtos = filtered.map { copyTrading ->
                val account = accountRepository.findById(copyTrading.accountId).orElse(null)
                val template = templateRepository.findById(copyTrading.templateId).orElse(null)
                val leader = leaderRepository.findById(copyTrading.leaderId).orElse(null)
                
                if (account == null || template == null || leader == null) {
                    logger.warn("跟单关系数据不完整: ${copyTrading.id}")
                    null
                } else {
                    toDto(copyTrading, account, template, leader)
                }
            }.filterNotNull()
            
            Result.success(
                CopyTradingListResponse(
                    list = dtos,
                    total = dtos.size.toLong()
                )
            )
        } catch (e: Exception) {
            logger.error("查询跟单列表失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 更新跟单状态
     */
    @Transactional
    fun updateCopyTradingStatus(request: CopyTradingUpdateStatusRequest): Result<CopyTradingDto> {
        return try {
            val copyTrading = copyTradingRepository.findById(request.copyTradingId).orElse(null)
                ?: return Result.failure(IllegalArgumentException("跟单关系不存在"))
            
            val updated = copyTrading.copy(
                enabled = request.enabled,
                updatedAt = System.currentTimeMillis()
            )
            
            val saved = copyTradingRepository.save(updated)
            
            // 重新启动监听（确保状态完全同步）
            kotlinx.coroutines.runBlocking {
                try {
                    monitorService.restartMonitoring()
                } catch (e: Exception) {
                    logger.error("重新启动跟单监听失败", e)
                }
            }
            
            val account = accountRepository.findById(saved.accountId).orElse(null)
            val template = templateRepository.findById(saved.templateId).orElse(null)
            val leader = leaderRepository.findById(saved.leaderId).orElse(null)
            
            if (account == null || template == null || leader == null) {
                return Result.failure(IllegalStateException("跟单关系数据不完整"))
            }
            
            Result.success(toDto(saved, account, template, leader))
        } catch (e: Exception) {
            logger.error("更新跟单状态失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 删除跟单
     */
    @Transactional
    fun deleteCopyTrading(copyTradingId: Long): Result<Unit> {
        return try {
            val copyTrading = copyTradingRepository.findById(copyTradingId).orElse(null)
                ?: return Result.failure(IllegalArgumentException("跟单关系不存在"))
            
            val leaderId = copyTrading.leaderId
            copyTradingRepository.delete(copyTrading)
            
            // 移除监听（如果该Leader没有其他启用的跟单关系）
            kotlinx.coroutines.runBlocking {
                try {
                    monitorService.removeLeaderMonitoring(leaderId)
                } catch (e: Exception) {
                    logger.error("移除Leader监听失败: leaderId=$leaderId", e)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("删除跟单失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 查询钱包绑定的模板
     */
    fun getAccountTemplates(accountId: Long): Result<AccountTemplatesResponse> {
        return try {
            // 验证账户是否存在
            val account = accountRepository.findById(accountId).orElse(null)
                ?: return Result.failure(IllegalArgumentException("账户不存在"))
            
            val copyTradings = copyTradingRepository.findByAccountId(accountId)
            
            val dtos = copyTradings.mapNotNull { copyTrading ->
                val template = templateRepository.findById(copyTrading.templateId).orElse(null)
                val leader = leaderRepository.findById(copyTrading.leaderId).orElse(null)
                
                if (template == null || leader == null) {
                    logger.warn("跟单关系数据不完整: ${copyTrading.id}")
                    null
                } else {
                    AccountTemplateDto(
                        templateId = template.id!!,
                        templateName = template.templateName,
                        copyTradingId = copyTrading.id!!,
                        leaderId = leader.id!!,
                        leaderName = leader.leaderName,
                        leaderAddress = leader.leaderAddress,
                        enabled = copyTrading.enabled
                    )
                }
            }
            
            Result.success(
                AccountTemplatesResponse(
                    list = dtos,
                    total = dtos.size.toLong()
                )
            )
        } catch (e: Exception) {
            logger.error("查询钱包绑定的模板失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 转换为 DTO
     */
    private fun toDto(
        copyTrading: CopyTrading,
        account: com.wrbug.polymarketbot.entity.Account,
        template: com.wrbug.polymarketbot.entity.CopyTradingTemplate,
        leader: com.wrbug.polymarketbot.entity.Leader
    ): CopyTradingDto {
        return CopyTradingDto(
            id = copyTrading.id!!,
            accountId = account.id!!,
            accountName = account.accountName,
            walletAddress = account.walletAddress,
            templateId = template.id!!,
            templateName = template.templateName,
            leaderId = leader.id!!,
            leaderName = leader.leaderName,
            leaderAddress = leader.leaderAddress,
            enabled = copyTrading.enabled,
            createdAt = copyTrading.createdAt,
            updatedAt = copyTrading.updatedAt
        )
    }
}

