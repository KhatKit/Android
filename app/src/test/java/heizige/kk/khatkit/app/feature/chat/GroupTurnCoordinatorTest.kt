package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.core.TokenUsage
import heizige.kk.khatkit.ai.core.Tool
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.db.entity.GroupRunEntity
import heizige.kk.khatkit.app.core.data.model.AssistantMemory
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoundBudget
import heizige.kk.khatkit.app.core.data.model.SpeakerStep
import heizige.kk.khatkit.app.core.data.model.VoteBallot
import heizige.kk.khatkit.app.core.data.model.VoteOutcome
import heizige.kk.khatkit.app.core.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * [GroupTurnCoordinator] 的判定内核测试。
 *
 * 这个内核承载契约里最硬的几条：run token 持久化幂等、续跑跳过已提交、prompt+completion
 * 预算口径、失败/取消/超时语义、投票与平票裁决、三角色视角隔离。执行层的边界（重复触发、
 * 预算恰好在第二位越界、进程被杀后重启）在真机上很难复现，所以在这里用纯 JVM 用例钉死。
 *
 * 用量一律构造 `TokenUsage.totalTokens` 与 `promptTokens + completionTokens` **不相等**的形状
 * （真实 provider 会回 cachedTokens，totalTokens 也可能是服务端口径），用来证明预算走的是
 * prompt+completion 而不是 `totalTokens`。
 */
class GroupTurnCoordinatorTest {

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private val alice = GroupRole(id = "alice", name = "爱丽丝", assistantId = "asst-1")
    private val bob = GroupRole(id = "bob", name = "鲍勃", assistantId = "asst-2")
    private val carol = GroupRole(id = "carol", name = "卡罗尔", assistantId = "asst-3")
    private val chair = GroupRole(id = "chair", name = "议长", assistantId = "asst-4", chair = true)

    private fun pipelineConfig(
        budget: Int = 1000,
        roles: List<GroupRole> = listOf(alice, bob, carol),
    ) = GroupConfig(
        roles = roles,
        mode = GroupChat.MODE_PIPELINE,
        tokenBudgetPerRound = budget,
    )

    private fun roundtableConfig(budget: Int = 1000) = GroupConfig(
        roles = listOf(alice, bob, carol, chair),
        mode = GroupChat.MODE_ROUNDTABLE,
        chairRoleId = chair.id,
        tokenBudgetPerRound = budget,
    )

    private fun voteConfig(
        tiePolicy: String = GroupChat.TIE_FAIL,
        candidates: List<String> = listOf("opt-a", "opt-b"),
        roles: List<GroupRole> = listOf(alice, bob, carol),
    ) = GroupConfig(
        roles = roles,
        mode = GroupChat.MODE_VOTE,
        tokenBudgetPerRound = 1000,
        voteCandidates = candidates,
        tiePolicy = tiePolicy,
    )

    private fun planOf(config: GroupConfig, trigger: String = "trigger-1") =
        GroupChat.newRound(triggerMessageId = trigger, config = config, mentionRoleIds = emptyList())

    private fun user(text: String, mentions: List<String> = emptyList()) = UIMessage(
        role = MessageRole.USER,
        parts = listOf(UIMessagePart.Text(text)),
        mentionRoleIds = mentions,
        turnKind = GroupChat.TURN_USER,
    )

