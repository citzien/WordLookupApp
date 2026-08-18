package com.school.wordhelper.ocr

import android.graphics.Rect

/** 一个识别出的单词及其在原图上的位置 */
data class OcrWord(
    val text: String,
    val box: Rect
)

object OcrFilter {

    private val englishWord = Regex("^[A-Za-z][A-Za-z'-]*$")

    /** 清理识别结果：去首尾标点、转小写；不是合格英文词返回 null */
    fun clean(raw: String): String? {
        var s = raw.trim()
        while (s.isNotEmpty() && !s.first().isLetter()) s = s.substring(1)
        while (s.isNotEmpty() && !s.last().isLetter()) s = s.dropLast(1)
        if (s.length < 2 || !englishWord.matches(s)) return null
        return s.lowercase()
    }
}
