package heizige.kk.khatkit.app.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 记忆空间：global / per-assistant / per-conversation 三级。
 *
 * id 约定：
 * - `__global__` 全局共享
 * - `conv:<uuid>` 会话级
 * - `group:<conversationId>:<roleId>` 群内角色独立记忆
 * - 其余为 assistant uuid（兼容旧 `memoryentity.assistant_id`）
 */
@Entity(tableName = "memory_spaces")
data class MemorySpaceEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("kind")
    val kind: String,
    @ColumnInfo("name")
    val name: String = "",
    @ColumnInfo("embedding_model")
    val embeddingModel: String? = null,
    @ColumnInfo("embedding_dim")
    val embeddingDim: Int? = null,
    @ColumnInfo("created_at")
    val createdAt: Long,
) {
    companion object {
        const val KIND_GLOBAL = "GLOBAL"
        const val KIND_ASSISTANT = "ASSISTANT"
        const val KIND_CONVERSATION = "CONVERSATION"
        const val KIND_GROUP = "GROUP"
        const val GROUP_PREFIX = "group:"

        const val GLOBAL_SPACE_ID = "__global__"
        const val CONVERSATION_PREFIX = "conv:"

        fun kindOf(spaceId: String): String = when {
            spaceId == GLOBAL_SPACE_ID -> KIND_GLOBAL
            spaceId.startsWith(CONVERSATION_PREFIX) -> KIND_CONVERSATION
            spaceId.startsWith(GROUP_PREFIX) -> KIND_GROUP
            else -> KIND_ASSISTANT
        }

        fun conversationSpaceId(conversationId: String): String =
            CONVERSATION_PREFIX + conversationId
    }
}
