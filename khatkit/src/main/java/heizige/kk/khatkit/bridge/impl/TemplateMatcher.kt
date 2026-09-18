package heizige.kk.khatkit.bridge.impl

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 纯 Kotlin 模板匹配（无 OpenCV 依赖），供无障碍 findImage 使用。
 *
 * 算法：图像金字塔 + 多尺度零均值归一化互相关（ZNCC）
 *  1. 屏幕灰度图缩到长边 <= [BASE_MAX_SIDE]（约 720p），再按 [COARSE_RATIO] 缩一次作粗搜层；
 *  2. 模板在 [SCALES] 三个尺度上分别缩放，粗搜层逐像素做 ZNCC，
 *     窗口均值/方差用积分图 O(1) 取，得分 = Σ(s·devT) / sqrt(Σs²-(Σs)²/n · ΣdevT²)；
 *  3. 取全局最优尺度，在基础分辨率上围绕粗搜命中点局部精修（±[REFINE_RADIUS]px）；
 *  4. 坐标映射回原图屏幕像素。
 */
internal object TemplateMatcher {

    data class Match(val centerX: Float, val centerY: Float, val score: Double)

    private val SCALES = doubleArrayOf(0.8, 1.0, 1.25)
    private const val BASE_MAX_SIDE = 1280.0
    private const val COARSE_RATIO = 0.25
    private const val REFINE_RADIUS = 12
    private const val MIN_TEMPLATE_PX = 6

    fun find(screen: Bitmap, template: Bitmap): Match? {
        if (screen.width <= 0 || screen.height <= 0) return null
        if (template.width < MIN_TEMPLATE_PX || template.height < MIN_TEMPLATE_PX) return null

        val baseScale = if (max(screen.width, screen.height) > BASE_MAX_SIDE) {
            BASE_MAX_SIDE / max(screen.width, screen.height)
        } else {
            1.0
        }
        val baseW = max(8, (screen.width * baseScale).roundToInt())
        val baseH = max(8, (screen.height * baseScale).roundToInt())
        val coarseScale = baseScale * COARSE_RATIO
        val coarseW = max(8, (screen.width * coarseScale).roundToInt())
        val coarseH = max(8, (screen.height * coarseScale).roundToInt())

        val baseGray = grayResized(screen, baseW, baseH)
        val coarseIntegral = Integral(grayResized(screen, coarseW, coarseH), coarseW, coarseH)

        var bestScale = 1.0
        var bestScore = -2.0
        var bestCoarseX = -1
        var bestCoarseY = -1
        for (scale in SCALES) {
            val tw = (template.width * coarseScale * scale).roundToInt()
            val th = (template.height * coarseScale * scale).roundToInt()
            if (tw < MIN_TEMPLATE_PX || th < MIN_TEMPLATE_PX) continue
            if (tw > coarseIntegral.w || th > coarseIntegral.h) continue
            val tpl = Template(grayResized(template, tw, th), tw, th)
            val hit = search(coarseIntegral, tpl, 0, coarseIntegral.w - tw, 0, coarseIntegral.h - th)
                ?: continue
            if (hit.score > bestScore) {
                bestScore = hit.score
                bestScale = scale
                bestCoarseX = hit.x
                bestCoarseY = hit.y
            }
        }
        if (bestCoarseX < 0) return null

        val baseIntegral = Integral(baseGray, baseW, baseH)
        val baseTw = (template.width * baseScale * bestScale).roundToInt()
        val baseTh = (template.height * baseScale * bestScale).roundToInt()
        if (baseTw in MIN_TEMPLATE_PX..baseIntegral.w && baseTh in MIN_TEMPLATE_PX..baseIntegral.h) {
            val tpl = Template(grayResized(template, baseTw, baseTh), baseTw, baseTh)
            val coarseToBase = baseW.toDouble() / coarseW
            val anchorX = (bestCoarseX * coarseToBase).roundToInt()
            val anchorY = (bestCoarseY * coarseToBase).roundToInt()
            val x0 = (anchorX - REFINE_RADIUS).coerceIn(0, baseIntegral.w - baseTw)
            val x1 = (anchorX + REFINE_RADIUS).coerceIn(0, baseIntegral.w - baseTw)
            val y0 = (anchorY - REFINE_RADIUS).coerceIn(0, baseIntegral.h - baseTh)
            val y1 = (anchorY + REFINE_RADIUS).coerceIn(0, baseIntegral.h - baseTh)
            search(baseIntegral, tpl, x0, x1, y0, y1)?.let { hit ->
                return Match(
                    centerX = ((hit.x + baseTw / 2.0) / baseScale).toFloat(),
                    centerY = ((hit.y + baseTh / 2.0) / baseScale).toFloat(),
                    score = hit.score,
                )
            }
        }
        val coarseTw = (template.width * coarseScale * bestScale).roundToInt()
        val coarseTh = (template.height * coarseScale * bestScale).roundToInt()
        return Match(
            centerX = ((bestCoarseX + coarseTw / 2.0) / coarseScale).toFloat(),
            centerY = ((bestCoarseY + coarseTh / 2.0) / coarseScale).toFloat(),
            score = bestScore,
        )
    }

