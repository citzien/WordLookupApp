package com.school.wordhelper.ocr

import android.graphics.Bitmap

/**
 * 去除图片里的格子/表格框线（水平长线 + 垂直线），保留文字。
 * 原理：run-length 检测长暗色线段，且线段两侧（4px 外）是空白才算框线，
 * 避免误删字母的竖笔画。
 * 输出图片尺寸与原图一致，OCR 返回的坐标可直接和原图对齐。
 */
object LineRemover {

    fun removeGridLines(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val dark = BooleanArray(w * h)
        for (i in 0 until w * h) {
            val p = pixels[i]
            val lum = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
            dark[i] = lum < 150
        }

        val minH = maxOf((w * 0.10).toInt(), 60)
        val minV = maxOf((h * 0.10).toInt(), 60)
        val gap = 4
        val line = BooleanArray(w * h)

        // 水平框线：长运行 + 上下 4px 外基本空白
        for (y in gap until h - gap) {
            var x = 0
            while (x < w) {
                if (dark[y * w + x]) {
                    var run = 1
                    while (x + run < w && dark[y * w + x + run]) run++
                    if (run >= minH) {
                        var aboveDark = 0
                        var belowDark = 0
                        for (k in 0 until run) {
                            if (dark[(y - gap) * w + x + k]) aboveDark++
                            if (dark[(y + gap) * w + x + k]) belowDark++
                        }
                        if (aboveDark < run * 0.3 && belowDark < run * 0.3) {
                            for (k in 0 until run) line[y * w + x + k] = true
                        }
                    }
                    x += run
                } else {
                    x++
                }
            }
        }

        // 垂直框线：长运行 + 左右 4px 外基本空白
        for (x in gap until w - gap) {
            var y = 0
            while (y < h) {
                if (dark[y * w + x]) {
                    var run = 1
                    while (y + run < h && dark[(y + run) * w + x]) run++
                    if (run >= minV) {
                        var leftDark = 0
                        var rightDark = 0
                        for (k in 0 until run) {
                            if (dark[(y + k) * w + x - gap]) leftDark++
                            if (dark[(y + k) * w + x + gap]) rightDark++
                        }
                        if (leftDark < run * 0.3 && rightDark < run * 0.3) {
                            for (k in 0 until run) line[(y + k) * w + x] = true
                        }
                    }
                    y += run
                } else {
                    y++
                }
            }
        }

        var any = false
        for (i in 0 until w * h) {
            if (line[i]) {
                pixels[i] = -1 // 白色
                any = true
            }
        }
        if (!any) return src

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
