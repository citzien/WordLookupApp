package com.school.wordhelper.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import com.school.wordhelper.data.httpPostJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 腾讯云 OCR（通用印刷体识别 GeneralBasicOCR，含位置信息）。
 * 使用前需在腾讯云控制台开通「文字识别」，并在访问管理 CAM 创建 API 密钥
 * （SecretId / SecretKey）。
 */
object TencentOcrClient {

    private const val HOST = "ocr.tencentcloudapi.com"
    private const val SERVICE = "ocr"
    private const val VERSION = "2018-11-19"
    private const val ACTION = "GeneralBasicOCR"
    private const val REGION = "ap-guangzhou"

    suspend fun recognize(bitmap: Bitmap, secretId: String, secretKey: String): List<OcrWord> =
        withContext(Dispatchers.IO) {
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            // 通用印刷体识别默认中英文混合（英文同样支持），
            // 不传 LanguageType，避免个别地区接口不支持该参数
            val payload = JSONObject()
                .put("ImageBase64", base64)
                .toString()
            val json = request(payload, secretId, secretKey)
            val response = JSONObject(json).optJSONObject("Response")
            val error = response?.optJSONObject("Error")
            if (error != null) {
                val code = error.optString("Code")
                throw IOException("腾讯云 OCR 错误(" + code + ")：" + error.optString("Message"))
            }
            val detections = response?.optJSONArray("TextDetections") ?: JSONArray()
            val words = LinkedHashSet<OcrWord>()
            for (i in 0 until detections.length()) {
                val d = detections.optJSONObject(i) ?: continue
                val cleaned = OcrFilter.clean(d.optString("DetectedText"))
                if (cleaned == null) continue
                if (words.any { it.text == cleaned }) continue
                val polygon = d.optJSONArray("Polygon")
                var minX = Int.MAX_VALUE
                var minY = Int.MAX_VALUE
                var maxX = 0
                var maxY = 0
                if (polygon != null) {
                    for (j in 0 until polygon.length()) {
                        val p = polygon.optJSONObject(j) ?: continue
                        val x = p.optInt("X")
                        val y = p.optInt("Y")
                        if (x < minX) minX = x
                        if (y < minY) minY = y
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                    }
                }
                if (maxX > minX && maxY > minY) {
                    words.add(OcrWord(cleaned, Rect(minX, minY, maxX, maxY)))
                }
            }
            words.toList()
        }

    /** 用一对密钥测试接口连通性 */
    suspend fun test(secretId: String, secretKey: String): String = withContext(Dispatchers.IO) {
        if (secretId.isBlank() || secretKey.isBlank()) {
            throw IOException("请先填写 SecretId 和 SecretKey")
        }
        val payload = JSONObject().put("ImageBase64", TINY_PNG).toString()
        try {
            val json = request(payload, secretId, secretKey)
            val error = JSONObject(json).optJSONObject("Response")?.optJSONObject("Error")
            if (error == null) {
                "连接成功！腾讯云 OCR 接口可用"
            } else {
                val code = error.optString("Code")
                if (code.startsWith("AuthFailure")) {
                    "密钥错误：" + error.optString("Message")
                } else {
                    "连接成功（鉴权通过，返回 " + code + "，请确认已开通文字识别服务）"
                }
            }
        } catch (e: Exception) {
            throw IOException("连接失败：" + (e.message ?: "未知错误"))
        }
    }

    private suspend fun request(payload: String, secretId: String, secretKey: String): String {
        val now = System.currentTimeMillis() / 1000
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(now * 1000))

        val actionLower = ACTION.lowercase()
        val canonicalHeaders =
            "content-type:application/json; charset=utf-8\nhost:$HOST\nx-tc-action:$actionLower\n"
        val signedHeaders = "content-type;host;x-tc-action"
        val canonicalRequest =
            "POST\n/\n\n$canonicalHeaders\n$signedHeaders\n" + sha256Hex(payload)
        val credentialScope = "$date/$SERVICE/tc3_request"
        val stringToSign = "TC3-HMAC-SHA256\n$now\n$credentialScope\n" + sha256Hex(canonicalRequest)

        val secretDate = hmacSha256(date, ("TC3" + secretKey).toByteArray(Charsets.UTF_8))
        val secretService = hmacSha256(SERVICE, secretDate)
        val secretSigning = hmacSha256("tc3_request", secretService)
        val signature = hex(hmacSha256(stringToSign, secretSigning))
        val authorization =
            "TC3-HMAC-SHA256 Credential=$secretId/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val headers = mapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Host" to HOST,
            "X-TC-Action" to ACTION,
            "X-TC-Version" to VERSION,
            "X-TC-Timestamp" to now.toString(),
            "X-TC-Region" to REGION,
            "Authorization" to authorization
        )
        return httpPostJson("https://$HOST/", payload, headers)
    }

    private fun sha256Hex(s: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return hex(digest)
    }

    private fun hmacSha256(data: String, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private const val TINY_PNG =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
}
