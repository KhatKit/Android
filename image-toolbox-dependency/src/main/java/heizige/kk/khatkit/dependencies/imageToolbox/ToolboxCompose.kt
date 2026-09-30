package heizige.kk.khatkit.dependencies.imageToolbox

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 多图合成：拼图网格、水平/垂直堆叠、双图混合（叠加/正片叠底/滤色等）、
 * 图片水印（叠加贴图），全部在本地用 Canvas / 像素运算完成。
 */
internal object ToolboxCompose {

    fun isComposeOp(op: String): Boolean = op in setOf(
        "grid", "collage", "stack_h", "stack_v", "side_by_side", "blend", "watermark_image",
    )

    fun apply(op: String, inputs: List<String>, p: Map<String, Any?>, target: File): String {
        require(inputs.isNotEmpty()) { "合成操作至少需要一张图片" }
        val bitmaps = inputs.map { ToolboxIO.decode(it) }
        try {
            val output = when (op) {
                "grid", "collage" -> grid(bitmaps, p)
                "stack_h", "side_by_side" -> stackHorizontal(bitmaps, p)
                "stack_v" -> stackVertical(bitmaps, p)
                "blend" -> {
                    require(bitmaps.size >= 2) { "blend 需要两张图片（底图 + 上层图）" }
                    blend(bitmaps[0], bitmaps[1], p)
                }
                "watermark_image" -> {
                    require(bitmaps.size >= 2) { "watermark_image 需要底图 + 水印图" }
                    overlayImage(bitmaps[0], bitmaps[1], p)
                }
                else -> error("不支持的合成操作：$op")
            }
            return ToolboxIO.saveAndRecycle(output, target, ToolboxParams.int(p, "quality", 92))
        } finally {
            bitmaps.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }

    private fun defaultGridColumns(count: Int): Int = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)

    private fun background(p: Map<String, Any?>): Int =
        ToolboxIO.parseColor(ToolboxParams.str(p, "background", "#FFFFFF"), Color.WHITE)

