package heizige.kk.khatkit.app.core.ui.components.ui

import heizige.kk.khatkit.uikit.mochi.MochiButton
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.uikit.mochi.MochiModalBottomSheet
import heizige.kk.khatkit.uikit.mochi.MochiSearchSheetHost
import ui.feice.mochi.basic.Text

/**
 * 项目统一的底部弹层（P4c 起单树：只剩 Mochi 实现）。
 *
 * 历史：以前按 旧双风格开关 在「Kedge 的 Miuix sheet」与「Khromia PrimaryBottomSheet」
 * 之间分流。全面切 Mochi 后两条分支都删掉，改用 [MochiModalBottomSheet]
 * （androidx Popup + Mochi 表面色，不引 material3 ModalBottomSheet）。
 *
 * 签名保持不变，57 个调用点零改动；[painter]/[imageVector]/[scrollable] 只为兼容
 * 旧调用方保留，Mochi 侧不用（图标槽在 P6 按 Mochi 形态重做）。
 *
 * 卡片弹层的 pager/nav/load_more 全部在 [content] 里，本包装只负责壳、标题与按钮行，
 * 因此这些行为逐行未变。
 */
@Composable
fun PrimaryBottomSheet(
    modifier: Modifier = Modifier,
    visible: Boolean,
    title: String,
    painter: Painter? = null,
    imageVector: ImageVector? = null,
    dismissText: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    scrollable: Boolean = true,
    dismissible: Boolean = true,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    MochiModalBottomSheet(
        show = visible,
        onDismissRequest = { if (dismissible) onDismiss() },
        title = title,
        modifier = modifier,
    ) {
        MochiSearchSheetHost { content(onDismiss) }
        if (confirmText != null && onConfirm != null) {
            MochiButton(
                onClick = { onConfirm() },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(text = confirmText)
            }
        }
        if (dismissText != null) {
            MochiTextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
                Text(text = dismissText)
            }
        }
    }
}
