package heizige.kk.khatkit.app.core.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import heizige.kk.khatkit.common.android.Logging
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 把选择器回传的 content URI 复制进应用缓存，让卡片脚本能按真实路径读文件。
 *
 * 选择器给的 URI 在卡片运行结束后不一定还有效（临时授权、MediaStore 缩略图条目），
 * 因此 `mediaPicker.pickMedia` 与表单 `file_picker` 都要先落地成文件再回传路径。
 *
 * 落点：`cacheDir/cards/<cardName>/picked/`（cardName 为空时用 `cacheDir/cards/picked/`，
 * 表单选择器拿不到卡片名，走这个共享目录）。缓存目录由系统按空间压力回收。
 */
class CardMediaImporter(private val context: Context) {

    /** 复制 [uris] 指向的文件；单个失败只跳过该文件，不影响其余。返回落地后的绝对路径。 */
    fun import(cardName: String, uris: List<String>): List<String> {
        if (uris.isEmpty()) return emptyList()
        val dir = pickedDir(cardName).apply { mkdirs() }
        pruneStale(dir)
        return uris.mapNotNull { raw ->
            val uri = raw.toUri()
            copyTo(dir, uri).also { if (it == null) Logging.log("CardMediaImporter", "导入失败：$raw") }
        }
    }

    /** 落地目录；cardName 为空时用共享目录。 */
    fun pickedDir(cardName: String): File {
        val base = File(context.cacheDir, "cards")
        val scope = cardName.trim().replace(Regex("""[^\w.\-]"""), "_")
        return if (scope.isEmpty()) File(base, "picked") else File(File(base, scope), "picked")
    }

    private fun copyTo(dir: File, uri: Uri): String? {
        val name = displayName(uri)
        val target = uniqueFile(dir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        return target.absolutePath
    }

    /** 取原始文件名（MediaStore / DocumentsProvider 提供时优先），并清掉路径分隔符。 */
    private fun displayName(uri: Uri): String {
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
        val raw = queried?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "picked"
        val safe = raw.replace(Regex("""[\\/:*?"<>|\s]"""), "_").trim('_').take(80)
        return safe.ifEmpty { "picked" }
    }

    /** 同名文件已存在时追加序号，避免覆盖用户上一轮选的内容。 */
    private fun uniqueFile(dir: File, name: String): File {
        val candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 1
        while (true) {
            val next = if (ext.isEmpty()) "${base}_$index" else "${base}_$index.$ext"
            val file = File(dir, next)
            if (!file.exists()) return file
            index++
        }
    }

    /** 清掉 24 小时前的旧副本；缓存无限增长会让用户莫名丢卡片数据。 */
    private fun pruneStale(dir: File) {
        val deadline = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < deadline) file.delete()
        }
    }
}