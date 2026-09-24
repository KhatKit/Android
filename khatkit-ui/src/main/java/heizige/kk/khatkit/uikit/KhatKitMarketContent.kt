package heizige.kk.khatkit.uikit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.HubCardMarketInfo
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeCheckbox
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeDialog
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.overlays.KedgeProgressIndicatorType
import kotlinx.coroutines.launch

/**
 * KhatKit 卡片市场正文（语义召回 + 安装/更新/卸载 + 密钥/设置入口）。
 *
 * Hub 账户已激活时额外提供：价格/协议/优选徽标（GET /api/cards）、
 * 「发布到 Hub」「提交改进（fork）」「投票」入口；未激活或后端不可用时只读降级。
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
    var triggers by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var showSettings by remember { mutableStateOf(false) }
    var secretCard by remember { mutableStateOf<String?>(null) }
    var activated by remember { mutableStateOf(false) }
    var hubInfo by remember { mutableStateOf<Map<String, HubCardMarketInfo>>(emptyMap()) }
    var publishTarget by remember { mutableStateOf<String?>(null) }
    var forkTarget by remember { mutableStateOf<String?>(null) }

    suspend fun reloadInstalled() {
        installed = controller.installedCardVersions()
        triggers = controller.installedCardTriggers()
    }

    fun refresh(search: String) {
        scope.launch {
            loading = true
            cards = controller.searchCards(search, limit = 50)
            reloadInstalled()
            loading = false
        }
        // Hub 详情单独拉取，避免后端不可达时拖慢卡片列表展示
        scope.launch {
            activated = controller.hubAccountStatus(refresh = false).activated
            hubInfo = controller.hubMarketCards().associateBy { it.name }
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

    publishTarget?.let { name ->
        PublishCardDialog(
            cardName = name,
            controller = controller,
            onDismiss = { publishTarget = null },
            onToast = onToast,
        )
    }

    forkTarget?.let { name ->
        ForkCardDialog(
            cardName = name,
            parentVersion = installed[name] ?: hubInfo[name]?.version.orEmpty(),
            controller = controller,
            onDismiss = { forkTarget = null },
            onToast = onToast,
        )
    }

    val localOnly = installed.keys.filterNot { name -> cards.any { it.name == name } }

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
                val runDoneToast = stringResource(R.string.khatkit_market_run_done, entry.name)
                val runFailedToast = stringResource(R.string.khatkit_market_run_failed)
                val needActivationToast = stringResource(R.string.khatkit_market_need_activation)
                val voteDoneToast = stringResource(R.string.khatkit_market_vote_done, entry.name)
                val voteFailedToast = stringResource(R.string.khatkit_market_vote_failed)

                KhatKitCardItem(
                    entry = entry,
                    installedVersion = installed[entry.name],
                    triggers = triggers[entry.name] ?: entry.triggers,
                    hubInfo = hubInfo[entry.name],
                    activated = activated,
                    onRun = {
                        scope.launch {
                            val result = controller.runCard(entry.name)
                            if (result.ok) {
                                onToast(runDoneToast, false)
                            } else {
                                onToast(result.message.ifBlank { runFailedToast }, true)
                            }
                        }
                    },
                    onInstall = {
                        scope.launch {
                            val ok = controller.installCard(entry)
                            onToast(if (ok) installedToast else installFailedToast, !ok)
                            reloadInstalled()
                        }
                    },
                    onUpdate = {
                        scope.launch {
                            val ok = controller.installCard(entry, force = true)
                            onToast(if (ok) updatedToast else updateFailedToast, !ok)
                            reloadInstalled()
                        }
                    },
                    onUninstall = {
                        scope.launch {
                            val ok = controller.uninstallCard(entry.name)
                            onToast(if (ok) uninstalledToast else uninstallFailedToast, !ok)
                            reloadInstalled()
                        }
                    },
                    onSecrets = { secretCard = entry.name },
                    onPublish = {
                        if (activated) publishTarget = entry.name else onToast(needActivationToast, true)
                    },
                    onFork = {
                        if (activated) forkTarget = entry.name else onToast(needActivationToast, true)
                    },
                    onVote = {
                        if (!activated) {
                            onToast(needActivationToast, true)
                        } else {
                            scope.launch {
                                val result = controller.voteCard(entry.name)
                                onToast(
                                    result.message.ifBlank {
                                        if (result.ok) voteDoneToast else voteFailedToast
                                    },
                                    !result.ok,
                                )
                            }
                        }
                    },
                )
            }

            if (localOnly.isNotEmpty()) {
                item(key = "__local_only_title") {
                    Text(
                        text = stringResource(R.string.khatkit_market_local_only_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(localOnly, key = { "local__$it" }) { name ->
                    val version = installed[name].orEmpty()
                    val runDoneToast = stringResource(R.string.khatkit_market_run_done, name)
                    val runFailedToast = stringResource(R.string.khatkit_market_run_failed)
                    val needActivationToast = stringResource(R.string.khatkit_market_need_activation)
                    LocalOnlyCardItem(
                        name = name,
                        version = version,
                        triggers = triggers[name].orEmpty(),
                        onRun = {
                            scope.launch {
                                val result = controller.runCard(name)
                                onToast(
                                    if (result.ok) runDoneToast
                                    else result.message.ifBlank { runFailedToast },
                                    !result.ok,
                                )
                            }
                        },
                        onPublish = {
                            if (activated) publishTarget = name else onToast(needActivationToast, true)
                        },
                        onFork = {
                            if (activated) forkTarget = name else onToast(needActivationToast, true)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KhatKitCardItem(
    entry: CardIndexEntry,
    installedVersion: String?,
    triggers: List<String>,
    hubInfo: HubCardMarketInfo?,
    activated: Boolean,
    onRun: () -> Unit,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onSecrets: () -> Unit,
    onPublish: () -> Unit,
    onFork: () -> Unit,
    onVote: () -> Unit,
) {
    val displayTriggers = triggers.filter { it in CardManifest.ALL_TRIGGERS }

    KedgeCard(modifier = Modifier.fillMaxWidth()) {
        Text(entry.name, style = MaterialTheme.typography.titleMedium)

        if (displayTriggers.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                displayTriggers.forEach { trigger -> TriggerBadge(trigger) }
            }
        }

        hubInfo?.let { HubInfoBadges(it) }

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
                if (CardManifest.TRIGGER_USER in triggers) {
                    KedgeTextButton(onClick = onRun) { Text(stringResource(R.string.khatkit_market_run)) }
                }
                if (installedVersion != entry.version) {
                    KedgeTextButton(onClick = onUpdate) { Text(stringResource(R.string.khatkit_market_update)) }
                }
                KedgeTextButton(onClick = onSecrets) { Text(stringResource(R.string.khatkit_market_secrets)) }
                KedgeTextButton(onClick = onUninstall) { Text(stringResource(R.string.khatkit_market_uninstall)) }
            } else {
                KedgeTextButton(onClick = onInstall) { Text(stringResource(R.string.khatkit_market_install)) }
            }
        }

        if (installedVersion != null || hubInfo != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (installedVersion != null) {
                    KedgeTextButton(onClick = onPublish) { Text(stringResource(R.string.khatkit_market_publish)) }
                }
                if (installedVersion != null) {
                    KedgeTextButton(onClick = onFork) { Text(stringResource(R.string.khatkit_market_fork)) }
                }
                if (hubInfo != null) {
                    KedgeTextButton(onClick = onVote) { Text(stringResource(R.string.khatkit_market_vote)) }
                }
            }
        }
    }
}

/** 本机已安装但不在 Hub 索引里的卡片（生成的对话卡片/本地卡片），提供运行与发布入口。 */
@Composable
private fun LocalOnlyCardItem(
    name: String,
    version: String,
    triggers: List<String>,
    onRun: () -> Unit,
    onPublish: () -> Unit,
    onFork: () -> Unit,
) {
    val displayTriggers = triggers.filter { it in CardManifest.ALL_TRIGGERS }
    KedgeCard(modifier = Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        if (displayTriggers.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                displayTriggers.forEach { trigger -> TriggerBadge(trigger) }
            }
        }
        Text(
            text = "v${version.ifBlank { "?" }} · " +
                stringResource(R.string.khatkit_market_local_only_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            if (CardManifest.TRIGGER_USER in triggers) {
                KedgeTextButton(onClick = onRun) { Text(stringResource(R.string.khatkit_market_run)) }
            }
            KedgeTextButton(onClick = onPublish) { Text(stringResource(R.string.khatkit_market_publish)) }
            KedgeTextButton(onClick = onFork) { Text(stringResource(R.string.khatkit_market_fork)) }
        }
    }
}

