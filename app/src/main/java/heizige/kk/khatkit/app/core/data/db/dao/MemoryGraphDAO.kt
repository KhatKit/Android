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

    /**
     * C1-M：按实体名取「同空间」的提及。
     *
     * `memory_mentions` 本身没有 `space_id` 列（加列要 Room 31→32 迁移，本包不允许），
     * 但它有 `chunk_id`，而 `memory_chunks` 有 `space_id` —— 所以用 JOIN 在查询层
     * 把空间闸门做掉，不动 schema。
     *
     * 不带空间条件的后果（已修的 bug）：A 角色检索命中实体「张伟」时，会把 B/C 角色
     * 空间乃至助手/全局空间里提到「张伟」的分块全部拉进候选并原样返回，直接违反
     * C1-08「三个空间互不串」。`spaceId` 故意不给默认值：宁可编译不过，也不要有人
     * 再写出无空间条件的版本。
     */
    @Query(
        """
        SELECT m.* FROM memory_mentions m
        JOIN memory_chunks c ON c.id = m.chunk_id
        WHERE m.entity_name = :entityName
          AND c.space_id = :spaceId
          AND c.deleted_at IS NULL
        """
    )
    suspend fun getMentionsOfEntity(entityName: String, spaceId: String): List<MemoryMentionEntity>

    /** 同上：`spaceId` 必填，理由见 [getMentionsOfEntity]。 */
    @Query(
        """
        SELECT DISTINCT m.chunk_id FROM memory_mentions m
        JOIN memory_chunks c ON c.id = m.chunk_id
        WHERE m.entity_name IN (:names)
          AND m.chunk_id != :excludeChunkId
          AND c.space_id = :spaceId
          AND c.deleted_at IS NULL
        """
    )
    suspend fun getChunkIdsMentioning(
        names: List<String>,
        excludeChunkId: Int,
        spaceId: String,
    ): List<Int>

    @Insert
    suspend fun insertMention(mention: MemoryMentionEntity): Long

    @Insert
    suspend fun insertMentions(mentions: List<MemoryMentionEntity>)

    @Query("DELETE FROM memory_mentions WHERE chunk_id = :chunkId")
    suspend fun deleteMentionsOfChunk(chunkId: Int)

    @Query("DELETE FROM memory_mentions WHERE chunk_id IN (SELECT id FROM memory_chunks WHERE space_id = :spaceId)")
    suspend fun deleteMentionsOfSpace(spaceId: String)
}
