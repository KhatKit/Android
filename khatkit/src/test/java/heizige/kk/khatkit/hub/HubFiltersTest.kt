package heizige.kk.khatkit.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubFiltersTest {

    private fun card(
        name: String,
        bridges: List<String> = emptyList(),
        privilege: String = "none",
    ) = CardIndexEntry(
        name = name,
        version = "1.0.0",
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
}
