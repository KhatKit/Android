package heizige.kk.khatkit.app.core.data.terminal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import heizige.kk.khatkit.app.feature.extensions.TerminalPluginInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TerminalPluginRecord(
    val pluginId: String = TerminalPluginInfo.ID,
    val version: String = "",
    val entryClass: String = "",
    val abi: List<String> = emptyList(),
    val sha256: String = "",
    val itemId: String = "",
    val installedAt: Long = 0L,
)

private val Context.terminalPluginDataStore by preferencesDataStore(name = "terminal_plugin")

class TerminalPluginStore(private val context: Context) {
    private val key = stringPreferencesKey("installed")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(): TerminalPluginRecord? = context.terminalPluginDataStore.data
        .map { it[key] }
        .first()
        ?.let { runCatching { json.decodeFromString(TerminalPluginRecord.serializer(), it) }.getOrNull() }

    suspend fun write(record: TerminalPluginRecord) {
        context.terminalPluginDataStore.edit {
            it[key] = json.encodeToString(TerminalPluginRecord.serializer(), record)
        }
    }

    suspend fun clear() = context.terminalPluginDataStore.edit { it.remove(key) }
}
