package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * [SummaryViewerScope] 的纯函数判定测试。
 *
 * 缺陷本体：`ChatManager.generateTitle` / `compressConversation` 直接把
 * `conversation.currentMessages` 送进 TITLE / SUMMARY 模型，群聊下其他角色的发言越权。
 * 修法是把「标题/摘要按谁的视角取」抽成纯函数（[SummaryViewerScope]），执行层只负责转调。
 * 这里钉的是**判定本身**；三个调用点有没有真的接上过滤由
 * `SummaryViewerScopeWiringSourceGuardTest` 在源码层钉。
 *
 * ⚠️ 「按 mode 选 viewer」这条口径是**产品判断，不是实证**（见 `SummaryViewerScope` 类
 * KDoc 的「⚠️ 待产品确认」一节）。因此本文件里每条 mode 用例的断言都写成
 * 「pipeline/vote 取名单第一位、roundtable 取议长」，产品改口径时**必须同时改这里** ——
 * 这正是把它做成真单测而不是文本护栏的价值：口径变了会红，红了就知道要重新拍板。
 */
class SummaryViewerScopeTest {

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private val alice = GroupRole(id = "alice", name = "爱丽丝", assistantId = "asst-1")
    private val bob = GroupRole(id = "bob", name = "鲍勃", assistantId = "asst-2")
    private val chair = GroupRole(id = "chair", name = "议长", assistantId = "asst-3", chair = true)

    private fun pipeline() = GroupConfig(
        roles = listOf(alice, bob),
        mode = GroupChat.MODE_PIPELINE,
        tokenBudgetPerRound = 1000,
    )

    private fun roundtable() = GroupConfig(
        roles = listOf(alice, bob, chair),
        mode = GroupChat.MODE_ROUNDTABLE,
        chairRoleId = chair.id,
        tokenBudgetPerRound = 1000,
    )

    private fun vote() = GroupConfig(
        roles = listOf(alice, bob),
        mode = GroupChat.MODE_VOTE,
        voteCandidates = listOf("alice", "bob"),
        tokenBudgetPerRound = 1000,
    )

    private fun user(text: String, mentions: List<String> = emptyList()) = UIMessage(
        role = MessageRole.USER,
        parts = listOf(UIMessagePart.Text(text)),
        mentionRoleIds = mentions,
        turnKind = GroupChat.TURN_USER,
    )

