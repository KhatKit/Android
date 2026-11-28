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
import heizige.kk.khatkit.app.feature.balance.helper.LowBalanceEpisodes
import heizige.kk.khatkit.app.feature.balance.helper.BalanceQuery
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
