package heizige.kk.khatkit.app.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 记忆分块：A2 的最小记忆单元，带来源引用与可解释字段。
 *
 * - `embedding` 为 Float32 BLOB（sqlite-vector 可直接扫描；缺省时走 Kotlin 暴力余弦兜底）
 * - `deleted_at` 非空 = 遗忘（软删，保留溯源）
 */
@Entity(
    tableName = "memory_chunks",
    indices = [
        Index("space_id"),
        Index("source_message_id"),
        Index("deleted_at"),
    ],
)
data class MemoryChunkEntity(
    @PrimaryKey(true)
    val id: Int = 0,
    @ColumnInfo("space_id")
    val spaceId: String,
    @ColumnInfo("content")
    val content: String,
    @ColumnInfo("source_kind")
    val sourceKind: String,
    @ColumnInfo("source_message_id")
    val sourceMessageId: String? = null,
    @ColumnInfo("source_ref_id")
    val sourceRefId: String? = null,
    @ColumnInfo("confidence")
    val confidence: Float = 1f,
    @ColumnInfo("extracted_at")
    val extractedAt: Long,
    @ColumnInfo("last_hit_at")
    val lastHitAt: Long = 0L,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
    @ColumnInfo("deleted_at")
    val deletedAt: Long? = null,
    @ColumnInfo("embedding")
    val embedding: ByteArray? = null,
) {
    companion object {
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_MESSAGE = "MESSAGE"
        const val SOURCE_DOCUMENT = "DOCUMENT"
        const val SOURCE_EXTRACTED = "EXTRACTED"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryChunkEntity) return false
        return id == other.id && spaceId == other.spaceId && content == other.content &&
            sourceKind == other.sourceKind && sourceMessageId == other.sourceMessageId &&
            sourceRefId == other.sourceRefId && confidence == other.confidence &&
            extractedAt == other.extractedAt && lastHitAt == other.lastHitAt &&
            createdAt == other.createdAt && updatedAt == other.updatedAt &&
            deletedAt == other.deletedAt && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + spaceId.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + sourceKind.hashCode()
        result = 31 * result + (sourceMessageId?.hashCode() ?: 0)
        result = 31 * result + (sourceRefId?.hashCode() ?: 0)
        result = 31 * result + confidence.hashCode()
        result = 31 * result + extractedAt.hashCode()
        result = 31 * result + lastHitAt.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + (deletedAt?.hashCode() ?: 0)
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}
