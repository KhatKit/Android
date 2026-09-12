package heizige.kk.khatkit.card

import kotlinx.serialization.json.Json

/** 解析 card.json。 */
object CardParser {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }

    fun parse(text: String): Result<CardManifest> =
        runCatching { json.decodeFromString<CardManifest>(text) }

    fun serialize(manifest: CardManifest): String =
        json.encodeToString(manifest)
}
