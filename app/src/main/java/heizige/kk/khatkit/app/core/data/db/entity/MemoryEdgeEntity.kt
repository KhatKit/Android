package heizige.kk.khatkit.app.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 轻量图谱的边：实体关系 / 共现。节点为隐式实体名（source_name / target_name）。
 *
 * - `evidence_chunk_id` 指向抽取来源分块，用于图谱扩展回捞记忆
 * - `invalidated_at` 非空 = 该关系已被替代或遗忘（bi-temporal）
 */
@Entity(
    tableName = "memory_edges",
    indices = [
        Index("space_id"),
        Index("source_name"),
        Index("target_name"),
        Index("evidence_chunk_id"),
    ],
)
data class MemoryEdgeEntity(
    @PrimaryKey(true)
    val id: Int = 0,
    @ColumnInfo("space_id")
    val spaceId: String,
    @ColumnInfo("source_name")
    val sourceName: String,
    @ColumnInfo("rel_type")
    val relType: String,
    @ColumnInfo("target_name")
    val targetName: String,
    @ColumnInfo("confidence")
    val confidence: Float = 1f,
    @ColumnInfo("evidence_chunk_id")
    val evidenceChunkId: Int? = null,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("invalidated_at")
    val invalidatedAt: Long? = null,
) {
    companion object {
        const val REL_MENTIONS = "MENTIONS"
        const val REL_RELATED = "RELATED"
        const val REL_CAUSES = "CAUSES"
        const val REL_PART_OF = "PART_OF"
        const val REL_HAPPENED_AT = "HAPPENED_AT"
        const val REL_CUSTOM = "CUSTOM"
    }
}
