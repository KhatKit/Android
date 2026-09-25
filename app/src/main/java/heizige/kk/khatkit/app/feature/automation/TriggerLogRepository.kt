package heizige.kk.khatkit.app.feature.automation

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId

/** 一次卡片触发的运行记录。 */
@Serializable
data class TriggerLogEntry(
    /** 运行开始时间（epoch millis） */
    val at: Long,
    val card: String,
    /** 事件类型（schedule / notification / ...） */
    val type: String,
    /** 事件负载摘要（如 `package=com.x, title=...`） */
    val payload: String = "",
    val ok: Boolean,
    /** 失败原因或成功备注（截断保存） */
    val message: String = "",
)

/** 由最近日志计算的紧凑统计（设置页展示）。 */
data class TriggerLogStats(
    val todayRuns: Int,
    val totalRuns: Int,
    val successRate: Int,
    val topCards: List<Pair<String, Int>>,
    val typeCounts: List<Pair<String, Int>>,
)

/** 统计最近日志：今日运行数、成功率、Top3 卡片、类型分布。 */
fun computeTriggerLogStats(
    entries: List<TriggerLogEntry>,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): TriggerLogStats {
    val todayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        .atStartOfDay(zone).toInstant().toEpochMilli()
    val todayRuns = entries.count { it.at >= todayStart }
    val okCount = entries.count { it.ok }
    val successRate = if (entries.isEmpty()) 0 else okCount * 100 / entries.size
    val topCards = entries.groupingBy { it.card }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(3)
        .map { it.key to it.value }
    val typeCounts = entries.groupingBy { it.type.ifBlank { "unknown" } }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }
    return TriggerLogStats(
        todayRuns = todayRuns,
        totalRuns = entries.size,
        successRate = successRate,
        topCards = topCards,
        typeCounts = typeCounts,
    )
}

/**
 * 触发执行日志：环形缓冲（最多 [MAX_ENTRIES] 条），SharedPreferences JSON 持久化。
 */
class TriggerLogRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    private val lock = Any()

    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<TriggerLogEntry>> = _entries.asStateFlow()

    fun append(entry: TriggerLogEntry) = synchronized(lock) {
        val next = (_entries.value + entry).takeLast(MAX_ENTRIES)
        persist(next)
        _entries.value = next
    }

    fun clear() = synchronized(lock) {
        persist(emptyList())
        _entries.value = emptyList()
    }

    private fun persist(list: List<TriggerLogEntry>) {
        runCatching { json.encodeToString(list) }
            .onSuccess { prefs.edit().putString(KEY_ENTRIES, it).apply() }
    }

    private fun read(): List<TriggerLogEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<TriggerLogEntry>>(raw) }
            .getOrDefault(emptyList())
            .takeLast(MAX_ENTRIES)
    }

    companion object {
        const val MAX_ENTRIES = 200

        private const val PREFS_NAME = "khatkit_trigger_log"
        private const val KEY_ENTRIES = "entries"
    }
}
