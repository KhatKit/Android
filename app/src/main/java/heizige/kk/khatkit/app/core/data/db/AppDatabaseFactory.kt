package heizige.kk.khatkit.app.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import heizige.kk.khatkit.app.core.data.db.fts.SimpleDictManager
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_6_7
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_11_12
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_13_14
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_14_15
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_15_16
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_25_26
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_27_28
import heizige.kk.khatkit.app.core.data.db.migrations.Migration_30_31

/** Shared schema, migrations and extensions for the app and staged backup validation. */
internal object AppDatabaseFactory {
    fun create(context: Context, name: String = SQLiteConfiguration.DATABASE_NAME): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(
                Migration_6_7, Migration_11_12, Migration_13_14,
                Migration_14_15, Migration_15_16, Migration_25_26, Migration_27_28,
                // C1-D 30→31：新建 group_runs + memory_chunks 增 role_id（显式迁移，见 Migration_30_31.kt）
                Migration_30_31,
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    val dictDir = SimpleDictManager.extractDict(context)
                    val cursor = db.query("SELECT jieba_dict(?)", arrayOf(dictDir.absolutePath))
                    cursor.use {
                        if (it.moveToFirst()) {
                            val result = it.getString(0)
                            val success = result?.trimEnd('/') == dictDir.absolutePath.trimEnd('/')
                            if (!success) {
                                android.util.Log.e(
                                    "DataSourceModule",
                                    "jieba_dict failed: $result, path=${dictDir.absolutePath}"
                                )
                            }
                        }
                    }
                    db.execSQL(
                        """
                        CREATE VIRTUAL TABLE IF NOT EXISTS message_fts USING fts5(
                            text,
                            node_id UNINDEXED,
                            message_id UNINDEXED,
                            conversation_id UNINDEXED,
                            title UNINDEXED,
                            update_at UNINDEXED,
                            tokenize = 'simple'
                        )
                        """.trimIndent()
                    )
                    // A2: 外部内容 FTS5 + 触发器同步（旧 memory_fts 随 MemoryEntity 一起废弃）
                    db.execSQL("DROP TABLE IF EXISTS memory_fts")
                    db.execSQL(
                        """
                        CREATE VIRTUAL TABLE IF NOT EXISTS memory_chunk_fts USING fts5(
                            content,
                            content='memory_chunks', content_rowid='id',
                            tokenize='unicode61 remove_diacritics 2'
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TRIGGER IF NOT EXISTS memory_chunks_ai AFTER INSERT ON memory_chunks BEGIN
                          INSERT INTO memory_chunk_fts(rowid, content) VALUES (new.id, new.content);
                        END
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TRIGGER IF NOT EXISTS memory_chunks_ad AFTER DELETE ON memory_chunks BEGIN
                          INSERT INTO memory_chunk_fts(memory_chunk_fts, rowid, content)
                          VALUES ('delete', old.id, old.content);
                        END
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TRIGGER IF NOT EXISTS memory_chunks_au AFTER UPDATE OF content ON memory_chunks BEGIN
                          INSERT INTO memory_chunk_fts(memory_chunk_fts, rowid, content)
                          VALUES ('delete', old.id, old.content);
                          INSERT INTO memory_chunk_fts(rowid, content) VALUES (new.id, new.content);
                        END
                        """.trimIndent()
                    )
                    // 确保 FTS 覆盖迁移过来的历史行（触发器只管迁移后的增量）
                    db.execSQL(
                        """
                        INSERT INTO memory_chunk_fts(rowid, content)
                        SELECT id, content FROM memory_chunks
                        WHERE id NOT IN (SELECT rowid FROM memory_chunk_fts)
                        """.trimIndent()
                    )
                    // sqlite-vector：注册向量列（失败静默，检索层有 Kotlin 暴力余弦兜底）
                    MemoryVectorIndex.ensure(db, context)
                }
            })
            .openHelperFactory(SQLiteConfiguration.openHelperFactory(context))
            .build()
}
