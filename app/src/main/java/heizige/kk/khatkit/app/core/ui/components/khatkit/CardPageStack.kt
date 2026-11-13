package heizige.kk.khatkit.app.core.ui.components.khatkit

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.ui.UiNode
import heizige.kk.khatkit.ui.UiRequest
import heizige.kk.khatkit.ui.UiScreenPage
import heizige.kk.khatkit.ui.UiSheetOptions
import heizige.kk.khatkit.uikit.mochi.LocalKkInsidePageStack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import ui.feice.mochi.theme.MochiTheme

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

/** 栈这一帧正在做哪种过渡；[CardPageStackRuntime] 只负责放动画。 */
internal enum class CardPageMotion { Push, Pop }

/**
 * 这一屏是不是「卡片页」：全屏页，而且要么带分页导航（卡片首页），要么显式 `push = true`（叠层页）。
 *
 * 只认这两种：别的卡片用 `fullscreen` 弹的普通全屏屏（例如 F-Droid 市场的搜索/详情屏）
 * 仍旧走原来的整屏弹层，外观不变。
 */
internal fun UiRequest.Screen.isCardPage(): Boolean =
    options.fullscreen && (pages.isNotEmpty() || options.layer)

/**
 * 卡片页栈的纯状态：卡片每来一屏，这里决定「压一层」还是「回根层」。
 *
 * 判定很简单，也正好对上卡片的脚本流程：
 * - `push = true` 的全屏屏 → 压一层；
 * - 其余全屏屏 → 这是根层重画（返回上一屏、应用设置、下拉刷新后都会重画根层），
 *   深栈开始退层，根层内容原地换新（滚动位置/分页状态都保留）。
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

    /** 栈的顺序 id（压层/退层才变）。 */
    val ids: List<Long> get() = order

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
            backSubmitted = false
            addLayer(screen)
            motion = CardPageMotion.Push
            return
        }
        // 非 push 但标题和栈顶那页一样：卡片正在原地刷新当前页（例如设置里切了时区再出一次设置屏）。
        // 只换这一层的内容，栈深和其它层都不动 —— 底下那页连重组都不会发生。
        val topId = order.last()
        val top = content[topId]
        val sameLayer = screen.options.layer || (top != null && top.title == screen.title)
        if (order.size > 1 && sameLayer) {
            content[topId] = frameOf(topId, screen)
            return
        }
        // 其余非 push：卡片回到了根层。根层换新数据（保留 id），深栈退掉。
        content[rootId] = frameOf(rootId, screen)
        if (order.size > 1) {
            // 退层动画期间保持 backSubmitted（连点不会再发一次返回事件），finishPop 时复位。
            motion = CardPageMotion.Pop
        } else {
            backSubmitted = false
        }
    }

    fun pushPop() {
        if (order.size <= 1) return
        motion = CardPageMotion.Pop
    }

    fun finishPop() {
        while (order.size > 1) {
            val id = order.removeAt(order.lastIndex)
            content.remove(id)
        }
        motion = null
        backSubmitted = false
    }

    fun clear() {
        order.clear()
        content.clear()
        motion = null
        backSubmitted = false
    }
}

/** 栈的运行时：状态 + 跟手动画 + 返回行为；页面版和弹层版共用。 */
@Stable
internal class CardPageStackRuntime(val provider: KhatKitToolProvider) {
    val state = CardPageStackState()
    val entering: Animatable<Float, AnimationVector1D> = Animatable(0f)
    val closing: Animatable<Float, AnimationVector1D> = Animatable(0f)

    /** 替用户发一次「返回上一屏」；根层则交给卡片自己关。 */
    fun back() {
        if (state.size <= 1) {
            provider.dismissUi()
            return
        }
        if (state.backSubmitted) return
        state.backSubmitted = true
        state.pushPop()
        val event = state.lastOrNull()?.options?.backEvent
        if (event != null) provider.submitScreen(event, emptyMap())
    }
}

/**
 * 卡片自己的页面（`CardWidgetPage`）用的入口：不弹层，直接铺在页面里。
 *
 * 跟手返回只管「退层」；根层（栈里只剩一页）的返回交给页面导航 —— 那是专门的页面返回，
 * 有 MochiNavHost 自己的预测性返回/来源容器形变，不该被卡片截胡。
 */
@Composable
internal fun CardPageStackPage(request: UiRequest?, provider: KhatKitToolProvider) {
    // 页面版不按「请求为空」清栈：卡片在算下一屏时先留着当前这层兜底（页面关闭会整页销毁）。
    val runtime = rememberCardPageStack(request, provider, clearOnIdle = false)
    if (runtime.state.isEmpty) return
    Box(Modifier.fillMaxSize()) {
        StackPredictiveBack(runtime, enabled = runtime.state.size > 1, dialogRoot = false)
        CardPageStackLayers(runtime)
    }
}

