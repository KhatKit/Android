package heizige.kk.khatkit.app.core.data.db

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.core.TokenUsage
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.core.data.db.fts.MessageFtsManager
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.toMessageNode
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FilesRepository
import heizige.kk.khatkit.app.core.data.datastore.DEFAULT_ASSISTANT_ID
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
 * C1-U 真机 UI 端到端行为的**夹具准备 + 状态读回**工具。
 *
 * ## 为什么写进**主库**
 *
 * 契约 `docs/beyond-operit-client-changes.md:232-235` 明写「只看截图或只看 UI 状态
 * 均标记 unverified」。所以 UI 那一侧要采的是「adb 操作 → 真实状态读回」，
 * 而群聊页只认**主库**里的会话：数据必须落 `rikka_hub`，UI 才看得见。
 * 因此这里刻意用 `AppDatabaseFactory.create(context)`（**不带库名参数**，
 * 即 `SQLiteConfiguration.DATABASE_NAME` = `rikka_hub`），与生产同一个工厂、
 * 同一套 SQLite 扩展、同一个 `onOpen` 回调、同一个 schema 版本。
 *
 * `install -r -t` 不清数据、`am instrument` 也不清，所以本类写进去的会话会**留在设备上**
 * 供 UI 侧使用——这正是本类存在的意义。数据 id 一律固定（不许随机 Uuid），
 * 重复运行是幂等覆盖，不会造出一堆同名会话把抽屉塞满。
 *
 * ## 这个类刻意不做的事
 *
 * **不发起任何 LLM 调用，也不伪造任何 LLM 输出。** 消息正文是固定夹具文本，
 * `modelId` 固定，设备上有没有可用模型与本类无关。`parseMentionsFromUiText`
 * 那条也不发请求：它只是把**从 UI dump 里读回来的文本**喂给生产的
 * `GroupChat.parseMentions`，判定「UI 插入的 @ 文本能不能被路由层认出来」。
 *
 * ## 用法
 *
 * ```
 * # 准备夹具（幂等）
 * am instrument -w -e class ...C1GroupUiE2EFixtureTest#seedGroupConversationIntoMainDb \
 *     heizige.kk.khatkit.debug.test/heizige.kk.khatkit.app.test.runner.AndroidJUnitRunner
 *
 * # 状态读回（可带 -e uiText "<从 dump 读回的输入框文本>"）
 * am instrument -w -e uiText "..." -e class ...C1GroupUiE2EFixtureTest#readBackMainDbState \
 *     ...
 * ```
 */
