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

/** Verifies that existing task rows gain an empty, non-sensitive rerun template. */
@RunWith(AndroidJUnit4::class)
class Migration_34_35_Test {
    private val testDb = "migration-34-35-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate34To35_addsArgsTemplateWithEmptyDefault() {
        helper.createDatabase(testDb, 34).close()
        val db = helper.runMigrationsAndValidate(testDb, 35, true, Migration_34_35)
        db.query("PRAGMA table_info(`card_runs`)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(1) != "args_template") continue
                found = true
                assertEquals("TEXT", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals("'{}'", cursor.getString(4))
            }
            assertTrue("card_runs 必须新增 args_template", found)
        }
        db.close()
    }
}
