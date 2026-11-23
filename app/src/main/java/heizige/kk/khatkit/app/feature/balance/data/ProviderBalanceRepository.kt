package heizige.kk.khatkit.app.feature.balance.data

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import heizige.kk.khatkit.ai.provider.BalanceOption
import heizige.kk.khatkit.ai.provider.CustomHeader
import heizige.kk.khatkit.ai.provider.ProviderSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

// A bare cumulative usage field is not a balance. A formula such as credits - usage is valid.
private val usageField = Regex("""\s*(?:[\w]+\s*\.\s*)*total_usage\s*""", RegexOption.IGNORE_CASE)
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

internal sealed interface BalanceReading {
    data object NotQueried : BalanceReading
    data object Loading : BalanceReading
    data object Unavailable : BalanceReading
    data class Value(val raw: String) : BalanceReading
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

@Singleton
class ProviderBalanceRepository @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("provider_balance_alerts", Context.MODE_PRIVATE)
    internal val episodes = LowBalanceEpisodes(
        wasLow = { prefs.getBoolean(it.toString(), false) },
        save = { id, low -> prefs.edit { if (low) putBoolean(id.toString(), true) else remove(id.toString()) } },
    )

    // The query is shared by settings and the model picker. Threshold edits do not make HTTP requests.
    private data class Source(
        val baseUrl: String, val apiKey: String, val headers: List<CustomHeader>, val path: String, val result: String,
    ) {
        override fun toString() = "BalanceSource(<redacted>)"
    }
    private val queries = LruCache<Uuid, Pair<Source, BalanceQuery>>(32)

    @Synchronized internal fun query(provider: ProviderSetting.OpenAI): BalanceQuery {
        val source = Source(provider.baseUrl, provider.apiKey, provider.customHeaders,
            provider.balanceOption.apiPath, provider.balanceOption.resultPath)
        val cached = queries.get(provider.id)
        if (cached?.first == source) return cached.second
        return BalanceQuery().also { queries.put(provider.id, source to it) }
    }
}
