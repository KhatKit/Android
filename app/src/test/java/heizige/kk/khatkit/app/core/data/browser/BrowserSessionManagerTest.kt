package heizige.kk.khatkit.app.core.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSessionManagerTest {
    @Test
    fun `supports multiple sessions and active selection`() {
        val manager = BrowserSessionManager()
        val first = manager.createSession("https://example.com")
        val second = manager.createSession("https://khatkit.dev")

        assertEquals(second.id, manager.activeSession()?.id)
        assertEquals(first.id, manager.selectSession(first.id).id)
        assertEquals(2, manager.listSessions().size)
        assertTrue(manager.closeSession(second.id))
        assertFalse(manager.closeSession(second.id))
    }

    @Test
    fun `tracks navigation history and bounds snapshots`() {
        val manager = BrowserSessionManager(maxSnapshotChars = 8)
        val session = manager.createSession("https://one.test")
        manager.navigate(session.id, "https://two.test")
        manager.navigate(session.id, "https://three.test")

        assertEquals("https://two.test", manager.goBack(session.id)?.currentUrl)
        assertEquals("https://three.test", manager.goForward(session.id)?.currentUrl)
        assertNull(manager.goForward(session.id))
        assertEquals("12345678", manager.updatePage(session.id, "https://three.test", "Three", "123456789").snapshot)
        assertNotNull(manager.getSession(session.id))
    }
}