package heizige.kk.khatkit.app.core.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.core.TokenUsage
import heizige.kk.khatkit.ai.core.Tool
import heizige.kk.khatkit.ai.core.sum
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.Modality
import heizige.kk.khatkit.ai.provider.Provider
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.ai.ui.ToolApprovalState
import heizige.kk.khatkit.ai.ui.StreamChunkHandler
import heizige.kk.khatkit.ai.ui.handleTextGenerationResult
import heizige.kk.khatkit.ai.ui.limitContext
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.transformers.InputMessageTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.MessageTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.OutputMessageTransformer
import heizige.kk.khatkit.app.core.data.files.FileFolders
import heizige.kk.khatkit.app.core.data.ai.transformers.onGenerationFinish
import heizige.kk.khatkit.app.core.data.ai.transformers.transforms
import heizige.kk.khatkit.app.core.data.ai.transformers.visualTransforms
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.findProvider
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.ai.tools.buildRoleIdentity
import heizige.kk.khatkit.app.core.data.model.AssistantMemory
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "GenerationHandler"
private const val MAX_TOOL_OUTPUT_CHARS = 32 * 1024
private const val TOOL_OUTPUT_PREVIEW_CHARS = 4 * 1024
private const val MAX_PROVIDER_NETWORK_RETRIES = 3
private const val INITIAL_PROVIDER_RETRY_DELAY_MS = 1_000L

private class StreamChunkHandlingException(cause: Throwable) : RuntimeException(cause)

@Serializable
sealed interface GenerationChunk {
    data class Messages(
        val messages: List<UIMessage>
    ) : GenerationChunk
}

