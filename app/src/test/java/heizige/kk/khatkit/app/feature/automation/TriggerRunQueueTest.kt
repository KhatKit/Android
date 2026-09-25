package heizige.kk.khatkit.app.feature.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerRunQueueTest {

    private fun item(name: String, external: Boolean = false) =
        TriggerRunQueue.Item(card = name, args = emptyMap(), external = external)

    @Test
    fun `starts immediately up to parallel limit and queues the rest`() {
        val queue = TriggerRunQueue(capacity = 10, maxParallel = { 2 })
        assertEquals(listOf("a"), queue.submit(item("a")).starts.map { it.card })
        assertEquals(listOf("b"), queue.submit(item("b")).starts.map { it.card })
        val third = queue.submit(item("c"))
        assertTrue(third.starts.isEmpty())
        assertNull(third.dropped)
        assertEquals(1, queue.queuedCount)
        assertEquals(2, queue.runningCount)
    }

    @Test
    fun `complete starts queued items in fifo order`() {
        val queue = TriggerRunQueue(capacity = 10, maxParallel = { 1 })
        queue.submit(item("a"))
        queue.submit(item("b"))
        queue.submit(item("c"))
        assertEquals(listOf("b"), queue.complete().map { it.card })
        assertEquals(listOf("c"), queue.complete().map { it.card })
        assertTrue(queue.complete().isEmpty())
        assertEquals(0, queue.runningCount)
    }

    @Test
    fun `drops oldest queued item when capacity exceeded`() {
        val queue = TriggerRunQueue(capacity = 2, maxParallel = { 1 })
        queue.submit(item("running"))
        queue.submit(item("q1"))
        queue.submit(item("q2"))
        val overflow = queue.submit(item("q3"))
        assertEquals("q1", overflow.dropped?.card)
        assertTrue(overflow.starts.isEmpty())
        assertEquals(2, queue.queuedCount)
        // 队列现在是 q2, q3
        assertEquals(listOf("q2"), queue.complete().map { it.card })
        assertEquals(listOf("q3"), queue.complete().map { it.card })
    }

    @Test
    fun `external flag is preserved`() {
        val queue = TriggerRunQueue(capacity = 10, maxParallel = { 1 })
        val started = queue.submit(item("a", external = true)).starts.single()
        assertTrue(started.external)
    }
}
