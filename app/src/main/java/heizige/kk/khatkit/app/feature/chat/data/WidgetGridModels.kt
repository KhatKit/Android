package heizige.kk.khatkit.app.feature.chat.data

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.card.CardManifest

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

/** 拖哪条边：右边改列数，下边改行数，右下角两个一起改。 */
internal enum class ResizeEdge(val horizontal: Boolean, val vertical: Boolean) {
    End(true, false), Bottom(false, true), Corner(true, true)
}
