package heizige.kk.khatkit.app.core.data.db.migrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * C1-P 纯 JVM 证据：**手写的 31→32 迁移 DDL 与 Room 导出的 schema 完全一致**，
 * 以及「导入的 cards 真的接上了落库链路」这几条结构性事实。
 *
 * 为什么这个测试重要：手写 `Migration` 的 DDL 只要和实体声明差一个默认值 / NOT NULL /
 * 列名，Room 的 `TableInfo` 校验就会在**真机升级那一刻**抛
 * `IllegalStateException: Migration didn't properly handle`，本地完全测不出来。
 * 这里把 `Migration_31_32.kt` 里暴露的 DDL 常量与
 * `app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/32.json`
 * 里 Room 自己生成的 `createSql` 逐项比对，漂移会在单测里立刻炸。
 *
 * ⚠️ 本测试**不是**迁移往返（不建旧库、不执行 SQL）。真正的「建 v31 库 → migrate →
 * 校验 schema + 旧数据仍在」往返需要设备，见 `Migration_31_32_Test`（androidTest）与
 * `tools/verification/c1p_migration_31_32_replay.py`（主机侧 sqlite3 重放）。
 *
 * ⚠️ 本工程 `app/build.gradle.kts` 只有 junit，**没有 Robolectric / room-testing /
 * coroutines-test**：DAO 在真实 SQLite 上的行为、Room 的 `TableInfo` 校验、以及 UI 上
 * 刷新后卡片是否真在，**都无法在 JVM 层验证**。最后三条是**源码文本护栏**，只能钉住
 * 「这条链路在源码里接上了」这一件事本身，钉不住运行时行为。
 */
class ConversationGroupCardsSchemaTest {

    private val schema: Map<String, Any> by lazy { loadSchema(32) }

    // ------------------------------------------------------- 31.json → 32.json

    @Test
    fun onlyConversationEntityChangedBetween31And32() {
        val old = loadSchema(31)
        assertEquals(
            "31→32 不允许新增或删除表",
            entities(old).keys,
            entities(schema).keys,
        )
        entities(old).keys.forEach { table ->
            if (table == "ConversationEntity") return@forEach
            assertEquals(
                "除 ConversationEntity 外，表 $table 不允许有任何变化（列/主键/索引/默认值）",
                entities(old)[table],
                entities(schema)[table],
            )
        }
        val oldColumns = columns(old, "ConversationEntity").keys
        val newColumns = columns(schema, "ConversationEntity").keys
        assertEquals("只允许新增 group_cards 一列", setOf("group_cards"), newColumns - oldColumns)
        assertEquals("不允许删列", emptySet<String>(), oldColumns - newColumns)
    }

    @Test
    fun databaseVersionIs32AndMigrationIsRegisteredFor31To32() {
        assertEquals(32L, database()["version"])
        assertEquals(31, Migration_31_32.startVersion)
        assertEquals(32, Migration_31_32.endVersion)
        assertFalse(
            "identityHash 必须随版本变化而变化（Room 用它判定是否需要重跑校验）",
            database()["identityHash"] == loadSchema(31)["database"]!!.let { (it as Map<*, *>)["identityHash"] },
        )
    }

    // ------------------------------------------------- ConversationEntity 新列

    @Test
    fun groupCardsColumnMatchesEntityDeclaration() {
        val col = columns(schema, "ConversationEntity")["group_cards"] ?: error("缺 group_cards 列")
        assertEquals("TEXT", col["affinity"])
        assertEquals("必须 NOT NULL", true, col["notNull"])
        assertEquals(
            "默认必须与实体 @ColumnInfo(defaultValue = \"\") 一致，否则真机升级时 Room 校验抛异常",
            "''",
            col["defaultValue"],
        )
        assertEquals("fieldPath 必须对应 ConversationEntity.groupCards", "groupCards", col["fieldPath"])
    }

    @Test
    fun explicitAddColumnSqlMatchesRoomGeneratedCreateSql() {
        // Room 生成的 createSql 应当就是「31 版那句 + 尾部追加 group_cards 列」，
        // 手写 ADD COLUMN 追加的列定义必须与它同口径（去反引号 / IF NOT EXISTS 后逐字相等）。
        val canonical32 = canonical(createSql(schema, "ConversationEntity"), "ConversationEntity")
        val canonical31 = canonical(createSql(loadSchema(31), "ConversationEntity"), "ConversationEntity")
        val primaryKeyClause = ", primary key(id))"
        assertTrue(
            "判据失效：32.json 的 createSql 结构与预期不符（末尾不是主键子句）",
            canonical32.endsWith(primaryKeyClause),
        )
        assertEquals(
            "手写迁移只加一列；32.json 的 createSql 必须等于 31.json 那句加这一列",
            canonical31.removeSuffix(primaryKeyClause) + ", group_cards text not null default ''",
            canonical32.removeSuffix(primaryKeyClause),
        )
        assertEquals(
            "手写 ADD COLUMN 必须与 32.json 里这一列的定义同口径（TEXT NOT NULL DEFAULT ''）",
            "alter table conversationentity add column group_cards text not null default ''",
            canonical(CONVERSATION_ADD_GROUP_CARDS_SQL, "ConversationEntity"),
        )
        assertTrue(
            "手写迁移必须用 ALTER TABLE ADD COLUMN（不能重建表）",
            CONVERSATION_ADD_GROUP_CARDS_SQL.contains("ADD COLUMN", ignoreCase = true),
        )
        assertFalse(
            "迁移里不允许 DROP TABLE（会连带删掉 FTS5 触发器，见 Migration_30_31.kt:79-84）",
            CONVERSATION_ADD_GROUP_CARDS_SQL.contains("DROP TABLE", ignoreCase = true),
        )
        assertFalse(
            "迁移里不允许 RENAME（同样会重建父表、拆掉 message_node 的外键）",
            CONVERSATION_ADD_GROUP_CARDS_SQL.contains("RENAME", ignoreCase = true),
        )
    }

