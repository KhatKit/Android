package heizige.kk.khatkit.app.feature.chat

import android.app.Application
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.core.data.ai.GenerationLoop
import heizige.kk.khatkit.app.core.data.ai.TranslationHandler
import heizige.kk.khatkit.app.core.data.ai.tools.ChatToolFactory
import heizige.kk.khatkit.app.core.data.ai.tools.local.LocalTools
import heizige.kk.khatkit.app.core.data.ai.transformers.Base64ImageToLocalFileTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.OcrTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.PlaceholderTransformer
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.db.AppDatabase
import heizige.kk.khatkit.app.core.data.db.AppDatabaseFactory
import heizige.kk.khatkit.app.core.data.db.dao.GroupRunDAO
import heizige.kk.khatkit.app.core.data.db.entity.GroupRunEntity
import heizige.kk.khatkit.app.core.data.db.fts.MessageFtsManager
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FilesRepository
import heizige.kk.khatkit.app.core.data.repository.FolderRepository
import heizige.kk.khatkit.app.core.data.repository.MemoryExtractor
import heizige.kk.khatkit.app.core.di.appEntryPoint
import heizige.kk.khatkit.app.core.util.JsonInstant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.uuid.Uuid

/**
 * C1 真机证据（第二轮）：采契约 `docs/beyond-operit-client-changes.md:206` 点名的
 * **实际模型调用序列** 与 **prompt+completion token 计数**。
 *
 * ## 与 `C1DeviceEvidenceTest` 的区别
 *
 * 上一轮明确写了「不发起任何真实 LLM 调用，也不伪造任何 LLM 产物」，因为设备上没有
 * API key。那一轮的 `model_sequence_source` 是 `routing-decision`（只跑了路由判定），
 * `token_source` 是 `budget-accounting-only`（只跑了预算累加）——都不是真实调用产物。
 *
 * 这一轮**绕开 API key 限制**：往生产单例 [SettingsRepository] 里装一个 base URL 指向
 * `http://127.0.0.1:8765/v1` 的自定义 OpenAI 兼容 provider；设备侧用
 * `adb reverse tcp:8765 tcp:8765` 打到开发机上的 mock 服务。于是本类跑的是**完整的生产
 * 代码路径**：
 *
 * ```
 * ChatManager.sendMessage
 *   → GenerationLoop
 *   → ProviderManager
 *   → OpenAIProvider
 *   → ChatCompletionsAPI.streamText      （真的构造 OpenAI wire body）
 *   → Ktor CIO OkHttpClient             （真的发 TCP 到 127.0.0.1:8765）
 * ```
 *
 * mock 服务在开发机侧把**每个请求的完整 body 原样落盘**（`requests.jsonl`）。于是
 * 「每个角色实际收到的 prompt」有了**独立于 app 的第三方记录**——这比只看消息 ID 强得多，
 * 也是视角隔离（C1-01/C1-03）最硬的形式。
 *
 * ## 不许伪造
 *
 * 全部断言基于**从真实库里读回来的数据**与**真实响应的 `usage`**。任何数字对不上就让
 * 用例失败：不调低断言、不加 `@Ignore`、不手填 token。
 *
 * ## 三个关键事实（决定了断言怎么写）
 *
 * 1. **视角隔离发生在 prompt 组装层**：`GroupTurnCoordinator.viewerMessages` 的返回值
 *    被执行层直接当 `messages` 传给生成管线（见该函数注释「发给模型的消息集合的唯一
 *    入口」），所以 mock 收到的 messages 数组就是隔离后的结果。
 * 2. **pipeline 的可见性规则**（`GroupChat.visibleMessages`）：观众看得见自己的消息、
 *    所有用户消息、@ 自己的消息、summary，以及 `predecessorId` 那一位的本轮输出。
 *    所以 pipeline 三角色里 **c 看得见 b 的话、看不见 a 的话**。
 * 3. **预算是 prompt+completion 累计**（`GroupTurnCoordinator.usageOf` 取
 *    `(promptTokens, completionTokens)`），不是 `TokenUsage.totalTokens`。
 *
 * ## 数据库隔离与其一处例外
 *
 * 会话与消息走**独立库** `c1-live-evidence.db`（同一个 `AppDatabaseFactory`），
 * 不碰用户数据。
 * ⚠️ `group_runs` 无法隔离：`ChatManager.groupRunDAO` 是 `by lazy` 从 Hilt 单例取到的
 * （见 `ChatManager.kt:231` 的注释「本包可改范围外」），指向生产库。所以本类**读**生产库
 * 的 `group_runs`，并在 `@After` 里按 conversationId 删掉自己那几行。
 */
