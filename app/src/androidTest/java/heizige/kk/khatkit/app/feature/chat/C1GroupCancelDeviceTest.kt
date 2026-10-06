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
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FilesRepository
import heizige.kk.khatkit.app.core.data.repository.FolderRepository
import heizige.kk.khatkit.app.core.data.repository.MemoryExtractor
import heizige.kk.khatkit.app.core.di.appEntryPoint
import heizige.kk.khatkit.app.core.util.JsonInstant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
 * C1-06「取消」半条的真机证据：**边流式边取消**，断言契约
 * `docs/beyond-operit-client-changes.md:201,228` 的四条落地：
 *
 * 1. **取消/超时不得写入未生成的消息** —— 库内任何助手消息都不得是
 *    [isUngeneratedAssistantMessage]（空产出气泡）；被取消角色之后的角色一条消息都不落库。
 * 2. **已生成消息与错误节点保留** —— 第一轮让 B 的第一次请求走生产失败路径
 *    （零内容合法 SSE → `本轮没有产出内容`；或 HTTP 500 且 autoRetry 关闭 → 异常原样上抛），
 *    落成 `FAILED` / `role_failed` 与一个真实错误节点；取消第二轮后它必须原样还在；
 *    第二轮已完成的角色 a 消息保留；b 的部分产出**按 `modelId` 认领**（被取消的 b
 *    永远不会被盖章，`roleId`/`roundId` 恒为 null），必须非空落库。
 * 3. **已消耗 token 记入 `group_runs.spent_tokens`** —— 取消时 spent == 已提交角色 a 的
 *    `prompt+completion`，且 > 0；`token_limit` 是配置快照。
 * 4. **`status = CANCELLED`、`reason = cancelled`**，`ended_at` 非空，committed 只含 a。
 *
 * ## 为什么必须关掉 autoRetry（`NetworkSetting(enableAutoRetry = false)`）
 *
 * `SettingsRepository` 的读取路径会给缺失 id 补回全部内置 provider
 * （`SettingsRepository.kt:349-352`），其中内置「极客猫」`enabled=true`、带可用 API key
 * 且是唯一带 models 的默认 provider（`DefaultProviders.kt:282-317`）。于是
 * `ProviderFailover.buildChain` 总会把它列为 failover 候选（`GenerationLoop.kt:94`）。
 * 若 mock 的 500 错误消息里含 `"500"`，`ProviderFailover.isEligible` 判为可切换，
 * `hasNext` 又只看 `settings.networkSetting.enableAutoRetry`（`GenerationLoop.kt:165-166`），
 * B 的请求就会被重放给真网关顶包 —— 上一轮真机事故的根因。
 * 关掉 autoRetry 同时关掉内层网络重试（`:586-588`）与外层 failover，500 会原样
 * 抛到 `ChatManager.onFailure` → `failGroupTurn`。零内容路径本就不抛异常、不触发
 * failover，作为第二道保险保留。
 *
 * ## 端点与「边流式」怎么保证
 *
 * 设备侧 `adb reverse tcp:8766 tcp:8766` 打到开发机部署的
 * `/tmp/opencode/c1-cancel/mock_openai_slow.py`（入库版为
 * `tools/verification/mock_openai_slow_cancel.py`，SSE 形状与已入库的
 * `mock_openai_v2.py` 一致）。该 mock 对角色 **B 的第一个请求**按 `MOCK_EMPTY_ROLE`
 * （零内容，默认）或 `MOCK_FAIL_ROLE`（HTTP 500）注入一次，可被 `POST /__reset`
 * 清掉重放；对其余请求按角色节流：A 快（0.02s/chunk）、B 慢（0.5s/chunk，首块立即发）。
 *
 * 取消必须落在「b 正在流式」的窗口里。测试**不等 a 的消息落库就停** —— a 的消息由
 * `stampGroupTurn` 先保存（`ChatManager.kt:1953`），而 B 的生成要到
 * `handleMessageComplete` 递归返回后才发起（`:1059`）；只看见 a 就停，可能卡在
 * 「A 已提交、B 还没开始」的缝隙，`stopGeneration` 走 `jobs.isEmpty()` 分支
 * （`:2287-2289`）只写一行 CANCELLED，并没有真的边流式边取消。所以测试等到两个
 * **正向信号**同时成立才调 [ChatManager.stopGeneration]：
 * ① `group_runs` 行已含 committed a 且 `spent_tokens > 0`（`persistRoundState`
 * 在 `stampGroupTurn` 之后才跑，`ChatManager.kt:1975`，避免 spent 断言读到旧值）；
 * ② session 内存态的 `currentMessages` 里出现 `modelId == modelBId` 且正文非空的
 * 助手消息 —— b 的流式半截只在内存里（流式期间不落库，取消/结束后才由
 * `finishGeneration` 保存），所以这个信号必须读 `chatManager.getConversationFlow`
 * 的 StateFlow，读库看不到它。
 *
 * ⚠️ 取消时机的命中/未命中不隐瞒：若取消前整轮已自然完成，`run2.status` 会是 COMPLETED，
 * 断言当场失败，证据 JSON 里也记着实测状态。测试**不会**为了通过而放宽断言。
 *
 * ## 不做什么
 *
 * - 不改任何 `app/src/main` 生产代码。
 * - 超时路径**本轮不做**（`GROUP_ROUND_STEP_TIMEOUT_MS` 固定 15 分钟）。
 */
