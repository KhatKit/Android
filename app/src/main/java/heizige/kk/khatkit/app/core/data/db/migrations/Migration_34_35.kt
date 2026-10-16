package heizige.kk.khatkit.app.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `card_runs` ADD COLUMN `args_template` TEXT NOT NULL DEFAULT '{}'")
    }
}
