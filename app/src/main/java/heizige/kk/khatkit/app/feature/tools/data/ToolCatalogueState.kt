package heizige.kk.khatkit.app.feature.tools.data

import heizige.kk.khatkit.hub.CardIndexEntry


internal data class ToolCatalogueState(
    val cards: List<CardIndexEntry> = emptyList(),
    val installed: Set<String> = emptySet(),
    val favorites: Set<String> = emptySet(),
    val recent: List<String> = emptyList(),
    val loading: Boolean = true,
    val complete: Boolean = true,
    val error: String? = null,
    val opening: String? = null,
)
