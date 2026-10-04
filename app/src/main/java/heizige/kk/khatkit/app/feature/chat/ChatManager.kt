package heizige.kk.khatkit.app.feature.chat

import android.app.Application
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.core.ReasoningLevel
import heizige.kk.khatkit.ai.core.Tool
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.ui.ToolApprovalState
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.ai.ui.canResumeToolExecution
import heizige.kk.khatkit.ai.ui.finishPendingTools
import heizige.kk.khatkit.ai.ui.isEmptyInputMessage
import heizige.kk.khatkit.common.android.Logging
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.GenerationChunk
import heizige.kk.khatkit.app.core.data.ai.ModelTaskType
import heizige.kk.khatkit.app.core.data.ai.TaskRoutes
import heizige.kk.khatkit.app.core.data.ai.GenerationLoop
import heizige.kk.khatkit.app.core.data.ai.TranslationHandler
import heizige.kk.khatkit.app.core.data.ai.mcp.McpManager
import heizige.kk.khatkit.app.core.data.ai.tools.ChatToolFactory
import heizige.kk.khatkit.app.core.data.ai.tools.GroupMemoryScope
import heizige.kk.khatkit.app.core.data.ai.tools.InvalidMcpServerNamesException
import heizige.kk.khatkit.app.core.data.ai.tools.shouldUseExternalWebSearch
import heizige.kk.khatkit.app.core.data.ai.transformers.Base64ImageToLocalFileTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.DocumentAsPromptTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.OcrTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.PlaceholderTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.PromptInjectionTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.RegexOutputTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.TemplateTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.ThinkTagTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.TimeReminderTransformer
import heizige.kk.khatkit.app.core.data.ai.tavern.TavernMacroTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.WorkspaceReminderTransformer
import heizige.kk.khatkit.app.core.data.event.AppEvent
import heizige.kk.khatkit.app.core.data.event.AppEventBus
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.data.datastore.findProvider
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
import heizige.kk.khatkit.app.core.data.datastore.getCurrentAssistant
import heizige.kk.khatkit.app.core.data.datastore.getCurrentChatModel
import heizige.kk.khatkit.app.core.data.db.dao.GroupRunDAO
import heizige.kk.khatkit.app.core.data.db.entity.GroupRunEntity
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.RoundBudget
import heizige.kk.khatkit.app.core.data.model.RoundPlan
import heizige.kk.khatkit.app.core.data.model.SpeakerStep
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.AssistantAffectScope
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.localFileUrls
import heizige.kk.khatkit.app.core.data.model.replaceRegexes
import heizige.kk.khatkit.app.core.data.model.toMessageNode
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FolderRepository
import heizige.kk.khatkit.app.core.data.repository.GroupMemorySpacePolicy
import heizige.kk.khatkit.app.core.data.repository.MemoryExtractor
import heizige.kk.khatkit.app.core.data.repository.MemoryRepository
import heizige.kk.khatkit.app.core.data.repository.WorkspaceRepository
import heizige.kk.khatkit.app.core.network.BadRequestException
import heizige.kk.khatkit.app.core.network.NotFoundException
import heizige.kk.khatkit.app.core.util.applyPlaceholders
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "ChatManager"

/**
 * 单个群聊角色的墙钟上限。超时后本轮按 `TIMEOUT` 收尾（只写运行日志，不写未生成的消息）。
 *
 * 只包住「一次角色发言」，不包住整轮：一轮要串行跑完所有角色，每位角色各自计时。
 */
private const val GROUP_ROUND_STEP_TIMEOUT_MS = 15 * 60 * 1000L

