package heizige.kk.khatkit.app.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.roleAvatarColor
import heizige.kk.khatkit.app.core.ui.components.ui.roleAvatarPalette
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.adsClick
import heizige.kk.khatkit.app.core.ui.icons.autoAwesome
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.dashboard
import heizige.kk.khatkit.app.core.ui.icons.deleteForever
import heizige.kk.khatkit.app.core.ui.icons.deployedCode
import heizige.kk.khatkit.app.core.ui.icons.editNote
import heizige.kk.khatkit.app.core.ui.icons.extension
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowUp
import heizige.kk.khatkit.app.core.ui.icons.upload
import heizige.kk.khatkit.app.core.ui.icons.verifiedUser
import heizige.kk.khatkit.app.core.ui.icons.vpnKey
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.feature.ui.shell.LocalKkBarBackdrop
import heizige.kk.khatkit.app.feature.ui.shell.MochiPageScaffold
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.HubCardMarketInfo
import heizige.kk.khatkit.uikit.KhatKitSecretsDialog
import heizige.kk.khatkit.uikit.mochi.Toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.abs
import ui.feice.mochi.basic.MochiActivityIndicator
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipDefaults
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiEmptyState
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.elastic.mochiPressDeform
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiScrollEdgeDefaults
import ui.feice.mochi.navigation.MochiTitleBar
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

/**
 * 卡片详情二级页（取代原先的半屏面板）。
 *
 * 版面照 Mochi 示例的二级页：分组底色上一列 16dp 边距的分区，首项是一张大卡片。
 * 这里的大卡片是卡片自己的海报（身份色渐变 + 身份形徽标，与轮播卡同一套视觉身份），
 * 名称、简介、统计和主操作都在卡上；往下依次是标签 → 信息 → 简介全文 → 原生依赖 → 管理 → 卸载。
 *
 * 条目来源：先查市场列表，查不到按名补查一次（[ExploreMarketViewModel.loadCard]），
 * 再查不到但本机装着，就按本机版本合成一条（没有下载地址，所以没有安装 / 更新按钮）。
 * 市场 VM 是 Activity 级的，与列表页共用一份：这里装 / 卸完，列表回来就是新状态。
 */
