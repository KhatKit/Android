package heizige.kk.khatkit.app.core.ui.context

import androidx.compose.runtime.staticCompositionLocalOf
import heizige.kk.khatkit.app.core.data.datastore.Settings

val LocalSettings = staticCompositionLocalOf<Settings> {
    error("No SettingsRepository provided")
}