    private fun assistant(
        text: String,
        roleId: String?,
        roundId: String? = "round-trigger-1",
        turnKind: String? = GroupChat.TURN_SPEAKER,
        usage: TokenUsage? = null,
    ) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(text)),
        roleId = roleId,
        roundId = roundId,
        turnKind = turnKind,
        usage = usage,
    )

    /**
     * 带 `mentionRoleIds` 的助手消息。[assistant] 刻意**没有**这个参数，所以本文件里
     * 「非 USER 消息被 @ 到」这条放行分支只能由本构造器触发——上面
     * `mentions are delivered only to the mentioned role` 造的 @ 挂在 **USER** 消息上，
     * 而 USER 消息本来就无条件放行，那条用例其实没碰到 mention 分支。
     */
    private fun speaking(
        text: String,
        roleId: String,
        mentionRoleIds: List<String> = emptyList(),
        roundId: String? = "round-trigger-1",
        turnKind: String? = GroupChat.TURN_SPEAKER,
    ) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(text)),
        roleId = roleId,
        mentionRoleIds = mentionRoleIds,
        roundId = roundId,
        turnKind = turnKind,
    )

    private fun stepOf(config: GroupConfig, roleId: String): SpeakerStep =
        GroupChat.plan(config, emptyList()).first { it.role.id == roleId }

    private fun state(
        conversationId: String = "conv-1",
        roundId: String = "round-trigger-1",
        runToken: String = "token-1",
        status: String = GroupRunEntity.STATUS_RUNNING,
        spent: Int = 0,
        limit: Int = 1000,
        committed: List<String> = emptyList(),
        skipped: List<String> = emptyList(),
        reason: String = "",
        error: String = "",
        startedAt: Long = 1_000L,
        updatedAt: Long = 1_000L,
        endedAt: Long? = null,
    ) = GroupTurnCoordinator.RoundState(
        conversationId = conversationId,
        roundId = roundId,
        runToken = runToken,
        status = status,
        spentTokens = spent,
        tokenLimit = limit,
        committedRoleIds = committed,
        skippedRoleIds = skipped,
        reason = reason,
        errorMessage = error,
        startedAt = startedAt,
        updatedAt = updatedAt,
        endedAt = endedAt,
    )

    /** 故意让 totalTokens 与 prompt+completion 不等，模拟 cachedTokens / 服务端口径差异。 */
    private fun skewedUsage(prompt: Int, completion: Int, total: Int) = TokenUsage(
        promptTokens = prompt,
        completionTokens = completion,
        cachedTokens = 7,
        totalTokens = total,
    )

    private val model = Model(modelId = "test-model")

    // ------------------------------------------------------------------
    // 1. claimRound：持久化 run token 幂等
    // ------------------------------------------------------------------

    @Test
    fun `fresh round acquires and its run token is part of the persisted row`() {
        val config = pipelineConfig()
        val plan = planOf(config)

        val claim = GroupTurnCoordinator.claimRound(
            conversationId = "conv-1",
            plan = plan,
            tokenLimit = 1000,
            existing = null,
            expectedRunToken = null,
            activeRunToken = null,
            newRunToken = "token-A",
            now = 5_000L,
        )

        assertTrue(claim is GroupTurnCoordinator.Claim.Acquired)
        claim as GroupTurnCoordinator.Claim.Acquired
        assertTrue("新轮次必须走 insert（主键 ABORT 才挡得住并发抢占）", claim.freshRow)
        assertEquals("token-A", claim.state.runToken)
        assertEquals(GroupRunEntity.STATUS_RUNNING, claim.state.status)
        assertEquals(0, claim.state.spentTokens)
        assertEquals(plan.roundId, claim.state.roundId)
        assertNull(claim.state.endedAt)

        // 落库实体与快照一一对应，否则「先落库再执行」拿不到 run token。
        val entity = GroupTurnCoordinator.toEntity(claim.state)
        assertEquals("token-A", entity.runToken)
        assertEquals(plan.roundId, entity.roundId)
        assertEquals(claim.state, GroupTurnCoordinator.fromEntity(entity))
    }

    @Test
    fun `second claim of the same round id is rejected while it is running`() {
        val config = pipelineConfig()

        val claim = GroupTurnCoordinator.claimRound(
            conversationId = "conv-1",
            plan = planOf(config),
            tokenLimit = 1000,
            existing = state(status = GroupRunEntity.STATUS_RUNNING),
            // 没有透传本进程令牌 = 重复触发（另一个入口/另一个进程），不得伪装成续跑。
            expectedRunToken = null,
            activeRunToken = "token-1",
            newRunToken = "token-B",
            now = 6_000L,
        )

        assertTrue(claim is GroupTurnCoordinator.Claim.Rejected)
        claim as GroupTurnCoordinator.Claim.Rejected
        assertEquals(GroupTurnCoordinator.RejectCode.ALREADY_RUNNING, claim.code)
        assertEquals("拒绝时不得换成新生成的令牌", "token-1", claim.state?.runToken)
    }

    @Test
    fun `a completed round stays rejected after process restart`() {
        val config = pipelineConfig()
        val completed = state(
            status = GroupRunEntity.STATUS_COMPLETED,
            committed = listOf("alice", "bob", "carol"),
            endedAt = 9_000L,
        )

        val claim = GroupTurnCoordinator.claimRound(
            conversationId = "conv-1",
            plan = planOf(config),
            tokenLimit = 1000,
            existing = completed,
            expectedRunToken = null,
            activeRunToken = null,
            newRunToken = "token-C",
            now = 20_000L,
        )

        assertTrue(claim is GroupTurnCoordinator.Claim.Rejected)
        claim as GroupTurnCoordinator.Claim.Rejected
        assertEquals(GroupTurnCoordinator.RejectCode.ALREADY_COMPLETED, claim.code)
        // 跨进程/重启后仍能判定「这轮已跑过」，靠的就是这一行库里的终态。
        assertEquals(listOf("alice", "bob", "carol"), claim.state?.committedRoleIds)
    }

    @Test
    fun `the same active run continues with an unchanged run token`() {
        val config = pipelineConfig()
        val running = state(status = GroupRunEntity.STATUS_RUNNING, committed = listOf("alice"))

        val claim = GroupTurnCoordinator.claimRound(
            conversationId = "conv-1",
            plan = planOf(config),
            tokenLimit = 1000,
            existing = running,
            expectedRunToken = "token-1",
            activeRunToken = "token-1",
            newRunToken = "token-D",
            now = 7_000L,
        )

        assertTrue(claim is GroupTurnCoordinator.Claim.Continued)
        claim as GroupTurnCoordinator.Claim.Continued
        assertEquals("token-1", claim.state.runToken)
        assertEquals(listOf("alice"), claim.state.committedRoleIds)
        assertEquals(7_000L, claim.state.updatedAt)
    }

    @Test
    fun `a stale running row left by a killed process is reclaimed and resumed`() {
        val config = pipelineConfig()
        // 库里非终态，但本进程没有活跃实例 = 上一个进程被强杀留下的僵尸。
        val zombie = state(status = GroupRunEntity.STATUS_RUNNING, committed = listOf("alice"))

        val claim = GroupTurnCoordinator.claimRound(
            conversationId = "conv-1",
            plan = planOf(config),
            tokenLimit = 1000,
            existing = zombie,
            expectedRunToken = null,
            activeRunToken = null,
            newRunToken = "token-E",
            now = 30_000L,
        )

        assertTrue(claim is GroupTurnCoordinator.Claim.Acquired)
        claim as GroupTurnCoordinator.Claim.Acquired
        assertFalse("僵尸回收走 UPDATE 而非 insert", claim.freshRow)
        assertEquals(GroupRunEntity.STATUS_RUNNING, claim.state.status)
        // 刻意沿用库里那一轮的令牌：它是这一轮的执行血缘标识，被覆盖会让日志指向错误执行。
        assertEquals("token-1", claim.state.runToken)
        // 已提交集合必须保留，否则续跑会让 alice 重复发言。
        assertEquals(listOf("alice"), claim.state.committedRoleIds)
    }

    @Test
    fun `an empty plan is rejected as no speaker but still leaves a run log row`() {
        val config = pipelineConfig(roles = emptyList())

        val claim = GroupTurnCoordinator.claimRound(
            conversationId = "conv-1",
            plan = planOf(config),
            tokenLimit = 1000,
            existing = null,
            expectedRunToken = null,
            activeRunToken = null,
            newRunToken = "token-F",
            now = 8_000L,
        )

        assertTrue(claim is GroupTurnCoordinator.Claim.Rejected)
        claim as GroupTurnCoordinator.Claim.Rejected
        assertEquals(GroupTurnCoordinator.RejectCode.NO_SPEAKER, claim.code)
        assertEquals(GroupRunEntity.STATUS_FAILED, claim.state?.status)
        assertEquals(GroupRunEntity.REASON_NO_SPEAKER, claim.state?.reason)
        assertEquals(8_000L, claim.state?.endedAt)
    }

    @Test
    fun `mentions that filter out everyone are rejected as no speaker`() {
        val config = pipelineConfig()
        val plan = GroupChat.newRound(
            triggerMessageId = "trigger-1",
            config = config,
            mentionRoleIds = listOf("ghost"),
        )
        assertTrue(plan.plan.isEmpty())

        val claim = GroupTurnCoordinator.claimRound(
            conversationId = "conv-1",
            plan = plan,
            tokenLimit = 1000,
            existing = null,
            expectedRunToken = null,
            activeRunToken = null,
            newRunToken = "token-G",
            now = 8_500L,
        )

        claim as GroupTurnCoordinator.Claim.Rejected
        assertEquals(GroupTurnCoordinator.RejectCode.NO_SPEAKER, claim.code)
    }

    // ------------------------------------------------------------------
    // 2. 续跑跳过已提交 turn
    // ------------------------------------------------------------------

    @Test
    fun `retry skips committed roles and lands on the next pending one`() {
        val plan = planOf(pipelineConfig())

        assertEquals("alice", GroupTurnCoordinator.nextStep(plan, emptyList())?.role?.id)
        assertEquals("bob", GroupTurnCoordinator.nextStep(plan, listOf("alice"))?.role?.id)
        assertEquals("carol", GroupTurnCoordinator.nextStep(plan, listOf("alice", "bob"))?.role?.id)
        assertNull("全员已提交就没有下一步", GroupTurnCoordinator.nextStep(plan, listOf("alice", "bob", "carol")))
    }

    @Test
    fun `pipeline steps carry only the immediate predecessor output`() {
        val steps = planOf(pipelineConfig()).plan

        assertNull("第一位没有上一位输出", steps[0].predecessorId)
        assertEquals("alice", steps[1].predecessorId)
        assertEquals("bob", steps[2].predecessorId)
        steps.forEach { assertFalse("非议长轮不得带 chairRound", it.chairRound) }
    }

    @Test
    fun `a failed role is not committed so the round resumes from it`() {
        val config = pipelineConfig()
        val plan = planOf(config)

        val failed = GroupTurnCoordinator.fail(
            state = state(committed = listOf("alice")),
            failedRoleId = "bob",
            remainingRoleIds = listOf("carol"),
            errorMessage = "IllegalStateException: boom",
            now = 11_000L,
        )

        assertEquals(GroupRunEntity.STATUS_FAILED, failed.status)
        assertEquals(GroupRunEntity.REASON_ROLE_FAILED, failed.reason)
        assertEquals("IllegalStateException: boom", failed.errorMessage)
        assertEquals(listOf("carol"), failed.skippedRoleIds)
        assertEquals(11_000L, failed.endedAt)
        assertTrue(GroupRunEntity.isTerminal(failed.status))
        // 失败角色不进 committed → 续跑仍轮到它；已完成角色保留 → 输出不丢。
        assertEquals(listOf("alice"), failed.committedRoleIds)
        assertEquals("bob", GroupTurnCoordinator.nextStep(plan, failed.committedRoleIds)?.role?.id)
    }

    @Test
    fun `retrying a failed round reclaims it and keeps committed outputs`() {
        val config = pipelineConfig()
        val plan = planOf(config)
        val failed = state(
            status = GroupRunEntity.STATUS_FAILED,
            committed = listOf("alice"),
            skipped = listOf("carol"),
            reason = GroupRunEntity.REASON_ROLE_FAILED,
            error = "boom",
            endedAt = 11_000L,
        )

        val claim = GroupTurnCoordinator.claimRound(
            conversationId = "conv-1",
            plan = plan,
            tokenLimit = 1000,
            existing = failed,
            expectedRunToken = null,
            activeRunToken = null,
            newRunToken = "token-H",
            now = 40_000L,
        )

        assertTrue(claim is GroupTurnCoordinator.Claim.Acquired)
        claim as GroupTurnCoordinator.Claim.Acquired
        assertEquals(GroupRunEntity.STATUS_RUNNING, claim.state.status)
        assertEquals("", claim.state.reason)
        assertEquals("", claim.state.errorMessage)
        assertNull(claim.state.endedAt)
        // skipped 是「上一段没跑」，新一段要重跑它们，所以必须清空。
        assertEquals(emptyList<String>(), claim.state.skippedRoleIds)
        assertEquals("bob", GroupTurnCoordinator.nextStep(plan, claim.state.committedRoleIds)?.role?.id)
    }

    @Test
    fun `the error node records the failure without inventing assistant prose`() {
        val node = GroupTurnCoordinator.errorNode(
            state = state(committed = listOf("alice")),
            config = pipelineConfig(),
            failedRoleId = "bob",
            detail = "IllegalStateException: boom",
        )

        assertEquals(MessageRole.ASSISTANT, node.role)
        assertEquals("bob", node.roleId)
        assertEquals("round-trigger-1", node.roundId)
        assertEquals(GroupChat.TURN_ERROR, node.turnKind)

        val text = node.toText()
        assertTrue("错误节点要带角色显示名而不是裸 id", text.contains("鲍勃"))
        assertTrue(text.contains("boom"))
        assertEquals("只记错误，不伪造回复正文", 1, node.parts.size)
        assertTrue(node.parts[0] is UIMessagePart.Text)
        // 失败节点不是发言，不该带选票前缀。
        assertFalse(text.contains(GroupChat.BALLOT_PREFIX))
    }

    // ------------------------------------------------------------------
    // 3. 预算按 prompt + completion 累计
    // ------------------------------------------------------------------

    @Test
    fun `usageOf reads prompt and completion rather than totalTokens`() {
        val message = assistant("done", "alice", usage = skewedUsage(prompt = 120, completion = 80, total = 9999))

        assertEquals(120 to 80, GroupTurnCoordinator.usageOf(message))

        // 非助手消息不参与预算；没有用量的助手消息同理。
        assertNull(GroupTurnCoordinator.usageOf(user("hi")))
        assertNull(GroupTurnCoordinator.usageOf(assistant("done", "alice")))
        assertNull(GroupTurnCoordinator.usageOf(null))
    }

    @Test
    fun `budget accumulates prompt plus completion across roles`() {
        val config = pipelineConfig(budget = 1000)
        val plan = planOf(config)

        // alice：100 + 50 = 150（不是 totalTokens）。
        val afterAlice = GroupTurnCoordinator.advance(
            state = state(limit = 1000),
            finishedRoleId = "alice",
            usage = 100 to 50,
            plan = plan,
            tokenLimit = 1000,
            now = 12_000L,
        )
        assertTrue(afterAlice is GroupTurnCoordinator.Advance.More)
        afterAlice as GroupTurnCoordinator.Advance.More
        assertEquals("必须按 prompt+completion 累计", 150, afterAlice.state.spentTokens)
        assertEquals(listOf("alice"), afterAlice.state.committedRoleIds)
        assertEquals("bob", afterAlice.next.role.id)

        // bob：200 + 100 = 300，继续累加本轮。
        val afterBob = GroupTurnCoordinator.advance(
            state = afterAlice.state,
            finishedRoleId = "bob",
            usage = 200 to 100,
            plan = plan,
            tokenLimit = 1000,
            now = 13_000L,
        )
        assertTrue(afterBob is GroupTurnCoordinator.Advance.More)
        afterBob as GroupTurnCoordinator.Advance.More
        assertEquals(450, afterBob.state.spentTokens)
        assertEquals(listOf("alice", "bob"), afterBob.state.committedRoleIds)
        assertEquals("carol", afterBob.next.role.id)
    }

    @Test
    fun `a skewed totalTokens value cannot push the round over budget`() {
        val config = pipelineConfig(budget = 1000)
        val plan = planOf(config)
        val message = assistant("done", "alice", usage = skewedUsage(prompt = 100, completion = 50, total = 900_000))

        val advance = GroupTurnCoordinator.advance(
            state = state(limit = 1000),
            finishedRoleId = "alice",
            usage = GroupTurnCoordinator.usageOf(message)!!,
            plan = plan,
            tokenLimit = 1000,
            now = 12_500L,
        )

        assertTrue("totalTokens 造假不该触发预算停", advance is GroupTurnCoordinator.Advance.More)
        assertEquals(150, (advance as GroupTurnCoordinator.Advance.More).state.spentTokens)
    }

    @Test
    fun `budget stopped writes all four fields into the run log`() {
        val config = pipelineConfig(budget = 400)
        val plan = planOf(config)

        val first = GroupTurnCoordinator.advance(
            state = state(limit = 400),
            finishedRoleId = "alice",
            usage = 100 to 50,
            plan = plan,
            tokenLimit = 400,
            now = 14_000L,
        )
        assertTrue(first is GroupTurnCoordinator.Advance.More)
        first as GroupTurnCoordinator.Advance.More

        // bob 把累计推到 450 ≥ 400，carol 必须停跑。
        val second = GroupTurnCoordinator.advance(
            state = first.state,
            finishedRoleId = "bob",
            usage = 200 to 100,
            plan = plan,
            tokenLimit = 400,
            now = 15_000L,
        )

        assertTrue(second is GroupTurnCoordinator.Advance.BudgetStopped)
        second as GroupTurnCoordinator.Advance.BudgetStopped

        // 四项：已用 / 上限 / 未运行角色 / 原因。
        val stop = second.stop
        assertEquals(450, stop.spent)
        assertEquals(400, stop.limit)
        assertEquals(listOf("carol"), stop.skippedRoleIds)
        assertEquals(GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED, stop.reason)

        // 四项同时落进 RoundState，执行层据此写 group_runs。
        assertEquals(450, second.state.spentTokens)
        assertEquals(400, second.state.tokenLimit)
        assertEquals(listOf("carol"), second.state.skippedRoleIds)
        assertEquals(GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED, second.state.reason)
        assertEquals(GroupRunEntity.STATUS_BUDGET_STOPPED, second.state.status)
        assertEquals(15_000L, second.state.endedAt)
        assertTrue(GroupRunEntity.isTerminal(second.state.status))
        // 已产出保留：alice / bob 仍在 committed 里。
        assertEquals(listOf("alice", "bob"), second.state.committedRoleIds)
    }

    @Test
    fun `the budget stop reason literal matches the GroupRunEntity constant`() {
        // GroupChat.budgetDecision 自带字面量（纯函数文件不依赖 Room 实体），
        // 这里钉住取值一致，防止两边漂移导致运行日志 UI 过滤不到。
        val stop = GroupChat.budgetDecision(spent = 10, limit = 10, remainingRoleIds = listOf("carol"))
        assertTrue(stop is RoundBudget.Stop)
        stop as RoundBudget.Stop
        assertEquals(GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED, stop.reason)
        assertEquals(GroupRunEntity.REASON_BUDGET_EXCEEDED, stop.reason)

        // 未到上限继续跑。
        assertEquals(
            RoundBudget.Continue,
            GroupChat.budgetDecision(spent = 9, limit = 10, remainingRoleIds = listOf("carol")),
        )
        // 没有剩余角色时不再判停，否则最后一人永远提交不了。
        assertEquals(
            RoundBudget.Continue,
            GroupChat.budgetDecision(spent = 99, limit = 10, remainingRoleIds = emptyList()),
        )
    }

    @Test
    fun `the next round resets spent to zero and never borrows the previous budget`() {
        val config = pipelineConfig(budget = 400)
        val stopped = state(
            status = GroupRunEntity.STATUS_BUDGET_STOPPED,
            spent = 450,
            limit = 400,
            committed = listOf("alice", "bob"),
            skipped = listOf("carol"),
            reason = GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED,
            endedAt = 15_000L,
        )

        // 新一轮 = 新 round_id → existing 为 null → spent 归零。
        val nextPlan = planOf(config, trigger = "trigger-2")
        assertNotEquals("round-trigger-1", nextPlan.roundId)

        val claim = GroupTurnCoordinator.claimRound(
            conversationId = "conv-1",
            plan = nextPlan,
            tokenLimit = 400,
            existing = null,
            expectedRunToken = null,
            activeRunToken = null,
            newRunToken = "token-next",
            now = 50_000L,
        )
        claim as GroupTurnCoordinator.Claim.Acquired
        assertEquals("下一轮 spent 必须归零", 0, claim.state.spentTokens)
        assertEquals(emptyList<String>(), claim.state.committedRoleIds)
        assertEquals(emptyList<String>(), claim.state.skippedRoleIds)
        assertEquals("alice", GroupTurnCoordinator.nextStep(nextPlan, claim.state.committedRoleIds)?.role?.id)

        // 上一轮的行仍在库里（运行日志按轮独立，不做跨轮求和）。
        assertEquals(450, stopped.spentTokens)
        assertEquals(400, stopped.tokenLimit)
    }

    @Test
    fun `raising the budget lets a budget stopped round resume`() {
        val config = pipelineConfig(budget = 10_000)
        val stopped = state(
            status = GroupRunEntity.STATUS_BUDGET_STOPPED,
            spent = 450,
            limit = 400,
            committed = listOf("alice", "bob"),
            skipped = listOf("carol"),
            reason = GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED,
            endedAt = 15_000L,
        )

        val claim = GroupTurnCoordinator.claimRound(
            conversationId = "conv-1",
            plan = planOf(config),
            tokenLimit = 10_000,
            existing = stopped,
            expectedRunToken = null,
            activeRunToken = null,
            newRunToken = "token-raise",
            now = 60_000L,
        )
        claim as GroupTurnCoordinator.Claim.Acquired
        assertEquals(GroupRunEntity.STATUS_RUNNING, claim.state.status)
        // 上限快照跟新配置走；spent 仍是本轮已用（续跑不是新轮，不能归零）。
        assertEquals(10_000, claim.state.tokenLimit)
        assertEquals(450, claim.state.spentTokens)
        assertEquals(emptyList<String>(), claim.state.skippedRoleIds)
        assertEquals("carol", GroupTurnCoordinator.nextStep(planOf(config), claim.state.committedRoleIds)?.role?.id)
        assertEquals(
            RoundBudget.Continue,
            GroupChat.budgetDecision(
                spent = claim.state.spentTokens,
                limit = claim.state.tokenLimit,
                remainingRoleIds = listOf("carol"),
            ),
        )
    }

    @Test
    fun `finishing the last role does not trip the budget check`() {
        val config = pipelineConfig(budget = 10)
        val advance = GroupTurnCoordinator.advance(
            state = state(limit = 10, committed = listOf("alice", "bob")),
            finishedRoleId = "carol",
            usage = 100 to 100,
            plan = planOf(config),
            tokenLimit = 10,
            now = 16_000L,
        )

        assertTrue("全员已发言时不该报预算停", advance is GroupTurnCoordinator.Advance.Finished)
        advance as GroupTurnCoordinator.Advance.Finished
        assertEquals(listOf("alice", "bob", "carol"), advance.state.committedRoleIds)
        assertEquals(200, advance.state.spentTokens)
    }

    @Test
    fun `per role usage is summed until the round hits the ceiling`() {
        val config = pipelineConfig(budget = 1000)
        val plan = planOf(config)
        // 每个角色真实产生 300 + 200 = 500，totalTokens 造假到 100000。
        val usage = GroupTurnCoordinator.usageOf(
            assistant("产出", "alice", usage = skewedUsage(prompt = 300, completion = 200, total = 100_000)),
        )!!
        assertNotEquals(100_000, GroupChat.roundTotalTokens(listOf(usage)))

        var current = state(limit = 1000)
        var stop: RoundBudget.Stop? = null
        for (roleId in listOf("alice", "bob", "carol")) {
            val advance = GroupTurnCoordinator.advance(
                state = current,
                finishedRoleId = roleId,
                usage = usage,
                plan = plan,
                tokenLimit = 1000,
                now = 17_000L,
            )
            current = when (advance) {
                is GroupTurnCoordinator.Advance.More -> advance.state
                is GroupTurnCoordinator.Advance.BudgetStopped -> {
                    stop = advance.stop
                    advance.state
                }
                // 全员已提交：不可能在 1000 上限、每角色 500 的形状下发生。
                is GroupTurnCoordinator.Advance.Finished -> advance.state
            }
            if (stop != null) break
        }

        assertNotNull("第二个角色就该触顶（500+500=1000）", stop)
        stop as RoundBudget.Stop
        assertEquals(1000, stop.spent)
        assertEquals(1000, stop.limit)
        assertEquals(listOf("carol"), stop.skippedRoleIds)
        assertEquals(GroupRunEntity.STATUS_BUDGET_STOPPED, current.status)
        assertEquals(listOf("alice", "bob"), current.committedRoleIds)
    }

    // ------------------------------------------------------------------
    // 4. 取消 / 超时
    // ------------------------------------------------------------------

    @Test
    fun `cancel writes cancelled status and reason without losing progress`() {
        val cancelled = GroupTurnCoordinator.cancelRound(
            state(committed = listOf("alice"), spent = 120),
            now = 70_000L,
        )

        assertEquals(GroupRunEntity.STATUS_CANCELLED, cancelled.status)
        assertEquals(GroupRunEntity.REASON_CANCELLED, cancelled.reason)
        assertEquals("", cancelled.errorMessage)
        assertEquals(70_000L, cancelled.endedAt)
        assertEquals(70_000L, cancelled.updatedAt)
        assertTrue(GroupRunEntity.isTerminal(cancelled.status))
        // 已完成角色的产出与计数都不能因为取消而丢失。
        assertEquals(listOf("alice"), cancelled.committedRoleIds)
        assertEquals(120, cancelled.spentTokens)
    }

    @Test
    fun `timeout writes timeout status and reason and keeps the round resumable`() {
        val timedOut = GroupTurnCoordinator.timeoutRound(
            state(committed = listOf("alice", "bob"), spent = 300),
            "TimeoutCancellationException",
            now = 80_000L,
        )

        assertEquals(GroupRunEntity.STATUS_TIMEOUT, timedOut.status)
        assertEquals(GroupRunEntity.REASON_TIMEOUT, timedOut.reason)
        assertEquals("TimeoutCancellationException", timedOut.errorMessage)
        assertEquals(80_000L, timedOut.endedAt)
        assertTrue(GroupRunEntity.isTerminal(timedOut.status))

        // 超时与取消是两回事，但都不悬挂，且都能续跑。
        assertNotEquals(GroupRunEntity.STATUS_CANCELLED, timedOut.status)
        assertEquals(listOf("alice", "bob"), timedOut.committedRoleIds)
        assertEquals("carol", GroupTurnCoordinator.nextStep(planOf(pipelineConfig()), timedOut.committedRoleIds)?.role?.id)
    }

    @Test
    fun `complete round writes completed with no reason`() {
        val done = GroupTurnCoordinator.completeRound(
            state(committed = listOf("alice", "bob", "carol"), spent = 500),
            now = 90_000L,
        )

        assertEquals(GroupRunEntity.STATUS_COMPLETED, done.status)
        assertEquals("", done.reason)
        assertEquals(90_000L, done.endedAt)
        assertTrue(GroupRunEntity.isTerminal(done.status))
        // 兼容别名与正式名同值，收尾走哪个判定都一样。
        assertEquals(GroupRunEntity.STATUS_COMMITTED, done.status)
    }

    @Test
    fun `cancel timeout and failure are three distinct terminal outcomes`() {
        val cancelled = GroupTurnCoordinator.cancelRound(state(), 1L)
        val timedOut = GroupTurnCoordinator.timeoutRound(state(), "x", 1L)
        val failed = GroupTurnCoordinator.fail(state(), "bob", listOf("carol"), "x", 1L)

        assertEquals(3, setOf(cancelled.status, timedOut.status, failed.status).size)
        assertEquals(3, setOf(cancelled.reason, timedOut.reason, failed.reason).size)
        assertTrue(listOf(cancelled, timedOut, failed).all { GroupRunEntity.isTerminal(it.status) })
    }

    // ------------------------------------------------------------------
    // 5. roundtable：全员完成后仅议长发言
    // ------------------------------------------------------------------

    @Test
    fun `roundtable puts the chair last and only the chair gets chairRound`() {
        val plan = planOf(roundtableConfig())

        assertEquals("先全员发言", listOf("alice", "bob", "carol"), plan.plan.filterNot { it.chairRound }.map { it.role.id })

        val chairSteps = plan.plan.filter { it.chairRound }
        assertEquals("只有一个议长汇总步", 1, chairSteps.size)
        assertEquals("chair", chairSteps.single().role.id)
        assertEquals("议长必须排在最后", "chair", plan.plan.last().role.id)
    }

    @Test
    fun `non chair members cannot see other members output during a roundtable`() {
        val config = roundtableConfig()
        val messages = listOf(
            user("议题：上线顺序"),
            assistant("alice 的看法", "alice"),
            assistant("bob 的看法", "bob"),
        )

        val aliceView = GroupTurnCoordinator.viewerMessages(config, messages, stepOf(config, "alice"))
        assertEquals(
            "普通成员只能看到用户消息和自己的发言",
            listOf("议题：上线顺序", "alice 的看法"),
            aliceView.map { it.toText() },
        )

        val bobView = GroupTurnCoordinator.viewerMessages(config, messages, stepOf(config, "bob"))
        assertEquals(listOf("议题：上线顺序", "bob 的看法"), bobView.map { it.toText() })

        // 议长汇总时才放开本轮全部发言。
        val chairStep = GroupChat.plan(config, emptyList()).first { it.chairRound }
        val chairView = GroupTurnCoordinator.viewerMessages(config, messages, chairStep)
        assertEquals(
            listOf("议题：上线顺序", "alice 的看法", "bob 的看法"),
            chairView.map { it.toText() },
        )
    }

    @Test
    fun `turn kind distinguishes the chair summary from an ordinary speaker turn`() {
        val config = roundtableConfig()
        val steps = GroupChat.plan(config, emptyList())

        assertEquals(GroupChat.TURN_SPEAKER, GroupTurnCoordinator.turnKindOf(steps.first { !it.chairRound }))
        assertEquals(GroupChat.TURN_CHAIR, GroupTurnCoordinator.turnKindOf(steps.first { it.chairRound }))
    }

    @Test
    fun `the chair role falls back to the chair flag and never returns a dangling id`() {
        assertEquals(
            "chair",
            GroupTurnCoordinator.chairRoleIdOf(
                GroupConfig(roles = listOf(alice, chair), mode = GroupChat.MODE_ROUNDTABLE, tokenBudgetPerRound = 1000),
            ),
        )
        // 显式 chair_role_id 优先。
        assertEquals(
            "alice",
            GroupTurnCoordinator.chairRoleIdOf(
                GroupConfig(
                    roles = listOf(alice, chair),
                    mode = GroupChat.MODE_ROUNDTABLE,
                    chairRoleId = alice.id,
                    tokenBudgetPerRound = 1000,
                ),
            ),
        )
        // 指向不存在的成员时回落到 chair 标志，不返回野指针。
        assertEquals(
            "chair",
            GroupTurnCoordinator.chairRoleIdOf(
                GroupConfig(
                    roles = listOf(alice, chair),
                    mode = GroupChat.MODE_ROUNDTABLE,
                    chairRoleId = "ghost",
                    tokenBudgetPerRound = 1000,
                ),
            ),
        )
        assertNull(GroupTurnCoordinator.chairRoleIdOf(pipelineConfig()))
    }

    // ------------------------------------------------------------------
    // 6. 视角隔离：三角色任意组合无越权
    // ------------------------------------------------------------------

    @Test
    fun `a viewer never sees a member message that is neither its own nor its pipeline predecessor`() {
        val config = pipelineConfig()
        val messages = listOf(
            user("先讨论架构"),
            assistant("alice 主张 A", "alice"),
            assistant("bob 主张 B", "bob"),
            assistant("carol 主张 C", "carol"),
            assistant("本轮小结", GroupChat.SUMMARY_ID, turnKind = GroupChat.TURN_VOTE_SUMMARY),
        )
        val steps = GroupChat.plan(config, emptyList())

        config.roles.forEach { viewer ->
            val step = steps.first { it.role.id == viewer.id }
            val visibleTexts = GroupTurnCoordinator.viewerMessages(config, messages, step).map { it.toText() }

            // 契约允许的额外可见项只有 pipeline 的直接上一位。
            val allowed = setOfNotNull(viewer.id, step.predecessorId)
            config.roles.filter { it.id !in allowed }.forEach { other ->
                assertFalse(
                    "${viewer.id} 越权看到了 ${other.id} 的发言",
                    visibleTexts.contains(messages.first { it.roleId == other.id }.toText()),
                )
            }
            // 自己的发言、用户消息、轮次摘要都在。
            assertTrue(visibleTexts.contains(messages.first { it.roleId == viewer.id }.toText()))
            assertTrue(visibleTexts.contains("先讨论架构"))
            assertTrue(visibleTexts.contains("本轮小结"))
            // 可见集合一定是原集合的子集，不会凭空造消息。
            val visible = GroupTurnCoordinator.viewerMessages(config, messages, step)
            assertTrue(visible.all { candidate -> messages.any { it.id == candidate.id } })
        }
    }

    @Test
    fun `every ordered viewer pair is checked for cross leakage in roundtable mode`() {
        // roundtable 没有 predecessor，每个成员只能看到「用户消息 + 自己」，断言最严格。
        val config = roundtableConfig()
        val memberIds = listOf("alice", "bob", "carol")
        val messages = buildList {
            add(user("议题"))
            memberIds.forEach { add(assistant("$it 的私有结论", it)) }
        }
        val steps = GroupChat.plan(config, emptyList())

        // 双重循环：任意 (viewer, 其他成员) 组合都断言一次。
        memberIds.forEach { viewerId ->
            val visible = GroupTurnCoordinator.viewerMessages(
                config = config,
                allMessages = messages,
                step = steps.first { it.role.id == viewerId },
            )
            memberIds.filter { it != viewerId }.forEach { otherId ->
                assertFalse("$viewerId 看到了 $otherId", visible.any { it.roleId == otherId })
            }
            assertEquals("$viewerId 只应看到用户消息 + 自己那条", 2, visible.size)
        }
    }

    @Test
    fun `mentions are delivered only to the mentioned role`() {
        val config = pipelineConfig()
        val messages = listOf(
            user("请 @鲍勃 回答", mentions = listOf("bob")),
            assistant("alice 抢答", "alice"),
            assistant("bob 的正式回答", "bob"),
        )

        val bobStep = GroupChat.plan(config, listOf("bob")).first()
        assertTrue(
            GroupTurnCoordinator.viewerMessages(config, messages, bobStep).any { it.toText() == "bob 的正式回答" },
        )

        val carolStep = GroupChat.plan(config, listOf("carol")).first()
        assertFalse(
            "未被 @ 的角色拿不到别人的回答",
            GroupTurnCoordinator.viewerMessages(config, messages, carolStep).any { it.toText() == "bob 的正式回答" },
        )
    }

    // ------------------------------------------------------------------
    // 6b. mention_role_ids 放行分支（上一条用例没碰到的那条）
    // ------------------------------------------------------------------

    /**
     * 契约（`docs/beyond-operit-client-changes.md:200`）明文要求可见集合包含
     * 「`mention_role_ids` 包含自己的消息」。这条用例**真正**触发那个分支：
     * `roleId = "alice"` 而 `mentionRoleIds = ["bob"]` 的一条**助手**消息。
     *
     * 用 `vote` 配置且不传 `predecessorId` / `chairRound`：`vote` 模式没有 predecessor，
     * 于是「bob 看得见」只可能来自 mention 分支，不会被上一位放行分支带出假阳性。
     */
    @Test
    fun `a mention on another role's message reaches exactly the mentioned viewer`() {
        val config = voteConfig()
        val messages = listOf(
            user("先讨论架构"),
            speaking("alice 点名让 bob 看", "alice", mentionRoleIds = listOf("bob")),
        )

        val visibleFor = { viewerId: String ->
            GroupChat.visibleMessages(config, messages, viewerId).map { it.toText() }
        }

        // 三个方向一起断，证明「作者」与「被 @」两条分支互不干扰。
        assertTrue(
            "作者看得到自己那条（roleId == viewerId 分支）",
            visibleFor("alice").contains("alice 点名让 bob 看"),
        )
        assertTrue(
            "被 @ 的 bob 看得到（mentionRoleIds 分支）",
            visibleFor("bob").contains("alice 点名让 bob 看"),
        )
        assertFalse(
            "既不是作者也不在 mention 里的 carol 看不到",
            visibleFor("carol").contains("alice 点名让 bob 看"),
        )
        // 三个人都得看得见用户消息。
        listOf("alice", "bob", "carol").forEach { viewerId ->
            assertTrue("$viewerId 应看得见用户消息", visibleFor(viewerId).contains("先讨论架构"))
        }
    }

    /**
     * 反向对照（防「过滤对所有人都不生效」的假阳性）：同一段文本、同一批视角，
     * 唯一差别是 `mentionRoleIds` 空不空。被 @ 的是 bob，看得见的**必须**只有 bob——
     * 如果这条在 mention 为空时也通过，说明上面那条根本没测到 mention 分支。
     */
    @Test
    fun `dropping the mention closes the branch again for the mentioned viewer`() {
        val config = voteConfig()
        val withMention = listOf(
            user("议题"),
            speaking("alice 的私密结论", "alice", mentionRoleIds = listOf("bob")),
        )
        val withoutMention = listOf(
            user("议题"),
            speaking("alice 的私密结论", "alice"),
        )

        val bobWith = GroupChat.visibleMessages(config, withMention, "bob").map { it.toText() }
        val bobWithout = GroupChat.visibleMessages(config, withoutMention, "bob").map { it.toText() }

        assertTrue(bobWith.contains("alice 的私密结论"))
        assertFalse(
            "mention 一去掉，bob 就必须重新看不见——否则上一条是假阳性",
            bobWithout.contains("alice 的私密结论"),
        )
        // alice 自己两种形状都看得见（自己的发言不受 mention 影响）。
        assertTrue(GroupChat.visibleMessages(config, withoutMention, "alice").map { it.toText() }
            .contains("alice 的私密结论"))
    }

    /** 多个角色同时被 @：放行集合是「全体被 @ 者」，不多不少。 */
    @Test
    fun `every mentioned role is reached and unmentioned ones are not`() {
        val config = voteConfig()
        val text = "alice 同时点名 bob 和 carol"
        val messages = listOf(
            user("三人一起看"),
            speaking(text, "alice", mentionRoleIds = listOf("bob", "carol")),
        )

        val visibleFor = { viewerId: String ->
            GroupChat.visibleMessages(config, messages, viewerId).map { it.toText() }
        }

        assertTrue("被 @ 的 bob 看得到", visibleFor("bob").contains(text))
        assertTrue("被 @ 的 carol 看得到", visibleFor("carol").contains(text))
        // 作者仍然看得见自己那条（走 roleId == viewerId 那条分支，与 mention 无关）。
        assertTrue("作者 alice 看得到自己那条", visibleFor("alice").contains(text))
        // 三条都看得见用户消息，用户消息不受任何分支影响。
        listOf("alice", "bob", "carol").forEach { viewerId ->
            assertTrue("$viewerId 应看得见用户消息", visibleFor(viewerId).contains("三人一起看"))
        }
    }

    @Test
    fun `a pipeline viewer additionally sees only the immediate predecessor output`() {
        val config = pipelineConfig()
        val messages = listOf(
            user("接力"),
            assistant("alice 输出", "alice"),
            assistant("bob 输出", "bob"),
        )

        // carol 接力时额外拿到 bob（上一位）的本轮输出，看不到 alice。
        val carolView = GroupTurnCoordinator.viewerMessages(config, messages, stepOf(config, "carol")).map { it.toText() }
        assertEquals(listOf("接力", "bob 输出"), carolView)
        assertFalse(carolView.contains("alice 输出"))
    }

    @Test
    fun `round id is derived from the trigger message so retries reuse the same round`() {
        val config = pipelineConfig()
        // 同一轮期间触发消息是同一条（id 固定），所以 round_id 稳定，重试必然落回同一轮。
        val trigger = user("t1")
        val first = GroupTurnCoordinator.roundPlanFor(config, listOf(trigger, assistant("a", "alice")))
        val retry = GroupTurnCoordinator.roundPlanFor(
            config,
            listOf(trigger, assistant("a", "alice"), assistant("b", "bob")),
        )

        assertEquals(first?.roundId, retry?.roundId)
        assertEquals("round-${trigger.id}", first?.roundId)
        assertEquals(first!!.roundId, GroupChat.roundIdFor(trigger.id.toString()))

        // 没有用户消息就没有轮次（不得凭空造一轮）。
        assertNull(GroupTurnCoordinator.roundPlanFor(config, listOf(assistant("a", "alice"))))
    }

    @Test
    fun `round messages only cover the segment after the trigger`() {
        val roundMessages = GroupTurnCoordinator.roundMessages(
            listOf(
                user("第一轮"),
                assistant("旧轮 alice", "alice", roundId = "round-1"),
                user("第二轮"),
                assistant("本轮 alice", "alice", roundId = "round-2"),
                assistant("本轮 bob", "bob", roundId = "round-2"),
            ),
        )
        assertEquals(listOf("本轮 alice", "本轮 bob"), roundMessages.map { it.toText() })
    }

    @Test
    fun `the memory query is built only from viewer visible user messages`() {
        val config = pipelineConfig()
        val messages = listOf(
            user("旧议题"),
            user("当前议题：预算"),
            assistant("alice 私密结论", "alice"),
        )

        val viewer = GroupTurnCoordinator.viewerMessages(config, messages, stepOf(config, "carol"))
        val query = GroupTurnCoordinator.memoryQuery(viewer)

        assertTrue(query.contains("当前议题：预算"))
        assertTrue(query.contains("旧议题"))
        // 检索词本身不能带越权内容。
        assertFalse(query.contains("私密结论"))
    }

    // ------------------------------------------------------------------
    // 7. 记忆按 roleId 收紧
    // ------------------------------------------------------------------

    @Test
    fun `memories are narrowed by role id and by viewer visible source message`() {
        val aliceMessage = assistant("alice 说的", "alice")
        val bobMessage = assistant("bob 说的", "bob")
        val memories = listOf(
            AssistantMemory(
                id = 1,
                content = "alice 记忆",
                sourceMessageId = aliceMessage.id.toString(),
                roleId = "alice",
            ),
            AssistantMemory(
                id = 2,
                content = "bob 记忆",
                sourceMessageId = bobMessage.id.toString(),
                roleId = "bob",
            ),
            // 无来源（可能来自用户显式 memory_add）→ 保留。
            AssistantMemory(id = 3, content = "用户手写记忆", sourceMessageId = null, roleId = null),
        )

        val filtered = GroupTurnCoordinator.memoriesForViewer(
            viewerRoleId = "alice",
            viewerMessageIds = setOf(aliceMessage.id.toString()),
            memories = memories,
        )

        assertEquals(listOf("alice 记忆", "用户手写记忆"), filtered.map { it.content })
        assertFalse(filtered.any { it.roleId == "bob" })
    }

    @Test
    fun `an empty memory list is returned untouched`() {
        assertEquals(
            emptyList<AssistantMemory>(),
            GroupTurnCoordinator.memoriesForViewer("alice", emptySet(), emptyList()),
        )
    }

    @Test
    fun `the memory space id is fixed to group conversation and role`() {
        assertEquals("group:conv-1:role:alice", GroupChat.memorySpaceId("conv-1", "alice"))
        assertNotEquals(
            GroupChat.memorySpaceId("conv-1", "alice"),
            GroupChat.memorySpaceId("conv-1", "bob"),
        )
        assertNotEquals(
            GroupChat.memorySpaceId("conv-1", "alice"),
            GroupChat.memorySpaceId("conv-2", "alice"),
        )

        // 配置校验拒绝与派生值不一致的写法（不得回退到全局/助手空间）。
        val bad = pipelineConfig().copy(roles = listOf(alice.copy(memorySpaceId = "assistant-space")))
        assertTrue(GroupChat.validate(bad, "conv-1").any { it.field.contains("memory_space_id") })

        val good = pipelineConfig().copy(
            roles = listOf(alice.copy(memorySpaceId = GroupChat.memorySpaceId("conv-1", "alice"))),
        )
        assertTrue(GroupChat.validate(good, "conv-1").isEmpty())
    }

    // ------------------------------------------------------------------
    // 8. 投票与平票裁决
    // ------------------------------------------------------------------

    @Test
    fun `ballots outside the candidate set are discarded`() {
        val config = voteConfig()
        val candidates = planOf(config).candidates
        val ballots = GroupTurnCoordinator.collectBallots(
            config = config,
            candidates = candidates,
            roundMessages = listOf(
                assistant("VOTE: opt-a", "alice"),
                assistant("VOTE: opt-c", "bob"), // 集外 → 丢弃
                assistant("VOTE: opt-b", "carol"),
                assistant("VOTE: opt-a", "ghost"), // 非本群成员 → 丢弃
                assistant("我觉得应该选 opt-a", "carol"), // 非结构化 → 丢弃
                user("VOTE: opt-a"), // 用户消息不是票
            ),
        )

        assertEquals(listOf("alice", "carol"), ballots.map { it.roleId })
        assertEquals(listOf("opt-a", "opt-b"), ballots.map { it.candidateId })
    }

    @Test
    fun `a decisive majority writes a vote summary and completes the round`() {
        val config = voteConfig(tiePolicy = GroupChat.TIE_FAIL)

        val resolution = GroupTurnCoordinator.resolveVote(
            state = state(committed = listOf("alice", "bob", "carol"), spent = 300),
            config = config,
            plan = planOf(config),
            roundMessages = listOf(
                assistant("VOTE: opt-a | 理由充分", "alice"),
                assistant("VOTE: opt-b", "bob"),
                assistant("VOTE: opt-a", "carol"),
            ),
            chairAlreadyDecided = false,
            now = 100_000L,
        )

        assertTrue(resolution is GroupTurnCoordinator.VoteResolution.Decided)
        resolution as GroupTurnCoordinator.VoteResolution.Decided
        assertEquals(GroupChat.TURN_VOTE_SUMMARY, resolution.summary.turnKind)
        assertEquals(GroupChat.SUMMARY_ID, resolution.summary.roleId)
        assertTrue(resolution.summary.toText().contains("opt-a"))
        assertEquals(GroupRunEntity.STATUS_COMPLETED, resolution.state.status)
        assertEquals(100_000L, resolution.state.endedAt)
        assertEquals(300, resolution.state.spentTokens)
    }

    @Test
    fun `the vote summary body shows candidate display names next to their ids`() {
        // 候选就是角色本身时摘要用显示名（displayNameOf 的用途），而不是裸 id。
        val config = voteConfig(candidates = listOf(alice.id, bob.id), roles = listOf(alice, bob))
        val outcome = GroupChat.tally(
            ballots = listOf(VoteBallot("carol", alice.id)),
            candidates = config.voteCandidates,
            tiePolicy = config.tiePolicy,
        )
        assertTrue(outcome is VoteOutcome.Decided)

        val summary = GroupTurnCoordinator.voteSummaryMessage(state(), config, outcome as VoteOutcome.Decided)
        val text = summary.toText()
        assertTrue("应显示角色名", text.contains("爱丽丝"))
        assertTrue("同时保留 id 便于追溯", text.contains(alice.id))
        assertFalse(text.contains("卡罗尔"))
    }

    @Test
    fun `displayNameOf falls back to the role id when no name is set`() {
        val config = voteConfig(roles = listOf(GroupRole(id = "nameless", assistantId = "a")))
        assertEquals("nameless", GroupTurnCoordinator.displayNameOf(config, "nameless"))
        assertEquals("ghost", GroupTurnCoordinator.displayNameOf(config, "ghost"))
        assertEquals("爱丽丝", GroupTurnCoordinator.displayNameOf(pipelineConfig(), "alice"))
    }

    @Test
    fun `TIE_FAIL leaves a tie undecided and writes no summary`() {
        val config = voteConfig(tiePolicy = GroupChat.TIE_FAIL)

        val resolution = GroupTurnCoordinator.resolveVote(
            state = state(committed = listOf("alice", "bob")),
            config = config,
            plan = planOf(config),
            roundMessages = listOf(
                assistant("VOTE: opt-a", "alice"),
                assistant("VOTE: opt-b", "bob"),
            ),
            chairAlreadyDecided = false,
            now = 102_000L,
        )

        assertTrue(
            "TIE_FAIL 下平票必须 Undecided，不能自动产生结论",
            resolution is GroupTurnCoordinator.VoteResolution.Undecided,
        )
        resolution as GroupTurnCoordinator.VoteResolution.Undecided
        assertEquals(GroupRunEntity.STATUS_FAILED, resolution.state.status)
        assertEquals(GroupTurnCoordinator.REASON_VOTE_NO_DECISION, resolution.state.reason)
        assertTrue(resolution.detail.contains("平票"))
        assertEquals(102_000L, resolution.state.endedAt)
    }

    @Test
    fun `TIE_CHAIR routes the same tie to the chair instead of failing`() {
        val config = voteConfig(tiePolicy = GroupChat.TIE_CHAIR, roles = listOf(alice, bob, chair))

        val resolution = GroupTurnCoordinator.resolveVote(
            state = state(committed = listOf("alice", "bob")),
            config = config,
            plan = planOf(config),
            roundMessages = listOf(
                assistant("VOTE: opt-a", "alice"),
                assistant("VOTE: opt-b", "bob"),
            ),
            chairAlreadyDecided = false,
            now = 103_000L,
        )

        assertTrue(resolution is GroupTurnCoordinator.VoteResolution.NeedsChairTieBreak)
        resolution as GroupTurnCoordinator.VoteResolution.NeedsChairTieBreak
        assertEquals("chair", resolution.chairRoleId)
        assertEquals(listOf("opt-a", "opt-b"), resolution.tiedCandidates)
        assertEquals(mapOf("opt-a" to 1, "opt-b" to 1), resolution.tally)
        // 裁决提示是给议长看的提示，不是对话内容。
        assertEquals(MessageRole.SYSTEM, resolution.instruction.role)
        assertTrue(resolution.instruction.isSynthetic)
        assertEquals("chair", resolution.instruction.roleId)
        val text = resolution.instruction.toText()
        assertTrue(text.contains(GroupChat.BALLOT_PREFIX))
        assertTrue(text.contains("议长"))
        assertTrue(text.contains("opt-a"))
    }

    @Test
    fun `the same tie yields different conclusions under TIE_FAIL and TIE_CHAIR`() {
        val roundMessages = listOf(
            assistant("VOTE: opt-a", "alice"),
            assistant("VOTE: opt-b", "bob"),
        )
        val failConfig = voteConfig(tiePolicy = GroupChat.TIE_FAIL)
        val chairConfig = voteConfig(tiePolicy = GroupChat.TIE_CHAIR, roles = listOf(alice, bob, chair))

        val underFail = GroupTurnCoordinator.resolveVote(
            state = state(),
            config = failConfig,
            plan = planOf(failConfig),
            roundMessages = roundMessages,
            chairAlreadyDecided = false,
            now = 104_000L,
        )
        val underChair = GroupTurnCoordinator.resolveVote(
            state = state(),
            config = chairConfig,
            plan = planOf(chairConfig),
            roundMessages = roundMessages,
            chairAlreadyDecided = false,
            now = 104_000L,
        )

        assertTrue(underFail is GroupTurnCoordinator.VoteResolution.Undecided)
        assertTrue(underChair is GroupTurnCoordinator.VoteResolution.NeedsChairTieBreak)
        assertNotEquals(underFail::class, underChair::class)
    }

    @Test
    fun `TIE_CHAIR without a chair fails loudly instead of hanging`() {
        val config = voteConfig(tiePolicy = GroupChat.TIE_CHAIR) // 没有任何议长
        assertNull(GroupTurnCoordinator.chairRoleIdOf(config))

        val resolution = GroupTurnCoordinator.resolveVote(
            state = state(),
            config = config,
            plan = planOf(config),
            roundMessages = listOf(
                assistant("VOTE: opt-a", "alice"),
                assistant("VOTE: opt-b", "bob"),
            ),
            chairAlreadyDecided = false,
            now = 105_000L,
        )

        assertTrue(resolution is GroupTurnCoordinator.VoteResolution.Undecided)
        resolution as GroupTurnCoordinator.VoteResolution.Undecided
        assertEquals(GroupRunEntity.STATUS_FAILED, resolution.state.status)
        assertTrue(resolution.detail.contains("议长"))
    }

    @Test
    fun `a chair tie break converges by counting only the chair vote`() {
        val config = voteConfig(tiePolicy = GroupChat.TIE_CHAIR, roles = listOf(alice, bob, chair))
        val roundMessages = listOf(
            assistant("VOTE: opt-a", "alice"),
            assistant("VOTE: opt-b", "bob"),
            assistant("VOTE: opt-a", "chair", turnKind = GroupChat.TURN_CHAIR),
        )
        assertTrue(GroupTurnCoordinator.chairAlreadyDecided(roundMessages, "chair"))

        val resolution = GroupTurnCoordinator.resolveVote(
            state = state(),
            config = config,
            plan = planOf(config),
            roundMessages = roundMessages,
            chairAlreadyDecided = true,
            now = 106_000L,
        )

        assertTrue(resolution is GroupTurnCoordinator.VoteResolution.Decided)
        assertTrue((resolution as GroupTurnCoordinator.VoteResolution.Decided).summary.toText().contains("opt-a"))
        assertEquals(GroupRunEntity.STATUS_COMPLETED, resolution.state.status)
    }

    @Test
    fun `a chair ballot outside the candidate set leaves the round undecided`() {
        val config = voteConfig(tiePolicy = GroupChat.TIE_CHAIR, roles = listOf(alice, bob, chair))
        val resolution = GroupTurnCoordinator.resolveVote(
            state = state(),
            config = config,
            plan = planOf(config),
            roundMessages = listOf(
                assistant("VOTE: opt-a", "alice"),
                assistant("VOTE: opt-b", "bob"),
                // 议长投了候选集外的票 → 无效，不能据此产生结论。
                assistant("VOTE: opt-z", "chair", turnKind = GroupChat.TURN_CHAIR),
            ),
            chairAlreadyDecided = true,
            now = 107_000L,
        )

        assertTrue(resolution is GroupTurnCoordinator.VoteResolution.Undecided)
        assertTrue((resolution as GroupTurnCoordinator.VoteResolution.Undecided).detail.contains("议长"))
        assertEquals(GroupRunEntity.STATUS_FAILED, resolution.state.status)
    }

    @Test
    fun `an empty candidate set makes the vote invalid rather than guessed`() {
        val config = voteConfig(candidates = emptyList())
        assertTrue(planOf(config).candidates.isEmpty())

        val resolution = GroupTurnCoordinator.resolveVote(
            state = state(),
            config = config,
            plan = planOf(config),
            roundMessages = listOf(assistant("VOTE: 随便什么", "alice")),
            chairAlreadyDecided = false,
            now = 108_000L,
        )

        assertTrue(resolution is GroupTurnCoordinator.VoteResolution.Undecided)
        resolution as GroupTurnCoordinator.VoteResolution.Undecided
        assertEquals(GroupRunEntity.STATUS_FAILED, resolution.state.status)
        assertEquals(emptyList<VoteBallot>(), GroupTurnCoordinator.collectBallots(config, emptyList(), listOf(assistant("VOTE: x", "alice"))))
    }

    @Test
    fun `ballot parsing is strict about the candidate set and the prefix`() {
        val candidates = listOf("opt-a", "opt-b")

        assertEquals("opt-a", GroupChat.parseBallot("VOTE: opt-a", "alice", candidates)?.candidateId)
        assertEquals("理由", GroupChat.parseBallot("VOTE: opt-a | 理由", "alice", candidates)?.reason)
        assertEquals("alice", GroupChat.parseBallot("VOTE: opt-b", "alice", candidates)?.roleId)
        // 前缀忽略大小写，截断也必须同样忽略，否则小写前缀会被静默丢弃。
        assertEquals("opt-a", GroupChat.parseBallot("vote: opt-a", "alice", candidates)?.candidateId)
        // 只认整行前缀，不做自由文本猜测。
        assertNull(GroupChat.parseBallot("VOTE: opt-z", "alice", candidates))
        assertNull(GroupChat.parseBallot("我觉得 opt-a 更好", "alice", candidates))
        assertNull(GroupChat.parseBallot("前缀VOTE: opt-a", "alice", candidates))
        assertNull(GroupChat.parseBallot("", "alice", candidates))
    }

    @Test
    fun `tally keeps only the last ballot per role`() {
        val outcome = GroupChat.tally(
            ballots = listOf(
                VoteBallot("alice", "opt-a"),
                VoteBallot("alice", "opt-b"),
                VoteBallot("bob", "opt-b"),
            ),
            candidates = listOf("opt-a", "opt-b"),
            tiePolicy = GroupChat.TIE_FAIL,
        )

        assertTrue(outcome is VoteOutcome.Decided)
        outcome as VoteOutcome.Decided
        assertEquals("opt-b", outcome.winner)
        // alice 的 opt-a 被「后一票覆盖前一票」整个丢弃，不是两票都算数。
        assertEquals(mapOf("opt-b" to 2), outcome.tally)
        assertNull(outcome.tally["opt-a"])
        assertEquals(2, outcome.ballots.size)
    }

    @Test
    fun `tie break scaffolding is reclaimed cleanly from message nodes`() {
        val config = voteConfig(tiePolicy = GroupChat.TIE_CHAIR, roles = listOf(alice, bob, chair))
        val instruction = GroupTurnCoordinator.tieBreakInstruction(
            state = state(),
            config = config,
            chairRoleId = "chair",
            tiedCandidates = listOf("opt-a", "opt-b"),
            tally = mapOf("opt-a" to 1, "opt-b" to 1),
        )
        val chairReply = assistant("VOTE: opt-a", "chair", turnKind = GroupChat.TURN_CHAIR)
        val nodes = listOf(
            user("投票").toMessageNode(),
            assistant("VOTE: opt-a", "alice").toMessageNode(),
            instruction.toMessageNode(),
            chairReply.toMessageNode(),
        )

        val cleaned = GroupTurnCoordinator.withoutTieBreakInstruction(nodes)
        val cleanedMessages = cleaned.flatMap { it.messages }

        assertEquals("脚手架被删掉，其余消息保留", 3, cleaned.size)
        assertTrue(cleanedMessages.none { it.role == MessageRole.SYSTEM && it.turnKind == GroupChat.TURN_CHAIR })
        // 议长的裁决回复本身不能被误删。
        assertTrue(cleanedMessages.any { it.roleId == "chair" && it.turnKind == GroupChat.TURN_CHAIR })
        assertTrue(cleanedMessages.any { it.roleId == "alice" })
        assertTrue(cleanedMessages.any { it.role == MessageRole.USER })
        // 不动源列表。
        assertEquals(4, nodes.size)
    }

    @Test
    fun `reclaiming scaffolding drops nodes that become empty`() {
        val only = GroupTurnCoordinator.tieBreakInstruction(
            state = state(),
            config = voteConfig(),
            chairRoleId = "alice",
            tiedCandidates = listOf("opt-a"),
            tally = mapOf("opt-a" to 1),
        ).toMessageNode()

        assertEquals(emptyList<MessageNode>(), GroupTurnCoordinator.withoutTieBreakInstruction(listOf(only)))
    }

    // ------------------------------------------------------------------
    // 9. 不同群互不干扰
    // ------------------------------------------------------------------

    @Test
    fun `different conversations keep independent budget decisions`() {
        // 群 A 已经用掉 450 且上限 400；群 B 才花了 10 且上限 1000。
        val convA = state(
            conversationId = "conv-A",
            runToken = "token-A",
            spent = 450,
            limit = 400,
            committed = listOf("alice", "bob"),
        )
        val convB = state(conversationId = "conv-B", runToken = "token-B", spent = 10, limit = 1000)

        assertTrue(
            "群 A 超预算",
            GroupChat.budgetDecision(convA.spentTokens, convA.tokenLimit, listOf("carol")) is RoundBudget.Stop,
        )
        assertEquals(
            "群 B 的判定不受群 A 影响",
            RoundBudget.Continue,
            GroupChat.budgetDecision(convB.spentTokens, convB.tokenLimit, listOf("alice", "bob", "carol")),
        )

        val entityA = GroupTurnCoordinator.toEntity(convA)
        val entityB = GroupTurnCoordinator.toEntity(convB)
        assertNotEquals(entityA.conversationId, entityB.conversationId)
        assertNotEquals(entityA.runToken, entityB.runToken)
        // 复合主键 (conversation_id, round_id) + run_token UNIQUE：两群互不冲突。
        assertEquals("conv-A", GroupTurnCoordinator.fromEntity(entityA).conversationId)
        assertEquals("conv-B", GroupTurnCoordinator.fromEntity(entityB).conversationId)
    }

    @Test
    fun `a run in one conversation does not block another conversation`() {
        val config = pipelineConfig()
        val plan = planOf(config)

        // 群 A 正在跑；群 B 用自己的 conversationId 抢占，必须成功。
        val claimB = GroupTurnCoordinator.claimRound(
            conversationId = "conv-B",
            plan = plan,
            tokenLimit = 1000,
            existing = null,
            expectedRunToken = null,
            activeRunToken = null,
            newRunToken = "token-B",
            now = 111_000L,
        )
        assertTrue(claimB is GroupTurnCoordinator.Claim.Acquired)
        claimB as GroupTurnCoordinator.Claim.Acquired
        assertEquals("conv-B", claimB.state.conversationId)
        assertEquals(0, claimB.state.spentTokens)

        // 群 A 自己再触发则必须被拒。
        val claimA = GroupTurnCoordinator.claimRound(
            conversationId = "conv-A",
            plan = plan,
            tokenLimit = 1000,
            existing = state(conversationId = "conv-A", status = GroupRunEntity.STATUS_RUNNING),
            expectedRunToken = null,
            activeRunToken = "token-A",
            newRunToken = "token-A2",
            now = 111_100L,
        )
        assertEquals(
            GroupTurnCoordinator.RejectCode.ALREADY_RUNNING,
            (claimA as GroupTurnCoordinator.Claim.Rejected).code,
        )
    }

    // ------------------------------------------------------------------
    // 10. 工具与检索同源上下文（证明真覆盖，不只是「应该覆盖」)
    // ------------------------------------------------------------------

    @Test
    fun `a tool system prompt only receives viewer visible messages`() {
        val config = roundtableConfig()
        val allMessages = listOf(
            user("公开议题"),
            assistant("alice 私密结论", "alice"),
            assistant("bob 私密结论", "bob"),
            assistant("carol 私密结论", "carol"),
        )
        var seenByTool: List<UIMessage> = emptyList()
        val spy = Tool(
            name = "spy",
            description = "记录它看到的 messages",
            systemPrompt = { _, messages ->
                seenByTool = messages
                messages.joinToString("\n") { it.toText() }
            },
            execute = { emptyList() },
        )

        // 执行层传进来的是**完整历史**（GenerationLoop.generateText 的 messages 形参）。
        val prompt = viewerScopedTool(spy, config, stepOf(config, "bob")).systemPrompt(model, allMessages)

        assertEquals("工具拿到的是过滤后的集合", 2, seenByTool.size)
        assertEquals(listOf("公开议题", "bob 私密结论"), seenByTool.map { it.toText() })
        assertTrue(prompt.contains("bob 私密结论"))
        assertFalse(prompt.contains("alice 私密结论"))
        assertFalse(prompt.contains("carol 私密结论"))
    }

    @Test
    fun `viewer scoping applies to every tool and follows the chair round`() {
        val config = roundtableConfig()
        val allMessages = listOf(
            user("议题"),
            assistant("alice 发言", "alice"),
            assistant("bob 发言", "bob"),
        )
        var aCount = -1
        var bCount = -1
        val tools = listOf(
            Tool(
                name = "a",
                description = "",
                systemPrompt = { _, m -> aCount = m.size; "" },
                execute = { emptyList() },
            ),
            Tool(
                name = "b",
                description = "",
                systemPrompt = { _, m -> bCount = m.size; "" },
                execute = { emptyList() },
            ),
        )

        // 普通成员：用户消息 + 自己那条 = 2
        viewerScopedTools(tools, config, stepOf(config, "alice")).forEach { it.systemPrompt(model, allMessages) }
        assertEquals(2, aCount)
        assertEquals(2, bCount)

        // 议长汇总：放开本轮全部发言 = 3
        val chairStep = GroupChat.plan(config, emptyList()).first { it.chairRound }
        viewerScopedTools(tools, config, chairStep).forEach { it.systemPrompt(model, allMessages) }
        assertEquals(3, aCount)
        assertEquals(3, bCount)
    }

    @Test
    fun `viewer scoping is idempotent when applied again downstream`() {
        val config = roundtableConfig()
        val messages = listOf(
            user("议题"),
            assistant("alice 发言", "alice"),
            assistant("bob 发言", "bob"),
        )
        val step = stepOf(config, "bob")

        val once = GroupTurnCoordinator.viewerMessages(config, messages, step)
        val twice = GroupTurnCoordinator.viewerMessages(config, once, step)
        val thrice = GroupTurnCoordinator.viewerMessages(config, twice, step)

        assertEquals(listOf("议题", "bob 发言"), once.map { it.toText() })
        assertEquals(once.map { it.id }, twice.map { it.id })
        assertEquals(once.map { it.id }, thrice.map { it.id })
    }

    /**
     * 反向证据：把过滤后的列表直接当 `generateText(messages = ...)` 传下去是**破坏性**的。
     *
     * 执行层用 `updateCurrentMessages(chunk.messages)` 把生成结果写回会话，而该函数按**下标**
     * 覆盖 `messageNodes`。传过滤后的列表会让下标错位，把别人的消息挤进同一个 node
     * （消息树的分支语义被破坏）。所以过滤只能注入到工具 systemPrompt 与 inputTransformer，
     * 不能动 messages 形参——这个用例把结论钉死，避免以后有人改回危险写法。
     */
    @Test
    fun `passing filtered messages into updateCurrentMessages corrupts other roles nodes`() {
        val conversation = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(
                user("议题").toMessageNode(),
                assistant("alice 发言", "alice").toMessageNode(),
                assistant("bob 发言", "bob").toMessageNode(),
            ),
        )
        val config = pipelineConfig()
        // carol 在 pipeline 里排在 bob 之后，predecessor 是 bob，所以她的可见集是 [议题, bob]。
        val carolStep = GroupChat.plan(config, emptyList()).first { it.role.id == "carol" }
        val filtered = GroupTurnCoordinator.viewerMessages(config, conversation.currentMessages, carolStep)

        assertTrue("过滤后确实更短", filtered.size < conversation.currentMessages.size)
        assertEquals(listOf("议题", "bob 发言"), filtered.map { it.toText() })

        val corrupted = conversation.updateCurrentMessages(filtered)

        // 过滤后只剩 [议题, bob]，按下标写回会把 bob 追加进 alice 所在的 node。
        val merged = corrupted.messageNodes.first { node -> node.messages.any { it.roleId == "alice" } }
        assertEquals("alice 的 node 被撑成两条消息", 2, merged.messages.size)
        assertTrue("alice 的 node 被塞进了 bob 的消息", merged.messages.any { it.roleId == "bob" })
        assertEquals(
            "bob 的消息出现在两个 node 里",
            2,
            corrupted.messageNodes.count { node -> node.messages.any { it.roleId == "bob" } },
        )
        // 致命后果：alice 的发言从「当前路径」里彻底消失（selectIndex 指向了后写入的 bob）。
        assertEquals(
            listOf(null, "bob", "bob"),
            corrupted.currentMessages.map { it.roleId },
        )
        assertEquals(
            listOf(null, "alice", "bob"),
            conversation.currentMessages.map { it.roleId },
        )
    }

    // ------------------------------------------------------------------
    // 11. 运行日志有效性辅助判定
    // ------------------------------------------------------------------

    @Test
    fun `round output present detects deleted or branched away outputs`() {
        val messages = listOf(
            user("议题"),
            assistant("alice 发言", "alice", roundId = "round-1"),
            assistant("bob 发言", "bob", roundId = "round-1"),
        )

        assertTrue(roundOutputPresent(messages, "round-1", listOf("alice")))
        assertTrue(roundOutputPresent(messages, "round-1", listOf("alice", "bob")))
        assertTrue(roundOutputPresent(messages, "round-1", emptyList()))
        // 切分支后 alice 的产出没了 → committed 失效，必须重跑这一轮。
        assertFalse(roundOutputPresent(messages, "round-2", listOf("alice")))
        // carol 从未发言。
        assertFalse(roundOutputPresent(messages, "round-1", listOf("carol")))
    }

    @Test
    fun `error nodes do not count as committed round output`() {
        val messages = listOf(
            user("议题"),
            assistant("[鲍勃] 本轮生成失败：boom", "bob", roundId = "round-1", turnKind = GroupChat.TURN_ERROR),
        )

        assertFalse(
            "只有错误节点不算产出，否则续跑会永远跳过这个角色",
            roundOutputPresent(messages, "round-1", listOf("bob")),
        )
    }

    @Test
    fun `group conversation detection requires both a config and the group type`() {
        val group = Conversation(
            assistantId = Uuid.random(),
            messageNodes = emptyList(),
            type = GroupChat.TYPE_GROUP,
            groupConfig = pipelineConfig(),
        )

        assertTrue(isGroupConversation(group))
        // 有 group_config 但已改回单聊 → 不能动运行日志。
        assertFalse(isGroupConversation(group.copy(type = GroupChat.TYPE_DIRECT)))
        assertFalse(isGroupConversation(group.copy(groupConfig = null)))
    }
}