@RunWith(AndroidJUnit4::class)
class C1GroupUiE2EFixtureTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var database: AppDatabase
    private lateinit var repository: ConversationRepository

    // ---------------- 固定 id：重复运行幂等 ----------------

    private val groupConvId = Uuid.parse("0c1c0de5-0000-0000-0000-00000000e2e0")
    private val directConvIdA = Uuid.parse("0c1c0de5-0000-0000-0000-00000000e2e1")
    private val directConvIdB = Uuid.parse("0c1c0de5-0000-0000-0000-00000000e2e2")

    /**
     * 设备上**真实存在**的助手 id 与三个真实模型 id。
     *
     * 为什么必须真读：群聊气泡上那行模型名是 `ChatList.resolveMessageModel` 用
     * `message.modelId` 去 `settings.providers` 里查出来的（`ChatMessageAvatar.kt:114-129`），
     * 库里查不到就**一个字符都不显示**。拿合成 Uuid 当 `modelId`，气泡上根本不会出现模型名，
     * 「气泡模型名 == 库里 modelId」这条就无从对账。所以这里真读一遍 DataStore：
     * 有真实模型就用真的（并且尽量取**三个不同**的，这样三条气泡的模型名可区分）；
     * 一个都没有就退回落成的假 id，并把 `real_model_count=0` 如实写进证据。
     */
    private val settings: Settings = runBlocking {
        SettingsRepository(
            context = context,
            scope = AppScope(),
            pebbleEngine = dagger.Lazy { PebbleEngine.Builder().build() },
        ).settingsFlow.first()
    }

    private val fixtureAssistantId: Uuid by lazy {
        settings.assistants.firstOrNull()?.id ?: DEFAULT_ASSISTANT_ID
    }

    private val realModels: List<Model> by lazy {
        settings.providers.flatMap { it.models }.distinctBy { it.id }
    }

    private fun modelFor(slot: Int): Uuid =
        realModels.getOrNull(slot)?.id ?: syntheticModel(slot)

    private fun syntheticModel(slot: Int): Uuid =
        Uuid.parse("0c1c0de5-0000-0000-0000-0000000f0a0${slot + 1}")

    /**
     * 夹具群配置：**名字里带空格**的角色是本类的存在理由之一。
     *
     * `Alice Johnson` 的多词名是 `GroupRoleCompletionProvider` 与 `GroupChat.parseMentions`
     * 两条口径的分水岭：前者锚定名字开头（`john` 匹配不上），后者按整名 + 词边界匹配。
     * 没有这个角色，②③ 两项的防伪边界就无从验证。
     *
     * 议长 `伽马`（`chair = true`）必须留：③ 里候选列表的议长加权与
     * `GroupSpeakerResolver.BADGE_CHAIR` 徽章都靠它才有可测的差异。
     */
    private val fixtureConfig = GroupConfig(
        roles = listOf(
            GroupRole(id = "alice", name = "Alice Johnson", assistantId = fixtureAssistantId.toString()),
            GroupRole(id = "alpha", name = "阿尔法", assistantId = fixtureAssistantId.toString()),
            GroupRole(
                id = "gamma",
                name = "伽马",
                assistantId = fixtureAssistantId.toString(),
                chair = true,
            ),
        ),
        mode = GroupChat.MODE_PIPELINE,
        chairRoleId = "gamma",
        tokenBudgetPerRound = 200_000,
        revision = 1,
    )

    @Before
    fun setUp() {
        // **不带库名**：这就是 app 主库。UI 只读主库，写别的库 UI 看不见。
        database = AppDatabaseFactory.create(context)
        repository = ConversationRepository(
            conversationDAO = database.conversationDao(),
            messageNodeDAO = database.messageNodeDao(),
            database = database,
            filesManager = FilesManager(context, FilesRepository(database.managedFileDao()), AppScope()),
            messageFtsManager = MessageFtsManager(database),
        )
    }

    @After
    fun tearDown() {
        // 故意**不删**主库：本类写的会话就是 UI 侧要用的夹具。
        if (::database.isInitialized) database.close()
    }

    // ==================================================================
    // 用例 1：把 3 角色群会话（+ 两条单聊）写进主库并读回
    // ==================================================================

    @Test
    fun seedGroupConversationIntoMainDb() = runBlocking {
        // 幂等：先删自己那三条再插。反复跑不撞 UNIQUE 约束，也不会造出一堆同名会话
        // 把抽屉塞满（`am instrument` 不清数据，所以这三条会一直在设备上）。
        listOf(groupConvId, directConvIdA, directConvIdB).forEach { id ->
            repository.deleteConversation(
                Conversation(
                    id = id,
                    assistantId = DEFAULT_ASSISTANT_ID,
                    title = "",
                    messageNodes = emptyList(),
                ),
            )
        }

        val messages = buildFixtureMessages()
        repository.insertConversation(
            Conversation(
                id = groupConvId,
                assistantId = DEFAULT_ASSISTANT_ID,
                title = "C1 UI E2E 群聊",
                messageNodes = messages.map { it.toMessageNode() },
                type = GroupChat.TYPE_GROUP,
                groupConfig = fixtureConfig,
            ),
        )
        // 两条单聊：让 ④ 的类型筛选 chip 有「另一类」可比，否则筛「单聊」看到 0 条
        // 无法区分「过滤生效」与「数据被丢了」。
        listOf(directConvIdA to "C1 UI E2E 单聊甲", directConvIdB to "C1 UI E2E 单聊乙")
            .forEach { (id, title) ->
                repository.insertConversation(
                    Conversation(
                        id = id,
                        assistantId = DEFAULT_ASSISTANT_ID,
                        title = title,
                        messageNodes = listOf(
                            UIMessage(
                                id = Uuid.random(),
                                role = MessageRole.USER,
                                parts = listOf(UIMessagePart.Text("单聊夹具 $title")),
                            ).toMessageNode(),
                        ),
                    ),
                )
            }

        // ---------- 读回：必须是从真 SQLite 读出来的对象 ----------
        val stored = repository.getConversationById(groupConvId)
        assertNotNull("群会话必须能从主库读回", stored)
        val conversation = requireNotNull(stored)
        assertEquals("会话类型必须是 GROUP", GroupChat.TYPE_GROUP, conversation.type)
        assertEquals("群配置必须逐字段往返", fixtureConfig, conversation.groupConfig)

        val storedMessages = conversation.currentMessages
        assertEquals("消息条数必须与写入一致", messages.size, storedMessages.size)

        emit(buildList {
        add("UI-E2E-FIXTURE-BEGIN")
        add("database_absolute_path=${database.openHelper.readableDatabase.path}")
        add("database_version=${database.openHelper.readableDatabase.version}")
        add("device_model=${Build.MODEL}")
        add("device_sdk=${Build.VERSION.SDK_INT}")
        add("package_name=${context.packageName}")
        add("conversation_id=$groupConvId")
        add("conversation_title=${conversation.title}")
        add("conversation_type=${conversation.type}")
        add("message_nodes_count=${conversation.messageNodes.size}")
        add("current_messages_count=${storedMessages.size}")
        add("roles=${fixtureConfig.roles.joinToString("|") { "${it.id}=${it.name}${if (it.chair) "(chair)" else ""}" }}")
        add("mode=${fixtureConfig.mode}")
        add("chair_role_id=${fixtureConfig.chairRoleId}")
        add("token_budget_per_round=${fixtureConfig.tokenBudgetPerRound}")
        add("real_model_count=${realModels.size}")
        add("fixture_assistant_id=$fixtureAssistantId")
        settings.assistants.forEach { a ->
            add("device_assistant=${a.id} name=${a.name} useAssistantAvatar=${a.useAssistantAvatar}")
        }
        realModels.forEachIndexed { i, m ->
            add("device_model[$i]=${m.id} displayName=${m.displayName} modelId=${m.modelId}")
        }
        add("direct_conversation_ids=$directConvIdA,$directConvIdB")
        // 逐条把「气泡上该显示什么」先算清楚，供 UI 侧逐条对账。
        storedMessages.forEach { message ->
            add(
                "msg=${message.id} role=${message.role.name} role_id=${message.roleId} " +
                    "round_id=${message.roundId} turn_kind=${message.turnKind} " +
                    "model_id=${message.modelId} text=${message.toText()}",
            )
        }
        add("UI-E2E-FIXTURE-END")
        })
    }

    // ==================================================================
    // 用例 2：从真 SQLite 读回节点数 + 按 type 分组计数（① 与 ④ 的硬证据）
    // ==================================================================

    @Test
    fun readBackMainDbState() {
        val db = database.openHelper.readableDatabase

        // ① 该会话的 message_node 行数（真 SQL，不是内存对象）
        val nodeCount = db.query(
            "SELECT COUNT(*) FROM message_node WHERE conversation_id = ?",
            arrayOf(groupConvId.toString()),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }

        // 消息条数（message_node → messages JSON 展开），与 ① 的「UI 应显示条数」对账用。
        val stored = runBlocking { repository.getConversationById(groupConvId) }
        assertNotNull("群会话必须能从主库读回", stored)
        val conversation = requireNotNull(stored)

        // ④ 类型筛选的硬证据：按 type 分组计数。
        val byType = mutableListOf<String>()
        db.query("SELECT type, COUNT(*) FROM ConversationEntity GROUP BY type ORDER BY type").use { c ->
            while (c.moveToNext()) {
                byType += "${c.getString(0)}=${c.getInt(1)}"
            }
        }
        val totalRows = db.query("SELECT COUNT(*) FROM ConversationEntity").use { c ->
            if (c.moveToFirst()) c.getInt(0) else -1
        }
        val groupedSum = byType.sumOf { it.substringAfter('=').toInt() }

        // 「来回切换不丢数据」的可测形式：分组计数之和恒等于总行数。
        assertEquals(
            "按 type 分组计数之和必须等于总行数（筛选只过滤、不丢行）",
            totalRows,
            groupedSum,
        )
        assertTrue("夹具群会话必须在库里", conversation.type == GroupChat.TYPE_GROUP)
        assertTrue("夹具单聊必须在库里（供类型筛选对比）", totalRows >= 3)

        emit(buildList {
        add("UI-E2E-READBACK-BEGIN")
        add("db_path=${db.path}")
        add("conversation_entity_rows=$totalRows")
        add("conversation_type_counts=${byType.joinToString("|")}")
        add("group_conv_message_node_rows=$nodeCount")
        add("group_conv_message_nodes_count=${conversation.messageNodes.size}")
        add("group_conv_current_messages_count=${conversation.currentMessages.size}")
        add(
            "group_conv_current_message_ids=" +
                conversation.currentMessages.joinToString(",") { it.id.toString() },
        )
        add(
            "group_conv_current_message_model_ids=" +
                conversation.currentMessages.joinToString(",") { it.modelId?.toString().orEmpty() },
        )
        add(
            "group_conv_current_message_role_ids=" +
                conversation.currentMessages.joinToString(",") { it.roleId.orEmpty() },
        )
        add(
            "group_conv_current_message_turn_kinds=" +
                conversation.currentMessages.joinToString(",") {
                    it.turnKind ?: if (it.role == MessageRole.USER) GroupChat.TURN_USER else GroupChat.TURN_SPEAKER
                },
        )
        add("UI-E2E-READBACK-END")
        })
    }

    // ==================================================================
    // 用例 3：把**从 UI dump 读回来的输入框文本**喂给生产的 parseMentions
    // ==================================================================

    /**
     * `-e uiText "<uiautomator dump 里输入框的 text 属性>"`。
     *
     * 这个用例存在的理由：②③ 断言的是「UI 插入的文本**能不能被路由层认出来**」，
     * 光看输入框里有 `@Alice Johnson ` 不够——`GroupChat.parseMentions` 是真正决定
     * 消息投给谁的那道判定（`GroupChat.mentioned`：整名 + 前后非词字符边界）。
     * 让判定在**设备上**跑一次，才算状态读回而不是自我观察。
     *
     * 同时把**反向**边界一起钉死：同一份夹具里 `@johnson` 必须解析不出任何角色
     * （整词边界要求前后非词字符，而 `johnson` 前面是 `@`… 但它不是整名，
     * 所以 `mentioned(text, "Alice Johnson")` 为假）——这就是防伪 @ 的路由侧一半。
     */
    @Test
    fun parseMentionsFromUiText() {
        val uiText = InstrumentationRegistry.getArguments()?.getString("uiText")
        assertNotNull("必须用 -e uiText 传入从 uiautomator dump 读回的输入框文本", uiText)
        val text = requireNotNull(uiText)

        val parsed = GroupChat.parseMentions(text, fixtureConfig.roles)

        emit(buildList {
        add("UI-E2E-MENTIONS-BEGIN")
        add("ui_text=$text")
        add("parsed_role_ids=$parsed")
        fixtureConfig.roles.forEach { role ->
            val hit = role.id in parsed
            add("role=${role.id} name=${role.name} mentioned=$hit")
        }
        // 反向边界：伪 @（只打名字的一个片段）在路由侧也不该命中。
        add("anti_spoof_at_johnson=${GroupChat.parseMentions("@johnson ", fixtureConfig.roles)}")
        add("anti_spoof_at_john=${GroupChat.parseMentions("@john ", fixtureConfig.roles)}")
        add("anti_spoof_email=${GroupChat.parseMentions("mail me at a@b.com ", fixtureConfig.roles)}")
        add("positive_at_alice=${GroupChat.parseMentions("@Alice Johnson ", fixtureConfig.roles)}")
        add("positive_at_gamma=${GroupChat.parseMentions("收尾 @伽马 ", fixtureConfig.roles)}")
        add("UI-E2E-MENTIONS-END")
        })

        assertTrue(
            "UI 读回的文本里若含 @Alice Johnson，必须被 parseMentions 命中 alice",
            text.contains("@Alice Johnson") == parsed.contains("alice"),
        )
    }

    // ==================================================================
    // 夹具消息：真实 roleId / roundId / turnKind
    // ==================================================================

    private fun buildFixtureMessages(): List<UIMessage> {
        val round1 = GroupChat.roundIdFor(user1Id.toString())
        val round2 = GroupChat.roundIdFor(user2Id.toString())
        val user1Text = "@Alice Johnson 请先定调这一轮的总论。"
        val user2Text = "继续，展开细节。"
        val summary = UIMessage(
            id = summaryId,
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("本轮小结：先列事实，再给判断。")),
            roleId = GroupChat.SUMMARY_ID,
            roundId = round2,
            turnKind = GroupChat.TURN_VOTE_SUMMARY,
            modelId = modelFor(0),
        )

        return listOf(
            UIMessage(
                id = user1Id,
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text(user1Text)),
                mentionRoleIds = GroupChat.parseMentions(user1Text, fixtureConfig.roles),
            ),
            speaker("alice", round1, a1Id, modelFor(0), "Alice 的第一轮发言：先把口径统一。"),
            UIMessage(
                id = user2Id,
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text(user2Text)),
                mentionRoleIds = GroupChat.parseMentions(user2Text, fixtureConfig.roles),
            ),
            speaker("alice", round2, a2Id, modelFor(0), "Alice 展开：先列事实，再给判断。"),
            speaker("alpha", round2, b2Id, modelFor(1), "阿尔法挑错：第二条缺乏可验证来源。"),
            speaker("gamma", round2, c2Id, modelFor(2), "伽马收束：按 Alice 的口径执行。"),
            summary,
        )
    }

    private fun speaker(
        roleId: String,
        roundId: String,
        id: Uuid,
        modelId: Uuid,
        text: String,
    ) = UIMessage(
        id = id,
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(text)),
        roleId = roleId,
        roundId = roundId,
        turnKind = GroupChat.TURN_SPEAKER,
        modelId = modelId,
        usage = TokenUsage(promptTokens = 100, completionTokens = 30, totalTokens = 130),
    )

    // ==================================================================
    // 证据落盘：`am instrument -w` 不回传 stdout（实测只回 Time/OK），
    // 所以证据既 println 又**追加**到设备文件，adb pull 直接拿走。
    // 追加而非覆盖：seed 与 readback、parseMentions 三段拼成一份时间线。
    // ==================================================================

    private fun emit(lines: List<String>) {
        lines.forEach(::println)
        val dir = requireNotNull(context.getExternalFilesDir(null)) {
            "getExternalFilesDir(null) 返回 null，无法落证据"
        }
        File(dir, EVIDENCE_FILE).appendText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }

    private companion object {
        const val EVIDENCE_FILE = "ui-e2e-evidence.txt"

        val user1Id = Uuid.parse("0c1c0de5-0000-0000-0000-00000000e101")
        val user2Id = Uuid.parse("0c1c0de5-0000-0000-0000-00000000e102")
        val a1Id = Uuid.parse("0c1c0de5-0000-0000-0000-00000000e201")
        val a2Id = Uuid.parse("0c1c0de5-0000-0000-0000-00000000e202")
        val b2Id = Uuid.parse("0c1c0de5-0000-0000-0000-00000000e203")
        val c2Id = Uuid.parse("0c1c0de5-0000-0000-0000-00000000e204")
        val summaryId = Uuid.parse("0c1c0de5-0000-0000-0000-00000000e205")
    }
}