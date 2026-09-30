package heizige.kk.khatkit.dependencies.imageToolbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
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
 * 本地图像工具箱依赖包入口：纯 Android SDK（Bitmap / Canvas / Paint / ColorMatrix / Matrix），
 * PDF 用 PdfDocument / PdfRenderer，无 javax.imageio、无网络、无云端依赖。
 *
 * 该实现不随应用编译，而是由 `:image-toolbox-dependency:buildDependencyDex` 打成
 * `image-toolbox-1.0.0.jar`（classes.dex + META-INF/khatkit-dependency.properties），
 * 随云端卡片从 Hub 下载、校验 sha256 后由宿主 DexClassLoader 加载为 `imageToolbox` bridge。
 *
 * - 输入解码后先按 EXIF 转正（输出不写回 EXIF，因此重编码即丢元数据）；
 * - 输出缺省写到源文件同目录 `<名字>_<操作>.<扩展名>`，也接受显式输出路径；
 * - /sdcard 等共享存储路径沿用宿主同款「所有文件访问」权限校验；
 * - 失败抛带中文说明的异常，引擎会编码为 `{"__error":"..."}`。
 *
 * 除既有类型化方法外，另提供四个分组派发方法覆盖 ImageToolbox 的更多能力：
 * [process]（滤镜/效果/几何）、[compose]（拼图/堆叠/混合）、[analyze]（信息/直方图/主色/取色/EXIF/ASCII）、
 * [pdfEdit]（PDF 旋转/重排/抽取/删除/N-up/压缩/合并）。
 */
class ImageToolboxDependency(@Suppress("UNUSED_PARAMETER") context: Context) : ImageToolboxBridge {