    private fun assistant(text: String, roleId: String) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(text)),
        roleId = roleId,
        roundId = "round-1",
        turnKind = GroupChat.TURN_SPEAKER,
    )

    private fun nodes(vararg messages: UIMessage): List<MessageNode> =
        messages.map { it.toMessageNode() }

    /**
     * `type` 刻意放在 `vararg` **之后**：这样调用点可以写
     * `conversation(config = null, user(..), assistant(..))`，命名实参之后的位置实参直接落到
     * vararg 上，而不会撞上 `type: String`。
     */
    private fun conversation(
        config: GroupConfig?,
        vararg messages: UIMessage,
        type: String = if (config == null) GroupChat.TYPE_DIRECT else GroupChat.TYPE_GROUP,
    ) = Conversation(
        assistantId = Uuid.random(),
        messageNodes = nodes(*messages),
        type = type,
        groupConfig = config,
    )

    // ------------------------------------------------------------------
    // 1. 非群聊：逐字不变，且返回同一个 List 实例
    // ------------------------------------------------------------------

    /**
     * 单聊路径必须**逐字不变**。
     *
     * 逐条比 id + 文本，而不是比引用：[Conversation.currentMessages] 是个 `get()`，每次访问
     * 都新建一个 List，所以「同一个实例」在这里根本不可断言（`assertSame` 必然失败）。
     * 真正要钉的是「内容与顺序一模一样」，那才是送进 TITLE 模型的东西 ——
     * `ChatManager.generateTitle` 里紧跟着的 `takeLast(4)` 与拼装逻辑一字未动，
     * 于是「可见集合逐条相同 ⇒ `takeLast(4)` 之后逐字相同」是可直接推出的。
     */
    @Test
    fun `a direct conversation is returned unchanged`() {
        val conversation = conversation(
            config = null,
            user("你好"),
            assistant("在的", alice.id),
        )

        val original = conversation.currentMessages
        val visible = SummaryViewerScope.messages(conversation)

        assertEquals(
            "非群聊必须原样返回 currentMessages 的全部条目（单聊路径逐字不变）",
            original.map { it.id },
            visible.map { it.id },
        )
        assertEquals(original.map { it.toText() }, visible.map { it.toText() })
        assertEquals(listOf("你好", "在的"), visible.map { it.toText() })
    }

    /**
     * `group_config` 非空但 `type` 不是 GROUP 的老数据，按 [isGroupConversation] 的严格口径
     * 判为单聊 ⇒ 逐字不变。这条防的是「有人只判 `groupConfig != null`」那种改法：那会让一批
     * 已被改回单聊的历史群会话突然开始走过滤，标题/摘要凭空少一半内容。
     */
    @Test
    fun `a leftover group config on a direct conversation still bypasses filtering`() {
        val conversation = conversation(
            config = pipeline(),
            user("议题"),
            assistant("alice 发言", alice.id),
            assistant("bob 发言", bob.id),
            type = GroupChat.TYPE_DIRECT,
        )

        assertEquals(
            "type != GROUP 时必须按单聊放行（与 isGroupConversation 同一口径）：alice 与 bob 的" +
                "发言都在",
            conversation.currentMessages.map { it.id },
            SummaryViewerScope.messages(conversation).map { it.id },
        )
    }

    // ------------------------------------------------------------------
    // 2. 群聊：三个 mode 各自的 viewer
    // ------------------------------------------------------------------

    @Test
    fun `pipeline mode views the conversation as the first listed role`() {
        val step = SummaryViewerScope.step(pipeline())
        assertEquals("pipeline 取名单第一位", alice.id, step?.role?.id)
        assertEquals("pipeline 不是议长汇总，不放开本轮他人输出", false, step?.chairRound)
    }

    @Test
    fun `vote mode views the conversation as the first listed role`() {
        val step = SummaryViewerScope.step(vote())
        assertEquals("vote 取名单第一位", alice.id, step?.role?.id)
        assertEquals(false, step?.chairRound)
    }

    /**
     * roundtable 取议长，且 `chairRound = true` —— 与 `GroupChat.plan` 给议长那一步的取值一致。
     *
     * `chairRound = true` 是这里唯一让摘要/标题**看得见本轮其他角色发言**的分支，而议长在
     * 契约里本就被允许看全量（`GroupChat.plan` 的 roundtable 分支给他 `chairRound = true`）。
     * 钉住它是为了防止有人把议长也降成普通成员视角（那会让群聊标题质量下降，且与轮次内核漂移）。
     */
    @Test
    fun `roundtable mode views the conversation as the chair with the full round`() {
        val step = SummaryViewerScope.step(roundtable())
        assertEquals("roundtable 取议长", chair.id, step?.role?.id)
        assertEquals("议长必须带 chairRound = true（与 GroupChat.plan 同源）", true, step?.chairRound)
    }

    // ------------------------------------------------------------------
    // 3. 群聊过滤真的挡住了别人的发言
    // ------------------------------------------------------------------

    /**
     * 反向证据（本题的核心）：群聊下 bob 的发言**不在** alice 视角的可见集合里。
     *
     * 这条是「修好了」的直接证据 —— 修复前 `generateTitle` 送的就是全量
     * `currentMessages`，bob 那条必然在里面。
     */
    @Test
    fun `other roles messages are invisible to the chosen viewer`() {
        val conversation = conversation(
            config = pipeline(),
            user("议题"),
            assistant("alice 发言", alice.id),
            assistant("bob 发言", bob.id),
        )

        val visible = SummaryViewerScope.messages(conversation)

        assertEquals(
            "alice 视角只该看到 [议题, alice 发言]",
            listOf("议题", "alice 发言"),
            visible.map { it.toText() },
        )
        assertFalse(
            "bob 的发言绝不能出现在发给 TITLE 模型的上下文里",
            visible.any { it.toText() == "bob 发言" },
        )
    }

    @Test
    fun `the chair of a roundtable sees every role output of the current round`() {
        val conversation = conversation(
            config = roundtable(),
            user("议题"),
            assistant("alice 发言", alice.id),
            assistant("bob 发言", bob.id),
            assistant("议长总结", chair.id),
        )

        val visible = SummaryViewerScope.messages(conversation)

        assertEquals(
            listOf("议题", "alice 发言", "bob 发言", "议长总结"),
            visible.map { it.toText() },
        )
    }

    /**
     * @ 提及仍然放行：过滤只挡「别人的自发发言」，用户明确 @ 到的消息对被 @ 者可见。
     * 复用 [GroupTurnCoordinator.viewerMessages] 的直接后果，不在这里另立口径。
     */
    @Test
    fun `a mentioned message stays visible to the mentioned role`() {
        val conversation = conversation(
            config = pipeline(),
            user("请 alice 回答", mentions = listOf(alice.id)),
            assistant("alice 发言", alice.id),
            assistant("bob 发言", bob.id),
        )

        assertEquals(
            listOf("请 alice 回答", "alice 发言"),
            SummaryViewerScope.messages(conversation).map { it.toText() },
        )
    }

    /**
     * 过滤幂等：把已过滤结果再过一次 [SummaryViewerScope]，与 [GroupTurnCoordinatorTest]
     * 里「重复过滤不改变结果」那条用例同源。标题生成不重复过滤，但幂等意味着将来谁在
     * 上游先滤一遍也不会引入差异。
     */
    @Test
    fun `filtering an already filtered set is a no-op`() {
        val config = pipeline()
        val conversation = conversation(
            config = config,
            user("议题"),
            assistant("alice 发言", alice.id),
            assistant("bob 发言", bob.id),
        )
        val once = SummaryViewerScope.messages(conversation)

        // 用同一个 step 再过一次 viewerMessages（与 SummaryViewerScope.messages 内部路径一致）。
        val step = SummaryViewerScope.step(config)!!
        val twice = GroupTurnCoordinator.viewerMessages(config, once, step)

        assertEquals(once.map { it.id }, twice.map { it.id })
        assertEquals(once.map { it.toText() }, twice.map { it.toText() })
    }

    // ------------------------------------------------------------------
    // 4. 退化口径：fail-closed，绝不回退成「不过滤」
    // ------------------------------------------------------------------

    /**
     * 群聊但 `roles` 为空 ⇒ 定不出 viewer ⇒ 返回**空列表**，不是全量。
     *
     * 这是本修复最重要的一条 fail-closed 断言：退化时若回退成「不过滤」，就等于把原来的
     * 漏洞原样留着，而且只在配置坏了的历史数据上发作（更难发现）。
     */
    @Test
    fun `a group conversation with no roles degrades to an empty scope, never to everything`() {
        val emptyRoles = GroupConfig(
            roles = emptyList(),
            mode = GroupChat.MODE_PIPELINE,
            tokenBudgetPerRound = 1000,
        )
        val conversation = conversation(
            config = emptyRoles,
            user("议题"),
            assistant("alice 发言", alice.id),
        )

        assertNull("roles 为空时定不出 viewer", SummaryViewerScope.step(emptyRoles))
        assertTrue(
            "退化必须是空集合，绝不能是全量",
            SummaryViewerScope.messages(conversation).isEmpty(),
        )
    }

    /**
     * 议长 id 不在名单里（配置残缺）⇒ 退回名单第一位，**不是**空集合。
     *
     * 与上一条刻意不同：这里名单本身是好的（有人可看），只是议长字段坏了。退回第一位是
     * 「降级但仍然安全」—— 过滤照旧生效，不会退化成全量泄漏。
     */
    @Test
    fun `a roundtable with a dangling chair id falls back to the first role`() {
        val broken = roundtable().copy(chairRoleId = "not-a-member")
        val step = SummaryViewerScope.step(broken)

        assertEquals("议长缺失时退回名单第一位", alice.id, step?.role?.id)
        assertEquals(
            "议长没定位到就不能开 chairRound（否则会按 GroupChat.visibleMessages 的" +
                "chairRound 分支放开本轮全部 ASSISTANT，而 viewer 是 alice —— 越权）",
            false,
            step?.chairRound,
        )
    }

    /**
     * `config == null` 时 [SummaryViewerScope.step] 返回 null，但
     * [SummaryViewerScope.messages] 走的是 `isGroupConversation == false` 那条早退，
     * **原样返回全量**。两条路径的差别必须钉住：step 的 null 语义是「定不出 viewer」，
     * messages 的 null-config 语义是「不是群聊」，混起来会有人误写成 fail-closed 空列表。
     */
    @Test
    fun `a null config means not a group chat, so messages stays unfiltered`() {
        assertNull(SummaryViewerScope.step(null))
        val conversation = conversation(
            config = null,
            user("你好"),
            assistant("在的", alice.id),
        )
        assertEquals(2, SummaryViewerScope.messages(conversation).size)
    }
}