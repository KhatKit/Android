package heizige.kk.khatkit.dependencies.imageToolbox

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToInt

/** Platform-independent calculations used by the downloadable dependency. */
internal object RasterTools {
    data class Tile(val x: Int, val y: Int, val width: Int, val height: Int)

    fun tiles(width: Int, height: Int, columns: Int, rows: Int): List<Tile> {
        require(width > 0 && height > 0)
        require(columns in 1..width && rows in 1..height) { "Grid must fit the image" }
        require(columns.toLong() * rows <= 256) { "At most 256 tiles are allowed" }
        return (0 until rows).flatMap { row ->
            (0 until columns).map { column ->
                val x = (column.toLong() * width / columns).toInt()
                val y = (row.toLong() * height / rows).toInt()
                Tile(x, y, ((column + 1L) * width / columns).toInt() - x,
                    ((row + 1L) * height / rows).toInt() - y)
            }
        }
    }

    data class Difference(val changedPixels: Int, val mae: Double, val mse: Double, val psnr: Double?)

    fun compare(first: IntArray, second: IntArray, threshold: Int): Difference {
        require(first.isNotEmpty() && first.size == second.size) { "Images must have equal pixel counts" }
        require(threshold in 0..255)
        var changed = 0
        var sum = 0L
        var squared = 0L
        first.indices.forEach { index ->
            var maxDifference = 0
            for (shift in 16 downTo 0 step 8) {
                val delta = abs(((first[index] ushr shift) and 255) - ((second[index] ushr shift) and 255))
                sum += delta
                squared += delta.toLong() * delta
                maxDifference = maxOf(maxDifference, delta)
            }
            // Alpha contributes to changedPixels; RGB quality metrics remain conventional RGB metrics.
            maxDifference = maxOf(maxDifference, abs((first[index] ushr 24) - (second[index] ushr 24)))
            if (maxDifference > threshold) changed++
        }
        val channels = first.size.toDouble() * 3
        val mse = squared / channels
        return Difference(changed, sum / channels, mse, if (mse == 0.0) null else 10 * log10(65025 / mse))
    }
}

/** Adobe/IRIDAS .cube 3D LUT, red-fastest ordering and trilinear interpolation. */
internal class CubeLut private constructor(
    private val size: Int,
    private val values: FloatArray,
    private val domainMin: FloatArray,
    private val domainMax: FloatArray,
) {
    private val positions = Array(3) { channel ->
        DoubleArray(256) { value ->
            ((value / 255.0 - domainMin[channel]) / (domainMax[channel] - domainMin[channel]))
                .coerceIn(0.0, 1.0) * (size - 1)
        }
    }

    fun apply(pixel: Int, strength: Double): Int {
        require(strength.isFinite() && strength in 0.0..1.0) { "strength must be between 0 and 1" }
        val originalRed = (pixel ushr 16) and 255
        val originalGreen = (pixel ushr 8) and 255
        val originalBlue = pixel and 255
        val x = positions[0][originalRed]
        val y = positions[1][originalGreen]
        val z = positions[2][originalBlue]
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val z0 = floor(z).toInt()
        val fx = x - x0
        val fy = y - y0
        val fz = z - z0
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        for (b in 0..1) for (g in 0..1) for (r in 0..1) {
            val index = (((z0 + b).coerceAtMost(size - 1) * size + (y0 + g).coerceAtMost(size - 1)) *
                size + (x0 + r).coerceAtMost(size - 1)) * 3
            val weight = (if (r == 0) 1 - fx else fx) * (if (g == 0) 1 - fy else fy) * (if (b == 0) 1 - fz else fz)
            red += values[index] * weight
            green += values[index + 1] * weight
            blue += values[index + 2] * weight
        }
        return (pixel and -0x1000000) or (mix(originalRed, red, strength) shl 16) or
            (mix(originalGreen, green, strength) shl 8) or mix(originalBlue, blue, strength)
    }

    private fun mix(original: Int, mapped: Double, strength: Double): Int =
        (original * (1 - strength) + mapped * 255 * strength).roundToInt().coerceIn(0, 255)

    companion object {
        fun parse(text: String): CubeLut {
            require(text.length <= 16 * 1024 * 1024) { "LUT exceeds 16 MiB" }
            var size = 0
            var values: FloatArray? = null
            var count = 0
            var domainMin = floatArrayOf(0f, 0f, 0f)
            var domainMax = floatArrayOf(1f, 1f, 1f)
            text.lineSequence().forEach { raw ->
                val line = raw.removePrefix("\uFEFF").substringBefore('#').trim()
                if (line.isNotEmpty()) {
                    val parts = line.split(Regex("\\s+"))
                    fun triple(): FloatArray {
                        require(parts.size == 4) { "Invalid LUT domain" }
                        return FloatArray(3) { parts[it + 1].toFloat().also { number -> require(number.isFinite()) } }
                    }
                    when (parts[0]) {
                        "TITLE" -> Unit
                        "LUT_3D_SIZE" -> {
                            require(size == 0 && parts.size == 2) { "Duplicate/invalid LUT size" }
                            size = parts[1].toInt()
                            require(size in 2..65) { "LUT size must be 2..65" }
                            values = FloatArray(size * size * size * 3)
                        }
                        "DOMAIN_MIN" -> domainMin = triple()
                        "DOMAIN_MAX" -> domainMax = triple()
                        else -> {
                            val data = requireNotNull(values) { "Expected LUT_3D_SIZE before samples; only 3D cube LUTs are supported" }
                            require(parts.size == 3 && count + 3 <= data.size) { "Invalid LUT sample count" }
                            parts.forEach { value -> data[count++] = value.toFloat().also { require(it.isFinite()) } }
                        }
                    }
                }
            }
            val data = requireNotNull(values) { "Missing LUT_3D_SIZE" }
            require(count == data.size) { "Incomplete LUT: expected ${data.size / 3} samples, got ${count / 3}" }
            require((0..2).all { domainMax[it] > domainMin[it] }) { "Invalid LUT domain range" }
            return CubeLut(size, data, domainMin, domainMax)
        }
    }
}
