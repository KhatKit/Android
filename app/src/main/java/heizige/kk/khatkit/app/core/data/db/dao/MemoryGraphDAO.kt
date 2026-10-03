package heizige.kk.khatkit.app.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import heizige.kk.khatkit.app.core.data.db.entity.MemoryEdgeEntity
import heizige.kk.khatkit.app.core.data.db.entity.MemoryMentionEntity

@Dao
interface MemoryGraphDAO {
    // ---- edges ----

    @Query("SELECT * FROM memory_edges WHERE space_id = :spaceId AND invalidated_at IS NULL")
    suspend fun getEdgesOfSpace(spaceId: String): List<MemoryEdgeEntity>

    @Query(
        """
        SELECT * FROM memory_edges
        WHERE space_id = :spaceId AND invalidated_at IS NULL
          AND (source_name = :entityName OR target_name = :entityName)
        """
    )
    suspend fun getEdgesOfEntity(spaceId: String, entityName: String): List<MemoryEdgeEntity>

    @Query(
        """
        SELECT * FROM memory_edges
        WHERE space_id = :spaceId AND invalidated_at IS NULL
          AND (source_name IN (:names) OR target_name IN (:names))
        """
    )
    suspend fun getEdgesOfEntities(spaceId: String, names: List<String>): List<MemoryEdgeEntity>

    @Query("SELECT * FROM memory_edges WHERE evidence_chunk_id = :chunkId AND invalidated_at IS NULL")
    suspend fun getEdgesOfChunk(chunkId: Int): List<MemoryEdgeEntity>

    @Query("SELECT * FROM memory_edges WHERE id = :id")
    suspend fun getEdgeById(id: Int): MemoryEdgeEntity?

    @Insert
    suspend fun insertEdge(edge: MemoryEdgeEntity): Long

    @Insert
    suspend fun insertEdges(edges: List<MemoryEdgeEntity>)

    @Query("UPDATE memory_edges SET invalidated_at = :invalidatedAt WHERE id = :id")
    suspend fun invalidateEdge(id: Int, invalidatedAt: Long)

    @Query("UPDATE memory_edges SET invalidated_at = :invalidatedAt WHERE evidence_chunk_id = :chunkId")
    suspend fun invalidateEdgesOfChunk(chunkId: Int, invalidatedAt: Long)

    @Query("DELETE FROM memory_edges WHERE space_id = :spaceId")
    suspend fun deleteEdgesOfSpace(spaceId: String)

    // ---- mentions ----

    @Query("SELECT * FROM memory_mentions WHERE chunk_id = :chunkId")
    suspend fun getMentionsOfChunk(chunkId: Int): List<MemoryMentionEntity>

    @Query("SELECT * FROM memory_mentions WHERE entity_name = :entityName")
    suspend fun getMentionsOfEntity(entityName: String): List<MemoryMentionEntity>

    @Query(
        """
        SELECT DISTINCT m.chunk_id FROM memory_mentions m
        WHERE m.entity_name IN (:names) AND m.chunk_id != :excludeChunkId
        """
    )
    suspend fun getChunkIdsMentioning(names: List<String>, excludeChunkId: Int): List<Int>

    @Insert
    suspend fun insertMention(mention: MemoryMentionEntity): Long

    @Insert
    suspend fun insertMentions(mentions: List<MemoryMentionEntity>)

    @Query("DELETE FROM memory_mentions WHERE chunk_id = :chunkId")
    suspend fun deleteMentionsOfChunk(chunkId: Int)

    @Query("DELETE FROM memory_mentions WHERE chunk_id IN (SELECT id FROM memory_chunks WHERE space_id = :spaceId)")
    suspend fun deleteMentionsOfSpace(spaceId: String)
}
