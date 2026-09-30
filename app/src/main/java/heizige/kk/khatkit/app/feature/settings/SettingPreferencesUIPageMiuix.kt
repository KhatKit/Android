package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSlider
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.feature.settings.components.BlurToggleGroupMiuix
import heizige.kk.khatkit.app.feature.settings.components.CodeDisplaySettingsGroupMiuix
import heizige.kk.khatkit.app.feature.settings.components.ListCardStyleGroupMiuix
import heizige.kk.khatkit.app.feature.settings.components.UiStyleGroupMiuix
import heizige.kk.khatkit.app.core.ui.theme.rememberChatFontFamily
import androidx.compose.material3.LocalTextStyle
import kotlin.math.roundToInt

/**
 * 界面偏好页的 Miuix 风格版。
 *
 * 字体/代码块/列表卡片样式三组的开关与滑块全部走 [PreferenceSwitch] / [PreferenceSlider]；
 * 依赖 ChatFontFamily、MarkdownBlock 等 MD3 专属组件的部分保留在各自子组件里。
 */
@Composable
fun SettingPreferencesUIPageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val chatFontFamily = rememberChatFontFamily(displaySetting)

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_preferences_ui),
        navigationIcon = { BackButton() },
    ) {
        miuixGroup {
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_user_avatar_title),
                summary = stringResource(R.string.setting_display_page_show_user_avatar_desc),
                checked = displaySetting.showUserAvatar,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showUserAvatar = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_assistant_bubble_title),
                summary = stringResource(R.string.setting_display_page_show_assistant_bubble_desc),
                checked = displaySetting.showAssistantBubble,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showAssistantBubble = it)) },
            )
            PreferenceSlider(
                title = stringResource(R.string.setting_display_page_bubble_opacity_title),
                value = displaySetting.bubbleOpacity,
                valueRange = 0.1f..1.0f,
                steps = 8,
                valueText = "${(displaySetting.bubbleOpacity * 100).roundToInt()}%",
                onValueChange = { updateDisplaySetting(displaySetting.copy(bubbleOpacity = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_chat_list_model_icon_title),
                summary = stringResource(R.string.setting_display_page_chat_list_model_icon_desc),
                checked = displaySetting.showModelIcon,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showModelIcon = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_model_name_title),
                summary = stringResource(R.string.setting_display_page_show_model_name_desc),
                checked = displaySetting.showModelName,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showModelName = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_datetime_in_message_title),
                summary = stringResource(R.string.setting_display_page_show_datetime_in_message_desc),
                checked = displaySetting.showDateTimeInMessage,
                onCheckedChange = {
                    updateDisplaySetting(displaySetting.copy(showDateTimeInMessage = it))
                },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_token_usage_title),
                summary = stringResource(R.string.setting_display_page_show_token_usage_desc),
                checked = displaySetting.showTokenUsage,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showTokenUsage = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_thinking_content_title),
                summary = stringResource(R.string.setting_display_page_show_thinking_content_desc),
                checked = displaySetting.showThinkingContent,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showThinkingContent = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_auto_collapse_thinking_title),
                summary = stringResource(R.string.setting_display_page_auto_collapse_thinking_desc),
                checked = displaySetting.autoCloseThinking,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(autoCloseThinking = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_enable_latex_rendering_title),
                summary = stringResource(R.string.setting_display_page_enable_latex_rendering_desc),
                checked = displaySetting.enableLatexRendering,
                onCheckedChange = {
                    updateDisplaySetting(displaySetting.copy(enableLatexRendering = it))
                },
            )
        }

        miuixGroup {
            PreferenceSlider(
                title = stringResource(R.string.setting_display_page_font_size_title),
                value = displaySetting.fontSizeRatio,
                valueRange = 0.5f..2f,
                steps = 11,
                valueText = "${(displaySetting.fontSizeRatio * 100).toInt()}%",
                onValueChange = { updateDisplaySetting(displaySetting.copy(fontSizeRatio = it)) },
            )
            // 预览块沿用 MD3 的 MarkdownBlock：它是纯排版组件，与风格无关。
            Column {
                MarkdownBlock(
                    content = stringResource(R.string.setting_display_page_font_size_preview),
                    style = LocalTextStyle.current.copy(
                        fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
                        lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio,
                        fontFamily = chatFontFamily,
                    ),
                )
            }
        }

        miuixGroup {
            CodeDisplaySettingsGroupMiuix(
                codeBlockAutoWrap = displaySetting.codeBlockAutoWrap,
                codeBlockAutoCollapse = displaySetting.codeBlockAutoCollapse,
                showLineNumbers = displaySetting.showLineNumbers,
                onUpdateCodeBlockAutoWrap = {
                    updateDisplaySetting(displaySetting.copy(codeBlockAutoWrap = it))
                },
                onUpdateCodeBlockAutoCollapse = {
                    updateDisplaySetting(displaySetting.copy(codeBlockAutoCollapse = it))
                },
                onUpdateShowLineNumbers = {
                    updateDisplaySetting(displaySetting.copy(showLineNumbers = it))
                },
            )
        }

        miuixGroup {
            UiStyleGroupMiuix()
        }

        miuixGroup {
            BlurToggleGroupMiuix()
        }

        miuixGroup {
            ListCardStyleGroupMiuix(
                largeCorner = settings.listCardLargeCorner,
                smallCorner = settings.listCardSmallCorner,
                gap = settings.listCardGap,
                onUpdateLargeCorner = { large ->
                    vm.updateSettings(
                        settings.copy(
                            listCardLargeCorner = large,
                            listCardSmallCorner = settings.listCardSmallCorner.coerceAtMost(large),
                        )
                    )
                },
                onUpdateSmallCorner = { small ->
                    vm.updateSettings(settings.copy(listCardSmallCorner = small))
                },
                onUpdateGap = { gap ->
                    vm.updateSettings(settings.copy(listCardGap = gap))
                },
            )
        }
    }
}
