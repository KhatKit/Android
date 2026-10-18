package heizige.kk.khatkit.card

import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Per-run locale snapshot. Locale tags are open-ended, just like Android's LocaleList. */
class CardStrings(
    private val manifest: CardManifest,
    localeTags: List<String> = listOf(Locale.getDefault().toLanguageTag()),
) {
    val locales: List<String> = localeTags.filter { it.isNotBlank() }.ifEmpty { listOf("en") }
    val locale: String get() = locales.first()
    private val translations = manifest.i18n.entries.associate { normalize(it.key) to it.value }
    private val candidates = buildList {
        // Honour the user's complete preference list before the card's fallback language.
        locales.forEach { addAll(fallbacks(it)) }
        addAll(fallbacks(manifest.defaultLocale))
        add("en")
    }.distinct()

    fun text(key: String, values: Map<String, Any?> = emptyMap(), fallback: String = key): String {
        val template = candidates.firstNotNullOfOrNull { translations[it]?.get(key)?.takeIf(String::isNotBlank) }
            ?: fallback
        return PLACEHOLDER.replace(template) { match ->
            values[match.groupValues[1]]?.toString() ?: match.value
        }
    }

    fun resolve(value: String): String =
        if (value.startsWith(PREFIX)) text(value.removePrefix(PREFIX)) else value

    val name: String get() = text("name", fallback = manifest.name)
    val description: String get() = text("description", fallback = resolve(manifest.description))
    val widgetTitle: String get() = manifest.widget?.title?.takeIf(String::isNotBlank)?.let(::resolve) ?: name

    fun resolveJson(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { resolveJson(it.value) })
        is JsonArray -> JsonArray(value.map(::resolveJson))
        is JsonPrimitive -> if (value.isString) JsonPrimitive(resolve(value.content)) else value
    }

    fun resolveValue(value: Any?): Any? = when (value) {
        is String -> resolve(value)
        is Map<*, *> -> value.entries.associate { it.key.toString() to resolveValue(it.value) }
        is List<*> -> value.map(::resolveValue)
        else -> value
    }

    companion object {
        private const val PREFIX = "@string/"
        private val PLACEHOLDER = Regex("""\{([A-Za-z0-9_]+)\}""")
        fun normalize(tag: String): String = tag.replace('_', '-').lowercase(Locale.ROOT)

        private fun fallbacks(tag: String): List<String> {
            val normalized = normalize(tag)
            val parsed = Locale.forLanguageTag(normalized)
            return buildList {
                add(normalized)
                if (parsed.script.isNotEmpty()) add("${parsed.language}-${parsed.script}".lowercase(Locale.ROOT))
                if (parsed.country.isNotEmpty()) add("${parsed.language}-${parsed.country}".lowercase(Locale.ROOT))
                if (parsed.language.isNotEmpty()) add(parsed.language.lowercase(Locale.ROOT))
            }.distinct()
        }
    }
}
