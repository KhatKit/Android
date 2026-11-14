package heizige.kk.khatkit.uikit

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.ui.UiScreenPage
import heizige.kk.khatkit.ui.UiSheetOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ui.feice.mochi.basic.MochiRefreshIndicator
import ui.feice.mochi.basic.Text as MochiText
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.basic.mochiRefreshHold
import ui.feice.mochi.basic.rememberMochiRefreshState
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.effect.overscroll.rememberMochiOverscrollEffect

/** Navigation stays entirely on the UI thread; Lua only handles real actions/data changes. */
@Composable
internal fun PagedNodeTreePage(
    title: String,
    pages: Map<String, UiScreenPage>,
    options: UiSheetOptions,
    pickerHost: FormPickerHost?,
    onAction: (String, Map<String, Any?>) -> Unit,
    onDismiss: () -> Unit,
    markdown: @Composable (String, Modifier) -> Unit,
    image: @Composable (String, String?, Modifier) -> Unit,
) {
    val navigation = options.navigation ?: return
    val ids = navigation.items.map { it.event }
    val initial = ids.indexOf(navigation.selected).coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initial) { ids.size }
    val scope = rememberCoroutineScope()
    // 禁用页不能靠滑动进入（导航项 enabled = false 时仍然显示，但不能被滑到）：
    // 滑到禁用页就弹回最近的可进入页。其余页一律允许原生横滑切页。
    LaunchedEffect(pager.settledPage) {
        if (navigation.items.getOrNull(pager.settledPage)?.enabled != false) return@LaunchedEffect
        val fallback = navigation.items.indexOfFirst { it.enabled }
        if (fallback >= 0 && fallback != pager.settledPage) pager.animateScrollToPage(fallback)
    }
    val lists = ids.map { id -> key(id) { rememberLazyListState() } }
    val scrolls = ids.map { id -> key(id) { rememberScrollState() } }
    val values = remember(ids) { ids.associateWith { mutableStateMapOf<String, Any?>() } }
    val counterHosts = ids.associateWith { id -> key(id) { rememberCounterButtonHost() } }
    // 拉刷新：动作发出去后等脚本回传的新一屏当作「刷新完成」（最多 8 秒），转圈才收回去。
    var refreshWait by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    LaunchedEffect(pages) {
        refreshWait?.complete(Unit)
        refreshWait = null
    }
    // Data updates retain every tab's scroll/input state. A deliberate script
    // selection (e.g. leaving settings) may still choose a different page.
    LaunchedEffect(navigation.selected) {
        val target = ids.indexOf(navigation.selected).coerceAtLeast(0)
        if (pager.currentPage != target || pager.currentPageOffsetFraction != 0f) {
            pager.animateScrollToPage(target, animationSpec = tween(260))
        }
    }
    val activeId = ids[pager.settledPage]
    val activePage = pages.getValue(activeId)
    val activeValues = values.getValue(activeId)
    fun action(event: String) = onAction(event,
        activePage.root.collect(activeValues) + (UiScreenPage.PAGE_VALUE_KEY to activeId))
    // 顶栏折叠：内容从栏下面开始，滚过一小段后栏里的小标题淡入、栏底色渐现。
    // 注意 topInset 要在页面里读，见下面的 HorizontalPager。
    val density = LocalDensity.current
    val edgeFadePx = with(density) { 8.dp.toPx() }
    val collapsePx = with(density) { 72.dp.toPx() }
    fun pageScrollProgress(index: Int, edgePx: Float): Float {
        val page = pages.getValue(ids[index])
        val scroll = if (page.root.lazyScreenRoot()) {
            val list = lists[index]
            if (list.firstVisibleItemIndex > 0) Float.MAX_VALUE / 4f
            else list.firstVisibleItemScrollOffset.toFloat()
        } else {
            scrolls[index].value.toFloat()
        }
        return smoothstep(0f, edgePx, scroll)
    }

    /**
     * `settledPage` changes only after a swipe crosses the snap point. Reading
     * it directly made the top bar jump at that boundary: the outgoing page
     * could be collapsed while the incoming page suddenly started expanded.
     * Blend the two visible pages using the live pager offset so the bar follows
     * the same horizontal gesture as the content.
     */
    fun pagedProgress(edgePx: Float): Float {
        val from = pager.currentPage.coerceIn(0, ids.lastIndex)
        val signedOffset = pager.currentPageOffsetFraction
        val direction = if (signedOffset >= 0f) 1 else -1
        val target = (from + direction).coerceIn(0, ids.lastIndex)
        val fraction = (kotlin.math.abs(signedOffset) * 2f).coerceIn(0f, 1f)
        val fromProgress = pageScrollProgress(from, edgePx)
        val targetProgress = pageScrollProgress(target, edgePx)
        return fromProgress + (targetProgress - fromProgress) * fraction
    }

    // 卡片页顶部本来就是页内大标题（page_title），栏里的小标题应当等大标题滚走后再渐现：
    // 手指向上滑、内容向上滚过 collapsePx 才淡入；向下回到顶部则让位给正文标题。
    // 横向切页时同时混合相邻页，
    // 顶栏和页面内容保持同一条手势进度，避免在 settledPage 切换点突然跳变。
    val titleProgress: () -> Float = { pagedProgress(collapsePx) }
    val backgroundProgress: () -> Float = { pagedProgress(edgeFadePx) }

    KhatKitPage(
        title = title,
        imageVector = Icons.Default.Edit,
        options = options.copy(loadMoreEvent = activePage.loadMoreEvent),
        onDismiss = onDismiss,
        onNavigate = { event ->
            val target = ids.indexOf(event)
            if (target >= 0) scope.launch { pager.animateScrollToPage(target, animationSpec = tween(220)) }
            else action(event)
        },
        scrollKey = activeId,
        contentKey = activePage.root,
        lazyListState = lists[pager.settledPage].takeIf { activePage.root.lazyScreenRoot() },
        pagerState = pager,
        titleProgress = titleProgress,
        backgroundProgress = backgroundProgress,
    ) {
        // 翻页用 pager 自带的横向滚动（scrollable 参与嵌套滚动）：页内的横向控件
        // （分段选择器、横向滚动区）先消费手势，空白处拖动才切页。之前用
        // pointerInput + detectHorizontalDragGestures 全屏拦横拖，把页内所有横向交互一起吃掉。
        HorizontalPager(
            state = pager,
            key = { ids[it] },
            modifier = Modifier.fillMaxSize(),
            // Keep adjacent lazy lists alive during a swipe. Each list still
            // composes only its viewport, rather than rebuilding the chart as
            // its page crosses the pager's composition boundary.
            beyondViewportPageCount = 1,
            userScrollEnabled = true,
        ) { index ->
            // 顶栏让出的高度由页面提供，必须在这里读，外面读到的是默认 0。
            val topInset = LocalKhatKitContentTopInset.current
            val id = ids[index]
            val page = pages.getValue(id)
            val pageValues = values.getValue(id)
            val errors = remember(page.root) { mutableStateMapOf<String, String>() }
            // 脚本重新出屏（page.root 换新）时，把「脚本改动过的字段值」写回宿主 state。
            // 典型场景：卡片把剪贴板内容填进输入框——在此之前分页页的 values 只在用户输入时更新，
            // 脚本改的值永远显示不出来。只同步与上一棵树不同的字段，用户正在输入的内容不会被盖掉。
            val previousFields = remember(id) { mutableStateOf<Map<String, Any?>>(emptyMap()) }
            LaunchedEffect(page.root) {
                val current = page.root.initialValues()
                if (previousFields.value.isEmpty()) {
                    current.forEach { (field, value) ->
                        if (!pageValues.containsKey(field)) pageValues[field] = value
                    }
                } else {
                    current.forEach { (field, value) ->
                        if (previousFields.value[field] != value) pageValues[field] = value
                    }
                }
                previousFields.value = current
            }
            val submit: (String) -> Unit = { event ->
                errors.clear()
                errors.putAll(page.root.errors(pageValues))
                val queuedCounter = page.root.walk().any { it.type == "CounterButton" && it.props["action"] == event }
                if (errors.isEmpty() && (!pager.isScrollInProgress || queuedCounter)) {
                    onAction(event, page.root.collect(pageValues) + (UiScreenPage.PAGE_VALUE_KEY to id))
                }
            }
            val currentPicker = pickerHost.takeIf { pager.settledPage == index && !pager.isScrollInProgress }
            DisposableEffect(currentPicker, id) {
                currentPicker?.attach { field, value -> pageValues[field] = value }
                onDispose { currentPicker?.detach() }
            }
            // Mochi 下拉刷新（UIRefreshControl）：每页自己一根越界弹簧 + 转圈，
            // 拉过阈值轻震一下、不等松手就开刷；事件名由卡片声明（options.pullRefreshEvent）。
            val pageOverscroll = rememberMochiOverscrollEffect()
            val lazyList = lists[index].takeIf { page.root.lazyScreenRoot() }
            val pullEvent = options.pullRefreshEvent
            val refresh: (suspend () -> Unit)? = pullEvent?.let { ev ->
                {
                    val wait = CompletableDeferred<Unit>()
                    refreshWait = wait
                    onAction(ev, page.root.collect(pageValues) + (UiScreenPage.PAGE_VALUE_KEY to id))
                    withTimeoutOrNull(8_000) { wait.await() }
                    refreshWait = null
                }
            }
            val refreshState = rememberMochiRefreshState(pageOverscroll, lazyList, onRefresh = refresh)
            val hold = Modifier.mochiRefreshHold(refreshState)
            Box(Modifier.fillMaxSize()) {
                if (page.root.lazyScreenRoot()) {
                    NodeTreeContent(page.root, pageValues, errors, currentPicker,
                        readOnly = false, submit, markdown, image, lazyList,
                        counterHost = counterHosts.getValue(id),
                        overscroll = pageOverscroll,
                        refreshHold = hold,
                        contentTopInset = topInset)
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(
                                scrolls[index],
                                overscrollEffect = pageOverscroll,
                                flingBehavior = MochiScrollDefaults.flingBehavior(),
                            )
                            .then(hold),
                    ) {
                        Spacer(Modifier.height(topInset))
                        // Moving the pager must not toggle every control's enabled
                        // colors. Gesture cancellation and submit above guard actions;
                        // readOnly describes the screen, not an animation frame.
                        NodeTreeContent(page.root, pageValues, errors, currentPicker,
                            readOnly = false, submit, markdown, image, lazyList,
                            counterHost = counterHosts.getValue(id),
                            overscroll = pageOverscroll)
                    }
                }
                // 转圈在「栏下沿」和「被拉下来的内容上沿」之间：栏让出的高度这里补上。
                // 转圈在「顶栏下沿」和「被拉下来的内容上沿」之间：栏高之外再留 8dp 边距。
                MochiRefreshIndicator(
                    refreshState,
                    Modifier.align(Alignment.TopCenter).padding(top = topInset + 8.dp),
                )
            }
        }
    }
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
