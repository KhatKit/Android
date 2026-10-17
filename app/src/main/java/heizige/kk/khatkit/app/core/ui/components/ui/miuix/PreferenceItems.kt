package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material3.Icon as MdIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MdText
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeRadioButton
import heizige.kk.kedge.components.KedgeSlider
import heizige.kk.kedge.components.KedgeSwitch
import heizige.kk.kedge.preferences.KedgeListPreference
import heizige.kk.kedge.preferences.KedgePreferenceOption
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.khatkit.app.core.ui.components.ui.LocalInlineCardGroup
import heizige.kk.khatkit.app.core.ui.icons.chevronRight
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle

/**
 * KhatKit Miuix 偏好项的薄包装：统一图标样式与常用默认值，
 * 免得每个页面重复写 KernelSU 那套 `startAction = { Icon(..., Modifier.padding(end = 6.dp)) }`。
 *
 * ⚠️ 本文件**不再自绘选项行**（t11）：[PreferenceSwitch] / [PreferenceArrow] /
 * [PreferenceInfo] / [PreferenceRadio]（以及 [PreferenceSlider] 的 MD3 一支）全部只是
 * **签名转接层**，行布局、卡片底色、圆角、内边距、字阶、按压回弹一律由
 * [KedgeOptionItem] 按 `LocalKedgeStyle` 分流决定（与 `Form.kt` 的 `FormItem`、
 * `miuix/KedgeForm.kt` 的 `KedgeFormRow` 同一条渲染路径）。
 *
 * 以前这里有两条自绘行：Miuix 一支直接渲染 miuix 原生 `SwitchPreference` /
 * `ArrowPreference`（自带 `BasicComponent` 行），MD3 一支渲染 `KedgeListItem` +
 * `Modifier.clickable`。两套行各自算一遍内边距/字阶/卡片，改一处漏一处；同一个
 * 「选项行」在 16-17 个 `*Miuix` 设置页里长成两种样子。现在 95 处调用点**零改动**，
 * 只是里面的行换成了统一的那一个。
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
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
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
    } else {
        // MD3Exp：onBackground → onSurfaceVariant（MD3 行内前置图标的语义色），
        // 禁用态按 MD3 惯例降到 38% 透明度。
        MdIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            modifier = modifier.padding(end = 6.dp),
        )
    }
}

/**
 * 选项行的圆角：与 `Form.kt` 的 `inlineGroupRowShape()` **同一条规则**。
 *
 * - 组外（单行）：按「整组只有一行」算，四角都是大圆角（`listCardStyle().indexedShape(0, 1)`）。
 * - 组内（[LocalInlineCardGroup] 非空，即 `KedgeFormCard` / `InlineCardGroup` 里）：一律按
 *   「中间项」算，只留内侧小圆角；首项上边 / 末项下边的大圆角由整组 `clip` 裁出来。
 *   Miuix 下 `smallCorner = 0`，组内就是直角矩形拼一张卡 —— 正是 KernelSU 的分组观感。
 *
 * 这条路径**不需要知道自己是组里第几个**，所以 95 处调用点不必传 index/count；
 * 组内混着 `PreferenceSlider`、条件行、标题 `Text` 也一样正确。
 */
@Composable
private fun preferenceRowShape(): Shape {
    val cards = listCardStyle()
    return if (LocalInlineCardGroup.current == null) {
        cards.indexedShape(0, 1)
    } else {
        cards.groupItemShape(isFirst = false, isLast = false)
    }
}

/**
 * 行标题。字阶取 [KedgeTextStyles.title]（Miuix = `headline1 + Medium`，与
 * [KedgeOptionItem] 自己那层 `MiuixTextStyleScope` 同值；MD3 = `titleMedium`）。
 *
 * 取色不能省：Miuix 一支原来由 `SwitchPreference` 的 `BasicComponent` 给
 * `onSurfaceContainer`，MD3 一支原来吃 `LocalContentColor`；现在两边都从这里出，
 * 免得 Miuix 页面里标题跟着 MD3 的 `LocalContentColor` 走成黑/白。
 */
@Composable
private fun PreferenceTitle(text: String) {
    MdText(
        text = text,
        style = KedgeTextStyles.title(),
        color = when (LocalKedgeStyle.current) {
            KedgeStyle.Miuix -> MiuixTheme.colorScheme.onSurfaceContainer
            KedgeStyle.MD3Exp -> Color.Unspecified
        },
    )
}

/**
 * 行摘要。Miuix 取 `onSurfaceVariantSummary`（= `BasicComponent` 给 summary 的那个角色），
 * MD3 保持改造前的 `onSurfaceVariant`。
 */
