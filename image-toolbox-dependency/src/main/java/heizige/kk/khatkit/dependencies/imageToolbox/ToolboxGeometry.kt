package heizige.kk.khatkit.dependencies.imageToolbox

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 单图几何 / 版式操作：按比例裁剪、fit/fill/smart-fit、任意角度旋转并自动裁掉黑边、
 * 最近邻/双线性重采样、描边、透明度、投影、双层与阴影边框、透明区域着色、内容裁剪。
 */
internal object ToolboxGeometry {

    fun apply(source: Bitmap, op: String, p: Map<String, Any?>): Bitmap = when (op) {
        "crop_aspect" -> cropAspect(source, p)
        "fit" -> fit(source, p, background = pickBackground(source, p))
        "smart_fit" -> fit(source, p, background = pickBackground(source, p))
        "fill" -> fill(source, p)
        "rotate_auto_crop" -> rotateAutoCrop(source, p)
        "resample" -> resample(source, p)
        "outline" -> outline(source, p)
        "opacity", "alpha" -> opacity(source, p)
        "drop_shadow" -> dropShadow(source, p)
        "border_double" -> borderDouble(source, p)
        "border_shadow" -> borderShadow(source, p)
        "tint_transparent" -> tintTransparent(source, p)
        "crop_to_content" -> cropToContent(source, p)
        else -> error("不支持的几何操作：$op")
    }

    fun isGeometryOp(op: String): Boolean = op in setOf(
        "crop_aspect", "fit", "smart_fit", "fill", "rotate_auto_crop", "resample",
        "outline", "opacity", "alpha", "drop_shadow", "border_double", "border_shadow",
        "tint_transparent", "crop_to_content",
    )

    // ══════════════ 裁剪 / 缩放 ══════════════

