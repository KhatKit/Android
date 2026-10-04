package heizige.kk.khatkit.app.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import heizige.kk.khatkit.app.core.data.db.entity.GroupRunEntity
import kotlinx.coroutines.flow.Flow

/**
 * C1-D 群聊轮次运行记录（run log）读写。
 *
 * 幂等用法（契约要求「同一 `conversationId + roundId` 的 run token 必须持久化后才可执行」）：
 * 1. 先 [findByRound] 查 `(conversationId, roundId)`；
 * 2. 不存在或旧记录已处于终态时才 [insert]（`OnConflictStrategy.ABORT`，
 *    复合主键 `conversation_id + round_id` 冲突会直接抛 `SQLiteConstraintException`，
 *    不静默覆盖既有运行日志）；
 * 3. 插入成功（run token 已持久化）之后才允许发起模型调用；
 * 4. 重试沿用同一 `roundId`，读 [GroupRunEntity.committedRoleIds] 跳过已提交 turn。
 *
 * 所有「更新」都以 `(conversationId, roundId)` 定位（= 主键），并顺带把 `updated_at`
 * 推到调用方给的时间戳，方便运行日志排查最后写入时间。
 */
@Dao
interface GroupRunDAO {
    @Query("SELECT COUNT(*) FROM group_runs")
    suspend fun count(): Int

    /**
     * 抢占 run token。`ABORT` 是刻意的：主键 `(conversation_id, round_id)` 冲突时抛异常，
     * 由上层判定「本轮已有运行实例」而不是覆盖既有记录——重复运行必须立刻暴露。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(run: GroupRunEntity)

    /** 按 runToken 取回（进程重启后确认某次执行是否已落库）。 */
    @Query("SELECT * FROM group_runs WHERE run_token = :runToken LIMIT 1")
    suspend fun getByRunToken(runToken: String): GroupRunEntity?

    @Query("SELECT * FROM group_runs WHERE conversation_id = :conversationId AND round_id = :roundId LIMIT 1")
    suspend fun findByRound(conversationId: String, roundId: String): GroupRunEntity?

    @Query("SELECT * FROM group_runs WHERE conversation_id = :conversationId AND round_id = :roundId LIMIT 1")
    fun observeByRound(conversationId: String, roundId: String): Flow<GroupRunEntity?>

    @Query(
        """
        SELECT * FROM group_runs
        WHERE conversation_id = :conversationId AND status = :status
        ORDER BY started_at ASC LIMIT :limit
        """
    )
    suspend fun listByConversationAndStatus(
        conversationId: String,
        status: String,
        limit: Int,
    ): List<GroupRunEntity>

    /**
     * 运行日志 UI / 「下一轮重新计数」：按群倒序列出最近 [limit] 轮。
     * 每轮预算是独立行，这里不做任何跨轮求和。
     */
    @Query("SELECT * FROM group_runs WHERE conversation_id = :conversationId ORDER BY started_at DESC LIMIT :limit")
    suspend fun listRecentByConversation(conversationId: String, limit: Int): List<GroupRunEntity>

    @Query("SELECT * FROM group_runs WHERE conversation_id = :conversationId ORDER BY started_at DESC LIMIT :limit")
    fun observeByConversation(conversationId: String, limit: Int): Flow<List<GroupRunEntity>>

    @Query(
        """
        UPDATE group_runs SET status = :status, error_message = :errorMessage, updated_at = :updatedAt
        WHERE conversation_id = :conversationId AND round_id = :roundId
        """
    )
    suspend fun updateStatus(
        conversationId: String,
        roundId: String,
        status: String,
        errorMessage: String,
        updatedAt: Long,
    )

    /** 收尾：写终态 + 结束时刻 + 错误信息（取消/超时/失败都走这里，只写日志不写消息）。 */
    @Query(
        """
        UPDATE group_runs SET status = :status, ended_at = :endedAt,
            error_message = :errorMessage, updated_at = :updatedAt
        WHERE conversation_id = :conversationId AND round_id = :roundId
        """
    )
    suspend fun finish(
        conversationId: String,
        roundId: String,
        status: String,
        endedAt: Long,
        errorMessage: String,
        updatedAt: Long,
    )

    /** 预算截断落日志：已用 / 上限 / 未运行角色 / 超预算原因。 */
    @Query(
        """
        UPDATE group_runs
        SET spent_tokens = :spent, token_limit = :tokenLimit,
            skipped_role_ids = :skippedRoleIds, reason = :reason,
            status = :status, updated_at = :updatedAt
        WHERE conversation_id = :conversationId AND round_id = :roundId
        """
    )
    suspend fun updateBudget(
        conversationId: String,
        roundId: String,
        spent: Int,
        tokenLimit: Int,
        skippedRoleIds: List<String>,
        reason: String,
        status: String,
        updatedAt: Long,
    )

