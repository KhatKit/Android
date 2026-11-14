package heizige.kk.khatkit.app.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.ui.settingAnchor
import heizige.kk.khatkit.app.core.ui.icons.autoAwesome
import heizige.kk.khatkit.app.core.ui.icons.barChart
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.campaign
import heizige.kk.khatkit.app.core.ui.icons.celebration
import heizige.kk.khatkit.app.core.ui.icons.database
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.favorite
import heizige.kk.khatkit.app.core.ui.icons.inventory2
import heizige.kk.khatkit.app.core.ui.icons.language
import heizige.kk.khatkit.app.core.ui.icons.memory
import heizige.kk.khatkit.app.core.ui.icons.notifications
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.icons.settings
import heizige.kk.khatkit.app.core.ui.icons.shelves
import heizige.kk.khatkit.app.core.ui.icons.travelExplore
import heizige.kk.khatkit.app.core.ui.icons.verifiedUser
/**
 * 一条设置搜索结果。
 *
 * 两种粒度共用一个类型：
 * - 页面级（anchor 为 null）：整页入口，跳过去就完了（「通知」「服务商」这种）；
 * - 选项级（anchor 非 null）：页面里的最小选项，跳过去之后还要描边 + 滚到它
 *   （「显示时间」「气泡不透明度」这种）。锚点由被命中的那一行自己声明，
 *   见 core/ui/components/ui/SettingFocus.kt 与 settingAnchor()。
 */
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
 */internal object SettingLeafEntries {

    private data class Leaf(
        @StringRes val title: Int,
        @StringRes val description: Int?,
        val keywords: String,
        val page: Screen,
    )

    private val leaves: List<Leaf> = listOf(
        // ── 界面 → 通用 ─────────────────────────────────────────────
        Leaf(R.string.setting_display_page_create_new_conversation_on_start_title, null, "new conversation start launch 新会话 启动 开屏", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_send_on_enter_title, null, "enter send ime keyboard 回车 发送 键盘", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_show_message_jumper_title, null, "jumper jump navigation 跳转 消息定位", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_message_jumper_position_title, null, "jumper left right position 跳转 位置 左右", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_enable_auto_scroll_title, null, "auto scroll 自动 滚动", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_use_app_icon_style_loading_indicator_title, null, "loading indicator icon 加载 图标", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_background_effect_title, null, "background blur glass effect 背景 模糊 玻璃", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_background_effect_type, null, "background blur glass 背景 效果 类型", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_enable_message_generation_haptic_effect_title, null, "haptic vibrate 触感 震动", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_skip_crop_image_title, null, "crop image 裁剪 图片", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_paste_long_text_as_file_title, null, "paste long text file 粘贴 长文本 文件", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_paste_long_text_threshold_title, null, "paste threshold length 粘贴 阈值 长度", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_volume_key_scroll_title, null, "volume key scroll 音量键 滚动", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_volume_key_scroll_ratio, null, "volume key scroll ratio speed 音量键 速度 比例", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_tts_page_default_playback_speed, R.string.setting_tts_page_default_playback_speed_description, "tts speed playback 朗读 语速 倍速", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_tts_only_read_quoted_title, null, "tts quote 朗读 引用", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_tts_read_outside_brackets_title, null, "tts brackets 朗读 括号", Screen.SettingPreferencesGeneral),
        Leaf(R.string.setting_display_page_auto_play_tts_title, R.string.setting_display_page_auto_play_tts_desc, "tts auto play 朗读 自动播放", Screen.SettingPreferencesGeneral),
        // ── 界面 → 消息显示 ─────────────────────────────────────────
        Leaf(R.string.setting_display_page_show_assistant_bubble_title, null, "bubble assistant 气泡 助手", Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_bubble_opacity_title, null, "bubble opacity alpha 气泡 透明度", Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_show_model_name_title, null, "model name 模型 名称", Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_show_datetime_in_message_title, null, "datetime time timestamp 时间 日期", Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_show_token_usage_title, null, "token usage 令牌 用量", Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_show_thinking_content_title, null, "thinking reasoning 思考 推理", Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_auto_collapse_thinking_title, null, "thinking collapse 思考 折叠", Screen.SettingPreferencesUIMessage),
        Leaf(R.string.setting_display_page_enable_latex_rendering_title, null, "latex math formula 公式 数学", Screen.SettingPreferencesUIMessage),
        // ── 界面 → 字体 ─────────────────────────────────────────────
        Leaf(R.string.setting_display_page_chat_font_family_title, null, "font family 字体 字族", Screen.SettingPreferencesUIFont),
        Leaf(R.string.setting_display_page_custom_font_title, null, "custom font import 自定义 字体 导入", Screen.SettingPreferencesUIFont),
        Leaf(R.string.setting_display_page_font_size_title, null, "font size 字号 大小", Screen.SettingPreferencesUIFont),
        // ── 界面 → 代码块 ───────────────────────────────────────────
        Leaf(R.string.setting_display_page_code_block_auto_wrap_title, null, "code wrap 代码 换行", Screen.SettingPreferencesUICode),
        Leaf(R.string.setting_display_page_code_block_auto_collapse_title, null, "code collapse 代码 折叠", Screen.SettingPreferencesUICode),
        Leaf(R.string.setting_display_page_show_line_numbers_title, null, "line numbers 行号 代码", Screen.SettingPreferencesUICode),
        // ── 通知 ────────────────────────────────────────────────────
        Leaf(R.string.setting_display_page_show_updates_title, null, "notification update 通知 更新", Screen.SettingPreferencesNotification),
        Leaf(R.string.setting_display_page_notification_message_generated, null, "notification message generated 通知 消息 生成", Screen.SettingPreferencesNotification),
        Leaf(R.string.setting_display_page_live_update_notification, null, "live update notification 实时 通知", Screen.SettingPreferencesNotification),
        Leaf(R.string.setting_display_page_floating_chat, null, "floating chat window 悬浮 聊天 窗口", Screen.SettingPreferencesNotification),
        // ── 网络 ────────────────────────────────────────────────────
        Leaf(R.string.setting_page_preferences_network_auto_retry, null, "retry network 重试 网络", Screen.SettingPreferencesNetwork),
        // ── 网页服务 ────────────────────────────────────────────────
        Leaf(R.string.setting_page_web_server_port, null, "web port 端口 服务端", Screen.SettingWeb),
        Leaf(R.string.setting_page_web_server_localhost_only, null, "localhost network 本机 局域网", Screen.SettingWeb),
        Leaf(R.string.setting_page_web_server_jwt_enable, null, "jwt auth token 认证 令牌", Screen.SettingWeb),
        Leaf(R.string.setting_page_web_server_password, null, "password 密码", Screen.SettingWeb),
        // ── 主题 ────────────────────────────────────────────────────
        Leaf(R.string.greeting_settings_dynamic_color, null, "dynamic color monet 动态取色 主题", Screen.SettingThemeColors),
        Leaf(R.string.setting_display_page_effects_title, null, "glass blur material effect 玻璃 模糊 材质 效果", Screen.SettingThemeMaterials),
        Leaf(R.string.setting_display_page_button_style_title, null, "button style 按钮 样式", Screen.SettingThemeMaterials),
        Leaf(R.string.setting_display_page_card_corner_large, null, "card corner radius 卡片 圆角", Screen.SettingThemeListCards),
        Leaf(R.string.setting_display_page_card_corner_small, null, "card corner radius 卡片 圆角", Screen.SettingThemeListCards),
        Leaf(R.string.setting_display_page_card_gap, null, "card gap spacing 卡片 间距", Screen.SettingThemeListCards),
    )

    /** 全部选项级条目（中文标题/说明在组合期解析成字符串）。 */
    @Composable
    fun entries(): List<SettingSearchEntry> = leaves.map { leaf ->
        SettingSearchEntry(
            title = stringResource(leaf.title),
            description = leaf.description?.let { stringResource(it) } ?: "",
            keywords = leaf.keywords,
            icon = leaf.page.leafIcon(),
            destination = leaf.page,
            anchor = settingAnchor(leaf.title),
            pageTitle = leaf.page.pageTitle(),
        )
    }

    /** 登记表规模：护栏测试与调试用。 */
    internal val size: Int get() = leaves.size
}

