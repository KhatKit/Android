package heizige.kk.khatkit.app.core.data.browser

data class BrowserTabState(
    val id: String,
    val url: String,
    val title: String?,
    val loading: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val error: String? = null,
)

data class BrowserUiState(
    val tabs: List<BrowserTabState> = emptyList(),
    val activeId: String? = null,
) {
    val active: BrowserTabState? get() = tabs.firstOrNull { it.id == activeId }
}

data class BrowserDownloadRequest(
    val sessionId: String,
    val url: String,
    val fileName: String,
    val userAgent: String?,
)
