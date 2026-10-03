package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
        // 照搬 KernelSU：SettingsMiuix.kt 里每个 startAction 都是
        // `Icon(..., Modifier.padding(end = 6.dp), tint = colorScheme.onBackground)`，
        // **不写 size**，尺寸由 Miuix Icon 按 painter 内在尺寸决定。我们之前钉了
        // size(24.dp)，内在尺寸更大的图标会被压小，和 KSU 不一致。
        modifier = modifier.padding(end = 6.dp),
    )
}

/**
 * Miuix 观感的可点击：按下时整行缩一点，抬起复原，**不画水波纹**。
 *
 * Miuix 的 `BasicComponent` 只在 `onClick != null` 时才既挂 clickable 又启用按压动画，
 * 而 `SwitchPreference` 压根没有 onClick 参数——所以行点击只能在这里补。
 * 缩放取 0.97：与 Miuix `holdDownState` 的观感接近，又不会让行看起来被压扁。
 */
@Composable
private fun Modifier.holdDownClickable(
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "preferenceHoldDown",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
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
        modifier = modifier
            .fillMaxWidth()
            // 整行可点：Miuix 的 SwitchPreference **没有 onClick 参数**（ArrowPreference
            // 才有），onClick 缺省时 BasicComponent 根本不挂 clickable，于是「点卡片
            // 不动、只能点开关」。这里自己补上：指示器关掉，改用 Miuix 那套按压缩放，
            // 免得在 Miuix 页面里出现一圈 MD3 水波纹。
            // 开关自己消费掉它那部分点击，不会冒泡回来，所以不会翻两次。
            .holdDownClickable(enabled) { onCheckedChange(!checked) },
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
