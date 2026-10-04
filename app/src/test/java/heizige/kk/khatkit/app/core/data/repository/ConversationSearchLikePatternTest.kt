package heizige.kk.khatkit.app.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 缺陷一的纯 JVM 证据：会话标题搜索的 **LIKE 转义**（`%` / `_` / 转义字符本身变字面量）。
 *
 * ## 之前的 bug
 *
 * `ConversationDAO` 那 5 条搜索查询是
 * `title LIKE '%' || :searchText || '%'` 且**没有 `ESCAPE`**，于是 SQLite 把用户输入里的
 * `%` / `_` 当通配符：搜 `100%` 连不含 `%` 的会话一起命中，搜 `a_b` 命中 `axbxc`，
 * 搜单个 `%` 命中全部。**只多命中、不漏命中**，所以是「搜出来的比想搜的多」那种很难被
 * 当成 bug 报上来的坑。
 *
 * ## 这里能验什么 / 不能验什么
 *
 * - 能验：[escapeConversationLikePattern] 这个纯函数的逐字符输出（转义字符选型、
 *   二次转义、转义字符自身、空串/空白不变、中文与反斜杠与单引号原样透传），
 *   以及「DAO 里每条 `:searchText` 查询都接了 ESCAPE 子句」「Repository 的每个
 *   DAO 搜索调用点都过了转义函数」这两条源码护栏。
 * - 不能验：转义后的串**真的**在 SQLite 上匹配到字面量含该字符的标题 —— 那需要真实
 *   SQLite 引擎，仓库 `testImplementation` 只有 junit（没有 Robolectric / room-testing，
 *   room-testing 只在 `androidTestImplementation`）。那部分由
 *   `tools/verification/c1s_conversation_type_filter_replay.py` 用宿主 CPython 的
 *   `sqlite3` 真实引擎重放（见该脚本的 G7 / G11 组）。本文件不伪造那部分证据。
 */
class ConversationSearchLikePatternTest {

    private val escapeChar = CONVERSATION_LIKE_ESCAPE_CHAR

    // 1. 空串 / 纯空白：行为必须与改动前逐字不变（抽屉「空词等于没搜」仍只由
    //    planConversationListQuery 判定，SQL 层照旧不拦，空白也不能被"顺手 trim"掉）
    @Test
    fun emptyAndBlank_escapeToThemselves() {
        assertEquals("", escapeConversationLikePattern(""))
        assertEquals("   ", escapeConversationLikePattern("   "))
        assertEquals("\t\n ", escapeConversationLikePattern("\t\n "))
        // 空串仍是空串、纯空白仍是纯空白：blankness 没被转义改变，
        // 所以 SQL 层那条 `LIKE '%%'` 老路的行为与改动前逐字相同
        assertTrue(escapeConversationLikePattern("").isEmpty())
        assertTrue(escapeConversationLikePattern("   ").isBlank())
        assertEquals(
            "   ".length,
            escapeConversationLikePattern("   ").length,
        )
    }

    // 2. 字面量 '%' / '_' 各自被转义成一个「转义符 + 自身」
    @Test
    fun wildcardCharacters_becomeEscapedLiterals() {
        assertEquals("${escapeChar}%", escapeConversationLikePattern("%"))
        assertEquals("${escapeChar}_", escapeConversationLikePattern("_"))
        assertEquals("a${escapeChar}%b", escapeConversationLikePattern("a%b"))
        assertEquals("a${escapeChar}_b", escapeConversationLikePattern("a_b"))
    }

    // 3. 转义字符本身：用户输入里出现转义符时必须被转义成「两个转义符」。
    //    这条是最容易漏的：漏了它，用户搜「波浪号 ~」就会把 ~ 当普通字符，
    //    反而把含 ~ 的标题全排除掉（这次是**漏命中**，比多命中更严重）。
    @Test
    fun escapeCharacter_itselfIsEscapedExactlyOnce() {
        assertEquals("$escapeChar$escapeChar", escapeConversationLikePattern("~"))
        assertEquals("a${escapeChar}${escapeChar}b", escapeConversationLikePattern("a~b"))
        // 绝不能出现「转义符 + 三个转义符」这种二次转义的产物
        assertEquals(1, Regex("$escapeChar$escapeChar").findAll(escapeConversationLikePattern("~")).count())
        assertNotEquals(
            "${escapeChar}${escapeChar}${escapeChar}",
            escapeConversationLikePattern("~"),
        )
    }

