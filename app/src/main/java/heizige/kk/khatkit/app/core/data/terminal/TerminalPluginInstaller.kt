package heizige.kk.khatkit.app.core.data.terminal

import android.content.Context
import heizige.kk.khatkit.app.feature.extensions.TerminalPluginInfo
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class TerminalManifest(
    val kind: String = "dependency",
    @SerialName("plugin_id") val pluginId: String = TerminalPluginInfo.ID,
    val version: String = "",
    @SerialName("entry_class") val entryClass: String = "",
    val abi: List<String> = emptyList(),
)

object TerminalPluginInstaller {
    private const val MAX_ZIP_BYTES = 8L * 1024 * 1024
    private const val MAX_UNZIPPED_BYTES = 32L * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    fun pluginsRoot(context: Context) = File(context.filesDir, "plugins/terminal")
    fun versionDir(context: Context, version: String) = File(pluginsRoot(context), version)
    fun nativeLibDir(context: Context, record: TerminalPluginRecord): File? {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull { it in record.abi } ?: return null
        return File(versionDir(context, record.version), "lib/$abi").takeIf { it.isDirectory }
    }

    suspend fun install(context: Context, zipFile: File, expectedSha256: String?, itemId: String): Result<TerminalPluginRecord> = withContext(Dispatchers.IO) {
        runCatching {
            require(zipFile.isFile && zipFile.length() in 1..MAX_ZIP_BYTES) { "terminal plugin zip size out of range" }
            val sha = sha256(zipFile)
            require(expectedSha256.isNullOrBlank() || sha.equals(expectedSha256.trim(), true)) { "plugin sha256 mismatch" }
            ZipFile(zipFile).use { zip ->
                val manifest = json.decodeFromString(
                    TerminalManifest.serializer(),
                    zip.getInputStream(zip.getEntry("manifest.json") ?: error("manifest missing"))
                        .bufferedReader().readText(),
                )
                require(manifest.kind == "dependency" && manifest.pluginId == TerminalPluginInfo.ID)
                require(manifest.version.isNotBlank() && manifest.entryClass.isNotBlank())
                val root = pluginsRoot(context).apply { mkdirs() }
                val temp = File(root, ".tmp-${System.currentTimeMillis()}").apply { mkdirs() }
                try {
                    var total = 0L
                    zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                        val name = entry.name.replace('\\', '/')
                        require(name.isNotBlank() && !name.startsWith('/') && !name.contains('\\') &&
                            !name.contains(':') && name.split('/').none { it.isEmpty() || it == "." || it == ".." })
                        total += entry.size.coerceAtLeast(0L)
                        require(total <= MAX_UNZIPPED_BYTES)
                        val out = File(temp, name).apply { parentFile?.mkdirs() }
                        zip.getInputStream(entry).use { input -> out.outputStream().use(input::copyTo) }
                        out.setReadOnly()
                    }
                    val target = versionDir(context, manifest.version)
                    target.deleteRecursively()
                    require(temp.renameTo(target)) { "cannot commit terminal plugin" }
                    val record = TerminalPluginRecord(manifest.pluginId, manifest.version, manifest.entryClass, manifest.abi, sha, itemId, System.currentTimeMillis())
                    TerminalPluginStore(context).write(record)
                    record
                } finally {
                    temp.deleteRecursively()
                }
            }
        }
    }

    suspend fun uninstall(context: Context): Boolean = withContext(Dispatchers.IO) {
        val ok = !pluginsRoot(context).exists() || pluginsRoot(context).deleteRecursively()
        TerminalPluginStore(context).clear()
        ok
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count <= 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
