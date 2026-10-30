package heizige.kk.khatkit.uikit.mochi

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ui.feice.mochi.theme.MochiColorScheme
import ui.feice.mochi.theme.MochiShapes
import ui.feice.mochi.theme.MochiTextStyles
import ui.feice.mochi.theme.MochiTheme

/**
 * U2 正式 Bridge：**Mochi 是唯一 token 源**，material3 只作渲染骨架。
 *
 * 页面直接用 MD3 组件就会拿到 Mochi 外观：
 *  - 颜色：MochiColorScheme -> material3 ColorScheme（37 个角色同名逐域拷贝，无派生计算）
 *  - 形状：MochiShapes -> material3 Shapes（MochiCapsule 的 full 无 MD3 对应角色，不映射）
 *  - 字阶：MochiTextStyles 的 15 个 MD3 角色 -> Typography（iOS 侧 largeTitle/navTitle/body 等不进 MD3）
 *  - 动效：MochiMotionScheme -> MotionScheme（当前先取 expressive，逐条 spring 映射待 API 稳定）
 *
 * 这里**不注入**旧色板/旧字阶：extend 调色板与 GoogleSans 只在功能点直接引用，不进主题。
 */
@Immutable
data class MochiMaterialTokens(
    val colorScheme: ColorScheme,
    val shapes: Shapes,
    val typography: Typography,
    val motionScheme: MotionScheme,
)

/** AMOLED 纯黑只压 background/surface，其余角色沿用 Mochi。 */
fun MochiColorScheme.withAmoledBackground(amoled: Boolean): MochiColorScheme =
    if (amoled) copy(background = Color(0xFF000000), surface = Color(0xFF000000)) else this

@Composable
fun rememberMochiMaterialTokens(amoled: Boolean = false): MochiMaterialTokens {
    val colors = MochiTheme.colorScheme
    val shapes = MochiTheme.shapes
    val textStyles = MochiTheme.textStyles
    val motion = MochiTheme.motionScheme
    return remember(colors, shapes, textStyles, motion, amoled) {
        MochiMaterialTokens(
            colorScheme = colors.withAmoledBackground(amoled).toMaterial3(),
            shapes = shapes.toMaterial3(),
            typography = textStyles.toMaterial3(),
            motionScheme = MotionScheme.expressive(),
        )
    }
}

private fun MochiColorScheme.toMaterial3(): ColorScheme = ColorScheme(
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

/**
 * 圆角**统一走 Mochi 的 G2 连续曲率**（2026-10-30 样式归属拍板）：
 * MochiShapes 的每个值是 kyant-capsule 的 ContinuousRoundedRectangle /
 * ContinuousCapsule，它们本来就 extends CornerBasedShape，所以可以**原样**塞进
 * material3 的 Shapes —— MD3 组件（Card/Button/TextField/Sheet…）拿到的就是
 * Mochi 的连续曲率，而不是普通 RoundedCornerShape。页面侧不要再手写圆角。
 */
private fun MochiShapes.toMaterial3(): Shapes = Shapes(
    extraSmall = extraSmall.asCornerBased(),
    small = small.asCornerBased(),
    medium = medium.asCornerBased(),
    large = large.asCornerBased(),
    extraLarge = extraLarge.asCornerBased(),
)

/** MochiShapes 的类型是 Shape，运行时是 ContinuousRoundedRectangle（CornerBasedShape）。 */
private fun androidx.compose.ui.graphics.Shape.asCornerBased():
    androidx.compose.foundation.shape.CornerBasedShape =
    this as? androidx.compose.foundation.shape.CornerBasedShape
        ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)

private fun MochiTextStyles.toMaterial3(): Typography = Typography(
    displayLarge = displayLarge,
    displayMedium = displayMedium,
    displaySmall = displaySmall,
    headlineLarge = headlineLarge,
    headlineMedium = headlineMedium,
    headlineSmall = headlineSmall,
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    titleSmall = titleSmall,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    bodySmall = bodySmall,
    labelLarge = labelLarge,
    labelMedium = labelMedium,
    labelSmall = labelSmall,
)
