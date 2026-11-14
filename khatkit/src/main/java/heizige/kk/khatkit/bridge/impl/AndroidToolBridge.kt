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
import heizige.kk.khatkit.bridge.ToolBridge
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
) : ToolBridge {
@Deprecated("请改用 fs.read")
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

@Deprecated("请改用 fs.write")
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

@Deprecated("请改用 fs.readBase64")
    override fun readBase64(path: String): String {
        requireSharedStorageAccess(path)
        val file = File(path)
        require(file.exists()) { "文件不存在：$path" }
        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        return "data:${mimeFor(file.name)};base64,$encoded"
    }

@Deprecated("请改用 fs.saveBase64")
    override fun saveBase64(data: String, outputPath: String): String {
        requireSharedStorageAccess(outputPath)
        val payload = data.substringAfter("base64,", data).trim()
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        val file = File(outputPath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file.absolutePath
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

@Deprecated("请改用 fs.list")
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

@Deprecated("请改用 fs.copy")
    override fun copyPath(src: String, dst: String) {
        requireSharedStorageAccess(src, dst)
        val source = File(src)
        require(source.exists()) { "不存在：$src" }
        source.copyRecursively(File(dst), overwrite = true)
    }

@Deprecated("请改用 fs.delete（fs.delete 会先向用户确认）")
    override fun deletePath(path: String, recursive: Boolean): Boolean {
        requireSharedStorageAccess(path)
        val target = File(path)
        require(target.exists()) { "不存在：$path" }
        return if (recursive) target.deleteRecursively() else target.delete()
    }

@Deprecated("请改用 fs.mkdir")
    override fun mkdir(path: String) {
        requireSharedStorageAccess(path)
        val dir = File(path)
        require(dir.mkdirs() || dir.isDirectory) { "创建目录失败：$path" }
    }

@Deprecated("请改用 fs.move")
    override fun renamePath(src: String, dst: String) {
        requireSharedStorageAccess(src, dst)
        require(File(src).renameTo(File(dst))) { "重命名失败：$src -> $dst" }
    }

@Deprecated("请改用 fs.zip")
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

@Deprecated("请改用 fs.unzip")
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
        if (!ClipboardAccess.isEnabled(context)) {
            throw SecurityException("剪贴板读取已在「设置 → 权限」中关闭")
        }
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // Android 10+ / ColorOS 等 ROM：没有「读取剪贴板」授权的应用会拿到 null，
        // 之前直接 return "" 会让卡片显示「已粘贴分享内容」但框里是空的。这里给出可解释的错误。
        val clip = manager.primaryClip
            ?: throw SecurityException("系统未允许读取剪贴板：请在系统设置里授予 KhatKit 剪贴板权限后重试")
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
            val engine = ocrEngineOrNull() ?: return ocrMissingPluginJson()
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                ?: return JSONObject().put("error", "OCR 失败：无法解码图片 / cannot decode image").toString()
            kotlinx.coroutines.runBlocking { engine.recognize(bitmap) }.text
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
            val engine = ocrEngineOrNull() ?: return ocrMissingPluginJson()
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                ?: return JSONObject().put("error", "OCR 失败：无法解码图片 / cannot decode image").toString()
            val result = kotlinx.coroutines.runBlocking { engine.recognize(bitmap) }
            val array = JSONArray()
            result.blocks.forEach { b ->
                array.put(boxJson(b.text, android.graphics.Rect(b.left, b.top, b.right, b.bottom)))
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

    /** OCR1：真实实现在 plugins/ocr-mlkit 插件里；没装插件时返回 null，走降级文案。 */
    private fun ocrEngineOrNull(): heizige.kk.khatkit.ocr.api.OcrEngine? =
        heizige.kk.khatkit.ocr.api.OcrEngineProvider.engine()

    private fun ocrMissingPluginJson(): String = JSONObject()
        .put("error", "OCR 需要安装 OCR 插件 / OCR requires the OCR plugin")
        .toString()

@Deprecated("请改用 fs.openDir")
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
