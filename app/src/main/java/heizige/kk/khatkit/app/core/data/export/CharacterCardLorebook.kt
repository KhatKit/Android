package heizige.kk.khatkit.app.core.data.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import heizige.kk.khatkit.app.core.data.model.InjectionPosition
import heizige.kk.khatkit.app.core.data.model.Lorebook
import heizige.kk.khatkit.app.core.data.model.PromptInjection
import kotlin.uuid.Uuid

/** Maps the CCv3 `data.character_book` object into KhatKit's native lorebook model. */
fun parseCharacterCardLorebook(
    cardJson: String,
    json: Json = ExportSerializer.DefaultJson,
): Lorebook? = runCatching {
    val root = json.parseToJsonElement(cardJson).jsonObject
    val book = root["data"]?.jsonObject?.get("character_book")?.jsonObject
        ?: root["character_book"]?.jsonObject
        ?: return null
    val entries = book["entries"]?.jsonArray.orEmpty().map { raw ->
        val entry = raw.jsonObject
        PromptInjection.RegexInjection(
            id = Uuid.random(),
            name = entry["comment"]?.jsonPrimitive?.contentOrNull
                ?: entry["keys"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull.orEmpty(),
            enabled = entry["enabled"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() != false,
            priority = entry["insertion_order"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 100,
            position = mapCharacterCardPosition(entry["position"]?.jsonPrimitive?.contentOrNull),
            content = entry["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            keywords = entry["keys"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            useRegex = false,
            caseSensitive = false,
            scanDepth = entry["case_sensitive"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 4,
            constantActive = entry["constant"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true,
        )
    }
    Lorebook(
        id = Uuid.random(),
        name = book["name"]?.jsonPrimitive?.contentOrNull ?: "Character Book",
        description = "",
        entries = entries,
    )
}.getOrNull()

private fun mapCharacterCardPosition(value: String?): InjectionPosition =
    when (value?.lowercase()) {
        "before_char", "before_character" -> InjectionPosition.BEFORE_SYSTEM_PROMPT
        "after_char", "after_character" -> InjectionPosition.AFTER_SYSTEM_PROMPT
        "at_depth" -> InjectionPosition.AT_DEPTH
        else -> InjectionPosition.AFTER_SYSTEM_PROMPT
    }
