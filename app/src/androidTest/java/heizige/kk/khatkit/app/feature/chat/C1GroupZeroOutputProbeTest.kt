package heizige.kk.khatkit.app.feature.chat

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.ai.core.MessageRole
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
import heizige.kk.khatkit.app.core.data.datastore.DEFAULT_PROVIDERS
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.Uuid

// ======================================================================
// 纯判定逻辑（不引用任何 Android / 项目类型）
//
// 这一段是本探针的「诊断内核」，与真机侧的采样记录解耦：采样线程只负责把
// `AssistantProbeSnapshot` 喂进来、把 `PlaceholderLifecycle` 算出来，判定则由下面这个
// 纯函数完成。之所以刻意做成纯函数，是为了能用**临时 JVM 探针**（`app/src/test/` 下）
// 对同一份判定逻辑做「改坏 → 真红 → 还原」的非空验证——androidTest 方法本体在主机上
// 跑不了，但这段判定逻辑可以逐字复制到 JVM 测试里证伪。
// ======================================================================

/**
 * 采样到的一条助手消息的瞬时快照（只保留判定需要的字段）。
 *
 * @param id 消息 id（占位消息在整个流式期间复用同一个 id，被 `finishGeneration` 丢弃后
 *   该 id 从会话内存态消失，所以「id 曾出现、最终不在内存态且从未盖章」正是「零产出被丢」的特征）。
 * @param roleId 盖章后的角色 id；生成期间的占位消息为 null。
 * @param partTypes `parts` 的类型名清单（`Text` / `Reasoning` / `Tool` …），判定成因②与③的关键。
 * @param textLength 正文长度（`toText().length`）。
 */
internal data class AssistantProbeSnapshot(
    val id: String,
    val roleId: String?,
    val turnKind: String?,
    val modelId: String?,
    val wireModelName: String?,
    val partTypes: List<String>,
    val textLength: Int,
    val usagePrompt: Int?,
    val usageCompletion: Int?,
    val usageTotal: Int?,
)

/** 一个采样点的内存态快照；签名变化时才入队（见 [C1GroupZeroOutputProbeTest.SessionMemorySampler]）。 */
internal data class SamplerTransition(
    val atMs: Long,
    val assistants: List<AssistantProbeSnapshot>,
)

/**
 * 一条助手消息 id 的完整生命周期（跨所有采样点聚合）。
 *
 * @param observedAsPlaceholder 是否至少有一次以 `roleId == null` 的形态被采样到
 *   （即「占位消息确实进过 session 内存态」）。
 * @param stampedRoleId 最终盖章的 roleId；从未盖章为 null。
 * @param vanishedUnstamped 曾以占位形态出现、从未盖章、且**不在最终内存态里** ⇒
 *   被 `finishGeneration` 丢掉的零产出占位消息。这正是成因①/②的分水岭。
 */
internal data class PlaceholderLifecycle(
    val id: String,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val observedAsPlaceholder: Boolean,
    val stampedRoleId: String?,
    val vanishedUnstamped: Boolean,
    val partTypesUnion: List<String>,
    val maxTextLength: Int,
    val turnKind: String?,
    val modelId: String?,
    val wireModelName: String?,
    val usagePrompt: Int?,
    val usageCompletion: Int?,
    val usageTotal: Int?,
)

/** 零产出成因。对应任务里点名的三种形状。 */
internal enum class ZeroOutputCause {
    /** ① 占位助手消息从未进入 session 内存态（零 chunk）。 */
    ZERO_CHUNK,

    /** ② 占位消息进过 session，但没有任何正文 part，随后被 `finishGeneration` 丢弃。 */
    CHUNK_WITHOUT_TEXT,

    /** ③ 占位消息进过 session 且带 `Reasoning` part（哪怕空）。 */
    REASONING_ONLY,

    /** 目标角色其实产出了正文（bug 未复现，或形状完全出乎意料）。 */
    CONTENT_PRESENT,
}

