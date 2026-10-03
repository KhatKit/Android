package heizige.kk.khatkit.app.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import heizige.kk.khatkit.app.core.data.db.entity.MemoryChunkEntity

@Dao
interface MemoryChunkDAO {
    @Query("SELECT * FROM memory_chunks WHERE space_id = :spaceId AND deleted_at IS NULL ORDER BY extracted_at DESC")
    fun getChunksOfSpaceFlow(spaceId: String): Flow<List<MemoryChunkEntity>>

    @Query("SELECT * FROM memory_chunks WHERE space_id = :spaceId AND deleted_at IS NULL ORDER BY extracted_at DESC")
    suspend fun getChunksOfSpace(spaceId: String): List<MemoryChunkEntity>

    @Query("SELECT * FROM memory_chunks WHERE id = :id")
    suspend fun getChunkById(id: Int): MemoryChunkEntity?

    @Query("SELECT * FROM memory_chunks WHERE id IN (:ids)")
    suspend fun getChunksByIds(ids: List<Int>): List<MemoryChunkEntity>

    @Query("SELECT * FROM memory_chunks WHERE source_message_id = :messageId AND deleted_at IS NULL")
    suspend fun getChunksByMessageId(messageId: String): List<MemoryChunkEntity>

    @Query("SELECT * FROM memory_chunks WHERE deleted_at IS NULL AND embedding IS NOT NULL")
    suspend fun getChunksWithEmbedding(): List<MemoryChunkEntity>

    @Query("SELECT COUNT(*) FROM memory_chunks WHERE space_id = :spaceId AND deleted_at IS NULL")
    suspend fun countChunksOfSpace(spaceId: String): Int

    @Query("SELECT COUNT(*) FROM memory_chunks WHERE deleted_at IS NULL")
    suspend fun countAllChunks(): Int

    @Insert
    suspend fun insertChunk(chunk: MemoryChunkEntity): Long

    @Insert
    suspend fun insertChunks(chunks: List<MemoryChunkEntity>)

    @Update
    suspend fun updateChunk(chunk: MemoryChunkEntity)

    @Query("UPDATE memory_chunks SET last_hit_at = :hitAt WHERE id = :id")
    suspend fun touchHit(id: Int, hitAt: Long)

    @Query("UPDATE memory_chunks SET embedding = :embedding, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateEmbedding(id: Int, embedding: ByteArray?, updatedAt: Long)

    @Query("UPDATE memory_chunks SET deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Int, deletedAt: Long)

    @Query("UPDATE memory_chunks SET deleted_at = :deletedAt WHERE space_id = :spaceId")
    suspend fun softDeleteSpace(spaceId: String, deletedAt: Long)

    @Query("DELETE FROM memory_chunks WHERE space_id = :spaceId")
    suspend fun hardDeleteSpace(spaceId: String)

    @Query("DELETE FROM memory_chunks WHERE id = :id")
    suspend fun hardDelete(id: Int)
}
