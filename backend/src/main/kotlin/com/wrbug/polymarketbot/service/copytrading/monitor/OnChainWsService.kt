package com.wrbug.polymarketbot.service.copytrading.monitor

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.wrbug.polymarketbot.api.*
import com.wrbug.polymarketbot.entity.Leader
import com.wrbug.polymarketbot.repository.LeaderRepository
import com.wrbug.polymarketbot.service.copytrading.statistics.CopyOrderTrackingService
import com.wrbug.polymarketbot.service.system.RpcNodeService
import com.wrbug.polymarketbot.util.RetrofitFactory
import com.wrbug.polymarketbot.util.createClient
import com.wrbug.polymarketbot.util.getProxyConfig
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * 链上 WebSocket 监听服务
 * 通过 Polygon RPC 的 eth_subscribe 实时监听链上交易
 */
@Service
class OnChainWsService(
    private val rpcNodeService: RpcNodeService,
    private val retrofitFactory: RetrofitFactory,
    private val copyOrderTrackingService: CopyOrderTrackingService,
    private val leaderRepository: LeaderRepository
) {
    
    private val logger = LoggerFactory.getLogger(OnChainWsService::class.java)
    
    // Gson 实例，用于解析 JSON
    private val gson = Gson()
    
    @Value("\${copy.trading.onchain.ws.reconnect.delay:3000}")
    private var reconnectDelay: Long = 3000  // 重连延迟（毫秒），默认3秒
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 存储需要监听的Leader：leaderId -> Leader
    private val monitoredLeaders = ConcurrentHashMap<Long, Leader>()
    
    // 存储每个 Leader 的订阅 ID：leaderId -> List<subscriptionId>
    // 每个 Leader 有 6 个订阅：USDC from/to, ERC1155 TransferSingle from/to, ERC1155 TransferBatch from/to
    private val leaderSubscriptions = ConcurrentHashMap<Long, MutableList<String>>()
    
    // 存储请求 ID 到 Leader ID 的映射：requestId -> leaderId
    // 用于在收到订阅响应时，将 subscription ID 关联到对应的 Leader
    private val requestIdToLeaderId = ConcurrentHashMap<Int, Long>()
    
    // WebSocket 连接
    private var webSocket: WebSocket? = null
    @Volatile
    private var isConnected = false
    
    // 订阅ID计数器（用于请求 ID）
    private var requestIdCounter = 0
    
    // 合约地址
    companion object {
        private const val USDC_CONTRACT = "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174"
        private const val ERC1155_CONTRACT = "0x4d97dcd97ec945f40cf65f87097ace5ea0476045"
        private const val ERC20_TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
        private const val ERC1155_TRANSFER_SINGLE_TOPIC = "0xc3d58168c5ae7397731d063d5bbf3d657854427343f4c083240f7aacaa2d0f62"
        private const val ERC1155_TRANSFER_BATCH_TOPIC = "0x4a39dc06d4c0dbc64b70af90fd698a233a518aa5d07e595d983b8c0526c8f7fb"
    }
    
    // 连接任务（确保只有一个连接任务在运行）
    private var connectionJob: Job? = null
    
    /**
     * 启动链上 WebSocket 监听
     * 只创建一个 WebSocket 连接，为所有 Leader 订阅
     */
    fun start(leaders: List<Leader>) {
        // 如果没有 Leader，不启动连接
        if (leaders.isEmpty()) {
            logger.info("没有需要监听的 Leader，不启动链上 WebSocket 连接")
            stop()
            return
        }
        
        // 如果连接任务已经在运行，先停止旧任务
        if (connectionJob != null && connectionJob!!.isActive) {
            logger.info("停止旧的连接任务，准备重新启动")
            connectionJob?.cancel()
            connectionJob = null
            // 关闭旧连接
            webSocket?.close(1000, "重新启动")
            webSocket = null
            isConnected = false
        }
        
        // 更新 Leader 列表
        monitoredLeaders.clear()
        leaders.forEach { leader ->
            addLeader(leader)
        }
        
        // 启动连接任务（只创建一个）
        connectionJob = scope.launch {
            startConnection()
        }
    }
    
    /**
     * 添加Leader监听
     * 如果 Leader 已经在监听列表中，不重复添加
     * 如果已连接，立即订阅
     */
    fun addLeader(leader: Leader) {
        if (leader.id == null) {
            logger.warn("Leader ID为空，跳过: ${leader.leaderAddress}")
            return
        }
        
        val leaderId = leader.id!!
        
        // 如果已经在监听列表中，不重复添加
        if (monitoredLeaders.containsKey(leaderId)) {
            logger.debug("Leader 已在监听列表中: ${leader.leaderName} (${leader.leaderAddress})")
            return
        }
        
        monitoredLeaders[leaderId] = leader
        logger.info("添加 Leader 监听: ${leader.leaderName} (${leader.leaderAddress})")
        
        // 如果已连接，立即订阅
        if (isConnected && webSocket != null) {
            scope.launch {
                subscribeLeader(leader)
            }
        } else {
            // 如果未连接，启动连接（如果连接任务未运行）
            if (connectionJob == null || !connectionJob!!.isActive) {
                connectionJob = scope.launch {
                    startConnection()
                }
            }
        }
    }
    
    /**
     * 移除Leader监听
     * 通过 eth_unsubscribe 取消该 Leader 的所有订阅
     * 如果没有 Leader 了，关闭 WebSocket 连接
     */
    fun removeLeader(leaderId: Long) {
        val leader = monitoredLeaders.remove(leaderId)
        if (leader != null) {
            logger.info("移除 Leader 监听: ${leader.leaderName} (${leader.leaderAddress})")
        }
        
        // 取消该 Leader 的所有订阅
        val subscriptions = leaderSubscriptions.remove(leaderId)
        if (subscriptions != null && subscriptions.isNotEmpty() && isConnected && webSocket != null) {
            logger.info("取消 Leader ${leader?.leaderName} 的 ${subscriptions.size} 个订阅")
            subscriptions.forEach { subscriptionId ->
                unsubscribe(subscriptionId)
            }
        }
        
        // 如果没有 Leader 了，关闭连接
        if (monitoredLeaders.isEmpty()) {
            logger.info("没有需要监听的 Leader，关闭链上 WebSocket 连接")
            stop()
        }
    }
    
    /**
     * 停止所有监听
     */
    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        
        // 取消所有订阅
        if (isConnected && webSocket != null) {
            leaderSubscriptions.values.flatten().forEach { subscriptionId ->
                unsubscribe(subscriptionId)
            }
        }
        
        webSocket?.close(1000, "正常关闭")
        webSocket = null
        isConnected = false
        monitoredLeaders.clear()
        leaderSubscriptions.clear()
        requestIdToLeaderId.clear()
    }
    
    /**
     * 启动连接（带重连机制）
     * 只创建一个 WebSocket 连接
     */
    private suspend fun startConnection() {
        while (scope.isActive) {
            try {
                // 检查是否有需要监听的 Leader
                if (monitoredLeaders.isEmpty()) {
                    logger.info("没有需要监听的 Leader，停止连接")
                    // 确保关闭连接
                    webSocket?.close(1000, "没有 Leader")
                    webSocket = null
                    isConnected = false
                    break
                }
                
                // 如果已经连接，不需要重新连接
                if (isConnected && webSocket != null) {
                    // 等待连接断开
                    waitForDisconnect()
                    // 连接断开后继续重连循环
                    continue
                }
                
                // 从后台配置获取 WS RPC URL
                val wsUrl = rpcNodeService.getWsUrl()
                val httpUrl = rpcNodeService.getHttpUrl()
                
                logger.info("连接链上 WebSocket: $wsUrl (监听 ${monitoredLeaders.size} 个 Leader)")
                
                // 创建 HTTP 客户端（用于 RPC 调用）
                val httpClient = createHttpClient()
                
                // 创建 RPC API 客户端
                val rpcApi = retrofitFactory.createEthereumRpcApi(httpUrl)
                
                // 连接 WebSocket（只创建一个连接，会先关闭旧连接）
                connectWebSocket(wsUrl, httpClient, rpcApi)
                
                // 等待连接建立（最多等待 15 秒）
                // 注意：onOpen 回调是异步的，需要等待一段时间
                var waitCount = 0
                val maxWait = 15  // 最多等待 15 秒
                while (!isConnected && waitCount < maxWait && scope.isActive) {
                    delay(1000)
                    waitCount++
                    // 每 3 秒打印一次日志，方便调试
                    if (waitCount % 3 == 0) {
                        logger.debug("等待 WebSocket 连接建立... (${waitCount}/${maxWait}秒)")
                    }
                }
                
                // 检查连接状态（同时检查 isConnected 和 webSocket 状态）
                val actuallyConnected = isConnected && webSocket != null
                
                // 如果连接失败，等待重连延迟后继续
                if (!actuallyConnected) {
                    logger.warn("WebSocket 连接超时或失败: isConnected=$isConnected, webSocket=${webSocket != null}, 等待重连")
                    delay(reconnectDelay)
                    continue
                }
                
                logger.info("WebSocket 连接已建立，开始监听")
                
                // 连接成功后持续监听
                waitForDisconnect()
                
                // 连接断开后，如果没有 Leader 了，不再重连
                if (monitoredLeaders.isEmpty()) {
                    logger.info("没有需要监听的 Leader，停止重连")
                    break
                }
                
                // 连接断开后，等待一下再重连（避免立即重连）
                logger.info("WebSocket 连接断开，等待 ${reconnectDelay}ms 后重连")
                delay(reconnectDelay)
            } catch (e: Exception) {
                // 如果没有 Leader 了，不再重连
                if (monitoredLeaders.isEmpty()) {
                    logger.info("没有需要监听的 Leader，停止重连")
                    // 确保关闭连接
                    webSocket?.close(1000, "没有 Leader")
                    webSocket = null
                    isConnected = false
                    break
                }
                logger.warn("链上 WebSocket 连接失败，等待重连: ${e.message}")
                // 确保关闭旧连接
                webSocket?.close(1000, "重连前关闭")
                webSocket = null
                isConnected = false
                delay(reconnectDelay)
            }
        }
    }
    
    /**
     * 创建 HTTP 客户端
     */
    private fun createHttpClient(): OkHttpClient {
        val proxy = getProxyConfig()
        val builder = createClient()
        
        if (proxy != null) {
            builder.proxy(proxy)
        }
        
        return builder.build()
    }
    
    /**
     * 连接 WebSocket
     * 确保只创建一个连接，创建新连接前先关闭旧连接
     */
    private fun connectWebSocket(wsUrl: String, httpClient: OkHttpClient, rpcApi: EthereumRpcApi) {
        // 先关闭旧连接（如果存在）
        val oldWebSocket = webSocket
        if (oldWebSocket != null) {
            try {
                oldWebSocket.close(1000, "重新连接")
            } catch (e: Exception) {
                logger.debug("关闭旧 WebSocket 连接时出错: ${e.message}")
            }
        }
        webSocket = null
        isConnected = false
        
        val request = Request.Builder()
            .url(wsUrl)
            .build()
        
        // 创建新连接（只创建一个）
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                isConnected = true
                logger.info("链上 WebSocket 连接成功")
                
                // 订阅所有 Leader
                scope.launch {
                    monitoredLeaders.values.forEach { leader ->
                        subscribeLeader(leader)
                    }
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handleMessage(text, httpClient, rpcApi)
                }
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                scope.launch {
                    handleMessage(bytes.utf8(), httpClient, rpcApi)
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                logger.warn("链上 WebSocket 连接关闭: code=$code, reason=$reason")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                logger.warn("链上 WebSocket 连接已关闭: code=$code, reason=$reason")
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                logger.error("链上 WebSocket 连接失败: ${t.message}", t)
                isConnected = false
                // 注意：这里不直接重连，由 startConnection 循环处理重连逻辑
            }
        })
    }
    
    /**
     * 等待连接断开
     */
    private suspend fun waitForDisconnect() {
        while (isConnected && scope.isActive) {
            delay(1000)
        }
    }
    
    /**
     * 订阅 Leader 钱包地址
     * 每个 Leader 有 6 个订阅，保存订阅 ID 以便后续取消
     */
    private suspend fun subscribeLeader(leader: Leader) {
        if (webSocket == null || !isConnected || leader.id == null) {
            return
        }
        
        val walletAddress = leader.leaderAddress.lowercase()
        val walletTopic = addressToTopic32(walletAddress)
        val leaderId = leader.id!!
        
        // 初始化该 Leader 的订阅列表
        if (!leaderSubscriptions.containsKey(leaderId)) {
            leaderSubscriptions[leaderId] = mutableListOf()
        }
        
        try {
            // 订阅 USDC Transfer (from wallet)
            subscribeLogs(USDC_CONTRACT, listOf(ERC20_TRANSFER_TOPIC, walletTopic), leaderId)
            
            // 订阅 USDC Transfer (to wallet)
            subscribeLogs(USDC_CONTRACT, listOf(ERC20_TRANSFER_TOPIC, null, walletTopic), leaderId)
            
            // 订阅 ERC1155 TransferSingle (from wallet)
            subscribeLogs(ERC1155_CONTRACT, listOf(ERC1155_TRANSFER_SINGLE_TOPIC, null, walletTopic), leaderId)
            
            // 订阅 ERC1155 TransferSingle (to wallet)
            subscribeLogs(ERC1155_CONTRACT, listOf(ERC1155_TRANSFER_SINGLE_TOPIC, null, null, walletTopic), leaderId)
            
            // 订阅 ERC1155 TransferBatch (from wallet)
            subscribeLogs(ERC1155_CONTRACT, listOf(ERC1155_TRANSFER_BATCH_TOPIC, null, walletTopic), leaderId)
            
            // 订阅 ERC1155 TransferBatch (to wallet)
            subscribeLogs(ERC1155_CONTRACT, listOf(ERC1155_TRANSFER_BATCH_TOPIC, null, null, walletTopic), leaderId)
            
            logger.debug("已订阅 Leader 钱包地址: ${leader.leaderName} (${walletAddress})")
        } catch (e: Exception) {
            logger.error("订阅 Leader 失败: leaderId=$leaderId, address=$walletAddress", e)
        }
    }
    
    /**
     * 订阅日志
     * @param address 合约地址
     * @param topics 主题列表
     * @param leaderId Leader ID，用于关联订阅响应
     */
    private fun subscribeLogs(address: String, topics: List<String?>, leaderId: Long) {
        val ws = webSocket ?: return
        
        val params = mapOf(
            "address" to address.lowercase(),
            "topics" to topics
        )
        
        val subscribeParams = listOf("logs", params)
        
        val requestId = ++requestIdCounter
        
        // 保存请求 ID 到 Leader ID 的映射
        requestIdToLeaderId[requestId] = leaderId
        
        val request = mapOf(
            "jsonrpc" to "2.0",
            "id" to requestId,
            "method" to "eth_subscribe",
            "params" to subscribeParams
        )
        
        val json = gson.toJson(request)
        ws.send(json)
    }
    
    /**
     * 取消订阅
     * @param subscriptionId 订阅 ID
     */
    private fun unsubscribe(subscriptionId: String) {
        val ws = webSocket ?: return
        
        val request = mapOf(
            "jsonrpc" to "2.0",
            "id" to (++requestIdCounter),
            "method" to "eth_unsubscribe",
            "params" to listOf(subscriptionId)
        )
        
        val json = gson.toJson(request)
        ws.send(json)
        logger.debug("已发送取消订阅请求: subscriptionId=$subscriptionId")
    }
    
    /**
     * 处理 WebSocket 消息
     */
    private suspend fun handleMessage(message: String, httpClient: OkHttpClient, rpcApi: EthereumRpcApi) {
        try {
            // 使用 Gson 解析消息
            val messageJson = gson.fromJson(message, JsonObject::class.java)
            
            // 处理订阅响应（包含 subscription ID）
            val id = messageJson.get("id")
            if (id != null && !id.isJsonNull) {
                val result = messageJson.get("result")
                if (result != null && result.isJsonPrimitive && result.asJsonPrimitive.isString) {
                    // 这是订阅响应，result 是 subscription ID（字符串）
                    val requestId = id.asInt
                    val subscriptionId = result.asString
                    val leaderId = requestIdToLeaderId.remove(requestId)
                    if (leaderId != null) {
                        // 保存订阅 ID 到 Leader
                        leaderSubscriptions.getOrPut(leaderId) { mutableListOf() }.add(subscriptionId)
                        logger.debug("收到订阅响应: leaderId=$leaderId, subscriptionId=$subscriptionId")
                    }
                }
                return
            }
            
            // 处理订阅通知（交易日志）
            val method = messageJson.get("method")?.asString
            if (method != "eth_subscription") {
                return
            }
            
            val params = messageJson.getAsJsonObject("params") ?: return
            // result 是一个对象，包含日志信息
            val result = params.getAsJsonObject("result") ?: return
            
            // 从 result 对象中获取 transactionHash（关键数据）
            val txHash = result.get("transactionHash")?.asString
            if (txHash.isNullOrEmpty()) {
                logger.debug("订阅通知中缺少 transactionHash，跳过处理")
                return
            }
            
            // 处理交易
            processTransaction(txHash, httpClient, rpcApi)
        } catch (e: Exception) {
            logger.error("处理 WebSocket 消息失败: ${e.message}", e)
            logger.debug("消息内容: $message", e)
        }
    }
    
    /**
     * 处理交易
     */
    private suspend fun processTransaction(txHash: String, httpClient: OkHttpClient, rpcApi: EthereumRpcApi) {
        try {
            // 获取交易 receipt
            val receiptRequest = JsonRpcRequest(
                method = "eth_getTransactionReceipt",
                params = listOf(txHash)
            )
            
            val receiptResponse = rpcApi.call(receiptRequest)
            if (!receiptResponse.isSuccessful || receiptResponse.body() == null) {
                return
            }
            
            val receiptRpcResponse = receiptResponse.body()!!
            if (receiptRpcResponse.error != null || receiptRpcResponse.result == null) {
                return
            }
            
            // 使用 Gson 解析 receipt JSON（result 是 JsonElement）
            val receiptJson = receiptRpcResponse.result.asJsonObject
            
            // 获取区块号和时间戳
            val blockNumber = receiptJson.get("blockNumber")?.asString
            val blockTimestamp = if (blockNumber != null) {
                getBlockTimestamp(blockNumber, rpcApi)
            } else {
                null
            }
            
            // 解析 receipt 中的 Transfer 日志
            val logs = receiptJson.getAsJsonArray("logs") ?: return
            val (erc20Transfers, erc1155Transfers) = parseReceiptTransfers(logs)
            
            // 为每个 Leader 处理交易
            for (leader in monitoredLeaders.values) {
                val trade = parseTradeFromTransfers(
                    txHash = txHash,
                    timestamp = blockTimestamp,
                    walletAddress = leader.leaderAddress,
                    erc20Transfers = erc20Transfers,
                    erc1155Transfers = erc1155Transfers
                )
                
                if (trade != null) {
                    // 调用 processTrade 处理交易（元数据已在 parseTradeFromTransfers 中补齐）
                    copyOrderTrackingService.processTrade(
                        leaderId = leader.id!!,
                        trade = trade,
                        source = "onchain-ws"
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("处理交易失败: txHash=$txHash, ${e.message}", e)
        }
    }
    
    /**
     * 解析 receipt 中的 Transfer 日志
     */
    private fun parseReceiptTransfers(logs: com.google.gson.JsonArray): Pair<List<Erc20Transfer>, List<Erc1155Transfer>> {
        val erc20 = mutableListOf<Erc20Transfer>()
        val erc1155 = mutableListOf<Erc1155Transfer>()
        
        for (logElement in logs) {
            val log = logElement.asJsonObject
            val address = log.get("address")?.asString?.lowercase() ?: continue
            val topicsArray = log.getAsJsonArray("topics") ?: continue
            val topics = topicsArray.mapNotNull { it.asString }
            if (topics.isEmpty()) continue
            
            val t0 = topics[0].lowercase()
            val data = log.get("data")?.asString ?: "0x"
            
            // USDC ERC20 Transfer
            if (address == USDC_CONTRACT.lowercase() && t0 == ERC20_TRANSFER_TOPIC && topics.size >= 3) {
                val from = topicToAddress(topics[1])
                val to = topicToAddress(topics[2])
                val value = hexToBigInt(data)
                erc20.add(Erc20Transfer(from, to, value))
                continue
            }
            
            // ERC1155 TransferSingle
            if (t0 == ERC1155_TRANSFER_SINGLE_TOPIC && topics.size >= 4) {
                val from = topicToAddress(topics[2])
                val to = topicToAddress(topics[3])
                val bytes = bytesFromHex(data)
                if (bytes.size >= 64) {
                    val tokenId = sliceBigInt32(bytes, 0)
                    val value = sliceBigInt32(bytes, 32)
                    erc1155.add(Erc1155Transfer(from, to, tokenId, value))
                }
                continue
            }
            
            // ERC1155 TransferBatch
            if (t0 == ERC1155_TRANSFER_BATCH_TOPIC && topics.size >= 4) {
                val from = topicToAddress(topics[2])
                val to = topicToAddress(topics[3])
                val bytes = bytesFromHex(data)
                if (bytes.size < 64) continue
                
                val offIds = sliceBigInt32(bytes, 0).toInt()
                val offVals = sliceBigInt32(bytes, 32).toInt()
                if (offIds + 32 > bytes.size || offVals + 32 > bytes.size) continue
                
                val nIds = sliceBigInt32(bytes, offIds).toInt()
                val nVals = sliceBigInt32(bytes, offVals).toInt()
                if (nIds != nVals) continue
                
                val idsStart = offIds + 32
                val valsStart = offVals + 32
                for (i in 0 until nIds) {
                    val ib = idsStart + i * 32
                    val vb = valsStart + i * 32
                    if (ib + 32 > bytes.size || vb + 32 > bytes.size) break
                    val tokenId = sliceBigInt32(bytes, ib)
                    val value = sliceBigInt32(bytes, vb)
                    erc1155.add(Erc1155Transfer(from, to, tokenId, value))
                }
            }
        }
        
        return Pair(erc20, erc1155)
    }
    
    /**
     * 从 Transfer 日志解析交易信息
     */
    private suspend fun parseTradeFromTransfers(
        txHash: String,
        timestamp: Long?,
        walletAddress: String,
        erc20Transfers: List<Erc20Transfer>,
        erc1155Transfers: List<Erc1155Transfer>
    ): TradeResponse? {
        val wallet = walletAddress.lowercase()
        
        // 计算 USDC 流入和流出
        val usdcOut = erc20Transfers.filter { it.from.lowercase() == wallet }
            .fold(BigInteger.ZERO) { acc, t -> acc + t.value }
        val usdcIn = erc20Transfers.filter { it.to.lowercase() == wallet }
            .fold(BigInteger.ZERO) { acc, t -> acc + t.value }
        
        // 计算 ERC1155 流入和流出（按 tokenId 聚合）
        val inById = mutableMapOf<BigInteger, BigInteger>()
        val outById = mutableMapOf<BigInteger, BigInteger>()
        for (t in erc1155Transfers) {
            if (t.to.lowercase() == wallet) {
                inById[t.tokenId] = (inById[t.tokenId] ?: BigInteger.ZERO) + t.value
            }
            if (t.from.lowercase() == wallet) {
                outById[t.tokenId] = (outById[t.tokenId] ?: BigInteger.ZERO) + t.value
            }
        }
        
        // 找到最大的流入和流出 tokenId
        fun best(map: Map<BigInteger, BigInteger>): Pair<BigInteger?, BigInteger> =
            map.entries.maxByOrNull { it.value }?.let { it.key to it.value } ?: (null to BigInteger.ZERO)
        
        val (bestInId, bestInVal) = best(inById)
        val (bestOutId, bestOutVal) = best(outById)
        
        // 判断交易方向
        var side: String? = null
        var asset: BigInteger? = null
        var sizeRaw = BigInteger.ZERO
        var usdcRaw = BigInteger.ZERO
        
        if (bestInId != null && bestInVal > BigInteger.ZERO && usdcOut > BigInteger.ZERO) {
            // BUY: 收到 token，支付 USDC
            side = "BUY"
            asset = bestInId
            sizeRaw = bestInVal
            usdcRaw = usdcOut
        } else if (bestOutId != null && bestOutVal > BigInteger.ZERO && usdcIn > BigInteger.ZERO) {
            // SELL: 卖出 token，收到 USDC
            side = "SELL"
            asset = bestOutId
            sizeRaw = bestOutVal
            usdcRaw = usdcIn
        } else {
            // 无法判断交易方向
            return null
        }
        
        // 计算价格和数量（USDC 有 6 位小数，shares 也有 6 位小数）
        val usdcSize = usdcRaw.toBigDecimal().divide(BigInteger("1000000").toBigDecimal(), 8, java.math.RoundingMode.DOWN)
        val size = sizeRaw.toBigDecimal().divide(BigInteger("1000000").toBigDecimal(), 8, java.math.RoundingMode.DOWN)
        val price = if (size.signum() > 0) {
            usdcSize.divide(size, 8, java.math.RoundingMode.DOWN)
        } else {
            return null
        }
        
        // 尝试通过 Gamma API 查询市场信息（通过 tokenId）
        val marketInfo = fetchMarketByTokenId(asset.toString())
        
        // 创建 TradeResponse
        return TradeResponse(
            id = txHash,
            market = marketInfo?.conditionId ?: "",
            side = side,
            price = price.toPlainString(),
            size = size.toPlainString(),
            timestamp = (timestamp ?: System.currentTimeMillis() / 1000).toString(),
            user = walletAddress,
            outcomeIndex = marketInfo?.outcomeIndex,
            outcome = marketInfo?.outcome
        )
    }
    
    /**
     * 通过 Gamma API 查询市场信息（通过 tokenId）
     */
    private suspend fun fetchMarketByTokenId(tokenId: String): MarketInfo? {
        return try {
            // 使用 HTTP 请求直接调用 Gamma API（因为 Retrofit 接口可能不支持 clob_token_ids 参数）
            val httpClient = createHttpClient()
            val url = "https://gamma-api.polymarket.com/markets?clob_token_ids=$tokenId"
            
            val request = okhttp3.Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                return null
            }
            
            val responseBody = response.body!!.string()
            // 使用 Gson 解析市场列表
            val marketsType = object : TypeToken<List<JsonObject>>() {}.type
            val markets = gson.fromJson<List<JsonObject>>(responseBody, marketsType)
            
            if (markets.isEmpty()) {
                return null
            }
            
            val market = markets.first()
            
            // 解析 clob_token_ids（可能是 JSON 字符串或数组）
            val clobTokenIdsRaw = market.get("clobTokenIds") ?: market.get("clob_token_ids")
            val clobTokenIds = when {
                clobTokenIdsRaw == null || clobTokenIdsRaw.isJsonNull -> null
                clobTokenIdsRaw.isJsonPrimitive && clobTokenIdsRaw.asJsonPrimitive.isString -> {
                    // 尝试解析 JSON 字符串
                    try {
                        val listType = object : TypeToken<List<String>>() {}.type
                        gson.fromJson<List<String>>(clobTokenIdsRaw.asString, listType)
                    } catch (e: Exception) {
                        null
                    }
                }
                clobTokenIdsRaw.isJsonArray -> {
                    clobTokenIdsRaw.asJsonArray.mapNotNull { it.asString }
                }
                else -> null
            }
            
            // 解析 outcomes（可能是 JSON 字符串或数组）
            val outcomesRaw = market.get("outcomes")
            val outcomes = when {
                outcomesRaw == null || outcomesRaw.isJsonNull -> null
                outcomesRaw.isJsonPrimitive && outcomesRaw.asJsonPrimitive.isString -> {
                    try {
                        val listType = object : TypeToken<List<String>>() {}.type
                        gson.fromJson<List<String>>(outcomesRaw.asString, listType)
                    } catch (e: Exception) {
                        null
                    }
                }
                outcomesRaw.isJsonArray -> {
                    outcomesRaw.asJsonArray.mapNotNull { it.asString }
                }
                else -> null
            }
            
            // 查找 tokenId 在 clobTokenIds 中的索引
            val outcomeIndex = clobTokenIds?.indexOfFirst { 
                it.equals(tokenId, ignoreCase = true)
            }?.takeIf { it >= 0 }
            
            // 获取 outcome 名称
            val outcome = if (outcomeIndex != null && outcomes != null && outcomeIndex < outcomes.size) {
                outcomes[outcomeIndex]
            } else {
                null
            }
            
            val conditionId = market.get("conditionId")?.asString ?: return null
            
            MarketInfo(
                conditionId = conditionId,
                outcomeIndex = outcomeIndex,
                outcome = outcome
            )
        } catch (e: Exception) {
            logger.warn("通过 Gamma API 查询市场信息失败: tokenId=$tokenId, ${e.message}")
            null
        }
    }
    
    /**
     * 市场信息（从 Gamma API 获取）
     */
    private data class MarketInfo(
        val conditionId: String,
        val outcomeIndex: Int?,
        val outcome: String?
    )
    
    /**
     * 获取区块时间戳
     */
    private suspend fun getBlockTimestamp(blockNumber: String, rpcApi: EthereumRpcApi): Long? {
        return try {
            val blockRequest = JsonRpcRequest(
                method = "eth_getBlockByNumber",
                params = listOf(blockNumber, false)
            )
            
            val blockResponse = rpcApi.call(blockRequest)
            if (!blockResponse.isSuccessful || blockResponse.body() == null) {
                return null
            }
            
            val blockRpcResponse = blockResponse.body()!!
            if (blockRpcResponse.error != null || blockRpcResponse.result == null) {
                return null
            }
            
            // 使用 Gson 解析 block JSON（result 是 JsonElement）
            val blockJson = blockRpcResponse.result.asJsonObject
            val timestampHex = blockJson.get("timestamp")?.asString ?: return null
            hexToBigInt(timestampHex).toLong()
        } catch (e: Exception) {
            logger.warn("获取区块时间戳失败: ${e.message}")
            null
        }
    }
    
    // 辅助函数
    private data class Erc20Transfer(val from: String, val to: String, val value: BigInteger)
    private data class Erc1155Transfer(val from: String, val to: String, val tokenId: BigInteger, val value: BigInteger)
    
    private fun topicToAddress(topic: String): String {
        val t = topic.removePrefix("0x").lowercase()
        return "0x" + t.takeLast(40)
    }
    
    private fun hexToBigInt(hex: String): BigInteger {
        val h = hex.removePrefix("0x")
        if (h.isEmpty()) return BigInteger.ZERO
        return BigInteger(h, 16)
    }
    
    private fun bytesFromHex(hex: String): ByteArray {
        val s = hex.removePrefix("0x")
        if (s.isEmpty()) return ByteArray(0)
        val out = ByteArray(s.length / 2)
        var i = 0
        while (i < s.length) {
            out[i / 2] = s.substring(i, i + 2).toInt(16).toByte()
            i += 2
        }
        return out
    }
    
    private fun sliceBigInt32(b: ByteArray, offset: Int): BigInteger {
        val sub = b.copyOfRange(offset, offset + 32)
        return BigInteger(1, sub)
    }
    
    /**
     * 地址转换为 32 字节 topic（前 24 字节为 0，后 8 字节为地址）
     */
    private fun addressToTopic32(address: String): String {
        val addr = address.removePrefix("0x").lowercase()
        return "0x" + "0".repeat(24) + addr
    }
    
    @PreDestroy
    fun destroy() {
        stop()
        scope.cancel()
    }
}

