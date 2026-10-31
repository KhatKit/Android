package heizige.kk.khatkit.exec

import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineKind
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.hub.LoadedCard
import heizige.kk.khatkit.ui.UiBridgeHost
import heizige.kk.khatkit.ui.UiRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class CardInteractionConcurrencyTest {
    private fun card(name: String) = LoadedCard(CardManifest(name), File("."), "", EngineKind.LUA)

    @Test fun immediateAnswerCannotBeLostBeforeTheScriptStartsWaiting() = runBlocking {
        val host = UiBridgeHost().apply { onHostAttached() }
        // This observer answers inline while enqueue is publishing the request.
        val observer = launch(Dispatchers.Unconfined) {
            host.request.filterIsInstance<UiRequest.Confirm>().collect { host.answerConfirm(true) }
        }
        try {
            withTimeout(5_000) {
                repeat(20) {
                    val run = CardRunContext(currentCoroutineContext().job)
                    val answer = withContext(Dispatchers.IO + CardRunContext.current.asContextElement(run)) {
                        host.confirm("fast", "", false)
                    }
                    assertTrue(answer)
                    assertNull(host.request.value)
                }
            }
        } finally { observer.cancelAndJoin() }
    }

    @Test fun waitingCardsDoNotOccupyComputeSlotsOrOverwriteEachOther() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val host = UiBridgeHost().apply { onHostAttached() }
        val manager = CardRunManager(scope, maxConcurrent = 1) { loaded, _ ->
            if (loaded.manifest.name != "background") {
                EngineResult.Ok(mapOf("confirmed" to host.confirm(loaded.manifest.name, "", false)))
            } else EngineResult.Ok(mapOf("background" to true))
        }
        try {
            withTimeout(5_000) {
                val first = async { manager.run(card("first"), emptyMap()) }
                host.request.first { (it as? UiRequest.Confirm)?.title == "first" }
                val second = async { manager.run(card("second"), emptyMap()) }
                // Both interactive cards may be waiting; a third card must still run.
                val background = manager.run(card("background"), emptyMap()).getOrThrow()
                assertEquals(true, background["background"])
                assertEquals("first", (host.request.value as UiRequest.Confirm).title)
                host.answerConfirm(true)
                assertEquals(true, first.await().getOrThrow()["confirmed"])
                host.request.first { (it as? UiRequest.Confirm)?.title == "second" }
                host.answerConfirm(false)
                assertEquals(false, second.await().getOrThrow()["confirmed"])
                assertNull(host.request.value)
            }
        } finally { scope.cancel(); host.dismiss() }
    }

    @Test fun cancellingVisibleOrQueuedInteractionRemovesOnlyItsOwnRequest() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val host = UiBridgeHost().apply { onHostAttached() }
        val manager = CardRunManager(scope, maxConcurrent = 1) { loaded, _ ->
            EngineResult.Ok(mapOf("answer" to host.confirm(loaded.manifest.name, "", false)))
        }
        try {
            withTimeout(5_000) {
                val first = launch { manager.run(card("first"), emptyMap()) }
                host.request.first { (it as? UiRequest.Confirm)?.title == "first" }
                val second = async { manager.run(card("second"), emptyMap()) }
                // Ensure second has reached its wait by passing another card through the gate.
                manager.run(card("probe"), emptyMap()) { _, _ -> EngineResult.Ok(emptyMap()) }
                first.cancelAndJoin()
                host.request.first { (it as? UiRequest.Confirm)?.title == "second" }
                host.answerConfirm(true)
                assertEquals(true, second.await().getOrThrow()["answer"])
            }
        } finally { scope.cancel(); host.dismiss() }
    }

    @Test fun nullAnswerDoesNotRepeatAnInteraction() = runBlocking {
        val count = AtomicInteger()
        val context = CardRunContext(currentCoroutineContext().job)
        val result = context.awaitInteraction<String?> { count.incrementAndGet(); null }
        assertNull(result)
        assertEquals(1, count.get())
        withContext(CardRunContext.current.asContextElement(context)) {
            assertNull(CardRunContext.awaitInteraction<String?> { count.incrementAndGet(); null })
        }
        assertEquals(2, count.get())
    }

    @Test fun cancelledOwnerScopeDoesNotLeaveCallerWaitingForever() = runBlocking {
        val scope = CoroutineScope(SupervisorJob().apply { cancel() } + Dispatchers.Default)
        val manager = CardRunManager(scope) { _, _ -> error("must not execute") }
        val result = withTimeout(2_000) { manager.run(card("cancelled"), emptyMap()) }
        assertEquals("CARD_CANCELLED", (result as EngineResult.Err).code)
    }

    @Test fun timedOutForegroundHandsTheSheetToTheNextCard() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val host = UiBridgeHost().apply { onHostAttached() }
        val manager = CardRunManager(scope, maxConcurrent = 1) { loaded, _ ->
            val name = loaded.manifest.name
            val answer = UiBridgeHost.withinCard(name) {
                UiBridgeHost.withinBudget(if (name == "first") 300L else 5_000L) {
                    host.confirm(name, "", false)
                }
            }
            EngineResult.Ok(mapOf("answer" to answer))
        }
        try {
            withTimeout(5_000) {
                val first = async { manager.run(card("first"), emptyMap()) }
                host.request.first { (it as? UiRequest.Confirm)?.title == "first" }
                val second = async { manager.run(card("second"), emptyMap()) }
                host.request.first { (it as? UiRequest.Confirm)?.title == "second" }
                assertFalse(host.hasPendingInteraction("first"))
                host.answerConfirm(true)
                assertEquals(false, first.await().getOrThrow()["answer"])
                assertEquals(true, second.await().getOrThrow()["answer"])
            }
        } finally { scope.cancel(); host.dismiss() }
    }

    @Test fun displayOnlyResultSurvivesCompletionAndYieldsToAnotherCardsForm() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val host = UiBridgeHost().apply { onHostAttached() }
        val manager = CardRunManager(scope, maxConcurrent = 1) { _, _ ->
            host.show(mapOf("title" to "result"), null)
            EngineResult.Ok(mapOf("ok" to true))
        }
        try {
            withTimeout(5_000) {
                assertEquals(true, manager.run(card("display"), emptyMap()).getOrThrow()["ok"])
                assertTrue(host.request.value is UiRequest.Show)
                val next = async {
                    manager.run(card("interactive"), emptyMap()) { _, _ ->
                        EngineResult.Ok(mapOf("confirmed" to host.confirm("next", "", false)))
                    }
                }
                host.request.first { it is UiRequest.Confirm }
                host.answerConfirm(true)
                assertEquals(true, next.await().getOrThrow()["confirmed"])
            }
        } finally { scope.cancel(); host.dismiss() }
    }
}
