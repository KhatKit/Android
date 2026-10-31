package heizige.kk.khatkit.ai.provider.providers.chatgpt

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.AccountCredentialProvider
import heizige.kk.khatkit.ai.provider.CustomHeader
import heizige.kk.khatkit.ai.provider.Modality
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.provider.Provider
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.provider.TextGenerationResult
import heizige.kk.khatkit.ai.provider.providers.configureAccountAuth
import heizige.kk.khatkit.ai.provider.providers.openai.ResponseAPI
import heizige.kk.khatkit.ai.provider.providers.openai.ResponseProviderCapabilities
import heizige.kk.khatkit.ai.provider.providers.openai.ResponsesEndpoint
import heizige.kk.khatkit.ai.provider.providers.withAccountAuthenticator
import heizige.kk.khatkit.ai.ui.StreamChunk
import heizige.kk.khatkit.ai.ui.StreamChunkHandler
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.util.fetchBody
import heizige.kk.khatkit.ai.util.json
import heizige.kk.khatkit.common.http.jsonPrimitiveOrNull
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.common.http.okhttp.Request
import heizige.kk.khatkit.common.http.stringOrNull

// 参照 openai/codex（codex-rs/model-provider-info、codex-rs/login）
internal const val CODEX_BASE_URL = "https://chatgpt.com/backend-api/codex"
private const val CODEX_ORIGINATOR = "codex_cli_rs"

// 模型列表按客户端版本过滤，太旧会拿不到新模型
private const val CODEX_CLIENT_VERSION = "0.163.0"

/**
 * Codex 后端对请求体的要求，Codex CLI 也是这样发的：
 *
 * - system 提示以 developer 消息放在 input 开头，不使用 instructions 字段
 * - 不接受 max_output_tokens、temperature、top_p
 * - store 必须为 false，多轮推理要靠加密的推理内容接上
 * - sessionId 作为 prompt_cache_key 发出，保证同一会话命中缓存
 */
internal val CODEX_CAPABILITIES = ResponseProviderCapabilities(
    systemMessageRole = "developer",
    supportsSamplingParams = false,
    alwaysIncludeEncryptedContent = true,
    supportsPromptCacheKey = true,
    supportsMaxOutputTokens = false,
)

/**
 * ChatGPT 账号登录后走 Codex 后端。协议是 Responses API，但和 api.openai.com 有几处不同：
 *
 * - 只支持流式；[generateText] 用流式结果拼出完整消息
 * - 请求体的差异见 [CODEX_CAPABILITIES]
 * - 鉴权除了 Bearer token，还要带 ChatGPT-Account-ID 和 originator
 *
 * 额度查询（上游 getQuota / Codex /wham/usage）暂未移植：本 fork 的 Provider 只有
 * getBalance(): String，待额度任务接入。
 */
class ChatGPTProvider(
    client: OkHttpClient,
    private val credentials: AccountCredentialProvider,
) : Provider<ProviderSetting.ChatGPT> {
    private val client = client.withAccountAuthenticator(credentials)
    private val responseAPI = ResponseAPI(this.client)

    override suspend fun listModels(providerSetting: ProviderSetting.ChatGPT): List<Model> {
        val request = Request.Builder()
            .url(CODEX_BASE_URL + "/models?client_version=" + CODEX_CLIENT_VERSION)
            .apply { configureAuth(providerSetting) }
            .get()
            .build()
        return parseCodexModels(client.fetchBody(request))
    }

    /** TODO(额度任务)：上游查 Codex /wham/usage 返回订阅窗口用量，本 fork 暂无 ProviderQuota */
    override suspend fun getBalance(providerSetting: ProviderSetting.ChatGPT): String = ""

    override suspend fun streamText(
        providerSetting: ProviderSetting.ChatGPT,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<StreamChunk> = responseAPI.streamText(
        endpoint = ResponsesEndpoint(
            url = CODEX_BASE_URL + "/responses",
            capabilities = CODEX_CAPABILITIES,
            configureRequest = {
                configureAuth(providerSetting, params.customHeaders)
                params.sessionId?.let { header("session-id", it) }
            },
        ),
        messages = messages,
        params = params,
    )

    override suspend fun generateText(
        providerSetting: ProviderSetting.ChatGPT,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): TextGenerationResult {
        val handler = StreamChunkHandler(params.model)
        var result = listOf(UIMessage(role = MessageRole.ASSISTANT, parts = emptyList(), modelId = params.model.id))
        var finish: StreamChunk.Finish? = null
        streamText(providerSetting, messages, params).collect { chunk ->
            if (chunk is StreamChunk.Finish) finish = chunk
            result = handler.handle(result, chunk)
        }
        val message = result.last()
        return TextGenerationResult(
            id = finish?.responseId.orEmpty(),
            model = finish?.model ?: params.model.modelId,
            message = message,
            finishReason = finish?.finishReason,
            usage = message.usage,
        )
    }

    private suspend fun Request.Builder.configureAuth(
        setting: ProviderSetting.ChatGPT,
        customHeaders: List<CustomHeader> = emptyList(),
    ) = configureAccountAuth(credentials, setting, customHeaders) { credential ->
        credential.accountId?.let { header("ChatGPT-Account-ID", it) }
        header("originator", CODEX_ORIGINATOR)
    }
}

/** 解析 GET /models 的结果，只保留 Codex 选择器里可见的模型 */
internal fun parseCodexModels(body: String): List<Model> {
    val models = json.parseToJsonElement(body).jsonObject["models"]?.jsonArray.orEmpty()
        .map { it.jsonObject }
    val visible = models.filter { it.stringOrNull("visibility") == "list" }.ifEmpty { models }
    return visible.mapNotNull { model ->
        val slug = model.stringOrNull("slug") ?: return@mapNotNull null
        val inputModalities = model["input_modalities"]?.jsonArray
            ?.mapNotNull { modality ->
                when (modality.jsonPrimitiveOrNull?.contentOrNull) {
                    "text" -> Modality.TEXT
                    "image" -> Modality.IMAGE
                    else -> null
                }
            }
            ?.ifEmpty { null }
            ?: listOf(Modality.TEXT, Modality.IMAGE)
        val reasoning = model["supported_reasoning_levels"]?.jsonArray?.isNotEmpty() == true
        Model(
            modelId = slug,
            displayName = model.stringOrNull("display_name") ?: slug,
            inputModalities = inputModalities,
            abilities = if (reasoning) listOf(ModelAbility.TOOL, ModelAbility.REASONING) else listOf(ModelAbility.TOOL),
        )
    }
}