    private fun grid(bitmaps: List<Bitmap>, p: Map<String, Any?>): Bitmap {
        val columns = ToolboxParams.int(p, "cols", ToolboxParams.int(p, "columns", defaultGridColumns(bitmaps.size)))
            .coerceIn(1, bitmaps.size)
        val rows = (bitmaps.size + columns - 1) / columns
        val gap = ToolboxParams.int(p, "gap", 4).coerceIn(0, 200)
        val cellWidth = ToolboxParams.int(p, "cell_width", ToolboxParams.int(p, "width", 0))
            .takeIf { it > 0 }
            ?: bitmaps.maxOf { it.width }
        val cellHeight = ToolboxParams.int(p, "cell_height", 0).takeIf { it > 0 }
            ?: bitmaps.maxOf { it.height }
        val outputWidth = columns * cellWidth + gap * (columns + 1)
        val outputHeight = rows * cellHeight + gap * (rows + 1)
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(background(p))
        bitmaps.forEachIndexed { index, bitmap ->
            val column = index % columns
            val row = index / columns
            val baseX = gap + column * (cellWidth + gap)
            val baseY = gap + row * (cellHeight + gap)
            val scale = max(cellWidth.toDouble() / bitmap.width, cellHeight.toDouble() / bitmap.height)
            val scaledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(cellWidth)
            val scaledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(cellHeight)
            val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            val source = android.graphics.Rect(
                (scaledWidth - cellWidth) / 2,
                (scaledHeight - cellHeight) / 2,
                (scaledWidth - cellWidth) / 2 + cellWidth,
                (scaledHeight - cellHeight) / 2 + cellHeight,
            )
            canvas.drawBitmap(scaled, source, android.graphics.RectF(baseX.toFloat(), baseY.toFloat(), (baseX + cellWidth).toFloat(), (baseY + cellHeight).toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            scaled.recycle()
        }
        return output
    }

    private fun stackHorizontal(bitmaps: List<Bitmap>, p: Map<String, Any?>): Bitmap {
        val gap = ToolboxParams.int(p, "gap", 0).coerceIn(0, 400)
        val targetHeight = ToolboxParams.int(p, "height", 0).takeIf { it > 0 } ?: bitmaps.maxOf { it.height }
        val totalWidth = bitmaps.maxOf { (it.width.toDouble() * targetHeight / it.height).roundToInt() }
        val outputWidth = totalWidth + gap * (bitmaps.size - 1)
        val output = Bitmap.createBitmap(outputWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(background(p))
        var x = 0
        bitmaps.forEach { bitmap ->
            val scaledWidth = (bitmap.width.toDouble() * targetHeight / bitmap.height).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, targetHeight, true)
            canvas.drawBitmap(scaled, x.toFloat(), 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            x += scaledWidth + gap
            scaled.recycle()
        }
        return output
    }

    private fun stackVertical(bitmaps: List<Bitmap>, p: Map<String, Any?>): Bitmap {
        val gap = ToolboxParams.int(p, "gap", 0).coerceIn(0, 400)
        val targetWidth = ToolboxParams.int(p, "width", 0).takeIf { it > 0 } ?: bitmaps.maxOf { it.width }
        val heights = bitmaps.map { (it.height.toDouble() * targetWidth / it.width).roundToInt().coerceAtLeast(1) }
        val outputHeight = heights.sum() + gap * (bitmaps.size - 1)
        val output = Bitmap.createBitmap(targetWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(background(p))
        var y = 0
        bitmaps.forEachIndexed { index, bitmap ->
            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, heights[index], true)
            canvas.drawBitmap(scaled, 0f, y.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
            y += heights[index] + gap
            scaled.recycle()
        }
        return output
    }

    private fun blend(base: Bitmap, top: Bitmap, p: Map<String, Any?>): Bitmap {
        val mode = ToolboxParams.str(p, "mode", "overlay").lowercase().replace('-', '_')
        val opacity = ToolboxParams.num(p, "opacity", 100.0).coerceIn(0.0, 100.0) / 100.0
        val width = base.width
        val height = base.height
        val basePixels = IntArray(width * height)
        base.getPixels(basePixels, 0, width, 0, 0, width, height)
        val scaledTop = if (top.width != width || top.height != height) {
            Bitmap.createScaledBitmap(top, width, height, true)
        } else {
            top
        }
        val topPixels = IntArray(width * height)
        scaledTop.getPixels(topPixels, 0, width, 0, 0, width, height)
        val output = IntArray(basePixels.size)
        for (index in basePixels.indices) {
            val baseColor = basePixels[index]
            val topColor = topPixels[index]
            val topAlpha = (topColor ushr 24) / 255.0 * opacity
            val br = baseColor shr 16 and 0xFF
            val bg = baseColor shr 8 and 0xFF
            val bb = baseColor and 0xFF
            val tr = topColor shr 16 and 0xFF
            val tg = topColor shr 8 and 0xFF
            val tb = topColor and 0xFF
            val mr = blendChannel(mode, br, tr)
            val mg = blendChannel(mode, bg, tg)
            val mb = blendChannel(mode, bb, tb)
            val r = (br + (mr - br) * topAlpha).roundToInt().coerceIn(0, 255)
            val g = (bg + (mg - bg) * topAlpha).roundToInt().coerceIn(0, 255)
            val b = (bb + (mb - bb) * topAlpha).roundToInt().coerceIn(0, 255)
            output[index] = (baseColor and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
        if (scaledTop !== top) scaledTop.recycle()
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun blendChannel(mode: String, base: Int, top: Int): Int {
        val b = base / 255.0
        val t = top / 255.0
        val result = when (mode) {
            "normal" -> t
            "multiply" -> b * t
            "screen" -> 1.0 - (1.0 - b) * (1.0 - t)
            "overlay" -> if (b < 0.5) 2.0 * b * t else 1.0 - 2.0 * (1.0 - b) * (1.0 - t)
            "soft_light" -> if (t < 0.5) {
                2.0 * b * t + b * b * (1.0 - 2.0 * t)
            } else {
                2.0 * b * (1.0 - t) + sqrt(b) * (2.0 * t - 1.0)
            }
            "hard_light" -> if (t < 0.5) 2.0 * b * t else 1.0 - 2.0 * (1.0 - b) * (1.0 - t)
            "add", "linear_dodge" -> min(1.0, b + t)
            "subtract" -> max(0.0, b - t)
            "difference" -> kotlin.math.abs(b - t)
            "darken" -> min(b, t)
            "lighten" -> max(b, t)
            "color_dodge" -> if (t >= 1.0) 1.0 else min(1.0, b / (1.0 - t))
            "color_burn" -> if (t <= 0.0) 0.0 else 1.0 - min(1.0, (1.0 - b) / t)
            else -> error("不支持的混合模式：$mode（normal/multiply/screen/overlay/soft_light/hard_light/add/subtract/difference/darken/lighten/color_dodge/color_burn）")
        }
        return (result * 255).roundToInt().coerceIn(0, 255)
    }

    private fun overlayImage(base: Bitmap, overlay: Bitmap, p: Map<String, Any?>): Bitmap {
        val scalePercent = ToolboxParams.num(p, "scale", 0.0)
        val opacity = ToolboxParams.num(p, "opacity", 100.0).coerceIn(0.0, 100.0)
        val targetWidth = if (scalePercent > 0) {
            (base.width * scalePercent / 100.0).roundToInt().coerceIn(1, base.width * 4)
        } else {
            ToolboxParams.int(p, "width", overlay.width).coerceIn(1, base.width * 4)
        }
        val targetHeight = (overlay.height.toDouble() * targetWidth / overlay.width).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(overlay, targetWidth, targetHeight, true)
        val margin = ToolboxParams.int(p, "margin", 16).coerceAtLeast(0)
        val position = ToolboxParams.str(p, "position", "bottom-right").lowercase().replace('_', '-')
        val x = when {
            position.contains("left") -> margin
            position.contains("center") -> ((base.width - targetWidth) / 2).coerceAtLeast(0)
            else -> (base.width - targetWidth - margin).coerceAtLeast(0)
        }
        val y = when {
            position.contains("top") -> margin
            position.contains("bottom") -> (base.height - targetHeight - margin).coerceAtLeast(0)
            else -> ((base.height - targetHeight) / 2).coerceAtLeast(0)
        }
        val output = base.copy(Bitmap.Config.ARGB_8888, true) ?: throw IllegalStateException("无法创建合成画布")
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (opacity / 100.0 * 255).roundToInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(scaled, x.toFloat(), y.toFloat(), paint)
        scaled.recycle()
        return output
    }
}
