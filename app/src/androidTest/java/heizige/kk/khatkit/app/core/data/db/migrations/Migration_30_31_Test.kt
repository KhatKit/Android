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
 * C1-D 交付证据：Room 30 → 31 迁移（旧库打开不崩 + 存量数据不丢 + run token 幂等）。
 *
 * 31 版的两个变化：
 * 1. 新建 `group_runs`（主键 `conversation_id + round_id`，`run_token` 唯一索引）——run log；
 * 2. `memory_chunks` 增加可空 `role_id` 列——群记忆按角色隔离的依据。
 *
 * 迁移是**显式** `Migration_30_31`（不在 `@Database(autoMigrations = ...)` 里声明），
 * 所以这里必须把它作为 vararg 传给 `runMigrationsAndValidate`；生产路径的注册在
 * `AppDatabaseFactory.addMigrations(...)`。
 *
 * ⚠️ 这是 instrumentation 测试，必须有设备/模拟器。本工程没有 Robolectric 依赖
 * （`app/build.gradle.kts` 只有 `junit`），所以「建 v30 库 → migrate → 断言」的往返
 * **无法在 JVM 上跑**；离线能跑的是 `GroupRunSchemaTest`（纯 JVM，比对 DDL 与导出的
 * 31.json）与 `tools/verification/c1d_migration_30_31_replay.py`（主机侧 sqlite3 重放）。
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

    /** 1. 旧库打开不崩：空 30 库跑迁移后表结构合法（Room TableInfo 校验通过）。 */
    @Test
    fun migrate30To31_opensLegacyDatabaseWithoutCrash() {
        helper.createDatabase(TEST_DB, 30).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true, Migration_30_31)

        val tables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) tables += c.getString(0)
        }
        assertTrue("group_runs 应由 30→31 迁移新建", tables.contains("group_runs"))
        // 显式 ALTER 不走「建 _new 表 → DROP → RENAME」，临时表不该出现
        assertFalse("_new_memory_chunks 不应残留", tables.contains("_new_memory_chunks"))

        db.query("SELECT role_id FROM memory_chunks LIMIT 1").use { c ->
            assertEquals("role_id", c.getColumnName(0))
        }
        db.close()
    }

    /** 2. group_runs 表结构与三个索引齐全，主键落在 conversation_id + round_id。 */
    @Test
    fun migrate30To31_groupRunsHasCompositePrimaryKeyAndIndices() {
        helper.createDatabase(TEST_DB, 30).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true, Migration_30_31)

        // column -> (type, notNull, pkPosition)
        val columns = mutableMapOf<String, Triple<String, Boolean, Int>>()
        db.query("PRAGMA table_info(`group_runs`)").use { c ->
            while (c.moveToNext()) columns[c.getString(1)] =
                Triple(c.getString(2), c.getInt(3) == 1, c.getInt(5))
        }
        listOf(
            "conversation_id", "round_id", "run_token", "status", "spent_tokens",
            "token_limit", "skipped_role_ids", "committed_role_ids", "reason",
            "error_message", "started_at", "updated_at", "ended_at",
        ).forEach { assertTrue("group_runs 缺列 $it", columns.containsKey(it)) }
        assertEquals("TEXT", columns["conversation_id"]!!.first)
        assertEquals("TEXT", columns["round_id"]!!.first)
        assertEquals("TEXT", columns["run_token"]!!.first)
        assertEquals("TEXT", columns["skipped_role_ids"]!!.first)
        assertEquals("TEXT", columns["committed_role_ids"]!!.first)
        assertEquals("INTEGER", columns["spent_tokens"]!!.first)
        assertEquals("INTEGER", columns["token_limit"]!!.first)
        assertEquals("INTEGER", columns["started_at"]!!.first)
        assertEquals("INTEGER", columns["updated_at"]!!.first)
        // 主键 = (conversation_id, round_id)，pk 序号 1 / 2
        assertEquals(1, columns["conversation_id"]!!.third)
        assertEquals(2, columns["round_id"]!!.third)
        listOf("conversation_id", "round_id", "run_token", "status").forEach {
            assertTrue("$it 必须 NOT NULL", columns[it]!!.second)
        }
        assertTrue("spent_tokens 必须 NOT NULL", columns["spent_tokens"]!!.second)
        assertTrue("skipped_role_ids 必须 NOT NULL", columns["skipped_role_ids"]!!.second)
        assertFalse("ended_at 必须可空", columns["ended_at"]!!.second)

        // 列默认值必须与实体声明一致（Room 校验同口径）
        val defaults = mutableMapOf<String, String?>()
        db.query("PRAGMA table_info(`group_runs`)").use { c ->
            while (c.moveToNext()) defaults[c.getString(1)] = if (c.isNull(4)) null else c.getString(4)
        }
        assertEquals("0", defaults["spent_tokens"])
        assertEquals("0", defaults["token_limit"])
        assertEquals("''", defaults["skipped_role_ids"])
        assertEquals("''", defaults["committed_role_ids"])
        assertEquals("''", defaults["reason"])
        assertEquals("''", defaults["error_message"])
        assertEquals(null, defaults["ended_at"])

        // 索引：run_token 唯一 + (conversation_id, started_at) + status
        val indices = mutableMapOf<String, Pair<Boolean, List<String>>>()
        db.query("PRAGMA index_list(`group_runs`)").use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)
                val unique = c.getInt(2) == 1
                val cols = mutableListOf<String>()
                db.query("PRAGMA index_info(`$name`)").use { ic ->
                    while (ic.moveToNext()) cols += ic.getString(2)
                }
                indices[name] = unique to cols
            }
        }
        assertEquals(
            listOf("run_token"),
            indices["index_group_runs_run_token"]!!.second,
        )
        assertTrue("run_token 索引必须唯一", indices["index_group_runs_run_token"]!!.first)
        assertEquals(
            listOf("conversation_id", "started_at"),
            indices["index_group_runs_conversation_id_started_at"]!!.second,
        )
        assertEquals(listOf("status"), indices["index_group_runs_status"]!!.second)

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

        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true, Migration_30_31)

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

        // rowid 未被改写（显式 ADD COLUMN 不重建表）
        db.query("SELECT MAX(rowid) FROM memory_chunks WHERE content = '存量记忆 B'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }

        // role_id 可空：新行可以写入 role_id
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

    /** 4. memory_chunks 的 role_id 索引由迁移创建，且老索引一个不少。 */
    @Test
    fun migrate30To31_memoryChunksGetsRoleIdIndexWithoutLosingOldOnes() {
        helper.createDatabase(TEST_DB, 30).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true, Migration_30_31)

        db.query("PRAGMA index_list(`memory_chunks`)").use { c ->
            val names = buildList { while (c.moveToNext()) add(c.getString(1)) }
            listOf("space_id", "source_message_id", "deleted_at", "role_id").forEach {
                assertTrue("memory_chunks 缺索引 $it，实际=$names", names.any { n -> n.contains(it) })
            }
        }
        db.close()
    }

    /**
     * 5. 主键在数据库层兜底幂等：同一 (conversationId, roundId) 第二条 INSERT 必须失败。
     *    run_token 唯一索引同样必须拒绝「同一令牌用于两次执行」。
     */
    @Test
    fun groupRunsPrimaryKeyAndRunTokenIndexRejectDuplicateRuns() {
        helper.createDatabase(TEST_DB, 31).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true)

        fun insertRun(
            conversationId: String,
            roundId: String,
            runToken: String,
            startedAt: Long,
        ) = db.execSQL(
            """
            INSERT INTO group_runs (conversation_id, round_id, run_token, status, started_at,
              updated_at, spent_tokens, token_limit, skipped_role_ids, committed_role_ids,
              reason, error_message)
            VALUES ('$conversationId', '$roundId', '$runToken', 'RUNNING', $startedAt,
              $startedAt, 0, 1000, '[]', '[]', '', '')
            """.trimIndent()
        )

        insertRun("conv-1", "round-msg-1", "token-a", 100)

        var dupRoundFailed = false
        try {
            insertRun("conv-1", "round-msg-1", "token-b", 101)
        } catch (e: Exception) {
            dupRoundFailed = true
        }
        assertTrue("同一 conversationId + roundId 第二条 run 必须被主键拒绝", dupRoundFailed)

        var dupTokenFailed = false
        try {
            insertRun("conv-1", "round-msg-2", "token-a", 102)
        } catch (e: Exception) {
            dupTokenFailed = true
        }
        assertTrue("同一 runToken 第二次使用必须被唯一索引拒绝", dupTokenFailed)

        // 不同群 / 不同轮次必须都能插入
        insertRun("conv-2", "round-msg-1", "token-c", 103)
        insertRun("conv-1", "round-msg-2", "token-d", 104)
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
            INSERT INTO group_runs (conversation_id, round_id, run_token, status, spent_tokens,
              token_limit, skipped_role_ids, reason, committed_role_ids, error_message,
              started_at, updated_at, ended_at)
            VALUES ('conv-3', 'round-msg-9', 'token-e', 'BUDGET_STOPPED', 1024, 1024,
              '["r2","r3"]', 'token_budget_exceeded', '["r1"]', '', 10, 20, 20)
            """.trimIndent()
        )

        db.query(
            "SELECT spent_tokens, token_limit, skipped_role_ids, reason, committed_role_ids, " +
                "error_message, started_at, updated_at, ended_at " +
                "FROM group_runs WHERE conversation_id = 'conv-3' AND round_id = 'round-msg-9'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1024, c.getInt(0))
            assertEquals(1024, c.getInt(1))
            assertEquals("""["r2","r3"]""", c.getString(2))
            assertEquals("token_budget_exceeded", c.getString(3))
            assertEquals("""["r1"]""", c.getString(4))
            assertEquals("", c.getString(5))
            assertEquals(10L, c.getLong(6))
            assertEquals(20L, c.getLong(7))
            assertEquals(20L, c.getLong(8))
        }

        // 预算更新语句（GroupRunDAO.updateBudget 的形状）可用
        db.execSQL(
            "UPDATE group_runs SET spent_tokens = 2048, token_limit = 2048, " +
                "skipped_role_ids = '[\"r3\"]', reason = 'token_budget_exceeded', " +
                "status = 'BUDGET_STOPPED', updated_at = 30 " +
                "WHERE conversation_id = 'conv-3' AND round_id = 'round-msg-9'"
        )
        db.query(
            "SELECT spent_tokens, skipped_role_ids, updated_at FROM group_runs " +
                "WHERE conversation_id = 'conv-3' AND round_id = 'round-msg-9'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2048, c.getInt(0))
            assertEquals("""["r3"]""", c.getString(1))
            assertEquals(30L, c.getLong(2))
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

        val db = helper.runMigrationsAndValidate(TEST_DB, 31, true, Migration_30_31)
        db.query("SELECT type, group_config FROM ConversationEntity WHERE id = 'conv-group-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("GROUP", c.getString(0))
            assertEquals("""{"schema_version":1,"mode":"pipeline"}""", c.getString(1))
        }
        db.close()
    }
}
