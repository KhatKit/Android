package heizige.kk.khatkit.uikit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import heizige.kk.khatkit.ui.UiSheetOptions

/** 宽度达到该值后表单排两列（横屏手机 / 平板）。 */
private val TWO_COLUMN_WIDTH = 600.dp

/** 两列布局的内容最大宽度，避免宽屏下每列过宽。 */
private val WIDE_FORM_MAX_WIDTH = 960.dp

/** 这些组件横跨整行，不参与两列排列。 */
private val FULL_WIDTH_TYPES = setOf("text", "markdown", "divider", "progress", "button")

/**
 * 声明式表单渲染器（设计文档 7.2）。
 *
 * 脚本描述「要什么 UI」，宿主用 Compose 渲染，交互后把值回传成 table。
 * 脚本永远不直接操作 View。组件 type 必须在
 * [heizige.kk.khatkit.bridge.UiWidgets.ALLOWED] 内。
 *
 * 控件全部走 Kedge，跟随 [KhatKitTheme] 切换风格。
 * [options] 来自 `ui.form` 的第三个参数：全屏 / 横屏 / 高度；宽屏时表单自动两列。
 */
@Composable
fun KhatKitForm(
    title: String,
    items: List<Map<String, Any?>>,
    onSubmit: (Map<String, Any?>) -> Unit,
    onCancel: () -> Unit,
    options: UiSheetOptions = UiSheetOptions(),
) {
    val values = remember { mutableStateMapOf<String, Any?>() }

    items.forEach { item ->
        val id = item["id"]?.toString()
        if (id != null && !values.containsKey(id) && item.containsKey("default")) {
            values[id] = item["default"]
        }
    }

    KhatKitSheet(
        title = title,
        imageVector = Icons.Filled.Edit,
        options = options,
        confirmText = stringResource(R.string.khatkit_form_confirm),
        onConfirm = { onSubmit(values.toMap()) },
        onDismiss = onCancel,
    ) { dismiss ->
        FormContent(items = items, values = values, onCancel = dismiss)
    }
}

@Composable
private fun FormContent(
    items: List<Map<String, Any?>>,
    values: MutableMap<String, Any?>,
    onCancel: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
    ) {
        val twoColumn = maxWidth >= TWO_COLUMN_WIDTH
        val rows = remember(items, twoColumn) {
            if (twoColumn) groupFormRows(items) else items.map { listOf(it) }
        }

        Column(
            modifier = Modifier
                .then(if (twoColumn) Modifier.widthIn(max = WIDE_FORM_MAX_WIDTH) else Modifier)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rows.forEach { row ->
                if (row.size == 1 && isFullWidthItem(row.first())) {
                    FormWidget(row.first(), values)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { item ->
                            Box(modifier = Modifier.weight(1f)) {
                                FormWidget(item, values)
                            }
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                KedgeTextButton(onClick = onCancel) {
                    Text(stringResource(R.string.khatkit_form_cancel))
                }
            }
        }
    }
}

/** 相邻的窄组件两两成行，整行组件独占一行（顺序保持）。 */
private fun groupFormRows(items: List<Map<String, Any?>>): List<List<Map<String, Any?>>> {
    val rows = mutableListOf<List<Map<String, Any?>>>()
    var pair = mutableListOf<Map<String, Any?>>()
    items.forEach { item ->
        if (isFullWidthItem(item)) {
            if (pair.isNotEmpty()) {
                rows += pair
                pair = mutableListOf()
            }
            rows += listOf(item)
        } else {
            pair += item
            if (pair.size == 2) {
                rows += pair
                pair = mutableListOf()
            }
        }
    }
    if (pair.isNotEmpty()) rows += pair
    return rows
}

private fun isFullWidthItem(item: Map<String, Any?>): Boolean =
    (item["type"]?.toString() ?: "text") in FULL_WIDTH_TYPES

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
