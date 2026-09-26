package heizige.kk.khatkit.uikit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
 * KhatKit 卡片市场 - 现代探索页面风格
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    var selectedCard by remember { mutableStateOf<CardIndexEntry?>(null) }

    // 分类筛选
    var selectedFilter by remember { mutableStateOf("all") }
    val filterOptions = listOf(
        "all" to "全部",
        "installed" to "已安装",
        "ai" to "AI 触发",
        "user" to "用户触发",
        "free" to "免费",
        "preferred" to "推荐"
    )

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
        scope.launch {
            activated = controller.hubAccountStatus(refresh = false).activated
            hubInfo = controller.hubMarketCards().associateBy { it.name }
        }
    }

    LaunchedEffect(Unit) { refresh("") }

    // 筛选卡片
    val filteredCards = remember(cards, selectedFilter, installed, hubInfo) {
        when (selectedFilter) {
            "installed" -> cards.filter { it.name in installed }
            "ai" -> cards.filter { CardManifest.TRIGGER_AI in it.triggers }
            "user" -> cards.filter { CardManifest.TRIGGER_USER in it.triggers }
            "free" -> cards.filter { hubInfo[it.name]?.priceCents?.let { it <= 0 } ?: true }
            "preferred" -> cards.filter { hubInfo[it.name]?.preferred == true }
            else -> cards
        }
    }

    // 精选卡片（优选 + 高票数）
    val featuredCards = remember(cards, hubInfo) {
        cards.filter {
            val info = hubInfo[it.name]
            info?.preferred == true || (info?.votes ?: 0) > 5
        }.take(5)
    }

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

    selectedCard?.let { card ->
        CardDetailDialog(
            entry = card,
            installedVersion = installed[card.name],
            triggers = triggers[card.name] ?: card.triggers,
            hubInfo = hubInfo[card.name],
            activated = activated,
            controller = controller,
            onDismiss = { selectedCard = null },
            onToast = onToast,
            onSecretsClick = { secretCard = card.name },
            onPublishClick = { publishTarget = card.name },
            onForkClick = { forkTarget = card.name },
            onRefreshInstalled = { scope.launch { reloadInstalled() } }
        )
    }

    val localOnly = installed.keys.filterNot { name -> cards.any { it.name == name } }

    Column(modifier = modifier.fillMaxSize()) {
        // 搜索栏和设置按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KedgeOutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.khatkit_market_search_hint),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { refresh(query) }) {
                Text("🔍", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = { showSettings = true }) {
                Text("⚙️", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.weight(1f),
        ) {
            // Hero 轮播区 - 精选卡片
            if (featuredCards.isNotEmpty() && query.isBlank()) {
                item(key = "hero_section") {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "✨ 精选推荐",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        val pagerState = rememberPagerState(pageCount = { featuredCards.size })

                        HorizontalPager(
                            state = pagerState,
                            contentPadding = PaddingValues(horizontal = 32.dp),
                            pageSpacing = 16.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            FeaturedCard(
                                entry = featuredCards[page],
                                installedVersion = installed[featuredCards[page].name],
                                hubInfo = hubInfo[featuredCards[page].name],
                                onClick = { selectedCard = featuredCards[page] }
                            )
                        }

                        // 页面指示器
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(featuredCards.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (pagerState.currentPage == index)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // 分类筛选标签
            item(key = "filter_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    items(filterOptions) { (key, label) ->
                        FilterChip(
                            selected = selectedFilter == key,
                            onClick = { selectedFilter = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // 卡片数量提示
            if (!loading) {
                item(key = "card_count") {
                    Text(
                        text = "共 ${filteredCards.size} 个卡片",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // 网格卡片列表
            if (!loading && filteredCards.isEmpty() && localOnly.isEmpty()) {
                item(key = "empty_state") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔍",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.khatkit_market_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 卡片网格
            items(filteredCards.chunked(2), key = { chunk -> chunk.first().name }) { chunk ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    chunk.forEach { entry ->
                        CompactCard(
                            entry = entry,
                            installedVersion = installed[entry.name],
                            triggers = triggers[entry.name] ?: entry.triggers,
                            hubInfo = hubInfo[entry.name],
                            onClick = { selectedCard = entry },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // 填充空位
                    if (chunk.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // 本地卡片
            if (localOnly.isNotEmpty()) {
                item(key = "local_only_header") {
                    Text(
                        text = stringResource(R.string.khatkit_market_local_only_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(localOnly.chunked(2), key = { chunk -> "local_${chunk.first()}" }) { chunk ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        chunk.forEach { name ->
                            LocalOnlyCompactCard(
                                name = name,
                                version = installed[name].orEmpty(),
                                triggers = triggers[name].orEmpty(),
                                onClick = { publishTarget = name },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (chunk.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** 精选卡片 - Hero 区域 */
@Composable
private fun FeaturedCard(
    entry: CardIndexEntry,
    installedVersion: String?,
    hubInfo: HubCardMarketInfo?,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 渐变背景
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (installedVersion != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = "已安装",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = entry.summary.ifBlank { entry.description },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    hubInfo?.let { info ->
                        if (info.preferred) {
                            BadgeChip(text = "✨ ${stringResource(R.string.khatkit_market_preferred)}", primary = true)
                        }
                        if (info.votes > 0) {
                            BadgeChip(text = "👍 ${info.votes}")
                        }
                        BadgeChip(
                            text = if (info.priceCents <= 0) "免费" else "¥${formatYuan(info.priceCents)}"
                        )
                    }
                }
            }
        }
    }
}

/** 紧凑卡片 - 网格展示 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactCard(
    entry: CardIndexEntry,
    installedVersion: String?,
    triggers: List<String>,
    hubInfo: HubCardMarketInfo?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (installedVersion != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = entry.summary.ifBlank { entry.description },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                triggers.filter { it in CardManifest.ALL_TRIGGERS }.forEach { trigger ->
                    SmallBadge(
                        text = when (trigger) {
                            CardManifest.TRIGGER_AI -> "AI"
                            else -> "User"
                        }
                    )
                }
                hubInfo?.let { info ->
                    if (info.preferred) {
                        SmallBadge(text = "✨", primary = true)
                    }
                    if (info.votes > 0) {
                        SmallBadge(text = "▲${info.votes}")
                    }
                }
            }
        }
    }
}

/** 本地卡片 - 紧凑版 */
@Composable
private fun LocalOnlyCompactCard(
    name: String,
    version: String,
    triggers: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "v${version.ifBlank { "?" }} · 本地卡片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                triggers.filter { it in CardManifest.ALL_TRIGGERS }.forEach { trigger ->
                    SmallBadge(
                        text = when (trigger) {
                            CardManifest.TRIGGER_AI -> "AI"
                            else -> "User"
                        }
                    )
                }
            }
        }
    }
}

/** 小徽章 */
@Composable
private fun SmallBadge(text: String, primary: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (primary) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (primary) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** 徽章芯片 */
@Composable
private fun BadgeChip(text: String, primary: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal,
            color = if (primary) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/** 卡片详情对话框 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardDetailDialog(
    entry: CardIndexEntry,
    installedVersion: String?,
    triggers: List<String>,
    hubInfo: HubCardMarketInfo?,
    activated: Boolean,
    controller: KhatKitController,
    onDismiss: () -> Unit,
    onToast: (String, Boolean) -> Unit,
    onSecretsClick: () -> Unit,
    onPublishClick: () -> Unit,
    onForkClick: () -> Unit,
    onRefreshInstalled: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val displayTriggers = triggers.filter { it in CardManifest.ALL_TRIGGERS }

    KedgeDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = entry.name
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 版本和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "v${entry.version}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (installedVersion != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.khatkit_market_installed_version, installedVersion),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 描述
            val description = entry.summary.ifBlank { entry.description }
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 标签
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                displayTriggers.forEach { trigger ->
                    BadgeChip(
                        text = when (trigger) {
                            CardManifest.TRIGGER_AI -> stringResource(R.string.khatkit_trigger_ai)
                            else -> stringResource(R.string.khatkit_trigger_user)
                        }
                    )
                }
                hubInfo?.let { info ->
                    BadgeChip(
                        text = if (info.priceCents <= 0) {
                            stringResource(R.string.khatkit_market_price_free)
                        } else {
                            stringResource(R.string.khatkit_market_price_per_call, formatYuan(info.priceCents))
                        },
                        primary = info.priceCents > 0
                    )
                    if (info.license.isNotBlank()) {
                        BadgeChip(text = info.license)
                    }
                    if (info.preferred) {
                        BadgeChip(text = stringResource(R.string.khatkit_market_preferred), primary = true)
                    }
                    if (info.votes > 0) {
                        BadgeChip(text = stringResource(R.string.khatkit_market_votes, info.votes))
                    }
                }
            }

            // 元信息
            Text(
                text = "${entry.engine} · ${entry.privilege} · ${entry.bridges.joinToString(", ").ifBlank { "L0" }}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 操作按钮
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (installedVersion != null) {
                    if (CardManifest.TRIGGER_USER in triggers) {
                        KedgeButton(
                            onClick = {
                                scope.launch {
                                    val result = controller.runCard(entry.name)
                                    onToast(
                                        result.message.ifBlank {
                                            if (result.ok) "已运行 ${entry.name}" else "运行失败"
                                        },
                                        !result.ok
                                    )
                                }
                            }
                        ) { Text(stringResource(R.string.khatkit_market_run)) }
                    }
                    if (installedVersion != entry.version) {
                        KedgeButton(
                            onClick = {
                                scope.launch {
                                    val ok = controller.installCard(entry, force = true)
                                    onToast(
                                        if (ok) "已更新 ${entry.name}" else "更新失败",
                                        !ok
                                    )
                                    onRefreshInstalled()
                                }
                            }
                        ) { Text(stringResource(R.string.khatkit_market_update)) }
                    }
                    KedgeTextButton(onClick = onSecretsClick) {
                        Text(stringResource(R.string.khatkit_market_secrets))
                    }
                    KedgeTextButton(
                        onClick = {
                            scope.launch {
                                val ok = controller.uninstallCard(entry.name)
                                onToast(
                                    if (ok) "已卸载 ${entry.name}" else "卸载失败",
                                    !ok
                                )
                                if (ok) onDismiss()
                                onRefreshInstalled()
                            }
                        }
                    ) { Text(stringResource(R.string.khatkit_market_uninstall)) }
                } else {
                    KedgeButton(
                        onClick = {
                            scope.launch {
                                val ok = controller.installCard(entry)
                                onToast(
                                    if (ok) "已安装 ${entry.name}" else "安装失败",
                                    !ok
                                )
                                onRefreshInstalled()
                            }
                        }
                    ) { Text(stringResource(R.string.khatkit_market_install)) }
                }

                if (installedVersion != null || hubInfo != null) {
                    if (installedVersion != null) {
                        KedgeTextButton(
                            onClick = {
                                if (activated) onPublishClick() else onToast("需要先激活账户", true)
                            }
                        ) { Text(stringResource(R.string.khatkit_market_publish)) }
                        KedgeTextButton(
                            onClick = {
                                if (activated) onForkClick() else onToast("需要先激活账户", true)
                            }
                        ) { Text(stringResource(R.string.khatkit_market_fork)) }
                    }
                    if (hubInfo != null) {
                        KedgeTextButton(
                            onClick = {
                                if (!activated) {
                                    onToast("需要先激活账户", true)
                                } else {
                                    scope.launch {
                                        val result = controller.voteCard(entry.name)
                                        onToast(
                                            result.message.ifBlank {
                                                if (result.ok) "已投票 ${entry.name}" else "投票失败"
                                            },
                                            !result.ok
                                        )
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.khatkit_market_vote)) }
                    }
                }
            }
        }
    }
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
