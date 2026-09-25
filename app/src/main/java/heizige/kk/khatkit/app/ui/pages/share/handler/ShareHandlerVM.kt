package heizige.kk.khatkit.app.ui.pages.share.handler


import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.SettingsStore
import kotlin.uuid.Uuid

@HiltViewModel(assistedFactory = ShareHandlerVM.Factory::class)
class ShareHandlerVM @AssistedInject constructor(
    @Assisted text: String,
    private val settingsStore: SettingsStore
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(text: String): ShareHandlerVM
    }
    val shareText = checkNotNull(text)
    val settings = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    suspend fun updateAssistant(assistantId: Uuid) {
        settingsStore.updateAssistant(assistantId)
    }
}
