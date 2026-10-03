package heizige.kk.khatkit.app.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import heizige.kk.khatkit.app.core.data.db.entity.MemorySpaceEntity

@Dao
interface MemorySpaceDAO {
    @Query("SELECT * FROM memory_spaces")
    fun getAllSpacesFlow(): Flow<List<MemorySpaceEntity>>

    @Query("SELECT * FROM memory_spaces WHERE id = :id")
    suspend fun getSpaceById(id: String): MemorySpaceEntity?

    @Query("SELECT * FROM memory_spaces")
    suspend fun getAllSpaces(): List<MemorySpaceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSpace(space: MemorySpaceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpace(space: MemorySpaceEntity)

    @Query("UPDATE memory_spaces SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query(
        """
        UPDATE memory_spaces
        SET embedding_model = :model, embedding_dim = :dim
        WHERE id = :id
        """
    )
    suspend fun updateEmbeddingBinding(id: String, model: String?, dim: Int?)

    @Query("DELETE FROM memory_spaces WHERE id = :id")
    suspend fun deleteSpace(id: String)
}
