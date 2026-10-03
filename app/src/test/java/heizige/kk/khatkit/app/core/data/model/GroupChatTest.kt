package heizige.kk.khatkit.app.core.data.model

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatTest {
    private val alice = GroupRole("a", "Alice", "asst-a")
    private val bob = GroupRole("b", "Bob", "asst-b")
    private val cara = GroupRole("c", "Cara", "asst-c", chair = true)
    private val roles = listOf(alice, bob, cara)

    @Test
    fun `mention routes only the named role and does not leak to the third`() {
        val mentions = GroupChat.parseMentions("@Bob 看一下", roles)
        assertEquals(listOf("b"), mentions)
        val messages = listOf(
            UIMessage.user("@Bob 看一下").copy(mentionRoleIds = mentions),
            UIMessage.assistant("Bob 的回复").copy(roleId = "b"),
            UIMessage.assistant("Alice 的私话").copy(roleId = "a"),
        )
        val bobSees = GroupChat.visibleMessages(config(GroupChat.MODE_VOTE), messages, "b").map { it.toText() }
        val caraSees = GroupChat.visibleMessages(config(GroupChat.MODE_VOTE), messages, "c").map { it.toText() }
        assertTrue(bobSees.any { it.contains("Bob 的回复") })
        assertFalse(bobSees.any { it.contains("Alice 的私话") })
        assertTrue(caraSees.any { it.contains("@Bob") })
        assertFalse(caraSees.any { it.contains("Bob 的回复") })
        assertFalse(caraSees.any { it.contains("Alice 的私话") })
    }

    @Test
    fun `pipeline hands only the previous role output to the next`() {
        val plan = GroupChat.plan(config(GroupChat.MODE_PIPELINE), emptyList())
        assertEquals(listOf("a", "b", "c"), plan.map { it.role.id })
        assertEquals("a", plan[1].predecessorId)
        val messages = listOf(
            UIMessage.user("开始"),
            UIMessage.assistant("Alice 说").copy(roleId = "a"),
        )
        val bobSees = GroupChat.visibleMessages(
            config(GroupChat.MODE_PIPELINE),
            messages,
            "b",
            predecessorId = "a",
        ).map { it.toText() }
        val caraSees = GroupChat.visibleMessages(
            config(GroupChat.MODE_PIPELINE),
            messages,
            "c",
        ).map { it.toText() }
        assertTrue(bobSees.any { it.contains("Alice 说") })
        assertFalse(caraSees.any { it.contains("Alice 说") })
    }

    @Test
    fun `roundtable chair sees the round and vote uses majority`() {
        val plan = GroupChat.plan(config(GroupChat.MODE_ROUNDTABLE), emptyList())
        assertEquals("c", plan.last().role.id)
        assertTrue(plan.last().chairRound)
        val messages = listOf(
            UIMessage.user("选题"),
            UIMessage.assistant("甲").copy(roleId = "a"),
            UIMessage.assistant("乙").copy(roleId = "b"),
        )
        val chairSees = GroupChat.visibleMessages(
            config(GroupChat.MODE_ROUNDTABLE),
            messages,
            "c",
            chairRound = true,
        ).map { it.toText() }
        assertTrue(chairSees.any { it.contains("甲") })
        assertTrue(chairSees.any { it.contains("乙") })
        // 投票改成结构化选票：正文里的 `VOTE:<候选id>|<理由>` 才是票，候选集外的行不算票。
        val candidates = listOf("甲", "乙")
        assertNull(GroupChat.parseBallot("VOTE:丙|不在候选集", "a", candidates))
        val ballots = listOf("a" to "甲", "b" to "甲", "c" to "乙")
            .mapNotNull { (roleId, picked) -> GroupChat.parseBallot("VOTE:$picked", roleId, candidates) }
        val decided = GroupChat.tally(ballots, candidates, GroupChat.TIE_FAIL)
        assertTrue(decided is VoteOutcome.Decided)
        assertEquals("甲", (decided as VoteOutcome.Decided).winner)
        // 甲乙各一票：没有多数，平票按 fail 判本轮失败，不产出胜者。
        val tied = GroupChat.tally(
            listOf(VoteBallot("a", "甲"), VoteBallot("b", "乙")),
            candidates,
            GroupChat.TIE_FAIL,
        )
        assertTrue(tied is VoteOutcome.Tie)
        assertEquals(listOf("甲", "乙"), (tied as VoteOutcome.Tie).candidates)
    }

    @Test
    fun `budget stops later speakers and keeps earlier output`() {
        val remaining = listOf("c")
        assertEquals(
            RoundBudget.Continue,
            GroupChat.budgetDecision(spent = 0, limit = 100, remainingRoleIds = remaining),
        )
        assertEquals(
            RoundBudget.Continue,
            GroupChat.budgetDecision(spent = 80, limit = 100, remainingRoleIds = remaining),
        )
        val stopped = GroupChat.budgetDecision(spent = 100, limit = 100, remainingRoleIds = remaining)
        assertTrue(stopped is RoundBudget.Stop)
        assertEquals(remaining, (stopped as RoundBudget.Stop).skippedRoleIds)
        // 新契约把 limit<=0 视为「不限预算」而不是「立刻停」；这种配置由 validate 直接拒收。
        assertEquals(
            RoundBudget.Continue,
            GroupChat.budgetDecision(spent = 0, limit = 0, remainingRoleIds = remaining),
        )
        assertTrue(
            GroupChat.validate(config(GroupChat.MODE_PIPELINE).copy(tokenBudgetPerRound = 0))
                .any { it.field == "token_budget_per_round" },
        )
        assertEquals(listOf("c"), GroupChat.pendingSpeakers(
            GroupChat.plan(config(GroupChat.MODE_PIPELINE), emptyList()),
            setOf("a", "b"),
        ).map { it.role.id })
    }

    @Test
    fun `qr round trip and per role memory spaces`() {
        val config = config(GroupChat.MODE_PIPELINE).copy(tokenBudgetPerRound = 400)
        val restored = GroupChat.decodeQr(GroupChat.encodeQr(config))
        assertEquals(config, restored)
        assertNull(GroupChat.decodeQr("""{"kind":"other"}"""))
        val spaces = roles.map { GroupChat.memorySpaceId("conv-1", it.id) }
        assertEquals(3, spaces.toSet().size)
        assertTrue(spaces.all { it.startsWith("group:conv-1:role:") })
    }

    @Test
    fun `list filter keeps the other type in the source`() {
        val source = listOf(GroupChat.TYPE_DIRECT, GroupChat.TYPE_GROUP, GroupChat.TYPE_DIRECT)
        val groups = GroupChat.filterType(source, GroupChat.TYPE_GROUP)
        assertEquals(listOf(GroupChat.TYPE_GROUP), groups)
        assertEquals(3, source.size)
        assertEquals(source, GroupChat.filterType(source, GroupChat.FILTER_ALL))
    }

    private fun config(mode: String) = GroupConfig(
        roles = roles,
        mode = mode,
        tokenBudgetPerRound = 1000,
    )
}
