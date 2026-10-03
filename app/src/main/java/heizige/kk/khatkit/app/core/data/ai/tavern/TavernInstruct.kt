package heizige.kk.khatkit.app.core.data.ai.tavern

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.model.Assistant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Instruct / sampler subset. Prompt order is system, then chat messages.
 * Sequences wrap each turn only when `wrap` is true. Unknown fields pass through.
 */
object TavernInstruct {
    private val json = Json { encodeDefaults = false }

    fun parse(raw: String): InstructTemplate {
        val root = json.parseToJsonElement(raw).jsonObject
        return InstructTemplate(
            systemSequence = root.string("system_sequence").orEmpty(),
            systemSuffix = root.string("system_suffix").orEmpty(),
            inputSequence = root.string("input_sequence").orEmpty(),
            inputSuffix = root.string("input_suffix").orEmpty(),
            outputSequence = root.string("output_sequence").orEmpty(),
            outputSuffix = root.string("output_suffix").orEmpty(),
            stopSequence = root.string("stop_sequence").orEmpty(),
            wrap = root["wrap"]?.jsonPrimitive?.booleanOrNull != false,
            raw = root,
        )
    }

    fun export(template: InstructTemplate): String {
        val updated = template.raw.toMutableMap()
        updated["system_sequence"] = JsonPrimitive(template.systemSequence)
        updated["system_suffix"] = JsonPrimitive(template.systemSuffix)
        updated["input_sequence"] = JsonPrimitive(template.inputSequence)
        updated["input_suffix"] = JsonPrimitive(template.inputSuffix)
        updated["output_sequence"] = JsonPrimitive(template.outputSequence)
        updated["output_suffix"] = JsonPrimitive(template.outputSuffix)
        updated["stop_sequence"] = JsonPrimitive(template.stopSequence)
        updated["wrap"] = JsonPrimitive(template.wrap)
        return json.encodeToString(JsonElement.serializer(), JsonObject(updated))
    }

    fun apply(messages: List<UIMessage>, template: InstructTemplate): String = buildString {
        messages.forEach { message ->
            val text = message.toText()
            if (!template.wrap) {
                append(text)
                return@forEach
            }
            when (message.role) {
                MessageRole.SYSTEM -> append(template.systemSequence).append(text).append(template.systemSuffix)
                MessageRole.USER -> append(template.inputSequence).append(text).append(template.inputSuffix)
                MessageRole.ASSISTANT -> append(template.outputSequence).append(text).append(template.outputSuffix)
                else -> append(text)
            }
        }
    }
}

data class InstructTemplate(
    val systemSequence: String,
    val systemSuffix: String,
    val inputSequence: String,
    val inputSuffix: String,
    val outputSequence: String,
    val outputSuffix: String,
    val stopSequence: String,
    val wrap: Boolean,
    val raw: JsonObject,
)

object TavernSamplerPreset {
    private val json = Json { encodeDefaults = false }

    fun parse(raw: String): SamplerPreset {
        val root = json.parseToJsonElement(raw).jsonObject
        return SamplerPreset(
            temperature = root["temperature"]?.jsonPrimitive?.floatOrNull,
            topP = root["top_p"]?.jsonPrimitive?.floatOrNull,
            topK = root["top_k"]?.jsonPrimitive?.intOrNull,
            maxTokens = root["max_tokens"]?.jsonPrimitive?.intOrNull
                ?: root["openai_max_tokens"]?.jsonPrimitive?.intOrNull,
            raw = root,
        )
    }

    fun export(preset: SamplerPreset): String {
        val updated = preset.raw.toMutableMap()
        preset.temperature?.let { updated["temperature"] = JsonPrimitive(it) }
        preset.topP?.let { updated["top_p"] = JsonPrimitive(it) }
        preset.topK?.let { updated["top_k"] = JsonPrimitive(it) }
        preset.maxTokens?.let { updated["max_tokens"] = JsonPrimitive(it) }
        return json.encodeToString(JsonElement.serializer(), JsonObject(updated))
    }

    fun apply(assistant: Assistant, preset: SamplerPreset): Assistant = assistant.copy(
        temperature = preset.temperature ?: assistant.temperature,
        topP = preset.topP ?: assistant.topP,
        maxTokens = preset.maxTokens ?: assistant.maxTokens,
    )
}

data class SamplerPreset(
    val temperature: Float?,
    val topP: Float?,
    val topK: Int?,
    val maxTokens: Int?,
    val raw: JsonObject,
)

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
