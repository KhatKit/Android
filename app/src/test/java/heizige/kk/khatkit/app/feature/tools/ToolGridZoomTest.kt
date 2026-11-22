package heizige.kk.khatkit.app.feature.tools

import heizige.kk.khatkit.app.feature.tools.helper.ToolGridZoom
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolGridZoomTest {
    @Test
    fun smallMovementsAccumulateBeforeChangingColumnsInEitherDirection() {
        val zoom = ToolGridZoom()
        assertEquals(0, zoom.update(1.05f))
        assertEquals(-1, zoom.update(1.05f))
        assertEquals(0, zoom.update(0.95f))
        assertEquals(1, zoom.update(0.95f))
    }

    @Test
    fun resettingFingerCountClearsUnfinishedPinch() {
        val zoom = ToolGridZoom()
        assertEquals(0, zoom.update(1.08f))
        zoom.reset()
        assertEquals(0, zoom.update(1.04f))
    }
}
