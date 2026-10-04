package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.ai.tavern.TavernChatCodec
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GroupSpeakerResolver] 的用例。
 *
 * 这个解析器决定群聊气泡上方「谁在说 / 这是什么性质」，一旦算错，界面上就会出现
 * 「爱丽丝的话挂在鲍勃名下」「错误小结看着像正常发言」这类没法自证对错的错，所以口径全部
 * 在这里钉死：单聊不受影响、合成小结、议长裁决、错误节点（两种 roleId 形状）、旧数据降级、
 * `mention_role_ids` 不得被当成说话者、以及显示名与 `displayNameOf` / 酒馆导出逐字一致。
 */
class GroupSpeakerResolverTest {

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private val alice = GroupRole(id = "alice", name = "爱丽丝", assistantId = "asst-1")
    private val bob = GroupRole(id = "bob", name = "鲍勃", assistantId = "asst-2")
    private val chair = GroupRole(id = "chair", name = "议长", assistantId = "asst-4", chair = true)

    private fun config(roles: List<GroupRole> = listOf(alice, bob, chair)) = GroupConfig(
        roles = roles,
        mode = GroupChat.MODE_PIPELINE,
        chairRoleId = chair.id,
        tokenBudgetPerRound = 1000,
    )

    private fun speakerOf(message: UIMessage, config: GroupConfig?): GroupSpeakerIdentity.Group =
        GroupSpeakerResolver.resolve(message, config) as GroupSpeakerIdentity.Group

    // ------------------------------------------------------------------
    // 用例
    // ------------------------------------------------------------------

    @Test
    fun `single chat message resolves to NotGroup`() {
        val message = UIMessage.assistant("在吗").copy(modelId = null)

        assertEquals(
            GroupSpeakerIdentity.NotGroup,
            GroupSpeakerResolver.resolve(message, config()),
        )
        // 群聊里用户自己那条消息同样没有 role_id：不是群身份，界面保持单聊渲染。
        assertEquals(
            GroupSpeakerIdentity.NotGroup,
            GroupSpeakerResolver.resolve(UIMessage.user("大家好"), config()),
        )
    }

    @Test
    fun `group speaker resolves role name and assistant id`() {
        val speaker = speakerOf(
            UIMessage.assistant("我先说").copy(roleId = "alice", turnKind = GroupChat.TURN_SPEAKER),
            config(),
        )

        assertEquals("alice", speaker.roleId)
        assertEquals("爱丽丝", speaker.displayName)
        assertEquals("asst-1", speaker.assistantId)
        assertEquals(GroupChat.TURN_SPEAKER, speaker.turnKind)
        assertFalse(speaker.isSummary)
        assertFalse(speaker.isError)
        assertFalse(speaker.isChair)
        assertNull(speaker.badge)
        assertFalse(speaker.degraded)
    }

    @Test
    fun `vote summary is recognised as group summary`() {
        val speaker = speakerOf(
            UIMessage.assistant("[多数决] 方案 B").copy(
                roleId = GroupChat.SUMMARY_ID,
                turnKind = GroupChat.TURN_VOTE_SUMMARY,
            ),
            config(),
        )

        assertTrue(speaker.isSummary)
        assertFalse(speaker.isError)
        assertNull(speaker.assistantId)
        assertEquals(GroupChat.SUMMARY_ID, speaker.roleId)
        // 合成节点没有角色名，也拿不到助手头像。
        assertEquals(GroupSpeakerResolver.SUMMARY_DISPLAY_NAME, speaker.displayName)
        assertEquals(GroupSpeakerResolver.BADGE_SUMMARY, speaker.badge)
    }

    @Test
    fun `chair turn kind is recognised as chair speech`() {
        val speaker = speakerOf(
            UIMessage.assistant("我来裁决").copy(roleId = "alice", turnKind = GroupChat.TURN_CHAIR),
            config(),
        )

        assertTrue(speaker.isChair)
        assertFalse(speaker.isSummary)
        assertFalse(speaker.isError)
        assertEquals(GroupSpeakerResolver.BADGE_CHAIR, speaker.badge)
        // 议长裁决仍然是那个角色说的话，名字与助手不能丢。
        assertEquals("爱丽丝", speaker.displayName)
        assertEquals("asst-1", speaker.assistantId)
    }

