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

/** 统计页的时间窗口（UI-5）：7/30/90 天，或全部。 */
enum class UsageRange(val days: Int?) {
    WEEK(7),
    MONTH(30),
    QUARTER(90),
    ALL(null),
}

/** 用量聚合（来自 usage_record 流水，US2 的 SQL 侧 GROUP BY）。 */
data class UsageStats(
    val isLoading: Boolean = true,
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val cachedTokens: Long = 0L,
    val requests: Long = 0L,
    val trend: List<UsageTrendPoint> = emptyList(),
    val byModel: List<UsageByModel> = emptyList(),
    val bySource: List<UsageBySource> = emptyList(),
) {
    val totalTokens: Long get() = promptTokens + completionTokens
}

data class AppStats(
    val isLoading: Boolean = true,
    val totalConversations: Int = 0,
    val totalMessages: Int = 0,
    val totalPromptTokens: Long = 0L,
    val totalCompletionTokens: Long = 0L,
    val totalCachedTokens: Long = 0L,
    val conversationsPerDay: Map<LocalDate, Int> = emptyMap(),
    val launchCount: Int = 0,
)

@HiltViewModel
class StatsVM @Inject constructor(
    private val conversationDAO: ConversationDAO,
    private val messageNodeDAO: MessageNodeDAO,
    private val usageRecordDAO: UsageRecordDAO,
    private val settingsStore: SettingsRepository,
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
        val since = range.days?.let { days ->
            System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        }
        val result = withContext(Dispatchers.IO) {
            val trend = if (since == null) {
                // 「全部」按周聚合，否则几百天的日桶在手机上画不下
                usageRecordDAO.trendByWeek(0L)
            } else {
                usageRecordDAO.trendByDay(since)
            }
            val byModel = usageRecordDAO.byModel(since ?: 0L)
            val bySource = usageRecordDAO.bySource(since ?: 0L)
            UsageStats(
                isLoading = false,
                promptTokens = byModel.sumOf { it.promptTokens },
                completionTokens = byModel.sumOf { it.completionTokens },
                cachedTokens = byModel.sumOf { it.cachedTokens },
                requests = byModel.sumOf { it.requests },
                trend = trend,
                byModel = byModel,
                bySource = bySource,
            )
        }
        // 区间可能在这期间被切走：只写回当前选中的那一次结果
        if (_range.value == range) _usage.value = result
    }

    private suspend fun loadStats() {
        delay(50)

        val today = LocalDate.now()

        // 热力图起始日期（52 周前的周日），格式 "yyyy-MM-dd" 直接与 JSON 中的 LocalDateTime 前缀比较
        val startDate = today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            .minusWeeks(52)
            .toString()

        // 基于用户消息的 createdAt 统计每日活跃消息数，SQLite 侧 GROUP BY，返回 ≤371 行
        val conversationsPerDay = withContext(Dispatchers.IO) {
            messageNodeDAO
                .getMessageCountPerDay(startDate)
                .mapNotNull { entry ->
                    runCatching { LocalDate.parse(entry.day) to entry.count }.getOrNull()
                }
                .toMap()
        }

        val totalConversations = conversationDAO.countAll()

        // json_each() + json_extract() 在 SQLite 侧聚合，不再加载完整 JSON 到 Kotlin
        val tokenStats = messageNodeDAO.getTokenStats()

        val launchCount = settingsStore.settingsFlow.value.launchCount

        _stats.value = AppStats(
            isLoading = false,
            totalConversations = totalConversations,
            totalMessages = tokenStats.totalMessages,
            totalPromptTokens = tokenStats.promptTokens,
            totalCompletionTokens = tokenStats.completionTokens,
            totalCachedTokens = tokenStats.cachedTokens,
            conversationsPerDay = conversationsPerDay,
            launchCount = launchCount,
        )
    }
}
