/*
 * 媒体选择器内部的小工具集。
 *
 * 对应 ImageToolbox 的 `core/ui/widget/enhanced`、`core/ui/widget/modifier` 里
 * 只被选择器用到的那几处（Apache-2.0, T8RIN）：柔和投影、带触感的 combinedClickable、
 * 容器底色混合。就地实现而不扩 Kedge，因为它们只服务网格格子 / 相册 chip / 预览条。
 */

package heizige.kk.khatkit.mediapicker.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 给圆角矩形画柔和投影（图片角标、尺寸角标都是它）。
 *
 * 用一层「透明色 + setShadowLayer」的圆角矩形当画笔：先画出投影，再随矩形本体一起
 * 合成。注意这里**不能**再调 `drawColor(CLEAR)` 去擦画布 —— 那样会把刚画好的投影一起
 * 擦掉，角标就成了没有底衬的白字。
 */
fun Modifier.advancedShadow(
    shadowColor: Color = Color.Black,
    alpha: Float = 1f,
    cornersRadius: Dp = 0.dp,
    shadowBlurRadius: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    offsetX: Dp = 0.dp,
) = drawWithCache {
    val shadowArgb = shadowColor.copy(alpha = alpha).toArgb()
    val transparentArgb = shadowColor.copy(alpha = 0f).toArgb()
    val radiusPx = cornersRadius.toPx()
    val blurPx = shadowBlurRadius.toPx()
    val offsetXPx = offsetX.toPx()
    val offsetYPx = offsetY.toPx()
    val paint = android.graphics.Paint().apply {
        this.color = transparentArgb
        setShadowLayer(blurPx, offsetXPx, offsetYPx, shadowArgb)
    }
    onDrawBehind {
        val width = size.width
        val height = size.height
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(0f, 0f, width, height, radiusPx, radiusPx, paint)
        }
    }
}

/** 半透明容器底色：按 [color] 自身 alpha 决定覆盖程度。 */
fun suggestContainerColorBy(color: Color): Color =
    color.copy(alpha = if (color.alpha > 0.5f) 0.4f else 0.2f)

/** 线性混合（粘性头底色 = surfaceContainer 与 primary 混一点再半透明）。 */
fun Color.blend(other: Color, ratio: Float): Color = Color(
    red = red * (1 - ratio) + other.red * ratio,
    green = green * (1 - ratio) + other.green * ratio,
    blue = blue * (1 - ratio) + other.blue * ratio,
    alpha = alpha * (1 - ratio) + other.alpha * ratio,
)

fun HapticFeedback.longPress() = performHapticFeedback(HapticFeedbackType.LongPress)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.hapticsCombinedClickable(
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    Modifier.combinedClickable(
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick?.let {
            {
                haptics.longPress()
                it()
            }
        },
        // 触感已由上面的 longPress() 手动触发，关掉系统默认避免双重震动
        hapticFeedbackEnabled = false,
        onClick = {
            haptics.longPress()
            onClick()
        },
    )
}
