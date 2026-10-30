package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowDown
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowUp
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiItem

/**
 * 通用下拉选择：**点击**触发的 MochiDropdown（U3c-fix）。
 *
 * 上一版误用 MochiContextMenu —— 那是「长按预览 → 菜单从卡片里长出来」的上下文菜单，
 * 不接受 expanded，所以点击永远弹不出来。这里换成 MochiDropdown：expanded/
 * onExpandedChange 由它管理，buttonContent 就是那颗锚点按钮。对外签名不变。
 */
@Composable
fun <T> Select(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionToString: @Composable (T) -> String = { it.toString() },
    optionLeading: @Composable ((T) -> Unit)? = null,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "selectArrowRotation",
    )
    val items: ImmutableList<MochiItem> = options.map { option ->
        MochiItem(
            label = optionToString(option),
            destructive = false,
            icon = { _ -> optionLeading?.invoke(option) },
        )
    }.toImmutableList()

    MochiDropdown(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        items = items,
        onItemClick = { index ->
            options.getOrNull(index)?.let(onOptionSelected)
            expanded = false
        },
        modifier = modifier,
        selectedIndex = options.indexOf(selectedOption),
        onClick = { expanded = !expanded },
        buttonContent = {
            leading()
            Text(text = optionToString(selectedOption), modifier = Modifier.weight(1f))
            trailing()
            Icon(
                imageVector = keyboardArrowDown,
                contentDescription = "expand",
                modifier = Modifier.rotate(arrowRotation),
            )
        },
    )
}

/**
 * 文本框 + 预设选项下拉：同样走 MochiDropdown，按钮槽就是那个只读输入框。
 *
 * 不用 exposed 下拉的原因保留：菜单高度 > 可见窗口 − 96dp 时旧实现会 coerceIn(min > max) 抛异常。
 */
@Composable
fun <T> SelectTextField(
    value: String,
    options: List<T>,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit = {},
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    optionToString: @Composable (T) -> String = { it.toString() },
) {
    var expanded by remember { mutableStateOf(false) }
    val items: ImmutableList<MochiItem> = options.map { option ->
        MochiItem(label = optionToString(option), destructive = false, icon = { })
    }.toImmutableList()

    MochiDropdown(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        items = items,
        onItemClick = { index ->
            options.getOrNull(index)?.let(onOptionSelected)
            expanded = false
        },
        modifier = modifier,
        selectedIndex = options.indexOfFirst { optionToString(it) == value },
        onClick = { expanded = !expanded },
        buttonContent = {
            Md3OutlinedTextFieldWithSlots(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                label = label,
                placeholder = placeholder,
                modifier = Modifier.weight(1f),
                trailingIcon = {
                    MochiIconButton(onClick = { expanded = !expanded }, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = if (expanded) keyboardArrowUp else keyboardArrowDown,
                            contentDescription = "expand",
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
            )
        },
    )
}
