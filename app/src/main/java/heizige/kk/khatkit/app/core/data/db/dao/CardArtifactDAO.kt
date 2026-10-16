package heizige.kk.khatkit.app.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import heizige.kk.khatkit.app.core.data.db.entity.CardArtifactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardArtifactDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artifacts: List<CardArtifactEntity>)

    @Query("SELECT * FROM card_artifacts WHERE run_id = :runId ORDER BY created_at ASC")
    fun observeForRun(runId: String): Flow<List<CardArtifactEntity>>

    @Query(
        """
        SELECT card_artifacts.* FROM card_artifacts
        INNER JOIN (SELECT id FROM card_runs ORDER BY started_at DESC LIMIT :runLimit) recent
            ON recent.id = card_artifacts.run_id
        ORDER BY card_artifacts.created_at DESC
        """
    )
    fun observeForRecentRuns(runLimit: Int): Flow<List<CardArtifactEntity>>

    @Query("UPDATE card_artifacts SET available = 0 WHERE uri = :uri")
    suspend fun markUnavailable(uri: String): Int

    @Query("DELETE FROM card_artifacts WHERE run_id = :runId")
    suspend fun deleteForRun(runId: String): Int
}
