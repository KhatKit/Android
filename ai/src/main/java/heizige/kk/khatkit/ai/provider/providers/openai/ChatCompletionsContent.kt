package heizige.kk.khatkit.ai.provider.providers.openai

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import heizige.kk.khatkit.common.http.jsonArrayOrNull
import heizige.kk.khatkit.common.http.jsonObjectOrNull
import heizige.kk.khatkit.common.http.jsonPrimitiveOrNull

internal data class ChatCompletionsContent(
    val text: String,
    val reasoning: String?,
)

/**
 * Chat Completions 的 content 可能是字符串，也可能是 Mistral 的 thinking/text 块列表。
 * 必须遍历所有块，因为流式响应可能在同一帧中同时结束 thinking 并开始 text。
 */
internal fun parseChatCompletionsContent(content: JsonElement?): ChatCompletionsContent {
    content?.jsonPrimitiveOrNull?.contentOrNull?.let {
        return ChatCompletionsContent(it, null)
    }
    val chunks = content?.jsonArrayOrNull ?: return ChatCompletionsContent("", null)
    val text = StringBuilder()
    val reasoning = StringBuilder()
    chunks.forEach { element ->
        val chunk = element.jsonObjectOrNull ?: return@forEach
        when (chunk["type"]?.jsonPrimitiveOrNull?.contentOrNull) {
            "text" -> text.append(chunk["text"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty())
            "thinking" -> chunk["thinking"]?.jsonArrayOrNull?.forEach { inner ->
                val innerChunk = inner.jsonObjectOrNull ?: return@forEach
                if (innerChunk["type"]?.jsonPrimitiveOrNull?.contentOrNull == "text") {
                    reasoning.append(innerChunk["text"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty())
                }
            }
        }
    }
    return ChatCompletionsContent(text.toString(), reasoning.toString().ifEmpty { null })
}
