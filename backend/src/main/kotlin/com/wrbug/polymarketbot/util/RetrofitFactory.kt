package com.wrbug.polymarketbot.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.wrbug.polymarketbot.api.EthereumRpcApi
import com.wrbug.polymarketbot.api.PolymarketClobApi
import com.wrbug.polymarketbot.api.PolymarketGammaApi
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

/**
 * Retrofit 客户端工厂
 * 用于创建带认证的 Polymarket CLOB API 客户端和 Ethereum RPC API 客户端
 */
@Component
class RetrofitFactory(
    @Value("\${polymarket.clob.base-url}")
    private val clobBaseUrl: String,
    @Value("\${polymarket.gamma.base-url}")
    private val gammaBaseUrl: String
) {
    
    /**
     * 创建带认证的 Polymarket CLOB API 客户端
     * @param apiKey API Key
     * @param apiSecret API Secret
     * @param apiPassphrase API Passphrase
     * @param walletAddress 钱包地址（用于 POLY_ADDRESS 请求头）
     * @return PolymarketClobApi 客户端
     */
    fun createClobApi(
        apiKey: String,
        apiSecret: String,
        apiPassphrase: String,
        walletAddress: String
    ): PolymarketClobApi {
        val authInterceptor = PolymarketAuthInterceptor(apiKey, apiSecret, apiPassphrase, walletAddress)
        
        // 添加响应日志拦截器，用于调试 JSON 解析错误
        val responseLoggingInterceptor = ResponseLoggingInterceptor()
        
        val okHttpClient = createClient()
            .addInterceptor(authInterceptor)
            .addInterceptor(responseLoggingInterceptor)
            .build()
        
        // 创建 lenient 模式的 Gson，允许解析格式不严格的 JSON
        val gson = GsonBuilder()
            .setLenient()
            .create()
        
        return Retrofit.Builder()
            .baseUrl(clobBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(PolymarketClobApi::class.java)
    }
    
    /**
     * 创建不带认证的 Polymarket CLOB API 客户端
     * 用于不需要认证的查询接口
     * @return PolymarketClobApi 客户端
     */
    fun createClobApiWithoutAuth(): PolymarketClobApi {
        // 添加响应日志拦截器，用于调试 JSON 解析错误
        val responseLoggingInterceptor = ResponseLoggingInterceptor()
        
        val okHttpClient = createClient()
            .addInterceptor(responseLoggingInterceptor)
            .build()
        
        // 创建 lenient 模式的 Gson，允许解析格式不严格的 JSON
        val gson = GsonBuilder()
            .setLenient()
            .create()
        
        return Retrofit.Builder()
            .baseUrl(clobBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(PolymarketClobApi::class.java)
    }
    
    /**
     * 创建 Ethereum RPC API 客户端
     * @param rpcUrl RPC 节点 URL
     * @return EthereumRpcApi 客户端
     */
    fun createEthereumRpcApi(rpcUrl: String): EthereumRpcApi {
        val okHttpClient = createClient().build()
        
        // 创建 lenient 模式的 Gson
        val gson = GsonBuilder()
            .setLenient()
            .create()
        
        return Retrofit.Builder()
            .baseUrl(rpcUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(EthereumRpcApi::class.java)
    }
    
    /**
     * 创建 Polymarket Gamma API 客户端
     * Gamma API 是公开 API，不需要认证
     * @return PolymarketGammaApi 客户端
     */
    fun createGammaApi(): PolymarketGammaApi {
        val baseUrl = if (gammaBaseUrl.endsWith("/")) {
            gammaBaseUrl.dropLast(1)
        } else {
            gammaBaseUrl
        }
        val okHttpClient = createClient().build()
        
        // 创建 lenient 模式的 Gson
        val gson = GsonBuilder()
            .setLenient()
            .create()
        
        return Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(PolymarketGammaApi::class.java)
    }
}

/**
 * 响应日志拦截器
 * 用于记录 API 响应的原始内容，帮助调试 JSON 解析错误
 */
class ResponseLoggingInterceptor : Interceptor {
    private val logger = LoggerFactory.getLogger(ResponseLoggingInterceptor::class.java)
    
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        
        // 只在响应不成功或可能有问题时记录响应体
        if (response.isSuccessful) {
            try {
                // 使用 peekBody 读取响应体，避免消费响应流
                // 只读取前 2KB，避免内存问题
                val responseBody = response.peekBody(2048)
                val responseBodyString = responseBody.string()
                
                // 检查是否是有效的 JSON
                val isJson = responseBodyString.trim().startsWith("{") || 
                            responseBodyString.trim().startsWith("[")
                
                if (!isJson || !response.isSuccessful) {
                    logger.warn(
                        "API 响应异常: method=${request.method}, url=${request.url}, " +
                        "code=${response.code}, isJson=$isJson, " +
                        "responseBody=${responseBodyString.take(500)}"
                    )
                }
            } catch (e: Exception) {
                logger.debug("读取响应体失败: ${e.message}")
            }
        }
        
        return response
    }
}

