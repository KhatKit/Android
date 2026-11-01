package heizige.kk.khatkit.app.feature.tools.helper

import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.LocaleMapResolver

internal enum class ToolCategory(val title: String, val icon: String) {
    IMAGE("图片处理", "image"),
    MEDIA("影音阅读", "play-circle"),
    TEXT("文字办公", "document-text"),
    FILE("文件管理", "folder"),
    WEB("网络工具", "globe"),
    DEVICE("设备管理", "phone-portrait"),
    LIFE("生活服务", "cafe"),
    DEVELOPMENT("开发工具", "code-slash"),
    OTHER("其他工具", "apps"),
}

internal fun CardIndexEntry.toolTitle(locales: List<String>): String =
    LocaleMapResolver.resolve(displayName, locales).ifBlank {
        // 老市场没有标题字段，中文简介的第一句通常就是功能名称。
        description.takeIf { it.any { c -> c in '\u4e00'..'\u9fff' } }
            ?.substringBefore('。')?.substringBefore('：')?.takeIf { it.length in 2..16 }
            ?: name.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

internal fun CardIndexEntry.toolDescription(locales: List<String>): String =
    LocaleMapResolver.resolve(descriptionI18n, locales).ifBlank { description }
        .takeUnless { it.startsWith("@string/") }.orEmpty()

/** 优先使用明确的功能名称，再用市场的领域标签兜底，避免所有图片工具挤进「影音」。 */
internal fun CardIndexEntry.toolCategory(): ToolCategory {
    val identity = name.lowercase()
    val words = "$identity $description".lowercase()
    return when {
        listOf("image", "photo", "picture", "cutout", "upscale", "removeobject", "图片", "图像", "抠图").any(words::contains) -> ToolCategory.IMAGE
        listOf("translate", "converter", "pdf", "ocr", "document", "翻译", "文档", "文字").any(words::contains) -> ToolCategory.TEXT
        listOf("kuaidi", "caipu", "weather", "快递", "菜谱", "天气").any(words::contains) -> ToolCategory.LIFE
        listOf("novel", "video", "audio", "music", "aggregate", "小说", "视频", "音乐", "阅读").any(words::contains) -> ToolCategory.MEDIA
        else -> when (tags?.domain?.lowercase()) {
            "media", "reading", "audio", "video" -> ToolCategory.MEDIA
            "text", "office", "document" -> ToolCategory.TEXT
            "file", "files", "storage" -> ToolCategory.FILE
            "net", "network", "web" -> ToolCategory.WEB
            "app", "device", "system" -> ToolCategory.DEVICE
            "life", "daily", "weather" -> ToolCategory.LIFE
            "dev", "code", "development", "ai" -> ToolCategory.DEVELOPMENT
            else -> ToolCategory.OTHER
        }
    }
}

internal fun CardIndexEntry.matchesToolQuery(query: String, locales: List<String>): Boolean {
    val text = "$name ${toolTitle(locales)} ${toolDescription(locales)} $summary " +
        "${tags?.domain.orEmpty()} ${tags?.action.orEmpty()} ${tags?.scene.orEmpty()} ${toolCategory().title}"
    return query.trim().split(Regex("\\s+")).all { text.contains(it, ignoreCase = true) }
}

internal fun CardIndexEntry.isUserTool(): Boolean = hasWidget && "user" in triggers
