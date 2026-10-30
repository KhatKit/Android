package heizige.kk.khatkit.uikit.mochi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/** 编辑弹窗的单个字段（U4d：替代 Kedge 的 EditFieldConfig）。 */
data class KkEditField(
    val label: String,
    val initialValue: String = "",
    val placeholder: String? = null,
    val maxLength: Int? = null,
    val onValidate: (String) -> String? = { null },
)

/**
 * 编辑弹窗：material3 AlertDialog + Md3OutlinedTextField 组合，替代 Kedge 的 KedgeEditDialog。
 * 校验失败时把错误文案作为 supportingText 显示，确认按钮置灰。
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
    if (!visible) return
    val values = remember(fields) { mutableStateListOf(*fields.map { it.initialValue }.toTypedArray()) }
    val errors = fields.map { it.onValidate(values[fields.indexOf(it)]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
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
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(values.toList()) },
                enabled = errors.all { it == null },
            ) { Text(confirmText ?: "OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText ?: "Cancel") }
        },
    )
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
