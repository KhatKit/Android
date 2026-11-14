package heizige.kk.khatkit.uikit.mochi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.feice.mochi.list.MochiListDefaults
import kotlin.math.abs
import kotlin.math.max

/**
 * Mochi 分组卡片（iOS inset grouped）的 Kk 版：一张圆角卡片，行与行之间 0.5dp 分隔线，
 * 分隔线从行的文字起点画到卡片右边；某一行按下高亮时，它上下两条线跟着高亮一起淡出、淡回来。
 *
 * 为什么不直接用 Mochi 的 `MochiListCard`：它的子项不是 `ColumnScope`，而 [KkGroupedColumn]
 * 的调用点（表单组、设置组）里混着 `align`/`weight` 和任意 composable。这里保留 `Column`，
 * 分隔线位置由一个只记录位置的 [Arrangement] 拿到子项边界，起点与按下态由组内的行
 * （[MochiOptionItem] / [MochiListItem]）通过 [LocalKkGroupedList] 上报。
 */
@Stable
class KkGroupedListState internal constructor() {
    internal var coordinates: LayoutCoordinates? = null

    /** 子项边界（px，组坐标）：第 i 条 = 第 i 个非零高子项的底边，最后一项不画 */
    internal var boundaries by mutableStateOf(emptyList<Int>())
    internal val rows = mutableStateListOf<KkGroupedRowInfo>()
}

/** 组内一行上报的几何与按下强度。 */
@Stable
class KkGroupedRowInfo internal constructor() {
    internal var row: LayoutCoordinates? = null
    internal var text: LayoutCoordinates? = null
    internal val press = Animatable(0f)
}

/** 当前子树所在的分组；组外是 `null`（行自己就是一张独立卡片）。 */
val LocalKkGroupedList = staticCompositionLocalOf<KkGroupedListState?> { null }

private class RecordingArrangement(
    private val state: KkGroupedListState,
) : Arrangement.Vertical {
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        var y = 0
        val edges = ArrayList<Int>(sizes.size)
        sizes.forEachIndexed { i, h ->
            outPositions[i] = y
            y += h
            if (h > 0) edges += y
        }
        if (edges.isNotEmpty()) edges.removeAt(edges.lastIndex)
        if (edges != state.boundaries) state.boundaries = edges
    }
}

/**
 * 一组行拼成的一张 Mochi 分组卡片。
 *
 * @param shape 整组外形（首项上边、末项下边的大圆角由它裁出来）
 * @param containerColor 卡片底色；组内不自带底色的子项透出它
 * @param topDivider 在顶边再画一条分隔线。给 LazyColumn 里逐项拼组用：每一项各包一层
 *   [KkGroupedColumn]（`shape` 按位号给），非首项传 `true`，线就落在它和上一项之间。
 */
@Composable
fun KkGroupedColumn(
    modifier: Modifier = Modifier,
    shape: Shape,
    containerColor: Color,
    topDivider: Boolean = false,
    animateHeight: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = remember { KkGroupedListState() }
    val arrangement = remember(state) { RecordingArrangement(state) }
    val divider = rememberKkListDividerColor()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (animateHeight) Modifier.kkAnimateContentSize() else Modifier)
            .clip(shape)
            .background(containerColor)
            .onPlaced { state.coordinates = it }
            .drawWithContent {
                drawContent()
                val w = 0.5.dp.toPx()
                val fallback = MochiListDefaults.RowPadding.toPx()
                val group = state.coordinates?.takeIf { it.isAttached }
                // 每行在组坐标里的上下边与文字起点；绘制时现算，不依赖 onPlaced 的先后
                val placed = if (group == null) emptyList() else state.rows.mapNotNull { info ->
                    // BottomSheet/Popup 切换时，旧行坐标可能短暂仍是 attached，但已经不在
                    // 这张分组卡片的 LayoutNode 子树里。localPositionOf 对这种坐标会直接抛出
                    // “layouts are not part of the same hierarchy”，因此先确认实际祖先关系。
                    val row = info.row?.takeIf { it.isAttached && it.isDescendantOf(group) }
                        ?: return@mapNotNull null
                    val top = group.localPositionOf(row, Offset.Zero).y
                    val textX = info.text
                        ?.takeIf { it.isAttached && it.isDescendantOf(group) }
                        ?.let { group.localPositionOf(it, Offset.Zero).x }
                    PlacedRow(top, top + row.size.height, textX, info.press.value)
                }
                val edges = if (topDivider) listOf(0) + state.boundaries else state.boundaries
                edges.forEach { y ->
                    // 起点取这条线上方那一行的文字起点（iOS），找不到就退回 16dp
                    val above = placed.firstOrNull { abs(it.bottom - y) <= 1.5f }
                    val below = placed.firstOrNull { abs(it.top - y) <= 1.5f }
                    val x = (above ?: below)?.textStart ?: fallback
                    val hidden = max(above?.press ?: 0f, below?.press ?: 0f)
                    if (hidden < 0.999f && size.width > x) {
                        val top = if (y == 0) 0f else y - w / 2f
                        drawRect(divider, Offset(x, top), Size(size.width - x, w), alpha = 1f - hidden)
                    }
                }
            },
        verticalArrangement = arrangement,
    ) {
        CompositionLocalProvider(LocalKkGroupedList provides state) {
            content()
        }
    }
}

