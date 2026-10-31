package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole

/**
 * 标准确认弹窗：Mochi 警告框（DG1 起不再走 material3 AlertDialog）。
 *
 * 取消/确认是胶囊按钮（[MochiDialogActionRole.Cancel] 排在左边，[MochiDialogActionRole.Primary] 主色）；
 * 点外面等同取消，与原来的 AlertDialog 一致。
 * MochiDialog 的按钮语义是「先执行 onClick，再调 onDismissRequest」：取消动作不写 onClick，
 * 关闭统一走 [onDismiss]；确认动作走 [onConfirm]，随后的 onDismissRequest 同样收口到 [onDismiss]。
 */
@Composable
fun RikkaConfirmDialog(
    show: Boolean,
    title: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: @Composable () -> Unit,
) {
    // 不再用 if (show) 卸载：visible 交给 MochiDialog，关闭时才有退场动画
    MochiDialog(
        visible = show,
        onDismissRequest = onDismiss,
        title = title,
        actions = persistentListOf(
            MochiDialogAction(label = dismissText, role = MochiDialogActionRole.Cancel),
            MochiDialogAction(
                label = confirmText,
                role = MochiDialogActionRole.Primary,
                onClick = onConfirm,
            ),
        ),
        dismissOnOutsideTap = true,
        content = { text() },
    )
}
