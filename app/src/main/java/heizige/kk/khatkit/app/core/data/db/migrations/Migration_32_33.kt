package heizige.kk.khatkit.app.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val CARD_RUNS_CREATE_SQL = """
    CREATE TABLE IF NOT EXISTS `card_runs` (
      `id` TEXT NOT NULL,
      `parent_run_id` TEXT,
      `card_name` TEXT NOT NULL,
      `card_version` TEXT NOT NULL,
      `trigger` TEXT NOT NULL,
      `status` TEXT NOT NULL,
      `started_at` INTEGER NOT NULL,
      `updated_at` INTEGER NOT NULL,
      `ended_at` INTEGER,
      `args_summary` TEXT NOT NULL,
      `output_summary` TEXT NOT NULL,
      `error_code` TEXT,
      `error_message` TEXT,
      PRIMARY KEY(`id`)
    )
""".trimIndent()

val Migration_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CARD_RUNS_CREATE_SQL)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_runs_started_at` ON `card_runs` (`started_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_runs_card_name_started_at` ON `card_runs` (`card_name`, `started_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_runs_status` ON `card_runs` (`status`)")
    }
}
