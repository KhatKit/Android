package heizige.kk.khatkit.ui

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NodeTreeParserTest {
    private fun node(type: String, props: Map<String, Any?> = emptyMap(), vararg children: Map<String, Any?>) =
        mapOf("__ui" to type, "props" to props, "children" to children.toList())

    private fun rejects(part: String, body: () -> Unit) {
        try { body(); fail("Expected rejection: $part") }
        catch (error: UiNodeException) { assertTrue(error.message, error.message!!.contains(part)) }
    }

    @Test fun collectsNestedTypedFieldsAndValidatesRequiredWithoutRejectingFalse() {
        val tree = UiNodeParser.parse(node("Column", emptyMap(),
            node("Section", mapOf("title" to "Input"),
                node("TextField", mapOf("id" to "name", "required" to true)),
                node("Checkbox", mapOf("id" to "checked", "required" to true, "value" to false)),
                node("NumberField", mapOf("id" to "n", "value" to 2, "min" to 0, "max" to 10, "step" to 2)))))
        assertEquals(setOf("name"), tree.errors(tree.initialValues()).keys)
        assertEquals(mapOf("name" to "Ada", "checked" to false, "n" to 2),
            tree.collect(tree.initialValues() + mapOf("name" to "Ada", "extra" to "discard")))
        assertTrue(tree.errors(tree.initialValues() + mapOf("name" to "Ada", "n" to 3)).containsKey("n"))
    }

    @Test fun rejectsUnknownComponentsPropsTokensTypesAndDuplicateIds() {
        rejects("WebView") { UiNodeParser.parse(node("WebView")) }
        rejects("Text.color") { UiNodeParser.parse(node("Text", mapOf("color" to "#fff"))) }
        rejects("Text.tone") { UiNodeParser.parse(node("Text", mapOf("tone" to "danger"))) }
        rejects("Image.src") { UiNodeParser.parse(node("Image", mapOf("src" to "https://example.com/image.png"))) }
        rejects("Switch.value") { UiNodeParser.parse(node("Switch", mapOf("id" to "x", "value" to 1))) }
        rejects("TextField.id") { UiNodeParser.parse(node("Column", emptyMap(),
            node("TextField", mapOf("id" to "x")), node("TextField", mapOf("id" to "x")))) }
        rejects("Text.children") { UiNodeParser.parse(node("Text", emptyMap(), node("Text"))) }
    }

    @Test fun enforcesExactDepthAndNodeLimits() {
        var tree = node("Text")
        repeat(31) { tree = node("Column", emptyMap(), tree) }
        UiNodeParser.parse(tree)
        rejects("depth") { UiNodeParser.parse(node("Column", emptyMap(), tree)) }
        UiNodeParser.parse(node("Column", emptyMap(), *Array(511) { node("Text") }))
        rejects("count") { UiNodeParser.parse(node("Column", emptyMap(), *Array(512) { node("Text") })) }
    }

    @Test fun acceptsRustEmptyTablesAndValidatesModifierArguments() {
        UiNodeParser.parse(mapOf("__ui" to "Column", "props" to emptyMap<String, Any?>(), "children" to emptyMap<String, Any?>()))
        UiNodeParser.parse(node("Text", mapOf("modifier" to mapOf("__mod" to listOf("fillMaxWidth", listOf("padding", 1, 2, 3, 4), listOf("visible", false))))))
        rejects("padding") { UiNodeParser.modifierOps(mapOf("__mod" to listOf(listOf("padding", 1, 2, 3)))) }
        rejects("weight") { UiNodeParser.modifierOps(mapOf("__mod" to listOf(listOf("weight", 0)))) }
        rejects("alpha") { UiNodeParser.modifierOps(mapOf("__mod" to listOf(listOf("alpha", 0.5)))) }
        rejects("Text.bold") { UiNodeParser.parse(node("Text", mapOf("bold" to "true"))) }
    }

    @Test fun screenReturnsActionAndValuesAndDismissReleasesWaiter() {
        val host = UiBridgeHost(timeoutMillis = 2000)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val task = pool.submit<Map<String, Any?>?> {
                host.screen("Screen", node("Column", emptyMap(),
                    node("TextField", mapOf("id" to "name", "required" to true)),
                    node("Button", mapOf("action" to "download"))), null)
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            while (host.request.value == null && System.nanoTime() < deadline) Thread.yield()
            assertTrue(host.request.value is UiRequest.Screen)
            host.submitScreen("download", mapOf("name" to ""))
            assertFalse(task.isDone)
            host.submitScreen("invented", mapOf("name" to "Ada"))
            assertFalse(task.isDone)
            host.submitScreen("download", mapOf("name" to "Ada", "extra" to 1))
            assertEquals(mapOf("event" to "download", "values" to mapOf("name" to "Ada")), task.get(1, TimeUnit.SECONDS))
            assertNull(host.request.value)
        } finally { host.dismiss(); pool.shutdownNow() }
    }

    @Test fun timeoutClearsTreeRequestAndLegacyStillSubmits() {
        val host = UiBridgeHost(timeoutMillis = 10)
        assertNull(host.form("Timeout", node("Column"), null))
        assertNull(host.request.value)
        val pool = Executors.newSingleThreadExecutor()
        val legacy = UiBridgeHost(timeoutMillis = 2000)
        try {
            val task = pool.submit<Map<String, Any?>?> {
                legacy.form("Legacy", listOf(mapOf("type" to "input", "id" to "x")), null)
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            while (legacy.request.value == null && System.nanoTime() < deadline) Thread.yield()
            assertTrue(legacy.request.value is UiRequest.Form)
            legacy.submitForm(mapOf("x" to "old"))
            assertEquals(mapOf("x" to "old"), task.get(1, TimeUnit.SECONDS))
        } finally { legacy.dismiss(); pool.shutdownNow() }
    }

    @Test fun runtimeBudgetLimitsBlockedFormAndVocabularyMatchesValidator() {
        val host = UiBridgeHost(timeoutMillis = 60_000)
        assertNull(UiBridgeHost.withinBudget(5) { host.form("Budget", node("Column"), null) })
        assertNull(host.request.value)
        assertEquals(heizige.kk.khatkit.card.UiWidgetVocabulary.NODES, UiNodeParser.components)
    }
}