    // ══════════════ 既有类型化 API ══════════════

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
            saveAndRecycle(ToolboxIO.applyColorMatrix(src, ColorMatrix()), outputFor(path, "rotated"))
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
        saveAndRecycle(ToolboxIO.applyColorMatrix(src, matrix), outputFor(path, "grayscale"))
    }

    override fun blur(path: String, radius: Int): String = transform(path) { src ->
        require(radius > 0) { "blur 的 radius 必须大于 0（收到 $radius）" }
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        val blurred = ToolboxFilter.boxBlur(pixels, width, height, radius)
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
        saveAndRecycle(ToolboxIO.applyColorMatrix(src, matrix), outputFor(path, "brightness_contrast"))
    }

    override fun saturation(path: String, factor: Float): String = transform(path) { src ->
        val matrix = ColorMatrix().apply { setSaturation(factor.coerceIn(0f, 10f)) }
        saveAndRecycle(ToolboxIO.applyColorMatrix(src, matrix), outputFor(path, "saturated"))
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
        saveAndRecycle(ToolboxIO.applyColorMatrix(src, matrix), outputFor(path, "hue"))
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
        saveAndRecycle(ToolboxIO.applyColorMatrix(src, matrix), outputFor(path, "auto_contrast"))
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
        saveAndRecycle(ToolboxIO.applyColorMatrix(src, matrix), outputFor(path, "inverted"))
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
        saveAndRecycle(ToolboxIO.applyColorMatrix(src, matrix), outputFor(path, "sepia"))
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
            color = ToolboxIO.parseColor(colorHex, Color.WHITE)
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
        canvas.drawColor(ToolboxIO.parseColor(colorHex, Color.WHITE))
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
        saveAndRecycle(output, outputFor(path, "rounded", "png"))
    }

    override fun convert(path: String, format: String, quality: Int): String = transform(path) { src ->
        save(src, outputFor(path, "converted", ToolboxIO.normalizeFormat(format)), quality)
    }

    override fun stripMetadata(path: String): String = transform(path) { src ->
        save(src, outputFor(path, "stripped", ToolboxIO.sourceExt(path)))
    }

    override fun imagesToPdf(paths: List<String>, output: String): String {
        require(paths.isNotEmpty()) { "imagesToPdf 需要至少一张图片" }
        paths.forEach { path ->
            ToolboxIO.requireSharedStorageAccess(path)
            require(File(path).exists()) { "图片不存在：$path" }
        }
        val first = File(paths.first())
        val target = if (output.isNotBlank()) File(output)
        else File(first.parentFile, "${first.nameWithoutExtension}_images.pdf")
        ToolboxIO.requireSharedStorageAccess(target.absolutePath)
        target.parentFile?.mkdirs()
        val document = PdfDocument()
        try {
            paths.forEachIndexed { index, path ->
                val bitmap = ToolboxIO.decode(path)
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(ToolboxIO.PDF_PAGE_WIDTH, ToolboxIO.PDF_PAGE_HEIGHT, index + 1).create()
                    val page = document.startPage(pageInfo)
                    page.canvas.drawColor(Color.WHITE)
                    val scale = min(
                        (ToolboxIO.PDF_PAGE_WIDTH - ToolboxIO.PDF_MARGIN * 2) / bitmap.width.toFloat(),
                        (ToolboxIO.PDF_PAGE_HEIGHT - ToolboxIO.PDF_MARGIN * 2) / bitmap.height.toFloat(),
                    )
                    val matrix = Matrix().apply {
                        postScale(scale, scale)
                        postTranslate(
                            (ToolboxIO.PDF_PAGE_WIDTH - bitmap.width * scale) / 2f,
                            (ToolboxIO.PDF_PAGE_HEIGHT - bitmap.height * scale) / 2f,
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
        ToolboxIO.requireSharedStorageAccess(path)
        val source = File(path)
        require(source.exists()) { "PDF 不存在：$path" }
        val targetDir = if (outputDir.isNotBlank()) File(outputDir) else source.parentFile
        ToolboxIO.requireSharedStorageAccess(targetDir.absolutePath)
        targetDir.mkdirs()
        val outputs = mutableListOf<String>()
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val width = (page.width * ToolboxIO.PDF_RENDER_SCALE).roundToInt().coerceAtLeast(1)
                        val height = (page.height * ToolboxIO.PDF_RENDER_SCALE).roundToInt().coerceAtLeast(1)
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
        ToolboxIO.requireSharedStorageAccess(path)
        val source = File(path)
        require(source.exists()) { "PDF 不存在：$path" }
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> return renderer.pageCount }
        }
    }

    // ══════════════ 依赖包扩展：分组派发 ══════════════

    /** 单图滤镜/效果/几何：[op] 见 docs/image-toolbox.md，[paramsJson] 为参数对象 JSON。 */
    override fun process(path: String, op: String, paramsJson: String): String {
        val normalized = op.trim().lowercase().replace('-', '_')
        require(normalized.isNotEmpty()) { "process 需要操作名（op）" }
        val params = ToolboxParams.parse(paramsJson)
        val source = ToolboxIO.decode(path)
        val ext = if (normalized in TRANSPARENT_OPS) "png" else null
        val target = outputFor(path, normalized, ext, ToolboxParams.str(params, "output", ""))
        val quality = ToolboxParams.int(params, "quality", 92)
        return try {
            val result = when {
                ToolboxFilter.isPreset(normalized) -> ToolboxFilter.preset(source, normalized)
                ToolboxGeometry.isGeometryOp(normalized) -> ToolboxGeometry.apply(source, normalized, params)
                else -> ToolboxFilter.apply(source, normalized, params)
            }
            saveAndRecycle(result, target, quality)
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    /** 多图合成：[inputsJson] 为输入路径 JSON 数组，[paramsJson] 为参数对象 JSON。 */
    override fun compose(op: String, inputsJson: String, paramsJson: String): String {
        val normalized = op.trim().lowercase().replace('-', '_')
        val inputs = ToolboxParams.parseArray(inputsJson)
        require(inputs.isNotEmpty()) { "compose 需要至少一张输入图片" }
        require(ToolboxCompose.isComposeOp(normalized)) { "不支持的合成操作：$normalized" }
        val params = ToolboxParams.parse(paramsJson)
        val target = outputFor(inputs.first(), normalized, "png", ToolboxParams.str(params, "output", ""))
        return ToolboxCompose.apply(normalized, inputs, params, target)
    }

    /** 只读分析：[query] 见 docs/image-toolbox.md，返回 JSON 字符串（绝不写文件）。 */
    override fun analyze(path: String, query: String, paramsJson: String): String {
        val normalized = query.trim().lowercase().replace('-', '_')
        require(ToolboxAnalyze.isAnalyzeQuery(normalized)) { "不支持的分析：$query" }
        return ToolboxAnalyze.analyze(path, normalized, ToolboxParams.parse(paramsJson))
    }

    /** PDF 编辑：[source] 为 PDF 路径，返回输出 PDF 路径。 */
    override fun pdfEdit(op: String, source: String, paramsJson: String): String {
        val normalized = op.trim().lowercase().replace('-', '_')
        require(ToolboxPdf.isPdfEditOp(normalized)) { "不支持的 PDF 操作：$op" }
        val params = ToolboxParams.parse(paramsJson)
        val target = outputFor(source, "pdf_$normalized", "pdf", ToolboxParams.str(params, "output", ""))
        return ToolboxPdf.apply(normalized, source, params, target)
    }

    // ══════════════ 内部工具 ══════════════

    private inline fun <T> transform(path: String, block: (Bitmap) -> T): T {
        val source = ToolboxIO.decode(path)
        return try {
            block(source)
        } finally {
            source.recycle()
        }
    }

    private fun saveAndRecycle(bitmap: Bitmap, target: File, quality: Int = 90): String = try {
        save(bitmap, target, quality)
    } finally {
        bitmap.recycle()
    }

    private fun save(bitmap: Bitmap, target: File, quality: Int = 90): String =
        ToolboxIO.save(bitmap, target, quality)

    private fun outputFor(source: String, op: String, ext: String? = null, output: String = ""): File =
        ToolboxIO.outputFor(source, op, ext, output)

    private fun sharpenChannel(center: Int, left: Int, right: Int, up: Int, down: Int, amount: Float): Int =
        (center + amount * (4f * center - left - right - up - down)).roundToInt().coerceIn(0, 255)

    private companion object {
        val TRANSPARENT_OPS = setOf(
            "outline", "opacity", "alpha", "drop_shadow", "tint_transparent", "round_corners",
        )
    }
}