@Composable
private fun PreferenceSummary(text: String) {
    MdText(
        text = text,
        style = KedgeTextStyles.body(),
        color = when (LocalKedgeStyle.current) {
            KedgeStyle.Miuix -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            KedgeStyle.MD3Exp -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/**
 * 开关项。[icon] 为空时不显示前置图标。
 *
 * 整行点击 = 翻转开关（`onClick` 交给 [KedgeOptionItem]，两种风格都既有整行点击、
 * 又有按压回弹）；右侧开关自己消费掉它的点击，不会冒泡回来，所以不会翻两次。
 */
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
    KedgeOptionItem(
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        modifier = modifier,
        shape = preferenceRowShape(),
        leadingContent = icon?.let { { PreferenceIcon(it, title, enabled) } },
        titleContent = { PreferenceTitle(title) },
        supportingContent = summary?.let { s -> { PreferenceSummary(s) } },
        trailingContent = {
            KedgeSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
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
    KedgeOptionItem(
        onClick = if (enabled) onClick else null,
        modifier = modifier,
        shape = preferenceRowShape(),
        // startAction 优先（调用点自带的图标/前置控件），其次才用 icon。
        leadingContent = startAction ?: icon?.let { { PreferenceIcon(it, title, enabled) } },
        titleContent = { PreferenceTitle(title) },
        supportingContent = summary?.let { s -> { PreferenceSummary(s) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                endAction?.invoke(this)
                MdIcon(
                    imageVector = chevronRight,
                    contentDescription = null,
                    tint = when (LocalKedgeStyle.current) {
                        // Miuix 的 `ArrowPreference` 自己画的那个箭头就是这个色。
                        KedgeStyle.Miuix -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                        KedgeStyle.MD3Exp -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
    )
}

/**
 * 滑块项。[valueText] 显示在滑块右侧（如百分比、当前值）。
 *
 * 两种风格都保留**原生滑块**，只把外壳收进 [KedgeOptionItem]：
 *
 * - Miuix 一支仍是 `SliderPreference`（`PreferenceItems.kt:213`）：Miuix 的滑块带拖动
 *   值气泡、刻度绘制与无障碍 setProgress 语义，自己用 `KedgeSlider` 拼一遍会丢这些；
 *   而且它本来就是 Miuix 自带的 `BasicComponent` 行，不是「裸 `KedgeListItem` + clickable」。
 * - MD3 一支原来是裸 `KedgeListItem`（透明无底色、无卡片圆角），现在换成
 *   `KedgeOptionItem(onClick = null)`：纯信息行不可点、无按压回弹，滑块仍是唯一交互元素。
 */
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
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
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
    } else {
        // MD3Exp：外壳走 KedgeOptionItem（纯信息行，onClick = null），滑块用 :kedge 的
        // KedgeSlider（valueText 仍显示在滑块右侧）。壳内结构与改造前的 KedgeListItem 相同，
        // 只多了卡片底色/圆角。
        KedgeOptionItem(
            onClick = null,
            modifier = modifier,
            shape = preferenceRowShape(),
            leadingContent = icon?.let { { PreferenceIcon(it, title, enabled) } },
            titleContent = { PreferenceTitle(title) },
            supportingContent = {
                Column {
                    if (summary != null) {
                        PreferenceSummary(summary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KedgeSlider(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier.weight(1f),
                            enabled = enabled,
                            valueRange = valueRange,
                            steps = steps,
                            onValueChangeFinished = onValueChangeFinished,
                        )
                        if (valueText != null) {
                            MdText(
                                text = valueText,
                                style = KedgeTextStyles.body(),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}

/**
 * 只读信息项。[onLongClick] 用于「连点版本号进 Debug」这类隐藏入口。
 *
 * 长按在 [KedgeOptionItem] 的 `onClick` 之外补：有 [onLongClick] 时整行点击交给挂上来的
 * `combinedClickable`（`onClick` 传 null，避免两个 clickable 各点一次），没有时直接走
 * [KedgeOptionItem] 的 `onClick`（带按压回弹）。
 */
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
    KedgeOptionItem(
        onClick = if (onLongClick == null) onClick else null,
        modifier = if (onLongClick == null) {
            modifier
        } else {
            modifier.combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick,
            )
        },
        shape = preferenceRowShape(),
        leadingContent = icon?.let { { PreferenceIcon(it, title) } },
        titleContent = { PreferenceTitle(title) },
        supportingContent = summary?.let { s -> { PreferenceSummary(s) } },
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
    KedgeOptionItem(
        onClick = if (enabled) onClick else null,
        modifier = modifier,
        shape = preferenceRowShape(),
        leadingContent = icon?.let { { PreferenceIcon(it, title, enabled) } },
        titleContent = { PreferenceTitle(title) },
        supportingContent = summary?.let { s -> { PreferenceSummary(s) } },
        trailingContent = {
            KedgeRadioButton(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
            )
        },
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
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
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
    } else {
        // MD3Exp：走 :kedge 的 KedgeListPreference（行 + 选项弹窗都是双风格组件）。
        // Miuix 的下拉行会显示当前选中项，所以没有 summary 时用选中项标签兜底。
        KedgeListPreference(
            title = title,
            options = items.mapIndexed { index, label -> KedgePreferenceOption(index, label) },
            selectedValue = selectedIndex,
            onValueSelected = onSelectedIndexChange,
            modifier = modifier,
            summary = summary ?: items.getOrNull(selectedIndex),
            icon = icon,
        )
    }
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
