package heizige.kk.khatkit.hub

/**
 * 三层漏斗的第一、二层（设计文档第 9 节）：
 * 硬过滤用结构化字段（纯布尔），语义召回留给 embedding 层。
 */
object HubFilters {

    /**
     * 能力过滤：设备没有的 bridge 对应的卡片直接不可见。
     * elevated 卡片还要求设备具备 shizuku 或 root。
     */
    fun byCapability(
        cards: List<CardIndexEntry>,
        available: Set<String>,
    ): List<CardIndexEntry> = cards.filter { card ->
        val bridgesOk = card.bridges.all { it in available }
        val privilegeOk = when (card.privilege) {
            "none" -> true
            "elevated" -> "shizuku" in available || "root" in available || "accessibility" in available
            else -> false
        }
        bridgesOk && privilegeOk
    }

    /**
     * 标签预过滤：把候选压到 limit 以内再喂给 AI。
     * 只做浏览/初筛，不承担语义检索职责。
     */
    fun byTags(
        cards: List<CardIndexEntry>,
        query: String,
        limit: Int = 20,
    ): List<CardIndexEntry> {
        val hits = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (hits.isEmpty()) return cards.take(limit)
        return cards
            .map { card -> card to score(card, hits) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .take(limit)
            .map { (card, _) -> card }
    }

    private fun score(card: CardIndexEntry, hits: List<String>): Int {
        val haystack = buildString {
            append(card.name.lowercase()).append(' ')
            append(card.description.lowercase()).append(' ')
            card.tags?.let {
                append(it.domain).append(' ')
                append(it.action).append(' ')
                it.scene?.let { scene -> append(scene) }
            }
        }
        return hits.count { haystack.contains(it) }
    }
}
