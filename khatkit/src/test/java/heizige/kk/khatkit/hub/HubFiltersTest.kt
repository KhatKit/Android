package heizige.kk.khatkit.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubFiltersTest {

    private fun card(
        name: String,
        bridges: List<String> = emptyList(),
        privilege: String = "none",
        description: String = "",
        tags: CardTags? = null,
    ) = CardIndexEntry(
        name = name,
        version = "1.0.0",
        description = description,
        tags = tags,
        privilege = privilege,
        bridges = bridges,
        url = "$name.zip",
    )

    @Test
    fun hidesCardsRequiringMissingBridge() {
        val cards = listOf(
            card("plain"),
            card("needs_root", bridges = listOf("root")),
            card("needs_ui", bridges = listOf("ui")),
        )
        val visible = HubFilters.byCapability(cards, available = setOf("tool", "ui"))
        assertEquals(listOf("plain", "needs_ui"), visible.map { it.name })
    }

    @Test
    fun elevatedHiddenWithoutPrivilegeBridge() {
        val cards = listOf(
            card("elevated_card", privilege = "elevated"),
        )
        assertTrue(HubFilters.byCapability(cards, setOf("tool")).isEmpty())
        assertEquals(1, HubFilters.byCapability(cards, setOf("tool", "root")).size)
    }

    @Test
    fun tagPrefilterRanksMatches() {
        val cards = listOf(
            card("pdf_merge", description = "合并 PDF", tags = CardTags("file", "convert")),
            card("image_resize", description = "缩放图片", tags = CardTags("media", "convert")),
            card("note", description = "无关", tags = CardTags("text", "create")),
        )
        val result = HubFilters.byTags(cards, "pdf", limit = 20)
        assertEquals("pdf_merge", result.first().name)
    }

    @Test
    fun tagPrefilterRespectsLimit() {
        val cards = (1..30).map { card("card_$it", description = "pdf tool") }
        assertEquals(20, HubFilters.byTags(cards, "pdf", limit = 20).size)
    }
}
