package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import heizige.kk.kedge.overlays.KedgeDropdownItemSlot
import heizige.kk.kedge.overlays.KedgeDropdownMenuSlots
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowDown
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowUp
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeSurface

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

    // 展开项本来就用 Kedge 的下拉项，只有外层的锚点与菜单容器还是 MD3 的。
    val anchor: @Composable (Modifier) -> Unit = { anchorModifier ->
        KedgeSurface(
            modifier = anchorModifier,
            shape = RoundedCornerShape(50),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leading()
                Text(
                    text = optionToString(selectedOption),
                    style = KedgeTextStyles.body(),
                    modifier = Modifier.weight(1f)
                )
                trailing()
                Icon(
                    imageVector = keyboardArrowDown,
                    contentDescription = "expand",
                    modifier = Modifier.rotate(arrowRotation),
                )
            }
        }
    }

    val menuItems: @Composable () -> Unit = {
        options.fastForEach { option ->
            KedgeDropdownItemSlot(
                onClick = {
                    onOptionSelected(option)
                    expanded = false
                },
                text = {
                    Text(text = optionToString(option), maxLines = 1)
                },
                leadingIcon = optionLeading?.let {
                    { it(option) }
                }
            )
        }
    }

    when (LocalKedgeStyle.current) {
        // MD3Exp 保持原来的 ExposedDropdownMenuBox 定位行为。
        KedgeStyle.MD3Exp -> ExposedDropdownMenuBox(
            modifier = modifier,
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            anchor(Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(20.dp),
            ) {
                menuItems()
            }
        }

        // Miuix：改用基于锚点宽度的弹出菜单，容器与菜单都是 Miuix 形态。
        KedgeStyle.Miuix -> {
            var anchorWidth by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            Box(modifier = modifier) {
                anchor(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { expanded = !expanded }
                        .onGloballyPositioned { anchorWidth = it.size.width }
                )
                KedgeDropdownMenuSlots(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .width(with(density) { anchorWidth.toDp() })
                        .heightIn(max = 240.dp),
                    content = menuItems,
                )
            }
        }
    }
}

/**
 * 文本框 + 预设选项下拉菜单
 *
 * 刻意不使用 ExposedDropdownMenuBox: 它的 ExposedDropdownMenuPositionProvider 在
 * "菜单高度 > 可见窗口高度 - 96dp" 时会 coerceIn(min > max) 直接抛异常 (issue #1549),
 * 小屏设备上点击底部的可编辑输入框会同时弹出输入法, 极易触发。
 * 普通 DropdownMenu 的定位逻辑对这种情况有兜底, 不会崩溃。
 */
@Composable
fun <T> SelectTextField(
    value: String,
    options: List<T>,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit = {},
    readOnly: Boolean = false,
    placeholder: @Composable (() -> Unit)? = null,
    optionToString: @Composable (T) -> String = { it.toString() },
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(modifier = modifier) {
        KedgeOutlinedTextFieldWithSlots(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            placeholder = placeholder,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { anchorWidth = it.size.width },
            trailingIcon = {
                KedgeIconButton(onClick = { expanded = !expanded }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        imageVector = if (expanded) keyboardArrowUp else keyboardArrowDown,
                        contentDescription = "expand"
                    )
                }
            },
            shape = RoundedCornerShape(16.dp)
        )

        // 只读时整个输入框都可点击展开, 且不会抢焦点弹出输入法
        if (readOnly) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = !expanded }
            )
        }

        KedgeDropdownMenuSlots(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(with(density) { anchorWidth.toDp() })
                .heightIn(max = 240.dp)
        ) {
            options.fastForEach { option ->
                KedgeDropdownItemSlot(
                    text = { Text(text = optionToString(option), maxLines = 1) },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    }
                )
            }
        }
    }
}