    // --------------------------------------------------- 落库链路的源码文本护栏

    @Test
    fun migrationIsRegisteredInFactoryAndNotAutoMigrated() {
        val factory = sourceOf("src/main/java/heizige/kk/khatkit/app/core/data/db/AppDatabaseFactory.kt")
        val database = sourceOf("src/main/java/heizige/kk/khatkit/app/core/data/db/AppDatabase.kt")
        val importBlock = migrationBlockOf(factory)
        // 锚点存在性：切不出内容就红，绝不让它退化成「整个文件」（见 migrationBlockOf 的 KDoc）
        assertTrue(
            "AppDatabaseFactory 里必须找得到 `.addMigrations(` 注册块（长度 ${factory.length}，" +
                "但切出 ${importBlock.length}）；锚点缺失时本护栏会退化成扫全文而恒真",
            importBlock.isNotEmpty(),
        )
        // 反空跑（1）：切出来的必须只是 addMigrations 的实参列表，不含文件头的 import 区
        assertFalse(
            "切分退化：importBlock 里出现了 `import androidx.room` —— 说明 `.addMigrations(` " +
                "锚点没命中、切分退化成扫全文，下面两条 contains 断言已被文件头的 import 行满足",
            importBlock.contains("import androidx.room"),
        )
        // 反空跑（2）：实参列表必须远小于整个文件（465 vs 5836 量级，留 4 倍余量）
        assertTrue(
            "切分退化：importBlock 长度 ${importBlock.length} 相对整个文件 ${factory.length} " +
                "太大了，说明切的不是 addMigrations 实参列表",
            importBlock.length * 4 < factory.length,
        )
        assertTrue(
            "AppDatabaseFactory.addMigrations 必须注册 Migration_31_32",
            importBlock.contains("Migration_31_32"),
        )
        assertTrue("既有 Migration_30_31 的注册不能被摘掉", importBlock.contains("Migration_30_31"))
        assertTrue("AppDatabase 版本必须是 32", database.contains("version = 32"))
        assertFalse(
            "31→32 **刻意不用 AutoMigration**：Room 的 DROP TABLE 会删掉三个 FTS5 触发器",
            Regex("AutoMigration\\(from = 31").containsMatchIn(database),
        )
    }

    @Test
    fun repositoryMapsGroupCardsOnTheFullRowOnly() {
        val repo = sourceOf(
            "src/main/java/heizige/kk/khatkit/app/core/data/repository/ConversationRepository.kt"
        )
        assertEquals(
            "整行映射里 encodeRoleCards 必须恰好出现一次（写）",
            1,
            countOf(repo, "GroupChat.encodeRoleCards("),
        )
        assertEquals(
            "整行映射里 decodeRoleCards 必须恰好出现一次（读）",
            1,
            countOf(repo, "GroupChat.decodeRoleCards("),
        )
        assertTrue(
            "conversationSummaryToConversation 必须写明「刻意不读 group_cards」的理由",
            repo.contains("刻意不读 `group_cards`"),
        )
        val dao = sourceOf("src/main/java/heizige/kk/khatkit/app/core/data/db/dao/ConversationDAO.kt")
        assertFalse(
            "抽屉列表的 7 条手写列名列表不应引入 group_cards（列表不显示就别拉 persona blob）",
            dao.contains("group_cards"),
        )
    }

