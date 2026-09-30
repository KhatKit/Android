package heizige.kk.khatkit.dependencies.imageToolbox

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.os.Build
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** 共享的位图读写 / 存储权限 / 参数解析工具（纯 Android SDK）。 */
internal object ToolboxIO {

    const val MAX_DECODE_PIXELS = 24_000_000L
    const val PDF_PAGE_WIDTH = 595
    const val PDF_PAGE_HEIGHT = 842
    const val PDF_MARGIN = 24f
    const val PDF_RENDER_SCALE = 2f

    /**
     * 共享存储权限校验（与宿主 bridge 同一文案）：应用私有目录放行，
     * /sdcard、/storage 等未授权时抛出可操作的中文错误。
     */
    fun requireSharedStorageAccess(vararg paths: String) {
        if (isAllFilesGranted()) return
        val blocked = paths.firstOrNull { path ->
            path.startsWith("/sdcard") ||
                path.startsWith("/storage") ||
                path.startsWith("/mnt/sdcard")
        } ?: return
        throw SecurityException(
            "无共享存储访问权限：$blocked\n" +
                "请在 KhatKit 卡片市场 → 设置里授予「所有文件访问」"
        )
    }

    fun isAllFilesGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /** 解码为 ARGB_8888，并按 EXIF 方向转正；超大图按 2 的幂降采样，避免 OOM。 */
    fun decode(path: String): Bitmap {
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

    fun applyExifOrientation(bitmap: Bitmap, path: String): Bitmap {
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

    fun applyColorMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return output
    }

    fun saveAndRecycle(bitmap: Bitmap, target: File, quality: Int = 90): String = try {
        save(bitmap, target, quality)
    } finally {
        bitmap.recycle()
    }

    fun save(bitmap: Bitmap, target: File, quality: Int = 90): String {
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

    fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

    fun outputFor(source: String, op: String, ext: String? = null, output: String = ""): File {
        val base = File(source)
        val target = if (output.isNotBlank()) {
            File(output)
        } else {
            File(base.parentFile, "${base.nameWithoutExtension}_$op.${ext ?: sourceExt(source)}")
        }
        requireSharedStorageAccess(target.absolutePath)
        return target
    }

    fun sourceExt(path: String): String = when (val ext = File(path).extension.lowercase()) {
        "jpg", "jpeg" -> "jpg"
        "png" -> "png"
        "webp" -> "webp"
        else -> "png"
    }

    fun normalizeFormat(format: String): String = when (format.trim().lowercase().removePrefix(".")) {
        "jpg", "jpeg" -> "jpg"
        "png" -> "png"
        "webp" -> "webp"
        else -> throw IllegalArgumentException("convert 只支持 png/jpg/webp，收到：$format")
    }

    fun parseColor(hex: String, fallback: Int): Int =
        runCatching { android.graphics.Color.parseColor(hex.trim()) }.getOrDefault(fallback)
}

/** 轻量 JSON 参数解析（org.json 随 Android SDK 提供，无需额外依赖）。 */
internal object ToolboxParams {

    fun parse(json: String): Map<String, Any?> {
        if (json.isBlank()) return emptyMap()
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        val map = LinkedHashMap<String, Any?>()
        obj.keys().forEach { key -> map[key] = unwrap(obj.get(key)) }
        return map
    }

    fun parseArray(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { index -> runCatching { arr.getString(index) }.getOrNull() }
            .filter { it.isNotBlank() }
    }

    private fun unwrap(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> LinkedHashMap<String, Any?>().apply {
            value.keys().forEach { key -> put(key, unwrap(value.get(key))) }
        }
        is JSONArray -> (0 until value.length()).map { unwrap(value.get(it)) }
        else -> value
    }

    fun num(p: Map<String, Any?>, key: String, fallback: Double): Double = when (val value = p[key]) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: fallback
        is Boolean -> if (value) 1.0 else 0.0
        else -> fallback
    }

    fun int(p: Map<String, Any?>, key: String, fallback: Int): Int = num(p, key, fallback.toDouble()).toInt()

    fun str(p: Map<String, Any?>, key: String, fallback: String): String = when (val value = p[key]) {
        is String -> value.takeIf { it.isNotBlank() } ?: fallback
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> fallback
    }

    fun bool(p: Map<String, Any?>, key: String, fallback: Boolean): Boolean = when (val value = p[key]) {
        is Boolean -> value
        is String -> when (value.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> fallback
        }
        is Number -> value.toInt() != 0
        else -> fallback
    }

    fun strings(p: Map<String, Any?>, key: String): List<String> = when (val value = p[key]) {
        is List<*> -> value.mapNotNull { it?.toString()?.takeIf { text -> text.isNotBlank() } }
        is String -> value.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }
}
