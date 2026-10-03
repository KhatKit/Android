package heizige.kk.khatkit.app.core.data.ai.tavern

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.model.MessageNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * SillyTavern chat files are JSONL: the first line is metadata without `mes`,
 * and each later line is one message. A JSON array with the same objects is also accepted.
 * Swipes map onto [MessageNode] branches. Fields outside this subset are copied back.
 */
object TavernChatCodec {
    private val json = Json { encodeDefaults = false }

    fun import(raw: String): TavernChatDocument {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            throw IllegalArgumentException("unsupported_chat")
        }
        if (!trimmed.startsWith("[")) {
            val asObject = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
            val wrapped = asObject?.get("messages")?.jsonArray
            if (asObject != null && wrapped != null) {
                return importObjects(listOf(asObject) + wrapped.map { it.jsonObject })
            }
            return importLines(trimmed.lineSequence().filter { it.isNotBlank() }.toList())
        }
        val root = json.parseToJsonElement(trimmed)
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root["messages"]?.jsonArray ?: throw IllegalArgumentException("missing_messages")
            else -> throw IllegalArgumentException("unsupported_chat")
        }
        if (array.isEmpty()) return TavernChatDocument(JsonObject(emptyMap()), emptyList())
        val first = array.first().jsonObject
        val header = if (first["mes"] == null && first["swipes"] == null) first else JsonObject(emptyMap())
        val messages = if (header.isEmpty()) array else JsonArray(array.drop(1))
        return TavernChatDocument(
            header = header,
            messages = messages.map { element ->
                val obj = element.jsonObject
                val swipes = obj["swipes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.ifEmpty { null }
                    ?: listOf(obj.string("mes").orEmpty())
                val selected = obj["swipe_id"]?.jsonPrimitive?.intOrNull?.coerceIn(0, swipes.lastIndex) ?: 0
                val role = when {
                    obj["is_system"]?.jsonPrimitive?.booleanOrNull == true -> MessageRole.SYSTEM
                    obj["is_user"]?.jsonPrimitive?.booleanOrNull == true -> MessageRole.USER
                    else -> MessageRole.ASSISTANT
                }
                TavernChatMessage(
                    node = MessageNode(
                        messages = swipes.map { text -> UIMessage.of(role, text) },
                        selectIndex = selected,
                    ),
                    raw = obj,
                )
            },
        )
    }

    fun export(document: TavernChatDocument): String {
        val messages = document.messages.map { message ->
            val swipes = message.node.messages.map { it.toText() }
            if (swipes.isEmpty()) return@map message.raw
            val selectedIndex = message.node.selectIndex.coerceIn(0, swipes.lastIndex)
            val selected = message.node.messages[selectedIndex]
            val updated = message.raw.toMutableMap()
            updated["name"] = JsonPrimitive(message.raw.string("name") ?: selected.role.name)
            updated["is_user"] = JsonPrimitive(selected.role == MessageRole.USER)
            updated["is_system"] = JsonPrimitive(selected.role == MessageRole.SYSTEM)
            updated["mes"] = JsonPrimitive(selected.toText())
            updated["swipes"] = JsonArray(swipes.map { JsonPrimitive(it) })
            updated["swipe_id"] = JsonPrimitive(selectedIndex)
            JsonObject(updated)
        }
        val header = document.header.ifEmpty {
            buildJsonObject {
                put("spec", "st_chat_v1")
            }
        }
        return json.encodeToString(JsonElement.serializer(), JsonArray(listOf(header) + messages))
    }

    /** One JSON object per line, the form SillyTavern writes under `chats/`. */
    fun exportJsonl(document: TavernChatDocument): String {
        val array = json.parseToJsonElement(export(document)).jsonArray
        return array.joinToString("\n") { json.encodeToString(JsonElement.serializer(), it) }
    }

    private fun importLines(lines: List<String>): TavernChatDocument {
        if (lines.isEmpty()) return TavernChatDocument(JsonObject(emptyMap()), emptyList())
        val objects = lines.map { json.parseToJsonElement(it).jsonObject }
        return importObjects(objects)
    }

    private fun importObjects(objects: List<JsonObject>): TavernChatDocument {
        if (objects.isEmpty()) return TavernChatDocument(JsonObject(emptyMap()), emptyList())
        val header = if (objects.first()["mes"] == null && objects.first()["swipes"] == null) {
            objects.first()
        } else {
            JsonObject(emptyMap())
        }
        val messages = if (header.isEmpty()) objects else objects.drop(1)
        return documentFrom(header, messages)
    }

    private fun documentFrom(header: JsonObject, messages: List<JsonObject>): TavernChatDocument =
        TavernChatDocument(
            header = header,
            messages = messages.map { obj ->
                val swipes = obj["swipes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.ifEmpty { null }
                    ?: listOf(obj.string("mes").orEmpty())
                val selected = obj["swipe_id"]?.jsonPrimitive?.intOrNull?.coerceIn(0, swipes.lastIndex) ?: 0
                val role = when {
                    obj["is_system"]?.jsonPrimitive?.booleanOrNull == true -> MessageRole.SYSTEM
                    obj["is_user"]?.jsonPrimitive?.booleanOrNull == true -> MessageRole.USER
                    else -> MessageRole.ASSISTANT
                }
                TavernChatMessage(
                    node = MessageNode(
                        messages = swipes.map { text -> UIMessage.of(role, text) },
                        selectIndex = selected,
                    ),
                    raw = obj,
                )
            },
        )

    fun exportNodes(
        nodes: List<MessageNode>,
        userName: String,
        characterName: String,
    ): String = export(
        TavernChatDocument(
            header = buildJsonObject {
                put("spec", "st_chat_v1")
                put("user_name", userName)
                put("character_name", characterName)
            },
            messages = nodes.map { node ->
                val role = node.messages.firstOrNull()?.role
                TavernChatMessage(
                    node = node,
                    raw = buildJsonObject {
                        put("name", if (role == MessageRole.USER) userName else characterName)
                    },
                )
            },
        )
    )
}

data class TavernChatDocument(
    val header: JsonObject,
    val messages: List<TavernChatMessage>,
)

data class TavernChatMessage(
    val node: MessageNode,
    val raw: JsonObject,
)

private fun UIMessage.Companion.of(role: MessageRole, text: String): UIMessage = when (role) {
    MessageRole.USER -> user(text)
    MessageRole.SYSTEM -> system(text)
    else -> assistant(text)
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
