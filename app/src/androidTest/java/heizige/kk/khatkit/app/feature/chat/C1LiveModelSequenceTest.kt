package heizige.kk.khatkit.app.feature.chat

import android.app.Application
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.core.TokenUsage
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
import heizige.kk.khatkit.app.core.data.datastore.DEFAULT_PROVIDERS
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
import heizige.kk.khatkit.app.core.data.model.VoteBallot
import heizige.kk.khatkit.app.core.data.model.VoteOutcome
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
 * 4. **`chairRound` 只在议长汇总那一步为真**（`GroupChat.plan` 的 `MODE_ROUNDTABLE` 分支：
 *    非议长 `SpeakerStep(it)` 无 `predecessorId`、无 `chairRound`，议长 `SpeakerStep(chair,
 *    chairRound = true)` 排在最后）。`GroupChat.visibleMessages` 的 `chairRound` 分支放开
 *    本轮全部 ASSISTANT 消息——roundtable 用例据此断言「议长看得见全部、普通角色看不见」。
 * 5. **vote 的候选集从不进 prompt**：`GroupChat.newRound` 的 `candidates =
 *    config.voteCandidates.ifEmpty { parseCandidates(userText) }` 只把候选集交给计票判定，
 *    没有一条 message 会提到它。所以「每个角色投给谁」必须由本测试经 system prompt 暗号
 *    告知 mock（见 [persona] 的 `CANDIDATES:` / `BALLOT:`），而「票面没被共享」仍由
 *    `buildContext` 的库内过滤独立证明。
 *
 * ## 配套的 mock 服务（`/tmp/opencode/roundtable-vote/mock_openai_v2.py`）
 *
 * 沿用 v1 的全部收尾格式（SSE 头、role-only opener、逐块 `flush()`、`finish_reason`、
 * usage-only trailer、`data: [DONE]`、`Connection: close`）与 `ceil(bytes/4)` 的 token 口径，
 * 只加了一条能力：system prompt 里出现 `BALLOT:` 时额外吐一行
 * `VOTE: <id> | <理由>` —— `GroupChat.parseBallot` 只认 `trim()` 后整行以
 * `BALLOT_PREFIX`（`"VOTE:"`）开头、且 id 在候选集内的行，v1 的散文回复认不出选票。
 * 没有 `BALLOT:` 时 mock 的回复文本与 v1 **逐字相同**，所以 pipeline / 截断两个既有用例的
 * 数字口径不变。
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
    private val roundtableCaseName = "roundtable"
    private val voteCaseName = "vote"

    /**
     * vote 候选集。两个候选、三张票，`a`/`b` 投 `opt-a`、`c` 投 `opt-b` —— 2:1 的真多数决，
     * 于是 `GroupChat.tally` 返回 `VoteOutcome.Decided`，`resolveVote` 走 `Decided` 分支，
     * `voteSummaryMessage` 才会真的写出 `__summary__` 节点（`ChatManager.completeGroupRound`）。
     *
     * 平票走的是 `TIE_CHAIR` 的**另一条分支**（`NeedsChairTieBreak`：多一轮议长裁决 + 脚手架
     * 增删），本轮不覆盖它——那需要第三套夹具，且会与「三个角色各发一次」的序列审计混在一起。
     */
    private val voteCandidates = listOf("opt-a", "opt-b")
    private val voteBallots = mapOf("A" to "opt-a", "B" to "opt-a", "C" to "opt-b")

    /** 预算充足轮：三个角色都要发言。 */
    private val mainBudget = 100_000

    /** 真机注入预算上限的 instrumentation 参数名（`-e c1TokenBudgetPerRound <Int>`）。 */
    private val budgetArg = "c1TokenBudgetPerRound"

    /**
     * 真实网关轮的预算上限，默认 [mainBudget]（100_000，绝不截断）。
     *
     * 存在的理由：mock 那条截断用例用 [budgetLimit] = 1，是**假 token**；而真实网关那条
     * 用例原先把上限写死 100_000，于是整轮真实 token（约 2.0 万）也够不到停跑线，
     * 「已用/上限/未运行角色」三个数在真实 token 下零份。`-e c1TokenBudgetPerRound <Int>`
     * 就是补出来的那个注入点。
     *
     * ⚠️ **只作用于真实网关那条用例**，不碰 [mainBudget] 本身：`mainBudget` 还被 mock 的
     * pipeline / roundtable / vote 三处共用（`groupConfig` / `roundtableConfig` /
     * `voteConfig`），改它会让 `-e` 顺带改掉三个 mock 模式的上限——那三个模式各有逐字钉死
     * 的基线（含 [budgetLimit] = 1 那条已验证基线），不能让一个真机参数牵动。
     *
     * 解析失败（缺省 / 空串 / 非数字 / 溢出）一律回落 [mainBudget]，即不传参数时
     * `realProviderRoundRecordsGenuineTokenUsage` 的行为与注入点存在之前逐字一致。
     */
    private val realProviderBudget: Int by lazy {
        val raw = runCatching { InstrumentationRegistry.getArguments().getString(budgetArg) }
            .getOrNull()
        val parsed = raw?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (raw != null && parsed == null) {
            trace("real:budget-override-unparsed raw=$raw -> fallback=$mainBudget")
        }
        parsed ?: mainBudget
    }

    /**
     * 预算截断轮：上限 1。只要第一个角色真产生了 token（必然 > 0）就会停跑。
     *
     * `GroupChat.budgetDecision` 的口径是 `limit <= 0 || spent < limit` 才继续，
     * 所以 `limit = 1` 是「第一个角色之后必定停跑」的最小正值。
     */
    private val budgetLimit = 1

    private val modelIds = listOf("mock-model-a", "mock-model-b", "mock-model-c")

    // ==================================================================
    // 真实网关（内置 provider「极客猫」）专用：固定 id + 取生产 provider 定义
    // ==================================================================

    private val realCaseName = "real-provider"

    /**
     * 真实网关可能比 mock 慢两个数量级（推理模型要先出 reasoning token），
     * 所以等待超时从默认的 120s/60s 放宽到 300s。
     */
    private val realTimeoutMillis = 300_000L

    /**
     * 内置的免费 OpenAI 兼容 provider「极客猫」，**直接从生产 [DEFAULT_PROVIDERS] 取**。
     *
     * 为什么不照抄一份：apiKey 是公共内置 key（已提交进仓库），在本文件里再写一遍
     * 就等于多一处明文副本，删起来一定漏。所以只按 **id** 定位，定义整个从生产表拿。
     * 顺带保证本用例跑的就是用户开箱即用的那份配置。注意它**没有**设
     * `useResponseApi`（默认 false），所以走的是 `POST /chat/completions`。
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

    /**
     * 每个角色一个 system prompt，埋 `CASE:<case> ROLECODE:<code>` 暗号。
     *
     * 为什么不埋 user 消息：pipeline 三次请求的**最后一条 user 消息是同一条**（后两次
     * 只是多出前一位的 assistant 输出），按 user 消息区分不出角色。system prompt 才是
     * 每角色独有的，而且「谁的 system prompt」正是「谁在说话」本身。mock 服务据此
     * 判定每个请求的发言者与 case。
     */
    /**
     * @param ballot 该角色要投的候选 id；非 null 时才埋 `CANDIDATES:` / `BALLOT:` 暗号。
     *
     * 为什么暗号要埋在 **system prompt** 里而不是指望模型「自己知道候选集」：vote 模式下
     * 候选集**根本没进 prompt**——`GroupTurnCoordinator.roundPlanFor` 调
     * `GroupChat.newRound`，候选集来自 `config.voteCandidates` 或用户文本的
     * `候选：a,b,c`，两者都不写进任何一条 message。所以 mock 无从「发现」候选，只能由
     * 本测试显式告知；而 system prompt 是每角色独有的位置，和已有的 `ROLECODE:` 同一处。
     *
     * `BALLOT:` 只对被指定的角色埋：mock v2 只在 system prompt 里读到 `BALLOT:` 时才吐出
     * 一行 `VOTE: <id> | <理由>`（`GroupChat.parseBallot` 要求 `trim()` 后整行以
     * `BALLOT_PREFIX` 开头、id 在候选集内）。三人各投一张，凑出 2:1 的多数决，
     * 这样 `resolveVote` 走 `Decided` 分支，`__summary__` 节点才会真的落库。
     */
    private fun persona(caseName: String, code: String, ballot: String? = null) = buildString {
        append("你是 KhatKit C1 群聊验证角色。")
        append("CASE:").append(caseName).append(" ")
        append("ROLECODE:").append(code).append(" ")
        if (ballot != null) {
            append("CANDIDATES:").append(voteCandidates.joinToString(",")).append(" ")
            append("BALLOT:").append(ballot).append(" ")
        }
        append("你正在参加一场三人 pipeline 群聊。请用简短中文回复，不要调用任何工具。")
        // 撑长度：保证 mock 侧 ceil(bytes/4) 的 prompt token 明显大于 1，
        // 这样 limit=1 的截断用例一定在第一个角色之后触发，而不是「压根没超」。
        repeat(6) { append("补充设定：保持角色一致性，只讲事实，不写形容词，不复述他人发言。") }
    }

    /**
     * @param ballots `ROLECODE` -> 要投的候选 id。只有 vote 用例传；pipeline / roundtable
     *   传 null，system prompt 与上一轮提交的证据逐字一致。
     */
    private fun mockSettings(caseName: String, ballots: Map<String, String>? = null) = Settings(
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
            assistant(assistantAId, "角色甲", caseName, "A", modelAId, ballots?.get("A")),
            assistant(assistantBId, "角色乙", caseName, "B", modelBId, ballots?.get("B")),
            assistant(assistantCId, "角色丙", caseName, "C", modelCId, ballots?.get("C")),
        ),
    )

    private fun assistant(
        id: Uuid,
        name: String,
        caseName: String,
        code: String,
        modelId: Uuid,
        ballot: String? = null,
    ) = Assistant(
        id = id,
        name = name,
        chatModelId = modelId,
        systemPrompt = persona(caseName, code, ballot),
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

    /**
     * 三个角色的名单，pipeline / roundtable / vote 三个用例共用。
     *
     * 抽出来的原因不是「省行数」，而是三个用例必须拿到**逐字相同**的角色绑定，否则
     * mock 侧按 `ROLECODE` 反查发言者、断言侧按 `roleId` 查消息，两边一旦漂移就查不
     * 出是夹具变了还是行为变了。议长固定为 `c`（`chair = true`）。
     */
    /**
     * 真实模型的 system prompt。
     *
     * 与 [persona] 的差别只有一处，但很关键：**措辞必须强硬到模型愿意照抄格式**。
     * `persona` 写的是「请用简短中文回复」，mock 无所谓（它按 system prompt 里的
     * `ROLECODE:` 拼字符串），真模型却可能答成散文、也可能一口气模拟三个角色
     * （实测 `glm-5.2` 就会「我来模拟三位依次发言」）。所以这里把输出格式写成
     * 一行模板，并显式禁止别的 ROLECODE / 模拟他人 / markdown / 思考过程。
     *
     * 已在本机按**真实的 pipeline 链**（a 的输出进 b 的上下文、b 的输出进 c 的上下文）
     * 对两个模型各跑两轮：6/6 都只回自己的代号。
     *
     * ⚠️ 第 3 条不是冗余。首版没有它，真机连跑三轮各挂一种：a 的输出被 b 原样学走
     * （`ROLECODE:A ...`）、c 学走 b 的、c 干脆一条内容都没产出（`role_failed`）。
     * 根因是 pipeline **本来就要把上一位的发言放进下一位的上下文**，而真模型会照抄
     * 眼前最近那条 assistant 消息的格式——mock 不会（它按 system prompt 拼字符串），
     * 所以这个坑只在真网关上现形。写死「历史里别人的代号不是你的」才压得住。
     */
    private fun realPersona(code: String) = buildString {
        append("你是 KhatKit C1 群聊验证角色。你的代号是 ").append(code).append("。")
        append("ROLECODE:").append(code).append(" ")
        append("CASE:").append(realCaseName).append(" ")
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
        // 与 mock 用例逐字一致：关掉一切额外能力，请求里只剩 system + 对话消息。
        enableMemory = false,
        useGlobalMemory = false,
        autoExtractMemory = false,
        enableWebSearch = false,
        localTools = emptyList(),
        enableTimeReminder = false,
        enableRecentChatsReference = false,
    )

    /**
     * 三个角色绑**不同**的模型：a、c 用 `deepseek-v4-flash`，b 用 `glm-5.2`。
     *
     * 有区分度才能证明「按角色选型」真的生效：若三个角色都退回会话的
     * `chatModelId`，`message.modelId` 会塌成同一个 uuid，断言当场失败。
     */
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

    /**
     * providers 只放这一个内置 provider——**不注入任何 mock / 回环地址**。
     * `chatModelId` / `fastModelId` 指向真实存在的 `deepseek-v4-flash` uuid。
     */
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

    private fun roles(caseName: String) = listOf(
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
    )

    private fun groupConfig(caseName: String, budget: Int) = GroupConfig(
        roles = roles(caseName),
        mode = GroupChat.MODE_PIPELINE,
        chairRoleId = "c",
        tokenBudgetPerRound = budget,
    )

    /** roundtable：议长汇总排在最后一位（`GroupChat.plan` 的 `filter { it.id != chair.id }` + 追加）。 */
    private fun roundtableConfig(caseName: String, budget: Int) = GroupConfig(
        roles = roles(caseName),
        mode = GroupChat.MODE_ROUNDTABLE,
        chairRoleId = "c",
        tokenBudgetPerRound = budget,
    )

    /** vote：候选集由配置显式给出（不走用户文本的 `候选：a,b,c` 严格解析）。 */
    private fun voteConfig(caseName: String, budget: Int) = GroupConfig(
        roles = roles(caseName),
        mode = GroupChat.MODE_VOTE,
        chairRoleId = "c",
        tokenBudgetPerRound = budget,
        voteCandidates = voteCandidates,
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
        // pipeline 规则：viewer 看得见**自己的**发言（GroupChat.visibleMessages 的
        // `roleId == viewerId` 分支），但看不见他人；b 只多看得见 a；c 只多看得见 b、看不见 a。
        //
        // ⚠️ 早期版本这里写的是 `seenByA.isEmpty()`，真机首跑就炸在这里：a 的可见集合是
        // `[a]` 而不是 `[]`。那是断言自己写错了——把「看不见他人」误写成「什么也看不见」，
        // 而把自己那条排除掉反而才是真正的越权。mock 侧独立记录（requests.jsonl seq=1）
        // 已证明 a 的请求里只有 system+user 两条消息、没有任何他人输出，与本断言一致。
        val seenByA = GroupChat.buildContext("a", messages, config, null).mapNotNull { it.roleId }
        assertTrue(
            "角色 a 只应看到自己的发言，不应看到 b 或 c，实际=$seenByA",
            seenByA.all { it == "a" },
        )

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
    // 用例 3：roundtable 一整轮 —— 前两位普通发言 + 议长汇总，共 4 次真实调用
    // ==================================================================

    /**
     * roundtable 的关键差别只有一个参数：`chairRound = true`。
     *
     * `GroupChat.plan`（`GroupChat.kt:595-600`）把议长排到最后一位并标上 `chairRound`，
     * 而 `GroupChat.visibleMessages`（`:547`）只在 `chairRound` 为真时放开
     * 「本轮全部 ASSISTANT 消息」。所以本用例要证明的**不是**「议长也发言」——pipeline
     * 的角色 c 本来就发言——而是：
     *
     * 1. 议长那一轮的请求里**同时**出现本轮另外两个角色的输出（看得见全部）；
     * 2. 前两位普通发言轮的请求里**不出现**别人的输出（看不见非前驱；roundtable 的
     *    非议长 step `predecessorId` 为 null，所以连前驱都没有）；
     * 3. 汇总发言只由议长发出：`turnKind` 只有议长那条是 `chair`，前两条是 `speaker`。
     */
    @Test
    fun roundtableRoundRecordsChairSummaryCallSequence() = runBlocking {
        val caseName = roundtableCaseName
        trace("roundtable:settings-update-begin")
        settingsStore.update(mockSettings(caseName))
        trace("roundtable:settings-update-done")
        val config = roundtableConfig(caseName, mainBudget)
        val conversationId = insertGroup(caseName, config)
        evidenceConversations += conversationId

        chatManager.sendMessage(
            conversationId = conversationId,
            content = listOf(UIMessagePart.Text("请三位依次发言，议长最后汇总。")),
            answer = true,
        )
        trace("roundtable:sendMessage-returned")

        val messages = awaitAssistantMessages(conversationId, expected = 3)
        trace("roundtable:await-messages-done count=${messages.size}")
        val triggerId = messages.last { it.role == MessageRole.USER }.id.toString()
        val expectedRoundId = GroupChat.roundIdFor(triggerId)
        val run = awaitTerminalRun(conversationId, expectedRoundId)

        val assistants = messages.filter { it.role == MessageRole.ASSISTANT }
        val byRole = assistants.associateBy { it.roleId }

        assertEquals("三个角色都必须发言", setOf("a", "b", "c"), byRole.keys)
        assertEquals("三个助手消息必须同属一轮", setOf(expectedRoundId), assistants.map { it.roundId }.toSet())

        // ---------------- 断言 1：议长排在最后（plan 的顺序） ----------------
        assertEquals(
            "发言顺序必须是名单顺序 + 议长最后（GroupChat.plan 的 roundtable 分支）",
            listOf("a", "b", "c"),
            assistants.map { it.roleId },
        )

        // ---------------- 断言 2：只有议长那条是 turn_kind=chair ----------------
        // 这是「汇总发言只由议长发出」的第一半：另外两个角色的轮次里没有汇总。
        assertEquals(
            "只有议长 c 的发言是 turn_kind=chair，实际=" +
                assistants.map { "${it.roleId}:${it.turnKind}" },
            mapOf("a" to GroupChat.TURN_SPEAKER, "b" to GroupChat.TURN_SPEAKER, "c" to GroupChat.TURN_CHAIR),
            assistants.associate { it.roleId to it.turnKind },
        )

        // ---------------- 断言 3：usage 必须来自真实响应 ----------------
        assistants.forEach { message ->
            val usage = requireNotNull(message.usage) {
                "角色 ${message.roleId} 没有 usage —— 响应里没带 usage，这条 token 证据就不成立"
            }
            assertTrue(
                "角色 ${message.roleId} 的 prompt_tokens 必须为正",
                usage.promptTokens > 0,
            )
            assertTrue("角色 ${message.roleId} 的 completion_tokens 必须为正", usage.completionTokens > 0)
        }

        // ---------------- 断言 4：token 对账 ----------------
        val sumPromptCompletion = assistants.sumOf {
            requireNotNull(it.usage).promptTokens + requireNotNull(it.usage).completionTokens
        }
        assertEquals(
            "group_runs.spent_tokens 必须等于议长三轮 (prompt+completion) 之和",
            sumPromptCompletion,
            run.spentTokens,
        )
        assertEquals("本轮应正常完成", GroupRunEntity.STATUS_COMPLETED, run.status)
        assertEquals("三个角色都必须进 committed 名单", listOf("a", "b", "c"), run.committedRoleIds)
        assertTrue("预算充足时不应有 skipped 角色，实际=${run.skippedRoleIds}", run.skippedRoleIds.isEmpty())
        assertNotNull("运行日志必须已收尾", run.endedAt)

        // ---------------- 断言 5：正文来自 mock 服务 ----------------
        assistants.forEach { message ->
            assertTrue(
                "角色 ${message.roleId} 的回复必须来自 mock 服务（应含 CASE:$caseName 暗号），实际=${message.toText()}",
                message.toText().contains("CASE:$caseName"),
            )
        }

        // ---------------- 断言 6：视角隔离（从真库读回的消息上判定） ----------------
        // roundtable 的普通 step：`predecessorId = null`、`chairRound = false`
        // （`GroupChat.plan` 的 `selected.filter { it.id != chair.id }.map { SpeakerStep(it) }`）。
        // 所以 a 的可见集合里只有自己的（此刻为空）与用户消息，b 同样。
        val seenByA = GroupChat.buildContext("a", messages, config, null).mapNotNull { it.roleId }
        assertTrue(
            "角色 a 看不见 b 或 c 的本轮发言（roundtable 非议长轮 predecessorId 为 null），实际=$seenByA",
            seenByA.none { it == "b" || it == "c" },
        )
        val seenByB = GroupChat.buildContext("b", messages, config, null).mapNotNull { it.roleId }
        assertTrue(
            "角色 b 看不见 a 或 c 的本轮发言，实际=$seenByB",
            seenByB.none { it == "a" || it == "c" },
        )

        // 议长：`chairRound = true` 放开本轮全部 ASSISTANT 消息。
        val chairVisible = GroupChat.buildContext("c", messages, config, null, chairRound = true)
        val seenByChair = chairVisible.mapNotNull { it.roleId }
        assertTrue(
            "议长必须看见本轮角色 a 的发言，实际=$seenByChair",
            "a" in seenByChair,
        )
        assertTrue(
            "议长必须看见本轮角色 b 的发言，实际=$seenByChair",
            "b" in seenByChair,
        )
        // 反向：同样这批消息，不带 chairRound 时议长看不见别人——否则上面那条断言
        // 就只是「过滤函数对所有人都不生效」这种假阳性。
        val chairWithoutFlag = GroupChat.buildContext("c", messages, config, null).mapNotNull { it.roleId }
        assertTrue(
            "不传 chairRound 时议长不该看见他人（否则证明不了 chairRound 是放开开关），实际=$chairWithoutFlag",
            chairWithoutFlag.none { it == "a" || it == "b" },
        )

        // ---------------- 断言 7：议长的汇总正文里带了它看到的那两人的暗号 ----------------
        // 正文里的 `ROLECODE:` 是 mock 按收到的 system prompt 写的（见 mock v2 的
        // `build_reply`），所以议长那条回复里出现 A/B，说明那两个角色的 system prompt
        // 真的进了议长的请求体——这与断言 6 的库内过滤互为独立证据。
        val chairText = byRole.getValue("c").toText()
        assertTrue(
            "议长的回复文本里应带自己的 ROLECODE:C，实际=$chairText",
            chairText.contains("ROLECODE:C"),
        )

        // ---------------- 断言 8：前两位的正文里**没有**别人的暗号 ----------------
        // 「汇总发言只由议长发出」的另一半：a、b 两条里不得出现议长的暗号，也不得出现
        // 对方的暗号（对方发言根本没进它们的 prompt，mock 不可能凭空写出来）。
        // 正则取正文里出现的**全部** ROLECODE 暗号，而不是「`contains(自己的)` 即可」——
        // 后者在本轮就会假绿：文本里同时出现 A 和 B 时 `contains("ROLECODE:B")` 也为真。
        // mock v2 的回复模板只嵌自己的暗号（`build_reply`），所以这里等于断言
        // 「该角色的 prompt 里只有自己的 system prompt」。
        val roleCodePattern = Regex("""ROLECODE:([A-Z])""")
        listOf("a" to "A", "b" to "B", "c" to "C").forEach { (roleId, ownCode) ->
            val codes = roleCodePattern.findAll(byRole.getValue(roleId).toText()).map { it.groupValues[1] }.toList()
            assertEquals(
                "角色 $roleId 的回复里必须只出现自己的 ROLECODE:$ownCode，实际=$codes",
                listOf(ownCode),
                codes,
            )
        }

        writeEvidence(
            "c1-live-evidence-roundtable.json",
            roundtableReport(conversationId, config, messages, run, sumPromptCompletion),
        )
    }

    // ==================================================================
    // 用例 4：vote 一整轮 —— 三张选票 + 计票 + `__summary__` 节点
    // ==================================================================

    /**
     * vote 的完整链路（本用例要一次跑通四段）：
     *
     * 1. `GroupChat.plan` 的 vote 分支（`GroupChat.kt:606`，`else -> selected.map { SpeakerStep(it) }`）
     *    既不给 `predecessorId` 也不给 `chairRound`——三个角色互相看不见。
     * 2. 每次发言的正文里有一整行 `VOTE: <候选id> | 理由`，
     *    `GroupChat.parseBallot`（`:645-657`）按 `trim()` 后 `startsWith("VOTE:", ignoreCase)`
     *    找第一条命中行，`substring(5).trim()` 后 `substringBefore('|').trim()` 取 id，
     *    **id 不在候选集内直接丢票**。
     * 3. `GroupTurnCoordinator.collectBallots`（`:398-410`）只收本轮、
     *    `roleId` 属于群里角色的 ASSISTANT 消息，再 `parseBallot`。
     * 4. `GroupChat.tally`（`:660-681`）多数决 → `VoteOutcome.Decided` →
     *    `resolveVote` 的 `Decided` 分支（`:449-454`）→
     *    `voteSummaryMessage` 写出 `role_id = __summary__`、`turn_kind = vote_summary` 的节点，
     *    `ChatManager.completeGroupRound` 把它 append 进会话并把轮次收成 COMPLETED。
     *
     * 三票投成 2:1（a/b → `opt-a`，c → `opt-b`），所以不需要平票裁决那一支。
     */
    @Test
    fun voteRoundRecordsBallotCallsAndSummarySequence() = runBlocking {
        val caseName = voteCaseName
        trace("vote:settings-update-begin")
        settingsStore.update(mockSettings(caseName, ballots = voteBallots))
        trace("vote:settings-update-done")
        val config = voteConfig(caseName, mainBudget)
        val conversationId = insertGroup(caseName, config)
        evidenceConversations += conversationId

        chatManager.sendMessage(
            conversationId = conversationId,
            content = listOf(UIMessagePart.Text("请三位各投一票，选出你支持的方案。")),
            answer = true,
        )
        trace("vote:sendMessage-returned")

        // 期望 3 条角色发言 + 1 条 `__summary__` 合成节点。
        val messages = awaitAssistantMessages(conversationId, expected = 4)
        trace("vote:await-messages-done count=${messages.size}")
        val triggerId = messages.last { it.role == MessageRole.USER }.id.toString()
        val expectedRoundId = GroupChat.roundIdFor(triggerId)
        val run = awaitTerminalRun(conversationId, expectedRoundId)

        val assistants = messages.filter { it.role == MessageRole.ASSISTANT }
        val speakers = assistants.filter { it.roleId != GroupChat.SUMMARY_ID }
        val summary = assistants.singleOrNull { it.roleId == GroupChat.SUMMARY_ID }

        // ---------------- 断言 1：三个角色各发一条，另有且仅有 1 条 summary ----------------
        assertEquals(
            "vote 模式应产出 3 条角色发言 + 1 条 __summary__，实际=" +
                assistants.map { "${it.roleId}:${it.turnKind}" },
            listOf("a", "b", "c", GroupChat.SUMMARY_ID),
            assistants.map { it.roleId },
        )
        assertNotNull("必须有且仅有一条 __summary__ 节点（parseBallot + tally 的产出）", summary)

        // ---------------- 断言 2：__summary__ 的三元组 ----------------
        requireNotNull(summary)
        assertEquals(
            "__summary__ 节点必须是 turn_kind=vote_summary",
            GroupChat.TURN_VOTE_SUMMARY,
            summary.turnKind,
        )
        assertEquals("__summary__ 节点必须与本轮同 round", expectedRoundId, summary.roundId)
        assertTrue(
            "__summary__ 正文应由 voteSummaryMessage 生成（应含「本轮投票结果」与「票数」），" +
                "实际=${summary.toText()}",
            summary.toText().contains("本轮投票结果") && summary.toText().contains("票数"),
        )

        // ---------------- 断言 3：parseBallot 真的解析出了三张选票 ----------------
        // 直接对**落库回来的正文**跑生产函数，而不是比对 mock 的意图：解析不出来就炸。
        val ballots = speakers.mapNotNull { message ->
            GroupChat.parseBallot(message.toText(), requireNotNull(message.roleId), config.voteCandidates)
        }
        assertEquals(
            "三个角色都必须投出候选集内的有效选票，实际解析出 $ballots",
            3,
            ballots.size,
        )
        assertEquals(
            "选票应按 a/b→opt-a、c→opt-b 分布，实际=$ballots",
            mapOf("a" to "opt-a", "b" to "opt-a", "c" to "opt-b"),
            ballots.associate { it.roleId to it.candidateId },
        )
        assertTrue(
            "每张选票都应带上 mock 写入的理由（`|` 之后那段），实际=$ballots",
            ballots.all { it.reason.isNotBlank() },
        )

        // ---------------- 断言 4：多数决结果 ----------------
        val outcome = GroupChat.tally(ballots, config.voteCandidates, config.tiePolicy)
        assertTrue(
            "三张选票 2:1 应得出明确结论，实际=$outcome",
            outcome is VoteOutcome.Decided,
        )
        val decided = outcome as VoteOutcome.Decided
        assertEquals("胜者必须是 opt-a", "opt-a", decided.winner)
        assertEquals("票数必须是 opt-a 2 / opt-b 1", mapOf("opt-a" to 2, "opt-b" to 1), decided.tally)
        assertTrue(
            "__summary__ 正文应带上胜者，实际=${summary.toText()}",
            summary.toText().contains("opt-a"),
        )

        // ---------------- 断言 5：token 对账 ----------------
        // 口径：只有**角色发言**的 usage 进 `spent_tokens`（`commitGroupTurn` 每次
        // `advance` 加一次）；`__summary__` 由 `voteSummaryMessage` 本地构造，从不经过
        // 模型，因此它的 `usage` 必须为 null，且不得计入。
        speakers.forEach { message ->
            val usage = requireNotNull(message.usage) {
                "角色 ${message.roleId} 没有 usage —— 响应里没带 usage，这条 token 证据就不成立"
            }
            assertTrue("角色 ${message.roleId} 的 prompt_tokens 必须为正", usage.promptTokens > 0)
            assertTrue("角色 ${message.roleId} 的 completion_tokens 必须为正", usage.completionTokens > 0)
        }
        assertEquals(
            "__summary__ 是本地合成节点，不该有模型 usage",
            null,
            summary.usage,
        )
        val sumPromptCompletion = speakers.sumOf {
            requireNotNull(it.usage).promptTokens + requireNotNull(it.usage).completionTokens
        }
        assertEquals(
            "group_runs.spent_tokens 必须等于三条角色发言 (prompt+completion) 之和",
            sumPromptCompletion,
            run.spentTokens,
        )
        assertEquals("本轮应正常完成", GroupRunEntity.STATUS_COMPLETED, run.status)
        assertEquals("三个角色都必须进 committed 名单", listOf("a", "b", "c"), run.committedRoleIds)
        assertNotNull("运行日志必须已收尾", run.endedAt)

        // ---------------- 断言 6：turnKind ----------------
        assertTrue(
            "三条角色发言的 turnKind 都应是 speaker，实际=" +
                speakers.map { "${it.roleId}:${it.turnKind}" },
            speakers.all { it.turnKind == GroupChat.TURN_SPEAKER },
        )

        // ---------------- 断言 7：视角隔离 ----------------
        // vote 的 `SpeakerStep` 既无 `predecessorId` 也无 `chairRound`，所以
        // `buildContext(x, ..., null, false)` 对每个角色只应返回「用户 + 自己的」。
        // ⚠️ 这里必须把 `__summary__` 排除掉再判「看得见谁」。它**本来就该**各视角都可见
        // （`GroupChat.visibleMessages` 的 `message.roleId == SUMMARY_ID -> true` 分支，票面
        // 不算票：正文只有结果与票数，不含谁投了什么），首跑时忘了排除，断言以
        // 「角色 a 只应看到自己的发言，实际=[a, __summary__]」炸掉——那是断言漏了
        // 一个合法可见节点，不是隔离越权。
        listOf("a", "b", "c").forEach { roleId ->
            val allVisible = GroupChat.buildContext(roleId, messages, config, null)
            assertTrue(
                "角色 $roleId 应当看得见本轮投票结果摘要（__summary__ 对所有视角可见），实际=" +
                    allVisible.mapNotNull { it.roleId },
                GroupChat.SUMMARY_ID in allVisible.mapNotNull { it.roleId },
            )
            val seenBallots = allVisible
                .filter { it.role == MessageRole.ASSISTANT }
                .mapNotNull { it.roleId }
                .filter { it != GroupChat.SUMMARY_ID }
            assertEquals(
                "角色 $roleId 只应看到自己那一张选票（vote 模式不共享票面），实际=$seenBallots",
                listOf(roleId),
                seenBallots,
            )
        }

        writeEvidence(
            "c1-live-evidence-vote.json",
            voteReport(conversationId, config, messages, run, sumPromptCompletion, ballots, decided),
        )
    }

    // ==================================================================
    // 用例 5：真实网关 —— 真实模型调用 + 真实 token（前四个用例的 token 都是 mock 估算值）
    // ==================================================================

    /**
     * 前四个用例的 token **全部来自 mock 服务**：`usage` 是 mock 按 `ceil(bytes/4)`
     * 估的，「实际模型调用序列」也只是 mock 记下来的请求顺序。所以它们能证明
     * 「预算怎么累加、可见集合怎么过滤」，**证明不了「真实模型真的回了多少 token」**。
     *
     * 这个用例把两者换成真的：直接用生产 [DEFAULT_PROVIDERS] 里那个内置的免费
     * OpenAI 兼容网关（默认 `enabled`），**设备直连公网**——不经过 `adb reverse`、
     * 不经过本机 mock、不装任何抓包代理。于是：
     *
     * - `message.usage` 是网关按真实分词返回的 `input_tokens` / `output_tokens`；
     * - `message.modelId` 是 `resolveGroupTurnModelId` / `TaskRoutes.resolve`
     *   真正为该角色选中的模型 uuid；
     * - `message.wireModelName` 是网关响应体 / SSE 帧顶层 `model` 字段里的**原样字符串**，
     *   与 `modelId`（本地配置 uuid）是两回事，见下面「模型名从哪来」一节；
     * - 走的还是那份配置的原生路径：`POST /chat/completions` + `stream_options.include_usage`，
     *   usage 由 SSE 收尾块解析（`ChatCompletionsAPI` 的 `parseTokenUsage`）。
     *
     * ## 模型序列为什么要有区分度
     *
     * 三个角色绑三种**不同**的绑定（a、c 为 `deepseek-v4-flash`，b 为 `glm-5.2`），
     * 期望序列 `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`。如果按角色选型没生效
     * （三个角色都退回会话的 `chatModelId`），序列会塌成同一个模型三遍，断言当场失败。
     * 所以这一条同时压住了「路由按角色选型」与「真实调用真的发生」两件事。
     *
     * ## `actual_model_call_sequence` 里的模型名从哪来（wire 优先）
     *
     * 每条发言记**两个**模型名字段，两条来源都留，方便对账：
     *
     * - `wire_model_name`：**首选**。网关响应体 / SSE 帧顶层 `model` 字段里的原样字符串，
     *   由 `StreamChunk.Finish` 落到 `UIMessage.wireModelName`，随对话 JSON 一起入库。
     *   本用例的消息是**从库里读回来的**（`awaitAssistantMessages` 走
     *   `repository.getConversationById(...).currentMessages`），所以这个值是**落盘后**的值。
     * - `uuid_reverse_lookup_model_string`：`UIMessage.modelId`（本地配置 uuid）经
     *   `realProvider.models` 反查出来的 `Model.modelId`。**仅在 wire 名为 null 时采信**。
     *
     * 逐条还有 `wire_model_name_provenance`：
     *
     * | 取值 | 含义 |
     * |---|---|
     * | `wire_response_model` | 来自响应体 / SSE 帧原样字符串，这是 wire 级 |
     * | `uuid_reverse_lookup_fallback` | **回退**：`wireModelName` 为 null，用 uuid 反查自己那张表 |
     *
     * ## 为什么还留着 uuid 反查这一条回退
     *
     * wire 名依赖网关**主动**报 `model` 字段。真网关不报时仍要能记下调用序列，所以留了回退；
     * 但它**必须**被标成 `uuid_reverse_lookup_fallback`，不能让读者误以为那是网关回传的。
     * 「两者皆空」（wire 名为 null 且 uuid 查不到）不允许静默通过——断言 1b 当场炸。
     *
     * ⚠️ `provider_model_table` 里那一列仍叫 `wire_model_string`，但它是**我们自己那张
     * provider 模型表的字段**，不是网关回传的值；它只用来解释「uuid 对应哪个上线名」，
     * 不能当成 wire 级观测（JSON 里已用 `provider_model_table_note` 与
     * `bindings[].wire_model_string_source` 两个字段如实标出这一点）。
     *
     * **因此它能证明**：真实网关确实被调用了（`message.usage` 是网关按真实分词返回的，
     * 见上面的 `token_source`）；`resolveGroupTurnModelId` / `TaskRoutes.resolve` 为每个角色
     * 选出的 **uuid** 确实随轮次推进而变化，且顺序与角色顺序一致；**网关自报的模型名**
     * （`wire_model_name`）与那份期望序列一致——这一条是 wire 级的，来源就是响应里那个字段。
     *
     * **因此它仍不能证明**：本用例**不是第三方抓包**——wire 名由 app 自己解析响应体取得，
     * 没有任何网线侧旁路记录。app 侧的可信度由
     * `ai/src/test/.../WireModelNameProvenanceTest.kt`（真实解码器 + 序列化往返）钉住，
     * 网关侧的独立复核用 `tools/verification/c1_wire_model_probe.py` 另跑一次对账。
     *
     * ⚠️ 别把这条读成「模型名未经核对」。核对发生了两轮：wire 名 vs 期望序列，
     * 以及 wire 名 vs uuid 反查名（两者都记在 JSON 里，对不上能直接看出来）。
     *
     * ## 两个必须记住的坑
     *
     * 1. **这两个都是推理模型**：网关回的 `completion_tokens_details.reasoning_tokens`
     *    是正的，推理 token **计入** `completionTokens`。别据此以为「模型没说话」——
     *    真判据是正文里的 `ROLECODE:`。
     * 2. **`maxTokens` 留空**（[Assistant] 默认 null，`ResponseAPI` 因此不发
     *    `max_output_tokens`）。若这里图省事填个几十，推理 token 会把配额吃光、
     *    正文变成空串，然后所有 usage 断言都变成假绿或假红。
     */
    @Test
    fun realProviderRoundRecordsGenuineTokenUsage() = runBlocking {
        val caseName = realCaseName

        // ---- 前置自检：本用例绝不能退化成本机 mock ----
        trace("real:preflight-begin")
        assertTrue(
            "本用例必须打真实公网网关，baseUrl 却是 ${realProvider.baseUrl}",
            realProvider.baseUrl.startsWith("https://") &&
                !realProvider.baseUrl.contains("127.0.0.1") &&
                !realProvider.baseUrl.contains("localhost"),
        )
        assertTrue("内置 provider 必须默认启用", realProvider.enabled)
        // 按 id 从生产表取出来的定义，必须与契约 docs 里记的 uuid 一致；不一致说明
        // 生产表变了，本用例的模型序列期望需要跟着改，而不是默默跑一个别的模型。
        assertEquals(
            "内置 deepseek-v4-flash 的 uuid 必须与契约一致",
            Uuid.parse("5a86b2d6-9c3c-4c58-9b27-f9295ba39201"),
            realFlashModel.id,
        )
        assertEquals(
            "内置 glm-5.2 的 uuid 必须与契约一致",
            Uuid.parse("8b6bf21c-56d8-40fd-93c8-6d657cac71a4"),
            realGlmModel.id,
        )
        // ⚠️ 内置 provider 并**没有**设 useResponseApi，默认 false，所以实际走的是
        // `POST /chat/completions`（OpenAIProvider.streamText 的 else 分支），
        // 不是 `/responses`。usage 由 ChatCompletionsAPI 从 SSE 的 usage-only
        // 收尾块解析（`stream_options.include_usage`），同样是真网关返回的数字。
        // 首跑时这里写的是 assertTrue(useResponseApi)，直接炸了——是断言写错，不是配置有问题。
        assertEquals(
            "内置 provider 未开 Responses API，应走 /chat/completions",
            false,
            realProvider.useResponseApi,
        )
        assertEquals(
            "chat completions 路径必须是 /chat/completions",
            "/chat/completions",
            realProvider.chatCompletionsPath,
        )
        trace("real:preflight-ok baseUrl=${realProvider.baseUrl}")

        settingsStore.update(realProviderSettings())
        trace("real:settings-update-done")
        val config = realProviderConfig(caseName, realProviderBudget)
        val conversationId = insertGroup(caseName, config)
        trace("real:conversation-inserted id=$conversationId")
        evidenceConversations += conversationId

        // ---- 走生产入口触发真实生成（真·公网 HTTPS 请求） ----
        chatManager.sendMessage(
            conversationId = conversationId,
            content = listOf(UIMessagePart.Text("请三位依次发言，每位一句话。")),
            answer = true,
        )
        trace("real:sendMessage-returned")

        val messages = awaitAssistantMessages(
            conversationId = conversationId,
            expected = 3,
            timeoutMillis = realTimeoutMillis,
        )
        trace("real:await-messages-done count=${messages.size}")
        val triggerId = messages.last { it.role == MessageRole.USER }.id.toString()
        val expectedRoundId = GroupChat.roundIdFor(triggerId)
        val run = awaitTerminalRun(conversationId, expectedRoundId, timeoutMillis = realTimeoutMillis)

        val assistants = messages.filter { it.role == MessageRole.ASSISTANT }
        val byRole = assistants.associateBy { it.roleId }

        // 先落一份「未经任何断言筛选」的原始快照，**独立文件** `c1-real-raw-dump.json`：
        // 后面若有断言炸了，这份仍是事发当时的真实数据，不用靠猜。
        // 注意它与正式报告 `c1-live-evidence-real-provider.json` 是两个文件，不会互相覆盖，
        // 所以跑完记得两个都拿走；它保留失败现场，是排查用的，不是验收证据。
        writeEvidence(
            "c1-real-raw-dump.json",
            buildJsonObject {
                put("note", "raw snapshot taken right after generation, before any assertion")
                put("conversation_id", conversationId.toString())
                put("message_count", messages.size)
                putJsonArray("messages") {
                    messages.forEach { message ->
                        add(
                            buildJsonObject {
                                put("role", message.role.name)
                                put("role_id", message.roleId)
                                put("turn_kind", message.turnKind)
                                put("model_id", message.modelId?.toString())
                                // 事发当时的原始值：网关自报的 wire 模型名（没报就是 null）。
                                // 正式报告里的 `wire_model_name_provenance` 判定在后面做。
                                put("wire_model_name", message.wireModelName)
                                put("usage_prompt", message.usage?.promptTokens ?: -1)
                                put("usage_completion", message.usage?.completionTokens ?: -1)
                                put("usage_total", message.usage?.totalTokens ?: -1)
                                put("text", message.toText())
                                putJsonArray("parts") {
                                    message.parts.forEach { part ->
                                        add(
                                            buildJsonObject {
                                                put("type", part::class.simpleName ?: "?")
                                                put("text", (part as? UIMessagePart.Text)?.text ?: "")
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                put("group_run_status", run.status)
                put("group_run_spent", run.spentTokens)
                put("group_run_limit", run.tokenLimit)
                put("group_run_committed", JsonArray(run.committedRoleIds.map { JsonPrimitive(it) }))
                put("group_run_skipped", JsonArray(run.skippedRoleIds.map { JsonPrimitive(it) }))
                put("group_run_ended_at", run.endedAt)
                put("group_run_reason", run.reason)
            },
        )

        assertEquals(
            "本轮不应有失败/错误节点（真实网关报错会让轮次转 FAILED），实际消息=" +
                messages.map { "${it.role}/${it.roleId}" } +
                "；app 错误=${chatManager.errors.value.map { it.title to it.error }}",
            listOf("a", "b", "c"),
            assistants.map { it.roleId },
        )
        assertEquals("三个助手消息必须同属一轮", setOf(expectedRoundId), assistants.map { it.roundId }.toSet())
        assertEquals("运行日志的 round_id 必须与消息上的 round_id 一致", expectedRoundId, run.roundId)

        // ---------------- 断言 1：模型序列按角色选型，且有区分度 ----------------
        // 这是本用例存在的核心理由：每个角色必须走自己绑的那个模型，而不是全退回
        // 会话的 chatModelId（那会让三个 modelId 变成同一个值）。
        assertEquals(
            "角色 a 的 modelId 必须是 deepseek-v4-flash",
            realFlashModel.id,
            byRole.getValue("a").modelId,
        )
        assertEquals(
            "角色 b 的 modelId 必须是 glm-5.2",
            realGlmModel.id,
            byRole.getValue("b").modelId,
        )
        assertEquals(
            "角色 c 的 modelId 必须是 deepseek-v4-flash",
            realFlashModel.id,
            byRole.getValue("c").modelId,
        )
        // 反向：序列里必须真的出现了两个不同的模型，否则「有区分度」的前提没成立，
        // 上面三条断言就成了同义反复。
        assertEquals(
            "实际调用的模型必须有两个不同的取值，否则证明不了按角色选型",
            setOf(realFlashModel.id, realGlmModel.id),
            assistants.mapNotNull { it.modelId }.toSet(),
        )
        // 模型名**优先读 wire 级**（网关响应体 / SSE 帧顶层 `model` 字段的原样字符串，
        // 由 `StreamChunk.Finish` 落盘），只有 wire 名缺失才回退到 uuid 反查——回退的那几条
        // 会带着 PROVENANCE_UUID_REVERSE_LOOKUP_FALLBACK 落进证据 JSON，不会冒充 wire 级证据。
        val wireModelNames = assistants.map { resolveWireModelName(it) }

        // ---------------- 断言 1b：不允许 wire 名与反查名「两者皆空」 ----------------
        // 这条钉的是「静默退化」：如果哪天 `wireModelName` 又悄悄不落了、而 uuid 反查也
        // 查不到（模型表漂了、uuid 为空……），旧写法会把占位符当成一个合法的模型名塞进
        // `actual_model_call_sequence`，读者看不出来。空实现必须当场炸。
        val bothMissing = wireModelNames.filter {
            it.wireModelName == null && it.uuidReverseLookupName == null
        }
        assertTrue(
            "每条发言都必须至少有一个模型名来源：要么 wireModelName 非空，要么 uuid 能反查到；" +
                "两者皆空的角色=" +
                assistants.zip(wireModelNames)
                    .filter { (_, name) -> name.wireModelName == null && name.uuidReverseLookupName == null }
                    .map { (message, _) -> message.roleId } +
                "。全量模型名来源=" + assistants.zip(wireModelNames).map { (m, n) -> "${m.roleId}:${n.provenance}" },
            bothMissing.isEmpty(),
        )
        // 反过来也要钉住：回退必须真的被标成回退，不能悄悄混进 wire 那一档。
        wireModelNames.forEach { name ->
            assertEquals(
                "provenance 与是否回退必须自洽：wireModelName=${name.wireModelName}",
                name.wireModelName == null,
                name.fallbackTaken,
            )
        }

        assertEquals(
            "期望的模型调用序列（按发言顺序）应为 deepseek-v4-flash → glm-5.2 → deepseek-v4-flash",
            listOf("deepseek-v4-flash", "glm-5.2", "deepseek-v4-flash"),
            wireModelNames.map { it.resolvedModelName },
        )
        // wire 名与反查名都记下来了，所以能直接断言「走 wire 路径的那些，反查名必须也查得到
        // （否则这条记录连对照都没有）」，且两者取值一致时不需要 fallback 标记。
        val wirePathNames = wireModelNames.filterNot { it.fallbackTaken }
        assertTrue(
            "走 wire 路径的记录必须同时留下反查名用于对账，实际=" +
                assistants.zip(wireModelNames)
                    .filter { (_, n) -> !n.fallbackTaken }
                    .map { (m, n) -> "${m.roleId}:wire=${n.wireModelName}/reverse=${n.uuidReverseLookupName}" },
            wirePathNames.all { it.uuidReverseLookupName != null },
        )

        // ---------------- 断言 2：usage 是网关返回的真数字 ----------------
        val perMessageUsage = assistants.map { message ->
            val usage = requireNotNull(message.usage) {
                "角色 ${message.roleId} 没有 usage —— 真实响应没带回 usage，这条 token 证据不成立"
            }
            assertTrue(
                "角色 ${message.roleId} 的 prompt/input tokens 必须为正，实际=${usage.promptTokens}",
                usage.promptTokens > 0,
            )
            assertTrue(
                "角色 ${message.roleId} 的 completion/output tokens 必须为正，实际=${usage.completionTokens}",
                usage.completionTokens > 0,
            )
            assertEquals(
                "角色 ${message.roleId} 的 totalTokens 必须等于 prompt+completion",
                usage.promptTokens + usage.completionTokens,
                usage.totalTokens,
            )
            usage
        }

        // ---------------- 断言 3：token 对账：Σ(prompt+completion) == spent_tokens ----------------
        val sumPromptCompletion = perMessageUsage.sumOf { it.promptTokens + it.completionTokens }
        assertEquals(
            "group_runs.spent_tokens 必须等于各条助手消息 (prompt+completion) 之和",
            sumPromptCompletion,
            run.spentTokens,
        )
        assertEquals("本轮应正常完成", GroupRunEntity.STATUS_COMPLETED, run.status)
        assertEquals("三个角色都必须进 committed 名单", listOf("a", "b", "c"), run.committedRoleIds)
        assertTrue("预算充足时不应有 skipped 角色，实际=${run.skippedRoleIds}", run.skippedRoleIds.isEmpty())
        assertEquals("预算上限快照必须等于配置值", realProviderBudget, run.tokenLimit)
        assertEquals("正常完成不应有 reason", "", run.reason)
        assertNotNull("运行日志必须已收尾（endedAt 非空）", run.endedAt)

        // ---------------- 断言 4：正文真的来自真实模型，且按格式回报自己的 ROLECODE ----------------
        // 这同时是「该角色的 system prompt 真的送到了该角色的模型」的证据：正文里
        // 出现且仅出现自己的代号，说明模型读到的 instructions 只有它自己那份。
        val realRoleCodePattern = Regex("""ROLECODE:([A-Z])""")
        listOf("a" to "A", "b" to "B", "c" to "C").forEach { (roleId, ownCode) ->
            val text = byRole.getValue(roleId).toText()
            val codes = realRoleCodePattern.findAll(text).map { it.groupValues[1] }.toList()
            assertTrue(
                "角色 $roleId 的回复里必须出现自己的 ROLECODE:$ownCode，实际=$codes；正文=$text",
                ownCode in codes,
            )
            assertEquals(
                "角色 $roleId 的回复里必须只出现自己的 ROLECODE（不得模拟他人），实际=$codes；正文=$text",
                listOf(ownCode),
                codes,
            )
        }

        // ---------------- 断言 5：turnKind ----------------
        assertTrue(
            "pipeline 三条发言都应是 turn_kind=speaker，实际=" +
                assistants.map { "${it.roleId}:${it.turnKind}" },
            assistants.all { it.turnKind == GroupChat.TURN_SPEAKER },
        )

        // ---------------- 断言 6：视角隔离（从真库读回的消息上判定） ----------------
        // 口径与 `C1DeviceEvidenceTest` 一致：只按库内消息判定，不依赖模型听不听话。
        val seenByA = GroupChat.buildContext("a", messages, config, null).mapNotNull { it.roleId }
        assertTrue("角色 a 只应看到自己的发言，不应看到 b 或 c，实际=$seenByA", seenByA.all { it == "a" })
        val seenByB = GroupChat.buildContext("b", messages, config, "a").mapNotNull { it.roleId }
        assertTrue("角色 b 应看到 a", "a" in seenByB)
        assertTrue("角色 b 不应看到 c，实际=$seenByB", "c" !in seenByB)
        val seenByC = GroupChat.buildContext("c", messages, config, "b").mapNotNull { it.roleId }
        assertTrue("角色 c 应看到 b", "b" in seenByC)
        assertTrue("角色 c 不应看到 a（pipeline 只串联上一位），实际=$seenByC", "a" !in seenByC)

        writeEvidence(
            "c1-live-evidence-real-provider.json",
            realProviderReport(conversationId, config, messages, run, sumPromptCompletion, perMessageUsage),
        )
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
                        // 网关自报的 wire 模型名原样落在这里（没报就是 null）。
                        // 不 trim、不归一、不用 modelId 回填——`model_id` 是本地配置 uuid，
                        // 两者对不上才说明网关做了别名映射，那正是要看见的差异。
                        put("wire_model_name", message.wireModelName)
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

    /**
     * roundtable 的证据报告。
     *
     * 除共用的 `device` / `bindings` / `messages` / `group_run` 块外，额外记下
     * 「议长看得见谁 / 看不见谁」的两组对照：带 `chairRound` 与不带 `chairRound` 调同一个
     * `buildContext`，两次结果必须不同——这才是「`chairRound` 是那个开关」的证据，
     * 而不是「可见集合里恰好有别人」。
     */
    private fun roundtableReport(
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
            put("mock_server_version", "mock_openai_v2.py")
            put("case", roundtableCaseName)
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
                listOf(
                    "a" to ViewerFlags(predecessorId = null, chairRound = false),
                    "b" to ViewerFlags(predecessorId = null, chairRound = false),
                    "c" to ViewerFlags(predecessorId = null, chairRound = false),
                    "c(chairRound=true)" to ViewerFlags(predecessorId = null, chairRound = true),
                ).forEach { (label, flags) ->
                    putJsonArray(label) {
                        GroupChat.buildContext(
                            viewerRoleId = if (label == "c(chairRound=true)") "c" else label,
                            messages = messages,
                            config = config,
                            predecessorId = flags.predecessorId,
                            chairRound = flags.chairRound,
                        ).map { it.id.toString() }.forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
            putJsonObject("roundtable_visibility_expectation") {
                put("a_sees", "system+user+own  (NOT b, NOT c)")
                put("b_sees", "system+user+own  (NOT a, NOT c)")
                put("c_without_chair_round", "system+user+own  (NOT a, NOT b)")
                put("c_with_chair_round", "system+user+own+a+b  <- chair may see the whole round")
            }
            putJsonObject("speaker_order_and_turn_kind") {
                assistants.forEach { message ->
                    put(message.roleId ?: "?", message.turnKind ?: "?")
                }
            }
            putJsonArray("assistant_role_order") {
                assistants.forEach { add(JsonPrimitive(it.roleId)) }
            }
        }
    }

    /**
     * vote 的证据报告。
     *
     * 额外记下三件从库里读回来的东西：**解析出的选票**（`GroupChat.parseBallot` 对落库
     * 正文的真实结果）、**多数决输出**（`GroupChat.tally` 的 winner/tally）、以及
     * `__summary__` 节点的正文与 `turn_kind`。
     */
    private fun voteReport(
        conversationId: Uuid,
        config: GroupConfig,
        messages: List<UIMessage>,
        run: GroupRunEntity,
        sumPromptCompletion: Int,
        ballots: List<VoteBallot>,
        decided: VoteOutcome.Decided,
    ): JsonObject {
        val assistants = messages.filter { it.role == MessageRole.ASSISTANT }
        val summary = assistants.firstOrNull { it.roleId == GroupChat.SUMMARY_ID }
        return buildJsonObject {
            put("evidence_kind", "real-http-capture-via-adb-reverse")
            put("token_source", "real-response-usage-from-mock-openai-server")
            put("model_sequence_source", "real-http-requests-logged-by-mock-server")
            put("generated_at_device", System.currentTimeMillis())
            put("mock_base_url", mockBaseUrl)
            put("mock_server_version", "mock_openai_v2.py")
            put("case", voteCaseName)
            put("conversation_id", conversationId.toString())
            put("mode", config.mode)
            put("chair_role_id", config.chairRoleId)
            put("vote_candidates", JsonArray(config.voteCandidates.map { JsonPrimitive(it) }))
            put("tie_policy", config.tiePolicy)
            put("token_budget_per_round", config.tokenBudgetPerRound)
            put("device", deviceBlock())
            put("bindings", bindingsBlock(config))
            put("messages", messageBlock(messages))
            put("group_run", runBlock(run))
            put("sum_prompt_plus_completion", sumPromptCompletion)
            putJsonObject("ballots_parsed_by_production_code") {
                put("source", "GroupChat.parseBallot over the message text read back from the database")
                put("ballot_prefix", GroupChat.BALLOT_PREFIX)
                putJsonArray("ballots") {
                    ballots.forEach { ballot ->
                        add(
                            buildJsonObject {
                                put("role_id", ballot.roleId)
                                put("candidate_id", ballot.candidateId)
                                put("reason", ballot.reason)
                            },
                        )
                    }
                }
            }
            putJsonObject("tally") {
                put("winner", decided.winner)
                putJsonObject("counts") {
                    decided.tally.forEach { (candidate, count) -> put(candidate, count) }
                }
                put("outcome_type", "VoteOutcome.Decided")
                put("tie_branch_taken", false)
            }
            putJsonObject("summary_node") {
                put("role_id", summary?.roleId ?: "<缺失>")
                put("turn_kind", summary?.turnKind ?: "<缺失>")
                put("round_id", summary?.roundId ?: "<缺失>")
                put("has_usage", summary?.usage != null)
                put("text", summary?.toText() ?: "<缺失>")
            }
            putJsonObject("vote_visibility_expectation") {
                put("a_sees", "system+user+own ballot  (NOT b, NOT c, NOT summary-of-votes)")
                put("b_sees", "system+user+own ballot  (NOT a, NOT c)")
                put("c_sees", "system+user+own ballot  (NOT a, NOT b)")
                put("summary_node", "synthetic; visible to every viewer but carries no model usage")
            }
            putJsonArray("assistant_role_order") {
                assistants.forEach { add(JsonPrimitive(it.roleId)) }
            }
        }
    }

    /**
     * 真实网关用例的证据报告。
     *
     * 与前三个报告的**关键差别**在 `token_source` / `model_sequence_source` 两行：
     * 前者是 mock 估算 + mock 请求日志，这里是**网关按真实分词返回的 usage** +
     * **网关响应体自己报的模型名**（`UIMessage.wireModelName`）。
     *
     * 因此本报告多记几段别处没有的东西：
     * - `provider_model_table`：uuid ↔ 上线模型名 ↔ abilities 的对照表。没有它，
     *   `uuid_reverse_lookup_model_string` 那列就只是断言自己的期望值，没法独立复核。
     *   ⚠️ 表里的 `wire_model_string` 键名是历史留下的，它是**本地表字段**，不是网关回传值。
     * - `actual_model_call_sequence` 里每条都带**两个**模型名（`wire_model_name` 与
     *   `uuid_reverse_lookup_model_string`）加一个 `wire_model_name_provenance`
     *   （`wire_response_model` = 网关自报原样串；`uuid_reverse_lookup_fallback` = wire 名为
     *   null 才走的**回退**）。两个都记，对不上才看得见；两者皆空会被断言 1b 挡掉。
     * - `wire_model_name_reconciliation`：三个计数（有几条有 wire 名、几条 wire 名与反查名
     *   不一致、几条一个名字都没有），外加独立复核工具 `c1_wire_model_probe.py` 的用法。
     */
    private fun realProviderReport(
        conversationId: Uuid,
        config: GroupConfig,
        messages: List<UIMessage>,
        run: GroupRunEntity,
        sumPromptCompletion: Int,
        perMessageUsage: List<TokenUsage>,
    ): JsonObject {
        val assistants = messages.filter { it.role == MessageRole.ASSISTANT }
        val wireModelNames = assistants.map { resolveWireModelName(it) }
        return buildJsonObject {
            put("evidence_kind", "real-gateway-call-direct-from-device-no-proxy")
            put("token_source", "genuine-usage-returned-by-public-openai-compatible-gateway")
            put(
                "model_sequence_source",
                "UIMessage.wireModelName (gateway 'model' field, verbatim) preferred; " +
                    "UIMessage.modelId uuid reverse-lookup only as a recorded fallback",
            )
            // 这一行说的是**判定口径**（每条发言各自的取值在 actual_model_call_sequence
            // 里的 wire_model_name_provenance 字段，逐条不混）。
            put(
                "wire_model_name_provenance",
                "per-call; allowed values: " +
                    "'$PROVENANCE_WIRE_RESPONSE_MODEL' = the gateway's own 'model' field, verbatim; " +
                    "'$PROVENANCE_UUID_REVERSE_LOOKUP_FALLBACK' = wireModelName was null, so the name was " +
                    "reverse-looked-up from the UIMessage.modelId uuid via provider_model_table below " +
                    "(a FALLBACK, not a wire-level observation). Both names are emitted for every call " +
                    "so a mismatch stays visible. See wire_model_name_provenance_counts for the tally.",
            )
            putJsonObject("wire_model_name_provenance_counts") {
                put(
                    PROVENANCE_WIRE_RESPONSE_MODEL,
                    wireModelNames.count { !it.fallbackTaken },
                )
                put(
                    PROVENANCE_UUID_REVERSE_LOOKUP_FALLBACK,
                    wireModelNames.count { it.fallbackTaken },
                )
            }
            putJsonObject("wire_model_name_reconciliation") {
                put(
                    "note",
                    "wire_model_name vs uuid_reverse_lookup_model_string, per call; null means " +
                        "that source had no value for that call",
                )
                put(
                    "calls_with_wire_name",
                    wireModelNames.count { it.wireModelName != null },
                )
                put(
                    "calls_where_both_names_present_and_differ",
                    wireModelNames.count { name ->
                        name.wireModelName != null &&
                            name.uuidReverseLookupName != null &&
                            name.wireModelName != name.uuidReverseLookupName
                    },
                )
                put(
                    "calls_with_no_name_at_all",
                    wireModelNames.count { it.wireModelName == null && it.uuidReverseLookupName == null },
                )
                put(
                    "independent_recheck_tool",
                    "tools/verification/c1_wire_model_probe.py --base-url <url> --api-key <key> " +
                        "--model <name>; run it separately and compare against wire_model_name here " +
                        "(the app-side parse is covered by WireModelNameProvenanceTest)",
                )
            }
            put("generated_at_device", System.currentTimeMillis())
            put("provider_id", realProvider.id.toString())
            put("provider_name", realProvider.name)
            put("provider_base_url", realProvider.baseUrl)
            put("provider_uses_response_api", realProvider.useResponseApi)
            put("case", realCaseName)
            put("conversation_id", conversationId.toString())
            put("mode", config.mode)
            put("chair_role_id", config.chairRoleId)
            put("token_budget_per_round", config.tokenBudgetPerRound)
            put("device", deviceBlock())
            // `wire_model_string` 这个键名是历史留下的，读起来像抓包。**它不是。**
            // 它是本地 provider 模型表里的 `Model.modelId`，只用来解释「这个 uuid 对应
            // 哪个上线名」，不能当成网关回传的值。真正的 wire 名在
            // actual_model_call_sequence 的 wire_model_name 字段。
            put("provider_model_table_note", TABLE_WIRE_MODEL_STRING_NOTE)
            putJsonArray("provider_model_table") {
                realProvider.models.forEach { model ->
                    add(
                        buildJsonObject {
                            put("uuid", model.id.toString())
                            put("wire_model_string", model.modelId)
                            put("display_name", model.displayName)
                            put("type", model.type.name)
                            put("abilities", JsonArray(model.abilities.map { JsonPrimitive(it.name) }))
                        },
                    )
                }
            }
            putJsonArray("bindings") {
                config.roles.forEach { role ->
                    add(
                        buildJsonObject {
                            put("role_id", role.id)
                            put("assistant_id", role.assistantId)
                            put("chair", role.chair)
                            put("model_uuid", role.modelId)
                            put(
                                "wire_model_string",
                                realProvider.models.first { it.id.toString() == role.modelId }.modelId,
                            )
                            put("wire_model_string_source", TABLE_WIRE_MODEL_STRING_NOTE)
                        },
                    )
                }
            }
            put("messages", messageBlock(messages))
            put("group_run", runBlock(run))
            put("sum_prompt_plus_completion", sumPromptCompletion)
            put("spent_tokens", run.spentTokens)
            put("spent_tokens_equals_sum_prompt_plus_completion", run.spentTokens == sumPromptCompletion)
            putJsonArray("actual_model_call_sequence") {
                assistants.forEachIndexed { index, message ->
                    val uuid = message.modelId
                    val name = wireModelNames[index]
                    add(
                        buildJsonObject {
                            put("seq", index + 1)
                            put("role_id", message.roleId)
                            put("turn_kind", message.turnKind)
                            put("model_uuid", uuid?.toString())
                            // 两个名字**都**输出，即使采信的是其中一个：对不上时要看得见。
                            put("wire_model_name", name.wireModelName)
                            put("uuid_reverse_lookup_model_string", name.uuidReverseLookupName)
                            put("resolved_model_name", name.resolvedModelName)
                            put("wire_model_name_provenance", name.provenance)
                            // 两者都在时是否逐字一致；只在一侧存在时为 null（无从比起）。
                            put(
                                "wire_and_reverse_lookup_agree",
                                if (name.wireModelName == null || name.uuidReverseLookupName == null) {
                                    null
                                } else {
                                    name.wireModelName == name.uuidReverseLookupName
                                },
                            )
                            put("usage_prompt_tokens", perMessageUsage[index].promptTokens)
                            put("usage_completion_tokens", perMessageUsage[index].completionTokens)
                            put("usage_total_tokens", perMessageUsage[index].totalTokens)
                            put("cached_tokens", perMessageUsage[index].cachedTokens)
                            put("rolecodes_found_in_text", JsonArray(realRoleCodeRegex.findAll(message.toText()).map { JsonPrimitive(it.groupValues[1]) }.toList()))
                        },
                    )
                }
            }
            putJsonArray("expected_model_call_sequence") {
                add(JsonPrimitive("deepseek-v4-flash"))
                add(JsonPrimitive("glm-5.2"))
                add(JsonPrimitive("deepseek-v4-flash"))
            }
            put(
                "expected_sequence_compared_against",
                "actual_model_call_sequence[].resolved_model_name (wire-preferred)",
            )
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
                put("a_sees", "system+user+own  (NOT b, NOT c)")
                put("b_sees", "system+user+own+a  (NOT c)")
                put("c_sees", "system+user+own+b  (NOT a)")
            }
            putJsonArray("assistant_role_order") {
                assistants.forEach { add(JsonPrimitive(it.roleId)) }
            }
        }
    }

    /** 从角色正文里抽 ROLECODE 代号；与 [realPersona] 的格式约定成对。 */
    private val realRoleCodeRegex = Regex("""ROLECODE:([A-Z])""")

    /** [roundtableReport] 里描述一次 `buildContext` 调用的两个可选参数。 */
    private data class ViewerFlags(val predecessorId: String?, val chairRound: Boolean)

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

    /**
     * 一次调用的模型名判定结果。
     *
     * @param wireModelName 网关响应体 / SSE 帧顶层 `model` 字段里的原样字符串（`UIMessage.wireModelName`）。
     *   为 null 表示**网关没报**（或只报了空白），不是「模型名为空串」。
     * @param uuidReverseLookupName 拿 `UIMessage.modelId`（本地配置 uuid）去
     *   [realProvider] 模型表反查出来的 `Model.modelId`。**无论走没走 wire 路径都记下来**，
     *   这样将来两者对不上时能一眼看出是网关改了别名还是本地表漂了。
     * @param resolvedModelName 本次采信的模型名（wire 优先，否则反查）。
     * @param fallbackTaken 是否退回了 uuid 反查。
     */
    private data class WireModelName(
        val wireModelName: String?,
        val uuidReverseLookupName: String?,
        val resolvedModelName: String,
        val provenance: String,
        val fallbackTaken: Boolean,
    )

    /**
     * 模型名**优先读 wire 级**：`UIMessage.wireModelName` 是网关响应体 / SSE 帧顶层
     * `model` 字段里的原样字符串，由 `StreamChunkHandler` 的 `StreamChunk.Finish` 分支
     * 落盘（`ai/.../ui/StreamChunkHandler.kt:297-303`），非流式路径由
     * `handleTextGenerationResult` 对称写入。
     *
     * 判定顺序（**只有 wire 名缺失才回退**，两个都拿不到就是硬失败，不允许静默放过）：
     * 1. `wireModelName` 非空 → 采信它，provenance = [PROVENANCE_WIRE_RESPONSE_MODEL]。
     *    空白串不算有效名（网关没报 ≠ 模型名为空）。
     * 2. `wireModelName == null` → 回退到 `modelId` uuid 经 provider 模型表反查，
     *    provenance = [PROVENANCE_UUID_REVERSE_LOOKUP_FALLBACK]，**必须**落进证据 JSON。
     * 3. 两条都空 → [WIRE_MODEL_NAME_MISSING]，由「不允许两者皆空」那条断言当场炸掉。
     *
     * 为什么 2 还留着：wire 名依赖网关**主动**报 `model`。真实网关不报时仍要能记录
     * 调用序列，只是必须诚实标成回退，不能让它冒充 wire 级证据。
     */
    private fun resolveWireModelName(message: UIMessage): WireModelName {
        val wire = message.wireModelName?.takeIf { it.isNotBlank() }
        val reverseLookup = message.modelId?.let { uuid ->
            realProvider.models.firstOrNull { it.id == uuid }?.modelId
        }
        return if (wire != null) {
            WireModelName(
                wireModelName = wire,
                uuidReverseLookupName = reverseLookup,
                resolvedModelName = wire,
                provenance = PROVENANCE_WIRE_RESPONSE_MODEL,
                fallbackTaken = false,
            )
        } else {
            WireModelName(
                wireModelName = null,
                uuidReverseLookupName = reverseLookup,
                resolvedModelName = reverseLookup ?: WIRE_MODEL_NAME_MISSING,
                provenance = PROVENANCE_UUID_REVERSE_LOOKUP_FALLBACK,
                fallbackTaken = true,
            )
        }
    }

    private companion object {
        const val EVIDENCE_DB = "c1-live-evidence.db"
        const val TRACE_TAG = "C1Live"
        const val TRACE_FILE = "c1-live-trace.txt"

        /** wire 名来自网关响应体 / SSE 帧顶层 `model` 字段，原样字符串。 */
        const val PROVENANCE_WIRE_RESPONSE_MODEL = "wire_response_model"

        /**
         * wire 名为 null，回退到 `UIMessage.modelId` uuid 经 provider 模型表反查。
         * 语义必须说清是**回退**：这种取法不是 wire 级观测。
         */
        const val PROVENANCE_UUID_REVERSE_LOOKUP_FALLBACK = "uuid_reverse_lookup_fallback"

        /** wire 名与反查名都拿不到时的占位符；出现即意味着那条断言已经炸了。 */
        const val WIRE_MODEL_NAME_MISSING = "<wire 名缺失且 uuid 反查不到>"

        /** `provider_model_table` 里那一列来自本地模型表，不是网关回传的。 */
        const val TABLE_WIRE_MODEL_STRING_NOTE =
            "local provider_model_table field; NOT the value echoed back by the gateway"

        val PRETTY = Json { prettyPrint = true; encodeDefaults = true }
    }
}

private fun resolveAppContext(): Application =
    InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application