package heizige.kk.khatkit.app.core.data.db.migrations

import android.content.ContentValues
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

/**
 * C1-D 交付证据：Room 30 → 31 迁移（旧库打开不崩 + 存量数据不丢 + run token 幂等）。
 *
 * 31 版的两个变化：
 * 1. 新建 `group_runs`（含 `conversation_id + round_id` 唯一索引）——run token 幂等表；
 * 2. `memory_chunks` 增加可空 `role_id` 列——群记忆按角色隔离的依据。
 *
 * 注意：`AutoMigration(from = 30, to = 31)` 声明在 `AppDatabase` 上，`MigrationTestHelper`
 * 会自动读取 `@Database.autoMigrations()`，因此这里不需要（也不应该）再传显式 Migration。
 */
@RunWith(AndroidJUnit4::class)
class Migration_30_31_Test {
    private val TEST_DB = "migration-30-31-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /** 1. 旧库打开不崩：空 30 库跑迁移后表结构合法。 */
    @Test
    fun migrate30To31_opensLegacyDatabaseWithoutCrash() {
        helper.createDatabase(TEST_DB, 30).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true)

        val tables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) tables += c.getString(0)
        }
        assertTrue("group_runs 应由 30→31 迁移新建", tables.contains("group_runs"))

        db.query("SELECT role_id FROM memory_chunks LIMIT 1").use { c ->
            assertEquals("role_id", c.getColumnName(0))
        }
        db.close()
    }

    /** 2. group_runs 表结构与三个索引齐全，唯一索引落在 conversation_id + round_id。 */
    @Test
    fun migrate30To31_groupRunsHasUniqueIndexOnConversationAndRound() {
        helper.createDatabase(TEST_DB, 30).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true)

        db.query("PRAGMA table_info(`group_runs`)").use { c ->
            val columns = buildList {
                while (c.moveToNext()) add(c.getString(1) to c.getInt(2))
            }.toMap()
            listOf(
                "id", "conversation_id", "round_id", "status", "spent_tokens", "limit_tokens",
                "skipped_role_ids", "reason", "committed_role_ids", "error_message",
                "started_at", "ended_at",
            ).forEach { assertTrue("group_runs 缺列 $it", columns.containsKey(it)) }
            // TEXT 列
            assertEquals("TEXT", columns["conversation_id"])
            assertEquals("TEXT", columns["round_id"])
            assertEquals("TEXT", columns["skipped_role_ids"])
            assertEquals("TEXT", columns["committed_role_ids"])
            // INTEGER 列
            assertEquals("INTEGER", columns["spent_tokens"])
            assertEquals("INTEGER", columns["limit_tokens"])
            assertEquals("INTEGER", columns["started_at"])
        }

        val indices = mutableMapOf<String, Pair<Int, Boolean>>() // name -> (unique, columnCount)
        db.query("PRAGMA index_list(`group_runs`)").use { c ->
            while (c.moveToNext()) {
                indices[c.getString(1)] = c.getInt(2) to (c.getInt(4) == 1)
            }
        }
        val unique = indices.filterValues { it.first == 1 }.keys
        assertEquals("恰好一个唯一索引", 1, unique.size)
        assertTrue(
            "唯一索引名应含 conversation_id 与 round_id，实际=$unique",
            unique.any { it.contains("conversation_id") && it.contains("round_id") }
        )
        assertTrue(indices.keys.any { it.contains("conversation_id") && it.contains("started_at") })
        assertTrue(indices.keys.any { it.contains("status") })

        db.close()
    }

    /** 3. 存量数据不丢：30 库里的 memory_chunks 迁移后 role_id 为 NULL 且原字段完整。 */
    @Test
    fun migrate30To31_existingMemoryChunksSurviveWithNullRoleId() {
        helper.createDatabase(TEST_DB, 30).apply {
            insert("memory_spaces", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
                put("id", "__global__")
                put("kind", "GLOBAL")
                put("name", "")
                put("created_at", 1_700_000_000_000L)
            })
            insert("memory_chunks", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
                put("space_id", "__global__")
                put("content", "存量记忆 A")
                put("source_kind", "MESSAGE")
                put("source_message_id", "msg-legacy-1")
                put("source_ref_id", "ref-legacy-1")
                put("confidence", 0.75f)
                put("extracted_at", 1_700_000_000_001L)
                put("last_hit_at", 1_700_000_000_002L)
                put("created_at", 1_700_000_000_003L)
                put("updated_at", 1_700_000_000_004L)
                put("deleted_at", 1_700_000_000_005L)
                put("embedding", byteArrayOf(1, 2, 3, 4))
            })
            insert("memory_chunks", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
                put("space_id", "__global__")
                put("content", "存量记忆 B")
                put("source_kind", "MANUAL")
                put("confidence", 1.0f)
                put("extracted_at", 1_700_000_000_006L)
                put("last_hit_at", 0L)
                put("created_at", 1_700_000_000_007L)
                put("updated_at", 1_700_000_000_008L)
            })
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true)

        db.query("SELECT COUNT(*) FROM memory_chunks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }

        db.query(
            """
            SELECT id, space_id, content, source_kind, source_message_id, source_ref_id,
                   confidence, extracted_at, last_hit_at, created_at, updated_at,
                   deleted_at, embedding, role_id
            FROM memory_chunks WHERE content = '存量记忆 A'
            """.trimIndent()
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("__global__", c.getString(1))
            assertEquals("MESSAGE", c.getString(3))
            assertEquals("msg-legacy-1", c.getString(4))
            assertEquals("ref-legacy-1", c.getString(5))
            assertEquals(0.75f, c.getFloat(6), 1e-6f)
            assertEquals(1_700_000_000_001L, c.getLong(7))
            assertEquals(1_700_000_000_002L, c.getLong(8))
            assertEquals(1_700_000_000_003L, c.getLong(9))
            assertEquals(1_700_000_000_004L, c.getLong(10))
            assertEquals(1_700_000_000_005L, c.getLong(11))
            assertTrue(c.getBlob(12).contentEquals(byteArrayOf(1, 2, 3, 4)))
            assertTrue("存量行 role_id 必须为 NULL", c.isNull(13))
        }

        // role_id 可空：存量行全部为 NULL，且新行可以写入 role_id
        db.execSQL(
            "INSERT INTO memory_chunks (space_id, content, source_kind, confidence, " +
                "extracted_at, last_hit_at, created_at, updated_at, role_id) " +
                "VALUES ('group:c1:role:r1', '群记忆', 'MESSAGE', 1.0, 1, 0, 1, 1, 'r1')"
        )
        db.query("SELECT COUNT(*) FROM memory_chunks WHERE role_id IS NULL").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM memory_chunks WHERE role_id = 'r1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.close()
    }

    /** 4. memory_chunks 的 role_id 索引由迁移创建。 */
    @Test
    fun migrate30To31_memoryChunksGetsRoleIdIndex() {
        helper.createDatabase(TEST_DB, 30).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true)

        db.query("PRAGMA index_list(`memory_chunks`)").use { c ->
            val names = buildList { while (c.moveToNext()) add(c.getString(1)) }
            assertTrue(
                "memory_chunks 应有 role_id 索引，实际=$names",
                names.any { it.contains("role_id") }
            )
        }
        // 老索引不能因为重建表而丢失
        db.query("PRAGMA index_list(`memory_chunks`)").use { c ->
            val names = buildList { while (c.moveToNext()) add(c.getString(1)) }
            listOf("space_id", "source_message_id", "deleted_at").forEach {
                assertTrue("memory_chunks 索引 $it 丢失，实际=$names", names.any { n -> n.contains(it) })
            }
        }
        db.close()
    }

    /**
     * 5. 唯一索引在数据库层兜底幂等：同一 (conversationId, roundId) 第二条 INSERT 必须失败。
     */
    @Test
    fun groupRunsUniqueIndexRejectsSecondRunOfSameRound() {
        helper.createDatabase(TEST_DB, 31).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true)

        db.execSQL(
            """
            INSERT INTO group_runs (id, conversation_id, round_id, status, spent_tokens,
              limit_tokens, skipped_role_ids, committed_role_ids, started_at)
            VALUES ('token-a', 'conv-1', 'round-msg-1', 'RUNNING', 0, 1000, '[]', '[]', 100)
            """.trimIndent()
        )

        var failed = false
        try {
            db.execSQL(
                """
                INSERT INTO group_runs (id, conversation_id, round_id, status, spent_tokens,
                  limit_tokens, skipped_role_ids, committed_role_ids, started_at)
                VALUES ('token-b', 'conv-1', 'round-msg-1', 'RUNNING', 0, 1000, '[]', '[]', 101)
                """.trimIndent()
            )
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("同一 conversationId + roundId 第二条 run 必须被唯一索引拒绝", failed)

        // 不同群 / 不同轮次必须都能插入
        db.execSQL(
            """
            INSERT INTO group_runs (id, conversation_id, round_id, status, spent_tokens,
              limit_tokens, skipped_role_ids, committed_role_ids, started_at)
            VALUES ('token-c', 'conv-2', 'round-msg-1', 'RUNNING', 0, 1000, '[]', '[]', 102)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO group_runs (id, conversation_id, round_id, status, spent_tokens,
              limit_tokens, skipped_role_ids, committed_role_ids, started_at)
            VALUES ('token-d', 'conv-1', 'round-msg-2', 'RUNNING', 0, 1000, '[]', '[]', 103)
            """.trimIndent()
        )
        db.query("SELECT COUNT(*) FROM group_runs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(3, c.getInt(0))
        }
        db.close()
    }

    /** 6. 预算/未运行角色/超预算原因可完整落库并读回（List<String> 以 JSON 数组存储）。 */
    @Test
    fun groupRunsStoresBudgetSkipAndCommittedRolesAsJsonArray() {
        helper.createDatabase(TEST_DB, 31).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true)

        db.execSQL(
            """
            INSERT INTO group_runs (id, conversation_id, round_id, status, spent_tokens,
              limit_tokens, skipped_role_ids, reason, committed_role_ids, error_message,
              started_at, ended_at)
            VALUES ('token-e', 'conv-3', 'round-msg-9', 'COMMITTED', 1024, 1024,
              '["r2","r3"]', 'token_budget_exceeded', '["r1"]', NULL, 10, 20)
            """.trimIndent()
        )

        db.query(
            "SELECT spent_tokens, limit_tokens, skipped_role_ids, reason, committed_role_ids, " +
                "started_at, ended_at FROM group_runs WHERE id = 'token-e'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1024, c.getInt(0))
            assertEquals(1024, c.getInt(1))
            assertEquals("""["r2","r3"]""", c.getString(2))
            assertEquals("token_budget_exceeded", c.getString(3))
            assertEquals("""["r1"]""", c.getString(4))
            assertEquals(10L, c.getLong(5))
            assertEquals(20L, c.getLong(6))
        }

        // 预算更新语句（DAO updateBudget 的形状）可用
        db.execSQL(
            "UPDATE group_runs SET spent_tokens = 2048, limit_tokens = 2048, " +
                "skipped_role_ids = '[\"r3\"]', reason = 'token_budget_exceeded' WHERE id = 'token-e'"
        )
        db.query("SELECT spent_tokens, skipped_role_ids FROM group_runs WHERE id = 'token-e'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2048, c.getInt(0))
            assertEquals("""["r3"]""", c.getString(1))
        }
        db.close()
    }

    /** 7. 30 库里的群会话数据（Conversation.type/group_config）迁移后必须完好。 */
    @Test
    fun migrate30To31_groupConversationRowsSurvive() {
        helper.createDatabase(TEST_DB, 30).apply {
            insert("ConversationEntity", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
                put("id", "conv-group-1")
                put("assistant_id", "assistant-1")
                put("title", "群会话")
                put("nodes", "[]")
                put("create_at", 1L)
                put("update_at", 2L)
                put("suggestions", "[]")
                put("is_pinned", 0)
                put("custom_system_prompt", "")
                put("mode_injection_ids", "[]")
                put("lorebook_ids", "[]")
                put("workspace_cwd", "")
                put("folder_id", "")
                put("type", "GROUP")
                put("group_config", """{"schema_version":1,"mode":"pipeline"}""")
            })
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true)
        db.query("SELECT type, group_config FROM ConversationEntity WHERE id = 'conv-group-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("GROUP", c.getString(0))
            assertEquals("""{"schema_version":1,"mode":"pipeline"}""", c.getString(1))
        }
        db.close()
    }
}
