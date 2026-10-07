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
import heizige.kk.khatkit.app.core.data.ai.tavern.TavernChatCodec
import heizige.kk.khatkit.app.core.data.ai.tools.ChatToolFactory
import heizige.kk.khatkit.app.core.data.ai.tools.local.LocalTools
import heizige.kk.khatkit.app.core.data.ai.transformers.Base64ImageToLocalFileTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.OcrTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.PlaceholderTransformer
import heizige.kk.khatkit.app.core.data.datastore.NetworkSetting
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.db.AppDatabase
import heizige.kk.khatkit.app.core.data.db.AppDatabaseFactory
import heizige.kk.khatkit.app.core.data.db.dao.GroupRunDAO
import heizige.kk.khatkit.app.core.data.db.entity.GroupRunEntity
import heizige.kk.khatkit.app.core.data.db.fts.MessageFtsManager
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.SpeakerStep
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FilesRepository
import heizige.kk.khatkit.app.core.data.repository.FolderRepository
import heizige.kk.khatkit.app.core.data.repository.MemoryExtractor
import heizige.kk.khatkit.app.core.di.appEntryPoint
import heizige.kk.khatkit.app.core.util.JsonInstant
import heizige.kk.khatkit.common.android.appTempFolder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.uuid.Uuid

/**
 * C1-07「失败续跑 / 幂等」的真机证据。契约 `docs/beyond-operit-client-changes.md:204`
 * （同一群同一 `round_id` 只允许一个运行实例；重试使用同一 `round_id` 并跳过已提交 turn）
 * 与 `:227-228`（run token 持久化后才可执行）。
 *
 * ## 两阶段设计
 *
 * 阶段 1：三角色 pipeline 群聊，mock 对角色 **b 的首次请求**注入**真正的零 content SSE**
 * （`MOCK_EMPTY_ROLE=B`，默认值）→ 走生产「本轮没有产出内容」路径
 * （`ChatManager.kt:1954-1958` → `failGroupTurn`）→ 该轮 `FAILED/role_failed`。
 *
 * 阶段 2：**不新增 USER 消息**，在 b 的错误节点上触发续跑（见下「重试入口」），
 * 复用同一 `round_id` / 同一 `group_runs` 行，跳过已提交的 a，只补 b、c。
 *
 * ## ⚠️ 为什么 `POST /__reset` 必须放在**阶段 1 之前**而不是两阶段之间
 *
 * mock 的 `claim_injection(role)`（`tools/verification/mock_openai_slow_cancel.py:124-137`）
 * 是「每个角色只注入一次」：`role in failed_once` 就跳过。`/__reset` 清空 `failed_once`
 * （`:140-145`）是**重新武装**下一次注入（docstring `:39-42`：「Reuse a long-lived server
 * across test attempts and still get the injection ... on the next attempt」）。
 *
 * ⇒ 如果在阶段 1 与阶段 2 之间 reset，b 的阶段 2 请求会被**再次**判为「首次」而重新注入
 * 零 content，b 会再失败一次，续跑永远到不了 `COMPLETED`。正确用法是**开测前 reset 一次**
 * （让重复跑测试时阶段 1 的注入确定性成立），阶段 2 靠阶段 1 已消耗的 `failed_once={B}`
 * 自然拿到正常内容。这是本用例相对任务书「阶段 2 再 reset」的**修正**，理由即上。
 *
 * ## 重试入口（机制核实结论 5）
 *
 * 「同一触发消息再发一次」= `ChatManager.sendMessage` **不是**续跑路径：它先 `append`
 * 一条新 USER 消息（`ChatManager.kt:532-540`），`roundPlanFor` 取最后一条 USER
 * （`GroupTurnCoordinator.kt:763-771`）⇒ `round_id` 变成新的一轮，旧 FAILED 行原样留着。
 * 生产里真正复用同一 `round_id` 的入口是 `ChatManager.regenerateAtMessage`
 * （`ChatManager.kt:609-653`）：对**已存在的**消息触发，不新增 USER 消息，再进
 * `handleMessageComplete`（`:633/:638`）。本用例在 b 的 `turn_kind=error` 节点上触发它，
 * 因此 last USER 触发消息不变 ⇒ `round_id` 不变。
 *
 * ⚠️ 群聊 UI 目前**没有**重试入口：`ChatList.kt:521-523` 把 `onRegenerate` 用
 * `if (!groupChat)` 关掉（`ChatMessage.kt:135-141` / `ChatMessageActions.kt:84-92` 说明
 * 「重新生成」非群聊感知）。所以续跑路径目前只能从 `ChatManager` API 触达（UI 侧缺失，
 * 见汇报的契约-代码缺口）。
 *
 * ## 为什么必须关掉 autoRetry（同 C1-06 的教训）
 *
 * `SettingsRepository.kt:349-354` 的读取路径会给缺失 id 补回内置「极客猫」
 * （enabled + 硬编码 key + 唯一带 models）⇒ 永远是 `ProviderFailover.buildChain`
 * 的候选（`GenerationLoop.kt:94`）。若 mock 注入 500 且 `NetworkSetting(enableAutoRetry=true)`，
 * 请求会被重放给真网关顶包（`GenerationLoop.kt:165-166`）。本用例走**零 content** 路径
 * （不抛异常、不触发 failover），但仍照 `C1GroupCancelDeviceTest` 关掉 `enableAutoRetry`
 * 作为第二道保险，并保留 modelId/wireModelName 来源守卫。
 *
 * ## 不做什么
 *
 * - 不改任何 `app/src/main` 生产代码。
 * - 不跑真机集成（设备可能不在线）；本文件只做编译期 + JVM 纯函数验证。
 */
@RunWith(AndroidJUnit4::class)
class C1GroupRetryResumeDeviceTest {

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

    /** 慢速 mock 地址；设备侧经 `adb reverse tcp:8766 tcp:8766` 落到开发机。 */
    private val mockBaseUrl = "http://127.0.0.1:8766/v1"
    private val mockResetUrl = "http://127.0.0.1:8766/__reset"