@RunWith(AndroidJUnit4::class)
class C1GroupCancelDeviceTest {

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

    /** 慢速 mock 地址；设备侧经 `adb reverse` 落到开发机同一个端口。 */
    private val mockBaseUrl = "http://127.0.0.1:8766/v1"

    private val attemptLabel: String by lazy {
        runCatching { InstrumentationRegistry.getArguments().getString("c1CancelAttempt") }
            .getOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: "x"
    }

    // ---------------- 固定 id ----------------

    private val providerId = Uuid.parse("0c1c06ca-0000-0000-0000-000000000001")
    private val modelAId = Uuid.parse("0c1c06ca-0000-0000-0000-00000000000a")
    private val modelBId = Uuid.parse("0c1c06ca-0000-0000-0000-00000000000b")
    private val modelCId = Uuid.parse("0c1c06ca-0000-0000-0000-00000000000c")
    private val assistantAId = Uuid.parse("0c1c06ca-0000-0000-0000-0000000000a1")
    private val assistantBId = Uuid.parse("0c1c06ca-0000-0000-0000-0000000000b1")
    private val assistantCId = Uuid.parse("0c1c06ca-0000-0000-0000-0000000000c1")

    /** mock 配置里的三个本地 model id；任何别的 modelId 出现即真网关顶包。 */
    private val mockModelIds get() = setOf(modelAId, modelBId, modelCId)

