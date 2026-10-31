package heizige.kk.khatkit.uikit.mochi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.persistentListOf
// 同包里还有项目内的兼容壳 MochiDialog（Popup 版），这里要的是 Mochi 库的警告框，用别名避开重名
import ui.feice.mochi.basic.MochiDialog as MochiBasicDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole

/** 编辑弹窗的单个字段（U4d：替代 Kedge 的 EditFieldConfig）。 */
data class KkEditField(
    val label: String,
    val initialValue: String = "",
    val placeholder: String? = null,
    val maxLength: Int? = null,
    val onValidate: (String) -> String? = { null },
)

/**
 * 编辑弹窗：Mochi 警告框（DG1 起不再走 material3 AlertDialog）+ Md3OutlinedTextField 组合，
 * 替代 Kedge 的 KedgeEditDialog。
 * 校验失败时把错误文案作为 supportingText 显示，确认按钮置灰（MochiDialogAction.enabled）。
 * 调用点签名不变。
 */
@Composable
fun KkEditDialog(
    visible: Boolean,
    title: String,
    fields: List<KkEditField>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String? = null,
    dismissText: String? = null,
) {
    // 每次弹出都用 initialValue 重新起一份（旧实现的 if (!visible) return 就是这个效果）
    val values = remember(fields, visible) {
        mutableStateListOf(*fields.map { it.initialValue }.toTypedArray())
    }
    val errors = fields.mapIndexed { index, field ->
        field.onValidate(values.getOrElse(index) { "" })
    }
    MochiBasicDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = title,
        actions = persistentListOf(
            MochiDialogAction(
                label = dismissText ?: "Cancel",
                role = MochiDialogActionRole.Cancel,
            ),
            MochiDialogAction(
                label = confirmText ?: "OK",
                role = MochiDialogActionRole.Primary,
                // 校验没过就不给点：MochiDialog 点按钮后必关，这里靠 enabled 把关闭口收在按钮状态上
                enabled = errors.all { it == null },
                onClick = { onConfirm(values.toList()) },
            ),
        ),
        dismissOnOutsideTap = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp())) {
            fields.forEachIndexed { index, field ->
                val error = field.onValidate(values.getOrElse(index) { "" })
                Md3OutlinedTextField(
                    value = values.getOrElse(index) { "" },
                    onValueChange = { input ->
                        val clipped = field.maxLength?.let { input.take(it) } ?: input
                        if (index < values.size) values[index] = clipped
                    },
                    label = field.label,
                    placeholder = field.placeholder,
                    isError = error != null,
                    supportingText = error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
