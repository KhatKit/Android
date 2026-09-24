package heizige.kk.khatkit.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSessionTest {

    private fun click(at: Long, viewId: String = "com.example:id/btn") = RecordedStep(
        type = RecordedStep.Type.CLICK,
        atMillis = at,
        packageName = "com.example",
        viewId = viewId,
    )

    private fun text(at: Long, value: String, viewId: String = "com.example:id/input") = RecordedStep(
        type = RecordedStep.Type.SET_TEXT,
        atMillis = at,
        packageName = "com.example",
        viewId = viewId,
        inputText = value,
    )

    @Test
    fun consecutiveDuplicatesAreDropped() {
        val session = RecordingSession()
        assertTrue(session.add(click(1)))
        assertFalse(session.add(click(2)))
        assertFalse(session.add(click(3)))
        assertEquals(1, session.size)
    }

    @Test
    fun textChangesDebounceToFinalValue() {
        val session = RecordingSession()
        session.add(text(1, "h"))
        session.add(text(2, "he"))
        session.add(text(3, "hello"))
        assertEquals(1, session.size)
        val step = session.snapshot().single()
        assertEquals("hello", step.inputText)
        assertEquals(3L, step.atMillis)

        // 不同输入框不合并
        session.add(text(4, "other", viewId = "com.example:id/input2"))
        assertEquals(2, session.size)
    }

    @Test
    fun sameClickWithDifferentTargetIsKept() {
        val session = RecordingSession()
        session.add(click(1, "com.example:id/a"))
        session.add(click(2, "com.example:id/b"))
        assertEquals(2, session.size)
    }

    @Test
    fun stepsAreCappedAndFullFlagIsSet() {
        val session = RecordingSession(maxSteps = 3)
        (1..5).forEach { index -> session.add(click(index.toLong(), "com.example:id/btn$index")) }
        assertEquals(3, session.size)
        assertTrue(session.isFull)

        session.clear()
        assertEquals(0, session.size)
        assertFalse(session.isFull)
    }
}
