package heizige.kk.khatkit.bridge.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import heizige.kk.khatkit.bridge.ApprovalGate
import heizige.kk.khatkit.bridge.FsBridge
import heizige.kk.khatkit.bridge.RunGrants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 受卡片根目录约束的文件 bridge。 */
class ScopedFsBridgeImpl(
    private val context: Context,
    private val cardName: String,
    private val roots: Set<String> = emptySet(),
    private val approvalGate: ApprovalGate? = null,
    /** dispatch 层判定为 allow 的方法；见 [RunGrants]。 */
    private val grants: RunGrants = RunGrants(),
) : FsBridge {
    fun context(): Context = context
    private val privateRoot = File(context.filesDir, "cards/$cardName").apply { mkdirs() }
    private val allowedRoots = (roots + privateRoot.canonicalPath).map { File(it).canonicalPath }.toSet()

    override fun read(path: String): String = resolve(path, false).readText()

    internal fun imageSource(path: String): String {
        if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            require(context.contentResolver.persistedUriPermissions.any { it.isReadPermission && it.uri == uri } ||
                context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                "Image.src: content URI has no read grant"
            }
            context.contentResolver.openInputStream(uri)?.use { } ?: error("Image.src: unreadable URI")
            return path
        }
        require(!path.contains("://")) { "Image.src: only local files/content URI are supported" }
        val file = resolve(path, false)
        require(file.isFile && file.canRead()) { "Image.src: unreadable file" }
        return file.path
    }

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
        // manifest 里显式写了 permissions.methods["fs.delete"]="allow" 时不再打扰用户
        // （判定在 RustBridgeDispatcher 里做，这里只是读结果）
        val allowed = grants.isGranted(FS_DELETE_KEY) || (approvalGate?.request(
            "删除卡片文件",
            "卡片 $cardName 请求删除路径：${file.absolutePath}",
            "file_delete",
        ) ?: false)
        if (!allowed) throw SecurityException("用户拒绝了操作：删除文件 ${file.absolutePath}")
        if (file.isDirectory && !recursive && file.listFiles()?.isNotEmpty() == true) {
            throw IllegalArgumentException("目录非空，需显式传 recursive=true：$path")
        }
        return if (recursive) file.deleteRecursively() else file.delete()
    }

    /** 压缩：入参与输出都要过沙箱；条目名相对各自父目录，与 `tool.zip` 同格式。 */
    override fun zip(paths: List<String>, output: String): String {
        require(paths.isNotEmpty()) { "没有待压缩的文件" }
        val targets = paths.map { resolve(it, false) }
        val out = resolve(output, true)
        out.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(out).buffered()).use { zos ->
            targets.forEach { file ->
                require(file.exists()) { "不存在：${file.absolutePath}" }
                val base = file.parentFile ?: File("/")
                file.walkTopDown().forEach { current ->
                    val entryName = current.relativeTo(base).path + if (current.isDirectory) "/" else ""
                    zos.putNextEntry(ZipEntry(entryName))
                    if (current.isFile) current.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return out.absolutePath
    }

    /** 解压：逐条校验条目不越出目标目录（防 zip slip）。 */
    override fun unzip(zipPath: String, outputDir: String): String {
        val source = resolve(zipPath, false)
        val out = resolve(outputDir, true)
        out.mkdirs()
        val canonicalOut = out.canonicalPath + File.separator
        ZipInputStream(FileInputStream(source).buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val target = File(out, entry.name)
                require(target.canonicalPath.startsWith(canonicalOut)) { "非法压缩包路径（疑似 zip slip）：${entry.name}" }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return out.absolutePath
    }

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

/** `permissions.methods` 里对应的方法键。 */
private const val FS_DELETE_KEY = "fs.delete"
