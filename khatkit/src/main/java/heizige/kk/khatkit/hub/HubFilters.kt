package heizige.kk.khatkit.hub

/**
 * 三层漏斗的第一层（设计文档第 9 节）：
 * 硬过滤用结构化字段（纯布尔），语义召回交给服务端 embedding。
 */
object HubFilters {

    /**
     * 能力过滤：设备没有的 bridge 对应的卡片直接不可见。
     * elevated 卡片还要求设备具备 shizuku / root / accessibility。
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
}
