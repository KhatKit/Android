package heizige.kk.khatkit.dependency

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import heizige.kk.khatkit.card.CardManifest
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 依赖包加载结果：成功时 [bridge] 是可直接注册进 BridgeRegistry 的动态 bridge 对象。 */
sealed interface DependencyEnsureResult {
    data class Ok(
        val name: String,
        val version: String,
        val sha256: String,
        val bridge: Any,
    ) : DependencyEnsureResult

    data class Err(val code: String, val message: String) : DependencyEnsureResult
}

/**
 * 原生依赖包管理器（见 docs/dependency-system.md）。
 *
 * 生命周期：卡片 manifest 声明 `requires.dependencies` → 执行前 [ensure] 下载/校验/加载。
 * 安全约束：
 *  - 只从用户配置的 Hub 下载（url 必须是相对路径），不信任任意外部镜像；
 *  - 下载后必须校验 manifest 声明的 sha256，不匹配直接拒绝（不落盘、不加载）；
 *  - 产物只存应用私有目录 `filesDir/dependencies/<name>/<version>.jar`，绝不从共享存储加载；
 *  - 产物必须包含 `classes.dex`，否则视为非法；
 *  - Android 14+ 动态代码加载（W^X）要求代码文件只读：落盘后 fsync + setReadOnly 并复核
 *    `canWrite()==false`，目录尽力收紧为「仅属主可读、不可写」；已加载版本复用只读产物不重写。
 *
 * 入口类解析顺序：`META-INF/khatkit-dependency.properties` 的 `entry=`；
 * 缺省约定 `heizige.kk.khatkit.dependencies.<name>.<Name>Dependency`（name 首字母大写）。
 */
