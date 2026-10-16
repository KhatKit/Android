package heizige.kk.khatkit.app.core.data.db.migrations

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

@RunWith(AndroidJUnit4::class)
class Migration_35_36_Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate35To36_addsNullableTextContent() {
        val name = "migration-35-36-test"
        helper.createDatabase(name, 35).apply {
            execSQL(
                """
                INSERT INTO card_artifacts
                    (id, run_id, kind, uri, mime_type, display_name, size_bytes, available, created_at)
                VALUES ('old-file', 'old-run', 'file', 'file:///tmp/result.txt',
                    'text/plain', 'result.txt', 12, 1, 1234)
                """.trimIndent(),
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 36, true, Migration_35_36)
        db.query("PRAGMA table_info(`card_artifacts`)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(1) != "text_content") continue
                found = true
                assertEquals("TEXT", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
            }
            assertTrue("card_artifacts must include text_content", found)
        }
        db.query("SELECT display_name, text_content FROM card_artifacts WHERE id = 'old-file'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("result.txt", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        db.close()
    }
}
