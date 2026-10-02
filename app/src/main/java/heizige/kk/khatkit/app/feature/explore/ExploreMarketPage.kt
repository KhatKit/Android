package heizige.kk.khatkit.app.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import heizige.kk.kedge.components.KedgeFilterChip
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import heizige.kk.kedge.components.KedgeBadge
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.ai.provider.BuiltInTools
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.Modality
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.data.ai.mcp.McpStatus
import heizige.kk.khatkit.app.core.data.ai.mcp.serverUrl
import heizige.kk.khatkit.app.core.data.ai.tools.local.LocalToolOption
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.autoAwesome
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.calendarMonth
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.contentPaste
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.deleteForever
import heizige.kk.khatkit.app.core.ui.icons.extension
import heizige.kk.khatkit.app.core.ui.icons.package2
import heizige.kk.khatkit.app.core.ui.icons.psychology
import heizige.kk.khatkit.app.core.ui.icons.questionAnswer
import heizige.kk.khatkit.app.core.ui.icons.schedule
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.icons.timer
import heizige.kk.khatkit.app.core.ui.icons.travelExplore
import heizige.kk.khatkit.app.core.ui.icons.volumeUp
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khromia.helper.Toast
import heizige.kk.khromia.components.pressBounce
import heizige.kk.khromia.components.shape.AutoCornersShape
import heizige.kk.khromia.data.harmonizeWithPrimary
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.HubCardMarketInfo
import heizige.kk.khatkit.uikit.KhatKitSecretsDialog
import heizige.kk.khatkit.uikit.KhatKitTheme
import heizige.kk.khatkit.uikit.KhatKitUiStyle
import kotlinx.coroutines.launch
import heizige.kk.kedge.adaptive.KedgePageScaffold
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.kedge.theme.KedgeStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.overlays.KedgePullToRefreshBox

private enum class ExploreFilter(val label: String) {
    ALL("全部"),
    TOOLS("工具"),
    SKILLS("技能"),
    MCP("MCP"),
    MODELS("模型能力"),
    CARDS("卡片"),
}

private enum class CardFilter(val label: String) {
    ALL("全部"),
    INSTALLED("已安装"),
    FREE("免费"),
    PREFERRED("推荐"),
    AI("AI 触发"),
}

private data class LocalToolInfo(
    val option: LocalToolOption,
    val title: String,
    val desc: String,
    val icon: ImageVector,
)

