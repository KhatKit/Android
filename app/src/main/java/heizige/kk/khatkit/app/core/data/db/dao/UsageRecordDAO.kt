package heizige.kk.khatkit.app.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import heizige.kk.khatkit.app.core.data.db.entity.UsageRecordEntity
import kotlinx.coroutines.flow.Flow

/** US1（上游 5a90c0548）：用量流水读写。统计页按时间窗口聚合，消息统计行只取最近几天的平均速度。 */
@Dao
interface UsageRecordDAO {
    @Insert
    suspend fun insert(record: UsageRecordEntity)

    @Query("SELECT * FROM usage_record WHERE created_at >= :sinceMillis ORDER BY created_at DESC")
    fun observeSince(sinceMillis: Long): Flow<List<UsageRecordEntity>>

    @Query("SELECT * FROM usage_record ORDER BY created_at DESC")
    fun observeAll(): Flow<List<UsageRecordEntity>>

    @Query("SELECT COUNT(*) FROM usage_record")
    suspend fun count(): Int

    @Query("DELETE FROM usage_record WHERE created_at < :beforeMillis")
    suspend fun deleteBefore(beforeMillis: Long)

    // ---- US2 聚合：趋势 / 模型排行 / 来源拆分 ----

    @Query(
        """
        SELECT strftime('%Y-%m-%d', created_at / 1000, 'unixepoch', 'localtime') AS bucket,
               SUM(prompt_tokens) AS promptTokens,
               SUM(completion_tokens) AS completionTokens,
               SUM(cached_tokens) AS cachedTokens,
               COUNT(*) AS requests
        FROM usage_record WHERE created_at >= :sinceMillis
        GROUP BY bucket ORDER BY bucket
        """
    )
    suspend fun trendByDay(sinceMillis: Long): List<UsageTrendPoint>

    @Query(
        """
        SELECT strftime('%Y-%W', created_at / 1000, 'unixepoch', 'localtime') AS bucket,
               SUM(prompt_tokens) AS prompt_tokens,
               SUM(completion_tokens) AS completion_tokens,
               SUM(cached_tokens) AS cached_tokens,
               COUNT(*) AS requests
        FROM usage_record WHERE created_at >= :sinceMillis
        GROUP BY bucket ORDER BY bucket
        """
    )
    suspend fun trendByWeek(sinceMillis: Long): List<UsageTrendPoint>

    @Query(
        """
        SELECT model_id AS modelId, model_name AS modelName,
               SUM(prompt_tokens) AS prompt_tokens,
               SUM(completion_tokens) AS completion_tokens,
               SUM(cached_tokens) AS cached_tokens,
               COUNT(*) AS requests
        FROM usage_record WHERE created_at >= :sinceMillis
        GROUP BY model_id, model_name ORDER BY (SUM(prompt_tokens) + SUM(completion_tokens)) DESC
        """
    )
    suspend fun byModel(sinceMillis: Long): List<UsageByModel>

    @Query(
        """
        SELECT source AS source,
               SUM(prompt_tokens) AS prompt_tokens,
               SUM(completion_tokens) AS completion_tokens,
               SUM(cached_tokens) AS cached_tokens,
               COUNT(*) AS requests
        FROM usage_record WHERE created_at >= :sinceMillis
        GROUP BY source ORDER BY (SUM(prompt_tokens) + SUM(completion_tokens)) DESC
        """
    )
    suspend fun bySource(sinceMillis: Long): List<UsageBySource>
}

/** 趋势桶：日 = yyyy-MM-dd，周 = yyyy-WW。 */
data class UsageTrendPoint(
    val bucket: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val cachedTokens: Long,
    val requests: Long,
)

data class UsageByModel(
    val modelId: String?,
    val modelName: String?,
    val promptTokens: Long,
    val completionTokens: Long,
    val cachedTokens: Long,
    val requests: Long,
)

data class UsageBySource(
    val source: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val cachedTokens: Long,
    val requests: Long,
)
