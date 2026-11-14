package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.SettingFocusController
import heizige.kk.khatkit.app.navigation.ScreenRoutes
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
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
import heizige.kk.khatkit.app.core.ui.icons.psychology
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.icons.settings
import heizige.kk.khatkit.app.core.ui.icons.shelves
import heizige.kk.khatkit.app.core.ui.icons.travelExplore
import heizige.kk.khatkit.app.core.ui.icons.verifiedUser
import heizige.kk.khatkit.app.core.util.plus
import ui.feice.mochi.basic.MochiSearchBar

@Composable
fun SettingFeatureSearchPage() {
    val navController = LocalNavController.current
    var query by remember { mutableStateOf("") }
    val entries = rememberSettingSearchEntries()
    // 选项级结果（「最小选项」）跟页面级结果一起筛：同一份过滤口径，页面级排在前面，
    // 选项级按归属页分组接在后面（见 filteredSettingResults）。
    val leafEntries = SettingLeafEntries.entries()
    val filteredEntries = remember(query, entries, leafEntries) {
        filteredSettingResults(query, entries, leafEntries)
    }

    MochiSettingsPageScaffold(
        title = stringResource(R.string.search_picker_title),
        scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 8.dp,
                top = 12.dp,
                end = 8.dp,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("search") {
                MochiSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(horizontal = 8.dp),
                    placeholder = stringResource(R.string.search_picker_title),
                )
            }
            item("results") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    filteredEntries.forEach { entry ->
                        item(
                            onClick = {
                                entry.anchor?.let { anchor ->
                                    SettingFocusController.request(
                                        route = ScreenRoutes.routeOf(entry.destination),
                                        anchor = anchor,
                                    )
                                }
                                navController.navigate(entry.destination)
                            },
                            chevron = true,
                            leadingContent = {
                                Icon(
                                    imageVector = entry.icon,
                                    contentDescription = null,
                                )
                            },
                            supportingContent = entry.supportingLine()
                                ?.let { line -> { Text(line) } },
                            headlineContent = { Text(entry.title) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberSettingSearchEntries(): List<SettingSearchEntry> {
    val entries = listOf(
        SettingSearchEntry(stringResource(R.string.setting_page_preferences_ui), stringResource(R.string.setting_page_preferences_ui_desc), "ui appearance theme color font code 界面 外观 主题 颜色 字体 代码", palette, Screen.SettingPreferencesUI),
        SettingSearchEntry(stringResource(R.string.setting_page_preferences_notification), stringResource(R.string.setting_page_preferences_notification_desc), "notification alert reply 通知 提醒 回复", notifications, Screen.SettingPreferencesNotification),
        SettingSearchEntry(stringResource(R.string.setting_page_preferences_general), stringResource(R.string.setting_page_preferences_general_desc), "general interaction scroll input 通用 交互 滚动 输入", settings, Screen.SettingPreferencesGeneral),
        SettingSearchEntry(stringResource(R.string.setting_page_preferences_network), stringResource(R.string.setting_page_preferences_network_desc), "network proxy timeout connection 网络 代理 超时 连接", language, Screen.SettingPreferencesNetwork),
        SettingSearchEntry(stringResource(R.string.setting_page_permissions), stringResource(R.string.setting_page_permissions_desc), "permission location accessibility 权限 位置 无障碍", verifiedUser, Screen.SettingPermissions),
        SettingSearchEntry(stringResource(R.string.setting_page_assistant), stringResource(R.string.setting_page_assistant_desc), "assistant role character model 角色 助手 人设 模型", search, Screen.Assistant),
        SettingSearchEntry(stringResource(R.string.setting_page_extensions), stringResource(R.string.setting_page_extensions_desc), "extension plugin 扩展 插件", inventory2, Screen.Extensions),
        SettingSearchEntry(stringResource(R.string.setting_page_default_model), stringResource(R.string.setting_page_default_model_desc), "model prompt default 模型 提示词 默认", autoAwesome, Screen.SettingModels),
        SettingSearchEntry(stringResource(R.string.setting_page_providers), "", "provider api account 服务商 提供商 账号 接口", psychology, Screen.SettingProvider),
        SettingSearchEntry(stringResource(R.string.setting_page_search_service), "", "search engine web 搜索 搜索引擎 网络", travelExplore, Screen.SettingSearch),
        SettingSearchEntry(stringResource(R.string.setting_page_tts_service), stringResource(R.string.setting_page_tts_service_desc), "speech tts asr voice 语音 朗读 识别 声音", campaign, Screen.SettingSpeech),
        SettingSearchEntry(stringResource(R.string.setting_page_mcp), "", "mcp server tools 服务器 工具", dns, Screen.SettingMcp),
        SettingSearchEntry(stringResource(R.string.setting_page_trace_audit), stringResource(R.string.setting_page_trace_audit_desc), "trace audit debug request 追踪 审计 调试 请求", bolt, Screen.TraceAudit),
        SettingSearchEntry("探索市场", "浏览工具、技能与脚本卡片", "market tools skills cards 市场 工具 技能 卡片", inventory2, Screen.ExploreMarket),
        SettingSearchEntry("套餐激活", "套餐激活、额度与网关供应商", "package plan quota gateway 套餐 计划 额度 网关", favorite, Screen.SettingPackage),
        SettingSearchEntry("自动化触发器", "定时、事件自动运行卡片", "automation trigger schedule event 自动化 触发器 定时 事件", bolt, Screen.SettingTriggers),
        SettingSearchEntry(stringResource(R.string.setting_page_web_server), stringResource(R.string.setting_page_web_server_desc), "web api http server 网页 接口 服务端", dns, Screen.SettingWeb),
        SettingSearchEntry(stringResource(R.string.setting_page_data_backup), stringResource(R.string.setting_page_data_backup_desc), "backup restore export import 备份 恢复 导出 导入", database, Screen.Backup),
        SettingSearchEntry(stringResource(R.string.setting_page_chat_storage), "", "storage files media 存储 文件 媒体", database, Screen.SettingFiles),
        SettingSearchEntry(stringResource(R.string.setting_page_memory), stringResource(R.string.setting_page_memory_desc), "memory context 记忆 上下文", memory, Screen.SettingMemory),
        SettingSearchEntry(stringResource(R.string.setting_page_about), "", "about version info 关于 版本 信息", celebration, Screen.SettingAbout),
        SettingSearchEntry(stringResource(R.string.setting_page_stats), stringResource(R.string.setting_page_stats_desc), "statistics usage chart 统计 使用情况 图表", barChart, Screen.Stats),
        SettingSearchEntry(stringResource(R.string.setting_page_request_logs), stringResource(R.string.setting_page_request_logs_desc), "logs request network 日志 请求 网络", shelves, Screen.Log),
        SettingSearchEntry(stringResource(R.string.setting_page_donate), stringResource(R.string.setting_page_donate_desc), "donate support 捐赠 支持", favorite, Screen.SettingDonate),
    )
    return remember(entries) { entries.distinctBy { it.destination.toString() } }
}