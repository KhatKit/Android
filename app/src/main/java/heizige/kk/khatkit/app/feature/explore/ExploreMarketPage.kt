package heizige.kk.khatkit.app.feature.explore

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import heizige.kk.khatkit.app.core.ui.components.ui.activeNestedScroll
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import heizige.kk.khatkit.app.core.ui.components.ui.RikkaConfirmDialog
import heizige.kk.kedge.overlays.KedgeModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.components.KedgeFilterChip
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import heizige.kk.kedge.components.KedgeBadge
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.autoAwesome
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.build
import heizige.kk.khatkit.app.core.ui.icons.calendarMonth
import heizige.kk.khatkit.app.core.ui.icons.chevronRight
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.contentPaste
import heizige.kk.khatkit.app.core.ui.icons.dashboard
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.downloadForOffline
import heizige.kk.khatkit.app.core.ui.icons.deleteForever
import heizige.kk.khatkit.app.core.ui.icons.extension
import heizige.kk.khatkit.app.core.ui.icons.folderOpen
import heizige.kk.khatkit.app.core.ui.icons.memory
import heizige.kk.khatkit.app.core.ui.icons.package2
import heizige.kk.khatkit.app.core.ui.icons.payments
import heizige.kk.khatkit.app.core.ui.icons.psychology
import heizige.kk.khatkit.app.core.ui.icons.questionAnswer
import heizige.kk.khatkit.app.core.ui.icons.rocketLaunch
import heizige.kk.khatkit.app.core.ui.icons.schedule
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.icons.timer
import heizige.kk.khatkit.app.core.ui.icons.travelExplore
import heizige.kk.khatkit.app.core.ui.icons.volumeUp
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.util.minus
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.dependency.DependencyCacheEntry
import heizige.kk.khromia.helper.Toast
import heizige.kk.khromia.components.pressBounce
import heizige.kk.khromia.components.shape.AutoCornersShape
import heizige.kk.khromia.data.harmonizeWithPrimary
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.card.Semver
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.HubCardMarketInfo
import heizige.kk.khatkit.hub.HubResourceEntry
import heizige.kk.khatkit.uikit.KhatKitSecretsDialog
import heizige.kk.khatkit.uikit.KhatKitTheme
import heizige.kk.khatkit.uikit.KhatKitUiStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import heizige.kk.kedge.adaptive.KedgePageScaffold
import heizige.kk.kedge.adaptive.LocalKedgePageScrollBehavior
import heizige.kk.kedge.adaptive.miuixScrollBehavior
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeMiuixMorphingTitleBar
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.hooks.rememberSearchExpandState
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.kedge.theme.KedgeStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.overlays.KedgePullToRefreshBox
import kotlin.math.abs

private enum class ExploreFilter(val label: String) {
    ALL("全部"),
    CARDS("卡片"),
    SKILLS("技能"),
    MCP("MCP"),
    BUNDLES("合集"),
    MODELS("模型能力"),
}

private enum class CardFilter(val label: String) {
    ALL("全部"),
    INSTALLED("已安装"),
    FREE("免费"),
    PREFERRED("推荐"),
    AI("AI 触发"),
}

/**
 * 合并后的单行筛选轨道：内容类型 + 卡片筛选原本是上下两行 chip，现在合成一行。
 *
 * 每个 chip 自带它要同时写进的两个维度：「已安装 / 免费 / 推荐 / AI 触发」属于卡片筛选，
 * 选中时顺手把内容类型收到「卡片」—— 于是「筛了卡片却还铺着工具、技能、MCP」这种
 * 半生效状态不再存在，一行里任何时刻恰好一个 chip 是选中的。
 */
private enum class MarketChip(
    val label: String,
    val filter: ExploreFilter,
    val cardFilter: CardFilter,
    val icon: ImageVector,
) {
    ALL("全部", ExploreFilter.ALL, CardFilter.ALL, dashboard),
    CARDS("卡片", ExploreFilter.CARDS, CardFilter.ALL, extension),
    SKILLS("技能合集", ExploreFilter.SKILLS, CardFilter.ALL, autoAwesome),
    MCP("MCP 服务", ExploreFilter.MCP, CardFilter.ALL, dns),
    BUNDLES("合集包", ExploreFilter.BUNDLES, CardFilter.ALL, package2),
    MODELS("模型能力", ExploreFilter.MODELS, CardFilter.ALL, memory),
    INSTALLED("已安装", ExploreFilter.CARDS, CardFilter.INSTALLED, downloadForOffline),
    FREE("免费", ExploreFilter.CARDS, CardFilter.FREE, payments),
    PREFERRED("推荐", ExploreFilter.CARDS, CardFilter.PREFERRED, rocketLaunch),
    AI("AI 触发", ExploreFilter.CARDS, CardFilter.AI, bolt),
}

/**
 * 一级探索页最多铺几张卡片：1 张全宽主推 + 2 行两栏。
 * 剩下的交给二级「卡片市场」页（[ExploreCardsPage]）惰性加载，
 * 避免首屏一次性组合全量卡片。
 */
private const val CARD_PREVIEW_COUNT = 5

private data class LocalToolInfo(
    val option: LocalToolOption,
    val seed: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
)

private val localToolCatalog = listOf(
    LocalToolInfo(LocalToolOption.JavascriptEngine, "javascript_engine", "JavaScript 引擎", "QuickJS 执行代码，计算与脚本处理", code),
    LocalToolInfo(LocalToolOption.TimeInfo, "time_info", "时间信息", "获取日期、星期、时间与时区", schedule),
    LocalToolInfo(LocalToolOption.Clipboard, "clipboard", "剪贴板", "读写设备剪贴板中的纯文本", contentPaste),
    LocalToolInfo(LocalToolOption.Tts, "tts", "文字转语音", "用所选 TTS 服务朗读文本", volumeUp),
    LocalToolInfo(LocalToolOption.AskUser, "ask_user", "询问用户", "AI 向你提问以获取更多信息", questionAnswer),
    LocalToolInfo(LocalToolOption.ScreenTime, "screen_time", "屏幕时间", "读取应用屏幕使用时间", timer),
    LocalToolInfo(LocalToolOption.Calendar, "calendar", "日历", "查询和创建日历事件", calendarMonth),
)

// Stable extension identity: this mirrors KodeHeap's avatar palette hashing.
// 身份色只负责「区分彼此」，不负责抢眼：整组压到低饱和度中明度（莫兰迪调），
// 这样十几个图标排在一起不会变成调色盘，浅色/深色主题下都还看得清。
private val extensionPalette = listOf(
    Color(0xFFC27B7B), Color(0xFFA98BC4), Color(0xFF7FA6C9), Color(0xFF7FB5AB),
    Color(0xFF9CB07C), Color(0xFFC9A06A), Color(0xFFB09287), Color(0xFF8B9BAA),
)

private val extensionShapes = listOf(
    MaterialShapes.Circle,
    MaterialShapes.Arch,
    MaterialShapes.Diamond,
    MaterialShapes.Pill,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Puffy,
    MaterialShapes.Sunny,
    MaterialShapes.Triangle,
    MaterialShapes.Gem,
    MaterialShapes.SoftBurst,
)

private fun extensionColor(seed: String): Color =
    extensionPalette[abs(seed.hashCode()) % extensionPalette.size]

@Composable
private fun ExtensionBadge(seed: String, label: String, icon: ImageVector) {
    val index = abs(seed.hashCode()) % extensionPalette.size
    val color = extensionPalette[index]
    Row(
        modifier = Modifier
            .clip(extensionShapes[index].toShape())
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = color)
        Text(label, style = KedgeTextStyles.footnoteSmall(), color = color)
    }
}

