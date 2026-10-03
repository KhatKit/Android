package heizige.kk.khatkit.app.core.data.browser

import java.util.UUID

/** Lightweight multi-tab browser state independent from an Android WebView instance. */
class BrowserSessionManager(
    private val maxSnapshotChars: Int = DEFAULT_MAX_SNAPSHOT_CHARS,
) {
    private val sessions = linkedMapOf<String, BrowserSession>()
    private var activeSessionId: String? = null

    fun createSession(
        url: String = "about:blank",
        title: String? = null,
    ): BrowserSession {
        val session = BrowserSession(
            id = UUID.randomUUID().toString(),
            currentUrl = url,
            title = title,
            history = listOf(url),
            historyIndex = 0,
        )
        sessions[session.id] = session
        activeSessionId = session.id
        return session
    }

    fun closeSession(id: String): Boolean {
        val removed = sessions.remove(id) ?: return false
        if (activeSessionId == removed.id) {
            activeSessionId = sessions.keys.lastOrNull()
        }
        return true
    }

    fun getSession(id: String): BrowserSession? = sessions[id]

    fun listSessions(): List<BrowserSession> = sessions.values.toList()

    fun activeSession(): BrowserSession? = activeSessionId?.let(sessions::get)

    fun selectSession(id: String): BrowserSession {
        require(sessions.containsKey(id)) { "Unknown browser session: $id" }
        activeSessionId = id
        return sessions.getValue(id)
    }

    fun navigate(id: String, url: String, title: String? = null): BrowserSession {
        require(url.isNotBlank()) { "URL must not be blank" }
        val session = requireSession(id)
        val history = session.history.take(session.historyIndex + 1) + url
        val updated = session.copy(
            currentUrl = url,
            title = title ?: session.title,
            history = history,
            historyIndex = history.lastIndex,
        )
        sessions[id] = updated
        return updated
    }

    fun goBack(id: String): BrowserSession? = moveHistory(id, -1)

    fun goForward(id: String): BrowserSession? = moveHistory(id, 1)

    fun updatePage(
        id: String,
        url: String,
        title: String?,
        snapshot: String?,
    ): BrowserSession {
        val session = requireSession(id)
        val updated = session.copy(
            currentUrl = url,
            title = title,
            snapshot = snapshot?.take(maxSnapshotChars),
        )
        sessions[id] = updated
        return updated
    }

    private fun moveHistory(id: String, offset: Int): BrowserSession? {
        val session = requireSession(id)
        val target = session.historyIndex + offset
        if (target !in session.history.indices) return null
        val updated = session.copy(
            currentUrl = session.history[target],
            historyIndex = target,
        )
        sessions[id] = updated
        return updated
    }

    private fun requireSession(id: String): BrowserSession =
        sessions[id] ?: error("Unknown browser session: $id")

    companion object {
        const val DEFAULT_MAX_SNAPSHOT_CHARS = 20_000
    }
}

data class BrowserSession(
    val id: String,
    val currentUrl: String,
    val title: String? = null,
    val snapshot: String? = null,
    val history: List<String> = emptyList(),
    val historyIndex: Int = -1,
)