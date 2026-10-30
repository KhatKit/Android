package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowDown
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowUp
import heizige.kk.khatkit.uikit.mochi.KkSurface
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiContextMenu
import ui.feice.mochi.basic.MochiItem

/**
 * 通用下拉选择（U3c）：菜单统一换成 mochi-compose 的 MochiContextMenu。
 *
 * 对外签名与旧版完全一致，调用点零改动；定位/展开/关闭交给 Mochi 的 context menu 引擎
 * （自带按屏幕左右半屏对齐与融球过渡）。
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

    MochiContextMenu(
        items = items,
        onItemClick = { index ->
            options.getOrNull(index)?.let(onOptionSelected)
            expanded = false
        },
        modifier = modifier,
    ) {
        KkSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            onClick = { expanded = !expanded },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading()
                Text(text = optionToString(selectedOption), modifier = Modifier.weight(1f))
                trailing()
                Icon(
                    imageVector = keyboardArrowDown,
                    contentDescription = "expand",
                    modifier = Modifier.rotate(arrowRotation),
                )
            }
        }
    }
}

/**
 * 文本框 + 预设选项下拉菜单（U3c）：同样用 MochiContextMenu。
 *
 * 不用 MD3 的 exposed 下拉实现的原因保留：它在「菜单高度 > 可见窗口 − 96dp」时会
 * coerceIn(min > max) 抛异常（issue #1549），小屏 + 输入法同时弹极易触发。
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

    MochiContextMenu(
        items = items,
        onItemClick = { index ->
            options.getOrNull(index)?.let(onOptionSelected)
            expanded = false
        },
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Md3OutlinedTextFieldWithSlots(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                label = label,
                placeholder = placeholder,
                modifier = Modifier.fillMaxWidth(),
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
            if (readOnly) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { expanded = !expanded },
                )
            }
        }
    }
}
