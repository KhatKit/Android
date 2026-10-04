package heizige.kk.khatkit.app.core.data.repository

import heizige.kk.khatkit.app.core.data.db.entity.MemoryChunkEntity
import heizige.kk.khatkit.app.core.data.model.GroupChat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C1-M 纯 JVM 证据：检索层的**空间闸门**（跨空间泄漏的回归测试）。
 *
 * 修复前的 bug（`MemoryRepository.searchHybridWithEmbedding`）：
 * ```
 * val candidates = chunkDao.getChunksByIds(candidateIds.toList()).filter { it.deletedAt == null }
 * val hits = chunkDao.getChunksByIds(orderedIds).filter { it.deletedAt == null }
 * ```
 * 两处都只看 `deleted_at`，不看 `space_id`；而泄漏源头
 * `MemoryRepository.searchGraphIds` 里的 `graphDao.getMentionsOfEntity(name)`
 * （`memory_mentions` 无 space_id 列、查询也无空间条件）会把「共享同一实体名」的
 * 分块全空间拉进候选。后果：A 角色检索命中实体「张伟」时，B/C 角色空间乃至助手、
 * 全局空间里提到「张伟」的分块被原样返回 —— 直接违反 C1-08「三个空间互不串」。
 *
 * 本测试用纯函数 [MemorySpaceGate] 复现同一判定：图谱扩展同时返回两个空间的 chunk，
 * 过滤后必须只剩本空间那个。
 *
 * 真实 DB 往返（JOIN 真在 SQLite 里生效、role_id/space_id 真落库）需要 Android
 * 运行时（Robolectric 未接入、缓存里也没有 `org.robolectric`），只能靠 androidTest，
 * 本文件不伪造那部分证据。
 */
class MemorySpaceGateTest {

    private fun chunk(
        id: Int,
        spaceId: String,
        deletedAt: Long? = null,
        roleId: String? = null,
        sourceMessageId: String? = null,
    ) = MemoryChunkEntity(
        id = id,
        spaceId = spaceId,
        content = "事实 #$id（提到张伟）",
        sourceKind = MemoryChunkEntity.SOURCE_EXTRACTED,
        sourceMessageId = sourceMessageId,
        extractedAt = 1_700_000_000_000L,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        deletedAt = deletedAt,
        roleId = roleId,
    )

    private val aliceSpace = GroupChat.memorySpaceId("conv-1", "alice")
    private val bobSpace = GroupChat.memorySpaceId("conv-1", "bob")

    /** 核心断言：图谱扩展同时返回 A 空间的 3 与 B 空间的 7，按空间过滤后只剩 3。 */
    @Test
    fun graphExpansionReturningForeignSpaceChunkKeepsOnlyOwnSpace() {
        val rows = listOf(chunk(3, aliceSpace), chunk(7, bobSpace))
        val graphExpandedIds = listOf(3, 7)

        assertEquals(listOf(3), MemorySpaceGate.retain(graphExpandedIds, rows, aliceSpace))
        assertEquals(listOf(7), MemorySpaceGate.retain(graphExpandedIds, rows, bobSpace))
        assertEquals(listOf(chunk(3, aliceSpace)), MemorySpaceGate.filter(rows, aliceSpace))
        assertNotEquals(aliceSpace, bobSpace)
    }

    /** 助手空间 / 全局空间里的同实体分块同样不能被群角色检索捞到。 */
    @Test
    fun globalAndAssistantSpacesAreNotReachableFromGroupSpace() {
        val rows = listOf(
            chunk(3, aliceSpace),
            chunk(7, MemoryRepository.GLOBAL_MEMORY_ID),
            chunk(9, "0198-assistant-uuid"),
        )
        assertEquals(listOf(3), MemorySpaceGate.retain(listOf(3, 7, 9), rows, aliceSpace))
        assertEquals(listOf(chunk(3, aliceSpace)), MemorySpaceGate.filter(rows, aliceSpace))
    }

    /** 三个角色空间两两不串（C1-08 的验收口径）。 */
    @Test
    fun threeRoleSpacesAreMutuallyIsolated() {
        val spaces = listOf("alice", "bob", "carol").map { GroupChat.memorySpaceId("conv-1", it) }
        val rows = listOf(chunk(3, spaces[0]), chunk(7, spaces[1]), chunk(11, spaces[2]))
        val allIds = rows.map { it.id }

        assertEquals(listOf(listOf(3), listOf(7), listOf(11)), spaces.map { MemorySpaceGate.retain(allIds, rows, it) })
        assertEquals(3, spaces.distinct().size)
    }

