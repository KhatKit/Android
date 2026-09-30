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
) {
    /**
     * 运行期动态注册的 bridge（原生依赖包加载后注册，如 imageToolbox）。
     * 依赖实现不随应用编译，见 [heizige.kk.khatkit.dependency.DependencyManager]。
     */
    private val dynamicBridges = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /** 宿主侧访问下载管理器（下载中心）。 */
    fun downloadBridge(): DownloadBridge? = download

    /** 注册/替换一个动态 bridge（依赖名即 bridge 名）。 */
    fun registerDynamic(name: String, bridge: Any) {
        dynamicBridges[name] = bridge
    }

    /** 动态 bridge 是否已注册。 */
    fun dynamicBridge(name: String): Any? = dynamicBridges[name]

    /** 宿主侧 AI 设备工具：解锁屏幕时用 root shell（优先）或 Shizuku shell。 */
    fun rootBridge(): RootBridge? = root

    /** 宿主侧 AI 设备工具：无 root 时回退 Shizuku shell。 */
    fun shizukuBridge(): ShizukuBridge? = shizuku

    /**
     * 当前设备具备的能力集合。
     *
     * 除编译进应用的 bridge 外，还包含：
     *  - 运行期加载的原生依赖包 bridge 名；
     *  - 常量 [CAPABILITY_DEPENDENCY]：表示设备支持按需下载 Dex 依赖包，
     *    Hub 据此让声明了 `requires.dependencies` 的卡片对 AI 可见（执行前会自动下载）。
     */
    fun availableBridges(): Set<String> = buildSet {
        if (tool != null) add("tool")
        if (ui != null) add("ui")
        if (download != null) add("download")
        if (storeProvider != null) add("store")
        if (shizuku != null) add("shizuku")
        if (root != null) add("root")
        if (accessibility != null) add("accessibility")
        addAll(dynamicBridges.keys)
        add(CAPABILITY_DEPENDENCY)
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
                else -> dynamicBridges[name]
            } ?: return false
            engine.define(name, impl)
        }
        return true
    }

    companion object {
        /** 通用能力：设备支持下载并加载原生 Dex 依赖包（所有 KhatKit 构建均满足）。 */
        const val CAPABILITY_DEPENDENCY = "dependency"
    }
}
