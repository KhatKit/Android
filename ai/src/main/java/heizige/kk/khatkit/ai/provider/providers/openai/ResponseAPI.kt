package heizige.kk.khatkit.ai.provider.providers.openai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.core.ReasoningLevel
import heizige.kk.khatkit.ai.core.TokenUsage
import heizige.kk.khatkit.ai.provider.BuiltInTools
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.provider.TextGenerationResult
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.provider.stream.SseEvent
import heizige.kk.khatkit.ai.provider.providers.PartGroup
import heizige.kk.khatkit.ai.provider.providers.groupPartsByToolBoundary
import heizige.kk.khatkit.ai.registry.ModelRegistry
import heizige.kk.khatkit.ai.ui.StreamChunk
import heizige.kk.khatkit.ai.ui.OpenAIReasoningMetadata
import heizige.kk.khatkit.ai.ui.ReasoningType
import heizige.kk.khatkit.ai.ui.ServerToolMetadata
import heizige.kk.khatkit.ai.ui.ServerToolProtocol
import heizige.kk.khatkit.ai.ui.ServerToolStatus
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.ai.ui.metadataAs
import heizige.kk.khatkit.ai.ui.toMetadata
import heizige.kk.khatkit.ai.util.HttpException
import heizige.kk.khatkit.ai.util.KeyRoulette
import heizige.kk.khatkit.ai.util.mergeCustomHeaders
import heizige.kk.khatkit.ai.util.configureReferHeaders
import heizige.kk.khatkit.ai.util.configureSessionHeaders
import heizige.kk.khatkit.ai.util.encodeBase64
import heizige.kk.khatkit.ai.util.json
import heizige.kk.khatkit.ai.util.mergeCustomBody
import heizige.kk.khatkit.ai.util.parseErrorDetail
import heizige.kk.khatkit.ai.util.stringSafe
import heizige.kk.khatkit.ai.util.toHeaders
import heizige.kk.khatkit.common.http.jsonObjectOrNull
import heizige.kk.khatkit.common.http.jsonPrimitiveOrNull
import heizige.kk.khatkit.common.http.okhttp.toHttpUrl
import heizige.kk.khatkit.common.http.okhttp.toMediaType
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.common.http.okhttp.Request
import heizige.kk.khatkit.common.http.okhttp.toRequestBody
import heizige.kk.khatkit.common.http.okhttp.Response
import heizige.kk.khatkit.common.http.okhttp.sse.EventSource
import heizige.kk.khatkit.common.http.okhttp.sse.EventSourceListener
import heizige.kk.khatkit.common.http.okhttp.sse.EventSources
import kotlin.time.Clock

private const val TAG = "ResponseAPI"

/**
 * 一次 Responses 调用的端点描述：地址、协议差异（capabilities）与请求头/鉴权装配。
 *
 * 抽出来的目的：OpenAI（api key + 可配 baseUrl）、ChatGPT 订阅（Codex 固定地址 + OAuth 凭据）
 * 与后续 Grok 共用同一套请求体构建与流式解码，只有这三件事不同。
 */
internal class ResponsesEndpoint(
    val url: String,
    val capabilities: ResponseProviderCapabilities = ResponseProviderCapabilities(),
    /** 构建请求时调用，负责请求头和鉴权；可挂起，以便先刷新 token */
    val configureRequest: suspend Request.Builder.() -> Unit = {},
)

