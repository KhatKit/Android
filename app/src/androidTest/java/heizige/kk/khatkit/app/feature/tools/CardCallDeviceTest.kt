package heizige.kk.khatkit.app.feature.tools

import heizige.kk.khatkit.bridge.BridgeRegistry
import heizige.kk.khatkit.bridge.CardCallHost
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineKind
import heizige.kk.khatkit.exec.CardExecutionOptions
import heizige.kk.khatkit.exec.CardExecutor
import heizige.kk.khatkit.exec.CardRunManager
import heizige.kk.khatkit.hub.LoadedCard
import heizige.kk.khatkit.ui.UiMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class CardCallDeviceTest {
    @Test
    fun luaCallsJsAndChildCannotCallItsAncestor() = runBlocking {
        val parent = fixture("fixture_parent", EngineKind.LUA, "fixture_child", "fixture_child", """
            return card.call("fixture_child", { n = 6 })
        """.trimIndent())
        val child = fixture("fixture_child", EngineKind.JS, "fixture_parent", "fixture_parent", """
            const result = card.call("fixture_parent", {});
            return { number: args.n * 7, cycle: result.error.code };
        """.trimIndent())
        lateinit var executor: CardExecutor
        val manager = CardRunManager(this, maxConcurrent = 1) { card, input -> executor.execute(card, input) }
        val host = CardCallHost({ name -> listOf(parent, child).find { it.manifest.name == name } }) {
                card, input, parentId, chain, deadline ->
            assertEquals("fixture-run-id", parentId)
            manager.run(card, input) { target, args ->
                executor.execute(target, args, CardExecutionOptions(
                    uiMode = UiMode.HEADLESS, deadlineAt = deadline, cardCallChain = chain,
                ))
            }
        }
        executor = CardExecutor(BridgeRegistry(cardCallHost = host))
        val result = withTimeout(10_000) {
            manager.run(parent, emptyMap()) { card, input ->
                executor.execute(card, input, CardExecutionOptions(interactionId = "fixture-run-id"))
            }
        }.getOrThrow()
        assertEquals(true, result["ok"])
        val value = result["value"] as Map<*, *>
        assertEquals(42, (value["number"] as Number).toInt())
        assertEquals("CARD_CALL_CYCLE", value["cycle"])
    }

    private fun fixture(name: String, kind: EngineKind, target: String, caller: String, code: String) = LoadedCard(
        manifest = CardManifest(
            name = name,
            engine = kind.name.lowercase(),
            requires = CardManifest.Requires(bridges = listOf("card")),
            cardCalls = CardManifest.CardCalls(targets = listOf(target), allowedCallers = listOf(caller)),
        ),
        dir = File("."),
        scriptText = code,
        engineKind = kind,
    )
}
