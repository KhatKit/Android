package heizige.kk.khatkit.app.feature.balance.helper

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.data.model.BalanceReading

/** Unknown samples do not end a low episode. Only the first observer claims the transition. */
internal class LowBalanceEpisodes(
    private val wasLow: (Uuid) -> Boolean,
    private val save: (Uuid, Boolean) -> Unit,
) {
    @Synchronized fun enteredLow(id: Uuid, low: Boolean?): Boolean {
        if (low == null || low == wasLow(id)) return false
        save(id, low)
        return low
    }
}
internal class BalanceQuery(private val now: () -> Long = SystemClock::elapsedRealtime) {
    private val mutex = Mutex()
    private val state = MutableStateFlow<BalanceReading>(BalanceReading.NotQueried)
    val reading = state.asStateFlow()
    private var lastSuccess: Long? = null

    suspend fun refresh(force: Boolean = false, fetch: suspend () -> String) = mutex.withLock {
        if (!force && lastSuccess?.let { now() - it < 120_000 } == true) return@withLock
        lastSuccess = null
        state.value = BalanceReading.Loading
        try {
            val raw = fetch().trim()
            state.value = if (raw.isEmpty()) BalanceReading.Unavailable else BalanceReading.Value(raw)
            if (raw.isNotEmpty()) lastSuccess = now()
        } catch (cancelled: CancellationException) {
            state.value = BalanceReading.Unavailable
            throw cancelled
        } catch (_: Exception) {
            state.value = BalanceReading.Unavailable
        }
    }
}
