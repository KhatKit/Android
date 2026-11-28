package heizige.kk.khatkit.app.feature.search.data

import dagger.hilt.android.lifecycle.HiltViewModel
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchMode
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchSort
import kotlin.uuid.Uuid


enum class MessageSearchScope {
    CURRENT_ASSISTANT,
    ALL_ASSISTANTS,
}

internal data class SearchRequest(
    val query: String,
    val sort: MessageSearchSort,
    val scope: MessageSearchScope,
    val assistantId: Uuid?,
    val debounce: Boolean,
    val mode: MessageSearchMode,
)
