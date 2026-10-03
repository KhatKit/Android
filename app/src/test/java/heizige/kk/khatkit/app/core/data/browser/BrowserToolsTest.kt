package heizige.kk.khatkit.app.core.data.browser

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class BrowserToolsTest {
    @Test
    fun `browser open requires approval and forwards url`() = runBlocking {
        var opened = ""
        val controller = object : BrowserToolController {
            override suspend fun listSessions() = "[]"
            override suspend fun open(url: String): String { opened = url; return "ok" }
            override suspend fun snapshot() = "snapshot"
            override suspend fun navigate(url: String) = url
        }
        val tool = buildBrowserTools(controller).first { it.name == "browser_open" }
        assertTrue(tool.needsApproval(JsonPrimitive("{}")))
        tool.execute(buildJsonObject { put("url", "https://example.com") })
        assertEquals("https://example.com", opened)
    }

    @Test
    fun `browser controller rejects unsupported schemes`() {
        val controller = BrowserSessionController(BrowserSessionManager())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { controller.open("file:///secret") }
        }
    }
}
