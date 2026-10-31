package heizige.kk.khatkit.app.core.data.ocr

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 插件包内的 manifest.json（OCR2 自定义格式）。 */
@Serializable
data class OcrPluginManifest(
    val kind: String = "dependency",
    @SerialName("plugin_id") val pluginId: String = "ocr",
    val version: String = "",
    @SerialName("entry_class") val entryClass: String = "",
    val abi: List<String> = emptyList(),
    @SerialName("min_app_version") val minAppVersion: String? = null,
)

/**
 * OCR2：原生插件安装器。
 *
 * 流程：下载（调用方完成）→ sha256 校验 → 路径安全校验 + 尺寸上限 → 解压到临时目录 →
 * 原子 rename 到 filesDir/plugins/ocr/<version>/ → DataStore 记录版本与来源。
 * ZIP 上限 8MiB，解压上限 32MiB；条目名拒绝绝对路径、盘符、`..` 与反斜杠穿越。
 */
object OcrPluginInstaller {
    private const val MAX_ZIP_BYTES = 8L * 1024 * 1024
    private const val MAX_UNZIPPED_BYTES = 32L * 1024 * 1024
    private const val MANIFEST = "manifest.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun pluginsRoot(context: Context): File = File(context.filesDir, "plugins/ocr")

    /** 当前 ABI 对应的原生库目录（插件没带当前 ABI 的 so 时返回 null）。 */
    fun nativeLibDir(context: Context, record: OcrPluginRecord): File? {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull { it in record.abi } ?: return null
        val dir = File(versionDir(context, record.version), "lib/$abi")
        return dir.takeIf { it.isDirectory }
    }

    fun versionDir(context: Context, version: String): File = File(pluginsRoot(context), version)

    /** 安装一个已下载到本地的插件 zip。失败时不留下半成品目录。 */
    suspend fun install(
        context: Context,
        zipFile: File,
        expectedSha256: String?,
        itemId: String,
    ): Result<OcrPluginRecord> = withContext(Dispatchers.IO) {
        runCatching {
            require(zipFile.isFile) { "plugin zip not found" }
            require(zipFile.length() in 1..MAX_ZIP_BYTES) { "plugin zip size out of range" }
            val sha = sha256(zipFile)
            if (!expectedSha256.isNullOrBlank()) {
                require(sha.equals(expectedSha256.trim(), ignoreCase = true)) { "plugin sha256 mismatch" }
            }
            ZipFile(zipFile).use { zip ->
                val manifestEntry = zip.getEntry(MANIFEST) ?: error("plugin manifest missing")
                val manifest = json.decodeFromString(OcrPluginManifest.serializer(), zip.getInputStream(manifestEntry).bufferedReader().readText())
                require(manifest.kind == "dependency" && manifest.pluginId == "ocr") { "not an ocr dependency plugin" }
                require(manifest.version.isNotBlank() && manifest.entryClass.isNotBlank()) { "plugin manifest incomplete" }
                require(manifest.abi.isNotEmpty()) { "plugin declares no abi" }
                var total = 0L
                val tmp = File(pluginsRoot(context), ".tmp-" + System.currentTimeMillis())
                try {
                    tmp.mkdirs()
                    for (entry in zip.entries()) {
                        if (entry.isDirectory) continue
                        val name = entry.name.replace('\\', '/')
                        require(isSafeRelativePath(name)) { "unsafe zip entry: " + name }
                        total += entry.size.coerceAtLeast(0L)
                        require(total <= MAX_UNZIPPED_BYTES) { "plugin unzipped size exceeds limit" }
                        val out = File(tmp, name)
                        out.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input -> out.outputStream().use { output -> input.copyTo(output) } }
                    }
                    val target = versionDir(context, manifest.version)
                    val backup = File(pluginsRoot(context), manifest.version + ".old")
                    if (target.exists()) {
                        backup.deleteRecursively()
                        require(target.renameTo(backup)) { "cannot replace existing plugin" }
                    }
                    require(tmp.renameTo(target)) { "cannot commit plugin directory" }
                    backup.deleteRecursively()
                    val record = OcrPluginRecord(
                        pluginId = manifest.pluginId,
                        version = manifest.version,
                        entryClass = manifest.entryClass,
                        abi = manifest.abi,
                        sha256 = sha,
                        itemId = itemId,
                        installedAt = System.currentTimeMillis(),
                    )
                    OcrPluginStore(context).write(record)
                    OcrPluginLoader.invalidate()
                    record
                } finally {
                    tmp.deleteRecursively()
                }
            }
        }
    }

    /** 卸载指定版本（或全部版本）；同时清掉 DataStore 记录与加载器缓存。 */
    suspend fun uninstall(context: Context, version: String? = null): Boolean = withContext(Dispatchers.IO) {
        val root = pluginsRoot(context)
        val targets = if (version == null) root.listFiles()?.toList().orEmpty() else listOf(versionDir(context, version))
        val ok = targets.all { dir -> !dir.exists() || dir.deleteRecursively() }
        OcrPluginStore(context).clear()
        OcrPluginLoader.invalidate()
        ok
    }

    /** 已安装占用磁盘大小（市场里显示「已下载大小」）。 */
    fun installedSize(context: Context, version: String? = null): Long {
        val dir = if (version == null) pluginsRoot(context) else versionDir(context, version)
        if (!dir.isDirectory) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun isSafeRelativePath(name: String): Boolean {
        if (name.isBlank() || name.startsWith("/") || name.contains("\\")) return false
        if (name.contains(":")) return false
        return name.split('/').none { it == ".." || it == "." || it.isEmpty() }
    }
}
