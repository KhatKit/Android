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
    url.startsWith("file:") -> runCatching { URI(url).path }.getOrNull()?.let(::File)
    url.startsWith("/") -> File(url)
    else -> null
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
