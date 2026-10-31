package heizige.kk.khatkit.ai.provider.providers.grok

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.core.ReasoningLevel
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

// 参照 can1357/oh-my-pi 的 xai-oauth 实现
private const val XAI_BASE_URL = "https://api.x.ai/v1"

// Grok CLI 的代理，模型目录在这里
private const val GROK_CLI_BASE_URL = "https://cli-chat-proxy.grok.com/v1"

// 图片、语音等不是对话模型
private val NON_CHAT_MODEL = Regex("imagine|tts|embed|video", RegexOption.IGNORE_CASE)

/**
 * xAI 对请求体的要求：system 提示以 system 消息放在 input 开头；不接受 reasoning.summary；
 * reasoning.effort 的取值见 [grokEffort]。所有模型都请求加密推理内容以接上多轮推理。
 */
internal val GROK_CAPABILITIES = ResponseProviderCapabilities(
    supportsReasoningSummary = false,
    systemMessageRole = "system",
    alwaysIncludeEncryptedContent = true,
    reasoningEffort = ::grokEffort,
)

// grok-4.20 的推理/非推理模型带任何 reasoning.effort（包括 none）都会报参数错误
private val MODELS_WITHOUT_REASONING_EFFORT = Regex("""^grok-4\.20(-\d{4})?-(non-)?reasoning$""")

/** 与 Vercel AI SDK 的 xAI provider 一致：xhigh 只有 grok-4.6 支持，其他模型落到 high */
private fun grokEffort(level: ReasoningLevel, modelId: String): String? {
    if (MODELS_WITHOUT_REASONING_EFFORT.matches(modelId)) return null
    return when (level) {
        ReasoningLevel.AUTO -> null
        ReasoningLevel.OFF -> "none"
        ReasoningLevel.LOW -> "low"
        ReasoningLevel.MEDIUM -> "medium"
        ReasoningLevel.HIGH -> "high"
        ReasoningLevel.XHIGH, ReasoningLevel.MAX -> if (modelId == "grok-4.6") "xhigh" else "high"
    }
}

/**
 * SuperGrok 订阅（xAI 账号登录）。对话走 api.x.ai 的 Responses API，用 OAuth access token 作 Bearer：
 *
 * - 请求体的差异见 [GROK_CAPABILITIES]
 * - 模型目录合并 Grok CLI 的 models-v2 与 api.x.ai 的 /models（CLI 优先）
 * - 额度查询（Grok CLI billing）未移植：本 fork 的 Provider 只有 getBalance(): String
 */
class GrokProvider(
    client: OkHttpClient,
    private val credentials: AccountCredentialProvider,
) : Provider<ProviderSetting.Grok> {
    private val client = client.withAccountAuthenticator(credentials)
    private val responseAPI = ResponseAPI(this.client)

    override suspend fun listModels(providerSetting: ProviderSetting.Grok): List<Model> = coroutineScope {
        // 两个目录拿到一个就够用
        val cliRequest = async {
            runCatching { getJson(providerSetting, GROK_CLI_BASE_URL + "/models-v2", grokCli = true) }
        }
        val apiRequest = async { runCatching { getJson(providerSetting, XAI_BASE_URL + "/models") } }
        val cli = cliRequest.await()
        val api = apiRequest.await()
        if (cli.isFailure && api.isFailure) throw cli.exceptionOrNull()!!
        parseGrokModels(cliCatalog = cli.getOrNull(), apiCatalog = api.getOrNull())
    }

    /** TODO(额度任务)：上游查 Grok CLI billing 的周/月额度，本 fork 暂无 ProviderQuota */
    override suspend fun getBalance(providerSetting: ProviderSetting.Grok): String = ""

    override suspend fun streamText(
        providerSetting: ProviderSetting.Grok,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<StreamChunk> = responseAPI.streamText(
        endpoint = ResponsesEndpoint(
            url = XAI_BASE_URL + "/responses",
            capabilities = GROK_CAPABILITIES,
            configureRequest = { configureAuth(providerSetting, params.customHeaders) },
        ),
        messages = messages,
        params = params,
    )

    override suspend fun generateText(
        providerSetting: ProviderSetting.Grok,
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

    private suspend fun getJson(
        setting: ProviderSetting.Grok,
        url: String,
        grokCli: Boolean = false,
    ): JsonObject {
        val request = Request.Builder()
            .url(url)
            .apply { configureAuth(setting, grokCli = grokCli) }
            .get()
            .build()
        return json.parseToJsonElement(client.fetchBody(request)).jsonObject
    }

    private suspend fun Request.Builder.configureAuth(
        setting: ProviderSetting.Grok,
        customHeaders: List<CustomHeader> = emptyList(),
        grokCli: Boolean = false,
    ) = configureAccountAuth(credentials, setting, customHeaders) {
        // Grok CLI 的代理要求带上，和官方 CLI 走同一个产品通道
        if (grokCli) header("X-XAI-Token-Auth", "xai-grok-cli")
    }
}

/**
 * 合并两个模型目录，两边都有的以 Grok CLI 的为准。
 *
 * 目录里声明了推理档位的模型才算推理模型，只有它们会带上 reasoning.effort。
 */
internal fun parseGrokModels(cliCatalog: JsonObject?, apiCatalog: JsonObject?): List<Model> {
    val models = linkedMapOf<String, Model>()
    listOfNotNull(cliCatalog, apiCatalog).forEach { catalog ->
        (catalog["data"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().forEach { row ->
            val meta = row["_meta"] as? JsonObject
            // Grok CLI 目录里的 id 可能和推理用的模型 id 不同
            val id = row.string("model") ?: row.string("modelId") ?: row.string("id")
                ?: meta?.string("model") ?: meta?.string("modelId") ?: return@forEach
            val hidden = (row["hidden"] ?: meta?.get("hidden"))?.jsonPrimitiveOrNull?.booleanOrNull == true
            if (hidden || NON_CHAT_MODEL.containsMatchIn(id) || id in models) return@forEach
            val efforts = (row["reasoningEfforts"] ?: row["reasoning_efforts"] ?: meta?.get("reasoningEfforts"))
                as? JsonArray
            models[id] = Model(
                modelId = id,
                displayName = row.string("name") ?: row.string("displayName") ?: row.string("display_name")
                    ?: meta?.string("displayName") ?: id,
                inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
                abilities = if (efforts.isNullOrEmpty()) {
                    listOf(ModelAbility.TOOL)
                } else {
                    listOf(ModelAbility.TOOL, ModelAbility.REASONING)
                },
            )
        }
    }
    return models.values.toList()
}

private fun JsonObject.string(key: String): String? = stringOrNull(key)?.takeIf { it.isNotBlank() }
