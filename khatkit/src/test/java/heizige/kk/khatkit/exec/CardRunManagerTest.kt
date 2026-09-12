package heizige.kk.khatkit.exec

import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineKind
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.hub.LoadedCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class CardRunManagerTest {

    private fun card(name: String) = LoadedCard(
        manifest = CardManifest(
            name = name,
            engine = "lua",
            entry = CardManifest.Entry(lua = "main.lua"),
        ),
        dir = File("."),
        scriptText = "",
        engineKind = EngineKind.LUA,
    )

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun successfulRunEndsInDoneState() = runBlocking {
        val manager = CardRunManager(scope()) { _, _ -> EngineResult.Ok(mapOf("ok" to true)) }
        val result = manager.run(card("demo"), emptyMap())

        assertTrue(result is EngineResult.Ok)
        val info = manager.snapshot().first { it.cardName == "demo" }
        assertEquals(CardRunState.DONE, info.state)
    }

    @Test
    fun failedRunEndsInFailedState() = runBlocking {
        val manager = CardRunManager(scope()) { _, _ -> EngineResult.Err("BOOM", "炸了") }
        manager.run(card("demo"), emptyMap())

        val info = manager.snapshot().first { it.cardName == "demo" }
        assertEquals(CardRunState.FAILED, info.state)
        assertEquals("炸了", info.error)
    }

    @Test
    fun exceptionBecomesFailedRun() = runBlocking {
        val manager = CardRunManager(scope()) { _, _ -> error("kaboom") }
        val result = manager.run(card("demo"), emptyMap())

        assertTrue(result is EngineResult.Err)
        assertEquals(CardRunState.FAILED, manager.snapshot().first().state)
    }

    @Test
    fun concurrencyIsCapped() = runBlocking {
        val active = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)
        val manager = CardRunManager(scope(), maxConcurrent = 2) { _, _ ->
            val now = active.incrementAndGet()
            maxSeen.updateAndGet { maxOf(it, now) }
            delay(80)
            active.decrementAndGet()
            EngineResult.Ok(emptyMap())
        }

        val jobs = (1..5).map { index -> launch { manager.run(card("c$index"), emptyMap()) } }
        jobs.joinAll()

        assertTrue("并发上限失效：max=$maxSeen", maxSeen.get() <= 2)
    }

    @Test
    fun cancelMarksRunCancelled() = runBlocking {
        val manager = CardRunManager(scope(), maxConcurrent = 1) { _, _ ->
            delay(10_000)
            EngineResult.Ok(emptyMap())
        }

        val pending = async { manager.run(card("slow"), emptyMap()) }
        withTimeout(2_000) {
            manager.runs.first { state ->
                state.values.any { it.state == CardRunState.RUNNING }
            }
        }
        val running = manager.snapshot().first { it.state == CardRunState.RUNNING }
        assertTrue(manager.cancel(running.id))

        val result = withTimeout(2_000) { pending.await() }
        assertTrue(result is EngineResult.Err)
        assertEquals(CardRunState.CANCELLED, manager.snapshot().first { it.id == running.id }.state)
    }
}
