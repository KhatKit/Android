package heizige.kk.khatkit.app.feature.webview

import androidx.activity.compose.BackHandler
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
import heizige.kk.kedge.overlays.KedgeDropdownItemSlot
import heizige.kk.kedge.overlays.KedgeDropdownMenuSlots
import heizige.kk.kedge.components.KedgeIconButton
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
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.theme.JetbrainsMono
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.app.core.ui.icons.bugReport
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.khatkit.app.core.ui.icons.public
import heizige.kk.khatkit.app.core.ui.icons.refresh
import heizige.kk.kedge.adaptive.KedgePageScaffold
import heizige.kk.kedge.theme.KedgeTextStyles

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

    BackHandler(state.canGoBack) {
        state.goBack()
    }

    KedgePageScaffold(
       topBar = {
            KedgePageTopBar(
                title = state.pageTitle?.takeIf { it.isNotEmpty() } ?: state.currentUrl
                        ?: "",
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    KedgeIconButton(onClick = { state.reload() }, shapes = IconButtonDefaults.shapes()) {
                        Icon(refresh, contentDescription = "Refresh")
                    }

                    KedgeIconButton(
                        onClick = { state.goForward() },
                        enabled = state.canGoForward,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(arrowForward, contentDescription = "Forward")
                    }

                    val urlHandler = LocalUriHandler.current
                    KedgeIconButton(
                        onClick = { showDropdown = true },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(moreVert, contentDescription = "More options")

                        KedgeDropdownMenuSlots(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            KedgeDropdownItemSlot(
                                text = { Text("Open in Browser") },
                                leadingIcon = { Icon(public, contentDescription = null) },
                                onClick = {
                                    showDropdown = false
                                    state.currentUrl?.let { url ->
                                        if (url.isNotBlank()) {
                                            urlHandler.openUri(url)
                                        }
                                    }
                                }
                            )
                            KedgeDropdownItemSlot(
                                text = { Text("Console Logs") },
                                leadingIcon = { Icon(bugReport, contentDescription = null) },
                                onClick = {
                                    showDropdown = false
                                    showConsoleSheet = true
                                }
                            )
                        }
                    }
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

    if (showConsoleSheet) {
        PrimaryBottomSheet(
            visible = true,
            title = "Console Logs",
            imageVector = bugReport,
            onDismiss = { showConsoleSheet = false },
            scrollable = false,
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                SelectionContainer {
                    LazyColumn {
                        items(state.consoleMessages) { message ->
                            Text(
                                text = "${message.messageLevel().name}: ${message.message()}\n" +
                                    "Source: ${message.sourceId()}:${message.lineNumber()}",
                                style = KedgeTextStyles.body(),
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
                        style = KedgeTextStyles.body(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
