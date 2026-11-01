package heizige.kk.khatkit.app.feature.tools

import heizige.kk.khatkit.app.feature.tools.helper.ToolCategory
import heizige.kk.khatkit.app.feature.tools.helper.isUserTool
import heizige.kk.khatkit.app.feature.tools.helper.matchesToolQuery
import heizige.kk.khatkit.app.feature.tools.helper.toolCategory
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.CardTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
