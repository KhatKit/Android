package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog

/**
 * 标准确认弹窗。**按风格分流**，所以调用点无需改动：
 *
 * - MD3Exp：Khromia 的 `AnimatedAlertDialog`（28dp 圆角 + surfaceContainerHigh），与原实现一致。
 * - 旧双风格（已下线）：曾走 Miuix 的 WindowDialog；现在统一走 Khromia AnimatedAlertDialog。
 *   按钮右下横排，形态对齐 KernelSU。
 *
 * 原来的注释只描述 MD3 形态，容易让人以为它在两种风格下表现一致 —— 实际上此前
 * Miuix 下一直是 MD3 弹窗。
 *
 * DG1（对话框迁移第一批）之后：core/ui/components + feature/chat 的调用点已全部直连
 * `ui.feice.mochi.basic.MochiDialog` / `MochiConfirmationDialog`（见各处 DG1 注释），
 * 本包装器只剩 38 个仓外调用点（assistant/backup/explore/extensions/history/imggen/search/
 * settings/tasks/workspace），留给后续批次一次性迁完再删，避免本批越界改调用点签名。
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            tonalElevation = tonalElevation,
            properties = properties,
        )
}
