package heizige.kk.khatkit.app.feature.settings

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
import heizige.kk.khatkit.app.feature.settings.data.SettingSearchEntry
/**
 * 一条设置搜索结果。
 *
 * 两种粒度共用一个类型：
 * - 页面级（anchor 为 null）：整页入口，跳过去就完了（「通知」「服务商」这种）；
 * - 选项级（anchor 非 null）：页面里的最小选项，跳过去之后还要描边 + 滚到它
 *   （「显示时间」「气泡不透明度」这种）。锚点由被命中的那一行自己声明，
 *   见 core/ui/components/ui/SettingFocus.kt 与 settingAnchor()。
 */
/** 选项级结果的行图标：按归属页面给一个同类图标即可（图标不是信息重点）。 */

internal fun Screen.leafIcon(): ImageVector = when (this) {
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
internal fun Screen.pageTitle(): String = when (this) {
    Screen.SettingPreferencesGeneral -> stringResource(R.string.setting_page_preferences_general)
    Screen.SettingPreferencesUIMessage -> stringResource(R.string.setting_page_preferences_ui_message)
    Screen.SettingPreferencesUIFont -> stringResource(R.string.setting_page_preferences_ui_font)
    Screen.SettingThemeNavBar -> stringResource(R.string.setting_preferences_navigation_title)
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
