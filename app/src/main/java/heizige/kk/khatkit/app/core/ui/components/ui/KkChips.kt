package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * U4b：旧 Kedge 芯片的兼容壳，内部一律 material3 [FilterChip]。
 *
 * 只保留调用点真正用到的参数（选中态/点击/文案/图标/禁用）；Kedge 专有的
 * 圆角/内边距/自定义配色槽在这里丢弃 —— 圆角统一由 U2 Bridge 的 Shapes 决定。
 */
@Composable
fun KkFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") cornerRadius: Dp? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") contentPadding: PaddingValues? = null,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}

/** U4b：输入芯片的兼容壳（语义同 [KkFilterChip]，未选中态即可输入样式）。 */
@Composable
fun KkInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") cornerRadius: Dp? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") contentPadding: PaddingValues? = null,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}
