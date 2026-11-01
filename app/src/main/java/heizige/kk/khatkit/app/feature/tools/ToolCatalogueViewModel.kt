package heizige.kk.khatkit.app.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.feature.tools.data.ToolCatalogueRepository
import heizige.kk.khatkit.app.feature.tools.helper.isUserTool
import heizige.kk.khatkit.hub.CardIndexEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class ToolCatalogueState(
    val cards: List<CardIndexEntry> = emptyList(),
    val installed: Set<String> = emptySet(),
    val favorites: Set<String> = emptySet(),
    val recent: List<String> = emptyList(),
    val loading: Boolean = true,
    val complete: Boolean = true,
    val error: String? = null,
    val opening: String? = null,
)

@HiltViewModel
internal class ToolCatalogueViewModel @Inject constructor(
    private val repository: ToolCatalogueRepository,
    private val provider: KhatKitToolProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(ToolCatalogueState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null
    private var openJob: Job? = null
    private var market = emptyList<CardIndexEntry>()
    private var marketOffset = 0
    private var local = emptyList<CardIndexEntry>()

    init {
        viewModelScope.launch {
            val cache = repository.cached()
            market = cache.cards
            marketOffset = cache.nextOffset
            _state.update {
                it.copy(
                    favorites = repository.favorites(),
                    recent = repository.recent(),
                    complete = cache.complete,
                )
            }
            reloadInstalled()
            refresh()
            provider.cardCatalogueRevision.collect { reloadInstalled() }
        }
    }

    private suspend fun reloadInstalled() {
        local = repository.installed()
        publish()
    }

    private fun publish() {
        // 安装包的真实触发方式优先于旧 Hub 的缺省值；市场顺序和最新元数据保留。
        val installed = local.associateBy { it.name }
        val cards = (market + local.filter { item -> market.none { it.name == item.name } })
            .filter { installed[it.name]?.isUserTool() ?: it.isUserTool() }
            .map { entry ->
                val own = installed[entry.name]
                if (own != null && entry.displayName.values.isEmpty()) {
                    entry.copy(displayName = own.displayName, description = own.description)
                } else entry
            }
        _state.update { it.copy(cards = cards, installed = installed.keys) }
    }

    fun refresh() {
        if (refreshJob?.isActive == true || loadMoreJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val result = repository.load()
                market = result.cards
                marketOffset = result.nextOffset
                reloadInstalled()
                _state.update { it.copy(complete = result.complete) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(error = "无法更新工具目录，请检查网络后重试。") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun loadMore() {
        if (_state.value.loading || _state.value.complete || loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val result = repository.load(marketOffset, market)
                market = result.cards
                marketOffset = result.nextOffset
                reloadInstalled()
                _state.update { it.copy(complete = result.complete) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(error = "无法加载更多工具，请检查网络后重试。") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun toggleFavorite(name: String) {
        val favorites = _state.value.favorites.let { if (name in it) it - name else it + name }
        repository.saveFavorites(favorites)
        _state.update { it.copy(favorites = favorites) }
    }

    fun open(entry: CardIndexEntry, onReady: () -> Unit) {
        if (openJob?.isActive == true) return
        openJob = viewModelScope.launch {
            _state.update { it.copy(opening = entry.name, error = null) }
            try {
                check(entry.name in _state.value.installed || provider.installCard(entry)) { "install failed" }
                reloadInstalled()
                val recent = repository.recordOpen(entry.name)
                _state.update { it.copy(recent = recent) }
                onReady()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(error = "工具下载失败，请检查网络后重试。") }
            } finally {
                _state.update { it.copy(opening = null) }
            }
        }
    }
}
