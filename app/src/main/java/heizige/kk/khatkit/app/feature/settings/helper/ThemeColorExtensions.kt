package heizige.kk.khatkit.app.feature.settings.helper

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import heizige.kk.khatkit.app.core.ui.icons.utility.add
import kotlin.math.roundToInt


// ---------------------------------------------------------------------------
// HSL 文本解析/格式化：自定义主题编辑弹窗用（internal，不新增对外 API）。
// ---------------------------------------------------------------------------

internal val hslNumberRegex = Regex("""[-+]?\d*\.?\d+""")

internal fun parseHslCode(value: String): FloatArray? {
    val values = buildList {
        for (match in hslNumberRegex.findAll(value)) {
            add(match.value.toFloatOrNull() ?: return null)
            if (size == 3) break
        }
    }

    if (values.size != 3) return null

    val hue = values[0].coerceIn(0f, 360f)
    val saturation = parseHslPercentOrFraction(values[1]) ?: return null
    val lightness = parseHslPercentOrFraction(values[2]) ?: return null

    return floatArrayOf(hue, saturation, lightness)
}

internal fun parseHslPercentOrFraction(value: Float): Float? {
    if (!value.isFinite()) return null
    return if (value > 1f) {
        (value / 100f).coerceIn(0f, 1f)
    } else {
        value.coerceIn(0f, 1f)
    }
}

internal fun formatHslCode(hue: Float, saturation: Float, lightness: Float): String {
    return "hsl(${hue.roundToInt()} ${(saturation * 100).roundToInt()}% ${(lightness * 100).roundToInt()}%)"
}