/** 选项级结果的行图标：按归属页面给一个同类图标即可（图标不是信息重点）。 */
private fun Screen.leafIcon(): ImageVector = when (this) {
    Screen.SettingPreferencesNotification -> notifications
    Screen.SettingPreferencesNetwork -> language
    Screen.SettingWeb -> dns
    Screen.SettingThemeColors, Screen.SettingThemeMaterials, Screen.SettingThemeListCards -> palette
    Screen.SettingSearch -> travelExplore
    Screen.SettingModels -> autoAwesome
    Screen.SettingMemory -> memory
    Screen.SettingFiles -> database
    Screen.SettingAbout -> celebration
    Screen.Stats -> barChart
    Screen.Log -> shelves
    Screen.SettingDonate -> favorite
    Screen.Extensions -> inventory2
    Screen.SettingSpeech -> campaign
    Screen.SettingMcp -> dns
    Screen.TraceAudit -> bolt
    Screen.SettingPermissions -> verifiedUser
    else -> settings
}

/** 页面级结果标题：与一级入口同一份文案，未知页给空串（调用点会退回页面编号）。 */
@Composable
private fun Screen.pageTitle(): String = when (this) {
    Screen.SettingPreferencesGeneral -> stringResource(R.string.setting_page_preferences_general)
    Screen.SettingPreferencesUIMessage -> stringResource(R.string.setting_page_preferences_ui_message)
    Screen.SettingPreferencesUIFont -> stringResource(R.string.setting_page_preferences_ui_font)
    Screen.SettingPreferencesUICode -> stringResource(R.string.setting_page_preferences_ui_code)
    Screen.SettingPreferencesNotification -> stringResource(R.string.setting_page_preferences_notification)
    Screen.SettingPreferencesNetwork -> stringResource(R.string.setting_page_preferences_network)
    Screen.SettingWeb -> stringResource(R.string.setting_page_web_server)
    Screen.SettingThemeColors, Screen.SettingThemeMaterials, Screen.SettingThemeListCards ->
        stringResource(R.string.setting_page_themes)
    else -> ""
}
/**
 * 搜索结果的统一过滤与排序。
 *
 * 口径（页面级与选项级同一份）：
 * 1. 关键词按空白切词，**每个词都要命中**（AND）—— 搜「气泡 透明」能收敛到气泡不透明度，
 *    而不是把所有含「气泡」的行都捞出来；
 * 2. 每个词在 title / description / keywords / 归属页标题 拼成的串里做 contains（忽略大小写）；
 * 3. 排序：页面级在前（先给「去哪一页」的粗粒度答案），选项级在后，同一页的选项相邻；
 * 4. 空关键词只返回页面级入口（与改造前一致：先给一级清单，不预先摊开上百个选项）。
 */
internal fun filteredSettingResults(
    query: String,
    pages: List<SettingSearchEntry>,
    leaves: List<SettingSearchEntry>,
): List<SettingSearchEntry> {
    val tokens = query.trim().lowercase().split(WHITESPACE).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return pages
    fun SettingSearchEntry.matches(): Boolean {
        val haystack = listOf(title, description, keywords, pageTitle)
            .joinToString(" ")
            .lowercase()
        return tokens.all { haystack.contains(it) }
    }
    return buildList {
        addAll(pages.filter { it.matches() })
        addAll(leaves.filter { it.matches() })
    }
}

/** 切词用的空白集合：半角空格 + 全角空格 + 制表符。 */
private val WHITESPACE = Regex("[\\s\\u3000]+")

/**
 * 结果行的第二行文案。
 *
 * - 选项级：「界面 → 消息显示」这种归属路径，告诉用户这个开关在哪一页；
 * - 页面级：页面原来那句说明；没有说明就给 null（调用点不画第二行）。
 */
internal fun SettingSearchEntry.supportingLine(): String? = when {
    anchor != null && pageTitle.isNotBlank() -> pageTitle
    description.isNotBlank() -> description
    else -> null
}