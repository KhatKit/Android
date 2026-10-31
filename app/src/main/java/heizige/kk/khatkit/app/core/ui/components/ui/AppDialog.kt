package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiDialog

/**
 * 通用对话框宿主（DG2 起由 Mochi 警告框渲染，不再用 material3 的 AlertDialog）。
 *
 * 为什么仍保留「宿主 + composable 槽位」这一层：仓内还有 38 个调用点各自带自定义内容
 * （表单、列表、校验、进度、危险操作文案），逐个把按钮换成 MochiDialogAction 会改到每个
 * 调用点的按钮组合与校验时机。改为在这里一次性换成 MochiDialog：遮罩、弹出/退场动效、
 * 圆角卡片、投影、拦住背景触摸全部由 Mochi 提供，调用点只需要改名字。
 *
 * - 标题/图标/正文/按钮仍是调用方给的 composable（顺序、语义、校验时机一律不动）；
 * - 按钮保持右下横排、取消在左、确认在右（与旧弹窗一致）。
 *
 * 参数 [shape] / [containerColor] / [tonalElevation] / [properties] 仅为调用点兼容保留：
 * Mochi 警告框的圆角、底色、阴影、窗口属性由主题统一给，调用点传了也不生效。
 *
 * 历史：此前是「按风格分流」的包装器（Khromia AnimatedAlertDialog / 更早的 Miuix WindowDialog），
 * 两种外观下实际都渲染 material3 弹窗。DG1 把 core/ui + feature/chat 的调用点直连
 * MochiDialog / MochiConfirmationDialog（带 MochiDialogAction + Role 标签），DG2 把剩下 38 个
 * 调用点统一收到这里。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape? = null,
    containerColor: Color? = null,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    // 点外面等同取消：与旧弹窗一致；标题走 content（宿主签名收的是 composable）
    MochiDialog(
        visible = true,
        onDismissRequest = onDismissRequest,
        title = "",
        actions = persistentListOf(),
        modifier = modifier,
        dismissOnOutsideTap = true,
        icon = icon,
    ) {
        title?.let {
            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                ProvideTextStyle(value = MaterialTheme.typography.titleLarge) { it() }
            }
        }
        text?.invoke()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dismissButton?.invoke()
            confirmButton()
        }
    }
}
