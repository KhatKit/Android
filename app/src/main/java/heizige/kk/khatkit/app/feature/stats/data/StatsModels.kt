package heizige.kk.khatkit.app.feature.stats.data

import heizige.kk.khatkit.app.core.data.db.dao.UsageByModel
import heizige.kk.khatkit.app.core.data.db.dao.UsageBySource
import heizige.kk.khatkit.app.core.data.db.dao.UsageTrendPoint
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel


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
