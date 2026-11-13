package heizige.kk.khatkit.uikit

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.ui.UiSheetOptions
import heizige.kk.khatkit.uikit.mochi.MochiNavigationBar
import heizige.kk.khatkit.uikit.mochi.MochiNavigationItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.collections.immutable.toPersistentList
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.navigation.MochiTitleBar
import ui.feice.mochi.theme.MochiDefaults

data class KhatKitPageAction(val event: String, val label: String, val imageVector: ImageVector)
internal val LocalKhatKitContentBottomInset = compositionLocalOf { 0.dp }
internal val LocalKhatKitContentTopInset = compositionLocalOf { 0.dp }

/** The enclosing Mochi route owns back navigation. */
val LocalKhatKitPageBack = staticCompositionLocalOf<(() -> Unit)?> { null }
/** Inline page content for card routes that already own the outer title bar. */
val LocalKhatKitPageInline = staticCompositionLocalOf { false }

/**
 * 卡片正文滚动进度的注册口（0 = 停在顶部，1 = 已滚过顶栏标题淡入距离）。
 *
 * 内联到卡片整页（[LocalKhatKitPageInline]）时，页面把自己的进度读取函数注册进来，
 * 外层 Mochi 骨架的顶栏就按它淡入标题：页内大标题（page_title / 介绍卡）滚走后才出现标题。
 * 用「注册读取器」而不是「直接给值」，是因为进度在外层骨架里读、在 [KhatKitPage] 内部算。
 */
val LocalKhatKitPageRegisterScrollProgress =
    staticCompositionLocalOf<((State<Float>) -> Unit)?> { null }

