package heizige.kk.khatkit.app.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 缺陷（offset 分页 API 用错 `LoadParams` 类型）的纯 JVM 证据。
 *
 * ## 之前的 bug
 *
 * `ConversationRepository` 的四个 offset 分页 API
 * （[ConversationRepository.getConversationsOfAssistantPage] /
 * [ConversationRepository.searchConversationsOfAssistantPage] /
 * [ConversationRepository.getUnfiledConversationsOfAssistantPage] /
 * [ConversationRepository.getConversationsOfFolderPage]）都构造
 * `PagingSource.LoadParams.Refresh(key = offset, ...)` 去直连 Room 生成的
 * `LimitOffsetPagingSource`。Room 2.8.5 的
 * `androidx.room.paging.util.RoomPagingUtil.getOffset(params, key, itemCount)` 对 `Refresh`
 * 做的是「刷新窗口夹取」：
 *
 * ```
 * Refresh -> if (key < itemCount - loadSize) key else max(0, itemCount - loadSize)
 * Append  -> key
 * ```
 *
 * 于是只要 `offset >= itemCount - limit`，Room 就**静默把 offset 夹到 `itemCount - limit`**，
 * 返回与上一页重叠的行，`nextKey` 也跟着错。`Append` 的 offset 恒为 `key`（即调用方给的
 * offset），才是 offset 分页该用的类型。
 *
 * ## 这里能验什么 / 不能验什么
 *
 * - 能验：用反射**真实调用 Room 2.8.5 的静态方法**
 *   `RoomPagingUtil.getOffset`，钉住 `Refresh` 会夹取、`Append` 不夹取这条语义
 *   （这正是修复的判据），以及「Repository 源码里四个 API 只出现 `Append`」的机械护栏。
 * - 不能验：把 `ConversationRepository` 的方法直接跑起来 —— 它要真实 Room/Android
 *   `AppDatabase` 运行时，而 `app` 的 `testImplementation` 只有 junit
 *   （room-testing 仅在 `androidTestImplementation`）。所以走反射语义 + 源码护栏这条权宜路径。
 */
class ConversationPageLoadParamsTest {

    private val loadParamsClass: Class<*> = Class.forName("androidx.paging.PagingSource\$LoadParams")

    private fun loadParamsCtor(type: String): java.lang.reflect.Constructor<*> {
        val cls = Class.forName("androidx.paging.PagingSource\$LoadParams\$$type")
        return cls.getConstructor(
            Any::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    }

    /** 真实反射调用 `androidx.room.paging.util.RoomPagingUtil.getOffset(params, key, itemCount)`。 */
    private fun roomOffset(isRefresh: Boolean, key: Int, loadSize: Int, itemCount: Int): Int {
        val roomPagingUtil = Class.forName("androidx.room.paging.util.RoomPagingUtil")
        val getOffset = roomPagingUtil.getMethod(
            "getOffset",
            loadParamsClass,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        val params = loadParamsCtor(if (isRefresh) "Refresh" else "Append")
            .newInstance(key as Any, loadSize as Any, false as Any)
        return getOffset.invoke(null, params, key as Any, itemCount as Any) as Int
    }

    // 1. 钉住「为什么必须是 Append」：Refresh 在越过夹取边界后会把 offset 静默夹到
    //    `itemCount - loadSize`，返回与上一页重叠的行。这是**描述性**断言（不是失败）。
    //    Rediscover 自 Room 2.8.5 字节码 RoomPagingUtil__RoomPagingUtilKt.getOffset。
    @Test
    fun refreshLoadParams_isClampedIntoLastWindow() {
        // itemCount=55, loadSize=20 => 夹取边界 itemCount - loadSize = 35
        assertEquals("offset=36 会被夹到 35", 35, roomOffset(true, 36, 20, 55))
        assertEquals("offset=40 会被夹到 35", 35, roomOffset(true, 40, 20, 55))
        assertEquals("offset=60 会被夹到 35", 35, roomOffset(true, 60, 20, 55))
        // 边界之内不夹
        assertEquals("offset=34 在边界内，不夹", 34, roomOffset(true, 34, 20, 55))
        assertEquals("offset=35 恰好落在边界上，结果仍是 35", 35, roomOffset(true, 35, 20, 55))
    }

    // 2. 修复的核心判据：Append 对所有 offset 都返回 offset 本身，不被 itemCount 夹取。
    @Test
    fun appendLoadParams_preservesOffsetEvenPastClampBoundary() {
        assertEquals("offset=36", 36, roomOffset(false, 36, 20, 55))
        assertEquals("offset=40", 40, roomOffset(false, 40, 20, 55))
        assertEquals("offset=60", 60, roomOffset(false, 60, 20, 55))
        assertEquals("offset=55", 55, roomOffset(false, 55, 20, 55))
    }

    // 3. 边界内两者一致；跨过边界后 Append 才是对的。这条把「修复前后差异只在越界区间」
    //    说清楚：首页 offset=0 无论哪种类型都返回 0，因此统一用 Append 不影响首屏。
    @Test
    fun refreshAndAppend_agreeInsideWindowAndDivergePastIt() {
        assertEquals(0, roomOffset(true, 0, 20, 55))
        assertEquals(0, roomOffset(false, 0, 20, 55))
        assertEquals(34, roomOffset(true, 34, 20, 55))
        assertEquals(34, roomOffset(false, 34, 20, 55))
        assertTrue(
            "越过夹取边界后 Append 必须不等于被夹的 Refresh",
            roomOffset(false, 40, 20, 55) != roomOffset(true, 40, 20, 55),
        )
    }

    // 4. 源码护栏：四个 offset 分页 API（共三处构造点，其中 loadConversationPage 被两个 API
    //    复用）必须用 Append，且不得再出现 Refresh。改回 Refresh 时这里立刻红。
    @Test
    fun repositorySource_usesAppendOnly() {
        val source = File(
            "src/main/java/heizige/kk/khatkit/app/core/data/repository/ConversationRepository.kt"
        ).readText()
        val appendCount = Regex("PagingSource\\.LoadParams\\.Append\\(").findAll(source).count()
        assertEquals("四个 API 的续读构造点应恰好 3 处 Append", 3, appendCount)
        assertTrue(
            "ConversationRepository 里不该再出现 LoadParams.Refresh",
            !source.contains("LoadParams.Refresh"),
        )
    }

    // 5. nextKey 结论：Repository 没有自己算 nextKey —— 三处都是直接透传 Room 的
    //    `result.nextKey`。Append 下 Room 的 queryDatabase 用
    //    `offset(==key) + data.size` 作为 nextKey，所以改 Append 后 nextKey 自然正确，
    //    无需在 Repository 侧另加逻辑；这里把它钉住，防止有人日后手搓 `offset + limit`。
    @Test
    fun repositorySource_delegatesNextKeyToRoom() {
        val source = File(
            "src/main/java/heizige/kk/khatkit/app/core/data/repository/ConversationRepository.kt"
        ).readText()
        assertEquals(
            "三处分页结果的 nextOffset 都应直接取 result.nextKey",
            3,
            Regex("nextOffset\\s*=\\s*result\\.nextKey").findAll(source).count(),
        )
    }
}
