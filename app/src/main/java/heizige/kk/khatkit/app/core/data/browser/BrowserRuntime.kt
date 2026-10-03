package heizige.kk.khatkit.app.core.data.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.view.ViewGroup
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import kotlin.coroutines.resume

/** Each session owns a WebView and an operation queue. All WebView access is on Main. */
class BrowserRuntime(
    context: Context,
    private val sessions: BrowserSessionManager = BrowserSessionManager(),
    private val archive: BrowserArchive = RoomBrowserArchive.get(context),
    private val loadTimeoutMillis: Long = 30_000,
    private val webViewFactory: (Context) -> WebView = { WebView(it) },
    private val checkUrl: (String) -> Unit = BrowserSessionManager::validateBrowserUrl,
) : BrowserToolController {
    private val appContext = context.applicationContext
    private class Tab(
        val view: WebView,
        val checkUrl: (String) -> Unit,
    ) {
        val mutex = Mutex()
        var loading: CompletableDeferred<Unit>? = null
        var closed = false
        var recordedUrl: String? = null
        var error: String? = null
    }
    private val tabs = ConcurrentHashMap<String, Tab>()
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableUiState = MutableStateFlow(BrowserUiState())
    val uiState = mutableUiState.asStateFlow()
    private val mutableDownloadRequest = MutableStateFlow<BrowserDownloadRequest?>(null)
    val downloadRequest = mutableDownloadRequest.asStateFlow()

    fun dismissDownload(request: BrowserDownloadRequest) {
        mutableDownloadRequest.compareAndSet(request, null)
    }

    private fun publishState() {
        check(Looper.myLooper() == Looper.getMainLooper())
        mutableUiState.value = BrowserUiState(
            sessions.listSessions().mapNotNull { session ->
                tabs[session.id]?.takeUnless { it.closed }?.let { tab ->
                    BrowserTabState(session.id, tab.view.url ?: session.currentUrl,
                        tab.view.title ?: session.title, tab.loading?.isCompleted == false,
                        tab.view.canGoBack(), tab.view.canGoForward(), tab.error)
                }
            },
            sessions.activeSession()?.id,
        )
    }

    /** A Compose host borrows the existing WebView; removing the host never closes the tab. */
    fun attach(id: String, host: FrameLayout) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val view = tabs[id]?.takeUnless { it.closed }?.view ?: return
        if (view.parent !== host) {
            (view.parent as? ViewGroup)?.removeView(view)
            host.removeAllViews()
            host.addView(view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    suspend fun select(id: String) = withContext(Dispatchers.Main.immediate) {
        sessions.selectSession(id)
        publishState()
    }

    suspend fun stop(id: String) = withContext(Dispatchers.Main.immediate) {
        tabs[id]?.let {
            it.view.stopLoading()
            // Finish the navigation with the partial document; cancellation would make
            // open() treat an intentional Stop as a failed new tab and destroy it.
            it.loading?.complete(Unit)
            it.error = null
        }
        publishState()
    }

    suspend fun moveHistory(id: String, backwards: Boolean) = onTab(id) { tab ->
        val possible = if (backwards) tab.view.canGoBack() else tab.view.canGoForward()
        if (possible) {
            tab.loading = CompletableDeferred()
            if (backwards) tab.view.goBack() else tab.view.goForward()
            publishState()
            tab.loading!!.await()
            capture(id, tab)
        }
    }

    override suspend fun history(): String =
        Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(BrowserHistoryEntry.serializer()), archive.history())

    override suspend fun clearHistory(): String {
        archive.clearHistory()
        return """{"cleared":true}"""
    }

    override suspend fun removeHistory(urls: List<String>): String {
        archive.removeHistory(urls)
        return """{"cleared":true}"""
    }

    override suspend fun bookmarks(): String =
        Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(BrowserBookmark.serializer()), archive.bookmarks())

    override suspend fun bookmark(url: String, title: String?): String {
        archive.bookmark(url, title)
        return """{"saved":true}"""
    }

    override suspend fun removeBookmark(url: String): String =
        buildJsonObject { put("removed", archive.removeBookmark(url)) }.toString()

    override suspend fun listSessions(): String = JsonArray(sessions.listSessions().map {
        buildJsonObject {
            put("id", it.id)
            put("url", it.currentUrl)
            put("title", it.title)
            put("active", sessions.activeSession()?.id == it.id)
        }
    }).toString()

    override fun forSession(id: String): BrowserToolController {
        require(sessions.getSession(id) != null) { "Unknown browser session: $id" }
        return object : BrowserToolController {
            override suspend fun listSessions() = this@BrowserRuntime.listSessions()
            override suspend fun open(url: String) = this@BrowserRuntime.open(url)
            override suspend fun snapshot() = onTab(id) { capture(id, it) }
            override suspend fun snapshot(selector: String?, offset: Int) =
                onTab(id) { capture(id, it, selector, offset) }
            override suspend fun snapshot(selector: String?, offset: Int, since: String?) =
                onTab(id) { capture(id, it, selector, offset, since) }
            override suspend fun navigate(url: String) = navigateTab(id, url)
            override suspend fun screenshot() = captureScreenshot(id)
            override suspend fun waitFor(selector: String?, text: String?, timeoutMs: Int) =
                waitForTab(id, selector, text, timeoutMs)
            override suspend fun click(selector: String) = action(id, BrowserScripts.click(selector))
            override suspend fun type(selector: String, text: String) = action(id, BrowserScripts.type(selector, text))
            override suspend fun extractLinks() = action(id, BrowserScripts.links)
            override suspend fun extractText() = action(id, BrowserScripts.text)
            override suspend fun extractText(selector: String) = action(id, BrowserScripts.text(selector))
            override suspend fun scroll(x: Int, y: Int) = action(id, BrowserScripts.scroll(x, y))
            override suspend fun pressKey(selector: String, key: String) = action(id, BrowserScripts.pressKey(selector, key))
            override suspend fun hover(selector: String) = action(id, BrowserScripts.hover(selector))
            override suspend fun close(id: String) = this@BrowserRuntime.close(id)
        }
    }

    override suspend fun open(url: String): String {
        checkUrl(url)
        val id = withContext(Dispatchers.Main.immediate) {
            val session = sessions.createSession()
            try {
                val tab = Tab(webViewFactory(appContext), checkUrl)
                tab.view.apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY),
                    )
                    layout(0, 0, 1080, 1920)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val error = runCatching { tab.checkUrl(request.url.toString()) }.exceptionOrNull()
                            if (error != null && request.isForMainFrame) {
                                tab.error = error.message
                                tab.loading?.completeExceptionally(error)
                                publishState()
                            }
                            return error != null
                        }

                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            if (runCatching { tab.checkUrl(request.url.toString()) }.isSuccess) return null
                            return WebResourceResponse(
                                "text/plain", "UTF-8", 403, "Forbidden", emptyMap(),
                                "Browser domain is not allowed".byteInputStream(),
                            )
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            if (tab.closed) return
                            tab.error = null
                            if (tab.loading == null || tab.loading!!.isCompleted) tab.loading = CompletableDeferred()
                            runCatching { tab.checkUrl(url) }.exceptionOrNull()?.let {
                                view.stopLoading()
                                tab.loading?.completeExceptionally(it)
                                tab.error = it.message
                            }
                            publishState()
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            if (!tab.closed && view.url == url) {
                                tab.loading?.complete(Unit)
                                sessions.updatePage(session.id, url, view.title, null)
                                publishState()
                                callbackScope.launch {
                                    try { tab.mutex.withLock { if (!tab.closed && tab.error == null) recordPage(tab) } }
                                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                    catch (e: Exception) { tab.error = e.message; publishState() }
                                }
                            }
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            if (!tab.closed && url != null) {
                                sessions.updatePage(session.id, url, view.title, null)
                                publishState()
                            }
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                tab.error = error.description.toString()
                                tab.loading?.completeExceptionally(IllegalStateException(error.description.toString()))
                                publishState()
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView, title: String?) {
                            if (!tab.closed) {
                                sessions.updatePage(session.id, view.url ?: "about:blank", title, null)
                                publishState()
                            }
                        }
                    }
                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                        try {
                            tab.checkUrl(url)
                            require(url.startsWith("http://", true) || url.startsWith("https://", true)) {
                                "此下载链接暂不支持"
                            }
                            // Keep the first request visible until the user handles it.
                            mutableDownloadRequest.compareAndSet(null, BrowserDownloadRequest(
                                session.id, url,
                                android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                                    .replace(Regex("[/\\\\\\p{Cntrl}]"), "_").take(120).ifBlank { "download" },
                                userAgent,
                            ))
                        } catch (e: Exception) {
                            tab.error = e.message
                        }
                        tab.loading?.complete(Unit)
                        publishState()
                    }
                }
                tabs[session.id] = tab
                publishState()
                session.id
            } catch (e: Throwable) {
                sessions.closeSession(session.id)
                throw e
            }
        }
        return try {
            navigateTab(id, url)
        } catch (e: Throwable) {
            withContext(NonCancellable) { close(id) }
            throw e
        }
    }

    private fun activeId() = sessions.activeSession()?.id ?: error("No active browser session")
    override suspend fun snapshot() = forSession(activeId()).snapshot()
    override suspend fun snapshot(selector: String?, offset: Int) =
        forSession(activeId()).snapshot(selector, offset)
    override suspend fun snapshot(selector: String?, offset: Int, since: String?) =
        forSession(activeId()).snapshot(selector, offset, since)
    override suspend fun navigate(url: String): String =
        sessions.activeSession()?.let { navigateTab(it.id, url) } ?: open(url)
    override suspend fun click(selector: String) = forSession(activeId()).click(selector)
    override suspend fun type(selector: String, text: String) = forSession(activeId()).type(selector, text)
    override suspend fun extractLinks() = forSession(activeId()).extractLinks()
    override suspend fun extractText() = forSession(activeId()).extractText()
    override suspend fun extractText(selector: String) = forSession(activeId()).extractText(selector)
    override suspend fun scroll(x: Int, y: Int) = forSession(activeId()).scroll(x, y)
    override suspend fun pressKey(selector: String, key: String) = forSession(activeId()).pressKey(selector, key)
    override suspend fun hover(selector: String) = forSession(activeId()).hover(selector)
    override suspend fun screenshot() = captureScreenshot(activeId())
    override suspend fun waitFor(selector: String?, text: String?, timeoutMs: Int) =
        waitForTab(activeId(), selector, text, timeoutMs)

    private suspend fun waitForTab(id: String, selector: String?, text: String?, timeoutMs: Int): String {
        require(!selector.isNullOrBlank() || !text.isNullOrEmpty()) { "A selector or text is required" }
        require(timeoutMs in 1..30_000) { "timeout_ms must be between 1 and 30000" }
        return withTimeout(timeoutMs.toLong()) {
            onTab(id) { tab ->
                while (true) {
                    tab.loading?.await()
                    tab.checkUrl(tab.view.url ?: "about:blank")
                    val state = Json.parseToJsonElement(evaluate(tab.view, BrowserScripts.ready(selector, text))).jsonObject
                    state["error"]?.let { error(it.jsonPrimitive.content) }
                    if (state["ready"]?.jsonPrimitive?.booleanOrNull == true) break
                    delay(100)
                }
                capture(id, tab)
            }
        }
    }

    private suspend fun <T> onTab(id: String, block: suspend (Tab) -> T): T {
        val tab = tabs[id] ?: error("Unknown browser session: $id")
        return tab.mutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                check(!tab.closed) { "Browser session is closed: $id" }
                try {
                    withTimeout(loadTimeoutMillis) {
                        tab.loading?.await()
                        block(tab)
                    }
                } catch (e: Throwable) {
                    tab.view.stopLoading()
                    tab.loading?.cancel()
                    tab.loading = null
                    tab.error = e.message
                    publishState()
                    throw e
                }
            }
        }
    }

    private suspend fun navigateTab(id: String, url: String): String {
        checkUrl(url)
        return onTab(id) { tab ->
            tab.checkUrl(url)
            tab.loading = CompletableDeferred()
            tab.recordedUrl = null
            tab.view.loadUrl(url)
            publishState()
            tab.loading!!.await()
            val actualUrl = tab.view.url ?: url
            tab.checkUrl(actualUrl)
            sessions.navigate(id, actualUrl, tab.view.title)
            capture(id, tab)
        }
    }

    private suspend fun action(id: String, script: String): String = onTab(id) { tab ->
        tab.checkUrl(tab.view.url ?: "about:blank")
        evaluate(tab.view, script)
    }

    private suspend fun capture(
        id: String, tab: Tab, selector: String? = null, offset: Int = 0, since: String? = null,
    ): String {
        tab.checkUrl(tab.view.url ?: "about:blank")
        val snapshot = evaluate(tab.view, BrowserScripts.snapshot(selector, offset, since))
        val updated = sessions.updatePage(id, tab.view.url ?: "about:blank", tab.view.title, snapshot)
        recordPage(tab)
        publishState()
        return buildJsonObject {
            put("id", updated.id)
            put("url", updated.currentUrl)
            put("title", updated.title)
            put("snapshot", Json.parseToJsonElement(snapshot))
        }.toString()
    }

    private suspend fun recordPage(tab: Tab) {
        val url = tab.view.url ?: return
        tab.checkUrl(url)
        if (url != tab.recordedUrl) {
            archive.record(url, tab.view.title)
            tab.recordedUrl = url
        }
    }

    private suspend fun captureScreenshot(id: String): BrowserScreenshot = onTab(id) { tab ->
        val view = tab.view
        tab.checkUrl(view.url ?: "about:blank")
        check(view.width > 0 && view.height > 0) { "Browser viewport has not been laid out" }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            val metadata = buildJsonObject {
                put("id", id)
                put("url", view.url)
                put("width", bitmap.width)
                put("height", bitmap.height)
                put("viewport_only", true)
            }.toString()
            val image = withContext(Dispatchers.IO) {
                screenshotFilesMutex.withLock {
                    val directory = File(appContext.filesDir, "browser/screenshots")
                    check(directory.isDirectory || directory.mkdirs()) { "Cannot create browser screenshot directory" }
                    val existing = directory.listFiles().orEmpty()
                        .filter { it.isFile && it.name.startsWith("viewport-") && it.extension == "png" }
                        .sortedByDescending { it.lastModified() }
                    val expiry = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                    existing.forEachIndexed { index, file ->
                        if (index >= 39 || file.lastModified() < expiry) file.delete()
                    }
                    val file = File.createTempFile("viewport-", ".png", directory)
                    try {
                        file.outputStream().use {
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Browser screenshot encoding failed" }
                        }
                        file
                    } catch (e: Throwable) {
                        file.delete()
                        throw e
                    }
                }
            }
            BrowserScreenshot(metadata, Uri.fromFile(image).toString())
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun evaluate(view: WebView, script: String): String =
        suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(script) { encoded ->
                if (continuation.isActive) {
                    continuation.resume(
                        (runCatching { Json.parseToJsonElement(encoded) }.getOrNull() as? JsonPrimitive)
                            ?.content ?: "null",
                    )
                }
            }
        }

    override suspend fun close(id: String): String {
        val tab = tabs[id] ?: return "Unknown browser session $id"
        return tab.mutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                if (!tab.closed) {
                    tab.closed = true
                    tab.loading?.cancel()
                    tab.view.stopLoading()
                    (tab.view.parent as? ViewGroup)?.removeView(tab.view)
                    tab.view.destroy()
                    tabs.remove(id, tab)
                    mutableDownloadRequest.value?.takeIf { it.sessionId == id }?.let(::dismissDownload)
                    sessions.closeSession(id)
                    publishState()
                }
                "Closed browser session $id"
            }
        }
    }

    companion object {
        private val screenshotFilesMutex = Mutex()
    }
}
