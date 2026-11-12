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
import heizige.kk.khatkit.app.core.data.ai.hub.HubAccountRepository
import heizige.kk.khatkit.app.core.data.ai.mcp.McpManager
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.data.ai.transformers.TemplateTransformer
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.db.dao.WorkspaceDAO
import heizige.kk.khatkit.app.core.data.event.AppEventBus
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.files.SkillManager
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.MemoryRepository
import heizige.kk.khatkit.app.core.data.repository.WorkspaceRepository
import heizige.kk.khatkit.app.feature.automation.TriggerController
import heizige.kk.khatkit.app.feature.workflow.WorkflowRepository
import heizige.kk.khatkit.app.feature.workspace.data.WorkspaceTerminalSessionManager
import heizige.kk.khatkit.app.core.util.EmojiData
import heizige.kk.khatkit.app.core.util.SoundEffectPlayer
import heizige.kk.khatkit.app.core.util.UpdateChecker
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.tts.provider.TTSManager
import heizige.kk.khatkit.app.core.network.WebServerManager
import heizige.kk.khatkit.workspace.WorkspaceManager

/**
 * OCR2b：把 app 的动态插件加载器注入 common 的 [heizige.kk.khatkit.ocr.api.OcrEngineProvider]。
 * 必须在主流程使用 OCR 之前调用（RouteActivity.onCreate 里做）；未安装插件时返回 null，
 * Provider 自动回退到 classpath 反射与「需要安装插件」降级，不抛异常。
 */
fun installSshPluginResolver(context: Context) {
    heizige.kk.khatkit.ssh.api.SshEngineProvider.installResolver(context) { ctx ->
        heizige.kk.khatkit.app.core.data.ssh.SshPluginLoader.engineOrNull(ctx)
    }
}

fun installOcrPluginResolver(context: Context) {
    heizige.kk.khatkit.ocr.api.OcrEngineProvider.installResolver(context) { ctx ->
        heizige.kk.khatkit.app.core.data.ocr.OcrPluginLoader.engineOrNull(ctx)
    }
}

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
    fun updateChecker(): UpdateChecker
    fun hubAccountRepository(): HubAccountRepository
    fun ttsManager(): TTSManager
    fun okHttpClient(): OkHttpClient
    fun templateTransformer(): TemplateTransformer
    fun webServerManager(): WebServerManager
    fun triggerController(): TriggerController
    fun workflowRepository(): WorkflowRepository
}

fun appEntryPoint(context: Context): AppEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java)

@Composable
fun rememberAppEntryPoint(): AppEntryPoint {
    val context = LocalContext.current
    return remember(context) { appEntryPoint(context) }
}
