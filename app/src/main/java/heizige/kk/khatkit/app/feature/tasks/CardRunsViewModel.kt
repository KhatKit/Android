package heizige.kk.khatkit.app.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.core.data.ai.tools.jsonToMap
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.data.db.dao.CardRunDAO
import heizige.kk.khatkit.app.core.data.db.dao.CardArtifactDAO
import heizige.kk.khatkit.app.core.data.db.entity.CardArtifactEntity
import heizige.kk.khatkit.app.core.data.db.entity.CardRunEntity
import heizige.kk.khatkit.uikit.CardRunResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CardRunsViewModel @Inject constructor(
    private val dao: CardRunDAO,
    private val artifactDao: CardArtifactDAO,
    private val provider: KhatKitToolProvider,
    private val appScope: AppScope,
) : ViewModel() {
    val runs: StateFlow<List<CardRunEntity>> = dao.observeRecent(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artifacts: StateFlow<List<CardArtifactEntity>> = artifactDao.observeForRecentRuns(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rerun(run: CardRunEntity, onResult: (CardRunResult) -> Unit = {}) {
        val args = run.argsTemplate
            .takeIf { it.isNotBlank() && it != "{}" }
            ?.let { raw ->
                runCatching { kotlinx.serialization.json.Json.parseToJsonElement(raw) }
                    .getOrNull()
                    ?.let { jsonToMap(it) }
            }
            .orEmpty()
        appScope.launch {
            onResult(provider.runCard(run.cardName, args, run.id))
        }
    }
}
