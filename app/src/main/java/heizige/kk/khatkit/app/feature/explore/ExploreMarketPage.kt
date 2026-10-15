package heizige.kk.khatkit.app.feature.explore

import android.icu.text.CompactDecimalFormat
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import heizige.kk.kedge.components.KedgeBadge
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeFilterChip
import heizige.kk.kedge.components.KedgeBadgedBox
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import heizige.kk.khatkit.app.core.ui.icons.build
import heizige.kk.khatkit.app.core.ui.icons.calendarMonth
import heizige.kk.khatkit.app.core.ui.icons.chevronRight
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.contentPaste
import heizige.kk.khatkit.app.core.ui.icons.dashboard
import heizige.kk.khatkit.app.core.ui.icons.deployedCode
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.downloadForOffline
import heizige.kk.khatkit.app.core.ui.icons.deleteForever
import heizige.kk.khatkit.app.core.ui.icons.extension
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
import heizige.kk.khatkit.app.core.ui.icons.volumeUp
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.ui.theme.ListCardStyle
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import heizige.kk.kedge.adaptive.KedgePageScaffold
import heizige.kk.kedge.adaptive.LocalKedgePageScrollBehavior
import heizige.kk.kedge.adaptive.miuixScrollBehavior
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeMiuixMorphingTitleBar
import heizige.kk.khatkit.app.core.ui.effect.BgEffectPainter
import heizige.kk.khatkit.app.core.ui.effect.rememberFrameTimeSeconds
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.hooks.SearchExpandState
import heizige.kk.khatkit.app.core.ui.hooks.rememberSearchExpandState
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.kedge.theme.KedgeStyle
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.overlays.KedgePullToRefreshBox
import kotlin.math.abs
import kotlin.math.pow

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


