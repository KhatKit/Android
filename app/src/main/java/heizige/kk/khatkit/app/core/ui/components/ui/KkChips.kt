package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import ui.feice.mochi.basic.MochiChip

/**
 * CH1/CH2：所有芯片统一走 Mochi [MochiChip]（不再保留 material3 FilterChip 回退）。
 *
 * [MochiChip] 的 label 是 String，自带选中态、勾选槽与按压/形变反馈，常规调用点用这两个函数：
 * 选中态走 selected，勾选标记走 showCheck，行内图标走 leadingIcon。
 * 需要富内容 label 或行尾内容的调用点用 [KkRichFilterChip] / [KkRichInputChip]：它们把
 * Compose 内容接到 Mochi 的 labelContent / trailingContent 槽上（本地 Mochi 扩展）。
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
    // 旧签名兼容：形状/内边距/配色由 Mochi 主题统一决定，这里接收后忽略。
    @Suppress("UNUSED_PARAMETER") cornerRadius: Dp? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") contentPadding: PaddingValues? = null,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
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
    @Suppress("UNUSED_PARAMETER") cornerRadius: Dp? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") contentPadding: PaddingValues? = null,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
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

/** 富内容 label 的芯片：label 走 MochiChip 的 labelContent 槽，trailingIcon 走 trailingContent 槽。 */
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
    MochiChip(
        label = "",
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        showCheck = true,
        leadingIcon = leadingIcon,
        labelContent = label,
        trailingContent = trailingIcon,
    )
}

/** 富内容 label 的输入芯片，同 [KkRichFilterChip]。 */
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
    MochiChip(
        label = "",
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        showCheck = false,
        leadingIcon = leadingIcon,
        labelContent = label,
        trailingContent = trailingIcon,
    )
}