    private val caseName = "cancel"
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
    fun cancelMidStreamKeepsGeneratedMessagesAndErrorNodeWithoutEmptyBubbles() = runBlocking {
        settingsStore.update(cancelSettings())

        val conversationId = insertGroup()
        evidenceConversations += conversationId

        // ========= 第一轮：mock 注入一次失败，造一个生产路径错误节点 =========
        // 默认注入是零内容合法 SSE（生产判「本轮没有产出内容」）；也可用
        // MOCK_FAIL_ROLE=B 走 HTTP 500。cancelSettings() 已关掉 autoRetry，
        // 500 不会被 failover 重放给真网关（原因见类注释）。
        val round1SentAt = System.currentTimeMillis()
        chatManager.sendMessage(
            conversationId = conversationId,
            content = listOf(UIMessagePart.Text("第一轮：请三位依次发言，每位一句话。")),
            answer = true,
        )
        val user1 = awaitUserMessages(conversationId, expected = 1).last { it.role == MessageRole.USER }
        val round1Id = GroupChat.roundIdFor(user1.id.toString())
        val run1 = awaitTerminalRun(conversationId, round1Id, timeoutMillis = 120_000)
        trace("round1:terminal status=${run1.status} reason=${run1.reason}")

        val afterRound1 = loadMessages(conversationId)
        writeRawDump(
            "c1-cancel-raw-round1-$attemptLabel.json",
            conversationId,
            round1Id,
            afterRound1,
            run1,
        )
        val round1Assistants = afterRound1.filter {
            it.role == MessageRole.ASSISTANT && it.roundId == round1Id
        }
        // mock 来源守卫：真网关一旦顶包（failover 重放），会留下自己的
        // modelId / wireModelName（如极客猫的 deepseek-v4-flash），这里直接抓住。
        val realGatewayLeaks = round1Assistants.filter { message ->
            message.toText().isNotBlank() &&
                message.turnKind != GroupChat.TURN_ERROR &&
                (message.modelId !in mockModelIds ||
                    (message.wireModelName != null && message.wireModelName !in MOCK_WIRE_MODEL_NAMES))
        }
        assertTrue(
            "第一轮不得有真网关顶包的产出（failover 会带进非 mock 的 modelId/wireModelName），实际=" +
                realGatewayLeaks.map {
                    "${it.roleId}:${it.modelId}:${it.wireModelName}:${it.toText().take(30)}"
                },
            realGatewayLeaks.isEmpty(),
        )
        assertTrue(
            "第一轮失败说明必须来自 mock 注入（零内容「本轮没有产出内容」或 500 注入标记），" +
                "实际 error_message=${run1.errorMessage}",
            run1.errorMessage.contains("本轮没有产出内容") ||
                run1.errorMessage.contains(MOCK_500_MARKER),
        )
        val errorNode = round1Assistants.firstOrNull {
            it.roleId == "b" && it.turnKind == GroupChat.TURN_ERROR
        }
        assertNotNull(
            "第一轮必须由生产失败路径写出 b 的错误节点，实际回合消息=" +
                round1Assistants.map { "${it.roleId}:${it.turnKind}:${it.toText().take(30)}" },
            errorNode,
        )
        val errorNodeId = requireNotNull(errorNode).id
        assertTrue(
            "错误节点正文应带生产前缀「本轮生成失败」且携带 mock 注入标记，实际=${errorNode.toText()}",
            errorNode.toText().contains("本轮生成失败") &&
                (errorNode.toText().contains("本轮没有产出内容") ||
                    errorNode.toText().contains(MOCK_500_MARKER)),
        )
        assertTrue(
            "第一轮角色 a 的已生成消息必须存在",
            round1Assistants.any { it.roleId == "a" && it.toText().isNotBlank() },
        )
        assertTrue(
            "第一轮角色 c 不该有消息（b 失败即停轮）",
            round1Assistants.none { it.roleId == "c" },
        )

        // ================= 第二轮：边流式边取消 =================
        val round2SentAt = System.currentTimeMillis()
        chatManager.sendMessage(
            conversationId = conversationId,
            content = listOf(UIMessagePart.Text("第二轮：继续展开细节。")),
            answer = true,
        )
        val user2 = awaitUserMessages(conversationId, expected = 2).last { it.role == MessageRole.USER }
        val round2Id = GroupChat.roundIdFor(user2.id.toString())
        trace("round2:trigger=${user2.id} round=$round2Id")

        // 等两个正向信号同时成立才停：
        // ① run 行已含 committed a 且 spent>0 —— A 的提交（含 spent 累加）确实落库，
        //    不会出现「看见 a 消息、persistRoundState 还没跑」的 spent 旧值竞态；
        // ② session 内存态出现 modelId==modelBId 的非空助手消息 —— B 真的开始流式了。
        // 只等 a 落库就停可能卡在 A→B 的缝隙里，stopGeneration 会走 jobs.isEmpty()
        // 分支只写一行 CANCELLED，并没有边流式边取消（原因见类注释）。
        // b 的流式半截只在内存里（不落库），所以信号必须读 chatManager 的 StateFlow。
        val liveMessagesFlow = chatManager.getConversationFlow(conversationId)
        var a2ObservedAt = 0L
        var b2StreamObservedAt = 0L
        awaitCondition(
            timeoutMillis = 120_000,
            what = "round2 a committed + b mid-stream",
            pollMillis = 200,
        ) {
            val now = System.currentTimeMillis()
            val runRow = groupRunDao.findByRound(conversationId.toString(), round2Id)
            val aCommitted = runRow != null &&
                runRow.committedRoleIds.contains("a") &&
                runRow.spentTokens > 0
            if (aCommitted && a2ObservedAt == 0L) a2ObservedAt = now
            val bStreaming = liveMessagesFlow.value.currentMessages.any { message ->
                message.role == MessageRole.ASSISTANT &&
                    message.modelId == modelBId &&
                    message.toText().isNotBlank()
            }
            if (aCommitted && bStreaming && b2StreamObservedAt == 0L) b2StreamObservedAt = now
            aCommitted && bStreaming
        }

        val stopCalledAt = System.currentTimeMillis()
        chatManager.stopGeneration(conversationId)
        val stopReturnedAt = System.currentTimeMillis()
        trace(
            "round2:stop-returned after ${stopReturnedAt - stopCalledAt}ms " +
                "(b streaming observed ${stopCalledAt - b2StreamObservedAt}ms before stop)",
        )

        val run2 = awaitTerminalRun(conversationId, round2Id, timeoutMillis = 60_000)
        trace("round2:terminal status=${run2.status} reason=${run2.reason} spent=${run2.spentTokens}")
        val finalMessages = loadMessages(conversationId)
        writeRawDump(
            "c1-cancel-raw-final-$attemptLabel.json",
            conversationId,
            round2Id,
            finalMessages,
            run2,
            extraRun = run1,
        )

        // ---------- 断言 1：库内没有空气泡（生产判据原样复用） ----------
        val emptyBubbles = finalMessages.filter { it.isUngeneratedAssistantMessage() }
        assertTrue(
            "取消后库内不得出现未生成助手消息（空气泡），实际=" +
                emptyBubbles.map { "${it.id}:${it.roundId}" },
            emptyBubbles.isEmpty(),
        )

        // ---------- 断言 2：已生成消息与错误节点保留 ----------
        val finalAssistants = finalMessages.filter { it.role == MessageRole.ASSISTANT }
        val a2 = finalAssistants.firstOrNull { it.roundId == round2Id && it.roleId == "a" }
        assertNotNull("第二轮角色 a 的已生成消息必须保留", a2)
        assertTrue("第二轮角色 a 的消息不得为空", requireNotNull(a2).toText().isNotBlank())

        // 被取消的 b 永远不会被盖章（stampGroupTurn 只在角色成功提交时调用），它的
        // roundId/roleId 恒为 null；HEAD 里的旧过滤器 `roundId == round2Id && roleId == "b"`
        // 对它恒空，`b2.all{}` 于是恒真 —— 空断言。按 modelBId 认领才能看到
        // finishGeneration 保存下来的部分产出（本地 JVM 已用真实 UIMessage 证过：
        // 旧过滤器对真实形态 0 命中，新过滤器恰好命中且能排除真网关顶包）。
        val b2 = cancelledRolePartials(finalAssistants, modelBId)
        assertTrue(
            "取消前已观测到 b 正向流式信号，部分产出必须落库（非空气泡契约），实际=" +
                b2.map { "${it.id}:round=${it.roundId}:role=${it.roleId}:${it.toText().take(40)}" },
            b2.isNotEmpty(),
        )
        assertTrue(
            "被取消的 b 的部分产出必须非空，实际=" + b2.map { it.toText().take(40) },
            b2.all { it.toText().isNotBlank() },
        )
        assertTrue(
            "被取消的 b 不得被盖章成已提交角色（它不在 committed 里）",
            b2.none { it.roleId != null || it.roundId != null },
        )
        assertTrue(
            "第二轮不得出现 c 的消息（取消发生在 b 之后、c 之前）",
            finalAssistants.none { it.roundId == round2Id && it.roleId == "c" },
        )
        val errorNodeStillThere = finalMessages.any { it.id == errorNodeId }
        assertTrue("第一轮的错误节点在取消后必须原样保留", errorNodeStillThere)
        assertTrue(
            "取消不得伪造任何新的错误节点（第二轮 b 被取消不等于失败）",
            finalAssistants.none {
                it.roundId == round2Id && it.turnKind == GroupChat.TURN_ERROR
            },
        )

        // ---------- 断言 3：已消耗 token 记入 spent_tokens ----------
        val a2Usage = requireNotNull(requireNotNull(a2).usage) {
            "第二轮 a 的 usage 必须来自真实响应（mock SSE trailer）"
        }
        val a2Tokens = a2Usage.promptTokens + a2Usage.completionTokens
        assertTrue("a 的 prompt+completion 必须为正，实际=$a2Tokens", a2Tokens > 0)
        assertEquals(
            "取消时 spent_tokens 必须等于已提交角色 a 的 prompt+completion",
            a2Tokens,
            run2.spentTokens,
        )
        assertEquals("token_limit 必须是配置快照", budget, run2.tokenLimit)

        // ---------- 断言 4：status/reason ----------
        assertEquals(
            "本轮必须以 CANCELLED 收尾（若为 COMPLETED 说明取消未命中流式窗口）",
            GroupRunEntity.STATUS_CANCELLED,
            run2.status,
        )
        assertEquals("reason 必须是 cancelled", GroupRunEntity.REASON_CANCELLED, run2.reason)
        assertNotNull("终态必须有 ended_at", run2.endedAt)
        assertEquals("committed 只应包含已提交的 a", listOf("a"), run2.committedRoleIds)
        assertTrue("cancel 不写 error_message", run2.errorMessage.isBlank())

        // 第一轮运行日志一起作为「错误节点保留」的旁证。
        assertEquals(GroupRunEntity.STATUS_FAILED, run1.status)
        assertEquals(GroupRunEntity.REASON_ROLE_FAILED, run1.reason)

        // ================= 证据 =================
        writeEvidence(
            "c1-device-cancel-report-$attemptLabel.json",
            buildJsonObject {
                put("case", "C1-06 cancel mid-stream")
                put("attempt", attemptLabel)
                putJsonObject("device") {
                    put("model", Build.MODEL)
                    put("sdk", Build.VERSION.SDK_INT)
                    put("release", Build.VERSION.RELEASE)
                    put("abi", Build.SUPPORTED_ABIS.joinToString(","))
                }
                put("mock_base_url", mockBaseUrl)
                put(
                    "mock_server",
                    "/tmp/opencode/c1-cancel/mock_openai_slow.py (repo: " +
                        "tools/verification/mock_openai_slow_cancel.py; first B request -> " +
                        "MOCK_EMPTY_ROLE zero-content SSE or MOCK_FAIL_ROLE HTTP 500, both mock-marked; " +
                        "enableAutoRetry=false so no failover replay to a real gateway; B slow-stream 0.5s/chunk)",
                )
                putJsonObject("conversation") {
                    put("id", conversationId.toString())
                    put("group_mode", GroupChat.MODE_PIPELINE)
                    put("budget", budget)
                    put("round1_id", round1Id)
                    put("round2_id", round2Id)
                }
                putJsonObject("timing_millis") {
                    put("round1_sent", round1SentAt)
                    put("round2_sent", round2SentAt)
                    put("round2_a_committed_observed", a2ObservedAt)
                    put("round2_b_stream_observed", b2StreamObservedAt)
                    put("b_stream_observed_to_stop_called", stopCalledAt - b2StreamObservedAt)
                    put("stop_called", stopCalledAt)
                    put("stop_returned", stopReturnedAt)
                    put("stop_call_duration", stopReturnedAt - stopCalledAt)
                }
                putJsonObject("run1_failure_round") {
                    put("status", run1.status)
                    put("reason", run1.reason)
                    put("spent_tokens", run1.spentTokens)
                    put("committed_role_ids", run1.committedRoleIds.joinToString(","))
                    put("error_message", run1.errorMessage)
                }
                putJsonObject("run2_cancel_round") {
                    put("status", run2.status)
                    put("reason", run2.reason)
                    put("spent_tokens", run2.spentTokens)
                    put("token_limit", run2.tokenLimit)
                    put("committed_role_ids", run2.committedRoleIds.joinToString(","))
                    put("skipped_role_ids", run2.skippedRoleIds.joinToString(","))
                    put("ended_at", run2.endedAt)
                }
                put("assert_expected", buildJsonObject {
                    put("status", GroupRunEntity.STATUS_CANCELLED)
                    put("reason", GroupRunEntity.REASON_CANCELLED)
                    put("empty_bubbles", 0)
                    put("committed", "a")
                })
                putJsonObject("observed") {
                    put("empty_bubble_count", emptyBubbles.size)
                    put("a2_present", a2 != null)
                    put("a2_prompt_completion", a2Tokens)
                    put("b2_partial_message_count", b2.size)
                    put("b2_texts", b2.joinToString(" | ") { it.toText().take(80) })
                    put("c2_message_count", finalAssistants.count { it.roundId == round2Id && it.roleId == "c" })
                    put("error_node_from_round1_still_present", errorNodeStillThere)
                    put("new_error_nodes_in_round2", finalAssistants.count {
                        it.roundId == round2Id && it.turnKind == GroupChat.TURN_ERROR
                    })
                }
                putJsonArray("messages_after_cancel") {
                    finalMessages.forEach { message ->
                        add(
                            buildJsonObject {
                                put("id", message.id.toString())
                                put("role", message.role.name)
                                put("role_id", message.roleId)
                                put("round_id", message.roundId)
                                put("turn_kind", message.turnKind)
                                put("text", message.toText())
                                put("is_ungenerated_assistant", message.isUngeneratedAssistantMessage())
                                message.usage?.let { usage ->
                                    put("usage_prompt", usage.promptTokens)
                                    put("usage_completion", usage.completionTokens)
                                }
                            },
                        )
                    }
                }
                put(
                    "note",
                    "production code untouched; test settings disable networkSetting.enableAutoRetry so a " +
                        "mock-injected 500 cannot be failover-replayed to a real gateway (round1 provenance: " +
                        "modelId/wireModelName must be mock's + error_message carries the mock marker); round2 " +
                        "waits for a committed AND b mid-stream (in-memory signal) before stopGeneration; b2 is " +
                        "claimed by modelId because a cancelled role is never stamped by stampGroupTurn",
                )
            },
        )

        println("C1-CANCEL-DEVICE-BEGIN")
        println("attempt=$attemptLabel status=${run2.status} reason=${run2.reason} spent=${run2.spentTokens} a2Tokens=$a2Tokens")
        println("emptyBubbles=${emptyBubbles.size} b2=${b2.size} c2=${finalAssistants.count { it.roundId == round2Id && it.roleId == "c" }} errorNodeKept=$errorNodeStillThere")
        println("C1-CANCEL-DEVICE-END")
    }

