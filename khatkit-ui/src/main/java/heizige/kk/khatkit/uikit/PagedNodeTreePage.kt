package heizige.kk.khatkit.uikit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.ui.UiNode
import heizige.kk.khatkit.ui.UiScreenPage
import heizige.kk.khatkit.ui.UiSheetOptions
import heizige.kk.khatkit.ui.localizedErrors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withTimeoutOrNull
import ui.feice.mochi.basic.MochiRefreshIndicator
import ui.feice.mochi.basic.MochiRefreshState
import ui.feice.mochi.basic.mochiRefreshHold
import ui.feice.mochi.basic.rememberMochiRefreshState
import ui.feice.mochi.effect.overscroll.MochiOverscrollEffect
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.effect.overscroll.rememberMochiOverscrollEffect
import ui.feice.mochi.navigation.MochiPeerTransition
import ui.feice.mochi.navigation.MochiPeerTransitionSpec
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

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
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val navigation = options.navigation ?: return
    val pageIds = navigation.items.map { it.event }.filter { it in pages }
    if (pageIds.isEmpty()) {
        Box(Modifier.fillMaxSize())
        return
    }
    val enabledIds = navigation.items.filter { it.enabled && it.event in pages }.map { it.event }
    val fallbackId = enabledIds.firstOrNull() ?: pageIds.first()
    fun validSelection(id: String?): String? = id?.takeIf {
        it in pageIds && (enabledIds.isEmpty() || it in enabledIds)
    }
    val requestedSelection = validSelection(navigation.selected)
    // Persist the stable navigation event ID (not a pager index). Restored ids are re-validated
    // through validSelection/activeId below; a removed or disabled id falls back to the first
    // usable page. lastScriptSelection starts from the current script selection so a restored
    // page is not mistaken for a script change and overwritten by the LaunchedEffect below.
    var selectedId by rememberSaveable(stateSaver = SelectedPageIdSaver) {
        mutableStateOf(requestedSelection ?: fallbackId)
    }
    var lastScriptSelection by remember { mutableStateOf(navigation.selected) }
    val activeId = validSelection(selectedId) ?: requestedSelection ?: fallbackId
    // Enabled pages now stay interactive during the Depth transition (readOnly is screen-level,
    // not an animation frame). Clear focus when the committed page changes so a retained
    // offscreen input cannot keep the IME or steal typing from the visible page.
    LaunchedEffect(activeId) { focusManager.clearFocus() }
    LaunchedEffect(pageIds, enabledIds, navigation.selected) {
        val scriptChanged = lastScriptSelection != navigation.selected
        selectedId = if (scriptChanged) {
            requestedSelection ?: validSelection(selectedId) ?: fallbackId
        } else {
            validSelection(selectedId) ?: requestedSelection ?: fallbackId
        }
        lastScriptSelection = navigation.selected
    }

    // Every mutable page value is keyed by its stable navigation event, not list index.
    // Keeping these hosts above the transition also preserves them if a page is temporarily
    // removed from the transition's retained composition while its ID remains in the data set.
    val lists = pageIds.associateWith { id -> key("list:$id") { rememberLazyListState() } }
    val scrolls = pageIds.associateWith { id -> key("scroll:$id") { rememberScrollState() } }
    val values = pageIds.associateWith { id ->
        key("values:$id") { remember { mutableStateMapOf<String, Any?>() } }
    }
    val errors = pageIds.associateWith { id ->
        key("errors:$id") { remember { mutableStateMapOf<String, String>() } }
    }
    val previousFields = pageIds.associateWith { id ->
        key("fields:$id") { remember { mutableStateOf<Map<String, Any?>>(emptyMap()) } }
    }
    val counterHosts = pageIds.associateWith { id -> key("counter:$id") { rememberCounterButtonHost() } }
    val refreshWaits = pageIds.associateWith { id ->
        key("refresh-wait:$id") { remember { mutableStateOf<CompletableDeferred<Unit>?>(null) } }
    }
    val loadMoreSent = pageIds.associateWith { id ->
        key("load-more:$id") { remember { mutableStateOf(false) } }
    }
    val loadMoreRoots = pageIds.associateWith { id ->
        key("load-more-root:$id") { remember { mutableStateOf<UiNode?>(null) } }
    }

    // Each page owns its overscroll/refresh lifecycle independently of the selected page.
    val refreshControllers = pageIds.associateWith { id ->
        key("refresh:$id") {
            val page = pages[id]
            val overscroll = rememberMochiOverscrollEffect()
            val pullEvent = options.pullRefreshEvent
            val waitState = refreshWaits.getValue(id)
            val refresh: (suspend () -> Unit)? = if (page != null && pullEvent != null && id in enabledIds) {
                {
                    val wait = CompletableDeferred<Unit>()
                    waitState.value = wait
                    try {
                        onAction(
                            pullEvent,
                            page.root.collect(values.getValue(id)) + (UiScreenPage.PAGE_VALUE_KEY to id),
                        )
                        withTimeoutOrNull(8_000) { wait.await() }
                    } finally {
                        if (waitState.value === wait) waitState.value = null
                    }
                }
            } else {
                null
            }
            val state = rememberMochiRefreshState(
                overscroll,
                lists.getValue(id).takeIf { page?.root?.lazyScreenRoot() == true },
                onRefresh = refresh,
            )
            PageRefreshController(overscroll, state, Modifier.mochiRefreshHold(state))
        }
    }
    // A card refresh returns a replacement tree. Complete each page's own spinner, without
    // allowing an update to reset a different page's work-in-progress state.
    LaunchedEffect(pages) {
        pageIds.forEach { id ->
            refreshWaits.getValue(id).value?.let { wait ->
                wait.complete(Unit)
                refreshWaits.getValue(id).value = null
            }
        }
    }

    val activePage = pages.getValue(activeId)
    val activeValues = values.getValue(activeId)
    fun action(event: String) {
        if (activeId !in enabledIds) return
        onAction(event, activePage.root.collect(activeValues) + (UiScreenPage.PAGE_VALUE_KEY to activeId))
    }

    val density = LocalDensity.current
    val collapsePx = with(density) { 72.dp.toPx() }
    fun pageScrollProgress(id: String, edgePx: Float): Float {
        val page = pages[id] ?: return 0f
        val scroll = if (page.root.lazyScreenRoot()) {
            val list = lists[id] ?: return 0f
            if (list.firstVisibleItemIndex > 0) Float.MAX_VALUE / 4f
            else list.firstVisibleItemScrollOffset.toFloat()
        } else {
            scrolls[id]?.value?.toFloat() ?: 0f
        }
        return smoothstep(0f, edgePx, scroll)
    }
    // MochiPeerTransition intentionally exposes no intermediate progress callback. The card's
    // own title remains tied to the committed stable page and its vertical scroll state.
    val titleProgress: () -> Float = { pageScrollProgress(activeId, collapsePx) }

    // Keep load-more page-scoped and active-only. A retained offscreen page must not fire when
    // a script refresh changes its tree while that page is not selected.
    LaunchedEffect(activeId, activePage.root, activePage.loadMoreEvent) {
        val event = activePage.loadMoreEvent ?: return@LaunchedEffect
        val sent = loadMoreSent.getValue(activeId)
        val lastRoot = loadMoreRoots.getValue(activeId)
        if (lastRoot.value != null && lastRoot.value !== activePage.root) sent.value = false
        lastRoot.value = activePage.root
        snapshotFlow {
            if (activePage.root.lazyScreenRoot()) {
                val layout = lists.getValue(activeId).layoutInfo
                layout.totalItemsCount > 0 &&
                    (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 2
            } else {
                val scroll = scrolls.getValue(activeId)
                scroll.maxValue > 0 && scroll.value >= scroll.maxValue - with(density) { 64.dp.toPx() }
            }
        }.distinctUntilChanged().collect { nearEnd ->
            if (!nearEnd) {
                sent.value = false
            } else if (!sent.value && activeId in enabledIds) {
                sent.value = true
                onAction(
                    event,
                    activePage.root.collect(values.getValue(activeId)) + (UiScreenPage.PAGE_VALUE_KEY to activeId),
                )
            }
        }
    }

    val pageBackground = MochiDefaults.groupedBackground()
    CompositionLocalProvider(LocalKhatKitPageNavigationSelection provides activeId) {
        KhatKitPage(
        title = title,
        imageVector = Icons.Default.Edit,
        // Load-more is observed here with stable per-page state instead of one tab's collector
        // being left active behind all retained peer pages.
        options = options.copy(loadMoreEvent = null),
        onDismiss = onDismiss,
        onNavigate = { event ->
            when {
                event in pageIds && event in enabledIds -> selectedId = event
                event in pageIds -> Unit
                else -> action(event)
            }
        },
        scrollKey = activeId,
        contentKey = activePage.root,
        lazyListState = lists.getValue(activeId).takeIf { activePage.root.lazyScreenRoot() },
        titleProgress = titleProgress,
    ) {
        MochiPeerTransition(
            targetState = activeId,
            modifier = Modifier.fillMaxSize(),
            pages = enabledIds.takeIf { it.size > 1 },
            onPageChange = { id -> if (id in enabledIds && id in pages) selectedId = id },
            background = pageBackground,
            backdrop = pageBackground,
            spec = MochiPeerTransitionSpec.Depth,
            retainPages = true,
        ) { id ->
            val page = pages[id]
            if (page == null || id !in pageIds) {
                // The library may retain a removed key until its interrupted transition settles.
                // Never dereference stale card data while that outgoing layer is retired.
                Box(Modifier.fillMaxSize())
            } else {
                val topInset = LocalKhatKitContentTopInset.current
                val pageValues = values.getValue(id)
                val pageErrors = errors.getValue(id)
                val previousPageFields = previousFields.getValue(id)
                val active = id == activeId && id in enabledIds
                LaunchedEffect(page.root) {
                    pageErrors.clear()
                    val current = page.root.initialValues()
                    if (previousPageFields.value.isEmpty()) {
                        current.forEach { (field, value) ->
                            if (!pageValues.containsKey(field)) pageValues[field] = value
                        }
                    } else {
                        current.forEach { (field, value) ->
                            if (previousPageFields.value[field] != value) pageValues[field] = value
                        }
                    }
                    previousPageFields.value = current
                }
                LaunchedEffect(context, configuration) {
                    if (pageErrors.isNotEmpty()) {
                        val visibleFields = pageErrors.keys.toSet()
                        val localized = page.root.localizedErrors(context, pageValues).filterKeys { it in visibleFields }
                        pageErrors.clear()
                        pageErrors.putAll(localized)
                    }
                }
                val submit: (String) -> Unit = { event ->
                    pageErrors.clear()
                    pageErrors.putAll(page.root.localizedErrors(context, pageValues))
                    if (active && pageErrors.isEmpty()) {
                        onAction(event, page.root.collect(pageValues) + (UiScreenPage.PAGE_VALUE_KEY to id))
                    }
                }
                val currentPicker = pickerHost.takeIf { active }
                DisposableEffect(currentPicker, id) {
                    currentPicker?.attach { field, value -> pageValues[field] = value }
                    onDispose { currentPicker?.detach() }
                }

                val refresh = refreshControllers.getValue(id)
                val lazyList = lists.getValue(id).takeIf { page.root.lazyScreenRoot() }
                Box(Modifier.fillMaxSize()) {
                    if (page.root.lazyScreenRoot()) {
                        NodeTreeContent(
                            page.root,
                            pageValues,
                            pageErrors,
                            currentPicker,
                            readOnly = id !in enabledIds,
                            submit,
                            markdown,
                            image,
                            lazyList,
                            counterHost = counterHosts.getValue(id),
                            overscroll = refresh.overscroll,
                            refreshHold = refresh.hold,
                            contentTopInset = topInset,
                        )
                    } else {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(
                                    scrolls.getValue(id),
                                    overscrollEffect = refresh.overscroll,
                                    flingBehavior = MochiScrollDefaults.flingBehavior(),
                                )
                                .then(refresh.hold),
                        ) {
                            Spacer(Modifier.height(topInset))
                            NodeTreeContent(
                                page.root,
                                pageValues,
                                pageErrors,
                                currentPicker,
                                readOnly = id !in enabledIds,
                                submit,
                                markdown,
                                image,
                                lazyList,
                                counterHost = counterHosts.getValue(id),
                                overscroll = refresh.overscroll,
                            )
                        }
                    }
                    MochiRefreshIndicator(
                        refresh.state,
                        Modifier.align(Alignment.TopCenter).padding(top = topInset + 8.dp),
                    )
                }
            }
        }
    }
    }
}

/** Saves only the stable event ID; the current page set validates it on restore. */
private val SelectedPageIdSaver: Saver<String, String> = Saver(
    save = { it },
    restore = { it },
)

private data class PageRefreshController(
    val overscroll: MochiOverscrollEffect,
    val state: MochiRefreshState,
    val hold: Modifier,
)

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
