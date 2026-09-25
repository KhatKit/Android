package heizige.kk.khatkit.uikit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeCheckbox
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeRadioButton
import heizige.kk.kedge.components.KedgeSlider
import heizige.kk.kedge.components.KedgeSwitch
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.overlays.KedgeProgressIndicatorType
import heizige.kk.khromia.components.PrimaryBottomSheet

/**
 * 声明式表单渲染器（设计文档 7.2）。
 *
 * 脚本描述「要什么 UI」，宿主用 Compose 渲染，交互后把值回传成 table。
 * 脚本永远不直接操作 View。组件 type 必须在
 * [heizige.kk.khatkit.bridge.UiWidgets.ALLOWED] 内。
 *
 * 控件全部走 Kedge，跟随 [KhatKitTheme] 切换风格。
 */
@Composable
fun KhatKitForm(
    title: String,
    items: List<Map<String, Any?>>,
    onSubmit: (Map<String, Any?>) -> Unit,
    onCancel: () -> Unit,
) {
    val values = remember { mutableStateMapOf<String, Any?>() }

    items.forEach { item ->
        val id = item["id"]?.toString()
        if (id != null && !values.containsKey(id) && item.containsKey("default")) {
            values[id] = item["default"]
        }
    }

    PrimaryBottomSheet(
        visible = true,
        title = title,
        imageVector = Icons.Filled.Edit,
        confirmText = stringResource(R.string.khatkit_form_confirm),
        onConfirm = { onSubmit(values.toMap()) },
        onDismiss = onCancel,
        scrollable = true,
    ) { dismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { item -> FormWidget(item, values) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                KedgeTextButton(onClick = { dismiss() }) {
                    Text(stringResource(R.string.khatkit_form_cancel))
                }
            }
        }
    }
}

@Composable
private fun FormWidget(item: Map<String, Any?>, values: MutableMap<String, Any?>) {
    val type = item["type"]?.toString() ?: "text"
    val id = item["id"]?.toString().orEmpty()
    val label = item["label"]?.toString().orEmpty()

    when (type) {
        "text", "markdown" -> Text(label.ifBlank { item["value"]?.toString().orEmpty() })
        "divider" -> HorizontalDivider()

        "input" -> KedgeOutlinedTextField(
            value = values[id]?.toString().orEmpty(),
            onValueChange = { values[id] = it },
            label = label,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        "number" -> KedgeOutlinedTextField(
            value = values[id]?.toString().orEmpty(),
            onValueChange = { values[id] = it.toDoubleOrNull() ?: it },
            label = label,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        "switch" -> FormRow(label) {
            KedgeSwitch(
                checked = values[id] as? Boolean ?: false,
                onCheckedChange = { values[id] = it },
            )
        }

        "slider" -> {
            val min = (item["min"] as? Number)?.toFloat() ?: 0f
            val max = (item["max"] as? Number)?.toFloat() ?: 100f
            val current = (values[id] as? Number)?.toFloat()
                ?: (item["default"] as? Number)?.toFloat() ?: min
            Column {
                Text("$label: ${current.toInt()}")
                KedgeSlider(
                    value = current,
                    onValueChange = { values[id] = it },
                    valueRange = min..max,
                )
            }
        }

        "select", "radio" -> {
            val options = (item["options"] as? List<*>).orEmpty().map { it.toString() }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label)
                options.forEach { option ->
                    FormRow(option) {
                        KedgeRadioButton(
                            selected = values[id] == option,
                            onClick = { values[id] = option },
                        )
                    }
                }
            }
        }

        "file_picker", "dir_picker" -> KedgeOutlinedTextField(
            value = values[id]?.toString().orEmpty(),
            onValueChange = { values[id] = it },
            label = stringResource(R.string.khatkit_form_path_label, label),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        "progress" -> {
            val ratio = (values[id] as? Number)?.toFloat()
                ?: (item["ratio"] as? Number)?.toFloat() ?: 0f
            KedgeProgressIndicator(
                progress = ratio,
                type = KedgeProgressIndicatorType.Linear,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        "button" -> KedgeButton(
            onClick = { },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label.ifBlank { stringResource(R.string.khatkit_form_action) })
        }

        "custom" -> {
            val renderer = UiRegistry.resolve(item["renderer"]?.toString())
            if (renderer != null) {
                renderer(item)
            } else {
                Text(
                    stringResource(
                        R.string.khatkit_form_renderer_missing,
                        label,
                        item["renderer"].toString(),
                    )
                )
            }
        }

        else -> {
            KedgeCheckbox(checked = false, onCheckedChange = null)
            Text(stringResource(R.string.khatkit_form_widget_unsupported, label, type))
        }
    }
}

@Composable
private fun FormRow(label: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        trailing()
    }
}
