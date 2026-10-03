package heizige.kk.khatkit.app.core.data.browser

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class BrowserHistoryStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `records unique urls and keeps newest entries`() {
        val store = BrowserHistoryStore(temporaryFolder.root.resolve("history.json"), maxEntries = 2)
        store.clear()
        store.record("https://one.example", now = 1)
        store.record("https://two.example", now = 2)
        store.record("https://one.example", now = 3)

        assertEquals(listOf("https://two.example", "https://one.example"), store.list().map { it.url })
        store.clear()
    }
}
