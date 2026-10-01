package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import heizige.kk.kedge.overlays.KedgeAlertDialogSlots
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.khromia.components.AnimatedAlertDialog

/**
 * 标准确认弹窗。**按风格分流**，所以调用点无需改动：
 *
 * - MD3Exp：Khromia 的 `AnimatedAlertDialog`（28dp 圆角 + surfaceContainerHigh），与原实现一致。
 * - Miuix：[KedgeAlertDialogSlots]，走 Miuix 的 WindowDialog，icon/title/text 竖排、
 *   按钮右下横排，形态对齐 KernelSU。
 *
 * 原来的注释只描述 MD3 形态，容易让人以为它在两种风格下表现一致 —— 实际上此前
 * Miuix 下一直是 MD3 弹窗。
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
    when (LocalKedgeStyle.current) {
        KedgeStyle.Miuix -> KedgeAlertDialogSlots(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
        )

        KedgeStyle.MD3Exp -> AnimatedAlertDialog(
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
}
