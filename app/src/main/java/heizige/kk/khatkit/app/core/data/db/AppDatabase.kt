package heizige.kk.khatkit.app.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import heizige.kk.khatkit.ai.core.TokenUsage
import heizige.kk.khatkit.app.core.data.db.dao.ConversationDAO
import heizige.kk.khatkit.app.core.data.db.dao.FolderDAO
import heizige.kk.khatkit.app.core.data.db.dao.GenMediaDAO
import heizige.kk.khatkit.app.core.data.db.dao.GroupRunDAO
import heizige.kk.khatkit.app.core.data.db.dao.ManagedFileDAO
import heizige.kk.khatkit.app.core.data.db.dao.MemoryChunkDAO
import heizige.kk.khatkit.app.core.data.db.dao.MemoryGraphDAO
import heizige.kk.khatkit.app.core.data.db.dao.MemorySpaceDAO
import heizige.kk.khatkit.app.core.data.db.dao.MessageNodeDAO
import heizige.kk.khatkit.app.core.data.db.dao.WorkflowDao
import heizige.kk.khatkit.app.core.data.db.dao.WorkspaceDAO
import heizige.kk.khatkit.app.core.data.db.entity.ConversationEntity
import heizige.kk.khatkit.app.core.data.db.entity.FolderEntity
import heizige.kk.khatkit.app.core.data.db.entity.GenMediaEntity
import heizige.kk.khatkit.app.core.data.db.entity.GroupRunEntity
import heizige.kk.khatkit.app.core.data.db.entity.ManagedFileEntity
import heizige.kk.khatkit.app.core.data.db.entity.MemoryChunkEntity
import heizige.kk.khatkit.app.core.data.db.entity.MemoryEdgeEntity
import heizige.kk.khatkit.app.core.data.db.entity.MemoryMentionEntity
import heizige.kk.khatkit.app.core.data.db.entity.MemorySpaceEntity
import heizige.kk.khatkit.app.core.data.db.entity.MessageNodeEntity
import heizige.kk.khatkit.app.core.data.db.entity.StringListConverter
import heizige.kk.khatkit.app.core.data.db.entity.WorkflowEntity
import heizige.kk.khatkit.app.core.data.db.entity.WorkflowRunEntity
import heizige.kk.khatkit.app.core.data.db.entity.WorkflowRunStepEntity
import heizige.kk.khatkit.app.core.data.db.entity.WorkspaceEntity
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_16_17
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_22_23
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_27_28
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_8_9
import heizige.kk.khatkit.app.core.util.JsonInstant
import heizige.kk.khatkit.app.core.data.db.dao.FavoriteDAO
import heizige.kk.khatkit.app.core.data.db.entity.FavoriteEntity

@Database(
    entities = [
        ConversationEntity::class,
        MemorySpaceEntity::class,
        MemoryChunkEntity::class,
        MemoryEdgeEntity::class,
        MemoryMentionEntity::class,
        GenMediaEntity::class,
        MessageNodeEntity::class,
        ManagedFileEntity::class,
        WorkspaceEntity::class,
        FolderEntity::class,
        FavoriteEntity::class,
        WorkflowEntity::class,
        WorkflowRunEntity::class,
        WorkflowRunStepEntity::class,
        GroupRunEntity::class,
    ],
    version = 32,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9, spec = Migration_8_9::class),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 16, to = 17, spec = Migration_16_17::class),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
        AutoMigration(from = 22, to = 23, spec = Migration_22_23::class),
        AutoMigration(from = 23, to = 24),
        AutoMigration(from = 24, to = 25),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 28, to = 29),
        AutoMigration(from = 29, to = 30),
        // C1-D 30→31 **刻意不用 AutoMigration**：Room 的自动迁移会走
        // 「建 _new_memory_chunks → 全量拷贝 → DROP → RENAME」，DROP 会连带删掉
        // `memory_chunks_ai/ad/au` 三个 FTS5 触发器。改为显式 Migration_30_31，
        // 用 `ALTER TABLE memory_chunks ADD COLUMN role_id` 直接加列（不动旧表 / rowid /
        // 索引 / 触发器）。注册见 AppDatabaseFactory.addMigrations。
        // C1-P 31→32 同理不用 AutoMigration（同样的 DROP TABLE 触发器问题，另加
        // message_node 的外键指向 ConversationEntity）：显式 Migration_31_32，
        // `ALTER TABLE ConversationEntity ADD COLUMN group_cards`。
    ]
)
@TypeConverters(TokenUsageConverter::class, StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDAO

    abstract fun memorySpaceDao(): MemorySpaceDAO

    abstract fun memoryChunkDao(): MemoryChunkDAO

    abstract fun memoryGraphDao(): MemoryGraphDAO

    abstract fun genMediaDao(): GenMediaDAO

    abstract fun messageNodeDao(): MessageNodeDAO

    abstract fun managedFileDao(): ManagedFileDAO

    abstract fun workspaceDao(): WorkspaceDAO

    abstract fun folderDao(): FolderDAO

    abstract fun favoriteDao(): FavoriteDAO

    abstract fun workflowDao(): WorkflowDao

    abstract fun groupRunDao(): GroupRunDAO
}

object TokenUsageConverter {
    @TypeConverter
    fun fromTokenUsage(usage: TokenUsage?): String {
        return JsonInstant.encodeToString(usage)
    }

    @TypeConverter
    fun toTokenUsage(usage: String): TokenUsage? {
        return JsonInstant.decodeFromString(usage)
    }
}
