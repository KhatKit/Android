package heizige.kk.khatkit.app.feature.settings.data

import heizige.kk.khatkit.app.feature.settings.pageTitle
import heizige.kk.khatkit.app.feature.settings.leafIcon
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.ui.settingAnchor
import heizige.kk.khatkit.app.core.ui.icons.ai.autoAwesome
import heizige.kk.khatkit.app.core.ui.icons.settings.barChart
import heizige.kk.khatkit.app.core.ui.icons.automation.bolt
import heizige.kk.khatkit.app.core.ui.icons.utility.campaign
import heizige.kk.khatkit.app.core.ui.icons.utility.celebration
import heizige.kk.khatkit.app.core.ui.icons.settings.database
import heizige.kk.khatkit.app.core.ui.icons.utility.dns
import heizige.kk.khatkit.app.core.ui.icons.settings.favorite
import heizige.kk.khatkit.app.core.ui.icons.files.inventory2
import heizige.kk.khatkit.app.core.ui.icons.settings.language
import heizige.kk.khatkit.app.core.ui.icons.settings.memory
import heizige.kk.khatkit.app.core.ui.icons.settings.notifications
import heizige.kk.khatkit.app.core.ui.icons.settings.palette
import heizige.kk.khatkit.app.core.ui.icons.utility.search
import heizige.kk.khatkit.app.core.ui.icons.settings.settings
import heizige.kk.khatkit.app.core.ui.icons.files.shelves
import heizige.kk.khatkit.app.core.ui.icons.utility.travelExplore
import heizige.kk.khatkit.app.core.ui.icons.utility.verifiedUser

internal data class SettingSearchEntry(
    val title: String,
    val description: String,
    val keywords: String,
    val icon: ImageVector,
    val destination: Screen,
    /** 选项级结果的行锚点；页面级结果为 null。 */
    val anchor: String? = null,
    /** 选项级结果所属页面的标题（页面级结果留空，标题就是 title 本身）。 */
    val pageTitle: String = "",
)
/**
 * 选项级设置项登记表：设置页里每一个最小选项一行。
 *
 * 为什么是声明式清单而不是运行时遍历页面内容：Compose 页面是命令式组合，跑在 UI 线程、
 * 依赖 ViewModel，搜索页不可能去「遍历某个已组合页面里的所有控件」。所以选项级搜索必须
 * 靠两边各自声明、按同一个 StringRes 对齐：
 *   - 这里登记 title + keywords + 所在页；
 *   - 页面侧对应的那一行传 anchor = settingAnchor(R.string.同一个常量)。
 * 两边引用同一个常量，改名时编译期就会有一边报错。
 *
 * 维护约定：给设置页新增一个最小选项时，本表补一行、页面上的行补 anchor 参数。
 * 漏了页面侧锚点不会崩，只是搜索命中后跳过去不高亮（退化成页面级行为）。
 */
internal object SettingLeafEntries {

    private data class Leaf(
        @StringRes val title: Int,
        @StringRes val description: Int?,
        @StringRes val keywords: Int,
        val page: Screen,
    )

