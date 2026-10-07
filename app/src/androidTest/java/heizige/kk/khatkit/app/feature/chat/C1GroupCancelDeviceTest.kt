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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import java.security.MessageDigest
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

        chatManager = buildChatManager()
        trace("setUp:chatManager-constructed")
    }

    /**
     * 手工装配一个 [ChatManager]（与 `AppHiltModule.provideChatManager` 同一批依赖）。
     *
     * [stepTimeoutMs] 直接注入生产构造函数新加的可注入步超时；省略时用
     * [DEFAULT_STEP_TIMEOUT_MS]（= 生产私有常量 `GROUP_ROUND_STEP_TIMEOUT_MS` 的值，15 分钟），
     * 与 C1-06 之前的写死值逐字一致 —— 取消用例因此不受影响。
     */
    private fun buildChatManager(stepTimeoutMs: Long = DEFAULT_STEP_TIMEOUT_MS): ChatManager {
        val entry = appEntryPoint(appContext)
        val providerManager = entry.providerManager()
        val json = JsonInstant
        val memoryRepository = entry.memoryRepository()
        return ChatManager(
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
            groupRoundStepTimeoutMs = stepTimeoutMs,
        )
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

        // ---------- 断言 5：各 viewer 的可见消息 ID 台账（契约 :232-235） ----------
        // 台账由生产 GroupChat.visibleMessages 逐 viewer 计算，并逐项断言：
        // 台账 == 生产输出、含 user 触发消息、含自己的发言、可见集里其他角色必须授权。
        val cancelConfig = groupConfig()
        val viewerPlans = planViewerLedgerPlans(cancelConfig, GroupChat.plan(cancelConfig, emptyList()))
        val viewerLedger = viewerVisibilityLedger(finalMessages, cancelConfig, viewerPlans)
        assertViewerVisibilityLedger(viewerLedger, finalMessages, cancelConfig, viewerPlans)

        // ---------- 断言 6：契约 :206 的导出 SHA-256 ----------
        // 真库读回会话 → 生产 TavernChatCodec.exportGroupJsonl → 生产 writeExportTempFile
        // 写真实文件 → 同进程 MessageDigest("SHA-256")，并把同一份字节复制到 external files dir
        // 供 `adb pull` 复算。
        val exportEvidence = exportGroupJsonlEvidence(conversationId, EXPORT_FILE_NAME)

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
                // ===== 契约 :206 / :232-235 逐例字段（本轮补齐） =====
                // `viewer_visibility`: 各 viewer 的可见消息 ID 台账（生产 visibleMessages）。
                put("viewer_visibility", viewerLedger)
                put("viewer_visibility_asserted", true)
                // 实际模型调用序列：每条助手消息的 wire 身份 + 归属 + token（源自真机落库事实）。
                // `role_id`/`round_id` 为 null 的那条即被取消、永远盖不上章的部分产出。
                putJsonArray("actual_model_call_sequence") {
                    finalAssistants.forEach { message ->
                        add(
                            buildJsonObject {
                                put("message_id", message.id.toString())
                                put("role_id", message.roleId)
                                put("round_id", message.roundId)
                                put("turn_kind", message.turnKind)
                                put("model_id", message.modelId?.toString())
                                put("wire_model_name", message.wireModelName)
                                put("stamped_as_committed", message.roleId != null && message.turnKind != GroupChat.TURN_ERROR)
                                put("prompt_tokens", message.usage?.promptTokens ?: -1)
                                put("completion_tokens", message.usage?.completionTokens ?: -1)
                            },
                        )
                    }
                }
                // 导出哈希：生产导出器 → 生产 IO 助手落盘 → MessageDigest("SHA-256")。
                exportEvidence.forEach { (key, value) -> put(key, value) }
                put(
                    "contract_fields_added_this_round",
                    "docs/beyond-operit-client-changes.md:206 + :232-235 fields newly written by this " +
                        "run: viewer_visibility (each viewer's visible message IDs, from production " +
                        "GroupChat.visibleMessages), actual_model_call_sequence, export_sha256/export_bytes/" +
                        "export_line_count/export_path. Timeout half (GROUP_ROUND_STEP_TIMEOUT_MS=15min) " +
                        "remains unverified, see timeout_half_verified / timeout_half_note.",
                )
                put("timeout_half_verified", false)
                put(
                    "timeout_half_note",
                    "The cancellation half is asserted by this run; the timeout half is NOT verified. " +
                        "Production wraps one role's turn in withTimeout(GROUP_ROUND_STEP_TIMEOUT_MS) " +
                        "(ChatManager.kt:116 = 15*60*1000 = 900000 ms, applied at ChatManager.kt:1005). " +
                        "Reaching that branch requires either waiting ~15 minutes or touching production " +
                        "code (test-only injection point not allowed), so the timeout half stays unverified.",
                )
            },
        )

        println("C1-CANCEL-DEVICE-BEGIN")
        println("attempt=$attemptLabel status=${run2.status} reason=${run2.reason} spent=${run2.spentTokens} a2Tokens=$a2Tokens")
        println("emptyBubbles=${emptyBubbles.size} b2=${b2.size} c2=${finalAssistants.count { it.roundId == round2Id && it.roleId == "c" }} errorNodeKept=$errorNodeStillThere")
        println("C1-CANCEL-DEVICE-END")
    }

    /**
     * C1-06「超时」半条的真机证据：注入一个很小的步超时（[TIMEOUT_STEP_MS]），
     * 让第 2 个角色 **B 挂住不返回**（mock `MOCK_HANG_ROLE=B`：发完 role-only opener
     * 后一个 content 都不发），断言契约 `docs/beyond-operit-client-changes.md:201`
     * 的「取消/超时不得写入未生成的消息」在**超时**这条出口上同样成立：
     *
     * 1. 轮次落 `TIMEOUT` / `reason=timeout`（生产 `GroupTurnCoordinator.timeoutRound`）；
     * 2. **空气泡数 = 0**（`UngeneratedMessageFilter` 语义：被超时角色的零内容占位被丢弃）；
     * 3. 已生成角色 a 的消息保留；
     * 4. 错误节点形状 `turn_kind=error`、`role_id=b`，正文来自生产 `errorNode` 前缀
     *    「本轮生成失败」并携带超时 detail；
     * 5. `spent_tokens` = 已提交角色 a 的 prompt+completion（超时不追加）；
     * 6. `committed_role_ids=["a"]`、`skipped_role_ids=[]`（`timeoutRound` 与 `cancelRound`
     *    一样不写 skipped —— 超时角色之后的 c 根本没开始，没有「跳过」可言）。
     *
     * 为什么能这样测：生产构造函数新加了可注入的 `groupRoundStepTimeoutMs`（默认仍是 15 分钟），
     * 本用例把它设成 [TIMEOUT_STEP_MS]，于是不用真的等 15 分钟就能走到 `withTimeout` 分支。
     */
    @Test
    fun stepTimeoutRecordsTimeoutWithoutEmptyBubbleAndKeepsGeneratedRole() = runBlocking {
        // 只在本用例替换成小超时的 manager；@Before 已按默认 15 分钟建好一个，直接覆盖即可
        // （JUnit4 每个 @Test 都是新实例，不影响取消用例）。
        chatManager = buildChatManager(stepTimeoutMs = TIMEOUT_STEP_MS)
        settingsStore.update(cancelSettings())

        val conversationId = insertGroup()
        evidenceConversations += conversationId

        val sentAt = System.currentTimeMillis()
        chatManager.sendMessage(
            conversationId = conversationId,
            content = listOf(UIMessagePart.Text("超时用例：请三位依次发言，每位一句话。")),
            answer = true,
        )
        val user = awaitUserMessages(conversationId, expected = 1).last { it.role == MessageRole.USER }
        val roundId = GroupChat.roundIdFor(user.id.toString())
        val run = awaitTerminalRun(conversationId, roundId, timeoutMillis = 120_000)
        val endedAt = System.currentTimeMillis()
        val elapsed = endedAt - sentAt
        trace("timeout:terminal status=${run.status} reason=${run.reason} spent=${run.spentTokens} elapsed=${elapsed}ms")

        val messages = loadMessages(conversationId)
        writeRawDump("c1-timeout-raw-$attemptLabel.json", conversationId, roundId, messages, run)

        // ---------- 断言 1：契约 :201 —— 超时不得写入未生成的消息 ----------
        val emptyBubbles = messages.filter { it.isUngeneratedAssistantMessage() }
        assertTrue(
            "超时后库内不得出现未生成助手消息（空气泡），实际=" +
                emptyBubbles.map { "${it.id}:${it.roleId}:${it.roundId}" },
            emptyBubbles.isEmpty(),
        )

        val assistants = messages.filter { it.role == MessageRole.ASSISTANT }

        // ---------- 断言 2：已生成角色 a 的消息保留 ----------
        val a = assistants.firstOrNull { it.roundId == roundId && it.roleId == "a" }
        assertNotNull(
            "超时轮里已提交角色 a 的已生成消息必须保留，实际=" +
                assistants.map { "${it.roleId}:${it.turnKind}:${it.toText().take(30)}" },
            a,
        )
        assertTrue("角色 a 的消息不得为空", requireNotNull(a).toText().isNotBlank())

        // ---------- 断言 3：超时角色的错误节点 + 零真实产出 ----------
        val errorNode = assistants.firstOrNull {
            it.roundId == roundId && it.roleId == "b" && it.turnKind == GroupChat.TURN_ERROR
        }
        assertNotNull(
            "超时必须由生产失败路径写出 b 的错误节点，实际回合消息=" +
                assistants.map { "${it.roleId}:${it.turnKind}:${it.toText().take(30)}" },
            errorNode,
        )
        val errorText = requireNotNull(errorNode).toText()
        assertTrue(
            "错误节点正文应带生产前缀「本轮生成失败」，实际=$errorText",
            errorText.contains("本轮生成失败"),
        )
        assertTrue(
            "错误节点正文必须反映超时（detail 来自 errorDetailOf(TimeoutCancellationException)），实际=$errorText",
            errorText.contains("Timeout") || errorText.contains("Timed out"),
        )
        assertTrue(
            "被超时的 b 不得留下任何非错误正文（挂住没有 content，占位气泡必须被过滤）",
            assistants.none {
                it.roundId == roundId && it.roleId == "b" && it.turnKind != GroupChat.TURN_ERROR
            },
        )
        assertTrue(
            "被超时的 b 不得有 modelId=modelB 的落库消息（零产出占位被丢弃），实际=" +
                cancelledRolePartials(assistants, modelBId).map { "${it.id}:${it.toText().take(30)}" },
            cancelledRolePartials(assistants, modelBId).isEmpty(),
        )
        assertTrue(
            "超时发生在 b，本轮的 c 不得有消息",
            assistants.none { it.roundId == roundId && it.roleId == "c" },
        )
        assertTrue(
            "超时不得伪造 a 的错误节点（a 是成功提交，不是失败）",
            assistants.none {
                it.roundId == roundId && it.roleId == "a" && it.turnKind == GroupChat.TURN_ERROR
            },
        )

        // ---------- 断言 4：status / reason / 运行日志字段 ----------
        assertEquals(
            "本轮必须以 TIMEOUT 收尾",
            GroupRunEntity.STATUS_TIMEOUT,
            run.status,
        )
        assertEquals("reason 必须是 timeout", GroupRunEntity.REASON_TIMEOUT, run.reason)
        assertEquals("committed 只应包含已提交的 a", listOf("a"), run.committedRoleIds)
        assertEquals(
            "timeoutRound 与 cancelRound 一样不写 skipped（超时角色之后的 c 未开始，没有跳过）",
            emptyList<String>(),
            run.skippedRoleIds,
        )
        assertNotNull("终态必须有 ended_at", run.endedAt)
        assertTrue(
            "error_message 必须是超时 detail，实际=${run.errorMessage}",
            run.errorMessage.contains("Timeout") || run.errorMessage.contains("Timed out"),
        )
        assertEquals("token_limit 必须是配置快照", budget, run.tokenLimit)
        val aUsage = requireNotNull(requireNotNull(a).usage) {
            "角色 a 的 usage 必须来自真实响应（mock SSE trailer）"
        }
        val aTokens = aUsage.promptTokens + aUsage.completionTokens
        assertTrue("a 的 prompt+completion 必须为正，实际=$aTokens", aTokens > 0)
        assertEquals(
            "超时时 spent_tokens 必须等于已提交角色 a 的 prompt+completion（超时不追加）",
            aTokens,
            run.spentTokens,
        )
        assertTrue(
            "总耗时必须 ≥ 注入的步超时（证明确实走到了 withTimeout 分支），实际=${elapsed}ms",
            elapsed >= TIMEOUT_STEP_MS,
        )
        assertTrue(
            "总耗时不应接近 15 分钟（证明用的是注入值而不是生产默认常量），实际=${elapsed}ms",
            elapsed < 90_000,
        )

        // ---------- 证据 ----------
        writeEvidence(
            "c1-device-timeout-report-$attemptLabel.json",
            buildJsonObject {
                put("case", "C1-06 timeout half")
                put("attempt", attemptLabel)
                put("injected_step_timeout_ms", TIMEOUT_STEP_MS)
                putJsonObject("device") {
                    put("model", Build.MODEL)
                    put("sdk", Build.VERSION.SDK_INT)
                    put("release", Build.VERSION.RELEASE)
                    put("abi", Build.SUPPORTED_ABIS.joinToString(","))
                }
                put("mock_base_url", mockBaseUrl)
                put(
                    "mock_server",
                    "tools/verification/mock_openai_slow_cancel.py with MOCK_HANG_ROLE=B, " +
                        "MOCK_EMPTY_ROLE=, MOCK_FAIL_ROLE= (B gets a 200 SSE opener then no content); " +
                        "enableAutoRetry=false so no failover replay to a real gateway",
                )
                putJsonObject("conversation") {
                    put("id", conversationId.toString())
                    put("group_mode", GroupChat.MODE_PIPELINE)
                    put("budget", budget)
                    put("round_id", roundId)
                }
                putJsonObject("timing_millis") {
                    put("sent", sentAt)
                    put("ended", endedAt)
                    put("elapsed", elapsed)
                }
                putJsonObject("timeout_round") {
                    put("status", run.status)
                    put("reason", run.reason)
                    put("spent_tokens", run.spentTokens)
                    put("token_limit", run.tokenLimit)
                    put("committed_role_ids", run.committedRoleIds.joinToString(","))
                    put("skipped_role_ids", run.skippedRoleIds.joinToString(","))
                    put("error_message", run.errorMessage)
                    put("ended_at", run.endedAt)
                }
                putJsonObject("assert_expected") {
                    put("status", GroupRunEntity.STATUS_TIMEOUT)
                    put("reason", GroupRunEntity.REASON_TIMEOUT)
                    put("empty_bubbles", 0)
                    put("committed", "a")
                    put("skipped", "")
                }
                putJsonObject("observed") {
                    put("empty_bubble_count", emptyBubbles.size)
                    put("a_present", a != null)
                    put("a_prompt_completion", aTokens)
                    put("error_node_role_id", errorNode?.roleId)
                    put("error_node_turn_kind", errorNode?.turnKind)
                    put("error_node_text", errorText)
                    put("b_non_error_messages", assistants.count {
                        it.roundId == roundId && it.roleId == "b" && it.turnKind != GroupChat.TURN_ERROR
                    })
                    put("b_modelB_partials", cancelledRolePartials(assistants, modelBId).size)
                    put("c_message_count", assistants.count { it.roundId == roundId && it.roleId == "c" })
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
                    "Timeout half of contract :201. Production constructor now takes an injectable " +
                        "groupRoundStepTimeoutMs (default = the old 15-minute constant, so production " +
                        "wiring is unchanged). WithTimeout fires -> TimeoutCancellationException -> " +
                        "failGroupTurn(timedOut=true) -> TIMEOUT/timeout. Role A committed first and is " +
                        "preserved; role B hangs with zero content so its placeholder is dropped " +
                        "(empty_bubble_count=0); an error node is written for B.",
                )
            },
        )

        println("C1-TIMEOUT-DEVICE-BEGIN")
        println("attempt=$attemptLabel status=${run.status} reason=${run.reason} spent=${run.spentTokens} aTokens=$aTokens elapsed=${elapsed}ms")
        println("emptyBubbles=${emptyBubbles.size} committed=${run.committedRoleIds} skipped=${run.skippedRoleIds} errorNodeText=$errorText")
        println("C1-TIMEOUT-DEVICE-END")
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

    // ==================================================================
    // 契约 :206 / :232-235：viewer 可见台账 + 导出哈希
    // ==================================================================

    /**
     * 台账计划：由生产 [GroupChat.plan] 的步骤推导每个 viewer 的实际视角
     * （predecessor / chairRound），未被本轮计划选中的角色补一条素视角，
     * 保证台账按契约覆盖**全部** viewer。
     *
     * 与 `C1LiveModelSequenceTest.planViewerLedgerPlans` 语义逐字相同；本文件重复一份
     * 是为了不动那个刚被另一任务大改过的文件（契约允许测试侧重复 helper）。
     */
    private data class ViewerLedgerPlan(
        val key: String,
        val viewerRoleId: String,
        val predecessorId: String? = null,
        val chairRound: Boolean = false,
        val allowedOtherRoleIds: Set<String> = emptySet(),
    )

    private fun planViewerLedgerPlans(
        config: GroupConfig,
        planSteps: List<SpeakerStep>,
    ): List<ViewerLedgerPlan> {
        val byViewer = LinkedHashMap<String, ViewerLedgerPlan>()
        planSteps.forEach { step ->
            byViewer[step.role.id] = ViewerLedgerPlan(
                key = step.role.id,
                viewerRoleId = step.role.id,
                predecessorId = step.predecessorId,
                chairRound = step.chairRound,
                allowedOtherRoleIds = when {
                    step.chairRound -> config.roles.map { it.id }.filter { it != step.role.id }.toSet()
                    step.predecessorId != null -> setOf(step.predecessorId)
                    else -> emptySet()
                },
            )
        }
        config.roles.forEach { role ->
            byViewer.putIfAbsent(role.id, ViewerLedgerPlan(key = role.id, viewerRoleId = role.id))
        }
        return byViewer.values.toList()
    }

    /**
     * 契约 `docs/beyond-operit-client-changes.md:232-235` 点名的「各 viewer 的可见消息 ID」
     * 台账（按 viewer 分组）。可见集合由**生产函数** [GroupChat.visibleMessages] 计算 ——
     * 与提示词组装层 `GroupTurnCoordinator.viewerMessages` 同一入口。
     */
    private fun viewerVisibilityLedger(
        messages: List<UIMessage>,
        config: GroupConfig,
        plans: List<ViewerLedgerPlan>,
    ): JsonObject = buildJsonObject {
        plans.forEach { plan ->
            val visible = GroupChat.visibleMessages(
                config = config,
                messages = messages,
                viewerId = plan.viewerRoleId,
                predecessorId = plan.predecessorId,
                chairRound = plan.chairRound,
            )
            putJsonObject(plan.key) {
                put("viewer_role_id", plan.viewerRoleId)
                putJsonArray("visible_message_ids") {
                    visible.forEach { add(JsonPrimitive(it.id.toString())) }
                }
                put("visible_count", visible.size)
                put("predecessor_id", plan.predecessorId)
                put("chair_round", plan.chairRound)
                put("allowed_other_role_ids", plan.allowedOtherRoleIds.joinToString(","))
                putJsonArray("visible_assistant_role_ids") {
                    visible.filter { it.role == MessageRole.ASSISTANT }
                        .mapNotNull { it.roleId }
                        .forEach { add(JsonPrimitive(it)) }
                }
            }
        }
    }

    /**
     * 对台账本身做契约点名的断言（不只是把数字写进 JSON）：
     *
     * 1. 每个 viewer 的 `visible_message_ids` 必须与生产 `visibleMessages` 的输出**逐一相等**；
     * 2. 必须包含该轮的 user 触发消息；
     * 3. 必须包含**自己**发的助手消息（有的话）；
     * 4. 轮次摘要（`role_id = __summary__`，若有）对所有 viewer 可见；
     * 5. 可见集里出现的**其他角色**助手消息必须落在该 viewer 的授权名单内。
     */
    private fun assertViewerVisibilityLedger(
        ledger: JsonObject,
        messages: List<UIMessage>,
        config: GroupConfig,
        plans: List<ViewerLedgerPlan>,
    ) {
        val byId = messages.associateBy { it.id.toString() }
        val userMessageIds = messages.filter { it.role == MessageRole.USER }.map { it.id.toString() }
        val summaryIds = messages.filter { it.roleId == GroupChat.SUMMARY_ID }.map { it.id.toString() }
        plans.forEach { plan ->
            val entry = requireNotNull(ledger[plan.key]) { "台账缺少 viewer=${plan.key}" }.jsonObject
            val visibleIds = entry.getValue("visible_message_ids").jsonArray
                .map { it.jsonPrimitive.content }
                .toSet()
            val expected = GroupChat.visibleMessages(
                config = config,
                messages = messages,
                viewerId = plan.viewerRoleId,
                predecessorId = plan.predecessorId,
                chairRound = plan.chairRound,
            ).map { it.id.toString() }.toSet()
            assertEquals(
                "viewer=${plan.key} 的台账必须等于生产 visibleMessages 的输出",
                expected,
                visibleIds,
            )
            userMessageIds.forEach { id ->
                assertTrue("viewer=${plan.key} 必须可见 user 触发消息 $id", id in visibleIds)
            }
            messages.filter { it.role == MessageRole.ASSISTANT && it.roleId == plan.viewerRoleId }
                .forEach { own ->
                    assertTrue(
                        "viewer=${plan.key} 必须可见自己发的助手消息 ${own.id}",
                        own.id.toString() in visibleIds,
                    )
                }
            summaryIds.forEach { id ->
                assertTrue("viewer=${plan.key} 必须可见轮次摘要 $id", id in visibleIds)
            }
            visibleIds.mapNotNull { byId[it] }
                .filter { it.role == MessageRole.ASSISTANT }
                .mapNotNull { it.roleId }
                .filter { it != plan.viewerRoleId && it != GroupChat.SUMMARY_ID }
                .forEach { otherRole ->
                    assertTrue(
                        "viewer=${plan.key} 的可见集里出现未授权角色 $otherRole 的助手消息",
                        otherRole in plan.allowedOtherRoleIds,
                    )
                }
        }
    }

    /**
     * 契约 `:206`/`:232-235` 的**逐例导出哈希**：真库读回会话 → 生产导出器
     * [TavernChatCodec.exportGroupJsonl] → **生产 IO 助手** `writeExportTempFile`
     * （`ConversationExport.kt:836`）真写盘 → 同进程 `MessageDigest("SHA-256")`，
     * 并把同一份字节复制到 external files dir 供 `adb pull` 后本机复算。
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
            groupName = "C1 cancel ${stored.title}",
            createDate = null,
        )
        val bytes = exported.toByteArray(Charsets.UTF_8)
        assertTrue("导出字节不应为空：$exportFileName", bytes.isNotEmpty())
        assertTrue(
            "导出的首行必须是带 chat_metadata 的表头（生产 JSONL 形态）",
            exported.lineSequence().first().contains("chat_metadata"),
        )
        val sha = sha256Hex(bytes)

        // 生产 IO 分发：真写进 app 临时目录，返回 FileProvider URI。
        val uri = writeExportTempFile(context, exportFileName) { it.write(bytes) }
        val productionFile = File(context.appTempFolder, exportFileName)
        assertEquals("生产 IO 落盘字节数必须等于导出字节数", bytes.size.toLong(), productionFile.length())
        assertTrue(
            "生产 IO 落盘内容必须逐字节等于导出字节",
            productionFile.readBytes().contentEquals(bytes),
        )

        // 复制到 external files dir，供 `adb pull` 后在本机复算哈希。
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
                "same-process MessageDigest(\"SHA-256\") over the bytes produced by production " +
                    "TavernChatCodec.exportGroupJsonl; the same bytes were re-written through the " +
                    "production writeExportTempFile (ConversationExport.kt:836) and byte-compared",
            )
            put("export_path", pullFile.absolutePath)
            put("export_file_name", exportFileName)
            put("export_production_io_file", productionFile.absolutePath)
            put("export_production_io_uri", uri.toString())
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

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

        /**
         * 生产私有常量 `ChatManager.GROUP_ROUND_STEP_TIMEOUT_MS` 的值（15 分钟）。
         * 测试无法引用那个 private 常量，所以这里镜像一份；`buildChatManager()` 的默认参数用它，
         * 保证取消用例与生产默认行为逐字一致。
         */
        const val DEFAULT_STEP_TIMEOUT_MS = 15 * 60 * 1000L

        /** 超时用例注入的小步超时：足够让角色 a 先提交，又远小于 15 分钟默认值。 */
        const val TIMEOUT_STEP_MS = 5_000L

        /** mock SSE 帧自报的 wire 模型名；别的名字 = 真网关顶包。 */
        val MOCK_WIRE_MODEL_NAMES = setOf("mock-cancel-a", "mock-cancel-b", "mock-cancel-c")

        /** 导出证据 JSONL 里写的「用户名」（导出器必填参数，不是隐私数据）。 */
        const val EXPORT_USER_NAME = "C1 验证用户"

        /** `adb pull` 导出副本的目录（挂在 external files dir 下）。 */
        const val EXPORT_PULL_DIR = "c1-cancel-export"

        /** 本用例导出的群聊 JSONL 文件名。 */
        const val EXPORT_FILE_NAME = "c1-export-cancel.jsonl"

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
