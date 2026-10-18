package heizige.kk.khatkit.hub

import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CardWidgetCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private fun card(): LoadedCard {
        val dir = folder.newFolder("card")
        dir.resolve("main.lua").writeText("return { task = true }")
        dir.resolve("widget.lua").writeText("return { widget = true }")
        val manifest = CardManifest(
            name = "test_card", engine = "lua", entry = CardManifest.Entry(lua = "main.lua"),
            requires = CardManifest.Requires(bridges = listOf("ui", "store", "imageToolbox"),
                dependencies = listOf(CardManifest.DependencyReq("imageToolbox", "1.0.0"))),
            widget = CardManifest.Widget(CardManifest.Entry(lua = "widget.lua"), bridges = listOf("ui", "store")),
        )
        return LoadedCard(manifest, dir, "normal tool", EngineKind.LUA)
    }

    @Test fun widgetLoadsOnlyItsOwnScriptAndRequiredDependencies() {
        val card = card()
        val widget = CardCache(folder.root).loadWidget(card)
        assertEquals("return { widget = true }", widget.scriptText)
        assertEquals(setOf("ui", "store"), widget.manifest.requiredBridges)
        assertTrue(widget.manifest.requiredDependencies.isEmpty())
        assertEquals("normal tool", card.scriptText)
    }

    @Test fun mainEntryAliasesAndEscapingPathsAreRejectedAtRuntime() {
        val card = card()
        val cache = CardCache(folder.root)
        listOf("./main.lua", "../widget.lua", "/tmp/widget.lua").forEach { path ->
            val invalid = card.copy(manifest = card.manifest.copy(widget =
                CardManifest.Widget(CardManifest.Entry(lua = path))))
            assertTrue(path, runCatching { cache.loadWidget(invalid) }.isFailure)
        }
    }
}