    private val leaves: List<Leaf> = listOf(
        // ── 界面 → 通用 ─────────────────────────────────────────────
        Leaf(R.string.setting_display_page_create_new_conversation_on_start_title, null, R.string.setting_display_page_create_new_conversation_on_start_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_send_on_enter_title, null, R.string.setting_display_page_send_on_enter_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_show_message_jumper_title, null, R.string.setting_display_page_show_message_jumper_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_message_jumper_position_title, null, R.string.setting_display_page_message_jumper_position_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_enable_auto_scroll_title, null, R.string.setting_display_page_enable_auto_scroll_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_use_app_icon_style_loading_indicator_title, null, R.string.setting_display_page_use_app_icon_style_loading_indicator_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_background_effect_title, null, R.string.setting_display_page_background_effect_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_background_effect_type, null, R.string.setting_display_page_background_effect_type_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_enable_message_generation_haptic_effect_title, null, R.string.setting_display_page_enable_message_generation_haptic_effect_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_skip_crop_image_title, null, R.string.setting_display_page_skip_crop_image_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_paste_long_text_as_file_title, null, R.string.setting_display_page_paste_long_text_as_file_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_paste_long_text_threshold_title, null, R.string.setting_display_page_paste_long_text_threshold_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_volume_key_scroll_title, null, R.string.setting_display_page_volume_key_scroll_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_volume_key_scroll_ratio, null, R.string.setting_display_page_volume_key_scroll_ratio_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_tts_page_default_playback_speed, R.string.setting_tts_page_default_playback_speed_description, R.string.setting_tts_page_default_playback_speed_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_tts_only_read_quoted_title, null, R.string.setting_display_page_tts_only_read_quoted_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_tts_read_outside_brackets_title, null, R.string.setting_display_page_tts_read_outside_brackets_title_keywords, Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_auto_play_tts_title, R.string.setting_display_page_auto_play_tts_desc, R.string.setting_display_page_auto_play_tts_title_keywords, Screen.SettingPreferencesGeneral),
        // ── 界面 → 消息显示 ─────────────────────────────────────────
        Leaf(R.string.setting_display_page_show_assistant_bubble_title, null, R.string.setting_display_page_show_assistant_bubble_title_keywords, Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_split_assistant_bubble_title, R.string.setting_display_page_split_assistant_bubble_desc, R.string.setting_display_page_split_assistant_bubble_title_keywords, Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_bubble_opacity_title, null, R.string.setting_display_page_bubble_opacity_title_keywords, Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_show_model_name_title, null, R.string.setting_display_page_show_model_name_title_keywords, Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_show_datetime_in_message_title, null, R.string.setting_display_page_show_datetime_in_message_title_keywords, Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_show_token_usage_title, null, R.string.setting_display_page_show_token_usage_title_keywords, Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_show_thinking_content_title, null, R.string.setting_display_page_show_thinking_content_title_keywords, Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_auto_collapse_thinking_title, null, R.string.setting_display_page_auto_collapse_thinking_title_keywords, Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_enable_latex_rendering_title, null, R.string.setting_display_page_enable_latex_rendering_title_keywords, Screen.SettingPreferencesUIMessage),
        // ── 界面 → 字体 ─────────────────────────────────────────────
        Leaf(R.string.setting_preferences_font_serif_title, R.string.setting_preferences_font_serif_desc, R.string.setting_preferences_font_serif_title_keywords, Screen.SettingPreferencesUIFont),
        Leaf(R.string.setting_display_page_chat_font_family_title, null, R.string.setting_display_page_chat_font_family_title_keywords, Screen.SettingPreferencesUIFont),
        Leaf(R.string.setting_display_page_custom_font_title, null, R.string.setting_display_page_custom_font_title_keywords, Screen.SettingPreferencesUIFont),
        Leaf(R.string.setting_display_page_font_size_title, null, R.string.setting_display_page_font_size_title_keywords, Screen.SettingPreferencesUIFont),
        Leaf(R.string.setting_preferences_predictive_back_title, R.string.setting_preferences_predictive_back_desc, R.string.setting_preferences_predictive_back_title_keywords, Screen.SettingThemeNavBar),
        // ── 界面 → 代码块 ───────────────────────────────────────────
        Leaf(R.string.setting_display_page_code_block_auto_wrap_title, null, R.string.setting_display_page_code_block_auto_wrap_title_keywords, Screen.SettingPreferencesUICode),
        Leaf(R.string.setting_display_page_code_block_auto_collapse_title, null, R.string.setting_display_page_code_block_auto_collapse_title_keywords, Screen.SettingPreferencesUICode),
        Leaf(R.string.setting_display_page_show_line_numbers_title, null, R.string.setting_display_page_show_line_numbers_title_keywords, Screen.SettingPreferencesUICode),
        // ── 通知 ────────────────────────────────────────────────────
        Leaf(R.string.setting_display_page_show_updates_title, null, R.string.setting_display_page_show_updates_title_keywords, Screen.SettingPreferencesNotification),
        Leaf(R.string.setting_display_page_notification_message_generated, null, R.string.setting_display_page_notification_message_generated_keywords, Screen.SettingPreferencesNotification),
        Leaf(R.string.setting_display_page_live_update_notification, null, R.string.setting_display_page_live_update_notification_keywords, Screen.SettingPreferencesNotification),
        Leaf(R.string.setting_display_page_floating_chat, null, R.string.setting_display_page_floating_chat_keywords, Screen.SettingPreferencesNotification),
        // ── 网络 ────────────────────────────────────────────────────
        Leaf(R.string.setting_page_preferences_network_auto_retry, null, R.string.setting_page_preferences_network_auto_retry_keywords, Screen.SettingPreferencesNetwork),
        // ── 网页服务 ────────────────────────────────────────────────
        Leaf(R.string.setting_page_web_server_port, null, R.string.setting_page_web_server_port_keywords, Screen.SettingWeb),
        Leaf(R.string.setting_page_web_server_localhost_only, null, R.string.setting_page_web_server_localhost_only_keywords, Screen.SettingWeb),
        Leaf(R.string.setting_page_web_server_jwt_enable, null, R.string.setting_page_web_server_jwt_enable_keywords, Screen.SettingWeb),
        Leaf(R.string.setting_page_web_server_password, null, R.string.setting_page_web_server_password_keywords, Screen.SettingWeb),
        // ── 主题 ────────────────────────────────────────────────────
        Leaf(R.string.greeting_settings_dynamic_color, null, R.string.greeting_settings_dynamic_color_keywords, Screen.SettingThemeColors),
        Leaf(R.string.setting_display_page_effects_title, null, R.string.setting_display_page_effects_title_keywords, Screen.SettingThemeMaterials),
        Leaf(R.string.setting_display_page_button_style_title, null, R.string.setting_display_page_button_style_title_keywords, Screen.SettingThemeMaterials),
        Leaf(R.string.setting_display_page_card_corner_large, null, R.string.setting_display_page_card_corner_large_keywords, Screen.SettingThemeListCards),
        Leaf(R.string.setting_display_page_card_corner_small, null, R.string.setting_display_page_card_corner_small_keywords, Screen.SettingThemeListCards),
        Leaf(R.string.setting_display_page_card_gap, null, R.string.setting_display_page_card_gap_keywords, Screen.SettingThemeListCards),
    )

    /** 全部选项级条目（中文标题/说明在组合期解析成字符串）。 */
    @Composable
    fun entries(): List<SettingSearchEntry> = leaves.map { leaf ->
        SettingSearchEntry(
            title = stringResource(leaf.title),
            description = leaf.description?.let { stringResource(it) } ?: "",
            keywords = stringResource(leaf.keywords),
            icon = leaf.page.leafIcon(),
            destination = leaf.page,
            anchor = settingAnchor(leaf.title),
            pageTitle = leaf.page.pageTitle(),
        )
    }

    /** 登记表规模：护栏测试与调试用。 */
    internal val size: Int get() = leaves.size
}
