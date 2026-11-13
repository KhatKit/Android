package heizige.kk.khatkit.app.feature.extensions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.johnan.terminal.core.TerminalColorScheme
import com.johnan.terminal.ui.ModifierKeyState
import com.johnan.terminal.ui.TerminalRenderer
import com.johnan.terminal.ui.terminalUiConfig
import heizige.kk.khatkit.app.core.ui.theme.ColorMode
import heizige.kk.khatkit.app.core.ui.theme.KhatKitTheme
import heizige.kk.khatkit.app.feature.ui.shell.MochiSettingsPageScaffold
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup

/** 独立终端插件：会话属于插件本身，不依赖工作区或工作区 rootfs。 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun TerminalPluginPage() {
    val context = LocalContext.current
    val terminal = remember(context) { StandaloneShellTerminal(context) }

    DisposableEffect(terminal) {
        onDispose { terminal.close() }
    }

    KhatKitTheme(colorMode = ColorMode.DARK) {
        MochiSettingsPageScaffold(title = TerminalPluginInfo.DISPLAY_NAME) { innerPadding ->
            StandaloneTerminalContent(
                terminal = terminal,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .windowInsetsPadding(WindowInsets.imeAnimationTarget),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StandaloneTerminalContent(
    terminal: StandaloneShellTerminal,
    modifier: Modifier = Modifier,
) {
    val terminalState by terminal.emulator.screenState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    var showKeyboardSignal by remember { mutableIntStateOf(0) }
    var modifierState by remember { mutableStateOf(ModifierKeyState()) }
    var controlDown by remember { mutableStateOf(false) }
    var altDown by remember { mutableStateOf(false) }

    val uiConfig = remember {
        terminalUiConfig {
            typography {
                fontSize = 13.sp
                fontFamily = FontFamily.SansSerif
                letterSpacing = 0.sp
            }
            cursor {
                blink = true
                overrideColor = Color(0xFFFFC857)
            }
            colorScheme = TerminalColorScheme.DEFAULT
            gestures {
                enableTouchToFocus = true
                enableSelection = true
                touchScrollSendsWheelOnly = true
                wheelScrollMultiplier = 1.2f
            }
        }
    }

    Column(modifier = modifier) {
        TerminalRenderer(
            terminalState = terminalState,
            config = uiConfig,
            onInput = terminal::write,
            onArrowKey = { arrow, shift -> terminal.writeArrow(arrow, shift) },
            onLog = {},
            modifierKeyState = modifierState,
            onModifierStateChange = { modifierState = it },
            focusRequester = focusRequester,
            showKeyboardSignal = showKeyboardSignal,
            onKeyboardToggleAvailable = { requestFocus ->
                focusRequester.requestFocus()
                requestFocus()
            },
            onResize = terminal::resize,
            naturalTextLayout = false,
            onCellMeasured = { width, height ->
                terminal.emulator.setCellDimensionsPx(width.toInt(), height.toInt())
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        TerminalExtraKeysBar(
            controlDown = controlDown,
            altDown = altDown,
            onControlToggle = {
                controlDown = !controlDown
                modifierState = modifierState.copy(ctrlPressed = controlDown)
            },
            onAltToggle = {
                altDown = !altDown
                modifierState = modifierState.copy(altPressed = altDown)
            },
            onShowKeyboard = { showKeyboardSignal++ },
            onSendText = terminal::write,
        )
    }
}

@Composable
private fun TerminalExtraKeysBar(
    controlDown: Boolean,
    altDown: Boolean,
    onControlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onShowKeyboard: () -> Unit,
    onSendText: (String) -> Unit,
) {
    MochiChipGroup(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        spacing = 6.dp,
    ) {
        listOf(
            "键盘" to null,
            "ESC" to "\u001B",
            "TAB" to "\t",
            "-" to "-",
            "/" to "/",
            "|" to "|",
            "←" to "\u001B[D",
            "↓" to "\u001B[B",
            "↑" to "\u001B[A",
            "→" to "\u001B[C",
            "HOME" to "\u001B[H",
            "END" to "\u001B[F",
        ).forEach { (label, value) ->
            MochiChip(
                label = label,
                selected = false,
                onClick = { if (value == null) onShowKeyboard() else onSendText(value) },
            )
        }
        MochiChip(label = "CTRL", selected = controlDown, onClick = onControlToggle)
        MochiChip(label = "ALT", selected = altDown, onClick = onAltToggle)
    }
}
