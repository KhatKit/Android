package heizige.kk.khatkit.app.core.data.repository

import heizige.kk.khatkit.app.core.data.db.entity.MemoryChunkEntity
import kotlin.text.RegexOption

/**
 * 混合检索融合（纯函数，便于单测）。
 *
 * 参考 clawdiney 的 RRF：`score(item) = Σ_lists 1/(k + rank)`，k=60。
 * 时间衰减作为第四路排序列表参与融合，另按 last_hit_at 做轻度使用热度加成。
 */
object MemoryRetrievalEngine {
    const val RRF_K = 60
    const val DEFAULT_LIMIT = 8
    const val FETCH_MULTIPLIER = 3

    /** 时间衰减半衰期（天）。超过后排序权重减半。 */
    const val HALF_LIFE_DAYS = 30.0

    data class Hit(
        val chunkId: Int,
        val rrfScore: Double,
        val signals: Set<String>,
    )

    /**
     * 将多路已排序 chunk id 列表做 RRF 融合。
     * `rankedLists` 每个元素为按相关度降序的 chunk id 列表（越靠前越相关）。
     */
    fun rrfFuse(rankedLists: List<List<Int>>, k: Int = RRF_K): List<Hit> {
        val scores = LinkedHashMap<Int, Double>()
        val signals = HashMap<Int, MutableSet<String>>()
        val signalNames = listOf("fts", "vector", "graph", "recent")
        rankedLists.forEachIndexed { listIndex, list ->
            val signal = signalNames.getOrElse(listIndex) { "s$listIndex" }
            list.forEachIndexed { rank, id ->
                scores[id] = (scores[id] ?: 0.0) + 1.0 / (k + rank + 1)
                signals.getOrPut(id) { mutableSetOf() }.add(signal)
            }
        }
        return scores.entries
            .sortedByDescending { it.value }
            .map { Hit(it.key, it.value, signals[it.key].orEmpty()) }
    }

    /** 时间衰减因子：越新越高，半衰期 [HALF_LIFE_DAYS] 天。 */
    fun recencyFactor(extractedAt: Long, now: Long, halfLifeDays: Double = HALF_LIFE_DAYS): Double {
        if (extractedAt <= 0L) return 0.5
        val ageDays = (now - extractedAt).coerceAtLeast(0L) / 86_400_000.0
        return Math.pow(0.5, ageDays / halfLifeDays)
    }

    /**
     * 按时间衰减对 chunk 排序（作为 RRF 第四路）：`extracted_at * decay` 降序。
     * 同时给最近命中（last_hit_at）少量加成，模拟"用过的记忆更活跃"。
     */
    fun rankByRecency(chunks: List<MemoryChunkEntity>, now: Long): List<Int> {
        return chunks.sortedByDescending { chunk ->
            val recency = chunk.extractedAt * recencyFactor(chunk.extractedAt, now)
            val usageBoost = if (chunk.lastHitAt > 0) 1.0 + 0.1 * recencyFactor(chunk.lastHitAt, now) else 1.0
            recency * usageBoost
        }.map { it.id }
    }

    /** FTS5 查询词清洗：分词后用 OR 连接，避免语法字符打断 MATCH。 */
    fun toFtsQuery(query: String): String {
        val tokens = Regex("[\\p{L}\\p{N}_]+").findAll(query.trim())
            .map { it.value.trim().replace("\"", "\"\"") }
            .filter { it.isNotBlank() }
            .toList()
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" OR ") { "\"$it\"" }
    }
}
