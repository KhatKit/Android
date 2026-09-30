package heizige.kk.khatkit.dependency

import android.content.Context
import java.io.File
import java.security.MessageDigest

data class DependencyCacheEntry(
    val name: String,
    val version: String,
    val sizeBytes: Long,
    val updatedAt: Long,
    val sha256: String,
)

/** 仅管理应用私有依赖缓存，不触碰云端发布物或卡片清单。 */
object DependencyCache {
    fun list(context: Context): List<DependencyCacheEntry> {
        val root = File(context.applicationContext.filesDir, "dependencies")
        return root.listFiles().orEmpty()
            .filter { it.isDirectory }
            .flatMap { dir ->
                dir.listFiles().orEmpty()
                    .filter { it.isFile && it.extension == "jar" }
                    .map { file ->
                        DependencyCacheEntry(
                            name = dir.name,
                            version = file.nameWithoutExtension,
                            sizeBytes = file.length(),
                            updatedAt = file.lastModified(),
                            sha256 = sha256(file),
                        )
                    }
            }
            .sortedWith(compareBy({ it.name }, { it.version }))
    }

    fun delete(context: Context, name: String, version: String): Boolean {
        if (!NAME.matches(name) || !VERSION.matches(version)) return false
        val root = File(context.applicationContext.filesDir, "dependencies")
        val file = File(root, "$name/$version.jar")
        if (!file.isFile) return false
        // DependencyManager locks code artifacts read-only for Android 14 W^X.
        // Management deletion is an explicit user action, so temporarily unlock only
        // this private cache path, then remove empty directories.
        file.setWritable(true, true)
        file.parentFile?.setWritable(true, true)
        val deleted = file.delete()
        if (deleted) {
            file.parentFile?.listFiles()?.takeIf { it.isEmpty() }?.let { file.parentFile?.delete() }
            File(root, "$name/.readonly").let { readonly ->
                readonly.setWritable(true, true)
                readonly.listFiles().orEmpty().forEach { it.setWritable(true, true); it.delete() }
                readonly.delete()
            }
            File(root, name).takeIf { it.isDirectory && it.listFiles().isNullOrEmpty() }?.delete()
        }
        return deleted
    }

    fun clear(context: Context): Int {
        val entries = list(context)
        entries.forEach { delete(context, it.name, it.version) }
        return entries.size
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    private val NAME = Regex("[A-Za-z0-9_]+")
    private val VERSION = Regex("[A-Za-z0-9._+-]+")
}
