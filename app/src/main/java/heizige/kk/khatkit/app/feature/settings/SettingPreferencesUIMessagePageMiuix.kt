package heizige.kk.khatkit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSlider
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import kotlin.math.roundToInt

/**
 * 「消息显示」二级页的 Miuix 风格版。
 *
 * 由原「界面偏好设置」页（`SettingPreferencesUIPageMiuix.kt`）的第一个 miuixGroup 拆出：
 * 助手气泡 / 气泡不透明度 / 模型名 / 时间 / token 用量 / 思考内容 /
 * 思考自动折叠 / LaTeX 渲染，开关与滑块全部走 [PreferenceSwitch] / [PreferenceSlider]。
 *
 * 「显示用户头像」与「模型图标」两行已删（消息气泡旁的头像整体删除后，
 * `displaySetting.showUserAvatar` / `showModelIcon` 不再有消费方）；持久化字段保留。
 */
@Composable
fun SettingPreferencesUIMessagePageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_preferences_ui_message),
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        navigationIcon = { BackButton() },
    ) {
        miuixGroup {
            // 「显示助手消息气泡」开关保留，默认开。关掉后助手回复退回裸 Markdown，
            // 与用户侧气泡左右不对称属用户主动选择；消费方在 ChatMessage / ConversationExport。
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_assistant_bubble_title),
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
                title = stringResource(R.string.setting_display_page_show_model_name_title),
                checked = displaySetting.showModelName,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showModelName = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_datetime_in_message_title),
                checked = displaySetting.showDateTimeInMessage,
                onCheckedChange = {
                    updateDisplaySetting(displaySetting.copy(showDateTimeInMessage = it))
                },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_token_usage_title),
                checked = displaySetting.showTokenUsage,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showTokenUsage = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_thinking_content_title),
                checked = displaySetting.showThinkingContent,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showThinkingContent = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_auto_collapse_thinking_title),
                checked = displaySetting.autoCloseThinking,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(autoCloseThinking = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_enable_latex_rendering_title),
                checked = displaySetting.enableLatexRendering,
                onCheckedChange = {
                    updateDisplaySetting(displaySetting.copy(enableLatexRendering = it))
                },
            )
        }
    }
}
