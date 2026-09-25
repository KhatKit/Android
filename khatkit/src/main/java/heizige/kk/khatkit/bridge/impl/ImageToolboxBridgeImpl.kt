package heizige.kk.khatkit.bridge.impl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.Build
import android.os.ParcelFileDescriptor
import heizige.kk.khatkit.bridge.ImageToolboxBridge
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 本地图像工具箱实现：纯 Android SDK（Bitmap / Canvas / Paint / ColorMatrix / Matrix），
 * PDF 用 PdfDocument / PdfRenderer，无 javax.imageio、无网络、无云端依赖。
 *
 * - 输入解码后先按 EXIF 转正（输出不写回 EXIF，因此重编码即丢元数据）；
 * - 输出缺省写到源文件同目录 `<名字>_<操作>.<扩展名>`，也接受显式输出路径；
 * - /sdcard 等共享存储路径沿用 [requireSharedStorageAccess] 权限校验；
 * - 失败抛带中文说明的异常，引擎会编码为 `{"__error":"..."}`。
 */
class ImageToolboxBridgeImpl : ImageToolboxBridge {

    override fun resize(path: String, width: Int, height: Int, keepAspect: Boolean): String = transform(path) { src ->
        require(width > 0 || height > 0) { "resize 需要 width 或 height 至少一个为正数（收到 width=$width, height=$height）" }
        val (targetWidth, targetHeight) = if (keepAspect) {
            when {
                width > 0 && height > 0 -> {
                    val scale = min(width.toFloat() / src.width, height.toFloat() / src.height)
                    Pair((src.width * scale).roundToInt().coerceAtLeast(1), (src.height * scale).roundToInt().coerceAtLeast(1))
                }
                width > 0 -> Pair(width, (width.toFloat() * src.height / src.width).roundToInt().coerceAtLeast(1))
                else -> Pair((height.toFloat() * src.width / src.height).roundToInt().coerceAtLeast(1), height)
            }
        } else {
            Pair(if (width > 0) width else src.width, if (height > 0) height else src.height)
        }
        val scaled = Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true)
        if (scaled === src) save(src, outputFor(path, "resized")) else saveAndRecycle(scaled, outputFor(path, "resized"))
    }

    override fun crop(path: String, x: Int, y: Int, width: Int, height: Int): String = transform(path) { src ->
        require(x >= 0 && y >= 0) { "crop 的 x/y 不能为负数（收到 x=$x, y=$y）" }
        require(width > 0 && height > 0) { "crop 的 width/height 必须大于 0（收到 width=$width, height=$height）" }
        require(x + width <= src.width && y + height <= src.height) {
            "裁剪区域超出图片范围：x=$x, y=$y, width=$width, height=$height（图片 ${src.width}x${src.height}）"
        }
        val cropped = Bitmap.createBitmap(src, x, y, width, height)
        if (cropped === src) save(src, outputFor(path, "cropped")) else saveAndRecycle(cropped, outputFor(path, "cropped"))
    }

    override fun rotate(path: String, degrees: Float): String = transform(path) { src ->
        val normalized = ((degrees % 360f) + 360f) % 360f
        if (abs(normalized) < 0.01f) {
            saveAndRecycle(applyColorMatrix(src, ColorMatrix()), outputFor(path, "rotated"))
        } else {
            val matrix = Matrix().apply { postRotate(normalized) }
            saveAndRecycle(Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true), outputFor(path, "rotated"))
        }
    }

    override fun flip(path: String, horizontal: Boolean): String = transform(path) { src ->
        val matrix = Matrix().apply { postScale(if (horizontal) -1f else 1f, if (horizontal) 1f else -1f) }
        saveAndRecycle(Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true), outputFor(path, "flipped"))
    }

    override fun grayscale(path: String): String = transform(path) { src ->
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        saveAndRecycle(applyColorMatrix(src, matrix), outputFor(path, "grayscale"))
    }

    override fun blur(path: String, radius: Int): String = transform(path) { src ->
        require(radius > 0) { "blur 的 radius 必须大于 0（收到 $radius）" }
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        val blurred = boxBlur(pixels, width, height, radius)
        saveAndRecycle(Bitmap.createBitmap(blurred, width, height, Bitmap.Config.ARGB_8888), outputFor(path, "blurred"))
    }

    override fun sharpen(path: String, amount: Float): String = transform(path) { src ->
        require(amount > 0f) { "sharpen 的 amount 必须大于 0（收到 $amount）" }
        val strength = amount.coerceIn(0.1f, 10f)
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        val output = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val center = pixels[index]
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    output[index] = center
                    continue
                }
                val left = pixels[index - 1]
                val right = pixels[index + 1]
                val up = pixels[index - width]
                val down = pixels[index + width]
                val alpha = (center ushr 24) and 0xFF
                val red = sharpenChannel((center shr 16) and 0xFF, (left shr 16) and 0xFF, (right shr 16) and 0xFF, (up shr 16) and 0xFF, (down shr 16) and 0xFF, strength)
                val green = sharpenChannel((center shr 8) and 0xFF, (left shr 8) and 0xFF, (right shr 8) and 0xFF, (up shr 8) and 0xFF, (down shr 8) and 0xFF, strength)
                val blue = sharpenChannel(center and 0xFF, left and 0xFF, right and 0xFF, up and 0xFF, down and 0xFF, strength)
                output[index] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        saveAndRecycle(Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888), outputFor(path, "sharpened"))
    }

    override fun pixelate(path: String, blockSize: Int): String = transform(path) { src ->
        require(blockSize >= 2) { "pixelate 的 block 必须大于等于 2（收到 $blockSize）" }
        val block = blockSize.coerceAtMost(256)
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        val output = IntArray(pixels.size)
        var by = 0
        while (by < height) {
            var bx = 0
            while (bx < width) {
                val maxY = min(by + block, height)
                val maxX = min(bx + block, width)
                var alpha = 0L
                var red = 0L
                var green = 0L
                var blue = 0L
                var count = 0
                for (y in by until maxY) {
                    val row = y * width
                    for (x in bx until maxX) {
                        val color = pixels[row + x]
                        alpha += (color ushr 24) and 0xFF
                        red += (color shr 16) and 0xFF
                        green += (color shr 8) and 0xFF
                        blue += color and 0xFF
                        count++
                    }
                }
                val average = (((alpha / count).toInt()) shl 24) or
                    (((red / count).toInt()) shl 16) or
                    (((green / count).toInt()) shl 8) or
                    ((blue / count).toInt())
                for (y in by until maxY) {
                    val row = y * width
                    for (x in bx until maxX) output[row + x] = average
                }
                bx += block
            }
            by += block
        }
        saveAndRecycle(Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888), outputFor(path, "pixelated"))
    }

    override fun brightnessContrast(path: String, brightness: Int, contrast: Int): String = transform(path) { src ->
        val offset = brightness.coerceIn(-100, 100) * 2.55f
        val contrastLevel = contrast.coerceIn(-100, 100) * 2.55f
        val factor = (259f * (contrastLevel + 255f)) / (255f * (259f - contrastLevel))
        val shift = -factor * 128f + 128f + offset
        val matrix = ColorMatrix(
            floatArrayOf(
                factor, 0f, 0f, 0f, shift,
                0f, factor, 0f, 0f, shift,
                0f, 0f, factor, 0f, shift,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        saveAndRecycle(applyColorMatrix(src, matrix), outputFor(path, "brightness_contrast"))
    }

    override fun saturation(path: String, factor: Float): String = transform(path) { src ->
        val matrix = ColorMatrix().apply { setSaturation(factor.coerceIn(0f, 10f)) }
        saveAndRecycle(applyColorMatrix(src, matrix), outputFor(path, "saturated"))
    }

    override fun hue(path: String, degrees: Float): String = transform(path) { src ->
        val radians = Math.toRadians(degrees.toDouble())
        val cosValue = cos(radians).toFloat()
        val sinValue = sin(radians).toFloat()
        val lumR = 0.213f
        val lumG = 0.715f
        val lumB = 0.072f
        val matrix = ColorMatrix(
            floatArrayOf(
                lumR + cosValue * (1 - lumR) + sinValue * (-lumR),
                lumG + cosValue * (-lumG) + sinValue * (-lumG),
                lumB + cosValue * (-lumB) + sinValue * (1 - lumB),
                0f, 0f,
                lumR + cosValue * (-lumR) + sinValue * 0.143f,
                lumG + cosValue * (1 - lumG) + sinValue * 0.140f,
                lumB + cosValue * (-lumB) + sinValue * (-0.283f),
                0f, 0f,
                lumR + cosValue * (-lumR) + sinValue * (-(1 - lumR)),
                lumG + cosValue * (-lumG) + sinValue * lumG,
                lumB + cosValue * (1 - lumB) + sinValue * lumB,
                0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        saveAndRecycle(applyColorMatrix(src, matrix), outputFor(path, "hue"))
    }

    override fun autoContrast(path: String): String = transform(path) { src ->
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        val histogram = Array(3) { IntArray(256) }
        for (color in pixels) {
            histogram[0][(color shr 16) and 0xFF]++
            histogram[1][(color shr 8) and 0xFF]++
            histogram[2][color and 0xFF]++
        }
        val clip = max(1, (pixels.size * 0.005).toInt())
        val scales = FloatArray(3)
        val offsets = FloatArray(3)
        for (channel in 0..2) {
            var low = 0
            var high = 255
            var accumulated = 0
            for (value in 0..255) {
                accumulated += histogram[channel][value]
                if (accumulated > clip) {
                    low = value
                    break
                }
            }
            accumulated = 0
            for (value in 255 downTo 0) {
                accumulated += histogram[channel][value]
                if (accumulated > clip) {
                    high = value
                    break
                }
            }
            if (high > low) {
                val scale = 255f / (high - low)
                scales[channel] = scale
                offsets[channel] = -low * scale
            } else {
                scales[channel] = 1f
                offsets[channel] = 0f
            }
        }
        val matrix = ColorMatrix(
            floatArrayOf(
                scales[0], 0f, 0f, 0f, offsets[0],
                0f, scales[1], 0f, 0f, offsets[1],
                0f, 0f, scales[2], 0f, offsets[2],
                0f, 0f, 0f, 1f, 0f,
            )
        )
        saveAndRecycle(applyColorMatrix(src, matrix), outputFor(path, "auto_contrast"))
    }

    override fun invert(path: String): String = transform(path) { src ->
        val matrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        saveAndRecycle(applyColorMatrix(src, matrix), outputFor(path, "inverted"))
    }

    override fun sepia(path: String): String = transform(path) { src ->
        val matrix = ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        saveAndRecycle(applyColorMatrix(src, matrix), outputFor(path, "sepia"))
    }

    override fun watermark(
        path: String,
        text: String,
        position: String,
        alpha: Int,
        textSize: Int,
        colorHex: String,
    ): String = transform(path) { src ->
        require(text.isNotBlank()) { "watermark 的 text 不能为空" }
        val output = src.copy(Bitmap.Config.ARGB_8888, true) ?: error("无法创建水印画布：$path")
        val canvas = Canvas(output)
        val size = if (textSize > 0) {
            textSize.toFloat().coerceIn(8f, src.width / 2f)
        } else {
            (src.width / 12f).coerceAtLeast(16f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parseColor(colorHex, Color.WHITE)
            this.alpha = alpha.coerceIn(0, 255)
            this.textSize = size
            typeface = Typeface.DEFAULT_BOLD
        }
        val normalized = position.trim().lowercase().replace('_', '-')
        val metrics = paint.fontMetrics
        val margin = (src.width / 40).coerceAtLeast(12).toFloat()
        val textWidth = paint.measureText(text)
        val x = when {
            normalized.contains("left") -> margin
            normalized.contains("center") -> (src.width - textWidth) / 2f
            else -> src.width - margin - textWidth
        }
        val y = when {
            normalized.contains("top") -> margin - metrics.ascent
            normalized.contains("bottom") -> src.height - margin - metrics.descent
            else -> (src.height - (metrics.descent + metrics.ascent)) / 2f
        }
        canvas.drawText(text, x, y, paint)
        saveAndRecycle(output, outputFor(path, "watermarked"))
    }

    override fun border(path: String, width: Int, colorHex: String): String = transform(path) { src ->
        require(width > 0) { "border 的 width 必须大于 0（收到 $width）" }
        val border = width.coerceAtMost(2048)
        val output = Bitmap.createBitmap(src.width + border * 2, src.height + border * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(parseColor(colorHex, Color.WHITE))
        canvas.drawBitmap(src, border.toFloat(), border.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        saveAndRecycle(output, outputFor(path, "bordered"))
    }

    override fun roundCorners(path: String, radius: Int): String = transform(path) { src ->
        require(radius > 0) { "roundCorners 的 radius 必须大于 0（收到 $radius）" }
        val corner = radius.toFloat().coerceAtMost(min(src.width, src.height) / 2f)
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val clip = Path().apply {
            addRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), corner, corner, Path.Direction.CW)
        }
        canvas.clipPath(clip)
        canvas.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val sourceExt = File(path).extension.lowercase()
        val ext = if (sourceExt == "png" || sourceExt == "webp") sourceExt else "png"
        saveAndRecycle(output, outputFor(path, "rounded", ext))
    }

    override fun convert(path: String, format: String, quality: Int): String = transform(path) { src ->
        val ext = normalizeFormat(format)
        save(src, outputFor(path, "converted", ext), quality)
    }

    override fun stripMetadata(path: String): String = transform(path) { src ->
        save(src, outputFor(path, "stripped", sourceExt(path)))
    }

    override fun imagesToPdf(paths: List<String>, output: String): String {
        require(paths.isNotEmpty()) { "imagesToPdf 需要至少一张图片" }
        paths.forEach { path ->
            requireSharedStorageAccess(path)
            require(File(path).exists()) { "图片不存在：$path" }
        }
        val first = File(paths.first())
        val target = if (output.isNotBlank()) File(output)
        else File(first.parentFile, "${first.nameWithoutExtension}_images.pdf")
        requireSharedStorageAccess(target.absolutePath)
        target.parentFile?.mkdirs()
        val document = PdfDocument()
        try {
            paths.forEachIndexed { index, path ->
                val bitmap = decode(path)
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, index + 1).create()
                    val page = document.startPage(pageInfo)
                    page.canvas.drawColor(Color.WHITE)
                    val scale = min(
                        (PDF_PAGE_WIDTH - PDF_MARGIN * 2) / bitmap.width.toFloat(),
                        (PDF_PAGE_HEIGHT - PDF_MARGIN * 2) / bitmap.height.toFloat(),
                    )
                    val matrix = Matrix().apply {
                        postScale(scale, scale)
                        postTranslate(
                            (PDF_PAGE_WIDTH - bitmap.width * scale) / 2f,
                            (PDF_PAGE_HEIGHT - bitmap.height * scale) / 2f,
                        )
                    }
                    page.canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            FileOutputStream(target).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return target.absolutePath
    }

    override fun pdfToImages(path: String, outputDir: String): List<String> {
        require(path.isNotBlank()) { "PDF 路径不能为空" }
        requireSharedStorageAccess(path)
        val source = File(path)
        require(source.exists()) { "PDF 不存在：$path" }
        val targetDir = if (outputDir.isNotBlank()) File(outputDir) else source.parentFile
        requireSharedStorageAccess(targetDir.absolutePath)
        targetDir.mkdirs()
        val outputs = mutableListOf<String>()
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val width = (page.width * PDF_RENDER_SCALE).roundToInt().coerceAtLeast(1)
                        val height = (page.height * PDF_RENDER_SCALE).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val target = File(targetDir, "${source.nameWithoutExtension}_page${index + 1}.png")
                        outputs += saveAndRecycle(bitmap, target)
                    }
                }
            }
        }
        return outputs
    }

    override fun pdfPageCount(path: String): Int {
        require(path.isNotBlank()) { "PDF 路径不能为空" }
        requireSharedStorageAccess(path)
        val source = File(path)
        require(source.exists()) { "PDF 不存在：$path" }
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> return renderer.pageCount }
        }
    }

    // ---- 内部工具 ----

    /** 解码为 ARGB_8888，并按 EXIF 方向转正；超大图按 2 的幂降采样，避免 OOM。 */
    private fun decode(path: String): Bitmap {
        require(path.isNotBlank()) { "图片路径不能为空" }
        requireSharedStorageAccess(path)
        val file = File(path)
        require(file.exists()) { "图片不存在：$path" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法解码图片：$path" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while ((bounds.outWidth / inSampleSize).toLong() * (bounds.outHeight / inSampleSize) > MAX_DECODE_PIXELS) {
                inSampleSize *= 2
            }
        }
        val decoded = BitmapFactory.decodeFile(path, options) ?: error("无法解码图片：$path")
        return applyExifOrientation(decoded, path)
    }

    private fun applyExifOrientation(bitmap: Bitmap, path: String): Bitmap {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { postRotate(90f) }
            ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { postRotate(180f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { postRotate(270f) }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { postScale(1f, -1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply {
                postScale(-1f, 1f)
                postRotate(90f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply {
                postScale(-1f, 1f)
                postRotate(270f)
            }
            else -> return bitmap
        }
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented !== bitmap) bitmap.recycle()
        return oriented
    }

    private inline fun <T> transform(path: String, block: (Bitmap) -> T): T {
        val source = decode(path)
        return try {
            block(source)
        } finally {
            source.recycle()
        }
    }

    private fun applyColorMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun saveAndRecycle(bitmap: Bitmap, target: File, quality: Int = 90): String = try {
        save(bitmap, target, quality)
    } finally {
        bitmap.recycle()
    }

    private fun save(bitmap: Bitmap, target: File, quality: Int = 90): String {
        requireSharedStorageAccess(target.absolutePath)
        target.parentFile?.mkdirs()
        val format = when (target.extension.lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
            "webp" -> webpFormat()
            else -> throw IllegalArgumentException("不支持的输出格式：.${target.extension}（支持 png/jpg/webp）")
        }
        val effectiveQuality = if (format == Bitmap.CompressFormat.PNG) 100 else quality.coerceIn(1, 100)
        FileOutputStream(target).use { stream ->
            if (!bitmap.compress(format, effectiveQuality, stream)) {
                throw IllegalStateException("图片编码失败：${target.absolutePath}")
            }
        }
        return target.absolutePath
    }

    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

    private fun outputFor(source: String, op: String, ext: String? = null, output: String = ""): File {
        val base = File(source)
        val target = if (output.isNotBlank()) {
            File(output)
        } else {
            File(base.parentFile, "${base.nameWithoutExtension}_$op.${ext ?: sourceExt(source)}")
        }
        requireSharedStorageAccess(target.absolutePath)
        return target
    }

    private fun sourceExt(path: String): String = when (val ext = File(path).extension.lowercase()) {
        "jpg", "jpeg" -> "jpg"
        "png" -> "png"
        "webp" -> "webp"
        else -> "png"
    }

    private fun normalizeFormat(format: String): String = when (format.trim().lowercase().removePrefix(".")) {
        "jpg", "jpeg" -> "jpg"
        "png" -> "png"
        "webp" -> "webp"
        else -> throw IllegalArgumentException("convert 只支持 png/jpg/webp，收到：$format")
    }

    private fun parseColor(hex: String, fallback: Int): Int =
        runCatching { Color.parseColor(hex.trim()) }.getOrDefault(fallback)

    private fun sharpenChannel(center: Int, left: Int, right: Int, up: Int, down: Int, amount: Float): Int =
        (center + amount * (4f * center - left - right - up - down)).roundToInt().coerceIn(0, 255)

    /** 三次方框模糊（行列分离）近似高斯；返回新数组，不修改入参。 */
    private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val r = radius.coerceIn(1, 100)
        var current = pixels
        val temp = IntArray(pixels.size)
        repeat(3) {
            blurRows(current, temp, width, height, r)
            blurColumns(temp, current, width, height, r)
        }
        return current
    }

    private fun blurRows(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = src[row + offset.coerceIn(0, width - 1)]
                alpha += (color ushr 24) and 0xFF
                red += (color shr 16) and 0xFF
                green += (color shr 8) and 0xFF
                blue += color and 0xFF
            }
            for (x in 0 until width) {
                dst[row + x] = ((alpha / window) shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
                val out = src[row + (x - radius).coerceIn(0, width - 1)]
                val inc = src[row + (x + radius + 1).coerceIn(0, width - 1)]
                alpha += ((inc ushr 24) and 0xFF) - ((out ushr 24) and 0xFF)
                red += ((inc shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                green += ((inc shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                blue += (inc and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun blurColumns(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (x in 0 until width) {
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = src[offset.coerceIn(0, height - 1) * width + x]
                alpha += (color ushr 24) and 0xFF
                red += (color shr 16) and 0xFF
                green += (color shr 8) and 0xFF
                blue += color and 0xFF
            }
            for (y in 0 until height) {
                dst[y * width + x] = ((alpha / window) shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
                val out = src[(y - radius).coerceIn(0, height - 1) * width + x]
                val inc = src[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                alpha += ((inc ushr 24) and 0xFF) - ((out ushr 24) and 0xFF)
                red += ((inc shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                green += ((inc shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                blue += (inc and 0xFF) - (out and 0xFF)
            }
        }
    }

    private companion object {
        const val MAX_DECODE_PIXELS = 24_000_000L
        const val PDF_PAGE_WIDTH = 595
        const val PDF_PAGE_HEIGHT = 842
        const val PDF_MARGIN = 24f
        const val PDF_RENDER_SCALE = 2f
    }
}