    private fun aspectOf(p: Map<String, Any?>): Double {
        val ratio = ToolboxParams.str(p, "ratio", ToolboxParams.str(p, "aspect", ""))
        if (ratio.isNotBlank()) {
            val parts = ratio.split(':', '/', 'x').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size == 2) {
                val a = parts[0].toDoubleOrNull()
                val b = parts[1].toDoubleOrNull()
                if (a != null && b != null && a > 0 && b > 0) return a / b
            }
            ratio.toDoubleOrNull()?.takeIf { it > 0 }?.let { return it }
        }
        val width = ToolboxParams.num(p, "width", 0.0)
        val height = ToolboxParams.num(p, "height", 0.0)
        if (width > 0 && height > 0) return width / height
        return 0.0
    }

    private fun cropAspect(source: Bitmap, p: Map<String, Any?>): Bitmap {
        var target = aspectOf(p)
        if (target <= 0) target = source.width.toDouble() / source.height
        val sourceAspect = source.width.toDouble() / source.height
        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspect > target) {
            cropHeight = source.height
            cropWidth = (source.height * target).roundToInt().coerceIn(1, source.width)
        } else {
            cropWidth = source.width
            cropHeight = (source.width / target).roundToInt().coerceIn(1, source.height)
        }
        val anchor = ToolboxParams.str(p, "anchor", "center").lowercase().replace('_', '-')
        val x = when {
            anchor.contains("left") -> 0
            anchor.contains("right") -> source.width - cropWidth
            else -> (source.width - cropWidth) / 2
        }
        val y = when {
            anchor.contains("top") -> 0
            anchor.contains("bottom") -> source.height - cropHeight
            else -> (source.height - cropHeight) / 2
        }
        return Bitmap.createBitmap(source, x, y, cropWidth, cropHeight).let { cropped ->
            if (cropped === source) source.copy(Bitmap.Config.ARGB_8888, false) else cropped
        }
    }

    private fun pickBackground(source: Bitmap, p: Map<String, Any?>): Int {
        val configured = ToolboxParams.str(p, "background", ToolboxParams.str(p, "background_color", "auto"))
        if (!configured.equals("auto", ignoreCase = true)) {
            return ToolboxIO.parseColor(configured, Color.WHITE)
        }
        val corners = intArrayOf(
            source.getPixel(0, 0),
            source.getPixel(source.width - 1, 0),
            source.getPixel(0, source.height - 1),
            source.getPixel(source.width - 1, source.height - 1),
        )
        var r = 0
        var g = 0
        var b = 0
        corners.forEach { color ->
            r += color shr 16 and 0xFF
            g += color shr 8 and 0xFF
            b += color and 0xFF
        }
        val count = corners.size
        return Color.rgb(r / count, g / count, b / count)
    }

    private fun targetSize(source: Bitmap, p: Map<String, Any?>): Pair<Int, Int> {
        var width = ToolboxParams.int(p, "width", 0)
        var height = ToolboxParams.int(p, "height", 0)
        require(width > 0 || height > 0) { "需要 width 或 height 至少一个为正数" }
        if (width <= 0) width = (source.width.toDouble() * height / source.height).roundToInt()
        if (height <= 0) height = (source.height.toDouble() * width / source.width).roundToInt()
        return Pair(width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    private fun fit(source: Bitmap, p: Map<String, Any?>, background: Int): Bitmap {
        val (width, height) = targetSize(source, p)
        val scale = min(width.toDouble() / source.width, height.toDouble() / source.height)
        val scaledWidth = (source.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (source.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(background)
        val anchor = ToolboxParams.str(p, "anchor", "center").lowercase().replace('_', '-')
        val x = when {
            anchor.contains("left") -> 0f
            anchor.contains("right") -> (width - scaledWidth).toFloat()
            else -> (width - scaledWidth) / 2f
        }
        val y = when {
            anchor.contains("top") -> 0f
            anchor.contains("bottom") -> (height - scaledHeight).toFloat()
            else -> (height - scaledHeight) / 2f
        }
        canvas.drawBitmap(scaled, x, y, Paint(Paint.FILTER_BITMAP_FLAG))
        if (scaled !== source) scaled.recycle()
        return output
    }

    private fun fill(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val (width, height) = targetSize(source, p)
        val scale = max(width.toDouble() / source.width, height.toDouble() / source.height)
        val scaledWidth = (source.width * scale).roundToInt().coerceAtLeast(width)
        val scaledHeight = (source.height * scale).roundToInt().coerceAtLeast(height)
        val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        val x = (scaledWidth - width) / 2
        val y = (scaledHeight - height) / 2
        val output = Bitmap.createBitmap(scaled, x, y, width, height)
        if (scaled !== source) scaled.recycle()
        if (output === source) return source.copy(Bitmap.Config.ARGB_8888, false)
        return output
    }

    private fun rotateAutoCrop(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val degrees = ToolboxParams.num(p, "degrees", ToolboxParams.num(p, "angle", 0.0))
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        val rotated: Bitmap = if (abs(normalized) < 0.01) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        } else {
            val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
        val ratio = aspectOf(p).takeIf { it > 0 } ?: (source.width.toDouble() / source.height)
        val crop = largestInscribed(rotated.width.toDouble(), rotated.height.toDouble(), normalized, ratio)
        val result = Bitmap.createBitmap(
            rotated,
            ((rotated.width - crop.first) / 2).coerceAtLeast(0),
            ((rotated.height - crop.second) / 2).coerceAtLeast(0),
            crop.first.coerceAtLeast(1),
            crop.second.coerceAtLeast(1),
        )
        if (rotated !== source && rotated !== result) rotated.recycle()
        return if (result === source) source.copy(Bitmap.Config.ARGB_8888, false) ?: source else result
    }

    /** 旋转 [degrees] 后画布内能容纳的最大 [ratio] 比例矩形（二分搜索保证四角都在原图内）。 */
    private fun largestInscribed(width: Double, height: Double, degrees: Double, ratio: Double): Pair<Int, Int> {
        val radians = Math.toRadians(degrees)
        val c = abs(cos(radians))
        val s = abs(sin(radians))
        fun fits(scale: Double): Boolean {
            var probeWidth = width * scale
            var probeHeight = probeWidth / ratio
            if (probeHeight > height * scale) {
                probeHeight = height * scale
                probeWidth = probeHeight * ratio
            }
            val hx = probeWidth / 2
            val hy = probeHeight / 2
            for (sx in intArrayOf(-1, 1)) {
                for (sy in intArrayOf(-1, 1)) {
                    val x = sx * hx
                    val y = sy * hy
                    val originalX = x * c + y * s
                    val originalY = -x * s + y * c
                    if (abs(originalX) > width / 2 + 0.5 || abs(originalY) > height / 2 + 0.5) return false
                }
            }
            return true
        }
        var low = 0.0
        var high = 1.0
        repeat(60) {
            val middle = (low + high) / 2
            if (fits(middle)) low = middle else high = middle
        }
        var cropWidth = width * low
        var cropHeight = cropWidth / ratio
        if (cropHeight > height * low) {
            cropHeight = height * low
            cropWidth = cropHeight * ratio
        }
        return Pair(cropWidth.roundToInt().coerceAtLeast(1), cropHeight.roundToInt().coerceAtLeast(1))
    }

    private fun resample(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val (width, height) = targetSize(source, p)
        val method = ToolboxParams.str(p, "method", ToolboxParams.str(p, "filter", "bilinear")).lowercase()
        if (method == "nearest" || method == "point") {
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            val output = IntArray(width * height)
            for (y in 0 until height) {
                val sourceY = (y.toDouble() * source.height / height).toInt().coerceIn(0, source.height - 1)
                for (x in 0 until width) {
                    val sourceX = (x.toDouble() * source.width / width).toInt().coerceIn(0, source.width - 1)
                    output[y * width + x] = pixels[sourceY * source.width + sourceX]
                }
            }
            return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
        }
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    // ══════════════ 装饰 / 描边 ══════════════

    private fun outline(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val thickness = ToolboxParams.int(p, "width", ToolboxParams.int(p, "thickness", 3)).coerceIn(1, 64)
        val color = ToolboxIO.parseColor(ToolboxParams.str(p, "color", "#FFFFFF"), Color.WHITE)
        val padding = thickness
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val opaque = BooleanArray(pixels.size) { index -> (pixels[index] ushr 24) >= 128 }
        val output = Bitmap.createBitmap(width + padding * 2, height + padding * 2, Bitmap.Config.ARGB_8888)
        val outputPixels = IntArray(output.width * output.height)
        val radius = thickness / 2 + 1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (!opaque[index]) continue
                val isEdge = (x == 0 || !opaque[index - 1]) ||
                    (x == width - 1 || !opaque[index + 1]) ||
                    (y == 0 || !opaque[index - width]) ||
                    (y == height - 1 || !opaque[index + width])
                if (!isEdge) continue
                val centerX = x + padding
                val centerY = y + padding
                for (dy in -radius..radius) {
                    val py = centerY + dy
                    if (py < 0 || py >= output.height) continue
                    for (dx in -radius..radius) {
                        if (dx * dx + dy * dy > radius * radius) continue
                        val px = centerX + dx
                        if (px < 0 || px >= output.width) continue
                        outputPixels[py * output.width + px] = color
                    }
                }
            }
        }
        output.setPixels(outputPixels, 0, output.width, 0, 0, output.width, output.height)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, padding.toFloat(), padding.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun opacity(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val percent = ToolboxParams.num(p, "opacity", ToolboxParams.num(p, "alpha", ToolboxParams.num(p, "percent", 100.0)))
            .coerceIn(0.0, 100.0)
        val factor = percent / 100.0
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val alpha = ((color ushr 24) * factor).roundToInt().coerceIn(0, 255)
            pixels[index] = (alpha shl 24) or (color and 0x00FFFFFF)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun dropShadow(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val offsetX = ToolboxParams.int(p, "offset_x", ToolboxParams.int(p, "dx", 12))
        val offsetY = ToolboxParams.int(p, "offset_y", ToolboxParams.int(p, "dy", 12))
        val radius = ToolboxParams.int(p, "radius", ToolboxParams.int(p, "blur", 8)).coerceIn(1, 80)
        val color = ToolboxIO.parseColor(ToolboxParams.str(p, "color", "#80000000"), 0x80000000.toInt())
        val padding = max(radius * 2, max(abs(offsetX), abs(offsetY))) + 4
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val shadowPixels = IntArray(pixels.size) { index ->
            val alpha = pixels[index] ushr 24
            (alpha shl 24) or (color and 0x00FFFFFF)
        }
        val blurred = ToolboxFilter.boxBlur(shadowPixels, width, height, radius)
        val output = Bitmap.createBitmap(width + padding * 2, height + padding * 2, Bitmap.Config.ARGB_8888)
        val outputPixels = IntArray(output.width * output.height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val shadowAlpha = (blurred[y * width + x] ushr 24) * ((color ushr 24) / 255.0).toFloat()
                val targetX = x + padding + offsetX
                val targetY = y + padding + offsetY
                if (targetX < 0 || targetY < 0 || targetX >= output.width || targetY >= output.height) continue
                val target = targetY * output.width + targetX
                val existing = outputPixels[target]
                val existingAlpha = existing ushr 24
                val combined = (existingAlpha + shadowAlpha.roundToInt()).coerceAtMost(255)
                outputPixels[target] = (combined shl 24) or (color and 0x00FFFFFF)
            }
        }
        output.setPixels(outputPixels, 0, output.width, 0, 0, output.width, output.height)
        Canvas(output).drawBitmap(source, padding.toFloat(), padding.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun borderDouble(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val first = ToolboxParams.int(p, "width", ToolboxParams.int(p, "outer_width", 16)).coerceIn(1, 1024)
        val second = ToolboxParams.int(p, "inner_width", max(1, first / 2)).coerceIn(1, 1024)
        val firstColor = ToolboxIO.parseColor(ToolboxParams.str(p, "color", ToolboxParams.str(p, "outer_color", "#FFFFFF")), Color.WHITE)
        val secondColor = ToolboxIO.parseColor(ToolboxParams.str(p, "inner_color", "#222222"), Color.DKGRAY)
        val total = first + second
        val output = Bitmap.createBitmap(source.width + total * 2, source.height + total * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(firstColor)
        val inner = Rect(total - second, total - second, output.width - total + second, output.height - total + second)
        canvas.drawRect(RectF(inner), Paint().apply { color = secondColor })
        canvas.drawBitmap(source, total.toFloat(), total.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun borderShadow(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val width = ToolboxParams.int(p, "width", 24).coerceIn(1, 512)
        val color = ToolboxIO.parseColor(ToolboxParams.str(p, "color", "#000000"), Color.BLACK)
        val strength = ToolboxParams.num(p, "strength", 0.8).coerceIn(0.0, 1.0)
        val output = Bitmap.createBitmap(source.width + width * 2, source.height + width * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (step in 0 until width) {
            val alpha = (strength * (1.0 - step.toDouble() / width) * 255).roundToInt().coerceIn(0, 255)
            paint.color = (alpha shl 24) or (color and 0x00FFFFFF)
            canvas.drawRect(
                RectF(step.toFloat(), step.toFloat(), (output.width - step).toFloat(), (output.height - step).toFloat()),
                paint,
            )
        }
        canvas.drawBitmap(source, width.toFloat(), width.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun tintTransparent(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val color = ToolboxIO.parseColor(ToolboxParams.str(p, "color", "#FFFFFF"), Color.WHITE)
        val threshold = ToolboxParams.int(p, "threshold", 16).coerceIn(1, 254)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val alpha = pixels[index] ushr 24
            if (alpha < threshold) {
                val fillAlpha = ((threshold - alpha) * 255 / threshold).coerceIn(0, 255)
                pixels[index] = (fillAlpha shl 24) or (color and 0x00FFFFFF)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun cropToContent(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val threshold = ToolboxParams.num(p, "threshold", 24.0).coerceIn(0.0, 255.0)
        val padding = ToolboxParams.int(p, "padding", 0).coerceIn(0, 4096)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val background = pickBackground(
            source,
            mapOf("background" to ToolboxParams.str(p, "background", "auto")),
        )
        val br = background shr 16 and 0xFF
        val bg = background shr 8 and 0xFF
        val bb = background and 0xFF
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixels[y * width + x]
                val distance = sqrt(
                    (color shr 16 and 0xFF - br).toDouble() * (color shr 16 and 0xFF - br) +
                        (color shr 8 and 0xFF - bg).toDouble() * (color shr 8 and 0xFF - bg) +
                        (color and 0xFF - bb).toDouble() * (color and 0xFF - bb)
                )
                if (distance > threshold) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        require(maxX >= minX && maxY >= minY) { "crop_to_content 未找到与背景不同的内容" }
        val left = (minX - padding).coerceIn(0, width - 1)
        val top = (minY - padding).coerceIn(0, height - 1)
        val right = (maxX + padding).coerceIn(left, width - 1)
        val bottom = (maxY + padding).coerceIn(top, height - 1)
        val cropped = Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
        return if (cropped === source) source.copy(Bitmap.Config.ARGB_8888, false) ?: source else cropped
    }
}
