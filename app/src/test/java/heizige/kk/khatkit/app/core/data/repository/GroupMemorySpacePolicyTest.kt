package heizige.kk.khatkit.app.core.data.repository

import heizige.kk.khatkit.app.core.data.model.GroupChat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C1-M 纯 JVM 证据：群聊记忆空间是「**首次发言懒创建**」。
 *
 * 契约：「记忆空间键固定 `group:<conversationId>:role:<roleId>`，**首次发言懒创建**」。
 *
 * 修复前 `ChatManager.handleMessageComplete` 在每轮发言前 `groupConfig.roles.forEach`
 * 一次建**全部**角色的空间（`GroupChatPage.ensureSpaces` 打开页面时也建一遍）：从没说过
 * 一句话的角色也会在库里留下空间，「空间是否存在」泄露成员是否开过口。
 *
 * 这里断言 [GroupMemorySpacePolicy]：只为**本轮发言角色**返回空间键。
 */
class GroupMemorySpacePolicyTest {

    private val conversationId = "conv-1"

    /** 核心断言：alice 发言，只有 alice 的空间被建，bob / carol 的空间不存在。 */
    @Test
    fun onlyTheSpeakingRoleSpaceIsProvisioned() {
        val spaces = GroupMemorySpacePolicy.spacesToProvision(conversationId, "alice")
        assertEquals(listOf("group:conv-1:role:alice"), spaces)
        assertTrue(spaces.none { it.contains("bob") })
        assertTrue(spaces.none { it.contains("carol") })
    }

    /** 三个角色依次发言，空间按发言顺序一个个出现（而不是一次性全建）。 */
    @Test
    fun spacesAppearOneByOneAsRolesSpeak() {
        val provisionOrder = listOf("alice", "bob", "carol").map { roleId ->
            GroupMemorySpacePolicy.spacesToProvision(conversationId, roleId).single()
        }
        assertEquals(
            listOf("group:conv-1:role:alice", "group:conv-1:role:bob", "group:conv-1:role:carol"),
            provisionOrder,
        )
        assertEquals(3, provisionOrder.distinct().size)
    }

    /** 重复发言的角色拿到的还是同一个键（幂等，ensureSpace 不会重复插）。 */
    @Test
    fun repeatedTurnsOfSameRoleResolveToTheSameSpace() {
        val first = GroupMemorySpacePolicy.spacesToProvision(conversationId, "alice")
        val second = GroupMemorySpacePolicy.spacesToProvision(conversationId, "alice")
        assertEquals(first, second)
        assertEquals(GroupChat.memorySpaceId(conversationId, "alice"), first.single())
    }

    /** 单聊（没有发言角色）不建任何群空间。 */
    @Test
    fun directChatProvisionsNothing() {
        assertEquals(emptyList<String>(), GroupMemorySpacePolicy.spacesToProvision(conversationId, null))
    }

    /** 角色 id 缺失时不猜空间（空键会被 GroupChat.validate 拒掉，这里也不产出垃圾键）。 */
    @Test
    fun blankRoleIdProvisionsNothing() {
        listOf("", "   ").forEach { blank ->
            assertEquals(emptyList<String>(), GroupMemorySpacePolicy.spacesToProvision(conversationId, blank))
        }
        assertEquals(emptyList<String>(), GroupMemorySpacePolicy.spacesToProvision("", "alice"))
    }

    /** 键格式与契约一致，且带群前缀（`MemorySpaceEntity.kindOf` 认得出是群空间）。 */
    @Test
    fun provisionedKeyMatchesContractFormat() {
        val space = GroupMemorySpacePolicy.spacesToProvision(conversationId, "alice").single()
        assertEquals(GroupChat.memorySpaceId(conversationId, "alice"), space)
        assertTrue(space.startsWith("group:"))
    }
}