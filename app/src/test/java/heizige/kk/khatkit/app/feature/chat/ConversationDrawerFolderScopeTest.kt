package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import heizige.kk.khatkit.app.core.data.db.dao.CONVERSATION_TYPE_PREDICATE_SQL
import java.io.File

/**
 * B2 护栏：抽屉两条查询的 `folder_id` 口径**当前是不一致的**，这里把这个现状钉死。
 *
 * ## 现状（这就是被钉住的行为，不是被认可的行为）
 *
 * | 抽屉状态 | 走哪条查询 | 有没有 `folder_id` 条件 |
 * |---|---|---|
 * | 无搜索词 | `getUnfiledConversationsOfAssistantByType` | 有，`AND folder_id = ''` |
 * | 有搜索词 | `searchConversationsOfAssistantByType` | **没有** |
 *
 * 所以无搜索词时主列表只显示未归类会话，一输入搜索词，文件夹内的会话就混进同一份列表。
 *
 * ## 判断：这是遗漏，不是有意设计
 *
 * 依据（`git log -S` + commit message 都查过）：
 *
 * 1. `folder_id` 由 `a92248b2`「feat(chat): 新增会话文件夹分组功能」引入。那条 commit
 *    同时新建了未归档查询并给它加了 `folder_id = ''`，**却没动当时已在的搜索查询** ——
 *    引入者给新查询设了归属边界，忘了给旧搜索查询补同一道边界。
 * 2. 搜索进抽屉是后来的 `48c2c091`（一个大杂烩 UI commit，message 里没有任何一句提到
 *    文件夹）。它的 `when` 把 `keyword.isNotBlank()` 放在**第一**个分支，直接压过当时的
 *    `_selectedFolderId` 选择——从没有注释或 message 说明「搜索刻意跨文件夹」。
 * 3. `ad7ac808` 把抽屉里的文件夹选择整个删掉（文件夹改成二级页），主列表从此**只**表示
 *    未归类会话（`ChatDrawerViewModel` 的 KDoc 就是这么写的）。搜索路却没有跟着收窄，
 *    这次重构的 commit message 同样一字未提搜索口径 —— 两次改动都没人宣称过这个差异。
 * 4. 唯一的正面证据是 `ChatDrawerViewModel` 的 `ChatListQueryPlan` KDoc 写着
 *    「关键字为空 → UNFILED（与改动前一致）」，即作者把两条路当同一条语义在维护。
 *
 * `2f1d04a2`（C1）给搜索路补 `type` 谓词时，KDoc 明确说「与未归档路完全一致」——
 * 又是把两条路当同一口径的又一处证据，而 `folder_id` 恰好在这轮里被漏掉了。
 *
 * ## 为什么这里只钉现状、不改成一致
 *
 * 「搜索时跨文件夹」本身可以是一个合理的产品选择（用户搜一个词，多半希望翻遍所有
 * 会话，而不是只搜得到没归档的那一半）。而把它收窄成 `folder_id = ''` 会让**已归档的
 * 会话在抽屉里彻底搜不到**（二级页有没有搜索框还没验）。两种口径各有代价，属于产品
 * 决策，主管明确保留决策权。因此本测试断言的是**现状**：
 * `searchConversationsOfAssistantByType` 现在确实没有 `folder_id` 条件。哪天产品选了
 * 收窄口径，这条断言会红——那是预期的，红了就在同一个 commit 里把它翻过来。
 *
 * 与 `ConversationTypeFilterSourceGuardTest` 同样的限制：仓库 `testImplementation`
 * 只有 junit，没有 Robolectric / room-testing，这里只能断言**源码文本**，
 * 真实 SQLite 上的行为仍未验证。
 */
class ConversationDrawerFolderScopeTest {

    private fun sourceOf(relativePath: String): String = File(relativePath).readText()

    private val conversationDaoSource by lazy {
        sourceOf("src/main/java/heizige/kk/khatkit/app/core/data/db/dao/ConversationDAO.kt")
    }

