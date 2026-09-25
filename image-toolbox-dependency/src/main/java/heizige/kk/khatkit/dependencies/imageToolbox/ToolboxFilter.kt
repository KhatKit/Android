package heizige.kk.khatkit.dependencies.imageToolbox

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.Paint
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 单图滤镜 / 效果实现（纯 Bitmap 像素运算）。
 *
 * 覆盖 ImageToolbox 中可用纯 Android SDK 复刻的部分：
 * 色彩滤镜与预设、gamma/levels/曲线近似、阈值/色调分离/日光反转、颤音/胶片颗粒、
 * 浮雕/边缘/拉普拉斯、油画/水彩/素描近似、半色调/交叉阴影、故障风/像素排序、
 * 抖动、降噪、辉光、运动模糊等。
 */
internal object ToolboxFilter {

    // ══════════════ 预设（ColorMatrix 组合） ══════════════

    private data class Preset(
        val sat: Float = 1f,
        val rMul: Float = 1f,
        val gMul: Float = 1f,
        val bMul: Float = 1f,
        val rAdd: Float = 0f,
        val gAdd: Float = 0f,
        val bAdd: Float = 0f,
        val contrast: Float = 1f,
    )

    private val PRESETS: Map<String, Preset> = mapOf(
        "warm" to Preset(sat = 1.1f, rMul = 1.12f, bMul = 0.9f, rAdd = 10f),
        "cool" to Preset(sat = 1.05f, rMul = 0.92f, bMul = 1.12f, bAdd = 10f),
        "vintage" to Preset(sat = 0.75f, rMul = 1.08f, gMul = 1.02f, bMul = 0.9f, rAdd = 12f, gAdd = 6f, contrast = 1.05f),
        "retro" to Preset(sat = 0.85f, rMul = 1.05f, bMul = 0.85f, rAdd = 18f, gAdd = 8f, contrast = 1.1f),
        "film" to Preset(sat = 0.92f, rMul = 1.05f, bMul = 0.95f, rAdd = 8f, bAdd = -4f, contrast = 1.08f),
        "polaroid" to Preset(sat = 0.9f, rMul = 1.05f, gMul = 1.03f, bMul = 1.08f, gAdd = 12f, contrast = 1.12f),
        "lomo" to Preset(sat = 1.25f, rAdd = 6f, bAdd = -8f, contrast = 1.25f),
        "kodak" to Preset(sat = 1.05f, rMul = 1.08f, gMul = 1.02f, bMul = 0.92f, contrast = 1.06f),
        "fuji" to Preset(sat = 1.08f, rMul = 0.98f, gMul = 1.06f, bMul = 1.02f, contrast = 1.04f),
        "gotham" to Preset(sat = 0.4f, bMul = 1.1f, rAdd = -10f, contrast = 1.3f),
        "cyberpunk" to Preset(sat = 1.3f, gMul = 0.95f, rAdd = 12f, bAdd = 24f, contrast = 1.15f),
        "coffee" to Preset(sat = 0.8f, rMul = 1.1f, bMul = 0.85f, rAdd = 14f, gAdd = 4f),
        "golden_hour" to Preset(sat = 1.1f, rMul = 1.15f, gMul = 1.05f, bMul = 0.82f, rAdd = 16f),
        "pink_dream" to Preset(sat = 1.05f, rMul = 1.1f, gMul = 0.94f, bMul = 1.08f, rAdd = 10f, bAdd = 12f),
        "purple_mist" to Preset(sat = 0.95f, rMul = 1.06f, gMul = 0.95f, bMul = 1.12f, contrast = 1.04f),
        "sunrise" to Preset(sat = 1.12f, rMul = 1.14f, gMul = 0.98f, bMul = 0.9f, rAdd = 12f, gAdd = 6f),
        "autumn" to Preset(sat = 1.1f, rMul = 1.18f, bMul = 0.78f, contrast = 1.05f),
        "winter" to Preset(sat = 0.9f, rMul = 0.96f, gMul = 1.04f, bMul = 1.12f, bAdd = 8f, contrast = 1.06f),
        "ocean" to Preset(sat = 1.1f, rMul = 0.9f, gMul = 1.06f, bMul = 1.16f, contrast = 1.05f),
        "neon" to Preset(sat = 1.5f, rAdd = 8f, bAdd = 16f, contrast = 1.2f),
        "noir" to Preset(sat = 0f, bAdd = -6f, contrast = 1.35f),
        "pastel" to Preset(sat = 0.8f, rAdd = 18f, gAdd = 14f, bAdd = 10f, contrast = 0.9f),
        "fade" to Preset(sat = 0.85f, rAdd = 20f, gAdd = 16f, bAdd = 12f, contrast = 0.9f),
        "bleach_bypass" to Preset(sat = 0.55f, rAdd = 10f, gAdd = 6f, bAdd = 6f, contrast = 1.3f),
        "candlelight" to Preset(sat = 0.95f, rMul = 1.15f, gMul = 1.02f, bMul = 0.8f, rAdd = 18f, gAdd = 6f),
        "old_tv" to Preset(sat = 0.7f, gMul = 1.05f, rAdd = 10f, bAdd = -12f, contrast = 1.15f),
        "night_vision" to Preset(sat = 0.2f, gMul = 1.6f, gAdd = 20f, contrast = 1.2f),
        "pop_art" to Preset(sat = 1.6f, rAdd = 12f, gAdd = -6f, bAdd = 8f, contrast = 1.25f),
        "celluloid" to Preset(sat = 0.85f, rMul = 1.06f, gMul = 1.0f, bMul = 0.9f, rAdd = 10f, contrast = 1.08f),
        "fall_colors" to Preset(sat = 1.15f, rMul = 1.2f, gMul = 1.0f, bMul = 0.75f, contrast = 1.05f),
        "greenish" to Preset(sat = 1.0f, rMul = 0.9f, gMul = 1.12f, bMul = 0.92f),
    )

