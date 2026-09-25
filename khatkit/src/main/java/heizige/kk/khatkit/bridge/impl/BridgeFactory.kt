package heizige.kk.khatkit.bridge.impl

import android.content.Context
import heizige.kk.khatkit.bridge.BridgeRegistry
import heizige.kk.khatkit.bridge.UiBridge
import heizige.kk.khatkit.exec.CardExecutor
import heizige.kk.khatkit.exec.CommandRunner
import heizige.kk.khatkit.hub.LibResolver
import heizige.kk.khatkit.dependency.DependencyHttpException
import heizige.kk.khatkit.dependency.DependencyManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 下载策略层（用户在设置里配，脚本不能覆盖）。 */
data class DownloadPolicy(
    val maxConcurrent: Int = 3,
)

/**
 * 按设备当前能力组装六个 bridge（设计文档 7.1）。
 *
 * 缺失的 bridge 传 null，依赖它的卡片对 AI 不可见：
 * - shizuku：Shizuku 服务在线且已授权
 * - root：显式开启且 su 探测成功（默认禁用）
 * - accessibility：无障碍服务已开启并已连接
 *
 * engine:command 卡片通过 shizuku/root 执行，二者都缺时 command 卡片不可用。
 */
object BridgeFactory {

    suspend fun create(
        context: Context,
        scope: CoroutineScope,
        ui: UiBridge?,
        enableRoot: Boolean = false,
        policy: DownloadPolicy = DownloadPolicy(),
        libResolver: LibResolver? = null,
        hubBaseUrl: () -> String = { "" },
        onDependencyStatus: ((String) -> Unit)? = null,
    ): CardExecutor = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val http = HttpClient(CIO.create())
        val root = File(appContext.filesDir, "khatkit")

        val shizukuBridge = if (ShizukuBridgeImpl.isAvailable()) ShizukuBridgeImpl() else null
        val rootBridge = if (enableRoot && RootBridgeImpl.isAvailable()) RootBridgeImpl() else null
        val accessibilityBridge = AccessibilityBridgeHolder.current()

        val registry = BridgeRegistry(
            tool = AndroidToolBridge(appContext, http),
            ui = ui,
            download = DownloadManagerImpl(
                context = appContext,
                scope = scope,
                rootDir = File(root, "downloads"),
                http = http,
                maxConcurrent = policy.maxConcurrent,
            ),
            storeProvider = { cardName, quotaMb -> FileStoreBridge(appContext, cardName, quotaMb) },
            shizuku = shizukuBridge,
            root = rootBridge,
            accessibility = accessibilityBridge,
        )

        // 原生依赖包：随云端卡片下载，校验 sha256 + ECDSA 签名后 DexClassLoader 加载为动态 bridge。
        // 固定公钥在 PinnedDependencyKey（构建时写入），另在线核对服务端 pubkey 指纹（best-effort）。
        val dependencyManager = DependencyManager(
            context = appContext,
            hubBaseUrl = hubBaseUrl,
            fetchBytes = { url ->
                val response = http.get(url)
                if (!response.status.isSuccess()) {
                    val body = runCatching { response.bodyAsText() }.getOrNull()
                    throw DependencyHttpException(response.status.value, body)
                }
                response.readBytes()
            },
            onStatus = { onDependencyStatus?.invoke(it) },
            fetchPubkeyFingerprint = {
                val hub = hubBaseUrl().trim().trimEnd('/')
                if (hub.isBlank()) {
                    null
                } else {
                    val response = http.get("$hub/api/dependencies/pubkey")
                    if (!response.status.isSuccess()) {
                        null
                    } else {
                        // 优先读响应头；旧服务端没有该头时回退解析 JSON 的 fingerprintSha256
                        response.headers["X-KhatKit-Pubkey-Sha256"]?.trim()?.takeIf { it.isNotEmpty() }
                            ?: runCatching { response.bodyAsText() }.getOrNull()?.let { body ->
                                PUBKEY_FINGERPRINT_REGEX.find(body)?.groupValues?.get(1)
                            }
                    }
                }
            },
        )

        val commandRunner = when {
            shizukuBridge != null -> CommandRunner { command -> shizukuBridge.runCommand(command) }
            rootBridge != null -> CommandRunner { command -> rootBridge.shell(command) }
            else -> null
        }

        CardExecutor(registry, commandRunner, libResolver, dependencyManager, onDependencyStatus)
    }

    private val PUBKEY_FINGERPRINT_REGEX = Regex("\"fingerprintSha256\"\\s*:\\s*\"([0-9a-fA-F]{64})\"")
}
