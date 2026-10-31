package heizige.kk.khatkit.app.core.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import heizige.kk.khatkit.app.core.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_36_37"

/**
 * US1（上游 5a90c0548，本 fork 版本号 36→37）：新增 usage_record 用量流水表。
 * 旧数据的回填 SQL 依赖上游 json_extract 结构，本 fork 保守起见不自动回填，升级后从下一次请求开始记录。
 */
val Migration_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 36 to 37 (usage records)")
        DatabaseMigrationTracker.onMigrationStart(36, 37)
        db.beginTransaction()
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `usage_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, `source` TEXT NOT NULL, `provider_id` TEXT, `model_id` TEXT, " +
                    "`model_name` TEXT, `assistant_id` TEXT, `conversation_id` TEXT, " +
                    "`prompt_tokens` INTEGER NOT NULL, `completion_tokens` INTEGER NOT NULL, " +
                    "`cached_tokens` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_record_created_at` ON `usage_record` (`created_at`)")
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            throw e
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
