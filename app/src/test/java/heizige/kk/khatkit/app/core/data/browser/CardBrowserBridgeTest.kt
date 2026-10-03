package heizige.kk.khatkit.app.core.data.browser

import heizige.kk.khatkit.bridge.ApprovalGate
import heizige.kk.khatkit.bridge.BridgeContext
import org.junit.Assert.*
import org.junit.Test

class CardBrowserBridgeTest {
    private class Controller : BrowserToolController {
        val opened = mutableListOf<String>()
        val closed = mutableListOf<String>()
        var selected = ""
        var navigated = ""
        override suspend fun listSessions() = "[]"
        override suspend fun open(url: String): String {
            opened += url
            return """{"id":"tab-${opened.size}"}"""
        }
        override suspend fun snapshot() = selected
        override suspend fun navigate(url: String): String { navigated = url; return url }
        override fun forSession(id: String): BrowserToolController { selected = id; return this }
        override suspend fun close(id: String): String { closed += id; return id }
    }

    private fun context(
        permissions: Map<String, String> = emptyMap(),
        gate: ApprovalGate? = ApprovalGate { _, _, _ -> true },
    ) = BridgeContext("test-card", "lua", 1, setOf("example.com"), permissions = permissions, approvalGate = gate)

    @Test
    fun defaultAsksEveryTimeAndCleanupDoesNotAsk() {
        var asks = 0
        val controller = Controller()
        val bridge = CardBrowserBridge(context(gate = ApprovalGate { _, _, _ -> asks++; true }), controller) {}
        bridge.open("https://example.com")
        bridge.snapshot("tab-1", null, 0)
        bridge.snapshot("tab-1", null, 0)
        assertEquals(3, asks)
        bridge.close()
        assertEquals(listOf("tab-1"), controller.closed)
        assertEquals(3, asks)
    }

    @Test
    fun denyMissingGateAndExpiredRunDoNotInvokeHost() {
        val controller = Controller()
        val denied = CardBrowserBridge(context(mapOf("browser.open" to "deny")), controller) {}
        assertThrows(IllegalStateException::class.java) { denied.open("https://example.com") }
        val noGate = CardBrowserBridge(context(gate = null), controller) {}
        assertThrows(IllegalStateException::class.java) { noGate.open("https://example.com") }
        val expiredContext = context(mapOf("browser.open" to "allow"))
        expiredContext.deadline.setAt(1)
        val expired = CardBrowserBridge(expiredContext, controller) {}
        assertThrows(IllegalStateException::class.java) { expired.open("https://example.com") }
        assertTrue(controller.opened.isEmpty())
    }

    @Test
    fun onlyOwnedSessionsAreUsableAndNavigationChecksDomain() {
        val controller = Controller()
        val bridge = CardBrowserBridge(context(), controller) {}
        bridge.open("https://example.com")
        assertThrows(IllegalArgumentException::class.java) { bridge.snapshot("foreign", null, 0) }
        assertThrows(IllegalArgumentException::class.java) {
            bridge.act("tab-1", "navigate", mapOf("url" to "https://example.com.evil.test"))
        }
        bridge.act("tab-1", "navigate", mapOf("url" to "https://shop.example.com"))
        assertEquals("https://shop.example.com", controller.navigated)
        bridge.close("tab-1")
        assertThrows(IllegalArgumentException::class.java) { bridge.snapshot("tab-1", null, 0) }
        assertThrows(IllegalArgumentException::class.java) { bridge.snapshot("tab-1", null, -1) }
    }

    @Test
    fun domainPolicyRejectsSuffixTricksUserinfoAndUnsupportedSchemes() {
        val policy = BrowserDomainPolicy(setOf("example.com"))
        policy.check("https://example.com")
        policy.check("https://SHOP.EXAMPLE.COM/path")
        listOf("https://notexample.com", "https://example.com@evil.test", "file:///etc/passwd",
            "javascript:alert(1)", "https://example.com.evil.test").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { policy.check(url) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            BrowserDomainPolicy(emptySet()).check("https://example.com")
        }
    }
}
