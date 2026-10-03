package heizige.kk.khatkit.app.core.data.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserWebViewBridgeTest {
    @Test
    fun `session manager accepts only browser urls`() {
        val manager = BrowserSessionManager()
        val session = manager.createSession("about:blank")
        assertEquals("about:blank", session.currentUrl)
    }
}
