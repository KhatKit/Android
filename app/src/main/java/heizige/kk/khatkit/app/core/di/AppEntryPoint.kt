package heizige.kk.khatkit.app.core.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.app.core.data.ai.mcp.McpManager
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.data.ai.transformers.TemplateTransformer
import heizige.kk.khatkit.app.core.data.api.SponsorAPI
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.db.dao.WorkspaceDAO
import heizige.kk.khatkit.app.core.data.event.AppEventBus
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.files.SkillManager
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.MemoryRepository
import heizige.kk.khatkit.app.core.data.repository.WorkspaceRepository
import heizige.kk.khatkit.app.feature.automation.TriggerController
import heizige.kk.khatkit.app.feature.workspace.WorkspaceTerminalSessionManager
import heizige.kk.khatkit.app.core.util.EmojiData
import heizige.kk.khatkit.app.core.util.SoundEffectPlayer
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.tts.provider.TTSManager
import heizige.kk.khatkit.app.core.network.WebServerManager
import heizige.kk.khatkit.workspace.WorkspaceManager

/**
 * 供无法使用 @AndroidEntryPoint 的组件（ContentProvider）与 Compose 叶子节点读取应用级单例。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun settingsStore(): SettingsRepository
    fun filesManager(): FilesManager
    fun mcpManager(): McpManager
    fun providerManager(): ProviderManager
    fun soundEffectPlayer(): SoundEffectPlayer
    fun memoryRepository(): MemoryRepository
    fun conversationRepository(): ConversationRepository
    fun workspaceRepository(): WorkspaceRepository
    fun workspaceManager(): WorkspaceManager
    fun workspaceTerminalSessionManager(): WorkspaceTerminalSessionManager
    fun workspaceDao(): WorkspaceDAO
    fun appEventBus(): AppEventBus
    fun emojiData(): EmojiData
    fun skillManager(): SkillManager
    fun khatKitToolProvider(): KhatKitToolProvider
    fun ttsManager(): TTSManager
    fun okHttpClient(): OkHttpClient
    fun templateTransformer(): TemplateTransformer
    fun sponsorApi(): SponsorAPI
    fun webServerManager(): WebServerManager
    fun triggerController(): TriggerController
}

fun appEntryPoint(context: Context): AppEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java)

@Composable
fun rememberAppEntryPoint(): AppEntryPoint {
    val context = LocalContext.current
    return remember(context) { appEntryPoint(context) }
}