/**
 * 只取 [GroupRunDAO] 一个依赖的窄入口，避免为一个 DAO 去动 `core/di` 下别人的文件。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface GroupRunDaoEntryPoint {
    fun groupRunDAO(): GroupRunDAO
}

internal fun backgroundTextGenerationParams(
    model: Model,
    conversationId: Uuid,
    reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
): TextGenerationParams = TextGenerationParams(
    model = model,
    reasoningLevel = reasoningLevel,
    customHeaders = model.customHeaders,
    customBody = model.customBodies,
    sessionId = conversationId.toString(),
)

internal fun createForkConversation(
    source: Conversation,
    messageNodes: List<MessageNode>,
    existingTitles: Set<String> = emptySet(),
): Conversation = Conversation(
    id = Uuid.random(),
    assistantId = source.assistantId,
    title = generateSequence(1) { it + 1 }
        .map { "${source.title}($it)" }
        .first { it !in existingTitles },
    messageNodes = messageNodes,
    customSystemPrompt = source.customSystemPrompt,
    modeInjectionIds = source.modeInjectionIds,
    lorebookIds = source.lorebookIds,
    workspaceCwd = source.workspaceCwd,
    folderId = source.folderId,
)

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val solution: ChatErrorSolution? = null,
)

enum class ChatErrorSolution {
    CheckFastModelSettings,
}

class ChatManager(
    private val context: Application,
    private val appScope: AppScope,
    private val appEventBus: AppEventBus,
    private val settingsStore: SettingsRepository,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val memoryExtractor: MemoryExtractor,
    private val generationLoop: GenerationLoop,
    private val translationHandler: TranslationHandler,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val chatToolFactory: ChatToolFactory,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val workspaceRepository: WorkspaceRepository,
    private val folderRepository: FolderRepository,
    private val placeholderTransformer: PlaceholderTransformer,
    private val ocrTransformer: OcrTransformer,
    private val base64ImageToLocalFileTransformer: Base64ImageToLocalFileTransformer,
) {
    /**
     * 群聊轮次运行日志（`group_runs`）读写。
     *
     * `ChatManager` 是由 `core/di/AppHiltModule.provideChatService` **手工装配**的（不是 `@Inject`
     * 构造），而那个文件不在本包可改范围内，所以这里用 Hilt 的 `@EntryPoint` 取已有的单例 provider
     * （`RepositoryHiltModule.provideGroupRunDAO`）。若之后允许改 DI，应改回构造注入。
     */
    private val groupRunDAO: GroupRunDAO by lazy {
        EntryPointAccessors.fromApplication(context, GroupRunDaoEntryPoint::class.java).groupRunDAO()
    }
    /**
     * 群聊轮次的**非权威**进程内镜像，只回答一件事：「本进程此刻是否正在驱动某个群的一轮」。
     *
     * 权威状态全在 `group_runs`（run token / `committed_role_ids` / `spent_tokens` / `status`），
     * 每次判定都重新读库，所以进程被杀后这里自然清空而数据库仍能判定「这轮已跑过」并从
     * 失败角色续跑。C1 之前的 `PendingGroupTurn`（纯内存 `ArrayDeque` + 内存 `spent`）已删除：
     * 待发言角色每次都由 `plan - committedRoleIds` 现算，从根上消除「内存态与库不一致导致重复发言」。
     *
     * key 是 `conversationId`，**不同群互不阻塞**（没有全局锁，房间自带的 per-conversation 串行
     * 之外不再引入任何共享可变状态），也不共享任何 prompt buffer。
     */
    private val groupRunsInFlight = ConcurrentHashMap<Uuid, InFlightGroupRun>()

    private val inputTransformers = listOf(
        TavernMacroTransformer,
        TimeReminderTransformer,
        PromptInjectionTransformer,
        placeholderTransformer,
        DocumentAsPromptTransformer,
        ocrTransformer,
    )

    private val outputTransformers = listOf(
        ThinkTagTransformer,
        base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )

    // workspace 系统提示注入 (依赖 workspaceRepository, 故在类内构造)
    private val workspaceReminderTransformer = WorkspaceReminderTransformer(workspaceRepository)

    private val sessionManager = ConversationSessionManager(
        scope = appScope,
        createInitialConversation = { id ->
            Conversation.ofId(id, assistantId = settingsStore.settingsFlow.value.getCurrentAssistant().id)
        },
        onGenerationFinished = ::onSessionGenerationFinished,
    )

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) {
        if (error is CancellationException) return
        _errors.update {
            it + ChatError(title = title, error = error, conversationId = conversationId, solution = solution)
        }
    }

    fun dismissError(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAllErrors() {
        _errors.value = emptyList()
    }

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    fun cleanup() = runCatching { sessionManager.cleanup() }

    private fun onSessionGenerationFinished(session: ConversationSession, cause: Throwable?) {
        if (cause != null) session.messageQueue.pause()
        if (session.state.value.currentMessages.any { message ->
                message.parts.any { it is UIMessagePart.Tool && it.isPending }
            }) {
            session.messageQueue.failReplyWaiters(context.getString(R.string.chat_page_voice_tool_approval))
        }
        appScope.launch { dispatchNextQueuedMessage(session.id) }
    }

    // 保留 UI/Web 的入口，生命周期和状态查询统一交给 SessionManager。
    fun addConversationReference(conversationId: Uuid) {
        sessionManager.acquire(conversationId)
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessionManager.release(conversationId)
    }

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> =
        sessionManager.getConversationFlow(conversationId)

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> =
        sessionManager.getGenerationJobStateFlow(conversationId)

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> =
        sessionManager.getProcessingStatusFlow(conversationId)

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> = sessionManager.getConversationJobs()

    private fun launchGenerationJob(
        conversationId: Uuid,
        keepAliveInBackground: Boolean = true,
        block: suspend () -> Unit,
    ): Job {
        if (!keepAliveInBackground) return appScope.launch(start = CoroutineStart.LAZY) { block() }

        return appScope.launch(start = CoroutineStart.LAZY) {
            val generationId = Uuid.random()
            val foregroundStarted = ChatGenerationForegroundService.acquire(
                context = context,
                generationId = generationId,
                conversationId = conversationId,
            )
            try {
                block()
            } finally {
                if (foregroundStarted) {
                    ChatGenerationForegroundService.release(context, generationId)
                }
            }
        }
    }

    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid) {
        sessionManager.withSession(conversationId) { session ->
            session.initialize {
                conversationRepo.getConversationById(conversationId) ?: run {
                    // 新建对话, 并添加预设消息
                    val currentSettings = settingsStore.settingsFlowRaw.first()
                    val assistant = currentSettings.getCurrentAssistant()
                    Conversation.ofId(
                        id = conversationId,
                        assistantId = assistant.id,
                        messages = heizige.kk.khatkit.app.core.data.ai.tavern.tavernSeedNodes(assistant),
                        newConversation = true,
                    )
                }
            }
            settingsStore.updateAssistant(session.state.value.assistantId)
        }
    }

    // ---- 发送消息 ----

    fun getMessageQueueFlow(conversationId: Uuid): StateFlow<MessageQueueState> =
        sessionManager.getMessageQueueFlow(conversationId)

    fun removeQueuedMessage(conversationId: Uuid, messageId: Uuid) {
        sessionManager.get(conversationId)?.messageQueue?.remove(messageId)?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    fun beginEditQueuedMessage(conversationId: Uuid, messageId: Uuid): QueuedMessage? =
        sessionManager.get(conversationId)?.messageQueue?.beginEdit(messageId)

    fun finishEditQueuedMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>? = null
    ) {
        sessionManager.get(conversationId)?.messageQueue?.finishEdit(messageId, parts)
            ?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    private fun cleanupQueuedAttachments(previous: QueuedMessage) {
        val candidates = previous.parts.localFileUrls()
        if (candidates.isEmpty()) return
        appScope.launch {
            try {
                // 未打开的会话及未选中的分支也可能引用同一附件。
                val persistedReferences =
                    candidates.filter { conversationRepo.hasFileReference(it) }.toSet()
                // 数据库查询挂起期间队列可能已推进，删除前重新读取内存引用。
                val currentSessions = sessionManager.snapshot()
                val unusedFiles = unreferencedQueuedAttachmentUrls(
                    previous = previous,
                    conversations = currentSessions.map { it.state.value },
                    pendingMessages = currentSessions.flatMap {
                        it.messageQueue.state.value.messages + listOfNotNull(it.submittingMessage)
                    },
                ) - persistedReferences
                if (unusedFiles.isNotEmpty()) {
                    filesManager.deleteChatFiles(unusedFiles.map { it.toUri() })
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 无法确认引用时保留文件，避免误删。
                Log.w(TAG, "Failed to clean queued attachments", e)
            }
        }
    }

    fun resumeMessageQueue(conversationId: Uuid) {
        sessionManager.get(conversationId)?.messageQueue?.resume()
        dispatchNextQueuedMessage(conversationId)
    }

    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return
        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(content, answer)
            dispatchNextQueuedMessage(conversationId)
        }
    }

    /** Enqueue immediately; the result belongs to this item even after edits or later turns. */
    fun enqueueVoiceMessage(conversationId: Uuid, text: String): Deferred<String?> {
        val session = sessionManager.getOrCreate(conversationId)
        val reply = CompletableDeferred<String?>()
        synchronized(session) {
            check(text.isNotBlank()) { context.getString(R.string.chat_page_voice_empty) }
            check(!session.messageQueue.state.value.paused || session.messageQueue.state.value.messages.isEmpty()) {
                context.getString(R.string.chat_page_voice_resume_queue)
            }
            check(session.state.value.currentMessages.none { message ->
                message.parts.any { it is UIMessagePart.Tool && it.isPending }
            }) { context.getString(R.string.chat_page_voice_tools_before_resume) }
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(listOf(UIMessagePart.Text(text)), reply = reply)
            dispatchNextQueuedMessage(conversationId)
        }
        return reply
    }

    private fun dispatchNextQueuedMessage(conversationId: Uuid): Job? {
        val session = sessionManager.get(conversationId) ?: return null
        synchronized(session) {
            // A pending tool approval is still part of the current turn.
            if (session.getJob() != null || session.state.value.currentMessages.any { message ->
                    message.parts.any { it is UIMessagePart.Tool && it.isPending }
                }) return null
            val next = session.messageQueue.takeNext() ?: return null
            session.submittingMessage = next
            return sendQueuedMessage(session, next)
        }
    }

    private fun sendQueuedMessage(session: ConversationSession, queued: QueuedMessage): Job {
        val conversationId = session.id
        val content = queued.parts
        val answer = queued.answer
        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = answer,
        ) {
            try {
                finishInterruptedPendingTools(conversationId)

                val currentConversation = session.state.value
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(currentConversation.assistantId)
                    ?: settings.getCurrentAssistant()
                val processedContent = preprocessUserInputParts(content, assistant)

                // 添加消息到列表
                val mentionText = processedContent.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                val mentions = currentConversation.groupConfig
                    ?.let { GroupChat.parseMentions(mentionText, it.roles) }
                    .orEmpty()
                if (isGroupConversation(currentConversation)) abandonDanglingGroupRuns(conversationId)
                val newConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + UIMessage(
                        role = MessageRole.USER,
                        parts = processedContent,
                        mentionRoleIds = mentions,
                    ).toMessageNode(),
                )
                saveConversation(conversationId, newConversation)
                session.submittingMessage = null

                // 开始补全
                if (answer) {
                    handleMessageComplete(conversationId)
                }

                queued.reply?.completeWith(runCatching {
                    val messages = session.state.value.currentMessages
                    check(!session.messageQueue.state.value.paused) { context.getString(R.string.chat_page_voice_generation_failed) }
                    check(messages.none { message -> message.parts.any { it is UIMessagePart.Tool && it.isPending } }) {
                        context.getString(R.string.chat_page_voice_tool_approval)
                    }
                    val previousIds = currentConversation.currentMessages.map { it.id }.toSet()
                    messages.filter { it.id !in previousIds && it.role == MessageRole.ASSISTANT }
                        .joinToString("\n") { it.toText() }
                })
                // Voice owns playback, including when its observer has already left the page.
                // The ordinary autoplay collector must not read a late voice reply again.
                if (queued.reply == null) _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                queued.reply?.completeExceptionally(e)
                e.printStackTrace()
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null) queued.reply?.completeExceptionally(cause)
            synchronized(session) {
                if (session.submittingMessage?.id == queued.id) session.submittingMessage = null
            }
        }
        session.setJob(job)
        return job
    }

    private fun preprocessUserInputParts(parts: List<UIMessagePart>, assistant: Assistant): List<UIMessagePart> {
        return parts.map { part ->
            when (part) {
                is UIMessagePart.Image -> {
                    // 旧会话/第三方导入可能仍保存 content://，模型编码器只能读取本地文件；
                    // 发送前复制到应用私有上传目录，避免图片在 UI 可见但请求体为空。
                    val url = part.url
                    if (url.startsWith("content://")) {
                        filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()
                            ?.let { part.copy(url = it.toString()) }
                            ?: part
                    } else part
                }
                is UIMessagePart.Text -> {
                    part.copy(
                        text = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.USER,
                            visual = false
                        )
                    )
                }

                else -> part
            }
        }
    }

    // ---- 重新生成消息 ----

    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) = synchronized(sessionManager.getOrCreate(conversationId)) {
        val session = sessionManager.getOrCreate(conversationId)
        val previousJob = session.getJob()

        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = message.role == MessageRole.USER || regenerateAssistantMsg,
        ) {
            try {
                previousJob?.join()
                val conversation = session.state.value

                if (message.role == MessageRole.USER) {
                    // 如果是用户消息，则截止到当前消息
                    val node = conversation.getMessageNodeByMessage(message)
                    val indexAt = conversation.messageNodes.indexOf(node)
                    val newConversation = conversation.copy(
                        messageNodes = conversation.messageNodes.subList(0, indexAt + 1)
                    )
                    saveConversation(conversationId, newConversation)
                    handleMessageComplete(conversationId)
                } else {
                    if (regenerateAssistantMsg) {
                        val node = conversation.getMessageNodeByMessage(message)
                        val nodeIndex = conversation.messageNodes.indexOf(node)
                        handleMessageComplete(conversationId, messageRange = 0..<nodeIndex)
                    } else {
                        saveConversation(conversationId, conversation)
                    }
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_regenerate_message))
            }
        }

        session.setJob(job)
    }

    // ---- 处理工具调用审批 ----

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    ) = synchronized(sessionManager.getOrCreate(conversationId)) {
        val session = sessionManager.getOrCreate(conversationId)
        val previousJob = session.getJob()

        val hasOtherPendingTools = session.state.value.messageNodes.any { node ->
            node.currentMessage.parts.any { part ->
                part is UIMessagePart.Tool && part.isPending && part.toolCallId != toolCallId
            }
        }

        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = !hasOtherPendingTools,
        ) {
            try {
                afterPreviousGeneration(previousJob) {
                    val conversation = session.state.value
                    // Ignore double taps and stale approvals for completed or inactive tools.
                    if (conversation.currentMessages.none { message ->
                            message.getTools().any { it.toolCallId == toolCallId && it.isPending }
                        }) return@afterPreviousGeneration
                    val newApprovalState = when {
                        answer != null -> ToolApprovalState.Answered(answer)
                        approved -> ToolApprovalState.Approved
                        else -> ToolApprovalState.Denied(reason)
                    }

                    // Update the tool approval state
                    val updatedNodes = conversation.messageNodes.map { node ->
                        node.copy(
                            messages = node.messages.map { msg ->
                                msg.copy(
                                    parts = msg.parts.map { part ->
                                        when {
                                            part is UIMessagePart.Tool && part.toolCallId == toolCallId -> {
                                                part.copy(approvalState = newApprovalState)
                                            }

                                            else -> part
                                        }
                                    }
                                )
                            }
                        )
                    }
                    val updatedConversation = conversation.copy(messageNodes = updatedNodes)
                    saveConversation(conversationId, updatedConversation)

                    // Check if there are still pending tools
                    val hasPendingTools = updatedNodes.any { node ->
                        node.currentMessage.parts.any { part ->
                            part is UIMessagePart.Tool && part.isPending
                        }
                    }

                    // Only continue generation when all pending tools are handled
                    if (!hasPendingTools) {
                        handleMessageComplete(conversationId)
                    }

                    _generationDoneFlow.emit(conversationId)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_tool_approval))
            }
        }

        session.setJob(job, cancelPrevious = false)
    }

    // ---- 处理消息补全 ----

    private suspend fun handleMessageComplete(
        conversationId: Uuid,
        messageRange: ClosedRange<Int>? = null
    ) {
        val settings = settingsStore.settingsFlow.first()
        val initialConversation = getConversationFlow(conversationId).value
        var assistant = settings.getAssistantById(initialConversation.assistantId)
            ?: settings.getCurrentAssistant()
        var groupStep: SpeakerStep? = null
        var model = TaskRoutes.resolve(settings, ModelTaskType.CHAT, assistant.chatModelId)

        val senderName = if (assistant.useAssistantAvatar) {
            assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
        } else {
            model.displayName
        }
        val useExternalWebSearch = shouldUseExternalWebSearch(assistant, model)

        runCatching {

            // reset suggestions
            updateConversation(conversationId, initialConversation.copy(chatSuggestions = emptyList()))

            // memory tool
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (useExternalWebSearch || mcpManager.getAllAvailableTools().isNotEmpty()) {
                    addError(
                        IllegalStateException(context.getString(R.string.tools_warning)),
                        conversationId,
                        title = context.getString(R.string.error_title_tool_unavailable)
                    )
                }
            }

            // check invalid messages
            checkInvalidMessages(conversationId)
            val conversation = getConversationFlow(conversationId).value
            val groupConfig = conversation.groupConfig
            val groupEntry = if (groupConfig != null) takeGroupTurn(conversation, groupConfig) else null
            // 群聊但本轮没有可执行的发言者（已完成 / 被并发拒绝 / 预算已停 / 全员已提交）：
            // 绝不能退化成「不过滤的普通生成」，否则当前视角会收到别人的发言。
            if (groupEntry is GroupTurnEntry.Idle) {
                Logging.log(TAG, "handleMessageComplete: group round idle, skip generation")
                return
            }
            groupStep = (groupEntry as? GroupTurnEntry.Speak)?.step
            val step = groupStep
            if (step != null && groupConfig != null) {
                settings.getAssistantById(Uuid.parse(step.role.assistantId))?.let { assistant = it }
                model = TaskRoutes.resolve(settings, ModelTaskType.CHAT, assistant.chatModelId)
                // C1-M：只为本轮发言角色建空间（契约「首次发言懒创建」），不再一次建全部
                // 角色——没开口的角色不该在库里留下空间。未发言角色的空间由其第一次
                // 发言时的同一处代码创建。
                GroupMemorySpacePolicy.spacesToProvision(
                    conversationId = conversationId.toString(),
                    speakingRoleId = step.role.id,
                ).forEach { spaceId ->
                    memoryRepository.ensureSpace(spaceId, step.role.name)
                }
            }

            // 契约「工具调用、检索与记忆注入均使用同一 viewer 过滤结果」：
            // 这一份可见集合是 prompt 组装、工具 systemPrompt、记忆检索 query 的**唯一**来源。
            val viewerMessages = if (step != null && groupConfig != null) {
                GroupTurnCoordinator.viewerMessages(groupConfig, conversation.currentMessages, step)
            } else {
                null
            }

            val tools = try {
                chatToolFactory.createTools(
                    settings = settings,
                    assistant = assistant,
                    model = model,
                    workspaceCwd = conversation.workspaceCwd,
                    // C1-M：群聊时记忆工具绑定本轮发言角色的群空间
                    // `group:<conv>:role:<role>`，不回退到助手/全局空间。
                    groupMemory = step?.let { GroupMemoryScope(conversationId.toString(), it.role.id) },
                )
            } catch (error: InvalidMcpServerNamesException) {
                sessionManager.get(conversationId)?.messageQueue?.pause()
                addError(
                    error = IllegalStateException(
                        context.getString(
                            R.string.error_mcp_invalid_server_name,
                            error.names.joinToString(", "),
                        )
                    ),
                    conversationId = conversationId,
                )
                return
            }.let { created ->
                // 工具的 systemPrompt 拿到的是「本视角可见消息」而不是完整历史：契约禁止
                // UI 层隐藏但仍发送，而 GenerationLoop 的 tool.systemPrompt(model, messages)
                // 用的是未过滤的形参，所以过滤必须由这里（生成管线的调用点）注入。
                if (step != null && groupConfig != null) {
                    viewerScopedTools(created, groupConfig, step)
                } else {
                    created
                }
            }

            // 记忆检索：query 只取 viewer 可见的消息，检索结果再按 roleId 收紧一次。
            val memoryScope = when {
                step != null -> GroupChat.memorySpaceId(conversationId.toString(), step.role.id)
                assistant.useGlobalMemory -> MemoryRepository.GLOBAL_MEMORY_ID
                else -> assistant.id.toString()
            }
            val memoryQuery = viewerMessages?.let(GroupTurnCoordinator::memoryQuery)
                ?: conversation.currentMessages
                    .takeLast(6)
                    .filter { it.role == MessageRole.USER }
                    .joinToString("\n") { it.toText() }
                    .takeLast(4_000)
            val memories = memoryRepository.searchMemories(
                assistantId = memoryScope,
                query = memoryQuery,
                limit = 8,
            ).let { found ->
                if (step != null && viewerMessages != null) {
                    GroupTurnCoordinator.memoriesForViewer(
                        viewerRoleId = step.role.id,
                        viewerMessageIds = viewerMessages.mapTo(mutableSetOf()) { it.id.toString() },
                        memories = found,
                    )
                } else {
                    found
                }
            }

            // start generating
            val session = sessionManager.getOrCreate(conversationId)
            val generationFlow = generationLoop.generateText(
                settings = settings,
                model = model,
                processingStatus = session.processingStatus,
                messages = conversation.currentMessages.let {
                    if (messageRange != null) {
                        it.subList(messageRange.start, messageRange.endInclusive + 1)
                    } else {
                        it
                    }
                },
                assistant = assistant,
                conversationId = conversationId,
                conversationSystemPrompt = conversation.customSystemPrompt,
                conversationModeInjectionIds = conversation.modeInjectionIds,
                conversationLorebookIds = conversation.lorebookIds,
                workspaceCwd = conversation.workspaceCwd,
                memories = memories,
                inputTransformers = buildList {
                    val groupConfig = conversation.groupConfig
                    if (step != null && groupConfig != null) {
                        add(
                            GroupPerspectiveTransformer(
                                config = groupConfig,
                                viewerId = step.role.id,
                                predecessorId = step.predecessorId,
                                chairRound = step.chairRound,
                            ),
                        )
                    }
                    addAll(inputTransformers)
                    add(templateTransformer)
                    add(workspaceReminderTransformer)
                },
                outputTransformers = outputTransformers,
                tools = tools,
            ).onCompletion {
                // 可能被取消了，或者意外结束，兜底更新
                val updatedConversation = session.finishGeneration { conversation ->
                    saveConversation(conversationId, conversation)
                }

                // A2 自动记忆抽取
                if (assistant.enableMemory && assistant.autoExtractMemory) {
                    val speakingRole = groupStep?.role
                    val memSpace = when {
                        speakingRole != null -> GroupChat.memorySpaceId(conversationId.toString(), speakingRole.id)
                        assistant.useGlobalMemory -> MemoryRepository.GLOBAL_MEMORY_ID
                        else -> assistant.id.toString()
                    }
                    // C1-M：抽取的输入也必须是 viewer 可见集合——直接喂完整历史会把同群
                    // 其他角色的发言抽进本角色空间，与检索/工具侧同一口径。这里刻意不传
                    // `predecessorId`：pipeline 里上一位的输出属于别人的发言，由他自己
                    // 写进自己的空间，记忆才真正一角色一份。
                    val extractScope = if (speakingRole != null && groupConfig != null) {
                        GroupChat.buildContext(speakingRole.id, updatedConversation.currentMessages, groupConfig)
                    } else {
                        updatedConversation.currentMessages
                    }
                    appScope.launch {
                        runCatching {
                            memoryExtractor.extractFromTurn(
                                spaceId = memSpace,
                                messages = extractScope.takeLast(MemoryExtractor.MAX_EXTRACT_WINDOW),
                                roleId = speakingRole?.id,
                                settings = settingsStore.settingsFlow.first(),
                            )
                        }
                    }
                }

                // 生成结束：取消 Live Update 通知，后台时发送完成通知
                appEventBus.emit(
                    AppEvent.ChatGenerationEnded(
                        conversationId = conversationId,
                        senderName = senderName,
                        contentPreview = updatedConversation.currentMessages.lastOrNull()
                            ?.toText()?.take(50)?.trim() ?: "",
                    )
                )
            }
            val consume: suspend () -> Unit = {
                generationFlow.collect { chunk ->
                    when (chunk) {
                        is GenerationChunk.Messages -> {
                            val updatedConversation = getConversationFlow(conversationId).value
                                .updateCurrentMessages(chunk.messages)
                            updateConversation(conversationId, updatedConversation)

                            // 通知等边缘副作用由 ChatNotificationManager 消费；
                            // tryEmit 不挂起，事件丢失只影响单次通知更新，不能反压生成链
                            chunk.messages.lastOrNull()?.let { lastMessage ->
                                appEventBus.tryEmit(
                                    AppEvent.ChatGenerationUpdate(conversationId, lastMessage, senderName)
                                )
                            }
                        }
                    }
                }
            }
            if (step != null) {
                // 群聊角色发言有墙钟上限：超时抛 TimeoutCancellationException，
                // 由下面的 onFailure 落成 STATUS_TIMEOUT，轮次不会无限悬挂。
                withTimeout(GROUP_ROUND_STEP_TIMEOUT_MS) { consume() }
            } else {
                consume()
            }
        }.onFailure {
            // 兜底取消 Live Update 通知（生成开始前失败时 onCompletion 不会执行）
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null))
            val step = groupStep
            if (step != null && it is TimeoutCancellationException) {
                // 超时不是用户取消：外层协程仍然存活，可以安全地把运行日志写成 TIMEOUT。
                failGroupTurn(conversationId, step, errorDetailOf(it), timedOut = true)
            } else if (step != null && it !is CancellationException) {
                // 单角色失败：写错误节点 + FAILED/role_failed，轮次保持可续跑。
                failGroupTurn(conversationId, step, errorDetailOf(it), timedOut = false)
            }
            if (it is CancellationException) throw it
            sessionManager.get(conversationId)?.messageQueue?.pause()

            it.printStackTrace()
            addError(it, conversationId, title = context.getString(R.string.error_title_generation))
            Logging.log(TAG, "handleMessageComplete: $it")
            Logging.log(TAG, it.stackTraceToString())
        }.onSuccess {
            val step = groupStep
            if (step == null) {
                val finalConversation = getConversationFlow(conversationId).value
                sessionManager.launchWithSession(conversationId) {
                    generateTitle(conversationId, finalConversation)
                }
                return@onSuccess
            }
            val conversation = getConversationFlow(conversationId).value
            val config = conversation.groupConfig ?: return@onSuccess
            val plan = GroupTurnCoordinator.roundPlanFor(config, conversation.currentMessages)
                ?: return@onSuccess
            when (val advance = commitGroupTurn(conversationId, step, plan, config)) {
                null -> groupRunsInFlight.remove(conversationId)

                is GroupTurnCoordinator.Advance.More -> {
                    // 还有角色没发言：续跑下一位。已提交角色在库里，pendingSpeakers 会跳过。
                    handleMessageComplete(conversationId)
                    return@onSuccess
                }

                is GroupTurnCoordinator.Advance.BudgetStopped -> {
                    // 预算用尽：spent/limit/skippedRoleIds/reason 四项已在运行日志里。
                    groupRunsInFlight.remove(conversationId)
                }

                is GroupTurnCoordinator.Advance.Finished -> {
                    completeGroupRound(conversationId, config, plan, advance.state)
                }
            }
            val finalConversation = getConversationFlow(conversationId).value
            sessionManager.launchWithSession(conversationId) {
                generateTitle(conversationId, finalConversation)
            }
        }
    }

    // ---- 检查无效消息 ----

    private fun checkInvalidMessages(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        var messagesNodes = conversation.messageNodes

        // 移除无效 tool (未执行的 Tool)
        messagesNodes = messagesNodes.mapIndexed { _, node ->
            // Check for Tool type with non-executed tools
            val hasPendingTools = node.currentMessage.getTools().any { !it.isExecuted }

            if (hasPendingTools) {
                // Keep messages that are ready to resume, such as approved/denied/answered tools.
                val hasResumableTool = node.currentMessage.getTools().any {
                    !it.isExecuted && it.approvalState.canResumeToolExecution()
                }
                if (hasResumableTool) {
                    return@mapIndexed node
                }

                // If all tools are executed, it's valid
                val allToolsExecuted = node.currentMessage.getTools().all { it.isExecuted }
                if (allToolsExecuted && node.currentMessage.getTools().isNotEmpty()) {
                    return@mapIndexed node
                }

                // Remove messages that still have unresolved tool approvals.
                return@mapIndexed node.copy(
                    messages = node.messages.filter { it.id != node.currentMessage.id },
                    selectIndex = node.selectIndex - 1
                )
            }
            node
        }

        // 更新index
        messagesNodes = messagesNodes.map { node ->
            if (node.messages.isNotEmpty() && node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0)
            } else {
                node
            }
        }

        // 移除无效消息
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

        updateConversation(conversationId, conversation.copy(messageNodes = messagesNodes))
    }

    private fun cancelToolByUser(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        return tool.copy(
            output = listOf(
                UIMessagePart.Text(
                    """{"status":"cancelled","error":"Generation cancelled by user before tool execution completed."}"""
                )
            )
        )
    }

    private suspend fun finishInterruptedPendingTools(conversationId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val lastNode = currentConversation.messageNodes.lastOrNull() ?: return
        val lastMessage = lastNode.currentMessage
        val updatedMessage = lastMessage.finishPendingTools(::cancelToolByUser)
        if (updatedMessage == lastMessage) {
            return
        }

        val updatedConversation = currentConversation.copy(
            messageNodes = currentConversation.messageNodes.dropLast(1) + lastNode.copy(
                messages = lastNode.messages.map { message ->
                    if (message.id == lastMessage.id) updatedMessage else message
                }
            )
        )
        saveConversation(conversationId, updatedConversation)
    }

    // ---- 生成标题 ----

    suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val shouldGenerate = when {
            force -> true
            conversation.title.isBlank() -> true
            else -> false
        }
        if (!shouldGenerate) return@withContext

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = runCatching { TaskRoutes.resolve(settings, ModelTaskType.TITLE) }.getOrNull()
                ?: return@runCatching
            val provider = model.findProvider(settings.providers) ?: return@runCatching

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        prompt = settings.titlePrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(4).joinToString("\n\n") { it.summaryAsText(maxLength = 500) })
                    ),
                ),
                params = backgroundTextGenerationParams(model, conversationId, settings.fastModelReasoningLevel),
            )

            // 生成完，conversation可能不是最新了，因此需要重新获取
            conversationRepo.getConversationById(conversation.id)?.let {
                saveConversation(
                    conversationId,
                    it.copy(title = result.message.toText().trim())
                )
            }
        }.onFailure {
            it.printStackTrace()
            addError(
                error = it,
                conversationId = conversationId,
                title = context.getString(R.string.error_title_generate_title),
                solution = ChatErrorSolution.CheckFastModelSettings,
            )
        }
    }

    // ---- 压缩对话历史 ----

    suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 32
    ): Result<Unit> = runCatching {
        val settings = settingsStore.settingsFlow.first()
        val model = TaskRoutes.resolve(settings, ModelTaskType.SUMMARY)
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException("Provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        val maxMessagesPerChunk = 256
        val allMessages = conversation.currentMessages

        // Split messages into those to compress and those to keep
        val messagesToCompress: List<UIMessage>
        val messagesToKeep: List<UIMessage>

        if (keepRecentMessages > 0 && allMessages.size > keepRecentMessages) {
            messagesToCompress = allMessages.dropLast(keepRecentMessages)
            messagesToKeep = allMessages.takeLast(keepRecentMessages)
        } else if (keepRecentMessages > 0) {
            // Not enough messages to compress while keeping recent ones
            throw IllegalStateException(context.getString(R.string.chat_page_compress_not_enough_messages))
        } else {
            messagesToCompress = allMessages
            messagesToKeep = emptyList()
        }

        fun splitMessages(messages: List<UIMessage>): List<List<UIMessage>> {
            if (messages.size <= maxMessagesPerChunk) return listOf(messages)
            val mid = messages.size / 2
            val left = splitMessages(messages.subList(0, mid))
            val right = splitMessages(messages.subList(mid, messages.size))
            return left + right
        }

        suspend fun compressMessages(messages: List<UIMessage>): String {
            val contentToCompress = messages.joinToString("\n\n") { it.summaryAsText(maxLength = 2000) }
            val prompt = settings.compressPrompt.applyPlaceholders(
                "content" to contentToCompress,
                "target_tokens" to targetTokens.toString(),
                "additional_context" to if (additionalPrompt.isNotBlank()) {
                    "Additional instructions from user: $additionalPrompt"
                } else "",
                "locale" to Locale.getDefault().displayName
            )

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = backgroundTextGenerationParams(model, conversationId),
            )

            return result.message.toText().trim().takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Failed to generate compressed summary")
        }

        val compressedSummaries = coroutineScope {
            splitMessages(messagesToCompress)
                .map { chunk -> async { compressMessages(chunk) } }
                .awaitAll()
        }

        // Create new conversation with compressed history as multiple user messages + kept messages
        val newMessageNodes = buildList {
            compressedSummaries.forEach { summary ->
                add(UIMessage.user(summary).toMessageNode())
            }
            addAll(messagesToKeep.map { it.toMessageNode() })
        }
        val newConversation = conversation.copy(
            messageNodes = newMessageNodes,
            chatSuggestions = emptyList(),
        )

        saveConversation(conversationId, newConversation)
    }

    // ---- 对话状态更新 ----

    private fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        val session = sessionManager.getOrCreate(conversationId)
        checkFilesDelete(conversation, session.state.value)
        session.updateConversation(conversation)
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        val current = getConversationFlow(conversationId).value
        updateConversation(conversationId, update(current))
    }

    private suspend fun updateConversationMetadata(
        conversationId: Uuid,
        update: (Conversation) -> Conversation,
        persist: suspend (Conversation) -> Unit,
    ) {
        sessionManager.withSession(conversationId) { session ->
            session.initialize {
                conversationRepo.getConversationById(conversationId)
                    ?: throw NotFoundException("Conversation not found")
            }
            session.updateMetadata(update, persist)
        }
    }

    suspend fun toggleConversationPinned(conversationId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            update = { it.copy(isPinned = !it.isPinned) },
            persist = { conversationRepo.updatePinStatus(conversationId, it.isPinned) },
        )
    }

    suspend fun moveConversationToAssistant(conversationId: Uuid, assistantId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            // 文件夹属于助手，移动后清除原助手的文件夹归属。
            update = { it.copy(assistantId = assistantId, folderId = null) },
            persist = { conversationRepo.updateConversationAssistant(conversationId, it.assistantId) },
        )
    }

    /**
     * 移动会话到文件夹（folderId 为 null 表示移出到未归类）。
     *
     * 若该会话当前有活跃 session（正在查看或后台生成），先同步内存态再落库：
     * 否则仅改数据库 folder_id，而内存里那份 Conversation 仍是旧 folderId，
     * 后续任意 saveConversation(id, state.value) 会用整对象把 folder_id 覆盖回旧值，导致移动丢失。
     * 先改内存可确保这段窗口内的整对象保存也带上新 folderId。
     */
    suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        if (sessionManager.get(conversationId) != null) {
            updateConversationState(conversationId) { it.copy(folderId = folderId) }
        }
        conversationRepo.updateConversationFolderId(conversationId, folderId)
    }

    /**
     * 文件夹内是否存在正在生成回复的会话。
     * 仅活跃 session 可能在生成；内存态 folderId 为权威（移动会先同步内存态）。
     */
    fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean {
        return sessionManager.snapshot().any { it.isGenerating && it.state.value.folderId == folderId }
    }

    /**
     * 删除文件夹（folder_id 归属会被清空，会话本身保留）。
     *
     * 先把内存中归属该文件夹的活跃 session folderId 置空，再删库：
     * 否则 clearFolder 只改了数据库，而活跃 session 内存态仍指向该文件夹，
     * 后续整对象保存会写回一个已被删除的 folder_id，导致会话在列表中悬空。
     */
    suspend fun deleteFolder(folderId: Uuid) {
        sessionManager.snapshot()
            .filter { it.state.value.folderId == folderId }
            .forEach { updateConversationState(it.id) { c -> c.copy(folderId = null) } }
        folderRepository.deleteFolder(folderId)
    }

    private fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val session = sessionManager.get(newConversation.id)
        val queuedFiles = (session?.messageQueue?.state?.value?.messages.orEmpty() +
                listOfNotNull(session?.submittingMessage))
            .flatMap { it.parts }.localFileUrls().map { it.toUri() }
        val newFiles = newConversation.files + queuedFiles
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file ->
            newFiles.none { it == file }
        }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        val exists = conversationRepo.existsConversationById(conversation.id)
        if (!exists && conversation.title.isBlank() && conversation.messageNodes.isEmpty()) {
            return // 新会话且为空时不保存
        }

        val updatedConversation = conversation.copy()
        updateConversation(conversationId, updatedConversation)

        if (!exists) {
            conversationRepo.insertConversation(updatedConversation)
        } else {
            conversationRepo.updateConversation(updatedConversation)
        }

        // 删除消息或切换分支也可能解除工具审批阻塞，保存成功后重新检查队列。
        // 调度器仍会检查当前生成任务、待审批工具、暂停状态及编辑占位。
        dispatchNextQueuedMessage(conversationId)
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()

                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()

                if (messageText.isBlank()) return@launch

                // Set loading state for translation
                val loadingText = context.getString(R.string.translating)
                updateTranslationField(conversationId, message.id, loadingText)

                translationHandler.translateText(
                    settings = settings,
                    sourceText = messageText,
                    targetLanguage = targetLanguage
                ) { translatedText ->
                    // Update translation field in real-time
                    updateTranslationField(conversationId, message.id, translatedText)
                }.collect { /* Final translation already handled in onStreamUpdate */ }

                // Save the conversation after translation is complete
                saveConversation(conversationId, getConversationFlow(conversationId).value)
            } catch (e: Exception) {
                // Clear translation field on error
                clearTranslationField(conversationId, message.id)
                addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
            }
        }
    }

    private fun updateTranslationField(
        conversationId: Uuid,
        messageId: Uuid,
        translationText: String
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = translationText)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // ---- 消息操作 ----

    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return

        val currentConversation = getConversationFlow(conversationId).value
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getAssistantById(currentConversation.assistantId)
            ?: settings.getCurrentAssistant()
        val processedParts = preprocessUserInputParts(parts, assistant)
        var edited = false

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (!node.messages.any { it.id == messageId }) {
                return@map node
            }
            edited = true

            node.copy(
                messages = node.messages + UIMessage(
                    role = node.role,
                    parts = processedParts,
                ),
                selectIndex = node.messages.size
            )
        }

        if (!edited) return

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            throw NotFoundException("Message not found")
        }

        val copiedNodes = currentConversation.messageNodes
            .subList(0, targetNodeIndex + 1)
            .map { node ->
                node.copy(
                    id = Uuid.random(),
                    messages = node.messages.map { message ->
                        message.copy(
                            parts = message.parts.map { part ->
                                part.copyWithForkedFileUrl()
                            }
                        )
                    }
                )
            }

        val existingTitles = conversationRepo
            .getConversationsOfAssistant(currentConversation.assistantId)
            .first()
            .mapTo(mutableSetOf()) { it.title }
        val forkConversation = createForkConversation(currentConversation, copiedNodes, existingTitles)

        saveConversation(forkConversation.id, forkConversation)
        return forkConversation
    }

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNode = currentConversation.messageNodes.firstOrNull { it.id == nodeId }
            ?: throw NotFoundException("Message node not found")

        if (selectIndex !in targetNode.messages.indices) {
            throw BadRequestException("Invalid selectIndex")
        }

        if (targetNode.selectIndex == selectIndex) {
            return
        }

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.id == nodeId) {
                node.copy(selectIndex = selectIndex)
            } else {
                node
            }
        }

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedConversation = buildConversationAfterMessageDelete(currentConversation, messageId)

        if (updatedConversation == null) {
            if (failIfMissing) {
                throw NotFoundException("Message not found")
            }
            return
        }

        saveConversation(conversationId, updatedConversation)
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ) {
        deleteMessage(conversationId, message.id, failIfMissing = false)
    }

    private fun buildConversationAfterMessageDelete(
        conversation: Conversation,
        messageId: Uuid,
    ): Conversation? {
        val targetNodeIndex = conversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            return null
        }

        val updatedNodes = conversation.messageNodes.mapIndexedNotNull { index, node ->
            if (index != targetNodeIndex) {
                return@mapIndexedNotNull node
            }

            val nextMessages = node.messages.filterNot { it.id == messageId }
            if (nextMessages.isEmpty()) {
                return@mapIndexedNotNull null
            }

            val nextSelectIndex = node.selectIndex.coerceAtMost(nextMessages.lastIndex)
            node.copy(
                messages = nextMessages,
                selectIndex = nextSelectIndex,
            )
        }

        return conversation.copy(messageNodes = updatedNodes)
    }

    private fun UIMessagePart.copyWithForkedFileUrl(): UIMessagePart {
        fun copyLocalFileIfNeeded(url: String): String {
            if (!url.startsWith("file:")) return url
            val copied = filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()
            return copied?.toString() ?: url
        }

        return when (this) {
            is UIMessagePart.Image -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Document -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Video -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Audio -> copy(url = copyLocalFileIfNeeded(url))
            else -> this
        }
    }

    fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = null)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun createGroup(): Uuid {
        val settings = settingsStore.settingsFlow.value
        val current = settings.getCurrentAssistant()
        val members = (listOf(current) + settings.assistants.filter { it.id != current.id }.take(2))
        val roles = members.map { assistant ->
            GroupRole(
                id = assistant.id.toString(),
                name = assistant.name.ifBlank { "角色" },
                assistantId = assistant.id.toString(),
                chair = assistant.id == current.id,
            )
        }
        val config = GroupConfig(
            roles = roles,
            mode = GroupChat.MODE_PIPELINE,
            tokenBudgetPerRound = 2000,
        )
        val conversation = Conversation(
            assistantId = current.id,
            title = "群聊",
            messageNodes = emptyList(),
            type = GroupChat.TYPE_GROUP,
            groupConfig = config,
        )
        // 落库前跑一遍字段级校验。返回类型是 Uuid（ChatDrawerViewModel.createGroup 依赖它），
        // 不能改成可空/结果对象，所以非法配置只能拒收：先记日志再抛，绝不写进库。
        // 当前组装逻辑本应恒合法，这里是把「先校验后落库」固化成结构约束，而不是口头约定。
        val invalid = GroupChat.validate(config, conversation.id.toString())
        if (invalid.isNotEmpty()) {
            val detail = invalid.joinToString { "${it.field}: ${it.message}" }
            Log.w(TAG, "createGroup 群配置校验失败，拒绝落库：$detail")
            error("群配置校验失败，拒绝落库：$detail")
        }
        conversationRepo.insertConversation(conversation)
        return conversation.id
    }

    /**
     * 轮次准入：**落库成功之后**才可能返回 [GroupTurnEntry.Speak]，也就是「run token 必须持久化
     * 后才可执行」（契约硬约束）。
     *
     * 返回 [GroupTurnEntry.Idle] 表示这一轮**不允许**再发起模型调用（已完成 / 并发被拒 /
     * 预算已停 / 全员已提交 / 触发消息不是用户消息）：调用方必须放弃生成，绝不能退化成
     * 「不过滤的普通生成」，否则当前视角会收到别人的发言。
     */
    private suspend fun takeGroupTurn(
        conversation: Conversation,
        config: GroupConfig,
    ): GroupTurnEntry {
        val conversationId = conversation.id
        val key = conversationId.toString()
        // 触发消息固定为最后一条 USER 消息，所以整轮期间 roundId 稳定，重试必然落回同一轮。
        val plan = GroupTurnCoordinator.roundPlanFor(config, conversation.currentMessages)
            ?: return GroupTurnEntry.Idle

        // 平票裁决（TIE_CHAIR）是同一轮里的额外一步：run token 已在库，不必再 claim。
        val inFlight = groupRunsInFlight[conversationId]
        inFlight?.forcedStep?.let { forced ->
            val running = groupRunDAO.findByRound(key, plan.roundId)
            if (running != null &&
                running.runToken == inFlight.runToken &&
                !GroupRunEntity.isTerminal(running.status)
            ) {
                groupRunsInFlight[conversationId] = InFlightGroupRun(runToken = inFlight.runToken)
                return GroupTurnEntry.Speak(forced)
            }
            groupRunsInFlight.remove(conversationId)
        }

        var existing = groupRunDAO.findByRound(key, plan.roundId)?.let(GroupTurnCoordinator::fromEntity)
        if (existing != null &&
            !roundOutputPresent(conversation.currentMessages, plan.roundId, existing.committedRoleIds)
        ) {
            // 运行日志说这一轮跑过，但会话里已经没有这些角色的产出（用户删除/切分支/重新生成）：
            // committed 集合已失效，删掉这行按新轮次重跑，否则会留下「跳过了一个谁都没发言的角色」的空洞。
            groupRunDAO.deleteByRound(key, plan.roundId)
            existing = null
        }

        val active = groupRunsInFlight[conversationId]?.runToken
        val claim = GroupTurnCoordinator.claimRound(
            conversationId = key,
            plan = plan,
            tokenLimit = config.tokenBudgetPerRound,
            existing = existing,
            // 只有「本进程正在驱动这一轮」才允许当续跑，重复触发无法伪装成续跑。
            expectedRunToken = active?.takeIf { it == existing?.runToken },
            activeRunToken = active,
            newRunToken = Uuid.random().toString(),
            now = System.currentTimeMillis(),
        )
        val state = when (claim) {
            is GroupTurnCoordinator.Claim.Acquired -> {
                if (!persistClaim(claim.state, claim.freshRow)) {
                    // 并发抢占失败（主键冲突）：库里已有别的实例的运行日志，本进程不得驱动这一轮。
                    return GroupTurnEntry.Idle
                }
                claim.state
            }

            is GroupTurnCoordinator.Claim.Continued -> {
                persistRoundState(claim.state)
                claim.state
            }

            is GroupTurnCoordinator.Claim.Rejected -> {
                claim.state?.let { persistRoundState(it) }
                Logging.log(TAG, "group round rejected: ${claim.code} round=${plan.roundId}")
                return GroupTurnEntry.Idle
            }
        }

        val pending = GroupChat.pendingSpeakers(plan.plan, state.committedRoleIds.toSet())
        if (pending.isEmpty()) {
            // 全员已提交（典型：全员发言后带着待审批工具重新进入）：本轮已跑完，不重复发言。
            persistRoundState(GroupTurnCoordinator.completeRound(state, System.currentTimeMillis()))
            groupRunsInFlight.remove(conversationId)
            return GroupTurnEntry.Idle
        }
        // 续跑时先按本轮累计判定：预算已用尽就别浪费一次模型调用，直接把剩余角色记为未运行。
        val stop = GroupChat.budgetDecision(
            spent = state.spentTokens,
            limit = state.tokenLimit,
            remainingRoleIds = pending.map { it.role.id },
        )
        if (stop is RoundBudget.Stop) {
            persistRoundState(
                state.copy(
                    status = GroupRunEntity.STATUS_BUDGET_STOPPED,
                    reason = stop.reason,
                    skippedRoleIds = stop.skippedRoleIds,
                    spentTokens = stop.spent,
                    tokenLimit = stop.limit,
                    endedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            groupRunsInFlight.remove(conversationId)
            return GroupTurnEntry.Idle
        }
        groupRunsInFlight[conversationId] = InFlightGroupRun(runToken = state.runToken)
        return GroupTurnEntry.Speak(pending.first())
    }

    /**
     * 抢占落库。`freshRow` 走 `insert`（主键冲突抛 `SQLiteConstraintException`），
     * **catch 必须在事务外**——所以这里直接调 `insert`，不走 `upsertRun`。
     */
    private suspend fun persistClaim(
        state: GroupTurnCoordinator.RoundState,
        freshRow: Boolean,
    ): Boolean {
        if (freshRow) {
            return try {
                groupRunDAO.insert(GroupTurnCoordinator.toEntity(state))
                true
            } catch (e: SQLiteException) {
                Log.w(TAG, "group round already claimed: ${state.roundId}", e)
                false
            }
        }
        groupRunDAO.updateStatus(
            conversationId = state.conversationId,
            roundId = state.roundId,
            status = state.status,
            errorMessage = state.errorMessage,
            updatedAt = state.updatedAt,
        )
        persistRoundState(state)
        return true
    }

    /** 运行日志落库：已用/上限/未运行角色/原因始终写，`ended_at` 只在终态写。 */
    private suspend fun persistRoundState(state: GroupTurnCoordinator.RoundState) {
        val key = state.conversationId
        val now = state.updatedAt
        groupRunDAO.updateBudget(
            conversationId = key,
            roundId = state.roundId,
            spent = state.spentTokens,
            tokenLimit = state.tokenLimit,
            skippedRoleIds = state.skippedRoleIds,
            reason = state.reason,
            status = state.status,
            updatedAt = now,
        )
        groupRunDAO.updateCommittedRoles(key, state.roundId, state.committedRoleIds, now)
        if (GroupRunEntity.isTerminal(state.status)) {
            groupRunDAO.finish(
                conversationId = key,
                roundId = state.roundId,
                status = state.status,
                endedAt = state.endedAt ?: now,
                errorMessage = state.errorMessage,
                updatedAt = now,
            )
        }
    }

    /**
     * 一次发言成功后的提交：给产出打上 `role_id` / `round_id` / `turn_kind`，把角色追加进
     * `committed_role_ids`，再按 **prompt + completion** 累加本轮已用并判停。
     */
    private suspend fun commitGroupTurn(
        conversationId: Uuid,
        step: SpeakerStep,
        plan: RoundPlan,
        config: GroupConfig,
    ): GroupTurnCoordinator.Advance? {
        val key = conversationId.toString()
        val produced = stampGroupTurn(conversationId, step, plan.roundId)
        if (produced == null) {
            // 一个 token 都没产出（空助手消息已被丢弃）：不算提交，本轮按失败收尾，
            // 下次触发同一 round_id 时这个角色还会被轮到。
            failGroupTurn(conversationId, step, "本轮没有产出内容", timedOut = false)
            return null
        }
        val state = groupRunDAO.findByRound(key, plan.roundId)?.let(GroupTurnCoordinator::fromEntity)
            ?: return null
        val advance = GroupTurnCoordinator.advance(
            state = state,
            finishedRoleId = step.role.id,
            usage = GroupTurnCoordinator.usageOf(produced) ?: (0 to 0),
            plan = plan,
            tokenLimit = config.tokenBudgetPerRound,
            now = System.currentTimeMillis(),
        )
        val advanced = when (advance) {
            is GroupTurnCoordinator.Advance.More -> advance.state
            is GroupTurnCoordinator.Advance.BudgetStopped -> advance.state
            is GroupTurnCoordinator.Advance.Finished -> advance.state
        }
        persistRoundState(advanced)
        return advance
    }

    /** 给本次产出的助手消息盖上群聊三元组；找不到未署名助手消息时返回 null。 */
    private suspend fun stampGroupTurn(
        conversationId: Uuid,
        step: SpeakerStep,
        roundId: String,
    ): UIMessage? {
        val conversation = getConversationFlow(conversationId).value
        val nodes = conversation.messageNodes
        var nodeIndex = -1
        var messageIndex = -1
        loop@ for (i in nodes.indices.reversed()) {
            val messages = nodes[i].messages
            for (j in messages.indices.reversed()) {
                val message = messages[j]
                if (message.role == MessageRole.ASSISTANT &&
                    message.roleId == null &&
                    message.roundId == null
                ) {
                    nodeIndex = i
                    messageIndex = j
                    break@loop
                }
            }
        }
        if (nodeIndex < 0) return null
        val node = nodes[nodeIndex]
        val stamped = node.messages[messageIndex].copy(
            roleId = step.role.id,
            roundId = roundId,
            turnKind = GroupTurnCoordinator.turnKindOf(step),
        )
        val newNodes = nodes.toMutableList()
        newNodes[nodeIndex] = node.copy(
            messages = node.messages.toMutableList().also { it[messageIndex] = stamped },
        )
        saveConversation(conversationId, conversation.copy(messageNodes = newNodes))
        return stamped
    }

    /** 全员发言完成后的收尾：pipeline/roundtable 直接完成，vote 计票。 */
    private suspend fun completeGroupRound(
        conversationId: Uuid,
        config: GroupConfig,
        plan: RoundPlan,
        state: GroupTurnCoordinator.RoundState,
    ) {
        val now = System.currentTimeMillis()
        if (config.mode != GroupChat.MODE_VOTE) {
            persistRoundState(GroupTurnCoordinator.completeRound(state, now))
            groupRunsInFlight.remove(conversationId)
            return
        }
        val conversation = getConversationFlow(conversationId).value
        val roundMessages = GroupTurnCoordinator.roundMessages(conversation.currentMessages)
        val chairRoleId = GroupTurnCoordinator.chairRoleIdOf(config)
        val resolution = GroupTurnCoordinator.resolveVote(
            state = state,
            config = config,
            plan = plan,
            roundMessages = roundMessages,
            // 议长裁决过就只数它自己那一票，保证收敛，不会二次平票。
            chairAlreadyDecided = chairRoleId != null &&
                GroupTurnCoordinator.chairAlreadyDecided(roundMessages, chairRoleId),
            now = now,
        )
        when (resolution) {
            is GroupTurnCoordinator.VoteResolution.Decided -> {
                appendGroupMessages(conversationId, listOf(resolution.summary))
                persistRoundState(resolution.state)
                dropTieBreakScaffolding(conversationId)
                groupRunsInFlight.remove(conversationId)
            }

            is GroupTurnCoordinator.VoteResolution.NeedsChairTieBreak -> {
                // TIE_CHAIR：裁决指令挂进本轮，议长以 chairRound 视角（看得见全部票）再跑一轮。
                appendGroupMessages(conversationId, listOf(resolution.instruction))
                groupRunsInFlight[conversationId] = InFlightGroupRun(
                    runToken = state.runToken,
                    forcedStep = SpeakerStep(
                        role = config.roles.first { it.id == resolution.chairRoleId },
                        chairRound = true,
                    ),
                )
                handleMessageComplete(conversationId)
            }

            is GroupTurnCoordinator.VoteResolution.Undecided -> {
                // 只有 Decided 才写摘要；未决只留错误节点 + 运行日志，不伪造结论。
                appendGroupMessages(conversationId, listOf(voteFailureNode(plan.roundId, resolution.detail)))
                persistRoundState(resolution.state)
                dropTieBreakScaffolding(conversationId)
                groupRunsInFlight.remove(conversationId)
            }
        }
    }

    /**
     * 单角色失败 / 超时：写一条 `turn_kind = error` 的节点（**只记错误，不伪造助手回复正文**），
     * 运行日志落 FAILED/TIMEOUT；已完成角色留在 `committed_role_ids` 里，所以轮次仍可续跑。
     */
    private suspend fun failGroupTurn(
        conversationId: Uuid,
        step: SpeakerStep?,
        detail: String,
        timedOut: Boolean,
    ) {
        val conversation = getConversationFlow(conversationId).value
        val config = conversation.groupConfig ?: return
        val plan = GroupTurnCoordinator.roundPlanFor(config, conversation.currentMessages) ?: return
        val failedRoleId = step?.role?.id ?: return
        val state = groupRunDAO.findByRound(conversationId.toString(), plan.roundId)
            ?.let(GroupTurnCoordinator::fromEntity)
            ?: return
        val remaining = GroupChat.pendingSpeakers(plan.plan, state.committedRoleIds.toSet())
            .map { it.role.id }
            .filter { it != failedRoleId }
        val now = System.currentTimeMillis()
        val failed = if (timedOut) {
            GroupTurnCoordinator.timeoutRound(state, detail, now)
        } else {
            GroupTurnCoordinator.fail(state, failedRoleId, remaining, detail, now)
        }
        appendGroupMessages(
            conversationId,
            listOf(GroupTurnCoordinator.errorNode(state, config, failedRoleId, detail)),
        )
        persistRoundState(failed)
        groupRunsInFlight.remove(conversationId)
    }

    /** 用户取消：只把运行日志写成 CANCELLED，不写任何未生成的消息。 */
    private suspend fun cancelActiveGroupRun(conversationId: Uuid) {
        val token = groupRunsInFlight.remove(conversationId)?.runToken ?: return
        val entity = groupRunDAO.getByRunToken(token) ?: return
        if (entity.conversationId != conversationId.toString()) return
        if (GroupRunEntity.isTerminal(entity.status)) return
        persistRoundState(
            GroupTurnCoordinator.cancelRound(
                GroupTurnCoordinator.fromEntity(entity),
                System.currentTimeMillis(),
            )
        )
    }

    /**
     * 用户发了新消息：上一轮若还挂在 RUNNING（进程被杀 / 异常中断 / 取消未收尾），按「用户放弃」
     * 收尾，保证任何群聊轮次都不会永久悬挂、也不会被下一轮挪用预算。
     */
    private suspend fun abandonDanglingGroupRuns(conversationId: Uuid) {
        groupRunsInFlight.remove(conversationId)
        val now = System.currentTimeMillis()
        groupRunDAO.listByConversationAndStatus(
            conversationId = conversationId.toString(),
            status = GroupRunEntity.STATUS_RUNNING,
            limit = 8,
        ).forEach { entity ->
            persistRoundState(GroupTurnCoordinator.cancelRound(GroupTurnCoordinator.fromEntity(entity), now))
        }
    }

    private suspend fun appendGroupMessages(conversationId: Uuid, messages: List<UIMessage>) {
        if (messages.isEmpty()) return
        val conversation = getConversationFlow(conversationId).value
        saveConversation(
            conversationId,
            conversation.copy(messageNodes = conversation.messageNodes + messages.map { it.toMessageNode() }),
        )
    }

    /** 回收平票裁决脚手架：它是给议长看的提示，不属于对话内容。 */
    private suspend fun dropTieBreakScaffolding(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        val nodes = GroupTurnCoordinator.withoutTieBreakInstruction(conversation.messageNodes)
        if (nodes == conversation.messageNodes) return
        saveConversation(conversationId, conversation.copy(messageNodes = nodes))
    }

    private fun voteFailureNode(roundId: String, detail: String): UIMessage = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text("[投票] 本轮未能得出结论：$detail")),
        roleId = GroupChat.SUMMARY_ID,
        roundId = roundId,
        turnKind = GroupChat.TURN_ERROR,
    )

    /** 运行日志里的错误摘要：类名 + 消息，避免只留一句没有线索的话。 */
    private fun errorDetailOf(error: Throwable): String {
        val type = error::class.simpleName ?: "Error"
        val message = error.message?.take(200).orEmpty()
        return if (message.isBlank()) type else "$type: $message"
    }

    // 停止当前会话生成任务（不清理会话缓存）
    suspend fun stopGeneration(conversationId: Uuid) {
        val session = sessionManager.get(conversationId)
        val jobs = if (session == null) {
            emptyList()
        } else {
            synchronized(session) {
                session.messageQueue.pause()
                session.cancelJobs()
            }
        }
        if (jobs.isEmpty()) {
            cancelActiveGroupRun(conversationId)
            return
        }
        jobs.forEach { it.join() }
        finishInterruptedPendingTools(conversationId)
        cancelActiveGroupRun(conversationId)
    }
}

