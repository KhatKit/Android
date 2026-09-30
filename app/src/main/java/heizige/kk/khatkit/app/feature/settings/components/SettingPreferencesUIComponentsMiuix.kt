package heizige.kk.khatkit.app.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceRadio
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSlider
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSwitch
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.uikit.KhatKitUiStyle
import kotlin.math.roundToInt

/** 代码块显示设置的 Miuix 版。 */
@Composable
fun CodeDisplaySettingsGroupMiuix(
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
    showLineNumbers: Boolean,
    onUpdateCodeBlockAutoWrap: (Boolean) -> Unit,
    onUpdateCodeBlockAutoCollapse: (Boolean) -> Unit,
    onUpdateShowLineNumbers: (Boolean) -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(R.string.setting_display_page_code_block_auto_wrap_title),
        summary = stringResource(R.string.setting_display_page_code_block_auto_wrap_desc),
        checked = codeBlockAutoWrap,
        onCheckedChange = onUpdateCodeBlockAutoWrap,
    )
    PreferenceSwitch(
        title = stringResource(R.string.setting_display_page_code_block_auto_collapse_title),
        summary = stringResource(R.string.setting_display_page_code_block_auto_collapse_desc),
        checked = codeBlockAutoCollapse,
        onCheckedChange = onUpdateCodeBlockAutoCollapse,
    )
    PreferenceSwitch(
        title = stringResource(R.string.setting_display_page_show_line_numbers_title),
        summary = stringResource(R.string.setting_display_page_show_line_numbers_desc),
        checked = showLineNumbers,
        onCheckedChange = onUpdateShowLineNumbers,
    )
}

/**
 * 界面风格切换的 Miuix 版：单选，用 [KhatKitUiStyle] 两个枚举值。
 * 切换后整棵 composition 会换主题，因此这里只更新 provider。
 */
@Composable
fun UiStyleGroupMiuix() {
    val khatKitProvider = rememberAppEntryPoint().khatKitToolProvider()
    // 直接读 provider 的 state，不要 remember 缓存：否则别处切换风格后这里显示不同步。
    val uiStyle = khatKitProvider.uiStyle

    KhatKitUiStyle.entries.forEach { style ->
        PreferenceRadio(
            title = if (style == KhatKitUiStyle.MATERIAL) "Material 3" else "Miuix",
            selected = uiStyle == style,
            onClick = { khatKitProvider.uiStyle = style },
        )
    }
}

/**
 * 毛玻璃开关的 Miuix 版：写入 provider 即刻生效（根部会重建 backdrop）。
 */
@Composable
fun BlurToggleGroupMiuix() {
    val khatKitProvider = rememberAppEntryPoint().khatKitToolProvider()
    // 直接读 provider 的 state：enableBlur 是 mutableStateOf，写完即刻生效并回显。
    val enabled = khatKitProvider.enableBlur

    PreferenceSwitch(
        title = stringResource(R.string.setting_page_blur_title),
        summary = stringResource(R.string.setting_page_blur_desc),
        checked = enabled,
        onCheckedChange = { khatKitProvider.enableBlur = it },
    )
}

/** 列表卡片样式（圆角/间距）的 Miuix 版。 */
@Composable
fun ListCardStyleGroupMiuix(
    largeCorner: Float,
    smallCorner: Float,
    gap: Float,
    onUpdateLargeCorner: (Float) -> Unit,
    onUpdateSmallCorner: (Float) -> Unit,
    onUpdateGap: (Float) -> Unit,
) {
    PreferenceSlider(
        title = stringResource(R.string.setting_display_page_card_corner_large),
        value = largeCorner,
        valueRange = 8f..40f,
        steps = 31,
        valueText = "${largeCorner.roundToInt()}dp",
        onValueChange = { onUpdateLargeCorner(it.roundToInt().toFloat()) },
    )
    PreferenceSlider(
        title = stringResource(R.string.setting_display_page_card_corner_small),
        value = smallCorner,
        valueRange = 0f..largeCorner.coerceAtLeast(1f),
        steps = (largeCorner.roundToInt().coerceAtLeast(1) - 1).coerceAtLeast(0),
        valueText = "${smallCorner.roundToInt()}dp",
        onValueChange = { onUpdateSmallCorner(it.roundToInt().toFloat()) },
    )
    PreferenceSlider(
        title = stringResource(R.string.setting_display_page_card_gap),
        value = gap,
        valueRange = 0f..16f,
        steps = 15,
        valueText = "${gap.roundToInt()}dp",
        onValueChange = { onUpdateGap(it.roundToInt().toFloat()) },
    )
}