/**
 * 判定内核：由「全生命周期 + 目标失败角色」判出成因①/②/③。
 *
 * 判据（顺序即优先级）：
 * 1. 目标角色若存在**已盖章、非错误节点、且正文非空**的消息 ⇒ `CONTENT_PRESENT`
 *    （零产出假设不成立）。⚠️ 必须排除 `turnKind == [errorTurnKind]` 的失败节点：它也带
 *    `roleId = 失败角色` 且正文是「本轮生成失败：…」的**非空**文案，若不排除会把这次失败
 *    误判成「有产出」，整个判定失效。
 * 2. 不存在任何「曾出现、未盖章、最终消失」的占位消息 ⇒ `ZERO_CHUNK`（成因①）。
 * 3. 存在这种消失占位、且其中任一携带 `Reasoning` part ⇒ `REASONING_ONLY`（成因③）。
 * 4. 存在消失占位、且任一携带 `Text` part 或正文长度 > 0 ⇒ `CONTENT_PRESENT`（有内容却被丢，
 *    与「零正文」假设矛盾，必须显式暴露而不是硬套结论）。
 * 5. 其余（消失占位既无 Reasoning 也无正文）⇒ `CHUNK_WITHOUT_TEXT`（成因②）。
 *
 * @param errorTurnKind 失败节点的 `turnKind`（生产为 `GroupChat.TURN_ERROR`，值 `"error"`）。
 *   做成参数是为了让这段判定内核不依赖任何项目类型，从而能逐字复制到 JVM 探针里证伪。
 */
internal fun diagnoseZeroOutputCause(
    failingRoleId: String,
    lifecycles: List<PlaceholderLifecycle>,
    errorTurnKind: String = "error",
): ZeroOutputCause {
    val stampedSuccessForFailing = lifecycles.firstOrNull {
        it.stampedRoleId == failingRoleId && it.turnKind != errorTurnKind
    }
    if (stampedSuccessForFailing != null && stampedSuccessForFailing.maxTextLength > 0) {
        return ZeroOutputCause.CONTENT_PRESENT
    }
    val vanished = lifecycles.filter { it.vanishedUnstamped }
    if (vanished.isEmpty()) {
        return ZeroOutputCause.ZERO_CHUNK
    }
    if (vanished.any { lc -> lc.partTypesUnion.any { it.contains("Reasoning", ignoreCase = true) } }) {
        return ZeroOutputCause.REASONING_ONLY
    }
    if (vanished.any { lc -> lc.partTypesUnion.any { it == "Text" } || lc.maxTextLength > 0 }) {
        return ZeroOutputCause.CONTENT_PRESENT
    }
    return ZeroOutputCause.CHUNK_WITHOUT_TEXT
}

/**
 * 运行期只读探针（**不跑真机，本轮只交付结构正确的探针**）：在后台线程按
 * [SAMPLE_INTERVAL_MS] 的间隔采样 `ChatManager.getConversationFlow(id).value.currentMessages`，
 * 把生成期间只存在于 **session 内存态**的助手占位消息逐帧记下来。
 *
 * ## 为什么必须采内存态而不是 raw dump
 *
 * 群聊的助手占位消息在生成期间只活在 session 内存态（`ChatManager.updateConversation` →
 * `session.updateConversation`），只有 `finishGeneration` / `stampGroupTurn` 才落库。既有
 * `C1LiveModelSequenceTest` 的 raw dump 在 `finally` 里读**仓库**，看到的是终局：第 3 位
 * 角色那一步已经是 `turn_kind=error / role_failed`，**中间态全部丢失**。本探针补的就是这段
 * 中间态——它是把「零 chunk（占位从没进 session）」与「有 chunk 无正文（占位进过、随后被丢）」
 * 分开的唯一直接观测。
 *
 * ## 判定口径
 *
 * - 目标失败角色从未有任何占位 id 出现 ⇒ 成因①（[ZeroOutputCause.ZERO_CHUNK]）。
 * - 有占位 id 出现、无 `Reasoning`、无正文、随后消失 ⇒ 成因②（[ZeroOutputCause.CHUNK_WITHOUT_TEXT]）。
 * - 有占位 id 出现且带 `Reasoning` part（哪怕空）⇒ 成因③（[ZeroOutputCause.REASONING_ONLY]）。
 *
 * ⚠️ 本类只做旁路记录，不改动任何既有验收测试的断言，也不碰 `app/src/main/`。
 */
