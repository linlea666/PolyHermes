package com.wrbug.polymarketbot.controller

import com.wrbug.polymarketbot.dto.*
import com.wrbug.polymarketbot.service.AccountService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 账户管理控制器
 */
@RestController
@RequestMapping("/api/copy-trading/accounts")
class AccountController(
    private val accountService: AccountService
) {
    
    private val logger = LoggerFactory.getLogger(AccountController::class.java)
    
    /**
     * 通过私钥导入账户
     */
    @PostMapping("/import")
    fun importAccount(@RequestBody request: AccountImportRequest): ResponseEntity<ApiResponse<AccountDto>> {
        return try {
            // 参数验证
            if (request.privateKey.isBlank()) {
                return ResponseEntity.ok(ApiResponse.paramError("私钥不能为空"))
            }
            if (request.walletAddress.isBlank()) {
                return ResponseEntity.ok(ApiResponse.paramError("钱包地址不能为空"))
            }
            
            val result = accountService.importAccount(request)
            result.fold(
                onSuccess = { account ->
                    logger.info("成功导入账户: ${account.id}")
                    ResponseEntity.ok(ApiResponse.success(account))
                },
                onFailure = { e ->
                    logger.error("导入账户失败: ${e.message}", e)
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.ok(ApiResponse.paramError(e.message ?: "参数错误"))
                        else -> ResponseEntity.ok(ApiResponse.serverError("导入账户失败: ${e.message}"))
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("导入账户异常: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.serverError("导入账户失败: ${e.message}"))
        }
    }
    
    /**
     * 更新账户信息
     */
    @PostMapping("/update")
    fun updateAccount(@RequestBody request: AccountUpdateRequest): ResponseEntity<ApiResponse<AccountDto>> {
        return try {
            val result = accountService.updateAccount(request)
            result.fold(
                onSuccess = { account ->
                    logger.info("成功更新账户: ${account.id}")
                    ResponseEntity.ok(ApiResponse.success(account))
                },
                onFailure = { e ->
                    logger.error("更新账户失败: ${e.message}", e)
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.ok(ApiResponse.paramError(e.message ?: "参数错误"))
                        else -> ResponseEntity.ok(ApiResponse.serverError("更新账户失败: ${e.message}"))
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("更新账户异常: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.serverError("更新账户失败: ${e.message}"))
        }
    }
    
    /**
     * 删除账户
     */
    @PostMapping("/delete")
    fun deleteAccount(@RequestBody request: AccountDeleteRequest): ResponseEntity<ApiResponse<Unit>> {
        return try {
            val result = accountService.deleteAccount(request.accountId)
            result.fold(
                onSuccess = {
                    logger.info("成功删除账户: ${request.accountId}")
                    ResponseEntity.ok(ApiResponse.success(Unit))
                },
                onFailure = { e ->
                    logger.error("删除账户失败: ${e.message}", e)
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.ok(ApiResponse.paramError(e.message ?: "参数错误"))
                        is IllegalStateException -> ResponseEntity.ok(ApiResponse.businessError(e.message ?: "业务逻辑错误"))
                        else -> ResponseEntity.ok(ApiResponse.serverError("删除账户失败: ${e.message}"))
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("删除账户异常: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.serverError("删除账户失败: ${e.message}"))
        }
    }
    
    /**
     * 查询账户列表
     */
    @PostMapping("/list")
    fun getAccountList(): ResponseEntity<ApiResponse<AccountListResponse>> {
        return try {
            val result = accountService.getAccountList()
            result.fold(
                onSuccess = { response ->
                    logger.info("成功查询账户列表: ${response.total} 个账户")
                    ResponseEntity.ok(ApiResponse.success(response))
                },
                onFailure = { e ->
                    logger.error("查询账户列表失败: ${e.message}", e)
                    ResponseEntity.ok(ApiResponse.serverError("查询账户列表失败: ${e.message}"))
                }
            )
        } catch (e: Exception) {
            logger.error("查询账户列表异常: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.serverError("查询账户列表失败: ${e.message}"))
        }
    }
    
    /**
     * 查询账户详情
     */
    @PostMapping("/detail")
    fun getAccountDetail(@RequestBody request: AccountDetailRequest): ResponseEntity<ApiResponse<AccountDto>> {
        return try {
            val result = accountService.getAccountDetail(request.accountId)
            result.fold(
                onSuccess = { account ->
                    logger.info("成功查询账户详情: ${account.id}")
                    ResponseEntity.ok(ApiResponse.success(account))
                },
                onFailure = { e ->
                    logger.error("查询账户详情失败: ${e.message}", e)
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.ok(ApiResponse.paramError(e.message ?: "参数错误"))
                        else -> ResponseEntity.ok(ApiResponse.serverError("查询账户详情失败: ${e.message}"))
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("查询账户详情异常: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.serverError("查询账户详情失败: ${e.message}"))
        }
    }
    
    /**
     * 查询账户余额
     */
    @PostMapping("/balance")
    fun getAccountBalance(@RequestBody request: AccountBalanceRequest): ResponseEntity<ApiResponse<AccountBalanceResponse>> {
        return try {
            val result = accountService.getAccountBalance(request.accountId)
            result.fold(
                onSuccess = { balance ->
                    logger.info("成功查询账户余额")
                    ResponseEntity.ok(ApiResponse.success(balance))
                },
                onFailure = { e ->
                    logger.error("查询账户余额失败: ${e.message}", e)
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.ok(ApiResponse.paramError(e.message ?: "参数错误"))
                        else -> ResponseEntity.ok(ApiResponse.serverError("查询账户余额失败: ${e.message}"))
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("查询账户余额异常: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.serverError("查询账户余额失败: ${e.message}"))
        }
    }
    
    /**
     * 设置默认账户
     */
    @PostMapping("/set-default")
    fun setDefaultAccount(@RequestBody request: SetDefaultAccountRequest): ResponseEntity<ApiResponse<Unit>> {
        return try {
            val result = accountService.setDefaultAccount(request.accountId)
            result.fold(
                onSuccess = {
                    logger.info("成功设置默认账户: ${request.accountId}")
                    ResponseEntity.ok(ApiResponse.success(Unit))
                },
                onFailure = { e ->
                    logger.error("设置默认账户失败: ${e.message}", e)
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.ok(ApiResponse.paramError(e.message ?: "参数错误"))
                        else -> ResponseEntity.ok(ApiResponse.serverError("设置默认账户失败: ${e.message}"))
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("设置默认账户异常: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.serverError("设置默认账户失败: ${e.message}"))
        }
    }
    
    /**
     * 查询所有账户的仓位列表
     */
    @PostMapping("/positions/list")
    fun getAllPositions(): ResponseEntity<ApiResponse<PositionListResponse>> {
        return try {
            val result = runBlocking { accountService.getAllPositions() }
            result.fold(
                onSuccess = { positionListResponse ->
                    val total = positionListResponse.currentPositions.size + positionListResponse.historyPositions.size
                    logger.info("成功查询仓位列表: 当前仓位 ${positionListResponse.currentPositions.size} 个，历史仓位 ${positionListResponse.historyPositions.size} 个，共 $total 个")
                    ResponseEntity.ok(ApiResponse.success(positionListResponse))
                },
                onFailure = { e ->
                    logger.error("查询仓位列表失败: ${e.message}", e)
                    ResponseEntity.ok(ApiResponse.serverError("查询仓位列表失败: ${e.message}"))
                }
            )
        } catch (e: Exception) {
            logger.error("查询仓位列表异常: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.serverError("查询仓位列表失败: ${e.message}"))
        }
    }
    
    /**
     * 卖出仓位
     */
    @PostMapping("/positions/sell")
    fun sellPosition(@RequestBody request: PositionSellRequest): ResponseEntity<ApiResponse<PositionSellResponse>> {
        return try {
            // 参数验证
            if (request.accountId <= 0) {
                return ResponseEntity.ok(ApiResponse.paramError("账户ID无效"))
            }
            if (request.marketId.isBlank()) {
                return ResponseEntity.ok(ApiResponse.paramError("市场ID不能为空"))
            }
            if (request.side !in listOf("YES", "NO")) {
                return ResponseEntity.ok(ApiResponse.paramError("方向必须是YES或NO"))
            }
            if (request.orderType !in listOf("MARKET", "LIMIT")) {
                return ResponseEntity.ok(ApiResponse.paramError("订单类型必须是MARKET或LIMIT"))
            }
            if (request.quantity.isBlank()) {
                return ResponseEntity.ok(ApiResponse.paramError("卖出数量不能为空"))
            }
            if (request.orderType == "LIMIT" && (request.price == null || request.price.isBlank())) {
                return ResponseEntity.ok(ApiResponse.paramError("限价订单必须提供价格"))
            }
            
            val result = runBlocking { accountService.sellPosition(request) }
            result.fold(
                onSuccess = { response ->
                    logger.info("成功创建卖出订单: 账户=${request.accountId}, 市场=${request.marketId}, 订单ID=${response.orderId}")
                    ResponseEntity.ok(ApiResponse.success(response))
                },
                onFailure = { e ->
                    logger.error("创建卖出订单失败: ${e.message}", e)
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.ok(ApiResponse.paramError(e.message ?: "参数错误"))
                        is IllegalStateException -> ResponseEntity.ok(ApiResponse.businessError(e.message ?: "业务逻辑错误"))
                        else -> ResponseEntity.ok(ApiResponse.serverError("创建卖出订单失败: ${e.message}"))
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("创建卖出订单异常: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.serverError("创建卖出订单失败: ${e.message}"))
        }
    }
    
    /**
     * 获取市场价格
     */
    @PostMapping("/markets/price")
    fun getMarketPrice(@RequestBody request: MarketPriceRequest): ResponseEntity<ApiResponse<MarketPriceResponse>> {
        return try {
            if (request.marketId.isBlank()) {
                return ResponseEntity.ok(ApiResponse.paramError("市场ID不能为空"))
            }
            
            val result = runBlocking { accountService.getMarketPrice(request.marketId) }
            result.fold(
                onSuccess = { response ->
                    logger.info("成功获取市场价格: 市场=${request.marketId}")
                    ResponseEntity.ok(ApiResponse.success(response))
                },
                onFailure = { e ->
                    logger.error("获取市场价格失败: ${e.message}", e)
                    ResponseEntity.ok(ApiResponse.serverError("获取市场价格失败: ${e.message}"))
                }
            )
        } catch (e: Exception) {
            logger.error("获取市场价格异常: ${e.message}", e)
            ResponseEntity.ok(ApiResponse.serverError("获取市场价格失败: ${e.message}"))
        }
    }
}

