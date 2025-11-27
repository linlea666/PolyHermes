package com.wrbug.polymarketbot.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Polymarket Gamma API 接口定义
 * 用于查询市场信息
 * Base URL: https://gamma-api.polymarket.com
 * 文档: https://docs.polymarket.com/api-reference/markets/list-markets
 */
interface PolymarketGammaApi {

    /**
     * 根据 condition ID 列表获取市场信息
     * 文档: https://docs.polymarket.com/api-reference/markets/list-markets
     * @param conditionIds condition ID 数组（16 进制字符串，如 "0x..."）
     * @param includeTag 是否包含标签信息
     * @return 市场信息数组
     */
    @GET("/markets")
    suspend fun listMarkets(
        @Query("condition_ids") conditionIds: List<String>? = null,
        @Query("include_tag") includeTag: Boolean? = null
    ): Response<List<MarketResponse>>
}

/**
 * 市场响应（根据 Gamma API 文档）
 */
data class MarketResponse(
    val id: String? = null,
    val question: String? = null,  // 市场名称
    val conditionId: String? = null,
    val slug: String? = null,
    val icon: String? = null,
    val image: String? = null,
    val description: String? = null,
    val category: String? = null,
    val active: Boolean? = null,
    val closed: Boolean? = null,
    val archived: Boolean? = null,
    val volume: String? = null,
    val liquidity: String? = null,
    val endDate: String? = null,
    val startDate: String? = null,
    val outcomes: String? = null,
    val outcomePrices: String? = null,
    val volumeNum: Double? = null,
    val liquidityNum: Double? = null,
    val lastTradePrice: Double? = null,
    val bestBid: Double? = null,
    val bestAsk: Double? = null
)

