package heizige.kk.khatkit.app.core.data.ai.mcp

import android.content.Context
import androidx.core.net.toUri
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.oauth.CustomTabsOAuthAuthorizationLauncher
import heizige.kk.khatkit.oauth.OAuthHttpClient
import heizige.kk.khatkit.oauth.OAuthLoopbackCallbackServer
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.datastore.getCurrentAssistant
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.files.saveUploadFromBytes
import heizige.kk.khatkit.app.core.util.JsonInstant
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/**
 * MCP 子系统的公共入口。
 *
 * 这里仅协调配置、OAuth、连接注册表与 UI 内容转换；单个服务器的连接状态机由
 * [McpSessionRegistry] 管理，OAuth 协议细节由 [McpOAuthCoordinator] 管理。
 */
class McpManager(
    private val settingsStore: SettingsRepository,
    private val appScope: AppScope,
    private val filesManager: FilesManager,
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followSslRedirects(true)
        .followRedirects(true)
        .build()

    private val httpClient = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        install(SSE)
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 10 * 60_000
        }
    }

    private val statusStore = McpStatusStore()
    private val oauthCallbackServer = OAuthLoopbackCallbackServer(
        port = MCP_OAUTH_CALLBACK_PORT,
        callbackPath = MCP_OAUTH_CALLBACK_PATH,
    )
    private val oauthCoordinator = McpOAuthCoordinator(
        settingsStore = settingsStore,
        appScope = appScope,
        oauthClient = OAuthHttpClient(okHttpClient),
        discoveryClient = McpOAuthDiscoveryClient(okHttpClient),
        callbackServer = oauthCallbackServer,
        authorizationLauncher = CustomTabsOAuthAuthorizationLauncher,
        updateStatus = statusStore::update,
    )
    private val sessionRegistry = McpSessionRegistry(
        settingsStore = settingsStore,
        appScope = appScope,
        httpClient = httpClient,
        oauthCoordinator = oauthCoordinator,
        statusStore = statusStore,
    )

    init {
        appScope.launch {
            settingsStore.settingsFlow
                .map { settings -> settings.mcpServers }
                .distinctUntilChanged()
                .collect(sessionRegistry::reconcile)
        }
    }

    val syncingStatus: StateFlow<Map<Uuid, McpStatus>>
        get() = statusStore.status

    fun getClient(config: McpServerConfig): Client? = sessionRegistry.getClient(config.id)

    fun getStatus(config: McpServerConfig): Flow<McpStatus> = sessionRegistry.getStatus(config.id)

    fun getAllAvailableTools(): List<Triple<Uuid, String, McpTool>> {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        return settings.mcpServers
            .filter { it.commonOptions.enable && it.id in assistant.mcpServers }
            .flatMap { server ->
                server.commonOptions.tools
                    .filter { tool -> tool.enable }
                    .map { tool -> Triple(server.id, server.commonOptions.name, tool) }
            }
    }

    suspend fun callTool(serverId: Uuid, toolName: String, args: JsonObject): List<UIMessagePart> {
        val result = try {
            sessionRegistry.callTool(serverId, toolName, args)
        } catch (e: CancellationException) {
            throw e
        } catch (e: McpClientUnavailableException) {
            return listOf(UIMessagePart.Text("Failed to execute MCP tool: ${e.message ?: e.javaClass.name}"))
        }
        return result.content.map { content ->
            when (content) {
                is TextContent -> UIMessagePart.Text(content.text)
                is ImageContent -> convertImageContentToFilePart(content)
                else -> UIMessagePart.Text(JsonInstant.encodeToString(content))
            }
        }
    }

    suspend fun addClient(config: McpServerConfig) = sessionRegistry.addClient(config)

    suspend fun removeClient(config: McpServerConfig) = sessionRegistry.removeClient(config)

    suspend fun syncAll() = sessionRegistry.syncAll()

    fun startAuthorization(config: McpServerConfig, context: Context) {
        oauthCoordinator.startAuthorization(config, context)
    }

    fun cancelAuthorization(config: McpServerConfig) {
        oauthCoordinator.cancelAuthorization(config.id)
    }

    suspend fun clearAuthorization(config: McpServerConfig) {
        val freshConfig = oauthCoordinator.clearAuthorization(config)
        sessionRegistry.addClient(freshConfig)
    }

    private suspend fun convertImageContentToFilePart(image: ImageContent): UIMessagePart.Image {
        val bytes = Base64.decode(image.data)
        val extension = android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(image.mimeType) ?: "bin"
        val entity = filesManager.saveUploadFromBytes(
            bytes = bytes,
            displayName = "mcp_image.$extension",
            mimeType = image.mimeType,
        )
        return UIMessagePart.Image(url = filesManager.getFile(entity).toUri().toString())
    }
}
