package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import heizige.kk.khatkit.app.core.data.db.dao.CONVERSATION_TYPE_PREDICATE_SQL
import heizige.kk.khatkit.app.core.data.model.GroupChat
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
     * 抽屉主列表与顶栏搜索关键字**解耦**：抽屉搜索改成「当前角色消息正文检索」
     * （`ChatDrawerViewModel.messageSearch`）后，主列表只剩「未归档」一条查询，关键字
     * 不再进 `combine`。以前把关键字塞进 combine 会让每敲一个键就重建一次分页数据源
     * （白重建 + 多打一次 SQL），而搜索态渲染的是 messageSearch，压根用不到它。
     *
     * 这条护栏钉住「不回头」：源码里不许再出现按标题搜索分页的那条查询，也不许再出现
     * 已删除的 `planConversationListQuery` 判定函数。
     */
    @Test
    fun drawerMainList_isDecoupledFromSearchKeyword() {
        assertFalse(
            "抽屉主列表不该再走按会话标题过滤的分页查询（搜索已改为消息正文检索）",
            drawerViewModelSource.contains("searchConversationsOfAssistantPaging"),
        )
        assertFalse(
            "planConversationListQuery 的 SEARCH 分支已删除，不该再出现这个判定函数",
            drawerViewModelSource.contains("planConversationListQuery"),
        )
        // 2026-10-17：抽屉改成「只有角色列表」（主人确认：去掉角色/聊天记录切换 tab、
        // 不再单独列会话、文件夹功能整体移除）。抽屉侧连分页会话列表也不再有，
        // 因此上面「只剩未归档一条查询」的正面断言随之反转成「一条都不许有」。
        assertFalse(
            "抽屉已不再列会话，不该再持有未归档会话的分页数据源",
            drawerViewModelSource.contains("getUnfiledConversationsOfAssistantPaging"),
        )
        assertFalse(
            "抽屉会话列表连同类型筛选一起删除，不该再出现 _typeFilter",
            drawerViewModelSource.contains("_typeFilter"),
        )
    }

    /** chip 值 → SQL type 参数：FILTER_ALL/空白折成空串（= 不筛），只有具体类型才传下去。 */
    @Test
    fun typeFilterValue_mapsToSqlParameter() {
        assertEquals(GroupChat.TYPE_DIRECT, GroupChat.TYPE_DIRECT.asConversationTypeArgument())
        assertEquals(GroupChat.TYPE_GROUP, GroupChat.TYPE_GROUP.asConversationTypeArgument())
        assertEquals("", GroupChat.FILTER_ALL.asConversationTypeArgument())
        assertEquals("", "".asConversationTypeArgument())
        assertEquals("", "   ".asConversationTypeArgument())
        // 与 DAO 里的空串语义同一套：不引入第二种表达
        assertEquals(
            GroupChat.TYPE_GROUP.asConversationTypeArgument(),
            GroupChat.TYPE_GROUP.takeUnless { it == GroupChat.FILTER_ALL }.orEmpty(),
        )
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
