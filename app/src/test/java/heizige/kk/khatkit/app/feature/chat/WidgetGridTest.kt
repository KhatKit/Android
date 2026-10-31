package heizige.kk.khatkit.app.feature.chat

import androidx.compose.ui.geometry.Offset
import heizige.kk.khatkit.card.CardManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGridTest {
    private fun cells(w: Int, h: Int) = WidgetCells(w, h)

    @Test fun firstFitBackfillsGapsInOrder() {
        val slots = packWidgets(listOf(cells(2, 2), cells(4, 1), cells(2, 1), cells(1, 1)), columns = 4)
        assertEquals(
            listOf(GridSlot(0, 0), GridSlot(0, 2), GridSlot(2, 0), GridSlot(2, 1)),
            slots,
        )
    }

    @Test fun oversizedWidthIsClampedToTheGrid() {
        assertEquals(listOf(GridSlot(0, 0), GridSlot(0, 1)), packWidgets(listOf(cells(9, 1), cells(1, 1)), columns = 4))
    }

    @Test fun limitsFallBackAndClamp() {
        val defaults = WidgetSizeLimits.of(null)
        assertEquals(cells(4, 2), defaults.default)
        assertTrue(defaults.resizable)

        val fixed = WidgetSizeLimits.of(CardManifest.WidgetSize(default = listOf(2, 2), min = listOf(2, 2), max = listOf(2, 2)))
        assertFalse(fixed.resizable)

        // max 小于 min 时抬到 min；default 落在 [min, max] 里。
        val odd = WidgetSizeLimits.of(CardManifest.WidgetSize(default = listOf(1, 9), min = listOf(3, 2), max = listOf(2, 1)))
        assertEquals(cells(3, 2), odd.max)
        assertEquals(cells(3, 2), odd.default)
        assertEquals(cells(3, 2), odd.clamp(cells(1, 6)))
    }

    @Test fun geometryCapsCellsAndCentersTheGrid() {
        val narrow = WidgetGridGeometry.of(width = 348, gap = 12, maxCell = 1000)
        assertEquals(78, narrow.cell)
        assertEquals(0, narrow.left)
        assertEquals(78 * 2 + 12, narrow.span(2))

        val wide = WidgetGridGeometry.of(width = 1000, gap = 12, maxCell = 120)
        assertEquals(120, wide.cell)
        assertEquals((1000 - (120 * 4 + 12 * 3)) / 2, wide.left)
    }

    @Test fun resizeSnapsToNearestCellAndRubberBandsPastLimits() {
        val g = WidgetGridGeometry(cell = 100, gap = 10, left = 0)
        val limits = WidgetSizeLimits(min = cells(2, 1), max = cells(4, 3), default = cells(2, 2))
        val start = cells(2, 2)

        val (half, halfCells) = resizeTarget(start, Offset(60f, 40f), limits, g)
        assertEquals(cells(3, 2), halfCells)
        assertEquals(g.span(2) + 60f, half.width)

        val (past, pastCells) = resizeTarget(start, Offset(1000f, 0f), limits, g)
        assertEquals(cells(4, 2), pastCells)
        assertTrue(past.width > g.span(4))
        assertTrue(past.width < g.span(4) + g.step)

        val (_, shrunk) = resizeTarget(start, Offset(-500f, -500f), limits, g)
        assertEquals(limits.min, shrunk)
    }

    @Test fun rectsMatchTheGridPlacement() {
        val g = WidgetGridGeometry(cell = 100, gap = 10, left = 5)
        val rects = widgetRects(listOf(cells(2, 1), cells(2, 2)), g)
        assertEquals(Offset(5f, 0f), rects[0].topLeft)
        assertEquals(Offset(5f + 2 * 110, 0f), rects[1].topLeft)
        assertEquals(210f, rects[1].height)
    }
}
