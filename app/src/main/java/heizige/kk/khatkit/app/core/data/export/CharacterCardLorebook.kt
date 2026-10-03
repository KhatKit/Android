package heizige.kk.khatkit.app.core.data.export

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.app.core.data.model.InjectionPosition
import heizige.kk.khatkit.app.core.data.model.Lorebook
import heizige.kk.khatkit.app.core.data.model.LorebookKeyLogic
import heizige.kk.khatkit.app.core.data.model.PromptInjection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.Uuid

/** Maps a character card `character_book` into KhatKit's native lorebook model. */
fun parseCharacterCardLorebook(
    cardJson: String,
    json: Json = ExportSerializer.DefaultJson,
): Lorebook? = runCatching {
    val root = json.parseToJsonElement(cardJson).jsonObject
    val book = root["data"]?.jsonObject?.get("character_book")?.jsonObject
        ?: root["character_book"]?.jsonObject
        ?: return null
    Lorebook(
        id = Uuid.random(),
        name = book.string("name") ?: "Character Book",
        description = book.string("description").orEmpty(),
        entries = book.entryObjects().map { it.toRegexInjection() },
        recursiveScanning = book["recursive_scanning"].bool() == true,
    )
}.getOrNull()

private fun JsonObject.entryObjects(): List<JsonObject> = when (val entries = this["entries"]) {
    is JsonArray -> entries.map { it.jsonObject }
    is JsonObject -> entries.values.map { it.jsonObject }
    else -> emptyList()
}

private fun JsonObject.toRegexInjection(): PromptInjection.RegexInjection {
    val keys = stringList("keys").ifEmpty { stringList("key") }
    val extensions = this["extensions"] as? JsonObject
    val selective = this["selective"].bool() == true
    val useProbability = extensions?.get("useProbability")?.bool() != false
    return PromptInjection.RegexInjection(
        id = Uuid.random(),
        name = string("comment") ?: string("name") ?: keys.firstOrNull().orEmpty(),
        enabled = this["enabled"].bool() != false,
        priority = int("priority") ?: int("insertion_order") ?: 100,
        position = mapCharacterCardPosition(this["position"] ?: extensions?.get("position")),
        content = string("content").orEmpty(),
        injectDepth = int("depth") ?: extensions.int("depth") ?: 4,
        role = mapRole(int("role") ?: extensions.int("role")),
        keywords = keys,
        useRegex = this["use_regex"].bool() == true || this["useRegex"].bool() == true,
        caseSensitive = this["case_sensitive"].bool() == true,
        scanDepth = int("scan_depth") ?: 4,
        constantActive = this["constant"].bool() == true,
        secondaryKeywords = stringList("secondary_keys").ifEmpty { stringList("secondaryKeys") },
        selective = selective,
        keyLogic = mapKeyLogic(
            int("selectiveLogic") ?: int("selective_logic") ?: extensions.int("selectiveLogic"),
            selective,
        ),
        probability = if (!useProbability) 100 else int("probability") ?: extensions.int("probability") ?: 100,
        includeInRecursion = this["exclude_recursion"].bool() != true &&
            this["excludeRecursion"].bool() != true &&
            extensions?.get("exclude_recursion").bool() != true,
        preventRecursion = this["prevent_recursion"].bool() == true ||
            this["preventRecursion"].bool() == true ||
            extensions?.get("prevent_recursion").bool() == true,
    )
}

private fun mapCharacterCardPosition(value: JsonElement?): InjectionPosition {
    val token = (value as? JsonPrimitive)?.contentOrNull?.lowercase()
    return when (token) {
        "before_char", "before_character", "0" -> InjectionPosition.BEFORE_SYSTEM_PROMPT
        "after_char", "after_character", "1" -> InjectionPosition.AFTER_SYSTEM_PROMPT
        "at_depth", "4" -> InjectionPosition.AT_DEPTH
        else -> InjectionPosition.AFTER_SYSTEM_PROMPT
    }
}

private fun mapRole(role: Int?): MessageRole = when (role) {
    0 -> MessageRole.SYSTEM
    2 -> MessageRole.ASSISTANT
    else -> MessageRole.USER
}

private fun mapKeyLogic(value: Int?, selective: Boolean): LorebookKeyLogic = when (value) {
    0 -> LorebookKeyLogic.AND_ANY
    1 -> LorebookKeyLogic.NOT_ALL
    2 -> LorebookKeyLogic.NOT_ANY
    3 -> LorebookKeyLogic.AND_ALL
    else -> if (selective) LorebookKeyLogic.AND_ANY else LorebookKeyLogic.OR
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject?.int(key: String): Int? = this?.get(key)?.jsonPrimitive?.intOrNull
    ?: this?.get(key)?.jsonPrimitive?.contentOrNull?.toIntOrNull()

private fun JsonObject.stringList(key: String): List<String> = when (val value = this[key]) {
    is JsonArray -> value.mapNotNull { it.jsonPrimitive.contentOrNull }
    is JsonPrimitive -> value.contentOrNull?.let { listOf(it) }.orEmpty()
    else -> emptyList()
}

private fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.let { primitive ->
    primitive.booleanOrNull
        ?: primitive.intOrNull?.let { it != 0 }
        ?: primitive.contentOrNull?.toBooleanStrictOrNull()
}
