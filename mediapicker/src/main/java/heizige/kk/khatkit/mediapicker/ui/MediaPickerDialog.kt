package heizige.kk.khatkit.mediapicker.ui

import android.os.Build
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import ui.feice.mochi.navigation.MochiNavDefaults
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.rememberMochiNavigator
import ui.feice.mochi.theme.MochiTheme

/** Fullscreen picker window with the same page transition and back gesture as Mochi routes. */
@Composable
internal fun MediaPickerDialog(
    onDismiss: () -> Unit,
    darkSystemBars: Boolean = MochiTheme.colorScheme.isDark,
    content: @Composable (onClose: () -> Unit) -> Unit,
) {
    val navigator = rememberMochiNavigator("closed")
    var opened by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    val close = { navigator.pop(); Unit }
    LaunchedEffect(navigator) {
        navigator.push("picker")
        opened = true
    }
    LaunchedEffect(opened, navigator.current, navigator.isTransitioning) {
        if (opened && navigator.current == "closed" && !navigator.isTransitioning) dismiss()
    }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val dialog = window?.callback as? ComponentDialog ?: return@Dialog
        SideEffect {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkSystemBars
                    isAppearanceLightNavigationBars = !darkSystemBars
                }
            }
        }
        // A page's inherited dispatcher belongs to the Activity. This window has its own
        // predictive-back input; bind handlers to it so gestures reach the picker host.
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dialog) {
            val spec = MochiNavDefaults.spec().copy(pushBackdrop = Color.Transparent)
            MochiNavHost(navigator, Modifier.fillMaxSize(), spec = spec, predictiveBack = true) { route ->
                if (route == "picker") content(close)
            }
        }
    }
}
