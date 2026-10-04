package heizige.kk.khatkit.app.core.data.db.migrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * C1-D 纯 JVM 证据：**手写的 30→31 迁移 DDL 与 Room 导出的 schema 完全一致**。
 *
 * 为什么这个测试重要：手写 `Migration` 的 DDL 只要和实体声明差一个默认值 /
 * NOT NULL / 主键 / 索引，Room 的 `TableInfo` 校验就会在**真机升级那一刻**抛
 * `IllegalStateException: Migration didn't properly handle`，本地完全测不出来。
 * 这里把 `Migration_30_31.kt` 里暴露的 DDL 常量与
 * `app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/31.json`
 * 里 Room 自己生成的 `createSql` 逐项比对，漂移会在单测里立刻炸。
 *
 * ⚠️ 本测试**不是**迁移往返（不建旧库、不执行 SQL）。真正的「建 v30 库 → migrate →
 * 校验 schema + 旧数据仍在」往返需要设备或 Robolectric，见 `Migration_30_31_Test`
 * （androidTest）。本测试只保证「迁移要建的表 == Room 期望的表」这个静态前提。
 */
class GroupRunSchemaTest {

    private val schema: Map<String, Any> by lazy { loadSchema() }

    // ---------------------------------------------------------------- group_runs

    @Test
    fun explicitCreateTableMatchesRoomGeneratedCreateTable() {
        val expected = canonical(entity("group_runs").createSql(), "group_runs")
        assertEquals(
            "手写 CREATE TABLE 必须与 Room 在 31.json 生成的 createSql 等价；" +
                "不一致会在真机 30→31 升级时被 TableInfo 校验拒绝",
            expected,
            canonical(GROUP_RUNS_CREATE_SQL, "group_runs"),
        )
    }

    @Test
    fun explicitIndicesMatchRoomGeneratedIndices() {
        val indices = entity("group_runs").indices()
        assertEquals(
            "group_runs 索引数量/名字与 Room 期望不一致",
            setOf(
                "index_group_runs_run_token",
                "index_group_runs_conversation_id_started_at",
                "index_group_runs_status",
            ),
            indices.map { it["name"] }.toSet(),
        )
        val handWritten = mapOf(
            "index_group_runs_run_token" to GROUP_RUNS_RUN_TOKEN_INDEX_SQL,
            "index_group_runs_conversation_id_started_at" to GROUP_RUNS_CONVERSATION_STARTED_AT_INDEX_SQL,
            "index_group_runs_status" to GROUP_RUNS_STATUS_INDEX_SQL,
        )
        indices.forEach { index ->
            val name = index["name"] as String
            val sql = handWritten[name] ?: error("手写迁移缺少索引 $name 的 DDL")
            assertEquals(
                "索引 $name 与 Room 期望不一致",
                canonical(index.createSql(), "group_runs"),
                canonical(sql, "group_runs"),
            )
        }
    }

    @Test
    fun primaryKeyIsConversationIdAndRoundId() {
        // 契约：「同一群同一 round_id 只允许一个运行实例」必须由主键在数据库级兜底。
        val pk = entity("group_runs").primaryKey()
        assertEquals(listOf("conversation_id", "round_id"), pk["columnNames"])
        assertEquals(false, pk["autoGenerate"])
    }

    @Test
    fun runTokenColumnIsNotNullAndUnique() {
        val runToken = columns("group_runs")["run_token"] ?: error("group_runs 缺 run_token 列")
        assertEquals("TEXT", runToken["affinity"])
        assertEquals(true, runToken["notNull"])

        val uniqueColumns = entity("group_runs").indices()
            .filter { it["unique"] == true }
            .map { index -> (index["columnNames"] as List<*>).joinToString(",") }
        assertEquals(
            "run_token 必须有唯一索引（两次执行不得共用同一令牌）",
            listOf("run_token"),
            uniqueColumns,
        )
    }

    @Test
    fun budgetColumnsExistWithContractSemantics() {
        val cols = columns("group_runs")
        // 已用 / 上限：INTEGER NOT NULL DEFAULT 0（0 表示不限）
        listOf("spent_tokens", "token_limit").forEach {
            val c = cols[it] ?: error("group_runs 缺 $it")
            assertEquals("$it 必须是 INTEGER", "INTEGER", c["affinity"])
            assertEquals("$it 必须 NOT NULL", true, c["notNull"])
            assertEquals("$it 默认 0", "0", c["defaultValue"])
        }
        // 未运行角色 / 已提交角色：TEXT NOT NULL DEFAULT ''（JSON 数组字符串）
        listOf("skipped_role_ids", "committed_role_ids").forEach {
            val c = cols[it] ?: error("group_runs 缺 $it")
            assertEquals("$it 必须是 TEXT", "TEXT", c["affinity"])
            assertEquals("$it 必须 NOT NULL", true, c["notNull"])
            assertEquals("$it 默认空串", "''", c["defaultValue"])
        }
        // 超预算原因 / 失败节点信息
        listOf("reason", "error_message").forEach {
            val c = cols[it] ?: error("group_runs 缺 $it")
            assertEquals("$it 必须是 TEXT", "TEXT", c["affinity"])
            assertEquals("$it 默认空串", "''", c["defaultValue"])
        }
        // 溯源：conversation_id / round_id / status / run_token NOT NULL
        listOf("conversation_id", "round_id", "status").forEach {
            assertEquals("$it 必须 NOT NULL", true, cols[it]!!["notNull"])
        }
        // 时间戳：started_at / updated_at NOT NULL，ended_at 可空（尚未收尾）
        assertEquals(true, cols["started_at"]!!["notNull"])
        assertEquals(true, cols["updated_at"]!!["notNull"])
        assertEquals(null, cols["ended_at"]!!["notNull"])
    }

