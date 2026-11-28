package heizige.kk.khatkit.app.feature.search


import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.datastore.getCurrentAssistant
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchResult
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchMode
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchSort
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.R
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.feature.search.data.MessageSearchScope
import heizige.kk.khatkit.app.feature.search.data.SearchPreferencesRepository
import heizige.kk.khatkit.app.feature.search.data.SearchRequest

@HiltViewModel

class SearchViewModel @Inject constructor(
    private val context: Application,
    private val conversationRepo: ConversationRepository,
    private val settingsStore: SettingsRepository,
) : ViewModel() {
    private val searchRequests = Channel<SearchRequest>(Channel.CONFLATED)
    private var currentAssistantId: Uuid? = null

    var searchQuery by mutableStateOf("")
        private set
    var searchScope by mutableStateOf(MessageSearchScope.CURRENT_ASSISTANT)
        private set
    var mode by mutableStateOf(MessageSearchMode.KEYWORDS)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    var sortOrder by mutableStateOf(SearchPreferencesRepository.readSortOrder(context))
        private set
    var results by mutableStateOf<List<MessageSearchResult>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRebuilding by mutableStateOf(false)
        private set
    var rebuildProgress by mutableStateOf(0 to 0)
        private set

    init {
        viewModelScope.launch {
            searchRequests.receiveAsFlow().collectLatest { request -> performSearch(request) }
        }
        viewModelScope.launch {
            settingsStore.settingsFlow
                .map { it.getCurrentAssistant().id }
                .distinctUntilChanged()
                .collect { assistantId ->
                    currentAssistantId = assistantId
                    if (searchScope == MessageSearchScope.CURRENT_ASSISTANT) {
                        search()
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        searchQuery = query
        requestSearch(debounce = true)
    }

    fun onScopeChange(scope: MessageSearchScope) {
        if (searchScope == scope) return
        searchScope = scope
        search()
    }

    fun onModeChange(mode: MessageSearchMode) {
        if (this.mode == mode) return
        this.mode = mode
        search()
    }

    fun onSortChange(sort: MessageSearchSort) {
        if (sortOrder == sort) return
        sortOrder = sort
        SearchPreferencesRepository.writeSortOrder(context, sort)
        search()
    }

    fun search() {
        requestSearch()
    }

    private fun requestSearch(debounce: Boolean = false) {
        val assistantId = when (searchScope) {
            MessageSearchScope.CURRENT_ASSISTANT -> currentAssistantId
            MessageSearchScope.ALL_ASSISTANTS -> null
        }
        searchRequests.trySend(
            SearchRequest(
                query = searchQuery,
                sort = sortOrder,
                scope = searchScope,
                assistantId = assistantId,
                debounce = debounce,
                mode = mode,
            )
        )
    }

    fun rebuildIndex() {
        viewModelScope.launch {
            isRebuilding = true
            rebuildProgress = 0 to 0
            try {
                conversationRepo.rebuildAllIndexes { current, total ->
                    rebuildProgress = current to total
                }
            } finally {
                isRebuilding = false
            }
        }
    }

    private suspend fun performSearch(request: SearchRequest) {
        results = emptyList()
        searchError = null
        if (request.query.isBlank() ||
            (request.scope == MessageSearchScope.CURRENT_ASSISTANT && request.assistantId == null)
        ) {
            return
        }
        isLoading = true
        try {
            if (request.debounce) delay(300L)
            val sort = if (request.mode != MessageSearchMode.KEYWORDS && request.sort == MessageSearchSort.RELEVANCE) {
                MessageSearchSort.NEWEST_FIRST
            } else request.sort
            results = conversationRepo.searchMessages(request.query, sort, request.assistantId, request.mode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            searchError = if (request.mode == MessageSearchMode.KEYWORDS) context.getString(R.string.search_page_error_failed) else e.message ?: context.getString(R.string.search_page_error_failed)
        } catch (_: Exception) {
            searchError = context.getString(R.string.search_page_error_failed)
        } finally {
            isLoading = false
        }
    }
}