    /** 单角色 token 累加（同一轮内递增计数，不跨轮挪用）。 */
    @Query(
        """
        UPDATE group_runs SET spent_tokens = :spent, updated_at = :updatedAt
        WHERE conversation_id = :conversationId AND round_id = :roundId
        """
    )
    suspend fun updateSpentTokens(conversationId: String, roundId: String, spent: Int, updatedAt: Long)

    /** 重试跳过的依据：已成功提交 turn 的角色集合。 */
    @Query(
        """
        UPDATE group_runs SET committed_role_ids = :committedRoleIds, updated_at = :updatedAt
        WHERE conversation_id = :conversationId AND round_id = :roundId
        """
    )
    suspend fun updateCommittedRoles(
        conversationId: String,
        roundId: String,
        committedRoleIds: List<String>,
        updatedAt: Long,
    )

    /** 失败节点：记录是哪个角色失败、报了什么错（本轮就此停止）。 */
    @Query(
        """
        UPDATE group_runs SET reason = :reason, error_message = :errorMessage, updated_at = :updatedAt
        WHERE conversation_id = :conversationId AND round_id = :roundId
        """
    )
    suspend fun updateFailure(
        conversationId: String,
        roundId: String,
        reason: String,
        errorMessage: String,
        updatedAt: Long,
    )

    /**
     * 幂等「upsert 一轮」：不存在就插入（抢占 run token），存在就推进可变字段。
     *
     * 保留不可变字段：主键 `(conversationId, roundId)`、[runToken]（旧行的令牌是上一进程留下的，
     * 不能被本次覆盖，否则日志会指向错误执行）、[startedAt]。可推进字段：状态、已用/上限、
     * 已提交角色、未运行角色、原因、错误信息、结束时刻。
     *
     * 用于「重试同一 round_id」：调用方直接把新一轮的完整进度传进来即可，不需要先查再手工分支。
     */
    @Transaction
    suspend fun upsertRun(run: GroupRunEntity): GroupRunEntity {
        val existing = findByRound(run.conversationId, run.roundId)
        if (existing == null) {
            insert(run)
            return run
        }
        finish(
            conversationId = run.conversationId,
            roundId = run.roundId,
            status = run.status,
            endedAt = run.endedAt ?: existing.endedAt ?: run.updatedAt,
            errorMessage = run.errorMessage,
            updatedAt = run.updatedAt,
        )
        updateBudget(
            conversationId = run.conversationId,
            roundId = run.roundId,
            spent = run.spentTokens,
            tokenLimit = run.tokenLimit,
            skippedRoleIds = run.skippedRoleIds,
            reason = run.reason,
            status = run.status,
            updatedAt = run.updatedAt,
        )
        updateCommittedRoles(
            conversationId = run.conversationId,
            roundId = run.roundId,
            committedRoleIds = run.committedRoleIds,
            updatedAt = run.updatedAt,
        )
        return findByRound(run.conversationId, run.roundId) ?: run
    }

    @Query("DELETE FROM group_runs WHERE conversation_id = :conversationId AND round_id = :roundId")
    suspend fun deleteByRound(conversationId: String, roundId: String)

    /**
     * 归档：清掉某群已结束的历史运行。`RUNNING` 硬编码为 `GroupRunEntity.STATUS_RUNNING`
     * （可能仍在跑，不能删）；`@Query` 无法引用常量。
     */
    @Query(
        """
        DELETE FROM group_runs
        WHERE conversation_id = :conversationId AND status != 'RUNNING'
        """
    )
    suspend fun deleteFinishedOfConversation(conversationId: String)

    /**
     * 归档：清理早于给定时间戳的已结束运行（契约要求运行日志不能无限增长）。
     * `RUNNING` 同样硬编码保留，见 [deleteFinishedOfConversation]。
     */
    @Query(
        """
        DELETE FROM group_runs
        WHERE started_at < :before AND status != 'RUNNING'
        """
    )
    suspend fun deleteFinishedBefore(before: Long)

    /** 进程被杀后残留的僵尸 RUNNING 记录（启动时回收，避免永久占位该 round_id）。 */
    @Query("SELECT * FROM group_runs WHERE status = :status ORDER BY started_at ASC")
    suspend fun listByStatus(status: String): List<GroupRunEntity>

    @Query("DELETE FROM group_runs WHERE status = :status")
    suspend fun deleteByStatus(status: String)
}