/**
 * 按条件显隐的一行（「开了 A 才出现的 B」）：展开 + 淡入出现、收起 + 淡出消失，不再一下蹦出来。
 *
 * 放在 [KkGroupedColumn] 里也安全：分隔线每次布局按子项高度现算，收起中的行还有高度就照常画线，
 * 收到 0 高（以及动画结束被移出组合）就被 [RecordingArrangement] 跳过，不会闪一条多余的线。
 * 弹簧参数照 Mochi 列表里展开类动画的手感（阻尼 0.9、刚度 400，不回弹）。
 */
@Composable
fun KkAnimatedRow(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = KkRowEnter,
        exit = KkRowExit,
    ) {
        content()
    }
}

private val KkRowEnter = expandVertically(
    animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f, visibilityThreshold = IntSize.VisibilityThreshold),
    expandFrom = Alignment.Top,
) + fadeIn(spring(dampingRatio = 0.9f, stiffness = 400f))

private val KkRowExit = shrinkVertically(
    animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f, visibilityThreshold = IntSize.VisibilityThreshold),
    shrinkTowards = Alignment.Top,
) + fadeOut(spring(dampingRatio = 0.9f, stiffness = 400f))

@Composable
internal fun rememberKkListDividerColor(): Color = MochiListDefaults.colors().divider

/**
 * 组内的行把自己登记到分组：位置（算分隔线落在哪）、文字起点（分隔线从哪开始）、按下强度。
 * 组外返回 `null`，调用方按独立卡片处理。
 */
@Composable
internal fun rememberKkGroupedRow(): KkGroupedRowInfo? {
    val group = LocalKkGroupedList.current ?: return null
    val info = remember(group) { KkGroupedRowInfo() }
    DisposableEffect(group, info) {
        group.rows += info
        onDispose { group.rows -= info }
    }
    return info
}

private class PlacedRow(val top: Float, val bottom: Float, val textStart: Float?, val press: Float)

/**
 * LayoutCoordinates.isAttached 只表示节点仍挂在某个 Compose owner 上；Popup/BottomSheet
 * 切换期间，旧节点可能仍 attached，却已经和当前分组不在同一棵 LayoutNode 树。
 */
private fun LayoutCoordinates.isDescendantOf(ancestor: LayoutCoordinates): Boolean {
    var current: LayoutCoordinates? = this
    while (current != null) {
        if (current === ancestor) return true
        current = current.parentLayoutCoordinates
    }
    return false
}

/** 登记行的外框；放在行最外层（背景之前）。 */
internal fun Modifier.kkGroupedRowPosition(info: KkGroupedRowInfo?): Modifier =
    if (info == null) this else onPlaced { info.row = it }

/** 登记行内文字列，分隔线从它的起点开始。 */
internal fun Modifier.kkGroupedTextStart(info: KkGroupedRowInfo?): Modifier =
    if (info == null) this else onPlaced { info.text = it }

/**
 * Mochi 列表行（`MochiListRow`）的点击与按下高亮，逐项照搬：手势用 `detectTapGestures`（不走 clickable 的
 * 滚动容器按下延迟、没有水波纹），按下 70ms 后整行灰底亮起（来滚动的手指还没亮就被接走，不会闪一下），
 * 松手先补亮再 350ms 淡出，被滚动接走时 120ms 淡出。[press] 在分组里由组持有，分隔线跟着它淡出。
 * [onClick] 为 `null` 或 [enabled] 为 false 时整行不可点、不高亮。
 */
@Composable
internal fun Modifier.kkRowTap(
    onClick: (() -> Unit)?,
    enabled: Boolean,
    press: Animatable<Float, *>,
    selected: Boolean = false,
): Modifier {
    val color = MochiListDefaults.colors().highlight
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val clickable = onClick != null && enabled
    val highlight = drawBehind {
        val a = press.value
        if (a > 0f) drawRect(color, alpha = a)
    }
    if (!clickable) return this.then(highlight).semantics(mergeDescendants = true) { if (!enabled) disabled() }
    return this
        .then(highlight)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    var lit: Job? = scope.launch {
                        delay(HighlightDelayMillis)
                        press.snapTo(1f)
                    }
                    val released = tryAwaitRelease()
                    lit?.cancel()
                    lit = null
                    if (released) press.snapTo(1f)
                    scope.launch { press.animateTo(0f, tween(if (released) 350 else 120)) }
                },
                onTap = { currentOnClick?.invoke() },
            )
        }
        .semantics(mergeDescendants = true) {
            role = Role.Button
            if (selected) this.selected = true
            onClick {
                currentOnClick?.invoke()
                true
            }
        }
}

private const val HighlightDelayMillis = 70L
