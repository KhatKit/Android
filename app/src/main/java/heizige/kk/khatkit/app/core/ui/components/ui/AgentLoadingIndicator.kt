package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.feice.mochi.theme.MochiTheme

/**
 * Agent 风格加载指示器 - 基于应用图标 |<_ 的纯 Compose 实现
 *
 * 设计理念：
 * - 使用应用图标 |<_ 字形
 * - 三个元素依次脉动：竖线 → 尖括号 → 下划线
 * - 纯 Compose Canvas 绘制，无需 AndroidView
 * - 自动适配主题颜色
 */
@Composable
fun AgentLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,  // 增大默认尺寸从 32dp 到 48dp
    color: Color = MochiTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "icon_loading")

    // 竖线透明度 (0-600ms)
    val barAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar_alpha"
    )

    // 尖括号透明度 (600-1200ms)
    val chevronAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 300,
                delayMillis = 600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevron_alpha"
    )

    // 下划线透明度 (1200-1800ms)
    val underscoreAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 300,
                delayMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "underscore_alpha"
    )

    Canvas(
        modifier = modifier.size(size)
    ) {
        // 这些矢量坐标来自 512dp 画布中间的约 130dp 图形，不能直接除以 512，
        // 否则 48dp 指示器里只会剩一个约 11dp 的小标记。按图形自身边界缩放并居中。
        val scale = size.toPx() / 160f
        val origin = size.toPx() / 2f - 252f * scale
        fun coordinate(value: Float) = value * scale + origin
        val strokeWidth = 12.9705f * scale

        // 竖线 |
        drawLine(
            color = color.copy(alpha = barAlpha),
            start = Offset(coordinate(193.43f), coordinate(193.43f)),
            end = Offset(coordinate(193.43f), coordinate(310.57f)),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = color.copy(alpha = barAlpha),
            start = Offset(coordinate(206.84f), coordinate(193.43f)),
            end = Offset(coordinate(206.84f), coordinate(310.57f)),
            strokeWidth = strokeWidth
        )

        // 尖括号 <
        val chevronPath = Path().apply {
            moveTo(coordinate(310.57f), coordinate(197.14f))
            lineTo(coordinate(249.78f), coordinate(225.95f))
            lineTo(coordinate(249.78f), coordinate(234.28f))
            lineTo(coordinate(310.57f), coordinate(259.76f))
            lineTo(coordinate(310.57f), coordinate(245.83f))
            lineTo(coordinate(268.70f), coordinate(229.52f))
            lineTo(coordinate(310.57f), coordinate(211.07f))
            close()
        }
        drawPath(
            path = chevronPath,
            color = color.copy(alpha = chevronAlpha),
            style = Fill
        )

        // 下划线 _
        drawLine(
            color = color.copy(alpha = underscoreAlpha),
            start = Offset(coordinate(249.87f), coordinate(306.46f)),
            end = Offset(coordinate(310.48f), coordinate(306.46f)),
            strokeWidth = 8.04f * scale
        )
    }
}

/**
 * Agent 风格加载指示器 - 文字版
 *
 * 显示"思考中"文字加图标动画
 */
@Composable
fun AgentLoadingIndicatorWithText(
    modifier: Modifier = Modifier,
    text: String = "思考中",
    color: Color = MochiTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ui.feice.mochi.basic.Text(
            text = text,
            style = MochiTheme.textStyles.body,
            color = color
        )
        AgentLoadingIndicator(
            size = 24.dp,
            color = color
        )
    }
}

/**
 * Agent 风格加载指示器 - 脉动圆点版（备选）
 *
 * 三个脉动圆点，依次延迟启动
 */
@Composable
fun AgentLoadingDots(
    modifier: Modifier = Modifier,
    color: Color = MochiTheme.colorScheme.primary,
    dotSize: Dp = 8.dp,
    spacing: Dp = 6.dp,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            PulsatingDot(
                color = color,
                size = dotSize,
                delay = index * 150
            )
        }
    }
}

@Composable
private fun PulsatingDot(
    color: Color,
    size: Dp,
    delay: Int,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsating_dot_$delay")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 600,
                delayMillis = delay,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 600,
                delayMillis = delay,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .background(
                color = color.copy(alpha = alpha),
                shape = CircleShape
            )
    )
}

/**
 * Agent 风格加载指示器 - 旋转圆环版（备选）
 *
 * 使用 Mochi 的 ActivityIndicator
 */
@Composable
fun AgentLoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = MochiTheme.colorScheme.primary,
    size: Dp = 24.dp,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        ui.feice.mochi.basic.MochiActivityIndicator(
            color = color,
            modifier = Modifier.size(size)
        )
    }
}