    private val attemptLabel: String by lazy {
        runCatching { InstrumentationRegistry.getArguments().getString("c1RetryAttempt") }
            .getOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: "x"
    }

    // ---------------- 固定 id ----------------

    private val providerId = Uuid.parse("0c1c07ce-0000-0000-0000-000000000001")
    private val modelAId = Uuid.parse("0c1c07ce-0000-0000-0000-00000000000a")
    private val modelBId = Uuid.parse("0c1c07ce-0000-0000-0000-00000000000b")
    private val modelCId = Uuid.parse("0c1c07ce-0000-0000-0000-00000000000c")
    private val assistantAId = Uuid.parse("0c1c07ce-0000-0000-0000-0000000000a1")
    private val assistantBId = Uuid.parse("0c1c07ce-0000-0000-0000-0000000000b1")
    private val assistantCId = Uuid.parse("0c1c07ce-0000-0000-0000-0000000000c1")

    /** mock 配置里的三个本地 model id；任何别的 modelId 出现即真网关顶包。 */
    private val mockModelIds get() = setOf(modelAId, modelBId, modelCId)

    private val caseName = "retry"
    private val budget = 100_000

    @Before
    fun setUp() {
        trace("setUp:enter")
        val entry = appEntryPoint(appContext)
        settingsStore = entry.settingsStore()
        originalSettings = settingsStore.settingsFlow.value

        context.deleteDatabase(EVIDENCE_DB)
        evidenceDatabase = AppDatabaseFactory.create(context, EVIDENCE_DB)
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

        liveDatabase = AppDatabaseFactory.create(appContext)
        groupRunDao = liveDatabase.groupRunDao()

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

    @After
    fun tearDown() {
        runCatching { runBlocking { evidenceConversations.forEach { chatManager.stopGeneration(it) } } }
        runCatching { runBlocking { evidenceConversations.forEach { chatManager.removeConversationReference(it) } } }
        originalSettings?.let { original ->
            runCatching { runBlocking { settingsStore.update(original) } }
        }
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

    @Test
    fun retrySameRoundSkipsCommittedTurnsAndDoesNotDuplicateMessages() = runBlocking {
        settingsStore.update(retrySettings())

        // mock /__reset 放在阶段 1 之前：清 failed_once 让本次阶段 1 的零 content 注入
        // 确定性成立（见类 KDoc）。**绝不能**放在两阶段之间 —— 那会重新武装 b 的注入。
        val resetBody = resetMockInjections()
        trace("mock:reset body=$resetBody")

        val conversationId = insertGroup()
        evidenceConversations += conversationId

        // ================= 阶段 1：b 零产出 → FAILED/role_failed =================
        chatManager.sendMessage(
            conversationId = conversationId,
            content = listOf(UIMessagePart.Text("请三位依次发言，每位一句话。")),
            answer = true,
        )
        val user1 = awaitUserMessages(conversationId, expected = 1).last { it.role == MessageRole.USER }
        val round1Id = GroupChat.roundIdFor(user1.id.toString())
        val run1 = awaitTerminalRun(conversationId, round1Id, timeoutMillis = 120_000)
        trace("phase1:terminal status=${run1.status} reason=${run1.reason}")

        val after1 = loadMessages(conversationId)
        writeRawDump("c1-retry-raw-phase1-$attemptLabel.json", conversationId, round1Id, after1, run1)

        val round1Assistant = after1.filter { it.roundId == round1Id && it.role == MessageRole.ASSISTANT }
        val round1Real = round1Assistant.filter { it.turnKind != GroupChat.TURN_ERROR }
        val a1 = round1Real.singleOrNull { it.roleId == "a" }
        val errorNodeB = round1Assistant.singleOrNull {
            it.roleId == "b" && it.turnKind == GroupChat.TURN_ERROR
        }

        // 来源守卫：真网关一旦顶包会带非 mock 的 modelId/wireModelName。
        val round1Leaks = round1Real.filter { message ->
            message.toText().isNotBlank() &&
                (message.modelId !in mockModelIds ||
                    (message.wireModelName != null && message.wireModelName !in MOCK_WIRE_MODEL_NAMES))
        }

        // ---- 断言 P1-A：终态 FAILED / role_failed（GroupTurnCoordinator.fail:469-483）----
        assertEquals(GroupRunEntity.STATUS_FAILED, run1.status)
        assertEquals(GroupRunEntity.REASON_ROLE_FAILED, run1.reason)
        assertNotNull("FAILED 必须有 ended_at", run1.endedAt)
        // ---- 断言 P1-B：committed 只含 a（fail() 把已完成角色留在 committed；:465-467）----
        assertEquals(listOf("a"), run1.committedRoleIds)
        // ---- 断言 P1-C：skipped_role_ids 是失败角色之后未运行的角色（failGroupTurn:2124-2126）----
        assertEquals("skipped 必须体现剩余角色 c", listOf("c"), run1.skippedRoleIds)
        // ---- 断言 P1-D：库内实际发言消息只有 a 一条（b 的空产出被丢弃，不写空气泡）----
        assertTrue(
            "阶段 1 真实发言消息必须恰好 1 条（只有 a），实际=" +
                round1Real.map { "${it.roleId}:${it.turnKind}" },
            round1Real.size == 1 && a1 != null,
        )
        val a1Message = requireNotNull(a1) { "阶段 1 缺少角色 a 的真实发言消息" }
        assertEquals("a", a1Message.roleId)
        assertTrue("a 的消息必须非空", a1Message.toText().isNotBlank())
        assertTrue("阶段 1 不得出现 c 的消息", round1Assistant.none { it.roleId == "c" })
        // ---- 断言 P1-E：失败来自 mock 零 content 注入（commitGroupTurn:1954-1958）----
        assertTrue(
            "阶段 1 error_message 必须是生产零产出文案，实际=${run1.errorMessage}",
            run1.errorMessage.contains(EMPTY_OUTPUT_MARKER),
        )
        val errorNodeBMessage = requireNotNull(errorNodeB) {
            "必须由生产失败路径写出 b 的错误节点，实际助手消息=" +
                round1Assistant.map { "${it.roleId}:${it.turnKind}" }
        }
        assertTrue(
            "错误节点正文带生产前缀 + 零产出标记，实际=${errorNodeBMessage.toText()}",
            errorNodeBMessage.toText().contains("本轮生成失败") &&
                errorNodeBMessage.toText().contains(EMPTY_OUTPUT_MARKER),
        )
        assertTrue(
            "阶段 1 不得有真网关顶包（modelId/wireModelName 必须是 mock 的），实际=" +
                round1Leaks.map { "${it.roleId}:${it.modelId}:${it.wireModelName}" },
            round1Leaks.isEmpty(),
        )

        // ================= 阶段 2：同一 round_id 续跑（不新增 USER 消息） =================
        // 触发点 = b 的 turn_kind=error 节点；regenerateAtMessage 不 append USER 消息
        // （ChatManager.kt:609-653），last USER 触发消息不变 ⇒ round_id 不变。
        val errorNode = requireNotNull(
            chatManager.getConversationFlow(conversationId).value.currentMessages
                .firstOrNull { it.id == errorNodeBMessage.id },
        ) { "session 内存态里必须能找到 b 的错误节点" }
        val phase2StartedAt = System.currentTimeMillis()
        chatManager.regenerateAtMessage(
            conversationId = conversationId,
            message = errorNode,
            regenerateAssistantMsg = true,
        )
        val run2 = awaitCompletedRun(conversationId, round1Id, timeoutMillis = 120_000)
        trace("phase2:terminal status=${run2.status} committed=${run2.committedRoleIds}")
        val phase2FinishedAt = System.currentTimeMillis()

        // 阶段 2 结束后的**整棵消息树**（不是 currentMessages 投影）：错误节点是否被删除要看
        // messageNodes；currentMessages 只含每个节点的 selectIndex 那条（Conversation.kt:61-64）。
        val after2Conversation = loadConversation(conversationId)
        val after2 = after2Conversation.currentMessages
        writeRawDump("c1-retry-raw-phase2-$attemptLabel.json", conversationId, round1Id, after2, run2, extraRun = run1)

        val round2All = after2.filter { it.roundId == round1Id && it.role == MessageRole.ASSISTANT }
        val realByRole = listOf("a", "b", "c").associateWith { role ->
            round2All.filter { it.roleId == role && it.turnKind != GroupChat.TURN_ERROR }
        }
        val a2 = realByRole.getValue("a")
        val b2 = realByRole.getValue("b")
        val c2 = realByRole.getValue("c")
        val round2Leaks = round2All.filter { message ->
            message.turnKind != GroupChat.TURN_ERROR && message.toText().isNotBlank() &&
                (message.modelId !in mockModelIds ||
                    (message.wireModelName != null && message.wireModelName !in MOCK_WIRE_MODEL_NAMES))
        }

        // ================= 阶段 2 的形状判定 + 契约字段（先算完 → 落盘 → 再断言） =================
        //
        // ---- 错误节点的正确形状：被「同节点候选替换」，不是被删除 ----
        // `regenerateAtMessage` 对助手消息走 messageRange = 0..<nodeIndex（ChatManager.kt:636-638），
        // 生成输入只是「被点节点之前」的消息（:906-912），**不删任何节点**；新产出经
        // `Conversation.updateCurrentMessages`（Conversation.kt:74-106）落回 index == nodeIndex
        // 的那个节点，作为新的候选消息追加、并把 selectIndex 切过去（:81-93）。currentMessages
        // 只返回 selectIndex 那条（:61-64），所以快照里看不到旧错误节点，但它仍**原样留在**
        // messageNodes 里（saveMessageNodes 存整份 node.messages，ConversationRepository.kt:557-567）。
        // 这正是 `GroupRetryEntry.kt:42-44` 明写的设计：失败节点被点后续跑时，新产出作为该节点的
        // 新候选分支、role_id / round_id 一致，「账面对得上」。
        val after2Nodes = after2Conversation.messageNodes
        val errorNodeOwningNode = after2Nodes.firstOrNull { node ->
            node.messages.any { it.id == errorNodeBMessage.id }
        }
        val keptErrorNode = after2Nodes.flatMap { it.messages }.firstOrNull { it.id == errorNodeBMessage.id }
        val newBMessage = b2.singleOrNull()
        val errorSupersededByNewBInSameNode = errorNodeOwningNode != null &&
            newBMessage != null &&
            errorNodeOwningNode.messages.any { it.id == newBMessage.id }
        val errorNodeStillSelected = after2.any { it.id == errorNodeBMessage.id }

        // ---- 断言之外还要进证据 JSON 的值，全部在写盘之前算好 ----
        // round_rows：主键 (conversation_id, round_id) + claimRound 对终态行 reclaim 复用同一行
        //（GroupTurnCoordinator.kt:264-266），不新建行。
        val roundRows = groupRunDao.listRecentByConversation(conversationId.toString(), 50)
            .filter { it.roundId == round1Id }
        val config = groupConfig()
        val viewerLedger = viewerVisibilityLedger(after2, config)
        // after2 是两阶段结束后的完整会话快照（同一 round_id 的所有助手消息，选中项）。
        val callSequence = actualModelCallSequence(after2, round1Id)
        val exportEvidence = exportGroupJsonlEvidence(conversationId, "c1-retry-export-$attemptLabel.jsonl")
        // spent 期望用 singleOrNull 容错，保证即使某角色不是恰好 1 条，证据也能落盘（断言随后再红）。
        val a2Message = a2.singleOrNull()
        val c2Message = c2.singleOrNull()
        val allRolesSingle = a2Message != null && newBMessage != null && c2Message != null
        val allRolesHaveUsage = listOf(a2Message, newBMessage, c2Message).all { it?.usage != null }
        val expectedSpent = listOf(a2Message, newBMessage, c2Message)
            .mapNotNull { it?.usage }
            .sumOf { it.promptTokens + it.completionTokens }

        // ================= 证据落盘 =================
        writeEvidence(
            "c1-device-retry-resume-report-$attemptLabel.json",
            buildJsonObject {
                put("case", "C1-07 retry same round_id / idempotent resume")
                put("attempt", attemptLabel)
                putJsonObject("device") {
                    put("model", Build.MODEL)
                    put("sdk", Build.VERSION.SDK_INT)
                    put("release", Build.VERSION.RELEASE)
                    put("abi", Build.SUPPORTED_ABIS.joinToString(","))
                }
                put("mock_base_url", mockBaseUrl)
                put("mock_reset_url", mockResetUrl)
                put("mock_reset_response", resetBody)
                put(
                    "mock_server",
                    "tools/verification/mock_openai_slow_cancel.py (deployed /tmp/opencode/c1-cancel/mock_openai_slow.py); " +
                        "MOCK_EMPTY_ROLE=B -> role B's FIRST request returns a valid SSE with ZERO content events " +
                        "(production \"本轮没有产出内容\" path); /__reset re-arms failed_once and is called BEFORE phase 1, " +
                        "never between phases (re-arming would fail B again); enableAutoRetry=false so no failover replay",
                )
                putJsonObject("conversation") {
                    put("id", conversationId.toString())
                    put("group_mode", GroupChat.MODE_PIPELINE)
                    put("budget", budget)
                    put("round_id", round1Id)
                    put("trigger_user_message_id", user1.id.toString())
                }
                putJsonObject("timing_millis") {
                    put("phase2_started", phase2StartedAt)
                    put("phase2_finished", phase2FinishedAt)
                    put("phase2_duration", phase2FinishedAt - phase2StartedAt)
                }
                putJsonObject("phase1_failed_run") { runRowPut(this, run1) }
                putJsonObject("phase2_completed_run") { runRowPut(this, run2) }
                put("round_rows_for_round_id", roundRows.size)
                put("run_token_reused", run1.runToken == run2.runToken)
                putJsonObject("observed") {
                    put("phase1_real_speaker_count", round1Real.size)
                    put("phase1_error_node_id", errorNodeBMessage.id.toString())
                    put("phase1_reason", run1.reason)
                    put("phase1_skipped", run1.skippedRoleIds.joinToString(","))
                    putJsonObject("phase2_real_messages_per_role") {
                        realByRole.forEach { (role, list) -> put(role, list.size) }
                    }
                    put("a_message_id_stable", a1Message.id == a2.singleOrNull()?.id)
                    put("a_message_id_phase1", a1Message.id.toString())
                    put("a_message_id_phase2", a2.singleOrNull()?.id?.toString())
                    // 旧错误节点的正确口径：保留在 messageNodes（候选分支），但已被新 b 取代（非选中）。
                    put("old_error_node_in_message_tree", keptErrorNode != null)
                    put("old_error_node_selected_in_current", errorNodeStillSelected)
                    put("old_error_node_shares_node_with_new_b", errorSupersededByNewBInSameNode)
                    put("new_b_message_id", newBMessage?.id?.toString())
                    put("expected_spent_tokens", expectedSpent)
                    put("actual_spent_tokens", run2.spentTokens)
                }
                putJsonArray("assertions") {
                    listOf(
                        Triple("P1 status=FAILED", GroupRunEntity.STATUS_FAILED, run1.status),
                        Triple("P1 reason=role_failed", GroupRunEntity.REASON_ROLE_FAILED, run1.reason),
                        Triple("P1 committed=[a]", listOf("a").toString(), run1.committedRoleIds.toString()),
                        Triple("P1 skipped=[c]", listOf("c").toString(), run1.skippedRoleIds.toString()),
                        Triple("P1 real speaker count=1", "1", (round1Real.size).toString()),
                        Triple("P2 status=COMPLETED", GroupRunEntity.STATUS_COMPLETED, run2.status),
                        Triple("P2 committed=[a,b,c]", listOf("a", "b", "c").toString(), run2.committedRoleIds.toString()),
                        Triple("P2 group_runs rows=1", "1", roundRows.size.toString()),
                        Triple("P2 a id stable", a1Message.id.toString(), a2.singleOrNull()?.id?.toString() ?: "<missing>"),
                        Triple("P2 spent=Σusage", expectedSpent.toString(), run2.spentTokens.toString()),
                        Triple(
                            "P2 error node retained as branch (in messageNodes)",
                            "true",
                            (keptErrorNode != null).toString(),
                        ),
                        Triple(
                            "P2 error node superseded by new b (not selected)",
                            "true",
                            (!errorNodeStillSelected).toString(),
                        ),
                    ).forEach { (name, expected, actual) ->
                        add(
                            buildJsonObject {
                                put("name", name)
                                put("expected", expected)
                                put("actual", actual)
                            },
                        )
                    }
                }
                putJsonArray("messages_after_phase1") { after1.forEach { add(messageJson(it)) } }
                putJsonArray("messages_after_phase2") { after2.forEach { add(messageJson(it)) } }
                // 契约 :232-235：各 viewer 可见消息 ID 台账
                put("viewer_visibility", viewerLedger)
                // 契约 :206：实际模型调用序列
                put("actual_model_call_sequence", callSequence)
                // 契约 :206/:232-235：生产导出器 JSONL + SHA-256
                put("export_sha256", exportEvidence["export_sha256"] ?: JsonNull)
                putJsonObject("export") { exportEvidence.forEach { (k, v) -> put(k, v) } }
                put(
                    "scope_note",
                    "phase1/phase2 group_run fields, per-message id/modelId/wireModelName/usage and the " +
                        "assertion table are all MEASURED. actual_model_call_sequence is DERIVED from the " +
                        "persisted stamped-message order (each committed speaker = one model call; the zero-output " +
                        "B call is represented by its TURN_ERROR node) — the mock's host-side requests.jsonl is not " +
                        "readable from the device. viewer_visibility is computed by production GroupChat.visibleMessages. " +
                        "export_sha256 is over production TavernChatCodec.exportGroupJsonl bytes via production " +
                        "writeExportTempFile, byte-compared. Device-side behaviour is NOT verified in this environment " +
                        "(no device); compilation + JVM pure-function probes only.",
                )
            },
        )

        // ================= 断言（放在证据落盘之后：断言失败也不会吞掉报告） =================
        // ---- 断言 P2-A：a 不重复生成 —— 消息 id 不变、整轮 a 仍只有 1 条 ----
        // pendingSpeakers(plan, committed={a}) 跳过 a（GroupChat.kt:902-903），
        // 因此 a 不会被再次生成（roundOutputPresent 也确认 a 的产出还在，:2361-2373）。
        assertEquals("a 的已提交消息 id 不得改变（未重复生成）", a1Message.id, a2.singleOrNull()?.id)
        assertEquals("a 在整轮里必须恰好 1 条真实发言", 1, a2.size)
        // ---- 断言 P2-B：b 与 c 各自恰好 1 条 ----
        assertEquals("b 必须恰好 1 条真实发言", 1, b2.size)
        assertEquals("c 必须恰好 1 条真实发言", 1, c2.size)
        assertTrue("b 的补发言必须非空", b2.single().toText().isNotBlank())
        assertTrue("c 的补发言必须非空", c2.single().toText().isNotBlank())
        // ---- 断言 P2-C（修正）：旧错误节点被新产出「同节点候选替换」，不是被删除 ----
        // 旧断言 `after2.any { it.id == errorNodeBMessage.id }` 把「当前选中快照」当成了
        // 「整棵消息树」：after2 = currentMessages 只含 selectIndex 那条（Conversation.kt:61-64），
        // 而续跑的新产出按 updateCurrentMessages（:74-106）落在同一节点、并切走 selectIndex，
        // 所以旧错误节点必然不在快照里、却仍原样保留在 messageNodes 里（未被删除）。
        assertNotNull(
            "阶段 1 的 b 错误节点必须作为候选分支保留在 messageNodes 里（未被删除）",
            keptErrorNode,
        )
        assertNotNull(
            "旧错误节点必须仍属于某个 MessageNode（整节点未被删除）",
            errorNodeOwningNode,
        )
        assertTrue(
            "新 b 真实发言必须与被取代的旧错误节点落在同一 MessageNode（原位候选替换）",
            errorSupersededByNewBInSameNode,
        )
        assertTrue(
            "旧错误节点必须已被新产出取代（不再是 currentMessages 的选中项）",
            !errorNodeStillSelected,
        )
        assertTrue(
            "被取代的旧错误节点正文必须原样保留（仍是阶段 1 的失败文案），实际=" +
                keptErrorNode?.toText(),
            keptErrorNode != null &&
                keptErrorNode.toText().contains("本轮生成失败") &&
                keptErrorNode.toText().contains(EMPTY_OUTPUT_MARKER),
        )
        // ---- 断言 P2-D：committed == [a,b,c]，skipped 清空（reclaimed:87-95）----
        assertEquals(listOf("a", "b", "c"), run2.committedRoleIds)
        assertTrue("续跑完成后 skipped_role_ids 必须清空", run2.skippedRoleIds.isEmpty())
        assertEquals(GroupRunEntity.STATUS_COMPLETED, run2.status)
        assertEquals("正常收尾 reason 为空", "", run2.reason)
        assertNotNull("COMPLETED 必须有 ended_at", run2.endedAt)
        // ---- 断言 P2-E：同一 round_id 只有 1 行 group_runs（修好 P2-C 后本条必然执行到）----
        assertEquals("同一 round_id 的 group_runs 只允许 1 行", 1, roundRows.size)
        // ---- 断言 P2-F：run_token 沿用（GroupRunDAO 无改写 run_token 的语句）----
        assertEquals("续跑必须复用同一 run_token（同一运行实例）", run1.runToken, run2.runToken)
        // ---- 断言 P2-G：spent_tokens == Σ(各条消息 usage 的 prompt+completion) ----
        assertTrue("a/b/c 必须各自恰好 1 条且都带 usage 才能核对 spent", allRolesSingle && allRolesHaveUsage)
        assertEquals("spent_tokens 必须等于本轮所有产出消息的 prompt+completion 之和", expectedSpent, run2.spentTokens)
        assertEquals("token_limit 必须是配置快照", budget, run2.tokenLimit)
        // ---- 断言 P2-H：来源守卫（b/c 的补发言必须是 mock 的）----
        assertTrue(
            "阶段 2 不得有真网关顶包，实际=" + round2Leaks.map { "${it.roleId}:${it.modelId}:${it.wireModelName}" },
            round2Leaks.isEmpty(),
        )
        // 契约 :232-235：viewer 台账必须逐一等于当场生产 GroupChat.visibleMessages 输出。
        assertViewerLedgerMatchesProduction(viewerLedger, after2, config)

        println("C1-RETRY-DEVICE-BEGIN")
        println("attempt=$attemptLabel round=$round1Id")
        println("phase1 status=${run1.status} reason=${run1.reason} committed=${run1.committedRoleIds} skipped=${run1.skippedRoleIds} real=${round1Real.size}")
        println("phase2 status=${run2.status} committed=${run2.committedRoleIds} spent=${run2.spentTokens} rows=${roundRows.size} aStable=${a1Message.id == a2.singleOrNull()?.id}")
        println("C1-RETRY-DEVICE-END")
    }

    // ==================================================================
    // 设置 / 夹具
    // ==================================================================

    private fun persona(code: String) = buildString {
        append("你是 KhatKit C1 续跑用例角色。CASE:").append(caseName).append(" ")
        append("ROLECODE:").append(code).append(" ")
        append("你正在参加一场三人 pipeline 群聊。请用简短中文回复，不要调用任何工具。")
        repeat(6) { append("补充设定：保持角色一致性，只讲事实，不写形容词，不复述他人发言。") }
    }

    private fun assistant(id: Uuid, name: String, code: String, modelId: Uuid) = Assistant(
        id = id,
        name = name,
        chatModelId = modelId,
        systemPrompt = persona(code),
        enableMemory = false,
        useGlobalMemory = false,
        autoExtractMemory = false,
        enableWebSearch = false,
        localTools = emptyList(),
        enableTimeReminder = false,
        enableRecentChatsReference = false,
    )

    private fun retrySettings() = Settings(
        init = false,
        chatModelId = modelAId,
        fastModelId = modelAId,
        // 关键：关掉 autoRetry（读取路径会给缺失 id 补回内置「极客猫」，永远在 failover 链上）。
        // 本用例走零 content 路径本不抛异常，这里是第二道保险（见类 KDoc）。
        networkSetting = NetworkSetting(enableAutoRetry = false),
        providers = listOf(
            ProviderSetting.OpenAI(
                id = providerId,
                enabled = true,
                name = "c1-retry-mock",
                apiKey = "c1-retry-mock-key",
                baseUrl = mockBaseUrl,
                chatCompletionsPath = "/chat/completions",
                useResponseApi = false,
                models = listOf(
                    Model(modelId = "mock-retry-a", displayName = "C1 Retry A", id = modelAId, type = ModelType.CHAT),
                    Model(modelId = "mock-retry-b", displayName = "C1 Retry B", id = modelBId, type = ModelType.CHAT),
                    Model(modelId = "mock-retry-c", displayName = "C1 Retry C", id = modelCId, type = ModelType.CHAT),
                ),
            ),
        ),
        assistants = listOf(
            assistant(assistantAId, "角色甲", "A", modelAId),
            assistant(assistantBId, "角色乙", "B", modelBId),
            assistant(assistantCId, "角色丙", "C", modelCId),
        ),
    )

    private fun groupConfig() = GroupConfig(
        roles = listOf(
            GroupRole(id = "a", name = "角色甲", assistantId = assistantAId.toString(), modelId = modelAId.toString(), cardId = "card-retry-a"),
            GroupRole(id = "b", name = "角色乙", assistantId = assistantBId.toString(), modelId = modelBId.toString(), cardId = "card-retry-b"),
            GroupRole(id = "c", name = "角色丙", assistantId = assistantCId.toString(), chair = true, modelId = modelCId.toString(), cardId = "card-retry-c"),
        ),
        mode = GroupChat.MODE_PIPELINE,
        chairRoleId = "c",
        tokenBudgetPerRound = budget,
    )

    private suspend fun insertGroup(): Uuid {
        val conversation = Conversation(
            assistantId = assistantAId,
            title = "C1 retry $caseName",
            messageNodes = emptyList(),
            type = GroupChat.TYPE_GROUP,
            groupConfig = groupConfig(),
        )
        val invalid = GroupChat.validate(groupConfig(), conversation.id.toString())
        assertTrue("群配置必须合法，实际违规：$invalid", invalid.isEmpty())
        repository.insertConversation(conversation)
        chatManager.addConversationReference(conversation.id)
        chatManager.initializeConversation(conversation.id)
        val loaded = chatManager.getConversationFlow(conversation.id).value
        assertTrue(
            "群会话必须以 groupConfig 非空被装载进 session",
            loaded.type == GroupChat.TYPE_GROUP && loaded.groupConfig != null,
        )
        return conversation.id
    }

    // ==================================================================
    // mock 控制
    // ==================================================================

    /** `POST /__reset`：清 failed_once，重新武装注入。必须在阶段 1 之前调用（见类 KDoc）。 */
    private fun resetMockInjections(): String {
        val conn = (URL(mockResetUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            assertTrue("mock /__reset 必须返回 2xx，实际=$code body=$body", code in 200..299)
            body
        } finally {
            conn.disconnect()
        }
    }

    // ==================================================================
    // 轮询辅助
    // ==================================================================

    private suspend fun loadMessages(conversationId: Uuid): List<UIMessage> =
        loadConversation(conversationId).currentMessages

    /** 整棵消息树（含每个节点的全部候选分支）；判定「错误节点是否被取代/保留」必须用它。 */
    private suspend fun loadConversation(conversationId: Uuid): Conversation =
        requireNotNull(repository.getConversationById(conversationId)) { "会话 $conversationId 必须能从库读回" }

    private suspend fun awaitUserMessages(
        conversationId: Uuid,
        expected: Int,
        timeoutMillis: Long = 60_000,
    ): List<UIMessage> {
        var last: List<UIMessage> = emptyList()
        awaitCondition(timeoutMillis, "user messages >= $expected") {
            last = loadMessages(conversationId)
            last.count { it.role == MessageRole.USER } >= expected
        }
        return last
    }

    private suspend fun awaitTerminalRun(
        conversationId: Uuid,
        roundId: String,
        timeoutMillis: Long,
    ): GroupRunEntity {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last: GroupRunEntity? = null
        while (System.currentTimeMillis() < deadline) {
            val entity = groupRunDao.findByRound(conversationId.toString(), roundId)
            if (entity != null) {
                last = entity
                if (GroupRunEntity.isTerminal(entity.status)) return entity
            }
            Thread.sleep(150)
        }
        throw AssertionError(
            "等待 group_runs 收尾超时（${timeoutMillis}ms），round=$roundId，最后状态=${last?.status}，" +
                "app 错误=${chatManager.errors.value.map { it.title to it.error }}",
        )
    }

    /**
     * 等阶段 2 的续跑收尾。**不能**复用 [awaitTerminalRun]：续跑开始时那一行仍是上一段的
     * `FAILED`（终态），会立刻误判为完成。这里只等 `COMPLETED`（pipeline 正常收尾的唯一终态）。
     */
    private suspend fun awaitCompletedRun(
        conversationId: Uuid,
        roundId: String,
        timeoutMillis: Long,
    ): GroupRunEntity {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last: GroupRunEntity? = null
        while (System.currentTimeMillis() < deadline) {
            val entity = groupRunDao.findByRound(conversationId.toString(), roundId)
            if (entity != null) {
                last = entity
                if (entity.status == GroupRunEntity.STATUS_COMPLETED) return entity
            }
            Thread.sleep(150)
        }
        throw AssertionError(
            "等待续跑 COMPLETED 超时（${timeoutMillis}ms），round=$roundId，最后状态=${last?.status} " +
                "committed=${last?.committedRoleIds}，app 错误=${chatManager.errors.value.map { it.title to it.error }}",
        )
    }

    private suspend fun awaitCondition(
        timeoutMillis: Long,
        what: String,
        pollMillis: Long = 100,
        condition: suspend () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(pollMillis)
        }
        throw AssertionError(
            "等待条件超时（${timeoutMillis}ms）：$what；" +
                "app 错误=${chatManager.errors.value.map { it.title to it.error }}",
        )
    }

    // ==================================================================
    // 契约字段：viewer 台账 / 调用序列 / 导出哈希
    // ==================================================================

    /**
     * 契约 :232-235 的「各 viewer 可见消息 ID」台账。可见集合由**生产函数**
     * [GroupChat.visibleMessages] 计算（与提示词组装层 `GroupTurnCoordinator.viewerMessages`
     * 同一入口）。pipeline 的 predecessor 由生产 [GroupChat.plan] 派生。
     */
    private fun viewerVisibilityLedger(messages: List<UIMessage>, config: GroupConfig): JsonObject {
        val steps = GroupChat.plan(config, emptyList())
        return buildJsonObject {
            listOf("a", "b", "c").forEach { viewer ->
                val step: SpeakerStep? = steps.firstOrNull { it.role.id == viewer }
                val visible = GroupChat.visibleMessages(
                    config = config,
                    messages = messages,
                    viewerId = viewer,
                    predecessorId = step?.predecessorId,
                    chairRound = step?.chairRound == true,
                )
                putJsonObject(viewer) {
                    put("viewer_role_id", viewer)
                    put("predecessor_id", step?.predecessorId)
                    putJsonArray("visible_message_ids") {
                        visible.forEach { add(JsonPrimitive(it.id.toString())) }
                    }
                    put("visible_count", visible.size)
                    putJsonArray("visible_assistant_role_ids") {
                        visible.filter { it.role == MessageRole.ASSISTANT }
                            .mapNotNull { it.roleId }
                            .forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
        }
    }

    /** 台账必须逐一等于**当场**生产 `GroupChat.visibleMessages` 的输出（不是手抄期望值）。 */
    private fun assertViewerLedgerMatchesProduction(
        ledger: JsonObject,
        messages: List<UIMessage>,
        config: GroupConfig,
    ) {
        val steps = GroupChat.plan(config, emptyList())
        listOf("a", "b", "c").forEach { viewer ->
            val step = steps.firstOrNull { it.role.id == viewer }
            val expected = GroupChat.visibleMessages(
                config = config,
                messages = messages,
                viewerId = viewer,
                predecessorId = step?.predecessorId,
                chairRound = step?.chairRound == true,
            ).map { it.id.toString() }.toSet()
            val actual = ledger.getValue(viewer).jsonObject.getValue("visible_message_ids")
                .let { it as kotlinx.serialization.json.JsonArray }
                .map { it.jsonPrimitive.content }
                .toSet()
            assertEquals("viewer=$viewer 台账必须等于生产 visibleMessages 输出", expected, actual)
        }
    }

    /**
     * 契约 :206 的「实际模型调用序列」。**从持久化的盖章消息派生**（每条 committed 发言 =
     * 一次模型调用；零产出的 b 调用由其 TURN_ERROR 节点代表）。
     *
     * 诚实标注：mock 主机侧的 `requests.jsonl` 设备读不到，所以这不是主机请求日志，
     * 是消息元数据的确定性推导（证据 JSON 的 scope_note 也这么写）。
     */
    private fun actualModelCallSequence(messages: List<UIMessage>, roundId: String) = buildJsonArray {
        messages.filter { it.roundId == roundId && it.role == MessageRole.ASSISTANT }
            .forEach { message ->
                add(
                    buildJsonObject {
                        put("role_id", message.roleId)
                        put("turn_kind", message.turnKind)
                        put("outcome", if (message.turnKind == GroupChat.TURN_ERROR) "failed" else "committed")
                        put("model_id", message.modelId?.toString())
                        put("wire_model_name", message.wireModelName)
                        message.usage?.let { usage ->
                            put("prompt_tokens", usage.promptTokens)
                            put("completion_tokens", usage.completionTokens)
                        }
                    },
                )
            }
    }

    /**
     * 契约 :206/:232-235 的逐例导出哈希：生产 [TavernChatCodec.exportGroupJsonl] 产出字节 →
     * 生产 IO 助手 `writeExportTempFile`（`ConversationExport.kt:836`）真写盘 →
     * 同进程 `MessageDigest` 算 SHA-256。与 `C1LiveModelSequenceTest` 同一链条
     * （本文件自带私有实现，不改那个文件）。
     */
    private suspend fun exportGroupJsonlEvidence(conversationId: Uuid, exportFileName: String): JsonObject {
        val stored = requireNotNull(repository.getConversationById(conversationId)) {
            "会话 $conversationId 必须能从真库读回后才能导出"
        }
        val config = requireNotNull(stored.groupConfig) {
            "群会话必须带 groupConfig 才能走 Tavern 群聊导出"
        }
        val exported = TavernChatCodec.exportGroupJsonl(
            nodes = stored.messageNodes,
            config = config,
            cards = stored.groupCards.orEmpty(),
            userName = EXPORT_USER_NAME,
            groupName = "C1 retry ${stored.title}",
            createDate = null,
        )
        val bytes = exported.toByteArray(Charsets.UTF_8)
        assertTrue("导出字节不应为空：$exportFileName", bytes.isNotEmpty())
        assertTrue(
            "导出首行必须是带 chat_metadata 的表头（生产 JSONL 形态）",
            exported.lineSequence().first().contains("chat_metadata"),
        )
        val sha = sha256Hex(bytes)

        val uri = writeExportTempFile(context, exportFileName) { it.write(bytes) }
        val productionFile = File(context.appTempFolder, exportFileName)
        assertEquals("生产 IO 落盘字节数必须等于导出字节数", bytes.size.toLong(), productionFile.length())
        assertTrue(
            "生产 IO 落盘内容必须逐字节等于导出字节",
            productionFile.readBytes().contentEquals(bytes),
        )

        val pullDir = File(
            requireNotNull(context.getExternalFilesDir(null)) { "external files dir 为 null" },
            EXPORT_PULL_DIR,
        )
        assertTrue("导出 pull 目录建不出来：$pullDir", pullDir.mkdirs() || pullDir.isDirectory)
        val pullFile = File(pullDir, exportFileName)
        pullFile.writeBytes(bytes)
        assertEquals("pull 副本哈希必须与导出字节哈希一致", sha, sha256Hex(pullFile.readBytes()))

        return buildJsonObject {
            put("export_sha256", sha)
            put("export_bytes", bytes.size)
            put("export_line_count", exported.lines().count { it.isNotBlank() })
            put(
                "export_sha256_source",
                "same-process MessageDigest(\"SHA-256\") over production TavernChatCodec.exportGroupJsonl bytes, " +
                    "re-written through production writeExportTempFile and byte-compared",
            )
            put("export_path", pullFile.absolutePath)
            put("export_file_name", exportFileName)
            put("export_production_io_uri", uri.toString())
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ==================================================================
    // 证据 / 转储
    // ==================================================================

    private fun messageJson(message: UIMessage): JsonObject = buildJsonObject {
        put("id", message.id.toString())
        put("role", message.role.name)
        put("role_id", message.roleId)
        put("round_id", message.roundId)
        put("turn_kind", message.turnKind)
        put("model_id", message.modelId?.toString())
        put("wire_model_name", message.wireModelName)
        put("text", message.toText())
        message.usage?.let { usage ->
            putJsonObject("usage") {
                put("prompt", usage.promptTokens)
                put("completion", usage.completionTokens)
                put("total", usage.totalTokens)
            }
        }
    }

    private fun writeRawDump(
        name: String,
        conversationId: Uuid,
        roundId: String,
        messages: List<UIMessage>,
        run: GroupRunEntity,
        extraRun: GroupRunEntity? = null,
    ) {
        writeEvidence(
            name,
            buildJsonObject {
                put("raw_dump", true)
                put("attempt", attemptLabel)
                put("conversation_id", conversationId.toString())
                put("round_id", roundId)
                putJsonObject("run") { runRowPut(this, run) }
                extraRun?.let { extra -> putJsonObject("extra_run") { runRowPut(this, extra) } }
                putJsonArray("app_errors") {
                    chatManager.errors.value.forEach { error ->
                        add(
                            buildJsonObject {
                                put("title", error.title)
                                put("error_class", error.error::class.qualifiedName)
                                put("message", error.error.message)
                            },
                        )
                    }
                }
                putJsonArray("messages") { messages.forEach { add(messageJson(it)) } }
            },
        )
    }

    private fun runRowPut(obj: JsonObjectBuilder, run: GroupRunEntity) {
        obj.put("status", run.status)
        obj.put("reason", run.reason)
        obj.put("error_message", run.errorMessage)
        obj.put("spent_tokens", run.spentTokens)
        obj.put("token_limit", run.tokenLimit)
        obj.put("run_token", run.runToken)
        obj.put("committed_role_ids", run.committedRoleIds.joinToString(","))
        obj.put("skipped_role_ids", run.skippedRoleIds.joinToString(","))
        obj.put("started_at", run.startedAt)
        obj.put("ended_at", run.endedAt)
    }

    private fun writeEvidence(name: String, payload: JsonObject) {
        val dir = requireNotNull(context.getExternalFilesDir(null)) {
            "getExternalFilesDir(null) 不应为 null"
        }
        val file = File(dir, name)
        file.writeText(PRETTY.encodeToString(JsonElement.serializer(), payload))
        assertTrue("证据文件不应为空：${file.absolutePath}", file.length() > 0)
        trace("evidence:written $name bytes=${file.length()}")
    }

    private fun trace(step: String) {
        val line = "${System.currentTimeMillis()} $step"
        android.util.Log.i(TRACE_TAG, line)
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: return
            File(dir, TRACE_FILE).appendText(line + "\n")
        }
    }

    private companion object {
        const val EVIDENCE_DB = "c1-retry-evidence.db"
        const val TRACE_TAG = "C1Retry"
        const val TRACE_FILE = "c1-retry-trace.txt"

        /** 生产零产出失败文案（`ChatManager.kt:1957`）。 */
        const val EMPTY_OUTPUT_MARKER = "本轮没有产出内容"

        /** mock SSE 帧自报的 wire 模型名；别的名字 = 真网关顶包。 */
        val MOCK_WIRE_MODEL_NAMES = setOf("mock-retry-a", "mock-retry-b", "mock-retry-c")

        const val EXPORT_USER_NAME = "C1 验证用户"
        const val EXPORT_PULL_DIR = "c1-retry-export"

        val PRETTY = Json { prettyPrint = true; encodeDefaults = true }
    }
}

private fun resolveAppContext(): Application =
    InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
