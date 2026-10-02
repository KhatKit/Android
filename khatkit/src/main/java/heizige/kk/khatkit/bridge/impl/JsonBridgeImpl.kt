package heizige.kk.khatkit.bridge.impl

import heizige.kk.khatkit.bridge.JsonBridge
import heizige.kk.khatkit.engine.JsonValues
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** 基于项目现有 JsonValues 的结构化 JSON 实现。 */
class JsonBridgeImpl : JsonBridge {
    override fun decode(text: String): Any? = runCatching {
        JsonValues.fromElement(JsonValues.json.parseToJsonElement(text))
    }.getOrElse { throw IllegalArgumentException("JSON 解码失败：${it.message ?: "格式错误"}") }

    override fun encode(value: Any?): String = JsonValues.encode(value)

    override fun query(text: String, path: String, default: String?): String? {
        val root = runCatching { JsonValues.json.parseToJsonElement(text) }
            .getOrElse { throw IllegalArgumentException("JSON 查询失败：${it.message ?: "格式错误"}") }
        var current: JsonElement = root
        path.split('.').filter { it.isNotBlank() }.forEach { segment ->
            val match = Regex("""([^\[]+)|\[(\d+)]""").findAll(segment)
            match.forEach { token ->
                current = token.groups[2]?.value?.toIntOrNull()?.let { index ->
                    (current as? JsonArray)?.getOrNull(index)
                } ?: (current as? JsonObject)?.get(token.groups[1]?.value)
                    ?: return default
            }
        }
        return when (current) {
            is JsonPrimitive -> current.content
            else -> JsonValues.encode(JsonValues.fromElement(current))
        }
    }

    override fun merge(base: String, patch: String): String {
        val left = JsonValues.json.parseToJsonElement(base).jsonObject.toMutableMap()
        val right = JsonValues.json.parseToJsonElement(patch).jsonObject
        left.putAll(right)
        return JsonValues.encode(left.mapValues { JsonValues.fromElement(it.value) })
    }

    override fun pluck(text: String, keys: List<String>): Map<String, Any?> {
        val obj = JsonValues.json.parseToJsonElement(text).jsonObject
        return keys.associateWith { key -> obj[key]?.let(JsonValues::fromElement) }
    }

    override fun pretty(text: String): String = runCatching {
        JsonValues.json.parseToJsonElement(text).toString()
    }.getOrElse { throw IllegalArgumentException("JSON 格式化失败：${it.message ?: "格式错误"}") }
}
