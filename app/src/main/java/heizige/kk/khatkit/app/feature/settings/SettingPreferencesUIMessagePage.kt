package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.SwitchSetting
import heizige.kk.khatkit.app.core.ui.components.ui.settingItem
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khromia.components.FancySlider
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import kotlin.math.roundToInt

@Composable
fun SettingPreferencesUIMessagePage(vm: SettingViewModel = hiltViewModel()) {
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        SettingPreferencesUIMessagePageMiuix(vm)
        return
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.setting_page_preferences_ui_message),
        scrollBehavior = scrollBehavior,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(
                start = 8.dp,
                top = PageMetrics.TopContentPadding,
                end = 8.dp,
                bottom = 8.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    // 这里原有的 `show_user_avatar_title`（显示用户头像）与
                    // `chat_list_model_icon_title`（模型图标）两个开关已删：消息气泡旁的头像
                    // 整体删除（AI 头像只在顶栏保留一处），`showUserAvatar` / `showModelIcon`
                    // 再没有消费方，留着就是无效死开关。**持久化字段本身保留**
                    // （SettingsRepository 不动），旧数据与导入导出不受影响。
                    //
                    // `show_assistant_bubble`（显示助手消息气泡）**保留**，且消费方回来了
                    // （ChatMessage / ConversationExport 一起判定）。默认 true，老安装里
                    // 落盘的显式 false 由 PreferenceStoreV6Migration 一次性翻正。
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_show_assistant_bubble_title,
                            null,
                            checked = displaySetting.showAssistantBubble,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(showAssistantBubble = it)) },
                        )
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_bubble_opacity_title)) },
                        supportingContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FancySlider(
                                    value = displaySetting.bubbleOpacity,
                                    onValueChange = {
                                        updateDisplaySetting(displaySetting.copy(bubbleOpacity = it))
                                    },
                                    valueRange = 0.1f..1.0f,
                                    steps = 8,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(text = "${(displaySetting.bubbleOpacity * 100).roundToInt()}%")
                            }
                        }
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_show_model_name_title,
                            null,
                            checked = displaySetting.showModelName,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(showModelName = it)) },
                        )
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_show_datetime_in_message_title,
                            null,
                            checked = displaySetting.showDateTimeInMessage,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(showDateTimeInMessage = it)) },
                        )
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_show_token_usage_title,
                            null,
                            checked = displaySetting.showTokenUsage,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(showTokenUsage = it)) },
                        )
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_show_thinking_content_title,
                            null,
                            checked = displaySetting.showThinkingContent,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(showThinkingContent = it)) },
                        )
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_auto_collapse_thinking_title,
                            null,
                            checked = displaySetting.autoCloseThinking,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(autoCloseThinking = it)) },
                        )
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_enable_latex_rendering_title,
                            null,
                            checked = displaySetting.enableLatexRendering,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableLatexRendering = it)) },
                        )
                    )
                }
            }
        }
    }
}
