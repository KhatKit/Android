package heizige.kk.khatkit.app.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart

data class GeneratedCard(
    val name: String,
    val version: String = "1.0.0",
    val manifestJson: String,
    val scriptText: String,
)

/**
 * 把一段对话存成卡片（设计文档 15.2 第 13 问）。
 *
 * 生成的卡片只依赖 ui bridge：运行后用 `ui.show` 展示存档内容，
 * 既是「把对话变成可复用资产」的最小闭环，也不需要任何额外权限。
 */
object ConversationCardFactory {

    fun create(
        title: String,
        messages: List<UIMessage>,
        nowMillis: Long = System.currentTimeMillis(),
    ): GeneratedCard {
        val safeTitle = title.ifBlank { "对话存档" }
        val name = buildName(safeTitle, nowMillis)
        val markdown = buildTranscript(safeTitle, messages)

        val manifest = buildJsonObject {
            put("name", name)
            put("version", "1.0.0")
            put("description", "对话存档：$safeTitle")
            put("author", "user")
            put("license", "MIT")
            put("engine", "js")
            putJsonObject("entry") { put("js", "main.js") }
            put("privilege", "none")
            putJsonObject("requires") {
                putJsonArray("bridges") { add("ui") }
                putJsonArray("libs") {}
                putJsonArray("bins") {}
                putJsonArray("env") {}
            }
            putJsonObject("network") { putJsonArray("allow") {} }
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {}
            }
            putJsonObject("tags") {
                put("domain", "text")
                put("action", "read")
                put("scene", "daily")
            }
            putJsonObject("store") {
                put("quota_mb", 5)
                put("secret", false)
            }
        }

        val script = buildString {
            appendLine("// 对话存档卡片：运行后展示存档内容")
            appendLine("ui.show({")
            appendLine("  title: ${jsString(safeTitle)},")
            appendLine("  markdown: ${jsString(markdown)}")
            appendLine("});")
            appendLine("return { shown: true, name: ${jsString(name)} };")
        }

        return GeneratedCard(name = name, manifestJson = manifest.toString(), scriptText = script)
    }

    internal fun buildName(title: String, nowMillis: Long): String {
        val slug = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(24)
            .ifBlank { "note" }
        return "chat_${slug}_${nowMillis.toString().takeLast(4)}"
    }

    internal fun buildTranscript(title: String, messages: List<UIMessage>): String = buildString {
        appendLine("# $title")
        appendLine()
        messages.forEach { message ->
            val text = message.parts
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("\n") { it.text }
                .trim()
            if (text.isNotEmpty()) {
                appendLine("**${message.role.name.lowercase()}**")
                appendLine()
                appendLine(text)
                appendLine()
            }
        }
    }

    private fun jsString(value: String): String =
        Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(value))
}