    fun isPreset(name: String): Boolean = PRESETS.containsKey(name)

    fun preset(source: Bitmap, name: String): Bitmap {
        val preset = PRESETS[name] ?: error("未知预设：$name")
        val saturation = ColorMatrix().apply { setSaturation(preset.sat) }
        val channels = ColorMatrix(
            floatArrayOf(
                preset.rMul, 0f, 0f, 0f, preset.rAdd,
                0f, preset.gMul, 0f, 0f, preset.gAdd,
                0f, 0f, preset.bMul, 0f, preset.bAdd,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        val c = preset.contrast
        val shift = 128f * (1f - c)
        val contrast = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, shift,
                0f, c, 0f, 0f, shift,
                0f, 0f, c, 0f, shift,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        return ColorMatrix(saturation).apply {
            postConcat(channels)
            postConcat(contrast)
        }.let { ToolboxIO.applyColorMatrix(source, it) }
    }

    // ══════════════ 统一入口 ══════════════

    fun apply(source: Bitmap, op: String, p: Map<String, Any?>): Bitmap = when (op) {
        "gamma" -> lut(source) { v ->
            val gamma = ToolboxParams.num(p, "gamma", ToolboxParams.num(p, "value", 2.2)).coerceIn(0.05, 10.0)
            (255.0 * (v / 255.0).pow(1.0 / gamma)).roundToInt()
        }

        "levels" -> levels(source, p)
        "curves" -> curves(source, p)
        "exposure" -> lut(source) { v ->
            val factor = 2.0.pow(ToolboxParams.num(p, "ev", 0.0).coerceIn(-4.0, 4.0))
            (v * factor).roundToInt()
        }

        "temperature" -> channelShift(
            source,
            rShift = ToolboxParams.num(p, "amount", ToolboxParams.num(p, "temperature", 0.0)) / 100.0 * 40.0,
            bShift = -ToolboxParams.num(p, "amount", ToolboxParams.num(p, "temperature", 0.0)) / 100.0 * 40.0,
        )

        "tint" -> {
            val amount = ToolboxParams.num(p, "amount", 0.0) / 100.0 * 40.0
            channelShift(source, gShift = -amount, rShift = amount / 2.0, bShift = amount / 2.0)
        }

        "vibrance" -> vibrance(source, ToolboxParams.num(p, "amount", ToolboxParams.num(p, "vibrance", 0.0)))
        "threshold" -> threshold(source, p)
        "posterize" -> posterize(source, ToolboxParams.int(p, "levels", 8))
        "solarize" -> solarize(source, p)
        "color_balance" -> colorBalance(source, p)
        "monochrome" -> monochrome(source, p)
        "black_white", "bw" -> blackAndWhite(source, p)
        "false_color" -> falseColor(source, ToolboxParams.str(p, "preset", "thermal"))
        "duotone" -> duotone(source, p)
        "highlights_shadows" -> highlightsShadows(source, p)
        "haze" -> haze(source, ToolboxParams.num(p, "amount", 30.0), add = true)
        "dehaze" -> haze(source, ToolboxParams.num(p, "amount", 30.0), add = false)
        "equalize" -> equalize(source)
        "vignette" -> vignette(source, p)
        "grain", "noise" -> grain(source, p)
        "emboss" -> emboss(source, ToolboxParams.num(p, "strength", 1.0).toFloat())
        "edge", "sobel" -> sobel(source, ToolboxParams.num(p, "strength", 1.0).toFloat())
        "laplacian" -> laplacian(source, ToolboxParams.num(p, "strength", 1.0).toFloat())
        "sketch" -> sketch(source, p)
        "oil" -> kuwahara(source, ToolboxParams.int(p, "radius", 3).coerceIn(1, 4))
        "kuwahara" -> kuwahara(source, ToolboxParams.int(p, "radius", 2).coerceIn(1, 4))
        "watercolor" -> watercolor(source, p)
        "halftone" -> halftone(source, p)
        "crosshatch" -> crosshatch(source, p)
        "glitch" -> glitch(source, p)
        "pixel_sort" -> pixelSort(source, p)
        "dither" -> dither(source, p)
        "denoise", "reduce_noise" -> median(source, ToolboxParams.int(p, "radius", 1).coerceIn(1, 3))
        "unsharp" -> unsharp(source, p)
        "glow" -> glow(source, p)
        "motion_blur" -> motionBlur(source, p)
        "replace_color" -> replaceColor(source, p)
        else -> error("不支持的图像操作：$op（可用：滤镜预设 / gamma / levels / curves / 阈值 / 色调分离 / 暗角 / 颗粒 / 浮雕 / 边缘 / 油画 / 水彩 / 素描 / 故障 / 像素排序…）")
    }

    // ══════════════ 基础工具 ══════════════

    private fun luminance(color: Int): Int = ((color shr 16 and 0xFF) * 299 + (color shr 8 and 0xFF) * 587 + (color and 0xFF) * 114) / 1000

    private fun clamp8(value: Int): Int = value.coerceIn(0, 255)

    private inline fun lut(source: Bitmap, transform: (Int) -> Int): Bitmap {
        val table = IntArray(256) { clamp8(transform(it)) }
        return lut(source, table)
    }

    private fun lut(source: Bitmap, table: IntArray): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            pixels[index] = (color and 0xFF000000.toInt()) or
                (table[color shr 16 and 0xFF] shl 16) or
                (table[color shr 8 and 0xFF] shl 8) or
                table[color and 0xFF]
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** 每个通道独立 LUT：tables[0..2] = r/g/b，长度 256。 */
    private fun channelLut(source: Bitmap, tables: Array<IntArray>): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            pixels[index] = (color and 0xFF000000.toInt()) or
                (tables[0][color shr 16 and 0xFF].coerceIn(0, 255) shl 16) or
                (tables[1][color shr 8 and 0xFF].coerceIn(0, 255) shl 8) or
                tables[2][color and 0xFF].coerceIn(0, 255)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun levelLut(inBlack: Int, inWhite: Int, gamma: Double, outBlack: Int, outWhite: Int): IntArray {
        val table = IntArray(256)
        val inRange = (inWhite - inBlack).coerceAtLeast(1)
        for (value in 0..255) {
            val normalized = ((value - inBlack).toDouble() / inRange).coerceIn(0.0, 1.0)
            val corrected = if (gamma == 1.0) normalized else normalized.pow(1.0 / gamma)
            table[value] = (outBlack + corrected * (outWhite - outBlack)).roundToInt().coerceIn(0, 255)
        }
        return table
    }

    private fun levels(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val inBlack = ToolboxParams.int(p, "in_black", ToolboxParams.int(p, "black", 0))
        val inWhite = ToolboxParams.int(p, "in_white", ToolboxParams.int(p, "white", 255))
        val gamma = ToolboxParams.num(p, "gamma", 1.0)
        val outBlack = ToolboxParams.int(p, "out_black", 0)
        val outWhite = ToolboxParams.int(p, "out_white", 255)
        val table = levelLut(inBlack, inWhite, gamma, outBlack, outWhite)
        return channelLut(source, arrayOf(table, table.copyOf(), table.copyOf()))
    }

    private fun curves(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val shadows = ToolboxParams.num(p, "shadows", 0.0) / 100.0
        val midtones = ToolboxParams.num(p, "midtones", ToolboxParams.num(p, "mid", 0.0)) / 100.0
        val highlights = ToolboxParams.num(p, "highlights", 0.0) / 100.0
        val table = IntArray(256)
        for (value in 0..255) {
            var t = value / 255.0
            t += shadows * 0.25 * (1 - t) * (1 - t)
            t += highlights * 0.25 * t * t
            t += midtones * (t - t * t) * 1.0
            table[value] = (t.coerceIn(0.0, 1.0) * 255).roundToInt()
        }
        return channelLut(source, arrayOf(table, table.copyOf(), table.copyOf()))
    }

    private fun channelShift(source: Bitmap, rShift: Double = 0.0, gShift: Double = 0.0, bShift: Double = 0.0): Bitmap {
        val r = IntArray(256) { (it + rShift).roundToInt().coerceIn(0, 255) }
        val g = IntArray(256) { (it + gShift).roundToInt().coerceIn(0, 255) }
        val b = IntArray(256) { (it + bShift).roundToInt().coerceIn(0, 255) }
        return channelLut(source, arrayOf(r, g, b))
    }

    private fun vibrance(source: Bitmap, amount: Double): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val strength = (amount / 100.0).coerceIn(-1.0, 2.0)
        for (index in pixels.indices) {
            val color = pixels[index]
            val r = color shr 16 and 0xFF
            val g = color shr 8 and 0xFF
            val b = color and 0xFF
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val saturation = (maxC - minC) / 255.0
            val factor = 1.0 + strength * (1.0 - saturation)
            val gray = luminance(color).toDouble()
            val nr = (gray + (r - gray) * factor).roundToInt().coerceIn(0, 255)
            val ng = (gray + (g - gray) * factor).roundToInt().coerceIn(0, 255)
            val nb = (gray + (b - gray) * factor).roundToInt().coerceIn(0, 255)
            pixels[index] = (color and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun threshold(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val level = ToolboxParams.int(p, "threshold", ToolboxParams.int(p, "level", 128)).coerceIn(0, 255)
        val soft = ToolboxParams.int(p, "soft", 0).coerceAtLeast(0)
        val table = IntArray(256) { value ->
            when {
                soft <= 0 -> if (value >= level) 255 else 0
                else -> {
                    val t = ((value - (level - soft)).toDouble() / (soft * 2)).coerceIn(0.0, 1.0)
                    (t * 255).roundToInt()
                }
            }
        }
        return channelLut(source, arrayOf(table, table.copyOf(), table.copyOf()))
    }

    private fun posterize(source: Bitmap, levels: Int): Bitmap {
        val count = levels.coerceIn(2, 64)
        val step = 255.0 / (count - 1)
        val table = IntArray(256) { (Math.round(it / step) * step).roundToInt().coerceIn(0, 255) }
        return channelLut(source, arrayOf(table, table.copyOf(), table.copyOf()))
    }

    private fun solarize(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val level = ToolboxParams.int(p, "threshold", ToolboxParams.int(p, "level", 128)).coerceIn(0, 255)
        val table = IntArray(256) { if (it > level) 255 - it else it }
        return channelLut(source, arrayOf(table, table.copyOf(), table.copyOf()))
    }

    private fun colorBalance(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val rGain = 1.0 + ToolboxParams.num(p, "red", ToolboxParams.num(p, "r", 0.0)) / 100.0
        val gGain = 1.0 + ToolboxParams.num(p, "green", ToolboxParams.num(p, "g", 0.0)) / 100.0
        val bGain = 1.0 + ToolboxParams.num(p, "blue", ToolboxParams.num(p, "b", 0.0)) / 100.0
        return channelLut(
            source,
            arrayOf(
                IntArray(256) { (it * rGain).roundToInt().coerceIn(0, 255) },
                IntArray(256) { (it * gGain).roundToInt().coerceIn(0, 255) },
                IntArray(256) { (it * bGain).roundToInt().coerceIn(0, 255) },
            )
        )
    }

    private fun monochrome(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val color = ToolboxIO.parseColor(ToolboxParams.str(p, "color", "#808080"), Color.GRAY)
        val strength = (ToolboxParams.num(p, "strength", 100.0) / 100.0).coerceIn(0.0, 1.0)
        val cr = color shr 16 and 0xFF
        val cg = color shr 8 and 0xFF
        val cb = color and 0xFF
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val lum = luminance(pixels[index]) / 255.0
            val nr = (cr * lum).roundToInt().coerceIn(0, 255)
            val ng = (cg * lum).roundToInt().coerceIn(0, 255)
            val nb = (cb * lum).roundToInt().coerceIn(0, 255)
            val old = pixels[index]
            val or = old shr 16 and 0xFF
            val og = old shr 8 and 0xFF
            val ob = old and 0xFF
            pixels[index] = (old and 0xFF000000.toInt()) or
                ((or + (nr - or) * strength).roundToInt().coerceIn(0, 255) shl 16) or
                ((og + (ng - og) * strength).roundToInt().coerceIn(0, 255) shl 8) or
                (ob + (nb - ob) * strength).roundToInt().coerceIn(0, 255)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun blackAndWhite(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val redWeight = ToolboxParams.num(p, "red", 30.0)
        val blueWeight = ToolboxParams.num(p, "blue", 10.0)
        val contrast = ToolboxParams.num(p, "contrast", 0.0)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val c = (259.0 * (contrast + 255.0)) / (255.0 * (259.0 - contrast))
        for (index in pixels.indices) {
            val color = pixels[index]
            val r = color shr 16 and 0xFF
            val g = color shr 8 and 0xFF
            val b = color and 0xFF
            val gray = ((r * redWeight + g * (100 - redWeight - blueWeight) + b * blueWeight) / 100.0)
                .roundToInt().coerceIn(0, 255)
            val adjusted = (c * (gray - 128) + 128).roundToInt().coerceIn(0, 255)
            pixels[index] = (color and 0xFF000000.toInt()) or (adjusted shl 16) or (adjusted shl 8) or adjusted
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun gradient(stops: List<Pair<Float, Int>>): IntArray {
        val sorted = stops.sortedBy { it.first }
        return IntArray(256) { value ->
            val t = value / 255f
            var lower = sorted.first()
            var upper = sorted.last()
            for (index in 0 until sorted.size - 1) {
                if (t >= sorted[index].first && t <= sorted[index + 1].first) {
                    lower = sorted[index]
                    upper = sorted[index + 1]
                    break
                }
            }
            val span = (upper.first - lower.first).takeIf { it > 0.0001f } ?: 1f
            val k = ((t - lower.first) / span).coerceIn(0f, 1f)
            val lr = lower.second shr 16 and 0xFF
            val lg = lower.second shr 8 and 0xFF
            val lb = lower.second and 0xFF
            val ur = upper.second shr 16 and 0xFF
            val ug = upper.second shr 8 and 0xFF
            val ub = upper.second and 0xFF
            val r = (lr + (ur - lr) * k).roundToInt()
            val g = (lg + (ug - lg) * k).roundToInt()
            val b = (lb + (ub - lb) * k).roundToInt()
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private val FALSE_COLOR_PALETTES: Map<String, IntArray> = mapOf(
        "thermal" to gradient(listOf(0f to Color.BLACK, 0.25f to Color.BLUE, 0.5f to Color.RED, 0.75f to Color.YELLOW, 1f to Color.WHITE)),
        "rainbow" to gradient(listOf(0f to Color.RED, 0.2f to Color.YELLOW, 0.4f to Color.GREEN, 0.6f to Color.CYAN, 0.8f to Color.BLUE, 1f to Color.MAGENTA)),
        "fire" to gradient(listOf(0f to Color.BLACK, 0.4f to Color.rgb(120, 0, 0), 0.75f to Color.rgb(255, 120, 0), 1f to Color.YELLOW)),
        "ice" to gradient(listOf(0f to Color.BLACK, 0.5f to Color.rgb(0, 60, 160), 0.8f to Color.CYAN, 1f to Color.WHITE)),
        "ocean" to gradient(listOf(0f to Color.BLACK, 0.5f to Color.rgb(0, 40, 90), 0.8f to Color.rgb(0, 160, 170), 1f to Color.WHITE)),
        "neon" to gradient(listOf(0f to Color.BLACK, 0.4f to Color.rgb(90, 0, 160), 0.7f to Color.MAGENTA, 1f to Color.CYAN)),
    )

    private fun falseColor(source: Bitmap, preset: String): Bitmap {
        val table = FALSE_COLOR_PALETTES[preset.lowercase()] ?: FALSE_COLOR_PALETTES.getValue("thermal")
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val lum = luminance(pixels[index])
            pixels[index] = (pixels[index] and 0xFF000000.toInt()) or (table[lum] and 0x00FFFFFF)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun duotone(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val dark = ToolboxIO.parseColor(ToolboxParams.str(p, "dark", ToolboxParams.str(p, "shadow", "#000000")), Color.BLACK)
        val light = ToolboxIO.parseColor(ToolboxParams.str(p, "light", ToolboxParams.str(p, "highlight", "#FFFFFF")), Color.WHITE)
        val dr = dark shr 16 and 0xFF
        val dg = dark shr 8 and 0xFF
        val db = dark and 0xFF
        val lr = light shr 16 and 0xFF
        val lg = light shr 8 and 0xFF
        val lb = light and 0xFF
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val k = luminance(color) / 255.0
            val r = (dr + (lr - dr) * k).roundToInt().coerceIn(0, 255)
            val g = (dg + (lg - dg) * k).roundToInt().coerceIn(0, 255)
            val b = (db + (lb - db) * k).roundToInt().coerceIn(0, 255)
            pixels[index] = (color and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun highlightsShadows(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val shadows = ToolboxParams.num(p, "shadows", 0.0)
        val highlights = ToolboxParams.num(p, "highlights", 0.0)
        val table = IntArray(256) { value ->
            val t = value / 255.0
            val shadowTerm = shadows / 100.0 * (1 - t) * (1 - t) * 255.0
            val highlightTerm = -highlights / 100.0 * t * t * 255.0
            (value + shadowTerm + highlightTerm).roundToInt().coerceIn(0, 255)
        }
        return channelLut(source, arrayOf(table, table.copyOf(), table.copyOf()))
    }

    private fun haze(source: Bitmap, amount: Double, add: Boolean): Bitmap {
        val strength = (amount / 100.0).coerceIn(0.0, 1.0)
        val table = IntArray(256) { value ->
            val t = value / 255.0
            val result = if (add) {
                t + strength * (1 - t) * 0.5
            } else {
                (t - 0.5) * (1 + strength) + 0.5
            }
            (result * 255).roundToInt().coerceIn(0, 255)
        }
        return channelLut(source, arrayOf(table, table.copyOf(), table.copyOf()))
    }

    private fun equalize(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val total = pixels.size
        val tables = Array(3) { IntArray(256) }
        for (channel in 0..2) {
            val histogram = IntArray(256)
            for (color in pixels) histogram[(color shr (16 - channel * 8)) and 0xFF]++
            var cumulative = 0
            for (value in 0..255) {
                cumulative += histogram[value]
                tables[channel][value] = (cumulative * 255.0 / total).roundToInt().coerceIn(0, 255)
            }
        }
        return channelLut(source, tables)
    }

    private fun vignette(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val strength = (ToolboxParams.num(p, "strength", 60.0) / 100.0).coerceIn(0.0, 1.0)
        val radius = ToolboxParams.num(p, "radius", 0.75).coerceIn(0.1, 1.0)
        val softness = ToolboxParams.num(p, "softness", 0.45).coerceIn(0.05, 1.0)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val cx = (width - 1) / 2.0
        val cy = (height - 1) / 2.0
        val maxDistance = sqrt(cx * cx + cy * cy).coerceAtLeast(1.0)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val dx = (x - cx) / maxDistance
                val dy = (y - cy) / maxDistance
                val distance = sqrt(dx * dx + dy * dy)
                val factor = if (distance <= radius) {
                    1.0
                } else {
                    val t = ((distance - radius) / softness).coerceIn(0.0, 1.0)
                    1.0 - strength * t
                }
                val color = pixels[index]
                val r = ((color shr 16 and 0xFF) * factor).roundToInt().coerceIn(0, 255)
                val g = ((color shr 8 and 0xFF) * factor).roundToInt().coerceIn(0, 255)
                val b = ((color and 0xFF) * factor).roundToInt().coerceIn(0, 255)
                pixels[index] = (color and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun grain(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val amount = ToolboxParams.num(p, "amount", ToolboxParams.num(p, "strength", 20.0)).coerceIn(0.0, 100.0) / 100.0 * 80.0
        val mono = ToolboxParams.bool(p, "mono", ToolboxParams.bool(p, "monochrome", true))
        val seed = ToolboxParams.num(p, "seed", 42.0).toLong()
        val random = Random(seed)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            if (mono) {
                val noise = (random.nextDouble() - 0.5) * amount
                val r = (color shr 16 and 0xFF) + noise
                val g = (color shr 8 and 0xFF) + noise
                val b = (color and 0xFF) + noise
                pixels[index] = (color and 0xFF000000.toInt()) or
                    (r.roundToInt().coerceIn(0, 255) shl 16) or
                    (g.roundToInt().coerceIn(0, 255) shl 8) or
                    b.roundToInt().coerceIn(0, 255)
            } else {
                val nr = (color shr 16 and 0xFF) + (random.nextDouble() - 0.5) * amount
                val ng = (color shr 8 and 0xFF) + (random.nextDouble() - 0.5) * amount
                val nb = (color and 0xFF) + (random.nextDouble() - 0.5) * amount
                pixels[index] = (color and 0xFF000000.toInt()) or
                    (nr.roundToInt().coerceIn(0, 255) shl 16) or
                    (ng.roundToInt().coerceIn(0, 255) shl 8) or
                    nb.roundToInt().coerceIn(0, 255)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun convolve(source: Bitmap, kernel: FloatArray, size: Int, offset: Float = 0f, absolute: Boolean = false): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val output = IntArray(pixels.size)
        val half = size / 2
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (ky in 0 until size) {
                    val sy = (y + ky - half).coerceIn(0, height - 1)
                    for (kx in 0 until size) {
                        val sx = (x + kx - half).coerceIn(0, width - 1)
                        val weight = kernel[ky * size + kx]
                        val color = pixels[sy * width + sx]
                        r += (color shr 16 and 0xFF) * weight
                        g += (color shr 8 and 0xFF) * weight
                        b += (color and 0xFF) * weight
                    }
                }
                fun finish(value: Float): Int = (if (absolute) abs(value) else value + offset).roundToInt().coerceIn(0, 255)
                output[y * width + x] = (pixels[y * width + x] and 0xFF000000.toInt()) or
                    (finish(r) shl 16) or (finish(g) shl 8) or finish(b)
            }
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun emboss(source: Bitmap, strength: Float): Bitmap {
        val s = strength.coerceIn(0.1f, 4f)
        return convolve(
            source,
            floatArrayOf(-2f * s, -s, 0f, -s, 1f, s, 0f, s, 2f * s),
            3,
            offset = 128f,
        )
    }

    private fun grayscaleCopy(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val lum = luminance(pixels[index])
            pixels[index] = (pixels[index] and 0xFF000000.toInt()) or (lum shl 16) or (lum shl 8) or lum
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun sobel(source: Bitmap, strength: Float): Bitmap {
        val gray = grayscaleCopy(source)
        val result = convolve(
            gray,
            floatArrayOf(-1f, -2f, -1f, 0f, 0f, 0f, 1f, 2f, 1f),
            3,
            absolute = true,
        )
        gray.recycle()
        val vertical = convolve(
            source,
            floatArrayOf(-1f, 0f, 1f, -2f, 0f, 2f, -1f, 0f, 1f),
            3,
            absolute = true,
        )
        val width = result.width
        val height = result.height
        val a = IntArray(width * height)
        val b = IntArray(width * height)
        result.getPixels(a, 0, width, 0, 0, width, height)
        vertical.getPixels(b, 0, width, 0, 0, width, height)
        val output = IntArray(a.size)
        for (index in a.indices) {
            val magnitude = (sqrt(
                ((a[index] shr 16 and 0xFF).toDouble() * (a[index] shr 16 and 0xFF)) +
                    ((b[index] shr 16 and 0xFF).toDouble() * (b[index] shr 16 and 0xFF))
            ) * strength).roundToInt().coerceIn(0, 255)
            output[index] = (a[index] and 0xFF000000.toInt()) or (magnitude shl 16) or (magnitude shl 8) or magnitude
        }
        result.recycle()
        vertical.recycle()
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun laplacian(source: Bitmap, strength: Float): Bitmap =
        convolve(
            source,
            floatArrayOf(0f, -1f, 0f, -1f, 4f, -1f, 0f, -1f, 0f),
            3,
            absolute = true,
        ).let { edges ->
            val width = edges.width
            val height = edges.height
            val a = IntArray(width * height)
            edges.getPixels(a, 0, width, 0, 0, width, height)
            for (index in a.indices) {
                val value = ((a[index] shr 16 and 0xFF) * strength).roundToInt().coerceIn(0, 255)
                a[index] = (a[index] and 0xFF000000.toInt()) or (value shl 16) or (value shl 8) or value
            }
            Bitmap.createBitmap(a, width, height, Bitmap.Config.ARGB_8888).also { edges.recycle() }
        }

    private fun sketch(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val radius = ToolboxParams.int(p, "radius", 8).coerceIn(1, 50)
        val gray = grayscaleCopy(source)
        val width = gray.width
        val height = gray.height
        val pixels = IntArray(width * height)
        gray.getPixels(pixels, 0, width, 0, 0, width, height)
        val inverted = IntArray(pixels.size) { index ->
            val c = pixels[index] and 0xFF
            (pixels[index] and 0xFF000000.toInt()) or ((255 - c) shl 16) or ((255 - c) shl 8) or (255 - c)
        }
        val blurred = boxBlur(inverted, width, height, radius)
        val output = IntArray(pixels.size)
        for (index in pixels.indices) {
            val g = pixels[index] and 0xFF
            val b = blurred[index] and 0xFF
            val dodge = if (b >= 255) 255 else (g * 255.0 / (255 - b)).roundToInt().coerceIn(0, 255)
            output[index] = (pixels[index] and 0xFF000000.toInt()) or (dodge shl 16) or (dodge shl 8) or dodge
        }
        gray.recycle()
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun kuwahara(source: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 4)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val output = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var bestVariance = Double.MAX_VALUE
                var bestR = 0
                var bestG = 0
                var bestB = 0
                for (quadrant in 0 until 4) {
                    val xStart = if (quadrant == 0 || quadrant == 2) x - r else x
                    val xEnd = if (quadrant == 0 || quadrant == 2) x else x + r
                    val yStart = if (quadrant == 0 || quadrant == 1) y - r else y
                    val yEnd = if (quadrant == 0 || quadrant == 1) y else y + r
                    var sumR = 0.0
                    var sumG = 0.0
                    var sumB = 0.0
                    var sumSq = 0.0
                    var count = 0
                    for (sy in yStart..yEnd) {
                        val cy = sy.coerceIn(0, height - 1)
                        for (sx in xStart..xEnd) {
                            val cx = sx.coerceIn(0, width - 1)
                            val color = pixels[cy * width + cx]
                            val pr = (color shr 16 and 0xFF).toDouble()
                            val pg = (color shr 8 and 0xFF).toDouble()
                            val pb = (color and 0xFF).toDouble()
                            sumR += pr
                            sumG += pg
                            sumB += pb
                            sumSq += pr * pr + pg * pg + pb * pb
                            count++
                        }
                    }
                    if (count == 0) continue
                    val meanR = sumR / count
                    val meanG = sumG / count
                    val meanB = sumB / count
                    val variance = sumSq / count - (meanR * meanR + meanG * meanG + meanB * meanB)
                    if (variance < bestVariance) {
                        bestVariance = variance
                        bestR = meanR.roundToInt().coerceIn(0, 255)
                        bestG = meanG.roundToInt().coerceIn(0, 255)
                        bestB = meanB.roundToInt().coerceIn(0, 255)
                    }
                }
                output[y * width + x] = (pixels[y * width + x] and 0xFF000000.toInt()) or
                    (bestR shl 16) or (bestG shl 8) or bestB
            }
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun watercolor(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val radius = ToolboxParams.int(p, "radius", 2).coerceIn(1, 3)
        val levels = ToolboxParams.int(p, "levels", 12).coerceIn(4, 32)
        val smoothed = median(source, radius)
        val posterized = posterize(smoothed, levels)
        smoothed.recycle()
        val edges = sobel(source, 0.6f)
        val width = source.width
        val height = source.height
        val base = IntArray(width * height)
        val edgePixels = IntArray(width * height)
        posterized.getPixels(base, 0, width, 0, 0, width, height)
        edges.getPixels(edgePixels, 0, width, 0, 0, width, height)
        for (index in base.indices) {
            val edge = edgePixels[index] and 0xFF
            val darken = (1.0 - edge / 255.0 * 0.45)
            val r = ((base[index] shr 16 and 0xFF) * darken).roundToInt().coerceIn(0, 255)
            val g = ((base[index] shr 8 and 0xFF) * darken).roundToInt().coerceIn(0, 255)
            val b = ((base[index] and 0xFF) * darken).roundToInt().coerceIn(0, 255)
            base[index] = (base[index] and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
        posterized.recycle()
        edges.recycle()
        return Bitmap.createBitmap(base, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun halftone(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val cell = ToolboxParams.int(p, "cell", ToolboxParams.int(p, "size", 6)).coerceIn(3, 32)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        var by = 0
        while (by < height) {
            var bx = 0
            while (bx < width) {
                var sum = 0
                var count = 0
                val maxY = min(by + cell, height)
                val maxX = min(bx + cell, width)
                for (y in by until maxY) {
                    for (x in bx until maxX) {
                        sum += luminance(pixels[y * width + x])
                        count++
                    }
                }
                val average = if (count == 0) 255 else sum / count
                val ratio = 1.0 - average / 255.0
                val radius = sqrt(ratio / Math.PI) * cell
                if (radius > 0.2) {
                    canvas.drawCircle(bx + cell / 2f, by + cell / 2f, radius.toFloat(), paint)
                }
                bx += cell
            }
            by += cell
        }
        return output
    }

    private fun crosshatch(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val edges = sobel(source, 1.0f)
        val threshold = ToolboxParams.int(p, "threshold", 60).coerceIn(0, 255)
        val spacing = ToolboxParams.int(p, "spacing", 8).coerceIn(3, 32)
        val width = source.width
        val height = source.height
        val edgePixels = IntArray(width * height)
        edges.getPixels(edgePixels, 0, width, 0, 0, width, height)
        val output = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val edge = edgePixels[index] and 0xFF
                val onDiagonalA = (x + y) % spacing == 0
                val onDiagonalB = ((x - y) % spacing + spacing) % spacing == 0
                output[index] = when {
                    edge >= threshold -> Color.rgb(20, 20, 20)
                    onDiagonalA || onDiagonalB -> Color.rgb(160, 160, 160)
                    else -> Color.WHITE
                }
            }
        }
        edges.recycle()
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun glitch(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val intensity = ToolboxParams.int(p, "intensity", 30).coerceIn(1, 200)
        val channelShift = ToolboxParams.int(p, "channel_shift", max(1, intensity / 3)).coerceIn(1, 200)
        val density = ToolboxParams.num(p, "density", 0.12).coerceIn(0.0, 1.0)
        val seed = ToolboxParams.num(p, "seed", 42.0).toLong()
        val random = Random(seed)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val shifted = pixels.copyOf()
        for (y in 0 until height) {
            if (random.nextDouble() < density) {
                val shift = random.nextInt(intensity * 2 + 1) - intensity
                for (x in 0 until width) {
                    val sourceX = (x - shift).coerceIn(0, width - 1)
                    shifted[y * width + x] = pixels[y * width + sourceX]
                }
            }
        }
        val output = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val left = shifted[y * width + (x - channelShift).coerceIn(0, width - 1)]
                val right = shifted[y * width + (x + channelShift).coerceIn(0, width - 1)]
                output[index] = (shifted[index] and 0xFF000000.toInt()) or
                    (left and 0x00FF0000) or
                    (shifted[index] and 0x0000FF00) or
                    (right and 0x000000FF)
            }
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun pixelSort(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val threshold = ToolboxParams.int(p, "threshold", 128).coerceIn(0, 255)
        val reverse = ToolboxParams.bool(p, "reverse", false)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (y in 0 until height) {
            val row = y * width
            var x = 0
            while (x < width) {
                if (luminance(pixels[row + x]) > threshold) {
                    var end = x
                    while (end < width && luminance(pixels[row + end]) > threshold) end++
                    val segment = (x until end).map { pixels[row + it] }
                        .sortedBy { color -> luminance(color) }
                    for ((offset, color) in segment.withIndex()) {
                        pixels[row + x + offset] = color
                    }
                    if (reverse) {
                        for (offset in 0 until (end - x) / 2) {
                            val a = row + x + offset
                            val b = row + end - 1 - offset
                            val tmp = pixels[a]
                            pixels[a] = pixels[b]
                            pixels[b] = tmp
                        }
                    }
                    x = end
                } else {
                    x++
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun dither(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val levels = ToolboxParams.int(p, "levels", 2).coerceIn(2, 8)
        val step = 255.0 / (levels - 1)
        val errors = Array(3) { DoubleArray(width + 2) }
        val nextErrors = Array(3) { DoubleArray(width + 2) }
        for (y in 0 until height) {
            for (channel in 0..2) java.util.Arrays.fill(nextErrors[channel], 0.0)
            for (x in 0 until width) {
                val index = y * width + x
                val color = pixels[index]
                val channels = intArrayOf(color shr 16 and 0xFF, color shr 8 and 0xFF, color and 0xFF)
                for (channel in 0..2) {
                    val value = (channels[channel] + errors[channel][x + 1]).coerceIn(0.0, 255.0)
                    val quantized = (Math.round(value / step) * step).coerceIn(0.0, 255.0)
                    val error = value - quantized
                    channels[channel] = quantized.roundToInt()
                    errors[channel][x + 2] += error * 7 / 16
                    nextErrors[channel][x] += error * 3 / 16
                    nextErrors[channel][x + 1] += error * 5 / 16
                    nextErrors[channel][x + 2] += error * 1 / 16
                }
                pixels[index] = (color and 0xFF000000.toInt()) or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
            }
            for (channel in 0..2) {
                val tmp = errors[channel]
                errors[channel] = nextErrors[channel]
                System.arraycopy(tmp, 0, nextErrors[channel], 0, tmp.size)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun median(source: Bitmap, radius: Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val output = IntArray(pixels.size)
        val r = radius.coerceIn(1, 3)
        val window = IntArray((r * 2 + 1) * (r * 2 + 1))
        for (y in 0 until height) {
            for (x in 0 until width) {
                var count = 0
                val reds = ArrayList<Int>(window.size)
                val greens = ArrayList<Int>(window.size)
                val blues = ArrayList<Int>(window.size)
                for (dy in -r..r) {
                    val sy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -r..r) {
                        val sx = (x + dx).coerceIn(0, width - 1)
                        val color = pixels[sy * width + sx]
                        reds.add(color shr 16 and 0xFF)
                        greens.add(color shr 8 and 0xFF)
                        blues.add(color and 0xFF)
                        count++
                    }
                }
                reds.sort()
                greens.sort()
                blues.sort()
                val middle = count / 2
                output[y * width + x] = (pixels[y * width + x] and 0xFF000000.toInt()) or
                    (reds[middle] shl 16) or (greens[middle] shl 8) or blues[middle]
            }
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun unsharp(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val amount = ToolboxParams.num(p, "amount", 1.0).coerceIn(0.0, 5.0)
        val radius = ToolboxParams.int(p, "radius", 3).coerceIn(1, 40)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val blurred = boxBlur(pixels, width, height, radius)
        val output = IntArray(pixels.size)
        for (index in pixels.indices) {
            val sourceColor = pixels[index]
            val blurredColor = blurred[index]
            fun sharp(channel: Int): Int {
                val original = sourceColor shr channel and 0xFF
                val soft = blurredColor shr channel and 0xFF
                return (original + amount * (original - soft)).roundToInt().coerceIn(0, 255)
            }
            output[index] = (sourceColor and 0xFF000000.toInt()) or (sharp(16) shl 16) or (sharp(8) shl 8) or sharp(0)
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun glow(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val threshold = ToolboxParams.int(p, "threshold", 180).coerceIn(0, 255)
        val radius = ToolboxParams.int(p, "radius", 12).coerceIn(1, 60)
        val amount = ToolboxParams.num(p, "amount", 0.6).coerceIn(0.0, 1.0)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val bright = IntArray(pixels.size) { index ->
            val color = pixels[index]
            if (luminance(color) >= threshold) color else (color and 0xFF000000.toInt())
        }
        val blurred = boxBlur(bright, width, height, radius)
        val output = IntArray(pixels.size)
        for (index in pixels.indices) {
            val base = pixels[index]
            val light = blurred[index]
            val r = screen(base shr 16 and 0xFF, light shr 16 and 0xFF, amount)
            val g = screen(base shr 8 and 0xFF, light shr 8 and 0xFF, amount)
            val b = screen(base and 0xFF, light and 0xFF, amount)
            output[index] = (base and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun screen(a: Int, b: Int, amount: Double): Int {
        val screened = 255 - (255 - a) * (255 - b) / 255
        return (a + (screened - a) * amount).roundToInt().coerceIn(0, 255)
    }

    private fun motionBlur(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val length = ToolboxParams.int(p, "length", 12).coerceIn(1, 100)
        val angle = Math.toRadians(ToolboxParams.num(p, "angle", ToolboxParams.num(p, "degrees", 0.0)))
        val dx = cos(angle)
        val dy = sin(angle)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val output = IntArray(pixels.size)
        val samples = length * 2 + 1
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0L
                var g = 0L
                var b = 0L
                for (step in -length..length) {
                    val sx = (x + dx * step).roundToInt().coerceIn(0, width - 1)
                    val sy = (y + dy * step).roundToInt().coerceIn(0, height - 1)
                    val color = pixels[sy * width + sx]
                    r += color shr 16 and 0xFF
                    g += color shr 8 and 0xFF
                    b += color and 0xFF
                }
                val base = pixels[y * width + x]
                output[y * width + x] = (base and 0xFF000000.toInt()) or
                    ((r / samples).toInt() shl 16) or ((g / samples).toInt() shl 8) or (b / samples).toInt()
            }
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun replaceColor(source: Bitmap, p: Map<String, Any?>): Bitmap {
        val from = ToolboxIO.parseColor(ToolboxParams.str(p, "from", "#FF0000"), Color.RED)
        val to = ToolboxIO.parseColor(ToolboxParams.str(p, "to", "#00FF00"), Color.GREEN)
        val tolerance = ToolboxParams.num(p, "tolerance", 30.0).coerceIn(0.0, 255.0)
        val fr = from shr 16 and 0xFF
        val fg = from shr 8 and 0xFF
        val fb = from and 0xFF
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val distance = sqrt(
                (color shr 16 and 0xFF - fr).toDouble().pow(2) +
                    (color shr 8 and 0xFF - fg).toDouble().pow(2) +
                    (color and 0xFF - fb).toDouble().pow(2)
            )
            if (distance <= tolerance) {
                pixels[index] = (color and 0xFF000000.toInt()) or (to and 0x00FFFFFF)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** 三次方框模糊（行列分离）近似高斯；返回新数组，不修改入参。 */
    fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val r = radius.coerceIn(1, 100)
        var current = pixels
        val temp = IntArray(pixels.size)
        repeat(3) {
            if (current === pixels) {
                current = pixels.copyOf()
            }
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
}
