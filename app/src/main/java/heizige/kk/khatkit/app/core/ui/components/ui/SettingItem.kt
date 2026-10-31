package heizige.kk.khatkit.app.core.ui.components.ui

import heizige.kk.khatkit.uikit.mochi.KkSwitch
import heizige.kk.khatkit.uikit.mochi.KkSlider
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.Md3SwitchFormRow
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.utils.rememberMochiHaptics

/**
 * 设置项的声明式描述。
 *
 * 页面只负责把配置描述成 [SettingItem] 列表，渲染交给 [SettingGroup]，避免每个开关/滑块
 * 都手写十幾行 slot 样板。
 */
@Immutable
sealed interface SettingItem {
    @get:StringRes
    val titleRes: Int

    @get:StringRes
    val descriptionRes: Int?
}

/** 开关项。 */
@Immutable
data class SwitchSetting(
    @StringRes override val titleRes: Int,
    @StringRes override val descriptionRes: Int?,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    val enabled: Boolean = true,
) : SettingItem

/** 滑块项。[valueLabel] 缺省时按百分比显示。 */
@Immutable
data class SliderSetting(
    @StringRes override val titleRes: Int,
    @StringRes override val descriptionRes: Int?,
    val value: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    val onValueChange: (Float) -> Unit,
    val steps: Int = 0,
    val enabled: Boolean = true,
    val valueLabel: ((Float) -> String)? = null,
) : SettingItem

/** 纯信息项，只展示不可交互。 */
@Immutable
data class InfoSetting(
    @StringRes override val titleRes: Int,
    @StringRes override val descriptionRes: Int? = null,
) : SettingItem

/** 点击跳转项（进二级页面），行尾自动带统一的 › 。弹窗/选择器别用它。 */
@Immutable
data class NavSetting(
    @StringRes override val titleRes: Int,
    @StringRes override val descriptionRes: Int? = null,
    val onClick: () -> Unit,
) : SettingItem

/**
 * 渲染一整组设置项，自动套上 [CardGroup] 的分组圆角与间距。
 *
 * ```
 * SettingGroup(R.string.setting_display) {
 *     settingItem(SwitchSetting(R.string.show_avatar, R.string.show_avatar_desc, display.showUserAvatar) {
 *         update(display.copy(showUserAvatar = it))
 *     })
 *     settingItem(SliderSetting(R.string.opacity, value = display.bubbleOpacity, valueRange = 0.1f..1f) {
 *         update(display.copy(bubbleOpacity = it))
 *     })
 * }
 * ```
 */
@Composable
fun SettingGroup(
    @StringRes titleRes: Int? = null,
    modifier: Modifier = Modifier,
    items: List<SettingItem>,
) {
    if (items.isEmpty()) return
    CardGroup(
        modifier = modifier,
        title = titleRes?.let { res -> { Text(stringResource(res)) } },
    ) {
        items.forEach { setItem(it) }
    }
}

/**
 * 把一个 [SettingItem] 追加进当前 [CardGroupScope]，用于在同一分组里与自定义 slot 行混排。
 *
 * [visible]：依赖别的设置才出现的行传条件，不要包 `if`，显隐才有展开/收起动画。
 */
fun CardGroupScope.settingItem(item: SettingItem, visible: Boolean = true) = setItem(item, visible)

/**
 * 左标题 + 右开关的紧凑行，用于表单中间（不在 [CardGroup] 分组内）。
 *
 * 整行可点：点行任意位置等价于点开关，与设置分组内的开关行保持一致。
 *
 * 渲染交给双风格的 [Md3SwitchFormRow]：MD3 还是原来的左标题右开关，
 * Miuix 则自己出一行圆角卡片并换 Miuix 字阶。之前这里是裸 `Row` + 默认字阶，
 * 在 Miuix 页面里就是「唯一一块没做过的那块」，所以别再在这里手写布局。
 *
 * ```
 * SwitchRow(R.string.setting_provider_page_enable, checked = provider.enabled) {
 *     onEdit(provider.copy(enabled = it))
 * }
 * ```
 */
@Composable
fun SwitchRow(
    @StringRes titleRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Md3SwitchFormRow(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        label = { Text(stringResource(titleRes)) },
    )
}

/** 左标题 + 右滑块的紧凑行，[valueLabel] 缺省时按百分比显示。 */
@Composable
fun SliderRow(
    @StringRes titleRes: Int,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    valueLabel: ((Float) -> String)? = null,
) {
    // 照 Mochi 的滑块行：标题与数值同一行，滑块在下一行占满。
    Column(modifier = modifier.fillMaxWidth()) {
        SliderTitleRow(
            title = stringResource(titleRes),
            valueText = valueLabel?.invoke(value) ?: "${(value * 100).toInt()}%",
        )
        KkSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}

/**
 * Mochi 滑块行的标题行：标题左、实时数值右（同一行）。
 *
 * 排版照 Mochi 的 `MochiListSliderRow`（TitledSliderRow）：数值不再挤在滑块右边，
 * 滑块在下面占满整行 —— 这是 Mochi 里滑块唯一的摆法。
 */
@Composable
fun SliderTitleRow(
    title: String,
    valueText: String,
    editor: SliderValueEditor? = null,
) {
    var editing by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (editor != null) {
            // 数值本身就是入口：点开弹窗直接输入精确值 / 恢复默认。
            // 用主色区分「可点」，Mochi 的只读数值仍是次要色（SliderValueText）。
            val haptics = rememberMochiHaptics()
            Text(
                text = valueText,
                style = MochiTheme.textStyles.body.copy(fontFeatureSettings = "tnum"),
                // 颜色和只读数值一致（次要色）：可点靠点击热区和触觉表达，不用主色把数值挑出来
                color = MochiTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clip(MochiTheme.shapes.full)
                    .clickable {
                        haptics.tapOpen()
                        editing = true
                    }
                    .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
            )
            SliderValueInputDialog(
                visible = editing,
                title = title,
                editor = editor,
                onDismiss = { editing = false },
            )
        } else {
            SliderValueText(valueText)
        }
    }
}

