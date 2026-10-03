package heizige.kk.khatkit.app.core.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDomSnapshotTest {
    @Test
    fun `removes executable and styling content while preserving visible text`() {
        val html = """
            <html><style>.x{display:none}</style><body>
            <h1>Products</h1><button data-id="buy">Buy now</button>
            <script>alert('secret')</script><p>Price: $9</p>
            </body></html>
        """.trimIndent()

        val snapshot = buildBrowserDomSnapshot(html)

        assertEquals("Products\nBuy now\nPrice: $9", snapshot)
        assertFalse(snapshot.contains("alert"))
        assertFalse(snapshot.contains("display:none"))
    }

    @Test
    fun `bounds snapshot output`() {
        assertTrue(buildBrowserDomSnapshot("abcdefghijk", 10).toByteArray().size <= 10)
    }

    @Test
    fun `utf8 budget includes truncation marker without splitting code points`() {
        for (budget in listOf(1, 9, 40, 100, 20_000)) {
            val snapshot = buildBrowserDomSnapshot("测试😀".repeat(4000), budget)
            assertTrue(snapshot.toByteArray(Charsets.UTF_8).size <= budget)
            assertEquals(snapshot, snapshot.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
        }
    }
}
