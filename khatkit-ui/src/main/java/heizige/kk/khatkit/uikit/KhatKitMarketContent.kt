package heizige.kk.khatkit.uikit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.overlays.KedgeProgressIndicatorType
import kotlinx.coroutines.launch

/**
 * KhatKit 卡片市场正文（语义召回 + 安装/更新/卸载 + 密钥/设置入口）。
 *
 * 只面向 [KhatKitController]，不依赖 app；外层的 Scaffold / 顶栏 / Toaster
 * 由宿主提供，进入前请用 [KhatKitTheme] 包裹。
 */
@Composable
fun KhatKitMarketContent(
    controller: KhatKitController,
    onToast: (String, Boolean) -> Unit,
    onStyleChanged: (KhatKitUiStyle) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var cards by remember { mutableStateOf<List<CardIndexEntry>>(emptyList()) }
    var installed by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showSettings by remember { mutableStateOf(false) }
    var secretCard by remember { mutableStateOf<String?>(null) }

    fun refresh(search: String) {
        scope.launch {
            loading = true
            cards = controller.searchCards(search, limit = 50)
            installed = controller.installedCardVersions()
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh("") }

    if (showSettings) {
        KhatKitSettingsDialog(
            controller = controller,
            onDismiss = { showSettings = false },
            onApplied = {
                showSettings = false
                onStyleChanged(controller.uiStyle)
                refresh(query)
            },
            onToast = onToast,
        )
    }

    secretCard?.let { cardName ->
        KhatKitSecretsDialog(
            cardName = cardName,
            controller = controller,
            onDismiss = { secretCard = null },
            onToast = onToast,
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KedgeOutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.khatkit_market_search_hint),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            KedgeTextButton(onClick = { refresh(query) }) { Text(stringResource(R.string.khatkit_market_search)) }
            KedgeTextButton(onClick = { showSettings = true }) { Text(stringResource(R.string.khatkit_market_settings)) }
        }

        if (loading) {
            KedgeProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                type = KedgeProgressIndicatorType.Linear,
            )
        }
        if (!loading && cards.isEmpty()) {
            Text(
                text = stringResource(R.string.khatkit_market_empty),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(cards, key = { it.name }) { entry ->
                val installedToast = stringResource(R.string.khatkit_market_installed, entry.name)
                val installFailedToast = stringResource(R.string.khatkit_market_install_failed)
                val updatedToast = stringResource(R.string.khatkit_market_updated, entry.name)
                val updateFailedToast = stringResource(R.string.khatkit_market_update_failed)
                val uninstalledToast = stringResource(R.string.khatkit_market_uninstalled, entry.name)
                val uninstallFailedToast = stringResource(R.string.khatkit_market_uninstall_failed)

                KhatKitCardItem(
                    entry = entry,
                    installedVersion = installed[entry.name],
                    onInstall = {
                        scope.launch {
                            val ok = controller.installCard(entry)
                            onToast(if (ok) installedToast else installFailedToast, !ok)
                            installed = controller.installedCardVersions()
                        }
                    },
                    onUpdate = {
                        scope.launch {
                            val ok = controller.installCard(entry, force = true)
                            onToast(if (ok) updatedToast else updateFailedToast, !ok)
                            installed = controller.installedCardVersions()
                        }
                    },
                    onUninstall = {
                        scope.launch {
                            val ok = controller.uninstallCard(entry.name)
                            onToast(if (ok) uninstalledToast else uninstallFailedToast, !ok)
                            installed = controller.installedCardVersions()
                        }
                    },
                    onSecrets = { secretCard = entry.name },
                )
            }
        }
    }
}

@Composable
private fun KhatKitCardItem(
    entry: CardIndexEntry,
    installedVersion: String?,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onSecrets: () -> Unit,
) {
    KedgeCard(modifier = Modifier.fillMaxWidth()) {
        Text(entry.name, style = MaterialTheme.typography.titleMedium)

        val description = entry.summary.ifBlank { entry.description }
        if (description.isNotBlank()) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = "v${entry.version} · ${entry.engine} · ${entry.privilege} · " +
                entry.bridges.joinToString(", ").ifBlank { "L0" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            if (installedVersion != null) {
                Text(
                    text = stringResource(R.string.khatkit_market_installed_version, installedVersion),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (installedVersion != entry.version) {
                    KedgeTextButton(onClick = onUpdate) { Text(stringResource(R.string.khatkit_market_update)) }
                }
                KedgeTextButton(onClick = onSecrets) { Text(stringResource(R.string.khatkit_market_secrets)) }
                KedgeTextButton(onClick = onUninstall) { Text(stringResource(R.string.khatkit_market_uninstall)) }
            } else {
                KedgeTextButton(onClick = onInstall) { Text(stringResource(R.string.khatkit_market_install)) }
            }
        }
    }
}