@RunWith(AndroidJUnit4::class)
class C1LiveModelSequenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val appContext = resolveAppContext()

    private lateinit var evidenceDatabase: AppDatabase
    private lateinit var liveDatabase: AppDatabase
    private lateinit var repository: ConversationRepository
    private lateinit var groupRunDao: GroupRunDAO
    private lateinit var settingsStore: SettingsRepository
    private lateinit var chatManager: ChatManager

    private var originalSettings: Settings? = null
    private val evidenceConversations = mutableListOf<Uuid>()

    /** mock 服务地址；设备侧经 `adb reverse` 落到开发机的同一个端口。 */
    private val mockBaseUrl = "http://127.0.0.1:8765/v1"

    // ---------------- 固定 id：证据必须可复现 ----------------

    private val providerId = Uuid.parse("0c1c11ae-0000-0000-0000-000000000001")

    private val modelAId = Uuid.parse("0c1c11ae-0000-0000-0000-00000000000a")
    private val modelBId = Uuid.parse("0c1c11ae-0000-0000-0000-00000000000b")
    private val modelCId = Uuid.parse("0c1c11ae-0000-0000-0000-00000000000c")

    private val assistantAId = Uuid.parse("0c1c11ae-0000-0000-0000-0000000000a1")
    private val assistantBId = Uuid.parse("0c1c11ae-0000-0000-0000-0000000000b1")
    private val assistantCId = Uuid.parse("0c1c11ae-0000-0000-0000-0000000000c1")

    private val mainCaseName = "main"
    private val budgetCaseName = "budget"

    /** 预算充足轮：三个角色都要发言。 */
    private val mainBudget = 100_000

    /**
     * 预算截断轮：上限 1。只要第一个角色真产生了 token（必然 > 0）就会停跑。
     *
     * `GroupChat.budgetDecision` 的口径是 `limit <= 0 || spent < limit` 才继续，
     * 所以 `limit = 1` 是「第一个角色之后必定停跑」的最小正值。
     */
    private val budgetLimit = 1

    private val modelIds = listOf("mock-model-a", "mock-model-b", "mock-model-c")

    /**
     * 每个角色一个 system prompt，埋 `CASE:<case> ROLECODE:<code>` 暗号。
     *
     * 为什么不埋 user 消息：pipeline 三次请求的**最后一条 user 消息是同一条**（后两次
     * 只是多出前一位的 assistant 输出），按 user 消息区分不出角色。system prompt 才是
     * 每角色独有的，而且「谁的 system prompt」正是「谁在说话」本身。mock 服务据此
     * 判定每个请求的发言者与 case。
     */
    private fun persona(caseName: String, code: String) = buildString {
        append("你是 KhatKit C1 群聊验证角色。")
        append("CASE:").append(caseName).append(" ")
        append("ROLECODE:").append(code).append(" ")
        append("你正在参加一场三人 pipeline 群聊。请用简短中文回复，不要调用任何工具。")
        // 撑长度：保证 mock 侧 ceil(bytes/4) 的 prompt token 明显大于 1，
        // 这样 limit=1 的截断用例一定在第一个角色之后触发，而不是「压根没超」。
        repeat(6) { append("补充设定：保持角色一致性，只讲事实，不写形容词，不复述他人发言。") }
    }

    private fun mockSettings(caseName: String) = Settings(
        init = false,
        chatModelId = modelAId,
        fastModelId = modelAId,
        providers = listOf(
            ProviderSetting.OpenAI(
                id = providerId,
                enabled = true,
                name = "c1-live-mock",
                apiKey = "c1-live-mock-key",
                baseUrl = mockBaseUrl,
                chatCompletionsPath = "/chat/completions",
                useResponseApi = false,
                models = listOf(
                    Model(modelId = modelIds[0], displayName = "C1 Live A", id = modelAId, type = ModelType.CHAT),
                    Model(modelId = modelIds[1], displayName = "C1 Live B", id = modelBId, type = ModelType.CHAT),
                    Model(modelId = modelIds[2], displayName = "C1 Live C", id = modelCId, type = ModelType.CHAT),
                ),
            ),
        ),
        assistants = listOf(
            assistant(assistantAId, "角色甲", caseName, "A", modelAId),
            assistant(assistantBId, "角色乙", caseName, "B", modelBId),
            assistant(assistantCId, "角色丙", caseName, "C", modelCId),
        ),
    )

    private fun assistant(
        id: Uuid,
        name: String,
        caseName: String,
        code: String,
        modelId: Uuid,
    ) = Assistant(
        id = id,
        name = name,
        chatModelId = modelId,
        systemPrompt = persona(caseName, code),
        // 关掉一切额外能力：保证请求里只有 system + 对话消息，
        // viewer 隔离审计才不会被工具注入的 systemPrompt / 记忆注入干扰。
        enableMemory = false,
        useGlobalMemory = false,
        autoExtractMemory = false,
        enableWebSearch = false,
        localTools = emptyList(),
        enableTimeReminder = false,
        enableRecentChatsReference = false,
    )

    private fun groupConfig(caseName: String, budget: Int) = GroupConfig(
        roles = listOf(
            GroupRole(
                id = "a",
                name = "角色甲",
                assistantId = assistantAId.toString(),
                modelId = modelAId.toString(),
                cardId = "card-$caseName-a",
            ),
            GroupRole(
                id = "b",
                name = "角色乙",
                assistantId = assistantBId.toString(),
                modelId = modelBId.toString(),
                cardId = "card-$caseName-b",
            ),
            GroupRole(
                id = "c",
                name = "角色丙",
                assistantId = assistantCId.toString(),
                chair = true,
                modelId = modelCId.toString(),
                cardId = "card-$caseName-c",
            ),
        ),
        mode = GroupChat.MODE_PIPELINE,
        chairRoleId = "c",
        tokenBudgetPerRound = budget,
    )

    @Before
    fun setUp() {
        trace("setUp:enter")
        startWatchdog()
        val entry = appEntryPoint(appContext)
        trace("setUp:entryPoint-ok")
        settingsStore = entry.settingsStore()
        originalSettings = settingsStore.settingsFlow.value
        trace("setUp:settingsStore-ok init=${originalSettings?.init}")

        // 会话 / 消息：独立库。
        context.deleteDatabase(EVIDENCE_DB)
        evidenceDatabase = AppDatabaseFactory.create(context, EVIDENCE_DB)
        trace("setUp:evidenceDb-created")
        repository = ConversationRepository(
            conversationDAO = evidenceDatabase.conversationDao(),
            messageNodeDAO = evidenceDatabase.messageNodeDao(),
            database = evidenceDatabase,
            filesManager = FilesManager(
                context,
                FilesRepository(evidenceDatabase.managedFileDao()),
                AppScope(),
            ),
            messageFtsManager = MessageFtsManager(evidenceDatabase),
        )
        trace("setUp:repository-ok")

        // group_runs：ChatManager 内部走 Hilt 单例（生产库），这里另开一条连接只读。
        liveDatabase = AppDatabaseFactory.create(appContext)
        trace("setUp:liveDb-created")
        groupRunDao = liveDatabase.groupRunDao()
        trace("setUp:groupRunDao-ok")

        val providerManager = entry.providerManager()
        val json = JsonInstant
        val memoryRepository = entry.memoryRepository()

        chatManager = ChatManager(
            context = appContext,
            appScope = AppScope(),
            appEventBus = entry.appEventBus(),
            settingsStore = settingsStore,
            conversationRepo = repository,
            memoryRepository = memoryRepository,
            memoryExtractor = MemoryExtractor(memoryRepository, providerManager, json),
            generationLoop = GenerationLoop(appContext, providerManager, json),
            translationHandler = TranslationHandler(providerManager),
            templateTransformer = entry.templateTransformer(),
            providerManager = providerManager,
            chatToolFactory = ChatToolFactory(
                json = json,
                memoryRepository = memoryRepository,
                conversationRepository = repository,
                localTools = LocalTools(
                    appContext,
                    entry.appEventBus(),
                    entry.ttsManager(),
                    settingsStore,
                ),
                mcpManager = entry.mcpManager(),
                skillManager = entry.skillManager(),
                workspaceRepository = entry.workspaceRepository(),
                filesManager = entry.filesManager(),
                khatKitToolProvider = entry.khatKitToolProvider(),
            ),
            mcpManager = entry.mcpManager(),
            filesManager = entry.filesManager(),
            workspaceRepository = entry.workspaceRepository(),
            folderRepository = FolderRepository(
                evidenceDatabase.folderDao(),
                evidenceDatabase.conversationDao(),
            ),
            placeholderTransformer = PlaceholderTransformer(settingsStore),
            ocrTransformer = OcrTransformer(appContext, settingsStore, providerManager),
            base64ImageToLocalFileTransformer = Base64ImageToLocalFileTransformer(entry.filesManager()),
        )
        trace("setUp:chatManager-constructed")
    }

