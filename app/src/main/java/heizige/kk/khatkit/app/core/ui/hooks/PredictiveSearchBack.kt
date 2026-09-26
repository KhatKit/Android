package heizige.kk.khatkit.app.core.ui.hooks

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException

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

    LaunchedEffect(expanded) {
        progress.animateTo(if (expanded) 1f else 0f, animationSpec)
    }

    PredictiveBackHandler(enabled = expanded) { backEvents ->
        try {
            backEvents.collect { event ->
                // 手势进度 0→1 == 收起进度 0→1，展开进度即 1 - progress
                progress.snapTo((1f - event.progress).coerceIn(0f, 1f))
            }
            // 手势提交：先收起，再把展开布尔置为 false（收敛动画继续进行）
            progress.animateTo(0f, animationSpec)
            onCollapse()
        } catch (e: CancellationException) {
            // 手势取消：弹回展开态
            progress.animateTo(1f, animationSpec)
            throw e
        }
    }

    return remember(progress) { SearchExpandState(progress.asState()) }
}

/** [rememberSearchExpandState] 返回的进度容器，读取 [progress] 可驱动逐帧动画。 */
@Stable
class SearchExpandState internal constructor(
    val progress: State<Float>,
)
