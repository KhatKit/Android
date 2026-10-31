package heizige.kk.khatkit.app.core.data.ai.tools

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.di.appEntryPoint
import heizige.kk.khatkit.app.core.ui.components.khatkit.KhatKitUiHost
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.app.core.ui.theme.ProvideMaterial3Theme
import heizige.kk.khatkit.bridge.BridgeRegistry
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineKind
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.engine.RustScriptEngine
import heizige.kk.khatkit.engine.WasmScriptEngine
import heizige.kk.khatkit.exec.CardExecutor
import heizige.kk.khatkit.exec.CardRunManager
import heizige.kk.khatkit.hub.CardCache
import heizige.kk.khatkit.hub.CardIndex
import heizige.kk.khatkit.hub.HubClient
import heizige.kk.khatkit.hub.LoadedCard
import heizige.kk.khatkit.ui.UiBridgeHost
import heizige.kk.khatkit.ui.UiRequest
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Runs the packaged ARM JNI engines against the actual app sheet renderer on a device. */
class CardRuntimeDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var host: UiBridgeHost
    private lateinit var scope: CoroutineScope
    private lateinit var manager: CardRunManager

    @Before fun attachProductionUi() {
        assertTrue("Packaged native library must load on this ABI", RustScriptEngine.isAvailable)
        val provider = appEntryPoint(context).khatKitToolProvider()
        // Keep test plumbing out of the shipping API while using the real singleton host.
        host = provider.javaClass.getDeclaredField("uiHost").apply { isAccessible = true }.get(provider) as UiBridgeHost
        assertNull("Do not replace an existing user interaction", host.request.value)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val executor = CardExecutor(BridgeRegistry(ui = host))
        manager = CardRunManager(scope, maxConcurrent = 1) { card, args -> executor.execute(card, args) }
        compose.setContent {
            ui.feice.mochi.theme.MochiTheme {
                ProvideMaterial3Theme(amoled = false) {
                    CompositionLocalProvider(LocalSettings provides Settings.dummy()) { KhatKitUiHost() }
                }
            }
        }
        compose.waitUntil(10_000) { host.hostAttached.value }
    }

    @After fun cleanUp() {
        if (::scope.isInitialized) runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        if (::host.isInitialized) while (host.request.value != null) host.dismiss()
    }

    private fun card(name: String, kind: EngineKind, interactive: Boolean = true): LoadedCard {
        val script = when {
            !interactive -> if (kind == EngineKind.LUA) "return { value = 42 }" else "return {value:42};"
            kind == EngineKind.LUA -> """return { answer = ui.confirm(args.title, "Native Lua card", false) }"""
            else -> """return {answer:ui.confirm(args.title, "Native JavaScript card", false)};"""
        }
        return LoadedCard(CardManifest(name,
            requires = CardManifest.Requires(bridges = if (interactive) listOf("ui") else emptyList())),
            context.cacheDir, script, kind)
    }

    private fun waitForTitle(title: String) {
        compose.waitUntil(10_000) {
            (host.request.value as? UiRequest.Confirm)?.title == title &&
                compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun confirm() {
        compose.onNodeWithText(context.getString(R.string.khatkit_confirm)).performClick()
    }

    private fun waitForQueued(title: String) {
        val queueLock = host.javaClass.getDeclaredField("queueLock").apply { isAccessible = true }.get(host)
        val queued = host.javaClass.getDeclaredField("queued").apply { isAccessible = true }.get(host) as Collection<*>
        compose.waitUntil(10_000) {
            synchronized(queueLock) { queued.any { it is UiRequest.Confirm && it.title == title } }
        }
    }

    private fun evidence(name: String) {
        compose.waitForIdle()
        val dir = File(context.getExternalFilesDir(null), "card-runtime-device-evidence").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        Log.i("CardRuntimeDevice", name)
    }

    @Test fun nativeUiQueueDoesNotBlockBackgroundAndBothAnswersRoundTrip() {
        val first = scope.async { manager.run(card("device_first", EngineKind.LUA), mapOf("title" to "Device card A")) }
        waitForTitle("Device card A")
        val second = scope.async { manager.run(card("device_second", EngineKind.JS), mapOf("title" to "Device card B")) }
        waitForQueued("Device card B")
        val background = scope.async { manager.run(card("device_background", EngineKind.LUA, false), emptyMap()) }
        runBlocking { withTimeout(5_000) { assertEquals(42L, (background.await().getOrThrow()["value"] as Number).toLong()) } }
        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        assertEquals("Device card A", (host.request.value as UiRequest.Confirm).title)
        evidence("background_finished_while_A_and_B_wait")
        confirm()
        waitForTitle("Device card B")
        evidence("B_visible_after_A_answer")
        confirm()
        runBlocking { withTimeout(5_000) {
            assertEquals(true, first.await().getOrThrow()["answer"])
            assertEquals(true, second.await().getOrThrow()["answer"])
        } }
        compose.waitUntil(5_000) { host.request.value == null }
    }

    @Test fun cancellingOneNativeCardLeavesTheOtherInteractiveCardAlive() {
        val first = scope.async { manager.run(card("device_cancel", EngineKind.LUA), mapOf("title" to "Cancel card A")) }
        waitForTitle("Cancel card A")
        val second = scope.async { manager.run(card("device_keep", EngineKind.JS), mapOf("title" to "Keep card B")) }
        waitForQueued("Keep card B")
        runBlocking { withTimeout(5_000) {
            manager.run(card("device_probe", EngineKind.LUA, false), emptyMap()).getOrThrow()
            first.cancelAndJoin()
        } }
        waitForTitle("Keep card B")
        assertFalse(second.isCompleted)
        evidence("B_survives_A_cancellation")
        confirm()
        runBlocking { withTimeout(5_000) { assertEquals(true, second.await().getOrThrow()["answer"]) } }
    }

    class Probe(private val entered: CompletableDeferred<Unit>) {
        fun started(): Boolean { entered.complete(Unit); return true }
    }

    @Test fun nativeSandboxAndPureLoopCancellationWorkOnDevice() = runBlocking {
        val lua = RustScriptEngine.create(RustScriptEngine.KIND_LUA)
        try {
            val result = lua.eval("""return { safe = io == nil and os == nil and dofile == nil
                and loadfile == nil and package.loadlib == nil and package.searchers[2] == nil }""", emptyMap()).getOrThrow()
            assertEquals(true, result["safe"])
        } finally { lua.close() }
        for (kind in listOf(RustScriptEngine.KIND_LUA, RustScriptEngine.KIND_JS)) {
            val entered = CompletableDeferred<Unit>()
            val looping = scope.async {
                manager.run(card("device_loop", EngineKind.LUA, false), emptyMap()) { _, _ ->
                    val engine = RustScriptEngine.create(kind)
                    try {
                        engine.define("probe", Probe(entered))
                        engine.eval(if (kind == RustScriptEngine.KIND_LUA)
                            "probe.started(); while true do end" else "probe.started(); while (true) {}", emptyMap())
                    } finally { engine.close() }
                }
            }
            withTimeout(5_000) {
                entered.await()
                looping.cancelAndJoin()
                manager.run(card("device_after_loop", EngineKind.LUA, false), emptyMap()).getOrThrow()
            }
            Log.i("CardRuntimeDevice", "native_loop_cancelled kind=$kind next_card_completed=true")
        }
    }

    class Store {
        var note = ""
        fun kvGet(key: String, fallback: String): String = if (key == "note") note else fallback
        fun kvSet(key: String, value: String) { if (key == "note") note = value }
    }
    class I18n { fun locale(): String = "zh" }
    class Ui {
        var tree: Map<String, Any?>? = null
        fun show(root: Map<String, Any?>, options: Map<String, Any?>?) { tree = root }
    }

    @Test fun actualMoonbitBinaryAndWidgetUseArmJniAndTheSameStore() {
        val fixture = File(context.getExternalFilesDir(null), "card-runtime-device-fixtures")
        assertTrue("Push the actual MoonBit fixture before running", File(fixture, "card.wasm").isFile)
        val store = Store()
        val ui = Ui()
        val engine = WasmScriptEngine.create(fixture)
        try {
            engine.define("i18n", I18n())
            engine.define("store", store)
            engine.define("ui", ui)
            assertEquals(true, engine.eval("", mapOf("note" to "device run")).getOrThrow()["ok"])
            assertEquals("device run", store.note)
            assertEquals(true, engine.evalWidget(mapOf("widget" to mapOf(
                "action" to "save", "values" to mapOf("note" to "device widget")))).getOrThrow()["ok"])
            assertEquals("device widget", store.note)
            assertEquals("Column", ui.tree?.get("__ui"))
            Log.i("CardRuntimeDevice", "moonbit_run_widget_store_and_ui_passed=true")
        } finally { engine.close() }
    }

    @Test fun actualMoonbitScreenWaitsWithoutBlockingANativeBackgroundCard() {
        val fixture = File(context.getExternalFilesDir(null), "card-runtime-device-fixtures")
        val store = Store()
        val wasmCard = LoadedCard(CardManifest("device_wasm_screen"), fixture, "", EngineKind.WASM)
        val interactive = scope.async {
            manager.run(wasmCard, emptyMap()) { _, _ ->
                val engine = WasmScriptEngine.create(fixture)
                try {
                    engine.define("i18n", I18n())
                    engine.define("store", store)
                    engine.define("ui", host)
                    engine.eval("", emptyMap())
                } finally { engine.close() }
            }
        }
        compose.waitUntil(10_000) {
            host.request.value is UiRequest.Screen &&
                compose.onAllNodesWithText("@string/save").fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking { withTimeout(5_000) {
            val background = manager.run(card("device_wasm_background", EngineKind.JS, false), emptyMap())
            assertEquals(42L, (background.getOrThrow()["value"] as Number).toLong())
        } }
        assertFalse(interactive.isCompleted)
        evidence("WASM_screen_waits_while_native_background_finishes")
        compose.onNode(hasSetTextAction()).performTextInput("device native WASM note")
        compose.onNodeWithText("@string/save").performClick()
        runBlocking { withTimeout(5_000) {
            assertEquals(true, interactive.await().getOrThrow()["ok"])
        } }
        assertEquals("device native WASM note", store.note)
    }

    @Test fun liveHubPackageDownloadsAndVerifiesOnThePhonesNetwork() = runBlocking(Dispatchers.IO) {
        val base = appEntryPoint(context).khatKitToolProvider().hubBaseUrl.trimEnd('/')
        val capabilities = "tool,ui,store,net,host,time,i18n,download,accessibility,imageToolbox,dependency,fs,system"
        val connection = URL("$base/api/khatkit/index.json?bridges=$capabilities&limit=100").openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        val text = try {
            assertEquals(200, connection.responseCode)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
        val index = Json { ignoreUnknownKeys = true }.decodeFromString<CardIndex>(text)
        assertTrue("Live index must contain cards", index.cards.isNotEmpty())
        val entry = index.cards.firstOrNull { it.name == "image_resize_crop" } ?: index.cards.first()
        assertTrue("A live download must verify its SHA-256", entry.hash.isNotBlank())
        val scratch = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "card-device-online-").toFile()
        val hub = HubClient(base)
        try {
            val cache = CardCache(scratch)
            val loaded = cache.load(cache.ensure(entry, hub))
            assertEquals(entry.name, loaded.manifest.name)
            assertEquals(entry.version, loaded.manifest.version)
            assertTrue(loaded.manifest.hasUi)
            Log.i("CardRuntimeDevice", "live_download_passed card=${entry.name} version=${entry.version} engine=${loaded.engineKind} hasUi=${loaded.manifest.hasUi}")
        } finally { hub.close(); scratch.deleteRecursively() }
        Unit
    }
}
