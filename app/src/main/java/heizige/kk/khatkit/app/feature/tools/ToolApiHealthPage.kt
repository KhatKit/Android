package heizige.kk.khatkit.app.feature.tools

import heizige.kk.khatkit.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.LocalKkNavController
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.feature.tools.data.ToolApiStatus
import heizige.kk.khatkit.app.feature.tools.data.apiHost
import heizige.kk.khatkit.card.CardStrings
import java.text.DateFormat
import java.util.Date
import ui.feice.mochi.basic.MochiActivityIndicator
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme
import heizige.kk.khatkit.uikit.mochi.kkBarButtonColors

private data class ApiCard(val name: String, val title: String, val hosts: List<String>, val canOpen: Boolean)

@Composable
internal fun ToolApiHealthPage() {
    val provider = rememberAppEntryPoint().khatKitToolProvider()
    val nav = LocalKkNavController.current
    val observations by provider.apiHealth.entries.collectAsStateWithLifecycle()
    val revision by provider.cardCatalogueRevision.collectAsStateWithLifecycle()
    var cards by remember { mutableStateOf<List<ApiCard>?>(null) }
    var onlyIssues by remember { mutableStateOf(false) }
    LaunchedEffect(provider, revision) {
        cards = provider.chatCards().mapNotNull { card ->
            val manifest = card.manifest
            val declared = manifest.network.allow
            if (declared.isEmpty()) null else ApiCard(
                manifest.name, CardStrings(manifest).name, declared.mapNotNull(::apiHost).distinct().sorted(),
                manifest.supportsUser() && manifest.widget != null,
            )
        }
    }
    val groups = remember(cards, observations, onlyIssues) {
        val byCard = observations.groupBy { it.card }
        cards.orEmpty().mapNotNull { card ->
            val statuses = byCard[card.name].orEmpty()
            val rows = if (onlyIssues) statuses.filter { it.issue } else statuses
            if (onlyIssues && rows.isEmpty()) null else card to rows
        }.sortedWith(compareByDescending<Pair<ApiCard, List<ToolApiStatus>>> { (_, rows) -> rows.any { it.issue } }
            .thenBy { it.first.title })
    }
    MochiSettingsPageScaffold(
        title = stringResource(R.string.tools_api_health_title),
        actions = {
            if (observations.isNotEmpty()) {
                MochiBarIconButton(
                    onClick = { provider.apiHealth.clear() },
                    navId = "tools.api.clear",
                    colors = kkBarButtonColors(),
                ) { MochiIonicon("trash", contentDescription = stringResource(R.string.tools_clear_request_status)) }
            }
        },
    ) { pagePadding ->
        LazyColumn(Modifier.fillMaxSize().padding(pagePadding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                MochiChipGroup {
                    MochiChip(stringResource(R.string.tools_scope_all), !onlyIssues, { onlyIssues = false })
                    MochiChip(stringResource(R.string.tools_filter_issues), onlyIssues, { onlyIssues = true })
                }
            }
            if (cards == null) item { MochiActivityIndicator() }
            else if (groups.isEmpty()) item {
                Text(if (onlyIssues) stringResource(R.string.tools_empty_no_issues) else stringResource(R.string.tools_empty_no_network_cards),
                    color = MochiTheme.colorScheme.onSurfaceVariant)
            }
            items(groups, key = { it.first.name }) { (card, rows) ->
                MochiListSection {
                    MochiListRow(
                        title = card.title,
                        subtitle = pluralStringResource(R.plurals.tools_request_status_count, rows.size, rows.size),
                        onClick = if (card.canOpen) ({ nav?.navigate(Screen.CardWidget(card.name, card.title)) }) else null,
                    )
                    for (entry in rows) {
                        val label = when (entry.status) {
                            null -> stringResource(R.string.tools_connection_failed)
                            in 200..299 -> stringResource(R.string.tools_response_ok, entry.status)
                            in 300..399 -> stringResource(R.string.tools_response_redirect, entry.status)
                            else -> "HTTP ${entry.status}"
                        }
                        val observedAt = remember(entry.timeMillis) { DateFormat.getDateTimeInstance().format(Date(entry.timeMillis)) }
                        MochiListRow(title = "${entry.method} ${entry.host}", subtitle = "$label · $observedAt",
                            leading = { MochiIonicon(if (entry.issue) "warning" else "checkmark-circle") })
                    }
                    if (!onlyIssues) {
                        val unchecked = card.hosts.filter { host -> rows.none { it.host == host } }
                        for (host in unchecked) MochiListRow(title = host, subtitle = stringResource(R.string.tools_no_request_observed))
                        if (rows.isEmpty() && unchecked.isEmpty()) {
                            MochiListRow(title = stringResource(R.string.tools_no_request_observed), subtitle = stringResource(R.string.tools_no_domain_limit))
                        }
                    }
                }
            }
        }
    }
}