/**
 * 探索市场两页共用的搜索顶栏（一级探索页 / 二级卡片市场）。
 *
 * 形态照 ChatPage：折叠态是一行大标题（+ 可选副标题），右上角一枚 search↔close 图标；
 * 点开后**标题槽位** morph 成输入框，字号字色与标题逐项相等 —— 输入框是**替换**标题，
 * 两者不同字阶时展开会看到「标题跳了一下」。展开进度由 [rememberSearchExpandState] 提供
 * （含预测性返回跟手收起）。
 *
 * 抽成一份的原因：两页的手感必须逐帧同形。此前二级页用的是常驻搜索药丸（KedgeSearchBar），
 * 一级页是顶栏 morph，同一个市场两种搜索形态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreSearchTopBar(
    title: String,
    subtitle: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onToggleSearch: () -> Unit,
    onExitSearch: () -> Unit,
    searchExpand: SearchExpandState,
    scrollBehavior: TopAppBarScrollBehavior,
    /** 搜索图标左边多出来的一枚动作（一级页是「本地」入口；二级页没有，传 null）。 */
    extraAction: (@Composable () -> Unit)? = null,
) {
    val navController = LocalNavController.current
    val searchProgress = searchExpand.progress
    val searchVisible by remember { derivedStateOf { searchProgress.value > 0.001f } }
    val titleVisible by remember { derivedStateOf { searchProgress.value < 0.999f } }
    // 与侧边栏一致：展开后自动聚焦，delay(100) 等动画起手再抢焦点，
    // 直接 requestFocus() 会把入场动画顶掉。
    val searchFocus = remember { FocusRequester() }
    // MD3 的 LargeFlexibleTopAppBar 内部是 TwoRowsTopAppBar，同一个 title lambda
    // 会被渲染两遍（收起态小标题行 + 展开态大标题行），两行按折叠进度交叉淡化，
    // 任何时刻只有一行可见。FocusRequester 只能挂一份（挂两份时 requestFocus()
    // 落在先注册的那份 = 收起态小标题行 = 看不见的那行），所以打开搜索时记下当时
    // 可见的是哪一行，只把 requester 挂到那一行，光标才不会落进隐藏副本。
    var focusLargeTitleRow by remember { mutableStateOf(true) }
    val isMiuix = LocalKedgeStyle.current == KedgeStyle.Miuix
    LaunchedEffect(expanded) {
        if (expanded) {
            if (!isMiuix) {
                focusLargeTitleRow = scrollBehavior.state.collapsedFraction < 0.5f
            }
            delay(100)
            searchFocus.requestFocus()
        }
    }

    // 右上角 search↔close 图标：同一槽内交叉淡化，判定照 ChatPage ——
    // !searching → search；searching && 有关键字 → close；搜索态但还没输入时
    // 两边都不命中（槽留空）。
    val searching = searchProgress.value >= 0.5f
    val hasQuery = searching && query.isNotEmpty()
    val searchAction: @Composable () -> Unit = {
        KedgeIconButton(
            onClick = {
                when {
                    !searching -> onToggleSearch()
                    hasQuery -> onQueryChange("")
                    else -> onExitSearch()
                }
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!searching) {
                    Icon(
                        imageVector = search,
                        contentDescription = stringResource(R.string.explore_market_search),
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - searchProgress.value
                        },
                    )
                }
                if (hasQuery) {
                    Icon(
                        imageVector = close,
                        contentDescription = stringResource(R.string.explore_market_clear_search),
                        modifier = Modifier.graphicsLayer {
                            alpha = searchProgress.value
                        },
                    )
                }
            }
        }
    }
    // 返回箭头：搜索态点它先退出搜索，否则才退出页面
    val navigationIcon: @Composable () -> Unit = {
        BackButton(
            onClick = {
                if (searchProgress.value >= 0.5f) onExitSearch() else navController.popBackStack()
            },
        )
    }

    if (isMiuix) {
        // Miuix：交给现成的 morphing 顶栏（ChatPage 用的同一个），标题/副标题
        // 与输入框按同一进度 morph。
        KedgeMiuixMorphingTitleBar(
            title = title,
            subtitle = subtitle,
            expanded = expanded,
            keyword = query,
            onKeywordChange = onQueryChange,
            dragProgress = searchProgress.value,
            // 不传 dragging，组件内部的 snapTo 会被门控掉（只剩 tween），
            // 返回手势拖动就没有跟手反馈。
            dragging = searchExpand.dragging.value,
            onExitSearch = onExitSearch,
            placeholder = stringResource(R.string.history_page_search),
            navigationIcon = navigationIcon,
            actions = {
                extraAction?.invoke()
                searchAction()
            },
            focusRequester = searchFocus,
        )
    } else {
        KedgePageLargeTopBar(
            title = title,
            subtitle = subtitle,
            navigationIcon = navigationIcon,
            actions = {
                extraAction?.invoke()
                searchAction()
            },
            scrollBehavior = scrollBehavior,
            colors = CustomColors.topBarColors,
            titleContent = {
                // 标题与输入框共用同一个字阶 token
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
                                text = title,
                                style = titleStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = KedgeTextStyles.body(),
                                    color = LocalContentColor.current.copy(0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (searchVisible) {
                        // 用 searchVisible 而不是 expanded：布尔先变 false、
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
                                onValueChange = onQueryChange,
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

    var secretCard by remember { mutableStateOf<String?>(null) }
    var selectedCard by remember { mutableStateOf<CardIndexEntry?>(null) }
    // 轮播卡「已安装 → 卸载」的待确认目标。非空时页面根部弹确认框。
    var uninstallTarget by remember { mutableStateOf<CardIndexEntry?>(null) }
    var publishTarget by remember { mutableStateOf<CardIndexEntry?>(null) }
    var forkTarget by remember { mutableStateOf<CardIndexEntry?>(null) }
    // 资源市场里被点开看详情的合集（技能合集 / MCP 服务 / 合集包）。
    var resourceTarget by remember { mutableStateOf<HubResourceEntry?>(null) }
    // P0-5 条目级「配置」：非空时弹复用设置页 MCP 表单的配置抽屉（含缺必填提示）。
    var mcpConfigTarget by remember { mutableStateOf<McpInstallPlan?>(null) }
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
        // 搜索关键词必须在 LazyColumn 之前取出来：LazyColumn 的 content lambda 不是
        // @Composable 上下文，里面只有 item {} 的 lambda 里才能调 stringResource。
        val featuredKeywords = stringResource(R.string.explore_market_featured_search_keywords)
        val showFeatured = cardsVisible && matches(featuredKeywords) &&
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
            }

            // 2b. 「本地」入口已挪到顶栏（见 topBar 的 localAction）：一级页只负责发现与下载，
            // 本机内容不再占一整行版面。原先那一行报的账（已安装 / 仅本机 / 本地工具 / 技能 /
            // MCP）在「本地」二级页逐块列出，这里只留一枚图标按钮 + 本机卡片计数角标。

            // 3b. 卡片列表骨架：与「一组条目行」同形，只在这块还没到货时占位。
            // 已有缓存时刷新不铺骨架，交给上面的下拉指示器，避免每次刷新都把列表整块抽掉。
            if (cardsVisible && cardsPending) {
                item(key = "cards_skeleton") {
                    Box(modifier = swapIn(sectionPad)) { SectionSkeleton(rows = 3) }
                }
            }

            // 一级页只铺 preview 张，全量列表按需进二级页（卡片市场）。
            //
            // 单列：此前是「1 张全宽主推 + 两栏窄卡」的 1 + N 网格，一屏里两套卡面尺寸，
            // 视线要在大小卡之间来回换。现在整区一行一张、全宽到底。
            // 主推位由上面的轮播承担，这里不再需要一张放大的卡。
            //
            // 不再有「外部脚本卡片」这类分区标题：内容类型由每行前置图标自报
            // （卡片 extension / 技能 school / MCP dns / 合集包 package2 / 模型 memory /
            // 依赖 deployed_code），整页只留「发现」本身，管理入口在顶栏的「本地」。
            if (cardsVisible && cardsMatched.isNotEmpty()) {
                item(key = "cards") {
                    val preview = cardsMatched.take(CARD_PREVIEW_COUNT)
                    // 还有剩的才给入口：卡不多时点进去是个和这页一样的列表。
                    val remaining = cardsMatched.size - preview.size
                    MarketContentSwap(
                        key = preview.joinToString(",") { it.name } + "|" + remaining,
                        modifier = swapIn(sectionPad),
                    ) {
                        GroupContainer(
                            count = preview.size + if (remaining > 0) 1 else 0,
                        ) { index, shape ->
                            if (index < preview.size) {
                                val entry = preview[index]
                                CompactCard(
                                    entry = entry,
                                    installedVersion = installed[entry.name],
                                    triggers = triggers[entry.name] ?: entry.triggers,
                                    hubInfo = hubInfo[entry.name],
                                    busy = busyCard == entry.name,
                                    onClick = { selectedCard = entry },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = shape,
                                )
                            } else {
                                MarketMoreRow(
                                    label = stringResource(R.string.explore_market_view_all_cards_count, cardsMatched.size),
                                    onClick = { navController.navigate(Screen.ExploreCards(query)) },
                                    shape = shape,
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
                    MarketContentSwap(
                        key = visibleSkillPacks.joinToString(",") { it.slug },
                        modifier = swapIn(sectionPad),
                    ) {
                        GroupContainer(
                            count = visibleSkillPacks.size,
                        ) { index, shape ->
                            val entry = visibleSkillPacks[index]
                            ResourceRow(
                                entry = entry,
                                installed = resourceInstalled(entry),
                                busy = busyResource == entry.slug,
                                onInstall = { installResource(entry) },
                                onClick = { resourceTarget = entry },
                                shape = shape,
                            )
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
                    MarketContentSwap(
                        key = visibleMcpServers.joinToString(",") { it.slug },
                        modifier = swapIn(sectionPad),
                    ) {
                        GroupContainer(
                            count = visibleMcpServers.size,
                        ) { index, shape ->
                            val entry = visibleMcpServers[index]
                            ResourceRow(
                                entry = entry,
                                installed = resourceInstalled(entry),
                                busy = busyResource == entry.slug,
                                needsConfig = resourceNeedsConfig(entry),
                                onInstall = { installResource(entry) },
                                onConfigure = { configureMcp(entry) },
                                onClick = { resourceTarget = entry },
                                shape = shape,
                            )
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
                    MarketContentSwap(
                        key = visibleBundles.joinToString(",") { it.slug },
                        modifier = swapIn(sectionPad),
                    ) {
                        GroupContainer(
                            count = visibleBundles.size,
                        ) { index, shape ->
                            val entry = visibleBundles[index]
                            ResourceRow(
                                entry = entry,
                                installed = resourceInstalled(entry),
                                busy = busyResource == entry.slug,
                                onInstall = { installResource(entry) },
                                onClick = { resourceTarget = entry },
                                shape = shape,
                            )
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
                    val preview = visibleModels.take(8)
                    MarketContentSwap(
                        key = preview.joinToString(",") { it.first + "/" + it.second.modelId },
                        modifier = swapIn(sectionPad),
                    ) {
                        GroupContainer(
                            count = preview.size,
                        ) { index, shape ->
                            val (providerName, model) = preview[index]
                            ModelRow(
                                providerName = providerName,
                                model = model,
                                onClick = { navController.navigate(Screen.SettingModels) },
                                shape = shape,
                            )
                        }
                    }
                }
            }

            if (cardsVisible && depsPending) {
                item(key = "dependency_cache_skeleton") {
                    Box(modifier = swapIn(sectionPad)) { SectionSkeleton(rows = 2) }
                }
            }

            // 原生依赖包：维护型信息，排在所有发现型内容之后。这里只报「本机已缓存了什么」，
            // 清理入口在「本地」二级页的同一分区（一级页不再有分区标题与清空按钮）。
            if (cardsVisible && dependencies.isNotEmpty()) {
                item(key = "dependency_cache") {
                    MarketContentSwap(
                        key = dependencies.joinToString(",") { it.name + it.version },
                        modifier = swapIn(sectionPad),
                    ) {
                        GroupContainer(
                            count = dependencies.size,
                        ) { index, shape ->
                            val dependency = dependencies[index]
                            DependencyRow(
                                name = dependency.name,
                                version = dependency.version,
                                cached = dependency,
                                shape = shape,
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

    KedgePageScaffold(
        // 顶栏不再常驻搜索药丸：入口是右上角那个搜索图标，点开后才在**标题槽位**里
        // morph 出输入框（逻辑照 ChatPage）。所以这里只有一行大标题栏。
        topBar = {
            // 「本地」入口：一级页唯一一条本机内容入口，形态是一枚图标按钮（原先是一整行
            // 卡片列表项）。角标报本机卡片数（市场装的 + 仅本机的，与「本地」页顶部分法一致），
            // 0 张时不挂角标 —— 写法照 McpPickerButton。
            val localAction: @Composable () -> Unit = {
                KedgeBadgedBox(
                    badge = {
                        if (installed.isNotEmpty()) {
                            KedgeBadge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(text = installed.size.toString())
                            }
                        }
                    },
                ) {
                    KedgeIconButton(onClick = { navController.navigate(Screen.ExploreLocal) }) {
                        Icon(
                            imageVector = folderManaged,
                            contentDescription = stringResource(R.string.explore_market_local_page_title),
                        )
                    }
                }
            }
            // 顶栏不再常驻搜索药丸：入口是右上角那个搜索图标，点开后才在**标题槽位**里
            // morph 出输入框（逻辑照 ChatPage，两页共用同一个 [ExploreSearchTopBar]）。
            ExploreSearchTopBar(
                title = stringResource(R.string.explore_market_title),
                subtitle = stringResource(R.string.explore_market_subtitle),
                query = query,
                onQueryChange = { new ->
                    // 清空时把远端结果集刷回全量，与原来的清除按钮一致
                    if (query.isNotBlank() && new.isEmpty()) vm.refresh("")
                    query = new
                },
                expanded = searchExpanded,
                onToggleSearch = { searchExpanded = true },
                onExitSearch = { searchExpanded = false },
                searchExpand = searchExpand,
                scrollBehavior = scrollBehavior,
                extraAction = localAction,
            )
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
        RikkaConfirmDialog(
            show = true,
            title = stringResource(R.string.explore_market_uninstall_title, target.name),
            confirmText = stringResource(R.string.explore_market_uninstall),
            dismissText = stringResource(R.string.explore_market_cancel),
            onConfirm = {
                uninstallTarget = null
                vm.uninstall(target.name) { ok ->
                    scope.launch { Toast.show(if (ok) toastUninstalledNamed.format(target.name) else toastUninstallFailed, !ok) }
                }
            },
            onDismiss = { uninstallTarget = null },
        ) {
            Text(
                stringResource(
                    R.string.explore_market_uninstall_message,
                    target.name,
                    installed[target.name] ?: target.version,
                ),
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
    // 同上：浮层宿主里的 Toast 也在协程里，模板串在组合期取出。
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
                            result.message.ifBlank {
                                if (result.ok) {
                                    toastPublishSubmitted
                                } else {
                                    toastPublishFailed
                                }
                            },
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
                            result.message.ifBlank {
                                if (result.ok) {
                                    toastForkSubmitted
                                } else {
                                    toastForkFailed
                                }
                            },
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
            onRun = { vm.run(entry.name) { r -> scope.launch { Toast.show(r.message.ifBlank { if (r.ok) toastRanNamed.format(entry.name) else toastRunFailed }, !r.ok) } } },
            onInstall = { vm.install(entry) { ok -> scope.launch { Toast.show(if (ok) toastInstalledNamed.format(entry.name) else toastInstallFailed, !ok) } } },
            onUpdate = { vm.update(entry) { ok -> scope.launch { Toast.show(if (ok) toastUpdatedNamed.format(entry.name) else toastUpdateFailed, !ok) } } },
            onUninstall = {
                vm.uninstall(entry.name) { ok ->
                    scope.launch {
                        Toast.show(if (ok) toastUninstalledNamed.format(entry.name) else toastUninstallFailed, !ok)
                        if (ok) onSelectCard(null)
                    }
                }
            },
            onVote = {
                if (!activated) {
                    Toast.show(toastAccountRequired, true)
                } else {
                    vm.vote(entry.name) { r ->
                        scope.launch {
                            Toast.show(r.message.ifBlank { if (r.ok) toastVotedNamed.format(entry.name) else toastVoteFailed }, !r.ok)
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

/** 卡片命中搜索词的口径（名称 + 摘要 + 描述），一级页与卡片市场共用一份。 */
private fun CardIndexEntry.matchesQuery(query: String): Boolean =
    query.isBlank() || (name + summary + description).contains(query, ignoreCase = true)

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
    val density = LocalDensity.current

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
            inFilter && entry.matchesQuery(query)
        }
    }
    // 「全部 N」里的 N 是**当前搜索词下的总数**，不跟着卡片筛选走：选中「已安装」后
    // 它不该从 47 掉成 12（读起来是「全部只有 12 张」），那一格的语义是分母。
    val totalCount = remember(cards, query) { cards.count { it.matchesQuery(query) } }
    // 列表卡片形状与设置页同一份口径（见 [listCardStyle]）。
    val cardStyle = listCardStyle()

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
    // 悬浮筛选栏的钉线（px→dp = 折叠态顶栏下沿）。算法与一级页逐字相同，那边有完整推导；
    // 两页的 chip 轨道必须钉在同一条线上，否则二级页看着就是「chip 一滚就没了」。
    var filterPinLine by remember { mutableStateOf(0.dp) }
    // 列表滚动状态提到页面这一层：sticky 的 chip 栏钉没钉住，要看列表滚到哪了。
    val listState = rememberLazyListState()
    // 页面底色 / 钉住后的底色：与一级页同源（悬浮栏与正文必须同色，只能有一份来源）。
    val pageSurface = CustomColors.pageContainerColor
    val chipDockedSurface = CustomColors.topBarColors.scrolledContainerColor

    KedgePageScaffold(
        topBar = {
            ExploreSearchTopBar(
                title = stringResource(R.string.explore_market_cards_page_title),
                subtitle = null,
                query = query,
                onQueryChange = { new ->
                    // 清空时把远端结果集刷回全量，与一级页一致
                    if (query.isNotBlank() && new.isEmpty()) vm.refresh("")
                    query = new
                },
                expanded = searchExpanded,
                onToggleSearch = { searchExpanded = true },
                onExitSearch = { searchExpanded = false },
                searchExpand = searchExpand,
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = activeNestedScroll(scrollBehavior),
        containerColor = pageSurface,
        md3ScrollBehavior = scrollBehavior,
    ) { innerPadding ->
        // 钉线 = 折叠态顶栏下沿：stickyHeader 只钉在 LazyColumn 自己的顶边上，
        // 不下移就钉在屏幕顶端（正好在顶栏背后）。推导见一级页同名那段注释。
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
            val pinPx = if (limit.isFinite() && limit < 0f && limit > -10_000f) {
                (with(density) { innerPadding.calculateTopPadding().toPx() } - offset + limit)
                    .coerceAtLeast(0f)
            } else {
                0f
            }
            val pin = with(density) { pinPx.toDp() }
            if (pin != filterPinLine) filterPinLine = pin
        }

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
            cardStyle = cardStyle,
            onSelectCard = { selectedCard = it },
            onClearQuery = { query = "" },
            onReload = { vm.refresh("") },
            listState = listState,
            pinLine = filterPinLine,
            pageSurface = pageSurface,
            dockedSurface = chipDockedSurface,
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(top = 8.dp, bottom = 32.dp),
        )
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

/**
 * 二级「卡片市场」的正文：悬浮筛选轨道 + 卡片列表。
 *
 * 形态与一级页的卡片区一致 —— 一行一张全宽卡、每行前置 extension 图标自报类型，
 * 筛选轨道用带图标的 [MarketFilterChip]，卡片总数并进「全部 N」那一格，
 * 页面里不再有分区标题或「共 N 张 · 已安装 N」这类说明行。
 *
 * chip 栏是 [stickyHeader]：滚过它的自然位置后就钉在 [pinLine]（折叠态顶栏下沿）上，
 * 正文从它下面滚过去，底色跟着「钉没钉住」在两档之间渐变 —— 与一级页同一条钉线。
 *
 * 卡片逐项挂 [swapIn]（= `Modifier.animateItem`，上游 ImageToolbox 列表同款，
 * 见其 `ScreenPreferenceSelection.kt` / `HelpListContent.kt` 的逐项 `animateItem()`）：
 * 搜索词或筛选一变，出列表的卡淡出、进列表的卡淡入（延后 40ms）、留下来的卡用弹簧
 * 滑到新位置，而不是整块列表硬切。
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
    cardStyle: ListCardStyle,
    onSelectCard: (CardIndexEntry) -> Unit,
    onClearQuery: () -> Unit,
    onReload: () -> Unit,
    listState: LazyListState,
    pinLine: Dp,
    pageSurface: Color,
    dockedSurface: Color,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    // 「钉住了」= 列表已滚过 chip 栏的自然位置。chip 栏在这一页是首项，所以位号恒为 0：
    // 它自己只要滚过 0 就说明已经顶在钉线上。不写死任何高度，字体缩放/风格切换都不影响。
    val chipsDocked by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    // 整页只剩「卡片连成一条」这一种分组：形状按卡在**结果集**里的位号算（首项圆上边、
    // 末项圆下边、中间四角小圆），项间距取 [ListCardStyle.gap] —— 与一级页 [GroupContainer]
    // 的卡片栈逐像素同款。二级页此前一张卡一格、每格整张圆角 + 12dp 间距，同一批卡在两个
    // 页面是两种卡面，现在两边共用一份口径。
    val swapKey = remember(matched) { matched.joinToString(",") { it.name } }
    LazyColumn(
        state = listState,
        // 列表整体下移一个「折叠态顶栏高度」：sticky 只钉在 LazyColumn 自己的顶边上，
        // 不下移就钉在屏幕顶端（顶栏背后）。正文位置 = 下移量 + contentPadding.top，
        // 两者之和不变，所以版面不动，变的只有钉线。
        modifier = modifier.padding(top = pinLine),
        contentPadding = contentPadding - PaddingValues(top = pinLine),
        verticalArrangement = Arrangement.spacedBy(cardStyle.gap),
    ) {
        stickyHeader(key = "card_filters") {
            val chipBarSurface by animateColorAsState(
                targetValue = if (chipsDocked) dockedSurface else pageSurface,
                animationSpec = tween(durationMillis = 200),
                label = "card_filter_chip_bar_surface",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(chipBarSurface)
                    // 卡与卡的间距已收到 [ListCardStyle.gap]，chip 栏与第一张卡之间留回
                    // 一段呼吸位（底色跟着 [chipBarSurface]，钉住时下半截是这个实色）。
                    .padding(top = 4.dp, bottom = 14.dp),
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    // 边距用 contentPadding 而不是外层 padding：chip 能像卡片一样滑到
                    // 屏幕边缘，不被盒子夹断。
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(CardFilter.entries) { option ->
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
        }

        // 正文的左右 16dp：chip 栏自己带 contentPadding，列表这一层不再带横向内边距，
        // 钉住的实色底才能铺满整屏（与一级页同一手法）。
        val rowPad = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        if (loading && matched.isEmpty()) {
            item(key = "loading") { Box(modifier = swapIn(rowPad)) { CardGridSkeleton() } }
        } else if (matched.isEmpty()) {
            item(key = "empty") {
                Box(modifier = swapIn(rowPad)) {
                    ExploreEmptyState(
                        query = query,
                        scopeLabel = stringResource(R.string.explore_market_cards_page_title),
                        onClearQuery = onClearQuery,
                        onReload = onReload,
                    )
                }
            }
        } else {
            // 关键：一张卡一个 item，滑到哪组合到哪；形状按它在结果集里的位号算，
            // 与一级页那条卡片栈同一份口径（[ListCardStyle.indexedShape]）。
            itemsIndexed(matched, key = { _, entry -> entry.name }) { index, entry ->
                // 搜索换场：`animateItem` 只管「项自己进出 + 邻项挪位」，项号（key = 卡名）
                // 没变时**管不到**「同一格里的内容换了」。所以每格再套一层内容换场，
                // 键取整个结果集的签名 —— 命中集合一变，屏上这几张卡一起走一遍
                // `AnimatedContent` 的默认换场（淡出 → 延迟 90ms 淡入并 0.92→1 放大）。
                MarketContentSwap(key = swapKey, modifier = swapIn(rowPad)) {
                    CompactCard(
                        entry = entry,
                        installedVersion = installed[entry.name],
                        triggers = triggers[entry.name] ?: entry.triggers,
                        hubInfo = hubInfo[entry.name],
                        busy = busyCard == entry.name,
                        onClick = { onSelectCard(entry) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardStyle.indexedShape(index, matched.size),
                    )
                }
            }
        }
    }
}

/**
 * 「查看全部」入口：一级页铺满 [CARD_PREVIEW_COUNT] 张后，去二级页看剩下的。
 * 组内最后一行（形状由调用点按组内位号给出），沿用 option item 的度量，只把文字居中。
 */
@Composable
private fun MarketMoreRow(
    label: String,
    onClick: () -> Unit,
    shape: Shape = AutoCornersShape(ExploreShape.card),
) {
    KedgeOptionItem(
        onClick = onClick,
        shape = shape,
        titleContent = {
            Text(
                text = label,
                style = KedgeTextStyles.title().copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/**
 * 形状一致性锁：整页只用这一套圆角。
 * 轮播 item 26 / 卡片 32 / 图标瓦片 12 / 筛选 chip 12（按下收到 8）；
 * 列表条目行（option item）的形状不在这里 —— 它跟设置页共用 [listCardStyle]，
 * 由用户可配的「列表卡片样式」决定。实心药丸按钮交给 Kedge 组件自己的形状。
 * 新加组件时不要再写魔法数字，从这里取。
 */
private object ExploreShape {
    val carousel = 26.dp
    /** 轮播卡按住：在 26dp 档位上收一档（与筛选 chip 同一套「按压 = 收圆角」语言）。 */
    val carouselPressed = 22.dp
    /** 卡片：网格卡与列表条目统一 32dp 一档（参考稿的 `rounded-lg` = 2rem），按住收 4dp。 */
    val surface = 32.dp
    /** 列表条目卡片：与 [surface] 同档 —— 两处共用一个数，卡片语言只有一个来源。 */
    val card = 32.dp
    /** 旧的分组底板档位：分组容器已改成一叠卡片，这一档只剩骨架条在用。 */
    val group = 18.dp
    /** 图标瓦片：网格卡 40dp 配 12dp 圆角。 */
    val tile = 12.dp
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
 * 组尾的「管理」行：分区标题去掉之后，本地页每个分组末尾用一行 option item 承接
 * 原来标题条上的动作（管理本地工具 / 管理技能 / 管理 MCP / 清空依赖缓存）。
 *
 * 形态与组内条目同一套度量（[KedgeOptionItem] + 组内位号给的形状），文字居中，
 * 行里不再出现「已安装卡片 / 本地工具」这类分区标题字样。
 */
@Composable
private fun GroupActionRow(
    label: String,
    onClick: () -> Unit,
    shape: Shape = AutoCornersShape(ExploreShape.card),
) {
    KedgeOptionItem(
        onClick = onClick,
        shape = shape,
        titleContent = {
            Text(
                text = label,
                style = KedgeTextStyles.title().copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/**
 * 条目组：**一组条目合成一张卡**（首项圆上边、末项圆下边、项间 [ListCardStyle.gap] 间距），
 * 圆角与间距全部走 [listCardStyle] —— 与设置页的列表卡片同一份口径，
 * 于是「设置 → 界面 → 列表卡片样式」里改一次，市场页与设置页一起变。
 *
 * 形状按组内位号算好传给条目：条目自己不猜自己在组里的位置（同 `CardGroup`
 * 把 `indexedShape` 算好再交给 `KedgeOptionItem` 的做法）。
 */
@Composable
private fun GroupContainer(
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, shape: Shape) -> Unit,
) {
    if (count <= 0) return
    val cards = listCardStyle()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(cards.gap),
    ) {
        for (index in 0 until count) {
            content(index, cards.indexedShape(index, count))
        }
    }
}

/**
 * 行尾「下一级」箭头：导航型条目的统一收尾，与设置页的 `PreferenceArrow` 同一语义。
 * 只做指示，不接受点击（点击归整行）。
 */
@Composable
private fun RowChevron() {
    Icon(
        imageVector = chevronRight,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
}

/**
 * 行首图标：设置页 option item 的图标槽尺寸（图标 24dp，外层圆形淡底由
 * [KedgeOptionItem] 自己铺）。内容类型就靠这一枚图标区分，行里不再写「技能合集」这类字样。
 */
@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
 *
 * Miuix 风格不走这套：整段改由 [KedgeFilterChip] 的 Miuix 分支接管（勾选框 + 文本，
 * 见函数体开头），MD3 那套图标块/形状回弹只在 MD3Exp 下生效。
 */
@Composable
private fun MarketFilterChip(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    // Miuix 风格下换成 Kedge 的 Miuix chip（Miuix 0.9.3 没有 chip 组件，
    // KedgeFilterChip 的 Miuix 分支用「Checkbox + 文本」自己拼）。
    // 这里不再画图标块：一整排 chip 各带一块彩色图标砖本来就是 MD3 卡片的语言，
    // Miuix 的筛选语言是勾选框 + 文字，混在一起两边都不像。
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        KedgeFilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
        )
        return
    }
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
 * 骨架 → 真实内容的换场：进出场与邻项挪位全用 `animateItem` 的默认 spec
 * （fadeIn/fadeOut/placementSpec 都是 `spring(StiffnessMediumLow)`）。
 *
 * 不自己调 spec：上游 ImageToolbox 的列表逐项就是裸 `Modifier.animateItem()`
 * （`ScreenPreferenceSelection.kt:222` / `HelpListContent.kt:81` / `TutorialCategoryContent.kt:79`），
 * 手写 tween 只会让「市场的手感」和参照物又分家。`animateItem` 是 [LazyItemScope] 的成员，
 * 只能挂在 lazy item 根节点上。
 */
private fun LazyItemScope.swapIn(base: Modifier): Modifier = base.animateItem()

/**
 * 搜索换场：**同一格**里装的内容换了一批时（搜索词变了 → 命中集合变了），
 * 旧内容淡出微缩、新内容淡入微放，而不是硬切一下。
 *
 * 与 [swapIn] 的分工：`animateItem` 管的是「惰性项**自己**进出 + 邻项挪位」，
 * 而一级页每个分区是**一个**惰性项（[GroupContainer] 把这些行组在一格里），
 * 项号没变 → `animateItem` 什么都不会做，命中行只会硬换。所以格里这一层要单独换场。
 *
 * 换场形状**不写自定义 spec**，用的就是 `AnimatedContent` 的默认 spec
 * （`fadeIn(tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.92f, tween(220, 90))`
 * `togetherWith fadeOut(tween(90))`，外加默认的 `SizeTransform`）：上游 ImageToolbox 的
 * 搜索换场正是这个默认值 —— `HelpListContent.kt` 的整块列表换场
 * （`AnimatedContent(searchKeyword.isBlank() to filteredTips.isNotEmpty())`）与
 * `ScreenPreferenceSelection.kt:123` 都只传 `transitionSpec = { fadeIn() togetherWith fadeOut() }`
 * 或干脆不传。缩放要的就是这个 0.92 的可见幅度：手写 0.97 那一档在屏幕上看不出内容换过。
 *
 * [key] 传「真的变了才该动」的东西：调用点传的是命中集合的签名（`joinToString` 出来的名字串）。
 * 签名一变，`AnimatedContent` 会把上一帧那份 content lambda（闭包捕获的是旧数据）留作离场内容，
 * 于是拿到的是真·新旧两批内容，而不是同一批内容叠两次。
 */
@Composable
private fun MarketContentSwap(
    key: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = key,
        modifier = modifier,
        label = "explore_market_content_swap",
    ) { content() }
}

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
 * 卡片列表骨架：一行一条，与 [CompactCard] 同形（[SkeletonRow] 那张 option item 骨架）。
 * 二级页「卡片市场」还在拉数据时铺三条。
 */
@Composable
private fun CardGridSkeleton() {
    val cards = listCardStyle()
    Column(verticalArrangement = Arrangement.spacedBy(cards.gap)) {
        repeat(3) { index ->
            SkeletonRow(shape = cards.indexedShape(index, 3))
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

/**
 * 条目骨架行：底色 / 圆角 / 内边距 / 图标槽与 [KedgeOptionItem] 的 option item 逐项对齐
 * （12dp 内边距、24dp 图标 + 8dp 圆形垫、标题 / 摘要两条灰块）。
 * 形状由调用点按组内位号给出，到货时是「原地填充」而不是整页重排。
 */
@Composable
private fun SkeletonRow(shape: Shape = AutoCornersShape(ExploreShape.card)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBlock(Modifier.size(40.dp), CircleShape)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(14.dp), AutoCornersShape(ExploreShape.tile))
            SkeletonBlock(Modifier.fillMaxWidth(0.32f).height(11.dp), AutoCornersShape(ExploreShape.tile))
        }
    }
}

/**
 * 分区骨架：与「一条目一行的 [GroupContainer]」同构 —— 一组同形行，到货时原地填充。
 * 一级页已无分区标题，所以这里也不再铺标题条。
 */
@Composable
private fun SectionSkeleton(rows: Int) {
    GroupContainer(count = rows) { _, shape -> SkeletonRow(shape = shape) }
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
            text = if (loading) stringResource(R.string.explore_market_footer_syncing) else stringResource(R.string.explore_market_footer_end),
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
            text = if (query.isBlank()) stringResource(R.string.explore_market_empty_title) else stringResource(R.string.explore_market_empty_no_match_title, query),
            style = KedgeTextStyles.title().copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (query.isBlank()) {
                stringResource(R.string.explore_market_empty_hint, scopeLabel)
            } else {
                stringResource(R.string.explore_market_empty_search_hint)
            },
            style = KedgeTextStyles.body(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        KedgeTextButton(
            onClick = if (query.isBlank()) onReload else onClearQuery,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(if (query.isBlank()) stringResource(R.string.explore_market_reload) else stringResource(R.string.explore_market_clear_search))
        }
    }
}

@Composable
private fun ToolRow(
    tool: LocalToolInfo,
    enabled: Boolean,
    onClick: () -> Unit,
    shape: Shape = AutoCornersShape(ExploreShape.card),
) {
    KedgeOptionItem(
        onClick = onClick,
        shape = shape,
        leadingContent = { RowIcon(tool.icon) },
        titleContent = {
            Text(text = stringResource(tool.titleRes), style = KedgeTextStyles.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = stringResource(tool.descRes),
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            MetaChip(text = if (enabled) stringResource(R.string.explore_market_enabled) else stringResource(R.string.explore_market_disabled), accent = enabled)
        },
    )
}

@Composable
private fun SkillRow(
    name: String,
    description: String,
    onClick: () -> Unit,
    shape: Shape = AutoCornersShape(ExploreShape.card),
) {
    KedgeOptionItem(
        onClick = onClick,
        shape = shape,
        leadingContent = { RowIcon(school) },
        titleContent = {
            Text(text = name, style = KedgeTextStyles.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = description,
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { RowChevron() },
    )
}

@Composable
private fun McpRow(
    server: McpServerConfig,
    status: McpStatus?,
    onClick: () -> Unit,
    // P0-5：装完但缺必填时，行上直接给「配置」入口。
    onConfigure: (() -> Unit)? = null,
    shape: Shape = AutoCornersShape(ExploreShape.card),
) {
    // 展示用 URL 一律过掩码：`?token=` / `&api_key=` 这类 query 不能出现在列表上。
    val displayUrl = maskMcpUrl(server.serverUrl)
    val name = server.commonOptions.name.ifBlank { displayUrl }
    val cardStatus = mcpCardStatus(server, status)
    KedgeOptionItem(
        onClick = onClick,
        shape = shape,
        leadingContent = { RowIcon(dns) },
        titleContent = {
            Text(text = name, style = KedgeTextStyles.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = displayUrl,
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (cardStatus == McpCardStatus.NEEDS_CONFIG && onConfigure != null) {
                    CircleActionButton(
                        icon = heizige.kk.khatkit.app.core.ui.icons.settings,
                        contentDescription = stringResource(R.string.explore_market_mcp_configure_named, name),
                        onClick = onConfigure,
                        size = 34.dp,
                        iconSize = 18.dp,
                    )
                }
                when (cardStatus) {
                    // 缺必填优先：这时底下就算报了「连上」也不认，不能显示已连接。
                    McpCardStatus.NEEDS_CONFIG -> MetaChip(stringResource(R.string.explore_market_mcp_needs_config))
                    McpCardStatus.CONNECT_FAILED -> MetaChip(stringResource(R.string.explore_market_mcp_error))
                    McpCardStatus.CONFIGURED -> when (status) {
                        McpStatus.Connected -> MetaChip(stringResource(R.string.explore_market_mcp_connected), accent = true)
                        McpStatus.Connecting, is McpStatus.Reconnecting -> MetaChip(stringResource(R.string.explore_market_mcp_connecting))
                        is McpStatus.Error -> MetaChip(stringResource(R.string.explore_market_mcp_error))
                        McpStatus.NeedsAuthorization, McpStatus.Authorizing -> MetaChip(stringResource(R.string.explore_market_mcp_needs_auth))
                        else -> MetaChip(
                            if (server.commonOptions.enable) {
                                stringResource(R.string.explore_market_mcp_disconnected)
                            } else {
                                stringResource(R.string.explore_market_mcp_stopped)
                            }
                        )
                    }
                }
            }
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
    // P0-5：MCP 服务条目多一个「配置」入口；技能合集 / 合集包传 null 不显示。
    onConfigure: (() -> Unit)? = null,
    // P0-5：装过但缺必填的 MCP 条目，行上直接标「需要配置」。
    needsConfig: Boolean = false,
    shape: Shape = AutoCornersShape(ExploreShape.card),
) {
    KedgeOptionItem(
        onClick = onClick,
        shape = shape,
        // 行里不再有「技能合集 / MCP 服务 / 合集包」徽标：类型由这枚图标区分
        // （技能 school / MCP dns / 合集包 package2），行首图标就是分类的唯一出处。
        leadingContent = { RowIcon(resourceIcon(entry.icon, entry.kind)) },
        titleContent = {
            Text(
                text = entry.name,
                style = KedgeTextStyles.title(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (needsConfig) {
                    MetaChip(text = stringResource(R.string.explore_market_mcp_needs_config))
                }
                if (!busy && onConfigure != null) {
                    CircleActionButton(
                        icon = heizige.kk.khatkit.app.core.ui.icons.settings,
                        contentDescription = stringResource(R.string.explore_market_mcp_configure_named, entry.name),
                        onClick = onConfigure,
                        size = 34.dp,
                        iconSize = 18.dp,
                    )
                }
                when {
                    busy -> KedgeProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    installed -> CircleActionButton(
                        icon = check,
                        contentDescription = stringResource(R.string.explore_market_state_installed),
                        onClick = null,
                        size = 34.dp,
                        iconSize = 18.dp,
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                    else -> CircleActionButton(
                        icon = downloadForOffline,
                        contentDescription = stringResource(R.string.explore_market_cd_download_named, entry.name),
                        onClick = onInstall,
                        size = 34.dp,
                        iconSize = 18.dp,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    )
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
    /** P0-5：MCP 服务条目给「配置」入口；其它条目为 null。 */
    onConfigure: (() -> Unit)? = null,
    /** P0-5：已装但缺必填，详情里也标「需要配置」，与卡片/本机列表同一判定。 */
    needsConfig: Boolean = false,
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
                    text = stringResource(R.string.explore_market_version_prefix, entry.version),
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
                    KedgeBadge(containerColor = MaterialTheme.colorScheme.primaryContainer) { Text(stringResource(R.string.explore_market_state_installed)) }
                }
                if (needsConfig) {
                    KedgeBadge(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(stringResource(R.string.explore_market_mcp_needs_config))
                    }
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
                        text = stringResource(R.string.explore_market_includes),
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
                    text = stringResource(R.string.explore_market_provided_by, entry.author),
                    style = KedgeTextStyles.footnoteSmall(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BigActionButton(
                    text = when {
                        busy -> stringResource(R.string.explore_market_action_installing)
                        installed -> stringResource(R.string.explore_market_action_reinstall)
                        else -> stringResource(R.string.explore_market_action_download_enable)
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
                    modifier = Modifier.weight(1f),
                )
                // P0-5：先配置再安装 —— 缺必填的 MCP 条目也能在这里进表单预填。
                if (onConfigure != null) {
                    BigActionButton(
                        text = stringResource(R.string.explore_market_mcp_configure),
                        onClick = { if (!busy) onConfigure() },
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelRow(
    providerName: String,
    model: Model,
    onClick: () -> Unit,
    shape: Shape = AutoCornersShape(ExploreShape.card),
) {
    KedgeOptionItem(
        onClick = onClick,
        shape = shape,
        leadingContent = { RowIcon(memory) },
        titleContent = {
            Text(
                text = model.displayName.ifBlank { model.modelId },
                style = KedgeTextStyles.title(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            // 提供方 + 能力标签压成一行：设置页的 option item 摘要只有一行，
            // 这里不再单独排一排胶囊。
            val badges = model.abilityBadges()
            Text(
                text = listOfNotNull(
                    providerName.takeIf { it.isNotBlank() },
                    badges.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                ).joinToString(" · "),
                style = KedgeTextStyles.footnoteSmall(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { RowChevron() },
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
    shape: Shape = AutoCornersShape(ExploreShape.card),
) {
    KedgeOptionItem(
        onClick = onClick,
        shape = shape,
        leadingContent = { RowIcon(extension) },
        titleContent = {
            Text(text = name, style = KedgeTextStyles.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.explore_market_local_card_meta, version),
                style = KedgeTextStyles.body().tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetaChip(text = triggers.joinToString("/").ifBlank { "local" })
                RowChevron()
            }
        },
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
    shape: Shape = AutoCornersShape(ExploreShape.card),
) {
    // 整行不可点（`onClick = null`）：这一行只报「本机缓存了什么」，唯一的动作是行尾删除。
    KedgeOptionItem(
        onClick = null,
        shape = shape,
        leadingContent = { RowIcon(deployedCode) },
        titleContent = {
            Text(
                text = stringResource(R.string.explore_market_name_version, name, version),
                style = KedgeTextStyles.title(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = if (cached != null) {
                    "${formatBytes(cached.sizeBytes)} · SHA-256 ${cached.sha256.take(12)}…"
                } else {
                    stringResource(R.string.explore_market_dependency_pending)
                },
                style = KedgeTextStyles.footnoteSmall().tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (cached != null) {
                CircleActionButton(
                    icon = deleteForever,
                    contentDescription = stringResource(R.string.explore_market_cd_delete_dependency),
                    onClick = onDelete,
                    size = 34.dp,
                    iconSize = 18.dp,
                )
            }
        },
    )
}

/**
 * 精选卡（轮播唯一单元）：MD3 品牌海报卡。
 *
 * 版面自上而下三档：圆形徽标 + 右上角版本号 → 大标题 + 一行副标题 → 安装药丸。
 * 底部按钮走 [KedgeButton]（两套风格各自的配色都在这里给全），不再是手写的 Row 药丸：
 * 一是形状/按压反馈跟全站按钮统一，二是要按需求去掉箭头图标后没必要再自己画一个。
 *
 * **配色由包名哈希生成**（见 [extensionPalette] / [posterTone]）：卡片底色是该卡身份色压暗后的
 * 「海报色」，同一张卡在一级页、二级页、轮播里是同一个色，不需要第二套视觉身份。
 * 徽标轮廓不再跟着身份形走（全站统一圆形），背景也不再是右上角一整块有机形，
 * 而是与关于页同款的动态流光（见 [CardFlowingLight]），颜色仍由上面那套身份色算法给出。
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
    // 身份色由包名哈希决定，与卡片行的类型图标是两套东西：
    // 同一张卡在网格里是哪个颜色，进了轮播还是它，不需要第二套视觉身份（背景流光的其中一半也吃这个色）。
    val identityIndex = abs(entry.name.hashCode()) % extensionPalette.size
    val identity = extensionPalette[identityIndex]
    val poster = posterTone(identity)
    // 流光的另一半是「当前主题色」：换主题或开动态取色时，这张卡的流光跟着整页一起换，见 [CardFlowingLight]。
    val themeAccent = MaterialTheme.colorScheme.primary
    val onPoster = Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AutoCornersShape(pressedCorner))
            .background(poster)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        // 底色仍是算法给出的海报色（上面那层 background），再叠一层卡片尺寸的流光：
        // 与关于页同一个 OS3 着色器，配色是「这张卡的身份色 × 主题色」的三套混色（每 4.5s 换一套），
        // 见 [CardFlowingLight] / [cardFlowingPalette]。
        // 低版本 / 无 RuntimeShader 时这一层直接不画，退化成原来的纯色海报卡。
        CardFlowingLight(
            identity = identity,
            accent = themeAccent,
            poster = poster,
            modifier = Modifier.matchParentSize(),
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
                        .clip(CircleShape)
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
 * 卡片尺寸的流光底：与关于页 [heizige.kk.khatkit.app.core.ui.effect.BgEffectBackground]
 * 共用同一个 OS3 着色器与同一套光点参数（半径、漂移、噪声全部走 `BgEffectConfig` 预设）、
 * 同一套换色弹簧（`dampingRatio = 0.9` / `stiffness = 35`），只有 `uColors` 不一样：
 *
 * - **每一档光点都是两种色的混合**（[cardFlowingPalette]）——这张卡的身份色（取色）×
 *   当前主题的 `colorScheme.primary`。四个光点各吃一个不同的配比与 alpha，于是同一帧里
 *   卡片上就有四团色相不同的柔光，而不是原先「四档同色压暗」的一块平雾。
 * - **三套配比循环**（每 [CardFlowingLightPeriodMillis] 换一套，弹簧插值）：配比在
 *   「偏取色」和「偏主题色」之间来回换气。换主题或开动态取色时，这张卡的流光跟着整页一起换。
 *
 * 传给 painter 的 `bound` 用整张卡的高度（`logoHeight = size.height`），
 * 让四个光点铺满卡片而不是像关于页那样只占上方 78%；横向比高时由 painter 内部的
 * aspect 归一化兜住，光点不会被拉成椭圆。
 *
 * 只负责「画在底色之上」：调用方已经用 [posterTone] 铺了一层不透明海报色，
 * 所以这里的半透明光点叠上去即可，低版本不画也不影响可读性。
 */
@Composable
private fun CardFlowingLight(
    identity: Color,
    accent: Color,
    poster: Color,
    modifier: Modifier = Modifier,
) {
    // Android 13 以下没有 RuntimeShader：直接不画，露出下面那层海报色。
    // 与 BgEffectBackground 一样必须是显式版本比较 + 提前 return，lint 的 NewApi 才认。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isRuntimeShaderSupported()) return

    val painter = remember { BgEffectPainter() }
    val animTime = rememberFrameTimeSeconds()
    // [poster] 进 key：对比度上限是按「叠在这张卡的底色上」算的，底色变了（换身份色）要重算。
    val stages = remember(identity, accent, poster) { cardFlowingPalette(identity, accent, poster) }
    val stage = remember { Animatable(0f) }

    LaunchedEffect(stages) {
        var target = 1f
        while (isActive) {
            delay(CardFlowingLightPeriodMillis)
            stage.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
            )
            target += 1f
        }
    }

    Canvas(modifier = modifier) {
        painter.updateResolution(size.width, size.height)
        // 固定吃深色预设：海报底本身就是深色，深色预设的 `lightOffset = 0` 不会在整张卡上
        // 再抬一层亮度（浅色预设那个 +0.1 会把白字压到 2:1，见下面 [cardFlowingPalette]）。
        painter.updatePresetIfNeeded(size.height, size.height, size.width, isDark = true)
        val current = stage.value
        val base = current.toInt()
        val fraction = current - base
        val from = stages[base % stages.size]
        val to = stages[(base + 1) % stages.size]
        painter.updateColors(FloatArray(16) { i -> from[i] + (to[i] - from[i]) * fraction })
        painter.updateAnimTime(animTime())
        drawRect(painter.brush)
    }
}

/** 三套配色之间的换气间隔。比关于页（dark 预设 8s × 0.5 = 4s）略慢，卡片小、换色更显眼。 */
private const val CardFlowingLightPeriodMillis = 4500L

/**
 * 四个光点各自「取色 : 主题色」的配比，三套循环用。每套内部刻意拉开差距（0.12 → 0.82），
 * 让同一帧里四团光的色相彼此不同；套与套之间又反过来（这套偏取色的位号，下套偏主题色），
 * 于是循环起来是「整张卡缓慢换气」而不是「四团光原地呼吸」。
 */
private val cardLightMix = listOf(
    listOf(0.12f, 0.62f, 0.34f, 0.82f),
    listOf(0.82f, 0.26f, 0.60f, 0.14f),
    listOf(0.46f, 0.88f, 0.20f, 0.58f),
)

/** 每档光点的 alpha（0.42–0.62，与关于页深色预设同一档）：越高越压得住海报底、越显主题色。 */
private val cardLightAlpha = listOf(
    listOf(0.56f, 0.46f, 0.62f, 0.50f),
    listOf(0.48f, 0.60f, 0.44f, 0.58f),
    listOf(0.60f, 0.42f, 0.56f, 0.48f),
)

/**
 * 光点叠在海报底上之后允许的最大相对亮度（0.15 ⇒ 白字对比度 ≈ 4.7:1，见 [capAgainstPoster]）。
 * 取 0.15 而不是踩在 4.5:1 的 0.183 上，是给「噪声把光点往灰里拉」留余量 ——
 * 同亮度的色去饱和后亮度会升，离线复刻这份着色器实测会多出约 12%。
 */
private const val CardLightMaxLuminance = 0.15f

/**
 * 流光配色：三套（每套 4 个光点）`uColors`，每点 = 身份色 × 主题色的混色再压到亮度上限。
 *
 * **每个光点单独算上限**（[capAgainstPoster]）：混进主题色之后光点会变亮（深色主题下
 * `primary` 是浅紫），亮到一定程度白字就读不出来，所以按「这一点的颜色、这个 alpha 叠在
 * 这张卡的底色上」反解出一个缩放系数，只压亮度、不动色相与饱和度。
 *
 * 数字是离线复刻同一份着色器跑出来的：8 个身份色 × 6 个主题色（MD3 默认浅/深 + 4 个动态
 * 取色）× 3 套配色 × 16 个时刻 —— 纯白字最低 4.71:1（改版前 4.77:1，可读性没有退步），
 * 而卡面亮度的空间标准差从 0.0093 抬到 0.0346（≈3.7×）：这就是「原来太平」的量化口径。
 */
private fun cardFlowingPalette(identity: Color, accent: Color, poster: Color): Array<FloatArray> =
    Array(cardLightMix.size) { stage ->
        val mixes = cardLightMix[stage]
        val alphas = cardLightAlpha[stage]
        val lights = Array(4) { i ->
            capAgainstPoster(
                light = lerpColor(identity, accent, mixes[i]),
                poster = poster,
                alpha = alphas[i],
            )
        }
        FloatArray(16) { i ->
            val light = lights[i / 4]
            when (i % 4) {
                0 -> light.red
                1 -> light.green
                2 -> light.blue
                else -> alphas[i / 4]
            }
        }
    }

/** 逐通道线性插值（不用 `androidx.compose.ui.graphics.lerp`，避免和别处的 lerp 重名）。 */
private fun lerpColor(start: Color, stop: Color, fraction: Float): Color = Color(
    red = start.red + (stop.red - start.red) * fraction,
    green = start.green + (stop.green - start.green) * fraction,
    blue = start.blue + (stop.blue - start.blue) * fraction,
    alpha = 1f,
)

/**
 * 把 [light] 同比例压暗到「以 [alpha] 叠在 [poster] 上之后，亮度不超过 [CardLightMaxLuminance]」
 * 的最亮那一点（二分 24 次，收敛到 6e-8 以内）。
 *
 * 缩放三通道等价于只压 HSV 的 V：色相与饱和度分毫不动，所以压完还是「那个颜色」，只是暗了一档。
 */
private fun capAgainstPoster(light: Color, poster: Color, alpha: Float): Color {
    var low = 0f
    var high = 1f
    repeat(CardLightCapSteps) {
        val mid = (low + high) / 2f
        val luminance = relativeLuminance(
            red = light.red * mid * alpha + poster.red * (1f - alpha),
            green = light.green * mid * alpha + poster.green * (1f - alpha),
            blue = light.blue * mid * alpha + poster.blue * (1f - alpha),
        )
        if (luminance <= CardLightMaxLuminance) low = mid else high = mid
    }
    return Color(
        red = light.red * low,
        green = light.green * low,
        blue = light.blue * low,
        alpha = 1f,
    )
}

private const val CardLightCapSteps = 24

/** WCAG 相对亮度：先按 sRGB 传输函数线性化，再按人眼感光加权。 */
private fun relativeLuminance(red: Float, green: Float, blue: Float): Float =
    0.2126f * channelToLinear(red) + 0.7152f * channelToLinear(green) + 0.0722f * channelToLinear(blue)

private fun channelToLinear(channel: Float): Float =
    if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

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

@Composable
private fun CardInstallState.label(): String = when (this) {
    CardInstallState.INSTALL -> stringResource(R.string.explore_market_state_install)
    CardInstallState.UPDATE -> stringResource(R.string.explore_market_state_update)
    CardInstallState.INSTALLED -> stringResource(R.string.explore_market_state_installed)
}

@Composable
private fun CardInstallState.busyLabel(): String = when (this) {
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
private fun compactDownloadCount(downloads: Long, locale: Locale): String? = runCatching {
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

/** 卡片元信息压成一行文本，取代一排小胶囊（原来 2 栏窄卡里胶囊墙会挤成三行）。 */
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
 * 卡片列表单元：设置页 option item 的一行 —— 行首图标（[extension]）+ 名称 + 摘要与元信息
 * + 行尾「安装状态 / 下一级箭头」。一级页把它放进 [GroupContainer]（组内位号决定圆角），
 * 二级页一张卡一个 item（整张圆角）。
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
    shape: Shape = AutoCornersShape(ExploreShape.card),
) {
    // 状态判定与轮播药丸同一套。
    val stateLabel = installedVersion?.let { cardInstallState(it, entry.version).label() }
    KedgeOptionItem(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        leadingContent = { RowIcon(extension) },
        titleContent = {
            Text(
                text = entry.name,
                style = KedgeTextStyles.title(),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = entry.summary.ifBlank { entry.description },
                    style = KedgeTextStyles.body(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                )
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    busy -> KedgeProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    stateLabel != null -> MetaChip(stateLabel, accent = true)
                }
                RowChevron()
            }
        },
    )
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
                    text = stringResource(R.string.explore_market_version_prefix, entry.version),
                    style = KedgeTextStyles.title(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (installedVersion != null) {
                KedgeBadge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text(stringResource(R.string.explore_market_installed_version_badge, installedVersion))
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
                    ExtensionBadge(dependency.name, stringResource(R.string.explore_market_name_version, dependency.name, dependency.version), deployedCode)
                }
                displayTriggers.forEach { trigger ->
                    KedgeBadge(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(if (trigger == CardManifest.TRIGGER_AI) stringResource(R.string.explore_market_card_filter_ai) else stringResource(R.string.explore_market_trigger_user))
                    }
                }
                hubInfo?.let { info ->
                    // 免费卡片不再挂「免费」胶囊（默认就是免费，标签只是噪音）；
                    // 收费卡片的价格仍然必须露出，否则用户看不到计费方式。
                    if (info.priceCents > 0L) {
                        KedgeBadge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(stringResource(R.string.explore_market_price_per_call, formatYuan(info.priceCents)))
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
                Text(stringResource(R.string.explore_market_section_dependencies), style = KedgeTextStyles.title(), fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    entry.dependencies.forEach { dependency ->
                        ExtensionBadge(
                            seed = dependency.name,
                            label = stringResource(R.string.explore_market_name_version, dependency.name, dependency.version),
                            icon = deployedCode,
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
                    Text(stringResource(R.string.explore_market_processing), style = KedgeTextStyles.body())
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (installedVersion != null) {
                    if (CardManifest.TRIGGER_USER in triggers) {
                        BigActionButton(
                            text = stringResource(R.string.explore_market_run_card),
                            onClick = onRun,
                            container = MaterialTheme.colorScheme.primary.harmonizeWithPrimary(),
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    if (installedVersion != entry.version && entry.url.isNotBlank()) {
                        BigActionButton(
                            text = stringResource(R.string.explore_market_update_to_version, entry.version),
                            onClick = onUpdate,
                            container = MaterialTheme.colorScheme.primary.harmonizeWithPrimary(),
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButtonLike(text = stringResource(R.string.explore_market_secrets), onClick = onSecretsClick, modifier = Modifier.weight(1f))
                        OutlinedButtonLike(text = stringResource(R.string.explore_market_uninstall), onClick = onUninstall, modifier = Modifier.weight(1f))
                    }
                    if (activated) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButtonLike(text = stringResource(R.string.explore_market_publish_to_hub), onClick = onPublishClick, modifier = Modifier.weight(1f))
                            OutlinedButtonLike(text = stringResource(R.string.explore_market_submit_revision), onClick = onForkClick, modifier = Modifier.weight(1f))
                        }
                    }
                } else if (entry.url.isNotBlank()) {
                    BigActionButton(
                        text = stringResource(R.string.explore_market_install_card),
                        onClick = onInstall,
                        container = MaterialTheme.colorScheme.primary.harmonizeWithPrimary(),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                if (hubInfo != null) {
                    OutlinedButtonLike(
                        text = if (activated) stringResource(R.string.explore_market_vote) else stringResource(R.string.explore_market_vote_needs_activation),
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
        title = { Text(stringResource(R.string.explore_market_publish_dialog_title, entry.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KedgeOutlinedTextFieldWithSlots(
                    value = license,
                    onValueChange = { license = it },
                    label = { Text(stringResource(R.string.explore_market_license_label)) },
                    singleLine = true,
                    shape = AutoCornersShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                KedgeOutlinedTextFieldWithSlots(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.explore_market_price_label)) },
                    singleLine = true,
                    shape = AutoCornersShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                KedgeOutlinedTextFieldWithSlots(
                    value = changelog,
                    onValueChange = { changelog = it },
                    label = { Text(stringResource(R.string.explore_market_changelog_label)) },
                    shape = AutoCornersShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.explore_market_publish_note),
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
                Text(stringResource(R.string.explore_market_publish_submit))
            }
        },
        dismissButton = {
            KedgeTextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.explore_market_cancel)) }
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
        title = { Text(stringResource(R.string.explore_market_fork_dialog_title, entry.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KedgeOutlinedTextFieldWithSlots(
                    value = parent,
                    onValueChange = { parent = it },
                    label = { Text(stringResource(R.string.explore_market_parent_version)) },
                    singleLine = true,
                    shape = AutoCornersShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                KedgeOutlinedTextFieldWithSlots(
                    value = newVersion,
                    onValueChange = { newVersion = it },
                    label = { Text(stringResource(R.string.explore_market_new_version)) },
                    singleLine = true,
                    shape = AutoCornersShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                KedgeOutlinedTextFieldWithSlots(
                    value = changelog,
                    onValueChange = { changelog = it },
                    label = { Text(stringResource(R.string.explore_market_changelog_required)) },
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
                Text(stringResource(R.string.explore_market_submit))
            }
        },
        dismissButton = {
            KedgeTextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.explore_market_cancel)) }
        },
    )
}

private fun formatYuan(cents: Long): String {
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
 * 卡片的详情 / 运行 / 卸载 / 发布继续复用 [CardMarketOverlays]；
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
    val uiStyle = vm.uiStyle
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var selectedCard by remember { mutableStateOf<CardIndexEntry?>(null) }
    var secretCard by remember { mutableStateOf<String?>(null) }
    var publishTarget by remember { mutableStateOf<CardIndexEntry?>(null) }
    var forkTarget by remember { mutableStateOf<CardIndexEntry?>(null) }
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

    // 本机卡片没有市场条目，就地合成一个只含名称/版本/空 url 的索引项，
    // 让详情抽屉（运行、卸载、密钥）对它同样可用 —— 一级页的「本机卡片」也是这么做的。
    fun installedEntry(name: String): CardIndexEntry =
        cards.firstOrNull { it.name == name }
            ?: CardIndexEntry(name = name, version = installed[name].orEmpty(), url = "")

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.explore_market_local_page_title),
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        // 与一级页 / 卡片市场页同一口径：页面里没有分区标题，一行一张全宽条目，
        // 类型由每行的前置图标自报（卡片 extension / 工具 build / 技能 school /
        // MCP dns / 依赖 deployedCode）。分组之间只靠间距分块。
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding +
                PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (loading && installed.isEmpty() && skills.isEmpty() && dependencyViews.isEmpty()) {
                item(key = "loading") { SectionSkeleton(rows = 3) }
            }

            if (installedFromMarket.isNotEmpty()) {
                item(key = "installed_market") {
                    GroupContainer(count = installedFromMarket.size) { index, shape ->
                        val name = installedFromMarket[index]
                        LocalCardRow(
                            name = name,
                            version = installed[name].orEmpty(),
                            triggers = triggers[name].orEmpty(),
                            onClick = { selectedCard = installedEntry(name) },
                            shape = shape,
                        )
                    }
                }
            }

            if (localOnly.isNotEmpty()) {
                item(key = "local_only") {
                    GroupContainer(count = localOnly.size) { index, shape ->
                        val name = localOnly[index]
                        LocalCardRow(
                            name = name,
                            version = installed[name].orEmpty(),
                            triggers = triggers[name].orEmpty(),
                            onClick = { selectedCard = installedEntry(name) },
                            shape = shape,
                        )
                    }
                }
            }

            item(key = "tools") {
                GroupContainer(count = localToolCatalog.size + 1) { index, shape ->
                    if (index < localToolCatalog.size) {
                        val tool = localToolCatalog[index]
                        ToolRow(
                            tool = tool,
                            enabled = tool.option in currentAssistantLocalTools,
                            onClick = {
                                navController.navigate(
                                    Screen.AssistantLocalTool(settings.assistantId.toString())
                                )
                            },
                            shape = shape,
                        )
                    } else {
                        GroupActionRow(
                            label = stringResource(R.string.explore_market_manage_local_tools),
                            onClick = {
                                navController.navigate(
                                    Screen.AssistantLocalTool(settings.assistantId.toString())
                                )
                            },
                            shape = shape,
                        )
                    }
                }
            }

            if (skills.isNotEmpty()) {
                item(key = "skills") {
                    GroupContainer(count = skills.size + 1) { index, shape ->
                        if (index < skills.size) {
                            val skill = skills[index]
                            SkillRow(
                                name = skill.name,
                                description = skill.description,
                                onClick = { navController.navigate(Screen.SkillDetail(skill.name)) },
                                shape = shape,
                            )
                        } else {
                            GroupActionRow(
                                label = stringResource(R.string.explore_market_manage_skills),
                                onClick = { navController.navigate(Screen.Skills) },
                                shape = shape,
                            )
                        }
                    }
                }
            }

            if (mcpServers.isNotEmpty()) {
                item(key = "mcp") {
                    GroupContainer(count = mcpServers.size + 1) { index, shape ->
                        if (index < mcpServers.size) {
                            val server = mcpServers[index]
                            McpRow(
                                server = server,
                                status = mcpStatus[server.id],
                                onClick = { navController.navigate(Screen.SettingMcp) },
                                onConfigure = { mcpConfigTarget = rePlanMcpInstall(server) },
                                shape = shape,
                            )
                        } else {
                            GroupActionRow(
                                label = stringResource(R.string.explore_market_manage_mcp),
                                onClick = { navController.navigate(Screen.SettingMcp) },
                                shape = shape,
                            )
                        }
                    }
                }
            }

            if (dependencyViews.isNotEmpty()) {
                item(key = "dependency_cache") {
                    GroupContainer(count = dependencyViews.size + if (dependencies.isEmpty()) 0 else 1) { index, shape ->
                        if (index < dependencyViews.size) {
                            val view = dependencyViews[index]
                            DependencyRow(
                                name = view.name,
                                version = view.version,
                                cached = view.cached,
                                shape = shape,
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
                        } else {
                            GroupActionRow(
                                label = stringResource(R.string.explore_market_clear),
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
                                shape = shape,
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