    @Test
    fun `error turn kind is recognised on both role id shapes`() {
        // 形状一：ChatManager.voteFailureNode —— 失败摘要也写成 SUMMARY_ID。
        val onSummary = speakerOf(
            UIMessage.assistant("[投票] 本轮未能得出结论").copy(
                roleId = GroupChat.SUMMARY_ID,
                turnKind = GroupChat.TURN_ERROR,
            ),
            config(),
        )
        assertTrue(onSummary.isError)
        assertTrue(onSummary.isSummary)
        assertEquals(GroupSpeakerResolver.BADGE_ERROR, onSummary.badge)

        // 形状二：GroupTurnCoordinator 的发言失败节点 —— 用出错角色自己的 id。
        val onRole = speakerOf(
            UIMessage.assistant("[爱丽丝] 本轮生成失败").copy(
                roleId = "alice",
                turnKind = GroupChat.TURN_ERROR,
            ),
            config(),
        )
        assertTrue(onRole.isError)
        assertFalse(onRole.isSummary)
        assertEquals(GroupSpeakerResolver.BADGE_ERROR, onRole.badge)
        assertEquals("爱丽丝", onRole.displayName)
    }

    @Test
    fun `role id without group config degrades instead of crashing`() {
        // 旧数据 / 群配置被清：role_id 还在，配置没了。
        val speaker = speakerOf(
            UIMessage.assistant("历史发言").copy(roleId = "alice", turnKind = GroupChat.TURN_SPEAKER),
            config = null,
        )

        assertEquals("alice", speaker.roleId)
        // 降级到裸 role_id，而不是空串或抛异常。
        assertEquals("alice", speaker.displayName)
        assertTrue(speaker.displayName.isNotBlank())
        assertNull(speaker.assistantId)
        assertTrue(speaker.degraded)

        // 配了群配置但角色已被删，同样降级到裸 id。
        val ghost = speakerOf(
            UIMessage.assistant("孤儿发言").copy(roleId = "ghost", turnKind = GroupChat.TURN_SPEAKER),
            config(),
        )
        assertEquals("ghost", ghost.displayName)
        assertNull(ghost.assistantId)
        assertTrue(ghost.degraded)
    }

    @Test
    fun `mention role ids are never treated as the speaker`() {
        val speaker = speakerOf(
            UIMessage.assistant("@爱丽丝 @鲍勃 你怎么看").copy(
                roleId = "alice",
                turnKind = GroupChat.TURN_SPEAKER,
                mentionRoleIds = listOf("bob", "chair"),
            ),
            config(),
        )

        assertEquals("alice", speaker.roleId)
        assertEquals("爱丽丝", speaker.displayName)
        assertEquals("asst-1", speaker.assistantId)
        // 被 @ 的人不能顶替说话者，也不能因此挂上议长徽章。
        assertNull(speaker.badge)
        assertFalse(speaker.isChair)
    }

    @Test
    fun `summary display name matches displayNameOf and tavern export`() {
        val config = config()
        val speaker = speakerOf(
            UIMessage.assistant("[多数决] 方案 B").copy(
                roleId = GroupChat.SUMMARY_ID,
                turnKind = GroupChat.TURN_VOTE_SUMMARY,
            ),
            config,
        )

        // 真实角色的名字必须与 displayNameOf 逐字相同——气泡层不允许自己再算一份。
        val aliceSpeaker = speakerOf(
            UIMessage.assistant("我先说").copy(roleId = "alice", turnKind = GroupChat.TURN_SPEAKER),
            config,
        )
        assertEquals(GroupTurnCoordinator.displayNameOf(config, "alice"), aliceSpeaker.displayName)
        assertEquals(GroupTurnCoordinator.displayNameOf(config, "chair"), "议长")

        // 合成节点不在 roles 里，displayNameOf 只会回落到契约 id；显示的必须是人类可读名，
        // 且与酒馆导出用的是同一份定义（这里真跑一遍导出，锁住 D1-5 没被改回去）。
        assertEquals(GroupChat.SUMMARY_ID, GroupTurnCoordinator.displayNameOf(config, GroupChat.SUMMARY_ID))
        val exported = TavernChatCodec.exportGroup(
            nodes = listOf(
                MessageNode.of(
                    UIMessage.assistant("[多数决] 方案 B").copy(
                        roleId = GroupChat.SUMMARY_ID,
                        roundId = "r1",
                        turnKind = GroupChat.TURN_VOTE_SUMMARY,
                    )
                ),
            ),
            config = config,
            userName = "Ada",
            groupName = "三人组",
        )
        val restored = requireNotNull(TavernChatCodec.importGroup(exported))
        assertEquals(speaker.displayName, restored.messages.single().name)
    }
}