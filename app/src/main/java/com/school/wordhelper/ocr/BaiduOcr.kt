package com.school.wordhelper.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import com.school.wordhelper.data.httpGet
import com.school.wordhelper.data.httpPostForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder

/** 百度 OCR 带错误码的异常 */
private class BaiduOcrException(val code: String, message: String) : IOException(message)

/**
 * 百度智能云 OCR（高精度含位置版，额度用完自动降级通用版）。
 * 密钥保存在本地 SharedPreferences。
 */
class BaiduOcrClient(private val context: Context) {

    private val prefs = context.getSharedPreferences("word_helper", Context.MODE_PRIVATE)
    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0L

    suspend fun recognize(bitmap: Bitmap): List<OcrWord> = withContext(Dispatchers.IO) {
        val token = accessToken()
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val body = "image=" + URLEncoder.encode(base64, "UTF-8") + "&language_type=ENG"

        // 高精度含位置版优先；额度用尽自动降级到通用版
        try {
            recognizeOnce("accurate", token, body)
        } catch (e: BaiduOcrException) {
            if (e.code == "17" || e.code == "18") {
                recognizeOnce("general", token, body)
            } else {
                throw e
            }
        }
    }

    /** 用输入的 Key 测试能否连接百度 OCR（设置页「测试连接」） */
    suspend fun testKeys(apiKey: String, secretKey: String): String {
        if (apiKey.isBlank() || secretKey.isBlank()) {
            throw IOException("请先填写 API Key 和 Secret Key")
        }
        val token = fetchToken(apiKey, secretKey)
        return "连接成功！已获取访问令牌（" + token.take(8) + "…）"
    }

    private suspend fun recognizeOnce(api: String, token: String, body: String): List<OcrWord> {
        val url = "https://aip.baidubce.com/rest/2.0/ocr/v1/" + api + "?access_token=" + token
        val json = httpPostForm(url, body)
        val obj = JSONObject(json)
        val errorCode = obj.optString("error_code")
        if (errorCode.isNotBlank()) {
            val msg = when (errorCode) {
                "6" -> "应用没有文字识别权限：请到百度智能云控制台 → 文字识别 → 应用列表 → 编辑应用，勾选「通用文字识别」相关接口并保存，同时确认服务已开通"
                "17", "18" -> "百度 OCR 免费额度已用完，请明天再试，或在控制台领取/购买额度"
                "110" -> "访问令牌无效，请重新填写 API Key / Secret Key 并点「测试连接」"
                else -> obj.optString("error_msg")
            }
            throw BaiduOcrException(errorCode, "百度 OCR 错误(" + errorCode + ")：" + msg)
        }

        val words = LinkedHashSet<OcrWord>()
        val arr = obj.optJSONArray("words_result")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val cleaned = OcrFilter.clean(item.optString("words"))
                if (cleaned == null) continue
                if (words.any { it.text == cleaned }) continue
                val loc = item.optJSONObject("location")
                val left = loc?.optInt("left") ?: 0
                val top = loc?.optInt("top") ?: 0
                val width = loc?.optInt("width") ?: 0
                val height = loc?.optInt("height") ?: 0
                words.add(OcrWord(cleaned, Rect(left, top, left + width, top + height)))
            }
        }
        return words.toList()
    }

    private suspend fun accessToken(): String {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiresAt) return cachedToken!!

        val apiKey = prefs.getString("baidu_api_key", "").orEmpty().trim()
        val secretKey = prefs.getString("baidu_secret_key", "").orEmpty().trim()
        if (apiKey.isEmpty() || secretKey.isEmpty()) {
            throw IOException("还没有配置百度 OCR 的 API Key，请点右上角设置填写")
        }
        return fetchToken(apiKey, secretKey)
    }

    private suspend fun fetchToken(apiKey: String, secretKey: String): String {
        val now = System.currentTimeMillis()
        val url = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials" +
            "&client_id=" + URLEncoder.encode(apiKey, "UTF-8") +
            "&client_secret=" + URLEncoder.encode(secretKey, "UTF-8")
        val json = httpGet(url)
        val obj = JSONObject(json)
        val token = obj.optString("access_token")
        if (token.isBlank()) {
            val desc = obj.optString("error_description").ifBlank { "请检查 API Key / Secret Key" }
            throw IOException("获取百度 token 失败：" + desc)
        }
        cachedToken = token
        tokenExpiresAt = now + (obj.optLong("expires_in", 2592000L) - 60L) * 1000L
        return token
    }
}
