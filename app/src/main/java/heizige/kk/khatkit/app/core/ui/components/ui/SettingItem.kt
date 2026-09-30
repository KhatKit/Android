package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khromia.components.FancySlider
import heizige.kk.khromia.components.OptionSwitch

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

/** 点击跳转项。 */
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
 */
fun CardGroupScope.settingItem(item: SettingItem) = setItem(item)

/**
 * 左标题 + 右开关的紧凑行，用于表单中间（不在 [CardGroup] 分组内）。
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
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(titleRes))
        OptionSwitch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
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
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(titleRes))
        FancySlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(text = valueLabel?.invoke(value) ?: "${(value * 100).toInt()}%")
    }
}

private fun CardGroupScope.setItem(item: SettingItem) {
    item(
        onClick = (item as? NavSetting)?.onClick,
        supportingContent = item.descriptionRes?.let { res -> { Text(stringResource(res)) } },
        trailingContent = when (item) {
            is SwitchSetting -> {
                {
                    OptionSwitch(
                        checked = item.checked,
                        enabled = item.enabled,
                        onCheckedChange = item.onCheckedChange,
                    )
                }
            }

            is SliderSetting -> {
                {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FancySlider(
                            value = item.value,
                            onValueChange = item.onValueChange,
                            valueRange = item.valueRange,
                            steps = item.steps,
                            enabled = item.enabled,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = item.valueLabel?.invoke(item.value)
                                ?: "${(item.value * 100).toInt()}%",
                            color = LocalContentColor.current,
                        )
                    }
                }
            }

            else -> null
        },
        headlineContent = { Text(stringResource(item.titleRes)) },
    )
}
