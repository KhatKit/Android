package heizige.kk.khatkit.app.core.ui.components.khatkit

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.ui.SheetAction
import heizige.kk.khatkit.ui.UiRequest
import heizige.kk.khatkit.ui.UiSheetOptions
import heizige.kk.khatkit.uikit.KhatKitForm
import heizige.kk.khatkit.uikit.KhatKitSheet
import heizige.kk.khatkit.uikit.KhatKitSheetAction
import heizige.kk.khatkit.uikit.KhatKitTheme
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.icons.checkCircle
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.deleteForever
import heizige.kk.khatkit.app.core.ui.icons.description
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.khatkit.app.core.ui.icons.refresh
import heizige.kk.khatkit.app.core.ui.icons.save
import heizige.kk.khatkit.app.core.ui.icons.vpnKey
import heizige.kk.khatkit.app.core.ui.icons.warning
import heizige.kk.khatkit.app.core.ui.components.webview.WebView
import heizige.kk.khatkit.app.core.ui.components.webview.applyScriptWebOptions
import heizige.kk.khatkit.app.core.ui.components.webview.rememberWebViewState
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint

/** 结果卡片在宽屏下的内容最大宽度。 */
private val SHOW_CONTENT_MAX_WIDTH = 840.dp

/** 卡片网页登录 WebView 的默认高度（桌面模式下页面整体缩放到屏幕宽度）。 */
private val WEB_SHEET_HEIGHT = 480.dp

/**
 * 卡片声明的 `icon` 名 → 应用图标。
 *
 * 命不中时返回 null：顶栏只放图标按钮，没有图标的动作退回内容区文字按钮，
 * 避免出现无图标的空按钮。
 */
private fun sheetIcon(name: String): ImageVector? = when (name.trim().lowercase()) {
    "key", "key_round", "vpn_key", "login", "password" -> vpnKey
    "save", "save_cookie", "download" -> save
    "info", "info_status", "cookie_status" -> description
    "delete", "clear", "clear_cookie", "delete_forever" -> deleteForever
    "more", "more_vert", "overflow" -> moreVert
    "close", "cancel" -> close
    "refresh", "reload" -> refresh
    else -> null
}

private fun SheetAction.toSheetAction(): KhatKitSheetAction? {
    val vector = sheetIcon(icon)
    if (vector == null || event.isBlank() || label.isBlank()) return null
    return KhatKitSheetAction(event = event, label = label, imageVector = vector)
}

/**
 * 卡片 ui bridge 的宿主挂载点（设计文档 7.2）。
 *
 * 卡片执行时（可能在聊天流里），脚本线程阻塞等待；这里在 Compose 主线程
 * 渲染表单/确认/进度/结果卡片，交互后把值回传。渲染层在 :khatkit-ui。
 *
 * `ui.form` / `ui.show` 的 options 支持 fullscreen / landscape / height：
 * landscape 生效期间把 Activity 方向切到 FULL_SENSOR，弹层关闭（或请求变化）后还原 UNSPECIFIED。
 */
