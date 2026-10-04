package heizige.kk.khatkit.app.core.data.db.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.app.core.data.db.AppDatabase
import heizige.kk.khatkit.app.core.data.model.GroupChat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * C1-P 交付证据：Room 31 → 32 迁移（群聊导入的角色卡元数据真的落库）。
 *
 * 32 版只有一处变化：`ConversationEntity` 增加 `group_cards TEXT NOT NULL DEFAULT ''`。
 *
 * 迁移是**显式** `Migration_31_32`（不在 `@Database(autoMigrations = ...)` 里声明），
 * 所以这里必须把它作为 vararg 传给 `runMigrationsAndValidate`；生产路径的注册在
 * `AppDatabaseFactory.addMigrations(...)`。
 *
 * ⚠️ 这是 instrumentation 测试，**必须有设备/模拟器**。本工程没有 Robolectric 依赖
 * （`app/build.gradle.kts` 只有 `junit`），所以这一整类往返在离线环境**无法执行**：
 * 离线能跑的只有 `ConversationGroupCardsSchemaTest`（纯 JVM，比对 DDL 与导出的 32.json）
 * 与 `tools/verification/c1p_migration_31_32_replay.py`（主机侧 sqlite3 重放）。
 * 本文件尚未在任何设备上跑过——**别把它当成已验证证据**。
 */
@RunWith(AndroidJUnit4::class)
class Migration_31_32_Test {
    private val TEST_DB = "migration-31-32-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /** 1. 旧库打开不崩：空 31 库跑迁移后结构合法（Room TableInfo 校验通过）。 */
    @Test
    fun migrate31To32_opensLegacyDatabaseWithoutCrash() {
        helper.createDatabase(TEST_DB, 31).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 32, true, Migration_31_32)