/** 滑块行右侧的实时数值：Mochi body 字阶 + 等宽数字 + 次要色（拖到 x1.5 时数字不会左右抖）。 */
@Composable
fun SliderValueText(text: String) {
    Text(
        text = text,
        style = MochiTheme.textStyles.body.copy(fontFeatureSettings = "tnum"),
        color = MochiTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

private fun CardGroupScope.setItem(item: SettingItem, visible: Boolean = true) {
    val sliderValueText = (item as? SliderSetting)?.let {
        it.valueLabel?.invoke(it.value) ?: "${(it.value * 100).toInt()}%"
    }
    // SliderSetting 没有默认值信息：弹窗只给「直接输入」，不显示「重置」
    val sliderEditor = (item as? SliderSetting)?.let {
        SliderValueEditor(
            value = it.value,
            valueRange = it.valueRange,
            onValueChange = it.onValueChange,
            steps = it.steps,
        )
    }
    item(
        visible = visible,
        chevron = item is NavSetting,
        // 只有导航行给整行点击。开关行照 Mochi（iOS）：行本身不可点、不高亮，只有开关能拨。
        onClick = when (item) {
            is NavSetting -> item.onClick
            else -> null
        },
        supportingContent = when (item) {
            // 滑块行照 Mochi：说明在滑块下面，滑块占满文字列
            is SliderSetting -> {
                {
                    Column {
                        KkSlider(
                            value = item.value,
                            onValueChange = item.onValueChange,
                            valueRange = item.valueRange,
                            steps = item.steps,
                            enabled = item.enabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        item.descriptionRes?.let { res ->
                            Text(
                                text = stringResource(res),
                                style = MochiTheme.textStyles.footnote,
                                color = MochiTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            else -> item.descriptionRes?.let { res -> { Text(stringResource(res)) } }
        },
        trailingContent = when (item) {
            is SwitchSetting -> {
                {
                    KkSwitch(
                        checked = item.checked,
                        enabled = item.enabled,
                        onCheckedChange = item.onCheckedChange,
                    )
                }
            }

            else -> null
        },
        headlineContent = {
            if (sliderValueText != null) {
                SliderTitleRow(
                    title = stringResource(item.titleRes),
                    valueText = sliderValueText,
                    editor = sliderEditor,
                )
            } else {
                Text(stringResource(item.titleRes))
            }
        },
    )
}

/**
 * 分组里的一行滑块（Mochi 滑块行 + 点数值直接输入）。
 *
 * 版面：标题与实时数值同一行，滑块在下一行占满文字列；[description] 是滑块下面的一行说明，
 * [supporting] 放需要跟着滑块的内容（例如字号预览）。
 *
 * 右上角的数值是可点的：点击弹出 [SliderValueInputDialog]，能直接输入精确值、恢复默认。
 * 调用方要给出 [defaultValue]（模型里的默认值），「重置」按钮才有落点。
 *
 * @param value 滑块内部单位的值（百分比设置传 0.45 这类，显示按 [displayScale] 换算）
 * @param steps 档位数，与滑块 steps 同义
 * @param displayScale 显示单位换算：百分比传 100，其余 1
 * @param prefix / suffix 显示单位前后缀（x1.5 的 x、45% 的 %、24dp 的 dp）
 * @param decimals 显示与输入的小数位
 */
fun CardGroupScope.sliderItem(
    // 标题与说明走 composable lambda：CardGroup 的 DSL 块不是 composable 上下文，
    // 传 String 的话 stringResource 会在调用点报「@Composable invocations ...」
    title: @Composable () -> String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    defaultValue: Float?,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    visible: Boolean = true,
    displayScale: Float = 1f,
    prefix: String = "",
    suffix: String = "",
    decimals: Int = 0,
    description: (@Composable () -> String)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
) {
    val editor = SliderValueEditor(
        value = value,
        valueRange = valueRange,
        defaultValue = defaultValue,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        displayScale = displayScale,
        prefix = prefix,
        suffix = suffix,
        decimals = decimals,
        steps = steps,
    )
    item(
        modifier = modifier,
        visible = visible,
        headlineContent = {
            SliderTitleRow(
                title = title(),
                valueText = sliderValueText(
                    value = value,
                    displayScale = displayScale,
                    decimals = decimals,
                    prefix = prefix,
                    suffix = suffix,
                ),
                editor = editor,
            )
        },
        supportingContent = {
            Column {
                KkSlider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    steps = steps,
                    enabled = enabled,
                    onValueChangeFinished = onValueChangeFinished,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (description != null) {
                    Text(
                        text = description(),
                        style = MochiTheme.textStyles.footnote,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                }
                supporting?.invoke()
            }
        },
    )
}
