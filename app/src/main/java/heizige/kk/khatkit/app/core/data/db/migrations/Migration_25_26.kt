package heizige.kk.khatkit.app.core.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Migration_25_26"

/** 收藏功能已移除，删除遗留的 favorites 表。 */
val Migration_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: drop favorites table")
        db.execSQL("DROP TABLE IF EXISTS favorites")
    }
}
