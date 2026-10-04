package heizige.kk.khatkit.app.core.data.db

import heizige.kk.khatkit.app.core.data.db.entity.MemoryChunkEntity
import heizige.kk.khatkit.app.core.data.repository.toModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * C1-D 纯 JVM 证据：`memory_chunks.role_id` 一路带到 `AssistantMemory.roleId`。
 *
 * 契约：「群聊记忆空间键固定 `group:<conversationId>:role:<roleId>`」「群消息写入记忆时
 * 带 `source_message_id` 与 `role_id`，检索结果再次经过 viewer 过滤」。
 * 过滤发生在检索层，而检索层拿到的 `AssistantMemory` 必须在 `role_id` 丢失前就带上角色，
 * 否则上层无从判越权——这条映射断了，C 包的过滤就只能退化成「按空间键兜底」。
 *
 * 真实 DB 往返（role_id 真的落库、真的读回）见 `Migration_30_31_Test`（androidTest）。
 */
class MemoryRoleIdMappingTest {

    private fun chunk(spaceId: String, roleId: String?, sourceMessageId: String?) = MemoryChunkEntity(
        spaceId = spaceId,
        content = "内容",
        sourceKind = MemoryChunkEntity.SOURCE_MESSAGE,
        sourceMessageId = sourceMessageId,
        confidence = 0.8f,
        extractedAt = 1_700_000_000_000L,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        roleId = roleId,
    )

    @Test
    fun groupMemoryCarriesRoleIdAndSourceMessageId() {
        val model = chunk(
            spaceId = "group:conv-1:role:r2",
            roleId = "r2",
            sourceMessageId = "msg-1",
        ).toModel()
        assertEquals("r2", model.roleId)
        assertEquals("msg-1", model.sourceMessageId)
        assertEquals("group:conv-1:role:r2", model.spaceId)
    }

    @Test
    fun legacyDirectChatMemoryStillMapsToNullRoleId() {
        // 单聊 / 全局 / 助手空间没有「发言角色」，存量行迁移后 role_id 为 NULL
        val model = chunk(spaceId = "__global__", roleId = null, sourceMessageId = "msg-2").toModel()
        assertNull(model.roleId)
        assertEquals("msg-2", model.sourceMessageId)
    }
}
