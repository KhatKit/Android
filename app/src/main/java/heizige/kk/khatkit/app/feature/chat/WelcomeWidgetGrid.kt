package heizige.kk.khatkit.app.feature.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.uikit.mochi.KkHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ui.feice.mochi.anim.MochiSprings
import ui.feice.mochi.effect.elastic.rubberBand
import ui.feice.mochi.shape.MochiCapsule
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme
import kotlin.math.roundToInt
import heizige.kk.khatkit.app.feature.chat.data.ResizeEdge
import heizige.kk.khatkit.app.feature.chat.data.WidgetCells
import heizige.kk.khatkit.app.feature.chat.data.WidgetGridGeometry
import heizige.kk.khatkit.app.feature.chat.data.WidgetSizeLimits
import heizige.kk.khatkit.app.feature.chat.helper.packWidgets
import heizige.kk.khatkit.app.feature.chat.helper.resizeTarget
import heizige.kk.khatkit.app.feature.chat.helper.WidgetResizeState





/**
 * 小组件网格。[sizes] 与子项一一对应；子项拿到的约束就是它占的格子（宽高都是固定值），
 * 所以卡片内容可以 `fillMaxSize`。格位变化时子项直接落到新格位，避免在自定义
 * Layout 的放置回调里再次触发布局树重排。
 */
@Composable
internal fun WidgetGrid(
    sizes: List<WidgetCells>,
    geometry: (width: Int) -> WidgetGridGeometry,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val g = geometry(constraints.maxWidth)
        val columns = WidgetGridGeometry.Columns
        val slots = packWidgets(sizes, columns)
        val placeables = measurables.mapIndexed { index, measurable ->
            val size = sizes.getOrNull(index) ?: WidgetCells(columns, 1)
            measurable.measure(Constraints.fixed(g.span(size.width.coerceIn(1, columns)), g.span(size.height)))
        }
        val rows = slots.zip(sizes).maxOfOrNull { (slot, size) -> slot.row + size.height } ?: 0
        layout(constraints.maxWidth, if (rows == 0) 0 else g.span(rows)) {
            placeables.forEachIndexed { index, placeable ->
                val slot = slots.getOrNull(index) ?: return@forEachIndexed
                placeable.place(g.left + slot.column * g.step, slot.row * g.step)
            }
        }
    }
}

/**
 * 拖边改尺寸的像素级预览：卡片按 [size] 画，但**向上报的还是格子的尺寸**。
 *
 * 于是只有被拖的这一张在自己的格子里「长出去」压住邻居，网格的格位、邻居位置一动不动 ——
 * 这是和「把预览尺寸喂回自定义 Layout 重新排整张网格」的关键区别：后者会让提交时的重排
 * 落到已经放置好的子树上，正是 hwui 递归崩溃的触发点（配合卡片常驻的阴影层一起复现）。
 */
internal fun Modifier.widgetResizePreview(size: Size?): Modifier =
    if (size == null) {
        this
    } else {
        layout { measurable, constraints ->
            val w = size.width.roundToInt().coerceAtLeast(1)
            val h = size.height.roundToInt().coerceAtLeast(1)
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(constraints.minWidth, constraints.minHeight) { placeable.place(0, 0) }
        }
    }

/**
 * 编辑态的三个拖动把手：右边、下边、右下角。
 *
 * 画出来是一截胶囊，下面垫一圈页面底色的更大胶囊，压在描边上也不会和边线糊成一团；
 * 触摸区 44dp，骑在边线上（各向外溢出一半）。读屏用户走 [resizeSemantics] 的四个动作，不用拖。
 */
@Composable
internal fun BoxScope.WidgetResizeHandles(
    size: WidgetCells,
    limits: WidgetSizeLimits,
    geometry: WidgetGridGeometry,
    state: WidgetResizeState,
    haptics: KkHaptics,
    reduceMotion: Boolean,
    onResize: (WidgetCells) -> Unit,
    onResizeEnd: () -> Unit,
) {
    val current by rememberUpdatedState(size)
    val resize by rememberUpdatedState(onResize)
    val finish by rememberUpdatedState(onResizeEnd)
    val accent = MochiTheme.colorScheme.primary
    val ring = MochiDefaults.groupedBackground()

    fun handle(edge: ResizeEdge): Modifier = Modifier.pointerInput(edge, geometry, limits) {
        var start = current
        var total = Offset.Zero
        fun release() {
            state.settle(Size(geometry.span(current.width).toFloat(), geometry.span(current.height).toFloat()), reduceMotion)
            finish()
        }
        detectDragGestures(
            onDragStart = {
                start = current
                total = Offset.Zero
                state.start(edge, Size(geometry.span(start.width).toFloat(), geometry.span(start.height).toFloat()))
                haptics.dragStarted()
            },
            onDragEnd = {
                haptics.dragStopped()
                release()
            },
            onDragCancel = ::release,
        ) { change, amount ->
            change.consume()
            total += Offset(if (edge.horizontal) amount.x else 0f, if (edge.vertical) amount.y else 0f)
            val (frame, next) = resizeTarget(start, total, limits, geometry)
            state.follow(frame)
            if (next != current) {
                haptics.tick()
                resize(next)
            }
        }
    }

    @Composable
    fun Grip(modifier: Modifier, width: Dp, height: Dp) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Box(Modifier.size(width + 6.dp, height + 6.dp).background(ring, MochiCapsule))
            Box(Modifier.size(width, height).background(accent, MochiCapsule))
        }
    }

    val touch = 44.dp
    val half = touch / 2
    Grip(handle(ResizeEdge.End).align(Alignment.CenterEnd).offset(x = half).size(touch, 56.dp), 6.dp, 28.dp)
    Grip(handle(ResizeEdge.Bottom).align(Alignment.BottomCenter).offset(y = half).size(56.dp, touch), 28.dp, 6.dp)
    Grip(handle(ResizeEdge.Corner).align(Alignment.BottomEnd).offset(x = half - 6.dp, y = half - 6.dp).size(touch), 14.dp, 14.dp)
}

/** 拖动时浮在小组件中央的格数读数；读数随手直接换，不做过渡。 */
@Composable
internal fun BoxScope.WidgetSizeReadout(size: WidgetCells) {
    Box(
        Modifier
            .align(Alignment.Center)
            .background(MochiTheme.colorScheme.inverseSurface.copy(alpha = 0.86f), MochiCapsule)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(size.toString(), style = MochiTheme.textStyles.subhead, color = MochiTheme.colorScheme.inverseOnSurface)
    }
}

/**
 * 读屏的调整入口：加宽、变窄、加高、变矮，一次一格并立即落盘。
 * [labels] 依次是这四个动作的文案；[extra] 追加别的动作（前移 / 后移）。
 */
internal fun Modifier.resizeSemantics(
    description: String,
    size: WidgetCells,
    limits: WidgetSizeLimits,
    labels: List<String>,
    onResize: (WidgetCells) -> Unit,
    onResizeEnd: () -> Unit,
    extra: List<CustomAccessibilityAction> = emptyList(),
): Modifier = semantics {
    contentDescription = description
    fun nudge(dw: Int, dh: Int) = limits.clamp(WidgetCells(size.width + dw, size.height + dh))
        .takeIf { it != size }
        ?.let { { onResize(it); onResizeEnd(); true } }
    customActions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).zip(labels).mapNotNull { (delta, label) ->
        nudge(delta.first, delta.second)?.let { CustomAccessibilityAction(label, it) }
    } + extra
}