    private class Integral(val gray: IntArray, val w: Int, val h: Int) {
        private val sum = LongArray((w + 1) * (h + 1))
        private val sumSq = LongArray((w + 1) * (h + 1))

        init {
            for (y in 0 until h) {
                var rowSum = 0L
                var rowSq = 0L
                val rowBase = y * w
                val above = y * (w + 1)
                val current = (y + 1) * (w + 1)
                for (x in 0 until w) {
                    val value = gray[rowBase + x]
                    rowSum += value
                    rowSq += value.toLong() * value
                    sum[current + x + 1] = sum[above + x + 1] + rowSum
                    sumSq[current + x + 1] = sumSq[above + x + 1] + rowSq
                }
            }
        }

        fun windowSum(x: Int, y: Int, w: Int, h: Int): Long {
            val top = y * (this.w + 1)
            val bottom = (y + h) * (this.w + 1)
            return sum[bottom + x + w] - sum[bottom + x] - sum[top + x + w] + sum[top + x]
        }

        fun windowSumSq(x: Int, y: Int, w: Int, h: Int): Long {
            val top = y * (this.w + 1)
            val bottom = (y + h) * (this.w + 1)
            return sumSq[bottom + x + w] - sumSq[bottom + x] - sumSq[top + x + w] + sumSq[top + x]
        }
    }

    private class Template(gray: IntArray, val w: Int, val h: Int) {
        val count = w * h
        val dev = FloatArray(count)
        val norm: Double

        init {
            var sum = 0.0
            for (value in gray) sum += value
            val mean = sum / count
            var sq = 0.0
            for (i in gray.indices) {
                val d = gray[i] - mean
                dev[i] = d.toFloat()
                sq += d * d
            }
            norm = sq
        }
    }

    private data class Hit(val x: Int, val y: Int, val score: Double)

    private fun search(
        integral: Integral,
        tpl: Template,
        x0: Int,
        x1: Int,
        y0: Int,
        y1: Int,
    ): Hit? {
        if (x1 < x0 || y1 < y0 || tpl.norm <= 1e-6) return null
        val gray = integral.gray
        val sw = integral.w
        var bestScore = -2.0
        var bestX = x0
        var bestY = y0
        for (y in y0..y1) {
            for (x in x0..x1) {
                val sumS = integral.windowSum(x, y, tpl.w, tpl.h).toDouble()
                val sumSq = integral.windowSumSq(x, y, tpl.w, tpl.h).toDouble()
                val varSum = sumSq - sumS * sumS / tpl.count
                if (varSum <= 1e-6) continue
                var cross = 0.0
                var index = 0
                for (j in 0 until tpl.h) {
                    var src = (y + j) * sw + x
                    for (i in 0 until tpl.w) {
                        cross += gray[src + i] * tpl.dev[index++]
                    }
                }
                val score = cross / sqrt(varSum * tpl.norm)
                if (score > bestScore) {
                    bestScore = score
                    bestX = x
                    bestY = y
                }
            }
        }
        return if (bestScore <= -1.0) null else Hit(bestX, bestY, bestScore)
    }

    private fun grayResized(source: Bitmap, w: Int, h: Int): IntArray {
        val scaled = if (source.width == w && source.height == h) {
            source
        } else {
            Bitmap.createScaledBitmap(source, w, h, true)
        }
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== source) scaled.recycle()
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            pixels[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        return pixels
    }
}
