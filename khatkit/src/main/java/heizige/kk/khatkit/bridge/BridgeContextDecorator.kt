package heizige.kk.khatkit.bridge

/**
 * 给既有 bridge 加上本次卡片运行的上下文。
 *
 * 当前 P0 只负责准入状态和透明委托。具体方法策略在新增/收紧 bridge
 * 实现中消费 [context]，这样旧 bridge 的行为保持向后兼容。
 */
interface ContextAwareBridge {
    val context: BridgeContext
}

/** 透明 Tool 包装器；接口委托会生成与 delegate 相同的方法。 */
class ScopedToolBridge(
    override val context: BridgeContext,
    delegate: ToolBridge,
) : ToolBridge by delegate, ContextAwareBridge

class ScopedNetBridge(
    override val context: BridgeContext,
    delegate: NetBridge,
) : NetBridge by delegate, ContextAwareBridge {
    private val scoped = if (delegate is heizige.kk.khatkit.bridge.impl.AndroidNetBridge) {
        heizige.kk.khatkit.bridge.impl.AndroidNetBridge(
            httpClient = delegate.httpClient(),
            allow = context.networkAllow,
        )
    } else {
        delegate
    }

    override fun get(url: String, headers: Map<String, Any?>): String = scoped.get(url, headers)
    override fun post(url: String, body: String, headers: Map<String, Any?>): String =
        scoped.post(url, body, headers)
    override fun put(url: String, body: String, headers: Map<String, Any?>): String =
        scoped.put(url, body, headers)
    override fun delete(url: String, headers: Map<String, Any?>): String = scoped.delete(url, headers)
    override fun multipart(
        url: String,
        fields: Map<String, Any?>,
        fileField: String?,
        filePath: String?,
        headers: Map<String, Any?>,
        saveBinary: Boolean,
    ): String = scoped.multipart(url, fields, fileField, filePath, headers, saveBinary)
    override fun streamText(
        url: String,
        method: String,
        body: String,
        headers: Map<String, Any?>,
        timeoutSeconds: Int,
    ): String = scoped.streamText(url, method, body, headers, timeoutSeconds)
    override fun toFile(url: String, outputPath: String, headers: Map<String, Any?>): String =
        scoped.toFile(url, outputPath, headers)
    override fun head(url: String, headers: Map<String, Any?>): Map<String, Any?> = scoped.head(url, headers)
}

class ScopedFsBridge(
    override val context: BridgeContext,
    delegate: FsBridge,
) : FsBridge by delegate, ContextAwareBridge

class ScopedJsonBridge(
    override val context: BridgeContext,
    delegate: JsonBridge,
) : JsonBridge by delegate, ContextAwareBridge

class ScopedCryptoBridge(
    override val context: BridgeContext,
    delegate: CryptoBridge,
) : CryptoBridge by delegate, ContextAwareBridge

class ScopedTimeBridge(
    override val context: BridgeContext,
    delegate: TimeBridge,
) : TimeBridge by delegate, ContextAwareBridge

class ScopedHostBridge(
    override val context: BridgeContext,
    delegate: HostBridge,
) : HostBridge by delegate, ContextAwareBridge

class ScopedSystemBridge(
    override val context: BridgeContext,
    delegate: SystemBridge,
) : SystemBridge by delegate, ContextAwareBridge

class ScopedAiBridge(
    override val context: BridgeContext,
    delegate: AiBridge,
) : AiBridge by delegate, ContextAwareBridge

/** 透明 UI 包装器。 */
class ScopedUiBridge(
    override val context: BridgeContext,
    delegate: UiBridge,
) : UiBridge by delegate, ContextAwareBridge

/** 透明 Web 包装器。 */
class ScopedWebBridge(
    override val context: BridgeContext,
    delegate: WebBridge,
) : WebBridge by delegate, ContextAwareBridge

/** 透明下载包装器。 */
class ScopedDownloadBridge(
    override val context: BridgeContext,
    delegate: DownloadBridge,
) : DownloadBridge by delegate, ContextAwareBridge

/** 透明存储包装器。 */
class ScopedStoreBridge(
    override val context: BridgeContext,
    delegate: StoreBridge,
) : StoreBridge by delegate, ContextAwareBridge

/** 透明 Shizuku 包装器。 */
class ScopedShizukuBridge(
    override val context: BridgeContext,
    delegate: ShizukuBridge,
) : ShizukuBridge by delegate, ContextAwareBridge

/** 透明 Root 包装器。 */
class ScopedRootBridge(
    override val context: BridgeContext,
    delegate: RootBridge,
) : RootBridge by delegate, ContextAwareBridge

/** 透明无障碍包装器。 */
class ScopedAccessibilityBridge(
    override val context: BridgeContext,
    delegate: AccessibilityBridge,
) : AccessibilityBridge by delegate, ContextAwareBridge

/** 动态依赖桥暂不知晓接口，保持原对象注入。 */
fun scopedBridge(context: BridgeContext, name: String, delegate: Any): Any = when (name) {
    "tool" -> ScopedToolBridge(context, delegate as ToolBridge)
    "net" -> ScopedNetBridge(context, delegate as NetBridge)
    "fs" -> ScopedFsBridge(context, delegate as FsBridge)
    "json" -> ScopedJsonBridge(context, delegate as JsonBridge)
    "crypto" -> ScopedCryptoBridge(context, delegate as CryptoBridge)
    "time" -> ScopedTimeBridge(context, delegate as TimeBridge)
    "host" -> ScopedHostBridge(context, delegate as HostBridge)
    "system" -> ScopedSystemBridge(context, delegate as SystemBridge)
    "ai" -> ScopedAiBridge(context, delegate as AiBridge)
    "ui" -> ScopedUiBridge(context, delegate as UiBridge)
    "web" -> ScopedWebBridge(context, delegate as WebBridge)
    "download" -> ScopedDownloadBridge(context, delegate as DownloadBridge)
    "store" -> ScopedStoreBridge(context, delegate as StoreBridge)
    "shizuku" -> ScopedShizukuBridge(context, delegate as ShizukuBridge)
    "root" -> ScopedRootBridge(context, delegate as RootBridge)
    "accessibility" -> ScopedAccessibilityBridge(context, delegate as AccessibilityBridge)
    else -> delegate
}
