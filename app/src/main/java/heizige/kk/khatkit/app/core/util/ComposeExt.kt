package heizige.kk.khatkit.app.core.util

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = this.calculateStartPadding(layoutDirection) + other.calculateStartPadding(
            layoutDirection
        ),
        top = this.calculateTopPadding() + other.calculateTopPadding(),
        end = this.calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection),
        bottom = this.calculateBottomPadding() + other.calculateBottomPadding()
    )
}

/**
 * [plus] 的反向操作：把已经加进去的边距再扣回来。和 [plus] 一样按 start/end 语义处理横向
 * （PaddingValues 的 `minus` 只在 foundation 里、且不做 RTL 归一），逐边夹到 ≥ 0 ——
 * `PaddingValues(top = 负)` 会在构造时直接抛 "Padding must be non-negative"。
 */
@Composable
operator fun PaddingValues.minus(other: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = (this.calculateStartPadding(layoutDirection) - other.calculateStartPadding(
            layoutDirection
        )).coerceAtLeast(0.dp),
        top = (this.calculateTopPadding() - other.calculateTopPadding()).coerceAtLeast(0.dp),
        end = (this.calculateEndPadding(layoutDirection) - other.calculateEndPadding(layoutDirection))
            .coerceAtLeast(0.dp),
        bottom = (this.calculateBottomPadding() - other.calculateBottomPadding()).coerceAtLeast(0.dp)
    )
}

fun Color.toCssHex(): String {
    val alpha = (alpha * 255).toInt()
    val red = (red * 255).toInt()
    val green = (green * 255).toInt()
    val blue = (blue * 255).toInt()
    return "#${String.format("%02X%02X%02X%02X", red, green, blue, alpha)}"
}

@Composable
fun Dp.toSp(): TextUnit = with(LocalDensity.current) {
    this@toSp.toSp()
}

@Composable
fun TextUnit.toDp(): Dp = with(LocalDensity.current) {
    // Density.toDp(TextUnit) 仅支持 Sp 单位，Em/Unspecified 会抛 "Only Sp can convert to Px"
    if (this@toDp.isSp) this@toDp.toDp() else 0.dp
}

fun TextFieldState.insertAtCursor(text: String) {
    this.edit {
        insert(selection.start, text)
    }
}

// 用于未开启 autoMirror、但在当前语境下有方向含义的图标
@Composable
fun Modifier.mirrorForRtl(): Modifier =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) scale(scaleX = -1f, scaleY = 1f) else this
