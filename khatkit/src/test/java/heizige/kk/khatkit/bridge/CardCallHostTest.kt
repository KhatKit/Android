package heizige.kk.khatkit.bridge

import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.card.CardValidator
import heizige.kk.khatkit.engine.EngineKind
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.exec.CardRunContext
import heizige.kk.khatkit.exec.CardRunManager
import heizige.kk.khatkit.hub.LoadedCard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CardCallHostTest {
    @Test
    fun childRunsWithOneComputePermitAndKeepsTheTrackedParent() = runBlocking {
        val child = card("child", callers = listOf("parent"))
        val parent = card("parent", targets = listOf("child"))
        val manager = CardRunManager(this, maxConcurrent = 1) { current, args ->
            EngineResult.Ok(args + ("card" to current.manifest.name))
        }
        val host = CardCallHost({ child }) { target, args, parentRun, _, _ ->
            assertEquals("parent-run-id", parentRun)
            manager.run(target, args)
        }
        val result = withTimeout(5_000) {
            manager.run(parent, emptyMap()) { current, _ ->
                val bridge = host.bind(current.manifest, BridgeContext("parent", "lua", 1,
                    interactionId = "parent-run-id"), null)
                EngineResult.Ok(bridge.call("child", mapOf("n" to 2, "__khatkit_trigger" to "AI")))
            }
        }
        assertEquals(mapOf("ok" to true, "value" to mapOf("n" to 2, "card" to "child")), result.getOrThrow())
    }

    @Test
    fun declinesUndeclaredMissingUnexportedAndPrivilegedEntries() = inRun {
        val parent = card("parent", targets = listOf("child"))
        var installed: LoadedCard? = null
        val bridge = CardCallHost({ installed }) { _, _, _, _, _ -> error("must not execute") }
            .bind(parent.manifest, context("parent"), null)
        assertEquals("CARD_CALL_NAME", bridge.code("../child"))
        assertEquals("CARD_CALL_NOT_DECLARED", bridge.code("other"))
        assertEquals("CARD_CALL_NOT_INSTALLED", bridge.code("child"))
        installed = card("child")
        assertEquals("CARD_CALL_NOT_EXPORTED", bridge.code("child"))
        for (forbidden in listOf("net", "crypto", "fs", "store", "root", "ui")) {
            installed = card("child", callers = listOf("parent"), bridges = listOf(forbidden))
            assertEquals("CARD_CALL_CAPABILITIES", bridge.code("child"))
        }
    }

    @Test
    fun rejectsCyclesAndSharesBudgetAcrossSiblingCalls() = inRun {
        val parent = card("parent", targets = listOf("child"), callers = listOf("child"))
        val child = card("child", targets = listOf("parent"), callers = listOf("parent"))
        lateinit var host: CardCallHost
        host = CardCallHost({ if (it == "child") child else parent }) { target, _, _, chain, _ ->
            EngineResult.Ok(host.bind(target.manifest, context("child"), chain).call("parent", null))
        }
        val bridge = host.bind(parent.manifest, context("parent"), null)
        repeat(12) {
            val actual = bridge.call("child", null)["value"] as Map<*, *>
            assertEquals("CARD_CALL_CYCLE", (actual["error"] as Map<*, *>)["code"])
        }
        assertEquals("CARD_CALL_LIMIT", bridge.code("child"))
        val depthThree = CardCallChain.root("first").descend("second")!!.descend("third")!!.descend("parent")!!
        assertEquals("CARD_CALL_DEPTH", host.bind(parent.manifest, context("parent"), depthThree).code("child"))
    }

    @Test
    fun cancellingParentStopsChildBeforeReusingPermit() = runBlocking {
        val child = card("child", callers = listOf("parent"))
        val parent = card("parent", targets = listOf("child"))
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val manager = CardRunManager(this, maxConcurrent = 1) { _, _ ->
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                stopped.complete(Unit)
            }
        }
        val host = CardCallHost({ child }) { target, args, _, _, _ -> manager.run(target, args) }
        withTimeout(5_000) {
            val running = async {
                manager.run(parent, emptyMap()) { current, _ ->
                    EngineResult.Ok(host.bind(current.manifest, context("parent"), null).call("child", null))
                }
            }
            started.await()
            running.cancelAndJoin()
            stopped.await()
            assertEquals(EngineResult.Ok(emptyMap()), manager.run(parent, emptyMap()) { _, _ -> EngineResult.Ok(emptyMap()) })
        }
    }

    @Test
    fun validatorRejectsIncompleteOrUnsafeDeclarations() {
        val parent = card("parent", targets = listOf("child")).manifest
        assertTrue(CardValidator.validate(parent).isEmpty())
        val invalid = parent.copy(requires = CardManifest.Requires())
        assertTrue(CardValidator.validate(invalid).any { it.code == "CARD_CALL_BRIDGE" })
        val child = card("child", callers = listOf("parent"), bridges = listOf("net")).manifest
        assertTrue(CardValidator.validate(child).any { it.code == "CARD_CALL_CAPABILITIES" })
    }

    private fun inRun(block: () -> Unit) = runBlocking {
        withContext(CardRunContext.current.asContextElement(CardRunContext(coroutineContext.job))) { block() }
    }

    private fun context(name: String) = BridgeContext(name, "lua", 1)

    private fun CardCallBridge.code(name: String) = (call(name, null)["error"] as? Map<*, *>)?.get("code")

    private fun card(name: String, targets: List<String> = emptyList(), callers: List<String> = emptyList(),
        bridges: List<String> = emptyList()): LoadedCard {
        val manifest = CardManifest(name = name, engine = "lua", entry = CardManifest.Entry(lua = "main.lua"),
            requires = CardManifest.Requires(bridges = bridges + if (targets.isEmpty()) emptyList() else listOf("card")),
            tags = CardManifest.Tags("data", "convert"),
            cardCalls = CardManifest.CardCalls(targets, callers))
        return LoadedCard(manifest, File("."), "", EngineKind.LUA)
    }
}
