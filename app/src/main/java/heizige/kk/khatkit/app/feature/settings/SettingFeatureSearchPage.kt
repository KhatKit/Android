package heizige.kk.khatkit.app.feature.settings

import heizige.kk.khatkit.app.feature.settings.data.SettingLeafEntries
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
import heizige.kk.khatkit.app.core.ui.icons.ai.psychology
import heizige.kk.khatkit.app.core.ui.icons.utility.search
import heizige.kk.khatkit.app.core.ui.icons.settings.settings
import heizige.kk.khatkit.app.core.ui.icons.files.shelves
import heizige.kk.khatkit.app.core.ui.icons.utility.travelExplore
import heizige.kk.khatkit.app.core.ui.icons.utility.verifiedUser
import heizige.kk.khatkit.app.core.util.plus
import ui.feice.mochi.basic.MochiSearchBar
import heizige.kk.khatkit.app.feature.settings.data.SettingSearchEntry
import heizige.kk.khatkit.app.core.data.model.Assistant



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
        SettingSearchEntry(stringResource(R.string.setting_page_preferences_ui), stringResource(R.string.setting_page_preferences_ui_desc), stringResource(R.string.setting_page_preferences_ui_keywords), palette, Screen.SettingPreferencesUI),
        SettingSearchEntry(stringResource(R.string.setting_page_preferences_notification), stringResource(R.string.setting_page_preferences_notification_desc), stringResource(R.string.setting_page_preferences_notification_keywords), notifications, Screen.SettingPreferencesNotification),
        SettingSearchEntry(stringResource(R.string.setting_page_preferences_general), stringResource(R.string.setting_page_preferences_general_desc), stringResource(R.string.setting_page_preferences_general_keywords), settings, Screen.SettingPreferencesGeneral),
        SettingSearchEntry(stringResource(R.string.setting_page_preferences_network), stringResource(R.string.setting_page_preferences_network_desc), stringResource(R.string.setting_page_preferences_network_keywords), language, Screen.SettingPreferencesNetwork),
        SettingSearchEntry(stringResource(R.string.setting_page_permissions), stringResource(R.string.setting_page_permissions_desc), stringResource(R.string.setting_page_permissions_keywords), verifiedUser, Screen.SettingPermissions),
        SettingSearchEntry(stringResource(R.string.setting_page_assistant), stringResource(R.string.setting_page_assistant_desc), stringResource(R.string.setting_page_assistant_keywords), search, Screen.Assistant),
        SettingSearchEntry(stringResource(R.string.setting_page_extensions), stringResource(R.string.setting_page_extensions_desc), stringResource(R.string.setting_page_extensions_keywords), inventory2, Screen.Extensions),
        SettingSearchEntry(stringResource(R.string.setting_page_default_model), stringResource(R.string.setting_page_default_model_desc), stringResource(R.string.setting_page_default_model_keywords), autoAwesome, Screen.SettingModels),
        SettingSearchEntry(stringResource(R.string.setting_page_providers), "", stringResource(R.string.setting_page_providers_keywords), psychology, Screen.SettingProvider),
        SettingSearchEntry(stringResource(R.string.setting_page_search_service), "", stringResource(R.string.setting_page_search_service_keywords), travelExplore, Screen.SettingSearch),
        SettingSearchEntry(stringResource(R.string.setting_page_tts_service), stringResource(R.string.setting_page_tts_service_desc), stringResource(R.string.setting_page_tts_service_keywords), campaign, Screen.SettingSpeech),
        SettingSearchEntry(stringResource(R.string.setting_page_mcp), "", stringResource(R.string.setting_page_mcp_keywords), dns, Screen.SettingMcp),
        SettingSearchEntry(stringResource(R.string.setting_page_trace_audit), stringResource(R.string.setting_page_trace_audit_desc), stringResource(R.string.setting_page_trace_audit_keywords), bolt, Screen.TraceAudit),
        SettingSearchEntry(stringResource(R.string.explore_market_title), stringResource(R.string.setting_market_desc), stringResource(R.string.setting_market_keywords), inventory2, Screen.ExploreMarket),
        SettingSearchEntry(stringResource(R.string.setting_triggers_title), stringResource(R.string.setting_triggers_desc), stringResource(R.string.setting_triggers_keywords), bolt, Screen.SettingTriggers),
        SettingSearchEntry(stringResource(R.string.setting_page_web_server), stringResource(R.string.setting_page_web_server_desc), stringResource(R.string.setting_page_web_server_keywords), dns, Screen.SettingWeb),
        SettingSearchEntry(stringResource(R.string.setting_page_data_backup), stringResource(R.string.setting_page_data_backup_desc), stringResource(R.string.setting_page_data_backup_keywords), database, Screen.Backup),
        SettingSearchEntry(stringResource(R.string.setting_page_chat_storage), "", stringResource(R.string.setting_page_chat_storage_keywords), database, Screen.SettingFiles),
        SettingSearchEntry(stringResource(R.string.setting_page_memory), stringResource(R.string.setting_page_memory_desc), stringResource(R.string.setting_page_memory_keywords), memory, Screen.SettingMemory),
        SettingSearchEntry(stringResource(R.string.setting_page_about), "", stringResource(R.string.setting_page_about_keywords), celebration, Screen.SettingAbout),
        SettingSearchEntry(stringResource(R.string.setting_page_stats), stringResource(R.string.setting_page_stats_desc), stringResource(R.string.setting_page_stats_keywords), barChart, Screen.Stats),
        SettingSearchEntry(stringResource(R.string.setting_page_request_logs), stringResource(R.string.setting_page_request_logs_desc), stringResource(R.string.setting_page_request_logs_keywords), shelves, Screen.Log),
        SettingSearchEntry(stringResource(R.string.setting_page_donate), stringResource(R.string.setting_page_donate_desc), stringResource(R.string.setting_page_donate_keywords), favorite, Screen.SettingDonate),
    )
    return remember(entries) { entries.distinctBy { it.destination.toString() } }
}
