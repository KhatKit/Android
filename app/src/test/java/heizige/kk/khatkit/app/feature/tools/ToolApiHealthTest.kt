package heizige.kk.khatkit.app.feature.tools

import heizige.kk.khatkit.app.feature.tools.data.apiHost
import heizige.kk.khatkit.app.feature.tools.data.replaceApiStatus
import heizige.kk.khatkit.app.feature.tools.data.toApiStatus
import heizige.kk.khatkit.bridge.CardApiObservation
import org.junit.Assert.*
import org.junit.Test

class ToolApiHealthTest {
    @Test fun latestResultReplacesOnlyTheSameCardHostAndMethod() {
        val first = CardApiObservation("sample", "example.test", "GET", 503, 1).toApiStatus()!!
        val other = first.copy(method = "POST", status = 401, timeMillis = 2)
        val recovered = first.copy(status = 200, timeMillis = 3)
        assertEquals(listOf(recovered, other), replaceApiStatus(listOf(first, other), recovered))
        assertFalse(recovered.issue)
        assertTrue(other.issue)
    }

    @Test fun locationsAreReducedToHosts() {
        assertEquals("example.test", apiHost("https://EXAMPLE.test/path?k=secret"))
        assertNull(apiHost("*"))
        assertNull(apiHost("example.test/?k=secret"))
    }
}