/** Legacy sheet options stay parseable, but all card content occupies a page. */
@Composable
fun KhatKitPage(
    title: String,
    @Suppress("UNUSED_PARAMETER") imageVector: ImageVector,
    options: UiSheetOptions,
    @Suppress("UNUSED_PARAMETER") dismissText: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    headerActions: List<KhatKitPageAction> = emptyList(),
    overflowActions: List<KhatKitPageAction> = emptyList(),
    @Suppress("UNUSED_PARAMETER") overflowIcon: ImageVector? = null,
    onAction: (KhatKitPageAction) -> Unit = {},
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit = {},
    scrollKey: Any? = null,
    contentKey: Any? = null,
    lazyListState: LazyListState? = null,
    pagerState: PagerState? = null,
    titleProgress: (() -> Float)? = null,
    backgroundProgress: (() -> Float)? = null,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    val scroll = remember(scrollKey) { ScrollState(0) }
    val density = LocalDensity.current
    val back = LocalKhatKitPageBack.current ?: {
        options.backEvent?.let(onNavigate) ?: onDismiss()
    }
    val inline = LocalKhatKitPageInline.current
    val navigation = options.navigation?.takeIf { it.items.isNotEmpty() }
    val selected = pagerState?.let { navigation?.items?.getOrNull(it.targetPage)?.event }
        ?: navigation?.selected
    var navigationPending by remember(contentKey) { mutableStateOf(false) }
    var barHeight by remember { mutableStateOf(0) }
    var bottomHeight by remember { mutableStateOf(0) }
    val topInset = with(density) { barHeight.toDp() }
    val bottomInset = with(density) { bottomHeight.toDp() }

    LaunchedEffect(options.loadMoreEvent, lazyListState, scroll, contentKey) {
        val event = options.loadMoreEvent ?: return@LaunchedEffect
        var sent = false
        snapshotFlow {
            if (lazyListState != null) {
                val layout = lazyListState.layoutInfo
                layout.totalItemsCount > 0 &&
                    (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 2
            } else scroll.maxValue > 0 && scroll.value >= scroll.maxValue - with(density) { 64.dp.toPx() }
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && !sent) {
                sent = true
                onNavigate(event)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MochiDefaults.groupedBackground())) {
        val paged = pagerState != null
        Column(
            Modifier.fillMaxSize()
                .then(if (!paged) Modifier.padding(top = topInset, bottom = bottomInset) else Modifier)
                .then(if (pagerState == null && lazyListState == null) Modifier.verticalScroll(scroll) else Modifier),
        ) {
            // 内联到卡片整页时，把正文的滚动进度报给外层骨架：顶栏小标题只在页内
            // 大标题滚走之后才淡入（分页路径各自按列表算，不进这里）。
            val densityLocal = LocalDensity.current
            val titleFadePx = with(densityLocal) { 72.dp.toPx() }
            val register = LocalKhatKitPageRegisterScrollProgress.current
            // 把这一页的滚动进度以 State 形式注册给外层骨架：State 才能在读的时候触发重组
            // （scroll.value 变了 → 骨架顶栏重组 → 标题淡入）。分页页恒为 1（标题常显）。
            val progressState = remember(scroll, paged, titleFadePx) {
                derivedStateOf {
                    if (paged) 1f else (scroll.value.toFloat() / titleFadePx).coerceIn(0f, 1f)
                }
            }
            DisposableEffect(register, progressState) {
                register?.invoke(progressState)
                onDispose { }
            }
            CompositionLocalProvider(
                LocalKhatKitContentTopInset provides if (paged) topInset else 0.dp,
                LocalKhatKitContentBottomInset provides if (paged) bottomInset else 0.dp,
            ) { content(onDismiss) }
        }
        // CardWidgetPage already owns the route title bar; inline content keeps
        // the same page body and actions without nesting a second title bar.
        if (!inline) {
            MochiTitleBar(
                title = title,
                modifier = Modifier.onSizeChanged { barHeight = it.height },
                background = MochiDefaults.groupedBackground(),
                titleProgress = if (paged) titleProgress ?: { 1f } else { { 1f } },
                backgroundProgress = backgroundProgress ?: { 1f },
                navigationIcon = { MochiBackButton(onClick = back) },
                actions = {
                    options.topActions.forEach { item ->
                        MochiBarIconButton(
                            onClick = { onNavigate(item.event) },
                            navId = "card.action.${item.event}", contentKey = item.icon ?: item.event,
                        ) {
                            Icon(
                                UiIconVectors.vector(item.icon ?: "more") ?: Icons.Default.MoreHoriz,
                                contentDescription = item.label,
                            )
                        }
                    }
                    headerActions.forEach { item ->
                        MochiBarIconButton(
                            onClick = { onAction(item) },
                            navId = "card.action.${item.event}", contentKey = item.event,
                        ) { Icon(item.imageVector, contentDescription = item.label) }
                    }
                    if (overflowActions.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        MochiDropdown(
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            items = overflowActions.map { MochiItem(it.label) }.toPersistentList(),
                            onItemClick = { index ->
                                expanded = false
                                overflowActions.getOrNull(index)?.let(onAction)
                            },
                        ) { Icon(Icons.Default.MoreHoriz, contentDescription = "更多操作") }
                    }
                },
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { bottomHeight = it.height }) {
            if (confirmText != null && onConfirm != null) {
                MochiButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(confirmText) }
            }
            if (navigation != null) {
                MochiNavigationBar(
                    selectedItem = selected ?: navigation.items.first().event,
                    onItemSelected = { event ->
                        if ((pagerState != null || !navigationPending) && event != selected &&
                            navigation.items.any { it.event == event && it.enabled }) {
                            if (pagerState == null) navigationPending = true
                            onNavigate(event)
                        }
                    },
                    navigationItems = navigation.items.map { item ->
                        MochiNavigationItem(
                            key = item.event, label = item.label,
                            icon = UiIconVectors.vector(item.icon ?: "apps") ?: UiIconVectors.vector("home")!!,
                            enabled = item.enabled,
                        )
                    },
                    floating = true,
                )
            } else Spacer(Modifier.navigationBarsPadding())
        }
    }
}