    /** 跨群也不串：同一个 roleId 在不同会话里是不同的空间。 */
    @Test
    fun sameRoleInAnotherConversationIsADifferentSpace() {
        val conv1 = GroupChat.memorySpaceId("conv-1", "alice")
        val conv2 = GroupChat.memorySpaceId("conv-2", "alice")
        val rows = listOf(chunk(3, conv1), chunk(4, conv2))
        assertEquals(listOf(3), MemorySpaceGate.retain(listOf(3, 4), rows, conv1))
        assertEquals(listOf(4), MemorySpaceGate.retain(listOf(3, 4), rows, conv2))
    }

    /** 软删（遗忘）在本空间内也必须被挡掉——闸门不能只做空间、不做 deleted_at。 */
    @Test
    fun softDeletedChunkIsDroppedEvenInItsOwnSpace() {
        val rows = listOf(chunk(3, aliceSpace), chunk(4, aliceSpace, deletedAt = 1_700_000_100_000L))
        assertEquals(listOf(3), MemorySpaceGate.retain(listOf(3, 4), rows, aliceSpace))
        assertEquals(listOf(chunk(3, aliceSpace)), MemorySpaceGate.filter(rows, aliceSpace))
        assertFalse(MemorySpaceGate.allows(rows[1], aliceSpace))
        assertTrue(MemorySpaceGate.allows(rows[0], aliceSpace))
    }

    /** 查不到行的 id 一并丢掉：无法证明空间归属的结果宁可不返回。 */
    @Test
    fun idsWithoutRowAreDropped() {
        val rows = listOf(chunk(3, aliceSpace))
        assertEquals(listOf(3), MemorySpaceGate.retain(listOf(3, 99), rows, aliceSpace))
        assertEquals(emptyList<Int>(), MemorySpaceGate.retain(listOf(99), rows, aliceSpace))
        assertEquals(emptyList<Int>(), MemorySpaceGate.retain(emptyList(), rows, aliceSpace))
    }

    /**
     * `spaceId = null`（全库检索）只丢软删，不做空间限制——这是保留的旧语义，
     * 群聊路径不允许走到这里（见 [requireSpace] 与 `searchHybridInSpace`）。
     */
    @Test
    fun nullSpaceIdOnlyDropsDeletedChunks() {
        val rows = listOf(chunk(3, aliceSpace), chunk(7, bobSpace), chunk(9, aliceSpace, deletedAt = 1L))
        assertEquals(listOf(chunk(3, aliceSpace), chunk(7, bobSpace)), MemorySpaceGate.filter(rows, null))
    }

    /** 契约路径要求显式空间键：空键直接失败，不静默退化成全库检索。 */
    @Test
    fun requireSpaceRejectsNullAndBlank() {
        assertEquals("group:conv-1:role:alice", MemorySpaceGate.requireSpace("group:conv-1:role:alice"))
        listOf(null, "", "   ").forEach { bad ->
            val error = runCatching { MemorySpaceGate.requireSpace(bad) }.exceptionOrNull()
            assertTrue("spaceId=$bad 应该被拒绝", error is IllegalArgumentException)
        }
    }

    /**
     * `source_message_id == null` 的记忆按 [GroupChat.filterMemoryForViewer] 的口径放行
     * —— 这是模型主动 `memory_add`（来源无法归因）的情形。放行不等于泄漏：群聊隔离靠
     * 空间键 + `role_id`，这一列只管「来源消息对 viewer 是否可见」。
     */
    @Test
    fun memoryWithoutSourceMessageIdPassesViewerFilter() {
        val memories = listOf(
            chunk(3, aliceSpace, roleId = "alice", sourceMessageId = null),
            chunk(7, aliceSpace, roleId = "alice", sourceMessageId = "msg-bob-only"),
        )
        val byKey = memories.associateBy { it.id.toString() }
        val visible = GroupChat.filterMemoryForViewer(
            viewerId = "alice",
            viewerMessageIds = setOf("msg-alice-1"),
            sourceMessageIdOf = { byKey[it]?.sourceMessageId },
            candidateMessageIds = byKey.keys.toList(),
        ).toSet()
        assertEquals(setOf("3"), visible)
    }
}