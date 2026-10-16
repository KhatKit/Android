package heizige.kk.khatkit.app.core.data.db.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.app.core.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * C1-R 迁移证据：Room 32 -> 33 新增 card_runs 后，旧库可打开且记录可读写。
 *
 * 这是 instrumentation 测试，真实的 Room TableInfo 校验需要设备/模拟器；
 * 离线环境对应的结构护栏见 CardRunsSchemaTest。
 */
@RunWith(AndroidJUnit4::class)
class Migration_32_33_Test {
    private val testDb = "migration-32-33-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate32To33_createsCardRunsTableAndIndexes() {
        helper.createDatabase(testDb, 32).close()

        val db = helper.runMigrationsAndValidate(testDb, 33, true, Migration_32_33)
        db.query("PRAGMA table_info(`card_runs`)").use { cursor ->
            val columns = buildList {
                while (cursor.moveToNext()) add(cursor.getString(1) to cursor.getString(2))
            }
            assertEquals(
                listOf(
                    "id" to "TEXT",
                    "parent_run_id" to "TEXT",
                    "card_name" to "TEXT",
                    "card_version" to "TEXT",
                    "trigger" to "TEXT",
                    "status" to "TEXT",
                    "started_at" to "INTEGER",
                    "updated_at" to "INTEGER",
                    "ended_at" to "INTEGER",
                    "args_summary" to "TEXT",
                    "output_summary" to "TEXT",
                    "error_code" to "TEXT",
                    "error_message" to "TEXT",
                ),
                columns,
            )
        }
        db.query("PRAGMA index_list(`card_runs`)").use { cursor ->
            val indexes = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
            assertTrue(indexes.contains("index_card_runs_started_at"))
            assertTrue(indexes.contains("index_card_runs_card_name_started_at"))
            assertTrue(indexes.contains("index_card_runs_status"))
        }
        db.close()
    }

    @Test
    fun migrate32To33_cardRunRowsRoundTripAndLegacyTablesRemain() {
        helper.createDatabase(testDb, 32).close()
        val db = helper.runMigrationsAndValidate(testDb, 33, true, Migration_32_33)

        db.insert(
            "card_runs",
            SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("id", "run-1")
                putNull("parent_run_id")
                put("card_name", "builtin.echo")
                put("card_version", "1.0.0")
                put("trigger", "用户")
                put("status", "succeeded")
                put("started_at", 10L)
                put("updated_at", 20L)
                put("ended_at", 20L)
                put("args_summary", "text")
                put("output_summary", "ok")
                putNull("error_code")
                putNull("error_message")
            },
        )
        db.query("SELECT card_name, status, output_summary FROM card_runs WHERE id = 'run-1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("builtin.echo", it.getString(0))
            assertEquals("succeeded", it.getString(1))
            assertEquals("ok", it.getString(2))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'ConversationEntity'").use {
            assertTrue("旧表必须仍存在", it.moveToFirst())
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use {
            val names = buildSet {
                while (it.moveToNext()) add(it.getString(0))
            }
            assertFalse("迁移不应残留临时 card_runs 表", names.contains("_new_card_runs"))
        }
        db.close()
    }
}
