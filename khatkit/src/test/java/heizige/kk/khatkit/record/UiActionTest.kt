package heizige.kk.khatkit.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiActionTest {

    @Test
    fun `fromRecordedStep maps types isomorphically`() {
        val click = RecordedStep(
            type = RecordedStep.Type.CLICK, atMillis = 100L,
            packageName = "pkg", viewId = "id", text = "t", x = 1, y = 2,
        )
        val a = UiAction.fromRecordedStep(click)
        assertEquals(UiAction.Type.CLICK, a.type)
        assertEquals("pkg", a.packageName)
        assertEquals("id", a.viewId)
        assertEquals("human", a.origin)
        // round-trip back to RecordedStep preserves core fields
        val back = a.toRecordedStep()
        assertEquals(RecordedStep.Type.CLICK, back.type)
        assertEquals("id", back.viewId)
        assertEquals("t", back.text)
    }

    @Test
    fun `all recorded types round-trip`() {
        val types = listOf(
            RecordedStep.Type.CLICK, RecordedStep.Type.SET_TEXT, RecordedStep.Type.SWIPE,
            RecordedStep.Type.OPEN_APP, RecordedStep.Type.WAIT,
        )
        for (t in types) {
            val step = RecordedStep(type = t, atMillis = 1L, inputText = "x", direction = "up")
            val action = UiAction.fromRecordedStep(step)
            assertEquals(t, action.toRecordedStep().type)
        }
    }

    @Test
    fun `press and global degrade to wait on export`() {
        val press = UiAction(type = UiAction.Type.PRESS, x = 5, y = 5, durationMs = 600)
        assertEquals(RecordedStep.Type.WAIT, press.toRecordedStep().type)
        val global = UiAction(type = UiAction.Type.GLOBAL, globalAction = "back")
        assertEquals(RecordedStep.Type.WAIT, global.toRecordedStep().type)
    }

    @Test
    fun `bbox target helpers`() {
        val a = UiAction(type = UiAction.Type.CLICK, bbox = UiBBox(0, 0, 10, 10))
        assertTrue(a.hasBBox)
        assertTrue(a.targetLabel().contains("bbox"))
        val noBbox = UiAction(type = UiAction.Type.CLICK, x = 3, y = 4)
        assertFalse(noBbox.hasBBox)
        assertTrue(noBbox.hasCenter)
    }

    @Test
    fun `targetLabel priority text over desc over viewId over coords`() {
        assertEquals("hello", UiAction(UiAction.Type.CLICK, text = "hello", desc = "d", viewId = "v").targetLabel())
        assertEquals("d", UiAction(UiAction.Type.CLICK, desc = "d", viewId = "v").targetLabel())
        assertEquals("v", UiAction(UiAction.Type.CLICK, viewId = "a/b/v").targetLabel())
        assertEquals("(3, 4)", UiAction(UiAction.Type.CLICK, x = 3, y = 4).targetLabel())
    }

    @Test
    fun `comments render action kind`() {
        assertTrue(UiAction(UiAction.Type.CLICK, text = "确定").comment().contains("点击"))
        assertTrue(UiAction(UiAction.Type.SET_TEXT, inputText = "abc").comment().contains("输入"))
        assertTrue(UiAction(UiAction.Type.GLOBAL, globalAction = "home").comment().contains("全局"))
    }

    @Test
    fun `bbox intersection and containment`() {
        val a = UiBBox(0, 0, 10, 10)
        val b = UiBBox(5, 5, 15, 15)
        assertEquals(25, a.intersectionArea(b))
        assertTrue(a.contains(5, 5))
        assertFalse(a.contains(11, 5))
    }
}
