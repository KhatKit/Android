package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import heizige.kk.khatkit.app.core.ui.components.ui.SwitchSetting
import heizige.kk.khatkit.app.core.ui.components.ui.settingItem
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
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

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.setting_page_preferences_ui_message),
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.pageContainerColor
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
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_show_user_avatar_title,
                            null,
                            checked = displaySetting.showUserAvatar,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(showUserAvatar = it)) },
                        )
                    )
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
                            R.string.setting_display_page_chat_list_model_icon_title,
                            null,
                            checked = displaySetting.showModelIcon,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(showModelIcon = it)) },
                        )
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
