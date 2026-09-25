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
import heizige.kk.khatkit.app.core.data.db.dao.ManagedFileDAO
import heizige.kk.khatkit.app.core.data.db.dao.MemoryDAO
import heizige.kk.khatkit.app.core.data.db.dao.MessageNodeDAO
import heizige.kk.khatkit.app.core.data.db.dao.WorkspaceDAO
import heizige.kk.khatkit.app.core.data.db.entity.ConversationEntity
import heizige.kk.khatkit.app.core.data.db.entity.FolderEntity
import heizige.kk.khatkit.app.core.data.db.entity.GenMediaEntity
import heizige.kk.khatkit.app.core.data.db.entity.ManagedFileEntity
import heizige.kk.khatkit.app.core.data.db.entity.MemoryEntity
import heizige.kk.khatkit.app.core.data.db.entity.MessageNodeEntity
import heizige.kk.khatkit.app.core.data.db.entity.WorkspaceEntity
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_16_17
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_22_23
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_8_9
import heizige.kk.khatkit.app.core.util.JsonInstant

@Database(
    entities = [
        ConversationEntity::class,
        MemoryEntity::class,
        GenMediaEntity::class,
        MessageNodeEntity::class,
        ManagedFileEntity::class,
        WorkspaceEntity::class,
        FolderEntity::class,
    ],
    version = 26,
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
    ]
)
@TypeConverters(TokenUsageConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDAO

    abstract fun memoryDao(): MemoryDAO

    abstract fun genMediaDao(): GenMediaDAO

    abstract fun messageNodeDao(): MessageNodeDAO

    abstract fun managedFileDao(): ManagedFileDAO

    abstract fun workspaceDao(): WorkspaceDAO

    abstract fun folderDao(): FolderDAO
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
