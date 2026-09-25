package heizige.kk.khatkit.dependencies.imageToolbox

import android.graphics.Bitmap
import android.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 只读分析：图像信息、直方图、主色/调色板、坐标取色、平均色、EXIF、ASCII 字符画。
 * 不写任何文件，统一返回 JSON 字符串。
 */
internal object ToolboxAnalyze {

    fun isAnalyzeQuery(query: String): Boolean = query in setOf(
        "info", "histogram", "dominant", "palette", "pick", "color_at", "average", "exif", "ascii",
    )

    fun analyze(path: String, query: String, p: Map<String, Any?>): String {
        val bitmap = ToolboxIO.decode(path)
        try {
            return when (query) {
                "info" -> info(path, bitmap)
                "histogram" -> histogram(bitmap)
                "dominant", "palette" -> dominant(bitmap, p)
                "pick", "color_at" -> pick(bitmap, p)
                "average" -> average(bitmap)
                "exif" -> exif(path)
                "ascii" -> ascii(bitmap, p)
                else -> error("不支持的分析：$query（info/histogram/dominant/palette/pick/color_at/average/exif/ascii）")
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun info(path: String, bitmap: Bitmap): String = JSONObject().apply {
        put("path", path)
        put("width", bitmap.width)
        put("height", bitmap.height)
        put("megapixels", (bitmap.width.toLong() * bitmap.height / 1_000_000.0 * 100).roundToInt() / 100.0)
        put("aspect", ((bitmap.width.toDouble() / bitmap.height) * 1000).roundToInt() / 1000.0)
        put("format", ToolboxIO.sourceExt(path))
        put("size_bytes", File(path).length())
        put("has_alpha", bitmap.hasAlpha())
        put("config", bitmap.config?.name ?: "UNKNOWN")
    }.toString()

    private fun histogram(bitmap: Bitmap): String {
        val red = IntArray(256)
        val green = IntArray(256)
        val blue = IntArray(256)
        val luminance = IntArray(256)
        val pixels = pixelsOf(bitmap)
        for (color in pixels) {
            val r = color shr 16 and 0xFF
            val g = color shr 8 and 0xFF
            val b = color and 0xFF
            red[r]++
            green[g]++
            blue[b]++
            luminance[(r * 299 + g * 587 + b * 114) / 1000]++
        }
        return JSONObject().apply {
            put("red", JSONArray(red.toList()))
            put("green", JSONArray(green.toList()))
            put("blue", JSONArray(blue.toList()))
            put("luminance", JSONArray(luminance.toList()))
        }.toString()
    }

    private fun dominant(bitmap: Bitmap, p: Map<String, Any?>): String {
        val count = ToolboxParams.int(p, "count", ToolboxParams.int(p, "colors", 8)).coerceIn(1, 64)
        val quantize = ToolboxParams.int(p, "quantize", 5).coerceIn(1, 7)
        val shift = 8 - quantize
        val buckets = HashMap<Int, LongArray>()
        for (color in pixelsOf(bitmap)) {
            val r = color shr 16 and 0xFF
            val g = color shr 8 and 0xFF
            val b = color and 0xFF
            val key = ((r shr shift) shl (quantize * 2)) or ((g shr shift) shl quantize) or (b shr shift)
            val bucket = buckets.getOrPut(key) { LongArray(4) }
            bucket[0] += r
            bucket[1] += g
            bucket[2] += b
            bucket[3]++
        }
        val total = (bitmap.width.toLong() * bitmap.height).coerceAtLeast(1)
        val sorted = buckets.values.sortedByDescending { it[3] }.take(count)
        val colors = JSONArray()
        for (bucket in sorted) {
            val samples = bucket[3]
            val r = (bucket[0] / samples).toInt().coerceIn(0, 255)
            val g = (bucket[1] / samples).toInt().coerceIn(0, 255)
            val b = (bucket[2] / samples).toInt().coerceIn(0, 255)
            colors.put(
                JSONObject().apply {
                    put("hex", String.format("#%02X%02X%02X", r, g, b))
                    put("r", r)
                    put("g", g)
                    put("b", b)
                    put("count", samples)
                    put("ratio", (samples.toDouble() / total * 10000).roundToInt() / 10000.0)
                }
            )
        }
        return JSONObject().apply {
            put("count", colors.length())
            put("colors", colors)
        }.toString()
    }

    private fun pick(bitmap: Bitmap, p: Map<String, Any?>): String {
        val x = ToolboxParams.int(p, "x", ToolboxParams.int(p, "px", -1))
        val y = ToolboxParams.int(p, "y", ToolboxParams.int(p, "py", -1))
        require(x >= 0 && y >= 0 && x < bitmap.width && y < bitmap.height) {
            "取色坐标超出图片范围：($x, $y)，图片 ${bitmap.width}x${bitmap.height}"
        }
        val color = bitmap.getPixel(x, y)
        return JSONObject().apply {
            put("x", x)
            put("y", y)
            put("hex", String.format("#%08X", color))
            put("r", color shr 16 and 0xFF)
            put("g", color shr 8 and 0xFF)
            put("b", color and 0xFF)
            put("a", color ushr 24)
        }.toString()
    }

    private fun average(bitmap: Bitmap): String {
        var r = 0L
        var g = 0L
        var b = 0L
        var a = 0L
        val pixels = pixelsOf(bitmap)
        for (color in pixels) {
            r += color shr 16 and 0xFF
            g += color shr 8 and 0xFF
            b += color and 0xFF
            a += color ushr 24
        }
        val count = pixels.size.coerceAtLeast(1)
        val ar = (r / count).toInt()
        val ag = (g / count).toInt()
        val ab = (b / count).toInt()
        val aa = (a / count).toInt()
        return JSONObject().apply {
            put("hex", String.format("#%02X%02X%02X", ar, ag, ab))
            put("r", ar)
            put("g", ag)
            put("b", ab)
            put("a", aa)
        }.toString()
    }

    private fun exif(path: String): String {
        val exif = runCatching { ExifInterface(path) }.getOrNull()
            ?: error("无法读取 EXIF：$path")
        val tags = linkedMapOf<String, String>(
            "make" to ExifInterface.TAG_MAKE,
            "model" to ExifInterface.TAG_MODEL,
            "datetime" to ExifInterface.TAG_DATETIME,
            "orientation" to ExifInterface.TAG_ORIENTATION,
            "exposure_time" to ExifInterface.TAG_EXPOSURE_TIME,
            "f_number" to ExifInterface.TAG_F_NUMBER,
            "iso" to ExifInterface.TAG_ISO_SPEED_RATINGS,
            "focal_length" to ExifInterface.TAG_FOCAL_LENGTH,
            "flash" to ExifInterface.TAG_FLASH,
            "white_balance" to ExifInterface.TAG_WHITE_BALANCE,
            "image_width" to ExifInterface.TAG_IMAGE_WIDTH,
            "image_length" to ExifInterface.TAG_IMAGE_LENGTH,
            "gps_latitude" to ExifInterface.TAG_GPS_LATITUDE,
            "gps_longitude" to ExifInterface.TAG_GPS_LONGITUDE,
        )
        return JSONObject().apply {
            tags.forEach { (key, tag) ->
                val value = runCatching { exif.getAttribute(tag) }.getOrNull()
                if (!value.isNullOrBlank()) put(key, value)
            }
        }.toString()
    }

    private fun ascii(bitmap: Bitmap, p: Map<String, Any?>): String {
        val width = ToolboxParams.int(p, "width", ToolboxParams.int(p, "cols", 80)).coerceIn(16, 400)
        val charset = ToolboxParams.str(p, "charset", " .:-=+*#%@").ifBlank { " .:-=+*#%@" }
        val invert = ToolboxParams.bool(p, "invert", false)
        val height = max(1, (width.toDouble() * bitmap.height / bitmap.width * 0.5).roundToInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val builder = StringBuilder()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixels[y * width + x]
                val lum = ((color shr 16 and 0xFF) * 299 + (color shr 8 and 0xFF) * 587 + (color and 0xFF) * 114) / 1000
                val normalized = if (invert) lum / 255.0 else 1.0 - lum / 255.0
                val index = (normalized * (charset.length - 1)).roundToInt().coerceIn(0, charset.length - 1)
                builder.append(charset[index])
            }
            builder.append('\n')
        }
        if (scaled !== bitmap) scaled.recycle()
        return JSONObject().apply {
            put("width", width)
            put("height", height)
            put("charset", charset)
            put("ascii", builder.toString())
        }.toString()
    }

    private fun pixelsOf(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }
}
