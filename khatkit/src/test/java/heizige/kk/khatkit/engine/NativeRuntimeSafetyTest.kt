package heizige.kk.khatkit.engine

import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.exec.CardRunManager
import heizige.kk.khatkit.hub.LoadedCard
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class NativeRuntimeSafetyTest {
    class Probe(private val entered: CompletableDeferred<Unit>) {
        fun started(): Boolean { entered.complete(Unit); return true }
    }

    @Test fun luaCannotAccessProcessOrFilesystemOutsideBridges() {
        assumeTrue(RustEngineTest.NativeLib.available)
        val engine = RustScriptEngine.create(RustScriptEngine.KIND_LUA)
        try {
            val value = engine.eval("""
                return { io = io == nil, os = os == nil, dofile = dofile == nil,
                    loadfile = loadfile == nil, loadlib = package.loadlib == nil,
                    path = package.path, cpath = package.cpath,
                    diskLoader = package.searchers[2] == nil }
            """.trimIndent(), emptyMap()).getOrThrow()
            for (key in listOf("io", "os", "dofile", "loadfile", "loadlib", "diskLoader")) assertEquals(key, true, value[key])
            assertEquals("", value["path"])
            assertEquals("", value["cpath"])
        } finally { engine.close() }
    }

    @Test fun cancellingPureLuaLoopReleasesItsComputeSlot() = cancelLoop(RustScriptEngine.KIND_LUA)
    @Test fun cancellingPureJavascriptLoopReleasesItsComputeSlot() = cancelLoop(RustScriptEngine.KIND_JS)

    private fun cancelLoop(kind: Int) = runBlocking {
        assumeTrue(RustEngineTest.NativeLib.available)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CompletableDeferred<Unit>()
        val card = LoadedCard(CardManifest("native_loop"), File("."), "", EngineKind.LUA)
        val manager = CardRunManager(scope, maxConcurrent = 1) { _, _ ->
            val engine = RustScriptEngine.create(kind)
            try {
                engine.define("probe", Probe(entered))
                engine.eval(if (kind == 0) "probe.started(); while true do end" else "probe.started(); while (true) {}", emptyMap())
            } finally { engine.close() }
        }
        try {
            withTimeout(5_000) {
                val caller = launch { manager.run(card, emptyMap()) }
                entered.await()
                caller.cancelAndJoin()
                val next = manager.run(card, emptyMap()) { _, _ -> EngineResult.Ok(mapOf("released" to true)) }
                assertEquals(true, next.getOrThrow()["released"])
            }
        } finally { scope.cancel() }
    }
}