@Composable
fun KhatKitUiHost() {
    val provider = rememberAppEntryPoint().khatKitToolProvider()
    val request by provider.uiRequest.collectAsStateWithLifecycle()
    val progress by provider.uiProgress.collectAsStateWithLifecycle()

    val landscape = when (val current = request) {
        is UiRequest.Sheet -> current.options.landscape
        is UiRequest.Form -> current.options.landscape
        is UiRequest.Show -> current.options.landscape
        else -> false
    }
    val activity = LocalActivity.current
    DisposableEffect(landscape) {
        if (landscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            onDispose {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        } else {
            onDispose { }
        }
    }

    KhatKitTheme(
        style = provider.uiStyle,
        darkTheme = heizige.kk.khatkit.app.core.ui.hooks.rememberIsDarkTheme(),
    ) {
        when (val current = request) {
            is UiRequest.Sheet -> ActionSheet(
                title = current.title,
                url = current.url,
                isWeb = current.isWeb,
                options = current.options,
                headerActions = current.topBarActions.mapNotNull { it.toSheetAction() },
                overflowActions = current.overflowActions.mapNotNull { it.toSheetAction() },
                contentActions = current.contentActions,
                onAction = { event, values -> provider.selectSheetAction(event, values) },
                onDismiss = { provider.dismissUi() },
            )
            is UiRequest.Form -> KhatKitForm(
                title = current.title,
                items = current.items,
                options = current.options,
                onSubmit = { values -> provider.submitForm(values) },
                onCancel = { provider.dismissUi() },
            )

            is UiRequest.Confirm -> PrimaryBottomSheet(
                visible = true,
                title = current.title,
                imageVector = if (current.danger) warning else checkCircle,
                confirmText = stringResource(
                    if (current.danger) R.string.khatkit_confirm_danger else R.string.khatkit_confirm
                ),
                onConfirm = { provider.answerConfirm(true) },
                onDismiss = { provider.answerConfirm(false) },
                scrollable = true,
            ) { dismiss ->
                ConfirmSheetContent(
                    message = current.message,
                    onCancel = { dismiss() },
                )
            }

            is UiRequest.Show -> ShowCardSheet(
                card = current.card,
                options = current.options,
                onDismiss = { provider.dismissUi() },
            )

            null -> Unit
        }

        progress?.let { (ratio, label) ->
            Popup(alignment = Alignment.BottomCenter) {
                KedgeSurface(
                    modifier = Modifier
                        .systemBarsPadding()
                        .padding(bottom = 48.dp)
                        .widthIn(max = 320.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                        KedgeProgressIndicator(
                            progress = ratio,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 操作 Sheet（`ui.sheet` 与卡片网页登录 `web.openLogin` 共用）。
 *
 * - [headerActions] / [overflowActions] 由脚本在 action 里用 `icon` + `placement` 声明，
 *   渲染成 BottomSheet 最上方拖柄所在行右侧的图标按钮与溢出菜单（网页登录的「登录并保存 Cookie」走这里）；
 * - [contentActions] 仍是内容区底部的文字按钮，保持历史行为；
 * - [options] 里的 `desktop` / `user_agent` 决定网页渲染方式（桌面模式），
 *   宿主不内置任何 UA；点击动作时把当前 WebView URL 一起回传，顶栏保存按钮据此
 *   按当前域名存 Cookie。
 */
@Composable
private fun ActionSheet(
    title: String,
    url: String?,
    isWeb: Boolean,
    options: UiSheetOptions,
    headerActions: List<KhatKitSheetAction>,
    overflowActions: List<KhatKitSheetAction>,
    contentActions: List<SheetAction>,
    onAction: (String, Map<String, Any?>) -> Unit,
    onDismiss: () -> Unit,
) {
    // 顶栏图标按钮 / 溢出菜单 / 内容区按钮点击时都带上当前 WebView URL，
    // 网页登录据此按「当前域名」读取并保存 Cookie（登录后常已跳到别的域名）。
    val currentUrl = remember { mutableStateOf<String?>(null) }
    val fire: (KhatKitSheetAction) -> Unit = { action ->
        onAction(action.event, currentUrl.value?.let { mapOf("currentUrl" to it) } ?: emptyMap())
    }

    KhatKitSheet(
        title = title,
        imageVector = description,
        options = options,
        dismissText = stringResource(R.string.khatkit_close),
        headerActions = headerActions,
        overflowActions = overflowActions,
        overflowIcon = moreVert,
        onAction = fire,
        onDismiss = onDismiss,
    ) {
        val webState = if (!url.isNullOrBlank()) {
            rememberWebViewState(
                url = url,
                // 只对网页登录 Sheet 应用脚本声明的渲染开关，宿主不内置 UA
                settings = {
                    if (isWeb) applyScriptWebOptions(options.desktop, options.userAgent)
                },
            )
        } else {
            null
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            webState?.let { state ->
                val pageUrl = state.currentUrl
                // WebView 跳转后回传 URL 也要跟着变，所以每次重组都同步。
                SideEffect { currentUrl.value = pageUrl ?: url }
                WebView(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WEB_SHEET_HEIGHT),
                )
            }
            contentActions.forEach { action ->
                if (action.event.isNotBlank() && action.label.isNotBlank()) {
                    KedgeTextButton(
                        onClick = {
                            onAction(
                                action.event,
                                currentUrl.value?.let { mapOf("currentUrl" to it) } ?: emptyMap(),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(action.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmSheetContent(message: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            KedgeTextButton(onClick = onCancel) {
                Text(stringResource(R.string.khatkit_action_cancel))
            }
        }
    }
}

@Composable
private fun ShowCardSheet(
    card: Map<String, Any?>,
    options: UiSheetOptions,
    onDismiss: () -> Unit,
) {
    val title = card["title"]?.toString()?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.khatkit_show_result_title)
    val content = (card["markdown"] ?: card["text"] ?: card["content"])?.toString()

    KhatKitSheet(
        title = title,
        imageVector = description,
        options = options,
        dismissText = stringResource(R.string.khatkit_close),
        onDismiss = onDismiss,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = SHOW_CONTENT_MAX_WIDTH)) {
                if (content != null) {
                    MarkdownBlock(content)
                } else {
                    Text(card.entries.joinToString("\n") { "${it.key}: ${it.value}" })
                }
            }
        }
    }
}
