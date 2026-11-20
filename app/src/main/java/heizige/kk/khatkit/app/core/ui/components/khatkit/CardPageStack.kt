package heizige.kk.khatkit.app.core.ui.components.khatkit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.ui.UiNode
import heizige.kk.khatkit.ui.UiRequest
import heizige.kk.khatkit.ui.UiScreenPage
import heizige.kk.khatkit.ui.UiSheetOptions
import heizige.kk.khatkit.uikit.LocalKhatKitPageBack
import heizige.kk.khatkit.uikit.mochi.LocalKkInsidePageStack
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.rememberMochiNavigator

/**
 * 卡片页导航栈的一层。
 *
 * 卡片页（全屏 `ui.screen`）自己就是一个小导航栈：卡片首页是根层，
 * 卡片再调一次 `ui.screen` 且声明 `push = true` 时，新页**压**在根层上（从右边滑进来），
 * 返回时逐层退掉 —— 不再每换一屏就重开一张弹层。
 */
internal data class CardPageFrame(
    val id: Long,
    val title: String,
    val root: UiNode,
    val options: UiSheetOptions,
    val pages: Map<String, UiScreenPage>,
)

/** 桥接请求与 Mochi 导航栈当前需要同步的压层或退层操作。 */
internal enum class CardPageMotion { Push, Pop }

/**
 * 这一屏是不是「卡片页」：全屏页，而且要么带分页导航（卡片首页），要么显式 `push = true`（叠层页）。
 *
 * 只认这两种：别的卡片用 `fullscreen` 弹的普通全屏屏（例如 F-Droid 市场的搜索/详情屏）
 * 仍旧走原来的整屏弹层，外观不变。
 */
internal fun UiRequest.Screen.isCardPage(): Boolean =
    options.fullscreen && (pages.isNotEmpty() || options.layer || options.push)

/**
 * 卡片页栈的纯状态，每层页面独立保存内容与身份。
 *
 * `push = true` 压入新层，当前层刷新只更新自身；返回请求更新目标父层，
 * 正在退出的子层保留原内容，直到 Mochi 的返回动画结束才移除。
 */
@Stable
internal class CardPageStackState {
    /**
     * 栈的顺序（只有压层/退层才变）与每层内容（原地刷新时才变）分开存。
     *
     * 这是「切个时区，底下一整页跟着重新加载」的根因：之前用一条 CardPageFrame 列表存栈，
     * 换掉最上面那层的内容会让整条列表失效，所有层一起重组 —— 底下那页（74 个节点 + 分页 + 懒列表）
     * 也跟着重画一遍，又慢又像重载。现在每层内容各存一份，改谁只重组谁。
     */
    private val order = mutableStateListOf<Long>()
    private val content = mutableStateMapOf<Long, CardPageFrame>()
    private var nextId = 0L
    private var popTargetId: Long? = null
    private var pendingPush: UiRequest.Screen? = null

    /** 栈的顺序 id（压层/退层才变）。 */
    val ids: List<Long> get() = order

    val popTargetIndex: Int get() = order.indexOf(popTargetId).coerceAtLeast(0)

    val size: Int get() = order.size
    val isEmpty: Boolean get() = order.isEmpty()

    fun frame(id: Long): CardPageFrame? = content[id]

    fun lastOrNull(): CardPageFrame? = order.lastOrNull()?.let { content[it] }

    var motion by mutableStateOf<CardPageMotion?>(null)
        internal set

    /** 已经替用户发过本次返回事件，连点/跟手提交重复触发时只发一次。 */
    var backSubmitted = false

    private fun frameOf(id: Long, screen: UiRequest.Screen) =
        CardPageFrame(id, screen.title, screen.root, screen.options, screen.pages)

    private fun addLayer(screen: UiRequest.Screen) {
        val id = nextId++
        content[id] = frameOf(id, screen)
        order.add(id)
    }

