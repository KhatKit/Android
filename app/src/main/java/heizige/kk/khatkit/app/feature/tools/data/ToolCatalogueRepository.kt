package heizige.kk.khatkit.app.feature.tools.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.feature.tools.helper.isUserTool
import heizige.kk.khatkit.card.CardStrings
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.CardTags
import heizige.kk.khatkit.hub.LocaleMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

internal data class ToolCatalogue(
    val cards: List<CardIndexEntry>,
    val complete: Boolean,
    val nextOffset: Int = 0,
)

/** 页面所有数据从这里读取；目录缓存、收藏和最近打开的顺序各自持久化。 */
internal class ToolCatalogueRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val provider: KhatKitToolProvider,
) {
    private val preferences = context.getSharedPreferences("user_tool_catalogue", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun cached(): ToolCatalogue = withContext(Dispatchers.IO) {
        ToolCatalogue(
            runCatching {
                json.decodeFromString<List<CardIndexEntry>>(preferences.getString("catalogue", "[]")!!)
            }.getOrDefault(emptyList()),
            preferences.getBoolean("complete", false),
            preferences.getInt("next_offset", 0),
        )
    }

    suspend fun load(offset: Int = 0, existing: List<CardIndexEntry> = emptyList()): ToolCatalogue =
        withContext(Dispatchers.IO) {
        val page = provider.userToolPage(offset)
        val entries = linkedMapOf<String, CardIndexEntry>()
        existing.forEach { entries[it.name] = it }
        page.cards.forEach { entries[it.name] = it }
        val nextOffset = offset + page.cards.size
        val existingNames = existing.mapTo(HashSet()) { it.name }
        val total = page.total
        val complete = when {
            page.cards.isEmpty() -> true
            total != null -> nextOffset >= total
            page.cards.size < PAGE_SIZE -> true
            page.cards.none { it.name !in existingNames } -> true
            else -> false
        }
        ToolCatalogue(entries.values.filter { it.isUserTool() }, complete, nextOffset).also {
            preferences.edit().putString("catalogue", json.encodeToString(it.cards))
                .putBoolean("complete", complete)
                .putInt("next_offset", nextOffset)
                .apply()
        }
    }

    suspend fun installed(): List<CardIndexEntry> = withContext(Dispatchers.IO) {
        provider.chatCards().map { loaded ->
            val card = loaded.manifest
            val strings = CardStrings(card)
            CardIndexEntry(
                name = card.name, version = card.version, url = "",
                description = strings.description,
                hasUi = card.hasUi,
                hasWidget = card.widget != null,
                triggers = card.triggers,
                displayName = LocaleMap(mapOf("und" to strings.name)),
                tags = card.tags?.let { CardTags(it.domain, it.action, it.scene) },
            )
        }
    }

    fun favorites(): Set<String> = preferences.getStringSet("favorites", emptySet()).orEmpty().toSet()
    fun saveFavorites(names: Set<String>) { preferences.edit().putStringSet("favorites", names).apply() }
    fun recent(): List<String> = runCatching {
        json.decodeFromString<List<String>>(preferences.getString("recent", "[]")!!)
    }.getOrDefault(emptyList())
    fun recordOpen(name: String): List<String> = (listOf(name) + recent()).distinct().take(24).also {
        preferences.edit().putString("recent", json.encodeToString(it)).apply()
    }

    private companion object {
        const val PAGE_SIZE = 200
    }
}
