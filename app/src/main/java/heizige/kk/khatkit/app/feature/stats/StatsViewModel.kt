package heizige.kk.khatkit.app.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import heizige.kk.khatkit.app.core.data.db.dao.ConversationDAO
import heizige.kk.khatkit.app.core.data.db.dao.UsageByModel
import heizige.kk.khatkit.app.core.data.db.dao.UsageBySource
import heizige.kk.khatkit.app.core.data.db.dao.UsageRecordDAO
import heizige.kk.khatkit.app.core.data.db.dao.UsageTrendPoint
import heizige.kk.khatkit.app.core.data.db.dao.MessageNodeDAO
import heizige.kk.khatkit.app.core.data.db.dao.getMessageCountPerDay
import heizige.kk.khatkit.app.core.data.db.dao.getTokenStats
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import heizige.kk.khatkit.app.feature.stats.data.UsageRange
import heizige.kk.khatkit.app.feature.stats.data.UsageStats
import heizige.kk.khatkit.app.feature.stats.data.AppStats
import heizige.kk.khatkit.app.feature.stats.data.StatsRepository
@HiltViewModel

internal class StatsViewModel @Inject constructor(
    private val repository: StatsRepository,
) : ViewModel() {

    private val _stats = MutableStateFlow(AppStats())
    val stats = _stats.asStateFlow()

    private val _range = MutableStateFlow(UsageRange.WEEK)
    val range = _range.asStateFlow()

    private val _usage = MutableStateFlow(UsageStats())
    val usage = _usage.asStateFlow()

    init {
        viewModelScope.launch { loadStats() }
        viewModelScope.launch { loadUsage(_range.value) }
    }

    /** 切时间窗口：只重查用量聚合，概览卡片不动。 */
    fun selectRange(range: UsageRange) {
        if (_range.value == range) return
        _range.value = range
        _usage.value = _usage.value.copy(isLoading = true)
        viewModelScope.launch { loadUsage(range) }
    }

    private suspend fun loadUsage(range: UsageRange) {
        val result = repository.loadUsage(range)
        // 区间可能在这期间被切走：只写回当前选中的那一次结果
        if (_range.value == range) _usage.value = result
    }

    private suspend fun loadStats() {
        _stats.value = repository.loadStats()
    }
}
