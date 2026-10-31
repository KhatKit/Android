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

    @Test fun refreshEventIsScriptOwnedAndFollowsLastSuccessfulShow() {
        val bridge = CardWidgetBridge()
        bridge.show(text("cached"), mapOf("refresh_action" to "refresh_status"))
        assertEquals("refresh_status", bridge.refreshAction)
        listOf<Any>("", " ", 42, "x".repeat(129)).forEach { invalid ->
            assertTrue(runCatching { bridge.show(text("invalid"), mapOf("refresh_action" to invalid)) }.isFailure)
            assertEquals("cached", bridge.content!!.props["text"])
            assertEquals("refresh_status", bridge.refreshAction)
        }
        bridge.show(text("default"), null)
        assertEquals("", bridge.refreshAction)
    }

    @Test fun sizeLayoutsPickTheLargestThatFitsAndFallBackToRoot() {
        val bridge = CardWidgetBridge()
        bridge.show(text("root"), mapOf(
            "frame" to "plain",
            "layouts" to listOf(
                mapOf("min_w" to 2, "min_h" to 1, "tree" to text("small"), "frame" to "full"),
                mapOf("min_w" to 4, "min_h" to 1, "tree" to text("wide")),
                mapOf("min_w" to 2, "min_h" to 2, "tree" to text("square")),
                mapOf("min_w" to 4, "min_h" to 3, "tree" to text("large")),
            ),
        ))
        val content = CardWidgetContent(bridge.content!!, frame = bridge.frame, layouts = bridge.layouts)
        fun pick(w: Int, h: Int) = content.pick(w, h).root.props["text"]
        assertEquals("root", pick(1, 1))
        assertEquals(WidgetFrame.PLAIN, content.pick(1, 1).frame)
        assertEquals("small", pick(3, 1))
        assertEquals(WidgetFrame.FULL, content.pick(3, 1).frame)
        assertEquals("wide", pick(4, 1))
        assertEquals("square", pick(2, 4))
        // 4×2 下 wide(4) 与 square(4) 同面积，取更宽的。
        assertEquals("wide", pick(4, 2))
        assertEquals("large", pick(4, 6))
    }

    @Test fun invalidLayoutsFailWithoutPublishing() {
        val bridge = CardWidgetBridge()
        listOf<Any>(
            "nope",
            listOf(mapOf("min_w" to 0, "min_h" to 1, "tree" to text("x"))),
            listOf(mapOf("min_w" to 1.5, "min_h" to 1, "tree" to text("x"))),
            listOf(mapOf("min_w" to 1, "min_h" to 1, "tree" to text("x"), "frame" to "glass")),
            listOf(mapOf("min_w" to 1, "min_h" to 1, "tree" to text("x"), "extra" to true)),
            listOf(mapOf("min_w" to 1, "min_h" to 1)),
            List(7) { mapOf("min_w" to 1, "min_h" to 1, "tree" to text("x")) },
            List(5) {
                mapOf("min_w" to 1, "min_h" to 1, "tree" to mapOf(
                    "__ui" to "Column", "props" to emptyMap<String, Any>(), "children" to List(60) { text("x") },
                ))
            },
        ).forEach { layouts ->
            assertTrue(layouts.toString(), runCatching { bridge.show(text("bad"), mapOf("layouts" to layouts)) }.isFailure)
        }
        assertNull(bridge.content)
        // Rust 的空 Lua 表 {} 等同于没有 layouts。
        bridge.show(text("ok"), mapOf("layouts" to emptyMap<String, Any>()))
        assertTrue(bridge.layouts.isEmpty())
    }
}
