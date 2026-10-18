package heizige.kk.khatkit.card

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardWidgetManifestTest {
    private val manifest = CardManifest(
        name = "widget_test", engine = "lua", entry = CardManifest.Entry(lua = "main.lua"),
        requires = CardManifest.Requires(bridges = listOf("ui", "store")),
        widget = CardManifest.Widget(CardManifest.Entry(lua = "widget.lua"), bridges = listOf("ui")),
        tags = CardManifest.Tags("text", "create"),
    )

    private fun codes(card: CardManifest = manifest, scripts: Map<String, String> = emptyMap()) =
        CardValidator.validate(card, scripts).filter { it.severity == Severity.ERROR }.map { it.code }

    @Test fun validWidgetAndArbitraryLocalesPass() {
        assertTrue(codes(manifest.copy(i18n = mapOf("sr-Latn-RS" to mapOf("name" to "Test")))).isEmpty())
    }

    @Test fun separateEntryAndContainedPathsAreRequired() {
        assertTrue("WIDGET_MAIN_ENTRY" in codes(manifest.copy(widget = CardManifest.Widget(CardManifest.Entry(lua = "main.lua")))))
        assertTrue("WIDGET_PATH" in codes(manifest.copy(widget = CardManifest.Widget(CardManifest.Entry(lua = "../widget.lua")))))
        assertTrue("WIDGET_ENTRY" in codes(manifest.copy(widget = CardManifest.Widget(CardManifest.Entry(js = "widget.js")))))
    }

    @Test fun widgetsCannotEscalateBridgeCapabilities() {
        assertTrue("WIDGET_BRIDGES" in codes(manifest.copy(widget = manifest.widget!!.copy(bridges = listOf("ui", "root")))))
        assertTrue("WIDGET_BRIDGES" in codes(manifest.copy(widget = manifest.widget!!.copy(bridges = emptyList()))))
    }

    @Test fun missingWidgetScriptIsReportedAndValidScriptScanned() {
        assertTrue("WIDGET_SCRIPT_MISSING" in codes(scripts = mapOf("main.lua" to "return {}")))
        assertFalse("WIDGET_SCRIPT_MISSING" in codes(scripts = mapOf("main.lua" to "return {}", "widget.lua" to "return {}")))
    }

    @Test fun duplicateNormalizedLocalesAreRejected() {
        assertTrue("LOCALE_DUPLICATE" in codes(manifest.copy(i18n = mapOf("en-US" to emptyMap(), "en_us" to emptyMap()))))
    }
}
