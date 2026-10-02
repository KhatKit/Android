package heizige.kk.khatkit.bridge

import heizige.kk.khatkit.engine.ScriptEngine
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.bridge.impl.CardAiBridge
import heizige.kk.khatkit.bridge.impl.EmbeddingEngine
import heizige.kk.khatkit.bridge.impl.StoreRequest
import heizige.kk.khatkit.bridge.impl.ScopedFsBridgeImpl
import heizige.kk.khatkit.ui.MediaPickerHost

/**
 * 按设备当前能力决定挂哪些 bridge（设计文档 7.1 的能力协商）。
 *
 * 宿主启动时构造一次，缺失的 bridge 传 null。依赖缺失 bridge 的卡片
 * 对 AI 不可见（宿主应已从候选列表中过滤），这里再做一次兜底。
 */
class BridgeRegistry(
    private val tool: ToolBridge? = null,
    private val net: NetBridge? = null,
    private val fs: FsBridge? = null,
    private val json: JsonBridge? = null,
    private val crypto: CryptoBridge? = null,
    private val time: TimeBridge? = null,
    private val host: HostBridge? = null,
    private val system: SystemBridge? = null,
    private val ai: AiBridge? = null,
    private val mediaPicker: MediaPickerBridge? = null,
    private val ui: UiBridge? = null,
    private val web: WebBridge? = null,
    private val download: DownloadBridge? = null,
    private val storeProvider: ((request: StoreRequest) -> StoreBridge)? = null,
    private val embeddingEngine: EmbeddingEngine? = null,
    private val shizuku: ShizukuBridge? = null,
    private val root: RootBridge? = null,
    private val accessibility: AccessibilityBridge? = null,
    private val schedule: ScheduleBridge? = null,
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
        if (net != null) add("net")
        if (fs != null) add("fs")
        if (json != null) add("json")
        if (crypto != null) add("crypto")
        if (time != null) add("time")
        if (host != null) add("host")
        if (system != null) add("system")
        if (ai != null) add("ai")
        if (mediaPicker != null) add("mediaPicker")
        if (ui != null) add("ui")
        if (web != null) add("web")
        if (download != null) add("download")
        if (storeProvider != null) add("store")
        if (shizuku != null) add("shizuku")
        if (root != null) add("root")
        if (accessibility != null) add("accessibility")
        if (schedule != null) add("schedule")
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
        manifest: CardManifest,
        approvalGate: ApprovalGate? = null,
        deadlineAt: Long = 0L,
    ): Boolean {
        val required = manifest.requiredBridges
        if (!supports(required)) return false
        val context = BridgeContext(
            cardName = manifest.name,
            engine = manifest.engine,
            quotaMb = manifest.store.quotaMb,
            networkAllow = manifest.network.allow.map { it.lowercase().substringBefore(':') }.toSet(),
            permissions = manifest.permissions.methods,
            approvalGate = approvalGate,
            deadlineAt = deadlineAt,
        )
        required.forEach { name ->
            val impl: Any = when (name) {
                "tool" -> tool
                "net" -> net
                "fs" -> when (fs) {
                    is ScopedFsBridgeImpl -> ScopedFsBridgeImpl(
                        context = fs.context(),
                        cardName = manifest.name,
                        roots = (manifest.permissions.fsRead + manifest.permissions.fsWrite).toSet(),
                        approvalGate = approvalGate,
                        grants = context.grants,
                    )
                    else -> fs
                }
                "json" -> json
                "crypto" -> crypto
                "time" -> time
                "host" -> host
                "system" -> system
                "ai" -> when (val a = ai) {
                    is CardAiBridge -> a.forRun(context)
                    else -> a
                }
                "mediaPicker" -> when (val mp = mediaPicker) {
                    is MediaPickerHost -> mp.forCard(manifest.name)
                    else -> mp
                }
                "ui" -> ui?.let { delegate ->
                    val imageFs = (fs as? ScopedFsBridgeImpl)?.let {
                        ScopedFsBridgeImpl(it.context(), manifest.name,
                            manifest.permissions.fsRead.toSet(), approvalGate, context.grants)
                    }
                    UiImageAccessBridge(delegate) { path ->
                        imageFs?.imageSource(path) ?: error("Image.src: file access unavailable")
                    }
                }
                "web" -> web
                "download" -> download
                "store" -> storeProvider?.invoke(
                    StoreRequest(
                        cardName = manifest.name,
                        quotaMb = manifest.store.quotaMb,
                        permissions = context.permissions,
                        approvalGate = approvalGate,
                        deadline = context.deadline,
                        grants = context.grants,
                    ),
                )
                "shizuku" -> shizuku
                "root" -> root
                "accessibility" -> accessibility
                "schedule" -> when (val s = schedule) {
                    is CardBindable -> s.bindCard(manifest.name)
                    else -> s
                }
                else -> dynamicBridges[name]
            } ?: return false
            engine.define(name, scopedBridge(context, name, impl))
        }
        return true
    }

    companion object {
        /** 通用能力：设备支持下载并加载原生 Dex 依赖包（所有 KhatKit 构建均满足）。 */
        const val CAPABILITY_DEPENDENCY = "dependency"
    }
}