    fun apply(screen: UiRequest.Screen) {
        val rootId = order.firstOrNull()
        if (rootId == null) {
            backSubmitted = false
            addLayer(screen)
            return
        }
        if (screen.options.push) {
            if (motion == CardPageMotion.Pop) {
                pendingPush = screen
                return
            }
            backSubmitted = false
            addLayer(screen)
            motion = CardPageMotion.Push
            return
        }
        // 分页屏是根页，即使它和子页的标题相同，也不能覆盖正在退出的子页。
        val returning = motion == CardPageMotion.Pop || backSubmitted
        val topId = order.last()
        val top = content[topId]
        val sameLayer = screen.pages.isEmpty() &&
            (screen.options.layer || (top != null && top.title == screen.title))
        if (order.size > 1 && !returning && sameLayer) {
            content[topId] = frameOf(topId, screen)
            return
        }
        val targetId = if (returning && screen.pages.isEmpty()) {
            popTargetId ?: order[order.lastIndex - 1]
        } else {
            rootId
        }
        content[targetId] = frameOf(targetId, screen)
        if (order.size > 1) {
            popTargetId = targetId
            motion = CardPageMotion.Pop
        } else {
            backSubmitted = false
        }
    }

    fun pushPop() {
        if (order.size <= 1 || motion == CardPageMotion.Pop) return
        popTargetId = order[order.lastIndex - 1]
        motion = CardPageMotion.Pop
    }

    fun finishPop() {
        val target = popTargetId ?: order.firstOrNull()
        while (order.size > 1 && order.last() != target) {
            val id = order.removeAt(order.lastIndex)
            content.remove(id)
        }
        popTargetId = null
        motion = null
        backSubmitted = false
        val next = pendingPush
        pendingPush = null
        if (next != null) apply(next)
    }

    fun clear() {
        order.clear()
        content.clear()
        pendingPush = null
        popTargetId = null
        motion = null
        backSubmitted = false
    }
}

/** Keeps bridge requests separate from the navigation entries that are still animating out. */
@Stable
internal class CardPageStackRuntime(val provider: KhatKitToolProvider) {
    val state = CardPageStackState()

    fun back() {
        if (state.size <= 1) {
            provider.dismissUi()
            return
        }
        if (state.backSubmitted) return
        state.backSubmitted = true
        state.pushPop()
        state.lastOrNull()?.options?.backEvent?.let { provider.submitScreen(it, emptyMap()) }
    }
}

/**
 * Card child pages use Mochi's page transitions, including gesture cancellation and release.
 * Bridge replies may update the parent immediately; the leaving entry keeps its own frame
 * until Mochi finishes its closing animation.
 */
@Composable
internal fun CardPageStackPage(request: UiRequest?, provider: KhatKitToolProvider) {
    val runtime = remember(provider) { CardPageStackRuntime(provider) }
    val state = runtime.state
    LaunchedEffect(request) {
        val screen = request as? UiRequest.Screen
        if (screen != null && screen.isCardPage()) state.apply(screen)
    }
    if (state.isEmpty) return
    CardPageStackHost(state, runtime::back) { frame ->
        CompositionLocalProvider(
            LocalKkInsidePageStack provides true,
            LocalKhatKitPageBack provides runtime::back,
        ) {
            TreePage(frame.title, frame.root, frame.options, provider, pages = frame.pages)
        }
    }
}

@Composable
internal fun CardPageStackHost(
    state: CardPageStackState,
    onBack: () -> Unit,
    content: @Composable (CardPageFrame) -> Unit,
) {
    val navigator = rememberMochiNavigator(state.ids.first().toString())
    val routes = navigator.backStack
    LaunchedEffect(state.motion, state.ids.toList()) {
        when (state.motion) {
            CardPageMotion.Push -> {
                val route = state.ids.last().toString()
                if (navigator.current != route) navigator.push(route)
                state.motion = null
            }
            CardPageMotion.Pop -> {
                val target = state.ids[state.popTargetIndex].toString()
                if (navigator.current != target) navigator.popTo(state.popTargetIndex)
            }
            null -> Unit
        }
    }
    // A completed system/full-page swipe changes Mochi's stack. Only then send the
    // card's back event; a cancelled prediction never changes the bridge or page data.
    LaunchedEffect(routes) {
        if (routes.size < state.size && state.motion == null) onBack()
    }
    LaunchedEffect(navigator.isTransitioning, routes, state.motion) {
        if (state.motion == CardPageMotion.Pop && !navigator.isTransitioning &&
            routes.size == state.popTargetIndex + 1) {
            state.finishPop()
        }
    }
    MochiNavHost(navigator, Modifier.fillMaxSize()) { route ->
        val frame = route.toLongOrNull()?.let(state::frame)
        if (frame != null) content(frame)
    }
}
