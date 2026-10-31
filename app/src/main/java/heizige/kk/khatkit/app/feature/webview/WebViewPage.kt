package heizige.kk.khatkit.app.feature.webview

import heizige.kk.khatkit.app.LocalPageRoute
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiItem
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import heizige.kk.khatkit.uikit.mochi.MochiMenuRow
import heizige.kk.khatkit.uikit.mochi.MochiMenuPopup
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.webview.WEB_VIEW_BASE_URL
import heizige.kk.khatkit.app.core.ui.components.webview.WebView
import heizige.kk.khatkit.app.core.ui.components.webview.WebViewContentCache
import heizige.kk.khatkit.app.core.ui.components.webview.rememberWebViewState
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.theme.JetbrainsMono
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.app.core.ui.icons.bugReport
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.khatkit.app.core.ui.icons.public
import heizige.kk.khatkit.app.core.ui.icons.refresh
import heizige.kk.khatkit.app.feature.ui.shell.MochiPageScaffold
import androidx.compose.runtime.DisposableEffect
import heizige.kk.khatkit.app.PageBackInterceptor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewPage(url: String, contentId: String) {
    val context = LocalContext.current
    val state = if (url.isNotEmpty()) {
        rememberWebViewState(
            url = url,
            settings = {
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
            })
    } else {
        val content = remember(contentId) {
            WebViewContentCache.load(context.cacheDir, contentId).orEmpty()
        }
        rememberWebViewState(
            data = content,
            baseUrl = WEB_VIEW_BASE_URL,
            mimeType = "text/html",
            settings = {
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
            }
        )
    }

    var showDropdown by remember { mutableStateOf(false) }
    var showConsoleSheet by remember { mutableStateOf(false) }

    // U7a-6：WebView 内可后退时先退网页，登记到 PageBackInterceptor（不再页面级 BackHandler）。
    // 登记时带上所在页：只拦本页的返回，压在下面的页里还开着的浮层不会吞掉栈顶页的返回
    val webBackTokenRoute = LocalPageRoute.current
    val webBackToken = remember { Any() }
    DisposableEffect(state.canGoBack) {
        if (state.canGoBack) {
            PageBackInterceptor.push(webBackToken, webBackTokenRoute) {
                state.goBack()
                true
            }
        } else {
            PageBackInterceptor.pop(webBackToken)
        }
        onDispose { PageBackInterceptor.pop(webBackToken) }
    }

    MochiPageScaffold(
       topBar = {
            KedgePageTopBar(
                title = state.pageTitle?.takeIf { it.isNotEmpty() } ?: state.currentUrl
                        ?: "",
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    MochiIconButton(onClick = { state.reload() }, shapes = IconButtonDefaults.shapes()) {
                        Icon(refresh, contentDescription = "Refresh")
                    }

                    MochiIconButton(
                        onClick = { state.goForward() },
                        enabled = state.canGoForward,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(arrowForward, contentDescription = "Forward")
                    }

                    val urlHandler = LocalUriHandler.current
                    MochiDropdown(
                        expanded = showDropdown,
                        onExpandedChange = { showDropdown = it },
                        items = kotlinx.collections.immutable.persistentListOf(
                            MochiItem(
                                label = "Open in Browser",
                                icon = { color -> Icon(public, contentDescription = null, tint = color) },
                            ),
                            MochiItem(
                                label = "Console Logs",
                                icon = { color -> Icon(bugReport, contentDescription = null, tint = color) },
                            ),
                        ),
                        onItemClick = { index ->
                            showDropdown = false
                            when (index) {
                                0 -> state.currentUrl?.let { url ->
                                    if (url.isNotBlank()) urlHandler.openUri(url)
                                }
                                1 -> showConsoleSheet = true
                            }
                        },
                        onClick = { showDropdown = !showDropdown },
                        buttonContent = { Icon(moreVert, contentDescription = "More options") },
                    )
                }
            )
        },
    
        md3ScrollBehavior = null,
    ) {
        WebView(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
        )
    }

        PrimaryBottomSheet(
            visible = showConsoleSheet,
            title = "Console Logs",
            imageVector = bugReport,
            onDismiss = { showConsoleSheet = false },
            scrollable = false,
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                SelectionContainer {
                    LazyColumn {
                        items(state.consoleMessages) { message ->
                            Text(
                                text = "${message.messageLevel().name}: ${message.message()}\n" +
                                    "Source: ${message.sourceId()}:${message.lineNumber()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = JetbrainsMono,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                color = when (message.messageLevel().name) {
                                    "ERROR" -> MaterialTheme.colorScheme.error
                                    "WARNING" -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                if (state.consoleMessages.isEmpty()) {
                    Text(
                        text = "No console messages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
}
