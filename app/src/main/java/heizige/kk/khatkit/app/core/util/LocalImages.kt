package heizige.kk.khatkit.app.core.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.net.URI

/** 可直传 Coil 渲染的本机图片扩展名。 */
private val LOCAL_IMAGE_EXTENSIONS =
    setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")

private const val LOCAL_IMAGE_MAX_PATH_CHARS = 500

/** 递归扫描的最大深度：Map/List 自引用（cycle）时超过即停止，保证扫描一定终止。 */
private const val LOCAL_IMAGE_SCAN_MAX_DEPTH = 32

/** 工具结果单次最多附带的图片张数，避免一次渲染过多缩略图。 */
const val LOCAL_IMAGE_RESULT_LIMIT = 8

/**
 * 把「file:// URI 或裸绝对路径」解析成本机真实存在的图片文件；
 * http(s)/data/content URI、相对路径、其它扩展名与不存在的文件一律返回 null。
 */
fun localImageFileOf(raw: String): File? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.length > LOCAL_IMAGE_MAX_PATH_CHARS) return null
    val path = when {
        trimmed.startsWith("file:", ignoreCase = true) ->
            runCatching { URI(trimmed).path }.getOrNull()

        trimmed.startsWith("/") -> trimmed
        else -> null
    } ?: return null
    val extension = path.substringAfterLast('.', "").lowercase()
    if (extension !in LOCAL_IMAGE_EXTENSIONS) return null
    return File(path).takeIf { it.isFile }
}

/** 从任意嵌套结构（Map/List/数组/JSON 字符串与对象）里提取本机图片路径，去重、保序、限量。 */
fun collectLocalImagePaths(value: Any?, limit: Int = LOCAL_IMAGE_RESULT_LIMIT): List<String> {
    if (limit <= 0) return emptyList()
    val found = LinkedHashSet<String>()

    fun scan(element: Any?, depth: Int) {
        if (found.size >= limit || depth > LOCAL_IMAGE_SCAN_MAX_DEPTH) return
        when (element) {
            null -> Unit
            is String -> localImageFileOf(element)?.let { found += it.absolutePath }
            is JsonPrimitive -> if (element.isString) scan(element.content, depth + 1)
            is JsonObject -> element.values.forEach { scan(it, depth + 1) }
            is JsonArray -> element.forEach { scan(it, depth + 1) }
            is Map<*, *> -> element.values.forEach { scan(it, depth + 1) }
            is Iterable<*> -> element.forEach { scan(it, depth + 1) }
            is Array<*> -> element.forEach { scan(it, depth + 1) }
            else -> Unit
        }
    }

    scan(value, 0)
    return found.take(limit)
}

/** 整行只含一个本机图片路径（允许 file://、反引号/引号包裹）时返回该文件。 */
private fun localImagePathLine(line: String): File? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.length > LOCAL_IMAGE_MAX_PATH_CHARS) return null
    val unwrapped = trimmed.trim('`', '"', '\'', '“', '”', '「', '」', '『', '』')
    return localImageFileOf(unwrapped)
}

/**
 * 把聊天文本中「单独成行且真实存在」的本机图片路径转成 Markdown 图片语法，让回复直接渲染缩略图；
 * 代码块内、混有其它文字的行、URL 与不存在的路径都保持原文。
 */
fun expandLocalImagePathLines(content: String): String {
    if (!content.contains('/')) return content
    var inCodeFence = false
    var changed = false
    val lines = content.split('\n').map { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inCodeFence = !inCodeFence
            return@map line
        }
        if (inCodeFence || line.startsWith("    ") || line.startsWith("\t")) return@map line
        val file = localImagePathLine(trimmed) ?: return@map line
        changed = true
        "![${file.name.replace("]", "")}](${file.toURI()})"
    }
    return if (changed) lines.joinToString("\n") else content
}
