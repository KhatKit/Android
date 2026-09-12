package heizige.kk.khatkit.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Kotlin <-> JSON 转换，供 native 引擎与 JS 引擎共用。 */
internal object JsonValues {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(value: Any?): String =
        json.encodeToString(JsonElement.serializer(), toElement(value))

    fun toElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toElement(v) })
        is Iterable<*> -> JsonArray(value.map { toElement(it) })
        is Array<*> -> JsonArray(value.map { toElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    fun fromElement(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.content == "true" || element.content == "false" -> element.content.toBoolean()
            element.content.contains('.') -> element.content.toDoubleOrNull()
            else -> element.content.toLongOrNull() ?: element.content
        }
        is JsonArray -> element.map { fromElement(it) }
        is JsonObject -> element.mapValues { fromElement(it.value) }
    }
}
