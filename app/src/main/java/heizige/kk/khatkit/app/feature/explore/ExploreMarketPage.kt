package heizige.kk.khatkit.app.feature.explore

import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme
import android.icu.text.CompactDecimalFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import ui.feice.mochi.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import java.text.NumberFormat
import java.util.Locale
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
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.calendarMonth
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.contentPaste
import heizige.kk.khatkit.app.core.ui.icons.dashboard
import heizige.kk.khatkit.app.core.ui.icons.deployedCode
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.downloadForOffline
import heizige.kk.khatkit.app.core.ui.icons.deleteForever
import heizige.kk.khatkit.app.core.ui.icons.editNote
import heizige.kk.khatkit.app.core.ui.icons.extension
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowUp
import heizige.kk.khatkit.app.core.ui.icons.folderManaged
import heizige.kk.khatkit.app.core.ui.icons.memory
import heizige.kk.khatkit.app.core.ui.icons.package2
import heizige.kk.khatkit.app.core.ui.icons.payments
import heizige.kk.khatkit.app.core.ui.icons.psychology
import heizige.kk.khatkit.app.core.ui.icons.questionAnswer
import heizige.kk.khatkit.app.core.ui.icons.rocketLaunch
import heizige.kk.khatkit.app.core.ui.icons.schedule
import heizige.kk.khatkit.app.core.ui.icons.school
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.icons.timer
import heizige.kk.khatkit.app.core.ui.icons.travelExplore
import heizige.kk.khatkit.app.core.ui.icons.upload
import heizige.kk.khatkit.app.core.ui.icons.vpnKey
import heizige.kk.khatkit.app.core.ui.icons.volumeUp
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.util.minus
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.dependency.DependencyCacheEntry
import heizige.kk.khatkit.ssh.api.SshPluginInfo
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.card.Semver
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.HubCardMarketInfo
import heizige.kk.khatkit.hub.HubResourceEntry
import heizige.kk.khatkit.uikit.KhatKitSecretsDialog

import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.feature.ui.shell.LocalKkBarBackdrop
import heizige.kk.khatkit.app.feature.ui.shell.MochiPageScaffold
import heizige.kk.khatkit.uikit.mochi.KkBarIconScope
import heizige.kk.khatkit.uikit.mochi.kkBarButtonColors
import heizige.kk.khatkit.app.core.ui.components.ui.roleAvatarColor
import heizige.kk.khatkit.app.core.ui.components.ui.roleAvatarPalette
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.hooks.rememberSearchExpandState
import heizige.kk.khatkit.uikit.mochi.MochiPullToRefreshBox
import ui.feice.mochi.basic.MochiActivityIndicator
import ui.feice.mochi.basic.MochiCarousel
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiEmptyState
import ui.feice.mochi.basic.MochiSearchBar
import ui.feice.mochi.basic.MochiChipGroup
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetContent
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiScrollEdgeDefaults
import ui.feice.mochi.navigation.MochiTitleBar
import kotlin.math.abs
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiBadgedBox
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiDialogTextField
import ui.feice.mochi.basic.MochiDialogDefaults
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.MochiTextFieldDefaults
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiSkeleton
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListStatusChip
import ui.feice.mochi.list.LocalMochiListItemSpacing
import ui.feice.mochi.list.mochiListSection
import ui.feice.mochi.list.rows
import androidx.compose.foundation.layout.heightIn
import ui.feice.mochi.effect.elastic.mochiPressDeform
import ui.feice.mochi.theme.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.layout.layout
import kotlinx.collections.immutable.persistentListOf

private enum class ExploreFilter {
    ALL,
    CARDS,
    SKILLS,
    MCP,
    BUNDLES,
    MODELS,
}

private enum class CardFilter(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    ALL(R.string.explore_market_card_filter_all, dashboard),
    INSTALLED(R.string.explore_market_card_filter_installed, downloadForOffline),
    FREE(R.string.explore_market_card_filter_free, payments),
    PREFERRED(R.string.explore_market_card_filter_preferred, rocketLaunch),
    AI(R.string.explore_market_card_filter_ai, bolt),
}

/**
 * 合并后的单行筛选轨道：内容类型 + 卡片筛选原本是上下两行 chip，现在合成一行。
 *
 * 每个 chip 自带它要同时写进的两个维度：「已安装 / 免费 / 推荐 / AI 触发」属于卡片筛选，
 * 选中时顺手把内容类型收到「卡片」—— 于是「筛了卡片却还铺着工具、技能、MCP」这种
 * 半生效状态不再存在，一行里任何时刻恰好一个 chip 是选中的。
 */
private enum class MarketChip(
    @StringRes val labelRes: Int,
    val filter: ExploreFilter,
    val cardFilter: CardFilter,
    val icon: ImageVector,
) {
    ALL(R.string.explore_market_chip_all, ExploreFilter.ALL, CardFilter.ALL, dashboard),
    CARDS(R.string.explore_market_chip_cards, ExploreFilter.CARDS, CardFilter.ALL, extension),
    SKILLS(R.string.explore_market_chip_skills, ExploreFilter.SKILLS, CardFilter.ALL, school),
    MCP(R.string.explore_market_chip_mcp, ExploreFilter.MCP, CardFilter.ALL, dns),
    BUNDLES(R.string.explore_market_chip_bundles, ExploreFilter.BUNDLES, CardFilter.ALL, package2),
    MODELS(R.string.explore_market_chip_models, ExploreFilter.MODELS, CardFilter.ALL, memory),
    INSTALLED(R.string.explore_market_chip_installed, ExploreFilter.CARDS, CardFilter.INSTALLED, downloadForOffline),
    FREE(R.string.explore_market_chip_free, ExploreFilter.CARDS, CardFilter.FREE, payments),
    PREFERRED(R.string.explore_market_chip_preferred, ExploreFilter.CARDS, CardFilter.PREFERRED, rocketLaunch),
    AI(R.string.explore_market_chip_ai, ExploreFilter.CARDS, CardFilter.AI, bolt),
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
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val icon: ImageVector,
)

private val localToolCatalog = listOf(
    LocalToolInfo(LocalToolOption.JavascriptEngine, "javascript_engine", R.string.explore_market_tool_javascript_engine_title, R.string.explore_market_tool_javascript_engine_desc, code),
    LocalToolInfo(LocalToolOption.TimeInfo, "time_info", R.string.explore_market_tool_time_info_title, R.string.explore_market_tool_time_info_desc, schedule),
    LocalToolInfo(LocalToolOption.Clipboard, "clipboard", R.string.explore_market_tool_clipboard_title, R.string.explore_market_tool_clipboard_desc, contentPaste),
    LocalToolInfo(LocalToolOption.Tts, "tts", R.string.explore_market_tool_tts_title, R.string.explore_market_tool_tts_desc, volumeUp),
    LocalToolInfo(LocalToolOption.AskUser, "ask_user", R.string.explore_market_tool_ask_user_title, R.string.explore_market_tool_ask_user_desc, questionAnswer),
    LocalToolInfo(LocalToolOption.ScreenTime, "screen_time", R.string.explore_market_tool_screen_time_title, R.string.explore_market_tool_screen_time_desc, timer),
    LocalToolInfo(LocalToolOption.Calendar, "calendar", R.string.explore_market_tool_calendar_title, R.string.explore_market_tool_calendar_desc, calendarMonth),
)

// Stable extension identity: this mirrors KodeHeap's avatar palette hashing.
// 身份色只负责「区分彼此」，不负责抢眼：整组压到低饱和度中明度（莫兰迪调），
// 这样十几个图标排在一起不会变成调色盘，浅色/深色主题下都还看得清。
internal val extensionShapes = listOf(
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

/** 标签胶囊的统一高度：桥接 / 依赖 / 触发方式 / 授权 / 统计量全用它，改这里就全变。 */
private val TagChipHeight = 26.dp

/**
 * 详情弹窗里的标签胶囊 —— **整个市场只有这一种胶囊**，五种标签共用同一套高度、内边距、字阶。
 *
 * 为什么要收成一种：原先桥接/依赖是自绘 Row（图标 + 16% 角色色底），触发方式与授权用的是
 * [MochiBadge]，一行里两种高度、两种圆角，看起来像两套控件拼的。
 *
 * 颜色必须成对显式给，不能靠组件默认值：`MochiBadge` 在 Miuix 分支的 `contentColor` 取
 * `MiuixBadgeDefaults.contentColor`，配 `surfaceContainerHigh` 底色实测只有 **1.2:1**
 * —— 深灰字压深灰底，就是「标签字看不见」的那一条。这里的口径：
 * - 有色标签（桥接 / 依赖）：16% 角色色打底，文字取「角色色 ←→ onSurface」的中间色，
 *   仍带色相，实测 4.5:1 → **约 7:1**；
 * - 中性标签（触发方式 / 授权 / 下载量 / 票数）：surfaceContainerHigh 打底 + onSurfaceVariant，
 *   约 **9:1**。
 *
 * @param seed 非空 = 按这个种子取色相；空 = 中性灰。
 * @param onClick 非空 = 可点（点了以 [label] 为搜索词），并带水波纹。
 * @param containerColor / contentColor 显式配色（「已安装」「按次计费」这类语义底色用）。
 */
@Composable
private fun TagChip(
    label: String,
    icon: ImageVector? = null,
    seed: String? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val accent = seed?.let(::roleAvatarColor)
    val background = containerColor
        ?: accent?.copy(alpha = 0.16f)
        ?: MochiTheme.colorScheme.surfaceContainerHigh
    val foreground = contentColor
        ?: accent?.let { lerp(it, MochiTheme.colorScheme.onSurface, 0.45f) }
        ?: MochiTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .height(TagChipHeight)
            .clip(MochiTheme.shapes.full)
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = foreground)
        }
        Text(label, style = MochiTheme.textStyles.labelSmall, color = foreground)
    }
}


/**
 * 探索市场两页共用的搜索顶栏（一级探索页 / 二级卡片市场）。
 *
 * 形态照 ChatPage：折叠态是一行大标题（+ 可选副标题），右上角一枚 search↔close 图标；
 * 点开后**标题槽位** morph 成输入框，字号字色与标题逐项相等 —— 输入框是**替换**标题，
 * 两者不同字阶时展开会看到「标题跳了一下」。展开进度由 [rememberSearchExpandState] 提供
 * （含预测性返回跟手收起）。
 *
 * 抽成一份的原因：两页的手感必须逐帧同形。此前二级页用的是常驻搜索药丸（Md3SearchBar），
 * 一级页是顶栏 morph，同一个市场两种搜索形态。
 */
/**
 * 页面顶栏：Mochi 的紧凑标题栏。
 *
 * 大标题与搜索框是正文首项（见 [ExploreMarketPage] 的 page_header），所以栏里的标题只在
 * 内容滚上去之后渐显（[MochiScrollEdgeDefaults.progress]），滚回顶部时把位置让给正文大标题，
 * 与 iOS 的 large title → 紧凑标题交接同一条规则。返回、「本地」入口都是栏里的 Mochi 按钮。
 */