    /**
     * 抽出一条 DAO 方法上的 `@Query` 注解原文，并把 Kotlin 字符串拼接**折叠成最终 SQL**：
     * 先把 `CONVERSATION_TYPE_PREDICATE_SQL` 这个常量名换成它的字面值，再去掉 `" + `
     * / ` + "` 这些拼接记号。不做常量替换的话，`"..." + CONVERSATION_TYPE_PREDICATE_SQL + "..."`
     * 折叠后只剩常量名，谓词文本根本不在里面。
     *
     * 注意不能用 `substringBeforeLast(")")`：SQL 文本自身带括号（`(:type = '' OR ...)`）。
     */
    private fun queryOf(methodName: String): String {
        val lines = conversationDaoSource.lines()
        val start = lines.indexOfFirst { it.trimStart().startsWith("fun $methodName(") }
        assertTrue("DAO 里找不到 $methodName", start >= 0)
        for (i in start - 1 downTo 0) {
            val line = lines[i].trim()
            if (line.startsWith("@Query(")) {
                val raw = lines.subList(i, start)
                    .joinToString(" ")
                    .trim()
                    .removePrefix("@Query(")
                    .removeSuffix(")")
                return raw
                    .replace("CONVERSATION_TYPE_PREDICATE_SQL", CONVERSATION_TYPE_PREDICATE_SQL)
                    .replace("\" + ", "")
                    .replace(" + \"", "")
            }
            // 中间隔着的 KDoc / 空行都跳过；遇到别的注解或别的 fun 就说明找错了位置
            if (line.isNotEmpty() && !line.startsWith("*") && !line.startsWith("/") && !line.startsWith("//")) {
                break
            }
        }
        error("$methodName 上方没有 @Query")
    }

    /**
     * 只取 `WHERE` 之后的部分。**必须**这样切：SELECT 列表里两条查询都有
     * `folder_id as folderId`（那是给列表项用的投影，不是筛选条件），拿整条 SQL 判
     * `folder_id` 会让「有没有 folder_id 条件」这条断言恒真。
     */
    private fun whereClauseOf(sql: String): String =
        sql.substringAfter("WHERE ").substringBefore(" ORDER BY")

    /**
     * 未归档路：有 `AND folder_id = ''`，这是抽屉主列表的语义（「未归入任何文件夹的会话」）。
     */
    @Test
    fun unfiledQuery_filtersByFolder() {
        val sql = queryOf("getUnfiledConversationsOfAssistantByType")
        assertTrue(
            "未归档查询的 WHERE 应带 folder_id = ''",
            whereClauseOf(sql).contains("AND folder_id = ''"),
        )
    }

    /**
     * 搜索路：**当前**没有 `folder_id` 条件 —— 这条断言锁的是现状，不是认可。
     * 改成收窄口径时（产品决策），本条会红，届时把它翻成 assertTrue。
     */
    @Test
    fun searchQuery_currentlyHasNoFolderCondition() {
        val where = whereClauseOf(queryOf("searchConversationsOfAssistantByType"))
        assertFalse(
            "搜索查询的 WHERE 当前不过滤 folder_id；这是 B2 已知的口径不一致，" +
                "收窄口径是待定的产品决策，不要顺手改这里",
            where.contains("folder_id"),
        )
        // 顺带钉住它确实还按 assistant + 标题关键字筛（别把搜索路改成别的东西）
        assertTrue(where.contains("assistant_id = :assistantId"))
        assertTrue(where.contains("title LIKE '%' || :searchText || '%'"))
    }

    /**
     * 两条路共用的东西仍然必须共用：type 谓词是同一个常量文本，空串 = 不筛。
     * 这条与 `folder_id` 无关，是防止以后有人只给一条路改谓词。
     */
    @Test
    fun bothQueries_stillShareTheTypePredicate() {
        // 1 次声明（const val 那一行）+ 2 次引用（未归档路 + 搜索路）
        assertEquals(
            3,
            Regex("CONVERSATION_TYPE_PREDICATE_SQL").findAll(conversationDaoSource).count(),
        )
        assertEquals(
            " AND (:type = '' OR type = :type)",
            CONVERSATION_TYPE_PREDICATE_SQL,
        )
        for (method in listOf(
            "getUnfiledConversationsOfAssistantByType",
            "searchConversationsOfAssistantByType",
        )) {
            assertTrue(
                "$method 应引用共用的 type 谓词常量",
                whereClauseOf(queryOf(method)).contains("(:type = '' OR type = :type)"),
            )
        }
    }

    /**
     * 抽屉的查询判定函数本身不受 `folder_id` 影响：有搜索词走 SEARCH，没有走 UNFILED。
     * 这条锁住「判定只看搜索词」这一事实 —— `folder_id` 的差异完全落在 SQL 文本里，
     * 不在判定里，所以改口径时这个纯函数一个字都不用动。
     */
    @Test
    fun queryPlan_decidesOnKeywordAlone() {
        assertEquals(ConversationListQuery.UNFILED, planConversationListQuery("", "").query)
        assertEquals(ConversationListQuery.SEARCH, planConversationListQuery("abc", "").query)
    }
}