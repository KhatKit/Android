package heizige.kk.khatkit.bridge

import heizige.kk.khatkit.engine.ScriptEngine
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.card.CardStrings
import heizige.kk.khatkit.bridge.impl.CardAiBridge
import heizige.kk.khatkit.bridge.impl.CardI18nBridge
import heizige.kk.khatkit.bridge.impl.EmbeddingEngine
import heizige.kk.khatkit.bridge.impl.StoreRequest
import heizige.kk.khatkit.bridge.impl.ScopedFsBridgeImpl
import heizige.kk.khatkit.ui.HeadlessUiBridge
import heizige.kk.khatkit.ui.MediaPickerHost
import heizige.kk.khatkit.ui.UiMode

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
    private val browserProvider: ((BridgeContext) -> BrowserBridge)? = null,
    private val localeTags: () -> List<String> = { listOf(java.util.Locale.getDefault().toLanguageTag()) },
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
        if (browserProvider != null) add("browser")
        addAll(dynamicBridges.keys)
        add(CAPABILITY_DEPENDENCY)
        add("i18n")
    }

    /** 卡片所需能力是否全部具备。 */
    fun supports(required: Collection<String>): Boolean = availableBridges().containsAll(required)

    /**
     * 按本次运行的 UI 策略挑出真正要注入的 `ui` 实现。
     *
     * [UiMode.HEADLESS] 恒返回 [HeadlessUiBridge]（不弹层、立即返回），**优先于**任何
     * 运行期 override —— 无人值守时不允许被一张带 `ui` 的卡片反手弹出弹层。
     */
    fun uiDelegate(uiOverride: UiBridge?, uiMode: UiMode): UiBridge? = when (uiMode) {
        UiMode.HEADLESS -> HeadlessUiBridge
        UiMode.INTERACTIVE -> uiOverride ?: ui
    }

    /**
     * 本次运行实际具备的能力集合。
     *
     * 与 [availableBridges] 的差别只有 `ui` 一项：HEADLESS 下 `ui` 仍然算“具备”
     * （脚本可以照常调用，拿到的全是立即返回的空实现），而不是让整卡因缺 bridge 被拒。
     */
    fun availableBridges(uiMode: UiMode, uiOverride: UiBridge? = null): Set<String> {
        val base = availableBridges() - "ui"
        return if (uiDelegate(uiOverride, uiMode) != null) base + "ui" else base
    }

    /**
     * 把卡片声明过的 bridge 注入引擎。
     * @return 缺任一必需 bridge 时返回 false，卡片不可用。
     */
    fun inject(
        engine: ScriptEngine,
        manifest: CardManifest,
        approvalGate: ApprovalGate? = null,
        deadlineAt: Long = 0L,
        runResources: MutableList<AutoCloseable>? = null,
        uiOverride: UiBridge? = null,
        uiMode: UiMode = UiMode.INTERACTIVE,
        locales: List<String>? = null,
    ): Boolean {
        val required = manifest.requiredBridges
        val delegateUi = uiDelegate(uiOverride, uiMode)
        val available = availableBridges(uiMode, uiOverride)
        if (!available.containsAll(required)) return false
        val context = BridgeContext(
            cardName = manifest.name,
            engine = manifest.engine,
            quotaMb = manifest.store.quotaMb,
            networkAllow = manifest.network.allow.map { it.lowercase().substringBefore(':') }.toSet(),
            permissions = manifest.permissions.methods,
            approvalGate = approvalGate,
            deadlineAt = deadlineAt,
        )
        val strings = CardStrings(manifest, locales ?: localeTags())
        engine.define("i18n", scopedBridge(context, "i18n", CardI18nBridge(strings)))
        required.filterNot { it == "i18n" }.forEach { name ->
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
                "ui" -> delegateUi?.let { delegate ->
                    val imageFs = (fs as? ScopedFsBridgeImpl)?.let {
                        ScopedFsBridgeImpl(it.context(), manifest.name,
                            manifest.permissions.fsRead.toSet(), approvalGate, context.grants)
                    }
                    val checked = UiImageAccessBridge(
                        delegate,
                        { path -> imageFs?.imageSource(path) ?: error("Image.src: file access unavailable") },
                        allowRemote = { url ->
                            val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
                            host != null && context.networkAllow.any { it == "*" || host == it || host.endsWith(".$it") }
                        },
                    )
                    LocalizedUiBridge(checked, strings)
                }
                "web" -> web
                "browser" -> browserProvider?.invoke(context)?.also {
                    if (it is AutoCloseable) runResources?.add(it)
                }
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
