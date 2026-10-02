package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.material3.TooltipBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import heizige.kk.kedge.components.KedgeTextTooltipBox

/**
 * 悬浮提示，按风格分流：Miuix 下走 Miuix 的 TooltipBox，MD3Exp 走原生。
 *
 * 之前这里固定用 MD3 的 [androidx.compose.material3.TooltipBox]，Miuix 下完全不显示
 * （Miuix 没有那套 TooltipScope）。两边接收者类型不同，所以在这里收口。
 *
 * 文案收 [text] 而不是插槽：Miuix 的便捷重载只收字符串，而现有调用点全是纯文字。
 */
@Composable
fun Tooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    KedgeTextTooltipBox(
        text = text,
        modifier = modifier,
        content = content,
    )
}
