package heizige.kk.khatkit.plugin

import android.content.Context
import dalvik.system.DexClassLoader
import heizige.kk.khatkit.card.CardManifest
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 插件加载结果：成功时 [bridge] 是可直接注册进 BridgeRegistry 的动态 bridge 对象。 */
sealed interface PluginEnsureResult {
    data class Ok(
        val name: String,
        val version: String,
        val sha256: String,
        val bridge: Any,
    ) : PluginEnsureResult

    data class Err(val code: String, val message: String) : PluginEnsureResult
}

/**
 * 原生插件管理器（见 docs/plugin-system.md）。
 *
 * 生命周期：卡片 manifest 声明 `requires.plugins` → 执行前 [ensure] 下载/校验/加载。
 * 安全约束：
 *  - 只从用户配置的 Hub 下载（url 必须是相对路径），不信任任意外部镜像；
 *  - 下载后必须校验 manifest 声明的 sha256，不匹配直接拒绝（不落盘、不加载）；
 *  - 产物只存应用私有目录 `filesDir/plugins/<name>/<version>.jar`，绝不从共享存储加载；
 *  - 产物必须包含 `classes.dex`，否则视为非法。
 *
 * 入口类解析顺序：`META-INF/khatkit-plugin.properties` 的 `entry=`；
 * 缺省约定 `heizige.kk.khatkit.plugins.<name>.<Name>Plugin`（name 首字母大写）。
 */
