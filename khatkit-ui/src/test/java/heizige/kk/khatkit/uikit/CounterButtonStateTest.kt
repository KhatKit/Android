package heizige.kk.khatkit.uikit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

class CounterButtonStateTest {
    @Test fun optimisticTapsQueueBehindOneRequestAndReconcileExactlyOnce() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val batches = mutableListOf<Int>()
            val state = CounterButtonState(scope, 100, 2, 0)
            state.update(100, 2, 0) { batches += it }
            repeat(3) { state.tap() }
            assertEquals(103L, state.optimisticCount)
            assertEquals(5L, state.optimisticLocalCount)
            state.flush()
            repeat(4) { state.tap() }
            state.flush()
            assertEquals(listOf(3), batches)
            assertEquals(107L, state.optimisticCount)
            state.update(103, 5, 1) { batches += it }
            state.flush()
            assertEquals(listOf(3, 4), batches)
            state.update(107, 9, 2) { batches += it }
            state.flush()
            assertEquals(listOf(3, 4), batches)
            assertEquals(107L, state.optimisticCount)
            assertEquals(9L, state.optimisticLocalCount)
        } finally { scope.cancel() }
    }

    @Test fun failedBatchRollsBackButKeepsNewTapsAndBatchesNeverExceedTen() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val batches = mutableListOf<Int>()
            val state = CounterButtonState(scope, 100, 0, 0)
            state.update(100, 0, 0) { batches += it }
            state.tap()
            state.flush()
            repeat(25) { state.tap() }
            state.update(100, 0, 1) { batches += it }
            assertEquals(125L, state.optimisticCount) // The failed tap is rolled back.
            state.flush()
            assertEquals(listOf(1, 10), batches)
            state.update(110, 10, 2) { batches += it }
            state.flush()
            state.update(120, 20, 3) { batches += it }
            state.flush()
            assertEquals(listOf(1, 10, 10, 5), batches)
        } finally { scope.cancel() }
    }
}
