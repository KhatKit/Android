package heizige.kk.khatkit.dependency

import android.content.Context
import heizige.kk.khatkit.R
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

    /**
     * 失败结果。[retryable] 表示可以尝试卡片清单声明中同一依赖的其它版本
     * （版本撤销 / 下架 / 网络失败），签名与哈希失败一律不可重试（安全红线）。
     */
    data class Err(
        val code: String,
        val message: String,
        val retryable: Boolean = false,
    ) : DependencyEnsureResult
}

/** 依赖下载返回非 2xx 时抛出，携带 HTTP 状态码供上层区分 410（撤销）/ 404（下架）。 */
class DependencyHttpException(
    val status: Int,
    val bodyMessage: String? = null,
) : Exception("HTTP $status${bodyMessage?.takeIf { it.isNotBlank() }?.let { "：$it" } ?: ""}")

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
    /** 构建时固定的签名公钥（X.509 DER base64），与服务端 /api/dependencies/pubkey 对应。 */
    private val pinnedPublicKeyBase64: String = PinnedDependencyKey.PUBLIC_KEY_BASE64,
    /** 构建时固定的公钥指纹（hex 小写），用于核对服务端公钥。 */
    private val pinnedFingerprintSha256: String = PinnedDependencyKey.FINGERPRINT_SHA256,
    /** 可选：拉取服务端公钥指纹（离线/失败时回退到本地固定公钥，不影响加载）。 */
    private val fetchPubkeyFingerprint: (suspend () -> String?)? = null,
) {
    private val appContext = context.applicationContext
    private val dependenciesDir = File(appContext.filesDir, "dependencies")
    private val dexDir = File(appContext.codeCacheDir, "dependency-dex")
    private val loaded = ConcurrentHashMap<String, LoadedDependency>()
    private val mutex = Mutex()
    private val pinnedKeyDer: ByteArray = runCatching {
        DependencySignature.decodePublicKeyBase64(pinnedPublicKeyBase64)
    }.getOrElse { error ->
        Log.e(TAG, "App 内置依赖签名公钥非法：${error.message}")
        ByteArray(0)
    }
    private val remoteKeyChecked = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        Log.i(TAG, "依赖包信任锚：ECDSA P-256/SHA256withECDSA，固定公钥指纹 sha256=$pinnedFingerprintSha256")
    }

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
                appContext.getString(R.string.khatkit_dependency_sha_invalid, req.name, req.sha256),
            )
        }
        val signature = req.signature.trim()
        if (signature.isEmpty()) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_SIGNATURE_MISSING",
                appContext.getString(R.string.khatkit_dependency_signature_missing, req.name, req.version),
            )
        }
        if (!DependencySignature.isWellFormedBase64(signature)) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_SIGNATURE_INVALID",
                appContext.getString(R.string.khatkit_dependency_signature_malformed, req.name, req.version),
            )
        }
        if (pinnedKeyDer.isEmpty()) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_PUBKEY_INVALID",
                appContext.getString(R.string.khatkit_dependency_key_invalid),
            )
        }
        val expected = req.sha256.lowercase()
        val key = key(req.name, req.version, expected)
        loaded[key]?.let { cached ->
            return DependencyEnsureResult.Ok(cached.name, cached.version, cached.sha256, cached.bridge)
        }

        ensureRemoteKeyTrusted()?.let { return it }

        val artifact = artifactFile(req.name, req.version)
        if (artifact.exists()) {
            // 缓存必须同时满足 sha256 + ECDSA 签名；任一不符即删除后重新下载
            val cachedBytes = runCatching { artifact.readBytes() }.getOrNull()
            val cacheOk = cachedBytes != null &&
                DependencyArtifactHelper.sha256(cachedBytes).equals(expected, ignoreCase = true) &&
                DependencySignature.verify(cachedBytes, signature, pinnedKeyDer)
            if (!cacheOk) {
                Log.w(TAG, "缓存产物校验失败，删除后重新下载：name=${req.name} version=${req.version}")
                ensureWritableDir(artifact.parentFile)
                artifact.delete()
            }
        }
        if (!artifact.exists() || !artifact.isFile || artifact.length() == 0L) {
            download(req, artifact)?.let { return it }
        }
        // 落盘/复用产物在加载前做最终复核，保证 DexClassLoader 拿到的字节 = 已签名字节
        val finalBytes = runCatching { artifact.readBytes() }.getOrNull()
        if (
            finalBytes == null ||
            !DependencyArtifactHelper.sha256(finalBytes).equals(expected, ignoreCase = true) ||
            !DependencySignature.verify(finalBytes, signature, pinnedKeyDer)
        ) {
            ensureWritableDir(artifact.parentFile)
            artifact.delete()
            Log.w(TAG, "依赖包加载前复核失败：name=${req.name} version=${req.version} fingerprint=$pinnedFingerprintSha256")
            return DependencyEnsureResult.Err(
                "DEPENDENCY_SIGNATURE_INVALID",
                appContext.getString(R.string.khatkit_dependency_recheck_failed, req.name, req.version),
            )
        }
        return load(req, artifact, expected, key)
    }

    /**
     * 在线核对服务端公钥指纹（best-effort）：
     * - 服务端公钥与内置固定指纹不一致 → 拒绝加载（DEPENDENCY_PUBKEY_MISMATCH）；
     * - 拉取失败（离线/旧服务端）→ 继续用内置固定公钥，签名校验仍是硬门槛。
     * 每个实例只成功核对一次；失败会重置以便下次重试。
     */
    private suspend fun ensureRemoteKeyTrusted(): DependencyEnsureResult.Err? {
        val fetcher = fetchPubkeyFingerprint ?: return null
        if (!remoteKeyChecked.compareAndSet(false, true)) return null
        val remote = try {
            fetcher()
        } catch (e: CancellationException) {
            remoteKeyChecked.set(false)
            throw e
        } catch (e: Throwable) {
            remoteKeyChecked.set(false)
            Log.w(TAG, "拉取服务端签名公钥指纹失败，继续使用内置固定公钥：${e.message ?: e.javaClass.simpleName}")
            return null
        }
        if (remote.isNullOrBlank()) {
            remoteKeyChecked.set(false)
            return null
        }
        Log.i(TAG, "服务端依赖签名公钥指纹 sha256=$remote，内置固定指纹 sha256=$pinnedFingerprintSha256")
        if (!DependencySignature.matchesPinnedFingerprint(remote, pinnedFingerprintSha256)) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_PUBKEY_MISMATCH",
                appContext.getString(R.string.khatkit_dependency_key_mismatch, remote, pinnedFingerprintSha256),
            )
        }
        return null
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
                appContext.getString(R.string.khatkit_dependency_url_invalid, req.name, path),
            )
        }
        val hub = hubBaseUrl().trim().trimEnd('/')
        if (hub.isBlank()) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_HUB_UNCONFIGURED",
                appContext.getString(R.string.khatkit_dependency_hub_missing, req.name),
            )
        }
        val url = hub + path
        onStatus(appContext.getString(R.string.khatkit_dependency_downloading, req.name, req.version))
        val bytes = try {
            fetchBytes(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DependencyHttpException) {
            val spec = DependencyFallback.classifyHttp(e.status)
            val message = when (spec.code) {
                "DEPENDENCY_REVOKED" ->
                    appContext.getString(R.string.khatkit_dependency_revoked, req.name, req.version)

                "DEPENDENCY_VERSION_UNAVAILABLE" ->
                    appContext.getString(R.string.khatkit_dependency_version_missing, req.name, req.version)

                else ->
                    appContext.getString(R.string.khatkit_dependency_download_http, req.name, req.version, e.status)
            }
            return DependencyEnsureResult.Err(spec.code, message, retryable = spec.retryable)
        } catch (e: Throwable) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_DOWNLOAD_FAILED",
                appContext.getString(R.string.khatkit_dependency_download_error, req.name, req.version, e.message ?: e.javaClass.simpleName),
                retryable = true,
            )
        }
        if (bytes.isEmpty()) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_DOWNLOAD_FAILED",
                appContext.getString(R.string.khatkit_dependency_download_empty, req.name, req.version),
                retryable = true,
            )
        }
        val actual = DependencyArtifactHelper.sha256(bytes)
        if (!actual.equals(req.sha256, ignoreCase = true)) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_HASH_MISMATCH",
                appContext.getString(R.string.khatkit_dependency_hash_failed, req.sha256, actual),
            )
        }
        // sha256 之后再做 ECDSA 签名校验：sha256 防传输损坏，签名防服务端/Hub 被篡改后投毒
        if (!DependencySignature.verify(bytes, req.signature.trim(), pinnedKeyDer)) {
            Log.w(
                TAG,
                "依赖包签名校验失败：name=${req.name} version=${req.version} " +
                    "fingerprint=$pinnedFingerprintSha256",
            )
            return DependencyEnsureResult.Err(
                "DEPENDENCY_SIGNATURE_INVALID",
                appContext.getString(R.string.khatkit_dependency_signature_failed, req.name, req.version, pinnedFingerprintSha256),
            )
        }
        val parent = target.parentFile
        if (!ensureWritableDir(parent)) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_STORAGE_FAILED",
                appContext.getString(R.string.khatkit_dependency_storage_failed, req.name, parent?.absolutePath),
            )
        }
        DependencyArtifactHelper.writeAtomically(target, bytes)
        // 落盘后复核一次（防写坏/被替换），通过后置为只读：Android 14+ DexClassLoader 拒绝可写代码文件
        if (!DependencyArtifactHelper.sha256(target).equals(req.sha256, ignoreCase = true)) {
            target.delete()
            return DependencyEnsureResult.Err(
                "DEPENDENCY_HASH_MISMATCH",
                appContext.getString(R.string.khatkit_dependency_saved_hash_failed, req.name, req.version),
            )
        }
        if (!DependencyArtifactHelper.markReadOnly(target)) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_READONLY_FAILED",
                appContext.getString(R.string.khatkit_dependency_readonly_failed, req.name, target.absolutePath),
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
        if (!DependencyArtifactHelper.containsClassesDex(artifact)) {
            ensureWritableDir(artifact.parentFile)
            artifact.delete()
            return DependencyEnsureResult.Err(
                "DEPENDENCY_ARTIFACT_INVALID",
                appContext.getString(R.string.khatkit_dependency_artifact_invalid, req.name),
            )
        }
        val codeFile = try {
            ensureReadOnlyCodeFile(req.name, artifact, expected)
        } catch (e: Throwable) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_READONLY_FAILED",
                appContext.getString(R.string.khatkit_dependency_readonly_error, req.name, e.message ?: e.javaClass.simpleName),
            )
        }
        val optimized = File(dexDir, "${req.name}/${expected.take(12)}")
        if (!ensureWritableDir(optimized)) {
            return DependencyEnsureResult.Err(
                "DEPENDENCY_DEX_DIR_UNWRITABLE",
                appContext.getString(R.string.khatkit_dependency_dex_unwritable, req.name, optimized.absolutePath),
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
                appContext.getString(R.string.khatkit_dependency_load_blocked_retry, req.name, req.version, e.message ?: e.javaClass.simpleName),
            )
        } catch (e: IllegalStateException) {
            loaded[key]?.let { cached ->
                return DependencyEnsureResult.Ok(cached.name, cached.version, cached.sha256, cached.bridge)
            }
            DependencyEnsureResult.Err(
                "DEPENDENCY_LOAD_BLOCKED",
                appContext.getString(R.string.khatkit_dependency_load_blocked, req.name, req.version, e.message ?: e.javaClass.simpleName),
            )
        } catch (e: Throwable) {
            DependencyEnsureResult.Err(
                "DEPENDENCY_LOAD_FAILED",
                appContext.getString(R.string.khatkit_dependency_load_failed, req.name, req.version, e.message ?: e.javaClass.simpleName),
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
        if (DependencyArtifactHelper.markReadOnly(artifact)) {
            lockDownDir(artifact.parentFile)
            return artifact
        }

        val copyDir = File(dependenciesDir, "$name/.readonly")
        val copy = File(copyDir, "${artifact.nameWithoutExtension}-${expected.take(12)}.jar")
        if (
            copy.isFile && copy.length() > 0L && !copy.canWrite() &&
            DependencyArtifactHelper.sha256(copy).equals(expected, ignoreCase = true)
        ) {
            return copy
        }
        if (!ensureWritableDir(artifact.parentFile) || !ensureWritableDir(copyDir)) {
            error("只读副本目录不可写：${copyDir.absolutePath}")
        }
        DependencyArtifactHelper.writeAtomically(copy, artifact.readBytes())
        if (!DependencyArtifactHelper.markReadOnly(copy)) {
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
