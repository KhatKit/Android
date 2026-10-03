package heizige.kk.khatkit.app.core.data.browser

/**
 * Internal browser runtime contract.
 *
 * Browser capabilities are exposed to AI only through CardBrowserBridge.
 */
interface BrowserToolController {
    fun forSession(id: String): BrowserToolController =
        error("Targeting browser sessions is unavailable")

    suspend fun listSessions(): String
    suspend fun open(url: String): String
    suspend fun snapshot(): String

    suspend fun snapshot(selector: String?, offset: Int): String {
        require(selector == null && offset == 0) {
            "Filtered or paged snapshots are unavailable"
        }
        return snapshot()
    }

    suspend fun snapshot(selector: String?, offset: Int, since: String?): String {
        require(since == null) { "Incremental snapshots are unavailable" }
        return snapshot(selector, offset)
    }

    suspend fun navigate(url: String): String
    suspend fun screenshot(): BrowserScreenshot =
        error("Browser screenshots are unavailable")

    suspend fun waitFor(selector: String?, text: String?, timeoutMs: Int): String =
        error("Browser waiting is unavailable")

    suspend fun click(selector: String): String =
        error("Browser actions are unavailable")

    suspend fun type(selector: String, text: String): String =
        error("Browser actions are unavailable")

    suspend fun extractLinks(): String =
        error("Browser actions are unavailable")

    suspend fun close(id: String): String =
        error("Closing browser tabs is unavailable")

    suspend fun extractText(): String =
        error("Browser actions are unavailable")

    suspend fun extractText(selector: String): String =
        error("Element text is unavailable")

    suspend fun scroll(x: Int, y: Int): String =
        error("Browser actions are unavailable")

    suspend fun pressKey(selector: String, key: String): String =
        error("Browser actions are unavailable")

    suspend fun hover(selector: String): String =
        error("Browser actions are unavailable")

    suspend fun history(): String =
        error("Browser history is unavailable")

    suspend fun clearHistory(): String =
        error("Browser history is unavailable")

    suspend fun removeHistory(urls: List<String>): String =
        error("Selective history deletion is unavailable")

    suspend fun bookmarks(): String =
        error("Browser bookmarks are unavailable")

    suspend fun bookmark(url: String, title: String?): String =
        error("Browser bookmarks are unavailable")

    suspend fun removeBookmark(url: String): String =
        error("Browser bookmarks are unavailable")
}

data class BrowserScreenshot(
    val metadata: String,
    val imageUrl: String,
)