    // 4. '%' 与 '_' 混合、且与转义字符同时出现：一次遍历里全部正确处理
    @Test
    fun mixedWildcardsAndEscapeCharacter() {
        assertEquals(
            "a${escapeChar}%b${escapeChar}_c${escapeChar}${escapeChar}d",
            escapeConversationLikePattern("a%b_c~d"),
        )
        // 三个字符连着：'~' 后面紧跟 '%'，若实现里先转义 '%' 再回处理 '~' 就会多出一个转义符
        assertEquals(
            "${escapeChar}${escapeChar}${escapeChar}%",
            escapeConversationLikePattern("~%"),
        )
        assertEquals(
            "${escapeChar}${escapeChar}${escapeChar}_",
            escapeConversationLikePattern("~_"),
        )
    }

    // 5. '%' 在首 / 中 / 尾三个位置
    @Test
    fun percentAtLeadingMiddleAndTrailingPosition() {
        assertEquals("${escapeChar}%abc", escapeConversationLikePattern("%abc"))
        assertEquals("ab${escapeChar}%cd", escapeConversationLikePattern("ab%cd"))
        assertEquals("abc${escapeChar}%", escapeConversationLikePattern("abc%"))
        assertEquals("${escapeChar}%${escapeChar}%", escapeConversationLikePattern("%%"))
        assertEquals(
            "${escapeChar}%a${escapeChar}%b${escapeChar}%",
            escapeConversationLikePattern("%a%b%"),
        )
    }

    // 6. 中文（含全角标点）逐 Char 原样透传：转义只认 '%' / '_' / 转义符三个字符
    @Test
    fun chineseText_isPassedThroughUntouched() {
        assertEquals("中文标题", escapeConversationLikePattern("中文标题"))
        assertEquals(
            "群聊（草稿）100${escapeChar}% 已完成",
            escapeConversationLikePattern("群聊（草稿）100% 已完成"),
        )
        assertEquals(
            "標題${escapeChar}_a${escapeChar}_b${escapeChar}_",
            escapeConversationLikePattern("標題_a_b_"),
        )
        // 零宽字符 / emoji 代理对：逐 Char 透传，不做规范化、不做大小写处理
        assertEquals(
            "👩‍💻 100${escapeChar}% \u200b",
            escapeConversationLikePattern("👩‍💻 100% \u200b"),
        )
    }

    // 7. 反斜杠**不**转义：它不是 LIKE 的特殊字符（选 ~ 做转义符就是为了避开它），
    //    用户搜「a\b」就该匹配标题里的字面量 a\b，而不是被吃掉一个字符
    @Test
    fun backslash_isNotTouched() {
        assertEquals("a\\b", escapeConversationLikePattern("a\\b"))
        assertEquals("C:\\Users\\me", escapeConversationLikePattern("C:\\Users\\me"))
        // 反斜杠原样 + '%' 照常转义：两者互不干扰
        assertEquals(
            "100\\${escapeChar}% 完成",
            escapeConversationLikePattern("100\\% 完成"),
        )
    }

    // 8. 单引号**不**转义：Room 走参数绑定，值不会进 SQL 字面量，转义它反而会
    //    把「it's」变成「it~'s」这种搜不到的结果
    @Test
    fun singleQuote_isNotTouched() {
        assertEquals("it's", escapeConversationLikePattern("it's"))
        assertEquals("''", escapeConversationLikePattern("''"))
        assertEquals("'; DROP TABLE x; --", escapeConversationLikePattern("'; DROP TABLE x; --"))
    }

    // 9. 转义字符的选型本身就是契约：换成 '%' / '_' / '\' 会让整条查询坏掉，
    //    所以这里把「不是哪几个字符」钉死，而不是只测当前值
    @Test
    fun escapeCharacter_isNotAWildcardNorABackslash() {
        assertNotEquals('%', escapeChar)
        assertNotEquals('_', escapeChar)
        assertNotEquals('\\', escapeChar)
        // 非字母数字：转义符不该出现在常见标题里（理由见该常量的 KDoc）
        assertFalse(escapeChar.isLetterOrDigit())
        // 非 ASCII 不引入编码链路：单字节 ASCII 字符，SQL 字面量 / Kotlin 字面量 / Room
        // 参数绑定三处都不需要任何额外转义
        assertTrue(escapeChar.code in 0x20..0x7E)
    }

    // 10. 拼进 SQL 的 ESCAPE 子句与字符常量逐字节一致（两份 const 漂移会在这里变红，
    //     而不是等到某条搜索结果莫名其妙）
    @Test
    fun escapeSqlLiteral_matchesTheEscapeCharacterConstant() {
        assertEquals(" ESCAPE '${CONVERSATION_LIKE_ESCAPE_CHAR}'", CONVERSATION_LIKE_ESCAPE_SQL)
        assertEquals(" ESCAPE '~'", CONVERSATION_LIKE_ESCAPE_SQL)
        // SQLite 要求 ESCAPE 的表达式求值后恰好 1 个字符
        assertEquals(1, CONVERSATION_LIKE_ESCAPE_SQL.trim().removePrefix("ESCAPE ")
            .removeSurrounding("'", "'").length)
    }

