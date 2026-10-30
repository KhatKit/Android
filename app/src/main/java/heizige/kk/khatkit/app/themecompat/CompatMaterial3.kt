package heizige.kk.khatkit.app.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.compositionLocalOf
import ui.feice.mochi.theme.MochiColorScheme
import ui.feice.mochi.theme.darkColorScheme
import ui.feice.mochi.theme.lightColorScheme

/**
 * material3 兼容层（P2 临时桥，P6/P7 连同 material3 依赖一起删除）。
 *
 * 2026-10-30 口径：**全面切 Mochi，不保留旧设计语言**。所以这里不再搬旧色板/字阶：
 * mochi 侧用 `lightColorScheme()/darkColorScheme()` 的默认值，material3 侧
 * 只做「mochi 色值 → MD3 ColorScheme」的逐域映射，供 P4/P5 尚未替换的页面渲染，
 * 从而全 app 一上手就是 Mochi 调色板。预设主题（PresetTheme/presets）保留在
 * themecompat 里等 P5 按 Mochi 形态重做，**运行期不再注入**。
 */

val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

val LocalExtendColors = compositionLocalOf { lightExtendColors() }

@Composable
fun ProvideMaterial3Theme(dark: Boolean, amoled: Boolean, content: @Composable () -> Unit) {
    val legacy = remember(dark, amoled) {
        val mochi = if (dark) darkColorScheme() else lightColorScheme()
        if (dark && amoled) {
            mochi.deriveAmoled().toMaterial3ColorScheme()
        } else {
            mochi.toMaterial3ColorScheme()
        }
    }
    CompositionLocalProvider(LocalContentColor provides legacy.onBackground) {
        MaterialExpressiveTheme(
            colorScheme = legacy,
            typography = Typography,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current

/** AMOLED 纯黑：只压背景/表面，其余角色沿用 Mochi 默认。 */
private fun MochiColorScheme.deriveAmoled(): MochiColorScheme = copy(
    background = AMOLED_DARK_BACKGROUND,
    surface = AMOLED_DARK_BACKGROUND,
)

/** mochi 调色板 → MD3 ColorScheme，逐域拷贝，不做派生计算。 */
private fun MochiColorScheme.toMaterial3ColorScheme(): ColorScheme = ColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceTint = surfaceTint,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    outline = outline,
    outlineVariant = outlineVariant,
    scrim = scrim,
    surfaceBright = surfaceBright,
    surfaceDim = surfaceDim,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
)
