package heizige.kk.khatkit.app.core.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ui.feice.mochi.navigation.MochiPeerTransition
import ui.feice.mochi.navigation.MochiPeerTransitionSpec
import ui.feice.mochi.navigation.MochiTabScaffoldState
import ui.feice.mochi.navigation.rememberMochiTabScaffoldState
import ui.feice.mochi.theme.MochiTheme

/** One saveable library selection for adaptive bottom bar, rail, sidebar and page content. */
@Stable
class MochiNavPagesState internal constructor(
    internal val pages: List<Int>,
    private val tabs: MochiTabScaffoldState,
    private val scope: CoroutineScope,
) {
    val currentPage: Int get() = tabs.selectedIndex.coerceIn(pages.indices)
    internal val slots = mutableMapOf<Int, NavPageReselectSlot>()
    private var reselectJob: Job? = null

    fun select(page: Int) {
        if (page !in pages) return
        reselectJob?.cancel()
        if (page == currentPage) {
            val action = slots[page]?.action ?: return
            reselectJob = scope.launch { action() }
        } else {
            tabs.select(page)
        }
    }
}

@Composable
fun rememberMochiNavPagesState(pageCount: Int): MochiNavPagesState {
    require(pageCount > 0)
    val tabs = rememberMochiTabScaffoldState()
    val scope = rememberCoroutineScope()
    return remember(tabs, scope, pageCount) {
        MochiNavPagesState(List(pageCount) { it }, tabs, scope)
    }
}

/**
 * Adaptive pages have an existing shared header, floating bar and 600/840dp side navigation.
 * Keep those hosts and delegate all page animation, dragging, cancellation and retention to
 * the same library Depth transition as MochiTabScaffold; no application gesture recognizer.
 */
@Composable
fun MochiNavPages(
    state: MochiNavPagesState,
    modifier: Modifier = Modifier,
    background: Color = MochiTheme.colorScheme.background,
    content: @Composable (Int) -> Unit,
) {
    MochiPeerTransition(
        targetState = state.currentPage,
        modifier = modifier.fillMaxSize().background(background),
        pages = state.pages,
        onPageChange = state::select,
        background = background,
        // Public facade defaults backdrop to surfaceContainerHighest; MochiTabScaffold's
        // internal transition uses the page background. Match the demo: Depth 0.94 shrink
        // must expose the page colour around the card, not a grey band.
        backdrop = background,
        spec = MochiPeerTransitionSpec.Depth,
        retainPages = true,
    ) { page ->
        val slot = remember(state, page) { NavPageReselectSlot() }
        DisposableEffect(state, page, slot) {
            state.slots[page] = slot
            onDispose { if (state.slots[page] === slot) state.slots.remove(page) }
        }
        CompositionLocalProvider(LocalNavPageReselectSlot provides slot) { content(page) }
    }
}

internal class NavPageReselectSlot {
    var owner: Any? = null
    var action: (suspend () -> Unit)? = null
}

private val LocalNavPageReselectSlot = staticCompositionLocalOf<NavPageReselectSlot?> { null }

/** Register only the root scroller; nested editors and sheets cannot replace its callback. */
@Composable
fun MochiNavPageReselectHandler(onReselect: suspend () -> Unit) {
    val slot = LocalNavPageReselectSlot.current ?: return
    val current by rememberUpdatedState(onReselect)
    val token = remember { Any() }
    DisposableEffect(slot, token) {
        if (slot.owner == null) {
            slot.owner = token
            slot.action = { current() }
        }
        onDispose {
            if (slot.owner === token) {
                slot.owner = null
                slot.action = null
            }
        }
    }
}
