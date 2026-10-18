package heizige.kk.khatkit.uikit

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import heizige.kk.khatkit.ui.UiScreenPage
import heizige.kk.khatkit.ui.UiSheetOptions
import kotlinx.coroutines.launch

/** Navigation stays entirely on the UI thread; Lua only handles real actions/data changes. */
@Composable
internal fun PagedNodeTreeSheet(
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
    val lists = ids.map { id -> key(id) { rememberLazyListState() } }
    val values = remember(ids) { ids.associateWith { mutableStateMapOf<String, Any?>() } }
    val counterHosts = ids.associateWith { id -> key(id) { rememberCounterButtonHost() } }
    // Data updates retain every tab's scroll/input state. A deliberate script
    // selection (e.g. leaving settings) may still choose a different page.
    LaunchedEffect(navigation.selected) {
        if (pager.settledPage != initial) pager.scrollToPage(initial)
    }
    val activeId = ids[pager.settledPage]
    val activePage = pages.getValue(activeId)
    val activeValues = values.getValue(activeId)
    fun action(event: String) = onAction(event,
        activePage.root.collect(activeValues) + (UiScreenPage.PAGE_VALUE_KEY to activeId))

    KhatKitSheet(
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
    ) {
        HorizontalPager(
            state = pager,
            key = { ids[it] },
            modifier = Modifier.fillMaxSize(),
            // Keep adjacent lazy lists alive during a swipe. Each list still
            // composes only its viewport, rather than rebuilding the chart as
            // its page crosses the pager's composition boundary.
            beyondViewportPageCount = 1,
            // Disabled entries are still visible but cannot be entered by swipe.
            userScrollEnabled = navigation.items.all { it.enabled },
        ) { index ->
            val id = ids[index]
            val page = pages.getValue(id)
            val pageValues = values.getValue(id)
            val errors = remember(page.root) { mutableStateMapOf<String, String>() }
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
            Box(if (page.root.lazyScreenRoot()) Modifier.fillMaxSize()
                else Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Moving the pager must not toggle every control's enabled
                // colors. Gesture cancellation and submit above guard actions;
                // readOnly describes the screen, not an animation frame.
                NodeTreeContent(page.root, pageValues, errors, currentPicker,
                    readOnly = false, submit, markdown, image,
                    lists[index].takeIf { page.root.lazyScreenRoot() },
                    counterHost = counterHosts.getValue(id))
            }
        }
    }
}
