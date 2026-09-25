package heizige.kk.khatkit.hub

import heizige.kk.khatkit.bridge.BridgeRegistry
import heizige.kk.khatkit.card.CardManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubFiltersTest {

    private fun card(
        name: String,
        bridges: List<String> = emptyList(),
        privilege: String = "none",
        dependencies: List<CardManifest.DependencyReq> = emptyList(),
    ) = CardIndexEntry(
        name = name,
        version = "1.0.0",
        privilege = privilege,
        bridges = bridges,
        url = "$name.zip",
        dependencies = dependencies,
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
    fun dependencyCardVisibleWhenRuntimeSupportsDependencies() {
        val dependency = CardManifest.DependencyReq(
            name = "imageToolbox",
            version = "1.0.0",
            sha256 = "a".repeat(64),
        )
        val cards = listOf(
            card("image_resize", bridges = listOf("tool", "imageToolbox"), dependencies = listOf(dependency)),
        )

        // 已加载依赖包：直接可见
        assertEquals(
            1,
            HubFilters.byCapability(cards, setOf("tool", "imageToolbox", BridgeRegistry.CAPABILITY_DEPENDENCY)).size,
        )
        // 未加载但支持依赖包运行时：可见（运行前自动下载）
        assertEquals(1, HubFilters.byCapability(cards, setOf("tool", BridgeRegistry.CAPABILITY_DEPENDENCY)).size)
        // 不支持依赖包运行时：隐藏
        assertTrue(HubFilters.byCapability(cards, setOf("tool")).isEmpty())
    }
}
