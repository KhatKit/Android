package heizige.kk.khatkit.app.feature.tools

import heizige.kk.khatkit.app.core.data.model.ToolCategory
import heizige.kk.khatkit.app.core.util.isUserTool
import heizige.kk.khatkit.app.core.util.matchesToolQuery
import heizige.kk.khatkit.app.core.util.recommendedTools
import heizige.kk.khatkit.app.core.util.toolCategory
import heizige.kk.khatkit.app.feature.chat.helper.toolHeaderIcon
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.CardTags
import heizige.kk.khatkit.ui.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogueExtensionsTest {
    @Test
    fun userToolRequiresWidgetEntryAndUserTrigger() {
        assertTrue(tool(hasWidget = true, triggers = listOf("ai", "user")).isUserTool())
        assertFalse(tool(hasUi = true, hasWidget = false, triggers = listOf("user")).isUserTool())
        assertFalse(tool(hasWidget = true, triggers = listOf("ai")).isUserTool())
    }

    @Test
    fun categoryUsesSpecificNameHintsBeforeBroadDomain() {
        assertEquals(ToolCategory.IMAGE, tool(name = "image_compress", domain = "media").toolCategory())
        assertEquals(ToolCategory.TEXT, tool(name = "translate_page", domain = "net").toolCategory())
        assertEquals(ToolCategory.WEB, tool(name = "dns_lookup", domain = "net").toolCategory())
    }

    @Test
    fun searchRequiresEveryTermAndIncludesCategoryLabels() {
        val entry = tool(name = "image_compress", domain = "media", description = "压缩长图")
        assertTrue(entry.matchesToolQuery("压缩 图片处理", listOf("zh-CN")))
        assertFalse(entry.matchesToolQuery("压缩 不存在", listOf("zh-CN")))
    }

    @Test
    fun recommendationsHandleEmptyAndSmallCataloguesWithoutDuplicateNames() {
        assertTrue(recommendedTools(emptyList(), seed = 10, page = 0).isEmpty())
        val first = tool(name = "first", description = "primary")
        val second = tool(name = "second")
        val cards = listOf(first, first.copy(description = "duplicate"), second)
        assertEquals(setOf(first, second), recommendedTools(cards, seed = 10, page = 0).toSet())
        assertEquals(recommendedTools(cards, 10, 0), recommendedTools(cards, 10, 1))
    }

    @Test
    fun recommendationsRotateThroughTheCatalogueDeterministically() {
        val cards = (1..7).map { tool(name = "tool_$it") }
        val pages = (0..2).map { recommendedTools(cards, seed = 10, page = it) }
        assertEquals(listOf(3, 3, 3), pages.map { it.toSet().size })
        assertTrue(pages[0].intersect(pages[1].toSet()).isEmpty())
        assertEquals(cards.toSet(), pages.flatten().toSet())
        assertEquals(pages[0], recommendedTools(cards, 10, 0))
        assertEquals(pages[0], recommendedTools(cards, 10, 7))
    }

    @Test
    fun headerIconIsTakenFromTheBannerButNotFromTheBody() {
        val icon = UiNode("Icon", mapOf("name" to "bolt", "size" to 38), emptyList())
        val title = UiNode("Text", mapOf("text" to "Title"), emptyList())
        val banner = UiNode("Column", emptyMap(), listOf(icon, title))
        assertEquals("bolt", banner.toolHeaderIcon())
        assertNull(banner.copy(children = listOf(title, icon)).toolHeaderIcon())
        assertNull(banner.copy(children = listOf(icon.copy(props = mapOf("name" to "bolt", "size" to 20)), title)).toolHeaderIcon())
    }

    private fun tool(
        name: String = "tool",
        domain: String = "other",
        hasUi: Boolean = true,
        hasWidget: Boolean = false,
        triggers: List<String> = listOf("user"),
        description: String = "",
    ) = CardIndexEntry(
        name = name, version = "1.0.0", url = "", hasUi = hasUi, hasWidget = hasWidget, triggers = triggers,
        description = description, tags = CardTags(domain, "read"),
    )
}
