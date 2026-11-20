package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.LocalKkNavController
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.khatkit.CardPageStackPage
import heizige.kk.khatkit.app.core.ui.components.khatkit.KhatKitRequestPage
import heizige.kk.khatkit.app.core.ui.components.khatkit.isCardPage
import heizige.kk.khatkit.ui.UiRequest

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
    val active = nav?.current == Screen.CardInteraction(name)
    var shown by remember(name) { mutableStateOf<UiRequest?>(null) }
    SideEffect { if (active && owned != null) shown = owned }
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
        // Closing routes stay composed during predictive-back release. Keep their
        // last request even when the bridge has already published the parent page.
        (if (active) owned ?: shown else shown)?.let { current ->
            if (current is UiRequest.Screen && current.isCardPage()) {
                CardPageStackPage(if (active) owned else null, provider)
            } else {
                KhatKitRequestPage(current, provider)
            }
        }
    }
}