class GenerationLoop(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val usageRecorder: UsageRecorder,
    private val json: Json,
) {
    fun generateText(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer> = emptyList(),
        outputTransformers: List<OutputMessageTransformer> = emptyList(),
        assistant: Assistant,
        memories: List<AssistantMemory>? = null,
        tools: List<Tool> = emptyList(),
        maxSteps: Int = 256,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationId: Uuid? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
        workspaceCwd: String? = null,
        onAutoCompress: (suspend (List<UIMessage>) -> List<UIMessage>)? = null,
    ): Flow<GenerationChunk> = flow {
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val providerImpl = providerManager.getProviderByType(provider)
        val failoverChain = ProviderFailover.buildChain(settings, provider, model)

        var messages: List<UIMessage> = messages
        var autoCompressedAssistantId: Uuid? = null

        for (stepIndex in 0 until maxSteps) {
            Log.i(TAG, "streamText: start step #$stepIndex (${model.id})")

            // Check if we have tool calls ready to continue after user interaction.
            val pendingTools = messages.lastOrNull()?.getTools()?.filter {
                it.canResumeExecution
            } ?: emptyList()

            val toolsToProcess: List<UIMessagePart.Tool>

            // Skip generation if we have approved/denied tool calls to handle
            if (pendingTools.isEmpty()) {
                for ((chainIndex, candidate) in failoverChain.withIndex()) {
                    val (candidateProvider, candidateModel) = candidate
                    val messagesForCandidate =
                        if (candidateModel.inputModalities.contains(Modality.IMAGE)) {
                            messages
                        } else {
                            messages.withLocalImagePathHints()
                        }
                    try {
                        generateInternal(
                            assistant = assistant,
                            settings = settings,
                            messages = messagesForCandidate,
                            onUpdateMessages = {
                                messages = it.withoutLocalImagePathHints().transforms(
                                    transformers = outputTransformers,
                                    context = context,
                                    model = model,
                                    assistant = assistant,
                                    settings = settings
                                )
                                emit(
                                    GenerationChunk.Messages(
                                        messages.visualTransforms(
                                            transformers = outputTransformers,
                                            context = context,
                                            model = model,
                                            assistant = assistant,
                                            settings = settings
                                        )
                                    )
                                )
                            },
                            transformers = inputTransformers,
                            model = candidateModel,
                            providerImpl = if (candidateProvider.id == provider.id) {
                                providerImpl
                            } else {
                                providerManager.getProviderByType(candidateProvider)
                            },
                            provider = candidateProvider,
                            tools = tools,
                            memories = memories ?: emptyList(),
                            stream = assistant.streamOutput,
                            processingStatus = processingStatus,
                            conversationSystemPrompt = conversationSystemPrompt,
                            conversationId = conversationId,
                            conversationModeInjectionIds = conversationModeInjectionIds,
                            conversationLorebookIds = conversationLorebookIds,
                            workspaceCwd = workspaceCwd,
                        )
                        break
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        val hasNext = chainIndex < failoverChain.lastIndex &&
                            settings.networkSetting.enableAutoRetry
                        TaskRoutes.router.reportFailure(
                            ModelRouteCandidate(
                                candidateProvider.id.toString(),
                                candidateModel.id.toString(),
                            ),
                            System.currentTimeMillis(),
                        )
                        if (ProviderFailover.isEligible(error)) {
                            heizige.kk.khatkit.ai.util.KeyPool.shared.failLast(
                                candidateProvider.id.toString(),
                                if ((error.message ?: "").contains("429")) 429 else 500,
                                System.currentTimeMillis(),
                            )
                        }
                        if (!hasNext || !ProviderFailover.isEligible(error)) throw error
                        Log.w(
                            TAG,
                            "provider failover: ${candidateProvider.name} 失败，" +
                                "切换下一个 (${chainIndex + 1}/${failoverChain.lastIndex + 1})",
                            error,
                        )
                        messages = messagesForCandidate.withoutLocalImagePathHints()
                    }
                }
                messages = messages.visualTransforms(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                messages = messages.onGenerationFinish(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                messages = messages.slice(0 until messages.lastIndex) + messages.last().copy(
                    finishedAt = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                )
                emit(GenerationChunk.Messages(messages))

                val toolCalls = messages.last().getTools().filter { !it.isExecuted }
                if (toolCalls.isEmpty()) {
                    // no tool calls, break
                    break
                }

                // Check for tools that need approval
                var hasPendingApproval = false
                val updatedTools = toolCalls.map { tool ->
                    val toolDef = tools.find { it.name == tool.toolName }
                    when {
                        // Tool needs approval and state is Auto -> set to Pending
                        toolDef?.needsApproval(tool.inputAsJson()) == true &&
                            tool.approvalState is ToolApprovalState.Auto -> {
                            hasPendingApproval = true
                            tool.copy(approvalState = ToolApprovalState.Pending)
                        }
                        // State is Pending -> keep waiting
                        tool.approvalState is ToolApprovalState.Pending -> {
                            hasPendingApproval = true
                            tool
                        }

                        else -> tool
                    }
                }

                // If any tools were updated to Pending, update the message and break
                if (updatedTools != toolCalls) {
                    val lastMessage = messages.last()
                    val updatedParts = lastMessage.parts.map { part ->
                        if (part is UIMessagePart.Tool) {
                            updatedTools.find { it.toolCallId == part.toolCallId } ?: part
                        } else {
                            part
                        }
                    }
                    messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
                    emit(GenerationChunk.Messages(messages))
                }

                // If there are pending approvals, break and wait for user
                if (hasPendingApproval) {
                    Log.i(TAG, "generateText: waiting for tool approval")
                    break
                }

                toolsToProcess = updatedTools
            } else {
                // Resuming after user interaction - use the resumable tools directly.
                Log.i(TAG, "generateText: resuming with ${pendingTools.size} resumable tools")
                toolsToProcess = messages.last().getTools().filter { it.canResumeExecution }
            }

            // Handle tools concurrently. A card call may wait on network/UI for a long time;
            // keeping the batch parallel prevents one cloud card from serializing the others.
            // `awaitAll` preserves input order, so message/tool-call correlation remains stable.
            val executedTools = coroutineScope {
                toolsToProcess.map { tool ->
                    async(Dispatchers.Default) {
                        when (tool.approvalState) {
                            is ToolApprovalState.Denied -> {
                                // Tool was denied by user.
                                val reason = (tool.approvalState as ToolApprovalState.Denied).reason
                                tool.copy(
                                    output = listOf(
                                        UIMessagePart.Text(
                                            json.encodeToString(
                                                buildJsonObject {
                                                    put(
                                                        "error",
                                                        JsonPrimitive("Tool execution denied by user. Reason: ${reason.ifBlank { "No reason provided" }}")
                                                    )
                                                }
                                            )
                                        )
                                    )
                                )
                            }

                            is ToolApprovalState.Answered -> {
                                // Tool was answered by user (e.g., ask_user tool).
                                val answer = (tool.approvalState as ToolApprovalState.Answered).answer
                                tool.copy(output = listOf(UIMessagePart.Text(answer)))
                            }

                            is ToolApprovalState.Pending -> {
                                // Should not reach here, but just in case
                                null
                            }

                            else -> {
                                // Auto or Approved - execute the tool.
                                val toolStartedAt = Clock.System.now()
                                runCatching {
                                    val toolDef = tools.find { toolDef -> toolDef.name == tool.toolName }
                                        ?: error("Tool ${tool.toolName} not found")
                                    val args = runCatching {
                                        json.parseToJsonElement(tool.input.ifBlank { "{}" })
                                    }.getOrElse {
                                        error("Invalid tool arguments JSON for ${tool.toolName}: ${it.message}")
                                    }
                                    Log.i(TAG, "generateText: executing tool ${toolDef.name} with args: $args")
                                    val hookedArgs = heizige.kk.khatkit.app.core.data.ai.toolpkg.ToolPkgHooks.prepareToolArgs(
                                        enabled = settings.featureFlags.pluginHooksEnabled,
                                        toolName = tool.toolName,
                                        args = args,
                                    )
                                    val result = toolDef.execute(hookedArgs)
                                    val hasShellAccess = tools.any { it.name == "workspace_shell" }
                                    tool.copy(
                                        output = maybeTruncateToolOutput(tool.toolCallId, result, hasShellAccess),
                                        startedAt = toolStartedAt,
                                        finishedAt = Clock.System.now(),
                                    )
                                }.getOrElse {
                                    // 取消必须向上传播，否则停止生成会被误报为工具执行错误
                                    if (it is CancellationException) throw it
                                    it.printStackTrace()
                                    tool.copy(
                                        output = listOf(
                                            UIMessagePart.Text(
                                                json.encodeToString(
                                                    buildJsonObject {
                                                        put(
                                                            "error",
                                                            JsonPrimitive(buildString {
                                                                append("[${it.javaClass.name}] ${it.message}")
                                                                append("\n${it.stackTraceToString()}")
                                                            })
                                                        )
                                                    }
                                                )
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (executedTools.isEmpty()) {
                // No results to add (all tools were pending)
                break
            }

            // Update last message with executed tools (NOT create TOOL message)
            val lastMessage = messages.last()
            val updatedParts = lastMessage.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    executedTools.find { it.toolCallId == part.toolCallId } ?: part
                } else part
            }
            messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
            emit(
                GenerationChunk.Messages(
                    messages.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant,
                        settings = settings
                    )
                )
            )

            // 工具执行后通常会继续下一次模型请求；如果这一轮已经达到上下文阈值，
            // 先让上层压缩并替换本地消息，再进入下一步，避免把超长历史继续发送出去。
            val latestAssistant = messages.lastOrNull()
            if (
                onAutoCompress != null &&
                latestAssistant?.role == MessageRole.ASSISTANT &&
                latestAssistant.id != autoCompressedAssistantId &&
                latestAssistant.contextTokens() != null
            ) {
                val compressed = onAutoCompress(messages)
                if (compressed != messages) {
                    messages = compressed
                    autoCompressedAssistantId = latestAssistant.id
                    emit(
                        GenerationChunk.Messages(
                            messages.transforms(
                                transformers = outputTransformers,
                                context = context,
                                model = model,
                                assistant = assistant,
                                settings = settings,
                            )
                        )
                    )
                }
            }
        }

    }.flowOn(Dispatchers.IO)

    private suspend fun generateInternal(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
        transformers: List<MessageTransformer>,
        model: Model,
        providerImpl: Provider<ProviderSetting>,
        provider: ProviderSetting,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        stream: Boolean,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationId: Uuid? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
        workspaceCwd: String? = null,
    ) {
        val internalMessages = buildList {
            val system = buildString {
                val effectiveSystemPrompt =
                    if (assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()) {
                        conversationSystemPrompt
                    } else {
                        assistant.systemPrompt
                    }
                if (effectiveSystemPrompt.isNotBlank()) {
                    append(effectiveSystemPrompt)
                }
                appendLine()
                append(buildRoleIdentity(assistant))

                // 记忆
                if (assistant.enableMemory) {
                    appendLine()
                    append(buildMemoryPrompt(memories = memories))
                }
                // 工具prompt
                tools.forEach { tool ->
                    appendLine()
                    append(tool.systemPrompt(model, messages))
                }
            }
            if (system.isNotBlank()) {
                add(UIMessage.system(prompt = system).copy(isSynthetic = true))
            }
            addAll(messages.limitContext(assistant.contextMessageLimit))
        }.transforms(
            transformers = transformers,
            context = context,
            model = model,
            assistant = assistant,
            settings = settings,
            conversationModeInjectionIds = conversationModeInjectionIds,
            conversationLorebookIds = conversationLorebookIds,
            processingStatus = processingStatus,
            workspaceCwd = workspaceCwd,
        )

        var messages: List<UIMessage> = messages
        val params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            tools = tools,
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(model.customBodies)
            },
            sessionId = (conversationId ?: Uuid.random()).toString(),
        )
        try {
            if (stream) {
                // 每次重试都从本次模型调用开始前的消息快照重新合并，避免将重试响应
                // 追加到已经展示的半截回复后面。预先创建助手消息可让所有尝试复用同一 ID，
                // ChatManager 因而会覆盖当前分支，而不是创建新的候选消息。
                //
                // 「复用末尾助手消息」只在它属于**本次生成自己**时才成立，即它还没被
                // `ChatManager.stampGroupTurn` 盖上 `role_id`。群聊里上一位角色的发言
                // 同样是末尾的 ASSISTANT 消息，但那条已经 `roleId != null`（已提交、
                // 已计入 `committed_role_ids`），复用它会把本角色的回复**并进上一位的
                // 那条消息**（同一个 UIMessage 多出一个 Text part），随后
                // `stampGroupTurn` 因为找不到 `roleId == null` 的助手消息而返回 null，
                // 该角色被误判为「本轮没有产出内容」并写成 role_failed。
                //
                // 真机证据（pipeline 三角色，mock seq=1 角色A、seq=2 角色B）：落库的那条
                // 助手消息 `role_id="a"` 却有 A、B 两段正文，usage 是 B 的；而
                // `group_runs` 是 `status=FAILED / committed=["a"] / skipped=["c"] /
                // reason=role_failed / error_message=本轮没有产出内容`。请求本身是发的，
                // 断的是产出归属，不是模型调用。
                //
                // 单聊不受影响：那里 `roleId` 恒为 null，复用条件与改动前逐字相同。
                val reusableTrailingAssistant = messages.lastOrNull()
                    ?.let { it.role == MessageRole.ASSISTANT && it.roleId == null }
                    ?: false
                val responseBaseMessages =
                    if (reusableTrailingAssistant) {
                        messages
                    } else {
                        messages + UIMessage(
                            role = MessageRole.ASSISTANT,
                            parts = emptyList(),
                            modelId = model.id,
                        )
                    }
                var retryCount = 0

                while (true) {
                    val streamChunkHandler = StreamChunkHandler(model)
                    var attemptMessages = responseBaseMessages
                    try {
                        providerImpl.streamText(
                            providerSetting = provider,
                            messages = internalMessages,
                            params = params
                        ).collect { chunk ->
                            try {
                                if (retryCount > 0) {
                                    processingStatus.value = null
                                }
                                attemptMessages = streamChunkHandler.handle(attemptMessages, chunk)
                                onUpdateMessages(attemptMessages)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                // 下游消息转换或 UI 更新失败不属于网络故障，不能重放模型请求。
                                throw StreamChunkHandlingException(error)
                            }
                        }
                        // 每步结束记录「末次请求 usage / 累计请求次数」：lastUsage 取末次不累加，
                        // usage 才是多步累计值（占用条看 lastUsage，统计行看 usage）
                        messages = attemptMessages.withStepStats(
                            stepBase = responseBaseMessages.lastOrNull(),
                            stepUsage = attemptMessages.lastOrNull()?.usage,
                        )
                        onUpdateMessages(messages)
                        runCatching {
                            usageRecorder.record(
                                source = UsageRecorder.SOURCE_CHAT,
                                usage = attemptMessages.lastOrNull()?.usage,
                                providerId = provider.id.toString(),
                                modelId = model.id.toString(),
                                modelName = model.modelId,
                                assistantId = assistant.id.toString(),
                                conversationId = conversationId?.toString(),
                            )
                        }
                        break
                    } catch (error: Throwable) {
                        if (error is StreamChunkHandlingException) {
                            throw error.cause ?: error
                        }
                        retryCount = awaitNetworkRetryOrThrow(
                            error = error,
                            retryCount = retryCount,
                            processingStatus = processingStatus,
                            enabled = settings.networkSetting.enableAutoRetry,
                        )
                    }
                }
            } else {
                val result = executeProviderRequestWithRetry(
                    processingStatus = processingStatus,
                    enabled = settings.networkSetting.enableAutoRetry,
                ) {
                    providerImpl.generateText(
                        providerSetting = provider,
                        messages = internalMessages,
                        params = params,
                    )
                }
                // 与流式分支同一个坑：`handleTextGenerationResult` 在末尾消息与
                // `incoming` 同为 ASSISTANT 时会**并进**末尾那条。群聊里末尾那条是上一位
                // 角色已提交的发言（`roleId != null`），并进去会让本角色的产出丢失归属，
                // 随后 `stampGroupTurn` 找不到 `roleId == null` 的消息而误判本轮无产出。
                // 所以这里先把「已提交的末尾助手消息」摘掉，落到追加新消息的分支上。
                val mergeBase =
                    if (messages.lastOrNull()?.let { it.role == MessageRole.ASSISTANT && it.roleId == null } == true) {
                        messages
                    } else if (messages.lastOrNull()?.role == MessageRole.ASSISTANT) {
                        messages.dropLast(1)
                    } else {
                        messages
                    }
                val stepBase = mergeBase.lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT }
                messages = mergeBase.handleTextGenerationResult(result = result, model = model)
                    .withStepStats(stepBase = stepBase, stepUsage = result.usage)
                runCatching {
                    usageRecorder.record(
                        source = UsageRecorder.SOURCE_CHAT,
                        usage = result.usage,
                        providerId = provider.id.toString(),
                        modelId = model.id.toString(),
                        modelName = model.modelId,
                        assistantId = assistant.id.toString(),
                        conversationId = conversationId?.toString(),
                    )
                }
                onUpdateMessages(messages)
            }
        } finally {
            processingStatus.value = null
        }
    }

    private suspend fun <T> executeProviderRequestWithRetry(
        processingStatus: MutableStateFlow<String?>,
        enabled: Boolean,
        block: suspend () -> T,
    ): T {
        var retryCount = 0
        while (true) {
            try {
                return block()
            } catch (error: Throwable) {
                retryCount = awaitNetworkRetryOrThrow(
                    error = error,
                    retryCount = retryCount,
                    processingStatus = processingStatus,
                    enabled = enabled,
                )
            }
        }
    }

    private suspend fun awaitNetworkRetryOrThrow(
        error: Throwable,
        retryCount: Int,
        processingStatus: MutableStateFlow<String?>,
        enabled: Boolean,
    ): Int {
        // 用户主动停止生成时，底层连接也可能以 IOException("canceled") 收尾；
        // 先检查协程状态，确保取消不会被当作网络波动重新拉起。
        currentCoroutineContext().ensureActive()
        if (!enabled || error !is IOException || retryCount >= MAX_PROVIDER_NETWORK_RETRIES) {
            throw error
        }

        val nextRetryCount = retryCount + 1
        val retryDelay = INITIAL_PROVIDER_RETRY_DELAY_MS shl retryCount
        processingStatus.value = context.getString(
            R.string.chat_generation_network_retrying,
            getNetworkErrorMessage(error),
            nextRetryCount,
            MAX_PROVIDER_NETWORK_RETRIES,
        )
        Log.w(
            TAG,
            "Provider connection failed, retrying in ${retryDelay}ms " +
                    "($nextRetryCount/$MAX_PROVIDER_NETWORK_RETRIES)",
            error,
        )
        delay(retryDelay)
        return nextRetryCount
    }

    private fun getNetworkErrorMessage(error: IOException): String {
        val messageRes = when (error) {
            is UnknownHostException -> R.string.chat_generation_network_unknown_host
            is SocketTimeoutException -> R.string.chat_generation_network_timeout
            is ConnectException, is NoRouteToHostException -> R.string.chat_generation_network_unreachable
            else -> R.string.chat_generation_network_disconnected
        }
        return context.getString(messageRes)
    }

    private fun maybeTruncateToolOutput(
        toolCallId: String,
        output: List<UIMessagePart>,
        hasShellAccess: Boolean,
    ): List<UIMessagePart> {
        val textParts = output.filterIsInstance<UIMessagePart.Text>()
        val nonTextParts = output.filter { it !is UIMessagePart.Text }
        val totalChars = textParts.sumOf { it.text.length }

        if (totalChars <= MAX_TOOL_OUTPUT_CHARS || !hasShellAccess) return output

        Log.i(TAG, "maybeTruncateToolOutput: truncating tool $toolCallId output ($totalChars chars)")

        val fullText = textParts.joinToString("\n") { it.text }
        val preview = fullText.take(TOOL_OUTPUT_PREVIEW_CHARS)

        val fileName = "${toolCallId}.txt"
        val outputDir = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() }
        File(outputDir, fileName).writeText(fullText)

        return listOf(
            UIMessagePart.Text(
                buildString {
                    appendLine("[Tool output truncated: $totalChars characters total]")
                    appendLine("Full output saved to: /tool_outputs/$fileName")
                    appendLine("Use shell to read: `cat /tool_outputs/$fileName`")
                    appendLine("Use shell to search: `grep \"pattern\" /tool_outputs/$fileName`")
                    appendLine()
                    append(preview)
                }
            )
        ) + nonTextParts
    }

}

/**
 * 文本模型无法消费 image_url，但本地工具卡仍需要知道附件文件。
 * 将路径作为生成上下文中的合成文本注入，不写回会话数据库，也不改变聊天气泡。
 */
private fun List<UIMessage>.withLocalImagePathHints(): List<UIMessage> {
    val index = indexOfLast { it.role == MessageRole.USER }
    if (index < 0) return this
    val message = this[index]
    val paths = message.parts.filterIsInstance<UIMessagePart.Image>().mapNotNull { image ->
        when {
            image.url.startsWith("file://") -> image.url.removePrefix("file://")
            image.url.startsWith("/") -> image.url
            else -> null
        }
    }
    if (paths.isEmpty()) return this
    val hint = "[本地图片附件，供图像工具调用，不需要向用户索要路径]\n" +
        paths.mapIndexed { i, path -> "image_${i + 1}=$path" }.joinToString("\n")
    val already = message.parts.filterIsInstance<UIMessagePart.Text>().any { it.text.contains("[本地图片附件") }
    if (already) return this
    return toMutableList().also { it[index] = message.copy(parts = message.parts + UIMessagePart.Text(hint)) }
}

/**
 * 给本步产生的助手消息补上用量统计字段。
 *
 * - [stepBase]：本步开始前那条助手消息（多轮工具调用复用它时带着上一步的统计）
 * - [stepUsage]：本步自己的 usage（末次请求），不累加
 * - usage 字段保持「多步累计」语义，与上游 AgentLoop.withStepStats 一致
 */
private fun List<UIMessage>.withStepStats(
    stepBase: UIMessage?,
    stepUsage: TokenUsage?,
): List<UIMessage> {
    val last = lastOrNull() ?: return this
    if (last.role != MessageRole.ASSISTANT) return this
    val updated = last.copy(
        usage = stepBase?.usage.sum(stepUsage) ?: last.usage,
        lastUsage = stepUsage ?: stepBase?.lastUsage ?: last.lastUsage,
        stepCount = (stepBase?.stepCount ?: 0) + 1,
    )
    return dropLast(1) + updated
}

private fun List<UIMessage>.withoutLocalImagePathHints(): List<UIMessage> = map { message ->
    message.copy(
        parts = message.parts.filterNot { part ->
            part is UIMessagePart.Text && part.text.startsWith("[本地图片附件，供图像工具调用")
        }
    )
}
