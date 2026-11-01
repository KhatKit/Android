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

/** 一个小组件占的格数：[width] 列 × [height] 行。 */
internal data class WidgetCells(val width: Int, val height: Int) {
    override fun toString() = "$width × $height"
}

/** manifest `widget.size` 的约束；缺省或非法时用 [CardManifest.WidgetSize] 的默认值。 */
internal data class WidgetSizeLimits(val min: WidgetCells, val max: WidgetCells, val default: WidgetCells) {
    fun clamp(cells: WidgetCells) = WidgetCells(
        cells.width.coerceIn(min.width, max.width),
        cells.height.coerceIn(min.height, max.height),
    )

    /** 尺寸固定（min == max）的组件不给把手。 */
    val resizable: Boolean get() = min != max

    companion object {
        fun of(spec: CardManifest.WidgetSize?): WidgetSizeLimits {
            val fallback = CardManifest.WidgetSize()
            // 校验器已经拦过非法值；这里再兜一次，避免旧缓存里的坏 manifest 让网格崩掉。
            fun cells(raw: List<Int>, default: List<Int>) = (raw.takeIf { it.size == 2 } ?: default).let {
                WidgetCells(
                    it[0].coerceIn(1, CardManifest.WidgetSize.MAX_COLUMNS),
                    it[1].coerceIn(1, CardManifest.WidgetSize.MAX_ROWS),
                )
            }
            val s = spec ?: fallback
            val min = cells(s.min, fallback.min)
            val max = cells(s.max, fallback.max).let { WidgetCells(maxOf(it.width, min.width), maxOf(it.height, min.height)) }
            val limits = WidgetSizeLimits(min, max, min)
            return limits.copy(default = limits.clamp(cells(s.default, fallback.default)))
        }
    }
}

/** 网格里的左上角格子。 */
internal data class GridSlot(val column: Int, val row: Int)

/**
 * 按顺序首次适配（first-fit）：每个小组件放进行优先扫描到的第一个空位。
 * 顺序就是添加顺序，所以窄组件会回填到上一行的空档里，网格不留洞。
 */
internal fun packWidgets(sizes: List<WidgetCells>, columns: Int): List<GridSlot> {
    val taken = mutableListOf<BooleanArray>()
    fun free(column: Int, row: Int, size: WidgetCells): Boolean {
        for (y in row until row + size.height) {
            val line = taken.getOrNull(y) ?: continue
            for (x in column until column + size.width) if (line[x]) return false
        }
        return true
    }
    return sizes.map { raw ->
        val size = WidgetCells(raw.width.coerceIn(1, columns), raw.height.coerceAtLeast(1))
        val slot = generateSequence(0) { it + 1 }.firstNotNullOf { row ->
            (0..columns - size.width).firstOrNull { free(it, row, size) }?.let { GridSlot(it, row) }
        }
        while (taken.size < slot.row + size.height) taken += BooleanArray(columns)
        for (y in slot.row until slot.row + size.height) {
            for (x in slot.column until slot.column + size.width) taken[y][x] = true
        }
        slot
    }
}

/**
 * 网格几何：正方格，手机竖屏约 76dp；大屏封顶 [MaxCell]，整格居中。
 * 测量和拖动共用这一份整数像素，拖动换算出来的格数和摆出来的格子严格对齐。
 */
internal data class WidgetGridGeometry(val cell: Int, val gap: Int, val left: Int) {
    val step: Int get() = cell + gap
    fun span(n: Int) = cell * n + gap * (n - 1)

    companion object {
        const val Columns = CardManifest.WidgetSize.MAX_COLUMNS
        val Gap: Dp = 12.dp
        val MaxCell: Dp = 120.dp

        fun of(width: Int, gap: Int, maxCell: Int): WidgetGridGeometry {
            val cell = minOf((width - gap * (Columns - 1)) / Columns, maxCell).coerceAtLeast(1)
            val grid = cell * Columns + gap * (Columns - 1)
            return WidgetGridGeometry(cell, gap, (width - grid).coerceAtLeast(0) / 2)
        }
    }
}

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

/** 网格里第 [index] 个小组件的格位（左上角像素 + 尺寸），和 [WidgetGrid] 摆出来的严格一致。 */
internal fun widgetRects(sizes: List<WidgetCells>, g: WidgetGridGeometry): List<Rect> =
    packWidgets(sizes, WidgetGridGeometry.Columns).zip(sizes) { slot, size ->
        Rect(
            Offset((g.left + slot.column * g.step).toFloat(), (slot.row * g.step).toFloat()),
            Size(g.span(size.width.coerceIn(1, WidgetGridGeometry.Columns)).toFloat(), g.span(size.height).toFloat()),
        )
    }

/**
 * 编辑态拖动换位。被拖那格跟着手指走（[translation] = 起点 + 位移 − 当前格位），
 * 它的中心压到别的格子上就换到那一格的序号，其它格子直接让位；
 * 松手后从手指位置弹回自己的新格位。
 */