        db.query("PRAGMA table_info(`ConversationEntity`)").use { c ->
            val columns = buildList { while (c.moveToNext()) add(c.getString(1) to c.getString(4)) }
            assertTrue(
                "ConversationEntity 缺 group_cards 列，实际=$columns",
                columns.any { it.first == "group_cards" },
            )
            assertEquals(
                "group_cards 默认值必须是 ''（与实体 defaultValue 一致）",
                "''",
                columns.first { it.first == "group_cards" }.second,
            )
        }
        // 显式 ADD COLUMN 不走「建 _new 表 → DROP → RENAME」，临时表不该出现
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            val tables = buildSet { while (c.moveToNext()) add(c.getString(0)) }
            assertFalse("_new_ConversationEntity 不应残留", tables.contains("_new_ConversationEntity"))
        }
        db.close()
    }

    /** 2. 旧行的 group_cards 迁移后是空串（不是 NULL），且其余列一字不改。 */
    @Test
    fun migrate31To32_legacyRowsGetEmptyStringAndKeepEverythingElse() {
        helper.createDatabase(TEST_DB, 31).apply {
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

        val db = helper.runMigrationsAndValidate(TEST_DB, 32, true, Migration_31_32)

        db.query(
            "SELECT type, group_config, group_cards FROM ConversationEntity WHERE id = 'conv-group-1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("GROUP", c.getString(0))
            assertEquals("""{"schema_version":1,"mode":"pipeline"}""", c.getString(1))
            assertEquals(
                "旧行迁移后 group_cards 必须是空串（NOT NULL DEFAULT ''），Repository 侧回落到 null",
                "",
                c.getString(2),
            )
            assertFalse("group_cards 不得是 NULL", c.isNull(2))
        }
        db.close()
    }

    /** 3. 角色卡元数据 blob 可写可读回，含非 ASCII persona 与 null 字段。 */
    @Test
    fun groupCardsBlobRoundTripsThroughRealSqlite() {
        helper.createDatabase(TEST_DB, 31).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 32, true, Migration_31_32)

        val blob = """[{"role_id":"r1","name":"阿斯莫德","assistant_id":"assistant-1",""" +
            """"card_id":"card-42","persona":"你是一个冷静的记录者。","avatar_ref":null}]"""
        // 用绑定实参而不是字符串拼接：persona 里的引号/中文不参与 SQL 解析，
        // 断言的才是「blob 逐字节存得进去也读得回来」这一件事。
        insertConversation(db, id = "conv-cards", groupCards = blob)
        db.query("SELECT group_cards FROM ConversationEntity WHERE id = 'conv-cards'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("blob 必须逐字节读回", blob, c.getString(0))
        }

        // 空列表与空串是两个不同的值：null（从没导入过）vs []（导入过但没有卡）
        insertConversation(db, id = "conv-empty", groupCards = "[]")
        db.query("SELECT group_cards FROM ConversationEntity WHERE id = 'conv-empty'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("[]", c.getString(0))
        }

        insertConversation(db, id = "conv-legacy", groupCards = "", type = GroupChat.TYPE_DIRECT)
        db.query("SELECT group_cards FROM ConversationEntity WHERE id = 'conv-legacy'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("", c.getString(0))
        }
        db.close()
    }

    private fun insertConversation(
        db: SupportSQLiteDatabase,
        id: String,
        groupCards: String,
        type: String = GroupChat.TYPE_GROUP,
    ) {
        db.query(
            "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at," +
                " suggestions, type, group_config, group_cards) VALUES (?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any>(id, "assistant-1", "群", "[]", 1L, 2L, "[]", type, "", groupCards),
        )
    }

    /**
     * 4. 表身份没被换掉：`sqlite_master.sql` 是「31 版那句 + 追加的列定义」，
     *    说明用的是 ALTER 而不是 DROP + 重建（后者会删掉 memory_chunks 的 FTS5 触发器，
     *    还会拆掉 message_node 的外键）。
     */
    @Test
    fun migrate31To32_usesAlterAndDoesNotRebuildTheTable() {
        val legacySql = helper.createDatabase(TEST_DB, 31).let { db ->
            db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'ConversationEntity'")
                .use { c ->
                    c.moveToFirst()
                    c.getString(0)
                }.also { db.close() }
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 32, true, Migration_31_32)
        val migratedSql = db.query(
            "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'ConversationEntity'"
        ).use { c ->
            c.moveToFirst()
            c.getString(0)
        }
        assertEquals(
            "建表 SQL 必须等于原 SQL 末尾追加 group_cards 列（证明是原地 ADD COLUMN）",
            legacySql.replace(
                "PRIMARY KEY(`id`)",
                "`group_cards` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`)",
            ),
            migratedSql,
        )
        db.close()
    }

    /** 5. 子表外键仍指向 ConversationEntity（重建父表会拆掉它）。 */
    @Test
    fun migrate31To32_keepsMessageNodeForeignKey() {
        helper.createDatabase(TEST_DB, 31).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 32, true, Migration_31_32)
        db.query("PRAGMA foreign_key_list(`message_node`)").use { c ->
            val targets = buildList { while (c.moveToNext()) add(c.getString(2)) }
            assertTrue(
                "message_node 的外键仍应指向 ConversationEntity，实际=$targets",
                targets.contains("ConversationEntity"),
            )
        }
        db.close()
    }

    /** 6. 迁移后 Room 自己声明的新列默认值与实体一致（Room 写入路径不依赖默认值，但钉住它）。 */
    @Test
    fun migrate31To32_groupCardsIsNotNullWithEmptyDefault() {
        helper.createDatabase(TEST_DB, 31).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 32, true, Migration_31_32)
        db.query("PRAGMA table_info(`ConversationEntity`)").use { c ->
            var found = false
            while (c.moveToNext()) {
                if (c.getString(1) != "group_cards") continue
                found = true
                assertEquals("TEXT", c.getString(2))
                assertEquals("必须 NOT NULL", 1, c.getInt(3))
                assertEquals("默认 ''", "''", c.getString(4))
                assertEquals("不是主键", 0, c.getInt(5))
            }
            assertTrue("PRAGMA 里没有 group_cards 列", found)
        }
        assertNull(
            "整行 SELECT 时 group_cards 不得为 NULL",
            db.query("SELECT group_cards FROM ConversationEntity LIMIT 1").use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            },
        )
        db.close()
    }
}