@Composable
private fun StackPredictiveBack(
    runtime: CardPageStackRuntime,
    enabled: Boolean,
    dialogRoot: Boolean,
) {
    PredictiveBackHandler(enabled = enabled) { backEvents ->
        try {
            backEvents.collect { event ->
                val progress = event.progress.coerceIn(0f, 1f)
                if (runtime.state.size > 1) {
                    runtime.entering.snapTo(progress)
                } else if (dialogRoot) {
                    runtime.closing.snapTo(progress)
                }
            }
            runtime.back()
        } catch (error: CancellationException) {
            if (runtime.state.size > 1) {
                runtime.entering.animateTo(0f, tween(durationMillis = 180))
            } else if (dialogRoot) {
                runtime.closing.animateTo(0f, tween(durationMillis = 180))
            }
            throw error
        }
    }
}

@Composable
private fun rememberCardPageStack(
    request: UiRequest?,
    provider: KhatKitToolProvider,
    clearOnIdle: Boolean,
): CardPageStackRuntime {
    val runtime = remember { CardPageStackRuntime(provider) }
    val state = runtime.state
    val entering = runtime.entering
    val closing = runtime.closing

    // 卡片的新一屏：落进栈里。放 side effect 里，不在组合过程中改状态。
    LaunchedEffect(request) {
        val screen = request as? UiRequest.Screen
        if (screen != null && screen.isCardPage()) {
            // 压层：先把新页挪到屏幕右侧，下一帧再滑进来（否则会先闪一帧到位的样子）。
            if (screen.options.push && !state.isEmpty) entering.snapTo(1f)
            state.apply(screen)
            return@LaunchedEffect
        }
        // 请求被收掉（卡片结束/用户取消）时把栈收掉。先等一小会儿，避开请求队列
        // 「先清空再挂下一屏」的中间态；上面还压着别的请求（sheet 之类）时栈先留着。
        if (!clearOnIdle) return@LaunchedEffect
        delay(140)
        if (provider.uiRequest.value != null) return@LaunchedEffect
        if (state.isEmpty) return@LaunchedEffect
        closing.animateTo(1f, tween(durationMillis = 180, easing = FastOutLinearInEasing))
        state.clear()
        closing.snapTo(0f)
    }

    LaunchedEffect(state.motion) {
        when (state.motion) {
            CardPageMotion.Push -> {
                entering.snapTo(1f)
                entering.animateTo(0f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
            }
            CardPageMotion.Pop -> {
                // 退层：当前页滑出去（到 1 时下层已经回到原位），然后把它从栈里摘掉，
                // 并把进度归零 —— 不归零的话剩下那层会一直带着 translationX = 整屏宽（白屏）。
                entering.animateTo(1f, tween(durationMillis = 240, easing = FastOutLinearInEasing))
                state.finishPop()
                entering.snapTo(0f)
            }
            null -> return@LaunchedEffect
        }
        state.motion = null
    }
    return runtime
}

@Composable
private fun CardPageStackLayers(runtime: CardPageStackRuntime) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MochiTheme.colorScheme.background)
            .graphicsLayer {
                translationY = runtime.closing.value * size.height
                alpha = 1f - runtime.closing.value * 0.2f
            },
    ) {
        // 只读顺序（压层/退层才变）。每层的内容在自己的 layer 里读 —— 换掉栈顶那层的内容
        // 只让那一层重组，下面那页不受影响。
        val ids = runtime.state.ids
        ids.forEachIndexed { index, id ->
            key(id) {
                CardPageStackLayer(
                    id = id,
                    depth = ids.lastIndex - index,
                    entering = runtime.entering,
                    runtime = runtime,
                )
            }
        }
    }
}

/**
 * 栈里的一层：内容 + 跟手位移 + 下层压暗。
 *
 * [depth] 0 = 栈顶，1 = 被盖住的那层（跟着顶层滑动一起退位），2+ = 更深的层（静止在后面）。
 */
@Composable
private fun CardPageStackLayer(
    id: Long,
    depth: Int,
    entering: Animatable<Float, AnimationVector1D>,
    runtime: CardPageStackRuntime,
) {
    // 只订阅这一层自己的内容：别的层刷新不会让本层重组。
    val frame = runtime.state.frame(id) ?: return
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val motion = entering.value
                if (depth == 0) {
                    translationX = motion * size.width
                } else {
                    val cover = if (depth == 1) 1f - motion else 1f
                    translationX = -size.width * 0.28f * cover
                    val scale = 1f - 0.04f * cover
                    scaleX = scale
                    scaleY = scale
                }
                clip = true
            },
    ) {
        CompositionLocalProvider(LocalKkInsidePageStack provides true) {
            TreePage(
                title = frame.title,
                root = frame.root,
                options = frame.options,
                provider = runtime.provider,
                pages = frame.pages,
            )
        }
        if (depth > 0) {
            // 下层压暗：跟手动画在 draw 阶段读 entering，不跟着重组。
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (depth == 1) 0.22f * (1f - entering.value) else 0.22f
                    }
                    .background(Color.Black),
            )
            // 被盖住的层不能收到触摸（顶层滑动途中会短暂露出下层）。
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
    }
}