    // ==================================================================
    // 设置 / 夹具
    // ==================================================================

    private fun persona(code: String) = buildString {
        append("你是 KhatKit C1 取消用例角色。CASE:").append(caseName).append(" ")
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

    private fun cancelSettings() = Settings(
        init = false,
        chatModelId = modelAId,
        fastModelId = modelAId,
        // 关键：关掉 autoRetry。读取路径会给缺失 id 补回内置「极客猫」（enabled、带 key、
        // 唯一带 models），它永远在 failover 链上；开着 autoRetry 时 mock 的 500 消息
        // 含 "500" 会被 isEligible 判为可切换，B 的请求被重放给真网关顶包。
        // 关掉它 = 内层网络重试与外层 failover 同时关闭，500 原样上抛到生产失败路径。
        networkSetting = NetworkSetting(enableAutoRetry = false),
        providers = listOf(
            ProviderSetting.OpenAI(
                id = providerId,
                enabled = true,
                name = "c1-cancel-mock",
                apiKey = "c1-cancel-mock-key",
                baseUrl = mockBaseUrl,
                chatCompletionsPath = "/chat/completions",
                useResponseApi = false,
                models = listOf(
                    Model(modelId = "mock-cancel-a", displayName = "C1 Cancel A", id = modelAId, type = ModelType.CHAT),
                    Model(modelId = "mock-cancel-b", displayName = "C1 Cancel B", id = modelBId, type = ModelType.CHAT),
                    Model(modelId = "mock-cancel-c", displayName = "C1 Cancel C", id = modelCId, type = ModelType.CHAT),
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
            GroupRole(id = "a", name = "角色甲", assistantId = assistantAId.toString(), modelId = modelAId.toString(), cardId = "card-cancel-a"),
            GroupRole(id = "b", name = "角色乙", assistantId = assistantBId.toString(), modelId = modelBId.toString(), cardId = "card-cancel-b"),
            GroupRole(id = "c", name = "角色丙", assistantId = assistantCId.toString(), chair = true, modelId = modelCId.toString(), cardId = "card-cancel-c"),
        ),
        mode = GroupChat.MODE_PIPELINE,
        chairRoleId = "c",
        tokenBudgetPerRound = budget,
    )

    private suspend fun insertGroup(): Uuid {
        val conversation = Conversation(
            assistantId = assistantAId,
            title = "C1 cancel $caseName",
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
    // 轮询辅助
    // ==================================================================

    private suspend fun loadMessages(conversationId: Uuid): List<UIMessage> =
        repository.getConversationById(conversationId)?.currentMessages.orEmpty()

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

    private fun trace(step: String) {
        val line = "${System.currentTimeMillis()} $step"
        android.util.Log.i(TRACE_TAG, line)
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: return
            File(dir, TRACE_FILE).appendText(line + "\n")
        }
    }

    /**
     * 无条件原始转储：**不管断言是否通过**都在现场读一遍并落盘（成功/失败都有证据）。
     *
     * 存在的理由：第一版用例把 500 注入轮的「b 的消息从哪来」判错了，
     * 而失败时证据 DB 会被 tearDown 删掉——没有现场就查不出 `modelId` / `wireModelName`。
     */
    private fun writeRawDump(
        name: String,
        conversationId: Uuid,
        roundId: String,
        messages: List<UIMessage>,
        run: GroupRunEntity,
        extraRun: GroupRunEntity? = null,
    ) {
        val payload = buildJsonObject {
            put("raw_dump", true)
            put("attempt", attemptLabel)
            put("conversation_id", conversationId.toString())
            put("round_id", roundId)
            putJsonObject("run") { runRowPut(this, run) }
            extraRun?.let { extra ->
                putJsonObject("extra_run") { runRowPut(this, extra) }
            }
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
                            put("wire_model_name", message.wireModelName)
                            put("text", message.toText())
                            putJsonArray("part_types") {
                                message.parts.forEach { part ->
                                    add(kotlinx.serialization.json.JsonPrimitive(part::class.simpleName ?: "?"))
                                }
                            }
                            message.usage?.let { usage ->
                                putJsonObject("usage") {
                                    put("prompt", usage.promptTokens)
                                    put("completion", usage.completionTokens)
                                    put("total", usage.totalTokens)
                                }
                            }
                        },
                    )
                }
            }
        }
        writeEvidence(name, payload)
    }

