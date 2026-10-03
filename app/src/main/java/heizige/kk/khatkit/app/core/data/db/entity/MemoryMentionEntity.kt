package heizige.kk.khatkit.app.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 记忆提及：分块中出现的实体名，以及来源消息引用。
 *
 * 检索时用于图谱扩展（chunk → entity → 共享该 entity 的其他 chunk）
 * 与来源跳转（message_id → 原消息）。
 */
@Entity(
    tableName = "memory_mentions",
    indices = [
        Index("chunk_id"),
        Index("entity_name"),
        Index("message_id"),
    ],
)
data class MemoryMentionEntity(
    @PrimaryKey(true)
    val id: Int = 0,
    @ColumnInfo("chunk_id")
    val chunkId: Int,
    @ColumnInfo("entity_name")
    val entityName: String,
    @ColumnInfo("message_id")
    val messageId: String? = null,
    @ColumnInfo("created_at")
    val createdAt: Long,
)
