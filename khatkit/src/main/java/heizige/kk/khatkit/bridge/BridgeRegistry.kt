package heizige.kk.khatkit.bridge

import heizige.kk.khatkit.engine.ScriptEngine

/**
 * 按设备当前能力决定挂哪些 bridge（设计文档 7.1 的能力协商）。
 *
 * 宿主启动时构造一次，缺失的 bridge 传 null。依赖缺失 bridge 的卡片
 * 对 AI 不可见（宿主应已从候选列表中过滤），这里再做一次兜底。
 */
class BridgeRegistry(
    private val tool: ToolBridge? = null,
    private val ui: UiBridge? = null,
    private val download: DownloadBridge? = null,
    private val storeProvider: ((cardName: String, quotaMb: Int) -> StoreBridge)? = null,
    private val shizuku: ShizukuBridge? = null,
    private val root: RootBridge? = null,
    private val accessibility: AccessibilityBridge? = null,
    private val imageToolbox: ImageToolboxBridge? = null,
) {
    /** 宿主侧访问下载管理器（下载中心）。 */
    fun downloadBridge(): DownloadBridge? = download

    /** 宿主侧 AI 设备工具：解锁屏幕时用 root shell（优先）或 Shizuku shell。 */
    fun rootBridge(): RootBridge? = root

    /** 宿主侧 AI 设备工具：无 root 时回退 Shizuku shell。 */
    fun shizukuBridge(): ShizukuBridge? = shizuku

    /** 当前设备具备的能力集合。 */
    fun availableBridges(): Set<String> = buildSet {
        if (tool != null) add("tool")
        if (ui != null) add("ui")
        if (download != null) add("download")
        if (storeProvider != null) add("store")
        if (shizuku != null) add("shizuku")
        if (root != null) add("root")
        if (accessibility != null) add("accessibility")
        if (imageToolbox != null) add("imageToolbox")
    }

    /** 卡片所需能力是否全部具备。 */
    fun supports(required: Collection<String>): Boolean = availableBridges().containsAll(required)

    /**
     * 把卡片声明过的 bridge 注入引擎。
     * @return 缺任一必需 bridge 时返回 false，卡片不可用。
     */
    fun inject(
        engine: ScriptEngine,
        cardName: String,
        required: Collection<String>,
        storeQuotaMb: Int = 50,
    ): Boolean {
        if (!supports(required)) return false
        required.forEach { name ->
            val impl: Any = when (name) {
                "tool" -> tool
                "ui" -> ui
                "download" -> download
                "store" -> storeProvider?.invoke(cardName, storeQuotaMb)
                "shizuku" -> shizuku
                "root" -> root
                "accessibility" -> accessibility
                "imageToolbox" -> imageToolbox
                else -> null
            } ?: return false
            engine.define(name, impl)
        }
        return true
    }
}