class PluginManager(
    context: Context,
    private val hubBaseUrl: () -> String = { "" },
    private val fetchBytes: suspend (String) -> ByteArray,
    private val onStatus: (String) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val pluginsDir = File(appContext.filesDir, "plugins")
    private val dexDir = File(appContext.codeCacheDir, "plugin-dex")
    private val loaded = ConcurrentHashMap<String, LoadedPlugin>()
    private val mutex = Mutex()

    data class LoadedPlugin(
        val name: String,
        val version: String,
        val sha256: String,
        val entry: String,
        val bridge: Any,
    )

    /** 已加载插件（供能力上报与诊断）。 */
    fun loadedPlugins(): Collection<LoadedPlugin> = loaded.values.toList()

    /** 已加载插件名集合：与 BridgeRegistry 动态 bridge 名一致。 */
    fun loadedPluginNames(): Set<String> = loaded.values.mapTo(mutableSetOf()) { it.name }

    /**
     * 确保插件 [req] 可用：命中缓存直接复用；缺失/损坏则从 Hub 下载并校验后加载。
     * 任何失败都返回中文错误，绝不加载未通过 sha256 校验的产物。
     */
    suspend fun ensure(req: CardManifest.PluginReq): PluginEnsureResult = mutex.withLock {
        if (!SHA256_REGEX.matches(req.sha256)) {
            return PluginEnsureResult.Err(
                "PLUGIN_SHA256_INVALID",
                "插件 ${req.name} 的 sha256 非法（必须是 64 位小写十六进制）：${req.sha256}",
            )
        }
        val expected = req.sha256.lowercase()
        val key = key(req.name, req.version, expected)
        loaded[key]?.let { cached ->
            return PluginEnsureResult.Ok(cached.name, cached.version, cached.sha256, cached.bridge)
        }

        val artifact = artifactFile(req.name, req.version)
        if (artifact.exists() && !sha256(artifact).equals(expected, ignoreCase = true)) {
            // 缓存被篡改或损坏：删除后重新下载，避免加载到与声明不一致的产物
            artifact.delete()
        }
        if (!artifact.exists() || !artifact.isFile || artifact.length() == 0L) {
            download(req, artifact)?.let { return it }
        }
        return load(req, artifact, expected, key)
    }

    private suspend fun download(
        req: CardManifest.PluginReq,
        target: File,
    ): PluginEnsureResult.Err? {
        val path = req.url?.takeIf { it.isNotBlank() }
            ?: "/api/plugins/${req.name}/${req.version}"
        if (!path.startsWith("/")) {
            return PluginEnsureResult.Err(
                "PLUGIN_URL_INVALID",
                "插件 ${req.name} 的下载地址必须是相对 Hub 的路径（以 / 开头），不允许任意外部镜像：$path",
            )
        }
        val hub = hubBaseUrl().trim().trimEnd('/')
        if (hub.isBlank()) {
            return PluginEnsureResult.Err(
                "PLUGIN_HUB_UNCONFIGURED",
                "未配置 Hub 地址，无法下载插件 ${req.name}；请在卡片市场设置中填写 Hub 地址后重试",
            )
        }
        val url = hub + path
        onStatus("正在下载插件：${req.name} ${req.version}")
        val bytes = try {
            fetchBytes(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return PluginEnsureResult.Err(
                "PLUGIN_DOWNLOAD_FAILED",
                "插件下载失败：${req.name} ${req.version}（${e.message ?: e.javaClass.simpleName}）。请检查网络与 Hub 地址后重试",
            )
        }
        if (bytes.isEmpty()) {
            return PluginEnsureResult.Err(
                "PLUGIN_DOWNLOAD_FAILED",
                "插件下载失败：${req.name} ${req.version} 返回空内容",
            )
        }
        val actual = sha256(bytes)
        if (!actual.equals(req.sha256, ignoreCase = true)) {
            return PluginEnsureResult.Err(
                "PLUGIN_HASH_MISMATCH",
                "插件 sha256 校验失败：期望 ${req.sha256}，实际 $actual。已拒绝加载，请更新卡片或联系发布者",
            )
        }
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
        return null
    }

    private fun load(
        req: CardManifest.PluginReq,
        artifact: File,
        expected: String,
        key: String,
    ): PluginEnsureResult {
        val entry = readEntry(artifact) ?: conventionEntry(req.name)
        if (!containsDex(artifact)) {
            artifact.delete()
            return PluginEnsureResult.Err(
                "PLUGIN_ARTIFACT_INVALID",
                "插件 ${req.name} 产物非法：缺少 classes.dex（应为 D8 产物 jar）",
            )
        }
        val optimized = File(dexDir, "${req.name}/${expected.take(12)}").apply { mkdirs() }
        return try {
            val loader = DexClassLoader(artifact.absolutePath, optimized.absolutePath, null, appContext.classLoader)
            val clazz = Class.forName(entry, true, loader)
            val instance = instantiate(clazz)
            val bridge = PluginBridgeWrapper(req.name, instance)
            val plugin = LoadedPlugin(req.name, req.version, expected, entry, bridge)
            loaded[key] = plugin
            PluginEnsureResult.Ok(plugin.name, plugin.version, plugin.sha256, plugin.bridge)
        } catch (e: Throwable) {
            PluginEnsureResult.Err(
                "PLUGIN_LOAD_FAILED",
                "插件加载失败：${req.name} ${req.version}（${e.message ?: e.javaClass.simpleName}）",
            )
        }
    }

    private fun instantiate(clazz: Class<*>): Any {
        val withContext = clazz.constructors.firstOrNull { ctor ->
            ctor.parameterTypes.size == 1 && Context::class.java.isAssignableFrom(ctor.parameterTypes[0])
        }
        return if (withContext != null) {
            withContext.newInstance(appContext)
        } else {
            clazz.getDeclaredConstructor().newInstance()
        }
    }

    /** 读取产物内 `META-INF/khatkit-plugin.properties` 的 entry（UTF-8）。 */
    private fun readEntry(artifact: File): String? = runCatching {
        ZipFile(artifact).use { zip ->
            val entry = zip.getEntry(PROPERTIES_PATH) ?: return@use null
            val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            text.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith(ENTRY_KEY) }
                ?.substringAfter('=')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
    }.getOrNull()

    private fun containsDex(artifact: File): Boolean = runCatching {
        ZipFile(artifact).use { it.getEntry("classes.dex") != null }
    }.getOrDefault(false)

    private fun artifactFile(name: String, version: String): File =
        File(pluginsDir, "$name/$version.jar")

    private fun key(name: String, version: String, sha256: String): String = "$name@$version@$sha256"

    private fun conventionEntry(name: String): String {
        val className = name.replaceFirstChar { it.uppercaseChar() } + "Plugin"
        return "heizige.kk.khatkit.plugins.$name.$className"
    }

    private fun sha256(file: File): String =
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().toHex()
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
        private const val PROPERTIES_PATH = "META-INF/khatkit-plugin.properties"
        private const val ENTRY_KEY = "entry="
    }
}
