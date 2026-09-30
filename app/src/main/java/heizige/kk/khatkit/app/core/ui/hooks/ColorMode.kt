package heizige.kk.khatkit.app.core.ui.hooks

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import heizige.kk.khatkit.app.core.ui.theme.ColorMode

private const val COLOR_MODE_KEY = "colorMode"

@Composable
fun rememberColorMode(): MutableState<ColorMode> {
    val colorModeState = rememberSharedPreferenceString(COLOR_MODE_KEY, ColorMode.SYSTEM.name)
    return remember(colorModeState) {
        object : MutableState<ColorMode> {
            override var value: ColorMode
                get() = colorModeState.value.toColorMode()
                set(value) {
                    colorModeState.value = value.name
                }

            override fun component1(): ColorMode = value

            override fun component2(): (ColorMode) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberCurrentColorMode(): ColorMode {
    val colorModeValue by rememberSharedPreferenceString(COLOR_MODE_KEY, ColorMode.SYSTEM.name)
    return colorModeValue.toColorMode()
}

/**
 * 把用户的「颜色模式」设置解析成实际使用的深/浅色。
 *
 * Kedge 的 `KedgeTheme`（也就是 Miuix 主题）只认这个布尔值，且它自己的默认值是
 * `isSystemInDarkTheme()`——跟应用内设置无关。所以 Miuix 分支必须显式把本函数
 * 的结果传进去，否则在应用内切「浅色」对 Miuix 完全无效。
 */
@Composable
fun rememberIsDarkTheme(): Boolean =
    when (rememberCurrentColorMode()) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }

@Composable
fun rememberAmoledDarkMode(): MutableState<Boolean> {
    return rememberSharedPreferenceBoolean("amoledDark", false)
}

private fun String?.toColorMode(): ColorMode {
    return ColorMode.entries.firstOrNull { it.name == this } ?: ColorMode.SYSTEM
}
