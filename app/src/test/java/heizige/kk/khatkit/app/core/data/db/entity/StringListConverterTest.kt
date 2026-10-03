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
        assertEquals(
            setOf("RUNNING", "COMMITTED", "FAILED", "CANCELLED", "TIMEOUT"),
            GroupRunEntity.ALL_STATUSES,
        )
        assertTrue(GroupRunEntity.isStaleRunning(GroupRunEntity.STATUS_RUNNING))
        listOf(
            GroupRunEntity.STATUS_COMMITTED,
            GroupRunEntity.STATUS_FAILED,
            GroupRunEntity.STATUS_CANCELLED,
            GroupRunEntity.STATUS_TIMEOUT,
        ).forEach { assertTrue("$it 应为终态", GroupRunEntity.isTerminal(it)) }
        assertTrue(!GroupRunEntity.isTerminal(GroupRunEntity.STATUS_RUNNING))
        // 与 GroupChat.budgetDecision 写下的超预算原因字面量保持一致
        assertEquals("token_budget_exceeded", GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED)
    }
}
