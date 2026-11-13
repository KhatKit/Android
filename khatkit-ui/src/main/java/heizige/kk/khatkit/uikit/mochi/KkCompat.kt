package heizige.kk.khatkit.uikit.mochi

import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.uikit.mochi.SegmentedItem
import ui.feice.mochi.basic.MochiProgressBar
import ui.feice.mochi.basic.MochiProgressRing

/**
 * U4c：把最后一批 Kedge 专有组件换成 material3 骨架的本地壳。
 *
 * 命名以 Kk 开头，避免与已删的 Kedge/Khromia 同名；调用点只改 import + 组件名。
 * 圆角/配色全部来自 U2 Bridge（MaterialTheme），不再由组件自己决定。
 */

enum class KkProgressIndicatorType { Circular, Linear }

@Composable
fun KkProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    type: KkProgressIndicatorType = KkProgressIndicatorType.Circular,
    color: Color? = null,
    trackColor: Color? = null,
    strokeWidth: Dp? = null,
) {
    when (type) {
        KkProgressIndicatorType.Circular -> MochiProgressRing(
            progress = progress,
            modifier = modifier,
            strokeWidth = strokeWidth ?: 4.dp,
        )
        KkProgressIndicatorType.Linear -> MochiProgressBar(
            progress = progress ?: 0f,
            modifier = modifier,
        )
    }
}

@Composable
fun KkProgressIndicator(
    modifier: Modifier = Modifier,
    progress: () -> Float,
    type: KkProgressIndicatorType = KkProgressIndicatorType.Circular,
    color: Color? = null,
    trackColor: Color? = null,
    strokeWidth: Dp? = null,
) = KkProgressIndicator(modifier, progress(), type, color, trackColor, strokeWidth)

/** 单选分段控件：material3 SingleChoiceSegmentedButtonRow。 */
@Composable
fun KkSingleChoiceSegmentedRow(
    items: List<SegmentedItem>,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") fillWidth: Boolean = true,
) {
    // SW1：单选分段控件换 Mochi 真组件（滑块式选中块 + 触觉）。Mochi 的 items 是纯文本，
    // SegmentedItem.label 本就是 String，映射即可；带图标/自定义 slot 的调用点见文件末尾说明。
    MochiSegmentedControl(
        selectedIndex = items.indexOfFirst { it.selected },
        onSelect = { index -> items.getOrNull(index)?.onClick?.invoke() },
        items = kotlinx.collections.immutable.persistentListOf(*items.map { it.label }.toTypedArray()),
        modifier = modifier.fillMaxWidth(),
    )
}

/** 多选分段控件：用同一套 SegmentedButton，但每项独立勾选。 */
@Composable
fun KkMultiChoiceSegmentedRow(
    items: List<SegmentedItem>,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.MultiChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            SegmentedButton(
                checked = item.selected,
                onCheckedChange = { item.onClick() },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size),
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
fun KkFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable () -> Unit,
) {
    MochiIconButton(
        onClick = onClick,
        modifier = modifier,
        variant = MochiIconButtonVariant.Filled,
        content = content,
    )
}

@Composable
fun KkExtendedFloatingActionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor ?: androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
        icon = icon,
        text = text,
    )
}

/** 浮动工具栏：material3 没有等价物，用 Surface + Row 的行内工具栏。 */
@Composable
fun KkFloatingToolbar(
    expanded: Boolean = true,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!expanded) return
    Surface(
        modifier = modifier,
        shape = androidx.compose.material3.MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leadingContent?.invoke()
            content()
        }
    }
}

@Composable
fun KkAssistChip(
    onClick: () -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") containerColor: Color? = null,
) {
    // CH1：内容是纯文本，直接走 MochiChip（Mochi 的 Chip 自带按压/形变反馈）。
    MochiChip(
        label = label.orEmpty(),
        selected = false,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
    )
}

/** 旧 KedgeMiuixSurface 的替代：MD3 Surface。 */
@Composable
fun KkSurfaceContainer(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.material3.MaterialTheme.shapes.large,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, shape = shape, color = color, content = content)
}


