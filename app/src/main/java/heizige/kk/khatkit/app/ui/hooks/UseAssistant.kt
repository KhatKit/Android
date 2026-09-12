package heizige.kk.khatkit.app.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import heizige.kk.khatkit.app.data.datastore.Settings
import heizige.kk.khatkit.app.data.datastore.getCurrentAssistant
import heizige.kk.khatkit.app.data.model.Assistant

@Composable
fun rememberAssistantState(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit
): AssistantState {
    return remember(settings, onUpdateSettings) {
        AssistantState(settings, onUpdateSettings)
    }
}

class AssistantState(
    private val settings: Settings,
    private val onUpdateSettings: (Settings) -> Unit
) {
    private var _currentAssistant by mutableStateOf(
        settings.getCurrentAssistant()
    )
    val currentAssistant get() = _currentAssistant

    fun setSelectAssistant(assistant: Assistant) {
        onUpdateSettings(
            settings.copy(
                assistantId = assistant.id
            )
        )
    }
}
