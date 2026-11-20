package heizige.kk.khatkit.app.core.data.ai.transformers

import java.io.File
import java.net.URI
import java.util.Locale

/**
 * 文本模型的附图说明：给出本机绝对路径与类型/大小，便于模型直接调用工具处理。
 * 例：[用户附带图片: /data/user/0/.../files/upload/a.jpg，类型 image/jpeg，大小 123.4 KB]
 */
internal fun imageAttachmentNote(url: String, fromUser: Boolean = true): String? {
    val file = localImageFile(url)?.takeIf { it.isFile } ?: return null
    val mime = imageMimeType(file.name) ?: "image/*"
    return buildString {
        append(if (fromUser) "[用户附带图片: " else "[图片: ").append(file.absolutePath)
        append("，类型 ").append(mime)
        append("，大小 ").append(formatFileSize(file.length()))
        append("]")
    }
}

/** file:// URI 或裸绝对路径 → 本机文件；其它（http/data/content）返回 null。 */
internal fun localImageFile(url: String): File? = when {
    url.startsWith("file:") -> runCatching { File(URI(url)) }.getOrNull()
    url.startsWith("/") || File(url).isAbsolute -> File(url)
    else -> null
}

internal const val IMAGE_PATH_HINT_PREFIX = "[本地图片附件，可直接用于图片处理]"

/**
 * 给当前轮的模型与工具提供其各自可访问的图片路径。
 * 图片保存在应用的 files/upload 目录时，工作区 shell 通过 /upload 挂载访问；
 * KhatKit 卡片则在宿主侧使用绝对路径。
 */
internal fun imageAttachmentPathHint(urls: List<String>, uploadDirectory: File): String? {
    val files = urls.mapIndexedNotNull { index, url ->
        val file = localImageFile(url)?.takeIf { it.isFile } ?: return@mapIndexedNotNull null
        val inUploadDirectory = runCatching {
            file.canonicalFile.parentFile == uploadDirectory.canonicalFile
        }.getOrDefault(false)
        Triple(index + 1, file, inUploadDirectory)
    }
    if (files.isEmpty()) return null

    return buildString {
        appendLine(IMAGE_PATH_HINT_PREFIX)
        appendLine("不要向用户索要路径。KhatKit 图像卡片使用 host 路径；如 workspace_shell 可用，则使用 workspace 路径。")
        files.forEach { (index, file, inUploadDirectory) ->
            appendLine("image_${index}_host=${file.absolutePath}")
            if (inUploadDirectory) appendLine("image_${index}_workspace=/upload/${file.name}")
        }
    }.trimEnd()
}

internal fun imageMimeType(fileName: String): String? =
    when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "heic", "heif" -> "image/heic"
        "avif" -> "image/avif"
        else -> null
    }

/**
 * 非本地图片（内联 base64 / 远程 URL）的简短占位。
 * 绝不把 data: 的 base64 整段塞进文本，只保留类型与体积估计。
 */
internal fun imageAttachmentLabel(url: String, fromUser: Boolean = true): String {
    val label = if (fromUser) "用户附带图片" else "图片"
    val source = when {
        url.startsWith("data:") -> {
            val mime = url.substringAfter("data:").substringBefore(';').ifBlank { "image" }
            "$mime（内联 base64 已省略，约 ${formatFileSize(estimatedBase64Bytes(url))}）"
        }

        url.isBlank() -> "无地址"
        else -> url
    }
    return "[$label: $source]"
}

/** data: URL 的 base64 载荷近似字节数（4 字符 3 字节，扣除末尾 = 填充）。 */
internal fun estimatedBase64Bytes(dataUrl: String): Long {
    val payload = dataUrl.substringAfter(',', "")
    if (payload.isEmpty()) return 0L
    val padding = payload.takeLastWhile { it == '=' }.length
    return (payload.length.toLong() * 3 / 4 - padding).coerceAtLeast(0L)
}

internal fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0)
}