class DependencyManager(
    context: Context,
    private val hubBaseUrl: () -> String = { "" },
    private val fetchBytes: suspend (String) -> ByteArray,
    private val onStatus: (String) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val dependenciesDir = File(appContext.filesDir, "dependencies")
    private val dexDir = File(appContext.codeCacheDir, "dependency-dex")
    private val loaded = ConcurrentHashMap<String, LoadedDependency>()
    private val mutex = Mutex()

    data class LoadedDependency(
        val name: String,
        val version: String,
        val sha256: String,
        val entry: String,
        val bridge: Any,
    )

    /** 已加载依赖包（供能力上报与诊断）。 */
    fun loadedDependencies(): Collection<LoadedDependency> = loaded.values.toList()

    /** 已加载依赖包名集合：与 BridgeRegistry 动态 bridge 名一致。 */
    fun loadedDependencyNames(): Set<String> = loaded.values.mapTo(mutableSetOf()) { it.name }

    /**
     * 确保依赖包 [req] 可用：命中缓存直接复用；缺失/损坏则从 Hub 下载并校验后加载。
     * 任何失败都返回中文错误，绝不加载未通过 sha256 校验的产物。
     */
    suspend fun ensure(req: CardManifest.DependencyReq): DependencyEnsureResult = mutex.withLock {
        if (!SHA256_REGEX.matches(req.sha256)) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_SHA256_INVALID",
                "依赖包 ${req.name} 的 sha256 非法（必须是 64 位小写十六进制）：${req.sha256}",
            )
        }
        val expected = req.sha256.lowercase()
        val key = key(req.name, req.version, expected)
        loaded[key]?.let { cached ->
            return DependencyEnsureResult.Ok(cached.name, cached.version, cached.sha256, cached.bridge)
        }

        val artifact = artifactFile(req.name, req.version)
        if (artifact.exists() && !DependencyArtifactStore.sha256(artifact).equals(expected, ignoreCase = true)) {
            // 缓存被篡改或损坏：删除后重新下载，避免加载到与声明不一致的产物
            ensureWritableDir(artifact.parentFile)
            artifact.delete()
        }
        if (!artifact.exists() || !artifact.isFile || artifact.length() == 0L) {
            download(req, artifact)?.let { return it }
        }
        return load(req, artifact, expected, key)
    }

    private suspend fun download(
        req: CardManifest.DependencyReq,
        target: File,
    ): DependencyEnsureResult.Err? {
        val path = req.url?.takeIf { it.isNotBlank() }
            ?: "/api/dependencies/${req.name}/${req.version}"
        if (!path.startsWith("/")) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_URL_INVALID",
                "依赖包 ${req.name} 的下载地址必须是相对 Hub 的路径（以 / 开头），不允许任意外部镜像：$path",
            )
        }
        val hub = hubBaseUrl().trim().trimEnd('/')
        if (hub.isBlank()) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_HUB_UNCONFIGURED",
                "未配置 Hub 地址，无法下载依赖包 ${req.name}；请在卡片市场设置中填写 Hub 地址后重试",
            )
        }
        val url = hub + path
        onStatus("正在下载依赖包：${req.name} ${req.version}")
        val bytes = try {
            fetchBytes(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_DOWNLOAD_FAILED",
                "依赖包下载失败：${req.name} ${req.version}（${e.message ?: e.javaClass.simpleName}）。请检查网络与 Hub 地址后重试",
            )
        }
        if (bytes.isEmpty()) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_DOWNLOAD_FAILED",
                "依赖包下载失败：${req.name} ${req.version} 返回空内容",
            )
        }
        val actual = DependencyArtifactStore.sha256(bytes)
        if (!actual.equals(req.sha256, ignoreCase = true)) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_HASH_MISMATCH",
                "依赖包 sha256 校验失败：期望 ${req.sha256}，实际 $actual。已拒绝加载，请更新卡片或联系发布者",
            )
        }
        val parent = target.parentFile
        if (!ensureWritableDir(parent)) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_STORAGE_FAILED",
                "依赖包 ${req.name} 无法写入应用私有目录：${parent?.absolutePath}",
            )
        }
        DependencyArtifactStore.writeAtomically(target, bytes)
        // 落盘后复核一次（防写坏/被替换），通过后置为只读：Android 14+ DexClassLoader 拒绝可写代码文件
        if (!DependencyArtifactStore.sha256(target).equals(req.sha256, ignoreCase = true)) {
            target.delete()
            return DependencyEnsureResult.Err(
                "DEPENDENCY_HASH_MISMATCH",
                "依赖包 ${req.name} ${req.version} 落盘后 sha256 复核失败，已删除缓存产物，请重试",
            )
        }
        if (!DependencyArtifactStore.markReadOnly(target)) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_READONLY_FAILED",
                "依赖包 ${req.name} 已下载但无法置为只读（Android 14+ 要求动态代码文件只读）：" +
                    "${target.absolutePath}；已中止加载，请重试",
            )
        }
        lockDownDir(parent)
        return null
    }

    private fun load(
        req: CardManifest.DependencyReq,
        artifact: File,
        expected: String,
        key: String,
    ): DependencyEnsureResult {
        val entry = readEntry(artifact) ?: conventionEntry(req.name)
        if (!DependencyArtifactStore.containsClassesDex(artifact)) {
            ensureWritableDir(artifact.parentFile)
            artifact.delete()
            return DependencyEnsureResult.Err(
                "DEPENDENCY_ARTIFACT_INVALID",
                "依赖包 ${req.name} 产物非法：缺少 classes.dex（应为 D8 产物 jar）",
            )
        }
        val codeFile = try {
            ensureReadOnlyCodeFile(req.name, artifact, expected)
        } catch (e: Throwable) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_READONLY_FAILED",
                "依赖包 ${req.name} 产物无法置为只读（Android 14+ 动态代码加载限制要求代码文件只读）：" +
                    "${e.message ?: e.javaClass.simpleName}",
            )
        }
        val optimized = File(dexDir, "${req.name}/${expected.take(12)}")
        if (!ensureWritableDir(optimized)) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_DEX_DIR_UNWRITABLE",
                "依赖包 ${req.name} 的 dex 优化目录不可写：${optimized.absolutePath}",
            )
        }
        return try {
            Log.i(
                TAG,
                "load dex name=${req.name} version=${req.version} readOnly=${!codeFile.canWrite()} " +
                    "path=${codeFile.absolutePath} optimized=${optimized.absolutePath}",
            )
            val loader = DexClassLoader(codeFile.absolutePath, optimized.absolutePath, null, appContext.classLoader)
            val clazz = Class.forName(entry, true, loader)
            val instance = instantiate(clazz)
            val bridge = DependencyBridgeWrapper(req.name, instance)
            val dependency = LoadedDependency(req.name, req.version, expected, entry, bridge)
            loaded[key] = dependency
            DependencyEnsureResult.Ok(dependency.name, dependency.version, dependency.sha256, dependency.bridge)
        } catch (e: SecurityException) {
            // Android 14+ W^X 动态代码加载：代码文件仍被判为可写/被系统策略拦截 → 复用已加载副本
            loaded[key]?.let { cached ->
                return DependencyEnsureResult.Ok(cached.name, cached.version, cached.sha256, cached.bridge)
            }
            DependencyEnsureResult.Err(
                "DEPENDENCY_LOAD_BLOCKED",
                "原生依赖 ${req.name} ${req.version} 加载被系统拦截（Android 14+ 动态代码加载限制要求只读）：" +
                    "${e.message ?: e.javaClass.simpleName}。请重试；若仍失败请清除应用依赖缓存后重新下载",
            )
        } catch (e: IllegalStateException) {
            loaded[key]?.let { cached ->
                return DependencyEnsureResult.Ok(cached.name, cached.version, cached.sha256, cached.bridge)
            }
            DependencyEnsureResult.Err(
                "DEPENDENCY_LOAD_BLOCKED",
                "原生依赖 ${req.name} ${req.version} 加载被系统拦截（Android 14+ 动态代码加载限制）：" +
                    "${e.message ?: e.javaClass.simpleName}",
            )
        } catch (e: Throwable) {
            DependencyEnsureResult.Err(
                "DEPENDENCY_LOAD_FAILED",
                "依赖包加载失败：${req.name} ${req.version}（${e.message ?: e.javaClass.simpleName}）",
            )
        }
    }

    /**
     * 保证交给 DexClassLoader 的代码文件只读（Android 14+ W^X 动态代码加载限制）：
     * 1. 已是只读 → 直接复用（已加载版本不重写，避免加载后被修改）；
     * 2. 可 chmod → setReadOnly 并复核 `canWrite()==false`，同时收紧父目录权限；
     * 3. chmod 失败（文件系统/权限异常）→ 回退到专用只读副本，副本已存在且校验通过则直接复用。
     */
    private fun ensureReadOnlyCodeFile(name: String, artifact: File, expected: String): File {
        if (!artifact.canWrite()) {
            lockDownDir(artifact.parentFile)
            return artifact
        }
        if (DependencyArtifactStore.markReadOnly(artifact)) {
            lockDownDir(artifact.parentFile)
            return artifact
        }

        val copyDir = File(dependenciesDir, "$name/.readonly")
        val copy = File(copyDir, "${artifact.nameWithoutExtension}-${expected.take(12)}.jar")
        if (
            copy.isFile && copy.length() > 0L && !copy.canWrite() &&
            DependencyArtifactStore.sha256(copy).equals(expected, ignoreCase = true)
        ) {
            return copy
        }
        if (!ensureWritableDir(artifact.parentFile) || !ensureWritableDir(copyDir)) {
            error("只读副本目录不可写：${copyDir.absolutePath}")
        }
        DependencyArtifactStore.writeAtomically(copy, artifact.readBytes())
        if (!DependencyArtifactStore.markReadOnly(copy)) {
            error("只读副本仍可写：${copy.absolutePath}")
        }
        lockDownDir(copyDir)
        lockDownDir(artifact.parentFile)
        return copy
    }

    /** 确保目录存在且可写（可能被 [lockDownDir] 收紧过），失败返回 false。 */
    private fun ensureWritableDir(dir: File?): Boolean {
        if (dir == null) return false
        if (dir.exists() && !dir.canWrite()) dir.setWritable(true, true)
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) return false
        return dir.canWrite()
    }

    /** 尽力收紧目录权限：仅属主可读、不可写（防止已加载的动态代码被替换）。 */
    private fun lockDownDir(dir: File?) {
        dir ?: return
        if (!dir.exists()) return
        dir.setReadable(true, true)
        dir.setWritable(false, true)
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

    /** 读取产物内 `META-INF/khatkit-dependency.properties` 的 entry（UTF-8）。 */
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

    private fun artifactFile(name: String, version: String): File =
        File(dependenciesDir, "$name/$version.jar")

    private fun key(name: String, version: String, sha256: String): String = "$name@$version@$sha256"

    private fun conventionEntry(name: String): String {
        val className = name.replaceFirstChar { it.uppercaseChar() } + "Dependency"
        return "heizige.kk.khatkit.dependencies.$name.$className"
    }

    companion object {
        private const val TAG = "DependencyManager"
        private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
        private const val PROPERTIES_PATH = "META-INF/khatkit-dependency.properties"
        private const val ENTRY_KEY = "entry="
    }
}
