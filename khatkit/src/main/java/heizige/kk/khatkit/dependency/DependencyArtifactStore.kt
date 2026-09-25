package heizige.kk.khatkit.dependency

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * 依赖产物落盘与只读化工具（纯 JVM，便于单测）。
 *
 * Android 14+ 起，动态加载的代码文件（jar/dex/apk）必须是只读的：
 * 应用 targetSdk ≥ 34 时，DexClassLoader 加载可写代码文件会被系统以
 * SecurityException 拦截（W^X 动态代码加载限制），因此下载落盘后必须
 * fsync + setReadOnly 并复核 [File.canWrite]。
 */
internal object DependencyArtifactStore {

    fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().toHex()
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** 产物 jar 必须在根目录包含 classes.dex，否则视非法。 */
    fun containsClassesDex(artifact: File): Boolean = runCatching {
        ZipFile(artifact).use { it.getEntry("classes.dex") != null }
    }.getOrDefault(false)

    /** 写入临时文件并 fsync 后原子替换目标文件（临时文件与目标同目录）。 */
    fun writeAtomically(target: File, bytes: ByteArray) {
        val parent = target.parentFile ?: error("目标文件没有父目录：${target.absolutePath}")
        val tmp = File(parent, "${target.name}.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
    }

    /**
     * 把代码文件置为只读并复核：成功返回 true，调用后 [File.canWrite] 必须为 false。
     * 只读后再交给 DexClassLoader（Android 14+ 要求，见类注释）。
     */
    fun markReadOnly(file: File): Boolean {
        if (!file.exists()) return false
        file.setReadOnly()
        return !file.canWrite()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
