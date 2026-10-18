package heizige.kk.khatkit.ui

import heizige.kk.khatkit.bridge.UiBridge
import heizige.kk.khatkit.bridge.UiImageAccessBridge
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UiScreenPageTest {
    private fun node(type: String, props: Map<String, Any?> = emptyMap(), vararg children: Map<String, Any?>) =
        mapOf("__ui" to type, "props" to props, "children" to children.toList())
    private val first = node("Column", emptyMap(),
        node("TextField", mapOf("id" to "required_on_first", "required" to true)),
        node("Button", mapOf("action" to "first_action")))
    private val second = node("Column", emptyMap(),
        node("Button", mapOf("action" to "second_action")))
    private fun options(secondTree: Map<String, Any?> = second) = mapOf(
        "nav" to mapOf("selected" to "first", "items" to listOf(
            mapOf("id" to "first", "label" to "First"),
            mapOf("id" to "second", "label" to "Second"))),
        "pages" to mapOf(
            "first" to mapOf("tree" to first),
            "second" to mapOf("tree" to secondTree, "load_more" to "next")),
    )

    @Test fun parsesBoundedPagesMatchingNavigation() {
        val raw = options()
        val pages = UiScreenPage.parse(raw, UiSheetOptions.from(raw).navigation)
        assertEquals(listOf("first", "second"), pages.keys.toList())
        assertEquals("next", pages.getValue("second").loadMoreEvent)
        assertTrue(UiScreenPage.parse(null, null).isEmpty())
        assertTrue(runCatching { UiScreenPage.parse(raw, null) }.isFailure)
        assertTrue(runCatching {
            UiScreenPage.parse(raw + ("pages" to mapOf("unknown" to mapOf("tree" to second))),
                UiSheetOptions.from(raw).navigation)
        }.isFailure)
    }

    @Test fun validatesInactiveTreesAndTheirNodeBudgets() {
        for (tree in listOf(node("WebView"), node("Column", emptyMap(), *Array(512) { node("Text") }))) {
            val raw = options(tree)
            assertTrue(runCatching { UiScreenPage.parse(raw, UiSheetOptions.from(raw).navigation) }.isFailure)
        }
    }

    @Test fun routesOnlyActionsOnTheActivePageAndCollectsOnlyItsFields() {
        val host = UiBridgeHost(timeoutMillis = 2000).apply { onHostAttached() }
        val pool = Executors.newSingleThreadExecutor()
        try {
            val task = pool.submit<Map<String, Any?>?> { host.screen("Pages", first, options()) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            while (host.request.value == null && System.nanoTime() < deadline) Thread.yield()
            assertTrue(host.request.value is UiRequest.Screen)
            host.submitScreen("second_action", mapOf(UiScreenPage.PAGE_VALUE_KEY to "unknown"))
            host.submitScreen("first_action", mapOf(UiScreenPage.PAGE_VALUE_KEY to "second"))
            assertFalse(task.isDone)
            host.submitScreen("second_action", mapOf(UiScreenPage.PAGE_VALUE_KEY to "second", "extra" to "drop"))
            assertEquals(mapOf("event" to "second_action", "values" to emptyMap<String, Any?>(), "page" to "second"),
                task.get(1, TimeUnit.SECONDS))
        } finally {
            host.dismiss()
            pool.shutdownNow()
        }
    }

    @Test fun imagesOnInactivePagesCannotBypassDomainAuthorization() {
        var published = false
        val delegate = object : UiBridge by HeadlessUiBridge {
            override fun screen(title: String, root: Map<String, Any?>, options: Map<String, Any?>?): Map<String, Any?>? {
                published = true
                return null
            }
        }
        val bridge = UiImageAccessBridge(delegate, { it }, { it.startsWith("https://allowed.example/") })
        assertTrue(runCatching {
            bridge.screen("", first, options(node("Image", mapOf("src" to "https://denied.example/image.png"))))
        }.isFailure)
        assertFalse(published)
        bridge.screen("", first, options(node("Image", mapOf("src" to "https://allowed.example/image.png"))))
        assertTrue(published)
    }
}
