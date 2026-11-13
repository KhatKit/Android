package heizige.kk.khatkit.app.core.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.IOException
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.core.ReasoningLevel
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import heizige.kk.khatkit.app.core.data.ai.prompts.DEFAULT_OCR_PROMPT
import heizige.kk.khatkit.app.core.data.ai.prompts.DEFAULT_TITLE_PROMPT
import heizige.kk.khatkit.app.core.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import heizige.kk.khatkit.app.core.data.ai.prompts.LEARNING_MODE_PROMPT
import heizige.kk.khatkit.asr.ASRProviderSetting
import heizige.kk.khatkit.app.core.data.datastore.migration.PreferenceStoreV1Migration
import heizige.kk.khatkit.app.core.data.datastore.migration.PreferenceStoreV2Migration
import heizige.kk.khatkit.app.core.data.datastore.migration.PreferenceStoreV3Migration
import heizige.kk.khatkit.app.core.data.datastore.migration.PreferenceStoreV4Migration
import heizige.kk.khatkit.app.core.data.datastore.migration.PreferenceStoreV5Migration
import heizige.kk.khatkit.app.core.data.datastore.migration.PreferenceStoreV6Migration
import heizige.kk.khatkit.app.core.data.datastore.migration.PreferenceStoreV7Migration
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.Avatar
import heizige.kk.khatkit.app.core.data.model.InjectionPosition
import heizige.kk.khatkit.app.core.data.model.Lorebook
import heizige.kk.khatkit.app.core.data.model.PromptInjection
import heizige.kk.khatkit.app.core.data.model.QuickMessage
import heizige.kk.khatkit.app.core.data.model.RoleTemplates
import heizige.kk.khatkit.app.core.data.model.Tag
import heizige.kk.khatkit.app.core.data.sync.s3.S3Config
import heizige.kk.khatkit.app.core.ui.theme.CustomTheme
import heizige.kk.khatkit.uikit.mochi.NavBarLabelStyle
import heizige.kk.khatkit.app.core.ui.theme.PresetThemes
import heizige.kk.khatkit.app.core.util.JsonInstant
import heizige.kk.khatkit.app.core.util.toMutableStateFlow
import heizige.kk.khatkit.search.SearchCommonOptions
import heizige.kk.khatkit.search.SearchOptions
import heizige.kk.khatkit.tts.provider.TTSProviderSetting
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

private const val TAG = "PreferencesStore"
private const val SETTINGS_STORE_NAME = "settings"
private const val READ_MAX_RETRIES = 3

@Volatile
private var settingsDataStore: DataStore<Preferences>? = null

private val Context.settingsStore: DataStore<Preferences>
    get() = settingsDataStore ?: synchronized(SettingsRepository::class) {
        settingsDataStore ?: createSettingsDataStore(applicationContext).also { settingsDataStore = it }
    }

