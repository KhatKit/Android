package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import heizige.kk.khatkit.app.core.data.model.GroupChat

/**
 * 契约验收 C1-10：「筛选只过滤、不丢数据」。
 *
 * 抽屉的「全部 / 单聊 / 群聊」chip 之前是把 type 退化成对已加载分页页的内存
 * `pagingData.filter`，翻页会漏出别的类型、分页计数也会错。现在 type 只作为 SQL 参数
 * 传下去，选哪条查询的判定被抽成纯函数 [planConversationListQuery]，这里逐条钉死：
 *
 * - 只有搜索词 → 走搜索查询，且不传 type
 * - 搜索词 + 类型筛选 → 走**同一个**搜索查询，type 作为参数传下去
 * - 只有类型筛选 → 仍走未归档查询
 * - 「全部」chip → 不把 FILTER_ALL 传下去（等于不筛）
 * - 切 chip 只改参数、不换查询，因此不会重建/清空底层数据源
 *
 * 注意：DAO 的 SQL 本身（谓词文本、Room 的绑定顺序、真实分页计数）无法在 JVM 里验证——
 * 仓库的 testImplementation 只有 junit，没有 Robolectric / room-testing（见
 * MemorySpaceGateTest 的说明），那部分只能靠仪器测试。
 */
class ConversationListQueryPlanTest {

    private fun planOf(keyword: String, typeFilter: String) = planConversationListQuery(keyword, typeFilter)

    private fun queryOf(keyword: String, typeFilter: String) = planOf(keyword, typeFilter).query

    private fun typeArgOf(keyword: String, typeFilter: String) = planOf(keyword, typeFilter).typeArgument

    // 1. 只有搜索词、没有 type 筛选：走搜索查询，且不往下传 type
    @Test
    fun keywordOnly_goesToSearchQuery_withoutTypeArgument() {
        assertEquals(ConversationListQuery.SEARCH, queryOf("abc", GroupChat.FILTER_ALL))
        assertEquals("", typeArgOf("abc", GroupChat.FILTER_ALL))
        // 默认筛选值就是「全部」，不带 type 的旧调用形态也必须落到同一条查询
        assertEquals(ConversationListQuery.SEARCH, queryOf("abc", ""))
    }

    // 2. 搜索词 + type 筛选：仍是同一条搜索查询，type 作为参数传下去（不再有内存过滤兜底）
    @Test
    fun keywordWithTypeFilter_stillSearchQuery_andCarriesTypeArgument() {
        val group = planOf("abc", GroupChat.TYPE_GROUP)
        assertEquals(ConversationListQuery.SEARCH, group.query)
        assertEquals(GroupChat.TYPE_GROUP, group.typeArgument)

        val direct = planOf("abc", GroupChat.TYPE_DIRECT)
        assertEquals(ConversationListQuery.SEARCH, direct.query)
        assertEquals(GroupChat.TYPE_DIRECT, direct.typeArgument)

        // type 只能变参数，不能换查询——否则就是「另一条不带 type 的查询 + 内存过滤」
        assertEquals(group.query, direct.query)
    }

    // 3. 只有 type 筛选、没有搜索词：仍然走未归档查询
    @Test
    fun typeFilterOnly_stillGoesToUnfiledQuery() {
        assertEquals(ConversationListQuery.UNFILED, queryOf("", GroupChat.TYPE_DIRECT))
        assertEquals(GroupChat.TYPE_DIRECT, typeArgOf("", GroupChat.TYPE_DIRECT))
        assertEquals(ConversationListQuery.UNFILED, queryOf("", GroupChat.TYPE_GROUP))
        assertEquals(GroupChat.TYPE_GROUP, typeArgOf("", GroupChat.TYPE_GROUP))
        assertEquals(ConversationListQuery.UNFILED, queryOf("", GroupChat.FILTER_ALL))
    }

    // 4. 「全部」chip 不把 FILTER_ALL 当成一种类型传下去
    @Test
    fun filterAll_isNeverPassedDown_asTypeArgument() {
        listOf("", "abc", "   ").forEach { keyword ->
            assertEquals("", typeArgOf(keyword, GroupChat.FILTER_ALL))
        }
        // 未知/空白值同样折成空串（不筛），不把垃圾值送进 SQL
        assertEquals("", typeArgOf("abc", "   "))
    }

    // 5. 筛选只过滤、不丢数据：切 chip 的整个序列里，查询种类恒定，只有 type 参数在变，
    //    所以底层数据源不会被换成另一条查询（不会因此清空/重建列表）
    @Test
    fun switchingFilter_keepsSameQuery_andOnlyVariesTypeArgument() {
        val chipSequence = listOf(
            GroupChat.FILTER_ALL,
            GroupChat.TYPE_DIRECT,
            GroupChat.TYPE_GROUP,
            GroupChat.TYPE_DIRECT,
            GroupChat.FILTER_ALL,
        )
        val expectedArgs = listOf("", GroupChat.TYPE_DIRECT, GroupChat.TYPE_GROUP, GroupChat.TYPE_DIRECT, "")

        // 未归档态（无搜索词）
        assertEquals(
            List(chipSequence.size) { ConversationListQuery.UNFILED },
            chipSequence.map { queryOf("", it) },
        )
        assertEquals(expectedArgs, chipSequence.map { typeArgOf("", it) })

        // 搜索态（有搜索词）——同样恒定，两种状态来回切也不换查询
        assertEquals(
            List(chipSequence.size) { ConversationListQuery.SEARCH },
            chipSequence.map { queryOf("abc", it) },
        )
        assertEquals(expectedArgs, chipSequence.map { typeArgOf("abc", it) })
    }

    // 6. 关键搜索词：只有空白（含空串）才算「没有搜索词」，与改动前的 isNotBlank 判定一致
    @Test
    fun blankKeyword_isTreatedAsNoKeyword() {
        assertEquals(ConversationListQuery.UNFILED, queryOf("   ", GroupChat.TYPE_GROUP))
        assertEquals(ConversationListQuery.UNFILED, queryOf("", GroupChat.TYPE_GROUP))
        assertEquals(ConversationListQuery.SEARCH, queryOf(" a ", GroupChat.TYPE_GROUP))
    }

    // 7. 筛选值 → DAO 参数的折算本身就是那条空串语义，不引入第二种表达
    @Test
    fun typeArgumentMapping_onlyDistinguishesFilterAllFromConcreteType() {
        assertEquals(GroupChat.TYPE_DIRECT, GroupChat.TYPE_DIRECT.asConversationTypeArgument())
        assertEquals(GroupChat.TYPE_GROUP, GroupChat.TYPE_GROUP.asConversationTypeArgument())
        assertEquals("", GroupChat.FILTER_ALL.asConversationTypeArgument())
        assertEquals("", "".asConversationTypeArgument())
        assertEquals("", "  ".asConversationTypeArgument())
        // 未归档路原本就是这个折算，两条路共用
        assertEquals(
            GroupChat.TYPE_GROUP.asConversationTypeArgument(),
            GroupChat.TYPE_GROUP.takeUnless { it == GroupChat.FILTER_ALL }.orEmpty(),
        )
    }
}
