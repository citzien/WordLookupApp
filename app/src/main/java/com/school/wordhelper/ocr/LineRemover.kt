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

    /**
     * 去除「从文字中间穿过的横线」（练习册常见：每行中间一条线）。
     * 先按行检测长横线（允许被字母打断的小空隙），
     * 再逐像素判断：线上像素如果上下 3~6px 内有笔画（属于字母）就保留，否则擦白。
     */
    fun removeCrossingLines(src: Bitmap): Bitmap {
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

        val maxGap = maxOf(10, (w * 0.02).toInt())
        val minLen = (w * 0.35).toInt()

        // 1. 检测每行是否有长横线（允许被字母打断的小空隙）
        val lineRows = ArrayList<Int>()
        for (y in 0 until h) {
            var start = -1
            var last = -1
            var cnt = 0
            var x = 0
            while (x < w) {
                if (dark[y * w + x]) {
                    if (start < 0) start = x
                    last = x
                    cnt++
                } else {
                    if (last >= 0 && x - last > maxGap) {
                        if (last - start + 1 >= minLen && cnt >= minLen * 0.5) lineRows.add(y)
                        start = -1
                        last = -1
                        cnt = 0
                    }
                }
                x++
            }
            if (last >= 0 && last - start + 1 >= minLen && cnt >= minLen * 0.5) lineRows.add(y)
        }

        // 2. 逐像素擦线：保留属于字母的像素
        var any = false
        for (y in lineRows) {
            for (x in 0 until w) {
                if (!dark[y * w + x]) continue
                var letter = false
                outer@ for (dy in intArrayOf(-6, -5, -4, -3, 3, 4, 5, 6)) {
                    val yy = y + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -2..2) {
                        val xx = x + dx
                        if (xx < 0 || xx >= w) continue
                        if (dark[yy * w + xx]) {
                            letter = true
                            break@outer
                        }
                    }
                }
                if (!letter) {
                    pixels[y * w + x] = -1 // 白色
                    any = true
                }
            }
        }
        if (!any) return src

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
