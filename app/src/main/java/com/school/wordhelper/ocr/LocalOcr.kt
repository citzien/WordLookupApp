package com.school.wordhelper.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 本地离线 OCR（Tesseract 4 LSTM 引擎）。
 * 模型随 APK 打包（assets/tessdata/eng.traineddata），首次使用复制到私有目录，
 * 之后完全离线、无需密钥、无额度限制。精度略低于云端 OCR。
 */
class LocalOcrClient(private val context: Context) {

    private var api: TessBaseAPI? = null

    @Synchronized
    private fun ensureInit(): TessBaseAPI {
        api?.let { return it }
        val dataPath = File(context.filesDir, "tesseract").apply { mkdirs() }
        val tessdata = File(dataPath, "tessdata").apply { mkdirs() }
        val model = File(tessdata, "eng.traineddata")
        if (!model.exists()) {
            context.assets.open("tessdata/eng.traineddata").use { input ->
                model.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val t = TessBaseAPI()
        if (!t.init(dataPath.absolutePath, "eng")) {
            t.recycle()
            throw IOException("Tesseract 初始化失败（模型文件缺失）")
        }
        api = t
        return t
    }

    suspend fun recognize(bitmap: Bitmap): List<OcrWord> = withContext(Dispatchers.IO) {
        val t = ensureInit()
        try {
            t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
            t.setImage(bitmap)
            // 必须先调用 getUTF8Text() 触发识别，否则 resultIterator 为 null
            t.getUTF8Text()
            val iter = t.resultIterator ?: return@withContext emptyList()
            val words = LinkedHashSet<OcrWord>()
            iter.begin()
            do {
                val raw = iter.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
                val cleaned = OcrFilter.clean(raw)
                val rect = iter.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                if (cleaned != null && rect != null && rect.width() > 0 && rect.height() > 0) {
                    if (words.none { it.text == cleaned }) {
                        words.add(OcrWord(cleaned, Rect(rect)))
                    }
                }
            } while (iter.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            iter.delete()
            words.toList()
        } finally {
            t.clear()
        }
    }

    fun recycle() {
        api?.recycle()
        api = null
    }
}
