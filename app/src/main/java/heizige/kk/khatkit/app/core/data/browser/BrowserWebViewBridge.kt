package heizige.kk.khatkit.app.core.data.browser

import android.webkit.WebView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * Thin Android WebView adapter. Browser protocol/state stays platform independent;
 * only this class knows how to evaluate JavaScript in a WebView.
 */
class BrowserWebViewBridge(
    private val sessions: BrowserSessionManager,
    private val maxSnapshotBytes: Int = BrowserSessionManager.DEFAULT_MAX_SNAPSHOT_CHARS,
) {
    private val json = Json

    fun attach(webView: WebView, sessionId: String) {
        requireNotNull(sessions.getSession(sessionId)) { "Unknown browser session: $sessionId" }
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                capture(view, sessionId, url)
            }
        }
    }

    fun capture(webView: WebView, sessionId: String, url: String = webView.url.orEmpty()) {
        val script = """
            (() => {
              const hidden = new Set(['SCRIPT','STYLE','NOSCRIPT','TEMPLATE']);
              const root = document.body || document.documentElement;
              if (!root) return '';
              const clone = root.cloneNode(true);
              clone.querySelectorAll('*').forEach((node) => {
                if (hidden.has(node.tagName)) node.remove();
              });
              return clone.innerHTML;
            })()
        """.trimIndent()
        webView.evaluateJavascript(script) { encoded ->
            val html = runCatching { json.parseToJsonElement(encoded).jsonPrimitive.content }.getOrNull()
                ?: return@evaluateJavascript
            sessions.updatePage(
                id = sessionId,
                url = url,
                title = webView.title,
                snapshot = buildBrowserDomSnapshot(html, maxSnapshotBytes),
            )
        }
    }
}
