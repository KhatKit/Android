package heizige.kk.khatkit.app.feature.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import heizige.kk.khatkit.app.feature.automation.helper.TraceBrowseState
import heizige.kk.khatkit.app.core.data.automation.AutomationTraceManager
import heizige.kk.khatkit.app.feature.automation.helper.toStepViews
import heizige.kk.khatkit.app.core.data.automation.TraceStep
import heizige.kk.khatkit.app.core.data.automation.UiActionDto
import heizige.kk.khatkit.app.core.data.automation.BBoxDto
import heizige.kk.khatkit.app.core.data.automation.TraceMeta
import heizige.kk.khatkit.app.core.data.automation.TraceRecord

class TraceAuditTest {
    @Test
    fun `step view keeps action target result and hit method`() {
        val record = TraceRecord(
            meta = TraceMeta(id = "t", label = "wifi", startedAt = 1, stepCount = 1),
            steps = listOf(
                TraceStep(
                    seq = 0,
                    timestamp = 1,
                    action = UiActionDto(
                        type = "CLICK",
                        text = "Wi-Fi",
                        inputText = "",
                        bbox = BBoxDto(1, 2, 3, 4),
                    ),
                    result = "ok",
                    success = true,
                    screenshotBefore = "/tmp/frame.png",
                    resolutionMethod = "node-center",
                ),
            ),
        )
        val view = record.toStepViews().single()
        assertEquals("1. CLICK Wi-Fi", view.title)
        assertTrue(view.detail.contains("bbox 1,2-3,4"))
        assertEquals("ok", view.result)
        assertEquals("node-center", view.hit)
        assertEquals("/tmp/frame.png", view.screenshotPath)
    }

    @Test
    fun `cursor stays inside the step list`() {
        val start = TraceBrowseState(index = 0, count = 2)
        assertFalse(start.hasPrevious)
        val next = start.next()
        assertEquals(1, next.index)
        assertFalse(next.next().hasNext)
        assertEquals(0, next.previous().index)
        assertEquals(0, TraceBrowseState(0, 0).next().index)
    }
}
