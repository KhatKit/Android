package heizige.kk.khatkit.app.core.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("abcdefghij\n[Snapshot truncated]", buildBrowserDomSnapshot("abcdefghijk", 10))
    }
}