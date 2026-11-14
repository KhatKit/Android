package heizige.kk.khatkit.app.core.ui.hooks

import heizige.kk.khatkit.app.LocalPageRoute
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import heizige.kk.khatkit.app.PageBackInterceptor
import kotlin.coroutines.cancellation.CancellationException

/**
 * 顶栏搜索展开状态，暴露一个 0f（标题态）→ 1f（搜索态）的跟手进度。
 *
 * - 点击搜索/返回按钮切换 [expanded] 时，进度按 [animationSpec] 补间；
 * - 展开状态下系统返回手势（预测性返回）实时把返回进度映射为收起进度：
 *   手势进度 0→1 对应收起进度 0→1，标题/搜索框/箭头全部跟手；
 * - 手势提交（松手返回）时调用 [onCollapse]，由调用方把展开布尔置为 false；
 * - 手势取消时进度弹回展开态。
 *
 * 注意：必须在需要抢占返回手势的层级调用（例如 ModalDrawerSheet 的 content 内），
 * 使组合顺序晚于抽屉自身的预测返回处理器，保证搜索展开时搜索优先响应返回。
 */
@Composable
fun rememberSearchExpandState(
    expanded: Boolean,
    onCollapse: () -> Unit,
    animationSpec: FiniteAnimationSpec<Float> = tween(durationMillis = 220),
): SearchExpandState {
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    // 手势是否正在进行。调用方据此判断该跟手 [progress] 还是用自己补间，
    // 否则手势进度归零的一帧会误判为「没在拖动」而闪回。
    val dragging = remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        progress.animateTo(if (expanded) 1f else 0f, animationSpec)
    }

    // 预测返回跟手：展开时挂一条 PredictiveBackHandler，手势进度 0→1 映射成收起进度 1→0；
    // 提交（松手返回）才调 onCollapse，取消则弹回展开态。
    // PageBackInterceptor 仍然登记（只在展开时），负责吃掉「非手势」的返回（三键导航 / 旧版本），
    // 两者分工：手势走下面这条，按键走拦截器。
    // 登记时带上所在页：只拦本页的返回，压在下面的页里还开着的浮层不会吞掉栈顶页的返回
    // 手势进行中直接读 progress（不做自己的补间），调用方据此避免与手势打架。
    PredictiveBackHandler(enabled = progress.value > 0.02f) { backEvents ->
        try {
            backEvents.collect { event ->
                dragging.value = true
                progress.snapTo((1f - event.progress).coerceIn(0f, 1f))
            }
            // 手势提交：松手返回 → 收起（progress 由 LaunchedEffect 补间到 0）
            dragging.value = false
            onCollapse()
        } catch (e: CancellationException) {
            // 手势取消：回弹到展开态
            dragging.value = false
            progress.animateTo(1f, animationSpec)
        }
    }

    val expandBackTokenRoute = LocalPageRoute.current
    val expandBackToken = remember { Any() }
    DisposableEffect(expanded) {
        if (expanded) {
            PageBackInterceptor.push(expandBackToken, expandBackTokenRoute) {
                onCollapse()
                true
            }
        } else {
            PageBackInterceptor.pop(expandBackToken)
        }
        onDispose { PageBackInterceptor.pop(expandBackToken) }
    }

    return remember(progress) { SearchExpandState(progress.asState(), dragging) }
}

/**
 * [rememberSearchExpandState] 返回的进度容器。
 *
 * @param progress 0f（标题态）→ 1f（搜索态）的展开进度。
 * @param dragging 预测性返回手势是否正在进行；为 true 时应直接使用 [progress]，
 *   否则手势收尾那一帧容易与自身补间打架，表现为「收起动画播两遍」。
 */
@Stable
class SearchExpandState internal constructor(
    val progress: State<Float>,
    val dragging: State<Boolean>,
)
