package heizige.kk.khatkit.app.core.di

import dagger.hilt.android.qualifiers.ApplicationContext
import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import heizige.kk.khatkit.app.feature.chat.helper.ChatManager
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.core.data.ai.tools.local.LocalTools
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.data.ai.hub.HubAccountRepository
import heizige.kk.khatkit.app.core.data.event.AppEventBus
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FolderRepository
import heizige.kk.khatkit.app.core.data.repository.MemoryExtractor
import heizige.kk.khatkit.app.core.data.repository.MemoryRepository
import heizige.kk.khatkit.app.core.data.repository.RoleMemoryRepository
import heizige.kk.khatkit.app.core.data.repository.WorkspaceRepository
import heizige.kk.khatkit.app.core.data.ai.mcp.McpManager
import heizige.kk.khatkit.app.core.data.files.SkillManager
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.ai.GenerationLoop
import heizige.kk.khatkit.app.core.data.ai.TranslationHandler
import heizige.kk.khatkit.app.core.data.ai.transformers.Base64ImageToLocalFileTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.OcrTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.PlaceholderTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.TemplateTransformer
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.app.feature.chat.helper.ChatNotificationManager
import heizige.kk.khatkit.app.feature.automation.helper.TriggerManager
import heizige.kk.khatkit.app.feature.workspace.helper.WorkspaceTerminalSessionManager
import heizige.kk.khatkit.workspace.WorkspaceManager
import heizige.kk.khatkit.app.core.util.AppAnalytics
import heizige.kk.khatkit.app.core.util.EmojiData
import heizige.kk.khatkit.app.core.util.NoOpAnalytics
import heizige.kk.khatkit.app.core.util.EmojiHelper
import heizige.kk.khatkit.app.core.util.JsonInstant
import heizige.kk.khatkit.app.core.util.SoundEffectPlayer
import heizige.kk.khatkit.app.core.util.UpdateChecker
import heizige.kk.khatkit.app.core.data.remote.ModelsDevUpdater
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.app.core.network.KhatKitMcpToolHost
import heizige.kk.khatkit.app.core.network.McpToolHost
import heizige.kk.khatkit.app.core.network.WebServerManager
import heizige.kk.khatkit.tts.provider.TTSManager

@Module
@InstallIn(SingletonComponent::class)
object AppHiltModule {

    @Provides
    @Singleton
    fun provideMcpToolHost(provider: KhatKitToolProvider): McpToolHost = KhatKitMcpToolHost(provider)

    @Provides
    @Singleton
    fun provideWebServerManager(
        @ApplicationContext context: Context,
        appScope: AppScope,
        chatService: ChatManager,
        conversationRepo: ConversationRepository,
        folderRepo: FolderRepository,
        settingsStore: SettingsRepository,
        filesManager: FilesManager,
        mcpToolHost: McpToolHost,
    ): WebServerManager = WebServerManager(
        context = context,
        appScope = appScope,
        chatService = chatService,
        conversationRepo = conversationRepo,
        folderRepo = folderRepo,
        settingsStore = settingsStore,
        filesManager = filesManager,
        mcpToolHost = mcpToolHost,
    )
}
