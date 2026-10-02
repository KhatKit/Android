/*
 * 选择器 UI 的通用小部件。
 */

package heizige.kk.khatkit.mediapicker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.overlays.KedgeProgressIndicatorType

/**
 * `AnimatedVisibility` 的 Box 作用域版本：上游用它是因为浮层（FAB 列、加载遮罩）
 * 需要 `Modifier.align(...)` 这类 BoxScope 修饰符。
 */
@Composable
fun BoxAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = androidx.compose.animation.fadeIn() +
        androidx.compose.animation.scaleIn(initialScale = 0.9f),
    exit: ExitTransition = androidx.compose.animation.fadeOut() +
        androidx.compose.animation.scaleOut(targetScale = 0.9f),
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
    ) { content() }
}

/**
 * 分组按钮的圆角：组内两端用大圆角（16dp）包住，中间用 4dp 咬合。
 *
 * @param vertical true = 竖排（第一个在上），false = 横排（第一个在左）。
 */
@Composable
fun groupedShape(
    index: Int,
    size: Int,
    vertical: Boolean = true,
    roundedCorner: Dp = 16.dp,
    defaultCorner: Dp = 4.dp,
): Shape {
    val allRounded = index == -1 || size == 1
    fun corner(rounded: Boolean): Dp = if (rounded) roundedCorner else defaultCorner

    return if (allRounded) {
        RoundedCornerShape(roundedCorner)
    } else if (vertical) {
        RoundedCornerShape(
            topStart = corner(index == 0),
            topEnd = corner(index == 0),
            bottomStart = corner(index == size - 1),
            bottomEnd = corner(index == size - 1),
        )
    } else {
        RoundedCornerShape(
            topStart = corner(index == 0),
            bottomStart = corner(index == 0),
            topEnd = corner(index == size - 1),
            bottomEnd = corner(index == size - 1),
        )
    }
}

/** 顶栏底部的 1dp 分隔线，`width = 0.dp` 时完全不画（对应"有相册行才显示分割线"）。 */
@Composable
fun Modifier.drawBottomHairline(
    width: Dp,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
) = drawBehind {
    if (width > 0.dp) {
        val strokePx = width.toPx()
        val y = size.height - strokePx / 2f
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokePx)
    }
}

/** 重查数据时的波浪进度条。 */
@Composable
fun WavyPickerProgress(modifier: Modifier = Modifier) {
    KedgeProgressIndicator(
        type = KedgeProgressIndicatorType.Wavy,
        modifier = modifier.fillMaxWidth(),
    )
}
