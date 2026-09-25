package heizige.kk.khatkit.app.core.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import heizige.kk.khatkit.app.core.data.ai.tools.local.LocalToolOption
import heizige.kk.khatkit.app.core.data.datastore.DEFAULT_ASSISTANT_ID
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.util.JsonInstant

/**
 * 默认助手默认开启全部能力（本地工具 / 记忆 / Web 搜索 / 对话级提示词覆盖）。
 *
 * 只改默认助手，用户自建助手不动；只跑一次，之后用户手动关掉的不会被覆盖。
 */
class PreferenceStoreV4Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsRepository.VERSION]
        return version == null || version < 4
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        val assistantsJson = prefs[SettingsRepository.ASSISTANTS]
        if (assistantsJson != null) {
            runCatching {
                JsonInstant.decodeFromString<List<Assistant>>(assistantsJson)
            }.getOrNull()?.let { assistants ->
                val migrated = assistants.map { assistant ->
                    if (assistant.id == DEFAULT_ASSISTANT_ID) {
                        assistant.copy(
                            localTools = ALL_LOCAL_TOOLS,
                            enableMemory = true,
                            enableRecentChatsReference = true,
                            enableTimeReminder = true,
                            enableWebSearch = true,
                            allowConversationSystemPrompt = true,
                            allowConversationPromptInjection = true,
                        )
                    } else {
                        assistant
                    }
                }
                prefs[SettingsRepository.ASSISTANTS] = JsonInstant.encodeToString(migrated)
            }
        }
        prefs[SettingsRepository.VERSION] = 4
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}

    companion object {
        val ALL_LOCAL_TOOLS = listOf(
            LocalToolOption.JavascriptEngine,
            LocalToolOption.TimeInfo,
            LocalToolOption.Clipboard,
            LocalToolOption.Tts,
            LocalToolOption.AskUser,
            LocalToolOption.ScreenTime,
            LocalToolOption.Calendar,
        )
    }
}
