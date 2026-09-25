package heizige.kk.khatkit.dependencies.imageToolbox

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * PDF 编辑：逐页渲染后重建 PDF（旋转/重排/抽取/删除/N-up/重采样压缩/合并）。
 *
 * 说明：纯 Android SDK 只能通过 `PdfRenderer` 光栅化再 `PdfDocument` 重建，
 * 因此编辑后的 PDF 文字会转成位图（不可再选中），体积也会随渲染比例变化。
 */
internal object ToolboxPdf {

    fun isPdfEditOp(op: String): Boolean = op in setOf(
        "rotate", "reorder", "extract", "delete", "nup", "compress", "merge",
    )

    fun apply(op: String, source: String, p: Map<String, Any?>, target: File): String {
        require(source.isNotBlank()) { "PDF 路径不能为空" }
        ToolboxIO.requireSharedStorageAccess(source)
        require(File(source).exists()) { "PDF 不存在：$source" }
        val document = PdfDocument()
        try {
            when (op) {
                "rotate" -> {
                    val degrees = ToolboxParams.num(p, "degrees", ToolboxParams.num(p, "angle", 90.0))
                    withPages(source, null) { bitmap ->
                        addPage(document, rotate(bitmap, degrees))
                    }
                }
                "reorder" -> withPages(source, pageIndices(p, pageCount(source))) { bitmap ->
                    addPage(document, bitmap)
                }
                "extract" -> withPages(source, pageIndices(p, pageCount(source))) { bitmap ->
                    addPage(document, bitmap)
                }
                "delete" -> {
                    val remove = pageIndices(p, pageCount(source)).toSet()
                    val keep = (0 until pageCount(source)).filterNot { it in remove }
                    require(keep.isNotEmpty()) { "delete 不能删除全部页面" }
                    withPages(source, keep) { bitmap -> addPage(document, bitmap) }
                }
                "nup" -> nup(source, p, document)
                "compress" -> {
                    val scale = ToolboxParams.num(p, "scale", 0.7).coerceIn(0.1, 1.0)
                    val grayscale = ToolboxParams.bool(p, "grayscale", false)
                    withPages(source, null, scale) { bitmap ->
                        val page = if (grayscale) toGrayscale(bitmap) else bitmap
                        addPage(document, page)
                        if (page !== bitmap) page.recycle()
                    }
                }
                "merge" -> {
                    val files = (listOf(source) + ToolboxParams.strings(p, "files")).filter { it.isNotBlank() }
                    require(files.size >= 2) { "merge 至少需要两个 PDF（source + params.files）" }
                    files.forEach { path ->
                        ToolboxIO.requireSharedStorageAccess(path)
                        require(File(path).exists()) { "PDF 不存在：$path" }
                        withPages(path, null) { bitmap -> addPage(document, bitmap) }
                    }
                }
                else -> error("不支持的 PDF 操作：$op")
            }
            ToolboxIO.requireSharedStorageAccess(target.absolutePath)
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return target.absolutePath
    }

    private fun pageCount(path: String): Int {
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> return renderer.pageCount }
        }
    }

    /** 按 [indices]（0 基）渲染；null 表示全部页；[scale] 为渲染缩放。 */
    private fun withPages(
        path: String,
        indices: List<Int>?,
        scale: Double = 1.0,
        block: (Bitmap) -> Unit,
    ) {
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val selected = indices?.filter { it in 0 until renderer.pageCount }
                    ?: (0 until renderer.pageCount).toList()
                for (index in selected) {
                    renderer.openPage(index).use { page ->
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        try {
                            block(bitmap)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }

    private fun addPage(document: PdfDocument, bitmap: Bitmap) {
        val landscape = bitmap.width > bitmap.height
        val pageWidth = if (landscape) ToolboxIO.PDF_PAGE_HEIGHT else ToolboxIO.PDF_PAGE_WIDTH
        val pageHeight = if (landscape) ToolboxIO.PDF_PAGE_WIDTH else ToolboxIO.PDF_PAGE_HEIGHT
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create()
        val page = document.startPage(pageInfo)
        page.canvas.drawColor(Color.WHITE)
        val scale = min(
            (pageWidth - ToolboxIO.PDF_MARGIN * 2) / bitmap.width.toFloat(),
            (pageHeight - ToolboxIO.PDF_MARGIN * 2) / bitmap.height.toFloat(),
        )
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(
                (pageWidth - bitmap.width * scale) / 2f,
                (pageHeight - bitmap.height * scale) / 2f,
            )
        }
        page.canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        document.finishPage(page)
    }

    private fun rotate(source: Bitmap, degrees: Double): Bitmap {
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        if (abs(normalized) < 0.01) {
            return source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        }
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun toGrayscale(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val lum = ((color shr 16 and 0xFF) * 299 + (color shr 8 and 0xFF) * 587 + (color and 0xFF) * 114) / 1000
            pixels[index] = (color and 0xFF000000.toInt()) or (lum shl 16) or (lum shl 8) or lum
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun nup(source: String, p: Map<String, Any?>, document: PdfDocument) {
        val perSheet = ToolboxParams.int(p, "pages_per_sheet", ToolboxParams.int(p, "per_sheet", 4))
            .let { if (it == 2 || it == 4 || it == 6 || it == 9) it else 4 }
        val columns = when (perSheet) {
            2 -> 2
            6 -> 3
            9 -> 3
            else -> 2
        }
        val rows = perSheet / columns
        val gap = ToolboxParams.num(p, "gap", 8.0).coerceIn(0.0, 64.0).toFloat()
        val pageCount = pageCount(source)
        val all = (0 until pageCount).toList()
        val pages: List<Bitmap> = run {
            val collected = mutableListOf<Bitmap>()
            withPages(source, all) { bitmap ->
                collected += bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    ?: error("无法复制 PDF 页面位图")
            }
            collected
        }
        try {
            pages.chunked(perSheet).forEach { chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(ToolboxIO.PDF_PAGE_WIDTH, ToolboxIO.PDF_PAGE_HEIGHT, document.pages.size + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                val cellWidth = (ToolboxIO.PDF_PAGE_WIDTH - gap * (columns + 1)) / columns
                val cellHeight = (ToolboxIO.PDF_PAGE_HEIGHT - gap * (rows + 1)) / rows
                chunk.forEachIndexed { index, bitmap ->
                    val column = index % columns
                    val row = index / columns
                    val left = gap + column * (cellWidth + gap)
                    val top = gap + row * (cellHeight + gap)
                    val scale = min(cellWidth / bitmap.width.toFloat(), cellHeight / bitmap.height.toFloat())
                    val matrix = Matrix().apply {
                        postScale(scale, scale)
                        postTranslate(
                            left + (cellWidth - bitmap.width * scale) / 2f,
                            top + (cellHeight - bitmap.height * scale) / 2f,
                        )
                    }
                    page.canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                }
                document.finishPage(page)
            }
        } finally {
            pages.forEach { it.recycle() }
        }
    }

    /** 解析页码：接受 `"1-3,5"` 字符串或 `[1,3,5]` 数组，返回 0 基下标。 */
    private fun pageIndices(p: Map<String, Any?>, pageCount: Int): List<Int> {
        val spec = p["pages"] ?: p["page_range"] ?: p["range"]
        val result = linkedSetOf<Int>()
        when (spec) {
            is List<*> -> spec.forEach { item ->
                item?.toString()?.toIntOrNull()?.let { result += it - 1 }
            }
            is String -> spec.split(',', '，', ' ').forEach { token ->
                val text = token.trim()
                if (text.isEmpty()) return@forEach
                if (text.contains('-')) {
                    val parts = text.split('-')
                    val from = parts.getOrNull(0)?.trim()?.toIntOrNull()
                    val to = parts.getOrNull(1)?.trim()?.toIntOrNull()
                    if (from != null && to != null) {
                        val range = if (from <= to) from..to else to..from
                        range.take(1000).forEach { result += it - 1 }
                    }
                } else {
                    text.toIntOrNull()?.let { result += it - 1 }
                }
            }
            else -> Unit
        }
        val valid = result.filter { it in 0 until pageCount }
        require(valid.isNotEmpty()) { "pages 参数无效：$spec（示例 \"1-3,5\" 或 [1,3,5]）" }
        return valid
    }
}
