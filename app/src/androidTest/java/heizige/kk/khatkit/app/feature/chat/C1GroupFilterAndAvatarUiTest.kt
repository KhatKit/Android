package heizige.kk.khatkit.app.feature.chat

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.RouteActivity
import heizige.kk.khatkit.app.core.data.datastore.DEFAULT_ASSISTANT_ID
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.db.AppDatabase
import heizige.kk.khatkit.app.core.data.db.AppDatabaseFactory
import heizige.kk.khatkit.app.core.data.db.fts.MessageFtsManager
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.toMessageNode
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FilesRepository
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.uuid.Uuid

/**
 * C1 硬理由②「真机 UI 端到端」的 androidTest 级真断言（第五轮）。
 *
 * 背景：第九轮的 chip 过滤与成员头像组证据全部来自「adb 操作 + uiautomator dump
 * + 截图目视」。契约 `docs/beyond-operit-client-changes.md:206` 明文
 * 「不得仅凭 UI 截图勾选」，`:232-235` 又写「只看 UI 状态 → unverified」，
 * 所以那些永远不足以升级验收。本类把三件事变成**跑在被测 app 进程里的断言**：
 *
 * 1. `typeFilterChipsFilterWithoutLosingData` —— 筛选 chip 真按类型过滤、切回不丢；
 * 2. `groupMemberBarRendersOneAvatarPerRole` —— 成员头像组真的渲染且数量=角色数；
 * 3. `atMentionPickerInsertsParseableMention` —— `@` 选择器插入的文本能被生产
 *    `GroupChat.parseMentions` 解析出正确 `role_id`。
 *
 * ## 为什么用 `createEmptyComposeRule` + 手动 `ActivityScenario.launch`
 *
 * 三条测试都必须先 seed 数据、再启动被测 UI，而 `createAndroidComposeRule` 会在
 * `@Before` 之前就 launch。这里用空的 compose rule（只初始化测试环境），在 `@Before`
 * 里先把夹具写进**主库**（UI 只读主库），再用 `conversationId` extra 直接启动
 * `RouteActivity` 到群聊页（`RouteActivity.kt:267` 支持的入口）。于是测试跑的是
 * **完整生产路径**：真 Hilt、真导航、真 Room、真 ChatViewModel/输入框/补全 provider。
 *
 * ## 与 `C1GroupUiE2EFixtureTest` 的关系
 *
 * 复用其「固定 id + 幂等 seed 主库 + 真库读回」的思路，但**独立新文件、独立 id 段**
 * （`...f1xx`），不碰它一个字节。它写 `...e2xx`，两者互不影响。
 *
 * ## 反脆弱：过滤断言用「可达性」而不是「当时在不在屏幕上」
 *
 * LazyColumn 只组合视口内的项，「不可见」可能是懒加载也可能是真被过滤。所以过滤
 * 断言做成：先证明目标在旧列表里可达 → 切 chip → 等它从语义树消失 → 再试
 * `performScrollToNode` 找它，**必须失败**（若过滤失效，它会被滚出来，断言红）。
 *
 * ## 允许的生产代码改动（本轮只加语义、不加逻辑）
 *
 * - `GroupMemberBar.kt` 容器 `.testTag("group_member_bar")`；
 * - `ChatDrawer.kt` 会话列表 `.testTag("drawer_conversation_list")`。
 *
 * 二者都只是定位锚点，不改布局/行为。
 */
