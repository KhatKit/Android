package heizige.kk.khatkit.app.core.data.db.entity

import heizige.kk.khatkit.app.core.data.model.AssistantMemory
import heizige.kk.khatkit.app.core.util.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * C1-D：`group_runs` 的 `List<String>` 列转换与 `AssistantMemory.roleId` 的纯 JVM 往返。
 *
 * 这部分不依赖 Android 运行时，因此可以在 `:app:testDebugUnitTest` 里真实执行，
 * 覆盖 round-trip 证据里「含 List<String> 字段的实体读写完全相等」的转换层；
 * 实体级 round-trip 与唯一索引由 `GroupRunDAOTest` / `Migration_30_31_Test`
 * （instrumentation）覆盖。
 */
class StringListConverterTest {

    @Test
    fun committedAndSkippedRoleIdsRoundTripExactly() {
        val value = listOf("role-a", "role-b", "role-c")
        val column = StringListConverter.fromStringList(value)
        assertEquals(value, StringListConverter.toStringList(column))
    }

    @Test
    fun columnIsJsonArraySoItStaysReadableInRawSql() {
        assertEquals("""["r1","r2"]""", StringListConverter.fromStringList(listOf("r1", "r2")))
        assertEquals("[]", StringListConverter.fromStringList(emptyList()))
    }

    @Test
    fun nullAndBlankDegradeToEmptyListInsteadOfCrashing() {
        assertEquals(emptyList<String>(), StringListConverter.toStringList(null))
        assertEquals(emptyList<String>(), StringListConverter.toStringList(""))
        assertEquals(emptyList<String>(), StringListConverter.toStringList("   "))
        // 脏数据（旧库 / 人工改库）不得让打开即崩
        assertEquals(emptyList<String>(), StringListConverter.toStringList("not-json"))
        assertEquals(emptyList<String>(), StringListConverter.fromStringList(null).let {
            StringListConverter.toStringList(it)
        })
    }

    @Test
    fun unicodeRoleIdsSurviveRoundTrip() {
        val value = listOf("角色-甲", "role with space", "emoji-🙂")
        assertEquals(value, StringListConverter.toStringList(StringListConverter.fromStringList(value)))
    }

    @Test
    fun assistantMemoryRoleIdIsOptionalAndDefaultsToNull() {
        val legacy = AssistantMemory(id = 1, content = "旧记忆")
        assertNull(legacy.roleId)

        val group = legacy.copy(spaceId = "group:c1:role:r1", roleId = "r1")
        val restored = JsonInstant.decodeFromString<AssistantMemory>(
            JsonInstant.encodeToString(group)
        )
        assertEquals(group, restored)
        assertEquals("r1", restored.roleId)
    }

    @Test
    fun legacyAssistantMemoryJsonWithoutRoleIdStillDecodes() {
        val legacyJson =
            """{"id":7,"content":"存量记忆","spaceId":"__global__","sourceMessageId":"m1",""" +
                """"sourceKind":"MESSAGE","confidence":0.5,"extractedAt":123}"""
        val decoded = JsonInstant.decodeFromString<AssistantMemory>(legacyJson)
        assertEquals(7, decoded.id)
        assertEquals("存量记忆", decoded.content)
        assertNull("旧 JSON 没有 roleId 时必须读成 null", decoded.roleId)
    }

    @Test
    fun groupRunStatusConstantsCoverTheContractStates() {
        // 契约要求的状态集合：RUNNING / COMPLETED / FAILED / CANCELLED / BUDGET_STOPPED，
        // 另加 TIMEOUT（「取消/超时只写运行日志」里超时是独立的终态）。
        assertEquals(
            setOf("RUNNING", "COMPLETED", "FAILED", "CANCELLED", "BUDGET_STOPPED", "TIMEOUT"),
            GroupRunEntity.ALL_STATUSES,
        )
        assertTrue(GroupRunEntity.isStaleRunning(GroupRunEntity.STATUS_RUNNING))
        listOf(
            GroupRunEntity.STATUS_COMPLETED,
            GroupRunEntity.STATUS_FAILED,
            GroupRunEntity.STATUS_CANCELLED,
            GroupRunEntity.STATUS_BUDGET_STOPPED,
            GroupRunEntity.STATUS_TIMEOUT,
        ).forEach { assertTrue("$it 应为终态", GroupRunEntity.isTerminal(it)) }
        assertTrue(!GroupRunEntity.isTerminal(GroupRunEntity.STATUS_RUNNING))
        // 兼容别名与 COMPLETED 同值，落库不会产生第二种「正常收尾」字面量
        assertEquals(GroupRunEntity.STATUS_COMPLETED, GroupRunEntity.STATUS_COMMITTED)
        // 与 GroupChat.budgetDecision 写下的超预算原因字面量保持一致
        assertEquals("token_budget_exceeded", GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED)
        assertEquals(
            GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED,
            GroupRunEntity.REASON_BUDGET_EXCEEDED,
        )
    }

    @Test
    fun groupRunDefaultsMatchTheColumnDefaults() {
        // 未显式赋值的字段必须落到 DDL 里声明的默认值上，否则 Room 校验/读回会不一致
        val run = GroupRunEntity(
            conversationId = "conv-1",
            roundId = "round-msg-1",
            runToken = "token-1",
            status = GroupRunEntity.STATUS_RUNNING,
            startedAt = 1_700_000_000_000L,
        )
        assertEquals(0, run.spentTokens)
        assertEquals(0, run.tokenLimit)
        assertEquals(emptyList<String>(), run.skippedRoleIds)
        assertEquals(emptyList<String>(), run.committedRoleIds)
        assertEquals("", run.reason)
        assertEquals("", run.errorMessage)
        assertEquals(null, run.endedAt)
        assertEquals("updated_at 默认跟随 startedAt", run.startedAt, run.updatedAt)
    }

    @Test
    fun memoryChunkRoleIdDefaultsToNullForLegacyRows() {
        val legacy = MemoryChunkEntity(
            spaceId = "__global__",
            content = "存量记忆",
            sourceKind = MemoryChunkEntity.SOURCE_MANUAL,
            extractedAt = 1L,
            createdAt = 1L,
            updatedAt = 1L,
        )
        assertNull("存量单聊记忆没有角色", legacy.roleId)

        val group = legacy.copy(spaceId = "group:c1:role:r1", roleId = "r1")
        assertEquals("r1", group.roleId)
        assertEquals(legacy, legacy.copy(roleId = null))
    }
}
