package heizige.kk.khatkit.app.core.ui.components.ui

import android.view.View
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.feice.mochi.effect.mask.MochiFadeAmount
import ui.feice.mochi.effect.mask.MochiFadeEdge
import ui.feice.mochi.effect.mask.MochiFadeEdges
import ui.feice.mochi.effect.mask.MochiSmoothGradient
import ui.feice.mochi.effect.mask.mochiFadeMask

/** 栏与正文交界处渐隐（+ 渐进模糊）的默认长度。 */
val KkScrollEdgeExtent: Dp = 24.dp

/**
 * 顶栏 / 底栏和正文交界处的 Mochi 过渡：正文滚到栏下面时，贴着栏的那一段渐隐成透明；
 * 主题有滚动边缘效果（FX 开 = iOS 材质，Android 13+）时同一段还渐进模糊（[mochiFadeMask] 自带）。
 * 内容停在顶部 / 底部时那一边是实的，第一行、最后一行不会被糊掉。
 *
 * 骨架用：不知道正文里是哪个滚动容器，就从嵌套滚动里推算「离开头 / 结尾滚了多远」——
 * 子滚动容器每次真的滚了（consumed）就累加；往上 / 往下拉到头还有余量（available）时归零。
 * 没滚过之前两边都不遮（无从得知内容有没有超出），程序化滚动（scrollToItem）不经过嵌套滚动，看不到。
 * 知道滚动状态的地方用 [kkScrollEdgeFade] 的 [LazyListState] 重载，结果是精确的。
 *
 * @param top 顶边渐隐长度，0 = 这一边不遮
 * @param bottom 底边渐隐长度，0 = 这一边不遮
 */
@Composable
fun Modifier.kkScrollEdgeFade(
    top: Dp = KkScrollEdgeExtent,
    bottom: Dp = KkScrollEdgeExtent,
): Modifier {
    val rampPx = with(LocalDensity.current) { KkScrollEdgeExtent.toPx() }
    val tracker = remember { NestedScrollEdgeTracker() }
    tracker.rampPx = rampPx
    return this
        .mochiFadeMask(MochiFadeEdges(top = top, bottom = bottom), tracker)
        .nestedScroll(tracker)
}

/**
 * 精确版：按列表实际还能往前 / 往后滚多少给两边的深浅（含 contentPadding：
 * 列表滚到底、最后一项停在底栏上面时底边就是实的）。
 */
@Composable
fun Modifier.kkScrollEdgeFade(
    state: LazyListState,
    top: Dp = KkScrollEdgeExtent,
    bottom: Dp = KkScrollEdgeExtent,
): Modifier {
    val rampPx = with(LocalDensity.current) { KkScrollEdgeExtent.toPx() }
    val amount = remember(state, rampPx) { LazyListEdgeAmount(state, rampPx) }
    return mochiFadeMask(MochiFadeEdges(top = top, bottom = bottom), amount)
}

/**
 * 原生 View（WebView）版：View 自己滚，不经过 Compose 的嵌套滚动，只能直接听它的 scrollY。
 * 用法：`val edge = rememberKkViewScrollEdge()`，把 [KkViewScrollEdge.attach] 交给 View 的创建回调，
 * 再在包着它的 Modifier 上挂 `kkScrollEdgeFade(edge)`。
 */
@Composable
fun rememberKkViewScrollEdge(): KkViewScrollEdge {
    val rampPx = with(LocalDensity.current) { KkScrollEdgeExtent.toPx() }
    return remember { KkViewScrollEdge() }.also { it.rampPx = rampPx }
}

fun Modifier.kkScrollEdgeFade(
    edge: KkViewScrollEdge,
    top: Dp = KkScrollEdgeExtent,
    bottom: Dp = KkScrollEdgeExtent,
): Modifier = mochiFadeMask(MochiFadeEdges(top = top, bottom = bottom), edge)

@Stable
class KkViewScrollEdge internal constructor() : MochiFadeAmount {
    internal var rampPx = 1f
    private var fromStart by mutableFloatStateOf(0f)

    // 底边只知道「还能不能往下滚」；没滚过之前当作到底了，不遮
    private var canScrollDown by mutableStateOf(false)

    fun attach(view: View) {
        view.setOnScrollChangeListener { v, _, scrollY, _, _ ->
            fromStart = scrollY.toFloat()
            canScrollDown = v.canScrollVertically(1)
        }
    }

    override fun amount(edge: MochiFadeEdge): Float = when (edge) {
        MochiFadeEdge.Top -> MochiSmoothGradient.smoothstep(fromStart / rampPx)
        MochiFadeEdge.Bottom -> if (canScrollDown) 1f else 0f
        else -> 0f
    }
}

@Stable
private class NestedScrollEdgeTracker : NestedScrollConnection, MochiFadeAmount {
    var rampPx = 1f

    // 离开头 / 结尾各滚了多远（px）；结尾那边在第一次真的滚动之前不知道，先当 0
    private var fromStart by mutableFloatStateOf(0f)
    private var fromEnd by mutableFloatStateOf(0f)
    private var scrolled = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (consumed.y != 0f) {
            if (!scrolled) {
                // 能滚就说明内容超出了视口：结尾那边还有多少未知，当作很远
                scrolled = true
                fromEnd = Unknown
            }
            // 手指往上推（y < 0）内容往上走：离开头更远、离结尾更近
            fromStart = (fromStart - consumed.y).coerceAtLeast(0f)
            fromEnd = (fromEnd + consumed.y).coerceAtLeast(0f)
        }
        // 到头了还在拉：那一边贴住了
        if (available.y > EdgeSlop) fromStart = 0f
        if (available.y < -EdgeSlop) fromEnd = 0f
        return Offset.Zero
    }

    override fun amount(edge: MochiFadeEdge): Float = when (edge) {
        MochiFadeEdge.Top -> MochiSmoothGradient.smoothstep(fromStart / rampPx)
        MochiFadeEdge.Bottom -> MochiSmoothGradient.smoothstep(fromEnd / rampPx)
        else -> 0f
    }

    private companion object {
        const val Unknown = 1_000_000f
        const val EdgeSlop = 0.5f
    }
}

private class LazyListEdgeAmount(
    private val state: LazyListState,
    private val rampPx: Float,
) : MochiFadeAmount {
    override fun amount(edge: MochiFadeEdge): Float {
        val info = state.layoutInfo
        // layoutInfo 的 offset 是沿列表逻辑方向量的；reverseLayout 时首项那一侧在屏幕底部，两边对调
        val headSide = if (info.reverseLayout) MochiFadeEdge.Bottom else MochiFadeEdge.Top
        val items = info.visibleItemsInfo
        val px = when (edge) {
            headSide -> {
                val first = items.firstOrNull() ?: return 0f
                if (first.index > 0) return 1f
                // 第一项离视口（扣掉 contentPadding）起点还有多远
                info.viewportStartOffset + info.beforeContentPadding - first.offset
            }
            MochiFadeEdge.Top, MochiFadeEdge.Bottom -> {
                val last = items.lastOrNull() ?: return 0f
                if (last.index < info.totalItemsCount - 1) return 1f
                last.offset + last.size - (info.viewportEndOffset - info.afterContentPadding)
            }
            else -> return 0f
        }
        return MochiSmoothGradient.smoothstep(px.coerceAtLeast(0).toFloat() / rampPx)
    }
}
