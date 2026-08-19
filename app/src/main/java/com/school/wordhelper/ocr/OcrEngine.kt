package com.school.wordhelper.ocr

import android.content.Context
import android.graphics.Bitmap
import java.io.IOException

/**
 * OCR 引擎门面：根据设置选择 百度 / 腾讯云。
 * 识别前可选去除格子线（默认开启）。
 */
class OcrEngine(private val context: Context) {

    private val prefs = context.getSharedPreferences("word_helper", Context.MODE_PRIVATE)
    private val baidu = BaiduOcrClient(context)

    private fun engine(): String = prefs.getString("ocr_engine", "baidu").orEmpty()

    suspend fun recognize(bitmap: Bitmap, removeLines: Boolean = true): List<OcrWord> {
        val ocrBitmap = if (removeLines) {
            // 先去格子/表格框线，再去从文字中间穿过的横线
            var bmp = LineRemover.removeGridLines(bitmap)
            bmp = LineRemover.removeCrossingLines(bmp)
            bmp
        } else {
            bitmap
        }
        return if (engine() == "tencent") {
            val id = prefs.getString("tencent_secret_id", "").orEmpty().trim()
            val key = prefs.getString("tencent_secret_key", "").orEmpty().trim()
            if (id.isEmpty() || key.isEmpty()) {
                throw IOException("还没有配置腾讯云 OCR 的密钥，请点右上角设置填写")
            }
            TencentOcrClient.recognize(ocrBitmap, id, key)
        } else {
            baidu.recognize(ocrBitmap)
        }
    }

    /** 设置页「测试连接」：测试当前选中的引擎 */
    suspend fun testKeys(key1: String, key2: String): String {
        return if (engine() == "tencent") {
            TencentOcrClient.test(key1, key2)
        } else {
            baidu.testKeys(key1, key2)
        }
    }
}