    @Test
    fun importPathStoresPayloadCardsButExportPathStaysGenerated() {
        val page = sourceOf("src/main/java/heizige/kk/khatkit/app/feature/chat/GroupChatPage.kt")
        val exportCard = sourceOf(
            "src/main/java/heizige/kk/khatkit/app/feature/chat/GroupExportCard.kt"
        )
        // 导入：payload.cards 真的被交给落库回调
        assertTrue(
            "导入必须把 payload.cards 一起交出去落库",
            page.contains("onSave(result.payload.config, result.payload.cards)"),
        )
        // 手动保存配置：传 null = 与角色卡无关，保留库里已有的一份
        assertTrue(
            "手动保存群配置必须传 null（不得清掉已落库的卡片）",
            page.contains("onSave(draft, null)"),
        )
        assertTrue(
            "落库取值必须是 importedCards ?: conversation.groupCards",
            page.contains("groupCards = importedCards ?: conversation.groupCards"),
        )
        // 导出：二维码与酒馆文件两条路径仍然只用现场生成的那一份，一行都没改
        val exportSites = listOf(
            "GroupChat.encodeQr(draft, groupExportRoleCards(draft.roles, settings))",
            "cards = groupExportRoleCards(config.roles, settings),",
        )
        exportSites.forEach { site ->
            assertTrue(
                "导出链路必须仍是现场生成：找不到 `$site`",
                (page + exportCard).contains(site),
            )
        }
        assertEquals(
            "导出链路的 groupExportRoleCards 调用点必须仍是 2 处（不允许引入第三个来源）",
            2,
            countOf(page + exportCard, "groupExportRoleCards("),
        )
        assertFalse(
            "导出链路不得读导入快照（persona 属于导出方那台机器）",
            (page + exportCard).contains("encodeQr(draft, conversation.groupCards"),
        )
    }

    // ------------------------------------------------------------------- helpers

    private fun sourceOf(relativePath: String): String = File(relativePath).readText()

    private fun countOf(source: String, needle: String): Int = source.split(needle).size - 1

    /**
     * 切出 `.addMigrations(...)` 的**实参列表**文本（不含 `.addMigrations(` 本身与收尾括号）。
     *
     * ⚠️ 这里刻意**不用** `substringAfter(".addMigrations(").substringBefore(")")`。Kotlin 的
     * `substringAfter` 在锚点找不到时**返回整个字符串**（不抛异常、不返回 null），于是
     * `importBlock.contains("Migration_31_32")` 会被文件里**任意位置**的同名文本满足。
     * 而 `AppDatabaseFactory.kt` 第 15-16 行正好就有两行
     * `import ...db.migrations.Migration_30_31` / `Migration_31_32` —— 也就是说一旦
     * `.addMigrations(` 这个锚点被改名/重构掉，切分退化成扫全文后，下面两条 contains
     * 断言**照样绿**，护栏看着在守、其实一个字都没守。
     *
     * 所以这里手写 `indexOf` 硬切：**锚点缺失或括号不配平就返回空串**，让调用方的
     * `assertTrue(isNotEmpty())` 立刻红掉，退化路径被彻底堵死。
     */
    private fun migrationBlockOf(source: String): String {
        val anchorAt = source.indexOf(ADD_MIGRATIONS_ANCHOR)
        if (anchorAt < 0) return ""
        // 锚点末字符就是 `(`，实参从它后面开始
        val openAt = anchorAt + ADD_MIGRATIONS_ANCHOR.length - 1
        val closeAt = source.indexOf(')', openAt + 1)
        if (closeAt < 0) return ""
        return source.substring(openAt + 1, closeAt)
    }

    private fun database(): Map<String, Any> = schema["database"] as Map<String, Any>

    private fun entities(schema: Map<String, Any>): Map<String, Map<String, Any>> =
        ((schema["database"] as Map<String, Any>)["entities"] as List<*>)
            .map { it as Map<String, Any> }
            .associateBy { it["tableName"] as String }

    private fun createSql(schema: Map<String, Any>, table: String): String =
        entities(schema)[table]?.get("createSql") as? String ?: error("schema 里没有表 $table")

    private fun columns(schema: Map<String, Any>, table: String): Map<String, Map<String, Any?>> =
        ((entities(schema)[table]?.get("fields")) as List<*>)
            .map { it as Map<String, Any?> }
            .associateBy { it["columnName"] as String }

    /**
     * 归一化成可比对的字符串：把 `${TABLE_NAME}` 换成真实表名、去掉 `IF NOT EXISTS`、
     * 去掉反引号、压掉空白与括号内侧空格、统一小写。与 `GroupRunSchemaTest` 同一口径。
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

    private fun loadSchema(version: Int): Map<String, Any> {
        val candidates = listOf(
            File("schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/$version.json"),
            File("app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/$version.json"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error(
                "找不到 Room 导出的 $version.json（试过 ${candidates.joinToString { it.path }}，" +
                    "工作目录=${File("").absolutePath}）。请先跑 :app:compileDebugKotlin 导出 schema。"
            )
        return SchemaMiniJson.parse(file.readText()) as Map<String, Any>
    }

    private companion object {
        /**
         * 切注册块的锚点。**刻意独立成常量**：变异检验要把它整体换成一个不存在的字符串，
         * 用来证明「锚点缺失 → 返回空串 → 断言红」这条退化路径真的被堵死了（而不是退化后仍绿）。
         */
        const val ADD_MIGRATIONS_ANCHOR = ".addMigrations("
    }
}

/**
 * 极小 JSON 解析器（够用于 Room 的 schema 导出：对象 / 数组 / 字符串 / 数字 / 布尔 / null）。
 *
 * 与 `GroupRunSchemaTest.kt` 里那份同名对象逐字相同。这里复制一份并改名为 `SchemaMiniJson`
 * （同包同名会 Redeclaration），而不是把既有测试文件的 `private object MiniJson` 改成
 * internal —— 既有测试一个字都不动。
 */
private object SchemaMiniJson {

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