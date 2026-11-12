package heizige.kk.khatkit.app.core.data.ssh

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 已安装远程 SSH 插件的记录（DataStore 单键 JSON，不动 Settings 的 schema）。 */
@Serializable
data class SshPluginRecord(
    val pluginId: String = "ssh",
    val version: String = "",
    val entryClass: String = "",
    val abi: List<String> = emptyList(),
    val sha256: String = "",
    val itemId: String = "",
    val installedAt: Long = 0L,
)

private val Context.sshPluginDataStore by preferencesDataStore(name = "ssh_plugin")

/** 插件安装状态存取（安装器写入、加载器读取、扩展页展示）。 */
class SshPluginStore(private val context: Context) {
    private val key = stringPreferencesKey("installed")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(): SshPluginRecord? = runCatching {
        context.sshPluginDataStore.data.map { it[key] }.first()
            ?.let { json.decodeFromString(SshPluginRecord.serializer(), it) }
            ?: null
    }.getOrNull()

    suspend fun write(record: SshPluginRecord) {
        context.sshPluginDataStore.edit { it[key] = json.encodeToString(SshPluginRecord.serializer(), record) }
    }

    suspend fun clear() {
        context.sshPluginDataStore.edit { it.remove(key) }
    }
}
