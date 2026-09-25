package heizige.kk.khatkit.app.feature.download


import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadCenterViewModel @Inject constructor(
    private val provider: KhatKitToolProvider,
) : ViewModel() {

    val tasks = provider.downloadTasks

    init {
        viewModelScope.launch { provider.ensureDownloadsBound() }
    }

    fun pause(id: String) = viewModelScope.launch { provider.pauseDownload(id) }

    fun resume(id: String) = viewModelScope.launch { provider.resumeDownload(id) }

    fun cancel(id: String) = viewModelScope.launch { provider.cancelDownload(id) }

    fun remove(id: String) = viewModelScope.launch { provider.removeDownload(id) }
}
