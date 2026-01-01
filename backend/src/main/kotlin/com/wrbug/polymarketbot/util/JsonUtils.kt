package com.wrbug.polymarketbot.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.springframework.stereotype.Component

/**
 * JSON 工具类
 * 用于解析 JSON 字符串
 */
@Component
class JsonUtils(
    private val gson: Gson
) {
    
    /**
     * 解析 JSON 字符串数组
     * @param jsonString JSON 字符串，如 "[\"Yes\", \"No\"]"
     * @return 字符串列表，如果解析失败返回空列表
     */
    fun parseStringArray(jsonString: String?): List<String> {
        if (jsonString.isNullOrBlank()) {
            return emptyList()
        }
        
        return try {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(jsonString, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

