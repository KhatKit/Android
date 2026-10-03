package heizige.kk.khatkit.app.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * A2 记忆系统升级：KV `MemoryEntity` → 空间/分块/图谱/提及四表。
 *
 * 旧 `MemoryEntity(assistant_id, content)` 行迁移为 `memory_chunks`（`__global__` 归
 * GLOBAL 空间，其余归 ASSISTANT 空间），随后删除旧表。
 */
val Migration_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_spaces` (
              `id` TEXT NOT NULL,
              `kind` TEXT NOT NULL,
              `name` TEXT NOT NULL,
              `embedding_model` TEXT,
              `embedding_dim` INTEGER,
              `created_at` INTEGER NOT NULL,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_chunks` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `space_id` TEXT NOT NULL,
              `content` TEXT NOT NULL,
              `source_kind` TEXT NOT NULL,
              `source_message_id` TEXT,
              `source_ref_id` TEXT,
              `confidence` REAL NOT NULL,
              `extracted_at` INTEGER NOT NULL,
              `last_hit_at` INTEGER NOT NULL,
              `created_at` INTEGER NOT NULL,
              `updated_at` INTEGER NOT NULL,
              `deleted_at` INTEGER,
              `embedding` BLOB
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_chunks_space_id` ON `memory_chunks` (`space_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_chunks_source_message_id` ON `memory_chunks` (`source_message_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_chunks_deleted_at` ON `memory_chunks` (`deleted_at`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_edges` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `space_id` TEXT NOT NULL,
              `source_name` TEXT NOT NULL,
              `rel_type` TEXT NOT NULL,
              `target_name` TEXT NOT NULL,
              `confidence` REAL NOT NULL,
              `evidence_chunk_id` INTEGER,
              `created_at` INTEGER NOT NULL,
              `invalidated_at` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_edges_space_id` ON `memory_edges` (`space_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_edges_source_name` ON `memory_edges` (`source_name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_edges_target_name` ON `memory_edges` (`target_name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_edges_evidence_chunk_id` ON `memory_edges` (`evidence_chunk_id`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_mentions` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `chunk_id` INTEGER NOT NULL,
              `entity_name` TEXT NOT NULL,
              `message_id` TEXT,
              `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_mentions_chunk_id` ON `memory_mentions` (`chunk_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_mentions_entity_name` ON `memory_mentions` (`entity_name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_mentions_message_id` ON `memory_mentions` (`message_id`)")

        // 旧 KV 行 → 空间 + 分块
        db.execSQL(
            """
            INSERT OR IGNORE INTO memory_spaces (id, kind, name, created_at)
            SELECT DISTINCT assistant_id,
              CASE WHEN assistant_id = '__global__' THEN 'GLOBAL' ELSE 'ASSISTANT' END,
              '',
              CAST(strftime('%s','now') AS INTEGER) * 1000
            FROM MemoryEntity
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO memory_chunks
              (space_id, content, source_kind, confidence, extracted_at, last_hit_at, created_at, updated_at)
            SELECT assistant_id, content, 'MANUAL', 1.0,
              CAST(strftime('%s','now') AS INTEGER) * 1000, 0,
              CAST(strftime('%s','now') AS INTEGER) * 1000,
              CAST(strftime('%s','now') AS INTEGER) * 1000
            FROM MemoryEntity
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS `MemoryEntity`")
    }
}
