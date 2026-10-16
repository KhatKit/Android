package heizige.kk.khatkit.app.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val CARD_ARTIFACTS_CREATE_SQL = """
    CREATE TABLE IF NOT EXISTS `card_artifacts` (
      `id` TEXT NOT NULL,
      `run_id` TEXT NOT NULL,
      `kind` TEXT NOT NULL,
      `uri` TEXT NOT NULL,
      `mime_type` TEXT,
      `display_name` TEXT NOT NULL,
      `size_bytes` INTEGER,
      `available` INTEGER NOT NULL,
      `created_at` INTEGER NOT NULL,
      PRIMARY KEY(`id`)
    )
""".trimIndent()

val Migration_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CARD_ARTIFACTS_CREATE_SQL)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_artifacts_run_id` ON `card_artifacts` (`run_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_artifacts_created_at` ON `card_artifacts` (`created_at`)")
    }
}
