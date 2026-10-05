package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import heizige.kk.khatkit.app.core.data.db.dao.CONVERSATION_TYPE_PREDICATE_SQL
import java.io.File

/**
 * 抽屉列表 type 筛选下沉到 SQL 的两条结构性护栏。
 *
 * 这里断言的是**源码文本**，不是 SQL 执行结果：仓库 testImplementation 只有 junit，
 * 没有 Robolectric / room-testing，Room 查询只能在仪器测试里跑。所以能钉住的只有
 * 「内存过滤确实被删了」「两条查询确实共用同一个谓词常量」这两件事本身 ——
 * 谓词在真实 SQLite 上的行为（空串不筛、分页计数）仍然未经过 JVM 验证。
 */
class ConversationTypeFilterSourceGuardTest {

    private fun sourceOf(relativePath: String): String = File(relativePath).readText()

    private val drawerViewModelSource by lazy {
        sourceOf("src/main/java/heizige/kk/khatkit/app/feature/chat/ChatDrawerViewModel.kt")
    }

    private val conversationDaoSource by lazy {
        sourceOf("src/main/java/heizige/kk/khatkit/app/core/data/db/dao/ConversationDAO.kt")
    }

    /**
     * ViewModel 里不再有 PagingData 内存过滤：那个 filter 只作用于已加载的分页页，
     * 翻页会漏出别的类型，PagingData 的 itemCount 也会算错。type 只能在 SQL 里生效。
     */
    @Test
    fun drawerViewModel_hasNoInMemoryPagingFilter() {
        assertFalse(
            "ChatDrawerViewModel 不该再 import androidx.paging.filter",
            drawerViewModelSource.contains("import androidx.paging.filter"),
        )
        assertFalse(
            "ChatDrawerViewModel 不该再对 PagingData 做 filter（内存过滤只覆盖已加载页）",
            drawerViewModelSource.contains("pagingData.filter") ||
                drawerViewModelSource.contains("data.filter {"),
        )
    }

    /**
     * type 谓词只有一份文本，未归档路和搜索路都引用它 ——
     * `AND (:type = '' OR type = :type)`，空串 = 不筛，两条路不可能再长出第二种语义。
     */
    @Test
    fun bothQueries_shareOneTypePredicate_constant() {
        assertEquals(" AND (:type = '' OR type = :type)", CONVERSATION_TYPE_PREDICATE_SQL)

        val references = Regex("CONVERSATION_TYPE_PREDICATE_SQL").findAll(conversationDaoSource).count()
        // 1 次声明（const val 那一行）+ 2 次引用（未归档路 + 搜索路）
        assertEquals("两条 type 查询应共用同一谓词常量", 3, references)

        // 谓词只允许出现在常量定义里，不允许有人再裸写一份
        val inlineCopies = conversationDaoSource.split("(:type = '' OR type = :type)").size - 1
        assertEquals("type 谓词不允许出现第二份字面量", 1, inlineCopies)

        // 两条查询都真的带了 type 收窄：源码里是「字面量 + 常量」的未折叠拼接形式
        assertTrue(
            "搜索路应改用带 type 的查询",
            conversationDaoSource.contains("searchConversationsOfAssistantByType"),
        )
        assertTrue(
            "搜索路查询应保留关键字模糊匹配并接上共用谓词",
            conversationDaoSource.contains(
                "title LIKE '%' || :searchText || '%'\" + CONVERSATION_LIKE_ESCAPE_SQL + CONVERSATION_TYPE_PREDICATE_SQL"
            ),
        )
        assertTrue(
            "未归档路应继续按 folder_id = '' 收窄并共用同一谓词",
            conversationDaoSource.contains("folder_id = ''\" + CONVERSATION_TYPE_PREDICATE_SQL"),
        )
    }
}
