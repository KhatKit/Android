package heizige.kk.khatkit.hub

import heizige.kk.khatkit.bridge.BridgeRegistry

/**
 * 三层漏斗的第一层（设计文档第 9 节）：
 * 硬过滤用结构化字段（纯布尔），语义召回交给服务端 embedding。
 */
object HubFilters {

    /**
     * 能力过滤：设备没有的 bridge 对应的卡片直接不可见。
     * elevated 卡片还要求设备具备 shizuku / root / accessibility。
     *
     * 声明了 `requires.plugins` 的卡片：只要设备支持插件运行时（Capability.PLUGIN），
     * 插件 bridge 视为「可按需获取」——执行前会自动下载并校验 sha256。
     */
    fun byCapability(
        cards: List<CardIndexEntry>,
        available: Set<String>,
    ): List<CardIndexEntry> = cards.filter { card ->
        val pluginNames = card.plugins.mapTo(mutableSetOf()) { it.name }
        val pluginsUsable = BridgeRegistry.CAPABILITY_PLUGIN in available
        val bridgesOk = card.bridges.all { bridge ->
            bridge in available || (pluginsUsable && bridge in pluginNames)
        }
        val privilegeOk = when (card.privilege) {
            "none" -> true
            "elevated" -> "shizuku" in available || "root" in available || "accessibility" in available
            else -> false
        }
        bridgesOk && privilegeOk
    }
}
