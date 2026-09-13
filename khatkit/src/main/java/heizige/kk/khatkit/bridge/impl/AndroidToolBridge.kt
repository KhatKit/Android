package heizige.kk.khatkit.bridge.impl

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import heizige.kk.khatkit.bridge.ToolBridge
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

/**
 * tool bridge 的宿主实现（L0：文件/网络/媒体/压缩）。
 *
 * 长任务与平台差异都在宿主：脚本只调动作级 API。
 */
class AndroidToolBridge(
    private val context: Context,
    private val httpClient: HttpClient,
) : ToolBridge {

    init {
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
    }

    override fun readText(path: String): String {
        requireSharedStorageAccess(path)
        val file = File(path)
        require(file.exists()) { "文件不存在：$path" }
        return try {
            file.readText()
        } catch (e: SecurityException) {
            throw SecurityException("无读取权限：$path\n请授予「所有文件访问」后重试", e)
        }
    }

    override fun writeText(path: String, content: String) {
        requireSharedStorageAccess(path)
        val file = File(path)
        file.parentFile?.mkdirs()
        try {
            file.writeText(content)
        } catch (e: SecurityException) {
            throw SecurityException("无写入权限：$path\n请授予「所有文件访问」后重试", e)
        }
    }

    override fun httpGet(url: String, headers: Map<String, String>): String = runBlocking {
        httpClient.get(url) { headers.forEach { (key, value) -> header(key, value) } }.bodyAsText()
    }

    override fun httpPost(url: String, body: String, headers: Map<String, String>): String = runBlocking {
        httpClient.post(url) {
            headers.forEach { (key, value) -> header(key, value) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()
    }

    override fun compressImage(path: String, quality: Int): String {
        requireSharedStorageAccess(path)
        val source = File(path)
        require(source.exists()) { "图片不存在：$path" }
        val bitmap = BitmapFactory.decodeFile(path) ?: error("无法解码图片：$path")
        val output = File(source.parentFile, "${source.nameWithoutExtension}_compressed.jpg")
        try {
            FileOutputStream(output).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), stream)
            }
        } finally {
            bitmap.recycle()
        }
        return output.absolutePath
    }

    override fun mergePdf(paths: List<String>, output: String): String {
        require(paths.isNotEmpty()) { "没有待合并的 PDF" }
        requireSharedStorageAccess(*paths.toTypedArray(), output)
        paths.forEach { require(File(it).exists()) { "PDF 不存在：$it" } }
        val merger = PDFMergerUtility()
        paths.forEach { merger.addSource(File(it)) }
        File(output).parentFile?.mkdirs()
        merger.destinationFileName = output
        merger.mergeDocuments(null)
        return output
    }

    /** /sdcard、/storage 这类共享存储需要「所有文件访问」；否则直接给出可操作的报错。 */
    private fun requireSharedStorageAccess(vararg paths: String) {
        if (AllFilesAccess.isGranted()) return
        val blocked = paths.firstOrNull { isSharedStorage(it) } ?: return
        throw SecurityException(
            "无共享存储访问权限：$blocked\n" +
                "请在 KhatKit 卡片市场 → 设置里授予「所有文件访问」"
        )
    }

    private fun isSharedStorage(path: String): Boolean {
        val normalized = path.trim()
        return normalized.startsWith("/sdcard") ||
            normalized.startsWith("/storage") ||
            normalized.startsWith("/mnt/sdcard")
    }

    override fun openDir(path: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("file://$path"), "resource/folder")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }
}