    @Test
    fun groupRunsHasConversationStartedAtIndexForRecentRoundsLookup() {
        // 「按 conversationId 查最近 N 轮」与运行日志 UI 依赖这个索引
        assertTrue(
            "缺少 (conversation_id, started_at) 索引",
            entity("group_runs").indices().any {
                it["columnNames"] == listOf("conversation_id", "started_at")
            },
        )
        // 僵尸 RUNNING 回收依赖 status 索引
        assertTrue(
            "缺少 status 索引",
            entity("group_runs").indices().any { it["columnNames"] == listOf("status") },
        )
    }

    // ------------------------------------------------------- memory_chunks.role_id

    @Test
    fun memoryChunksRoleIdIsNullableWithNoDefault() {
        val roleId = columns("memory_chunks")["role_id"] ?: error("memory_chunks 缺 role_id 列")
        assertEquals("TEXT", roleId["affinity"])
        // 可空 + 无 DEFAULT：与 `ALTER TABLE ADD COLUMN role_id TEXT` 一致，
        // 且与实体 `roleId: String? = null`（无 defaultValue）一致。
        // 若实体给了 defaultValue，Room 校验与迁移 DDL 会不一致，真机升级直接崩。
        assertEquals(
            "role_id 必须可空（存量单聊记忆没有角色）；Room 导出里 notNull 为 null/false 均表示可空",
            null,
            roleId["notNull"],
        )
        assertEquals(
            "role_id 不能有 DEFAULT（否则与 ADD COLUMN DDL 不一致）",
            null,
            roleId["defaultValue"],
        )
    }

    @Test
    fun explicitAddColumnAndIndexSqlMatchRoomExpectation() {
        assertEquals(
            "手写 ADD COLUMN 必须是可空无默认的 TEXT",
            "alter table memory_chunks add column role_id text",
            canonical(MEMORY_CHUNKS_ADD_ROLE_ID_SQL, "memory_chunks"),
        )
        val expected = entity("memory_chunks").indices()
            .first { it["name"] == "index_memory_chunks_role_id" }
        assertEquals(
            canonical(expected.createSql(), "memory_chunks"),
            canonical(MEMORY_CHUNKS_ROLE_ID_INDEX_SQL, "memory_chunks"),
        )
    }

    // ------------------------------------------------------------ 迁移版本号本身

    @Test
    fun migrationIsRegisteredFor30To31() {
        assertEquals(30, Migration_30_31.startVersion)
        assertEquals(31, Migration_30_31.endVersion)
        assertEquals(31L, database()["version"])
    }

    @Test
    fun migrationDoesNotDropMemoryChunks() {
        // 契约取舍：显式 ALTER 而非 Room 自动迁移的「建 _new 表 → DROP → RENAME」，
        // 因为 DROP 会连带删掉 AppDatabaseFactory.onOpen 建的 memory_chunks FTS 触发器。
        val stmts = listOf(
            GROUP_RUNS_CREATE_SQL,
            GROUP_RUNS_RUN_TOKEN_INDEX_SQL,
            GROUP_RUNS_CONVERSATION_STARTED_AT_INDEX_SQL,
            GROUP_RUNS_STATUS_INDEX_SQL,
            MEMORY_CHUNKS_ADD_ROLE_ID_SQL,
            MEMORY_CHUNKS_ROLE_ID_INDEX_SQL,
        )
        stmts.forEach {
            assertFalse("迁移里不应出现 DROP TABLE：$it", it.contains("DROP TABLE", ignoreCase = true))
            assertFalse("迁移里不应出现 RENAME：$it", it.contains("RENAME", ignoreCase = true))
        }
        assertTrue(
            "迁移必须用 ALTER TABLE ADD COLUMN 加 role_id",
            MEMORY_CHUNKS_ADD_ROLE_ID_SQL.contains("ADD COLUMN", ignoreCase = true),
        )
    }

    // ------------------------------------------------------------------- helpers

    private fun database(): Map<String, Any> = schema["database"] as Map<String, Any>

