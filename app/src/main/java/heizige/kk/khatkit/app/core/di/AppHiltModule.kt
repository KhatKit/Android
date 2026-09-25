package heizige.kk.khatkit.app.core.di


import dagger.hilt.android.qualifiers.ApplicationContext
import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.core.data.ai.tools.local.LocalTools
import heizige.kk.khatkit.app.core.data.ai.tools.ChatToolFactory
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.data.event.AppEventBus
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FolderRepository
import heizige.kk.khatkit.app.core.data.repository.MemoryRepository
import heizige.kk.khatkit.app.core.data.repository.WorkspaceRepository
import heizige.kk.khatkit.app.core.data.ai.mcp.McpManager
import heizige.kk.khatkit.app.core.data.files.SkillManager
import heizige.kk.khatkit.app.core.data.datastore.SettingsStore
import heizige.kk.khatkit.app.core.data.ai.GenerationLoop
import heizige.kk.khatkit.app.core.data.ai.TranslationHandler
import heizige.kk.khatkit.app.core.data.ai.transformers.Base64ImageToLocalFileTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.OcrTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.PlaceholderTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.TemplateTransformer
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.app.feature.chat.ChatNotificationManager
import heizige.kk.khatkit.app.feature.chat.ChatService
import heizige.kk.khatkit.app.feature.automation.TriggerController
import heizige.kk.khatkit.app.feature.workspace.WorkspaceTerminalSessionManager
import heizige.kk.khatkit.app.core.util.AppAnalytics
import heizige.kk.khatkit.app.core.util.EmojiData
import heizige.kk.khatkit.app.core.util.NoOpAnalytics
import heizige.kk.khatkit.app.core.util.EmojiUtils
import heizige.kk.khatkit.app.core.util.JsonInstant
import heizige.kk.khatkit.app.core.util.SoundEffectPlayer
import heizige.kk.khatkit.app.core.util.UpdateChecker
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
    fun provideJson(): Json = JsonInstant

    @Provides
    @Singleton
    fun provideAppEventBus(): AppEventBus = AppEventBus()

    @Provides
    @Singleton
    fun provideLocalTools(
        @ApplicationContext context: Context,
        eventBus: AppEventBus,
        ttsManager: TTSManager,
        settingsStore: SettingsStore,
    ): LocalTools = LocalTools(context, eventBus, ttsManager, settingsStore)

    @Provides
    @Singleton
    fun provideUpdateChecker(
        client: OkHttpClient,
        appScope: AppScope,
        controller: KhatKitToolProvider,
    ): UpdateChecker = UpdateChecker(
        client = client,
        appScope = appScope,
        controller = controller,
    )

    @Provides
    @Singleton
    fun provideEmojiData(@ApplicationContext context: Context): EmojiData = EmojiUtils.loadEmoji(context)

    @Provides
    @Singleton
    fun provideTTSManager(@ApplicationContext context: Context): TTSManager = TTSManager(context)

    @Provides
    @Singleton
    fun provideAppAnalytics(): AppAnalytics = NoOpAnalytics

    @Provides
    @Singleton
    fun provideSoundEffectPlayer(@ApplicationContext context: Context): SoundEffectPlayer = SoundEffectPlayer(context)

    @Provides
    @Singleton
    fun provideWorkspaceTerminalSessionManager(
        @ApplicationContext context: Context,
        appScope: AppScope,
    ): WorkspaceTerminalSessionManager = WorkspaceTerminalSessionManager(context, appScope)

    // 生成通知与业务解耦：ChatService 只发事件，通知由这里消费
    @Provides
    @Singleton
    fun provideChatNotificationManager(
        application: Application,
        appScope: AppScope,
        eventBus: AppEventBus,
        settingsStore: SettingsStore,
    ): ChatNotificationManager = ChatNotificationManager(
        context = application,
        appScope = appScope,
        eventBus = eventBus,
        settingsStore = settingsStore,
    )

    @Provides
    @Singleton
    fun provideKhatKitToolProvider(
        @ApplicationContext context: Context,
        appScope: AppScope,
        json: Json,
    ): KhatKitToolProvider = KhatKitToolProvider(
        context = context,
        scope = appScope,
        json = json,
    )

    // 事件触发器：卡片在定时/通知/应用启动/充电时自动运行
    @Provides
    @Singleton
    fun provideTriggerController(
        @ApplicationContext context: Context,
        provider: KhatKitToolProvider,
        appScope: AppScope,
    ): TriggerController = TriggerController(
        context = context,
        provider = provider,
        scope = appScope,
    )

    @Provides
    @Singleton
    fun provideChatToolFactory(
        json: Json,
        memoryRepository: MemoryRepository,
        conversationRepository: ConversationRepository,
        localTools: LocalTools,
        mcpManager: McpManager,
        skillManager: SkillManager,
        workspaceRepository: WorkspaceRepository,
        filesManager: FilesManager,
        khatKitToolProvider: KhatKitToolProvider,
    ): ChatToolFactory = ChatToolFactory(
        json = json,
        memoryRepository = memoryRepository,
        conversationRepository = conversationRepository,
        localTools = localTools,
        mcpManager = mcpManager,
        skillManager = skillManager,
        workspaceRepository = workspaceRepository,
        filesManager = filesManager,
        khatKitToolProvider = khatKitToolProvider,
    )

    @Provides
    @Singleton
    fun provideChatService(
        context: Application,
        appScope: AppScope,
        appEventBus: AppEventBus,
        settingsStore: SettingsStore,
        conversationRepo: ConversationRepository,
        memoryRepository: MemoryRepository,
        generationLoop: GenerationLoop,
        translationHandler: TranslationHandler,
        templateTransformer: TemplateTransformer,
        providerManager: ProviderManager,
        chatToolFactory: ChatToolFactory,
        mcpManager: McpManager,
        filesManager: FilesManager,
        workspaceRepository: WorkspaceRepository,
        folderRepository: FolderRepository,
        placeholderTransformer: PlaceholderTransformer,
        ocrTransformer: OcrTransformer,
        base64ImageToLocalFileTransformer: Base64ImageToLocalFileTransformer,
    ): ChatService = ChatService(
        context = context,
        appScope = appScope,
        appEventBus = appEventBus,
        settingsStore = settingsStore,
        conversationRepo = conversationRepo,
        memoryRepository = memoryRepository,
        generationLoop = generationLoop,
        translationHandler = translationHandler,
        templateTransformer = templateTransformer,
        providerManager = providerManager,
        chatToolFactory = chatToolFactory,
        mcpManager = mcpManager,
        filesManager = filesManager,
        workspaceRepository = workspaceRepository,
        folderRepository = folderRepository,
        placeholderTransformer = placeholderTransformer,
        ocrTransformer = ocrTransformer,
        base64ImageToLocalFileTransformer = base64ImageToLocalFileTransformer,
    )

    @Provides
    @Singleton
    fun provideMcpToolHost(provider: KhatKitToolProvider): McpToolHost = KhatKitMcpToolHost(provider)

    @Provides
    @Singleton
    fun provideWebServerManager(
        @ApplicationContext context: Context,
        appScope: AppScope,
        chatService: ChatService,
        conversationRepo: ConversationRepository,
        folderRepo: FolderRepository,
        settingsStore: SettingsStore,
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
