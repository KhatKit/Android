package heizige.kk.khatkit.app.feature.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AutomationTracerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun tracer() = AutomationTracer(tmp.newFolder())

    @Test
    fun `record and load trace steps`() {
        val t = tracer()
        val id = t.begin("测试")
        t.record(
            action = AutomationTracer.UiActionDto(type = "CLICK", text = "确定", x = 10, y = 20),
            screenshotBefore = "/shot.png",
            result = "已点击",
            success = true,
            packageName = "pkg",
            resolutionMethod = "node",
            nowMillis = 1000L,
        )
        t.record(
            action = AutomationTracer.UiActionDto(type = "SET_TEXT", inputText = "hi", viewId = "edit"),
            success = true,
            nowMillis = 2000L,
        )
        val meta = t.finish()
        assertNotNull(meta)
        assertEquals(id, meta!!.id)
        assertEquals(2, meta.stepCount)

        val record = t.load(id)
        assertNotNull(record)
        assertEquals(2, record!!.steps.size)
        assertEquals("CLICK", record.steps[0].action.type)
        assertEquals("确定", record.steps[0].action.text)
        assertEquals("/shot.png", record.steps[0].screenshotBefore)
        assertEquals("node", record.steps[0].resolutionMethod)
        assertEquals(1000L, record.steps[0].timestamp)
        assertEquals("SET_TEXT", record.steps[1].action.type)
    }

    @Test
    fun `list shows traces newest first`() {
        val t = tracer()
        t.begin("old", nowMillis = 1000L)
        t.finish(2000L)
        t.begin("new", nowMillis = 5000L)
        t.finish(6000L)
        val list = t.list()
        assertEquals(2, list.size)
        assertEquals("new", list[0].label)
        assertEquals("old", list[1].label)
    }

    @Test
    fun `finish without begin returns null`() {
        assertNull(tracer().finish())
    }

    @Test
    fun `delete removes trace`() {
        val t = tracer()
        val id = t.begin("del")
        t.record(action = AutomationTracer.UiActionDto(type = "WAIT"))
        t.finish()
        assertTrue(t.delete(id))
        assertNull(t.load(id))
    }

    @Test
    fun `bbox dto survives serialization`() {
        val t = tracer()
        t.begin("bbox")
        t.record(
            action = AutomationTracer.UiActionDto(
                type = "CLICK",
                bbox = AutomationTracer.BBoxDto(1, 2, 3, 4),
                origin = "vision",
            ),
            resolutionMethod = "bbox",
        )
        t.finish()
        val record = t.load(t.list().first().id)!!
        val a = record.steps[0].action
        assertEquals(1, a.bbox!!.left)
        assertEquals(4, a.bbox!!.bottom)
        assertEquals("vision", a.origin)
    }
}
