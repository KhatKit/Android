package heizige.kk.khatkit.app.feature.settings

import heizige.kk.khatkit.app.core.ui.components.ui.settingAnchor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.uikit.mochi.KkFancySlider
import heizige.kk.khatkit.app.core.ui.components.ui.SwitchSetting
import heizige.kk.khatkit.app.core.ui.components.ui.settingItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.BackgroundEffectType
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import ui.feice.mochi.theme.MochiTheme
import heizige.kk.khatkit.app.core.ui.components.ui.sliderItem
import heizige.kk.khatkit.app.core.ui.components.ui.Select
import heizige.kk.khatkit.app.core.ui.hooks.rememberSharedPreferenceBoolean
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.plus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.math.roundToInt

@Composable
fun SettingPreferencesGeneralPage(vm: SettingViewModel = hiltViewModel()) {
    
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var ttsPlaybackSpeed by remember(settings.defaultTTSPlaybackSpeed) {
        mutableFloatStateOf(settings.defaultTTSPlaybackSpeed)
    }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_page_preferences_general),
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
                var createNewConversationOnStart by rememberSharedPreferenceBoolean(
                    "create_new_conversation_on_start",
                    true
                )
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_create_new_conversation_on_start_title,
                            null,
                            checked = createNewConversationOnStart,
                            onCheckedChange = { createNewConversationOnStart = it },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_create_new_conversation_on_start_title),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_send_on_enter_title,
                            null,
                            checked = displaySetting.sendOnEnter,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(sendOnEnter = it)) },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_send_on_enter_title),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_show_message_jumper_title,
                            R.string.setting_display_page_show_message_jumper_desc,
                            checked = displaySetting.showMessageJumper,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(showMessageJumper = it)) },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_show_message_jumper_title),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_message_jumper_position_title,
                            R.string.setting_display_page_message_jumper_position_desc,
                            checked = displaySetting.messageJumperOnLeft,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(messageJumperOnLeft = it)) },
                        ),
                        visible = displaySetting.showMessageJumper,
                        anchor = settingAnchor(R.string.setting_display_page_message_jumper_position_title),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_enable_auto_scroll_title,
                            null,
                            checked = displaySetting.enableAutoScroll,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableAutoScroll = it)) },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_enable_auto_scroll_title),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_use_app_icon_style_loading_indicator_title,
                            null,
                            checked = displaySetting.useAppIconStyleLoadingIndicator,
                            onCheckedChange = {
                                updateDisplaySetting(displaySetting.copy(useAppIconStyleLoadingIndicator = it))
                            },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_use_app_icon_style_loading_indicator_title),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_background_effect_title,
                            null,
                            checked = displaySetting.enableBlurEffect,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableBlurEffect = it)) },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_background_effect_title),
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_background_effect_type)) },
                        supportingContent = {
                            Select(
                                options = BackgroundEffectType.entries,
                                selectedOption = displaySetting.backgroundEffectType,
                                onOptionSelected = {
                                    updateDisplaySetting(displaySetting.copy(backgroundEffectType = it))
                                },
                                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                                optionToString = {
                                    when (it) {
                                        BackgroundEffectType.BLUR -> stringResource(R.string.setting_display_page_background_effect_blur)
                                        BackgroundEffectType.GLASS -> stringResource(R.string.setting_display_page_background_effect_glass)
                                    }
                                },
                            )
                        },
                        visible = displaySetting.enableBlurEffect,
                        anchor = settingAnchor(R.string.setting_display_page_background_effect_type),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_enable_message_generation_haptic_effect_title,
                            R.string.setting_display_page_enable_message_generation_haptic_effect_desc,
                            checked = displaySetting.enableMessageGenerationHapticEffect,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableMessageGenerationHapticEffect = it)) },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_enable_message_generation_haptic_effect_title),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_skip_crop_image_title,
                            null,
                            checked = displaySetting.skipCropImage,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(skipCropImage = it)) },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_skip_crop_image_title),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_paste_long_text_as_file_title,
                            R.string.setting_display_page_paste_long_text_as_file_desc,
                            checked = displaySetting.pasteLongTextAsFile,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(pasteLongTextAsFile = it)) },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_paste_long_text_as_file_title),
                    )
                    sliderItem(
                        title = { stringResource(R.string.setting_display_page_paste_long_text_threshold_title) },
                        value = displaySetting.pasteLongTextThreshold.toFloat(),
                        onValueChange = {
                            updateDisplaySetting(displaySetting.copy(pasteLongTextThreshold = it.toInt()))
                        },
                        valueRange = 100f..10000f,
                        defaultValue = 1000f,
                        visible = displaySetting.pasteLongTextAsFile,
                        anchor = settingAnchor(R.string.setting_display_page_paste_long_text_threshold_title),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_volume_key_scroll_title,
                            R.string.setting_display_page_volume_key_scroll_desc,
                            checked = displaySetting.enableVolumeKeyScroll,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableVolumeKeyScroll = it)) },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_volume_key_scroll_title),
                    )
                    sliderItem(
                        title = { stringResource(R.string.setting_display_page_volume_key_scroll_ratio) },
                        value = displaySetting.volumeKeyScrollRatio,
                        onValueChange = {
                            updateDisplaySetting(displaySetting.copy(volumeKeyScrollRatio = it))
                        },
                        valueRange = 0.25f..1.0f,
                        defaultValue = 1.0f,
                        steps = 2,
                        displayScale = 100f,
                        suffix = "%",
                        visible = displaySetting.enableVolumeKeyScroll,
                        anchor = settingAnchor(R.string.setting_display_page_volume_key_scroll_ratio),
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_tts_settings)) },
                ) {
                    sliderItem(
                        title = { stringResource(R.string.setting_tts_page_default_playback_speed) },
                        value = ttsPlaybackSpeed,
                        onValueChange = { ttsPlaybackSpeed = (it * 10).roundToInt() / 10f },
                        valueRange = 0.5f..2.0f,
                        defaultValue = 1.0f,
                        steps = 14,
                        prefix = "x",
                        decimals = 1,
                        description = { stringResource(R.string.setting_tts_page_default_playback_speed_description) },
                        onValueChangeFinished = {
                            vm.updateSettings(
                                settings.copy(defaultTTSPlaybackSpeed = ttsPlaybackSpeed)
                            )
                        },
                        anchor = settingAnchor(R.string.setting_tts_page_default_playback_speed),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_tts_only_read_quoted_title,
                            null,
                            checked = displaySetting.ttsOnlyReadQuoted,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(ttsOnlyReadQuoted = it)) },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_tts_only_read_quoted_title),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_tts_read_outside_brackets_title,
                            null,
                            checked = displaySetting.ttsOnlyReadOutsideBrackets,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(ttsOnlyReadOutsideBrackets = it)) },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_tts_read_outside_brackets_title),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_auto_play_tts_title,
                            R.string.setting_display_page_auto_play_tts_desc,
                            checked = displaySetting.autoPlayTTSAfterGeneration,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(autoPlayTTSAfterGeneration = it)) },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_auto_play_tts_title),
                    )
                }
            }
        }
    }
}