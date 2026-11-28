package heizige.kk.khatkit.app.feature.balance.helper

import android.os.SystemClock
import heizige.kk.khatkit.ai.provider.BalanceOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.feature.balance.data.BalanceReading




// A bare cumulative usage field is not a balance. A formula such as credits - usage is valid.
internal val usageField = Regex("""\s*(?:[\w]+\s*\.\s*)*total_usage\s*""", RegexOption.IGNORE_CASE)
internal val BalanceOption.isUsageOnly: Boolean get() = usageField.matches(resultPath)

internal fun BalanceOption.thresholdAmount(): BigDecimal? = lowBalanceThreshold.trim()
    .replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

internal fun BalanceOption.isLow(raw: String?): Boolean? {
    if (!enabled || !lowBalanceAlert || isUsageOnly) return null
    val amount = raw?.trim()?.toBigDecimalOrNull() ?: return null
    return amount < (thresholdAmount() ?: return null)
}

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