    private fun entity(table: String): Map<String, Any> =
        (database()["entities"] as List<*>)
            .map { it as Map<String, Any> }
            .firstOrNull { it["tableName"] == table }
            ?: error("31.json 里没有表 $table")

    private fun Map<String, Any>.indices(): List<Map<String, Any>> =
        (this["indices"] as? List<*>)?.map { it as Map<String, Any> } ?: emptyList()

    private fun Map<String, Any>.primaryKey(): Map<String, Any> =
        this["primaryKey"] as Map<String, Any>

    private fun Map<String, Any>.createSql(): String = this["createSql"] as String

    private fun columns(table: String): Map<String, Map<String, Any?>> =
        (entity(table)["fields"] as List<*>)
            .map { it as Map<String, Any?> }
            .associateBy { it["columnName"] as String }

    /**
     * 归一化成可比对的字符串：把 `${TABLE_NAME}` 换成真实表名、去掉反引号、
     * 去掉 `IF NOT EXISTS`（Room 生成的 createSql 总是带，迁移里带不带都能跑）、
     * 压掉空白与括号内侧空格、统一小写。
     */
    private fun canonical(sql: String, table: String): String = sql
        .replace("\${TABLE_NAME}", table)
        .replace("IF NOT EXISTS ", "")
        .replace("`", "")
        .replace(Regex("\\s+"), " ")
        .replace("( ", "(")
        .replace(" )", ")")
        .trim()
        .lowercase()

    private fun loadSchema(): Map<String, Any> {
        val candidates = listOf(
            File("schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/31.json"),
            File("app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/31.json"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error(
                "找不到 Room 导出的 31.json（试过 ${candidates.joinToString { it.path }}，" +
                    "工作目录=${File("").absolutePath}）。请先跑 :app:compileDebugKotlin 导出 schema。"
            )
        return MiniJson.parse(file.readText()) as Map<String, Any>    }
}

/**
 * 极小 JSON 解析器（够用于 Room 的 schema 导出：对象 / 数组 / 字符串 / 数字 / 布尔 / null）。
 *
 * 不用 kotlinx.serialization 是因为 schema 的结构是 `Map<String, Any>`（异构），
 * 给它套 `@Serializable` DTO 反而更绕；也不引第三方 JSON 库，避免动
 * `app/build.gradle.kts` 的依赖。
 */
private object MiniJson {

        fun parse(text: String): Any? {
        val cursor = Cursor(text)
        val value = cursor.readValue()
        cursor.skipWs()
        require(cursor.atEnd()) { "JSON 尾部有多余内容，位置 ${cursor.pos}" }
        return value
    }

    private class Cursor(val src: String) {
        var pos = 0

        fun atEnd() = pos >= src.length

        fun skipWs() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        fun readValue(): Any? {
            skipWs()
            require(pos < src.length) { "JSON 意外结束，位置 $pos" }
            return when (val c = src[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't', 'f' -> readBoolean()
                'n' -> readNull()
                else -> {
                    require(c == '-' || c.isDigit()) { "位置 $pos 出现意外字符 '$c'" }
                    readNumber()
                }
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') {
                pos++
                return out
            }
            while (true) {
                skipWs()
                val key = readString()
                skipWs()
                expect(':')
                out[key] = readValue()
                skipWs()
                when (val c = next()) {
                    ',' -> Unit
                    '}' -> return out
                    else -> error("对象里出现意外字符 '$c'，位置 ${pos - 1}")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') {
                pos++
                return out
            }
            while (true) {
                out += readValue()
                skipWs()
                when (val c = next()) {
                    ',' -> Unit
                    ']' -> return out
                    else -> error("数组里出现意外字符 '$c'，位置 ${pos - 1}")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                when (val c = next()) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = next()) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = src.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> error("非法转义 '\\$e'，位置 ${pos - 1}")
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readBoolean(): Boolean = when {
            src.startsWith("true", pos) -> { pos += 4; true }
            src.startsWith("false", pos) -> { pos += 5; false }
            else -> error("位置 $pos 不是布尔值")
        }

        private fun readNull(): Any? {
            require(src.startsWith("null", pos)) { "位置 $pos 不是 null" }
            pos += 4
            return null
        }

        private fun readNumber(): Any {
            val start = pos
            if (peek() == '-') pos++
            while (pos < src.length && (src[pos].isDigit() || src[pos] in ".eE+-")) pos++
            val raw = src.substring(start, pos)
            return raw.toLongOrNull() ?: raw.toDouble()
        }

        private fun peek(): Char = if (pos < src.length) src[pos] else '\u0000'

        private fun next(): Char {
            require(pos < src.length) { "JSON 意外结束，位置 $pos" }
            return src[pos++]
        }

        private fun expect(c: Char) {
            skipWs()
            require(next() == c) { "位置 ${pos - 1} 期望 '$c'" }
        }
    }
}