private val localToolCatalog = listOf(
    LocalToolInfo(LocalToolOption.JavascriptEngine, "JavaScript 引擎", "QuickJS 执行代码，计算与脚本处理", code),
    LocalToolInfo(LocalToolOption.TimeInfo, "时间信息", "获取日期、星期、时间与时区", schedule),
    LocalToolInfo(LocalToolOption.Clipboard, "剪贴板", "读写设备剪贴板中的纯文本", contentPaste),
    LocalToolInfo(LocalToolOption.Tts, "文字转语音", "用所选 TTS 服务朗读文本", volumeUp),
    LocalToolInfo(LocalToolOption.AskUser, "询问用户", "AI 向你提问以获取更多信息", questionAnswer),
    LocalToolInfo(LocalToolOption.ScreenTime, "屏幕时间", "读取应用屏幕使用时间", timer),
    LocalToolInfo(LocalToolOption.Calendar, "日历", "查询和创建日历事件", calendarMonth),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreMarketPage() {
    val navController = LocalNavController.current
    val vm = hiltViewModel<ExploreMarketViewModel>()
    val scope = rememberCoroutineScope()

    val settings by vm.settings.collectAsStateWithLifecycle()
    val skills by vm.skills.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val triggers by vm.triggers.collectAsStateWithLifecycle()
    val hubInfo by vm.hubInfo.collectAsStateWithLifecycle()
    val activated by vm.activated.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val busyCard by vm.busyCard.collectAsStateWithLifecycle()
    val mcpStatus by vm.mcpStatus.collectAsStateWithLifecycle()
    val dependencies by vm.dependencies.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ExploreFilter.ALL.name) }
    var cardFilter by rememberSaveable { mutableStateOf(CardFilter.ALL.name) }
    val selectedFilter = remember(filter) { ExploreFilter.valueOf(filter) }
    val selectedCardFilter = remember(cardFilter) { CardFilter.valueOf(cardFilter) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var secretCard by remember { mutableStateOf<String?>(null) }
    var selectedCard by remember { mutableStateOf<CardIndexEntry?>(null) }
    var publishTarget by remember { mutableStateOf<CardIndexEntry?>(null) }
    var forkTarget by remember { mutableStateOf<CardIndexEntry?>(null) }
    val provider = rememberAppEntryPoint().khatKitToolProvider()
    var uiStyle by remember { mutableStateOf(vm.uiStyle) }

    val enabledProviders = remember(settings) { settings.providers.filter { it.enabled } }
    val models = remember(enabledProviders) { enabledProviders.flatMap { p -> p.models.map { p.name to it } } }
    val mcpServers = remember(settings) { settings.mcpServers }
    val currentAssistantLocalTools = remember(settings) {
        settings.assistants.find { it.id == settings.assistantId }?.localTools?.toSet().orEmpty()
    }

    fun matches(text: String): Boolean = query.isBlank() || text.contains(query, ignoreCase = true)

    val filteredCards = remember(cards, selectedCardFilter, installed, hubInfo, triggers) {
        cards.filter { entry ->
            val info = hubInfo[entry.name]
            when (selectedCardFilter) {
                CardFilter.INSTALLED -> entry.name in installed
                CardFilter.FREE -> (info?.priceCents ?: 0L) <= 0L
                CardFilter.PREFERRED -> info?.preferred == true
                CardFilter.AI -> CardManifest.TRIGGER_AI in (triggers[entry.name] ?: entry.triggers)
                CardFilter.ALL -> true
            }
        }
    }
    val featuredCards = remember(cards, hubInfo) {
        cards.filter {
            val info = hubInfo[it.name]
            info?.preferred == true || (info?.votes ?: 0) > 5
        }.take(5)
    }
    val localOnly = remember(installed, cards) {
        installed.keys.filterNot { name -> cards.any { it.name == name } }.sorted()
    }

    KedgePageScaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.explore_market_title),
                subtitle = stringResource(R.string.explore_market_subtitle),
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // 页面底色用 Miuix surface，不能用顶栏的半透明色：
        // 否则整页透出下层黑底，正文比顶栏暗，看起来像顶栏/背景反了。
        containerColor = if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
            MiuixTheme.colorScheme.surface
        } else {
            CustomColors.topBarColors.containerColor
        },
    
        md3ScrollBehavior = scrollBehavior,
    ) { innerPadding ->
        val pullToRefreshState = rememberPullToRefreshState()
        KedgePullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { vm.refresh(query) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding + PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
            item(key = "search") {
                KedgeOutlinedTextFieldWithSlots(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AutoCornersShape(28.dp),
                    singleLine = true,
                    placeholder = { Text("搜索卡片、工具、技能、MCP、模型…") },
                    leadingIcon = { Icon(search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            KedgeIconButton(onClick = {
                                query = ""
                                vm.refresh("")
                            }) {
                                Icon(heizige.kk.khatkit.app.core.ui.icons.close, contentDescription = "清除")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }

            item(key = "market_overview") {
                KedgeCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                    ),
                    shape = AutoCornersShape(22.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        IconTile(
                            icon = package2,
                            container = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            size = 44.dp,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.explore_market_source_badge),
                                style = KedgeTextStyles.footnoteSmall(),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.explore_market_overview),
                                style = KedgeTextStyles.body(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = stringResource(R.string.explore_market_card_count, cards.size, installed.size),
                                style = KedgeTextStyles.body(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                            )
                        }
                    }
                }
            }

            item(key = "filter_chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ExploreFilter.entries) { option ->
                        KedgeFilterChip(
                            selected = selectedFilter == option,
                            onClick = { filter = option.name },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            if (selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.CARDS) {
                item(key = "dependency_cache") {
                    SectionHeader(
                        title = "原生依赖包",
                        icon = heizige.kk.khatkit.app.core.ui.icons.extension,
                        supporting = if (dependencies.isEmpty()) "暂无已下载依赖" else "已缓存 ${dependencies.size} 个版本",
                        actionLabel = dependencies.takeIf { it.isNotEmpty() }?.let { "清空" },
                        onAction = {
                            vm.clearDependencyCache { count ->
                                scope.launch { Toast.show(if (count > 0) "已清理 $count 个依赖包" else "没有可清理的依赖包") }
                            }
                        },
                    )
                    if (dependencies.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            dependencies.forEach { dependency ->
                                KedgeCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${dependency.name} ${dependency.version}", fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${formatBytes(dependency.sizeBytes)} · SHA-256 ${dependency.sha256.take(12)}…",
                                                style = KedgeTextStyles.body(),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        KedgeIconButton(onClick = {
                                            vm.deleteDependency(dependency) { ok ->
                                                scope.launch { Toast.show(if (ok) "已删除 ${dependency.name} ${dependency.version}" else "删除失败", !ok) }
                                            }
                                        }) {
                                            Icon(deleteForever, contentDescription = "删除依赖包")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val cardsVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.CARDS
            val cardsMatched = filteredCards.filter { matches(it.name + it.summary + it.description) }

            if (cardsVisible && featuredCards.isNotEmpty() && matches("卡片 精选 推荐")) {
                item(key = "featured") {
                    SectionHeader(
                        title = "精选推荐",
                        icon = travelExplore,
                        supporting = "${featuredCards.size} 张优选卡片",
                    )
                    val pagerState = rememberPagerState(pageCount = { featuredCards.size })
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        pageSpacing = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) { page ->
                        FeaturedCard(
                            entry = featuredCards[page],
                            installedVersion = installed[featuredCards[page].name],
                            hubInfo = hubInfo[featuredCards[page].name],
                            onClick = { selectedCard = featuredCards[page] },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        repeat(featuredCards.size) { index ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                }
            }

            if (cardsVisible && cardsMatched.isNotEmpty()) {
                item(key = "card_filters") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CardFilter.entries) { option ->
                            KedgeFilterChip(
                                selected = selectedCardFilter == option,
                                onClick = { cardFilter = option.name },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
                item(key = "card_grid") {
                    SectionHeader(
                        title = stringResource(R.string.explore_market_external_cards),
                        icon = package2,
                        supporting = "共 ${cardsMatched.size} 张卡片 · 已安装 ${installed.size}",
                        actionLabel = if (loading) null else "刷新",
                        onAction = { vm.refresh(query) },
                    )
                    if (loading) {
                        KedgeProgressIndicator(
                        modifier = Modifier.padding(16.dp),
                    )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            cardsMatched.chunked(2).forEach { chunk ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    chunk.forEach { entry ->
                                        CompactCard(
                                            entry = entry,
                                            installedVersion = installed[entry.name],
                                            triggers = triggers[entry.name] ?: entry.triggers,
                                            hubInfo = hubInfo[entry.name],
                                            busy = busyCard == entry.name,
                                            onClick = { selectedCard = entry },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (chunk.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            if (cardsVisible && localOnly.isNotEmpty() && matches("本机 本地 local")) {
                item(key = "local_cards") {
                    SectionHeader(
                        title = "本机卡片",
                        icon = bolt,
                        supporting = "${localOnly.size} 张仅存在于本机",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        localOnly.forEach { name ->
                            PressableCard(
                                onClick = {
                                    selectedCard = CardIndexEntry(
                                        name = name,
                                        version = installed[name].orEmpty(),
                                        url = "",
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    IconTile(
                                        icon = package2,
                                        container = MaterialTheme.colorScheme.primaryContainer.harmonizeWithPrimary(),
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = name, style = KedgeTextStyles.title())
                                        Text(
                                            text = "v${installed[name].orEmpty()} · 未在市场索引中",
                                            style = KedgeTextStyles.body(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    KedgeBadge(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                        Text(triggers[name]?.joinToString("/") ?: "local")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val toolsVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.TOOLS
            val tools = localToolCatalog.filter { matches(it.title + it.desc) }
            if (toolsVisible && tools.isNotEmpty()) {
                item(key = "tools") {
                    SectionHeader(
                        title = "本地工具",
                        icon = bolt,
                        supporting = "${tools.size} 项 · 当前助手启用 ${tools.count { it.option in currentAssistantLocalTools }} 项",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        tools.forEach { tool ->
                            ToolCard(
                                tool = tool,
                                enabled = tool.option in currentAssistantLocalTools,
                                onClick = {
                                    navController.navigate(
                                        Screen.AssistantLocalTool(settings.assistantId.toString())
                                    )
                                },
                            )
                        }
                    }
                }
            }

            val skillsVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.SKILLS
            val visibleSkills = skills.filter { matches(it.name + it.description) }
            if (skillsVisible && visibleSkills.isNotEmpty()) {
                item(key = "skills") {
                    SectionHeader(
                        title = "代理技能",
                        icon = extension,
                        supporting = "${visibleSkills.size} 个技能包",
                        actionLabel = "管理",
                        onAction = { navController.navigate(Screen.Skills) },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        visibleSkills.take(6).forEach { skill ->
                            PressableCard(
                                onClick = { navController.navigate(Screen.SkillDetail(skill.name)) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    IconTile(
                                        icon = extension,
                                        container = MaterialTheme.colorScheme.secondaryContainer.harmonizeWithPrimary(),
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = skill.name,
                                            style = KedgeTextStyles.title(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = skill.description,
                                            style = KedgeTextStyles.body(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val mcpVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.MCP
            val visibleMcp = mcpServers.filter { matches(it.commonOptions.name + it.serverUrl) }
            if (mcpVisible && visibleMcp.isNotEmpty()) {
                item(key = "mcp") {
                    SectionHeader(
                        title = "MCP 服务器",
                        icon = dns,
                        supporting = "${visibleMcp.size} 个服务器",
                        actionLabel = "管理",
                        onAction = { navController.navigate(Screen.SettingMcp) },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        visibleMcp.forEach { server ->
                            McpCard(
                                server = server,
                                status = mcpStatus[server.id],
                                onClick = { navController.navigate(Screen.SettingMcp) },
                            )
                        }
                    }
                }
            }

            val modelsVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.MODELS
            val visibleModels = models.filter { (providerName, model) ->
                matches(providerName + model.displayName + model.modelId)
            }
            if (modelsVisible && visibleModels.isNotEmpty()) {
                item(key = "models") {
                    SectionHeader(
                        title = "模型能力",
                        icon = autoAwesome,
                        supporting = "${visibleModels.size} 个模型 · ${enabledProviders.size} 个供应商",
                        actionLabel = "管理",
                        onAction = { navController.navigate(Screen.SettingModels) },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        visibleModels.take(8).forEach { (providerName, model) ->
                            ModelCard(
                                providerName = providerName,
                                model = model,
                                onClick = { navController.navigate(Screen.SettingModels) },
                            )
                        }
                    }
                }
            }

            val isEmpty = cardsMatched.isEmpty() && visibleSkills.isEmpty() && visibleMcp.isEmpty() &&
                visibleModels.isEmpty() && tools.filter { matches(it.title + it.desc) }.isEmpty()
            if (isEmpty) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "没有找到相关内容",
                            style = KedgeTextStyles.body(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            }
        }
    }

    KhatKitTheme(
        style = uiStyle,
        darkTheme = heizige.kk.khatkit.app.core.ui.hooks.rememberIsDarkTheme(),
    ) {
        secretCard?.let { cardName ->
            KhatKitSecretsDialog(
                cardName = cardName,
                controller = provider,
                onDismiss = { secretCard = null },
                onToast = { message, isError -> Toast.show(message = message, isError = isError) },
            )
        }
    }

    publishTarget?.let { entry ->
        PublishCardDialog(
            entry = entry,
            onDismiss = { publishTarget = null },
            onConfirm = { license, price, changelog ->
                publishTarget = null
                vm.publish(entry.name, license, price, changelog) { result ->
                    scope.launch {
                        Toast.show(
                            result.message.ifBlank { if (result.ok) "已提交发布审核" else "发布失败" },
                            !result.ok,
                        )
                        vm.refresh(query)
                    }
                }
            },
        )
    }

    forkTarget?.let { entry ->
        ForkCardDialog(
            entry = entry,
            parentVersion = installed[entry.name] ?: hubInfo[entry.name]?.version.orEmpty(),
            onDismiss = { forkTarget = null },
            onConfirm = { parent, newVersion, changelog ->
                forkTarget = null
                vm.fork(entry.name, parent, changelog, newVersion) { result ->
                    scope.launch {
                        Toast.show(
                            result.message.ifBlank { if (result.ok) "已提交改进" else "提交失败" },
                            !result.ok,
                        )
                        vm.refresh(query)
                    }
                }
            },
        )
    }

    selectedCard?.let { entry ->
        CardDetailSheet(
            entry = entry,
            installedVersion = installed[entry.name],
            triggers = triggers[entry.name] ?: entry.triggers,
            hubInfo = hubInfo[entry.name],
            activated = activated,
            busy = busyCard == entry.name,
            onDismiss = { selectedCard = null },
            onToast = { message, isError -> Toast.show(message = message, isError = isError) },
            onSecretsClick = {
                secretCard = entry.name
                selectedCard = null
            },
            onPublishClick = {
                publishTarget = entry
                selectedCard = null
            },
            onForkClick = {
                forkTarget = entry
                selectedCard = null
            },
            onRun = { vm.run(entry.name) { r -> scope.launch { Toast.show(r.message.ifBlank { if (r.ok) "已运行 ${entry.name}" else "运行失败" }, !r.ok) } } },
            onInstall = { vm.install(entry) { ok -> scope.launch { Toast.show(if (ok) "已安装 ${entry.name}" else "安装失败", !ok) } } },
            onUpdate = { vm.update(entry) { ok -> scope.launch { Toast.show(if (ok) "已更新 ${entry.name}" else "更新失败", !ok) } } },
            onUninstall = {
                vm.uninstall(entry.name) { ok ->
                    scope.launch {
                        Toast.show(if (ok) "已卸载 ${entry.name}" else "卸载失败", !ok)
                        if (ok) selectedCard = null
                    }
                }
            },
            onVote = {
                if (!activated) {
                    Toast.show("需要先激活账户", true)
                } else {
                    vm.vote(entry.name) { r ->
                        scope.launch {
                            Toast.show(r.message.ifBlank { if (r.ok) "已投票 ${entry.name}" else "投票失败" }, !r.ok)
                        }
                    }
                }
            },
        )
    }

}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    supporting: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(
            icon = icon,
            container = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.primary,
            size = 36.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = KedgeTextStyles.displayTitle(),
                fontWeight = FontWeight.Bold,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = KedgeTextStyles.body(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            val interactionSource = remember { MutableInteractionSource() }
            KedgeTextButton(
                onClick = onAction,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.pressBounce(interactionSource),
            ) { Text(actionLabel) }
        }
    }
}

@Composable
private fun IconTile(
    icon: ImageVector,
    container: Color,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .pressBounce(interactionSource)
            .clip(AutoCornersShape(14.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.52f),
            tint = contentColor,
        )
    }
}

@Composable
private fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceBright.harmonizeWithPrimary(),
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressBounce(interactionSource)
            .clip(AutoCornersShape(22.dp))
            .background(container)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        content()
    }
}

@Composable
private fun ToolCard(
    tool: LocalToolInfo,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    PressableCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconTile(
                icon = tool.icon,
                container = MaterialTheme.colorScheme.primaryContainer.harmonizeWithPrimary(),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tool.title, style = KedgeTextStyles.title())
                Text(
                    text = tool.desc,
                    style = KedgeTextStyles.body(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            KedgeBadge(
                containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(if (enabled) "已启用" else "未启用")
            }
        }
    }
}

@Composable
private fun McpCard(
    server: McpServerConfig,
    status: McpStatus?,
    onClick: () -> Unit,
) {
    PressableCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconTile(
                icon = dns,
                container = MaterialTheme.colorScheme.tertiaryContainer.harmonizeWithPrimary(),
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.commonOptions.name.ifBlank { server.serverUrl },
                    style = KedgeTextStyles.title(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = server.serverUrl,
                    style = KedgeTextStyles.body(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val (statusLabel, statusColor) = when (status) {
                McpStatus.Connected -> "已连接" to MaterialTheme.colorScheme.primary
                McpStatus.Connecting, is McpStatus.Reconnecting -> "连接中" to MaterialTheme.colorScheme.tertiary
                is McpStatus.Error -> "错误" to MaterialTheme.colorScheme.error
                McpStatus.NeedsAuthorization, McpStatus.Authorizing -> "需授权" to MaterialTheme.colorScheme.secondary
                else -> (if (server.commonOptions.enable) "未连接" else "已停用") to MaterialTheme.colorScheme.onSurfaceVariant
            }
            KedgeBadge(
                containerColor = statusColor.copy(alpha = 0.16f),
                contentColor = statusColor,
            ) {
                Text(statusLabel)
            }
        }
    }
}

@Composable
private fun ModelCard(
    providerName: String,
    model: Model,
    onClick: () -> Unit,
) {
    PressableCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconTile(
                    icon = psychology,
                    container = MaterialTheme.colorScheme.secondaryContainer.harmonizeWithPrimary(),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.displayName.ifBlank { model.modelId },
                        style = KedgeTextStyles.title(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = providerName,
                        style = KedgeTextStyles.footnoteSmall(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                model.abilityBadges().forEach { (label, color) ->
                    KedgeBadge(
                        containerColor = color.copy(alpha = 0.16f),
                        contentColor = color,
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

private fun Model.abilityBadges(): List<Pair<String, Color>> {
    val badges = mutableListOf<Pair<String, Color>>()
    if (ModelAbility.TOOL in abilities) badges.add("工具调用" to Color(0xFF4A6CF7))
    if (ModelAbility.REASONING in abilities) badges.add("推理" to Color(0xFF8B5CF6))
    if (Modality.IMAGE in inputModalities) badges.add("视觉输入" to Color(0xFF0EA5E9))
    if (Modality.IMAGE in outputModalities || type == ModelType.IMAGE) badges.add("图像输出" to Color(0xFFF59E0B))
    if (BuiltInTools.Search in tools) badges.add("内置搜索" to Color(0xFF10B981))
    if (BuiltInTools.ImageGeneration in tools) badges.add("图像生成" to Color(0xFFEC4899))
    return badges.take(4)
}

@Composable
private fun FeaturedCard(
    entry: CardIndexEntry,
    installedVersion: String?,
    hubInfo: HubCardMarketInfo?,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pressBounce(interactionSource)
            .clip(AutoCornersShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.harmonizeWithPrimary(),
                        MaterialTheme.colorScheme.tertiaryContainer.harmonizeWithPrimary(),
                    )
                )
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        style = KedgeTextStyles.title(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (installedVersion != null) {
                        KedgeBadge(containerColor = MaterialTheme.colorScheme.primary) { Text("已安装") }
                    } else if (hubInfo?.preferred == true) {
                        KedgeBadge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) { Text("优选") }
                    }
                }
                Text(
                    text = entry.summary.ifBlank { entry.description }.ifBlank { entry.author },
                    style = KedgeTextStyles.body(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(36.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (hubInfo != null) {
                        KedgeBadge(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
                            Text(if (hubInfo.priceCents <= 0L) "免费" else "¥${formatYuan(hubInfo.priceCents)}")
                        }
                        if (hubInfo.votes > 0) {
                            KedgeBadge(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
                                Text("▲ ${hubInfo.votes}")
                            }
                        }
                    }
                    KedgeBadge(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
                        Text("v${entry.version}")
                    }
                }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactCard(
    entry: CardIndexEntry,
    installedVersion: String?,
    triggers: List<String>,
    hubInfo: HubCardMarketInfo?,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressableCard(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = KedgeTextStyles.title(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (busy) {
                    KedgeProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (installedVersion != null) {
                    KedgeBadge(containerColor = MaterialTheme.colorScheme.primaryContainer) { Text("已装") }
                }
            }
            Text(
                text = entry.summary.ifBlank { entry.description },
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(34.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                triggers.filter { it in CardManifest.ALL_TRIGGERS }.forEach { trigger ->
                    KedgeBadge(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(if (trigger == CardManifest.TRIGGER_AI) "AI" else "手动")
                    }
                }
                hubInfo?.let { info ->
                    KedgeBadge(
                        containerColor = if (info.priceCents <= 0L) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(if (info.priceCents <= 0L) "免费" else "¥${formatYuan(info.priceCents)}")
                    }
                    if (info.preferred) {
                        KedgeBadge(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) { Text("优选") }
                    }
                    if (info.votes > 0) {
                        KedgeBadge(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) { Text("▲${info.votes}") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CardDetailSheet(
    entry: CardIndexEntry,
    installedVersion: String?,
    triggers: List<String>,
    hubInfo: HubCardMarketInfo?,
    activated: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onToast: (String, Boolean) -> Unit,
    onSecretsClick: () -> Unit,
    onPublishClick: () -> Unit,
    onForkClick: () -> Unit,
    onRun: () -> Unit,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onVote: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val displayTriggers = triggers.filter { it in CardManifest.ALL_TRIGGERS }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = KedgeTextStyles.displayTitle(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "v${entry.version}",
                    style = KedgeTextStyles.title(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (installedVersion != null) {
                KedgeBadge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text("已安装 v$installedVersion")
                }
            }

            val description = entry.summary.ifBlank { entry.description }
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = KedgeTextStyles.body(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                displayTriggers.forEach { trigger ->
                    KedgeBadge(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(if (trigger == CardManifest.TRIGGER_AI) "AI 触发" else "用户触发")
                    }
                }
                hubInfo?.let { info ->
                    KedgeBadge(
                        containerColor = if (info.priceCents <= 0) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(if (info.priceCents <= 0) "免费" else "¥${formatYuan(info.priceCents)}/次")
                    }
                    if (info.license.isNotBlank()) {
                        KedgeBadge(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) { Text(info.license) }
                    }
                    if (info.preferred) {
                        KedgeBadge(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) { Text("优选") }
                    }
                    if (info.votes > 0) {
                        KedgeBadge(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) { Text("▲ ${info.votes}") }
                    }
                }
            }

            Text(
                text = "${entry.engine} · ${entry.privilege} · ${entry.bridges.joinToString(", ").ifBlank { "L0" }}",
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KedgeProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("处理中…", style = KedgeTextStyles.body())
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (installedVersion != null) {
                    if (CardManifest.TRIGGER_USER in triggers) {
                        BigActionButton(
                            text = "运行卡片",
                            onClick = onRun,
                            container = MaterialTheme.colorScheme.primary.harmonizeWithPrimary(),
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    if (installedVersion != entry.version && entry.url.isNotBlank()) {
                        BigActionButton(
                            text = "更新到 v${entry.version}",
                            onClick = onUpdate,
                            container = MaterialTheme.colorScheme.primary.harmonizeWithPrimary(),
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButtonLike(text = "密钥", onClick = onSecretsClick, modifier = Modifier.weight(1f))
                        OutlinedButtonLike(text = "卸载", onClick = onUninstall, modifier = Modifier.weight(1f))
                    }
                    if (activated) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButtonLike(text = "发布到 Hub", onClick = onPublishClick, modifier = Modifier.weight(1f))
                            OutlinedButtonLike(text = "提交改进", onClick = onForkClick, modifier = Modifier.weight(1f))
                        }
                    }
                } else if (entry.url.isNotBlank()) {
                    BigActionButton(
                        text = "安装卡片",
                        onClick = onInstall,
                        container = MaterialTheme.colorScheme.primary.harmonizeWithPrimary(),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                if (hubInfo != null) {
                    OutlinedButtonLike(
                        text = if (activated) "投票 ▲" else "投票（需激活）",
                        onClick = onVote,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BigActionButton(
    text: String,
    onClick: () -> Unit,
    container: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pressBounce(interactionSource)
            .clip(AutoCornersShape(18.dp))
            .background(container)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KedgeTextStyles.title(),
            color = contentColor,
        )
    }
}

@Composable
private fun OutlinedButtonLike(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressBounce(interactionSource)
            .clip(AutoCornersShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.harmonizeWithPrimary())
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KedgeTextStyles.body(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PublishCardDialog(
    entry: CardIndexEntry,
    onDismiss: () -> Unit,
    onConfirm: (license: String, price: Double, changelog: String) -> Unit,
) {
    var license by remember(entry.name) { mutableStateOf("MIT") }
    var priceText by remember(entry.name) { mutableStateOf("0") }
    var changelog by remember(entry.name) { mutableStateOf("") }
    val price = priceText.toDoubleOrNull() ?: -1.0

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发布 ${entry.name} 到 Hub") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KedgeOutlinedTextFieldWithSlots(
                    value = license,
                    onValueChange = { license = it },
                    label = { Text("开源协议（MIT / Apache-2.0 / GPL-3.0…）") },
                    singleLine = true,
                    shape = AutoCornersShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                KedgeOutlinedTextFieldWithSlots(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("每次调用价格（元，0 = 免费）") },
                    singleLine = true,
                    shape = AutoCornersShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                KedgeOutlinedTextFieldWithSlots(
                    value = changelog,
                    onValueChange = { changelog = it },
                    label = { Text("更新说明") },
                    shape = AutoCornersShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "提交后进入审核，通过后可能成为首选版本。",
                    style = KedgeTextStyles.footnoteSmall(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            KedgeTextButton(
                onClick = { onConfirm(license, price, changelog) },
                enabled = license.isNotBlank() && price >= 0,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("提交发布")
            }
        },
        dismissButton = {
            KedgeTextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("取消") }
        },
    )
}

@Composable
private fun ForkCardDialog(
    entry: CardIndexEntry,
    parentVersion: String,
    onDismiss: () -> Unit,
    onConfirm: (parent: String, newVersion: String, changelog: String) -> Unit,
) {
    var parent by remember(entry.name) { mutableStateOf(parentVersion) }
    var newVersion by remember(entry.name) {
        mutableStateOf(
            parentVersion.split(".").let { parts ->
                if (parts.size == 3) "${parts[0]}.${parts[1]}.${(parts[2].toIntOrNull() ?: 0) + 1}"
                else "1.0.1"
            }
        )
    }
    var changelog by remember(entry.name) { mutableStateOf("") }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提交改进 ${entry.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KedgeOutlinedTextFieldWithSlots(
                    value = parent,
                    onValueChange = { parent = it },
                    label = { Text("父版本号") },
                    singleLine = true,
                    shape = AutoCornersShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                KedgeOutlinedTextFieldWithSlots(
                    value = newVersion,
                    onValueChange = { newVersion = it },
                    label = { Text("新版本号（默认补丁位 +1）") },
                    singleLine = true,
                    shape = AutoCornersShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                KedgeOutlinedTextFieldWithSlots(
                    value = changelog,
                    onValueChange = { changelog = it },
                    label = { Text("更新说明（必填）") },
                    shape = AutoCornersShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            KedgeTextButton(
                onClick = { onConfirm(parent, newVersion, changelog) },
                enabled = changelog.isNotBlank() && newVersion.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("提交")
            }
        },
        dismissButton = {
            KedgeTextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("取消") }
        },
    )
}

private fun formatYuan(cents: Long): String {
    val yuan = cents / 100.0
    return if (cents % 100 == 0L) yuan.toLong().toString()
    else "%.2f".format(yuan).trimEnd('0').trimEnd('.')
}