/**
 * 群聊轮次的**非权威**进程内镜像（内容）。
 *
 * 只有 [runToken] 与「本轮内的额外一步（平票裁决）」是进程内状态；权威的
 * `committed_role_ids` / `spent_tokens` / `status` 每次都重新读 `group_runs`。
 */
private data class InFlightGroupRun(
    val runToken: String,
    /** `TIE_CHAIR` 平票裁决这类同一轮内的额外一步；null = 按 `plan - committed` 现算下一位。 */
    val forcedStep: SpeakerStep? = null,
)

/** 轮次准入结果。 */
private sealed interface GroupTurnEntry {
    /** 落库成功，可以发起模型调用。 */
    data class Speak(val step: SpeakerStep) : GroupTurnEntry

    /** 本轮不允许再发起模型调用，调用方必须放弃生成。 */
    data object Idle : GroupTurnEntry
}

/**
 * 工具的 `systemPrompt` 与模型上下文、记忆检索 query 共用同一份 viewer 过滤结果。
 *
 * 契约明写「工具调用、检索与记忆注入均使用同一 viewer 过滤结果，禁止 UI 层隐藏但仍发送」。
 * `GenerationLoop.generateInternal` 里 `tool.systemPrompt(model, messages)` 用的是**调用方传入的
 * 完整历史**（`core/data/ai/GenerationLoop.kt` 归别的包所有，不能改），所以过滤必须在
 * ChatManager 这个调用点注入：把每个工具的 `systemPrompt` 包一层，先按当前 viewer 裁剪再交给它。
 *
 * 模型自身看到的上下文由 `GroupPerspectiveTransformer`（inputTransformers）保证，二者口径相同、
 * 重复过滤幂等。记忆检索 query 与检索结果过滤见 `GroupTurnCoordinator.memoryQuery` /
 * `memoriesForViewer`。
 */
