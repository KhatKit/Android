package heizige.kk.khatkit.ui

import heizige.kk.khatkit.bridge.BridgeRegistry
import heizige.kk.khatkit.bridge.StoreBridge
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineKind
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.engine.RustEngineTest
import heizige.kk.khatkit.exec.CardExecutionOptions
import heizige.kk.khatkit.exec.CardExecutor
import heizige.kk.khatkit.hub.LoadedCard
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class CardWidgetExecutionTest {
    @Test fun buttonEventsPersistAndReloadInAnotherLanguage() = runBlocking {
        assumeTrue(RustEngineTest.NativeLib.available)
        val stored = mutableMapOf<String, String>()
        val store = Proxy.newProxyInstance(StoreBridge::class.java.classLoader, arrayOf(StoreBridge::class.java)) { _, method, arguments ->
            when (method.name) {
                "kvGet" -> stored[arguments[0] as String] ?: arguments[1]
                "kvSet" -> { stored[arguments[0] as String] = arguments[1] as String; null }
                else -> error("Unexpected store call: ${method.name}")
            }
        } as StoreBridge
        val manifest = CardManifest(
            name = "note_test",
            requires = CardManifest.Requires(bridges = listOf("ui", "store")),
            i18n = mapOf("en" to mapOf("save" to "Save"), "ja" to mapOf("save" to "保存")),
        )
        val script = """
            local event = args.widget or {}
            if event.action == "save" then
              store.kvSet("note", tostring((event.values or {}).note or ""))
            end
            ui.show(ui.Column {
              ui.TextField { id = "note", value = store.kvGet("note", "") },
              ui.Button { label = "@string/save", action = "save" }
            })
            return { ok = true }
        """.trimIndent()
        val executor = CardExecutor(BridgeRegistry(storeProvider = { store }))
        suspend fun run(locale: String, event: Map<String, Any?>): UiNode {
            val inline = CardWidgetBridge()
            val result = executor.execute(
                LoadedCard(manifest, File("."), script, EngineKind.LUA),
                mapOf("widget" to event), CardExecutionOptions(ui = inline, locales = listOf(locale)),
            )
            assertTrue(result.toString(), result is EngineResult.Ok)
            return inline.content!!
        }
        run("en-US", mapOf("action" to "save", "values" to mapOf("note" to "部署前提交工单")))
        val reloaded = run("ja-JP", emptyMap())
        assertEquals("部署前提交工单", reloaded.fields().single().props["value"])
        assertEquals("保存", reloaded.walk().first { it.type == "Button" }.props["label"])
        assertEquals(1, stored.size)
    }

    @Test fun luaAndJsLocalizeUiAndNeverPublishToTheGlobalHost() = runBlocking {
        assumeTrue(RustEngineTest.NativeLib.available)
        val manifest = CardManifest(
            name = "widget_test",
            requires = CardManifest.Requires(bridges = listOf("ui")),
            i18n = mapOf("ja" to mapOf("name" to "メモ", "greet" to "こんにちは {name}")),
        )
        val scripts = mapOf(
            EngineKind.LUA to """
                ui.show(ui.Column {
                  ui.Text { text = "@string/name" },
                  ui.Button { label = "@string/name", action = "save" }
                })
                return { label = i18n.text("greet", { name = "友人" }), locale = i18n.locale() }
            """.trimIndent(),
            EngineKind.JS to """
                ui.show({__ui:"Text", props:{text:"@string/name"}, children:[]});
                return {label:i18n.text("greet", {name:"友人"}), locale:i18n.locale()};
            """.trimIndent(),
        )
        scripts.forEach { (kind, script) ->
            val global = CardWidgetBridge()
            val inline = CardWidgetBridge()
            val executor = CardExecutor(BridgeRegistry(ui = global, localeTags = { listOf("en") }))
            val result = executor.execute(
                LoadedCard(manifest, File("."), script, kind), emptyMap(),
                CardExecutionOptions(ui = inline, locales = listOf("ja-JP")),
            )
            assertTrue(result.toString(), result is EngineResult.Ok)
            val values = (result as EngineResult.Ok).value
            assertEquals("こんにちは 友人", values["label"])
            assertEquals("ja-JP", values["locale"])
            assertEquals("メモ", inline.content!!.walk().first { it.type == "Text" }.props["text"])
            assertNull(global.content)
        }
    }
}
