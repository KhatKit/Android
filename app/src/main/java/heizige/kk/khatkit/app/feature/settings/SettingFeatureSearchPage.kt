package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.Screen
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
import heizige.kk.khatkit.uikit.mochi.Md3SearchBar
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import ui.feice.mochi.theme.MochiTheme

private data class SettingSearchEntry(
    val title: String,
    val description: String,
    val keywords: String,
    val icon: ImageVector,
    val destination: Screen,
)

@Composable
fun SettingFeatureSearchPage() {
    val navController = LocalNavController.current
    var query by remember { mutableStateOf("") }
    val entries = rememberSettingSearchEntries()
    val filteredEntries = remember(query, entries) {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) {
            entries
        } else {
            entries.filter { entry ->
                listOf(entry.title, entry.description, entry.keywords)
                    .joinToString(" ")
                    .lowercase()
                    .contains(normalized)
            }
        }
    }

    MochiSettingsPageScaffold(
        title = "Search settings",
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
                Md3SearchBar(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.padding(horizontal = 8.dp),
                    placeholder = "Search settings",
                    showResultsWhenEmpty = false,
                )
            }
            items(filteredEntries, key = { it.destination.toString() }) { entry ->
                MochiOptionItem(
                    onClick = { navController.navigate(entry.destination) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                    leadingContent = {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = null,
                            tint = MochiTheme.colorScheme.primary,
                        )
                    },
                    titleContent = { Text(entry.title, style = MochiTheme.textStyles.body) },
                    supportingContent = {
                        Text(
                            entry.description,
                            style = MochiTheme.textStyles.footnote,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    chevron = true,
                )
            }
        }
    }
}

@Composable
private fun rememberSettingSearchEntries(): List<SettingSearchEntry> = remember {
    listOf(
        SettingSearchEntry("Interface", "Theme, messages, fonts and code display", "ui appearance theme color font code 界面 外观 主题 颜色 字体 代码", palette, Screen.SettingPreferencesUI),
        SettingSearchEntry("Notifications", "Update alerts and reply notifications", "notification alert reply 通知 提醒 回复", notifications, Screen.SettingPreferencesNotification),
        SettingSearchEntry("General", "Interaction, scrolling and input", "general interaction scroll input 通用 交互 滚动 输入", settings, Screen.SettingPreferencesGeneral),
        SettingSearchEntry("Network", "Proxy, timeout and connection behavior", "network proxy timeout connection 网络 代理 超时 连接", language, Screen.SettingPreferencesNetwork),
        SettingSearchEntry("Permissions", "Notifications, location and accessibility", "permission location accessibility 权限 位置 无障碍", verifiedUser, Screen.SettingPermissions),
        SettingSearchEntry("Roles", "Role identity, model and duties", "assistant role character model 角色 助手 人设 模型", search, Screen.Assistant),
        SettingSearchEntry("Extensions", "Installed plugins and extensions", "extension plugin 扩展 插件", inventory2, Screen.Extensions),
        SettingSearchEntry("Models", "Default model and prompt selection", "model prompt default 模型 提示词 默认", autoAwesome, Screen.SettingModels),
        SettingSearchEntry("Providers", "AI provider accounts and API settings", "provider api account 服务商 提供商 账号 接口", psychology, Screen.SettingProvider),
        SettingSearchEntry("Search services", "Configure web search engines", "search engine web 搜索 搜索引擎 网络", travelExplore, Screen.SettingSearch),
        SettingSearchEntry("Speech", "Text-to-speech and speech recognition", "speech tts asr voice 语音 朗读 识别 声音", campaign, Screen.SettingSpeech),
        SettingSearchEntry("MCP", "Model Context Protocol servers", "mcp server tools 服务器 工具", dns, Screen.SettingMcp),
        SettingSearchEntry("Trace audit", "Inspect model requests and execution traces", "trace audit debug request 追踪 审计 调试 请求", bolt, Screen.TraceAudit),
        SettingSearchEntry("Explore market", "Browse tools, skills and scripts", "market tools skills cards 市场 工具 技能 卡片", inventory2, Screen.ExploreMarket),
        SettingSearchEntry("Subscription", "Plan activation, quota and gateway", "package plan quota gateway 套餐 计划 额度 网关", favorite, Screen.SettingPackage),
        SettingSearchEntry("Automation", "Scheduled and event-driven card runs", "automation trigger schedule event 自动化 触发器 定时 事件", bolt, Screen.SettingTriggers),
        SettingSearchEntry("Web server", "Local API and MCP server", "web api http server 网页 接口 服务端", dns, Screen.SettingWeb),
        SettingSearchEntry("Data backup", "Backup and restore application data", "backup restore export import 备份 恢复 导出 导入", database, Screen.Backup),
        SettingSearchEntry("Storage", "Manage local chat files", "storage files media 存储 文件 媒体", database, Screen.SettingFiles),
        SettingSearchEntry("Memory", "Manage memory per role", "memory context 记忆 上下文", memory, Screen.SettingMemory),
        SettingSearchEntry("About", "Version, links and project information", "about version info 关于 版本 信息", celebration, Screen.SettingAbout),
        SettingSearchEntry("Statistics", "Usage statistics and trends", "statistics usage chart 统计 使用情况 图表", barChart, Screen.Stats),
        SettingSearchEntry("Request logs", "Inspect network and model request logs", "logs request network 日志 请求 网络", shelves, Screen.Log),
        SettingSearchEntry("Donate", "Support project development", "donate support 捐赠 支持", favorite, Screen.SettingDonate),
    ).distinctBy { it.destination.toString() }
}
