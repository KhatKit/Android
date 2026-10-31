package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import ui.feice.mochi.basic.MochiChip

/**
 * CH1：多选/筛选芯片统一走 Mochi。
 *
 * [MochiChip] 的 label 是 String，自带选中态、勾选槽与按压/形变反馈，常规调用点用这两个函数：
 * 选中态走 selected，勾选标记走 showCheck，行内图标走 leadingIcon。
 * 富内容（多行、自定义排版、行尾内容）的调用点用 [KkRichFilterChip] / [KkRichInputChip]。
 */
@Composable
fun KkFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCheck: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    MochiChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        showCheck = showCheck,
        leadingIcon = leadingIcon,
    )
}

@Composable
fun KkInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCheck: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    MochiChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        showCheck = showCheck,
        leadingIcon = leadingIcon,
    )
}

/** 富内容回退：label 是 Compose 内容（或需要 trailingIcon），MochiChip 表达不了，仍用 material3 [FilterChip]。 */
@Composable
fun KkRichFilterChip(
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

/** 富内容回退，同 [KkRichFilterChip]。 */
@Composable
fun KkRichInputChip(
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