@Composable
private fun ExtensionIcon(
    seed: String,
    icon: ImageVector,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val index = abs(seed.hashCode()) % extensionPalette.size
    val color = extensionPalette[index]
    Box(
        modifier = Modifier
            .size(size)
            .clip(extensionShapes[index].toShape())
            .background(color.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.52f),
            tint = color,
        )
    }
}

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
    val resources by vm.resources.collectAsStateWithLifecycle()
    val busyResource by vm.busyResource.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    // 搜索展开态：入口是顶栏右上角的搜索图标（不再常驻搜索药丸）。
    var searchExpanded by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(ExploreFilter.ALL.name) }
    var cardFilter by rememberSaveable { mutableStateOf(CardFilter.ALL.name) }
    val selectedFilter = remember(filter) { ExploreFilter.valueOf(filter) }
    val selectedCardFilter = remember(cardFilter) { CardFilter.valueOf(cardFilter) }
    // 单行轨道里选中的那一格：两个维度都相等才算数，保证任何时候只有一个 chip 亮。
    val selectedChip = remember(selectedFilter, selectedCardFilter) {
        MarketChip.entries.firstOrNull {
            it.filter == selectedFilter && it.cardFilter == selectedCardFilter
        } ?: MarketChip.ALL
    }
    // 合并前两行是各自独立的，存档（rememberSaveable）里可能停在「卡片筛选生效、内容类型
    // 却不是卡片」这种新 UI 点不出来的组合 —— 恢复时收进「卡片」，否则会出现一整行没一个选中。
    LaunchedEffect(Unit) {
        if (selectedCardFilter != CardFilter.ALL && selectedFilter != ExploreFilter.CARDS) {
            filter = ExploreFilter.CARDS.name
        }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val density = LocalDensity.current
    // 悬浮筛选栏的钉线（px→dp）：折叠态顶栏的下沿。含义、算法与为什么必须这么做见
    // KedgePageScaffold content 里的那段注释；marketList 里靠它把 stickyHeader 的钉线
    // 从屏幕顶端挪到顶栏下面。初值 0 = 改动前的行为。
    var filterPinLine by remember { mutableStateOf(0.dp) }
    // 页面列表的滚动状态。唯一用途是判断悬浮筛选栏有没有「钉住」（见 marketList 里的
    // [chipsDocked]）—— 原来 LazyColumn 用内部默认 state，页面拿不到这个信号。
    // marketList 只有一处调用点（搜索结果也在同一个列表里），所以可以共用这一份。
    val marketListState = rememberLazyListState()

    // 搜索展开进度：与 ChatPage 同一套（rememberSearchExpandState 内部接预测性返回，
    // 系统返回手势跟手收起）。折叠态标题、展开态输入框、右上角 search↔close 图标
    // 全部按这个进度交叉淡化。
    val searchExpand = rememberSearchExpandState(
        expanded = searchExpanded,
        onCollapse = {
            searchExpanded = false
            // 收起时清空关键字，否则列表留在过滤态却没有可见输入框
            if (query.isNotBlank()) {
                query = ""
                vm.refresh("")
            }
        },
    )
    val searchProgress = searchExpand.progress
    val searchVisible by remember { derivedStateOf { searchProgress.value > 0.001f } }
    val titleVisible by remember { derivedStateOf { searchProgress.value < 0.999f } }
    val searchFocus = remember { FocusRequester() }
    // MD3 的 LargeFlexibleTopAppBar 内部是 TwoRowsTopAppBar，titleContent 同一份 lambda
    // 会被渲染两遍（收起态小标题行 + 展开态大标题行），FocusRequester 只能挂一份：
    // 挂错那份 = 光标落进看不见的副本。这里记下打开搜索那一刻可见的是哪一行。
    var focusLargeTitleRow by remember { mutableStateOf(true) }
    // 组合局部量必须先在这里读出来：LaunchedEffect 的 lambda 不是 @Composable 上下文
    val isMiuixStyle = LocalKedgeStyle.current == KedgeStyle.Miuix
    // 页面底色 = surface（Miuix / MD3Exp 由 [CustomColors.pageContainerColor] 统一给）。
    // 不能取 [CustomColors.topBarColors]：那是 surfaceContainer，比 surface 深一档，
    // 页面底色会比正文暗、还跟顶栏同色，整页糊成一块。
    // 悬浮筛选栏（stickyHeader）与整页必须同源同色，所以在这里算一次、两处共用。
    val pageSurface = CustomColors.pageContainerColor
    // 悬浮筛选栏「钉住之后」的底色 = 顶栏滚动后的容器色（= surfaceContainer；Miuix 下顶栏与
    // 页面同色，这时两个值相等、切换是无效果的 no-op）。取 scrolledContainerColor 而不是
    // containerColor：钉住就代表页面处于滚动中，要跟的正是顶栏此刻显示的那一档。
    // 两个色都不透明，正文从下面滚过去始终被实色盖住。
    val chipDockedSurface = CustomColors.topBarColors.scrolledContainerColor
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            if (!isMiuixStyle) {
                focusLargeTitleRow = scrollBehavior.state.collapsedFraction < 0.5f
            }
            // 等展开动画起手再抢焦点，直接 requestFocus() 会把入场动画顶掉
            delay(100)
            searchFocus.requestFocus()
        }
    }

    var secretCard by remember { mutableStateOf<String?>(null) }
    var selectedCard by remember { mutableStateOf<CardIndexEntry?>(null) }
    // 轮播卡「已安装 → 卸载」的待确认目标。非空时页面根部弹确认框。
    var uninstallTarget by remember { mutableStateOf<CardIndexEntry?>(null) }
    var publishTarget by remember { mutableStateOf<CardIndexEntry?>(null) }
    var forkTarget by remember { mutableStateOf<CardIndexEntry?>(null) }
    // 资源市场里被点开看详情的合集（技能合集 / MCP 服务 / 合集包）。
    var resourceTarget by remember { mutableStateOf<HubResourceEntry?>(null) }
    var uiStyle by remember { mutableStateOf(vm.uiStyle) }

    val enabledProviders = remember(settings) { settings.providers.filter { it.enabled } }
    val models = remember(enabledProviders) { enabledProviders.flatMap { p -> p.models.map { p.name to it } } }
    val mcpServers = remember(settings) { settings.mcpServers }
    val currentAssistantLocalTools = remember(settings) {
        settings.assistants.find { it.id == settings.assistantId }?.localTools?.toSet().orEmpty()
    }

    fun matches(text: String): Boolean = query.isBlank() || text.contains(query, ignoreCase = true)

    /**
     * 「装没装过」的本地判定：只比对包内条目名 —— 技能看本机技能目录，MCP 看设置里的
     * 服务器名。不下载就能在列表上显示状态；服务端改名后最坏是显示成未安装（可重装），
     * 不会把已装内容判成别的合集。
     */
    val installedSkillNames = remember(skills) { skills.map { it.name }.toSet() }
    val installedMcpNames = remember(mcpServers) {
        mcpServers.map { it.commonOptions.name }.filter { it.isNotBlank() }.toSet()
    }
    fun resourceInstalled(entry: HubResourceEntry): Boolean =
        entry.items.isNotEmpty() && entry.items.all { it in installedSkillNames || it in installedMcpNames }

    fun installResource(entry: HubResourceEntry) {
        vm.installResource(entry) { message ->
            scope.launch { Toast.show(message, message.startsWith("下载失败")) }
        }
    }

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
        cards.sortedWith(
            compareByDescending<CardIndexEntry> { hubInfo[it.name]?.preferred == true }
                .thenByDescending { hubInfo[it.name]?.votes ?: 0 }
        ).take(8)
    }
    val localOnly = remember(installed, cards) {
        installed.keys.filterNot { name -> cards.any { it.name == name } }.sorted()
    }

    // 市场内容：折叠态挂在页面列表里，展开态搬进搜索框的全屏槽，所以抽成 lambda 复用。
    //
    // 版面顺序（自上而下）：轮播 → 主筛选 → 本地入口 → 卡片网格 → 技能合集 →
    // MCP 服务 → 合集包 → 模型能力 → 原生依赖包（收尾的家务区）→ 空状态。
    // 「原生依赖包」原本压在卡片网格前面，属于维护型信息，挡住发现型内容，故挪到最后。
    //
    // 本机内容（本机卡片 / 本地工具 / 已装技能与 MCP）不再平铺在这一页：这一页只负责
    // **发现与下载**，本机有什么去「本地」二级页看。
    val marketList: @Composable (Modifier, PaddingValues) -> Unit = { listModifier, listPadding ->
        // 列表本身不再提供左右内边距（轮播要铺满整屏），改由每个分区自己带一份 [sectionPad]：
        // LazyRow / Column 加上它之后的 viewport 仍是「16dp → 屏宽-16dp」，和原来逐像素等价。
        val sectionPad = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        // 精选轮播这一项是否出现（原来进到 content lambda 里才算）。它决定悬浮筛选栏在列表
        // 里的位号 [chipItemIndex]，而 sticky 有没有钉住又要靠这个位号判断，所以在
        // LazyColumn 之前先算出来。
        val cardsVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.CARDS
        val featuredPending = loading && featuredCards.isEmpty()
        val showFeatured = cardsVisible && matches("卡片 精选 推荐") &&
            (featuredCards.isNotEmpty() || featuredPending)
        // 悬浮筛选栏的位号：它前面只有「精选轮播」这一项；没有轮播时它自己就是首项。
        val chipItemIndex = if (showFeatured) 1 else 0
        // 「钉住了」= 列表已滚过 chip 栏的自然位置。sticky 项一旦顶上钉线就停在那儿不动，
        // 所以「它前面那一项完全滚出视口 / 它自己已经滚过 0」正好就是钉住的那一刻 ——
        // 不写死任何高度，轮播高度、字体缩放、风格切换都不影响判定。
        // 必须 derivedStateOf：滚动每帧都在改 firstVisibleItem*，直接读会让整页重组。
        val chipsDocked by remember(marketListState, chipItemIndex) {
            derivedStateOf {
                val index = marketListState.firstVisibleItemIndex
                index > chipItemIndex ||
                    (index == chipItemIndex && marketListState.firstVisibleItemScrollOffset > 0)
            }
        }
        LazyColumn(
            state = marketListState,
            // 列表整体下移「折叠态顶栏高度」，剩下的顶边距继续交给 contentPadding（见下）。
            // 两者之和恒等于调用方给的 listPadding.top，所以正文位置与改动前逐像素相同；
            // 真正变的只有 stickyHeader 的钉线 —— 从列表顶边（= 屏幕顶端、顶栏背后）
            // 挪到了列表顶边 + 这段下移 = 顶栏下沿。见 [filterPinLine]。
            modifier = listModifier.padding(top = filterPinLine),
            contentPadding = listPadding - PaddingValues(top = filterPinLine),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            val skillsVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.SKILLS
            val mcpVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.MCP
            val bundlesVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.BUNDLES
            val modelsVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.MODELS

            val cardsMatched = filteredCards.filter { matches(it.name + it.summary + it.description) }
            // 资源市场：按 kind 分三块，搜索命中名称 / 描述 / 标签 / 包内条目名。
            val matchedResources = resources.filter {
                matches(it.name + it.description + it.tags.joinToString(" ") + it.items.joinToString(" "))
            }
            val visibleSkillPacks = matchedResources.filter { it.isSkillPack }
            val visibleMcpServers = matchedResources.filter { it.isMcp }
            val visibleBundles = matchedResources.filter { it.isBundle }
            val visibleModels = models.filter { (providerName, model) ->
                matches(providerName + model.displayName + model.modelId)
            }
            val anyContent = when (selectedFilter) {
                ExploreFilter.ALL -> cardsMatched.isNotEmpty() || localOnly.isNotEmpty() ||
                    matchedResources.isNotEmpty() || visibleModels.isNotEmpty()
                ExploreFilter.CARDS -> cardsMatched.isNotEmpty() || localOnly.isNotEmpty() || dependencies.isNotEmpty()
                ExploreFilter.SKILLS -> visibleSkillPacks.isNotEmpty()
                ExploreFilter.MCP -> visibleMcpServers.isNotEmpty()
                ExploreFilter.BUNDLES -> visibleBundles.isNotEmpty()
                ExploreFilter.MODELS -> visibleModels.isNotEmpty()
            }

            // 预加载骨架的判定：**按分区各自判**，不看 anyContent。
            // 资源目录是网络来的，慢的时候整块都在自己那一格原位上铺同形骨架。
            // featuredPending 已在 LazyColumn 之前算过（chip 位号要用），此处不再重复。
            val cardsPending = loading && cardsMatched.isEmpty()
            val skillPacksPending = loading && visibleSkillPacks.isEmpty()
            val mcpMarketPending = loading && visibleMcpServers.isEmpty()
            val bundlesPending = loading && visibleBundles.isEmpty()
            val modelsPending = loading && visibleModels.isEmpty()
            val depsPending = loading && dependencies.isEmpty()

            // 1. 精选轮播：无标题，直接作为首屏视觉焦点（全页唯一的一级标题留给卡片网格）。
            // 出现条件 [showFeatured] 在 LazyColumn 之前算过（chip 栏位号依赖它）。
            if (showFeatured) {
                item(key = "featured") {
                    // 骨架 → 真实轮播：同一个位号上交叉淡入淡出，别让整屏最重的这块硬切。
                    Crossfade(
                        targetState = featuredPending,
                        animationSpec = tween(durationMillis = 320),
                        label = "featured_swap",
                    ) { pending ->
                    if (pending) {
                        FeaturedCarouselSkeleton()
                    } else {
                        // 全页唯一铺满整屏的 item：不再被列表的左右 16dp 包住，只留轮播自己的
                        // contentPadding —— 相邻卡从屏幕边缘露出来，滑动提示比「缩在中间」强得多。
                        // 上/左/右 16dp，与紧随其后的悬浮筛选栏同一套边距：横向 padding 由
                        // keyline 策略吃（决定相邻卡露多少），纵向落到 Pager 的 contentPadding，
                        // 卡本体高度 208dp 不变。下边不再留白 —— 下面就是筛选栏，再垫 16dp
                        // 会让 chip 栏的上下边距比两侧大一倍，看着孤零零地吊在中间。
                        val carouselState = rememberCarouselState { featuredCards.size }
                        HorizontalMultiBrowseCarousel(
                            state = carouselState,
                            preferredItemWidth = 264.dp,
                            itemSpacing = 12.dp,
                            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) { page ->
                            Box(
                                modifier = Modifier
                                    .height(208.dp)
                                    .maskClip(AutoCornersShape(ExploreShape.carousel))
                            ) {
                                FeaturedCard(
                                    entry = featuredCards[page],
                                    installedVersion = installed[featuredCards[page].name],
                                    hubInfo = hubInfo[featuredCards[page].name],
                                    busy = busyCard == featuredCards[page].name,
                                    onClick = { selectedCard = featuredCards[page] },
                                    onInstall = {
                                        // 装了但不是最新 → 覆盖安装（force），没装过 → 正常安装。
                                        val target = featuredCards[page]
                                        if (installed[target.name] == null) {
                                            vm.install(target) { ok ->
                                                scope.launch {
                                                    Toast.show(if (ok) "已安装 ${target.name}" else "安装失败", !ok)
                                                }
                                            }
                                        } else {
                                            vm.update(target) { ok ->
                                                scope.launch {
                                                    Toast.show(if (ok) "已更新 ${target.name}" else "更新失败", !ok)
                                                }
                                            }
                                        }
                                    },
                                    // 已是最新版时药丸变成卸载入口：先弹确认框，别让一次误触就删掉本机卡片。
                                    onUninstall = { uninstallTarget = featuredCards[page] },
                                )
                            }
                        }
                    }
                    }
                }
            }

            // 2. 筛选：内容类型 + 卡片筛选合并成同一条 chip 轨道（原本是上下两行 + 一个
            // 「卡片筛选」小标题）。两行各有一个「全部」，看着就是两套并列的筛选；合并后
            // 只剩一条轨道、一个「全部」，行内不再有分组标题。
            //
            // 悬浮栏：stickyHeader 把这条轨道钉住，正文从它下面滚过去。钉在哪条线上由
            // [filterPinLine] 决定（= 折叠态顶栏下沿），不是列表默认的「屏幕顶端」——
            // 后者在顶栏背后，等于没悬浮。以前它是普通 item，往上滚就被顶栏盖掉、
            // 滑出屏幕，想换筛选条件得先滚回顶部。底铺页面底色，滚过去的内容被实色盖住。
            //
            // 底色跟着「钉没钉住」走（[chipsDocked]）：一钉住就换成顶栏色 [chipDockedSurface]，
            // 和上面的顶栏连成一条实色带；滚回列表顶部再变回页面底色 [pageSurface]。
            // 用 animateColorAsState 而不是硬切：两色只差一档，渐变才不显跳。
            stickyHeader(key = "filter_chips") {
                val chipBarSurface by animateColorAsState(
                    targetValue = if (chipsDocked) chipDockedSurface else pageSurface,
                    animationSpec = tween(durationMillis = 200),
                    label = "filter_chip_bar_surface",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(chipBarSurface)
                        .padding(vertical = 4.dp),
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        // 边距与轮播同一套（轮播卡也是 16dp 起）。用 contentPadding 而不是
                        // 外层 padding：chip 能像卡片一样滑到屏幕边缘，不被盒子夹断。
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(MarketChip.entries, key = { it.name }) { chip ->
                            MarketFilterChip(
                                selected = chip == selectedChip,
                                label = chip.label,
                                icon = chip.icon,
                                onClick = {
                                    filter = chip.filter.name
                                    cardFilter = chip.cardFilter.name
                                },
                            )
                        }
                    }
                }
            }

            // 2b. 「本地」入口：本机内容与已安装内容有自己的二级页，这里只放一行入口。
            // 排在悬浮筛选栏之后 —— 它随正文一起滚走，不会像筛选栏那样钉在顶栏下面；
            // 轮播仍在首屏，入口只是它上面一条细带。
            if (matches("本地 本机 local 已安装 离线")) {
                item(key = "local_entry") {
                    Box(modifier = swapIn(sectionPad)) {
                        GroupContainer {
                            LocalEntryRow(
                                installedCount = installed.size,
                                localCount = localOnly.size,
                                toolCount = localToolCatalog.size,
                                toolEnabledCount = localToolCatalog.count {
                                    it.option in currentAssistantLocalTools
                                },
                                skillCount = skills.size,
                                mcpCount = mcpServers.size,
                                onClick = { navController.navigate(Screen.ExploreLocal) },
                            )
                        }
                    }
                }
            }

            // 3b. 卡片列表骨架：与「SectionHeader + 单列全宽卡」同形，只在这块还没到货时占位。
            // 已有缓存时刷新不铺骨架，交给上面的下拉指示器，避免每次刷新都把列表整块抽掉。
            if (cardsVisible && cardsPending) {
                item(key = "cards_skeleton") {
                    Column(modifier = swapIn(sectionPad)) {
                        SkeletonBlock(
                            Modifier.width(190.dp).height(20.dp),
                            AutoCornersShape(ExploreShape.tile),
                        )
                        Spacer(Modifier.height(14.dp))
                        CardGridSkeleton()
                    }
                }
            }

            if (cardsVisible && cardsMatched.isNotEmpty()) {
                item(key = "cards") {
                    Column(modifier = swapIn(sectionPad)) {
                        SectionHeader(
                            title = stringResource(R.string.explore_market_external_cards),
                            icon = package2,
                            // 不再带「共 N 张 · 已安装 M」：这一行里标题是 displayTitle 字阶，
                            // 再挤一个计数加文字按钮后标题只剩 ~90dp，实测被截成「外部…」。
                            // 计数在二级页顶部原样有一条（同一份数），这里让标题占满、不再截断。
                            actionLabel = "查看全部",
                            onAction = { navController.navigate(Screen.ExploreCards(query)) },
                        )
                        Spacer(Modifier.height(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 一级页只铺 preview 张，全量列表按需进二级页（卡片市场）。
                            //
                            // 单列：此前是「1 张全宽主推 + 两栏窄卡」的 1 + N 网格，一屏里两套
                            // 卡面尺寸，视线要在大小卡之间来回换，窄卡里的摘要还被挤成两行。
                            // 现在整区一行一张、全宽到底（同 ImageToolbox 首页那份工具列表的做法，
                            // 也与本页二级页的列表同款），只从下往上读一列，摘要不再被宽度压扁。
                            // 主推位由上面的轮播承担，这里不再需要一张放大的卡。
                            val preview = cardsMatched.take(CARD_PREVIEW_COUNT)
                            preview.forEach { entry ->
                                CompactCard(
                                    entry = entry,
                                    installedVersion = installed[entry.name],
                                    triggers = triggers[entry.name] ?: entry.triggers,
                                    hubInfo = hubInfo[entry.name],
                                    busy = busyCard == entry.name,
                                    onClick = { selectedCard = entry },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            // 还有剩的才给入口：卡不多时点进去是个和这页一样的列表。
                            val remaining = cardsMatched.size - preview.size
                            if (remaining > 0) {
                                MarketMoreRow(
                                    label = "查看全部 ${cardsMatched.size} 张卡片",
                                    onClick = { navController.navigate(Screen.ExploreCards(query)) },
                                )
                            }
                        }
                    }
                }
            }

            // 4. 技能合集：服务端把一组 SKILL.md 打包下发，装完直接进本机技能库。
            if (skillsVisible && skillPacksPending) {
                item(key = "skill_packs_skeleton") {
                    Box(modifier = swapIn(sectionPad)) { SectionSkeleton(rows = 3) }
                }
            }

            if (skillsVisible && visibleSkillPacks.isNotEmpty()) {
                item(key = "skill_packs") {
                    Column(modifier = swapIn(sectionPad), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel(
                            title = "技能合集",
                            icon = autoAwesome,
                            supporting = "${visibleSkillPacks.size} 个合集可下载",
                            actionLabel = "已装技能",
                            onAction = { navController.navigate(Screen.Skills) },
                        )
                        GroupContainer {
                            visibleSkillPacks.forEach { entry ->
                                ResourceRow(
                                    entry = entry,
                                    installed = resourceInstalled(entry),
                                    busy = busyResource == entry.slug,
                                    onInstall = { installResource(entry) },
                                    onClick = { resourceTarget = entry },
                                )
                            }
                        }
                    }
                }
            }

            // 5. MCP 服务：一条目 = 一组远端 MCP 端点，装进设置并挂到当前助手。
            if (mcpVisible && mcpMarketPending) {
                item(key = "mcp_market_skeleton") {
                    Box(modifier = swapIn(sectionPad)) { SectionSkeleton(rows = 3) }
                }
            }

            if (mcpVisible && visibleMcpServers.isNotEmpty()) {
                item(key = "mcp_market") {
                    Column(modifier = swapIn(sectionPad), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel(
                            title = "MCP 服务",
                            icon = dns,
                            supporting = "${visibleMcpServers.size} 个服务可添加",
                            actionLabel = "已添加",
                            onAction = { navController.navigate(Screen.SettingMcp) },
                        )
                        GroupContainer {
                            visibleMcpServers.forEach { entry ->
                                ResourceRow(
                                    entry = entry,
                                    installed = resourceInstalled(entry),
                                    busy = busyResource == entry.slug,
                                    onInstall = { installResource(entry) },
                                    onClick = { resourceTarget = entry },
                                )
                            }
                        }
                    }
                }
            }

            // 6. 合集包：技能 + MCP + 卡片打在一起，一次装齐。
            if (bundlesVisible && bundlesPending) {
                item(key = "bundles_skeleton") {
                    Box(modifier = swapIn(sectionPad)) { SectionSkeleton(rows = 2) }
                }
            }

            if (bundlesVisible && visibleBundles.isNotEmpty()) {
                item(key = "bundles") {
                    Column(modifier = swapIn(sectionPad), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel(
                            title = "合集包",
                            icon = package2,
                            supporting = "${visibleBundles.size} 个合集 · 技能与 MCP 一次装齐",
                        )
                        GroupContainer {
                            visibleBundles.forEach { entry ->
                                ResourceRow(
                                    entry = entry,
                                    installed = resourceInstalled(entry),
                                    busy = busyResource == entry.slug,
                                    onInstall = { installResource(entry) },
                                    onClick = { resourceTarget = entry },
                                )
                            }
                        }
                    }
                }
            }

            if (modelsVisible && modelsPending) {
                item(key = "models_skeleton") {
                    Box(modifier = swapIn(sectionPad)) { SectionSkeleton(rows = 3) }
                }
            }

            if (modelsVisible && visibleModels.isNotEmpty()) {
                item(key = "models") {
                    Column(modifier = swapIn(sectionPad), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel(
                            title = "模型能力",
                            icon = autoAwesome,
                            supporting = if (visibleModels.size > 8) {
                                "${visibleModels.size} 个 · 显示前 8 个 · ${enabledProviders.size} 个供应商"
                            } else {
                                "${visibleModels.size} 个模型 · ${enabledProviders.size} 个供应商"
                            },
                            actionLabel = "管理",
                            onAction = { navController.navigate(Screen.SettingModels) },
                        )
                        GroupContainer {
                            visibleModels.take(8).forEach { (providerName, model) ->
                                ModelRow(
                                    providerName = providerName,
                                    model = model,
                                    onClick = { navController.navigate(Screen.SettingModels) },
                                )
                            }
                        }
                    }
                }
            }

            if (cardsVisible && depsPending) {
                item(key = "dependency_cache_skeleton") {
                    Box(modifier = swapIn(sectionPad)) { SectionSkeleton(rows = 2) }
                }
            }

            if (cardsVisible && dependencies.isNotEmpty()) {
                item(key = "dependency_cache") {
                    Column(modifier = swapIn(sectionPad), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel(
                            title = "原生依赖包",
                            icon = heizige.kk.khatkit.app.core.ui.icons.extension,
                            supporting = "已缓存 ${dependencies.size} 个版本",
                            actionLabel = "清空",
                            onAction = {
                                vm.clearDependencyCache { count ->
                                    scope.launch {
                                        Toast.show(if (count > 0) "已清理 $count 个依赖包" else "没有可清理的依赖包")
                                    }
                                }
                            },
                        )
                        GroupContainer {
                            dependencies.forEach { dependency ->
                                DependencyRow(
                                    dependency = dependency,
                                    onDelete = {
                                        vm.deleteDependency(dependency) { ok ->
                                            scope.launch {
                                                Toast.show(
                                                    if (ok) "已删除 ${dependency.name} ${dependency.version}" else "删除失败",
                                                    !ok,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // 页脚同步条：所有分区铺完之后收尾（参考稿的底部指示器）—— 还在拉数据时转圈，
            // 已经到底了只剩一行灰字，页面底部不会突然被切断。
            if (anyContent) {
                item(key = "footer_sync") {
                    ExploreFooterSync(loading = loading, modifier = sectionPad)
                }
            }

            // 空状态：按当前筛选解释「为什么空」，并给一条出路。
            if (!anyContent && !loading) {
                item(key = "empty") {
                    Box(modifier = sectionPad) {
                        ExploreEmptyState(
                            query = query,
                            scopeLabel = when (selectedFilter) {
                                ExploreFilter.ALL -> "市场"
                                ExploreFilter.SKILLS -> "技能合集"
                                ExploreFilter.MCP -> "MCP 服务"
                                ExploreFilter.BUNDLES -> "合集包"
                                ExploreFilter.MODELS -> "模型能力"
                                ExploreFilter.CARDS -> "卡片市场"
                            },
                            onClearQuery = {
                                query = ""
                                vm.refresh("")
                            },
                            onReload = { vm.refresh(query) },
                        )
                    }
                }
            }
        }
    }

    KedgePageScaffold(
        // 顶栏不再常驻搜索药丸：入口是右上角那个搜索图标，点开后才在**标题槽位**里
        // morph 出输入框（逻辑照 ChatPage）。所以这里只有一行大标题栏。
        topBar = {
            // 右上角 search↔close 图标：同一槽内交叉淡化，判定照 ChatPage ——
            // !searching → search；searching && 有关键字 → close；搜索态但还没输入时
            // 两边都不命中（槽留空）。
            val searching = searchProgress.value >= 0.5f
            val hasQuery = searching && query.isNotEmpty()
            val searchAction: @Composable () -> Unit = {
                KedgeIconButton(
                    onClick = {
                        when {
                            !searching -> searchExpanded = true
                            hasQuery -> {
                                query = ""
                                vm.refresh("")
                            }
                            else -> searchExpanded = false
                        }
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!searching) {
                            Icon(
                                imageVector = search,
                                contentDescription = "搜索",
                                modifier = Modifier.graphicsLayer {
                                    alpha = 1f - searchProgress.value
                                },
                            )
                        }
                        if (hasQuery) {
                            Icon(
                                imageVector = close,
                                contentDescription = "清空搜索",
                                modifier = Modifier.graphicsLayer {
                                    alpha = searchProgress.value
                                },
                            )
                        }
                    }
                }
            }
            val onExitSearch: () -> Unit = { searchExpanded = false }
            // 返回箭头：搜索态点它先退出搜索，否则才退出页面
            val navigationIcon: @Composable () -> Unit = {
                BackButton(
                    onClick = {
                        if (searchProgress.value >= 0.5f) onExitSearch() else navController.popBackStack()
                    },
                )
            }

            if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
                // Miuix：交给现成的 morphing 顶栏（ChatPage 用的同一个），标题/副标题
                // 与输入框按同一进度 morph。
                KedgeMiuixMorphingTitleBar(
                    title = stringResource(R.string.explore_market_title),
                    subtitle = stringResource(R.string.explore_market_subtitle),
                    expanded = searchExpanded,
                    keyword = query,
                    onKeywordChange = { new ->
                        // 清空时把远端结果集刷回全量，与原来的清除按钮一致
                        if (query.isNotBlank() && new.isEmpty()) vm.refresh("")
                        query = new
                    },
                    dragProgress = searchProgress.value,
                    // 不传 dragging，组件内部的 snapTo 会被门控掉（只剩 tween），
                    // 返回手势拖动就没有跟手反馈。
                    dragging = searchExpand.dragging.value,
                    onExitSearch = onExitSearch,
                    placeholder = stringResource(R.string.history_page_search),
                    navigationIcon = navigationIcon,
                    actions = { searchAction() },
                    focusRequester = searchFocus,
                )
            } else {
                KedgePageLargeTopBar(
                    title = stringResource(R.string.explore_market_title),
                    subtitle = stringResource(R.string.explore_market_subtitle),
                    navigationIcon = navigationIcon,
                    actions = { searchAction() },
                    scrollBehavior = scrollBehavior,
                    colors = CustomColors.topBarColors,
                    titleContent = {
                        // 标题与输入框共用同一个字阶 token：输入框是**替换**标题，
                        // 字号字色必须严格相等，否则展开时会看到「标题跳了一下」。
                        val titleStyle = KedgeTextStyles.displayTitle()
                        // 同一份 lambda 会被两行各渲染一次，用行内标题字阶区分是哪一行
                        val isLargeTitleRow = LocalTextStyle.current.fontSize >
                            MaterialTheme.typography.titleLarge.fontSize
                        Box {
                            if (titleVisible) {
                                Column(
                                    modifier = Modifier.graphicsLayer {
                                        alpha = 1f - searchProgress.value
                                        translationX = -searchProgress.value * 24.dp.toPx()
                                    },
                                ) {
                                    Text(
                                        text = stringResource(R.string.explore_market_title),
                                        style = titleStyle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(R.string.explore_market_subtitle),
                                        style = KedgeTextStyles.body(),
                                        color = LocalContentColor.current.copy(0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (searchVisible) {
                                // 用 searchVisible 而不是 searchExpanded：布尔先变 false、
                                // 进度还在补间时若已卸载输入框，退场动画会被腰斩。
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.65f + 0.35f * searchProgress.value)
                                        .graphicsLayer {
                                            alpha = searchProgress.value
                                            translationX = (1f - searchProgress.value) * 24.dp.toPx()
                                        },
                                ) {
                                    if (query.isBlank()) {
                                        Text(
                                            text = stringResource(R.string.history_page_search),
                                            style = titleStyle,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                    BasicTextField(
                                        value = query,
                                        onValueChange = { new ->
                                            if (query.isNotBlank() && new.isEmpty()) vm.refresh("")
                                            query = new
                                        },
                                        singleLine = true,
                                        textStyle = titleStyle.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            // 只挂到打开搜索那一刻可见的那一行
                                            .then(
                                                if (isLargeTitleRow == focusLargeTitleRow) {
                                                    Modifier.focusRequester(searchFocus)
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        // Miuix 下 KedgePageScaffold 已接 Miuix 折叠行为，再挂 MD3 连接会吃掉全部
        // 滚动增量（整页滑不动），所以按风格取。
        modifier = activeNestedScroll(scrollBehavior),
        // 页面底色见上面算好的 [pageSurface]：悬浮筛选栏与整页必须同色，只能有一份来源。
        containerColor = pageSurface,
    
        md3ScrollBehavior = scrollBehavior,
    ) { innerPadding ->
        // 悬浮筛选栏钉在哪条线上 —— 这是「chip 到底悬不悬浮」的全部机关。
        //
        // stickyHeader 只会把条目钉在 LazyColumn **自己**的顶边（foundation
        // LazyLayoutStickyItems.kt:100 把钉住的偏移算成 `-beforeContentPadding`，再加回
        // `visualItemOffset = topPadding`，正好落在列表顶边），而 Scaffold 的正文是从
        // (0,0) 铺满整屏的（material3 Scaffold.kt:281：`bodyContentPlaceables.place(0, 0)`），
        // 顶栏画在它上面。于是「把 innerPadding 全交给 contentPadding」这种最自然的写法，
        // 钉线落在屏幕顶端 —— 正好在顶栏背后，看起来就是「chip 压根没悬浮，一滚就没了」。
        //
        // 修法：列表整体下移一个「折叠态顶栏高度」（marketList 里那句
        // `listPadding - PaddingValues(top = filterPinLine)`），剩下的顶边距仍交给
        // contentPadding。正文位置 = 下移量 + contentPadding.top，两者之和不变，所以版面
        // 逐像素不动；变的只是列表顶边，钉线跟着落到顶栏下沿。
        //
        // 折叠态高度 = 展开高度 + heightOffsetLimit，展开高度 = 当前高度 - heightOffset，
        // 于是只用这两个现成的量，不写死 64dp / 88dp：拖动顶栏时 current 与 heightOffset
        // 反向等量变化，结果是个常量（本就该是常量）。heightOffset 每帧都在动，所以放在
        // LaunchedEffect 里读 —— 组合期读它会让整页跟着每一次滚动重组。
        val miuixBarState = LocalKedgePageScrollBehavior.current?.miuixScrollBehavior?.state
        LaunchedEffect(innerPadding) {
            val offset: Float
            val limit: Float
            if (miuixBarState != null) {
                offset = miuixBarState.heightOffset
                limit = miuixBarState.heightOffsetLimit
            } else {
                offset = scrollBehavior.state.heightOffset
                limit = scrollBehavior.state.heightOffsetLimit
            }
            // limit 没被顶栏量过时是 -Float.MAX_VALUE（TopAppBarState 的初值）：
            // 那种情况下退回 0，也就是改动前的行为，绝不把列表凭空推下去。
            val pinPx = if (limit.isFinite() && limit < 0f && limit > -10_000f) {
                (with(density) { innerPadding.calculateTopPadding().toPx() } - offset + limit)
                    .coerceAtLeast(0f)
            } else {
                0f
            }
            val pin = with(density) { pinPx.toDp() }
            if (pin != filterPinLine) filterPinLine = pin
        }
        Box(modifier = Modifier.fillMaxSize()) {
            // 搜索结果就在这同一个列表里（marketList 本来就按 query 过滤各分区），
            // 不像以前那样把正文抽掉、搬进搜索框的全屏槽。
            KedgePullToRefreshBox(
                isRefreshing = loading,
                onRefresh = { vm.refresh(query) },
                modifier = Modifier.fillMaxSize(),
            ) {
                marketList(
                    Modifier.fillMaxSize(),
                    // 只留纵向：横向 16dp 下沉到各分区自己身上，轮播才能铺满整屏。
                    innerPadding + PaddingValues(top = 8.dp, bottom = 32.dp),
                )
            }
        }
    }

    // 卡片详情弹层 + 发布/改进对话框：一级页与二级卡片市场页共用同一份实现
    // （两页都能点开卡片，动作语义必须完全一致）。
    CardMarketOverlays(
        vm = vm,
        uiStyle = uiStyle,
        installed = installed,
        triggers = triggers,
        hubInfo = hubInfo,
        activated = activated,
        busyCard = busyCard,
        refreshQuery = query,
        selectedCard = selectedCard,
        onSelectCard = { selectedCard = it },
        secretCard = secretCard,
        onSecretCard = { secretCard = it },
        publishTarget = publishTarget,
        onPublishTarget = { publishTarget = it },
        forkTarget = forkTarget,
        onForkTarget = { forkTarget = it },
    )

    // 资源合集详情：先看清里面有什么（技能名 / MCP 端点），再决定装不装。
    resourceTarget?.let { entry ->
        ResourceDetailSheet(
            entry = entry,
            installed = resourceInstalled(entry),
            busy = busyResource == entry.slug,
            onDismiss = { resourceTarget = null },
            onInstall = {
                resourceTarget = null
                installResource(entry)
            },
        )
    }

    // 轮播卡「已安装」药丸走卸载，删本机文件不可逆 → 一律先过确认框。
    // 详情抽屉里的「卸载」保持原样（那里已经是二级页面里的显式两段操作，不再加一层）。
    uninstallTarget?.let { target ->
        RikkaConfirmDialog(
            show = true,
            title = "卸载 ${target.name}",
            confirmText = "卸载",
            dismissText = "取消",
            onConfirm = {
                uninstallTarget = null
                vm.uninstall(target.name) { ok ->
                    scope.launch { Toast.show(if (ok) "已卸载 ${target.name}" else "卸载失败", !ok) }
                }
            },
            onDismiss = { uninstallTarget = null },
        ) {
            Text(
                "将从本机删除 ${target.name} v${installed[target.name] ?: target.version} 及其本地文件，" +
                    "之后需要重新安装才能使用。",
            )
        }
    }
}

/**
 * 卡片市场的浮层宿主：详情抽屉、密钥对话框、发布 / 提交改进对话框。
 *
 * 这些动作在一级探索页和二级「卡片市场」页都必须可用，所以状态留在调用方，
 * 这里只负责把 [ExploreMarketViewModel] 的动作接到 UI 上。
 */
@Composable
private fun CardMarketOverlays(
    vm: ExploreMarketViewModel,
    uiStyle: KhatKitUiStyle,
    installed: Map<String, String>,
    triggers: Map<String, List<String>>,
    hubInfo: Map<String, HubCardMarketInfo>,
    activated: Boolean,
    busyCard: String?,
    refreshQuery: String,
    selectedCard: CardIndexEntry?,
    onSelectCard: (CardIndexEntry?) -> Unit,
    secretCard: String?,
    onSecretCard: (String?) -> Unit,
    publishTarget: CardIndexEntry?,
    onPublishTarget: (CardIndexEntry?) -> Unit,
    forkTarget: CardIndexEntry?,
    onForkTarget: (CardIndexEntry?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val provider = rememberAppEntryPoint().khatKitToolProvider()

    KhatKitTheme(
        style = uiStyle,
        darkTheme = heizige.kk.khatkit.app.core.ui.hooks.rememberIsDarkTheme(),
    ) {
        secretCard?.let { cardName ->
            KhatKitSecretsDialog(
                cardName = cardName,
                controller = provider,
                onDismiss = { onSecretCard(null) },
                onToast = { message, isError -> Toast.show(message = message, isError = isError) },
            )
        }
    }

    publishTarget?.let { entry ->
        PublishCardDialog(
            entry = entry,
            onDismiss = { onPublishTarget(null) },
            onConfirm = { license, price, changelog ->
                onPublishTarget(null)
                vm.publish(entry.name, license, price, changelog) { result ->
                    scope.launch {
                        Toast.show(
                            result.message.ifBlank { if (result.ok) "已提交发布审核" else "发布失败" },
                            !result.ok,
                        )
                        vm.refresh(refreshQuery)
                    }
                }
            },
        )
    }

    forkTarget?.let { entry ->
        ForkCardDialog(
            entry = entry,
            parentVersion = installed[entry.name] ?: hubInfo[entry.name]?.version.orEmpty(),
            onDismiss = { onForkTarget(null) },
            onConfirm = { parent, newVersion, changelog ->
                onForkTarget(null)
                vm.fork(entry.name, parent, changelog, newVersion) { result ->
                    scope.launch {
                        Toast.show(
                            result.message.ifBlank { if (result.ok) "已提交改进" else "提交失败" },
                            !result.ok,
                        )
                        vm.refresh(refreshQuery)
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
            onDismiss = { onSelectCard(null) },
            onToast = { message, isError -> Toast.show(message = message, isError = isError) },
            onSecretsClick = {
                onSecretCard(entry.name)
                onSelectCard(null)
            },
            onPublishClick = {
                onPublishTarget(entry)
                onSelectCard(null)
            },
            onForkClick = {
                onForkTarget(entry)
                onSelectCard(null)
            },
            onRun = { vm.run(entry.name) { r -> scope.launch { Toast.show(r.message.ifBlank { if (r.ok) "已运行 ${entry.name}" else "运行失败" }, !r.ok) } } },
            onInstall = { vm.install(entry) { ok -> scope.launch { Toast.show(if (ok) "已安装 ${entry.name}" else "安装失败", !ok) } } },
            onUpdate = { vm.update(entry) { ok -> scope.launch { Toast.show(if (ok) "已更新 ${entry.name}" else "更新失败", !ok) } } },
            onUninstall = {
                vm.uninstall(entry.name) { ok ->
                    scope.launch {
                        Toast.show(if (ok) "已卸载 ${entry.name}" else "卸载失败", !ok)
                        if (ok) onSelectCard(null)
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

/**
 * 二级页面：卡片市场全量列表。
 *
 * 一级探索页只铺 [CARD_PREVIEW_COUNT] 张卡片，全量列表按需进这一页；
 * 列表用 LazyColumn 逐项惰性组合（一级页此前是把几十张卡塞进一个 `item` 里的 Column，
 * 一屏就全部组合完）。搜索 / 筛选语义与一级页完全一致，动作共用 [CardMarketOverlays]。
 *
 * [initialQuery] 由一级页带过来，避免「在一级页搜了词、点进二级页结果词丢了」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreCardsPage(initialQuery: String = "") {
    val vm = hiltViewModel<ExploreMarketViewModel>()

    val cards by vm.cards.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val triggers by vm.triggers.collectAsStateWithLifecycle()
    val hubInfo by vm.hubInfo.collectAsStateWithLifecycle()
    val activated by vm.activated.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val busyCard by vm.busyCard.collectAsStateWithLifecycle()
    val uiStyle = vm.uiStyle
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var cardFilter by rememberSaveable { mutableStateOf(CardFilter.ALL.name) }
    val selectedCardFilter = remember(cardFilter) { CardFilter.valueOf(cardFilter) }

    var selectedCard by remember { mutableStateOf<CardIndexEntry?>(null) }
    var secretCard by remember { mutableStateOf<String?>(null) }
    var publishTarget by remember { mutableStateOf<CardIndexEntry?>(null) }
    var forkTarget by remember { mutableStateOf<CardIndexEntry?>(null) }

    // 与一级页同一套过滤条件：两页看到的是同一份「市场」，只是展示数量不同。
    val matched = remember(cards, selectedCardFilter, installed, hubInfo, triggers, query) {
        cards.filter { entry ->
            val info = hubInfo[entry.name]
            val inFilter = when (selectedCardFilter) {
                CardFilter.ALL -> true
                CardFilter.INSTALLED -> entry.name in installed
                CardFilter.FREE -> (info?.priceCents ?: 0L) <= 0L
                CardFilter.PREFERRED -> info?.preferred == true
                CardFilter.AI -> CardManifest.TRIGGER_AI in (triggers[entry.name] ?: entry.triggers)
            }
            inFilter && (query.isBlank() ||
                (entry.name + entry.summary + entry.description).contains(query, ignoreCase = true))
        }
    }

    KedgeSettingsPageScaffold(
        title = "卡片市场",
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding +
                PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search") {
                KedgeOutlinedTextFieldWithSlots(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索卡片名称、摘要") },
                    singleLine = true,
                    shape = RoundedCornerShape(ExploreShape.tile),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CardFilter.entries) { option ->
                            KedgeFilterChip(
                                selected = selectedCardFilter == option,
                                onClick = { cardFilter = option.name },
                                label = { Text(option.label) },
                            )
                        }
                    }
                    Text(
                        text = "共 ${matched.size} 张 · 已安装 ${installed.size}",
                        style = KedgeTextStyles.footnoteSmall().tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (loading && matched.isEmpty()) {
                item(key = "loading") { CardGridSkeleton() }
            } else if (matched.isEmpty()) {
                item(key = "empty") {
                    ExploreEmptyState(
                        query = query,
                        scopeLabel = "卡片市场",
                        onClearQuery = { query = "" },
                        onReload = { vm.refresh("") },
                    )
                }
            } else {
                // 关键：一张卡一个 item，滑到哪组合到哪。
                items(matched, key = { it.name }) { entry ->
                    CompactCard(
                        entry = entry,
                        installedVersion = installed[entry.name],
                        triggers = triggers[entry.name] ?: entry.triggers,
                        hubInfo = hubInfo[entry.name],
                        busy = busyCard == entry.name,
                        onClick = { selectedCard = entry },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    CardMarketOverlays(
        vm = vm,
        uiStyle = uiStyle,
        installed = installed,
        triggers = triggers,
        hubInfo = hubInfo,
        activated = activated,
        busyCard = busyCard,
        refreshQuery = query,
        selectedCard = selectedCard,
        onSelectCard = { selectedCard = it },
        secretCard = secretCard,
        onSecretCard = { secretCard = it },
        publishTarget = publishTarget,
        onPublishTarget = { publishTarget = it },
        forkTarget = forkTarget,
        onForkTarget = { forkTarget = it },
    )
}

/** 「查看全部」入口：一级页铺满 [CARD_PREVIEW_COUNT] 张后，去二级页看剩下的。 */
@Composable
private fun MarketMoreRow(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pressBounce(interactionSource)
            .clip(AutoCornersShape(ExploreShape.surface))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = KedgeTextStyles.title().copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 「本地」入口行：市场页顶部的唯一一条本机内容入口。
 *
 * 图标瓦片走 MD3 音调角色（`primaryContainer` / `onPrimaryContainer`），
 * 与筛选 chip 同一套「不写死色相」的规则；右侧 chevron 表示这一行是跳转而不是开关。
 * 副标题把本机内容的账目一次报清：已安装卡片 / 其中仅本机 / 本地工具已启用数 / 技能 / MCP。
 */
@Composable
private fun LocalEntryRow(
    installedCount: Int,
    localCount: Int,
    toolCount: Int,
    toolEnabledCount: Int,
    skillCount: Int,
    mcpCount: Int,
    onClick: () -> Unit,
) {
    val parts = buildList {
        add("已安装卡片 $installedCount")
        if (localCount > 0) add("其中仅本机 $localCount")
        add("本地工具 $toolEnabledCount/$toolCount")
        if (skillCount > 0) add("技能 $skillCount")
        if (mcpCount > 0) add("MCP $mcpCount")
    }
    PressableRow(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(ExploreShape.listTileSize)
                .clip(AutoCornersShape(ExploreShape.listTile))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = folderOpen,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "本地",
                style = KedgeTextStyles.title().copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                text = parts.joinToString(" · "),
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = chevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 形状一致性锁：整页只用这一套圆角。
 * 轮播 item 26（这一处按需求保持原样）/ 卡片 32 / 列表图标瓦片 16 / 网格图标瓦片 12 /
 * 筛选 chip 12（按下收到 8）；实心药丸按钮交给 Kedge 组件自己的形状。
 * 新加组件时不要再写魔法数字，从这里取。
 */
private object ExploreShape {
    val carousel = 26.dp
    /** 轮播卡按住：在 26dp 档位上收一档（与筛选 chip 同一套「按压 = 收圆角」语言）。 */
    val carouselPressed = 22.dp
    /** 卡片：网格卡与列表条目统一 32dp 一档（参考稿的 `rounded-lg` = 2rem），按住收 4dp。 */
    val surface = 32.dp
    val surfacePressed = 28.dp
    /** 列表条目卡片：与 [surface] 同档 —— 两处共用一个数，卡片语言只有一个来源。 */
    val card = 32.dp
    val cardPressed = 28.dp
    /** 旧的分组底板档位：分组容器已改成一叠卡片，这一档只剩骨架条在用。 */
    val group = 18.dp
    /** 图标瓦片：网格卡 40dp 配 12dp 圆角，列表卡 48dp 配 16dp 圆角。 */
    val tile = 12.dp
    val listTile = 16.dp
    val listTileSize = 48.dp
    /** 筛选 chip：44dp 高的满圆角药丸；选中后收到 12dp，按下在各自档位上再收一档。 */
    val chip = 22.dp
    val chipSelected = 12.dp
    val chipPressed = 14.dp
    val chipPressedSelected = 8.dp
    val chipHeight = 44.dp
    /** chip 左侧那枚身份色块：30dp 见方、11dp 圆角，距药丸左沿 6dp。 */
    val chipTileSize = 30.dp
    val chipTile = 11.dp
    val chipTileInset = 6.dp
}

/**
 * 卡片按压反馈：按住时圆角收一档（[rest] → [pressed]），松手用同一个弹簧弹回。
 *
 * 和筛选 chip 是同一套语言 —— 「按住 = 圆角收紧」，不再整体缩放：卡面缩小时圆角并不会
 * 跟着变，浅色卡在深色底上会露出四角，看着像卡在自己的轮廓里抖。
 * 弹簧参数与 [MarketFilterChip] 逐项一致，整页按压手感只有一个来源。
 */
@Composable
private fun rememberPressedCorner(
    interactionSource: InteractionSource,
    rest: Dp,
    pressed: Dp,
): Dp {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateDpAsState(
        targetValue = if (isPressed) pressed else rest,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_press_corner",
    ).value
}

/** 数字类信息（版本号、票数、体积、哈希）统一走等宽数字，避免上下行对不齐。 */
private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * 分区标题条：标题 + 右侧计数 + 右侧文字动作（参考稿的 `headline-sm` 行）。
 * 一级页与二级页共用同一条 —— 整页的分区标题只有一个字阶、一种排布。
 * 不再带发丝分隔线：条目各自成卡之后，标题只负责分组，不需要「切割线」。
 */
@Composable
private fun SectionLabel(
    title: String,
    icon: ImageVector,
    supporting: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = KedgeTextStyles.displayTitle(),
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.3).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = KedgeTextStyles.footnoteSmall().tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

/**
 * 一级分区标题：与 [SectionLabel] 同一条（整页只此一种标题排布）。
 * 保留这个入口是因为「卡片市场」是页面级首块，语义上仍按一级对待。
 */
@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    supporting: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = SectionLabel(
    title = title,
    icon = icon,
    supporting = supporting,
    actionLabel = actionLabel,
    onAction = onAction,
)

/**
 * 分区容器：**一叠独立卡片**，条目之间 8dp 间距 —— 不再是一整块底板 + 发丝分隔线。
 * 卡片自己负责底色 / 圆角 / 按压反馈（见 [PressableRow]），所以这里只剩排布。
 * 名字保留是因为两级页的每个分区都叫它；语义已经从「底板」变成「卡片栈」。
 */
@Composable
private fun GroupContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * 列表条目的卡片：一条目一张卡 —— 32dp 圆角 [ExploreShape.card]、`surfaceContainer` 底、
 * 20dp 内边距；整卡命中区可点，按住时圆角收一档（与网格卡、筛选 chip 同一套按压语言）。
 * 一级页的分区与二级页的列表都走这里，整页的条目形状因此只有一个来源。
 */
@Composable
private fun PressableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val corner = rememberPressedCorner(
        interactionSource = interactionSource,
        rest = ExploreShape.card,
        pressed = ExploreShape.cardPressed,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AutoCornersShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pressBounce(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/**
 * 状态标签。整页只有一个强调色：`accent = true` 才用主色，其余走中性面。
 * 结果就是模型能力那一排不再出现六种饱和色。
 */
@Composable
private fun MetaChip(text: String, accent: Boolean = false) {
    // 卡片底是 surfaceContainer，徽标要抬到 surfaceContainerHighest 才有一档可见的层次
    // （参考稿里所有小胶囊都是 `bg-surface-container-highest`）。
    KedgeBadge(
        containerColor = if (accent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (accent) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(text = text, style = KedgeTextStyles.footnoteSmall().tabular())
    }
}

/**
 * 单行筛选 chip：44dp 高的满圆角药丸，左侧嵌一枚 30dp 的图标块，右侧跟文字。
 *
 * 配色全部取 MD3 Expressive 的音调角色，chip 自己**不带色相** —— 一行十个也不会变成调色盘，
 * 换主题或开动态取色时整排跟着主题一起变，而不是十份写死的品牌色互相打架：
 *  · 未选中：药丸 `surfaceContainerHigh`，图标块 `onSurfaceVariant` 12% 淡洗，内容 `onSurfaceVariant`；
 *  · 选中：药丸 `secondaryContainer`，图标块反过来吃满 `onSecondaryContainer`（图标再用
 *    `secondaryContainer` 压回去），内容 `onSecondaryContainer`。
 * 选中是「形状 + 颜色」两件事一起变：圆角 22dp → 12dp，底色中性容器色 → secondaryContainer，
 * 颜色走 tween 渐变、圆角走 spring 回弹 —— 形状的弹性不该被颜色的线性时长拖慢。
 * 按下时在各自档位上再收一档（未选中 → 14dp / 选中 → 8dp）再弹回，与整页其它按压反馈同一套语言。
 */
@Composable
private fun MarketFilterChip(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = MaterialTheme.colorScheme
    val container = colors.surfaceContainerHigh
    val selectedContainer = colors.secondaryContainer
    // 选中态底色：compositeOver 到实底上（而不是直接给颜色带 alpha，
    // 否则深色主题下会透出后面的列表内容）。
    val background by animateColorAsState(
        targetValue = if (selected) selectedContainer else container,
        animationSpec = tween(durationMillis = 260),
        label = "market_chip_container",
    )
    val corner by animateDpAsState(
        targetValue = when {
            pressed && selected -> ExploreShape.chipPressedSelected
            pressed -> ExploreShape.chipPressed
            selected -> ExploreShape.chipSelected
            else -> ExploreShape.chip
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "market_chip_corner",
    )
    val tile by animateColorAsState(
        targetValue = if (selected) colors.onSecondaryContainer
        else colors.onSurfaceVariant.copy(alpha = 0.12f).compositeOver(container),
        animationSpec = tween(durationMillis = 220),
        label = "market_chip_tile",
    )
    val tileContent by animateColorAsState(
        targetValue = if (selected) selectedContainer else colors.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "market_chip_tile_content",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "market_chip_label",
    )
    val shape = AutoCornersShape(corner)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .height(ExploreShape.chipHeight)
            .padding(start = ExploreShape.chipTileInset, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(ExploreShape.chipTileSize)
                .clip(AutoCornersShape(ExploreShape.chipTile))
                .background(tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tileContent,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = KedgeTextStyles.body(),
            color = labelColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * 骨架 → 真实内容的换场：出栈的 160ms 内淡出，入栈的延后 40ms 淡入，
 * 邻项位置交给弹簧补。`animateItem` 是 [LazyItemScope] 的成员，只能挂在 lazy item 根节点上。
 */
private fun LazyItemScope.swapIn(base: Modifier): Modifier = base.animateItem(
    fadeInSpec = tween(durationMillis = 260, delayMillis = 40, easing = LinearOutSlowInEasing),
    fadeOutSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing),
    placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
)

/**
 * 骨架呼吸值（0f→1f 往返）。整块骨架共用一个时钟：灰块一多，每个 `rememberInfiniteTransition`
 * 都是独立动画帧源，各自起一个会让骨架本身变成掉帧的来源。
 */
@Composable
private fun rememberSkeletonPulse(): Float {
    val transition = rememberInfiniteTransition(label = "explore_skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "explore_skeleton_alpha",
    )
    return pulse
}

/** 加载骨架：形状与真实内容同形，替代居中转圈。 */
@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    shape: Shape = AutoCornersShape(ExploreShape.group),
    pulse: Float = rememberSkeletonPulse(),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.30f + 0.40f * pulse),
            ),
    )
}

/**
 * 卡面内部的次级灰块：压在骨架卡面上，所以取 `onSurface` 的低透明度 ——
 * 浅色主题下是「灰卡面上的深条」，深色主题下自动反过来，两边都有对比度。
 */
@Composable
private fun SkeletonBar(
    modifier: Modifier,
    shape: Shape = AutoCornersShape(ExploreShape.tile),
    pulse: Float,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f + 0.07f * pulse)),
    )
}

/**
 * 卡片列表骨架：卡面沿用 [CompactCard] 的真实底色与真实内边距，卡里按它的四段结构
 * （图标瓦片 + 标题 / 摘要 / 元信息）摆灰条。
 *
 * 高度**不写死** —— 摘要区照抄真实卡的固定高度（34dp），于是骨架卡与真卡等高：
 * 数据到货时卡面原地换内容，列表不发生任何位移。形状也照抄真卡列表：一行一张全宽。
 */
@Composable
private fun CardGridSkeleton() {
    val pulse = rememberSkeletonPulse()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            CardShellSkeleton(pulse = pulse, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 单张骨架卡：底色 / 圆角 / 内边距 / 行距与 [CompactCard] 逐项对齐。 */
@Composable
private fun CardShellSkeleton(pulse: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(AutoCornersShape(ExploreShape.surface))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SkeletonBlock(
                    Modifier.size(32.dp),
                    AutoCornersShape(ExploreShape.tile),
                    pulse,
                )
                SkeletonBlock(
                    Modifier.weight(1f).height(15.dp),
                    AutoCornersShape(ExploreShape.tile),
                    pulse,
                )
            }
            Column(
                modifier = Modifier.height(34.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SkeletonBlock(Modifier.fillMaxWidth().height(12.dp), AutoCornersShape(ExploreShape.tile), pulse)
                SkeletonBlock(
                    Modifier.fillMaxWidth(0.70f).height(12.dp),
                    AutoCornersShape(ExploreShape.tile),
                    pulse,
                )
            }
            SkeletonBlock(
                Modifier.fillMaxWidth(0.68f).height(11.dp),
                AutoCornersShape(ExploreShape.tile),
                pulse,
            )
        }
    }
}

/**
 * 轮播骨架：度量与 [HorizontalMultiBrowseCarousel] 抄同一份（item 264dp、间距 12dp、
 * contentPadding 上/左/右 16dp、卡高 208dp），数据到货时卡片不会位移，只是把灰块换成海报卡。
 *
 * 卡里也铺内容骨架（左上徽标 / 右上版本号 / 标题 / 摘要 / 左下安装药丸 + 右下元信息），
 * 版式按 [FeaturedCard] 的 `SpaceBetween` 三段摆放，和真卡同一套骨架线。
 */
@Composable
private fun FeaturedCarouselSkeleton() {
    val pulse = rememberSkeletonPulse()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 与真实轮播同一份留白：上 16dp，下边 0（下面紧接悬浮筛选栏）。
            .padding(top = 16.dp)
            .clipToBounds()
            .padding(start = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .width(264.dp)
                    .height(208.dp)
                    .clip(AutoCornersShape(ExploreShape.carousel))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest
                            .copy(alpha = 0.30f + 0.40f * pulse),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBar(
                            Modifier.size(46.dp),
                            AutoCornersShape(ExploreShape.tile),
                            pulse,
                        )
                        Spacer(Modifier.weight(1f))
                        SkeletonBar(
                            Modifier.width(40.dp).height(12.dp),
                            AutoCornersShape(ExploreShape.tile),
                            pulse,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBar(
                            Modifier.fillMaxWidth(0.62f).height(22.dp),
                            AutoCornersShape(ExploreShape.tile),
                            pulse,
                        )
                        SkeletonBar(
                            Modifier.fillMaxWidth(0.86f).height(13.dp),
                            AutoCornersShape(ExploreShape.tile),
                            pulse,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBar(
                            Modifier.width(84.dp).height(32.dp),
                            RoundedCornerShape(percent = 50),
                            pulse,
                        )
                        Spacer(Modifier.width(10.dp))
                        SkeletonBar(
                            Modifier.width(78.dp).height(12.dp),
                            AutoCornersShape(ExploreShape.tile),
                            pulse,
                        )
                    }
                }
            }
        }
    }
}

/** 条目卡骨架：与 [ToolRow] / [SkillRow] / [McpRow] / [ModelRow] / [ResourceRow] 同形。 */
@Composable
private fun SkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AutoCornersShape(ExploreShape.card))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkeletonBlock(Modifier.size(ExploreShape.listTileSize), AutoCornersShape(ExploreShape.listTile))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(14.dp), AutoCornersShape(ExploreShape.tile))
            SkeletonBlock(Modifier.fillMaxWidth(0.32f).height(11.dp), AutoCornersShape(ExploreShape.tile))
        }
    }
}

/**
 * 二级分区骨架：与 `SectionLabel + GroupContainer` 同构 —— 一条标题条 + 一叠同形行，
 * 连分组容器的底色都照原样，到货时是「原地填充」而不是整页重排。
 */
@Composable
private fun SectionSkeleton(rows: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBlock(Modifier.width(140.dp).height(18.dp), AutoCornersShape(ExploreShape.tile))
        // 与真实的一叠卡片同构：卡片栈 + 每行自己的卡面，到货时原地填充、不重排。
        GroupContainer {
            repeat(rows) { SkeletonRow() }
        }
    }
}

/** 页脚同步条：还在拉数据时转圈 + 文案；已经到底了就只剩一行灰字。 */
@Composable
private fun ExploreFooterSync(loading: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (loading) {
            KedgeProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        Text(
            text = if (loading) "正在同步探索流…" else "已经到底了",
            style = KedgeTextStyles.footnoteSmall(),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

/** 空状态：说清「为什么空」并给一条出路，而不是只有一个图标。 */
@Composable
private fun ExploreEmptyState(
    query: String,
    scopeLabel: String,
    onClearQuery: () -> Unit,
    onReload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(AutoCornersShape(ExploreShape.surface))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (query.isBlank()) travelExplore else search,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = if (query.isBlank()) "这里还是空的" else "没有匹配「$query」的结果",
            style = KedgeTextStyles.title().copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (query.isBlank()) "$scopeLabel 里暂时没有可展示的内容，下拉或点下面重新加载。"
            else "换个关键词，或者清除搜索看全部内容。",
            style = KedgeTextStyles.body(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        KedgeTextButton(
            onClick = if (query.isBlank()) onReload else onClearQuery,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(if (query.isBlank()) "重新加载" else "清除搜索")
        }
    }
}

@Composable
private fun ToolRow(
    tool: LocalToolInfo,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    PressableRow(onClick = onClick) {
        ExtensionIcon(tool.seed, tool.icon, ExploreShape.listTileSize)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = tool.title, style = KedgeTextStyles.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = tool.desc,
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MetaChip(text = if (enabled) "已启用" else "未启用", accent = enabled)
    }
}

@Composable
private fun SkillRow(name: String, description: String, onClick: () -> Unit) {
    PressableRow(onClick = onClick) {
        ExtensionIcon(name, extension, ExploreShape.listTileSize)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = name, style = KedgeTextStyles.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = description,
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun McpRow(
    server: McpServerConfig,
    status: McpStatus?,
    onClick: () -> Unit,
) {
    val name = server.commonOptions.name.ifBlank { server.serverUrl }
    PressableRow(onClick = onClick) {
        ExtensionIcon(name, dns, ExploreShape.listTileSize)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = name, style = KedgeTextStyles.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = server.serverUrl,
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (status) {
            McpStatus.Connected -> MetaChip("已连接", accent = true)
            McpStatus.Connecting, is McpStatus.Reconnecting -> MetaChip("连接中")
            is McpStatus.Error -> MetaChip("错误")
            McpStatus.NeedsAuthorization, McpStatus.Authorizing -> MetaChip("需授权")
            else -> MetaChip(if (server.commonOptions.enable) "未连接" else "已停用")
        }
    }
}

/** 服务端给的图标语义名 -> 本机图标；认不出就退回该 kind 的默认图标。 */
private fun resourceIcon(name: String, kind: String): ImageVector = when (name) {
    "travel_explore", "travelExplore" -> travelExplore
    "psychology", "research", "science" -> psychology
    "dns", "server", "cloud" -> dns
    "extension", "puzzle" -> extension
    "bolt", "automation" -> bolt
    "package", "package2", "bundle" -> package2
    "code" -> code
    "schedule", "timer" -> schedule
    else -> when (kind) {
        HubResourceEntry.KIND_MCP -> dns
        HubResourceEntry.KIND_BUNDLE -> package2
        else -> autoAwesome
    }
}

private fun resourceKindLabel(entry: HubResourceEntry): String = when {
    entry.isSkillPack -> "技能合集"
    entry.isMcp -> "MCP 服务"
    entry.isBundle -> "合集包"
    else -> "资源"
}

/** 行内元信息：条目数 + 下载量 + 体积，压成一行，避免窄行里堆一排胶囊。 */
private fun resourceMetaLine(entry: HubResourceEntry): String {
    val parts = buildList {
        if (entry.itemCount > 0) add("${entry.itemCount} 项")
        if (entry.downloads > 0) add(downloadsLabel(entry.downloads))
        if (entry.sizeBytes > 0) add(formatBytes(entry.sizeBytes))
    }
    return parts.joinToString(" · ")
}

/**
 * 资源市场的一张卡（参考稿的条目流）：图标块 + 标题行类目徽标 + 一行摘要 + 一行元信息
 * + 行尾圆形动作。点卡片进详情，圆形按钮直接下载；已装 / 安装中把动作位换成状态，
 * 免得同一个包给两次下载入口。
 */
@Composable
private fun ResourceRow(
    entry: HubResourceEntry,
    installed: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
    onClick: () -> Unit,
) {
    PressableRow(onClick = onClick) {
        ExtensionIcon(entry.slug, resourceIcon(entry.icon, entry.kind), ExploreShape.listTileSize)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = entry.name,
                    style = KedgeTextStyles.title(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                MetaChip(text = resourceKindLabel(entry))
            }
            Text(
                text = entry.description.ifBlank { entry.items.take(3).joinToString(" · ") },
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = resourceMetaLine(entry)
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = KedgeTextStyles.footnoteSmall().tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            busy -> KedgeProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            installed -> CircleActionButton(
                icon = check,
                contentDescription = "已安装",
                onClick = null,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary,
            )
            else -> CircleActionButton(
                icon = downloadForOffline,
                contentDescription = "下载 ${entry.name}",
                onClick = onInstall,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * 行尾圆形动作按钮（参考稿里卡片行尾那个圆）：默认中性容器底，给了底色就是实心动作。
 * `onClick = null` 表示这是纯状态位（已安装 / 已连接），不可点也不再吃无障碍焦点。
 * 形状是满圆，按压反馈用整页统一的 [pressBounce]。
 */
@Composable
private fun CircleActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .then(if (onClick != null) Modifier.pressBounce(interactionSource) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = contentColor,
        )
    }
}

/** 资源详情：先看清包里有什么（技能 / MCP 端点），再决定装不装。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ResourceDetailSheet(
    entry: HubResourceEntry,
    installed: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    KedgeModalBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KedgeBadge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(resourceKindLabel(entry))
                }
                if (installed) {
                    KedgeBadge(containerColor = MaterialTheme.colorScheme.primaryContainer) { Text("已安装") }
                }
                entry.tags.take(4).forEach { tag ->
                    KedgeBadge(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) { Text(tag) }
                }
                val meta = resourceMetaLine(entry)
                if (meta.isNotBlank()) {
                    KedgeBadge(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) { Text(meta) }
                }
            }

            if (entry.description.isNotBlank()) {
                Text(
                    text = entry.description,
                    style = KedgeTextStyles.body(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (entry.items.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "包含",
                        style = KedgeTextStyles.footnote().copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    entry.items.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = chevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = item,
                                style = KedgeTextStyles.body(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (entry.author.isNotBlank()) {
                Text(
                    text = "由 ${entry.author} 提供",
                    style = KedgeTextStyles.footnoteSmall(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            BigActionButton(
                text = when {
                    busy -> "正在安装…"
                    installed -> "重新安装"
                    else -> "下载并启用"
                },
                onClick = { if (!busy) onInstall() },
                container = if (busy) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (busy) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelRow(
    providerName: String,
    model: Model,
    onClick: () -> Unit,
) {
    PressableRow(onClick = onClick) {
        ExtensionIcon(model.modelId, psychology, ExploreShape.listTileSize)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = model.displayName.ifBlank { model.modelId },
                style = KedgeTextStyles.title(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = providerName,
                    style = KedgeTextStyles.footnoteSmall(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val badges = model.abilityBadges()
                if (badges.isNotEmpty()) {
                    Text(
                        text = badges.joinToString(" · "),
                        style = KedgeTextStyles.footnoteSmall(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 能力标签只做文案，不再一人一种饱和色（配色一致性锁）。 */
private fun Model.abilityBadges(): List<String> {
    val badges = mutableListOf<String>()
    if (ModelAbility.TOOL in abilities) badges.add("工具调用")
    if (ModelAbility.REASONING in abilities) badges.add("推理")
    if (Modality.IMAGE in inputModalities) badges.add("视觉输入")
    if (Modality.IMAGE in outputModalities || type == ModelType.IMAGE) badges.add("图像输出")
    if (BuiltInTools.Search in tools) badges.add("内置搜索")
    if (BuiltInTools.ImageGeneration in tools) badges.add("图像生成")
    return badges.take(4)
}

@Composable
private fun LocalCardRow(
    name: String,
    version: String,
    triggers: List<String>,
    onClick: () -> Unit,
) {
    PressableRow(onClick = onClick) {
        ExtensionIcon(name, package2, ExploreShape.listTileSize)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = name, style = KedgeTextStyles.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "v$version · 未在市场索引中",
                style = KedgeTextStyles.body().tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MetaChip(text = triggers.joinToString("/").ifBlank { "local" })
    }
}

@Composable
private fun DependencyRow(
    dependency: DependencyCacheEntry,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AutoCornersShape(ExploreShape.card))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ExtensionIcon(dependency.name, extension, ExploreShape.listTileSize)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${dependency.name} ${dependency.version}",
                style = KedgeTextStyles.title(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatBytes(dependency.sizeBytes)} · SHA-256 ${dependency.sha256.take(12)}…",
                style = KedgeTextStyles.footnoteSmall().tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CircleActionButton(
            icon = deleteForever,
            contentDescription = "删除依赖包",
            onClick = onDelete,
        )
    }
}

/**
 * 精选卡（轮播唯一单元）：MD3 品牌海报卡。
 *
 * 版面自上而下三档，和参考图一致：扇形徽标 + 右上角版本号 → 大标题 + 一行副标题 → 安装药丸。
 * 底部按钮走 [KedgeButton]（两套风格各自的配色都在这里给全），不再是手写的 Row 药丸：
 * 一是形状/按压反馈跟全站按钮统一，二是要按需求去掉箭头图标后没必要再自己画一个。
 *
 * **配色与形状都由包名哈希生成**（见 [extensionPalette] / [extensionShapes]）：
 * 卡片底色是该卡身份色压暗后的「海报色」，徽标轮廓就是该卡在网格里用的那个身份形 ——
 * 同一张卡在一级页、二级页、轮播里是同一个色、同一个轮廓，不需要第二套视觉身份。
 * 因此这里不碰 `colorScheme.primary`：轮播是首屏唯一的满色块，但它不应该抢主题色的话语权。
 *
 * 高度是写死的：标题 / 副标题都锁 1 行，[Arrangement.SpaceBetween] 才不会因为
 * 副标题折行把底部按钮挤出卡片（item 的 maskClip 会直接切掉溢出部分）。
 * 圆角有两层：外层是轮播 item 的 maskClip（keyline 会实时变宽，卡片自身的 clip 单独用会
 * 切不齐），内层就是这张卡自己的 clip —— 内层只负责按压时的圆角收紧（见下面 pressedCorner）。
 */
@Composable
private fun FeaturedCard(
    entry: CardIndexEntry,
    installedVersion: String?,
    hubInfo: HubCardMarketInfo?,
    busy: Boolean,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // 按压反馈：圆角收一档。外层的 item maskClip 形状是按 (itemDrawInfo, density) 记住的
    // （material3 CarouselItemScope.kt:88），换 shape 对象不会生效，所以动画挂在内层这一层
    // clip 上 —— 两层同心时交集就是更紧的那个圆角，滚动中带 keyline 裁切的侧卡不受影响
    // （那种情况下可见的角本来就来自 mask）。
    val pressedCorner = rememberPressedCorner(
        interactionSource = interactionSource,
        rest = ExploreShape.carousel,
        pressed = ExploreShape.carouselPressed,
    )
    // 药丸的三种语义：没装 = 安装；装了但不是最新 = 更新；装的就是市场这一版 = 已安装（点了走卸载）。
    val installState = cardInstallState(installedVersion, entry.version)
    // 身份色 / 身份形都由包名哈希决定，和本页 [ExtensionIcon] 用的是同一个种子、同一张表：
    // 同一张卡在网格里是哪个颜色、哪个轮廓，进了轮播还是它，不需要第二套视觉身份。
    val identityIndex = abs(entry.name.hashCode()) % extensionPalette.size
    val identity = extensionPalette[identityIndex]
    val poster = posterTone(identity)
    val badgeShape = extensionShapes[identityIndex].toShape()
    // 右上角那一块用同一张表里错开半个周期的形，避免和徽标撞成同一个轮廓。
    val blobShape = extensionShapes[(identityIndex + extensionShapes.size / 2) % extensionShapes.size]
        .toShape(startAngle = 18)
    val onPoster = Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AutoCornersShape(pressedCorner))
            .background(poster)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        // 右上角的有机形：纯平色块看着像没画完，这一块负责体积感。
        // 在暗底上叠低透明度白（而不是另取一个颜色）：色相永远跟着海报底色走，
        // 换主题也不会出现第二种品牌色。
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 54.dp, y = (-40).dp)
                .size(188.dp)
                .clip(blobShape)
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(badgeShape)
                        .background(onPoster),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = package2,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = poster,
                    )
                }
                Spacer(Modifier.weight(1f))
                // 版本号搬到右上角：原先它和下载量挤在底部同一行里互相抢宽度。
                // 已安装时显示本机装着的那个版本，否则显示市场版本。
                Text(
                    text = "v${installedVersion ?: entry.version}",
                    style = KedgeTextStyles.footnoteSmall().tabular(),
                    color = onPoster.copy(alpha = 0.85f),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = entry.name,
                    style = KedgeTextStyles.displayTitle().copy(fontWeight = FontWeight.SemiBold),
                    color = onPoster,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.summary.ifBlank { entry.description }.ifBlank { entry.author },
                    style = KedgeTextStyles.body(),
                    color = onPoster.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 按钮换成 Kedge 组件（MD3 / Miuix 两套风格各给一份配色），箭头图标按要求删掉：
                // 药丸里只留动作词。视觉仍是「海报纸底上的白药丸」，只换实现不换观感。
                // 「已安装」时这个药丸就是卸载入口（先弹确认框，见页面根部的 [RikkaConfirmDialog]）。
                KedgeButton(
                    onClick = { if (installState == CardInstallState.INSTALLED) onUninstall() else onInstall() },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = onPoster,
                        contentColor = poster,
                    ),
                    miuixColors = posterButtonMiuixColors(onPoster, poster),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = when {
                            busy -> installState.busyLabel()
                            else -> installState.label()
                        },
                        style = KedgeTextStyles.body(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                // 元信息让位给按钮：宽度不够时省略号收尾，绝不把按钮挤扁或顶出卡片。
                Text(
                    text = featuredMetaLine(entry, hubInfo),
                    style = KedgeTextStyles.footnoteSmall().tabular(),
                    color = onPoster.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 身份色 → 海报底色。
 *
 * 只对 RGB 做同比例缩放：缩放三通道等价于只压 HSV 的 V，**色相与饱和度分毫不动** ——
 * 「这张卡是什么颜色」这件身份信息不会被压暗改掉，而白字对比度从 2.8:1 抬到 6:1 以上，
 * 浅色主题、深色主题都得同一个结果（不依赖 colorScheme，也不受 dynamicColor 影响）。
 */
private fun posterTone(identity: Color, factor: Float = 0.58f): Color = Color(
    red = identity.red * factor,
    green = identity.green * factor,
    blue = identity.blue * factor,
    alpha = 1f,
)

/**
 * 「安装」药丸的 Miuix 配色。MD3 分支吃 [ButtonDefaults.buttonColors]，
 * Miuix 分支在 [KedgeButton] 里只能靠 `miuixColors` 单独喂 —— 海报卡是满色块，
 * 两种风格必须都是「白底 + 海报色字」，否则 Miuix 下会变成主题色药丸、和卡片撞色。
 */
@Composable
private fun posterButtonMiuixColors(
    container: Color,
    content: Color,
): top.yukonga.miuix.kmp.basic.ButtonColors =
    top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
        color = container,
        disabledColor = container.copy(alpha = 0.55f),
        contentColor = content,
        disabledContentColor = content.copy(alpha = 0.75f),
    )

/**
 * 卡片药丸的三种语义。轮播卡与详情页共用同一套判定，避免「同一张卡在两个地方说两句话」。
 */
private enum class CardInstallState { INSTALL, UPDATE, INSTALLED }

private fun CardInstallState.label(): String = when (this) {
    CardInstallState.INSTALL -> "安装"
    CardInstallState.UPDATE -> "更新"
    CardInstallState.INSTALLED -> "已安装"
}

private fun CardInstallState.busyLabel(): String = when (this) {
    CardInstallState.INSTALL -> "安装中…"
    CardInstallState.UPDATE -> "更新中…"
    CardInstallState.INSTALLED -> "卸载中…"
}

/**
 * 本机版本 vs 市场版本。
 *
 * 优先按 semver 比大小：涨版本才是「更新」，只有确实装着最新那版才是「已安装」。
 * 版本号不是合法 semver 时退回字符串比较 —— 解析失败绝不能把「装的是最新版」误判成
 * 「有新版本」，否则药丸会一直显示更新，而服务端并没有更新的包可下。
 */
private fun cardInstallState(installedVersion: String?, marketVersion: String): CardInstallState {
    if (installedVersion == null) return CardInstallState.INSTALL
    // 市场条目没给版本号（索引异常）时不能判定成「有新版本」，否则药丸永远显示更新。
    if (marketVersion.isBlank()) return CardInstallState.INSTALLED
    val installed = Semver.parse(installedVersion)
    val market = Semver.parse(marketVersion)
    return when {
        installed != null && market != null ->
            if (market > installed) CardInstallState.UPDATE else CardInstallState.INSTALLED
        installedVersion != marketVersion -> CardInstallState.UPDATE
        else -> CardInstallState.INSTALLED
    }
}

/**
 * 轮播卡底部元信息（右下角一行）：下载量 + 全部桥接名 + 原生依赖数。
 *
 * 此前只回退到 `bridges.first()`，于是整行只剩一个 "tool"，`ui` 和依赖全被吃掉 ——
 * 这里把该卡用到的能力一次列全。下载量优先取 Hub 市场数（usage_reports），
 * 没有就退回索引里的真实下载计数；两者都没有时这一项直接不占位。
 */
private fun featuredMetaLine(entry: CardIndexEntry, hubInfo: HubCardMarketInfo?): String {
    val parts = mutableListOf<String>()
    val downloads = hubInfo?.downloads?.takeIf { it > 0L } ?: entry.downloads.takeIf { it > 0L }
    downloads?.let { parts += downloadsLabel(it) }
    entry.bridges.distinct().forEach { parts += it }
    if (entry.dependencies.isNotEmpty()) parts += "依赖 ${entry.dependencies.size}"
    return parts.joinToString(" · ")
}

/**
 * 下载量文本：0 表示后端没给这个数（或确实没人下载），一律不展示，避免满屏「下载 0」。
 * 万以上折成「1.2 万」，卡片元信息只有一行的宽度。
 */
private fun downloadsLabel(downloads: Long): String = when {
    downloads >= 10_000L -> "下载 %.1f 万".format(downloads / 10_000f)
    else -> "下载 $downloads"
}

private fun HubCardMarketInfo.downloadsText(): String? =
    downloads.takeIf { it > 0L }?.let(::downloadsLabel)

/** 卡片元信息压成一行文本，取代一排小胶囊（原来 2 栏窄卡里胶囊墙会挤成三行）。 */
private fun cardMetaLine(
    entry: CardIndexEntry,
    triggers: List<String>,
    hubInfo: HubCardMarketInfo?,
    stateLabel: String?,
): String {
    val parts = mutableListOf<String>()
    if (stateLabel != null) parts += stateLabel
    // 下载量优先取 Hub 市场数；Hub 市场表没有这张卡（CDN 索引卡）时退回索引自带的真实计数。
    val downloads = hubInfo?.downloads?.takeIf { it > 0L } ?: entry.downloads.takeIf { it > 0L }
    downloads?.let { parts += downloadsLabel(it) }
    parts += "v${entry.version}"
    entry.bridges.distinct().take(1).forEach { parts += it }
    if (entry.dependencies.isNotEmpty()) parts += "依赖 ${entry.dependencies.size}"
    if (triggers.any { it == CardManifest.TRIGGER_AI }) parts += "AI 触发"
    return parts.joinToString(" · ")
}

/**
 * 卡片列表单元：一行一张，全宽。
 *
 * 以前有 `wide` 变体（更大的图标 / 三行摘要 / 一层主色环境光）给 1 + N 网格的首张
 * 主推位用；改成单列后每张都是全宽，两套度量只会让同一列里出现两种卡高，故删掉变体。
 */
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressedCorner = rememberPressedCorner(
        interactionSource = interactionSource,
        rest = ExploreShape.surface,
        pressed = ExploreShape.surfacePressed,
    )
    Box(
        modifier = modifier
            .clip(AutoCornersShape(pressedCorner))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ExtensionIcon(entry.name, package2, 40.dp)
                Spacer(Modifier.weight(1f))
                when {
                    busy -> KedgeProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    // 状态徽标挪到卡面右上角（参考稿的胶囊位）：标题要占满一行，
                    // 状态不该再挤进元信息行。判定与轮播药丸同一套。
                    installedVersion != null ->
                        MetaChip(cardInstallState(installedVersion, entry.version).label(), accent = true)
                }
            }
            Text(
                text = entry.name,
                style = KedgeTextStyles.title(),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.summary.ifBlank { entry.description },
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(34.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = cardMetaLine(
                        entry = entry,
                        triggers = triggers,
                        hubInfo = hubInfo,
                        stateLabel = null,
                    ),
                    style = KedgeTextStyles.footnoteSmall().tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CircleActionButton(
                    icon = add,
                    contentDescription = "查看 ${entry.name}",
                    onClick = onClick,
                    size = 32.dp,
                    iconSize = 18.dp,
                )
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
    val displayTriggers = triggers.filter { it in CardManifest.ALL_TRIGGERS }

    // 走 Kedge 的弹层：MD3Exp 下是 ModalBottomSheet，Miuix 下是 Miuix 的
    // WindowBottomSheet（自带标题栏与圆角）。此前直接用 MD3 ModalBottomSheet，
    // Miuix 风格下会漏出 MD3 弹层。
    KedgeModalBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
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
                entry.bridges.distinct().forEach { bridge ->
                    ExtensionBadge(bridge, bridge, bolt)
                }
                entry.dependencies.forEach { dependency ->
                    ExtensionBadge(dependency.name, "${dependency.name} ${dependency.version}", extension)
                }
                displayTriggers.forEach { trigger ->
                    KedgeBadge(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(if (trigger == CardManifest.TRIGGER_AI) "AI 触发" else "用户触发")
                    }
                }
                hubInfo?.let { info ->
                    // 免费卡片不再挂「免费」胶囊（默认就是免费，标签只是噪音）；
                    // 收费卡片的价格仍然必须露出，否则用户看不到计费方式。
                    if (info.priceCents > 0L) {
                        KedgeBadge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text("¥${formatYuan(info.priceCents)}/次")
                        }
                    }
                    info.downloadsText()?.let { text ->
                        KedgeBadge(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) { Text(text) }
                    }
                    if (info.license.isNotBlank()) {
                        KedgeBadge(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) { Text(info.license) }
                    }
                    // 「优选」胶囊同样下线；票数保留，它是用户能自己改变的量。
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

            if (entry.dependencies.isNotEmpty()) {
                Text("原生依赖", style = KedgeTextStyles.title(), fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    entry.dependencies.forEach { dependency ->
                        ExtensionBadge(
                            seed = dependency.name,
                            label = "${dependency.name} ${dependency.version}",
                            icon = extension,
                        )
                    }
                }
            }

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

/**
 * 二级页面：本地。
 *
 * 一级探索页把本机内容混在几十张远端卡片里（还受筛选 chip 影响），这一页把「本机 / 已安装」
 * 单独收成一处，进得来就一眼看全：
 *  已安装卡片（市场来源，可更新/卸载）→ 本机卡片（仅存在于本机）→ 本地工具 → 代理技能 →
 *  MCP 服务器 → 原生依赖包。
 *
 * 数据与一级页完全同源（同一个 [ExploreMarketViewModel]，各自持有一个实例），
 * 卡片的详情 / 运行 / 卸载 / 发布继续复用 [CardMarketOverlays]；
 * 工具、技能、MCP 的点按落到各自原有的管理页，这一页不自建第二套编辑入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreLocalPage() {
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
    val busyCard by vm.busyCard.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val mcpStatus by vm.mcpStatus.collectAsStateWithLifecycle()
    val dependencies by vm.dependencies.collectAsStateWithLifecycle()
    val uiStyle = vm.uiStyle
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var selectedCard by remember { mutableStateOf<CardIndexEntry?>(null) }
    var secretCard by remember { mutableStateOf<String?>(null) }
    var publishTarget by remember { mutableStateOf<CardIndexEntry?>(null) }
    var forkTarget by remember { mutableStateOf<CardIndexEntry?>(null) }

    val mcpServers = remember(settings) { settings.mcpServers }
    val currentAssistantLocalTools = remember(settings) {
        settings.assistants.find { it.id == settings.assistantId }?.localTools?.toSet().orEmpty()
    }
    // 与一级页同一套口径：市场索引里还查得到 = 来自市场；查不到 = 只存在于本机。
    val installedFromMarket = remember(installed, cards) {
        installed.keys.filter { name -> cards.any { it.name == name } }.sorted()
    }
    val localOnly = remember(installed, cards) {
        installed.keys.filterNot { name -> cards.any { it.name == name } }.sorted()
    }

    // 本机卡片没有市场条目，就地合成一个只含名称/版本/空 url 的索引项，
    // 让详情抽屉（运行、卸载、密钥）对它同样可用 —— 一级页的「本机卡片」也是这么做的。
    fun installedEntry(name: String): CardIndexEntry =
        cards.firstOrNull { it.name == name }
            ?: CardIndexEntry(name = name, version = installed[name].orEmpty(), url = "")

    KedgeSettingsPageScaffold(
        title = "本地",
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding +
                PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (loading && installed.isEmpty() && skills.isEmpty() && dependencies.isEmpty()) {
                item(key = "loading") { SectionSkeleton(rows = 3) }
            }

            if (installedFromMarket.isNotEmpty()) {
                item(key = "installed_market") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel(
                            title = "已安装卡片",
                            icon = downloadForOffline,
                            supporting = "${installedFromMarket.size} 张来自市场",
                        )
                        GroupContainer {
                            installedFromMarket.forEach { name ->
                                LocalCardRow(
                                    name = name,
                                    version = installed[name].orEmpty(),
                                    triggers = triggers[name].orEmpty(),
                                    onClick = { selectedCard = installedEntry(name) },
                                )
                            }
                        }
                    }
                }
            }

            if (localOnly.isNotEmpty()) {
                item(key = "local_only") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel(
                            title = "本机卡片",
                            icon = bolt,
                            supporting = "${localOnly.size} 张仅存在于本机",
                        )
                        GroupContainer {
                            localOnly.forEach { name ->
                                LocalCardRow(
                                    name = name,
                                    version = installed[name].orEmpty(),
                                    triggers = triggers[name].orEmpty(),
                                    onClick = { selectedCard = installedEntry(name) },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "tools") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel(
                        title = "本地工具",
                        icon = build,
                        supporting = "${localToolCatalog.size} 项 · 已启用 " +
                            "${localToolCatalog.count { it.option in currentAssistantLocalTools }} 项",
                        actionLabel = "管理",
                        onAction = {
                            navController.navigate(
                                Screen.AssistantLocalTool(settings.assistantId.toString())
                            )
                        },
                    )
                    GroupContainer {
                        localToolCatalog.forEach { tool ->
                            ToolRow(
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

            if (skills.isNotEmpty()) {
                item(key = "skills") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel(
                            title = "代理技能",
                            icon = autoAwesome,
                            supporting = "${skills.size} 个技能包",
                            actionLabel = "管理",
                            onAction = { navController.navigate(Screen.Skills) },
                        )
                        GroupContainer {
                            skills.forEach { skill ->
                                SkillRow(
                                    name = skill.name,
                                    description = skill.description,
                                    onClick = { navController.navigate(Screen.SkillDetail(skill.name)) },
                                )
                            }
                        }
                    }
                }
            }

            if (mcpServers.isNotEmpty()) {
                item(key = "mcp") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel(
                            title = "MCP 服务器",
                            icon = dns,
                            supporting = "${mcpServers.size} 个服务器",
                            actionLabel = "管理",
                            onAction = { navController.navigate(Screen.SettingMcp) },
                        )
                        GroupContainer {
                            mcpServers.forEach { server ->
                                McpRow(
                                    server = server,
                                    status = mcpStatus[server.id],
                                    onClick = { navController.navigate(Screen.SettingMcp) },
                                )
                            }
                        }
                    }
                }
            }

            if (dependencies.isNotEmpty()) {
                item(key = "dependency_cache") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel(
                            title = "原生依赖包",
                            icon = heizige.kk.khatkit.app.core.ui.icons.extension,
                            supporting = "已缓存 ${dependencies.size} 个版本",
                            actionLabel = "清空",
                            onAction = {
                                vm.clearDependencyCache { count ->
                                    scope.launch {
                                        Toast.show(
                                            if (count > 0) "已清理 $count 个依赖包" else "没有可清理的依赖包"
                                        )
                                    }
                                }
                            },
                        )
                        GroupContainer {
                            dependencies.forEach { dependency ->
                                DependencyRow(
                                    dependency = dependency,
                                    onDelete = {
                                        vm.deleteDependency(dependency) { ok ->
                                            scope.launch {
                                                Toast.show(
                                                    if (ok) "已删除 ${dependency.name} ${dependency.version}"
                                                    else "删除失败",
                                                    !ok,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    CardMarketOverlays(
        vm = vm,
        uiStyle = uiStyle,
        installed = installed,
        triggers = triggers,
        hubInfo = hubInfo,
        activated = activated,
        busyCard = busyCard,
        refreshQuery = "",
        selectedCard = selectedCard,
        onSelectCard = { selectedCard = it },
        secretCard = secretCard,
        onSecretCard = { secretCard = it },
        publishTarget = publishTarget,
        onPublishTarget = { publishTarget = it },
        forkTarget = forkTarget,
        onForkTarget = { forkTarget = it },
    )
}