private fun createSettingsDataStore(context: Context): DataStore<Preferences> {
    val file = File(context.filesDir, "datastore/$SETTINGS_STORE_NAME.preferences_pb").also {
        it.parentFile?.mkdirs()
    }
    return PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { exception ->
            Log.e(TAG, "Settings datastore corrupted, resetting", exception)
            runCatching {
                file.copyTo(File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}"))
            }.onFailure { Log.e(TAG, "Failed to backup corrupted settings file", it) }
            emptyPreferences()
        },
        migrations = listOf(
            PreferenceStoreV1Migration(),
            PreferenceStoreV2Migration(),
            PreferenceStoreV3Migration(),
            PreferenceStoreV4Migration(),
            PreferenceStoreV5Migration(),
            PreferenceStoreV6Migration(),
            PreferenceStoreV7Migration(),
        ),
        produceFile = { file },
    )
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    scope: AppScope,
    private val pebbleEngine: Lazy<PebbleEngine>,
) {
    private val updateMutex = Mutex()

    companion object {
        // 版本号
        val VERSION = intPreferencesKey("data_version")

        // UI设置
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_ID = stringPreferencesKey("theme_id")
        val CUSTOM_THEMES = stringPreferencesKey("custom_themes")
        val DISPLAY_SETTING = stringPreferencesKey("display_setting")
        val NETWORK_SETTING = stringPreferencesKey("network_setting")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")

        // 模型选择
        val FAVORITE_MODELS = stringPreferencesKey("favorite_models")
        val SELECT_MODEL = stringPreferencesKey("chat_model")
        val FAST_MODEL = stringPreferencesKey("fast_model")
        val FAST_MODEL_REASONING_LEVEL = stringPreferencesKey("fast_model_reasoning_level")
        val MEMORY_MODEL = stringPreferencesKey("memory_model")
        val MEMORY_MODEL_REASONING_LEVEL = stringPreferencesKey("memory_model_reasoning_level")
        val TRANSLATE_MODEL = stringPreferencesKey("translate_model")
        val IMAGE_GENERATION_MODEL = stringPreferencesKey("image_generation_model")
        val TITLE_PROMPT = stringPreferencesKey("title_prompt")
        val TRANSLATION_PROMPT = stringPreferencesKey("translation_prompt")
        val TRANSLATE_THINKING_BUDGET = intPreferencesKey("translate_thinking_budget")
        val OCR_MODEL = stringPreferencesKey("ocr_model")
        val OCR_PROMPT = stringPreferencesKey("ocr_prompt")
        val COMPRESS_MODEL = stringPreferencesKey("compress_model")
        val COMPRESS_PROMPT = stringPreferencesKey("compress_prompt")
        val ENABLE_AUTO_COMPRESS = booleanPreferencesKey("enable_auto_compress")
        val AUTO_COMPRESS_THRESHOLD = floatPreferencesKey("auto_compress_threshold")

        // 提供商
        val PROVIDERS = stringPreferencesKey("providers")

        // 助手
        val SELECT_ASSISTANT = stringPreferencesKey("select_assistant")
        val ASSISTANTS = stringPreferencesKey("assistants")
        val ASSISTANT_TAGS = stringPreferencesKey("assistant_tags")

        // 搜索
        val SEARCH_SERVICES = stringPreferencesKey("search_services")
        val SEARCH_COMMON = stringPreferencesKey("search_common")
        val SEARCH_SELECTED = intPreferencesKey("search_selected")

        // MCP
        val MCP_SERVERS = stringPreferencesKey("mcp_servers")

        // WebDAV
        val WEBDAV_CONFIG = stringPreferencesKey("webdav_config")

        // S3
        val S3_CONFIG = stringPreferencesKey("s3_config")

        // TTS
        val TTS_PROVIDERS = stringPreferencesKey("tts_providers")
        val SELECTED_TTS_PROVIDER = stringPreferencesKey("selected_tts_provider")
        val DEFAULT_TTS_PLAYBACK_SPEED = floatPreferencesKey("default_tts_playback_speed")

        // ASR
        val ASR_PROVIDERS = stringPreferencesKey("asr_providers")
        val SELECTED_ASR_PROVIDER = stringPreferencesKey("selected_asr_provider")

        // Web Server
        val WEB_SERVER_ENABLED = booleanPreferencesKey("web_server_enabled")
        val WEB_SERVER_PORT = intPreferencesKey("web_server_port")
        val WEB_SERVER_JWT_ENABLED = booleanPreferencesKey("web_server_jwt_enabled")
        val WEB_SERVER_ACCESS_PASSWORD = stringPreferencesKey("web_server_access_password")
        val WEB_SERVER_LOCALHOST_ONLY = booleanPreferencesKey("web_server_localhost_only")

        // 提示词注入
        val MODE_INJECTIONS = stringPreferencesKey("mode_injections")
        val LOREBOOKS = stringPreferencesKey("lorebooks")
        val QUICK_MESSAGES = stringPreferencesKey("quick_messages")
        val FEATURE_FLAGS = stringPreferencesKey("feature_flags")

        // 备份提醒
        val BACKUP_REMINDER_CONFIG = stringPreferencesKey("backup_reminder_config")

        // 统计
        val LAUNCH_COUNT = intPreferencesKey("launch_count")

        // Uses the same DataStore singleton without starting settings flows or requiring Koin.
        internal suspend fun restoreBeforeInitialization(context: Context, settings: Settings) {
            require(!settings.init) { "Cannot restore uninitialized settings" }
            persistSettings(context.settingsStore, settings)
        }

        private suspend fun persistSettings(dataStore: DataStore<Preferences>, settings: Settings) {
            dataStore.edit { preferences ->
                preferences[DYNAMIC_COLOR] = settings.dynamicColor
                preferences[THEME_ID] = settings.themeId
                preferences[CUSTOM_THEMES] = JsonInstant.encodeToString(settings.customThemes)
                preferences[DEVELOPER_MODE] = settings.developerMode
                preferences[DISPLAY_SETTING] = JsonInstant.encodeToString(settings.displaySetting)
                preferences[NETWORK_SETTING] = JsonInstant.encodeToString(settings.networkSetting)

                preferences[FAVORITE_MODELS] = JsonInstant.encodeToString(settings.favoriteModels)
                preferences[SELECT_MODEL] = settings.chatModelId.toString()
                preferences[FAST_MODEL] = settings.fastModelId.toString()
                preferences[FAST_MODEL_REASONING_LEVEL] = settings.fastModelReasoningLevel.name
                // 没设置时把 key 删掉，否则清除后重启会读回旧值
                val memoryModelId = settings.memoryModelId
                if (memoryModelId != null) {
                    preferences[MEMORY_MODEL] = memoryModelId.toString()
                } else {
                    preferences.remove(MEMORY_MODEL)
                }
                preferences[MEMORY_MODEL_REASONING_LEVEL] = settings.memoryModelReasoningLevel.name
                preferences[TRANSLATE_MODEL] = settings.translateModeId.toString()
                preferences[IMAGE_GENERATION_MODEL] = settings.imageGenerationModelId.toString()
                preferences[TITLE_PROMPT] = settings.titlePrompt
                preferences[TRANSLATION_PROMPT] = settings.translatePrompt
                preferences[TRANSLATE_THINKING_BUDGET] = settings.translateThinkingBudget
                preferences[OCR_MODEL] = settings.ocrModelId.toString()
                preferences[OCR_PROMPT] = settings.ocrPrompt
                preferences[COMPRESS_MODEL] = settings.compressModelId.toString()
                preferences[COMPRESS_PROMPT] = settings.compressPrompt
                preferences[ENABLE_AUTO_COMPRESS] = settings.enableAutoCompress
                preferences[AUTO_COMPRESS_THRESHOLD] = settings.autoCompressThreshold

                preferences[PROVIDERS] = JsonInstant.encodeToString(settings.providers)

                preferences[ASSISTANTS] = JsonInstant.encodeToString(settings.assistants)
                preferences[SELECT_ASSISTANT] = settings.assistantId.toString()
                preferences[ASSISTANT_TAGS] = JsonInstant.encodeToString(settings.assistantTags)

                preferences[SEARCH_SERVICES] = JsonInstant.encodeToString(settings.searchServices)
                preferences[SEARCH_COMMON] = JsonInstant.encodeToString(settings.searchCommonOptions)
                preferences[SEARCH_SELECTED] = settings.searchServiceSelected.coerceIn(0, (settings.searchServices.size - 1).coerceAtLeast(0))

                preferences[MCP_SERVERS] = JsonInstant.encodeToString(settings.mcpServers)
                preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(settings.webDavConfig)
                preferences[S3_CONFIG] = JsonInstant.encodeToString(settings.s3Config)
                preferences[TTS_PROVIDERS] = JsonInstant.encodeToString(settings.ttsProviders)
                settings.selectedTTSProviderId?.let {
                    preferences[SELECTED_TTS_PROVIDER] = it.toString()
                } ?: preferences.remove(SELECTED_TTS_PROVIDER)
                preferences[DEFAULT_TTS_PLAYBACK_SPEED] = settings.defaultTTSPlaybackSpeed.coerceIn(0.5f, 2.0f)
                preferences[ASR_PROVIDERS] = JsonInstant.encodeToString(settings.asrProviders)
                settings.selectedASRProviderId?.let {
                    preferences[SELECTED_ASR_PROVIDER] = it.toString()
                } ?: preferences.remove(SELECTED_ASR_PROVIDER)
                preferences[MODE_INJECTIONS] = JsonInstant.encodeToString(settings.modeInjections)
                preferences[LOREBOOKS] = JsonInstant.encodeToString(settings.lorebooks)
                preferences[QUICK_MESSAGES] = JsonInstant.encodeToString(settings.quickMessages)
                preferences[FEATURE_FLAGS] = JsonInstant.encodeToString(settings.featureFlags)
                preferences[WEB_SERVER_ENABLED] = settings.webServerEnabled
                preferences[WEB_SERVER_PORT] = settings.webServerPort
                preferences[WEB_SERVER_JWT_ENABLED] = settings.webServerJwtEnabled
                preferences[WEB_SERVER_ACCESS_PASSWORD] = settings.webServerAccessPassword
                preferences[WEB_SERVER_LOCALHOST_ONLY] = settings.webServerLocalhostOnly
                preferences[BACKUP_REMINDER_CONFIG] = JsonInstant.encodeToString(settings.backupReminderConfig)
                preferences[LAUNCH_COUNT] = settings.launchCount
            }
        }
    }

    private val dataStore = context.settingsStore

    val settingsFlowRaw = dataStore.data
        .retryWhen { cause, attempt ->
            val shouldRetry = cause is IOException && cause !is CorruptionException && attempt < READ_MAX_RETRIES
            if (shouldRetry) {
                Log.w(TAG, "Failed to read settings, retrying (${attempt + 1}/$READ_MAX_RETRIES)", cause)
                delay((100L shl attempt.toInt()).milliseconds)
            }
            shouldRetry
        }.map { preferences ->
            Settings(
                favoriteModels = preferences[FAVORITE_MODELS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                chatModelId = preferences[SELECT_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                fastModelId = preferences[FAST_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                fastModelReasoningLevel = preferences[FAST_MODEL_REASONING_LEVEL]
                    ?.let { value -> ReasoningLevel.entries.find { it.name == value } }
                    ?: ReasoningLevel.AUTO,
                memoryModelId = preferences[MEMORY_MODEL]?.let { Uuid.parse(it) },
                memoryModelReasoningLevel = preferences[MEMORY_MODEL_REASONING_LEVEL]
                    ?.let { value -> ReasoningLevel.entries.find { it.name == value } }
                    ?: ReasoningLevel.AUTO,
                translateModeId = preferences[TRANSLATE_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                imageGenerationModelId = preferences[IMAGE_GENERATION_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                titlePrompt = preferences[TITLE_PROMPT] ?: DEFAULT_TITLE_PROMPT,
                translatePrompt = preferences[TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
                translateThinkingBudget = preferences[TRANSLATE_THINKING_BUDGET] ?: 0,
                ocrModelId = preferences[OCR_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                ocrPrompt = preferences[OCR_PROMPT] ?: DEFAULT_OCR_PROMPT,
                compressModelId = preferences[COMPRESS_MODEL]?.let { Uuid.parse(it) } ?: DEFAULT_AUTO_MODEL_ID,
                compressPrompt = preferences[COMPRESS_PROMPT] ?: DEFAULT_COMPRESS_PROMPT,
                enableAutoCompress = preferences[ENABLE_AUTO_COMPRESS] != false,
                autoCompressThreshold = (preferences[AUTO_COMPRESS_THRESHOLD] ?: 0.8f)
                    .coerceIn(AUTO_COMPRESS_THRESHOLD_RANGE),
                assistantId = preferences[SELECT_ASSISTANT]?.let { Uuid.parse(it) }
                    ?: DEFAULT_ASSISTANT_ID,
                assistantTags = preferences[ASSISTANT_TAGS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                providers = JsonInstant.decodeFromString(preferences[PROVIDERS] ?: "[]"),
                assistants = JsonInstant.decodeFromString(preferences[ASSISTANTS] ?: "[]"),
                dynamicColor = preferences[DYNAMIC_COLOR] != false,
                themeId = preferences[THEME_ID] ?: PresetThemes[0].id,
                customThemes = preferences[CUSTOM_THEMES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                developerMode = preferences[DEVELOPER_MODE] == true,
                displaySetting = JsonInstant.decodeFromString(preferences[DISPLAY_SETTING] ?: "{}"),
                networkSetting = JsonInstant.decodeFromString(preferences[NETWORK_SETTING] ?: "{}"),
                searchServices = preferences[SEARCH_SERVICES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: listOf(SearchOptions.DEFAULT),
                searchCommonOptions = preferences[SEARCH_COMMON]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: SearchCommonOptions(),
                searchServiceSelected = preferences[SEARCH_SELECTED] ?: 0,
                mcpServers = preferences[MCP_SERVERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                webDavConfig = preferences[WEBDAV_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: WebDavConfig(),
                s3Config = preferences[S3_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: S3Config(),
                ttsProviders = preferences[TTS_PROVIDERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                selectedTTSProviderId = preferences[SELECTED_TTS_PROVIDER]?.let { Uuid.parse(it) }
                    ?: DEFAULT_SYSTEM_TTS_ID,
                defaultTTSPlaybackSpeed = preferences[DEFAULT_TTS_PLAYBACK_SPEED]?.coerceIn(0.5f, 2.0f) ?: 1.0f,
                asrProviders = preferences[ASR_PROVIDERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                selectedASRProviderId = preferences[SELECTED_ASR_PROVIDER]?.let { Uuid.parse(it) },
                modeInjections = preferences[MODE_INJECTIONS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                lorebooks = preferences[LOREBOOKS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                quickMessages = preferences[QUICK_MESSAGES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                webServerEnabled = preferences[WEB_SERVER_ENABLED] == true,
                webServerPort = preferences[WEB_SERVER_PORT] ?: 8080,
                webServerJwtEnabled = preferences[WEB_SERVER_JWT_ENABLED] == true,
                webServerAccessPassword = preferences[WEB_SERVER_ACCESS_PASSWORD] ?: "",
                webServerLocalhostOnly = preferences[WEB_SERVER_LOCALHOST_ONLY] == true,
                backupReminderConfig = preferences[BACKUP_REMINDER_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: BackupReminderConfig(),
                launchCount = preferences[LAUNCH_COUNT] ?: 0,
                featureFlags = preferences[FEATURE_FLAGS]?.let {
                    runCatching { JsonInstant.decodeFromString<FeatureFlags>(it) }.getOrDefault(FeatureFlags())
                } ?: FeatureFlags(),
            )
        }
        .map {
            var providers = it.providers.ifEmpty { DEFAULT_PROVIDERS }.toMutableList()
            DEFAULT_PROVIDERS.forEach { defaultProvider ->
                if (providers.none { it.id == defaultProvider.id }) {
                    providers.add(defaultProvider.copyProvider())
                }
            }
            providers = providers.map { provider ->
                val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
                if (defaultProvider != null) {
                    provider.copyProvider(
                        builtIn = defaultProvider.builtIn,
                        description = defaultProvider.description,
                        shortDescription = defaultProvider.shortDescription,
                    )
                } else provider
            }.toMutableList()
            val assistants = normalizeAssistants(it.assistants).map(::migrateLegacyDefaultRole)
            val ttsProviders = it.ttsProviders.ifEmpty { DEFAULT_TTS_PROVIDERS }.toMutableList()
            DEFAULT_TTS_PROVIDERS.forEach { defaultTTSProvider ->
                if (ttsProviders.none { provider -> provider.id == defaultTTSProvider.id }) {
                    ttsProviders.add(defaultTTSProvider.copyProvider())
                }
            }
            it.copy(
                providers = providers,
                assistants = assistants,
                ttsProviders = ttsProviders,
            )
        }
        .map { settings ->
            // 去重并清理无效引用
            val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
            val validModeInjectionIds = settings.modeInjections.map { it.id }.toSet()
            val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
            val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
            val asrProviders = settings.asrProviders.distinctBy { it.id }
            settings.copy(
                providers = settings.providers.distinctBy { it.id }.map { provider ->
                    when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Google -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Claude -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.ChatGPT -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Grok -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )
                        else -> provider
                    }
                },
                assistants = settings.assistants.distinctBy { it.id }.map { assistant ->
                    assistant.copy(
                        // 过滤掉不存在的 MCP 服务器 ID
                        mcpServers = assistant.mcpServers.filter { serverId ->
                            serverId in validMcpServerIds
                        }.toSet(),
                        // 过滤掉不存在的模式注入 ID
                        modeInjectionIds = assistant.modeInjectionIds.filter { id ->
                            id in validModeInjectionIds
                        }.toSet(),
                        // 过滤掉不存在的 Lorebook ID
                        lorebookIds = assistant.lorebookIds.filter { id ->
                            id in validLorebookIds
                        }.toSet(),
                        // 过滤掉不存在的快捷消息 ID
                        quickMessageIds = assistant.quickMessageIds.filter { id ->
                            id in validQuickMessageIds
                        }.toSet()
                    )
                },
                ttsProviders = settings.ttsProviders.distinctBy { it.id },
                asrProviders = asrProviders,
                selectedASRProviderId = settings.selectedASRProviderId
                    ?.takeIf { id -> asrProviders.any { provider -> provider.id == id } }
                    ?: asrProviders.firstOrNull()?.id,
                favoriteModels = settings.favoriteModels.filter { uuid ->
                    settings.providers.flatMap { it.models }.any { it.id == uuid }
                },
                modeInjections = settings.modeInjections.distinctBy { it.id },
                lorebooks = settings.lorebooks.distinctBy { it.id },
                quickMessages = settings.quickMessages.distinctBy { it.id },
            )
        }
        .map { it.withRoleModels() }
        .onEach {
            pebbleEngine.get().templateCache.invalidateAll()
        }

    val settingsFlow = settingsFlowRaw
        .distinctUntilChanged()
        .toMutableStateFlow(scope, Settings.dummy())

    suspend fun update(settings: Settings) = updateMutex.withLock {
        persistUpdate(settings)
    }

    private suspend fun persistUpdate(settings: Settings) {
        if(settings.init) {
            Log.w(TAG, "Cannot update dummy settings")
            return
        }
        // 助手列表非空是全局不变量。读取路径已兜底（见 settingsFlowRaw 里的 normalizeAssistants），
        // 写入路径也必须兜底：否则 settingsFlow 会在 persist 之前短暂持有空列表，
        // 让 getCurrentAssistant() 的 first() 抛 NoSuchElementException（真机删除最后一个助手即崩溃）。
        val updated = settings.copy(
            assistants = normalizeAssistants(settings.assistants).map(::migrateLegacyDefaultRole),
        ).withRoleModels()
        val previous = settingsFlow.value
        settingsFlow.value = updated
        try {
            persistSettings(dataStore, updated)
        } catch (error: Exception) {
            settingsFlow.value = previous
            throw error
        }
    }

    suspend fun incrementLaunchCount(): Int {
        var count = 0
        dataStore.edit { preferences ->
            count = (preferences[LAUNCH_COUNT] ?: 0) + 1
            preferences[LAUNCH_COUNT] = count
        }
        return count
    }

    suspend fun update(fn: (Settings) -> Settings) = updateMutex.withLock {
        persistUpdate(fn(settingsFlow.value))
    }

    suspend fun updateAssistant(assistantId: Uuid) {
        update { current ->
            current.copy(
                assistantId = assistantId.takeIf { id -> current.assistants.any { it.id == id } }
                    ?: current.getCurrentAssistant().id,
            )
        }
    }

    suspend fun updateAssistantModel(assistantId: Uuid, modelId: Uuid) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(chatModelId = modelId)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantReasoningLevel(assistantId: Uuid, reasoningLevel: ReasoningLevel) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(reasoningLevel = reasoningLevel)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantWebSearch(assistantId: Uuid, enabled: Boolean) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(enableWebSearch = enabled)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantMcpServers(assistantId: Uuid, mcpServers: Set<Uuid>) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(mcpServers = mcpServers)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantInjections(
        assistantId: Uuid,
        modeInjectionIds: Set<Uuid>,
        lorebookIds: Set<Uuid>,
        quickMessageIds: Set<Uuid> = emptySet(),
    ) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(
                            modeInjectionIds = modeInjectionIds,
                            lorebookIds = lorebookIds,
                            quickMessageIds = quickMessageIds,
                        )
                    } else {
                        assistant
                    }
                }
            )
        }
    }
}

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val customThemes: List<CustomTheme> = emptyList(),
    val customColorHistory: List<Long> = emptyList(),
    val listCardLargeCorner: Float = 26f,
    val listCardSmallCorner: Float = 0f,
    val listCardGap: Float = 0f,
    val developerMode: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val networkSetting: NetworkSetting = NetworkSetting(),
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val fastModelId: Uuid = Uuid.random(),
    val fastModelReasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    // 记忆整理用的模型；没设置时用快速模型（上游 72d419171）
    val memoryModelId: Uuid? = null,
    val memoryModelReasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val imageGenerationModelId: Uuid = Uuid.random(),
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val translateModeId: Uuid = Uuid.random(),
    val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val translateThinkingBudget: Int = 0,
    val ocrModelId: Uuid = Uuid.random(),
    val ocrPrompt: String = DEFAULT_OCR_PROMPT,
    val compressModelId: Uuid = Uuid.random(),
    val compressPrompt: String = DEFAULT_COMPRESS_PROMPT,
    val enableAutoCompress: Boolean = true,
    val autoCompressThreshold: Float = 0.8f,
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val searchServices: List<SearchOptions> = listOf(SearchOptions.DEFAULT),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    val ttsProviders: List<TTSProviderSetting> = DEFAULT_TTS_PROVIDERS,
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val defaultTTSPlaybackSpeed: Float = 1.0f,
    val asrProviders: List<ASRProviderSetting> = emptyList(),
    val selectedASRProviderId: Uuid? = null,
    val modeInjections: List<PromptInjection.ModeInjection> = DEFAULT_MODE_INJECTIONS,
    val lorebooks: List<Lorebook> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    val webServerEnabled: Boolean = false,
    val webServerPort: Int = 8080,
    val webServerJwtEnabled: Boolean = false,
    val webServerAccessPassword: String = "",
    val webServerLocalhostOnly: Boolean = false,
    val backupReminderConfig: BackupReminderConfig = BackupReminderConfig(),
    val launchCount: Int = 0,
    val featureFlags: FeatureFlags = FeatureFlags(),
) {
    companion object {
        // 构造一个用于初始化的settings, 但它不能用于保存，防止使用初始值存储
        fun dummy() = Settings(init = true)
    }
}

val AUTO_COMPRESS_THRESHOLD_RANGE = 0.5f..0.95f

@Serializable
data class FeatureFlags(
    val marketNewKinds: Boolean = false,
    val pluginHooksEnabled: Boolean = true,
)

fun FeatureFlags.visibleMarketKinds(): List<String> =
    if (marketNewKinds) {
        listOf("card", "skill", "mcp", "prompt", "workflow", "dependency", "provider_plugin")
    } else {
        listOf("card")
    }

@Serializable
data class NetworkSetting(
    val userAgent: String = "",
    val proxyUrl: String = "",
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    val enableAutoRetry: Boolean = true,
)

@Serializable
enum class ChatFontFamily {
    @SerialName("default")
    DEFAULT,
    @SerialName("serif")
    SERIF,
    @SerialName("monospace")
    MONOSPACE,

    @SerialName("custom")
    CUSTOM,
}

@Serializable
enum class BackgroundEffectType {
    @SerialName("blur")
    BLUR,

    @SerialName("glass")
    GLASS,
}

@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val useAppIconStyleLoadingIndicator: Boolean = true,
    val showUserAvatar: Boolean = true,
    // 默认开：助手回答与用户消息一样带气泡。开关仍在「消息显示」页，关掉退回裸 Markdown。
    // ⚠️ 本字段随 `encodeDefaults = true` 一起落盘，所以**既有安装**的 JSON 里是显式的
    // false —— 改这个默认值只影响新装。老安装由 `PreferenceStoreV6Migration` 一次性翻成
    // true（版本记到 6），之后用户手动关掉不会被覆盖。
    val showAssistantBubble: Boolean = true,
    val bubbleOpacity: Float = 1.0f,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showDateTimeInMessage: Boolean = false,
    val showTokenUsage: Boolean = true,
    val showThinkingContent: Boolean = true,
    val autoCloseThinking: Boolean = true,
    val updateCheckDisabledUntilEpochMillis: Long = 0L,
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val enableMessageGenerationHapticEffect: Boolean = false,
    val skipCropImage: Boolean = true,
    val enableNotificationOnMessageGeneration: Boolean = false,
    val enableLiveUpdateNotification: Boolean = false,
    /** FLOAT2：后台生成时显示悬浮窗（气泡/面板），默认关；需要 SYSTEM_ALERT_WINDOW 权限 */
    val enableFloatingChatWindow: Boolean = false,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val ttsOnlyReadQuoted: Boolean = false,
    val ttsOnlyReadOutsideBrackets: Boolean = false,
    val autoPlayTTSAfterGeneration: Boolean = false,
    val pasteLongTextAsFile: Boolean = false,
    val pasteLongTextThreshold: Int = 1000,
    val sendOnEnter: Boolean = false,
    val enableAutoScroll: Boolean = true,
    val enableLatexRendering: Boolean = true,
    val enableBlurEffect: Boolean = false,
    val backgroundEffectType: BackgroundEffectType = BackgroundEffectType.BLUR,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val chatCustomFontPath: String = "",
    val chatCustomFontName: String = "",
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 1.0f,
    /**
     * FX1：液态玻璃 / 光效 / 交互动效总开关，**默认开**（主人拍板）。
     *
     * 关掉时：MochiTheme 收到 `MochiMaterials()`（栏/菜单/面板/弹窗退回纯色、scrollEdge 模糊关），
     * 且 `LocalKkEffectsEnabled = false`（Kk 层按压形变/触觉静默）。Mochi 真组件内部的
     * pressScale 属基础触感，不随此开关。
     *
     * 兼容性：本字段是**新增**的，老安装的 JSON 里没有这个 key ⇒ 反序列化取默认 true，
     * 不需要迁移（对比 [showAssistantBubble] 那种「老安装显式写了 false」的情况）。
     */
    val effectsEnabled: Boolean = true,
    /**
     * 图标按钮的样式：true = 液态玻璃（顶栏按钮、返回键、带底色的圆钮都画玻璃），false = 实心底色。
     * 只在 [effectsEnabled] 开着时有意义（总开关关了一律实心）。新增字段，老安装取默认 true。
     */
    val glassButtons: Boolean = true,
    /**
     * 底部导航栏标签样式（外观 → 导航栏 → 导航栏样式）：图标+文字 / 仅选中项显示文字 / 仅图标。
     * 新增字段，老安装的 JSON 里没有这个 key ⇒ 反序列化取默认 [NavBarLabelStyle.LABELS]（老行为）。
     */
    val navBarLabelStyle: NavBarLabelStyle = NavBarLabelStyle.LABELS,
    /** 内容往下滚时底部导航栏收起成只剩当前图标的小胶囊（Mochi 的 iOS 26 行为，默认开）。 */
    val navBarCollapseOnScroll: Boolean = true,
    /**
     * 栏玻璃通透度 0..1（0 最透明、0.5 iOS 默认、1 色调最重），对应 Mochi 的 `iosMaterials(glassTint = …)`。
     * 只作用于「透」档玻璃（栏、栏里的按钮、警告框、玻璃样式按钮）；菜单 / 面板是固定的「实」档，不跟它变。
     * 新增字段，老安装取默认 0.5（= iOS 默认）。
     */
    val glassTint: Float = 0.5f,
    /**
     * 仅模糊：保留背景模糊、面色和内容对比度，去掉折射 / 色散 / 边光 / 按压玻璃泡等光学效果，
     * 对应 Mochi 的 `iosMaterials(blurOnly = true)` / `MochiGlass.asBlurOnly()`。新增字段，默认关。
     */
    val glassBlurOnly: Boolean = false,
)

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "rikkahub_backups",
    val items: List<BackupItem> = listOf(
        BackupItem.DATABASE,
        BackupItem.FILES
    ),
) {
    @Serializable
    enum class BackupItem {
        DATABASE,
        FILES,
    }
}

@Serializable
data class BackupReminderConfig(
    val enabled: Boolean = false,
    val intervalDays: Int = 7,
    val lastBackupTime: Long = 0L,
)

fun Settings.isNotConfigured() = providers.all { it.models.isEmpty() }

fun Settings.findModelById(uuid: Uuid?, fallback: Uuid? = null): Model? {
    if (uuid == null && fallback == null) return null
    return uuid?.let { this.providers.findModelById(it) }
        ?: fallback?.let { this.providers.findModelById(it) }
}

fun List<ProviderSetting>.findModelById(uuid: Uuid): Model? {
    this.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == uuid) {
                return model
            }
        }
    }
    return null
}

fun Settings.findModelProviderName(uuid: Uuid?): String? {
    uuid ?: return null
    return providers.firstOrNull { provider ->
        provider.models.any { it.id == uuid }
    }?.name
}

fun Settings.getCurrentChatModel(): Model? {
    return findModelById(this.getCurrentAssistant().chatModelId ?: this.chatModelId)
}

fun Settings.getCurrentAssistant(): Assistant {
    // 正常情况下 assistants 永不为空（见 normalizeAssistants）；这里再兜一层，
    // 让该函数成为全函数，任何来源的空列表都不会再抛 NoSuchElementException。
    return this.assistants.find { it.id == assistantId }
        ?: this.assistants.firstOrNull()
        ?: DEFAULT_ASSISTANTS.first()
}

/**
 * 助手列表非空不变量：空列表回落到 [DEFAULT_ASSISTANTS]。
 * 读取路径与写入路径共用，保证 settingsFlow 与磁盘都不会持有空助手列表。
 */
internal fun normalizeAssistants(assistants: List<Assistant>): List<Assistant> =
    if (assistants.isEmpty()) DEFAULT_ASSISTANTS else assistants

/**
 * 删除助手；删除后若列表为空，则原子地回落到 [DEFAULT_ASSISTANTS]（而不是留下空列表的中间态），
 * 从而让 [SettingsRepository.update] 始终以非空助手列表写入。
 */
fun Settings.removeAssistant(assistant: Assistant): Settings {
    val remaining = normalizeAssistants(assistants.filterNot { it.id == assistant.id })
    return copy(
        assistants = remaining,
        assistantId = assistantId.takeIf { selected -> remaining.any { it.id == selected } }
            ?: remaining.first().id,
    )
}

fun Settings.getAssistantById(id: Uuid): Assistant? {
    return this.assistants.find { it.id == id }
}

fun Settings.getQuickMessagesOfAssistant(assistant: Assistant) =
    quickMessages.filter { it.id in assistant.quickMessageIds }

fun Settings.getSelectedTTSProvider(): TTSProviderSetting? {
    return selectedTTSProviderId?.let { id ->
        ttsProviders.find { it.id == id }
    } ?: ttsProviders.firstOrNull()
}

fun Settings.getSelectedASRProvider(): ASRProviderSetting? {
    return selectedASRProviderId?.let { id ->
        asrProviders.find { it.id == id }
    } ?: asrProviders.firstOrNull()
}

/**
 * 「最佳 ASR」选择：
 * 1. 用户显式选中的 ASR 服务商（有 key 时优先）；
 * 2. 没有则按识别质量/实时性优先级自动挑一个已配置的；
 * 3. 一个都没配就用已启用的 OpenAI 兼容聊天服务商凭据走 gpt-4o-transcribe；
 * 4. 都没有返回 null（输入框不显示语音按钮）。
 */
fun Settings.resolveBestASRProvider(): ASRProviderSetting? {
    fun configured(provider: ASRProviderSetting): Boolean = when (provider) {
        is ASRProviderSetting.OpenAIRealtime -> provider.apiKey.isNotBlank()
        is ASRProviderSetting.DashScope -> provider.apiKey.isNotBlank()
        is ASRProviderSetting.Volcengine -> provider.apiKey.isNotBlank()
        is ASRProviderSetting.MiMo -> provider.apiKey.isNotBlank()
        is ASRProviderSetting.Step -> provider.apiKey.isNotBlank()
        is ASRProviderSetting.OpenAITranscribe -> provider.apiKey.isNotBlank()
        is ASRProviderSetting.GeminiTranscribe -> provider.apiKey.isNotBlank()
        is ASRProviderSetting.SherpaLocal -> provider.modelId.isNotBlank()
    }

    selectedASRProviderId
        ?.let { id -> asrProviders.find { it.id == id } }
        ?.takeIf(::configured)
        ?.let { return it }

    val available = asrProviders.filter(::configured)
    val priority = listOf(
        ASRProviderSetting.OpenAIRealtime::class,
        ASRProviderSetting.DashScope::class,
        ASRProviderSetting.Volcengine::class,
        ASRProviderSetting.Step::class,
        ASRProviderSetting.MiMo::class,
        ASRProviderSetting.OpenAITranscribe::class,
        ASRProviderSetting.GeminiTranscribe::class,
        ASRProviderSetting.SherpaLocal::class,
    )
    priority.forEach { type ->
        available.firstOrNull { type.isInstance(it) }?.let { return it }
    }

    // 没配置 ASR：优先借 Google 服务商（Gemini 有免费额度），再借 OpenAI 兼容。
    val googleProvider = providers.firstOrNull {
        it is ProviderSetting.Google && it.enabled && it.apiKey.isNotBlank() && !it.vertexAI
    } as? ProviderSetting.Google
    googleProvider?.let {
        return ASRProviderSetting.GeminiTranscribe(
            name = "Gemini Transcribe",
            apiKey = it.apiKey,
            baseUrl = it.baseUrl,
            model = "gemini-2.5-flash",
        )
    }

    val openAIProvider = providers.firstOrNull {
        it is ProviderSetting.OpenAI && it.enabled && it.apiKey.isNotBlank()
    } as? ProviderSetting.OpenAI
    return openAIProvider?.let {
        ASRProviderSetting.OpenAITranscribe(
            name = "OpenAI Transcribe",
            apiKey = it.apiKey,
            baseUrl = it.baseUrl,
            model = "gpt-4o-transcribe",
        )
    }
}

fun Model.findProvider(providers: List<ProviderSetting>, checkOverwrite: Boolean = true): ProviderSetting? {
    val provider = findModelProviderFromList(providers) ?: return null
    val providerOverwrite = this.providerOverwrite
    if (checkOverwrite && providerOverwrite != null) {
        return providerOverwrite.copyProvider(models = emptyList())
    }
    return provider
}

private fun Model.findModelProviderFromList(providers: List<ProviderSetting>): ProviderSetting? {
    providers.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == this.id) {
                return setting
            }
        }
    }
    return null
}

internal val DEFAULT_ASSISTANT_ID = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
internal val DEFAULT_ASSISTANTS = listOf(
    RoleTemplates.kitty(id = DEFAULT_ASSISTANT_ID),
)

val DEFAULT_SYSTEM_TTS_ID = Uuid.parse("026a01a2-c3a0-4fd5-8075-80e03bdef200")
private val DEFAULT_TTS_PROVIDERS = listOf(
    TTSProviderSetting.SystemTTS(
        id = DEFAULT_SYSTEM_TTS_ID,
        name = "",
    ),
)

internal val DEFAULT_ASSISTANTS_IDS = DEFAULT_ASSISTANTS.map { it.id }

val DEFAULT_MODE_INJECTIONS = listOf(
    PromptInjection.ModeInjection(
        id = Uuid.parse("b87eaf16-f5cd-4ac1-9e4f-b11ae3a61d74"),
        content = LEARNING_MODE_PROMPT,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        name = "Learning Mode"
    )
)
