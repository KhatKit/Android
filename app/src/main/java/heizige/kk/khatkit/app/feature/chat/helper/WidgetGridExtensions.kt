package heizige.kk.khatkit.app.feature.chat.helper

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import ui.feice.mochi.effect.elastic.rubberBand
import kotlin.math.roundToInt
import heizige.kk.khatkit.app.feature.chat.data.GridSlot
import heizige.kk.khatkit.app.feature.chat.data.WidgetCells
import heizige.kk.khatkit.app.feature.chat.data.WidgetGridGeometry
import heizige.kk.khatkit.app.feature.chat.data.WidgetSizeLimits



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

/** 网格里第 [index] 个小组件的格位（左上角像素 + 尺寸），和 [WidgetGrid] 摆出来的严格一致。 */
internal fun widgetRects(sizes: List<WidgetCells>, g: WidgetGridGeometry): List<Rect> =
    packWidgets(sizes, WidgetGridGeometry.Columns).zip(sizes) { slot, size ->
        Rect(
            Offset((g.left + slot.column * g.step).toFloat(), (slot.row * g.step).toFloat()),
            Size(g.span(size.width.coerceIn(1, WidgetGridGeometry.Columns)).toFloat(), g.span(size.height).toFloat()),
        )
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
