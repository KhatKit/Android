package heizige.kk.khatkit.app.feature.extensions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.icons.settings
import heizige.kk.khatkit.app.core.ui.theme.ColorMode
import heizige.kk.khatkit.app.core.ui.theme.KhatKitTheme
import heizige.kk.khatkit.app.feature.ui.shell.MochiSettingsPageScaffold
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import heizige.kk.khatkit.uikit.mochi.kkBarButtonColors
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.theme.MochiTheme

private const val TERMINAL_WIDGET_NAME = "__khatkit_terminal"

/**
 * 独立终端插件页面。终端绘制、输入、选区和软键盘全部交给 Termux TerminalView，
 * 宿主只负责页面壳和插件设置，不再维护另一套 Compose 终端模拟器。
 */
@Composable
fun TerminalPluginPage() {
    val context = LocalContext.current
    val provider = rememberAppEntryPoint().khatKitToolProvider()
    val textSize = with(LocalDensity.current) { 12.sp.roundToPx() }
    var showSettings by remember { mutableStateOf(false) }
    var shortcutEnabled by remember { mutableStateOf(provider.widgetEnabled(TERMINAL_WIDGET_NAME)) }
    val terminal = remember(context) { StandaloneTermuxSession(context) }

    DisposableEffect(terminal) {
        onDispose { terminal.close() }
    }

    KhatKitTheme(colorMode = ColorMode.DARK) {
        MochiSettingsPageScaffold(
            title = TerminalPluginInfo.DISPLAY_NAME,
            actions = {
                MochiBarIconButton(
                    onClick = { showSettings = true },
                    navId = "terminal.settings",
                    colors = kkBarButtonColors(),
                ) {
                    Icon(settings, contentDescription = "终端设置")
                }
            },
        ) { innerPadding ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .windowInsetsPadding(WindowInsets.imeAnimationTarget),
                factory = { viewContext ->
                    TerminalView(viewContext, null).apply {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        setTextSize(textSize)
                        setTypeface(terminal.typeface)
                        setTerminalViewClient(terminal)
                        attachSession(terminal.session)
                        terminal.view = this
                        post { terminal.focusAndShowKeyboard() }
                    }
                },
                update = { view ->
                    view.isFocusable = true
                    view.isFocusableInTouchMode = true
                    view.setTextSize(textSize)
                    view.setTypeface(terminal.typeface)
                    view.setTerminalViewClient(terminal)
                    terminal.view = view
                    view.attachSession(terminal.session)
                    view.onScreenUpdated()
                },
            )
        }

        PrimaryBottomSheet(
            visible = showSettings,
            title = "终端设置",
            imageVector = settings,
            onDismiss = { showSettings = false },
        ) { _ ->
            MochiOptionItem(
                onClick = {
                    val next = !shortcutEnabled
                    shortcutEnabled = next
                    provider.setWidgetEnabled(TERMINAL_WIDGET_NAME, next)
                },
                leadingContent = { Icon(settings, null) },
                titleContent = { Text("欢迎页快捷方式") },
                supportingContent = {
                    Text(
                        if (shortcutEnabled) "已在聊天欢迎页显示终端入口"
                        else "在聊天欢迎页添加一个打开终端的入口",
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    KkSwitch(
                        checked = shortcutEnabled,
                        onCheckedChange = {
                            shortcutEnabled = it
                            provider.setWidgetEnabled(TERMINAL_WIDGET_NAME, it)
                        },
                    )
                },
            )
        }
    }
}

/** Termux session adapter used by the plugin page. */
private class StandaloneTermuxSession(private val context: Context) :
    TerminalSessionClient,
    TerminalViewClient {
    val typeface: Typeface =
        ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
    val session = TerminalSession(
        "/system/bin/sh",
        context.filesDir.absolutePath,
        arrayOf("-i"),
        arrayOf(
            "HOME=${context.filesDir.absolutePath}",
            "PATH=/system/bin:/system/xbin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "PS1=\\$ ",
            "PS2=> ",
        ),
        2_000,
        this,
    )
    var view: TerminalView? = null

    fun close() {
        view = null
        session.finishIfRunning()
    }

    fun focusAndShowKeyboard() {
        val terminalView = view ?: return
        terminalView.requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        view?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) = Unit

    override fun onSessionFinished(finishedSession: TerminalSession) {
        view?.onScreenUpdated()
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?: return
        val bytes = text.toByteArray()
        session.write(bytes, 0, bytes.size)
    }

    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) { view?.invalidate() }
    override fun onTerminalCursorStateChange(state: Boolean) { view?.invalidate() }
    override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
    override fun onScale(scale: Float): Float = scale.coerceIn(0.8f, 1.25f)
    override fun onSingleTapUp(e: MotionEvent) = focusAndShowKeyboard()
    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession) = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
    override fun onLongPress(event: MotionEvent) = false
    override fun readControlKey() = false
    override fun readAltKey() = false
    override fun readShiftKey() = false
    override fun readFnKey() = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession) = false
    override fun onEmulatorSet() = Unit
    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.d(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "Terminal error", e) }
}