/**
     * 进度追踪：同时写 logcat 与外部文件。
     *
     * 写文件是必须的——本类早期版本在真机上**静默挂死**（app 连一行 logcat 都不打），
     * 只靠 logcat 无法定位卡在哪一步；写文件保证 `adb pull` 能拿到时间线。
     */
    private fun trace(step: String) {
        val line = "${System.currentTimeMillis()} $step"
        android.util.Log.i(TRACE_TAG, line)
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: return
            java.io.File(dir, TRACE_FILE).appendText(line + "\n")
        }
    }

    /**
     * 看门狗：挂死时靠它取证。
     *
     * 设备上 `kill -3` 需要 app uid（`adb shell` 直接发信号会被拒），logcat 缓冲区
     * 又会被其它 app 的日志冲掉。所以改成**测试进程自己**定期把自己的线程栈写进
     * 证据文件：主测试线程卡在哪、Room 的事务线程在等什么、生成协程在干什么，
     * 一次 dump 全部可见。
     */
    private fun startWatchdog() {
        Thread({
            val interesting = listOf(
                "heizige.kk.khatkit", "androidx.room", "kotlinx.coroutines",
                "android.os.Handler", "Test worker",
            )
            while (true) {
                Thread.sleep(5_000)
                runCatching {
                    val dump = StringBuilder("=== WATCHDOG ${System.currentTimeMillis()} ===\n")
                    Thread.getAllStackTraces().forEach { (thread, stack) ->
                        val head = stack.firstOrNull()?.let { "${it.className}.${it.methodName}" } ?: "?"
                        val frames = stack.filter {
                            frame -> interesting.any { frame.className.contains(it) }
                        }
                        if (frames.isEmpty() && !thread.name.contains("Test worker")) return@forEach
                        dump.append("--- thread '${thread.name}' state=${thread.state} top=$head\n")
                        frames.take(18).forEach { dump.append("      at $it\n") }
                    }
                    val dir = context.getExternalFilesDir(null)
                    if (dir != null) java.io.File(dir, TRACE_FILE).appendText(dump.toString())
                }
            }
        }, "c1-live-watchdog").apply { isDaemon = true }.start()
    }

    @After
    fun tearDown() {
        runCatching { runBlocking { evidenceConversations.forEach { chatManager.stopGeneration(it) } } }
        runCatching { runBlocking { evidenceConversations.forEach { chatManager.removeConversationReference(it) } } }
        // 还原生产设置，别把 mock provider 留给用户。
        originalSettings?.let { original ->
            runCatching { runBlocking { settingsStore.update(original) } }
        }
        // 清掉写进生产 group_runs 的本测试行。
        runCatching {
            runBlocking {
                evidenceConversations.forEach {
                    groupRunDao.deleteFinishedOfConversation(it.toString())
                }
            }
        }
        if (::evidenceDatabase.isInitialized) evidenceDatabase.close()
        if (::liveDatabase.isInitialized) liveDatabase.close()
        context.deleteDatabase(EVIDENCE_DB)
    }

    // ==================================================================
    // 用例 1：pipeline 一整轮 —— 3 角色 / 3 次真实模型调用 / 真实 token
    // ==================================================================

    @Test
    fun pipelineRoundRecordsRealModelSequenceAndRealTokenUsage() = runBlocking {
        val caseName = mainCaseName
        trace("main:settings-update-begin")
        settingsStore.update(mockSettings(caseName))
        trace("main:settings-update-done")
        val config = groupConfig(caseName, mainBudget)
        val conversationId = insertGroup(caseName, config)
        trace("main:conversation-inserted id=$conversationId")
        evidenceConversations += conversationId

        // ---- 走生产入口触发真实生成 ----
        chatManager.sendMessage(
            conversationId = conversationId,
            content = listOf(UIMessagePart.Text("请三位依次发言，每位一句话。")),
            answer = true,
        )
        trace("main:sendMessage-returned")

        val messages = awaitAssistantMessages(conversationId, expected = 3)
        trace("main:await-messages-done count=${messages.size}")
        val triggerId = messages.last { it.role == MessageRole.USER }.id.toString()
        val expectedRoundId = GroupChat.roundIdFor(triggerId)
        val run = awaitTerminalRun(conversationId, expectedRoundId)

        val assistants = messages.filter { it.role == MessageRole.ASSISTANT }
        val byRole = assistants.associateBy { it.roleId }

        // ---------------- 断言：轮次 id 由触发消息派生 ----------------
        assertEquals("三个助手消息必须同属一轮", setOf(expectedRoundId), assistants.map { it.roundId }.toSet())
        assertEquals("运行日志的 round_id 必须与消息上的 round_id 一致", expectedRoundId, run.roundId)

        // ---------------- 断言 1：助手消息数 == 角色数 ----------------
        assertEquals("助手消息数必须等于角色数", 3, assistants.size)
        assertEquals("三个角色都必须署名", setOf("a", "b", "c"), byRole.keys)

        // ---------------- 断言 2：每条消息的 modelId 与该角色绑定一致 ----------------
        assertEquals("角色 a 的 modelId 必须是 modelA", modelAId, byRole.getValue("a").modelId)
        assertEquals("角色 b 的 modelId 必须是 modelB", modelBId, byRole.getValue("b").modelId)
        assertEquals("角色 c 的 modelId 必须是 modelC", modelCId, byRole.getValue("c").modelId)

        // ---------------- 断言 3：usage 必须来自真实响应 ----------------
        assistants.forEach { message ->
            val usage = requireNotNull(message.usage) {
                "角色 ${message.roleId} 没有 usage —— 响应里没带 usage，这条 token 证据就不成立"
            }
            assertTrue(
                "角色 ${message.roleId} 的 prompt_tokens 必须为正（只有真实响应才有值）",
                usage.promptTokens > 0,
            )
            assertTrue(
                "角色 ${message.roleId} 的 completion_tokens 必须为正",
                usage.completionTokens > 0,
            )
            assertEquals(
                "角色 ${message.roleId} 的 totalTokens 必须等于 prompt+completion",
                usage.promptTokens + usage.completionTokens,
                usage.totalTokens,
            )
        }

        // ---------------- 断言 4：turnKind ----------------
        assertTrue(
            "pipeline 非议长轮的发言都应是 turn_kind=speaker，实际=" +
                assistants.map { "${it.roleId}:${it.turnKind}" },
            assistants.all { it.turnKind == GroupChat.TURN_SPEAKER },
        )

        // ---------------- 断言 5：spent == 各条 usage 的 (prompt+completion) 之和 ----------------
        // 口径是 prompt+completion 累计（GroupTurnCoordinator.usageOf），
        // 不是 TokenUsage.totalTokens，也不是最后一条消息的用量。
        val sumPromptCompletion = assistants.sumOf {
            requireNotNull(it.usage).promptTokens + requireNotNull(it.usage).completionTokens
        }
        assertEquals(
            "group_runs.spent_tokens 必须等于各条助手消息 (prompt+completion) 之和",
            sumPromptCompletion,
            run.spentTokens,
        )
        assertEquals("本轮应正常完成", GroupRunEntity.STATUS_COMPLETED, run.status)
        assertEquals("三个角色都必须进 committed 名单", listOf("a", "b", "c"), run.committedRoleIds)
        assertTrue("预算充足时不应有 skipped 角色，实际=${run.skippedRoleIds}", run.skippedRoleIds.isEmpty())
        assertEquals("预算上限快照必须等于配置值", mainBudget, run.tokenLimit)
        assertEquals("正常完成不应有 reason", "", run.reason)
        assertNotNull("运行日志必须已收尾（endedAt 非空）", run.endedAt)

        // ---------------- 断言 6：正文真的来自 mock 服务 ----------------
        assistants.forEach { message ->
            assertTrue(
                "角色 ${message.roleId} 的回复必须来自 mock 服务（应含 CASE:$caseName 暗号），实际=${message.toText()}",
                message.toText().contains("CASE:$caseName"),
            )
        }

        // ---------------- 断言 7：视角隔离（从真库读回的消息上判定） ----------------
        // pipeline 规则：a 看不见别人；b 只多看得见 a；c 只多看得见 b、看不见 a。
        val seenByA = GroupChat.buildContext("a", messages, config, null).mapNotNull { it.roleId }
        assertTrue("角色 a 不应看到任何他人发言，实际=$seenByA", seenByA.isEmpty())

        val seenByB = GroupChat.buildContext("b", messages, config, "a").mapNotNull { it.roleId }
        assertTrue("角色 b 应看到 a", "a" in seenByB)
        assertTrue("角色 b 不应看到 c，实际=$seenByB", "c" !in seenByB)

        val seenByC = GroupChat.buildContext("c", messages, config, "b").mapNotNull { it.roleId }
        assertTrue("角色 c 应看到 b", "b" in seenByC)
        assertTrue(
            "角色 c 不应看到 a（pipeline 只串联上一位），实际=$seenByC",
            "a" !in seenByC,
        )

        writeEvidence("c1-live-evidence-main.json", mainReport(conversationId, config, messages, run, sumPromptCompletion))
    }

    // ==================================================================
    // 用例 2：预算截断 —— 第一个角色就用完预算，剩下两个必须进 skipped 名单
    // ==================================================================

    @Test
    fun budgetTruncationSkipsRemainingRolesAndRecordsRunLog() = runBlocking {
        val caseName = budgetCaseName
        trace("budget:settings-update-begin")
        settingsStore.update(mockSettings(caseName))
        trace("budget:settings-update-done")
        val config = groupConfig(caseName, budgetLimit)
        val conversationId = insertGroup(caseName, config)
        evidenceConversations += conversationId

        chatManager.sendMessage(
            conversationId = conversationId,
            content = listOf(UIMessagePart.Text("请三位依次发言，每位一句话。")),
            answer = true,
        )
        trace("budget:sendMessage-returned")

        val messages = awaitAssistantMessages(conversationId, expected = 1)
        trace("budget:await-messages-done count=${messages.size}")
        val triggerId = messages.last { it.role == MessageRole.USER }.id.toString()
        val run = awaitTerminalRun(conversationId, GroupChat.roundIdFor(triggerId))

        val assistants = messages.filter { it.role == MessageRole.ASSISTANT }

        // ---------------- 断言 1：只产出第一个角色的发言 ----------------
        assertEquals("预算截断时只应有第一个角色发言", 1, assistants.size)
        assertEquals("应该只有角色 a 发言", "a", assistants.single().roleId)

        // ---------------- 断言 2：group_runs 记了 skipped 名单 ----------------
        assertEquals(
            "本轮应以 BUDGET_STOPPED 收尾",
            GroupRunEntity.STATUS_BUDGET_STOPPED,
            run.status,
        )
        assertEquals(
            "超预算原因必须落库",
            GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED,
            run.reason,
        )
        assertEquals("未运行角色必须是 b、c", listOf("b", "c"), run.skippedRoleIds)
        assertEquals("已提交角色只有 a", listOf("a"), run.committedRoleIds)
        assertEquals("预算上限快照必须是 $budgetLimit", budgetLimit, run.tokenLimit)
        assertNotNull("运行日志必须已收尾", run.endedAt)

        // ---------------- 断言 3：spent 恰好等于唯一那条发言的 (prompt+completion) ----------------
        val single = requireNotNull(assistants.single().usage) { "唯一的发言没有 usage，token 证据不成立" }
        assertTrue("唯一发言的 token 必须为正，否则 limit=1 不会触发截断", single.totalTokens > 0)
        assertEquals(
            "spent_tokens 必须等于唯一发言的 prompt+completion",
            single.promptTokens + single.completionTokens,
            run.spentTokens,
        )
        assertTrue(
            "被跳过的角色不得有任何消息，实际=${messages.map { it.roleId }}",
            messages.none { it.roleId == "b" || it.roleId == "c" },
        )

        writeEvidence("c1-live-evidence-budget.json", budgetReport(conversationId, config, messages, run))
    }

    // ==================================================================
    // 报告组装
    // ==================================================================

    private fun deviceBlock() = buildJsonObject {
        put("sdk", Build.VERSION.SDK_INT)
        put("model", Build.MODEL)
        put("abi", Build.SUPPORTED_ABIS.joinToString(","))
        put("fingerprint", Build.FINGERPRINT)
        put("package_name", context.packageName)
        put("evidence_db", EVIDENCE_DB)
    }

    private fun messageBlock(messages: List<UIMessage>) = buildJsonObject {
        put("count", messages.size)
        putJsonArray("messages") {
            messages.forEach { message ->
                add(
                    buildJsonObject {
                        put("id", message.id.toString())
                        put("role", message.role.name)
                        put("role_id", message.roleId)
                        put("round_id", message.roundId)
                        put("turn_kind", message.turnKind)
                        put("model_id", message.modelId?.toString())
                        put("mention_role_ids", JsonArray(message.mentionRoleIds.map { JsonPrimitive(it) }))
                        put("usage_prompt_tokens", message.usage?.promptTokens ?: -1)
                        put("usage_completion_tokens", message.usage?.completionTokens ?: -1)
                        put("usage_total_tokens", message.usage?.totalTokens ?: -1)
                        put("text", message.toText())
                    },
                )
            }
        }
    }

    private fun runBlock(run: GroupRunEntity) = buildJsonObject {
        put("conversation_id", run.conversationId)
        put("round_id", run.roundId)
        put("run_token", run.runToken)
        put("status", run.status)
        put("spent_tokens", run.spentTokens)
        put("token_limit", run.tokenLimit)
        put("reason", run.reason)
        put("error_message", run.errorMessage)
        put("started_at", run.startedAt)
        put("updated_at", run.updatedAt)
        put("ended_at", run.endedAt)
        put("skipped_role_ids", JsonArray(run.skippedRoleIds.map { JsonPrimitive(it) }))
        put("committed_role_ids", JsonArray(run.committedRoleIds.map { JsonPrimitive(it) }))
    }

    private fun bindingsBlock(config: GroupConfig) = buildJsonObject {
        putJsonArray("roles") {
            config.roles.forEach { role ->
                add(
                    buildJsonObject {
                        put("role_id", role.id)
                        put("assistant_id", role.assistantId)
                        put("chair", role.chair)
                        put("model_uuid", role.modelId)
                        put(
                            "model_string_sent_on_wire",
                            when (role.id) {
                                "a" -> modelIds[0]
                                "b" -> modelIds[1]
                                else -> modelIds[2]
                            },
                        )
                    },
                )
            }
        }
    }

    private fun mainReport(
        conversationId: Uuid,
        config: GroupConfig,
        messages: List<UIMessage>,
        run: GroupRunEntity,
        sumPromptCompletion: Int,
    ): JsonObject {
        val assistants = messages.filter { it.role == MessageRole.ASSISTANT }
        return buildJsonObject {
            put("evidence_kind", "real-http-capture-via-adb-reverse")
            put("token_source", "real-response-usage-from-mock-openai-server")
            put("model_sequence_source", "real-http-requests-logged-by-mock-server")
            put("generated_at_device", System.currentTimeMillis())
            put("mock_base_url", mockBaseUrl)
            put("case", mainCaseName)
            put("conversation_id", conversationId.toString())
            put("mode", config.mode)
            put("chair_role_id", config.chairRoleId)
            put("token_budget_per_round", config.tokenBudgetPerRound)
            put("device", deviceBlock())
            put("bindings", bindingsBlock(config))
            put("messages", messageBlock(messages))
            put("group_run", runBlock(run))
            put("sum_prompt_plus_completion", sumPromptCompletion)
            putJsonObject("viewer_visible_message_ids") {
                listOf("a" to null, "b" to "a", "c" to "b").forEach { (viewer, predecessor) ->
                    putJsonArray(viewer) {
                        GroupChat.buildContext(viewer, messages, config, predecessor)
                            .map { it.id.toString() }
                            .forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
            putJsonObject("pipeline_visibility_expectation") {
                put("a_sees", "system+user only")
                put("b_sees", "system+user+a")
                put("c_sees", "system+user+b  (NOT a)")
            }
            putJsonArray("assistant_role_order") {
                assistants.forEach { add(JsonPrimitive(it.roleId)) }
            }
        }
    }

    private fun budgetReport(
        conversationId: Uuid,
        config: GroupConfig,
        messages: List<UIMessage>,
        run: GroupRunEntity,
    ): JsonObject = buildJsonObject {
        put("evidence_kind", "real-http-capture-via-adb-reverse")
        put("token_source", "real-response-usage-from-mock-openai-server")
        put("model_sequence_source", "real-http-requests-logged-by-mock-server")
        put("generated_at_device", System.currentTimeMillis())
        put("mock_base_url", mockBaseUrl)
        put("case", budgetCaseName)
        put("conversation_id", conversationId.toString())
        put("mode", config.mode)
        put("token_budget_per_round", config.tokenBudgetPerRound)
        put("device", deviceBlock())
        put("bindings", bindingsBlock(config))
        put("messages", messageBlock(messages))
        put("group_run", runBlock(run))
        putJsonObject("expectation") {
            put("roles_that_spoke", "a")
            put("skipped_role_ids", "b,c")
            put("status", GroupRunEntity.STATUS_BUDGET_STOPPED)
            put("reason", GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED)
        }
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private suspend fun insertGroup(caseName: String, config: GroupConfig): Uuid {
        val conversation = Conversation(
            assistantId = assistantAId,
            title = "C1 live $caseName",
            messageNodes = emptyList(),
            type = GroupChat.TYPE_GROUP,
            groupConfig = config,
        )
        val invalid = GroupChat.validate(config, conversation.id.toString())
        assertTrue("群配置必须合法，实际违规：$invalid", invalid.isEmpty())
        trace("insertGroup:inserting id=${conversation.id}")
        repository.insertConversation(conversation)
        trace("insertGroup:inserted id=${conversation.id}")
        openGroupSession(conversation.id)
        return conversation.id
    }

    /**
     * 把刚落库的群会话装载进 [ChatManager] 的内存 session，**必须做，否则整轮根本不是群聊**。
     *
     * ## 为什么这不是多余的初始化
     *
     * [ChatManager] 的 session 是在 `getOrCreate` 时用
     * `createInitialConversation`（`ChatManager.kt:267`）造的，而那个 lambda 返回的是
     * `Conversation.ofId(id, assistantId = 当前助手)`——**一条全新的空会话**：
     * `type = TYPE_DIRECT`、`groupConfig = null`、`messageNodes = []`。
     * 也就是说 `sessionManager` 根本不会自己去数据库读回刚插入的群会话，
     * 「内存态 = 已落库的群会话」这一步只由 [ChatManager.initializeConversation] 完成
     * （生产上正是 `ChatViewModel.init` 的 `addConversationReference` + `initializeConversation`，
     * 见 `ChatViewModel.kt:109-113`）。
     *
     * 跳过它的后果不是「少一句提示词」，而是整条群聊链被静默旁路：
     * 1. `sendMessage` 里 `session.state.value` 是那条空会话，`saveConversation` 随后
     *    拿它整行覆盖刚落库的群会话，**`group_config` 被抹成 NULL**；
     * 2. `handleMessageComplete` 读到 `conversation.groupConfig == null`，于是
     *    `takeGroupTurn` 压根不被调用、`groupStep` 恒为 null，直接退化成一次普通单聊生成；
     * 3. 单聊路径用会话的 `assistantId` 选助手，落到 `getCurrentAssistant()` 的
     *    `assistants.first()`（本类即角色甲），所以 mock 只会看到**一个**
     *    `model=mock-model-a` + `ROLECODE:A` 的请求——正是实测抓到的那个请求；
     * 4. 之后 `groupStep == null` 走 `generateTitle`（`ChatManager.kt:989-994`），
     *    于是 mock 再收到一次标题生成请求，与实测的第二个请求完全吻合；
     * 5. `group_runs` 一行都不会写，`awaitTerminalRun` 永远等不到行。
     *
     * 所以本函数照抄生产入口顺序：先 `addConversationReference` 拿到引用（否则 session
     * 空闲 5s 就被回收），再 `initializeConversation` 从库里把群会话读进内存态。
     */
    private suspend fun openGroupSession(conversationId: Uuid) {
        chatManager.addConversationReference(conversationId)
        chatManager.initializeConversation(conversationId)
        val loaded = chatManager.getConversationFlow(conversationId).value
        // 断言装载结果，而不是相信上面那段推理：群配置丢失过一次，这里必须当场炸掉。
        assertTrue(
            "群会话必须以 groupConfig 非空被装载进 session，实际 type=${loaded.type}、" +
                "groupConfig=${loaded.groupConfig != null}",
            loaded.type == GroupChat.TYPE_GROUP && loaded.groupConfig != null,
        )
        trace("openGroupSession:loaded roles=${loaded.groupConfig?.roles?.size}")
    }

    /** 轮询等真实生成跑完：等到期望条数的助手消息落库。 */
    private suspend fun awaitAssistantMessages(
        conversationId: Uuid,
        expected: Int,
        timeoutMillis: Long = 120_000,
    ): List<UIMessage> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last: List<UIMessage> = emptyList()
        var polls = 0
        while (System.currentTimeMillis() < deadline) {
            trace("await:before-read poll=$polls")
            last = repository.getConversationById(conversationId)?.currentMessages.orEmpty()
            trace("await:after-read poll=$polls")
            if (polls % 20 == 0) {
                trace("await:msgs poll=$polls assistants=${last.count { it.role == MessageRole.ASSISTANT }} all=${last.map { "${it.role}/${it.roleId}" }}")
            }
            polls++
            if (last.count { it.role == MessageRole.ASSISTANT } >= expected) return last
            Thread.sleep(250)
        }
        throw AssertionError(
            "等待助手消息超时（${timeoutMillis}ms）：期望 $expected 条，" +
                "实际 ${last.count { it.role == MessageRole.ASSISTANT }} 条。" +
                "最后看到的消息=${last.map { "${it.role}/${it.roleId}" }}；" +
                "app 错误=${chatManager.errors.value.map { it.title to it.error }}",
        )
    }

    /** 等 group_runs 收尾（终态）。roundId 由触发消息派生，不猜。 */
    private suspend fun awaitTerminalRun(
        conversationId: Uuid,
        roundId: String,
        timeoutMillis: Long = 60_000,
    ): GroupRunEntity {
        val key = conversationId.toString()
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last: GroupRunEntity? = null
        while (System.currentTimeMillis() < deadline) {
            val entity = groupRunDao.findByRound(key, roundId)
            if (entity != null) {
                last = entity
                if (GroupRunEntity.isTerminal(entity.status)) return entity
            }
            Thread.sleep(200)
        }
        throw AssertionError(
            "等待 group_runs 收尾超时（${timeoutMillis}ms），round=$roundId，" +
                "最后状态=${last?.status}；app 错误=${chatManager.errors.value.map { it.title to it.error }}",
        )
    }

    private fun writeEvidence(name: String, payload: JsonObject) {
        val dir = requireNotNull(context.getExternalFilesDir(null)) {
            "getExternalFilesDir(null) 不应为 null"
        }
        val file = File(dir, name)
        file.writeText(PRETTY.encodeToString(JsonElement.serializer(), payload))
        assertTrue("证据文件不应为空：${file.absolutePath}", file.length() > 0)
    }

    private companion object {
        const val EVIDENCE_DB = "c1-live-evidence.db"
        const val TRACE_TAG = "C1Live"
        const val TRACE_FILE = "c1-live-trace.txt"

        val PRETTY = Json { prettyPrint = true; encodeDefaults = true }
    }
}

private fun resolveAppContext(): Application =
    InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application