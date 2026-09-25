package heizige.kk.khatkit.bridge.impl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.PowerManager
import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import heizige.kk.khatkit.bridge.ToolBridge
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
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

    override fun httpMultipart(
        url: String,
        fields: Map<String, String>,
        fileField: String?,
        filePath: String?,
        headers: Map<String, String>,
        saveBinary: Boolean,
    ): String = runBlocking {
        if (!filePath.isNullOrBlank()) requireSharedStorageAccess(filePath)
        val response = httpClient.post(url) {
            headers.forEach { (key, value) -> header(key, value) }
            setBody(
                MultiPartFormDataContent(
                    formData {
                        fields.forEach { (name, value) -> append(name, value) }
                        if (!fileField.isNullOrBlank() && !filePath.isNullOrBlank()) {
                            val file = File(filePath)
                            require(file.exists()) { "文件不存在：$filePath" }
                            append(
                                fileField,
                                file.readBytes(),
                                Headers.build {
                                    append(HttpHeaders.ContentType, mimeFor(file.name))
                                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                                },
                            )
                        }
                    }
                )
            )
        }
        saveImageOrText(url, response, saveBinary)
    }

    override fun readBase64(path: String): String {
        requireSharedStorageAccess(path)
        val file = File(path)
        require(file.exists()) { "文件不存在：$path" }
        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        return "data:${mimeFor(file.name)};base64,$encoded"
    }

    override fun saveBase64(data: String, outputPath: String): String {
        requireSharedStorageAccess(outputPath)
        val payload = data.substringAfter("base64,", data).trim()
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        val file = File(outputPath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /** 图片响应落盘；其余一律按文本返回。 */
    private suspend fun saveImageOrText(url: String, response: HttpResponse, saveBinary: Boolean): String {
        val contentType = response.contentType()
        val isImage = contentType?.contentType?.equals("image", ignoreCase = true) == true
        if (!saveBinary || !isImage) return response.bodyAsText()

        val bytes = response.readRawBytes()
        val subtype = contentType?.contentSubtype.orEmpty().lowercase()
        val extension = when (subtype) {
            "jpeg", "jpg" -> "jpg"
            "png", "webp", "gif", "bmp", "svg" -> subtype
            else -> subtype.ifBlank { "png" }.replace(Regex("[^a-z0-9]"), "").ifBlank { "png" }
        }
        val output = File("/sdcard/Download", "zenneko_${apiName(url)}_${System.currentTimeMillis()}.$extension")
        requireSharedStorageAccess(output.absolutePath)
        output.parentFile?.mkdirs()
        output.writeBytes(bytes)
        return output.absolutePath
    }

    private fun apiName(url: String): String {
        val name = url.substringAfterLast('/').substringBefore('?').removeSuffix(".php")
        return name.replace(Regex("[^A-Za-z0-9_]+"), "_").ifBlank { "api" }
    }

    private fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "heic" -> "image/heic"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
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

    override fun listFiles(path: String): String {
        requireSharedStorageAccess(path)
        val dir = File(path)
        require(dir.isDirectory) { "不是目录：$path" }
        val array = JSONArray()
        dir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { file ->
            array.put(JSONObject().apply {
                put("name", file.name)
                put("path", file.absolutePath)
                put("is_dir", file.isDirectory)
                put("size", file.length())
                put("modified", file.lastModified())
            })
        }
        return array.toString()
    }

    override fun copyPath(src: String, dst: String) {
        requireSharedStorageAccess(src, dst)
        val source = File(src)
        require(source.exists()) { "不存在：$src" }
        source.copyRecursively(File(dst), overwrite = true)
    }

    override fun deletePath(path: String, recursive: Boolean): Boolean {
        requireSharedStorageAccess(path)
        val target = File(path)
        require(target.exists()) { "不存在：$path" }
        return if (recursive) target.deleteRecursively() else target.delete()
    }

    override fun mkdir(path: String) {
        requireSharedStorageAccess(path)
        val dir = File(path)
        require(dir.mkdirs() || dir.isDirectory) { "创建目录失败：$path" }
    }

    override fun renamePath(src: String, dst: String) {
        requireSharedStorageAccess(src, dst)
        require(File(src).renameTo(File(dst))) { "重命名失败：$src -> $dst" }
    }

    override fun zip(paths: List<String>, output: String): String {
        require(paths.isNotEmpty()) { "没有待压缩的文件" }
        requireSharedStorageAccess(*(paths + output).toTypedArray())
        ZipOutputStream(FileOutputStream(output).buffered()).use { zos ->
            paths.forEach { path ->
                val file = File(path)
                require(file.exists()) { "不存在：$path" }
                val base = file.parentFile ?: File("/")
                file.walkTopDown().forEach { current ->
                    val entryName = current.relativeTo(base).path + if (current.isDirectory) "/" else ""
                    zos.putNextEntry(ZipEntry(entryName))
                    if (current.isFile) current.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return output
    }

    override fun unzip(zipPath: String, outputDir: String): String {
        requireSharedStorageAccess(zipPath, outputDir)
        val out = File(outputDir)
        out.mkdirs()
        val canonicalOut = out.canonicalPath + File.separator
        ZipInputStream(FileInputStream(zipPath).buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val target = File(out, entry.name)
                require(target.canonicalPath.startsWith(canonicalOut)) { "非法压缩包路径：${entry.name}" }
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

    override fun sleep(seconds: Int) {
        Thread.sleep(seconds.coerceIn(0, 120) * 1000L)
    }

    override fun setClipboard(text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("KhatKit", text))
    }

    override fun getClipboard(): String {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = manager.primaryClip ?: return ""
        return clip.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    }

    override fun wakeScreen(): String {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (power.isInteractive) return "屏幕已点亮"
        return try {
            @Suppress("DEPRECATION")
            val wakeLock = power.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "khatkit:wakeScreen",
            )
            wakeLock.acquire(3_000L)
            "屏幕已点亮"
        } catch (e: SecurityException) {
            "点亮屏幕失败：${e.message ?: "缺少权限"}\n" +
                "请授予 WAKE_LOCK 权限，或用 Shizuku/root 执行 input keyevent KEYCODE_WAKEUP"
        }
    }

    override fun ocrText(path: String): String {
        requireSharedStorageAccess(path)
        val file = File(path)
        require(file.exists()) { "文件不存在：$path" }
        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            Tasks.await(ocrRecognizer.process(image), OCR_TIMEOUT_SEC, TimeUnit.SECONDS).text
        } catch (e: Exception) {
            if (e is InterruptedException) Thread.currentThread().interrupt()
            JSONObject().put("error", "OCR 失败：${e.message ?: e.javaClass.simpleName}").toString()
        }
    }

    /**
     * 带坐标 OCR：按行返回识别文本与外接矩形。
     * `InputImage.fromFilePath` 已按 EXIF 方向把位图转正，boundingBox 即该位图像素坐标。
     */
    override fun ocrBoxes(path: String): String {
        requireSharedStorageAccess(path)
        val file = File(path)
        require(file.exists()) { "文件不存在：$path" }
        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val result = Tasks.await(ocrRecognizer.process(image), OCR_TIMEOUT_SEC, TimeUnit.SECONDS)
            val array = JSONArray()
            result.textBlocks.forEach { block ->
                val lines = block.lines
                if (lines.isEmpty()) {
                    block.boundingBox?.let { array.put(boxJson(block.text, it)) }
                } else {
                    lines.forEach { line ->
                        line.boundingBox?.let { array.put(boxJson(line.text, it)) }
                    }
                }
            }
            array.toString()
        } catch (e: Exception) {
            if (e is InterruptedException) Thread.currentThread().interrupt()
            JSONObject().put("error", "OCR 失败：${e.message ?: e.javaClass.simpleName}").toString()
        }
    }

    private fun boxJson(text: String, box: android.graphics.Rect): JSONObject =
        JSONObject()
            .put("text", text)
            .put("x", box.left)
            .put("y", box.top)
            .put("w", box.width())
            .put("h", box.height())

    private val ocrRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    override fun openDir(path: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("file://$path"), "resource/folder")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }

    private companion object {
        const val OCR_TIMEOUT_SEC = 30L
    }
}
