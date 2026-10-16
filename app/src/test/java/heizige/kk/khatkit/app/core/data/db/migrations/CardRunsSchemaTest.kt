package heizige.kk.khatkit.app.core.data.db.migrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Offline schema guard for the 32 -> 36 task/run schema migrations.
 *
 * The instrumentation test executes the migration against SQLite. This test keeps
 * the generated Room schema and hand-written DDL aligned in ordinary JVM builds.
 */
class CardRunsSchemaTest {
    @Test
    fun exportedSchemaContainsCardRunsWithExpectedColumnsAndIndexes() {
        val schema = schemaFile(36).readText()
        assertTrue(schema.contains("\"version\": 36"))
        assertTrue(schema.contains("\"tableName\": \"card_runs\""))
        assertTrue(
            schema.contains(
                "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`id` TEXT NOT NULL, " +
                    "`parent_run_id` TEXT, `card_name` TEXT NOT NULL, " +
                    "`card_version` TEXT NOT NULL, `trigger` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, `started_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, `ended_at` INTEGER, " +
                    "`args_summary` TEXT NOT NULL, `args_template` TEXT NOT NULL DEFAULT '{}', " +
                    "`output_summary` TEXT NOT NULL, " +
                    "`error_code` TEXT, `error_message` TEXT, PRIMARY KEY(`id`))",
            ),
        )
        listOf(
            "index_card_runs_started_at",
            "index_card_runs_card_name_started_at",
            "index_card_runs_status",
        ).forEach { index ->
            assertTrue("schema 缺少索引 $index", schema.contains("\"name\": \"$index\""))
        }
        assertTrue(schema.contains("\"tableName\": \"card_artifacts\""))
        assertTrue(schema.contains("`text_content` TEXT"))
        assertFalse("32 版不应包含 card_runs", schemaFile(32).readText().contains("\"tableName\": \"card_runs\""))
    }

    @Test
    fun migrationDdlAndRegistrationStayInSync() {
        assertEquals(32, Migration_32_33.startVersion)
        assertEquals(33, Migration_32_33.endVersion)
        assertTrue(CARD_RUNS_CREATE_SQL.contains("CREATE TABLE IF NOT EXISTS `card_runs`"))
        assertTrue(CARD_RUNS_CREATE_SQL.contains("`parent_run_id` TEXT"))
        assertTrue(CARD_RUNS_CREATE_SQL.contains("`error_message` TEXT"))
        assertEquals(33, Migration_33_34.startVersion)
        assertEquals(34, Migration_33_34.endVersion)
        assertTrue(CARD_ARTIFACTS_CREATE_SQL.contains("CREATE TABLE IF NOT EXISTS `card_artifacts`"))
        assertEquals(34, Migration_34_35.startVersion)
        assertEquals(35, Migration_34_35.endVersion)
        assertTrue(schemaFile(35).readText().contains("\"defaultValue\": \"'{}'\""))
        assertEquals(35, Migration_35_36.startVersion)
        assertEquals(36, Migration_35_36.endVersion)
        val factory = File("src/main/java/heizige/kk/khatkit/app/core/data/db/AppDatabaseFactory.kt").readText()
        assertTrue(factory.contains("Migration_32_33"))
        assertTrue(factory.contains("Migration_33_34"))
        assertTrue(factory.contains("Migration_34_35"))
        assertTrue(factory.contains("Migration_35_36"))
        assertTrue(File("src/main/java/heizige/kk/khatkit/app/core/data/db/AppDatabase.kt").readText().contains("version = 36"))
    }

    private fun schemaFile(version: Int = 33): File {
        val candidates = listOf(
            File("schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/$version.json"),
            File("app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/$version.json"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("找不到 Room schema $version.json，工作目录=${File("").absolutePath}")
    }
}
