package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.LocalKkNavController
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.khatkit.KhatKitRequestPage
import heizige.kk.khatkit.ui.UiRequest
import ui.feice.mochi.theme.MochiTheme

/**
 * A route for card initiated UI. The card's request is rendered in the Mochi
 * navigation stack, so forms, results and web login never become bottom sheets.
 */
@Composable
internal fun CardInteractionPage(name: String) {
    val provider = rememberAppEntryPoint().khatKitToolProvider()
    val nav = LocalKkNavController.current
    val request by provider.uiRequest.collectAsStateWithLifecycle()
    val owned = request?.takeIf { provider.isPageHosted(it, setOf(name)) }
    DisposableEffect(provider, name) {
        provider.attachCardPage(name)
        onDispose {
            provider.detachCardPage(name)
            val current = provider.uiRequest.value
            if (current != null && provider.isPageHosted(current, setOf(name))) {
                provider.dismissUi()
            }
        }
    }
    LaunchedEffect(owned) {
        if (owned == null && nav?.current == Screen.CardInteraction(name)) {
            nav.popBackStack()
        }
    }
    Box(Modifier.fillMaxSize()) {
        owned?.let { current ->
            MochiTheme { KhatKitRequestPage(current, provider) }
        }
    }
}
