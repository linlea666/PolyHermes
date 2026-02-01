package com.wrbug.polymarketbot.controller.smarttp

import com.wrbug.polymarketbot.dto.*
import com.wrbug.polymarketbot.enums.ErrorCode
import com.wrbug.polymarketbot.service.smarttp.SmartTakeProfitService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 智能止盈止损控制器
 * 
 * 提供配置管理、日志查询、风险评估等 API
 */
@RestController
@RequestMapping("/api/smart-take-profit")
class SmartTakeProfitController(
    private val smartTakeProfitService: SmartTakeProfitService,
    private val messageSource: MessageSource
) {
    private val logger = LoggerFactory.getLogger(SmartTakeProfitController::class.java)
    
    /**
     * 获取账户的止盈止损配置
     */
    @PostMapping("/config/get")
    fun getConfig(
        @RequestBody request: Map<String, Long>
    ): ResponseEntity<ApiResponse<SmartTakeProfitConfigResponse?>> {
        return try {
            val accountId = request["accountId"] 
                ?: return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_EMPTY, messageSource = messageSource))
            
            val config = smartTakeProfitService.getConfig(accountId)
            ResponseEntity.ok(ApiResponse.success(config))
        } catch (e: Exception) {
            logger.error("获取智能止盈止损配置失败: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_ERROR, messageSource = messageSource))
        }
    }
    
    /**
     * 保存配置（创建或更新）
     */
    @PostMapping("/config/save")
    fun saveConfig(
        @RequestBody request: SmartTakeProfitConfigRequest
    ): ResponseEntity<ApiResponse<SmartTakeProfitConfigResponse>> {
        return try {
            if (request.accountId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_EMPTY, messageSource = messageSource))
            }
            
            val config = smartTakeProfitService.saveConfig(request)
            ResponseEntity.ok(ApiResponse.success(config))
        } catch (e: Exception) {
            logger.error("保存智能止盈止损配置失败: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_ERROR, messageSource = messageSource))
        }
    }
    
    /**
     * 快速开启/关闭
     */
    @PostMapping("/config/toggle")
    fun toggleEnabled(
        @RequestBody request: SmartTakeProfitToggleRequest
    ): ResponseEntity<ApiResponse<Boolean>> {
        return try {
            if (request.accountId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_EMPTY, messageSource = messageSource))
            }
            
            val enabled = smartTakeProfitService.toggleEnabled(request)
            ResponseEntity.ok(ApiResponse.success(enabled))
        } catch (e: Exception) {
            logger.error("切换智能止盈止损状态失败: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_ERROR, messageSource = messageSource))
        }
    }
    
    /**
     * 删除配置
     */
    @PostMapping("/config/delete")
    fun deleteConfig(
        @RequestBody request: Map<String, Long>
    ): ResponseEntity<ApiResponse<Boolean>> {
        return try {
            val accountId = request["accountId"]
                ?: return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_EMPTY, messageSource = messageSource))
            
            smartTakeProfitService.deleteConfig(accountId)
            ResponseEntity.ok(ApiResponse.success(true))
        } catch (e: Exception) {
            logger.error("删除智能止盈止损配置失败: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_ERROR, messageSource = messageSource))
        }
    }
    
    /**
     * 查询执行日志
     */
    @PostMapping("/logs")
    fun getLogs(
        @RequestBody request: SmartTakeProfitLogQueryRequest
    ): ResponseEntity<ApiResponse<Page<SmartTakeProfitLogResponse>>> {
        return try {
            val logs = smartTakeProfitService.getLogs(request)
            ResponseEntity.ok(ApiResponse.success(logs))
        } catch (e: Exception) {
            logger.error("查询智能止盈止损日志失败: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_ERROR, messageSource = messageSource))
        }
    }
    
    /**
     * 获取账户仓位的风险评估
     */
    @PostMapping("/risk-assessment")
    fun getRiskAssessment(
        @RequestBody request: PositionRiskAssessmentRequest
    ): ResponseEntity<ApiResponse<List<PositionRiskAssessment>>> {
        return try {
            if (request.accountId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_EMPTY, messageSource = messageSource))
            }
            
            val assessments = runBlocking {
                smartTakeProfitService.assessAccountPositions(request.accountId)
            }
            ResponseEntity.ok(ApiResponse.success(assessments))
        } catch (e: Exception) {
            logger.error("获取仓位风险评估失败: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_ERROR, messageSource = messageSource))
        }
    }
}