@RunWith(AndroidJUnit4::class)
class C1GroupZeroOutputProbeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val appContext = resolveProbeAppContext()

    private lateinit var evidenceDatabase: AppDatabase
    private lateinit var liveDatabase: AppDatabase
    private lateinit var repository: ConversationRepository
    private lateinit var groupRunDao: GroupRunDAO
    private lateinit var settingsStore: SettingsRepository
    private lateinit var chatManager: ChatManager

    private var originalSettings: Settings? = null
    private val probeConversations = mutableListOf<Uuid>()

    private val probeCaseName = "zero-output-probe"

    // ---------------- 固定 id：与 C1LiveModelSequenceTest 的 real-provider 夹具一致 ----------------

    private val assistantAId = Uuid.parse("0c1c11ae-0000-0000-0000-0000000000a1")
    private val assistantBId = Uuid.parse("0c1c11ae-0000-0000-0000-0000000000b1")
    private val assistantCId = Uuid.parse("0c1c11ae-0000-0000-0000-0000000000c1")

    /**
     * 内置免费 OpenAI 兼容 provider「极客猫」，**直接从生产 [DEFAULT_PROVIDERS] 取**——
     * 与 `C1LiveModelSequenceTest.realProviderSettings` 同一思路，但本文件**独立复制**，
     * 不改动那个类一个字符。按 id 定位、不落明文副本。
     */
    private val realProviderId = Uuid.parse("5197b3ae-21fd-4924-abb0-2aa70ff4ac42")

    private val realProvider: ProviderSetting.OpenAI =
        requireNotNull(DEFAULT_PROVIDERS.filterIsInstance<ProviderSetting.OpenAI>().firstOrNull {
            it.id == realProviderId
        }) {
            "DEFAULT_PROVIDERS 里找不到 id=$realProviderId 的内置 provider"
        }

    private val realFlashModel = requireNotNull(realProvider.models.firstOrNull { it.modelId == "deepseek-v4-flash" }) {
        "内置 provider 里没有 deepseek-v4-flash"
    }
    private val realGlmModel = requireNotNull(realProvider.models.firstOrNull { it.modelId == "glm-5.2" }) {
        "内置 provider 里没有 glm-5.2"
    }

    /** 探针轮预算：充足，绝不截断（截断会把第 3 位角色推成 skipped 而不是 role_failed）。 */
    private val probeBudget = 100_000

    /** 真实网关轮的超时：推理模型先出 reasoning token，比 mock 慢。 */
    private val probeTimeoutMillis = 300_000L

    private fun realPersona(code: String) = buildString {
        append("你是 KhatKit C1 群聊验证角色。你的代号是 ").append(code).append("。")
        append("ROLECODE:").append(code).append(" ")
        append("CASE:").append(probeCaseName).append(" ")
        append("你正在参加一场三人 pipeline 群聊。")
        append("输出规则（必须严格遵守）：")
        append("1. 只输出一行。")
        append("2. 这一行必须以 ROLECODE:").append(code).append(" 开头，后面跟一句不超过20字的中文。")
        append("3. 极其重要：对话历史里别人的发言也带 ROLECODE: 前缀，但那是别人的代号。")
        append("你必须始终使用你自己的代号 ").append(code)
        append("，绝对不能沿用或模仿历史里出现的任何其它代号。")
        append("4. 禁止模拟其它角色，禁止列表、标题、markdown、思考过程。")
        append("正确示例：ROLECODE:").append(code).append(" 我已就位。")
        append("补充设定：保持角色一致性，只讲事实，不写形容词，不复述他人发言。")
    }

    private fun realAssistant(id: Uuid, name: String, code: String, modelId: Uuid) = Assistant(
        id = id,
        name = name,
        chatModelId = modelId,
        systemPrompt = realPersona(code),
        enableMemory = false,
        useGlobalMemory = false,
        autoExtractMemory = false,
        enableWebSearch = false,
        localTools = emptyList(),
        enableTimeReminder = false,
        enableRecentChatsReference = false,
    )

    private fun realRoles(caseName: String) = listOf(
        GroupRole(
            id = "a",
            name = "角色甲",
            assistantId = assistantAId.toString(),
            modelId = realFlashModel.id.toString(),
            cardId = "card-$caseName-a",
        ),
        GroupRole(
            id = "b",
            name = "角色乙",
            assistantId = assistantBId.toString(),
            modelId = realGlmModel.id.toString(),
            cardId = "card-$caseName-b",
        ),
        GroupRole(
            id = "c",
            name = "角色丙",
            assistantId = assistantCId.toString(),
            chair = true,
            modelId = realFlashModel.id.toString(),
            cardId = "card-$caseName-c",
        ),
    )

    private fun realProviderConfig(caseName: String, budget: Int) = GroupConfig(
        roles = realRoles(caseName),
        mode = GroupChat.MODE_PIPELINE,
        chairRoleId = "c",
        tokenBudgetPerRound = budget,
    )

    private fun realProviderSettings() = Settings(
        init = false,
        chatModelId = realFlashModel.id,
        fastModelId = realFlashModel.id,
        providers = listOf(realProvider),
        assistants = listOf(
            realAssistant(assistantAId, "角色甲", "A", realFlashModel.id),
            realAssistant(assistantBId, "角色乙", "B", realGlmModel.id),
            realAssistant(assistantCId, "角色丙", "C", realFlashModel.id),
        ),
    )

    @Before
    fun setUp() {
        trace("setUp:enter")
        val entry = appEntryPoint(appContext)
        settingsStore = entry.settingsStore()
        originalSettings = settingsStore.settingsFlow.value

        context.deleteDatabase(PROBE_DB)
        evidenceDatabase = AppDatabaseFactory.create(context, PROBE_DB)
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
        runCatching { runBlocking { probeConversations.forEach { chatManager.stopGeneration(it) } } }
        runCatching { runBlocking { probeConversations.forEach { chatManager.removeConversationReference(it) } } }
        originalSettings?.let { original ->
            runCatching { runBlocking { settingsStore.update(original) } }
        }
        runCatching {
            runBlocking {
                probeConversations.forEach { groupRunDao.deleteFinishedOfConversation(it.toString()) }
            }
        }
        if (::evidenceDatabase.isInitialized) evidenceDatabase.close()
        if (::liveDatabase.isInitialized) liveDatabase.close()
        context.deleteDatabase(PROBE_DB)
    }

    // ==================================================================
    // 探针用例：采样 session 内存态，把第 3 位发言者零产出的成因①/②/③分开
    // ==================================================================

    /**
     * 复现 pipeline 三角色链（a→b→c，c 为第 3 位），在生成的**同时**以约
     * [SAMPLE_INTERVAL_MS] 的间隔采样 session 内存态，最后写出证据 JSON。
     *
     * 断言只做「探针本身采到了东西」的非空校验（采样轮数 > 0、助手消息生命周期 >= 3、
     * 证据文件非空），**不断言失败形态**：失败形态由证据 JSON 的 `diagnosis` 字段表达，
     * 真机执行留给下一轮。
     */
    @Test
    fun pipelineThirdSpeakerZeroOutputProbe() = runBlocking {
        settingsStore.update(realProviderSettings())
        val config = realProviderConfig(probeCaseName, probeBudget)
        val conversationId = insertGroup(probeCaseName, config)
        probeConversations += conversationId

        // ---- 启动后台采样线程（先于 sendMessage）----
        val sampler = SessionMemorySampler()
        val sampling = AtomicBoolean(true)
        val samplerThread = Thread({
            while (sampling.get()) {
                val now = System.currentTimeMillis()
                runCatching {
                    sampler.record(
                        now,
                        chatManager.getConversationFlow(conversationId).value.currentMessages,
                    )
                }
                try {
                    Thread.sleep(SAMPLE_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "c1-zero-output-probe-sampler").apply { isDaemon = true }
        val probeStartedAt = System.currentTimeMillis()
        samplerThread.start()
        trace("probe:sampler-started at=$probeStartedAt")

        val messages: List<UIMessage>
        val run: GroupRunEntity
        var blockFailure: Throwable? = null
        try {
            chatManager.sendMessage(
                conversationId = conversationId,
                content = listOf(UIMessagePart.Text("请三位依次发言，每位一句话。")),
                answer = true,
            )
            trace("probe:sendMessage-returned")

            val trigger = awaitUserMessage(conversationId, probeTimeoutMillis)
            val roundId = GroupChat.roundIdFor(trigger.id.toString())
            run = awaitAnyTerminalRun(conversationId, roundId, probeTimeoutMillis)
            trace("probe:terminal status=${run.status} reason=${run.reason} committed=${run.committedRoleIds}")
            messages = repository.getConversationById(conversationId)?.currentMessages.orEmpty()
        } catch (t: Throwable) {
            blockFailure = t
            throw t
        } finally {
            sampling.set(false)
            samplerThread.interrupt()
            runCatching { samplerThread.join(5_000) }
            // 终局再采一帧，保证 finalIds 判定基于最新内存态。
            runCatching {
                sampler.record(
                    System.currentTimeMillis(),
                    chatManager.getConversationFlow(conversationId).value.currentMessages,
                )
            }
            trace("probe:sampler-stopped polls=${sampler.pollCount} failure=${blockFailure != null}")
        }

        val finalIds = chatManager.getConversationFlow(conversationId).value.currentMessages
            .map { it.id.toString() }
            .toSet()
        val lifecycles = sampler.lifecycles(finalIds)

        // 失败角色：库里那条 turn_kind=error 的错误节点。bug 未复现时为 null，不据此断言。
        val errorNode = messages.firstOrNull { it.turnKind == GroupChat.TURN_ERROR }
        val failingRoleId = errorNode?.roleId

        val diagnosis = if (failingRoleId != null) {
            diagnoseZeroOutputCause(failingRoleId, lifecycles)
        } else {
            null
        }
        trace(
            "probe:diagnosis failing=$failingRoleId cause=$diagnosis " +
                "vanished=${lifecycles.count { it.vanishedUnstamped }}",
        )

        // ---- 非空校验：探针必须真的采到了东西，否则静默通过毫无意义 ----
        assertTrue("采样线程必须至少运行过一轮", sampler.pollCount > 0)
        assertTrue(
            "采样必须观测到至少 3 条助手消息（a/b/c 或 a/b/error），实际 ${lifecycles.size}",
            lifecycles.size >= 3,
        )
        assertTrue(
            "必须至少观测到一条曾以占位形态（roleId==null）出现的助手消息，" +
                "否则采样精度不足以回答『占位是否进过 session』",
            lifecycles.any { it.observedAsPlaceholder },
        )

        writeProbeEvidence(
            buildJsonObject {
                put("probe_kind", "session-memory-sampling")
                put(
                    "note",
                    "只读采样 ChatManager.getConversationFlow(id).value.currentMessages，间隔约 " +
                        "${SAMPLE_INTERVAL_MS}ms；不改 app/src/main/，不降低任何既有断言强度",
                )
                put("case_name", probeCaseName)
                put("conversation_id", conversationId.toString())
                put("sampling_interval_ms", SAMPLE_INTERVAL_MS)
                put("probe_started_at_ms", probeStartedAt)
                put("sampler_first_poll_at_ms", sampler.firstPollAt)
                put("sampler_last_poll_at_ms", sampler.lastPollAt)
                put("sampler_poll_count", sampler.pollCount)
                put("sampler_transition_count", sampler.transitionCount)
                put("placeholders_observed_count", lifecycles.count { it.observedAsPlaceholder })
                put("vanished_unstamped_count", lifecycles.count { it.vanishedUnstamped })
                put("block_exception_pending", blockFailure != null)
                put("block_exception", blockFailure?.let { "${it::class.simpleName}: ${it.message}" })

                // ---- group_runs 终局（与既有 raw dump 同口径，便于交叉核对）----
                put("group_run_status", run.status)
                put("group_run_reason", run.reason)
                put("group_run_spent", run.spentTokens)
                put("group_run_limit", run.tokenLimit)
                put("group_run_committed", JsonArray(run.committedRoleIds.map { JsonPrimitive(it) }))
                put("group_run_skipped", JsonArray(run.skippedRoleIds.map { JsonPrimitive(it) }))
                put("error_node_role_id", failingRoleId)
                put("error_node_text", errorNode?.toText())

                // ---- 每个采样点：把「每一步」的内存态快照原样落盘 ----
                putJsonArray("transitions") {
                    sampler.transitionList().forEachIndexed { index, transition ->
                        add(
                            buildJsonObject {
                                put("step_index", index)
                                put("observed_at_ms", transition.atMs)
                                putJsonArray("assistants") {
                                    transition.assistants.forEach { add(snapshotToJson(it)) }
                                }
                            },
                        )
                    }
                }

                // ---- 每条助手消息 id 的生命周期：判定成因的直接证据 ----
                putJsonArray("placeholder_lifecycles") {
                    lifecycles.forEachIndexed { index, lc ->
                        add(
                            buildJsonObject {
                                put("step_index", index)
                                put("message_id", lc.id)
                                put("role_id", lc.stampedRoleId)
                                put("turn_kind", lc.turnKind)
                                put("observed_at_ms_first", lc.firstSeenMs)
                                put("observed_at_ms_last", lc.lastSeenMs)
                                put("placeholder_seen", lc.observedAsPlaceholder)
                                put("vanished_unstamped", lc.vanishedUnstamped)
                                putJsonArray("part_types") {
                                    lc.partTypesUnion.forEach { add(JsonPrimitive(it)) }
                                }
                                put("max_text_length", lc.maxTextLength)
                                put("model_id", lc.modelId)
                                put("wire_model_name", lc.wireModelName)
                                put("usage_prompt", lc.usagePrompt ?: -1)
                                put("usage_completion", lc.usageCompletion ?: -1)
                                put("usage_total", lc.usageTotal ?: -1)
                            },
                        )
                    }
                }
                putJsonArray("final_memory_state_ids") {
                    finalIds.sorted().forEach { add(JsonPrimitive(it)) }
                }

                // ---- 判定 ----
                put(
                    "diagnosis",
                    if (diagnosis == null) {
                        JsonNull
                    } else {
                        buildJsonObject {
                            put("cause", diagnosis.name)
                            put("cause_label", diagnoseCauseLabel(diagnosis))
                            put("failing_role_id", failingRoleId)
                            put("vanished_placeholder_count", lifecycles.count { it.vanishedUnstamped })
                            putJsonArray("vanished_placeholder_ids") {
                                lifecycles.filter { it.vanishedUnstamped }.forEach {
                                    add(JsonPrimitive(it.id))
                                }
                            }
                            putJsonArray("stamped_roles") {
                                lifecycles.mapNotNull { it.stampedRoleId }.distinct().forEach {
                                    add(JsonPrimitive(it))
                                }
                            }
                        }
                    },
                )
            },
        )
        trace("probe:evidence-written polls=${sampler.pollCount} cause=$diagnosis")
    }

    // ------------------------------------------------------------------
    // 夹具装载
    // ------------------------------------------------------------------

    private suspend fun insertGroup(caseName: String, config: GroupConfig): Uuid {
        val conversation = Conversation(
            assistantId = assistantAId,
            title = "C1 zero-output probe $caseName",
            messageNodes = emptyList(),
            type = GroupChat.TYPE_GROUP,
            groupConfig = config,
        )
        val invalid = GroupChat.validate(config, conversation.id.toString())
        assertTrue("群配置必须合法，实际违规：$invalid", invalid.isEmpty())
        repository.insertConversation(conversation)
        openGroupSession(conversation.id)
        return conversation.id
    }

    /**
     * 照抄 `C1LiveModelSequenceTest.openGroupSession`：先拿会话引用、再从库读进内存态。
     *
     * 跳过这一步会让 `session.state.value` 停留在空单聊会话，`sendMessage` 随后整行覆盖
     * 刚落库的群会话、抹掉 `group_config`，整条群聊链被静默旁路（详见原方法注释）。
     */
    private suspend fun openGroupSession(conversationId: Uuid) {
        chatManager.addConversationReference(conversationId)
        chatManager.initializeConversation(conversationId)
        val loaded = chatManager.getConversationFlow(conversationId).value
        assertTrue(
            "群会话必须以 groupConfig 非空被装载进 session，实际 type=${loaded.type}、" +
                "groupConfig=${loaded.groupConfig != null}",
            loaded.type == GroupChat.TYPE_GROUP && loaded.groupConfig != null,
        )
        trace("openGroupSession:loaded roles=${loaded.groupConfig?.roles?.size}")
    }

    /** 轮询内存 flow 直到触发用的 USER 消息出现（轮次 id 由它派生，不猜）。 */
    private suspend fun awaitUserMessage(conversationId: Uuid, timeoutMillis: Long): UIMessage {
        val flow = chatManager.getConversationFlow(conversationId)
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            flow.value.currentMessages.lastOrNull { it.role == MessageRole.USER }?.let { return it }
            Thread.sleep(100)
        }
        throw AssertionError("等待触发 USER 消息超时（${timeoutMillis}ms）")
    }

    /**
     * 等 group_runs 收尾（**任意**终态，不假定 COMPLETED）——本探针要观测的正是
     * `FAILED / role_failed` 这条路径，所以不能要求 COMPLETED。
     */
    private suspend fun awaitAnyTerminalRun(
        conversationId: Uuid,
        roundId: String,
        timeoutMillis: Long,
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
            "等待 group_runs 收尾超时（${timeoutMillis}ms），round=$roundId，最后状态=${last?.status}",
        )
    }

    private fun snapshotToJson(snapshot: AssistantProbeSnapshot) = buildJsonObject {
        put("message_id", snapshot.id)
        put("role_id", snapshot.roleId)
        put("turn_kind", snapshot.turnKind)
        put("model_id", snapshot.modelId)
        put("wire_model_name", snapshot.wireModelName)
        putJsonArray("part_types") { snapshot.partTypes.forEach { add(JsonPrimitive(it)) } }
        put("text_length", snapshot.textLength)
        put("usage_prompt", snapshot.usagePrompt ?: -1)
        put("usage_completion", snapshot.usageCompletion ?: -1)
        put("usage_total", snapshot.usageTotal ?: -1)
    }

    private fun toProbeSnapshot(message: UIMessage) = AssistantProbeSnapshot(
        id = message.id.toString(),
        roleId = message.roleId,
        turnKind = message.turnKind,
        modelId = message.modelId?.toString(),
        wireModelName = message.wireModelName,
        partTypes = message.parts.map { it::class.simpleName ?: "?" },
        textLength = message.toText().length,
        usagePrompt = message.usage?.promptTokens,
        usageCompletion = message.usage?.completionTokens,
        usageTotal = message.usage?.totalTokens,
    )

    private fun trace(step: String) {
        val line = "${System.currentTimeMillis()} $step"
        android.util.Log.i(TRACE_TAG, line)
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: return
            File(dir, TRACE_FILE).appendText(line + "\n")
        }
    }

    private fun writeProbeEvidence(payload: JsonObject) {
        val dir = requireNotNull(context.getExternalFilesDir(null)) {
            "getExternalFilesDir(null) 不应为 null"
        }
        val file = File(dir, PROBE_EVIDENCE_FILE)
        file.writeText(PRETTY.encodeToString(JsonElement.serializer(), payload))
        assertTrue("证据文件不应为空：${file.absolutePath}", file.length() > 0)
    }

    /** 采样器：后台线程只调 [record]；所有聚合都在锁内完成。 */
    private inner class SessionMemorySampler {
        private val lock = Any()
        private val transitions = mutableListOf<SamplerTransition>()
        private val firstSeen = LinkedHashMap<String, Long>()
        private val lastSeen = LinkedHashMap<String, Long>()
        private val placeholderSeen = LinkedHashMap<String, Boolean>()
        private val partTypes = LinkedHashMap<String, MutableSet<String>>()
        private val maxText = LinkedHashMap<String, Int>()
        private val roleStamp = LinkedHashMap<String, String?>()
        private val turnKinds = LinkedHashMap<String, String?>()
        private val modelIds = LinkedHashMap<String, String?>()
        private val wireNames = LinkedHashMap<String, String?>()
        private val usagePrompt = LinkedHashMap<String, Int>()
        private val usageCompletion = LinkedHashMap<String, Int>()
        private val usageTotal = LinkedHashMap<String, Int>()
        private var lastSignature: String? = null

        @Volatile
        var pollCount = 0
            private set

        @Volatile
        var firstPollAt = 0L
            private set

        @Volatile
        var lastPollAt = 0L
            private set

        val transitionCount: Int get() = synchronized(lock) { transitions.size }

        fun record(now: Long, messages: List<UIMessage>) {
            synchronized(lock) {
                pollCount++
                if (firstPollAt == 0L) firstPollAt = now
                lastPollAt = now
                val assistants = messages.filter { it.role == MessageRole.ASSISTANT }
                val snaps = assistants.map { toProbeSnapshot(it) }
                assistants.forEach { message ->
                    val id = message.id.toString()
                    firstSeen.putIfAbsent(id, now)
                    lastSeen[id] = now
                    placeholderSeen[id] = (placeholderSeen[id] ?: false) || message.roleId == null
                    val types = partTypes.getOrPut(id) { mutableSetOf() }
                    message.parts.forEach { types += (it::class.simpleName ?: "?") }
                    maxText[id] = maxOf(maxText[id] ?: 0, message.toText().length)
                    if (message.roleId != null) roleStamp[id] = message.roleId
                    if (message.turnKind != null) turnKinds[id] = message.turnKind
                    message.modelId?.let { modelIds[id] = it.toString() }
                    message.wireModelName?.let { wireNames[id] = it }
                    message.usage?.let {
                        usagePrompt[id] = it.promptTokens
                        usageCompletion[id] = it.completionTokens
                        usageTotal[id] = it.totalTokens
                    }
                }
                val signature = snaps.joinToString("|") {
                    "${it.id}:${it.roleId}:${it.turnKind}:${it.partTypes}:${it.textLength}:${it.modelId}:${it.wireModelName}"
                }
                if (signature != lastSignature) {
                    lastSignature = signature
                    transitions += SamplerTransition(now, snaps)
                }
            }
        }

        fun transitionList(): List<SamplerTransition> = synchronized(lock) { transitions.toList() }

        fun lifecycles(finalIds: Set<String>): List<PlaceholderLifecycle> = synchronized(lock) {
            firstSeen.keys.map { id ->
                val stamped = roleStamp[id]
                val seenPlaceholder = placeholderSeen[id] == true
                PlaceholderLifecycle(
                    id = id,
                    firstSeenMs = firstSeen.getValue(id),
                    lastSeenMs = lastSeen.getValue(id),
                    observedAsPlaceholder = seenPlaceholder,
                    stampedRoleId = stamped,
                    vanishedUnstamped = seenPlaceholder && stamped == null && id !in finalIds,
                    partTypesUnion = (partTypes[id]?.toList() ?: emptyList()).sorted(),
                    maxTextLength = maxText[id] ?: 0,
                    turnKind = turnKinds[id],
                    modelId = modelIds[id],
                    wireModelName = wireNames[id],
                    usagePrompt = usagePrompt[id],
                    usageCompletion = usageCompletion[id],
                    usageTotal = usageTotal[id],
                )
            }.sortedBy { it.firstSeenMs }
        }
    }

    private companion object {
        const val PROBE_DB = "c1-zero-output-probe.db"
        const val TRACE_TAG = "C1ZeroProbe"
        const val TRACE_FILE = "c1-zero-output-probe-trace.txt"
        const val PROBE_EVIDENCE_FILE = "c1-zero-output-probe.json"

        /** 采样间隔：取 100–200ms 区间的下沿，尽量不放过真实网关一次数秒生成窗口里的占位态。 */
        const val SAMPLE_INTERVAL_MS = 100L

        val PRETTY = Json { prettyPrint = true; encodeDefaults = true }
    }
}

/** 成因的中文标签，供证据 JSON 阅读，避免只能看英文枚举名。 */
private fun diagnoseCauseLabel(cause: ZeroOutputCause): String = when (cause) {
    ZeroOutputCause.ZERO_CHUNK -> "① 零 chunk：占位助手消息从未进入 session 内存态"
    ZeroOutputCause.CHUNK_WITHOUT_TEXT -> "② 有 chunk 无正文：占位进过 session，随后被 finishGeneration 丢弃"
    ZeroOutputCause.REASONING_ONLY -> "③ 仅 Reasoning：占位带 Reasoning part（哪怕空）"
    ZeroOutputCause.CONTENT_PRESENT -> "目标角色其实产出了正文（零产出未复现）"
}

private fun resolveProbeAppContext(): Application =
    InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