/** Hub 详情徽标：价格 / 协议 / 优选 / 票数。 */
@Composable
private fun HubInfoBadges(info: HubCardMarketInfo) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MarketBadge(
            text = if (info.priceCents <= 0) {
                stringResource(R.string.khatkit_market_price_free)
            } else {
                stringResource(R.string.khatkit_market_price_per_call, formatYuan(info.priceCents))
            },
            emphasized = info.priceCents > 0,
        )
        if (info.license.isNotBlank()) {
            MarketBadge(text = info.license, emphasized = false)
        }
        if (info.preferred) {
            MarketBadge(text = stringResource(R.string.khatkit_market_preferred), emphasized = true)
        }
        if (info.votes > 0) {
            MarketBadge(text = stringResource(R.string.khatkit_market_votes, info.votes), emphasized = false)
        }
    }
}

@Composable
private fun MarketBadge(text: String, emphasized: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (emphasized) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        modifier = Modifier
            .background(
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 发布到 Hub：确认开源协议 + 每次调用价格 + 更新说明。 */
@Composable
private fun PublishCardDialog(
    cardName: String,
    controller: KhatKitController,
    onDismiss: () -> Unit,
    onToast: (String, Boolean) -> Unit,
) {
    var license by remember(cardName) { mutableStateOf("MIT") }
    var priceText by remember(cardName) { mutableStateOf("0") }
    var changelog by remember(cardName) { mutableStateOf("") }
    var confirmed by remember(cardName) { mutableStateOf(false) }
    var submitting by remember(cardName) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val confirmError = stringResource(R.string.khatkit_market_publish_need_license_confirm)
    val invalidPrice = stringResource(R.string.khatkit_market_publish_invalid_price)
    val failedFallback = stringResource(R.string.khatkit_market_publish_failed)

    KedgeDialog(
        show = true,
        onDismissRequest = { if (!submitting) onDismiss() },
        title = stringResource(R.string.khatkit_market_publish_title, cardName),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KedgeOutlinedTextField(
                value = license,
                onValueChange = { license = it },
                label = stringResource(R.string.khatkit_market_license_label),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            KedgeOutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = stringResource(R.string.khatkit_market_publish_price),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            KedgeOutlinedTextField(
                value = changelog,
                onValueChange = { changelog = it },
                label = stringResource(R.string.khatkit_market_publish_changelog),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                KedgeCheckbox(checked = confirmed, onCheckedChange = { confirmed = it })
                Text(
                    text = stringResource(R.string.khatkit_market_publish_license_confirm),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                KedgeTextButton(onClick = { if (!submitting) onDismiss() }) {
                    Text(stringResource(R.string.khatkit_action_cancel))
                }
                KedgeButton(
                    onClick = {
                        val price = priceText.trim().toDoubleOrNull()
                        when {
                            !confirmed -> onToast(confirmError, true)
                            price == null || price < 0 -> onToast(invalidPrice, true)
                            else -> {
                                submitting = true
                                scope.launch {
                                    val result = controller.publishCard(
                                        cardName,
                                        license.trim(),
                                        price,
                                        changelog.trim(),
                                    )
                                    submitting = false
                                    onToast(result.message.ifBlank { failedFallback }, !result.ok)
                                    if (result.ok) onDismiss()
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.khatkit_market_publish_action))
                }
            }
            if (submitting) {
                KedgeProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    type = KedgeProgressIndicatorType.Linear,
                )
            }
        }
    }
}

/** 提交改进（fork 上传）：父版本 + 更新说明必填。 */
@Composable
private fun ForkCardDialog(
    cardName: String,
    parentVersion: String,
    controller: KhatKitController,
    onDismiss: () -> Unit,
    onToast: (String, Boolean) -> Unit,
) {
    var parent by remember(cardName) { mutableStateOf(parentVersion) }
    var newVersion by remember(cardName) { mutableStateOf(defaultNextVersion(parentVersion)) }
    var changelog by remember(cardName) { mutableStateOf("") }
    var submitting by remember(cardName) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val needChangelog = stringResource(R.string.khatkit_market_fork_need_changelog)
    val failedFallback = stringResource(R.string.khatkit_market_fork_failed)

    KedgeDialog(
        show = true,
        onDismissRequest = { if (!submitting) onDismiss() },
        title = stringResource(R.string.khatkit_market_fork_title, cardName),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KedgeOutlinedTextField(
                value = parent,
                onValueChange = { parent = it },
                label = stringResource(R.string.khatkit_market_fork_parent),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            KedgeOutlinedTextField(
                value = newVersion,
                onValueChange = { newVersion = it },
                label = stringResource(R.string.khatkit_market_fork_version),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            KedgeOutlinedTextField(
                value = changelog,
                onValueChange = { changelog = it },
                label = stringResource(R.string.khatkit_market_fork_changelog),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                KedgeTextButton(onClick = { if (!submitting) onDismiss() }) {
                    Text(stringResource(R.string.khatkit_action_cancel))
                }
                KedgeButton(
                    onClick = {
                        if (changelog.isBlank()) {
                            onToast(needChangelog, true)
                        } else {
                            submitting = true
                            scope.launch {
                                val result = controller.forkCard(
                                    cardName,
                                    parent.trim().ifBlank { parentVersion },
                                    changelog.trim(),
                                    newVersion.trim(),
                                )
                                submitting = false
                                onToast(result.message.ifBlank { failedFallback }, !result.ok)
                                if (result.ok) onDismiss()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.khatkit_market_fork_action))
                }
            }
            if (submitting) {
                KedgeProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    type = KedgeProgressIndicatorType.Linear,
                )
            }
        }
    }
}

/** fork 默认新版本号：补丁位 +1（1.2.3 -> 1.2.4），解析失败追加 .1。 */
private fun defaultNextVersion(parent: String): String {
    val parts = parent.trim().split('.')
    if (parts.size >= 3) {
        val patch = parts[2].toIntOrNull()
        if (patch != null) return "${parts[0]}.${parts[1]}.${patch + 1}"
    }
    return if (parent.isBlank()) "1.0.0" else "$parent.1"
}

/** 分转元展示：123 -> 1.23，120 -> 1.2。 */
private fun formatYuan(cents: Long): String {
    val yuan = cents / 100.0
    return if (cents % 100 == 0L) {
        yuan.toLong().toString()
    } else {
        "%.2f".format(yuan).trimEnd('0').trimEnd('.')
    }
}

@Composable
private fun TriggerBadge(trigger: String) {
    val label = when (trigger) {
        CardManifest.TRIGGER_AI -> stringResource(R.string.khatkit_trigger_ai)
        else -> stringResource(R.string.khatkit_trigger_user)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