@Composable
private fun MarketTopBar(
    title: String,
    subtitle: String?,
    listState: LazyListState,
    actions: @Composable RowScope.() -> Unit,
) {
    MochiTitleBar(
        title = title,
        subtitle = subtitle,
        // 页面骨架统一录正文、下发来源；材质没开玻璃时它是 null
        backdrop = LocalKkBarBackdrop.current,
        navigationIcon = { BackButton() },
        actions = { KkBarIconScope { actions() } },
        titleProgress = MochiScrollEdgeDefaults.progress(listState),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreMarketPage() {
    val navController = LocalNavController.current
    val vm = hiltViewModel<ExploreMarketViewModel>()
    val scope = rememberCoroutineScope()
    // 协程里的 Toast 不在 @Composable 上下文，取不到 stringResource：
    // 模板串在组合期取出，调用点只用 format 填参，不再拼字符串。
    val downloadFailedPrefix = stringResource(R.string.explore_market_download_failed_prefix)
    val toastInstalledNamed = stringResource(R.string.explore_market_toast_installed_named)
    val toastInstallFailed = stringResource(R.string.explore_market_toast_install_failed)
    val toastUpdatedNamed = stringResource(R.string.explore_market_toast_updated_named)
    val toastUpdateFailed = stringResource(R.string.explore_market_toast_update_failed)
    val toastUninstalledNamed = stringResource(R.string.explore_market_toast_uninstalled_named)
    val toastUninstallFailed = stringResource(R.string.explore_market_toast_uninstall_failed)
    val toastDependencyDeleted = stringResource(R.string.explore_market_toast_dependency_deleted)
    val toastDeleteFailed = stringResource(R.string.explore_market_toast_delete_failed)
    val toastMcpNothingToConfigure = stringResource(R.string.explore_market_mcp_nothing_to_configure)

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
    // OCR2：市场里原生插件（dependency）的安装状态，用于显示大小/卸载入口。
    val ocrPlugin by vm.ocrPlugin.collectAsStateWithLifecycle()
    // SSH1：远程 SSH 原生插件（kind=dependency, name=remote-ssh）的安装状态。
    val sshPlugin by vm.sshPlugin.collectAsStateWithLifecycle()
    // OCR4：/api/items 里的扩展条目（OCR 原生插件）。空列表 = 服务端没有/网络失败，整组不渲染。
    val extensionItems by vm.extensionItems.collectAsStateWithLifecycle()

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
    // 列表滚动状态：顶栏标题的渐显读它。
    val marketListState = rememberLazyListState()

    // 轮播卡「已安装 → 卸载」的待确认目标。非空时页面根部弹确认框。
    var uninstallTarget by remember { mutableStateOf<CardIndexEntry?>(null) }
    // 资源市场里被点开看详情的合集（技能合集 / MCP 服务 / 合集包）。
    var resourceTarget by remember { mutableStateOf<HubResourceEntry?>(null) }
    // P0-5 条目级「配置」：非空时弹复用设置页 MCP 表单的配置抽屉（含缺必填提示）。
    var mcpConfigTarget by remember { mutableStateOf<McpInstallPlan?>(null) }

    val enabledProviders = remember(settings) { settings.providers.filter { it.enabled } }
    val models = remember(enabledProviders) { enabledProviders.flatMap { p -> p.models.map { p.name to it } } }
    val mcpServers = remember(settings) { settings.mcpServers }
    val currentAssistantLocalTools = remember(settings) {
        settings.assistants.find { it.id == settings.assistantId }?.localTools?.toSet().orEmpty()
    }

    fun matches(text: String): Boolean = query.isBlank() || text.contains(query, ignoreCase = true)

    // 触发方式的两枚胶囊在详情弹窗里可点，点下去的搜索词就是**屏幕上那段文字**
    // （「AI 触发」/「用户触发」），所以比对文本里必须带上这份本地化文案，
    // 否则会出现「点标签 → 搜标签 → 0 张」。
    val triggerLabelAi = stringResource(R.string.explore_market_card_filter_ai)
    val triggerLabelUser = stringResource(R.string.explore_market_trigger_user)
    val triggerSearchLabels = remember(triggerLabelAi, triggerLabelUser) {
        mapOf(CardManifest.TRIGGER_AI to triggerLabelAi, CardManifest.TRIGGER_USER to triggerLabelUser)
    }

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

    /**
     * P0-5：条目卡上的「需要配置」。索引不带 headers，所以按名字回查本机已保存的
     * MCP 配置，缺必填就标出来 —— 与详情/本机列表用的是同一个 [mcpCardStatus]。
     */
    fun resourceNeedsConfig(entry: HubResourceEntry): Boolean =
        mcpServers.any { server ->
            val name = server.commonOptions.name
            name.isNotBlank() &&
                (name == entry.name || name in entry.items) &&
                mcpCardStatus(server, null) == McpCardStatus.NEEDS_CONFIG
        }

    fun installResource(entry: HubResourceEntry) {
        vm.installResource(entry) { message ->
            scope.launch { Toast.show(message, message.startsWith(downloadFailedPrefix)) }
        }
    }

    /**
     * P0-5：条目级「配置」。先按 slug 取包（与安装同一条下载路径），拿到预填的
     * 名称 / 传输 / URL / 头再弹表单；条目里没有 MCP 端点时只提示一下。
     */
    fun configureMcp(entry: HubResourceEntry) {
        resourceTarget = null
        vm.prepareMcpConfig(entry) { plan ->
            if (plan == null) {
                scope.launch { Toast.show(toastMcpNothingToConfigure) }
            } else {
                mcpConfigTarget = plan
            }
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
    // 版面顺序（自上而下）：轮播（**仅 MD3Exp**，Miuix 下整项不渲染）→ 主筛选 → 本地入口 → 卡片网格 → 技能合集 →
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
        // 搜索关键词必须在 LazyColumn 之前取出来：LazyColumn 的 content lambda 不是
        // @Composable 上下文，里面只有 item {} 的 lambda 里才能调 stringResource。
        val featuredKeywords = stringResource(R.string.explore_market_featured_search_keywords)
        // **Miuix 下整项不渲染**（主人判定：这个风格的轮播太丑，宁可不放）。精选卡本身不会消失，
        // 它照旧出现在下面同一筛选条件下的卡片网格里；这一项没有，chip 栏自己就是首项（[chipItemIndex]）。
        val showFeatured =
            cardsVisible && matches(featuredKeywords) &&
            (featuredCards.isNotEmpty() || featuredPending)
        LazyColumn(
            state = marketListState,
            modifier = listModifier,
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
        ) {
            // 首项 = 页面大标题 + 一行说明 + Mochi 搜索框：顶栏的紧凑标题只在滚上去后渐显，
            // 静止时由这一项承担「探索」这个一级标题。
            item(key = "page_header") {
                Column(
                    modifier = sectionPad,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.explore_market_title),
                        style = MochiTheme.textStyles.largeTitle,
                        color = MochiTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.explore_market_subtitle),
                        style = MochiTheme.textStyles.subhead,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                    MochiSearchBar(
                        query = query,
                        onQueryChange = { new ->
                            if (query.isNotBlank() && new.isEmpty()) vm.refresh("")
                            query = new
                        },
                        onSearch = { vm.refresh(it) },
                        onCancel = {
                            query = ""
                            vm.refresh("")
                        },
                        placeholder = stringResource(R.string.explore_market_search),
                    )
                }
            }

            val skillsVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.SKILLS
            val mcpVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.MCP
            val bundlesVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.BUNDLES
            val modelsVisible = selectedFilter == ExploreFilter.ALL || selectedFilter == ExploreFilter.MODELS

            val cardsMatched = filteredCards.filter {
                matches(it.searchText(triggerSearchLabels, hubInfo[it.name]?.license))
            }
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
            // 只在 MD3Exp 出现，且出现条件 [showFeatured] 在 LazyColumn 之前算过（chip 栏位号依赖它）。
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
                        // 精选轮播换成 Mochi 自己的翻页轮播：露出相邻卡、圆角、翻页指示器、
                        // 越界回弹都由组件给，页面只提供每页的卡面。
                        MochiCarousel(
                            pageCount = featuredCards.size,
                            height = 208.dp,
                            modifier = Modifier.fillMaxWidth(),
                            spacing = 12.dp,
                            cornerRadius = FeaturedCorner,
                        ) { page ->
                            Box(
                                modifier = Modifier
                                    .height(208.dp)
                            ) {
                                FeaturedCard(
                                    entry = featuredCards[page],
                                    installedVersion = installed[featuredCards[page].name],
                                    hubInfo = hubInfo[featuredCards[page].name],
                                    busy = busyCard == featuredCards[page].name,
                                    onClick = { navController.navigate(Screen.ExploreCardDetail(featuredCards[page].name)) },
                                    onInstall = {
                                        // 装了但不是最新 → 覆盖安装（force），没装过 → 正常安装。
                                        val target = featuredCards[page]
                                        if (installed[target.name] == null) {
                                            vm.install(target) { ok ->
                                                scope.launch {
                                                    Toast.show(if (ok) toastInstalledNamed.format(target.name) else toastInstallFailed, !ok)
                                                }
                                            }
                                        } else {
                                            vm.update(target) { ok ->
                                                scope.launch {
                                                    Toast.show(if (ok) toastUpdatedNamed.format(target.name) else toastUpdateFailed, !ok)
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
            // 轨道是 [MochiChipGroup]：横向滚动、左右 16dp 起止，chip 能滑到屏幕边缘。
            item(key = "filter_chips") {
                MochiChipGroup(modifier = Modifier.fillMaxWidth()) {
                    MarketChip.entries.forEach { chip ->
                        MarketFilterChip(
                            selected = chip == selectedChip,
                            label = stringResource(chip.labelRes),
                            icon = chip.icon,
                            onClick = {
                                filter = chip.filter.name
                                cardFilter = chip.cardFilter.name
                            },
                        )
                    }
                }
            }

            // 2b. 「本地」入口已挪到顶栏（见 topBar 的 localAction）：一级页只负责发现与下载，
            // 本机内容不再占一整行版面。原先那一行报的账（已安装 / 仅本机 / 本地工具 / 技能 /
            // MCP）在「本地」二级页逐块列出，这里只留一枚图标按钮 + 本机卡片计数角标。

            // 3b. 卡片列表骨架：与「一组条目行」同形，只在这块还没到货时占位。
            // 已有缓存时刷新不铺骨架，交给上面的下拉指示器，避免每次刷新都把列表整块抽掉。
            if (cardsVisible && cardsPending) {
                item(key = "cards_skeleton") {
                    SectionSkeleton(
                        rows = 3,
                        modifier = swapIn(sectionPad),
                        header = stringResource(R.string.explore_market_chip_cards),
                    )
                }
            }

            // 一级页只铺 preview 张，全量列表按需进二级页（卡片市场）。
            // 卡片区与工具台保持一致：两列、统一高度、卡片内直接给出安装动作，
            // 用户可以先扫标题和描述，再决定进入详情还是直接安装。
            //
            // 不再有「外部脚本卡片」这类分区标题：内容类型由每行前置图标自报
            // （卡片 extension / 技能 school / MCP dns / 合集包 package2 / 模型 memory /
            // 依赖 deployed_code），整页只留「发现」本身，管理入口在顶栏的「本地」。
            if (cardsVisible && cardsMatched.isNotEmpty()) {
                item(key = "cards") {
                    val preview = cardsMatched.take(CARD_PREVIEW_COUNT)
                    // 还有剩的才给入口：卡不多时点进去是个和这页一样的列表。
                    val remaining = cardsMatched.size - preview.size
                    MochiListSection(
                        modifier = swapIn(sectionPad),
                        header = stringResource(R.string.explore_market_chip_cards),
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            maxItemsInEachRow = 2,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            preview.forEach { entry ->
                                MarketGridCard(
                                    modifier = Modifier.fillMaxWidth(0.48f),
                                    entry = entry,
                                    installedVersion = installed[entry.name],
                                    triggers = triggers[entry.name] ?: entry.triggers,
                                    hubInfo = hubInfo[entry.name],
                                    busy = busyCard == entry.name,
                                    onClick = { navController.navigate(Screen.ExploreCardDetail(entry.name)) },
                                    onInstall = {
                                        vm.install(entry) { ok ->
                                            scope.launch {
                                                Toast.show(if (ok) toastInstalledNamed.format(entry.name) else toastInstallFailed, !ok)
                                            }
                                        }
                                    },
                                    onUpdate = {
                                        vm.update(entry) { ok ->
                                            scope.launch {
                                                Toast.show(if (ok) toastUpdatedNamed.format(entry.name) else toastUpdateFailed, !ok)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        if (remaining > 0) {
                            MochiListRow(
                                title = stringResource(R.string.explore_market_view_all_cards_count, cardsMatched.size),
                                onClick = { navController.navigate(Screen.ExploreCards(query)) },
                                chevron = true,
                            )
                        }
                    }
                }
            }

            // 3.9 原生插件：/api/items?kind=dependency 的扩展条目（不会出现在资源索引里），
            // 按条目分发到各自的安装器：remote-ssh 走 SSH 安装器、ocr-mlkit 走 OCR 安装器；
            // 其它 dependency 条目还没接安装链路，只显示、不假装能装。空/失败时整组不渲染。
            if (extensionItems.isNotEmpty()) {
                item(key = "ocr_extensions") {
                    MochiListSection(modifier = swapIn(sectionPad), header = "原生插件") {
                        extensionItems.forEach { entry ->
                            val isSsh = entry.name == SshPluginInfo.MARKET_NAME
                            key(entry.slug) {
                                ResourceRow(
                                    entry = entry,
                                    busy = busyResource == entry.slug,
                                    onInstall = {
                                        if (isSsh) {
                                            vm.installSshPlugin(entry) { message ->
                                                scope.launch { Toast.show(message) }
                                            }
                                        } else {
                                            installResource(entry)
                                        }
                                    },
                                    onClick = {
                                        if (isSsh) {
                                            vm.installSshPlugin(entry) { message ->
                                                scope.launch { Toast.show(message) }
                                            }
                                        } else {
                                            installResource(entry)
                                        }
                                    },
                                    onUninstall = if (isSsh) {
                                        ({
                                            vm.uninstallSshPlugin { message ->
                                                scope.launch { Toast.show(message) }
                                            }
                                        })
                                    } else {
                                        ({ vm.uninstallOcrPlugin() })
                                    },
                                    sizeLabel = if (entry.sizeBytes > 0) formatBytes(entry.sizeBytes) else null,
                                    installed = if (isSsh) sshPlugin != null else ocrPlugin != null,
                                )
                            }
                        }
                    }
                }
            }

            // 4. 技能合集：服务端把一组 SKILL.md 打包下发，装完直接进本机技能库。
            if (skillsVisible && skillPacksPending) {
                item(key = "skill_packs_skeleton") {
                    SectionSkeleton(
                        rows = 3,
                        modifier = swapIn(sectionPad),
                        header = stringResource(R.string.explore_market_section_skill_packs),
                    )
                }
            }

            if (skillsVisible && visibleSkillPacks.isNotEmpty()) {
                item(key = "skill_packs") {
                    MochiListSection(
                        modifier = swapIn(sectionPad),
                        header = stringResource(R.string.explore_market_section_skill_packs),
                    ) {
                        visibleSkillPacks.forEach { entry ->
                            key(entry.slug) {
                                ResourceRow(
                                    entry = entry,
                                    busy = busyResource == entry.slug,
                                    onInstall = { installResource(entry) },
                                    onClick = { resourceTarget = entry },
                                    onUninstall = if (entry.kind == "dependency") ({ vm.uninstallOcrPlugin() }) else null,
                                    sizeLabel = if (entry.kind == "dependency" && entry.sizeBytes > 0) formatBytes(entry.sizeBytes) else null,
                                    installed = if (entry.kind == "dependency") ocrPlugin != null else resourceInstalled(entry),
                                )
                            }
                        }
                    }
                }
            }

            // 5. MCP 服务：一条目 = 一组远端 MCP 端点，装进设置并挂到当前助手。
            if (mcpVisible && mcpMarketPending) {
                item(key = "mcp_market_skeleton") {
                    SectionSkeleton(
                        rows = 3,
                        modifier = swapIn(sectionPad),
                        header = stringResource(R.string.explore_market_section_mcp_services),
                    )
                }
            }

            if (mcpVisible && visibleMcpServers.isNotEmpty()) {
                item(key = "mcp_market") {
                    MochiListSection(
                        modifier = swapIn(sectionPad),
                        header = stringResource(R.string.explore_market_section_mcp_services),
                    ) {
                        visibleMcpServers.forEach { entry ->
                            key(entry.slug) {
                                ResourceRow(
                                    entry = entry,
                                    installed = resourceInstalled(entry),
                                    busy = busyResource == entry.slug,
                                    needsConfig = resourceNeedsConfig(entry),
                                    onInstall = { installResource(entry) },
                                    onConfigure = { configureMcp(entry) },
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
                    SectionSkeleton(
                        rows = 2,
                        modifier = swapIn(sectionPad),
                        header = stringResource(R.string.explore_market_section_bundles),
                    )
                }
            }

            if (bundlesVisible && visibleBundles.isNotEmpty()) {
                item(key = "bundles") {
                    MochiListSection(
                        modifier = swapIn(sectionPad),
                        header = stringResource(R.string.explore_market_section_bundles),
                    ) {
                        visibleBundles.forEach { entry ->
                            key(entry.slug) {
                                ResourceRow(
                                    entry = entry,
                                    busy = busyResource == entry.slug,
                                    onInstall = { installResource(entry) },
                                    onClick = { resourceTarget = entry },
                                    onUninstall = if (entry.kind == "dependency") ({ vm.uninstallOcrPlugin() }) else null,
                                    sizeLabel = if (entry.kind == "dependency" && entry.sizeBytes > 0) formatBytes(entry.sizeBytes) else null,
                                    installed = if (entry.kind == "dependency") ocrPlugin != null else resourceInstalled(entry),
                                )
                            }
                        }
                    }
                }
            }

            if (modelsVisible && modelsPending) {
                item(key = "models_skeleton") {
                    SectionSkeleton(
                        rows = 3,
                        modifier = swapIn(sectionPad),
                        header = stringResource(R.string.explore_market_section_models),
                    )
                }
            }

            if (modelsVisible && visibleModels.isNotEmpty()) {
                item(key = "models") {
                    val preview = visibleModels.take(8)
                    MochiListSection(
                        modifier = swapIn(sectionPad),
                        header = stringResource(R.string.explore_market_section_models),
                    ) {
                        preview.forEach { (providerName, model) ->
                            key(providerName, model.modelId) {
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
                    SectionSkeleton(
                        rows = 2,
                        modifier = swapIn(sectionPad),
                        header = stringResource(R.string.explore_market_section_dependencies),
                    )
                }
            }

            // 原生依赖包：维护型信息，排在所有发现型内容之后。这里只报「本机已缓存了什么」，
            // 清理入口在「本地」二级页的同一分区（一级页不再有分区标题与清空按钮）。
            if (cardsVisible && dependencies.isNotEmpty()) {
                item(key = "dependency_cache") {
                    MochiListSection(
                        modifier = swapIn(sectionPad),
                        header = stringResource(R.string.explore_market_section_dependencies),
                    ) {
                        dependencies.forEach { dependency ->
                            key(dependency.name, dependency.version) {
                                DependencyRow(
                                    name = dependency.name,
                                    version = dependency.version,
                                    cached = dependency,
                                    onDelete = {
                                        vm.deleteDependency(dependency) { ok ->
                                            scope.launch {
                                                Toast.show(
                                                    if (ok) {
                                                        toastDependencyDeleted.format(dependency.name, dependency.version)
                                                    } else {
                                                        toastDeleteFailed
                                                    },
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
                                ExploreFilter.ALL -> stringResource(R.string.explore_market_scope_market)
                                ExploreFilter.SKILLS -> stringResource(R.string.explore_market_section_skill_packs)
                                ExploreFilter.MCP -> stringResource(R.string.explore_market_section_mcp_services)
                                ExploreFilter.BUNDLES -> stringResource(R.string.explore_market_section_bundles)
                                ExploreFilter.MODELS -> stringResource(R.string.explore_market_section_models)
                                ExploreFilter.CARDS -> stringResource(R.string.explore_market_cards_page_title)
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

    // 外层 Box：整页（Scaffold + 所有浮层）收进同一个容器里。资源详情走 MochiSheet，遮罩由面板自己给。
    Box(modifier = Modifier.fillMaxSize()) {
        MochiPageScaffold(
            topBar = {
                // 「本地」入口：一级页唯一一条本机内容入口（图标按钮 + 本机卡片数角标）。
                MarketTopBar(
                    title = stringResource(R.string.explore_market_title),
                    subtitle = stringResource(R.string.explore_market_subtitle),
                    listState = marketListState,
                    actions = {
                        MochiBadgedBox(
                            badge = installed.size.takeIf { it > 0 }?.toString(),
                            color = MochiTheme.colorScheme.tertiaryContainer,
                            contentColor = MochiTheme.colorScheme.onTertiaryContainer,
                        ) {
                            MochiBarIconButton(
                                onClick = { navController.navigate(Screen.ExploreLocal) },
                                colors = kkBarButtonColors(),
                            ) {
                                Icon(
                                    imageVector = folderManaged,
                                    contentDescription = stringResource(R.string.explore_market_local_page_title),
                                    modifier = Modifier.size(22.dp),
                                    tint = MochiTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    },
                )
            },
            containerColor = MochiDefaults.groupedBackground(),
        ) { innerPadding ->
            MochiPullToRefreshBox(
                isRefreshing = loading,
                onRefresh = { vm.refresh(query) },
                modifier = Modifier.fillMaxSize(),
            ) {
                marketList(
                    Modifier.fillMaxSize(),
                    innerPadding + PaddingValues(top = 8.dp, bottom = 32.dp),
                )
            }
        }

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
            onConfigure = if (entry.isMcp) {
                { configureMcp(entry) }
            } else {
                null
            },
            needsConfig = entry.isMcp && resourceNeedsConfig(entry),
        )
    }

    // P0-5：条目级配置抽屉（复用设置页 MCP 表单），预填名称 / 传输 / URL / 头。
    mcpConfigTarget?.let { plan ->
        McpInstallConfigSheet(
            initial = plan.config,
            missingRequired = plan.missingRequired,
            onDismiss = { mcpConfigTarget = null },
            onConfirm = { config ->
                mcpConfigTarget = null
                vm.saveMcpServerConfig(config) { message ->
                    scope.launch { Toast.show(message) }
                }
            },
        )
    }

    // 轮播卡「已安装」药丸走卸载，删本机文件不可逆 → 一律先过确认框。
    // 详情抽屉里的「卸载」保持原样（那里已经是二级页面里的显式两段操作，不再加一层）。
    uninstallTarget?.let { target ->
        MochiDialog(
            visible = true,
            onDismissRequest = { uninstallTarget = null },
            title = stringResource(R.string.explore_market_uninstall_title, target.name),
            message = stringResource(
                R.string.explore_market_uninstall_message,
                target.name,
                installed[target.name] ?: target.version,
            ),
            actions = persistentListOf(
                MochiDialogAction(
                    label = stringResource(R.string.explore_market_cancel),
                    role = MochiDialogActionRole.Cancel,
                ),
                MochiDialogAction(
                    label = stringResource(R.string.explore_market_uninstall),
                    role = MochiDialogActionRole.Destructive,
                    onClick = {
                        uninstallTarget = null
                        vm.uninstall(target.name) { ok ->
                            scope.launch { Toast.show(if (ok) toastUninstalledNamed.format(target.name) else toastUninstallFailed, !ok) }
                        }
                    },
                ),
            ),
        )
    }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

/**
 * 卡片参与搜索的**全部**文本：名称 / 摘要 / 描述 / 桥接名 / 触发方式（含本地化文案）/ 服务端标签 / 授权。
 *
 * 为什么不再只是「名称 + 摘要 + 描述」：详情弹窗里的标签是可以点的（点了搜同类卡片），
 * 而 `tool`、`ui`、`imageToolbox`、`AI 触发`、`MIT` 这些属性本来就不写进简介 ——
 * 只搜老三样会出现「点了标签，结果 0 张」。一级页、卡片市场页共用这一份口径。
 *
 * @param triggerLabels 触发方式的本地化文案（`ai` → 「AI 触发」）。索引里存的是原始值，
 *   两边都要能命中：点胶囊传的是本地化文案，用户手打的可能是 `ai`。
 * @param license 授权标识（`MIT` 等）。它来自市场信息而不是索引条目，未发布到市场时为空。
 */
private fun CardIndexEntry.searchText(triggerLabels: Map<String, String>, license: String?): String =
    buildList {
        add(name)
        add(summary)
        add(description)
        addAll(bridges)
        triggers.forEach { trigger ->
            add(trigger)
            triggerLabels[trigger]?.let(::add)
        }
        tags?.let { tag ->
            add(tag.domain)
            add(tag.action)
            tag.scene?.let(::add)
        }
        if (!license.isNullOrBlank()) add(license)
    }.joinToString(" ")

/** 卡片命中搜索词的口径（[searchText]），一级页与卡片市场共用一份。 */
private fun CardIndexEntry.matchesQuery(
    query: String,
    triggerLabels: Map<String, String>,
    license: String?,
): Boolean = query.isBlank() || searchText(triggerLabels, license).contains(query, ignoreCase = true)

/**
 * 二级页面：卡片市场全量列表。
 *
 * 一级探索页只铺 [CARD_PREVIEW_COUNT] 张卡片，全量列表按需进这一页；
 * 列表用 LazyColumn 逐项惰性组合（一级页此前是把几十张卡塞进一个 `item` 里的 Column，
 * 一屏就全部组合完）。搜索 / 筛选语义与一级页完全一致，点卡片进 [ExploreCardDetailPage]。
 *
 * [initialQuery] 由一级页带过来，避免「在一级页搜了词、点进二级页结果词丢了」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreCardsPage(initialQuery: String = "") {
    val navController = LocalNavController.current
    val vm = hiltViewModel<ExploreMarketViewModel>()

    val cards by vm.cards.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val triggers by vm.triggers.collectAsStateWithLifecycle()
    val hubInfo by vm.hubInfo.collectAsStateWithLifecycle()
    val activated by vm.activated.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val busyCard by vm.busyCard.collectAsStateWithLifecycle()

    // 行尾下载/更新 icon button 的 Toast：模板串在组合期取出，协程里只 format。
    val scope = rememberCoroutineScope()
    val toastInstalledNamed = stringResource(R.string.explore_market_toast_installed_named)
    val toastInstallFailed = stringResource(R.string.explore_market_toast_install_failed)
    val toastUpdatedNamed = stringResource(R.string.explore_market_toast_updated_named)
    val toastUpdateFailed = stringResource(R.string.explore_market_toast_update_failed)

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var cardFilter by rememberSaveable { mutableStateOf(CardFilter.ALL.name) }
    val selectedCardFilter = remember(cardFilter) { CardFilter.valueOf(cardFilter) }

    // 与一级页同一套过滤条件：两页看到的是同一份「市场」，只是展示数量不同。
    // 触发方式的本地化文案同理（弹窗里的标签点了就以它为搜索词），见 [searchText]。
    val triggerLabelAi = stringResource(R.string.explore_market_card_filter_ai)
    val triggerLabelUser = stringResource(R.string.explore_market_trigger_user)
    val triggerSearchLabels = remember(triggerLabelAi, triggerLabelUser) {
        mapOf(CardManifest.TRIGGER_AI to triggerLabelAi, CardManifest.TRIGGER_USER to triggerLabelUser)
    }
    val matched = remember(cards, selectedCardFilter, installed, hubInfo, triggers, query, triggerSearchLabels) {
        cards.filter { entry ->
            val info = hubInfo[entry.name]
            val inFilter = when (selectedCardFilter) {
                CardFilter.ALL -> true
                CardFilter.INSTALLED -> entry.name in installed
                CardFilter.FREE -> (info?.priceCents ?: 0L) <= 0L
                CardFilter.PREFERRED -> info?.preferred == true
                CardFilter.AI -> CardManifest.TRIGGER_AI in (triggers[entry.name] ?: entry.triggers)
            }
            inFilter && entry.matchesQuery(query, triggerSearchLabels, info?.license)
        }
    }
    // 「全部 N」里的 N 是**当前搜索词下的总数**，不跟着卡片筛选走：选中「已安装」后
    // 它不该从 47 掉成 12（读起来是「全部只有 12 张」），那一格的语义是分母。
    val totalCount = remember(cards, query, triggerSearchLabels, hubInfo) {
        cards.count { it.matchesQuery(query, triggerSearchLabels, hubInfo[it.name]?.license) }
    }
    // 搜索态：与一级页 / ChatPage 同一套 —— 入口是顶栏右上角那枚搜索图标，点开后
    // **标题槽位** morph 成输入框（原先这一页是常驻搜索药丸，已去掉）。
    // 一级页带词进来（[initialQuery]）时直接以展开态入场，否则词躺在看不见的输入框里、
    // 列表却停在过滤态，看着像丢词。
    var searchExpanded by rememberSaveable { mutableStateOf(initialQuery.isNotBlank()) }
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
    // 列表滚动状态提到页面这一层：顶栏标题渐显、标签搜索后回顶都要用它。
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
    MochiPageScaffold(
        topBar = {
            MarketTopBar(
                title = stringResource(R.string.explore_market_cards_page_title),
                subtitle = null,
                listState = listState,
                actions = {},
            )
        },
        containerColor = MochiDefaults.groupedBackground(),
    ) { innerPadding ->
        cardsList(
            matched = matched,
            totalCount = totalCount,
            installed = installed,
            triggers = triggers,
            hubInfo = hubInfo,
            busyCard = busyCard,
            loading = loading,
            query = query,
            cardFilter = cardFilter,
            onCardFilter = { cardFilter = it },
            selectedCardFilter = selectedCardFilter,
            onSelectCard = { navController.navigate(Screen.ExploreCardDetail(it.name)) },
            onInstallCard = { entry ->
                vm.install(entry) { ok ->
                    scope.launch {
                        Toast.show(if (ok) toastInstalledNamed.format(entry.name) else toastInstallFailed, !ok)
                    }
                }
            },
            onUpdateCard = { entry ->
                vm.update(entry) { ok ->
                    scope.launch {
                        Toast.show(if (ok) toastUpdatedNamed.format(entry.name) else toastUpdateFailed, !ok)
                    }
                }
            },
            onClearQuery = { query = "" },
            onReload = { vm.refresh("") },
            listState = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(top = 8.dp, bottom = 32.dp),
        )
    }

    }
}

/**
 * 二级「卡片市场」的正文：筛选轨道 + 卡片列表。
 *
 * 形态与一级页的卡片区一致 —— 一行一张卡、每行前置 extension 图标自报类型，
 * 筛选轨道用带图标的 [MarketFilterChip]，卡片总数并进「全部 N」那一格。
 *
 * 卡片走 [mochiListSection]：几十到几百张卡连成一张 Mochi 分组卡片，但每一行仍是懒加载列表的
 * 一项，滑到哪组合到哪。行上不挂逐项进出场动画（快速滚动时条目持续进出视口，逐项动画会拖帧）。
 */
@Composable
private fun cardsList(
    matched: List<CardIndexEntry>,
    totalCount: Int,
    installed: Map<String, String>,
    triggers: Map<String, List<String>>,
    hubInfo: Map<String, HubCardMarketInfo>,
    busyCard: String?,
    loading: Boolean,
    query: String,
    cardFilter: String,
    onCardFilter: (String) -> Unit,
    selectedCardFilter: CardFilter,
    onSelectCard: (CardIndexEntry) -> Unit,
    onInstallCard: (CardIndexEntry) -> Unit,
    onUpdateCard: (CardIndexEntry) -> Unit,
    onClearQuery: () -> Unit,
    onReload: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    // 列表项之间不留间距：分组卡片的行要首尾相接；筛选轨道与卡片之间的距离由轨道自己的下边距给。
    CompositionLocalProvider(LocalMochiListItemSpacing provides 0.dp) {
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp),
        ) {
            item(key = "card_filters") {
                // 轨道外扩回屏幕边（bleed）：chip 能滑到屏幕边缘，不被列表的左右 16dp 夹断。
                MochiChipGroup(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
                    contentPadding = PaddingValues(0.dp),
                    bleed = 16.dp,
                ) {
                    CardFilter.entries.forEach { option ->
                        MarketFilterChip(
                            selected = selectedCardFilter == option,
                            label = when (option) {
                                CardFilter.ALL -> stringResource(
                                    R.string.explore_market_card_filter_all_count,
                                    totalCount,
                                )
                                else -> stringResource(option.labelRes)
                            },
                            icon = option.icon,
                            onClick = { onCardFilter(option.name) },
                        )
                    }
                }
            }

            if (loading && matched.isEmpty()) {
                item(key = "loading") { SectionSkeleton(rows = 3, modifier = swapIn(Modifier)) }
            } else if (matched.isEmpty()) {
                item(key = "empty") {
                    Box(modifier = swapIn(Modifier.fillMaxWidth())) {
                        ExploreEmptyState(
                            query = query,
                            scopeLabel = stringResource(R.string.explore_market_cards_page_title),
                            onClearQuery = onClearQuery,
                            onReload = onReload,
                        )
                    }
                }
            } else {
                mochiListSection(key = "market_cards") {
                    rows(
                        items = matched,
                        key = { entry -> entry.name },
                        contentType = { "market_card" },
                    ) { entry ->
                        CompactCard(
                            entry = entry,
                            installedVersion = installed[entry.name],
                            triggers = triggers[entry.name] ?: entry.triggers,
                            hubInfo = hubInfo[entry.name],
                            busy = busyCard == entry.name,
                            onClick = { onSelectCard(entry) },
                            onInstall = { onInstallCard(entry) },
                            onUpdate = { onUpdateCard(entry) },
                        )
                    }
                }
            }
        }
    }
}

/** 探索市场首屏卡片：沿用工具台的 MochiListCard 信息层级，详情点击与安装动作分开。 */
@Composable
private fun MarketGridCard(
    entry: CardIndexEntry,
    installedVersion: String?,
    triggers: List<String>,
    hubInfo: HubCardMarketInfo?,
    busy: Boolean,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val installState = installedVersion?.let { cardInstallState(it, entry.version) }
    val meta = cardMetaLine(entry, triggers, hubInfo, stateLabel = null)
    MochiListCard(
        modifier = modifier
            .heightIn(min = 178.dp)
            .mochiPressDeform()
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RowIcon(extension)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "v${installedVersion ?: entry.version}",
                    style = MochiTheme.textStyles.labelSmall.tabular(),
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = entry.name,
                style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.summary.ifBlank { entry.description }.ifBlank { entry.author },
                style = MochiTheme.textStyles.footnote,
                color = MochiTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = meta,
                style = MochiTheme.textStyles.labelSmall.tabular(),
                color = MochiTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (busy) {
                    MochiActivityIndicator(Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("处理中", style = MochiTheme.textStyles.footnote, color = MochiTheme.colorScheme.primary)
                } else {
                    Text(
                        text = when (installState) {
                            CardInstallState.INSTALLED -> "已安装"
                            CardInstallState.UPDATE -> "有更新"
                            else -> "未安装"
                        },
                        style = MochiTheme.textStyles.footnote,
                        color = if (installState == CardInstallState.INSTALLED) {
                            MochiTheme.colorScheme.onSurfaceVariant
                        } else {
                            MochiTheme.colorScheme.primary
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    MochiButton(
                        onClick = if (installState == CardInstallState.UPDATE) onUpdate else onInstall,
                        style = if (installState == CardInstallState.INSTALLED) MochiButtonStyle.Tonal else MochiButtonStyle.Filled,
                        enabled = installState != CardInstallState.INSTALLED,
                        minHeight = 32.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = when (installState) {
                                CardInstallState.UPDATE -> "更新"
                                CardInstallState.INSTALLED -> "已安装"
                                else -> "安装"
                            },
                            style = MochiTheme.textStyles.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/** 数字类信息（版本号、票数、体积、哈希）统一走等宽数字，避免上下行对不齐。 */
internal fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * 行首图标：照 iOS 26 设置行，28dp（[MochiListDefaults.LeadingSize]）连续圆角方块 + 12% 主色底，图标 17dp 主色。
 * 整组图标因此带一点色，但只有一种色相 —— 一排条目不会变成调色盘。
 * 内容类型就靠这一枚图标区分，行里不再写「技能合集」这类字样。
 */
@Composable
internal fun RowIcon(icon: ImageVector, tint: Color = MochiTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .size(MochiListDefaults.LeadingSize)
            .clip(MochiTheme.shapes.small)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = tint,
        )
    }
}

/**
 * 行上的状态标签，放进 [MochiListRow] 的 status 槽。整页只有一个强调色：`accent = true` 才用主色，
 * 其余走中性面，模型能力、连接状态这一排不会出现多种饱和色。
 */
@Composable
private fun StatusChip(text: String, accent: Boolean = false) {
    MochiListStatusChip(
        text = text,
        containerColor = if (accent) {
            MochiTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MochiTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (accent) MochiTheme.colorScheme.primary else MochiTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 行尾的圆形小动作（下载 / 配置 / 卸载 / 已装）：Mochi 图标按钮，比默认 48dp 收一档，
 * 让列表行保持 iOS 行高。图标色取按钮下发的内容色。
 */
@Composable
private fun RowActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    style: MochiButtonStyle = MochiButtonStyle.Tonal,
) {
    MochiIconButton(onClick = onClick, enabled = enabled, style = style, size = 34.dp) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = LocalContentColor.current,
        )
    }
}

/** 单行筛选 chip：Mochi 胶囊（选中底色 / 对勾 / 按压弹簧都由组件给），页面只递图标和文案。 */
@Composable
private fun MarketFilterChip(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    MochiChip(
        label = label,
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) MochiTheme.colorScheme.onPrimary else MochiTheme.colorScheme.primary,
            )
        },
    )
}

/**
 * 骨架 → 真实内容的换场：进出场与邻项挪位全用 `animateItem` 的默认 spec。
 * `animateItem` 是 [LazyItemScope] 的成员，只能挂在 lazy item 根节点上。
 * 分区内部的行增减由 [MochiListSection] 自己的高度动画负责 —— 一个容器只有一份高度动画。
 */
private fun LazyItemScope.swapIn(base: Modifier): Modifier = base.animateItem()

/** 骨架灰条：Mochi 骨架（扫光由组件给），内容只用来撑尺寸。 */
@Composable
private fun SkeletonBar(modifier: Modifier, shape: Shape = MochiTheme.shapes.full) {
    MochiSkeleton(loading = true, shape = shape) {
        Box(
            modifier = modifier.background(
                MochiTheme.colorScheme.surfaceContainerHigh,
                shape,
            ),
        )
    }
}

/**
 * 条目骨架行：度量与 [MochiListRow] 对齐（左右 16dp、最小行高 52dp、28dp 图标块 + 标题 / 摘要两条灰条），
 * 到货时是「原地填充」而不是整页重排。
 */
@Composable
private fun SkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MochiListDefaults.RowMinHeight)
            .padding(horizontal = MochiListDefaults.TextRowPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBar(Modifier.size(MochiListDefaults.LeadingSize), MochiTheme.shapes.small)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SkeletonBar(Modifier.fillMaxWidth(0.55f).height(14.dp))
            SkeletonBar(Modifier.fillMaxWidth(0.32f).height(11.dp))
        }
    }
}

/** 分区骨架：与真实分区同构的一张分组卡片，里面铺 [rows] 条骨架行。 */
@Composable
private fun SectionSkeleton(rows: Int, modifier: Modifier = Modifier, header: String? = null) {
    MochiListSection(modifier = modifier, header = header) {
        repeat(rows) { SkeletonRow() }
    }
}

/**
 * 轮播骨架：度量与 [MochiCarousel] 抄同一份（卡宽 264dp、间距 12dp、左 16dp、卡高 208dp），
 * 数据到货时卡片不会位移，只是把灰块换成海报卡。
 */
@Composable
private fun FeaturedCarouselSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clipToBounds()
            .padding(start = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) {
            SkeletonBar(Modifier.width(264.dp).height(208.dp), MochiTheme.shapes.largeIncreased)
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
            MochiActivityIndicator(size = 22.dp)
        }
        Text(
            text = if (loading) stringResource(R.string.explore_market_footer_syncing) else stringResource(R.string.explore_market_footer_end),
            style = MochiTheme.textStyles.footnote,
            color = MochiTheme.colorScheme.onSurfaceVariant,
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
    MochiEmptyState(
        title = if (query.isBlank()) {
            stringResource(R.string.explore_market_empty_title)
        } else {
            stringResource(R.string.explore_market_empty_no_match_title, query)
        },
        message = if (query.isBlank()) {
            stringResource(R.string.explore_market_empty_hint, scopeLabel)
        } else {
            stringResource(R.string.explore_market_empty_search_hint)
        },
        icon = {
            Icon(
                imageVector = if (query.isBlank()) travelExplore else search,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MochiTheme.colorScheme.primary,
            )
        },
        action = {
            MochiButton(onClick = if (query.isBlank()) onReload else onClearQuery, style = MochiButtonStyle.Tonal) {
                Text(
                    text = if (query.isBlank()) stringResource(R.string.explore_market_reload) else stringResource(R.string.explore_market_clear_search),
                    style = MochiTheme.textStyles.subhead.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        },
    )
}

@Composable
private fun ToolRow(
    tool: LocalToolInfo,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    MochiListRow(
        title = stringResource(tool.titleRes),
        subtitle = stringResource(tool.descRes),
        leading = { RowIcon(tool.icon) },
        status = {
            if (enabled) {
                StatusChip(text = stringResource(R.string.explore_market_enabled), accent = true)
            } else {
                StatusChip(text = stringResource(R.string.explore_market_disabled))
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun SkillRow(
    name: String,
    description: String,
    onClick: () -> Unit,
) {
    MochiListRow(
        title = name,
        subtitle = description.takeIf { it.isNotBlank() },
        onClick = onClick,
        leading = { RowIcon(school) },
        chevron = true,
    )
}

@Composable
private fun McpRow(
    server: McpServerConfig,
    status: McpStatus?,
    onClick: () -> Unit,
    onConfigure: (() -> Unit)? = null,
) {
    // 展示用 URL 一律过掩码：带 token 的 query 不能出现在列表上。
    val displayUrl = maskMcpUrl(server.serverUrl)
    val name = server.commonOptions.name.ifBlank { displayUrl }
    val cardStatus = mcpCardStatus(server, status)
    MochiListRow(
        title = name,
        subtitle = displayUrl,
        leading = { RowIcon(dns) },
        onClick = onClick,
        status = {
            when (cardStatus) {
                McpCardStatus.NEEDS_CONFIG -> StatusChip(stringResource(R.string.explore_market_mcp_needs_config))
                McpCardStatus.CONNECT_FAILED -> StatusChip(stringResource(R.string.explore_market_mcp_error))
                McpCardStatus.CONFIGURED -> when (status) {
                    McpStatus.Connected -> StatusChip(stringResource(R.string.explore_market_mcp_connected), accent = true)
                    McpStatus.Connecting, is McpStatus.Reconnecting -> StatusChip(stringResource(R.string.explore_market_mcp_connecting))
                    is McpStatus.Error -> StatusChip(stringResource(R.string.explore_market_mcp_error))
                    McpStatus.NeedsAuthorization, McpStatus.Authorizing -> StatusChip(stringResource(R.string.explore_market_mcp_needs_auth))
                    else -> StatusChip(
                        if (server.commonOptions.enable) {
                            stringResource(R.string.explore_market_mcp_disconnected)
                        } else {
                            stringResource(R.string.explore_market_mcp_stopped)
                        }
                    )
                }
            }
        },
        trailing = if (cardStatus == McpCardStatus.NEEDS_CONFIG && onConfigure != null) {
            {
                RowActionButton(
                    icon = heizige.kk.khatkit.app.core.ui.icons.settings,
                    contentDescription = stringResource(R.string.explore_market_mcp_configure_named, name),
                    onClick = onConfigure,
                )
            }
        } else {
            null
        },
    )
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
        else -> school
    }
}

@Composable
private fun resourceKindLabel(entry: HubResourceEntry): String = when {
    entry.isSkillPack -> stringResource(R.string.explore_market_section_skill_packs)
    entry.isMcp -> stringResource(R.string.explore_market_section_mcp_services)
    entry.isBundle -> stringResource(R.string.explore_market_section_bundles)
    else -> stringResource(R.string.explore_market_resource)
}

/** 行内元信息：条目数 + 下载量 + 体积，压成一行，避免窄行里堆一排胶囊。 */
@Composable
private fun resourceMetaLine(entry: HubResourceEntry): String {
    val itemCountPart = if (entry.itemCount > 0) {
        stringResource(R.string.explore_market_resource_items_count, entry.itemCount)
    } else {
        null
    }
    val downloadsPart = if (entry.downloads > 0) downloadsLabel(entry.downloads) else null
    val sizePart = if (entry.sizeBytes > 0) formatBytes(entry.sizeBytes) else null
    return listOfNotNull(itemCountPart, downloadsPart, sizePart).joinToString(" · ")
}

/**
 * 资源市场的一行：图标块 + 名称 + 摘要 / 元信息两行 + 行尾圆形动作。
 * 点行进详情，圆形按钮直接下载；已装 / 安装中把动作位换成状态，免得同一个包给两次下载入口。
 */
@Composable
private fun ResourceRow(
    entry: HubResourceEntry,
    installed: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
    onClick: () -> Unit,
    // P0-5：MCP 服务条目多一个「配置」入口；技能合集 / 合集包传 null 不显示。
    onConfigure: (() -> Unit)? = null,
    // P0-5：装过但缺必填的 MCP 条目，行上直接标「需要配置」。
    needsConfig: Boolean = false,
    // OCR2：已安装的原生插件条目显示大小与卸载入口（其它 kind 传 null，不显示）。
    sizeLabel: String? = null,
    onUninstall: (() -> Unit)? = null,
) {
    // 摘要与元信息各占一行（MochiListRow 的副标题最多两行），不再用「·」把两类信息拼成一串。
    val subtitle = listOfNotNull(
        entry.description.ifBlank { entry.items.take(3).joinToString(" · ") }.takeIf { it.isNotBlank() },
        resourceMetaLine(entry).takeIf { it.isNotBlank() },
    ).joinToString("\n").takeIf { it.isNotBlank() }
    MochiListRow(
        title = entry.name,
        subtitle = subtitle,
        leading = { RowIcon(resourceIcon(entry.icon, entry.kind)) },
        onClick = onClick,
        loading = busy,
        status = if (needsConfig) {
            { StatusChip(text = stringResource(R.string.explore_market_mcp_needs_config)) }
        } else {
            null
        },
        trailing = if (busy) {
            null
        } else {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (onConfigure != null) {
                        RowActionButton(
                            icon = heizige.kk.khatkit.app.core.ui.icons.settings,
                            contentDescription = stringResource(R.string.explore_market_mcp_configure_named, entry.name),
                            onClick = onConfigure,
                        )
                    }
                    when {
                        installed && onUninstall != null -> RowActionButton(
                            icon = heizige.kk.khatkit.app.core.ui.icons.delete,
                            contentDescription = "卸载",
                            onClick = { onUninstall() },
                        )
                        installed -> RowActionButton(
                            icon = check,
                            contentDescription = stringResource(R.string.explore_market_state_installed),
                            onClick = {},
                            enabled = false,
                        )
                        else -> RowActionButton(
                            icon = downloadForOffline,
                            contentDescription = stringResource(R.string.explore_market_cd_download_named, entry.name),
                            onClick = onInstall,
                            style = MochiButtonStyle.Filled,
                        )
                    }
                }
            }
        },
    )
}

/**
 * 资源合集详情：Mochi 半屏面板，与卡片详情同一套骨架：
 * 面板头部（名称 + 关闭）→ 图标瓦片与「版本 / 类型」→ 标签 → 描述 → 「包含」分区 → 作者 → 安装 / 配置按钮。
 */
@Composable
private fun ResourceDetailSheet(
    entry: HubResourceEntry,
    installed: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    /** P0-5：MCP 服务条目给「配置」入口；其它条目为 null。 */
    onConfigure: (() -> Unit)? = null,
    /** P0-5：已装但缺必填，详情里也标「需要配置」，与卡片/本机列表同一判定。 */
    needsConfig: Boolean = false,
) {
    val sheet = rememberMochiSheetState()
    LaunchedEffect(Unit) { sheet.show() }
    MochiSheet(state = sheet, onDismiss = onDismiss) {
        DetailSheetHeader(title = entry.name, onClose = { sheet.hide() })
        MochiSheetContent {
            DetailSheetHero(
                icon = resourceIcon(entry.icon, entry.kind),
                version = stringResource(R.string.explore_market_version_prefix, entry.version),
                kicker = resourceKindLabel(entry),
            )

            MochiChipGroup(wrap = true, contentPadding = PaddingValues(0.dp), spacing = 6.dp) {
                if (installed) {
                    TagChip(
                        label = stringResource(R.string.explore_market_state_installed),
                        containerColor = MochiTheme.colorScheme.primaryContainer,
                        contentColor = MochiTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (needsConfig) {
                    TagChip(
                        label = stringResource(R.string.explore_market_mcp_needs_config),
                        containerColor = MochiTheme.colorScheme.tertiaryContainer,
                        contentColor = MochiTheme.colorScheme.onTertiaryContainer,
                    )
                }
                entry.tags.take(4).forEach { tag -> TagChip(label = tag) }
                val meta = resourceMetaLine(entry)
                if (meta.isNotBlank()) {
                    TagChip(label = meta)
                }
            }

            if (entry.description.isNotBlank()) {
                Text(
                    text = entry.description,
                    style = MochiTheme.textStyles.subhead,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (entry.items.isNotEmpty()) {
                MochiListSection(header = stringResource(R.string.explore_market_includes)) {
                    entry.items.forEach { item -> MochiListRow(title = item) }
                }
            }

            if (busy) DetailBusyRow()

            if (entry.author.isNotBlank()) {
                Text(
                    text = stringResource(R.string.explore_market_provided_by, entry.author),
                    style = MochiTheme.textStyles.footnote,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailPrimaryButton(
                    text = when {
                        busy -> stringResource(R.string.explore_market_action_installing)
                        installed -> stringResource(R.string.explore_market_action_reinstall)
                        else -> stringResource(R.string.explore_market_action_download_enable)
                    },
                    onClick = { if (!busy) onInstall() },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                // P0-5：先配置再安装 —— 缺必填的 MCP 条目也能在这里进表单预填。
                if (onConfigure != null) {
                    DetailPrimaryButton(
                        text = stringResource(R.string.explore_market_mcp_configure),
                        onClick = { if (!busy) onConfigure() },
                        style = MochiButtonStyle.Tonal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    providerName: String,
    model: Model,
    onClick: () -> Unit,
) {
    val badges = model.abilityBadges()
    MochiListRow(
        title = model.displayName.ifBlank { model.modelId },
        // 供应商一行、能力一行：能力标签之间是同一对象的并列属性，才用「·」连。
        subtitle = listOfNotNull(
            providerName.takeIf { it.isNotBlank() },
            badges.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        ).joinToString("\n").takeIf { it.isNotBlank() },
        leading = { RowIcon(memory) },
        chevron = true,
        onClick = onClick,
    )
}

/** 能力标签只做文案，不再一人一种饱和色（配色一致性锁）。 */
@Composable
private fun Model.abilityBadges(): List<String> {
    val badges = mutableListOf<String>()
    if (ModelAbility.TOOL in abilities) badges.add(stringResource(R.string.explore_market_badge_tool_call))
    if (ModelAbility.REASONING in abilities) badges.add(stringResource(R.string.explore_market_badge_reasoning))
    if (Modality.IMAGE in inputModalities) badges.add(stringResource(R.string.explore_market_badge_vision_input))
    if (Modality.IMAGE in outputModalities || type == ModelType.IMAGE) badges.add(stringResource(R.string.explore_market_badge_image_output))
    if (BuiltInTools.Search in tools) badges.add(stringResource(R.string.explore_market_badge_builtin_search))
    if (BuiltInTools.ImageGeneration in tools) badges.add(stringResource(R.string.explore_market_badge_image_generation))
    return badges.take(4)
}

@Composable
private fun LocalCardRow(
    name: String,
    version: String,
    triggers: List<String>,
    onClick: () -> Unit,
) {
    MochiListRow(
        title = name,
        subtitle = stringResource(R.string.explore_market_local_card_meta, version),
        leading = { RowIcon(extension) },
        chevron = true,
        onClick = onClick,
        status = { StatusChip(text = triggers.joinToString("/").ifBlank { "local" }) },
    )
}

/**
 * 依赖包一行。
 *
 * [cached] 非空 = 本机已下载（报大小与 sha256，行尾可删）；为空 = 已安装卡片声明了但还没下载
 * （下载发生在运行卡片时，见 `dependency-system.md`），此时只报状态、不给删除入口。
 */
@Composable
private fun DependencyRow(
    name: String,
    version: String,
    cached: DependencyCacheEntry?,
    onDelete: () -> Unit,
) {
    // 整行不可点：这一行只报「本机缓存了什么」，唯一的动作是行尾删除。
    MochiListRow(
        title = stringResource(R.string.explore_market_name_version, name, version),
        subtitle = if (cached != null) {
            formatBytes(cached.sizeBytes) + " · SHA-256 " + cached.sha256.take(12) + "…"
        } else {
            stringResource(R.string.explore_market_dependency_pending)
        },
        leading = { RowIcon(deployedCode) },
        trailing = if (cached != null) {
            {
                RowActionButton(
                    icon = deleteForever,
                    contentDescription = stringResource(R.string.explore_market_cd_delete_dependency),
                    onClick = onDelete,
                )
            }
        } else {
            null
        },
    )
}

/**
 * 精选卡（轮播唯一单元）：MD3 品牌海报卡。
 *
 * 版面自上而下三档：身份形徽标 + 右上角版本号 → 大标题 + 一行副标题 → 安装药丸。
 * 底部按钮走 [MochiButton]（两套风格各自的配色都在这里给全），不再是手写的 Row 药丸：
 * 一是形状/按压反馈跟全站按钮统一，二是要按需求去掉箭头图标后没必要再自己画一个。
 *
 * **配色由包名哈希生成**（见 [roleAvatarPalette] / [posterTone]）：卡片底色是该卡身份色压暗后的
 * 「海报色」，同一张卡在一级页、二级页、轮播里是同一个色，不需要第二套视觉身份。
 * 徽标轮廓吃同一张身份形（[extensionShapes]，与网格里同一张卡的轮廓一致），背景是右上角一块
 * 同表错开半个周期的有机形 —— 纯平色块看着像没画完，这一块负责体积感。
 * 卡面底色也不掺主题色（决定身份的是包名哈希）：轮播是首屏唯一的满色块，它不该抢主题色的话语权。
 *
 * **这张卡只在 MD3Exp 下出现**：Miuix 的轮播被判定「太丑」，整个精选轮播项在 Miuix 下不再渲染
 * （见 `showFeatured`），所以这里没有任何风格分支、也没有 Miuix 专用配色。曾经只服务 Miuix 轮播的
 * 「关于页同款流光」（`CardFlowingLight` / `cardFlowingPalette`）已随之删除；精选卡片本身不会
 * 因此在 Miuix 下消失 —— 它照旧出现在同一个筛选条件下的卡片网格里。
 *
 * 高度是写死的：标题 / 副标题都锁 1 行，[Arrangement.SpaceBetween] 才不会因为
 * 副标题折行把底部按钮挤出卡片（item 的 maskClip 会直接切掉溢出部分）。
 * 圆角有两层：外层是 [MochiCarousel] 的页裁切，内层是这张卡自己的 clip（同一档 [FeaturedCorner]）；
 * 按压反馈走 Mochi 的 [mochiPressDeform]（轻缩 + 跟手），与全站按钮同一套手感。
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
    // 药丸的三种语义：没装 = 安装；装了但不是最新 = 更新；装的就是市场这一版 = 已安装（点了走卸载）。
    val installState = cardInstallState(installedVersion, entry.version)
    // 身份色 / 身份形都由包名哈希决定，与卡片行的类型图标是两套东西：
    // 同一张卡在网格里是哪个颜色、哪个轮廓，进了轮播还是它，不需要第二套视觉身份。
    val identityIndex = abs(entry.name.hashCode()) % roleAvatarPalette.size
    val identity = roleAvatarColor(entry.name)
    val poster = posterBase(identity, MochiTheme.colorScheme)
    val posterFill = posterGradient(poster)
    val badgeShape = extensionShapes[identityIndex].toShape()
    // 右上角那一块用同一张表里错开半个周期的形，避免和徽标撞成同一个轮廓。
    val blobShape = extensionShapes[(identityIndex + extensionShapes.size / 2) % extensionShapes.size]
        .toShape(startAngle = 18)
    val onPoster = Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .mochiPressDeform()
            .clip(MochiRoundedRectangle(FeaturedCorner))
            .background(posterFill)
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
                        imageVector = extension,
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
                    style = MochiTheme.textStyles.labelSmall.tabular(),
                    color = onPoster.copy(alpha = 0.85f),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = entry.name,
                    style = MochiTheme.textStyles.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = onPoster,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.summary.ifBlank { entry.description }.ifBlank { entry.author },
                    style = MochiTheme.textStyles.bodyMedium,
                    color = onPoster.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 药丸里只留动作词，视觉是「海报纸底上的白药丸」。
                // 「已安装」时这个药丸就是卸载入口（先弹确认框，见页面根部的卸载 [MochiDialog]）。
                PosterActionPill(
                    label = if (busy) installState.busyLabel() else installState.label(),
                    enabled = !busy,
                    contentColor = poster,
                    onClick = { if (installState == CardInstallState.INSTALLED) onUninstall() else onInstall() },
                )
                Spacer(Modifier.width(10.dp))
                // 元信息让位给按钮：宽度不够时省略号收尾，绝不把按钮挤扁或顶出卡片。
                Text(
                    text = featuredMetaLine(entry, hubInfo),
                    style = MochiTheme.textStyles.labelSmall.tabular()
                        .copy(textAlign = TextAlign.End),
                    color = onPoster.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 精选海报卡与轮播页共用的圆角：卡片 clip、轮播页裁切、轮播骨架同一档。 */
private val FeaturedCorner = 26.dp

/**
 * 海报上的动作药丸：[MochiButton]，白底 + 海报色字。
 * 海报底色由包名哈希生成、不属于主题色，所以这里的白底是成对给出的海报专用配色（深浅主题都成立）。
 */
@Composable
private fun PosterActionPill(
    label: String,
    enabled: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val colors = MochiButtonDefaults.colors(MochiButtonStyle.Filled).copy(
        container = Color.White,
        content = contentColor,
        disabledContainer = Color.White.copy(alpha = 0.6f),
        disabledContent = contentColor.copy(alpha = 0.6f),
    )
    MochiButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        minHeight = 36.dp,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MochiTheme.textStyles.subhead.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

/**
 * 身份色 → Mochi 海报底色。
 *
 * 颜色先从 Mochi 的语义色板取一个主题基底，再用卡片身份色只做轻量的色相区分。
 * 这样动态取色、深浅主题和不同卡片都能保持在同一套 Mochi 视觉范围内。
 */
internal fun posterBase(identity: Color, colors: ui.feice.mochi.theme.MochiColorScheme): Color {
    val themeBases = listOf(
        colors.primary,
        colors.secondary,
        colors.tertiary,
        colors.primaryContainer,
        colors.tertiaryContainer,
    )
    val themeBase = themeBases[abs(identity.hashCode()) % themeBases.size]
    val mix = 0.28f
    val mixed = Color(
        red = identity.red * mix + themeBase.red * (1f - mix),
        green = identity.green * mix + themeBase.green * (1f - mix),
        blue = identity.blue * mix + themeBase.blue * (1f - mix),
        alpha = 1f,
    )
    // 直接乘系数只会把莫兰迪色压成灰；改在 HSV 里定死明度、给饱和度一个下限：
    // 色相（= 这张卡的身份）不动，颜色不发灰，白字对比度稳定在 6:1 左右。
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (mixed.red * 255f).toInt().coerceIn(0, 255),
        (mixed.green * 255f).toInt().coerceIn(0, 255),
        (mixed.blue * 255f).toInt().coerceIn(0, 255),
        hsv,
    )
    hsv[1] = hsv[1].coerceAtLeast(0.42f)
    hsv[2] = if (colors.surface.luminance() > 0.5f) 0.48f else 0.62f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** 海报渐变：左上亮一档、右下暗一档，纯平色块看着像没画完。 */
internal fun posterGradient(base: Color): Brush = Brush.linearGradient(
    colors = listOf(
        Color(
            red = (base.red * 1.38f).coerceIn(0f, 1f),
            green = (base.green * 1.38f).coerceIn(0f, 1f),
            blue = (base.blue * 1.38f).coerceIn(0f, 1f),
        ),
        base,
        Color(
            red = (base.red * 0.72f).coerceIn(0f, 1f),
            green = (base.green * 0.72f).coerceIn(0f, 1f),
            blue = (base.blue * 0.72f).coerceIn(0f, 1f),
        ),
    ),
    start = Offset.Zero,
    end = Offset(1200f, 1000f),
)

/**
 * 「安装」药丸的 Miuix 配色。MD3 分支吃 [ButtonDefaults.buttonColors]，
 * Miuix 分支在 [MochiButton] 里只能靠 `miuixColors` 单独喂 —— 海报卡是满色块，
 * 两种风格必须都是「白底 + 海报色字」，否则 Miuix 下会变成主题色药丸、和卡片撞色。
 */

/**
 * 卡片药丸的三种语义。轮播卡与详情页共用同一套判定，避免「同一张卡在两个地方说两句话」。
 */
internal enum class CardInstallState { INSTALL, UPDATE, INSTALLED }

@Composable
internal fun CardInstallState.label(): String = when (this) {
    CardInstallState.INSTALL -> stringResource(R.string.explore_market_state_install)
    CardInstallState.UPDATE -> stringResource(R.string.explore_market_state_update)
    CardInstallState.INSTALLED -> stringResource(R.string.explore_market_state_installed)
}

@Composable
internal fun CardInstallState.busyLabel(): String = when (this) {
    CardInstallState.INSTALL -> stringResource(R.string.explore_market_state_installing)
    CardInstallState.UPDATE -> stringResource(R.string.explore_market_state_updating)
    CardInstallState.INSTALLED -> stringResource(R.string.explore_market_state_uninstalling)
}

/**
 * 本机版本 vs 市场版本。
 *
 * 优先按 semver 比大小：涨版本才是「更新」，只有确实装着最新那版才是「已安装」。
 * 版本号不是合法 semver 时退回字符串比较 —— 解析失败绝不能把「装的是最新版」误判成
 * 「有新版本」，否则药丸会一直显示更新，而服务端并没有更新的包可下。
 */
internal fun cardInstallState(installedVersion: String?, marketVersion: String): CardInstallState {
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
 * 此前只回退到 `bridges.first()`，于是整行只剩一个 `tool`，`ui` 和依赖全被吃掉 ——
 * 这里把该卡用到的能力一次列全。下载量优先取 Hub 市场数（usage_reports），
 * 没有就退回索引里的真实下载计数；两者都没有时这一项直接不占位。
 */
@Composable
private fun featuredMetaLine(entry: CardIndexEntry, hubInfo: HubCardMarketInfo?): String {
    val parts = mutableListOf<String>()
    val downloads = hubInfo?.downloads?.takeIf { it > 0L } ?: entry.downloads.takeIf { it > 0L }
    downloads?.let { parts += downloadsLabel(it) }
    entry.bridges.distinct().forEach { parts += it }
    if (entry.dependencies.isNotEmpty()) {
        parts += stringResource(R.string.explore_market_meta_dependencies, entry.dependencies.size)
    }
    return parts.joinToString(" · ")
}

/** 紧凑下限：与迁资源前口径一致——1 万以内照常分组显示，1 万以上才折成紧凑写法。 */
private const val COMPACT_DOWNLOAD_MIN = 10_000L

/**
 * 下载量文本：0 表示后端没给这个数（或确实没人下载），一律不展示，避免满屏「下载 0」。
 * 1 万以上折成紧凑写法，卡片元信息只有一行的宽度：zh 显示「1.2 万」，en 显示「12K」。
 * 首选 ICU 短紧凑格式；ICU 紧凑数据被裁剪的 ROM 上它只会吐出普通分组数字，
 * 此时退回 strings.xml 里的量级表（zh 万 / 亿，en K / M），Kotlin 侧不拼任何文案。
 */
@Composable
private fun downloadsLabel(downloads: Long): String {
    // LocalConfiguration 是可观察的：语言切换后会重组（Locale.getDefault() 不会）
    val locale = LocalConfiguration.current.locales[0]
    if (downloads < COMPACT_DOWNLOAD_MIN) {
        return stringResource(
            R.string.explore_market_downloads,
            NumberFormat.getIntegerInstance(locale).format(downloads),
        )
    }
    val compact = compactDownloadCount(downloads, locale)
    if (compact != null) return stringResource(R.string.explore_market_downloads, compact)
    val hiScale = integerResource(R.integer.explore_market_downloads_scale_hi)
    val loScale = integerResource(R.integer.explore_market_downloads_scale_lo)
    return if (downloads >= hiScale) {
        stringResource(R.string.explore_market_downloads_compact_hi, scaledDownloadCount(downloads, hiScale, locale))
    } else {
        stringResource(R.string.explore_market_downloads_compact_lo, scaledDownloadCount(downloads, loScale, locale))
    }
}

/** ICU 短紧凑格式（android.icu，API 24+，本项目 minSdk 26）；紧凑数据缺失退化成普通数字时返回 null 交兜底分支。 */
internal fun compactDownloadCount(downloads: Long, locale: Locale): String? = runCatching {
    CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(downloads)
}.getOrNull()?.takeIf { it != NumberFormat.getIntegerInstance(locale).format(downloads) }

/** 按资源量级折算：小数点随语言交给 NumberFormat，量级数字与单位都在资源里。 */
private fun scaledDownloadCount(downloads: Long, scale: Int, locale: Locale): String {
    val scaled = downloads.toDouble() / scale
    return NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = if (scaled >= 100) 0 else 1
        minimumFractionDigits = 0
    }.format(scaled)
}

@Composable
private fun HubCardMarketInfo.downloadsText(): String? =
    downloads.takeIf { it > 0L }?.let { downloadsLabel(it) }

/** 卡片元信息压成一行文本，供首屏网格卡和二级列表共用。 */
@Composable
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
    parts += stringResource(R.string.explore_market_version_prefix, entry.version)
    entry.bridges.distinct().take(1).forEach { parts += it }
    if (entry.dependencies.isNotEmpty()) {
        parts += stringResource(R.string.explore_market_meta_dependencies, entry.dependencies.size)
    }
    if (triggers.any { it == CardManifest.TRIGGER_AI }) {
        parts += stringResource(R.string.explore_market_card_filter_ai)
    }
    return parts.joinToString(" · ")
}

/**
 * 二级卡片市场的列表单元：一行 [MochiListRow] —— 行首图标（[extension]）+ 名称 + 摘要 / 元信息两行
 * + 行尾安装状态。首屏使用上面的 [MarketGridCard] 两列卡片。
 */
@Composable
private fun CompactCard(
    entry: CardIndexEntry,
    installedVersion: String?,
    triggers: List<String>,
    hubInfo: HubCardMarketInfo?,
    busy: Boolean,
    onClick: () -> Unit,
    // 行尾动作与技能 / MCP 行（[ResourceRow]）同一套：没装 = 下载，装了但不是最新 = 更新，
    // 已是最新 = 不可点的对勾。行内不再有「已安装」文字药丸，也不再给下一级箭头。
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 状态判定与轮播药丸同一套。
    val installState = installedVersion?.let { cardInstallState(it, entry.version) }
    val meta = cardMetaLine(entry = entry, triggers = triggers, hubInfo = hubInfo, stateLabel = null)
    MochiListRow(
        title = entry.name,
        modifier = modifier,
        // 摘要一行、元信息一行（副标题最多两行）。
        subtitle = listOf(entry.summary.ifBlank { entry.description }, meta)
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .takeIf { it.isNotBlank() },
        leading = { RowIcon(extension) },
        onClick = onClick,
        loading = busy,
        trailing = if (busy) {
            null
        } else {
            {
                when (installState) {
                    // 已安装是纯状态位：Tonal + `enabled = false` 拿掉点击与无障碍焦点。卸载入口在详情面板里。
                    CardInstallState.INSTALLED -> RowActionButton(
                        icon = check,
                        contentDescription = stringResource(R.string.explore_market_state_installed),
                        onClick = {},
                        enabled = false,
                    )
                    CardInstallState.UPDATE -> RowActionButton(
                        icon = downloadForOffline,
                        contentDescription = stringResource(R.string.explore_market_state_update),
                        onClick = onUpdate,
                        style = MochiButtonStyle.Filled,
                    )
                    else -> RowActionButton(
                        icon = downloadForOffline,
                        contentDescription = stringResource(R.string.explore_market_cd_download_named, entry.name),
                        onClick = onInstall,
                        style = MochiButtonStyle.Filled,
                    )
                }
            }
        },
    )
}

/** 详情面板头部：标题居中，右上角一枚关闭键（面板头部按钮与面板圆角同心）。 */
@Composable
private fun DetailSheetHeader(title: String, onClose: () -> Unit) {
    MochiSheetHeader(
        title = title,
        actions = {
            MochiBarIconButton(onClick = onClose, colors = kkBarButtonColors()) {
                Icon(
                    imageVector = close,
                    contentDescription = stringResource(R.string.explore_market_cancel),
                    modifier = Modifier.size(20.dp),
                    tint = MochiTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

/**
 * 详情面板正文首行：图标瓦片 + 两行说明。版本独占一行（主色、等宽数字），
 * 类型 / 引擎 · 权限在下一行 —— 两者不是同一类信息，不用「·」拼在一起。
 */
@Composable
private fun DetailSheetHero(icon: ImageVector, version: String?, kicker: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(MochiTheme.shapes.medium)
                .background(MochiTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MochiTheme.colorScheme.primary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (!version.isNullOrBlank()) {
                Text(
                    text = version,
                    style = MochiTheme.textStyles.subhead.tabular(),
                    color = MochiTheme.colorScheme.primary,
                )
            }
            if (!kicker.isNullOrBlank()) {
                Text(
                    text = kicker,
                    style = MochiTheme.textStyles.footnote,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 详情面板里「处理中」的一行：Mochi 活动指示器 + 文案。 */
@Composable
private fun DetailBusyRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MochiActivityIndicator(size = 16.dp)
        Text(
            text = stringResource(R.string.explore_market_processing),
            style = MochiTheme.textStyles.subhead,
            color = MochiTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 详情面板的主操作：整宽 Mochi 按钮。 */
@Composable
private fun DetailPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    style: MochiButtonStyle = MochiButtonStyle.Filled,
) {
    MochiButton(onClick = onClick, modifier = modifier, enabled = enabled, style = style) {
        Text(
            text = text,
            style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PublishCardDialog(
    entry: CardIndexEntry,
    onDismiss: () -> Unit,
    onConfirm: (license: String, price: Double, changelog: String) -> Unit,
) {
    var license by remember(entry.name) { mutableStateOf("MIT") }
    var priceText by remember(entry.name) { mutableStateOf("0") }
    var changelog by remember(entry.name) { mutableStateOf("") }
    val price = priceText.toDoubleOrNull() ?: -1.0

    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.explore_market_publish_dialog_title, entry.name),
        message = stringResource(R.string.explore_market_publish_note),
        actions = persistentListOf(
            MochiDialogAction(
                label = stringResource(R.string.explore_market_cancel),
                role = MochiDialogActionRole.Cancel,
            ),
            MochiDialogAction(
                label = stringResource(R.string.explore_market_publish_submit),
                role = MochiDialogActionRole.Primary,
                enabled = license.isNotBlank() && price >= 0,
                onClick = { onConfirm(license, price, changelog) },
            ),
        ),
    ) {
        DialogField(label = stringResource(R.string.explore_market_license_label)) {
            MochiDialogTextField(value = license, onValueChange = { license = it })
        }
        DialogField(label = stringResource(R.string.explore_market_price_label)) {
            MochiDialogTextField(
                value = priceText,
                onValueChange = { priceText = it },
                autoFocus = false,
            )
        }
        DialogField(label = stringResource(R.string.explore_market_changelog_label)) {
            DialogMultilineTextField(value = changelog, onValueChange = { changelog = it })
        }
    }
}

@Composable
internal fun ForkCardDialog(
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

    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.explore_market_fork_dialog_title, entry.name),
        actions = persistentListOf(
            MochiDialogAction(
                label = stringResource(R.string.explore_market_cancel),
                role = MochiDialogActionRole.Cancel,
            ),
            MochiDialogAction(
                label = stringResource(R.string.explore_market_submit),
                role = MochiDialogActionRole.Primary,
                enabled = changelog.isNotBlank() && newVersion.isNotBlank(),
                onClick = { onConfirm(parent, newVersion, changelog) },
            ),
        ),
    ) {
        DialogField(label = stringResource(R.string.explore_market_parent_version)) {
            MochiDialogTextField(value = parent, onValueChange = { parent = it })
        }
        DialogField(label = stringResource(R.string.explore_market_new_version)) {
            MochiDialogTextField(value = newVersion, onValueChange = { newVersion = it }, autoFocus = false)
        }
        DialogField(label = stringResource(R.string.explore_market_changelog_required)) {
            DialogMultilineTextField(value = changelog, onValueChange = { changelog = it })
        }
    }
}

/** 对话框里的一个输入项：脚注字阶的字段名 + 输入框（输入框自带 8dp 上边距）。 */
@Composable
private fun DialogField(label: String, field: @Composable () -> Unit) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(
            text = label,
            style = MochiTheme.textStyles.footnote,
            color = MochiTheme.colorScheme.onSurfaceVariant,
        )
        field()
    }
}

/**
 * 更新说明要能换行，而 [MochiDialogTextField] 是单行的：这里用多行 [MochiTextField]，
 * 照 [MochiDialogTextField] 的口径左右各外扩到与对话框按钮同宽、配色一致。
 */
@Composable
private fun DialogMultilineTextField(value: String, onValueChange: (String) -> Unit) {
    val outset = 24.dp - MochiDialogDefaults.ButtonInset
    MochiTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .padding(top = 8.dp)
            .layout { measurable, constraints ->
                val o = outset.roundToPx()
                val p = measurable.measure(
                    constraints.copy(
                        minWidth = (constraints.minWidth + o * 2).coerceAtMost(constraints.maxWidth + o * 2),
                        maxWidth = constraints.maxWidth + o * 2,
                    ),
                )
                layout((p.width - o * 2).coerceAtLeast(0), p.height) { p.place(-o, 0) }
            },
        singleLine = false,
        maxLines = 5,
        colors = MochiTextFieldDefaults.colors(
            container = MochiTheme.colorScheme.run { if (isDark) surfaceContainerHighest else surfaceContainerHigh },
        ),
    )
}

internal fun formatYuan(cents: Long): String {
    val yuan = cents / 100.0
    return if (cents % 100 == 0L) yuan.toLong().toString()
    else "%.2f".format(yuan).trimEnd('0').trimEnd('.')
}

/**
 * 本地页的一行依赖包。
 *
 * [cached] 非空 = 本机已有这个 jar；为空 = 已安装卡片声明了该依赖但还没下载到本机。
 */
private data class LocalDependencyView(
    val name: String,
    val version: String,
    val cached: DependencyCacheEntry?,
)

/**
 * 二级页面：本地。
 *
 * 一级探索页把本机内容混在几十张远端卡片里（还受筛选 chip 影响），这一页把「本机 / 已安装」
 * 单独收成一处，进得来就一眼看全：
 *  已安装卡片（市场来源，可更新/卸载）→ 本机卡片（仅存在于本机）→ 本地工具 → 代理技能 →
 *  MCP 服务器 → 原生依赖包（卡片声明的 + 本机已缓存的）。
 *
 * 数据与一级页完全同源（同一个 [ExploreMarketViewModel]，各自持有一个实例），
 * 卡片的详情 / 运行 / 卸载 / 发布都在 [ExploreCardDetailPage]；
 * 工具、技能、MCP 的点按落到各自原有的管理页，这一页不自建第二套编辑入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreLocalPage() {
    val navController = LocalNavController.current
    val vm = hiltViewModel<ExploreMarketViewModel>()
    val scope = rememberCoroutineScope()
    // 同上：本机页里的 Toast 也在协程里，模板串在组合期取出。
    val toastClearedDependencies = stringResource(R.string.explore_market_toast_cleared_dependencies)
    val toastNothingToClear = stringResource(R.string.explore_market_toast_nothing_to_clear)
    val toastDependencyDeleted = stringResource(R.string.explore_market_toast_dependency_deleted)
    val toastDeleteFailed = stringResource(R.string.explore_market_toast_delete_failed)

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
    // 已安装卡片声明的依赖包：本地页要连「还没下载的」一起列出来。
    val declaredDependencies by vm.declaredDependencies.collectAsStateWithLifecycle()

    // P0-5：本机 MCP 行的「配置」抽屉（缺必填的状态由 mcpCardStatus 现算）。
    var mcpConfigTarget by remember { mutableStateOf<McpInstallPlan?>(null) }

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
    // 依赖清单 = 卡片声明的 ∪ 本机已缓存的：
    // 声明的依赖必须先露出来（下载发生在运行卡片时，刚装完卡片缓存是空的），
    // 缓存的 jar 即使没有卡片再声明它（卡片被卸载/降级）也要留着让用户删掉。
    val dependencyViews = remember(dependencies, declaredDependencies) {
        val cachedByKey = dependencies.associateBy { it.name to it.version }
        val declaredKeys = declaredDependencies.entries
            .flatMap { (name, versions) -> versions.map { name to it } }
            .distinct()
        buildList {
            declaredKeys.forEach { (name, version) ->
                add(LocalDependencyView(name, version, cachedByKey[name to version]))
            }
            dependencies.forEach { entry ->
                if (declaredKeys.none { it.first == entry.name && it.second == entry.version }) {
                    add(LocalDependencyView(entry.name, entry.version, entry))
                }
            }
        }
    }


    MochiSettingsPageScaffold(
        title = stringResource(R.string.explore_market_local_page_title),
    ) { innerPadding ->
        // 每组一个 MochiListSection：有现成文案的组带分区标题，其余只靠分区间距分块；
        // 类型仍由每行的前置图标自报（卡片 extension / 工具 build / 技能 school / MCP dns / 依赖 deployedCode）。
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding +
                PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
        ) {
            if (loading && installed.isEmpty() && skills.isEmpty() && dependencyViews.isEmpty()) {
                item(key = "loading") { SectionSkeleton(rows = 3) }
            }

            if (installedFromMarket.isNotEmpty()) {
                item(key = "installed_market") {
                    MochiListSection(header = stringResource(R.string.explore_market_chip_installed)) {
                        installedFromMarket.forEach { name ->
                            key(name) {
                                LocalCardRow(
                                    name = name,
                                    version = installed[name].orEmpty(),
                                    triggers = triggers[name].orEmpty(),
                                    onClick = { navController.navigate(Screen.ExploreCardDetail(name)) },
                                )
                            }
                        }
                    }
                }
            }

            if (localOnly.isNotEmpty()) {
                item(key = "local_only") {
                    MochiListSection {
                        localOnly.forEach { name ->
                            key(name) {
                                LocalCardRow(
                                    name = name,
                                    version = installed[name].orEmpty(),
                                    triggers = triggers[name].orEmpty(),
                                    onClick = { navController.navigate(Screen.ExploreCardDetail(name)) },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "tools") {
                val openTools = {
                    navController.navigate(Screen.AssistantLocalTool(settings.assistantId.toString()))
                }
                MochiListSection {
                    localToolCatalog.forEach { tool ->
                        key(tool.option) {
                            ToolRow(
                                tool = tool,
                                enabled = tool.option in currentAssistantLocalTools,
                                onClick = openTools,
                            )
                        }
                    }
                    MochiListButton(
                        text = stringResource(R.string.explore_market_manage_local_tools),
                        onClick = openTools,
                    )
                }
            }

            item(key = "tasks") {
                MochiListSection {
                    MochiListRow(
                        title = stringResource(R.string.card_runs_title),
                        onClick = { navController.navigate(Screen.CardRuns) },
                        chevron = true,
                    )
                }
            }

            if (skills.isNotEmpty()) {
                item(key = "skills") {
                    MochiListSection(header = stringResource(R.string.explore_market_chip_skills)) {
                        skills.forEach { skill ->
                            key(skill.name) {
                                SkillRow(
                                    name = skill.name,
                                    description = skill.description,
                                    onClick = { navController.navigate(Screen.SkillDetail(skill.name)) },
                                )
                            }
                        }
                        MochiListButton(
                            text = stringResource(R.string.explore_market_manage_skills),
                            onClick = { navController.navigate(Screen.Skills) },
                        )
                    }
                }
            }

            if (mcpServers.isNotEmpty()) {
                item(key = "mcp") {
                    MochiListSection(header = stringResource(R.string.explore_market_chip_mcp)) {
                        mcpServers.forEach { server ->
                            key(server.id) {
                                McpRow(
                                    server = server,
                                    status = mcpStatus[server.id],
                                    onClick = { navController.navigate(Screen.SettingMcp) },
                                    onConfigure = { mcpConfigTarget = rePlanMcpInstall(server) },
                                )
                            }
                        }
                        MochiListButton(
                            text = stringResource(R.string.explore_market_manage_mcp),
                            onClick = { navController.navigate(Screen.SettingMcp) },
                        )
                    }
                }
            }

            if (dependencyViews.isNotEmpty()) {
                item(key = "dependency_cache") {
                    MochiListSection(header = stringResource(R.string.explore_market_section_dependencies)) {
                        dependencyViews.forEach { view ->
                            key(view.name, view.version) {
                                DependencyRow(
                                    name = view.name,
                                    version = view.version,
                                    cached = view.cached,
                                    onDelete = {
                                        val cached = view.cached
                                        if (cached != null) {
                                            vm.deleteDependency(cached) { ok ->
                                                scope.launch {
                                                    Toast.show(
                                                        if (ok) {
                                                            toastDependencyDeleted.format(cached.name, cached.version)
                                                        } else {
                                                            toastDeleteFailed
                                                        },
                                                        !ok,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        if (dependencies.isNotEmpty()) {
                            MochiListButton(
                                text = stringResource(R.string.explore_market_clear),
                                onClick = {
                                    vm.clearDependencyCache { count ->
                                        scope.launch {
                                            Toast.show(
                                                if (count > 0) {
                                                    toastClearedDependencies.format(count)
                                                } else {
                                                    toastNothingToClear
                                                }
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

    // P0-5：本机 MCP 行的配置抽屉 —— 预填当前值，缺必填则保存后仍是「需要配置」。
    mcpConfigTarget?.let { plan ->
        McpInstallConfigSheet(
            initial = plan.config,
            missingRequired = plan.missingRequired,
            onDismiss = { mcpConfigTarget = null },
            onConfirm = { config ->
                mcpConfigTarget = null
                vm.saveMcpServerConfig(config) { message ->
                    scope.launch { Toast.show(message) }
                }
            },
        )
    }

}
