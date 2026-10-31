package heizige.kk.khatkit.engine

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Uses the actual compiled MoonBit artifact and JNI host, rather than CLI mock responses. */
class WasmNativeIntegrationTest {
    class Store {
        var note = "before"
        fun kvGet(key: String, fallback: String): String = if (key == "note") note else fallback
        fun kvSet(key: String, value: String) { if (key == "note") note = value }
    }
    class I18n { fun locale(): String = "zh" }
    class Ui {
        var tree: Map<String, Any?>? = null
        fun show(root: Map<String, Any?>, options: Map<String, Any?>?) { tree = root }
    }

    @Test fun moonbitRunAndWidgetShareTheStoreAndUseTheInjectedHost() {
        assumeTrue(RustEngineTest.NativeLib.available)
        val cardDir = File(System.getenv("KHATKIT_CARDS_REPO") ?: "../../../KhatKitCards", "moonbit/dist/daily_note")
        assumeTrue("MoonBit fixture must be available", File(cardDir, "card.wasm").isFile)
        val store = Store()
        val ui = Ui()
        val engine = WasmScriptEngine.create(cardDir)
        try {
            engine.define("i18n", I18n())
            engine.define("store", store)
            engine.define("ui", ui)
            assertEquals(true, engine.eval("", mapOf("note" to "from run")).getOrThrow()["ok"])
            assertEquals("from run", store.note)
            assertEquals(true, engine.evalWidget(mapOf("widget" to mapOf(
                "action" to "save", "values" to mapOf("note" to "from widget"),
            ))).getOrThrow()["ok"])
            assertEquals("from widget", store.note)
            assertEquals("Column", ui.tree?.get("__ui"))
            assertTrue(ui.tree?.get("children") is List<*>)
        } finally { engine.close(); engine.close() }
    }
}
