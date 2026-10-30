package heizige.kk.khatkit.uikit.mochi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import ui.feice.mochi.theme.MochiTheme

/**
 * P4a：Kedge 容器族的 Mochi 版。
 *
 * 目标是把 MochiSurface / MochiCard / MochiWarningCard / 分隔线全部换成 Mochi 的
 * 表面色与圆角（MochiTheme.colorScheme / MochiShapes），**不再按 KedgeStyle 分叉**。
 * 参数名与旧 Kedge 组件保持一致，让调用点只改名字；已知缺口：
 * - `colors`（旧 Miuix/MD3 CardColors）只收不用：Mochi 卡片配色由主题决定，P6 用
 *   Mochi 的角色重做自定义配色；
 * - 旧的 Miuix 毛玻璃/backdrop 语义不再生效（P6 的 MochiGlassSurface 接手）。
 */

@Composable
fun MochiSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    shape: Shape? = null,
    shadowElevation: Dp = 0.dp,
    tonalElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val container = if (color == Color.Unspecified) MochiTheme.colorScheme.surface else color
    val contentColorResolved =
        if (contentColor == Color.Unspecified) MochiTheme.colorScheme.onSurface else contentColor
    val resolvedShape = shape ?: MochiTheme.shapes.medium
    val clickable = if (onClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }
    CompositionLocalProvider(LocalContentColor provides contentColorResolved) {
        Box(
            modifier = modifier
                .clip(resolvedShape)
                .background(container)
                .then(clickable),
        ) {
            content()
        }
    }
}

@Composable
fun MochiCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    shape: Shape? = null,
    onClick: (() -> Unit)? = null,
    /** 旧 Miuix/MD3 CardColors：只收不用，Mochi 卡片配色由主题决定（P6 重做）。 */
    colors: Any? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val container =
        if (color == Color.Unspecified) MochiTheme.colorScheme.surfaceContainer else color
    val contentColorResolved =
        if (contentColor == Color.Unspecified) MochiTheme.colorScheme.onSurface else contentColor
    val resolvedShape = shape ?: MochiTheme.shapes.large
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    CompositionLocalProvider(LocalContentColor provides contentColorResolved) {
        Box(
            modifier = modifier
                .clip(resolvedShape)
                .background(container)
                .then(clickable),
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/** 状态提示卡（警告/错误/信息）。配色取 Mochi 的 error / secondary 容器色。 */
@Composable
fun MochiWarningCard(
    message: String,
    modifier: Modifier = Modifier,
    /** 旧 KedgeStatusLevel：只用于选色，传 null 走 warning 默认（error 容器色）。 */
    level: Any? = null,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val scheme = MochiTheme.colorScheme
    MochiSurface(
        modifier = modifier,
        onClick = onClick,
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
        shape = MochiTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            androidx.compose.material3.Text(message)
            action?.invoke()
        }
    }
}

@Composable
fun MochiHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp? = null,
    color: Color? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness ?: 0.5.dp)
            .background(color ?: MochiTheme.colorScheme.outlineVariant),
    )
}

@Composable
fun MochiVerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp? = null,
    color: Color? = null,
) {
    Box(
        modifier = modifier
            .width(thickness ?: 0.5.dp)
            .background(color ?: MochiTheme.colorScheme.outlineVariant),
    )
}
