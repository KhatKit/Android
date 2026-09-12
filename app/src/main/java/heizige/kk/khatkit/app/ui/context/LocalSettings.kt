package heizige.kk.khatkit.app.ui.context

import androidx.compose.runtime.staticCompositionLocalOf
import heizige.kk.khatkit.app.data.datastore.Settings

val LocalSettings = staticCompositionLocalOf<Settings> {
    error("No SettingsStore provided")
}
