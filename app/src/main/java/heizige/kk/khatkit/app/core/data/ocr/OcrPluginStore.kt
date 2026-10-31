package heizige.kk.khatkit.app.core.data.ocr

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** OCR2：已安装 OCR 插件的记录（DataStore 单键 JSON，避免动 Settings 的 schema）。 */
@Serializable
data class OcrPluginRecord(
    val pluginId: String = "ocr",
    val version: String = "",
    val entryClass: String = "",
    val abi: List<String> = emptyList(),
    val sha256: String = "",
    val itemId: String = "",
    val installedAt: Long = 0L,
)

private val Context.ocrPluginDataStore by preferencesDataStore(name = "ocr_plugin")

/** 插件安装状态存取（安装器写入、加载器读取、市场 UI 展示）。 */
class OcrPluginStore(private val context: Context) {
    private val key = stringPreferencesKey("installed")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(): OcrPluginRecord? = runCatching {
        context.ocrPluginDataStore.data.map { it[key] }.first()
            ?.let { json.decodeFromString(OcrPluginRecord.serializer(), it) }
            ?: null
    }.getOrNull()

    suspend fun write(record: OcrPluginRecord) {
        context.ocrPluginDataStore.edit { it[key] = json.encodeToString(OcrPluginRecord.serializer(), record) }
    }

    suspend fun clear() {
        context.ocrPluginDataStore.edit { it.remove(key) }
    }
}