    private fun runRowPut(
        obj: kotlinx.serialization.json.JsonObjectBuilder,
        run: GroupRunEntity,
    ) {
        obj.put("status", run.status)
        obj.put("reason", run.reason)
        obj.put("error_message", run.errorMessage)
        obj.put("spent_tokens", run.spentTokens)
        obj.put("token_limit", run.tokenLimit)
        obj.put("run_token", run.runToken)
        obj.put("committed_role_ids", run.committedRoleIds.joinToString(","))
        obj.put("skipped_role_ids", run.skippedRoleIds.joinToString(","))
        obj.put("ended_at", run.endedAt)
    }

    private fun writeEvidence(name: String, payload: kotlinx.serialization.json.JsonObject) {
        val dir = requireNotNull(context.getExternalFilesDir(null)) {
            "getExternalFilesDir(null) 不应为 null"
        }
        val file = File(dir, name)
        file.writeText(PRETTY.encodeToString(JsonElement.serializer(), payload))
        assertTrue("证据文件不应为空：${file.absolutePath}", file.length() > 0)
        trace("evidence:written $name bytes=${file.length()}")
    }

    private companion object {
        const val EVIDENCE_DB = "c1-cancel-evidence.db"
        const val TRACE_TAG = "C1Cancel"
        const val TRACE_FILE = "c1-cancel-trace.txt"

        /** mock 500 响应体里的注入标记（`tools/verification/mock_openai_slow_cancel.py`）。 */
        const val MOCK_500_MARKER = "c1-cancel mock injected 500"

        /** mock SSE 帧自报的 wire 模型名；别的名字 = 真网关顶包。 */
        val MOCK_WIRE_MODEL_NAMES = setOf("mock-cancel-a", "mock-cancel-b", "mock-cancel-c")

        val PRETTY = Json { prettyPrint = true; encodeDefaults = true }
    }
}

/**
 * 认领「被取消角色」留下但永远盖不上章的消息：只有 `modelId` 是可用的稳定身份
 * （`roleId`/`roundId` 都是 null）。抽成函数是为了让这条过滤判据可以被单独审视 ——
 * 本机 JVM 已用真实 [UIMessage] 验证过：旧判据对真实形态 0 命中（`all{}` 恒真），
 * 本条恰好命中被取消的 b，且能排除已提交的 a、第一轮错误节点与真网关顶包。
 */
private fun cancelledRolePartials(messages: List<UIMessage>, modelId: Uuid): List<UIMessage> =
    messages.filter { it.role == MessageRole.ASSISTANT && it.modelId == modelId }

private fun resolveAppContext(): Application =
    InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
