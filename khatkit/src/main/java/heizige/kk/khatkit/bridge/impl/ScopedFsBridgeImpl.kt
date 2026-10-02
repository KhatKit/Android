package heizige.kk.khatkit.bridge.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import heizige.kk.khatkit.bridge.ApprovalGate
import heizige.kk.khatkit.bridge.FsBridge
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** 受卡片根目录约束的文件 bridge。 */
class ScopedFsBridgeImpl(
    private val context: Context,
    private val cardName: String,
    private val roots: Set<String> = emptySet(),
    private val approvalGate: ApprovalGate? = null,
) : FsBridge {
    fun context(): Context = context
    private val privateRoot = File(context.filesDir, "cards/$cardName").apply { mkdirs() }
    private val allowedRoots = (roots + privateRoot.canonicalPath).map { File(it).canonicalPath }.toSet()

    override fun read(path: String): String = resolve(path, false).readText()

    override fun write(path: String, content: String, append: Boolean) {
        val file = resolve(path, true)
        file.parentFile?.mkdirs()
        if (append) file.appendText(content) else file.writeText(content)
    }

    override fun exists(path: String): Boolean = runCatching { resolve(path, false).exists() }.getOrDefault(false)

    override fun stat(path: String): Map<String, Any?> {
        val file = resolve(path, false)
        return mapOf("path" to file.absolutePath, "name" to file.name, "isDir" to file.isDirectory, "size" to file.length(), "modified" to file.lastModified())
    }

    override fun list(path: String, recursive: Boolean, limit: Int): List<Map<String, Any?>> {
        val root = resolve(path, false)
        require(root.isDirectory) { "目录不存在：$path" }
        val result = mutableListOf<Map<String, Any?>>()
        fun visit(dir: File) {
            if (result.size >= limit.coerceAtLeast(0)) return
            dir.listFiles().orEmpty().forEach { child ->
                if (result.size >= limit.coerceAtLeast(0)) return@forEach
                result += stat(child.path)
                if (recursive && child.isDirectory) visit(child)
            }
        }
        visit(root)
        return result
    }

    override fun copy(src: String, dst: String) {
        val source = resolve(src, false)
        val target = resolve(dst, true)
        target.parentFile?.mkdirs()
        if (source.isDirectory) source.copyRecursively(target, overwrite = true) else source.copyTo(target, overwrite = true)
    }

    override fun move(src: String, dst: String) {
        val source = resolve(src, true)
        val target = resolve(dst, true)
        target.parentFile?.mkdirs()
        require(source.renameTo(target)) { "移动文件失败：$src -> $dst" }
    }

    override fun mkdir(path: String) {
        require(resolve(path, true).mkdirs() || resolve(path, true).isDirectory) { "创建目录失败：$path" }
    }

    override fun delete(path: String, recursive: Boolean): Boolean {
        val file = resolve(path, true)
        val allowed = approvalGate?.request(
            "删除卡片文件",
            "卡片 $cardName 请求删除路径：${file.absolutePath}",
            "file_delete",
        ) ?: false
        if (!allowed) throw SecurityException("用户拒绝了操作：删除文件 ${file.absolutePath}")
        if (file.isDirectory && !recursive && file.listFiles()?.isNotEmpty() == true) {
            throw IllegalArgumentException("目录非空，需显式传 recursive=true：$path")
        }
        return if (recursive) file.deleteRecursively() else file.delete()
    }

    override fun zip(paths: List<String>, output: String): String = throw UnsupportedOperationException("fs.zip 尚未迁移，请暂用 tool.zip")
    override fun unzip(zipPath: String, outputDir: String): String = throw UnsupportedOperationException("fs.unzip 尚未迁移，请暂用 tool.unzip")

    override fun readBase64(path: String): String = Base64.encodeToString(resolve(path, false).readBytes(), Base64.NO_WRAP)

    override fun saveBase64(data: String, outputPath: String): String {
        val file = resolve(outputPath, true)
        file.parentFile?.mkdirs()
        file.writeBytes(Base64.decode(data.substringAfter("base64,", data), Base64.DEFAULT))
        return file.absolutePath
    }

    override fun openDir(path: String) {
        val file = resolve(path, false)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("file://${file.absolutePath}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun resolve(path: String, write: Boolean): File {
        val file = File(path).canonicalFile
        val allowed = allowedRoots.any { file.path == it || file.path.startsWith("$it${File.separator}") }
        require(allowed) {
            "卡片文件路径越界：$path；请在 permissions.fsRead/fsWrite 中声明允许的目录"
        }
        if (write && roots.none { file.path == it || file.path.startsWith("$it${File.separator}") }) {
            requireSharedStorageAccess(file.path)
        }
        return file
    }
}
