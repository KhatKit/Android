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
}
