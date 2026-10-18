package heizige.kk.khatkit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardWidgetBridgeTest {
    private fun text(value: String) = mapOf("__ui" to "Text", "props" to mapOf("text" to value), "children" to emptyList<Any>())

    @Test fun runsHaveIndependentContentAndLastShowWins() {
        val first = CardWidgetBridge()
        val second = CardWidgetBridge()
        first.show(text("one"), null)
        first.show(text("two"), null)
        assertEquals("two", first.content!!.props["text"])
        assertNull(second.content)
    }

    @Test fun modalUiAndOversizedWidgetsFailWithoutPublishing() {
        val bridge = CardWidgetBridge()
        assertTrue(runCatching { bridge.confirm("", "", false) }.isFailure)
        assertTrue(runCatching {
            bridge.show(mapOf("__ui" to "Column", "props" to emptyMap<String, Any>(), "children" to List(65) { text("x") }), null)
        }.isFailure)
        assertNull(bridge.content)
    }

    @Test fun hostFrameDoesNotNestCardChrome() {
        val bridge = CardWidgetBridge()
        bridge.show(mapOf("__ui" to "Card", "props" to mapOf("title" to "Title"), "children" to listOf(text("ok"))), null)
        assertEquals("Column", bridge.content!!.type)
        assertEquals("ok", bridge.content!!.children.single().props["text"])
    }
}
