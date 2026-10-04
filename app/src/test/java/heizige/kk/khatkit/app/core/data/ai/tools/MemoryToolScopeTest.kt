package heizige.kk.khatkit.app.core.data.ai.tools

import heizige.kk.khatkit.app.core.data.repository.MemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C1-M 纯 JVM 证据：群聊时记忆工具（`memory_search` / `memory_add` / `memory_link` /
 * `memory_forget`）用的是哪个记忆空间。
 *
 * 契约：「记忆空间键固定 `group:<conversationId>:role:<roleId>`，首次发言懒创建；
 * **不得回退到全局/助手空间**」「工具调用、检索与记忆注入均使用同一 viewer 过滤结果」。
 *
 * 修复前 `ChatToolFactory.createTools` 的签名里没有 conversationId / roleId，群空间分支
 * 完全不存在：`memoryAssistantId` 只由 `useGlobalMemory` 决定，于是群角色的
 * `memory_add` 写进的是助手空间或全局空间——与 `group:<conv>:role:<role>` 完全脱钩，
 * 还能跨群共用。
 *
 * 这里断言 [MemoryToolScopeResolver]：群聊分支拿到的是群空间键，且**没有**任何回退到
 * 全局 / 助手空间的路径；单聊分支行为与修复前逐字一致（向后兼容）。
 */
class MemoryToolScopeTest {

    private val assistantId = "0198aaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val group = GroupMemoryScope(conversationId = "conv-1", roleId = "alice")

    @Test
    fun groupChatUsesContractSpaceKey() {
        val scope = MemoryToolScopeResolver.resolve(
            useGlobalMemory = false,
            assistantId = assistantId,
            group = group,
        )
        assertEquals("group:conv-1:role:alice", scope.spaceId)
        assertEquals("alice", scope.roleId)
        assertEquals(GroupMemoryScope("conv-1", "alice").let { "group:${it.conversationId}:role:${it.roleId}" }, scope.spaceId)
    }

    /** 开了全局记忆也不能把群角色带回全局空间——这正是契约禁止的回退。 */
    @Test
    fun groupChatNeverFallsBackToGlobalOrAssistantSpace() {
        listOf(false, true).forEach { useGlobalMemory ->
            val scope = MemoryToolScopeResolver.resolve(useGlobalMemory, assistantId, group)
            assertNotEquals(MemoryRepository.GLOBAL_MEMORY_ID, scope.spaceId)
            assertNotEquals(assistantId, scope.spaceId)
            assertTrue(scope.spaceId.startsWith("group:conv-1:role:"))
        }
    }

    /** 三个角色各自的键互不相同，且都不是全局 / 助手键。 */
    @Test
    fun eachRoleGetsItsOwnSpaceKey() {
        val scopes = listOf("alice", "bob", "carol").map { roleId ->
            MemoryToolScopeResolver.resolve(false, assistantId, GroupMemoryScope("conv-1", roleId))
        }
        assertEquals(listOf("group:conv-1:role:alice", "group:conv-1:role:bob", "group:conv-1:role:carol"), scopes.map { it.spaceId })
        assertEquals(3, scopes.map { it.spaceId }.distinct().size)
        assertEquals(3, scopes.map { it.roleId }.distinct().size)
        scopes.forEach { scope ->
            assertNotEquals(MemoryRepository.GLOBAL_MEMORY_ID, scope.spaceId)
            assertNotEquals(assistantId, scope.spaceId)
        }
    }

    /** 跨群不共用：同一个 roleId 在不同会话里是不同的空间。 */
    @Test
    fun differentGroupsDoNotShareSpaces() {
        val conv1 = MemoryToolScopeResolver.resolve(false, assistantId, GroupMemoryScope("conv-1", "alice"))
        val conv2 = MemoryToolScopeResolver.resolve(false, assistantId, GroupMemoryScope("conv-2", "alice"))
        assertNotEquals(conv1.spaceId, conv2.spaceId)
    }

    /** 群空间键缺 conversationId / roleId 时直接失败，不静默换空间。 */
    @Test
    fun incompleteGroupScopeFailsInsteadOfFallingBack() {
        listOf(
            GroupMemoryScope("", "alice"),
            GroupMemoryScope("conv-1", ""),
            GroupMemoryScope("   ", "  "),
        ).forEach { bad ->
            val error = runCatching {
                MemoryToolScopeResolver.resolve(useGlobalMemory = true, assistantId = assistantId, group = bad)
            }.exceptionOrNull()
            assertTrue("group=$bad 应该失败而不是回退", error is IllegalArgumentException)
        }
    }

    /** 单聊 + 助手空间：与修复前 `assistant.id.toString()` 逐字一致。 */
    @Test
    fun directChatKeepsAssistantSpace() {
        val scope = MemoryToolScopeResolver.resolve(
            useGlobalMemory = false,
            assistantId = assistantId,
            group = null,
        )
        assertEquals(assistantId, scope.spaceId)
        assertNull(scope.roleId)
    }

    /** 单聊 + 全局记忆：与修复前 `MemoryRepository.GLOBAL_MEMORY_ID` 一致。 */
    @Test
    fun directChatKeepsGlobalSpace() {
        val scope = MemoryToolScopeResolver.resolve(
            useGlobalMemory = true,
            assistantId = assistantId,
            group = null,
        )
        assertEquals(MemoryRepository.GLOBAL_MEMORY_ID, scope.spaceId)
        assertEquals("__global__", scope.spaceId)
        assertNull(scope.roleId)
    }
}