internal fun viewerScopedTools(
    tools: List<Tool>,
    config: GroupConfig,
    step: SpeakerStep,
): List<Tool> = tools.map { viewerScopedTool(it, config, step) }

internal fun viewerScopedTool(tool: Tool, config: GroupConfig, step: SpeakerStep): Tool {
    val delegate = tool.systemPrompt
    return tool.copy(
        systemPrompt = { model, messages ->
            delegate(model, GroupTurnCoordinator.viewerMessages(config, messages, step))
        },
    )
}

/**
 * 群聊会话判定：`group_config` 非空**且** `type == GROUP` 才算群聊。
 *
 * 与轮次内核同一口径（`GroupTurnCoordinator.roundPlanFor` 只在群聊轮次里被调用）。
 * 两个条件缺一不可：老数据可能残留 `group_config` 却已被改回单聊，这时绝不能让
 * [abandonDanglingGroupRuns] 去动运行日志——否则一次普通单聊发言会误伤同 id 的群聊行。
 */
internal fun isGroupConversation(conversation: Conversation): Boolean =
    conversation.groupConfig != null && conversation.type == GroupChat.TYPE_GROUP

/**
 * 运行日志声称已提交的角色，其产出是否还在会话里。
 *
 * 不在（用户删除消息、切分支、重新生成）说明 `committed_role_ids` 已失效，必须重新抢占这一轮，
 * 否则续跑会跳过一个谁都没发言的角色。
 */
internal fun roundOutputPresent(
    messages: List<UIMessage>,
    roundId: String,
    committedRoleIds: List<String>,
): Boolean {
    if (committedRoleIds.isEmpty()) return true
    return messages.any { message ->
        message.role == MessageRole.ASSISTANT &&
            message.roundId == roundId &&
            message.roleId in committedRoleIds &&
            message.turnKind != GroupChat.TURN_ERROR
    }
}

