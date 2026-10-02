package heizige.kk.khatkit.bridge

import heizige.kk.khatkit.engine.RustBridgeDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunDeadlineTest {

    @Test
    fun `zero means unlimited`() {
        val deadline = RunDeadline()
        assertFalse(deadline.expired())
        assertEquals(Long.MAX_VALUE, deadline.remainingMs())
        assertEquals(0L, deadline.at())
    }

    @Test
    fun `set counts from now and can be cleared`() {
        val deadline = RunDeadline()
        deadline.set(60_000)
        assertTrue(deadline.at() > System.currentTimeMillis())
        assertFalse(deadline.expired())
        deadline.set(0)
        assertFalse(deadline.expired())
    }

    @Test
    fun `negative rejected`() {
        try {
            RunDeadline().set(-1)
            throw AssertionError("应当拒绝负数")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("不能为负"))
        }
    }

    @Test
    fun `past deadline is expired with negative remaining`() {
        val deadline = RunDeadline()
        deadline.setAt(System.currentTimeMillis() - 1_000)
        assertTrue(deadline.expired())
        assertTrue(deadline.remainingMs() < 0)
    }

    @Test
    fun `dispatch rejects every bridge once the run timed out`() {
        val context = BridgeContext(cardName = "c", engine = "lua", quotaMb = 1)
        val target = ScopedHostBridge(context, object : HostBridge {
            override fun info() = mapOf<String, Any?>("ok" to true)
            override fun card() = mapOf<String, Any?>()
            override fun health() = mapOf<String, Any?>()
            override fun capabilities() = emptyList<String>()
            override fun log(level: String, message: String) = Unit
            override fun setTimeout(ms: Int) = Unit
            override fun elapsedMs() = 0L
        })
        val dispatcher = RustBridgeDispatcher(mapOf("host" to target))

        assertTrue(dispatcher.dispatch("host", "info", "[]").contains("\"ok\""))

        context.deadline.setAt(System.currentTimeMillis() - 1)
        val error = dispatcher.dispatch("host", "info", "[]")
        assertTrue(error, error.contains("卡片运行超时"))
    }

    @Test
    fun `setTimeout from script shortens the current run only`() {
        val context = BridgeContext(cardName = "c", engine = "lua", quotaMb = 1)
        val delegate = RecordingHostBridge()
        val target = ScopedHostBridge(context, delegate)
        val other = BridgeContext(cardName = "c2", engine = "lua", quotaMb = 1)
        val otherTarget = ScopedHostBridge(other, RecordingHostBridge())

        target.setTimeout(60_000)

        assertTrue(context.deadline.at() > System.currentTimeMillis())
        assertFalse(other.deadline.expired())
        assertTrue(otherTarget.context.deadline.at() == 0L)
        // delegate 的进程级字段仍会被写，用于诊断，不参与准入
        assertEquals(60_000, delegate.lastTimeout)
    }

    @Test
    fun `ui isCancelled flips when the run times out`() {
        val context = BridgeContext(cardName = "c", engine = "lua", quotaMb = 1)
        val ui = ScopedUiBridge(context, object : UiBridge {
            override fun form(title: String, items: List<Map<String, Any?>>, options: Map<String, Any?>?): Map<String, Any?>? = null
            override fun sheet(title: String, actions: List<Map<String, Any?>>, options: Map<String, Any?>?): Map<String, Any?>? = null
            override fun confirm(title: String, message: String, danger: Boolean) = false
            override fun progress(ratio: Float, label: String) = Unit
            override fun show(card: Map<String, Any?>, options: Map<String, Any?>?) = Unit
            override fun isCancelled() = false
        })

        assertFalse(ui.isCancelled())
        context.deadline.setAt(System.currentTimeMillis() - 1)
        assertTrue(ui.isCancelled())
    }

    private class RecordingHostBridge : HostBridge {
        var lastTimeout: Int = -1

        override fun info() = mapOf<String, Any?>()
        override fun card() = mapOf<String, Any?>()
        override fun health() = mapOf<String, Any?>()
        override fun capabilities() = emptyList<String>()
        override fun log(level: String, message: String) = Unit
        override fun setTimeout(ms: Int) {
            lastTimeout = ms
        }

        override fun elapsedMs() = 0L
    }
}