package heizige.kk.khatkit.app.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import heizige.kk.khatkit.app.core.data.db.entity.GroupRunEntity
import kotlinx.coroutines.flow.Flow

/**
 * C1-D 群聊轮次运行记录（run token）读写。
 *
 * 幂等用法（契约要求「同一群同一 round_id 只允许一个运行实例」）：
 * 1. 先 [findByRound] 查 `(conversationId, roundId)`；
 * 2. 不存在或旧记录已处于终态时才 [insert]（`OnConflictStrategy.ABORT`，
 *    唯一索引冲突会直接抛 `SQLiteConstraintException`，不静默覆盖）；
 * 3. 插入成功（run token 已持久化）之后才允许发起模型调用；
 * 4. 重试沿用同一 `roundId`，读 [GroupRunEntity.committedRoleIds] 跳过已提交 turn。
 */
@Dao
interface GroupRunDAO {
    @Query("SELECT COUNT(*) FROM group_runs")
    suspend fun count(): Int

    /**
     * 抢占 run token。`ABORT` 是刻意的：唯一索引 `conversation_id + round_id`
     * 冲突时抛异常，由上层判定「本轮已有运行实例」而不是覆盖既有记录。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(run: GroupRunEntity)

    @Query("SELECT * FROM group_runs WHERE id = :id")
    suspend fun getById(id: String): GroupRunEntity?

    @Query("SELECT * FROM group_runs WHERE conversation_id = :conversationId AND round_id = :roundId LIMIT 1")
    suspend fun findByRound(conversationId: String, roundId: String): GroupRunEntity?

    @Query("SELECT * FROM group_runs WHERE conversation_id = :conversationId AND round_id = :roundId LIMIT 1")
    fun observeByRound(conversationId: String, roundId: String): Flow<GroupRunEntity?>

    @Query("SELECT * FROM group_runs WHERE conversation_id = :conversationId AND status = :status LIMIT 1")
    suspend fun findByRoundAndStatus(
        conversationId: String,
        roundId: String,
        status: String,
    ): GroupRunEntity?

    /** 运行日志 UI：按群倒序列出历史运行。 */
    @Query("SELECT * FROM group_runs WHERE conversation_id = :conversationId ORDER BY started_at DESC LIMIT :limit")
    suspend fun listByConversation(conversationId: String, limit: Int): List<GroupRunEntity>

    @Query("SELECT * FROM group_runs WHERE conversation_id = :conversationId ORDER BY started_at DESC LIMIT :limit")
    fun observeByConversation(conversationId: String, limit: Int): Flow<List<GroupRunEntity>>

    @Query("UPDATE group_runs SET status = :status, error_message = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, errorMessage: String?)

    @Query("UPDATE group_runs SET status = :status, ended_at = :endedAt, error_message = :errorMessage WHERE id = :id")
    suspend fun finish(id: String, status: String, endedAt: Long, errorMessage: String?)

    /** 预算截断落日志：已用 / 上限 / 未运行角色 / 超预算原因。 */
    @Query(
        """
        UPDATE group_runs
        SET spent_tokens = :spent, limit_tokens = :limit,
            skipped_role_ids = :skippedRoleIds, reason = :reason
        WHERE id = :id
        """
    )
    suspend fun updateBudget(
        id: String,
        spent: Int,
        limit: Int,
        skippedRoleIds: List<String>,
        reason: String?,
    )

    /** 单角色 token 累加（同一轮内递增计数，不跨轮挪用）。 */
    @Query("UPDATE group_runs SET spent_tokens = :spent WHERE id = :id")
    suspend fun updateSpentTokens(id: String, spent: Int)

    /** 重试跳过的依据：已成功提交的 turn 角色集合。 */
    @Query("UPDATE group_runs SET committed_role_ids = :committedRoleIds WHERE id = :id")
    suspend fun updateCommittedRoles(id: String, committedRoleIds: List<String>)

    @Query("DELETE FROM group_runs WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 归档：清掉某群已结束的历史运行，保留 RUNNING（可能仍在跑）。 */
    @Query("DELETE FROM group_runs WHERE conversation_id = :conversationId AND status != :runningStatus")
    suspend fun deleteFinishedOfConversation(conversationId: String, runningStatus: String)

    /** 归档：清理早于给定时间戳的已结束运行。 */
    @Query("DELETE FROM group_runs WHERE started_at < :before AND status != :runningStatus")
    suspend fun deleteFinishedBefore(before: Long, runningStatus: String)

    /** 进程被杀后残留的僵尸 RUNNING 记录（启动时回收，避免永久占位）。 */
    @Query("SELECT * FROM group_runs WHERE status = :status ORDER BY started_at ASC")
    suspend fun listByStatus(status: String): List<GroupRunEntity>

    @Query("DELETE FROM group_runs WHERE status = :status")
    suspend fun deleteByStatus(status: String)
}
