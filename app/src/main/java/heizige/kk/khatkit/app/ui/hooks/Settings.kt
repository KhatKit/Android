package heizige.kk.khatkit.app.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.data.datastore.Settings
import heizige.kk.khatkit.app.data.datastore.SettingsStore
import heizige.kk.khatkit.app.di.rememberAppEntryPoint

@Composable
fun rememberUserSettingsState(): State<Settings> {
    val store = rememberAppEntryPoint().settingsStore()
    return store.settingsFlow.collectAsStateWithLifecycle(
        initialValue = Settings.dummy(),
    )
}
