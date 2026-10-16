package heizige.kk.khatkit.app.core.data.db.migrations

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.app.core.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite upgrade coverage for the local artifact index. */
@RunWith(AndroidJUnit4::class)
class Migration_33_34_Test {
    private val testDb = "migration-33-34-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate33To34_createsArtifactIndexAndRoundTrips() {
        helper.createDatabase(testDb, 33).close()
        val db = helper.runMigrationsAndValidate(testDb, 34, true, Migration_33_34)

        db.insert(
            "card_artifacts",
            SQLiteDatabase.CONFLICT_ABORT,
            android.content.ContentValues().apply {
                put("id", "artifact-1")
                put("run_id", "run-1")
                put("kind", "file")
                put("uri", "file:///sdcard/Download/result.png")
                put("mime_type", "image/png")
                put("display_name", "result.png")
                put("size_bytes", 42L)
                put("available", 1)
                put("created_at", 100L)
            },
        )
        db.query("PRAGMA index_list(`card_artifacts`)").use { cursor ->
            val indexes = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
            assertTrue(indexes.contains("index_card_artifacts_run_id"))
            assertTrue(indexes.contains("index_card_artifacts_created_at"))
        }
        db.query("SELECT kind, display_name, size_bytes FROM card_artifacts WHERE id = 'artifact-1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("file", it.getString(0))
            assertEquals("result.png", it.getString(1))
            assertEquals(42L, it.getLong(2))
        }
        db.close()
    }
}
