package heizige.kk.khatkit.app.feature.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceAuditTest {
    @Test
    fun `step view keeps action target result and hit method`() {
        val record = AutomationTracer.TraceRecord(
            meta = AutomationTracer.TraceMeta(id = "t", label = "wifi", startedAt = 1, stepCount = 1),
            steps = listOf(
                AutomationTracer.TraceStep(
                    seq = 0,
                    timestamp = 1,
                    action = AutomationTracer.UiActionDto(
                        type = "CLICK",
                        text = "Wi-Fi",
                        inputText = "",
                        bbox = AutomationTracer.BBoxDto(1, 2, 3, 4),
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
