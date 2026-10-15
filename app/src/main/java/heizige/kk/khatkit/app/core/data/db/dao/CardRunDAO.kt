package heizige.kk.khatkit.app.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import heizige.kk.khatkit.app.core.data.db.entity.CardRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardRunDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: CardRunEntity)

    @Query("UPDATE card_runs SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query(
        """
        UPDATE card_runs SET status = :status, updated_at = :updatedAt, ended_at = :endedAt,
            output_summary = :outputSummary, error_code = :errorCode, error_message = :errorMessage
        WHERE id = :id
        """
    )
    suspend fun finish(
        id: String,
        status: String,
        updatedAt: Long,
        endedAt: Long,
        outputSummary: String,
        errorCode: String?,
        errorMessage: String?,
    )

    @Query("SELECT * FROM card_runs ORDER BY started_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CardRunEntity>>

    @Query("SELECT * FROM card_runs ORDER BY started_at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<CardRunEntity>

    @Query("SELECT * FROM card_runs WHERE id = :id")
    suspend fun get(id: String): CardRunEntity?
}
