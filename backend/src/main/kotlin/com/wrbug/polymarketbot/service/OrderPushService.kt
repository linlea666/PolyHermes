package com.wrbug.polymarketbot.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.wrbug.polymarketbot.dto.OrderDetailDto
import com.wrbug.polymarketbot.dto.OrderMessageDto
import com.wrbug.polymarketbot.dto.OrderPushMessage
import com.wrbug.polymarketbot.entity.Account
import com.wrbug.polymarketbot.repository.AccountRepository
import com.wrbug.polymarketbot.util.RetrofitFactory
import com.wrbug.polymarketbot.websocket.PolymarketWebSocketClient
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 订单推送服务
 * 为每个账户建立到 Polymarket User Channel 的连接，接收订单消息并推送给前端
 */
@Service
class OrderPushService(
    private val accountRepository: AccountRepository,
    private val objectMapper: ObjectMapper,
    private val clobService: PolymarketClobService,
    private val retrofitFactory: RetrofitFactory  // 用于创建 Gamma API 客户端（不需要认证）
) {

    private val logger = LoggerFactory.getLogger(OrderPushService::class.java)

    @Value("\${polymarket.rtds.ws-url}")
    private lateinit var polymarketWsUrl: String

    // 存储账户 ID 和对应的 WebSocket 连接
    private val accountConnections = ConcurrentHashMap<Long, PolymarketWebSocketClient>()

    // 存储账户 ID 和对应的推送回调：accountId -> Set<(OrderPushMessage) -> Unit>
    private val accountCallbacks = ConcurrentHashMap<Long, MutableSet<(OrderPushMessage) -> Unit>>()

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * 初始化服务
     * 为所有有 API Key 的账户建立连接
     */
    @PostConstruct
    fun init() {
        logger.info("订单推送服务已初始化")
        scope.launch {
            connectAllAccounts()
        }
    }

    /**
     * 清理资源
     */
    @PreDestroy
    fun destroy() {
        logger.info("停止订单推送服务")
        accountConnections.values.forEach { client ->
            try {
                if (client.isConnected()) {
                    client.closeConnection()
                }
            } catch (e: Exception) {
                logger.error("关闭账户连接失败: ${e.message}", e)
            }
        }
        accountConnections.clear()
        accountCallbacks.clear()
        scope.cancel()
    }

    /**
     * 为所有有 API Key 且启用的账户建立连接
     */
    private suspend fun connectAllAccounts() {
        val accounts = accountRepository.findAll()
        accounts.forEach { account ->
            if (hasApiCredentials(account) && account.isEnabled) {
                connectAccount(account)
            }
        }
    }

    /**
     * 订阅所有启用的账户
     */
    fun subscribeAllEnabled(callback: (OrderPushMessage) -> Unit) {
        logger.info("订阅所有启用账户的订单推送")
        val accounts = accountRepository.findAll()
        accounts.forEach { account ->
            if (hasApiCredentials(account) && account.isEnabled) {
                val accountId = account.id!!
                accountCallbacks.getOrPut(accountId) { mutableSetOf() }.add(callback)

                // 如果账户连接不存在，建立连接
                if (!accountConnections.containsKey(accountId)) {
                    connectAccount(account)
                }
            }
        }
    }

    /**
     * 取消订阅所有账户
     */
    fun unsubscribeAll(callback: (OrderPushMessage) -> Unit) {
        logger.info("取消订阅所有账户的订单推送")
        accountCallbacks.values.forEach { callbacks ->
            callbacks.remove(callback)
        }
    }

    /**
     * 重新订阅所有启用的账户（用于账户状态变更时调用）
     */
    fun refreshSubscriptions() {
        logger.info("刷新所有账户的订阅状态")
        val accounts = accountRepository.findAll()
        val enabledAccountIds = accounts
            .filter { hasApiCredentials(it) && it.isEnabled }
            .map { it.id!! }
            .toSet()

        // 断开已禁用或删除的账户连接
        accountConnections.keys.forEach { accountId ->
            if (!enabledAccountIds.contains(accountId)) {
                disconnectAccount(accountId)
            }
        }

        // 为启用的账户建立连接（如果还没有）
        accounts.forEach { account ->
            if (hasApiCredentials(account) && account.isEnabled) {
                val accountId = account.id!!
                if (!accountConnections.containsKey(accountId)) {
                    connectAccount(account)
                }
            }
        }
    }

    /**
     * 检查账户是否有 API 凭证
     */
    private fun hasApiCredentials(account: Account): Boolean {
        return account.apiKey != null &&
                account.apiSecret != null &&
                account.apiPassphrase != null &&
                account.apiKey!!.isNotBlank() &&
                account.apiSecret!!.isNotBlank() &&
                account.apiPassphrase!!.isNotBlank()
    }

    /**
     * 为指定账户建立 User Channel 连接
     */
    fun connectAccount(account: Account) {
        if (!hasApiCredentials(account)) {
            logger.warn("账户 ${account.id} 没有 API 凭证，无法建立连接")
            return
        }

        if (!account.isEnabled) {
            logger.debug("账户 ${account.id} 未启用，跳过连接")
            return
        }

        if (accountConnections.containsKey(account.id)) {
            logger.debug("账户 ${account.id} 已存在连接，跳过")
            return
        }

        scope.launch {
            try {
                // 连接到 Polymarket RTDS User Channel
                // 根据官方文档：https://docs.polymarket.com/quickstart/websocket/WSS-Quickstart
                // URL 格式为: wss://ws-subscriptions-clob.polymarket.com/ws/user
                val wsUrl = "$polymarketWsUrl/ws/user"

                // 创建客户端，在连接建立后立即发送订阅消息
                val client = PolymarketWebSocketClient(
                    url = wsUrl,
                    sessionId = "account-${account.id}",
                    onMessage = { message -> handleMessage(account, message) },
                    onOpen = {
                        // 首次连接建立后立即发送订阅消息
                        val currentClient = accountConnections[account.id!!]
                        if (currentClient != null) {
                            try {
                                sendSubscribeMessage(currentClient, account)
                                logger.info("已为账户 ${account.id} (${account.accountName ?: account.walletAddress}) 建立 User Channel 连接并发送订阅消息")
                            } catch (e: Exception) {
                                logger.error("发送订阅消息失败: account=${account.id}, ${e.message}", e)
                                // 如果订阅失败，关闭连接（会触发重连）
                                currentClient.closeConnection()
                                accountConnections.remove(account.id)
                            }
                        } else {
                            logger.warn("账户 ${account.id} 的连接不存在，无法发送订阅消息")
                        }
                    },
                    onReconnect = {
                        // 重连后重新发送订阅消息
                        val currentClient = accountConnections[account.id!!]
                        if (currentClient != null) {
                            try {
                                sendSubscribeMessage(currentClient, account)
                                logger.info("账户 ${account.id} 重连成功，已重新发送订阅消息")
                            } catch (e: Exception) {
                                logger.error("重连后发送订阅消息失败: account=${account.id}, ${e.message}", e)
                            }
                        }
                    }
                )

                accountConnections[account.id!!] = client
                client.connect()
            } catch (e: Exception) {
                logger.error("为账户 ${account.id} 建立连接失败: ${e.message}", e)
                accountConnections.remove(account.id)
            }
        }
    }

    /**
     * 发送订阅消息到 Polymarket User Channel
     * 根据 https://docs.polymarket.com/developers/CLOB/websocket/user-channel
     * 参考 clob-client/examples/socketConnection.ts
     *
     * 订阅消息格式（与 clob-client 保持一致）：
     * {
     *   "auth": { "apiKey": "...", "secret": "...", "passphrase": "..." },
     *   "type": "user",
     *   "markets": [],  // 空数组表示订阅所有市场，也可以指定 condition IDs
     *   "assets_ids": [],
     *   "initial_dump": true
     * }
     */
    private fun sendSubscribeMessage(client: PolymarketWebSocketClient, account: Account) {
        try {
            val subscribeMessage = mapOf(
                "auth" to mapOf(
                    "apiKey" to account.apiKey,
                    "secret" to account.apiSecret,
                    "passphrase" to account.apiPassphrase
                ),
                "type" to "user",
                "markets" to emptyList<String>(),  // 空数组表示订阅所有市场
                "assets_ids" to emptyList<String>(),
                "initial_dump" to true
            )

            val json = objectMapper.writeValueAsString(subscribeMessage)
            client.sendMessage(json)
            logger.info("已发送 User Channel 订阅消息: account=${account.id}, apiKey=${account.apiKey?.take(10)}...")
        } catch (e: Exception) {
            logger.error("发送订阅消息失败: account=${account.id}, ${e.message}", e)
        }
    }

    /**
     * 处理收到的消息
     */
    private fun handleMessage(account: Account, message: String) {
        try {
            // 处理心跳响应（PONG），直接返回
            if (message.trim() == "PONG" || message.trim() == "pong") {
                logger.debug("收到 PONG 响应: account=${account.id}")
                return
            }

            // 尝试解析 JSON 消息
            val messageMap = objectMapper.readValue(message, Map::class.java) as Map<*, *>
            val eventType = messageMap["event_type"] as? String

            // 只处理 order 类型的消息
            if (eventType == "order") {
                val orderMessage = objectMapper.readValue(message, OrderMessageDto::class.java)

                // 异步获取订单详情
                scope.launch {
                    val orderDetail = fetchOrderDetail(account, orderMessage.id, orderMessage.market)

                    val pushMessage = OrderPushMessage(
                        accountId = account.id!!,
                        accountName = account.accountName ?: account.walletAddress,
                        order = orderMessage,
                        orderDetail = orderDetail
                    )

                    // 推送给所有订阅者
                    accountCallbacks[account.id]?.forEach { callback ->
                        try {
                            callback(pushMessage)
                        } catch (e: Exception) {
                            logger.error("推送订单消息失败: account=${account.id}, ${e.message}", e)
                        }
                    }
                }
            } else {
                // 记录其他类型的消息（用于调试）
                logger.debug("收到非订单消息: account=${account.id}, eventType=$eventType")
            }
        } catch (e: Exception) {
            // 如果解析失败，可能是非 JSON 消息（如 PONG），记录为 debug 级别
            if (message.trim() == "PONG" || message.trim() == "pong") {
                logger.debug("收到 PONG 响应: account=${account.id}")
            } else {
                logger.error("处理订单消息失败: account=${account.id}, message=${message.take(100)}, ${e.message}", e)
            }
        }
    }

    /**
     * 获取订单详情
     * 通过 PolymarketClobService 获取订单详情
     */
    private suspend fun fetchOrderDetail(
        account: Account,
        orderId: String,
        conditionId: String? = null
    ): OrderDetailDto? {
        return try {
            // 检查账户是否有 API 凭证
            if (account.apiKey == null || account.apiSecret == null || account.apiPassphrase == null) {
                logger.debug("账户 ${account.id} 未配置 API 凭证，无法获取订单详情")
                return null
            }
            
            // 通过 PolymarketClobService 获取订单详情（需要 L2 认证）
            val result = clobService.getOrder(
                orderId = orderId,
                apiKey = account.apiKey!!,
                apiSecret = account.apiSecret!!,
                apiPassphrase = account.apiPassphrase!!,
                walletAddress = account.walletAddress
            )
            
            result.fold(
                onSuccess = { openOrder ->
                    // 获取市场信息（通过 Gamma API）
                    val marketInfo = fetchMarketInfo(conditionId ?: openOrder.market)

                    // 转换为 DTO
                    // 注意：createdAt 是 unix timestamp (Long)，需要转换为字符串
                    OrderDetailDto(
                        id = openOrder.id,
                        market = openOrder.market,
                        side = openOrder.side,
                        price = openOrder.price,
                        size = openOrder.originalSize,  // 使用 original_size
                        filled = openOrder.sizeMatched,  // 使用 size_matched
                        status = openOrder.status,
                        createdAt = openOrder.createdAt.toString(),  // unix timestamp 转换为字符串
                        marketName = marketInfo?.question,
                        marketSlug = marketInfo?.slug,
                        marketIcon = marketInfo?.icon
                    )
                },
                onFailure = { e ->
                    logger.warn("获取订单详情失败: account=${account.id}, orderId=$orderId, ${e.message}")
                    null
                }
            )
        } catch (e: Exception) {
            logger.error("获取订单详情异常: account=${account.id}, orderId=$orderId, ${e.message}", e)
            null
        }
    }

    /**
     * 获取市场信息（通过 Gamma API）
     * 文档: https://docs.polymarket.com/api-reference/markets/list-markets
     *
     * 使用 /markets 接口，通过 condition_ids 查询参数获取市场信息
     * 订单返回的 market 字段是 16 进制的 condition ID（如 "0x..."）
     */
    private suspend fun fetchMarketInfo(conditionId: String): com.wrbug.polymarketbot.api.MarketResponse? {
        return try {
            // 创建 Gamma API 客户端（公开 API，不需要认证）
            val gammaApi = retrofitFactory.createGammaApi()

            // 调用 Gamma API 获取市场信息
            // 使用 /markets 接口，通过 condition_ids 查询参数
            val response = gammaApi.listMarkets(
                conditionIds = listOf(conditionId),
                includeTag = null
            )
            if (response.isSuccessful && response.body() != null) {
                val markets = response.body()!!
                if (markets.isNotEmpty()) {
                    val market = markets.first()
                    logger.debug("获取市场信息成功: conditionId=$conditionId, question=${market.question}")
                    return market
                } else {
                    logger.debug("未找到市场信息: conditionId=$conditionId")
                    return null
                }
            } else {
                logger.debug("获取市场信息失败: conditionId=$conditionId, code=${response.code()}, message=${response.message()}")
                null
            }
        } catch (e: Exception) {
            logger.debug("获取市场信息异常: conditionId=$conditionId, ${e.message}")
            null
        }
    }

    /**
     * 订阅账户的订单推送（保留用于向后兼容）
     */
    fun subscribe(accountId: Long, callback: (OrderPushMessage) -> Unit) {
        logger.info("订阅账户订单推送: $accountId")
        accountCallbacks.getOrPut(accountId) { mutableSetOf() }.add(callback)

        // 如果账户连接不存在，尝试建立连接
        if (!accountConnections.containsKey(accountId)) {
            val account = accountRepository.findById(accountId).orElse(null)
            if (account != null && hasApiCredentials(account) && account.isEnabled) {
                connectAccount(account)
            }
        }
    }

    /**
     * 取消订阅账户的订单推送（保留用于向后兼容）
     */
    fun unsubscribe(accountId: Long, callback: (OrderPushMessage) -> Unit) {
        logger.info("取消订阅账户订单推送: $accountId")
        accountCallbacks[accountId]?.remove(callback)

        // 如果没有订阅者了，可以考虑关闭连接（但暂时保持连接，以便后续订阅）
    }

    /**
     * 断开指定账户的连接
     */
    fun disconnectAccount(accountId: Long) {
        logger.info("断开账户连接: $accountId")
        val client = accountConnections.remove(accountId)
        client?.let {
            try {
                if (it.isConnected()) {
                    it.closeConnection()
                }
            } catch (e: Exception) {
                logger.error("关闭账户连接失败: $accountId, ${e.message}", e)
            }
        }
        accountCallbacks.remove(accountId)
    }
}

