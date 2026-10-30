package heizige.kk.khatkit.uikit.mochi

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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

/**
 * AMOLED：把**整条表面阶梯**重锚到纯黑，而不是只把 background/surface 打黑。
 *
 * 只压那两个角色会留两个洞：
 *  · surface 变 #000、而 surfaceContainer 仍是 N12 的灰 ⇒ 黑底上突然浮一层明显灰块，
 *    卡片不再像「一档」，像一个异物；
 *  · 顶栏取 surfaceContainer（N12）⇒ 黑页上一条灰带，浅/深都像配色反了。
 * 重锚后阶梯仍**单调**（Low < Container < High < Highest 逐档变亮约 2~5 个 tone），
 * 纯黑底上层次还在，且整页真正纯黑。
 *
 * 只在深色配色上生效：`amoledDark` 是独立开关，浅色下打开它不能把界面打成黑块。
 */
fun MochiColorScheme.withAmoledBackground(amoled: Boolean): MochiColorScheme =
    if (!amoled || !isDark) this
    else copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = lerp(surfaceContainerLow, Color.Black, 0.55f),
        surfaceContainer = lerp(surfaceContainer, Color.Black, 0.40f),
        surfaceContainerHigh = lerp(surfaceContainerHigh, Color.Black, 0.25f),
        surfaceContainerHighest = lerp(surfaceContainerHighest, Color.Black, 0.10f),
    )

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

/**
 * MD3 的 12 个 **fixed* / onFixed*** 角色：与明暗**无关**的 tone-fixed 色
 * （primary 家族 = P90/P80/P10/P30 一族；secondary/tertiary 同理）。
 *
 * `MochiColorScheme` 只给「当前明暗那一套」的 37 个角色，没有 fixed 系列。
 * 不显式传的话 material3 会走自己的默认推导，动态色组件（featured 卡、carousel、
 * 工具栏底色等）就会拿到跟 Mochi 无关的紫调默认色。这里从**已有角色**按 MD3 tone 规范反推，
 * 每个家族需要的四个 tone 在两个明暗里各有一半，正好互补：
 *
 * | tone | light 取谁 | dark 取谁 |
 * |---|---|---|
 * | 90 (Fixed) | primaryContainer | onPrimaryContainer |
 * | 80 (FixedDim) | inversePrimary（secondary/tertiary 无 inverse → 由 40→90 插值） | primary |
 * | 10 (OnFixed) | onPrimaryContainer | 由 30 向黑插值（暗色下没有 10 档角色） |
 * | 30 (OnFixedVariant) | 由 10→40 插值 | primaryContainer |
 *
 * 插值用 compose 的 `lerp`（Oklab，不发灰），误差约 1~3 个 tone，正文/图标对比度不受影响。
 * 一旦 Mochi 对外暴露 tonal palette，这里应改成精确取 tone。
 */
private class MochiFixedRoles(
    val primaryFixed: Color,
    val primaryFixedDim: Color,
    val onPrimaryFixed: Color,
    val onPrimaryFixedVariant: Color,
    val secondaryFixed: Color,
    val secondaryFixedDim: Color,
    val onSecondaryFixed: Color,
    val onSecondaryFixedVariant: Color,
    val tertiaryFixed: Color,
    val tertiaryFixedDim: Color,
    val onTertiaryFixed: Color,
    val onTertiaryFixedVariant: Color,
)

/** 90/80/10/30 四个 tone 的取法见 [MochiFixedRoles] 注释里的表。 */
private fun MochiColorScheme.fixedRoles(): MochiFixedRoles =
    if (!isDark) {
        MochiFixedRoles(
            primaryFixed = primaryContainer,
            primaryFixedDim = inversePrimary,
            onPrimaryFixed = onPrimaryContainer,
            onPrimaryFixedVariant = lerp(onPrimaryContainer, primary, 2f / 3f),
            secondaryFixed = secondaryContainer,
            secondaryFixedDim = lerp(secondary, secondaryContainer, 0.8f),
            onSecondaryFixed = onSecondaryContainer,
            onSecondaryFixedVariant = lerp(onSecondaryContainer, secondary, 2f / 3f),
            tertiaryFixed = tertiaryContainer,
            tertiaryFixedDim = lerp(tertiary, tertiaryContainer, 0.8f),
            onTertiaryFixed = onTertiaryContainer,
            onTertiaryFixedVariant = lerp(onTertiaryContainer, tertiary, 2f / 3f),
        )
    } else {
        MochiFixedRoles(
            primaryFixed = onPrimaryContainer,
            primaryFixedDim = primary,
            onPrimaryFixed = lerp(primaryContainer, Color.Black, 2f / 3f),
            onPrimaryFixedVariant = primaryContainer,
            secondaryFixed = onSecondaryContainer,
            secondaryFixedDim = secondary,
            onSecondaryFixed = lerp(secondaryContainer, Color.Black, 2f / 3f),
            onSecondaryFixedVariant = secondaryContainer,
            tertiaryFixed = onTertiaryContainer,
            tertiaryFixedDim = tertiary,
            onTertiaryFixed = lerp(tertiaryContainer, Color.Black, 2f / 3f),
            onTertiaryFixedVariant = tertiaryContainer,
        )
    }

private fun MochiColorScheme.toMaterial3(): ColorScheme {
    val fixed = fixedRoles()
    return ColorScheme(
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
    // fixed*：显式映射，避免动态色组件落回 material3 默认推导（见 MochiFixedRoles）
    primaryFixed = fixed.primaryFixed,
    primaryFixedDim = fixed.primaryFixedDim,
    onPrimaryFixed = fixed.onPrimaryFixed,
    onPrimaryFixedVariant = fixed.onPrimaryFixedVariant,
    secondaryFixed = fixed.secondaryFixed,
    secondaryFixedDim = fixed.secondaryFixedDim,
    onSecondaryFixed = fixed.onSecondaryFixed,
    onSecondaryFixedVariant = fixed.onSecondaryFixedVariant,
    tertiaryFixed = fixed.tertiaryFixed,
    tertiaryFixedDim = fixed.tertiaryFixedDim,
    onTertiaryFixed = fixed.onTertiaryFixed,
    onTertiaryFixedVariant = fixed.onTertiaryFixedVariant,
)
}

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
