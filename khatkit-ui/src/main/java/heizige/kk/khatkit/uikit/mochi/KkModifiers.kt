package heizige.kk.khatkit.uikit.mochi

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll

/**
 * 全局的内容尺寸动画：曲线和 Mochi 的 `mochiAnimateHeight` 默认弹簧**逐项一致**
 * （dampingRatio = 1f、stiffness = 380f、visibilityThreshold 0.5dp 那一档）。
 *
 * 用在「内容会变高/变矮」的容器上：行展开说明、开关切换后的副标题、输入框从单行长成多行、
 * 底部面板正文变长……不再各页自己挑一套 spring。
 */
val KkContentSizeSpec = spring<IntSize>(
    dampingRatio = 1f,
    stiffness = 380f,
    visibilityThreshold = IntSize(1, 1),
)

/** 见 [KkContentSizeSpec]：给任意容器加 Mochi 曲线的尺寸动画。 */
fun Modifier.kkAnimateContentSize(
    alignment: Alignment = Alignment.TopStart,
): Modifier = animateContentSize(animationSpec = KkContentSizeSpec, alignment = alignment)

/**
 * U4f1：替代 Khromia 的 `Modifier.fadingEdge`。用 DstIn 的 alpha 渐变把上下边缘擦淡，
 * 不引第三方、不做 offscreen 合成；四边都为 0 时原样返回。
 */
fun Modifier.fadingEdge(
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
    left: Dp = 0.dp,
    right: Dp = 0.dp,
    @Suppress("UNUSED_PARAMETER") strength: Float = 1f,
): Modifier {
    if (top <= 0.dp && bottom <= 0.dp && left <= 0.dp && right <= 0.dp) return this
    return this.drawWithContent {
        drawContent()
        val topPx = top.toPx()
        val bottomPx = bottom.toPx()
        if (topPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = topPx,
                ),
                size = androidx.compose.ui.geometry.Size(size.width, topPx),
                blendMode = BlendMode.DstIn,
            )
        }
        if (bottomPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - bottomPx,
                    endY = size.height,
                ),
                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - bottomPx),
                size = androidx.compose.ui.geometry.Size(size.width, bottomPx),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

/**
 * U4f1：替代 Khromia 的 `FullscreenPopup`。material3 Dialog + usePlatformDefaultWidth=false。
 * passThroughTouches 无法在 Dialog 上表达（Dialog 窗口总是吃触摸），保留参数仅为调用点零改动。
 */
@Composable
fun KkFullscreenPopup(
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") passThroughTouches: Boolean = false,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Dialog 是独立窗口，组合根的 ProvideMochiOverscroll 传不进来：在窗口内再 provide 一次，
        // 全屏弹层里的列表才有 Mochi 的橡皮筋越界（否则只有平台默认拉伸）。
        ProvideMochiOverscroll {
            Box(modifier = modifier.fillMaxSize()) { content() }
        }
    }
}

/** 提示/状态文案的通用样式占位（替代 Khromia KkToastDefaults 的取色）。 */
object KkToastDefaults {
    const val Alpha: Float = 0.95f
    const val SHORT_DURATION: Long = Toast.SHORT_DURATION

    val color: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

    val contentColor: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface

    /** 上游：卡片底部留白 = 屏幕短边 * 0.2f。 */
    const val BottomScreenFraction: Float = 0.2f

    /** 上游：卡片最大宽度 = 屏幕短边 * 0.7f。 */
    const val MaxWidthScreenFraction: Float = 0.7f

    /** 提示文字样式（沿用 Mochi 的正文级字阶）。 */
    val textStyle: androidx.compose.ui.text.TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium
}

/** 屏幕短边（dp），替代 Khromia 的 screenSizeMin。 */
val screenSizeMin: Dp
    @Composable get() = with(androidx.compose.ui.platform.LocalConfiguration.current) {
        androidx.compose.ui.unit.Dp(minOf(screenWidthDp, screenHeightDp).toFloat())
    }