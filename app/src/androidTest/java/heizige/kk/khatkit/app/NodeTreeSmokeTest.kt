package heizige.kk.khatkit.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import heizige.kk.khatkit.engine.RustScriptEngine
import heizige.kk.khatkit.ui.UiNodeParser
import heizige.kk.khatkit.uikit.*
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.app.core.data.datastore.Settings
import coil3.compose.AsyncImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NodeTreeSmokeTest {
    @get:Rule val compose = createComposeRule()

    private fun node(type: String, props: Map<String, Any?> = emptyMap(), vararg children: Map<String, Any?>): Map<String, Any?> =
        mapOf("__ui" to type, "props" to props, "children" to children.toList())

    @Test fun allComponentsRenderInBothStylesAndBothColorModes() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val imageFile = java.io.File(context.cacheDir, "dsl-smoke.png")
        val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var style by mutableStateOf(KhatKitUiStyle.MATERIAL)
        var dark by mutableStateOf(false)
        var component by mutableStateOf(node("Text", mapOf("text" to "fixture")))
        val fixtures = listOf(
            node("Column", mapOf("scrollable" to true), node("Text", mapOf("text" to "column"))),
            node("Row", mapOf("vAlign" to "space_between"), node("Text", mapOf("text" to "row", "modifier" to mapOf("__mod" to listOf(listOf("weight", 1)))))),
            node("Box", mapOf("hAlign" to "center"), node("Text", mapOf("text" to "box"))),
            node("Spacer", mapOf("height" to 8)), node("Divider"),
            node("Card", mapOf("title" to "card")), node("Section", mapOf("title" to "section")),
            node("Text", mapOf("text" to "title", "style" to "title", "tone" to "primary", "bold" to true)),
            node("Markdown", mapOf("text" to "**markdown**")),
            node("Image", mapOf("src" to imageFile.path)),
            node("Badge", mapOf("text" to "badge", "tone" to "success")),
            node("ProgressBar", mapOf("ratio" to 0.5, "label" to "progress")),
            node("Button", mapOf("label" to "button", "action" to "submit")),
        ) + UiNodeParser.fields.map { type ->
            node(type, mapOf("id" to "field", "label" to type) +
                if (type in setOf("Select", "RadioGroup")) mapOf("options" to listOf("a", "b")) else emptyMap())
        }
        compose.setContent {
            KhatKitTheme(style, dark) {
                val tree = remember(component) { UiNodeParser.parse(component) }
                val values = remember(tree) { mutableStateMapOf<String, Any?>().apply { putAll(tree.initialValues()) } }
                Box(Modifier.fillMaxWidth().height(400.dp)) {
                    tree.Render(values, emptyMap(), null, false, {},
                        markdown = { text, modifier ->
                            CompositionLocalProvider(LocalSettings provides remember { Settings.dummy() }) {
                                MarkdownBlock(text, modifier)
                            }
                        },
                        image = { src, description, modifier ->
                            AsyncImage(java.io.File(src), description, modifier.height(30.dp))
                        })
                }
            }
        }
        for (theme in KhatKitUiStyle.entries) for (mode in listOf(false, true)) for (fixture in fixtures) {
            compose.runOnIdle { style = theme; dark = mode; component = fixture }
            compose.waitForIdle()
        }
    }

    @Test fun requiredValidationAndSubmitUseCurrentValues() {
        val root = UiNodeParser.parse(node("Column", emptyMap(),
            node("TextField", mapOf("id" to "name", "label" to "Name", "required" to true)),
            node("Switch", mapOf("id" to "enabled", "value" to false)),
            node("Button", mapOf("label" to "Submit fixture", "action" to "submit"))))
        var submitted: Map<String, Any?>? = null
        compose.setContent {
            KhatKitTheme {
                NodeTreeSheet("Fixture", root, heizige.kk.khatkit.ui.UiSheetOptions(), form = true,
                    onAction = { _, values -> submitted = values }, onDismiss = {},
                    markdown = { s, m -> Text(s, m) }, image = { _, _, m -> Box(m) })
            }
        }
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithText("Submit fixture").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Submit fixture").performClick()
        compose.onNodeWithText("请填写此项").assertExists()
        compose.runOnIdle { assertNull(submitted) }
        compose.onNode(hasSetTextAction()).performTextInput("Ada")
        compose.onNodeWithText("Submit fixture").performClick()
        compose.runOnIdle { assertEquals(mapOf("name" to "Ada", "enabled" to false), submitted) }
    }

    class FakeUi {
        fun form(title: String, items: Any, options: Map<String, Any?>?): Map<String, Any?> {
            val tree = UiNodeParser.parse(items as Map<*, *>)
            return mapOf("title" to title, "fields" to tree.initialValues())
        }
    }

    @Test fun automaticFooterKeepsInvalidFormOpenAndCancelWorks() {
        val root = UiNodeParser.parse(node("Column", emptyMap(),
            node("TextField", mapOf("id" to "name", "label" to "Name", "required" to true))))
        var submitted = false
        var cancelled = false
        compose.setContent {
            KhatKitTheme {
                NodeTreeSheet("Footer", root, heizige.kk.khatkit.ui.UiSheetOptions(), form = true,
                    onAction = { _, _ -> submitted = true }, onDismiss = { cancelled = true },
                    markdown = { s, m -> Text(s, m) }, image = { _, _, m -> Box(m) })
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("提交").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("提交").performClick()
        compose.onNodeWithText("请填写此项").assertExists()
        compose.runOnIdle { assertFalse(submitted); assertFalse(cancelled) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    @Test fun nativeLuaInjectionSerializesAndValidatesTree() {
        assertTrue(RustScriptEngine.isAvailable)
        val engine = RustScriptEngine.create(RustScriptEngine.KIND_LUA)
        try {
            engine.define("ui", FakeUi())
            val result = engine.eval("""
                local Column, Switch = ui.Column, ui.Switch
                return ui.form("Native", Column {
                    Switch { id = "enabled", value = false },
                    modifier = ui.Modifier.fillMaxWidth().padding(16),
                })
            """.trimIndent(), emptyMap()).getOrThrow()
            assertEquals("Native", result["title"])
            assertEquals(mapOf("enabled" to false), result["fields"])
        } finally { engine.close() }
    }
}