@RunWith(AndroidJUnit4::class)
class C1GroupFilterAndAvatarUiTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: ConversationRepository
    private lateinit var settingsStore: SettingsRepository
    private lateinit var fixtureConfig: GroupConfig
    private var scenario: ActivityScenario<RouteActivity>? = null

    // ---------------- 固定 id：重复运行幂等（id 段 f1xx，与夹具的 e2xx 错开） ----------------

    private val groupAId = Uuid.parse("0c1c0de5-0000-0000-0000-00000000f1a1")
    private val groupBId = Uuid.parse("0c1c0de5-0000-0000-0000-00000000f1a2")
    private val directAId = Uuid.parse("0c1c0de5-0000-0000-0000-00000000f1b1")
    private val directBId = Uuid.parse("0c1c0de5-0000-0000-0000-00000000f1b2")

    @Before
    fun setUp() {
        database = AppDatabaseFactory.create(context)
        repository = ConversationRepository(
            conversationDAO = database.conversationDao(),
            messageNodeDAO = database.messageNodeDao(),
            database = database,
            filesManager = FilesManager(context, FilesRepository(database.managedFileDao()), AppScope()),
            messageFtsManager = MessageFtsManager(database),
        )
        settingsStore = SettingsRepository(
            context = context,
            scope = AppScope(),
            pebbleEngine = dagger.Lazy { PebbleEngine.Builder().build() },
        )
        // 证据文件每轮（整个 instrumentation 进程）只在第一个 @Before 清空一次：
        // 每个测试方法都会跑 @Before，若无条件删除会把前一个方法刚写的证据也删掉。
        if (EVIDENCE_CLEARED.compareAndSet(false, true)) {
            val evidenceDir = context.getExternalFilesDir(null)
            listOf("filter", "avatar", "mention").forEach { case ->
                evidenceDir?.let { File(it, "c1-round5-$case.txt").delete() }
            }
        }
        seedFixtures()
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        if (::database.isInitialized) database.close()
    }

    // ==================================================================
    // 测 1：筛选 chip 真的按类型过滤 + 切回不丢数据
    // ==================================================================

    @Test
    fun typeFilterChipsFilterWithoutLosingData() {
        openGroupChat(groupAId)
        openDrawer()

        val list = compose.onNodeWithTag(DRAWER_LIST_TAG)

        // ---------- 初始「全部」chips：4 条夹具会话（2 DIRECT + 2 GROUP）都可滚动到达 ----------
        assertReachable(list, GROUP_A)
        assertReachable(list, GROUP_B)
        assertReachable(list, DIRECT_A)
        assertReachable(list, DIRECT_B)

        // ---------- 切「单聊」：群条目必须不可达，单聊必须可达 ----------
        // 先让群条目在旧列表里处于组合状态，这样「消失」才是过滤生效的真信号。
        list.performScrollToNode(hasText(GROUP_A))
        compose.onNodeWithText(CHIP_DIRECT).performClick()
        compose.waitUntil(WAIT_MS) { !isReachableQuietly(list, GROUP_A) }

        assertFalse(
            "「单聊」筛选下群条目必须无法滚动到达（可达=过滤失效）",
            isReachableQuietly(list, GROUP_A),
        )
        assertFalse(
            "「单聊」筛选下群条目必须无法滚动到达（可达=过滤失效）",
            isReachableQuietly(list, GROUP_B),
        )
        assertEquals(
            "「单聊」筛选下抽屉列表里不得出现群条目（列表作用域计数）",
            0,
            countInDrawer(GROUP_A),
        )
        assertEquals(
            "「单聊」筛选下抽屉列表里不得出现群条目（列表作用域计数）",
            0,
            countInDrawer(GROUP_B),
        )
        assertReachable(list, DIRECT_A)
        assertReachable(list, DIRECT_B)
        emit(
            "filter",
            listOf(
                "case=direct",
                "group_a_reachable=false",
                "group_b_reachable=false",
                "direct_a_reachable=true",
                "direct_b_reachable=true",
            ),
        )

        // ---------- 切「群聊」：单聊必须不可达，群必须可达 ----------
        list.performScrollToNode(hasText(DIRECT_A))
        compose.onNodeWithText(CHIP_GROUP).performClick()
        compose.waitUntil(WAIT_MS) { !isReachableQuietly(list, DIRECT_A) }

        assertFalse(
            "「群聊」筛选下单聊条目必须无法滚动到达（可达=过滤失效）",
            isReachableQuietly(list, DIRECT_A),
        )
        assertFalse(
            "「群聊」筛选下单聊条目必须无法滚动到达（可达=过滤失效）",
            isReachableQuietly(list, DIRECT_B),
        )
        assertEquals(
            "「群聊」筛选下抽屉列表里不得出现单聊条目（列表作用域计数）",
            0,
            countInDrawer(DIRECT_A),
        )
        assertEquals(
            "「群聊」筛选下抽屉列表里不得出现单聊条目（列表作用域计数）",
            0,
            countInDrawer(DIRECT_B),
        )
        assertReachable(list, GROUP_A)
        assertReachable(list, GROUP_B)

        // ---------- 切回「全部」：4 条条目全部回来（与初始完全相同），且 DB 4 条都在 ----------
        list.performScrollToNode(hasText(GROUP_A))
        compose.onNodeWithText(CHIP_ALL).performClick()
        compose.waitUntil(WAIT_MS) { isReachableQuietly(list, DIRECT_A) }

        assertReachable(list, GROUP_A)
        assertReachable(list, GROUP_B)
        assertReachable(list, DIRECT_A)
        assertReachable(list, DIRECT_B)

        val dbState = runBlocking {
            listOf(groupAId, groupBId, directAId, directBId).map { id ->
                val c = repository.getConversationById(id)
                assertNotNull("切回「全部」后会话 $id 必须仍在主库（筛选只过滤不删）", c)
                "${c!!.title}=${c.type}"
            }
        }
        assertEquals(
            "DB 里 4 条夹具会话必须原样存在",
            listOf(
                "$GROUP_A=${GroupChat.TYPE_GROUP}",
                "$GROUP_B=${GroupChat.TYPE_GROUP}",
                "$DIRECT_A=${GroupChat.TYPE_DIRECT}",
                "$DIRECT_B=${GroupChat.TYPE_DIRECT}",
            ),
            dbState,
        )
        emit(
            "filter",
            buildList {
                add("case=switch_back_all")
                add("fixture_rows_in_db=${dbState.size}")
                dbState.forEach { add("db=$it") }
                add("all_titles_reachable=true")
            },
        )
    }

    // ==================================================================
    // 测 2：群聊页的成员头像组真的渲染，数量等于群角色数
    // ==================================================================

    /**
     * 关于「群条目」的口径：生产实现里「成员头像组」只有一个落点——
     * `GroupChatPage.kt:287` 挂到输入框上方的 [GroupMemberBar]；会话列表条目
     * （`ConversationList.kt:551-558`）只有「群」徽标，**没有**头像组。
     * 所以本测断言 [GroupMemberBar]：存在 + 点击语义节点数 == 角色数（3）。
     *
     * UIAvatar 自身无 contentDescription/testTag（第九轮 uiautomator dump 因此找不到
     * 任何头像节点），这里靠容器 testTag + 未合并语义树数 clickable 子节点，
     * 这正是「容器上打一个 tag 就够、不逐个头像打」的最小改动路线。
     */
    @Test
    fun groupMemberBarRendersOneAvatarPerRole() {
        openGroupChat(groupAId)
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodesWithTag(MEMBER_BAR_TAG).fetchSemanticsNodes().isNotEmpty()
        }

        val bar = compose.onNodeWithTag(MEMBER_BAR_TAG)
        bar.assertExists()

        val avatarNodes = compose
            .onAllNodes(hasAnyAncestor(hasTestTag(MEMBER_BAR_TAG)) and hasClickAction())
            .fetchSemanticsNodes()
        assertEquals(
            "成员头像组里的可点击头像数必须等于群角色数（夹具=3）",
            fixtureConfig.roles.size,
            avatarNodes.size,
        )
        bar.onChildren().assertCountEquals(fixtureConfig.roles.size)

        emit(
            "avatar",
            listOf(
                "case=member_bar",
                "member_bar_exists=true",
                "expected_roles=${fixtureConfig.roles.size}",
                "actual_clickable_avatars=${avatarNodes.size}",
                "role_names=${fixtureConfig.roles.joinToString("|") { it.name }}",
            ),
        )
    }

    // ==================================================================
    // 测 3：@ 选择器插入 —— 候选可见、点选后文本正确、生产解析命中
    // ==================================================================

    @Test
    fun atMentionPickerInsertsParseableMention() {
        openGroupChat(groupAId)
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodesWithTag(CHAT_INPUT_TAG).fetchSemanticsNodes().isNotEmpty()
        }

        // ---------- 场景 A：敲 @ ⇒ 选择器出现（候选角色名可见）⇒ 点选 ⇒ 真实文本可解析 ----------
        compose.onNodeWithTag(CHAT_INPUT_TAG).performClick()
        compose.onNodeWithTag(CHAT_INPUT_TAG).assert(isFocused())
        typeIntoChatInput("@")
        // 弹窗按分数排序：空 query 时按名单顺序，Alice Johnson（名单首位）排最前；
        // 真机实测 CompletionPopup 的 LazyColumn 只组合视口内的项，所以「等任一候选」
        // 必须等排第一的那个，不能等屏幕外的「阿尔法」。
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodes(hasText("Alice Johnson") and hasClickAction())
                .fetchSemanticsNodes().isNotEmpty()
        }
        emit(
            "mention",
            listOf(
                "case=at_picker_open",
                "chat_input_text=${chatInputText()}",
                "candidate_visible=Alice Johnson",
            ),
        )

        // 点选一个角色：输入框文本必须真的变成 "@Alice Johnson "
        compose.onNode(hasText("Alice Johnson") and hasClickAction()).performClick()
        compose.waitUntil(WAIT_MS) { chatInputText() == "@Alice Johnson " }
        val textMulti = chatInputText()
        assertEquals(
            "点选候选后输入框文本必须等于 @<角色名>+尾空格",
            "@Alice Johnson ",
            textMulti,
        )
        val parsedAlice = GroupChat.parseMentions(textMulti, fixtureConfig.roles)
        assertTrue(
            "UI 插入的 \"$textMulti\" 必须被 GroupChat.parseMentions 解析出 alice，实际=$parsedAlice",
            parsedAlice.contains("alice"),
        )
        assertEquals("命中角色数必须恰好 1", 1, parsedAlice.size)

        // ---------- 场景 B：中文角色 @阿 ⇒ 阿尔法，同样断言真实文本与解析 ----------
        compose.onNodeWithTag(CHAT_INPUT_TAG).performTextClearance()
        typeIntoChatInput("@阿")
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodes(hasText("阿尔法") and hasClickAction())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("阿尔法") and hasClickAction()).performClick()
        compose.waitUntil(WAIT_MS) { chatInputText() == "@阿尔法 " }
        val textAlpha = chatInputText()
        assertEquals("@阿尔法 ", textAlpha)
        val parsedAlpha = GroupChat.parseMentions(textAlpha, fixtureConfig.roles)
        assertTrue(
            "UI 插入的 \"$textAlpha\" 必须被 GroupChat.parseMentions 解析出 alpha，实际=$parsedAlpha",
            parsedAlpha.contains("alpha"),
        )
        assertEquals("命中角色数必须恰好 1", 1, parsedAlpha.size)

        // 反向边界：伪 @（片段）解析不出任何角色 —— 证明解析不是「见 @ 就给」。
        assertTrue(
            "控制组：@johnson 不应命中任何角色",
            GroupChat.parseMentions("@johnson ", fixtureConfig.roles).isEmpty(),
        )

        emit(
            "mention",
            listOf(
                "case=at_picker",
                "text_after_pick_role=$textMulti",
                "parsed_role_ids=$parsedAlice",
                "text_after_pick_chinese=$textAlpha",
                "parsed_role_ids_chinese=$parsedAlpha",
                "anti_spoof_at_johnson=${GroupChat.parseMentions("@johnson ", fixtureConfig.roles)}",
            ),
        )
    }

    // ==================================================================
    // 夹具：固定 id + 幂等 seed 主库（思路与 C1GroupUiE2EFixtureTest 相同）
    // ==================================================================

    private fun seedFixtures() = runBlocking {
        val settings = settingsStore.settingsFlow.first()
        // 列表按「当前助手」过滤（ChatDrawerViewModel.assistantIdFlow → settings.assistantId），
        // 所以会话必须挂当前助手，否则手机上根本看不到条目。
        val currentAssistantId = settings.assistantId
        // 角色引用的助手：真实存在的第一个助手；一个都没有时退回占位 id，
        // GroupMemberBar 解析不到就渲染程序生成头像，不让整排崩。
        val roleAssistantId = settings.assistants.firstOrNull()?.id ?: DEFAULT_ASSISTANT_ID
        fixtureConfig = GroupConfig(
            roles = listOf(
                GroupRole(id = "alice", name = "Alice Johnson", assistantId = roleAssistantId.toString()),
                GroupRole(id = "alpha", name = "阿尔法", assistantId = roleAssistantId.toString()),
                GroupRole(id = "gamma", name = "伽马", assistantId = roleAssistantId.toString(), chair = true),
            ),
            mode = GroupChat.MODE_PIPELINE,
            chairRoleId = "gamma",
            tokenBudgetPerRound = 200_000,
            revision = 1,
        )

        // 幂等：先删自己的四条再插（固定 id，重复跑不撞、不堆会话）。
        listOf(groupAId, groupBId, directAId, directBId).forEach { id ->
            repository.deleteConversation(
                Conversation(id = id, assistantId = currentAssistantId, title = "", messageNodes = emptyList()),
            )
        }

        val now = java.time.Instant.now()
        // updateAt 显式拉开，保证「全部」列表里四条顺序确定且相邻：
        // groupA > groupB > directA > directB（ORDER BY update_at DESC）。
        listOf(
            Triple(groupAId, GROUP_A, now),
            Triple(groupBId, GROUP_B, now.minusSeconds(1)),
            Triple(directAId, DIRECT_A, now.minusSeconds(2)),
            Triple(directBId, DIRECT_B, now.minusSeconds(3)),
        ).forEachIndexed { index, (id, title, updateAt) ->
            val isGroup = index < 2
            repository.insertConversation(
                Conversation(
                    id = id,
                    assistantId = currentAssistantId,
                    title = title,
                    messageNodes = listOf(
                        UIMessage(
                            id = Uuid.random(),
                            role = MessageRole.USER,
                            parts = listOf(UIMessagePart.Text("$title 夹具消息")),
                        ).toMessageNode(),
                    ),
                    updateAt = updateAt,
                    type = if (isGroup) GroupChat.TYPE_GROUP else GroupChat.TYPE_DIRECT,
                    groupConfig = if (isGroup) fixtureConfig else null,
                ),
            )
        }

        // 读回自检：seed 必须真的落到主库（UI 只读主库）。
        listOf(groupAId, groupBId, directAId, directBId).forEach { id ->
            assertNotNull("seed 后会话必须能从主库读回", repository.getConversationById(id))
        }
    }

    // ==================================================================
    // UI 宿主：直接启动 RouteActivity 到群聊页
    // ==================================================================

    private fun openGroupChat(conversationId: Uuid) {
        val intent = Intent(context, RouteActivity::class.java).apply {
            putExtra("conversationId", conversationId.toString())
        }
        scenario = ActivityScenario.launch<RouteActivity>(intent)
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodesWithTag(CHAT_INPUT_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 左缘向右滑，打开群聊页的 ModalNavigationDrawer；等到 chip 行可见。 */
    private fun openDrawer() {
        compose.onRoot().performTouchInput {
            swipe(
                start = Offset(1f, centerY),
                end = Offset(width - 1f, centerY),
                durationMillis = 250,
            )
        }
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodesWithText(CHIP_ALL).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ==================================================================
    // 断言工具
    // ==================================================================

    /**
     * 在 lazy 列表上滚动到 [text] 对应节点并断言其在**抽屉列表作用域内**存在。
     *
     * 用生产列表的 ScrollToIndex 语义，屏幕外的条目也能找到——这正是「过滤」能被
     * 真断言的前提：不可达 = 数据源里真的没有，而不是「没组合」。
     *
     * ⚠️ 必须限定 `hasAnyAncestor(drawer_conversation_list)`：群聊页顶栏标题也是
     * 同一个会话标题，全树 `onNodeWithText` 会命中多个节点（实测 3 个）。
     */
    private fun assertReachable(list: SemanticsNodeInteraction, text: String) {
        list.performScrollToNode(hasText(text))
        assertTrue(
            "抽屉列表里必须能滚动到条目「$text」",
            countInDrawer(text) > 0,
        )
    }

    /** 抽屉列表作用域内、文本恰为 [text] 的节点数。 */
    private fun countInDrawer(text: String): Int = compose
        .onAllNodes(hasAnyAncestor(hasTestTag(DRAWER_LIST_TAG)) and hasText(text))
        .fetchSemanticsNodes()
        .size

    /** 不抛异常的版本，用于 waitUntil 与「必须不可达」断言。 */
    private fun isReachableQuietly(list: SemanticsNodeInteraction, text: String): Boolean =
        runCatching { list.performScrollToNode(hasText(text)) }.isSuccess

    /** 从输入框语义节点读回真实文本（EditableText），不是测试自己记的状态。 */
    private fun chatInputText(): String =
        compose.onNodeWithTag(CHAT_INPUT_TAG).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    /**
     * 往 `chat_input` 输入 [text]，并把光标显式置于末尾。
     *
     * 为什么需要显式 SetSelection：真机实测 Compose 测试的 `performTextInput` 在
     * `TextFieldState` 上后置的 selection 不可靠，而 `GroupRoleCompletionProvider.findMention`
     * 要求 cursor 落在 `@` 之后（cursor <= 0 直接不触发）。真实键盘输入后光标必然停在
     * 刚输入的字符之后，所以这里补一次 `SetSelection(len, len)` 还原真实后置状态。
     * （本轮证据：test run instrument-round5-3 输入后 chat_input_text 已是 "@" 但
     * selection 未跟到末尾，候选因此不出现。）
     */
    private fun typeIntoChatInput(text: String) {
        val node = compose.onNodeWithTag(CHAT_INPUT_TAG)
        node.performTextInput(text)
        compose.waitForIdle()
        val current = chatInputText()
        node.performSemanticsAction(SemanticsActions.SetSelection) { setSelection ->
            // 第三个参数 = relativeToOriginalText（Compose 1.13 的 SetSelection 签名是
            // (start, end, relativeToOriginalText) -> Boolean；输入框无 VisualTransformation，
            // 传 false 即「相对当前文本」）。
            setSelection(current.length, current.length, false)
        }
        compose.waitForIdle()
    }

    /** 证据落盘：`am instrument -w` 不回传 stdout，写文件供 adb pull（追加，保留失败前线索）。 */
    private fun emit(case: String, lines: List<String>) {
        val dir = context.getExternalFilesDir(null) ?: return
        File(dir, "c1-round5-$case.txt").appendText(
            lines.joinToString("\n") + "\n----\n",
            Charsets.UTF_8,
        )
    }

    private companion object {
        const val WAIT_MS = 15_000L

        /** 整个 instrumentation 进程只清一次证据目录（见 @Before 注释）。 */
        val EVIDENCE_CLEARED = java.util.concurrent.atomic.AtomicBoolean(false)

        const val CHAT_INPUT_TAG = "chat_input"
        const val MEMBER_BAR_TAG = "group_member_bar"
        const val DRAWER_LIST_TAG = "drawer_conversation_list"

        const val CHIP_ALL = "全部"
        const val CHIP_DIRECT = "单聊"
        const val CHIP_GROUP = "群聊"

        const val GROUP_A = "C1-R5 群聊甲"
        const val GROUP_B = "C1-R5 群聊乙"
        const val DIRECT_A = "C1-R5 单聊甲"
        const val DIRECT_B = "C1-R5 单聊乙"
    }
}
