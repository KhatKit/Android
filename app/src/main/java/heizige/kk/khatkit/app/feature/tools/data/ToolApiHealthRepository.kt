package heizige.kk.khatkit.app.feature.tools.data

import android.content.Context
import heizige.kk.khatkit.bridge.CardApiObservation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.Locale

@Serializable
internal data class ToolApiStatus(
    val card: String,
    val host: String,
    val method: String,
    val status: Int? = null,
    val timeMillis: Long,
) {
    val issue: Boolean get() = status == null || status >= 400
}

internal class ToolApiHealthRepository(context: Context) {
    private val preferences = context.getSharedPreferences("tool_api_health", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(runCatching {
        Json.decodeFromString<List<ToolApiStatus>>(preferences.getString("entries", "[]") ?: "[]").take(MAX_ENTRIES)
    }.getOrDefault(emptyList()))
    val entries = _entries.asStateFlow()

    @Synchronized
    fun record(observation: CardApiObservation) {
        val entry = observation.toApiStatus() ?: return
        val updated = replaceApiStatus(_entries.value, entry)
        preferences.edit().putString("entries", Json.encodeToString(updated)).apply()
        _entries.value = updated
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove("entries").apply()
        _entries.value = emptyList()
    }
}

private const val MAX_ENTRIES = 200
private val cardName = Regex("[a-z][a-z0-9_-]{0,63}")
private val hostName = Regex("[a-z0-9][a-z0-9.\\-]{0,252}")

internal fun apiHost(value: String): String? {
    val raw = value.trim()
    val host = (if ("://" in raw) runCatching { URI(raw).host }.getOrNull() else raw)
        ?.trimEnd('.')?.lowercase(Locale.ROOT)
    return host?.takeIf(hostName::matches)
}

internal fun CardApiObservation.toApiStatus(): ToolApiStatus? {
    val safeHost = apiHost(host) ?: return null
    if (!cardName.matches(card) || method !in setOf("GET", "HEAD", "POST", "PUT", "DELETE")) return null
    if (status != null && status !in 100..599) return null
    return ToolApiStatus(card, safeHost, method, status, timeMillis)
}

internal fun replaceApiStatus(entries: List<ToolApiStatus>, entry: ToolApiStatus): List<ToolApiStatus> =
    (entries.filterNot { it.card == entry.card && it.host == entry.host && it.method == entry.method } + entry)
        .sortedByDescending { it.timeMillis }.take(MAX_ENTRIES)