    // 11. 源码护栏：DAO 里每条用 :searchText 的查询都接了 ESCAPE 子句。
    //     （Room 的编译期 SQL 校验 + 真实引擎行为不在这里，见文件 KDoc）
    @Test
    fun daoSource_everySearchTextQueryCarriesEscapeClause() {
        val source = File(
            "src/main/java/heizige/kk/khatkit/app/core/data/db/dao/ConversationDAO.kt"
        ).readText()
        val queryLines = source.lines().filter { it.trimStart().startsWith("@Query(") }
        val searchQueries = queryLines.filter { it.contains(":searchText") }
        assertEquals("DAO 里应恰好 5 条 :searchText 查询", 5, searchQueries.size)
        for (line in searchQueries) {
            assertTrue(
                "带 :searchText 的 @Query 必须接 CONVERSATION_LIKE_ESCAPE_SQL：\n$line",
                line.contains("+ CONVERSATION_LIKE_ESCAPE_SQL"),
            )
            assertTrue(
                "带 :searchText 的 @Query 必须仍以 '%' 收尾的包含匹配开头：\n$line",
                line.contains("title LIKE '%' || :searchText || '%'"),
            )
        }
        // 反面：不允许存在「title LIKE ... :searchText」却不带 ESCAPE 的 @Query
        assertFalse(
            "DAO 里不该再有裸 LIKE :searchText",
            queryLines.any {
                it.contains("title LIKE") && it.contains(":searchText")
                    && !it.contains("CONVERSATION_LIKE_ESCAPE_SQL")
            },
        )
    }

    // 12. 源码护栏：ConversationRepository 的每个 DAO 搜索调用点都过了转义函数。
    //     这是「所有调用方都经过它」的机械证明 —— 新增搜索入口时忘了转，这里立刻红。
    //     注意两种写法都要抓：`conversationDAO.searchX(` 与换行缩进后的 `.searchX(`。
    @Test
    fun repositorySource_everyDaoSearchCallGoesThroughTheEscapeFunction() {
        val source = File(
            "src/main/java/heizige/kk/khatkit/app/core/data/repository/ConversationRepository.kt"
        ).readText()
        val calls = Regex("(?:conversationDAO\\s*\\.\\s*|\\n\\s*\\.\\s*)(search\\w*)\\(").findAll(source).toList()
        assertEquals(
            "Repository 里交给 DAO 的搜索调用点应恰好 5 个（漏一个/多一个都要显式改这里）",
            5,
            calls.size,
        )
        for (call in calls) {
            val name = call.groupValues[1]
            val body = enclosingMethod(source, call.range.first)
            val callAt = body.indexOf(".$name(")
            assertTrue("调用点 .$name( 应落在某个方法体里", callAt >= 0)
            // 转义必须发生在这个 DAO 调用的**实参里**（含命名的 `searchText = ...`），
            // 而不是方法体的别处：形参约定是「:searchText 必须是已转义片段」
            val callEnd = matchingParen(body, body.indexOf('(', callAt))
            val escapeAt = body.indexOf("escapeConversationLikePattern(")
            assertTrue(
                "DAO 搜索调用点 .$name( 所在方法必须过 escapeConversationLikePattern",
                escapeAt >= 0,
            )
            assertTrue(
                "转义调用必须落在 .$name( 的实参里（含命名的 searchText = ...）",
                escapeAt in callAt until callEnd,
            )
        }
    }

    /** 从 [openIdx] 处的 `(` 出发找配对的 `)`（本文件里没有含括号的字符串字面量）。 */
    private fun matchingParen(text: String, openIdx: Int): Int {
        var depth = 0
        for (i in openIdx until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        throw AssertionError("openIdx=$openIdx 的括号在方法体里配不平")
    }

    /** 取 [at] 所在的顶层方法体文本（从最近的 `fun `/`suspend fun ` 声明到下一个声明）。 */
    private fun enclosingMethod(text: String, at: Int): String {
        val decls = listOf("    fun ", "    suspend fun ")
        val start = decls.maxOf { p -> text.lastIndexOf(p, at) }
        check(start >= 0) { "at=$at 之前找不到方法声明" }
        val end = decls.minOf { p -> text.indexOf(p, at).let { if (it < 0) Int.MAX_VALUE else it } }
        return text.substring(start, end)
    }
}
