package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text

/**
 * KhatKit Miuix 偏好项的薄包装：统一图标样式与常用默认值，
 * 免得每个页面重复写 KernelSU 那套 `startAction = { Icon(..., Modifier.padding(end = 6.dp)) }`。
 */

/**
 * 前置图标，样式对齐 KernelSU（`SettingsMiuix.kt` 里每个 startAction 都是
 * `Icon(..., Modifier.padding(end = 6.dp), tint = colorScheme.onBackground)`）：
 * 24dp 尺寸、右侧 6dp 间距、禁用时用 disabledOnSecondaryVariant 着色。
 */
@Composable
fun PreferenceIcon(
    imageVector: ImageVector,
    contentDescription: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = if (enabled) {
            MiuixTheme.colorScheme.onBackground
        } else {
            MiuixTheme.colorScheme.disabledOnSecondaryVariant
        },
        modifier = modifier.size(24.dp).padding(end = 6.dp),
    )
}

/** 开关项。[icon] 为空时不显示前置图标。 */
@Composable
fun PreferenceSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        startAction = icon?.let { { PreferenceIcon(it, title, enabled) } },
    )
}

/** 跳转项。[icon] 为空时不显示前置图标。 */
@Composable
fun PreferenceArrow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    startAction: (@Composable () -> Unit)? = null,
    endAction: (@Composable RowScope.() -> Unit)? = null,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        startAction = startAction ?: icon?.let { { PreferenceIcon(it, title, enabled) } },
        endActions = { endAction?.invoke(this) },
    )
}

/** 滑块项。[valueText] 显示在滑块右侧（如百分比、当前值）。 */
@Composable
fun PreferenceSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueText: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    SliderPreference(
        title = title,
        summary = summary,
        value = value,
        valueRange = valueRange,
        steps = steps,
        valueText = valueText,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        startAction = icon?.let { { PreferenceIcon(it, title, enabled) } },
    )
}

/** 只读信息项。[onLongClick] 用于「连点版本号进 Debug」这类隐藏入口。 */
@Composable
fun PreferenceInfo(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    ArrowPreference(
        title = title,
        summary = summary,
        onClick = onClick,
        modifier = modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        ),
        startAction = icon?.let { { PreferenceIcon(it, title) } },
    )
}

/** 单选项（圆点）。 */
@Composable
fun PreferenceRadio(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    RadioButtonPreference(
        title = title,
        summary = summary,
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        startAction = icon?.let { { PreferenceIcon(it, title, enabled) } },
    )
}

/** 下拉单选项。[icon] 为空时不显示前置图标。 */
@Composable
fun PreferenceDropdown(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OverlayDropdownPreference(
        title = title,
        summary = summary,
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        startAction = icon?.let { { PreferenceIcon(it, title, enabled) } },
    )
}


/** 空状态提示（居中标题 + 副标题），放在 [MiuixSettingsPage] 的 item 里。 */
@Composable
fun MiuixEmptyHint(
    text: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, color = MiuixTheme.colorScheme.onBackground)
        if (secondary != null) {
            Text(
                text = secondary,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = 13.sp,
            )
        }
    }
}