class ResponseAPI(
    private val client: OkHttpClient,
    private val keyRoulette: KeyRoulette = KeyRoulette.default()
) : OpenAIImpl {
    override suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): TextGenerationResult = generateText(providerSetting.toResponsesEndpoint(params), messages, params)

    internal suspend fun generateText(
        endpoint: ResponsesEndpoint,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): TextGenerationResult = withContext(Dispatchers.IO) {
        val requestBody = buildRequestBody(
            capabilities = endpoint.capabilities,
            messages = messages,
            params = params,
            stream = false,
        )
        val builder = Request.Builder()
            .url(endpoint.url)
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
        endpoint.configureRequest(builder)
        val request = builder.build()

        Log.i(TAG, "generateText: ${json.encodeToString(requestBody)}")

        // await() waits for the response headers; reading the body can still block.
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to get response: ${response.code} ${response.body.string()}")
            }

            val bodyStr = response.body.string()
            Log.i(TAG, "generateText: $bodyStr")
            val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
            parseResponseOutput(bodyJson)
        }
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<StreamChunk> = streamText(providerSetting.toResponsesEndpoint(params), messages, params)

    internal suspend fun streamText(
        endpoint: ResponsesEndpoint,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<StreamChunk> = callbackFlow {
        val requestBody = buildRequestBody(
            capabilities = endpoint.capabilities,
            messages = messages,
            params = params,
            stream = true,
        )
        val builder = Request.Builder()
            .url(endpoint.url)
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
        endpoint.configureRequest(builder)
        val request = builder.build()

        Log.i(TAG, "streamText: ${json.encodeToString(requestBody)}")

        val decoder = ResponseApiStreamDecoder()

        fun sendChunks(chunks: Iterable<StreamChunk>) {
            chunks.forEach { chunk ->
                trySend(chunk).onFailure { e ->
                    Log.w(TAG, "onEvent: chunk dropped (${e?.message})")
                }
            }
        }

        val listener = responseApiStreamListener(
            decoder = decoder,
            sendChunks = ::sendChunks,
            closeFlow = { close(it) },
        )

        val eventSource = EventSources.createFactory(client)
            .newEventSource(request, listener)

        awaitClose {
            println("[awaitClose] 关闭eventSource ")
            eventSource.cancel()
        }
        // trySend 在缓冲满时会静默丢弃 delta，导致回复中间缺字 (#1295)，因此缓冲必须无界
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    internal fun buildRequestBody(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean
    ): JsonObject = buildRequestBody(
        capabilities = resolveResponseProviderCapabilities(providerSetting.baseUrl.toHttpUrl().host),
        messages = messages,
        params = params,
        stream = stream,
    )

    internal fun buildRequestBody(
        capabilities: ResponseProviderCapabilities,
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean
    ): JsonObject {
        return buildJsonObject {
            put("model", params.model.modelId)
            put("stream", stream)
            put("store", false)

            if (capabilities.supportsSamplingParams && isModelAllowTemperature(params.model)) {
                if (params.temperature != null) put("temperature", params.temperature)
                if (params.topP != null) put("top_p", params.topP)
            }
            if (capabilities.supportsMaxOutputTokens && params.maxTokens != null) {
                put("max_output_tokens", params.maxTokens)
            }
            if (capabilities.supportsPromptCacheKey && params.sessionId != null) {
                put("prompt_cache_key", params.sessionId)
            }

            // system 提示：默认走 instructions；Codex 要求作为首条 developer 消息放进 input
            val systemText = messages.firstOrNull { it.role == MessageRole.SYSTEM }?.parts
                ?.filterIsInstance<UIMessagePart.Text>()
                ?.joinToString("\n") { it.text }
            val systemRole = capabilities.systemMessageRole
            if (systemText != null && systemRole == null) {
                put("instructions", systemText)
            }

            // messages
            put("input", buildJsonArray {
                if (systemText != null && systemRole != null) {
                    add(buildJsonObject {
                        put("role", systemRole)
                        put("type", "message")
                        put("content", systemText)
                    })
                }
                addAll(buildMessages(messages))
            })

            // reasoning
            if (params.model.abilities.contains(ModelAbility.REASONING)) {
                val level = params.reasoningLevel
                put("reasoning", buildJsonObject {
                    if (capabilities.supportsReasoningSummary) {
                        put("summary", "auto")
                    }
                    if (level != ReasoningLevel.AUTO) {
                        put("effort", level.effort)
                    }
                })
                if (capabilities.supportEncryptedContent) {
                    put("include", buildJsonArray {
                        add("reasoning.encrypted_content")
                    })
                }
            }
            // Codex 的推理续接要求无条件带上加密推理内容（与模型能力无关）
            if (capabilities.alwaysIncludeEncryptedContent) {
                put("include", buildJsonArray { add("reasoning.encrypted_content") })
            }

            // tools
            // Response API 的 tools 是扁平数组, 函数工具和内置工具可以共存, 必须写在同一个 key 下,
            // 否则后写入的会覆盖前者
            val useFunctionTools =
                params.model.abilities.contains(ModelAbility.TOOL) && params.tools.isNotEmpty()
            if (useFunctionTools || params.model.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    if (useFunctionTools) {
                        params.tools.forEach { tool ->
                            add(buildJsonObject {
                                put("type", "function")
                                put("name", tool.name)
                                put("description", tool.description)
                                put(
                                    "parameters",
                                    json.encodeToJsonElement(
                                        tool.parameters()
                                    )
                                )
                            })
                        }
                    }
                    // built-in tools
                    params.model.tools.forEach { builtInTool ->
                        when (builtInTool) {
                            BuiltInTools.Search -> {
                                add(buildJsonObject {
                                    put("type", "web_search")
                                })
                            }

                            BuiltInTools.UrlContext -> {} // not supported

                            BuiltInTools.ImageGeneration -> {
                                add(buildJsonObject {
                                    put("type", "image_generation")
                                    put("model", "gpt-image-2")
                                })
                            }
                        }
                    }
                }
            }
        }.mergeCustomBody(params.customBody)
    }

    /** OpenAI 的端点：可配 baseUrl + api key，能力按 baseUrl 的 host 决定 */
    private fun ProviderSetting.OpenAI.toResponsesEndpoint(params: TextGenerationParams) = ResponsesEndpoint(
        url = baseUrl + responsesPath,
        capabilities = resolveResponseProviderCapabilities(baseUrl.toHttpUrl().host),
        configureRequest = {
            headers(mergeCustomHeaders(params.customHeaders))
            addHeader("Authorization", "Bearer " + keyRoulette.next(apiKey, id.toString()))
            configureReferHeaders(baseUrl)
            configureSessionHeaders(baseUrl, params.sessionId)
        },
    )

    internal fun buildMessages(messages: List<UIMessage>) = buildJsonArray {
        messages
            .filter { message ->
                message.role != MessageRole.SYSTEM && (
                    message.isValidToUpload() || message.parts.any { part ->
                        part is UIMessagePart.Reasoning &&
                            part.metadataAs<OpenAIReasoningMetadata>()?.encryptedContent != null
                    }
                )
            }
            .forEach { message ->
                if (message.role == MessageRole.ASSISTANT) {
                    addAssistantItems(message)
                } else {
                    addUserItems(message)
                }
            }
    }

    private fun JsonArrayBuilder.addAssistantItems(message: UIMessage) {
        val groups = groupPartsByToolBoundary(message.parts)
        val contentBuffer = mutableListOf<UIMessagePart>()

        for (group in groups) {
            when (group) {
                is PartGroup.Content -> {
                    val emittedReasoningIds = mutableSetOf<String>()
                    group.parts.forEach { part ->
                        when (part) {
                            is UIMessagePart.Reasoning -> {
                                val reasoningMetadata = part.metadataAs<OpenAIReasoningMetadata>()
                                val reasoningId = reasoningMetadata?.reasoningId
                                if (reasoningId != null && !emittedReasoningIds.add(reasoningId)) {
                                    return@forEach
                                }
                                // 先输出累积的文本/图片内容
                                if (contentBuffer.isNotEmpty()) {
                                    addContentItem(MessageRole.ASSISTANT, contentBuffer)
                                    contentBuffer.clear()
                                }
                                // 输出 reasoning item
                                val reasoningParts = if (reasoningId == null) {
                                    listOf(part)
                                } else {
                                    group.parts.filterIsInstance<UIMessagePart.Reasoning>().filter {
                                        it.metadataAs<OpenAIReasoningMetadata>()?.reasoningId == reasoningId
                                    }
                                }
                                add(buildJsonObject {
                                    put("type", "reasoning")
                                    reasoningId?.let { put("id", it) }
                                    put("summary", buildJsonArray {
                                        reasoningParts
                                            .filter { it.reasoningType == ReasoningType.SUMMARY_TEXT }
                                            .filter { it.reasoning.isNotEmpty() }
                                            .forEach {
                                                add(buildJsonObject {
                                                    put("type", "summary_text")
                                                    put("text", it.reasoning)
                                                })
                                            }
                                    })
                                    val encryptedContent = reasoningMetadata?.encryptedContent
                                    val content = reasoningParts
                                        .filter { it.reasoningType == ReasoningType.REASONING_TEXT }
                                        .filter { it.reasoning.isNotEmpty() }
                                    if (encryptedContent == null && content.isNotEmpty()) {
                                        put("content", buildJsonArray {
                                            content.forEach {
                                                add(buildJsonObject {
                                                    put("type", "reasoning_text")
                                                    put("text", it.reasoning)
                                                })
                                            }
                                        })
                                    }
                                    encryptedContent?.let {
                                        put("encrypted_content", it)
                                    }
                                })
                            }

                            is UIMessagePart.Image -> {
                                if (contentBuffer.isNotEmpty()) {
                                    addContentItem(MessageRole.ASSISTANT, contentBuffer)
                                    contentBuffer.clear()
                                }
                                addContentItem(MessageRole.USER, listOf(part))
                            }

                            is UIMessagePart.Text -> {
                                contentBuffer.add(part)
                            }

                            is UIMessagePart.ServerTool -> {
                                if (contentBuffer.isNotEmpty()) {
                                    addContentItem(MessageRole.ASSISTANT, contentBuffer)
                                    contentBuffer.clear()
                                }
                                addServerToolItem(part)
                            }

                            else -> {}
                        }
                    }
                }

                is PartGroup.Tools -> {
                    // 先输出累积的内容
                    if (contentBuffer.isNotEmpty()) {
                        addContentItem(MessageRole.ASSISTANT, contentBuffer)
                        contentBuffer.clear()
                    }

                    // 同一批并发工具调用需先输出全部 function_call，再输出对应结果。
                    group.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function_call")
                            put("call_id", tool.toolCallId)
                            put("name", tool.toolName)
                            // 使用 inputAsJson() 归一化，避免流式中断导致的残缺 JSON 被发送
                            put("arguments", tool.inputAsJson().toString())
                        })
                    }
                    group.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function_call_output")
                            put("call_id", tool.toolCallId)
                            val hasImage = tool.output.any { it is UIMessagePart.Image }
                            if (hasImage) {
                                putJsonArray("output") {
                                    tool.output.forEach { part ->
                                        when (part) {
                                            is UIMessagePart.Image -> add(buildJsonObject {
                                                part.encodeBase64().onSuccess { encoded ->
                                                    put("type", "input_image")
                                                    put("image_url", encoded.base64)
                                                }.onFailure {
                                                    it.printStackTrace()
                                                    put("type", "input_text")
                                                    put("text", "Error: Failed to encode image to base64")
                                                }
                                            })
                                            is UIMessagePart.Text -> add(buildJsonObject {
                                                put("type", "input_text")
                                                put("text", part.text)
                                            })
                                            else -> {}
                                        }
                                    }
                                }
                            } else {
                                put(
                                    "output",
                                    tool.output.filterIsInstance<UIMessagePart.Text>()
                                        .joinToString("\n") { it.text }
                                )
                            }
                        })
                    }
                }
            }
        }

        // 输出剩余内容
        if (contentBuffer.isNotEmpty()) {
            addContentItem(MessageRole.ASSISTANT, contentBuffer)
        }
    }

    private fun JsonArrayBuilder.addServerToolItem(tool: UIMessagePart.ServerTool) {
        val metadata = tool.metadataAs<ServerToolMetadata>()
        val protocol = metadata?.protocol
        if (protocol != null && protocol != ServerToolProtocol.OPENAI_RESPONSES) return

        val rawCall = metadata?.call.takeIf { protocol == ServerToolProtocol.OPENAI_RESPONSES }
        if (rawCall != null) {
            add(rawCall)
            return
        }

        add(buildJsonObject {
            put("type", "${tool.toolName.removeSuffix("_call")}_call")
            put("id", tool.toolCallId)
            put("status", tool.status.toOpenAIStatus())
            tool.input?.let { input ->
                if (tool.toolName.removeSuffix("_call") == "web_search") put("action", input)
                else if (input is JsonObject) input.forEach { (key, value) -> put(key, value) }
                else put("input", input)
            }
            tool.output?.let { put("output", it) }
        })
    }

    private fun JsonArrayBuilder.addUserItems(message: UIMessage) {
        val contentParts = message.parts.filter { it is UIMessagePart.Text || it is UIMessagePart.Image }
        if (contentParts.isNotEmpty()) {
            addContentItem(message.role, contentParts)
        }
    }

    private fun JsonArrayBuilder.addContentItem(role: MessageRole, parts: List<UIMessagePart>) {
        if (parts.isEmpty()) return

        add(buildJsonObject {
            put("role", JsonPrimitive(role.name.lowercase()))

            if (parts.isOnlyTextPart()) {
                put("content", (parts.first() as UIMessagePart.Text).text)
            } else {
                putJsonArray("content") {
                    parts.forEach { part ->
                        when (part) {
                            is UIMessagePart.Text -> {
                                add(buildJsonObject {
                                    put("type", if (role == MessageRole.USER) "input_text" else "output_text")
                                    put("text", part.text)
                                })
                            }

                            is UIMessagePart.Image -> {
                                add(buildJsonObject {
                                    part.encodeBase64().onSuccess { encodedImage ->
                                        put("type", "input_image")
                                        put("image_url", encodedImage.base64)
                                    }.onFailure {
                                        it.printStackTrace()
                                        put("type", "input_text")
                                        put("text", "Error: Failed to encode image to base64")
                                    }
                                })
                            }

                            else -> {}
                        }
                    }
                }
            }
        })
    }

    internal fun parseResponseOutput(jsonObject: JsonObject): TextGenerationResult {
        println(jsonObject)
        val outputs = jsonObject["output"]?.jsonArray ?: error("output not found")
        val parts = arrayListOf<UIMessagePart>()

        outputs.forEach { outputItem ->
            val output = outputItem.jsonObject
            val type = output["type"]?.jsonPrimitive?.content ?: error("output type not found")
            when (type) {
                "reasoning" -> {
                    val reasoningMetadata = OpenAIReasoningMetadata(
                        reasoningId = output["id"]?.jsonPrimitive?.contentOrNull,
                        encryptedContent = output["encrypted_content"]?.jsonPrimitive?.contentOrNull,
                    ).toMetadata()
                    val reasoningPartStart = parts.size
                    output["summary"]?.jsonArray.orEmpty().map { it.jsonObject }.forEach { part ->
                        val partType = part["type"]?.jsonPrimitive?.content ?: error("part type not found")
                        when (partType) {
                            "summary_text" -> {
                                val text = part["text"]?.jsonPrimitive?.content ?: error("text not found")
                                parts.add(
                                    UIMessagePart.Reasoning(
                                        reasoning = text,
                                        createdAt = Clock.System.now(),
                                        finishedAt = Clock.System.now(),
                                        metadata = reasoningMetadata,
                                        reasoningType = ReasoningType.SUMMARY_TEXT,
                                    )
                                )
                            }
                        }
                    }
                    output["content"]?.jsonArray.orEmpty().map { it.jsonObject }.forEach { part ->
                        if (part["type"]?.jsonPrimitive?.contentOrNull == "reasoning_text") {
                            parts.add(
                                UIMessagePart.Reasoning(
                                    reasoning = part["text"]?.jsonPrimitive?.content ?: error("text not found"),
                                    createdAt = Clock.System.now(),
                                    finishedAt = Clock.System.now(),
                                    metadata = reasoningMetadata,
                                    reasoningType = ReasoningType.REASONING_TEXT,
                                )
                            )
                        }
                    }
                    if (parts.size == reasoningPartStart) {
                        parts.add(
                            UIMessagePart.Reasoning(
                                reasoning = "",
                                createdAt = Clock.System.now(),
                                finishedAt = Clock.System.now(),
                                metadata = reasoningMetadata,
                                reasoningType = ReasoningType.REASONING_TEXT,
                            )
                        )
                    }
                }

                "function_call" -> {
                    val callId = output["call_id"]?.jsonPrimitive?.content ?: error("call_id not found")
                    val name = output["name"]?.jsonPrimitive?.content ?: error("name not found")
                    val arguments =
                        output["arguments"]?.jsonPrimitive?.content ?: error("arguments not found")
                    parts.add(
                        UIMessagePart.Tool(
                            toolCallId = callId,
                            toolName = name,
                            input = arguments,
                            output = emptyList()
                        )
                    )
                }

                "message" -> {
                    val content = output["content"]?.jsonArray ?: error("content not found")
                    content.map { it.jsonObject }.forEach { part ->
                        val partType = part["type"]?.jsonPrimitive?.content ?: error("part type not found")
                        when (partType) {
                            "output_text" -> {
                                val text = part["text"]?.jsonPrimitive?.content ?: error("text not found")
                                parts.add(
                                    UIMessagePart.Text(
                                        text = text
                                    )
                                )
                            }

                            else -> error("unknown part type $partType")
                        }
                    }
                }

                else -> if (isOpenAIServerToolCall(type)) {
                    parts.add(output.toOpenAIServerTool())
                }
            }
        }

        return TextGenerationResult(
            id = jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: "",
            model = jsonObject["model"]?.jsonPrimitive?.contentOrNull ?: "",
            message = UIMessage(
                role = MessageRole.ASSISTANT,
                parts = parts,
            ),
            finishReason = jsonObject["status"]?.jsonPrimitive?.contentOrNull,
            usage = parseTokenUsage(jsonObject["usage"]?.jsonObject)
        )
    }

    private fun parseTokenUsage(jsonObject: JsonObject?): TokenUsage? {
        if (jsonObject == null) return null
        return TokenUsage(
            promptTokens = jsonObject["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            completionTokens = jsonObject["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            totalTokens = jsonObject["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            cachedTokens = jsonObject["input_tokens_details"]?.jsonObjectOrNull?.get("cached_tokens")?.jsonPrimitive?.intOrNull
                ?: 0
        )
    }
}

internal fun isOpenAIServerToolCall(type: String): Boolean =
    type.endsWith("_call") && type !in setOf(
        "function_call",
        "custom_tool_call",
        "computer_call",
        "local_shell_call",
        "shell_call",
        "image_generation_call",
    )

internal fun JsonObject.toOpenAIServerTool(): UIMessagePart.ServerTool {
    val type = get("type")?.jsonPrimitive?.contentOrNull ?: "server_tool_call"
    val protocolFields = setOf("type", "id", "status", "result", "output")
    val input = get("action") ?: JsonObject(filterKeys { it !in protocolFields })
        .takeUnless { it.isEmpty() }
    return UIMessagePart.ServerTool(
        toolCallId = get("id")?.jsonPrimitive?.contentOrNull ?: "",
        toolName = type.removeSuffix("_call"),
        input = input,
        output = get("output") ?: get("result"),
        status = get("status")?.jsonPrimitive?.contentOrNull.toServerToolStatus(),
        metadata = ServerToolMetadata(
            protocol = ServerToolProtocol.OPENAI_RESPONSES,
            call = this,
        ).toMetadata(),
    )
}

internal fun String?.toServerToolStatus(): ServerToolStatus = when (this) {
    "completed" -> ServerToolStatus.COMPLETED
    "failed", "incomplete", "cancelled" -> ServerToolStatus.FAILED
    else -> ServerToolStatus.IN_PROGRESS
}

private fun ServerToolStatus.toOpenAIStatus(): String = when (this) {
    ServerToolStatus.IN_PROGRESS -> "in_progress"
    ServerToolStatus.COMPLETED -> "completed"
    ServerToolStatus.FAILED -> "failed"
}

private fun isModelAllowTemperature(model: Model): Boolean {
    return !ModelRegistry.OPENAI_O_MODELS.match(model.modelId) && !ModelRegistry.GPT_5.match(model.modelId)
}

private fun List<UIMessagePart>.isOnlyTextPart(): Boolean {
    val gonnaSend = filter { it is UIMessagePart.Text || it is UIMessagePart.Image }.size
    val texts = filter { it is UIMessagePart.Text }.size
    return gonnaSend == texts && texts == 1
}

internal data class ResponseProviderCapabilities(
    val supportsReasoningSummary: Boolean = true,
    val supportEncryptedContent: Boolean = true,
    /**
     * system 提示的给法：null 表示用 OpenAI 的 instructions 字段；
     * 给字符串（如 Codex 的 "developer"）则作为 input 首条消息。
     */
    val systemMessageRole: String? = null,
    /** 是否接受 temperature/top_p（Codex 不接受） */
    val supportsSamplingParams: Boolean = true,
    /** 是否无条件带上 reasoning.encrypted_content（Codex 多轮推理靠它续接） */
    val alwaysIncludeEncryptedContent: Boolean = false,
    /** 是否把 sessionId 当作 prompt_cache_key 发出去（Codex 的会话粘性） */
    val supportsPromptCacheKey: Boolean = false,
    /** 是否接受 max_output_tokens（Codex 不接受） */
    val supportsMaxOutputTokens: Boolean = true,
)

internal fun resolveResponseProviderCapabilities(host: String): ResponseProviderCapabilities {
    return when (host) {
        "ark.cn-beijing.volces.com" -> ResponseProviderCapabilities(
            supportsReasoningSummary = false,
            supportEncryptedContent = false
        )

        else -> ResponseProviderCapabilities()
    }
}

/**
 * 构造 responses API 的 SSE 监听器。
 *
 * 抽成顶层 `internal` 函数是为了让失败路径能在 JVM 单测里用真实 [EventSource]
 * 驱动验证（`streamText` 自身依赖 `android.util.Log`，不便在单测里直接收集）。
 */
internal fun responseApiStreamListener(
    decoder: ResponseApiStreamDecoder,
    sendChunks: (Iterable<StreamChunk>) -> Unit,
    closeFlow: (Throwable?) -> Unit,
): EventSourceListener = object : EventSourceListener() {
    override fun onEvent(
        eventSource: EventSource,
        id: String?,
        type: String?,
        data: String
    ) {
        Log.d(TAG, "onEvent: $id/$type $data")
        try {
            val result = decoder.accept(SseEvent(id = id, event = type, data = data))
            sendChunks(result.chunks)
            if (result.completed) closeFlow(null)
        } catch (e: Throwable) {
            closeFlow(e)
        }
    }

    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
        var exception = t

        t?.printStackTrace()
        println("[onFailure] 发生错误: ${t?.javaClass?.name} ${t?.message} / $response")

        val bodyRaw = response?.body?.stringSafe()
        try {
            if (!bodyRaw.isNullOrBlank()) {
                val bodyElement = Json.parseToJsonElement(bodyRaw)
                println(bodyElement)
                exception = bodyElement.parseErrorDetail()
                Log.i(TAG, "onFailure: $exception")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "onFailure: failed to parse from $bodyRaw")
            e.printStackTrace()
        } finally {
            // 非 2xx 时 KtorEventSource 固定以 t=null 回调（错误信息只能来自响应体）；
            // 响应体为空或无法解析时 exception 保持 null，原样 close(null) 会让 callbackFlow
            // 按正常完成收场——零 chunk、零异常，上游只能报出「本轮没有产出内容」。
            // 兜底成带 HTTP 状态码的异常，保证失败回调必然以异常收场。
            closeFlow(
                exception ?: HttpException(
                    response?.let { "Failed to get response: HTTP ${it.code} ${it.message}" }
                        ?: "Stream failed: unknown error"
                )
            )
        }
    }

    override fun onClosed(eventSource: EventSource) {
        sendChunks(decoder.onClosed())
        closeFlow(null)
    }
}
