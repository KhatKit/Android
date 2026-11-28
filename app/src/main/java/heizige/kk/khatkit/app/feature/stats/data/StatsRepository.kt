package heizige.kk.khatkit.app.feature.stats.data

import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.db.dao.ConversationDAO
import heizige.kk.khatkit.app.core.data.db.dao.MessageNodeDAO
import heizige.kk.khatkit.app.core.data.db.dao.UsageRecordDAO
import heizige.kk.khatkit.app.core.data.db.dao.getMessageCountPerDay
import heizige.kk.khatkit.app.core.data.db.dao.getTokenStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** 统计页的只读聚合入口：SQL/时间窗/口径都收在这里，ViewModel 只持加载态。 */
internal class StatsRepository @Inject constructor(
    private val conversationDAO: ConversationDAO,
    private val messageNodeDAO: MessageNodeDAO,
    private val usageRecordDAO: UsageRecordDAO,
    private val settingsStore: SettingsRepository,
) {

    suspend fun loadUsage(range: UsageRange): UsageStats {
        val since = range.days?.let { days ->
            System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        }
        return withContext(Dispatchers.IO) {
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
    }

    suspend fun loadStats(): AppStats {
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

        return AppStats(
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
