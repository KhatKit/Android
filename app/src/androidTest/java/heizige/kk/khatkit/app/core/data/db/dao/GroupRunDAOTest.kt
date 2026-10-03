package heizige.kk.khatkit.app.core.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.app.core.data.db.AppDatabase
import heizige.kk.khatkit.app.core.data.db.entity.GroupRunEntity
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * C1-D：`group_runs` 的 round-trip 与幂等行为。
 *
 * 覆盖契约三条硬要求：
 * - run token 落库后字段完全可读回（含 `List<String>` 两个 JSON 列）
 * - 同一 `conversationId + roundId` 第二次插入被唯一索引拒绝（`ABORT` 抛异常）
 * - 预算截断把「已用/上限/未运行角色/超预算原因」写进运行日志
 */
@RunWith(AndroidJUnit4::class)
class GroupRunDAOTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: GroupRunDAO

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).openHelperFactory(RequerySQLiteOpenHelperFactory()).build()
        dao = database.groupRunDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun run(
        id: String = "token-1",
        conversationId: String = "conv-1",
        roundId: String = "round-msg-1",
    ) = GroupRunEntity(
        id = id,
        conversationId = conversationId,
        roundId = roundId,
        status = GroupRunEntity.STATUS_RUNNING,
        spentTokens = 0,
        limitTokens = 4096,
        startedAt = 1_700_000_000_000L,
    )

    @Test
    fun insertedRunIsReadBackFieldByField() = runBlocking {
        val entity = run().copy(
            status = GroupRunEntity.STATUS_COMMITTED,
            spentTokens = 3210,
            skippedRoleIds = listOf("r2", "r3"),
            reason = GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED,
            committedRoleIds = listOf("r1"),
            errorMessage = null,
            startedAt = 1_700_000_000_001L,
            endedAt = 1_700_000_009_999L,
        )
        dao.insert(entity)

        val loaded = dao.findByRound("conv-1", "round-msg-1")
        assertNotNull("run token 落库后必须能按 (conversationId, roundId) 读回", loaded)
        assertEquals(entity, loaded)
        assertEquals(listOf("r2", "r3"), loaded!!.skippedRoleIds)
        assertEquals(listOf("r1"), loaded.committedRoleIds)
        assertEquals(GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED, loaded.reason)
        assertNull(loaded.errorMessage)
        assertEquals(entity.hashCode(), loaded.hashCode())
    }

    @Test
    fun emptyListsRoundTripAsEmptyNotNull() = runBlocking {
        dao.insert(run())
        val loaded = dao.getById("token-1")!!
        assertEquals(emptyList<String>(), loaded.committedRoleIds)
        assertEquals(emptyList<String>(), loaded.skippedRoleIds)
        assertNull(loaded.endedAt)
    }

    @Test
    fun secondRunOfSameRoundIsRejectedByUniqueIndex() = runBlocking {
        dao.insert(run(id = "token-a"))

        var thrown: Throwable? = null
        try {
            dao.insert(run(id = "token-b"))
        } catch (e: Throwable) {
            thrown = e
        }
        assertNotNull("同一 conversationId + roundId 必须拒绝第二条 run", thrown)
        assertEquals(1, dao.count())
        // 原有记录未被覆盖，第二条根本没有落库
        assertNotNull(dao.getById("token-a"))
        assertNull(dao.getById("token-b"))
        assertEquals(GroupRunEntity.STATUS_RUNNING, dao.getById("token-a")!!.status)
    }

    @Test
    fun differentConversationOrRoundBothAllowed() = runBlocking {
        dao.insert(run(id = "token-a", conversationId = "conv-1", roundId = "round-1"))
        dao.insert(run(id = "token-b", conversationId = "conv-2", roundId = "round-1"))
        dao.insert(run(id = "token-c", conversationId = "conv-1", roundId = "round-2"))
        assertEquals(3, dao.count())
    }

    @Test
    fun budgetStopIsRecordedInRunLog() = runBlocking {
        dao.insert(run())

        dao.updateSpentTokens("token-1", 1800)
        dao.updateCommittedRoles("token-1", listOf("r1"))
        dao.updateBudget(
            id = "token-1",
            spent = 4096,
            limit = 4096,
            skippedRoleIds = listOf("r2", "r3"),
            reason = GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED,
        )
        dao.finish("token-1", GroupRunEntity.STATUS_COMMITTED, 1_700_000_009_000L, null)

        val loaded = dao.getById("token-1")!!
        assertEquals(GroupRunEntity.STATUS_COMMITTED, loaded.status)
        assertEquals(4096, loaded.spentTokens)
        assertEquals(4096, loaded.limitTokens)
        assertEquals(listOf("r2", "r3"), loaded.skippedRoleIds)
        assertEquals(listOf("r1"), loaded.committedRoleIds)
        assertEquals(GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED, loaded.reason)
        assertEquals(1_700_000_009_000L, loaded.endedAt)
        assertTrue(GroupRunEntity.isTerminal(loaded.status))
    }

    @Test
    fun failureKeepsCommittedRolesForRetryResume() = runBlocking {
        dao.insert(run())
        dao.updateCommittedRoles("token-1", listOf("r1"))
        dao.updateStatus("token-1", GroupRunEntity.STATUS_FAILED, "r2 请求超时")
        dao.finish("token-1", GroupRunEntity.STATUS_FAILED, 1_700_000_009_000L, "r2 请求超时")

        val loaded = dao.getById("token-1")!!
        assertEquals(GroupRunEntity.STATUS_FAILED, loaded.status)
        assertEquals(listOf("r1"), loaded.committedRoleIds)
        assertEquals("r2 请求超时", loaded.errorMessage)

        // 重试沿用同一 roundId，读回即可跳过已提交角色
        val retried = dao.findByRound("conv-1", "round-msg-1")!!
        assertEquals(listOf("r1"), retried.committedRoleIds)
    }

    @Test
    fun historyIsListedNewestFirstPerConversation() = runBlocking {
        dao.insert(run(id = "t1", conversationId = "conv-1", roundId = "round-1").copy(startedAt = 100))
        dao.insert(run(id = "t2", conversationId = "conv-1", roundId = "round-2").copy(startedAt = 300))
        dao.insert(run(id = "t3", conversationId = "conv-1", roundId = "round-3").copy(startedAt = 200))
        dao.insert(run(id = "t4", conversationId = "conv-2", roundId = "round-1").copy(startedAt = 999))

        assertEquals(
            listOf("t2", "t3", "t1"),
            dao.listByConversation("conv-1", 10).map { it.id },
        )
        assertEquals(listOf("t2"), dao.listByConversation("conv-1", 1).map { it.id })
    }

    @Test
    fun archivingKeepsRunningRecords() = runBlocking {
        dao.insert(run(id = "done", roundId = "round-1").copy(status = GroupRunEntity.STATUS_COMMITTED))
        dao.insert(run(id = "live", roundId = "round-2"))

        dao.deleteFinishedOfConversation("conv-1", GroupRunEntity.STATUS_RUNNING)

        assertEquals(listOf("live"), dao.listByConversation("conv-1", 10).map { it.id })
    }

    @Test
    fun staleRunningRecordsCanBeReclaimed() = runBlocking {
        dao.insert(run(id = "zombie-a", roundId = "round-1"))
        dao.insert(run(id = "zombie-b", roundId = "round-2"))

        assertEquals(
            listOf("zombie-a", "zombie-b"),
            dao.listByStatus(GroupRunEntity.STATUS_RUNNING).map { it.id },
        )
        dao.deleteByStatus(GroupRunEntity.STATUS_RUNNING)
        assertEquals(0, dao.count())
    }
}
