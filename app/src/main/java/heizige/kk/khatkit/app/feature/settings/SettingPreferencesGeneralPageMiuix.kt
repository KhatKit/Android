package heizige.kk.khatkit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.BackgroundEffectType
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceDropdown
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSlider
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.ui.hooks.rememberSharedPreferenceBoolean
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import kotlin.math.roundToInt

/**
 * 常规偏好页的 Miuix 风格版。
 *
 * 与 MD3 版逐项对应：开关走 [PreferenceSwitch]，下拉走 [PreferenceDropdown]，
 * 数值滑块走 [PreferenceSlider]，条件显示的子项保持同样的 when 语义。
 */
@Composable
fun SettingPreferencesGeneralPageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var ttsPlaybackSpeed by remember(settings.defaultTTSPlaybackSpeed) {
        mutableFloatStateOf(settings.defaultTTSPlaybackSpeed)
    }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    var createNewConversationOnStart by rememberSharedPreferenceBoolean(
        "create_new_conversation_on_start",
        true,
    )

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_preferences_general),
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        navigationIcon = { BackButton() },
    ) {
        miuixGroup {
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_create_new_conversation_on_start_title),
                summary = stringResource(R.string.setting_display_page_create_new_conversation_on_start_desc),
                checked = createNewConversationOnStart,
                onCheckedChange = { createNewConversationOnStart = it },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_send_on_enter_title),
                summary = stringResource(R.string.setting_display_page_send_on_enter_desc),
                checked = displaySetting.sendOnEnter,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(sendOnEnter = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_message_jumper_title),
                summary = stringResource(R.string.setting_display_page_show_message_jumper_desc),
                checked = displaySetting.showMessageJumper,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showMessageJumper = it)) },
            )
            if (displaySetting.showMessageJumper) {
                PreferenceSwitch(
                    title = stringResource(R.string.setting_display_page_message_jumper_position_title),
                    summary = stringResource(R.string.setting_display_page_message_jumper_position_desc),
                    checked = displaySetting.messageJumperOnLeft,
                    onCheckedChange = {
                        updateDisplaySetting(displaySetting.copy(messageJumperOnLeft = it))
                    },
                )
            }
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_enable_auto_scroll_title),
                summary = stringResource(R.string.setting_display_page_enable_auto_scroll_desc),
                checked = displaySetting.enableAutoScroll,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableAutoScroll = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_use_app_icon_style_loading_indicator_title),
                summary = stringResource(R.string.setting_display_page_use_app_icon_style_loading_indicator_desc),
                checked = displaySetting.useAppIconStyleLoadingIndicator,
                onCheckedChange = {
                    updateDisplaySetting(displaySetting.copy(useAppIconStyleLoadingIndicator = it))
                },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_background_effect_title),
                summary = stringResource(R.string.setting_display_page_background_effect_desc),
                checked = displaySetting.enableBlurEffect,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableBlurEffect = it)) },
            )
            if (displaySetting.enableBlurEffect) {
                PreferenceDropdown(
                    title = stringResource(R.string.setting_display_page_background_effect_type),
                    items = BackgroundEffectType.entries.map {
                        stringResource(
                            when (it) {
                                BackgroundEffectType.BLUR -> R.string.setting_display_page_background_effect_blur
                                BackgroundEffectType.GLASS -> R.string.setting_display_page_background_effect_glass
                            }
                        )
                    },
                    selectedIndex = BackgroundEffectType.entries.indexOf(displaySetting.backgroundEffectType),
                    onSelectedIndexChange = {
                        updateDisplaySetting(
                            displaySetting.copy(backgroundEffectType = BackgroundEffectType.entries[it])
                        )
                    },
                )
            }
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title),
                summary = stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc),
                checked = displaySetting.enableMessageGenerationHapticEffect,
                onCheckedChange = {
                    updateDisplaySetting(
                        displaySetting.copy(enableMessageGenerationHapticEffect = it)
                    )
                },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_skip_crop_image_title),
                summary = stringResource(R.string.setting_display_page_skip_crop_image_desc),
                checked = displaySetting.skipCropImage,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(skipCropImage = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_paste_long_text_as_file_title),
                summary = stringResource(R.string.setting_display_page_paste_long_text_as_file_desc),
                checked = displaySetting.pasteLongTextAsFile,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(pasteLongTextAsFile = it)) },
            )
            if (displaySetting.pasteLongTextAsFile) {
                PreferenceSlider(
                    title = stringResource(R.string.setting_display_page_paste_long_text_threshold_title),
                    value = displaySetting.pasteLongTextThreshold.toFloat(),
                    valueRange = 100f..10000f,
                    valueText = "${displaySetting.pasteLongTextThreshold}",
                    onValueChange = {
                        updateDisplaySetting(displaySetting.copy(pasteLongTextThreshold = it.toInt()))
                    },
                )
            }
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_volume_key_scroll_title),
                summary = stringResource(R.string.setting_display_page_volume_key_scroll_desc),
                checked = displaySetting.enableVolumeKeyScroll,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableVolumeKeyScroll = it)) },
            )
            if (displaySetting.enableVolumeKeyScroll) {
                PreferenceSlider(
                    title = stringResource(R.string.setting_display_page_volume_key_scroll_ratio),
                    value = displaySetting.volumeKeyScrollRatio,
                    valueRange = 0.25f..1.0f,
                    steps = 2,
                    valueText = "${(displaySetting.volumeKeyScrollRatio * 100).toInt()}%",
                    onValueChange = {
                        updateDisplaySetting(displaySetting.copy(volumeKeyScrollRatio = it))
                    },
                )
            }
        }

        miuixGroup {
            PreferenceSlider(
                title = stringResource(R.string.setting_tts_page_default_playback_speed),
                summary = stringResource(R.string.setting_tts_page_default_playback_speed_description),
                value = ttsPlaybackSpeed,
                valueRange = 0.5f..2.0f,
                steps = 14,
                valueText = "x${"%.1f".format(ttsPlaybackSpeed)}",
                onValueChange = { ttsPlaybackSpeed = (it * 10).roundToInt() / 10f },
                onValueChangeFinished = {
                    vm.updateSettings(settings.copy(defaultTTSPlaybackSpeed = ttsPlaybackSpeed))
                },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_tts_only_read_quoted_title),
                summary = stringResource(R.string.setting_display_page_tts_only_read_quoted_desc),
                checked = displaySetting.ttsOnlyReadQuoted,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(ttsOnlyReadQuoted = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_tts_read_outside_brackets_title),
                summary = stringResource(R.string.setting_display_page_tts_read_outside_brackets_desc),
                checked = displaySetting.ttsOnlyReadOutsideBrackets,
                onCheckedChange = {
                    updateDisplaySetting(displaySetting.copy(ttsOnlyReadOutsideBrackets = it))
                },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_auto_play_tts_title),
                summary = stringResource(R.string.setting_display_page_auto_play_tts_desc),
                checked = displaySetting.autoPlayTTSAfterGeneration,
                onCheckedChange = {
                    updateDisplaySetting(displaySetting.copy(autoPlayTTSAfterGeneration = it))
                },
            )
        }
    }
}