@Composable
fun ExploreCardDetailPage(name: String) {
    val navController = LocalNavController.current
    val vm = hiltViewModel<ExploreMarketViewModel>()
    val scope = rememberCoroutineScope()

    val cards by vm.cards.collectAsStateWithLifecycle()
    val focusCards by vm.focusCards.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val triggerMap by vm.triggers.collectAsStateWithLifecycle()
    val hubInfoMap by vm.hubInfo.collectAsStateWithLifecycle()
    val activated by vm.activated.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val busyCard by vm.busyCard.collectAsStateWithLifecycle()

    LaunchedEffect(name, loading) { if (!loading) vm.loadCard(name) }

    val entry = remember(name, cards, focusCards, installed) {
        cards.firstOrNull { it.name == name }
            ?: focusCards[name]
            ?: installed[name]?.let { version -> CardIndexEntry(name = name, version = version, url = "") }
    }

    // 协程里的 Toast 取不到 stringResource：模板串在组合期取出，回调里只 format。
    val toastUninstalledNamed = stringResource(R.string.explore_market_toast_uninstalled_named)
    val toastUninstallFailed = stringResource(R.string.explore_market_toast_uninstall_failed)
    val toastInstalledNamed = stringResource(R.string.explore_market_toast_installed_named)
    val toastInstallFailed = stringResource(R.string.explore_market_toast_install_failed)
    val toastUpdatedNamed = stringResource(R.string.explore_market_toast_updated_named)
    val toastUpdateFailed = stringResource(R.string.explore_market_toast_update_failed)
    val toastPublishSubmitted = stringResource(R.string.explore_market_toast_publish_submitted)
    val toastPublishFailed = stringResource(R.string.explore_market_toast_publish_failed)
    val toastForkSubmitted = stringResource(R.string.explore_market_toast_fork_submitted)
    val toastForkFailed = stringResource(R.string.explore_market_toast_fork_failed)
    val toastRanNamed = stringResource(R.string.explore_market_toast_ran_named)
    val toastRunFailed = stringResource(R.string.explore_market_toast_run_failed)
    val toastVotedNamed = stringResource(R.string.explore_market_toast_voted_named)
    val toastVoteFailed = stringResource(R.string.explore_market_toast_vote_failed)
    val toastAccountRequired = stringResource(R.string.explore_market_toast_account_required)

    var showSecrets by remember { mutableStateOf(false) }
    var showPublish by remember { mutableStateOf(false) }
    var showFork by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    MochiPageScaffold(
        topBar = {
            MochiTitleBar(
                title = name,
                backdrop = LocalKkBarBackdrop.current,
                navigationIcon = { BackButton() },
                // 正文首项就是带大标题的海报卡：栏里的标题等它滚到栏下面才渐显。
                titleProgress = MochiScrollEdgeDefaults.progress(listState),
            )
        },
        containerColor = MochiDefaults.groupedBackground(),
    ) { innerPadding ->
        if (entry == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                if (loading || name !in focusCards) {
                    MochiActivityIndicator()
                } else {
                    MochiEmptyState(
                        title = stringResource(R.string.explore_market_detail_not_found_title),
                        message = stringResource(R.string.explore_market_detail_not_found_message),
                    )
                }
            }
            return@MochiPageScaffold
        }

        val installedVersion = installed[entry.name]
        val triggers = (triggerMap[entry.name] ?: entry.triggers).filter { it in CardManifest.ALL_TRIGGERS }
        val hubInfo = hubInfoMap[entry.name]
        val busy = busyCard == entry.name
        val searchTag: (String) -> Unit = { tag -> navController.navigate(Screen.ExploreCards(tag)) }

        val onInstall: () -> Unit = {
            vm.install(entry) { ok ->
                scope.launch { Toast.show(if (ok) toastInstalledNamed.format(entry.name) else toastInstallFailed, !ok) }
            }
        }
        val onUpdate: () -> Unit = {
            vm.update(entry) { ok ->
                scope.launch { Toast.show(if (ok) toastUpdatedNamed.format(entry.name) else toastUpdateFailed, !ok) }
            }
        }
        val onRun: () -> Unit = {
            vm.run(entry.name) { r ->
                scope.launch {
                    Toast.show(r.message.ifBlank { if (r.ok) toastRanNamed.format(entry.name) else toastRunFailed }, !r.ok)
                }
            }
        }
        val onVote: () -> Unit = {
            if (!activated) {
                Toast.show(toastAccountRequired, true)
            } else {
                vm.vote(entry.name) { r ->
                    scope.launch {
                        Toast.show(r.message.ifBlank { if (r.ok) toastVotedNamed.format(entry.name) else toastVoteFailed }, !r.ok)
                    }
                }
            }
        }

        val tagGroups = detailTagGroups(entry, triggers, hubInfo)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
        ) {
            item(key = "hero") {
                CardDetailHero(
                    entry = entry,
                    installedVersion = installedVersion,
                    triggers = triggers,
                    hubInfo = hubInfo,
                    busy = busy,
                    onInstall = onInstall,
                    onUpdate = onUpdate,
                    onRun = onRun,
                )
            }

            if (tagGroups.isNotEmpty()) {
                item(key = "tags") { CardDetailTags(tagGroups, onTagClick = searchTag) }
            }

            item(key = "info") { CardDetailInfo(entry, installedVersion) }

            // 海报卡上已经有摘要；全文只在和摘要不同的时候单独成段，免得同一段话读两遍。
            if (entry.description.isNotBlank() && entry.description != entry.summary) {
                item(key = "about") {
                    MochiListSection(header = stringResource(R.string.explore_market_detail_section_about)) {
                        Text(
                            text = entry.description,
                            style = MochiTheme.textStyles.body,
                            color = MochiTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            if (entry.dependencies.isNotEmpty()) {
                item(key = "dependencies") {
                    MochiListSection(header = stringResource(R.string.explore_market_section_dependencies)) {
                        entry.dependencies.forEach { dependency ->
                            MochiListRow(
                                title = dependency.name,
                                value = dependency.version,
                                leading = { RowIcon(deployedCode) },
                            )
                        }
                    }
                }
            }

            val canManage = installedVersion != null
            val canContribute = installedVersion != null && activated
            if (canManage || hubInfo != null) {
                item(key = "manage") {
                    MochiListSection(header = stringResource(R.string.explore_market_detail_section_manage)) {
                        if (canManage) {
                            MochiListRow(
                                title = stringResource(R.string.explore_market_secrets),
                                leading = { RowIcon(vpnKey) },
                                chevron = true,
                                onClick = { showSecrets = true },
                            )
                        }
                        if (canContribute) {
                            MochiListRow(
                                title = stringResource(R.string.explore_market_publish_to_hub),
                                leading = { RowIcon(upload) },
                                chevron = true,
                                onClick = { showPublish = true },
                            )
                            MochiListRow(
                                title = stringResource(R.string.explore_market_submit_revision),
                                leading = { RowIcon(editNote) },
                                chevron = true,
                                onClick = { showFork = true },
                            )
                        }
                        if (hubInfo != null) {
                            MochiListRow(
                                title = stringResource(
                                    if (activated) R.string.explore_market_vote else R.string.explore_market_vote_needs_activation,
                                ),
                                value = hubInfo.votes.takeIf { it > 0 }?.toString(),
                                leading = { RowIcon(keyboardArrowUp) },
                                enabled = !busy,
                                onClick = onVote,
                            )
                        }
                    }
                }
            }

            // 卸载单独一组放在最底下（iOS 设置的危险操作位），点了先确认。
            if (canManage) {
                item(key = "uninstall") {
                    MochiListSection {
                        MochiListRow(
                            title = stringResource(R.string.explore_market_uninstall),
                            leading = { RowIcon(deleteForever, tint = MochiTheme.colorScheme.error) },
                            destructive = true,
                            enabled = !busy,
                            onClick = { confirmUninstall = true },
                        )
                    }
                }
            }
        }

        if (confirmUninstall && installedVersion != null) {
            MochiDialog(
                visible = true,
                onDismissRequest = { confirmUninstall = false },
                title = stringResource(R.string.explore_market_uninstall_title, entry.name),
                message = stringResource(R.string.explore_market_uninstall_message, entry.name, installedVersion),
                actions = persistentListOf(
                    MochiDialogAction(
                        label = stringResource(R.string.explore_market_cancel),
                        role = MochiDialogActionRole.Cancel,
                    ),
                    MochiDialogAction(
                        label = stringResource(R.string.explore_market_uninstall),
                        role = MochiDialogActionRole.Destructive,
                        onClick = {
                            confirmUninstall = false
                            vm.uninstall(entry.name) { ok ->
                                scope.launch {
                                    Toast.show(if (ok) toastUninstalledNamed.format(entry.name) else toastUninstallFailed, !ok)
                                    // 只在本机存在的卡（没有下载地址）卸完就没东西可看了，退回上一页。
                                    if (ok && entry.url.isBlank()) navController.popBackStack()
                                }
                            }
                        },
                    ),
                ),
            )
        }

        if (showSecrets) {
            val provider = rememberAppEntryPoint().khatKitToolProvider()
            MochiTheme {
                KhatKitSecretsDialog(
                    cardName = entry.name,
                    controller = provider,
                    onDismiss = { showSecrets = false },
                    onToast = { message, isError -> Toast.show(message = message, isError = isError) },
                )
            }
        }

        if (showPublish) {
            PublishCardDialog(
                entry = entry,
                onDismiss = { showPublish = false },
                onConfirm = { license, price, changelog ->
                    showPublish = false
                    vm.publish(entry.name, license, price, changelog) { result ->
                        scope.launch {
                            Toast.show(result.message.ifBlank { if (result.ok) toastPublishSubmitted else toastPublishFailed }, !result.ok)
                            vm.refresh("")
                        }
                    }
                },
            )
        }

        if (showFork) {
            ForkCardDialog(
                entry = entry,
                parentVersion = installedVersion ?: hubInfo?.version.orEmpty(),
                onDismiss = { showFork = false },
                onConfirm = { parent, newVersion, changelog ->
                    showFork = false
                    vm.fork(entry.name, parent, changelog, newVersion) { result ->
                        scope.launch {
                            Toast.show(result.message.ifBlank { if (result.ok) toastForkSubmitted else toastForkFailed }, !result.ok)
                            vm.refresh("")
                        }
                    }
                },
            )
        }
    }
}

/** 海报卡圆角：照 Mochi 二级页首卡的 32dp，比列表分区的圆角大一档。 */
private val HeroCorner = 32.dp

/**
 * 页首海报卡。
 *
 * 身份色、身份形与轮播卡同源（包名哈希），同一张卡从轮播点进来看到的是同一张「脸」。
 * 自上而下：徽标 + 版本胶囊 → 大标题 → 作者 → 摘要 → 统计条 → 主操作。
 * 主操作按状态给：没装 = 安装；有新版 = 更新（能手动运行时旁边再给一个运行）；装着最新版 = 运行。
 */
@Composable
private fun CardDetailHero(
    entry: CardIndexEntry,
    installedVersion: String?,
    triggers: List<String>,
    hubInfo: HubCardMarketInfo?,
    busy: Boolean,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onRun: () -> Unit,
) {
    val identityIndex = abs(entry.name.hashCode()) % roleAvatarPalette.size
    val poster = posterBase(roleAvatarColor(entry.name), MochiTheme.colorScheme)
    val posterFill = remember(poster) { posterGradient(poster) }
    val badgeShape = extensionShapes[identityIndex].toShape()
    val blobShape = extensionShapes[(identityIndex + extensionShapes.size / 2) % extensionShapes.size]
        .toShape(startAngle = 18)
    val onPoster = Color.White

    val installState = cardInstallState(installedVersion, entry.version)
    val canRun = installedVersion != null && CardManifest.TRIGGER_USER in triggers
    val canUpdate = installState == CardInstallState.UPDATE && entry.url.isNotBlank()
    val canInstall = installState == CardInstallState.INSTALL && entry.url.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MochiRoundedRectangle(HeroCorner))
            .background(posterFill),
    ) {
        // 两块低透明度白色有机形给平涂底色一点体积，色相永远跟着海报底色走。
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 72.dp, y = (-56).dp)
                .size(232.dp)
                .clip(blobShape)
                .background(onPoster.copy(alpha = 0.08f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-48).dp, y = 56.dp)
                .size(148.dp)
                .clip(badgeShape)
                .background(onPoster.copy(alpha = 0.05f)),
        )

        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(badgeShape)
                        .background(onPoster),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = extension,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = poster,
                    )
                }
                Spacer(Modifier.weight(1f))
                HeroPill(
                    text = if (installedVersion != null) {
                        stringResource(R.string.explore_market_installed_version_badge, installedVersion)
                    } else {
                        stringResource(R.string.explore_market_version_prefix, entry.version)
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = entry.name,
                style = MochiTheme.textStyles.largeTitle,
                color = onPoster,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val author = entry.author.ifBlank { hubInfo?.author.orEmpty() }
            if (author.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.explore_market_provided_by, author),
                    style = MochiTheme.textStyles.subhead,
                    color = onPoster.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val summary = entry.summary.ifBlank { hubInfo?.summary.orEmpty() }.ifBlank { entry.description }
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = summary,
                    style = MochiTheme.textStyles.body,
                    color = onPoster.copy(alpha = 0.88f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val stats = heroStats(entry, hubInfo)
            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                HeroStats(stats)
            }

            if (busy || canInstall || canUpdate || canRun) {
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        busy -> HeroButton(
                            text = stringResource(R.string.explore_market_processing),
                            poster = poster,
                            enabled = false,
                            busy = true,
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        )
                        canInstall -> HeroButton(
                            text = stringResource(R.string.explore_market_install_card),
                            poster = poster,
                            onClick = onInstall,
                            modifier = Modifier.weight(1f),
                        )
                        canUpdate -> {
                            if (canRun) {
                                HeroButton(
                                    text = stringResource(R.string.explore_market_run_card),
                                    poster = poster,
                                    primary = false,
                                    onClick = onRun,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            HeroButton(
                                text = stringResource(R.string.explore_market_update_to_version, entry.version),
                                poster = poster,
                                onClick = onUpdate,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        else -> HeroButton(
                            text = stringResource(R.string.explore_market_run_card),
                            poster = poster,
                            onClick = onRun,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 海报右上角的小胶囊：半透明白底 + 白字，与 Mochi 示例首卡的 Tag 同形。 */
@Composable
private fun HeroPill(text: String) {
    Text(
        text = text,
        style = MochiTheme.textStyles.labelMedium.tabular().copy(fontWeight = FontWeight.SemiBold),
        color = Color.White,
        maxLines = 1,
        modifier = Modifier
            .clip(MochiTheme.shapes.full)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private class HeroStat(val value: String, val label: String)

/** 统计条的格子：下载量 / 票数 / 价格，哪项没有数据就不占位（不显示满屏的 0）。 */
@Composable
private fun heroStats(entry: CardIndexEntry, hubInfo: HubCardMarketInfo?): List<HeroStat> {
    val locale = LocalConfiguration.current.locales[0]
    val downloads = hubInfo?.downloads?.takeIf { it > 0L } ?: entry.downloads.takeIf { it > 0L }
    val downloadsLabel = stringResource(R.string.explore_market_detail_stat_downloads)
    val votesLabel = stringResource(R.string.explore_market_detail_stat_votes)
    val priceLabel = stringResource(R.string.explore_market_detail_stat_price)
    val free = stringResource(R.string.explore_market_card_filter_free)
    val paid = hubInfo?.priceCents?.takeIf { it > 0L }?.let {
        stringResource(R.string.explore_market_price_per_call, formatYuan(it))
    }
    return buildList {
        downloads?.let { count ->
            val value = compactDownloadCount(count, locale) ?: NumberFormat.getIntegerInstance(locale).format(count)
            add(HeroStat(value, downloadsLabel))
        }
        if (hubInfo != null) {
            add(HeroStat(NumberFormat.getIntegerInstance(locale).format(hubInfo.votes), votesLabel))
            add(HeroStat(paid ?: free, priceLabel))
        }
    }
}

@Composable
private fun HeroStats(stats: List<HeroStat>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MochiRoundedRectangle(18.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(Color.White.copy(alpha = 0.18f)),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stat.value,
                    style = MochiTheme.textStyles.titleMedium.tabular().copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stat.label,
                    style = MochiTheme.textStyles.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 海报上的整宽按钮。海报底色不属于主题色，所以配色成对给：
 * 主操作白底 + 海报色字，次操作半透明白底 + 白字。
 */
@Composable
private fun HeroButton(
    text: String,
    poster: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val content = if (primary) poster else Color.White
    val colors = MochiButtonDefaults.colors(MochiButtonStyle.Filled).copy(
        container = if (primary) Color.White else Color.White.copy(alpha = 0.2f),
        content = content,
        disabledContainer = Color.White.copy(alpha = 0.6f),
        disabledContent = poster.copy(alpha = 0.8f),
    )
    MochiButton(
        onClick = onClick,
        modifier = modifier.mochiPressDeform(),
        enabled = enabled,
        colors = colors,
        minHeight = 50.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (busy) {
            MochiActivityIndicator(size = 16.dp, color = poster)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一枚标签：显示文字、点了用来搜索的词、前置图标。 */
private class DetailTag(val label: String, val icon: ImageVector, val query: String = label)

/** 一组标签共用一个色相：颜色区分的是「哪类属性」，不是每个词各一种颜色。 */
private class DetailTagGroup(val title: String, val accent: Color, val tags: List<DetailTag>)

/**
 * 标签分组：能力（桥接）/ 触发方式 / 分类 / 授权。四类都能拿来搜同类卡片，
 * 搜索口径见 [searchText]（桥接名、触发方式及其本地化文案、分类三段、授权都参与匹配）。
 */
@Composable
private fun detailTagGroups(
    entry: CardIndexEntry,
    triggers: List<String>,
    hubInfo: HubCardMarketInfo?,
): List<DetailTagGroup> {
    val capabilityTitle = stringResource(R.string.explore_market_detail_group_capabilities)
    val triggerTitle = stringResource(R.string.explore_market_detail_group_triggers)
    val categoryTitle = stringResource(R.string.explore_market_detail_group_category)
    val licenseTitle = stringResource(R.string.explore_market_detail_group_license)
    val aiLabel = stringResource(R.string.explore_market_card_filter_ai)
    val userLabel = stringResource(R.string.explore_market_trigger_user)
    return buildList {
        val bridges = entry.bridges.distinct().filter { it.isNotBlank() }
        if (bridges.isNotEmpty()) {
            add(DetailTagGroup(capabilityTitle, roleAvatarPalette[2], bridges.map { DetailTag(it, bolt) }))
        }
        if (triggers.isNotEmpty()) {
            val tags = triggers.distinct().map { trigger ->
                if (trigger == CardManifest.TRIGGER_AI) DetailTag(aiLabel, autoAwesome) else DetailTag(userLabel, adsClick)
            }
            add(DetailTagGroup(triggerTitle, roleAvatarPalette[5], tags))
        }
        val category = entry.tags?.let { listOfNotNull(it.domain, it.action, it.scene) }
            .orEmpty()
            .filter { it.isNotBlank() }
            .distinct()
        if (category.isNotEmpty()) {
            add(DetailTagGroup(categoryTitle, roleAvatarPalette[3], category.map { DetailTag(it, dashboard) }))
        }
        val license = hubInfo?.license?.takeIf { it.isNotBlank() }
        if (license != null) {
            add(DetailTagGroup(licenseTitle, roleAvatarPalette[1], listOf(DetailTag(license, verifiedUser))))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardDetailTags(groups: List<DetailTagGroup>, onTagClick: (String) -> Unit) {
    MochiListSection(
        header = stringResource(R.string.explore_market_detail_section_tags),
        footer = stringResource(R.string.explore_market_detail_tags_footer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = group.title,
                        style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        group.tags.forEach { tag ->
                            DetailTagChip(tag, group.accent, onClick = { onTagClick(tag.query) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * 标签芯片：[MochiChip]（按压、读屏都走库），底色是分组色的浅底，
 * 前面一枚分组色实心圆托着白色小图标 —— 一眼看出这是哪一类属性。
 */
@Composable
private fun DetailTagChip(tag: DetailTag, accent: Color, onClick: () -> Unit) {
    val scheme = MochiTheme.colorScheme
    val colors = MochiChipDefaults.colors().copy(
        container = accent.copy(alpha = if (scheme.isDark) 0.24f else 0.14f),
        content = lerp(accent, scheme.onSurface, if (scheme.isDark) 0.55f else 0.45f),
    )
    MochiChip(
        label = tag.label,
        selected = false,
        onClick = onClick,
        colors = colors,
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tag.icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White,
                )
            }
        },
    )
}

/** 信息分区：一行一项，值放在行尾的值槽里（不拼「·」）。没有的项不占行。 */
@Composable
private fun CardDetailInfo(entry: CardIndexEntry, installedVersion: String?) {
    MochiListSection(header = stringResource(R.string.explore_market_detail_section_info)) {
        if (entry.version.isNotBlank()) {
            MochiListRow(
                title = stringResource(R.string.explore_market_detail_version),
                value = stringResource(R.string.explore_market_version_prefix, entry.version),
            )
        }
        if (installedVersion != null && installedVersion != entry.version) {
            MochiListRow(
                title = stringResource(R.string.explore_market_detail_installed_version),
                value = stringResource(R.string.explore_market_version_prefix, installedVersion),
            )
        }
        if (entry.author.isNotBlank()) {
            MochiListRow(title = stringResource(R.string.explore_market_detail_author), value = entry.author)
        }
        if (entry.engine.isNotBlank()) {
            MochiListRow(title = stringResource(R.string.explore_market_detail_engine), value = entry.engine)
        }
        if (entry.privilege.isNotBlank()) {
            MochiListRow(title = stringResource(R.string.explore_market_detail_privilege), value = entry.privilege)
        }
        if (entry.sizeBytes > 0L) {
            MochiListRow(title = stringResource(R.string.explore_market_detail_size), value = formatBytes(entry.sizeBytes))
        }
    }
}
