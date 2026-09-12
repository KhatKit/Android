package heizige.kk.khatkit.app.di

import heizige.kk.khatkit.app.ui.pages.assistant.AssistantVM
import heizige.kk.khatkit.app.ui.pages.assistant.detail.AssistantDetailVM
import heizige.kk.khatkit.app.ui.pages.backup.BackupVM
import heizige.kk.khatkit.app.ui.pages.chat.ChatDrawerVM
import heizige.kk.khatkit.app.ui.pages.chat.ChatVM
import heizige.kk.khatkit.app.ui.pages.debug.DebugVM
import heizige.kk.khatkit.app.ui.pages.favorite.FavoriteVM
import heizige.kk.khatkit.app.ui.pages.search.SearchVM
import heizige.kk.khatkit.app.ui.pages.history.HistoryVM
import heizige.kk.khatkit.app.ui.pages.stats.StatsVM
import heizige.kk.khatkit.app.ui.pages.imggen.ImgGenVM
import heizige.kk.khatkit.app.ui.pages.extensions.PromptVM
import heizige.kk.khatkit.app.ui.pages.extensions.QuickMessagesVM
import heizige.kk.khatkit.app.ui.pages.extensions.skills.SkillDetailVM
import heizige.kk.khatkit.app.ui.pages.extensions.skills.SkillsVM
import heizige.kk.khatkit.app.ui.pages.extensions.workspace.WorkspaceDetailVM
import heizige.kk.khatkit.app.ui.pages.extensions.workspace.WorkspaceVM
import heizige.kk.khatkit.app.ui.pages.setting.SettingVM
import heizige.kk.khatkit.app.ui.pages.share.handler.ShareHandlerVM
import heizige.kk.khatkit.app.ui.pages.translator.TranslatorVM
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<ChatVM> { params ->
        ChatVM(
            id = params.get(),
            context = get(),
            settingsStore = get(),
            conversationRepo = get(),
            chatService = get(),
            updateChecker = get(),
            analytics = get(),
            filesManager = get(),
            favoriteRepository = get(),
        )
    }
    viewModelOf(::ChatDrawerVM)
    viewModelOf(::SettingVM)
    viewModelOf(::DebugVM)
    viewModelOf(::HistoryVM)
    viewModelOf(::AssistantVM)
    viewModel<AssistantDetailVM> {
        AssistantDetailVM(
            id = it.get(),
            settingsStore = get(),
            memoryRepository = get(),
            filesManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
        )
    }
    viewModelOf(::TranslatorVM)
    viewModel<ShareHandlerVM> {
        ShareHandlerVM(
            text = it.get(),
            settingsStore = get(),
        )
    }
    viewModelOf(::BackupVM)
    viewModelOf(::ImgGenVM)
    viewModelOf(::PromptVM)
    viewModelOf(::QuickMessagesVM)
    viewModelOf(::SkillsVM)
    viewModelOf(::SkillDetailVM)
    viewModelOf(::WorkspaceVM)
    viewModel<WorkspaceDetailVM> {
        WorkspaceDetailVM(
            id = it.get(),
            repository = get(),
            terminalSessionManager = get(),
        )
    }
    viewModelOf(::FavoriteVM)
    viewModelOf(::SearchVM)
    viewModelOf(::StatsVM)
}