@Stable
internal class WidgetMoveState(private val scope: CoroutineScope) {
    private val settle = Animatable(Offset.Zero, Offset.VectorConverter)
    private var origin by mutableStateOf(Offset.Zero)
    private var total by mutableStateOf(Offset.Zero)

    /** 正被拖的组件名。 */
    var name by mutableStateOf<String?>(null)
        private set

    /** 刚松手、正在弹回格位的组件名。 */
    var settling by mutableStateOf<String?>(null)
        private set

    /** 上一次换位压到的格子；手指离开它之前不再换，免得大小不一的格子来回抖。 */
    var hovered: String? = null

    fun lifted(card: String) = card == name || card == settling

    /** [card] 相对自己格位左上角 [slot] 的平移。 */
    fun translation(card: String, slot: Offset): Offset = when (card) {
        name -> origin + total - slot
        settling -> settle.value
        else -> Offset.Zero
    }

    fun start(card: String, slot: Offset) {
        name = card
        settling = null
        origin = slot
        total = Offset.Zero
        hovered = card
    }

    /** 返回被拖那格此刻的左上角（网格坐标）。 */
    fun drag(delta: Offset): Offset {
        total += delta
        return origin + total
    }

    fun drop(slot: Offset, reduceMotion: Boolean) {
        val card = name ?: return
        val from = origin + total - slot
        name = null
        hovered = null
        if (reduceMotion) return
        settling = card
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                settle.snapTo(from)
                settle.animateTo(Offset.Zero, MochiSprings.snappy(400))
            } finally {
                if (settling == card) settling = null
            }
        }
    }
}

/** 拖哪条边：右边改列数，下边改行数，右下角两个一起改。 */
internal enum class ResizeEdge(val horizontal: Boolean, val vertical: Boolean) {
    End(true, false), Bottom(false, true), Corner(true, true)
}

/**
 * 一张小组件的拖动尺寸状态。
 *
 * 手指拖动期间 [frame] 是像素级的「跟手」尺寸：越过 min/max 时走 Mochi 的橡皮筋；
 * 格数（[WidgetCells]）按四舍五入吸附，越过一格回调一次。松手后立即收回到吸附后的
 * 格子尺寸，然后清空，布局重新只认格子，避免确认阶段持续触发自定义测量。
 */
@Stable
internal class WidgetResizeState(private val scope: CoroutineScope) {
    /** 正在拖的那条边；null 表示没在拖。 */
    var edge by mutableStateOf<ResizeEdge?>(null)
        private set

    /** 跟手的像素级预览尺寸；null = 认格子尺寸（见 [widgetResizePreview]）。 */
    var preview by mutableStateOf<Size?>(null)
        private set

    private var settleJob: Job? = null

    fun start(edge: ResizeEdge, from: Size) {
        settleJob?.cancel()
        this.edge = edge
        preview = null
    }

    fun follow(size: Size) {
        settleJob?.cancel()
        preview = size
    }

    /**
     * 松手：把预览从当前像素尺寸用 spring 收回吸附后的格子尺寸，然后清空。
     * 弹簧参数与页面其它过渡同一套（MochiSprings），收回的过程就是「变大变小」的收尾动画。
     */
    fun settle(to: Size, reduceMotion: Boolean) {
        edge = null
        settleJob?.cancel()
        val from = preview
        if (from == null || reduceMotion) {
            preview = null
            return
        }
        settleJob = scope.launch {
            Animatable(0f).animateTo(1f, MochiSprings.snappy(320)) {
                preview = Size(
                    from.width + (to.width - from.width) * value,
                    from.height + (to.height - from.height) * value,
                )
            }
            preview = null
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
 * 拖动换算：沿两轴各自把「起始尺寸 + 位移」夹在 min/max 跨度内，越界部分过橡皮筋；
 * 吸附格数 = 起始格数 + round(位移 / 步长)，再按 limits 夹住。
 */
internal fun resizeTarget(
    start: WidgetCells,
    total: Offset,
    limits: WidgetSizeLimits,
    g: WidgetGridGeometry,
): Pair<Size, WidgetCells> {
    fun axis(cells: Int, delta: Float, min: Int, max: Int): Float {
        val raw = g.span(cells) + delta
        val lo = g.span(min).toFloat()
        val hi = g.span(max).toFloat()
        val limit = g.step.toFloat()
        return when {
            raw < lo -> lo + rubberBand(raw - lo, lo, limit)
            raw > hi -> hi + rubberBand(raw - hi, hi, limit)
            else -> raw
        }
    }
    val size = Size(
        axis(start.width, total.x, limits.min.width, limits.max.width),
        axis(start.height, total.y, limits.min.height, limits.max.height),
    )
    val cells = limits.clamp(
        WidgetCells(
            start.width + (total.x / g.step).roundToInt(),
            start.height + (total.y / g.step).roundToInt(),
        ),
    )
    return size to cells
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
