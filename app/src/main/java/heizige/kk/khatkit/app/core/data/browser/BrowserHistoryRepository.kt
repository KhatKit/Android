package heizige.kk.khatkit.app.core.data.browser

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class BrowserHistoryEntry(
    val url: String,
    val title: String? = null,
    val visitedAt: Long,
)

class BrowserHistoryRepository(
    private val file: File,
    private val maxEntries: Int = 200,
    private val json: Json = Json,
) {
    constructor(
        context: Context,
        maxEntries: Int = 200,
        json: Json = Json,
    ) : this(
        File(context.applicationContext.filesDir, "browser/history.json"),
        maxEntries,
        json,
    )

    @Synchronized
    fun list(): List<BrowserHistoryEntry> = read()

    @Synchronized
    fun record(url: String, title: String? = null, now: Long = System.currentTimeMillis()) {
        BrowserSessionManager.validateBrowserUrl(url)
        val updated = (list().filterNot { it.url == url } + BrowserHistoryEntry(url, title, now))
            .takeLast(maxEntries.coerceAtLeast(1))
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(updated))
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun read(): List<BrowserHistoryEntry> =
        runCatching {
            if (!file.isFile) emptyList()
            else json.decodeFromString<List<BrowserHistoryEntry>>(file.readText())
        }.getOrDefault(emptyList())
}
