package heizige.kk.khatkit.ui

import heizige.kk.khatkit.bridge.BridgeRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.concurrent.thread

/**
 * 运行期 UI 策略的三条纪律（`docs/lua-card-development.md` §3.5）：
 *
 * 1. HEADLESS 下 `ui.*` 立即返回，不阻塞、不留弹层；
 * 2. 宿主不在场时，阻塞型 `ui.*` 只等挂载宽限就降级，不再挂满 300 秒；
 * 3. 等待超时**不取消弹层**：用户迟到的提交经 `onLateAnswer` 交给宿主续跑。
 */
class UiBridgeHostUiModeTest {
    @Test
    fun `headless bridge answers immediately`() {
        val started = System.currentTimeMillis()
        assertNull(HeadlessUiBridge.form("t", mapOf("__ui" to true, "type" to "Column"), null))
        assertNull(HeadlessUiBridge.screen("t", mapOf("__ui" to true, "type" to "Column"), null))
        assertNull(HeadlessUiBridge.sheet("t", emptyList(), null))
        assertNull(HeadlessUiBridge.webSheet("t", "https://example.com", emptyList(), null))
        assertFalse(HeadlessUiBridge.confirm("t", "m", false))
        assertFalse(HeadlessUiBridge.isCancelled())
        assertTrue("headless must not block", System.currentTimeMillis() - started < 1_000)
    }

    @Test
    fun `registry exposes headless ui with the null bridge`() {
        val registry = BridgeRegistry()
        assertNull("no ui host registered", registry.uiDelegate(null, UiMode.INTERACTIVE))
        assertSame(HeadlessUiBridge, registry.uiDelegate(null, UiMode.HEADLESS))
        assertTrue("ui capability must stay visible", registry.availableBridges(UiMode.HEADLESS).contains("ui"))
        assertFalse(registry.availableBridges(UiMode.INTERACTIVE).contains("ui"))
    }

    @Test
    fun `blocking ui degrades when host is absent`() {
        val host = UiBridgeHost(timeoutMillis = 300_000)
        val started = System.currentTimeMillis()
        val confirmed = host.confirm("标题", "内容", false)
        assertFalse(confirmed)
        assertTrue(
            "host-attach grace must short-circuit the 300s wait",
            System.currentTimeMillis() - started < 10_000,
        )
        assertNull("nothing may stay on screen when host is absent", host.request.value)
    }

    @Test
    fun `timeout keeps the sheet and hands late answers to the host`() {
        val host = UiBridgeHost(timeoutMillis = 300_000)
        host.onHostAttached()
        val late = java.util.concurrent.CopyOnWriteArrayList<LateUiAnswer>()
        host.onLateAnswer = { _, answer -> late += answer }

        val busy = thread {
            UiBridgeHost.withinCard("demo_card") {
                // 300ms 预算：模拟运行期限先到，而不是宿主上限
                UiBridgeHost.withinBudget(300L) { host.confirm("标题", "内容", false) }
            }
        }
        busy.join(5_000)
        assertFalse(busy.isAlive)

        // 超时后弹层仍在（等用户操作），而不是被当成取消清掉
        assertNotNull("timed-out request must stay visible", host.request.value)
        assertEquals(1L, host.timeouts.value)

        // 用户之后才点「确定」：不是丢值，而是交给宿主续跑
        thread { host.answerConfirm(true) }.join(5_000)
        assertEquals(1, late.size)
        assertEquals(UiAnswerKind.CONFIRM, late.single().kind)
        assertEquals(true, late.single().confirmed)
        assertNull(host.request.value)
    }
}
