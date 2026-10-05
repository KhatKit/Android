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
 * C1-D：`group_runs` 的 round-trip 与幂等行为（真机/模拟器跑，Robolectric 不可用）。
 *
 * 覆盖契约三条硬要求：
 * - run token 落库后字段完全可读回（含 `List<String>` 两个 JSON 列）
 * - 同一 `conversationId + roundId` 第二次插入被**主键**拒绝（`ABORT` 抛异常），
 *   且同一 runToken 也不能被第二次使用
 * - 预算截断把「已用/上限/未运行角色/超预算原因」写进运行日志
 *
 * 另覆盖重试续跑：[upsertRun] 重放同一 round 时保留已提交角色集合与原 runToken。
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
        runToken: String = "token-1",
        conversationId: String = "conv-1",
        roundId: String = "round-msg-1",
    ) = GroupRunEntity(
        conversationId = conversationId,
        roundId = roundId,
        runToken = runToken,
        status = GroupRunEntity.STATUS_RUNNING,
        startedAt = 1_700_000_000_000L,
        spentTokens = 0,
        tokenLimit = 4096,
    )

    @Test
    fun insertedRunIsReadBackFieldByField() = runBlocking {
        val entity = run().copy(
            status = GroupRunEntity.STATUS_BUDGET_STOPPED,
            spentTokens = 3210,
            skippedRoleIds = listOf("r2", "r3"),
            reason = GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED,
            committedRoleIds = listOf("r1"),
            errorMessage = "",
            startedAt = 1_700_000_000_001L,
            updatedAt = 1_700_000_009_000L,
            endedAt = 1_700_000_009_999L,
        )
        dao.insert(entity)

        val loaded = dao.findByRound("conv-1", "round-msg-1")
        assertNotNull("run token 落库后必须能按 (conversationId, roundId) 读回", loaded)
        assertEquals(entity, loaded)
        assertEquals(listOf("r2", "r3"), loaded!!.skippedRoleIds)
        assertEquals(listOf("r1"), loaded.committedRoleIds)
        assertEquals(GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED, loaded.reason)
        assertEquals("token-1", loaded.runToken)
        assertEquals(entity.hashCode(), loaded.hashCode())
        // runToken 也能直接定位
        assertEquals(loaded, dao.getByRunToken("token-1"))
    }

    @Test
    fun emptyListsRoundTripAsEmptyNotNull() = runBlocking {
        dao.insert(run())
        val loaded = dao.findByRound("conv-1", "round-msg-1")!!
        assertEquals(emptyList<String>(), loaded.committedRoleIds)
        assertEquals(emptyList<String>(), loaded.skippedRoleIds)
        assertNull(loaded.endedAt)
        // 默认值与 DDL 的 DEFAULT 一致
        assertEquals(0, loaded.spentTokens)
        assertEquals("", loaded.reason)
        assertEquals("", loaded.errorMessage)
    }

    @Test
    fun secondRunOfSameRoundIsRejectedByPrimaryKey() = runBlocking {
        dao.insert(run(runToken = "token-a"))

        var thrown: Throwable? = null
        try {
            dao.insert(run(runToken = "token-b"))
        } catch (e: Throwable) {
            thrown = e
        }
        assertNotNull("同一 conversationId + roundId 必须拒绝第二条 run", thrown)
        assertEquals(1, dao.count())
        // 原有记录未被覆盖，第二条根本没有落库
        assertEquals("token-a", dao.findByRound("conv-1", "round-msg-1")!!.runToken)
        assertNull(dao.getByRunToken("token-b"))
        assertEquals(GroupRunEntity.STATUS_RUNNING, dao.findByRound("conv-1", "round-msg-1")!!.status)
    }

    @Test
    fun runTokenCannotBeReusedAcrossDifferentRounds() = runBlocking {
        // run_token 唯一索引：令牌不能被两次执行共用（哪怕轮次不同）
        dao.insert(run(runToken = "token-a", roundId = "round-1"))

        var thrown: Throwable? = null
        try {
            dao.insert(run(runToken = "token-a", roundId = "round-2"))
        } catch (e: Throwable) {
            thrown = e
        }
        assertNotNull("同一 runToken 不得用于两次执行", thrown)
        assertEquals(1, dao.count())
    }

    @Test
    fun differentConversationOrRoundBothAllowed() = runBlocking {
        dao.insert(run(runToken = "token-a", conversationId = "conv-1", roundId = "round-1"))
        dao.insert(run(runToken = "token-b", conversationId = "conv-2", roundId = "round-1"))
        dao.insert(run(runToken = "token-c", conversationId = "conv-1", roundId = "round-2"))
        assertEquals(3, dao.count())
    }

    @Test
    fun budgetStopIsRecordedInRunLog() = runBlocking {
        dao.insert(run())

        dao.updateSpentTokens("conv-1", "round-msg-1", 1800, 1_700_000_001_000L)
        dao.updateCommittedRoles("conv-1", "round-msg-1", listOf("r1"), 1_700_000_002_000L)
        dao.updateBudget(
            conversationId = "conv-1",
            roundId = "round-msg-1",
            spent = 4096,
            tokenLimit = 4096,
            skippedRoleIds = listOf("r2", "r3"),
            reason = GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED,
            status = GroupRunEntity.STATUS_BUDGET_STOPPED,
            updatedAt = 1_700_000_008_000L,
        )
        dao.finish(
            conversationId = "conv-1",
            roundId = "round-msg-1",
            status = GroupRunEntity.STATUS_BUDGET_STOPPED,
            endedAt = 1_700_000_009_000L,
            errorMessage = "",
            updatedAt = 1_700_000_009_000L,
        )

        val loaded = dao.findByRound("conv-1", "round-msg-1")!!
        assertEquals(GroupRunEntity.STATUS_BUDGET_STOPPED, loaded.status)
        assertEquals(4096, loaded.spentTokens)
        assertEquals(4096, loaded.tokenLimit)
        assertEquals(listOf("r2", "r3"), loaded.skippedRoleIds)
        assertEquals(listOf("r1"), loaded.committedRoleIds)
        assertEquals(GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED, loaded.reason)
        assertEquals(1_700_000_009_000L, loaded.endedAt)
        assertEquals(1_700_000_009_000L, loaded.updatedAt)
        assertTrue(GroupRunEntity.isTerminal(loaded.status))
    }

    @Test
    fun failureKeepsCommittedRolesForRetryResume() = runBlocking {
        dao.insert(run())
        dao.updateCommittedRoles("conv-1", "round-msg-1", listOf("r1"), 1_700_000_002_000L)
        dao.updateFailure(
            conversationId = "conv-1",
            roundId = "round-msg-1",
            reason = GroupRunEntity.REASON_ROLE_FAILED,
            errorMessage = "r2 请求超时",
            updatedAt = 1_700_000_003_000L,
        )
        dao.finish(
            conversationId = "conv-1",
            roundId = "round-msg-1",
            status = GroupRunEntity.STATUS_FAILED,
            endedAt = 1_700_000_009_000L,
            errorMessage = "r2 请求超时",
            updatedAt = 1_700_000_009_000L,
        )

        val loaded = dao.findByRound("conv-1", "round-msg-1")!!
        assertEquals(GroupRunEntity.STATUS_FAILED, loaded.status)
        assertEquals(listOf("r1"), loaded.committedRoleIds)
        assertEquals("r2 请求超时", loaded.errorMessage)
        assertEquals(GroupRunEntity.REASON_ROLE_FAILED, loaded.reason)

        // 重试沿用同一 roundId，读回即可跳过已提交角色
        val retried = dao.findByRound("conv-1", "round-msg-1")!!
        assertEquals(listOf("r1"), retried.committedRoleIds)
    }

    @Test
    fun upsertRunReplaysSameRoundWithoutDuplicatingRows() = runBlocking {
        val first = run(runToken = "token-a")
        dao.upsertRun(first)
        assertEquals(1, dao.count())

        // 重试同一 round：新令牌 + 已提交 r1 + 失败在 r2
        val retry = first.copy(
            runToken = "token-b",
            status = GroupRunEntity.STATUS_FAILED,
            committedRoleIds = listOf("r1"),
            reason = GroupRunEntity.REASON_ROLE_FAILED,
            errorMessage = "r2 500",
            spentTokens = 900,
            updatedAt = 1_700_000_009_000L,
            endedAt = 1_700_000_009_000L,
        )
        val stored = dao.upsertRun(retry)

        assertEquals("重试不应新增行", 1, dao.count())
        assertEquals(GroupRunEntity.STATUS_FAILED, stored.status)
        assertEquals(listOf("r1"), stored.committedRoleIds)
        assertEquals(900, stored.spentTokens)
        assertEquals("r2 500", stored.errorMessage)
        // 不可变字段保留旧行的值：runToken 仍指向第一次执行，startedAt 不被改写
        assertEquals("token-a", stored.runToken)
        assertEquals(first.startedAt, stored.startedAt)
        // 新令牌没有落库（被不可变字段保护住）
        assertNull(dao.getByRunToken("token-b"))
    }

    @Test
    fun historyIsListedNewestFirstPerConversation() = runBlocking {
        dao.insert(run(runToken = "t1", conversationId = "conv-1", roundId = "round-1").copy(startedAt = 100))
        dao.insert(run(runToken = "t2", conversationId = "conv-1", roundId = "round-2").copy(startedAt = 300))
        dao.insert(run(runToken = "t3", conversationId = "conv-1", roundId = "round-3").copy(startedAt = 200))
        dao.insert(run(runToken = "t4", conversationId = "conv-2", roundId = "round-1").copy(startedAt = 999))

        assertEquals(
            listOf("t2", "t3", "t1"),
            dao.listRecentByConversation("conv-1", 10).map { it.runToken },
        )
        assertEquals(listOf("t2"), dao.listRecentByConversation("conv-1", 1).map { it.runToken })
    }

    @Test
    fun archivingKeepsRunningRecords() = runBlocking {
        dao.insert(run(runToken = "done", roundId = "round-1").copy(status = GroupRunEntity.STATUS_COMPLETED))
        dao.insert(run(runToken = "live", roundId = "round-2"))

        dao.deleteFinishedOfConversation("conv-1")

        assertEquals(listOf("live"), dao.listRecentByConversation("conv-1", 10).map { it.runToken })
    }

    @Test
    fun archivingByAgeKeepsRunningRecords() = runBlocking {
        // `run()` 默认写死 STATUS_RUNNING，而 `deleteFinishedBefore` 的口径是
        // `status != 'RUNNING'`（GroupRunDAO.kt:222-228）。名字里带 done 的两行必须显式
        // 设成终态，才真的在测「按时间归档」；写法与上方 archivingKeepsRunningRecords 一致。
        dao.insert(
            run(runToken = "old-done", roundId = "round-1").copy(
                startedAt = 100,
                status = GroupRunEntity.STATUS_COMPLETED,
            ),
        )
        dao.insert(run(runToken = "old-live", roundId = "round-2").copy(startedAt = 150))
        dao.insert(
            run(runToken = "new-done", roundId = "round-3").copy(
                startedAt = 9_000,
                status = GroupRunEntity.STATUS_COMPLETED,
            ),
        )

        dao.deleteFinishedBefore(1_000)

        // 期望值必须写成 `sortedBy { it.runToken }` 真正产出的顺序（升序）：
        // "new-done" < "old-live"（'n' 110 < 'o' 111）。之前这里写成
        // `[old-live, new-done]`，与本行自己的排序方向相反，因此**无论 DAO 行为如何**
        // 都不可能通过——这一条是先于状态缺陷被掩盖的第二个测试自身笔误。
        // 断言强度未变：仍然是精确列表相等（存活集合 = {old-live, new-done}）。
        assertEquals(
            listOf("new-done", "old-live"),
            dao.listRecentByConversation("conv-1", 10).sortedBy { it.runToken }.map { it.runToken },
        )
    }

    @Test
    fun staleRunningRecordsCanBeReclaimed() = runBlocking {
        dao.insert(run(runToken = "zombie-a", roundId = "round-1"))
        dao.insert(run(runToken = "zombie-b", roundId = "round-2"))

        assertEquals(
            listOf("zombie-a", "zombie-b"),
            dao.listByStatus(GroupRunEntity.STATUS_RUNNING).map { it.runToken },
        )
        dao.deleteByStatus(GroupRunEntity.STATUS_RUNNING)
        assertEquals(0, dao.count())
    }
}
