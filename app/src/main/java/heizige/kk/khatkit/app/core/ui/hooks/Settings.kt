package heizige.kk.khatkit.app.core.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.SettingsStore
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint

@Composable
fun rememberUserSettingsState(): State<Settings> {
    val store = rememberAppEntryPoint().settingsStore()
    return store.settingsFlow.collectAsStateWithLifecycle(
        initialValue = Settings.dummy(),
    )
}
