package heizige.kk.khatkit.app.di

import kotlinx.serialization.json.Json
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.data.ai.tools.local.LocalTools
import heizige.kk.khatkit.app.data.ai.tools.ChatToolFactory
import heizige.kk.khatkit.app.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.data.event.AppEventBus
import heizige.kk.khatkit.app.service.ChatNotificationManager
import heizige.kk.khatkit.app.service.ChatService
import heizige.kk.khatkit.app.ui.pages.extensions.workspace.WorkspaceTerminalSessionManager
import heizige.kk.khatkit.app.utils.AppAnalytics
import heizige.kk.khatkit.app.utils.EmojiData
import heizige.kk.khatkit.app.utils.NoOpAnalytics
import heizige.kk.khatkit.app.utils.EmojiUtils
import heizige.kk.khatkit.app.utils.JsonInstant
import heizige.kk.khatkit.app.utils.SoundEffectPlayer
import heizige.kk.khatkit.app.utils.UpdateChecker
import heizige.kk.khatkit.web.KhatKitMcpToolHost
import heizige.kk.khatkit.web.McpToolHost
import heizige.kk.khatkit.web.WebServerManager
import heizige.kk.khatkit.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single {
        AppEventBus()
    }

    single {
        LocalTools(get(), get(), get(), get())
    }

    single {
        UpdateChecker(
            client = get(),
            appScope = get(),
        )
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single<AppAnalytics> {
        NoOpAnalytics
    }

    single {
        SoundEffectPlayer(get())
    }

    single {
        WorkspaceTerminalSessionManager(get(), get())
    }

    // 生成通知与业务解耦：ChatService 只发事件，通知由这里消费；
    // createdAtStart 保证进程启动即订阅，否则后台生成的事件会因无订阅者而丢失
    single(createdAtStart = true) {
        ChatNotificationManager(
            context = get(),
            appScope = get(),
            eventBus = get(),
            settingsStore = get(),
        )
    }

    single {
        KhatKitToolProvider(
            context = get(),
            scope = get<AppScope>(),
            json = get(),
        )
    }

    single {
        ChatToolFactory(
            json = get(),
            memoryRepository = get(),
            conversationRepository = get(),
            localTools = get(),
            mcpManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
            khatKitToolProvider = get(),
        )
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationLoop = get(),
            translationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            chatToolFactory = get(),
            mcpManager = get(),
            filesManager = get(),
            workspaceRepository = get(),
            folderRepository = get()
        )
    }

    single<McpToolHost> {
        KhatKitMcpToolHost(get())
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get(),
            mcpToolHost = get()
        )
    }
}
