package heizige.kk.khatkit.exec

import heizige.kk.khatkit.bridge.BridgeRegistry
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineKind
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.hub.LoadedCard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CardExecutorTest {

    private fun card(
        manifest: CardManifest,
        kind: EngineKind,
        script: String = "",
    ) = LoadedCard(manifest = manifest, dir = File("."), scriptText = script, engineKind = kind)

    @Test
    fun commandCardThrowingOnMissingArgBecomesErr() = runBlocking {
        val manifest = CardManifest(name = "cmd_card", engine = "command", command = "pm disable {pkg}")
        val result = CardExecutor(BridgeRegistry(), CommandRunner { "should not run" })
            .execute(card(manifest, EngineKind.COMMAND), emptyMap())
        assertTrue("缺参应返回 Err 而不是抛异常", result is EngineResult.Err)
    }

    @Test
    fun commandCardRejectingIllegalArgBecomesErr() = runBlocking {
        val manifest = CardManifest(name = "cmd_card", engine = "command", command = "pm disable {pkg}")
        val result = CardExecutor(BridgeRegistry(), CommandRunner { "x" })
            .execute(card(manifest, EngineKind.COMMAND), mapOf("pkg" to "com.x; rm -rf /"))
        assertTrue(result is EngineResult.Err)
    }

    @Test
    fun missingCommandRunnerBecomesErr() = runBlocking {
        val manifest = CardManifest(name = "cmd_card", engine = "command", command = "pm disable {pkg}")
        val result = CardExecutor(BridgeRegistry())
            .execute(card(manifest, EngineKind.COMMAND), mapOf("pkg" to "com.example"))
        assertTrue(result is EngineResult.Err)
    }

    @Test
    fun missingRequiredBridgeBecomesErr() = runBlocking {
        val manifest = CardManifest(
            name = "elevated_card",
            engine = "lua",
            entry = CardManifest.Entry(lua = "main.lua"),
            requires = CardManifest.Requires(bridges = listOf("root")),
        )
        val result = CardExecutor(BridgeRegistry())
            .execute(card(manifest, EngineKind.LUA, "return {}"), emptyMap())
        assertTrue(result is EngineResult.Err)
    }

    @Test
    fun luaCardRunsWithoutBridges() = runBlocking {
        val manifest = CardManifest(
            name = "plain",
            engine = "lua",
            entry = CardManifest.Entry(lua = "main.lua"),
        )
        val result = CardExecutor(BridgeRegistry())
            .execute(card(manifest, EngineKind.LUA, "return { ok = true }"), emptyMap())
        assertTrue(result is EngineResult.Ok)
    }
}
