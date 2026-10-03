package heizige.kk.khatkit.app.core.data.ai.tavern

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.model.Assistant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * chara_card_v2 / v3 round-trip. Unknown fields stay on the original JSON tree and are written
 * back unchanged. Interpreted fields are name, description, personality, scenario, system prompt,
 * and the first message.
 */
object CharacterCardCodec {
    const val SPEC_V2 = "chara_card_v2"
    const val SPEC_V3 = "chara_card_v3"

    private val json = Json { encodeDefaults = false }

    private val parsers: Map<String, (JsonObject, String?, String) -> Assistant> = mapOf(
        SPEC_V2 to ::assemble,
        SPEC_V3 to ::assemble,
    )

    fun parse(raw: String): JsonObject {
        val root = json.parseToJsonElement(raw).jsonObject
        val spec = root["spec"]?.jsonPrimitive?.contentOrNull
            ?: throw CharacterCardException("missing_spec")
        if (spec !in parsers) throw CharacterCardException("unsupported_spec", spec)
        if (root["data"]?.jsonObject == null) throw CharacterCardException("missing_data")
        return root
    }

    fun export(raw: String, nameOverride: String? = null): String {
        val root = parse(raw)
        val exported = if (nameOverride.isNullOrBlank()) root else overlayName(root, nameOverride)
        return json.encodeToString(JsonElement.serializer(), exported)
    }

    fun importAssistant(raw: String, background: String? = null): Assistant {
        val root = parse(raw)
        val spec = root["spec"]!!.jsonPrimitive.content
        val parser = parsers[spec] ?: throw CharacterCardException("unsupported_spec", spec)
        return parser(root, background, raw)
    }

    private fun assemble(root: JsonObject, background: String?, raw: String): Assistant {
        val data = root["data"]?.jsonObject ?: throw CharacterCardException("missing_data")
        val name = data.string("name") ?: throw CharacterCardException("missing_name")
        val description = data.string("description")
        val personality = data.string("personality")
        val scenario = data.string("scenario")
        val system = data.string("system_prompt")
        val firstMessage = data.string("first_mes")
        val prompt = buildString {
            appendLine("You are roleplaying as $name.")
            appendLine()
            if (!system.isNullOrBlank()) {
                appendLine(system)
                appendLine()
            }
            appendLine("## Description of the character")
            appendLine(description ?: "Empty")
            appendLine()
            appendLine("## Personality of the character")
            appendLine(personality ?: "Empty")
            appendLine()
            appendLine("## Scenario")
            append(scenario ?: "Empty")
        }
        return Assistant(
            name = name,
            presetMessages = if (firstMessage != null) listOf(UIMessage.assistant(firstMessage)) else emptyList(),
            systemPrompt = prompt,
            background = background,
            tavernCardJson = raw,
        )
    }

    /** first_mes plus alternate_greetings. Spec v2 requires these to be swipes, not extra turns. */
    fun greetingSwipes(raw: String): List<String> {
        val data = parse(raw)["data"]?.jsonObject ?: return emptyList()
        val first = data.string("first_mes")
        val alternates = (data["alternate_greetings"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        return listOfNotNull(first) + alternates
    }

    private fun overlayName(root: JsonObject, name: String): JsonObject {
        val data = root["data"]?.jsonObject ?: return root
        if (data.string("name") == name && root.string("name") == null) return root
        val newData = JsonObject(data.toMutableMap().apply { put("name", JsonPrimitive(name)) })
        val updated = root.toMutableMap()
        updated["data"] = newData
        if (root["name"] != null) updated["name"] = JsonPrimitive(name)
        return JsonObject(updated)
    }
}

class CharacterCardException(val code: String, val arg: String? = null) : IllegalArgumentException(
    if (arg == null) code else "$code:$arg"
)

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
