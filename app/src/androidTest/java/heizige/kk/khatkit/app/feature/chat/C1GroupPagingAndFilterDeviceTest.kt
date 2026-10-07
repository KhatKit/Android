package heizige.kk.khatkit.app.feature.chat

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.paging.PagingSource
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.RouteActivity
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.db.AppDatabase
import heizige.kk.khatkit.app.core.data.db.AppDatabaseFactory
import heizige.kk.khatkit.app.core.data.db.fts.MessageFtsManager
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.toMessageNode
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FilesRepository
import heizige.kk.khatkit.app.core.data.repository.LightConversationEntity
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.uuid.Uuid

/**
 * C1-10「单聊/群聊筛选」的第 6 轮设备侧补充：**分页数据源**与**逐 chip 全流程**。
 *
 * 背景：`C1GroupFilterAndAvatarUiTest` 已经把「筛选真按类型过滤、切回不丢」跑成了真机断言，
 * 但它只在一个方法里把 全部→单聊→群聊→全部 串了一遍，覆盖的是三条 chip 的**串联路径**，
 * 且没有触及底层 `PagingSource`。本类补齐两块（验收缺口）：
 *
 * 1. **`PagingSource` 真分页**：抽屉列表的数据源是 Room 的 `LimitOffsetPagingSource`
 *    （`ConversationDAO.getUnfiledConversationsOfAssistantByType` /
 *    `searchConversationsOfAssistantByType`，返回 `PagingSource<Int, LightConversationEntity>`），
 *    由 `ConversationRepository` 的 `Pager(PagingConfig(pageSize, initialLoadSize, ...))` 驱动。
 *    这里直接对**生产同一个 PagingSource**（同一 DAO 方法返回的对象，也是 Pager 的
 *    `pagingSourceFactory` 产物）做 `load(Refresh(...))`，断言首屏条数 = 生产
 *    `INITIAL_LOAD_SIZE`、后续页 = 生产 `PAGE_SIZE`、翻到底累计 == DB 行数且无重复。
 *    生产常量用反射读（`private const val`），避免在测试里另抄一份数字而漂移。
 *
 * 2. **逐 chip + 来回切换 + 混合列表渲染**：每片 chip 单独进入、单聊/群聊来回切两轮、
 *    每次断言列表内容与 chip 语义一致；切回后与初始可达集合**精确相等**；切换前后直读
 *    主库（全表 + 按 type 分组）计数必须逐字相同（「筛选只过滤、不丢数据」）。
 *    另外断言混合列表的渲染分支：只有群条目的合并语义节点同时带标题与「群」徽标，
 *    单聊条目不带（`ConversationList.kt:551-558` 的 `if (type == TYPE_GROUP) Text("群")`）。
 *
 * ## 事实（本轮只读调研结论，均以代码为准）
 *
 * - 抽屉里是 **3 个 chip**：全部 / 单聊 / 群聊（`ChatDrawer.kt:546-556`）；不存在第 4 个。
 * - 类型筛选**下沉到 SQL**：`ChatDrawerViewModel.conversations` 把 type 透传给 DAO，
 *   不在内存里 `pagingData.filter`（`ChatDrawerViewModel.kt:70-94`，护栏
 *   `ConversationTypeFilterSourceGuardTest` 钉死「无内存过滤 + 两条查询共用谓词」）。
 * - 分页是**真实 LIMIT/OFFSET 分页**：`pageSize = 20`、`initialLoadSize = 40`
 *   （`ConversationRepository.kt:50-53`），`enablePlaceholders = false`。
 *
 * ## 为什么不需要新增 testTag
 *
 * - 列表锚点 `drawer_conversation_list` 已在 `ChatDrawer.kt:571`；
 * - chip 是库组件 `KedgeFilterChip`，按文本「全部/单聊/群聊」即可唯一命中（`ChatDrawer.kt:554`）；
 * - 群徽标是纯 `Text("群")`（`ConversationList.kt:552-557`），精确文本即可定位，
 *   且行本身 `combinedClickable` 合并语义后，「群」与标题同属一个合并节点；
 * - PagingSource 断言走 DAO，不需要任何 UI 锚点。
 *
 * ⇒ 本轮**不碰 `app/src/main`**。
 *
 * ## 与既有测试的关系
 *
 * 独立新文件、独立 id 段（UI 夹具 `...f3xx`，分页夹具 `...f4xx`），不动
 * `C1GroupFilterAndAvatarUiTest`（`...f1xx`）与用户所有夹具（`...e2xx`）一个字节。
 */
@RunWith(AndroidJUnit4::class)
class C1GroupPagingAndFilterDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: ConversationRepository
    private lateinit var settingsStore: SettingsRepository
    private lateinit var fixtureConfig: GroupConfig
    private var scenario: ActivityScenario<RouteActivity>? = null

    /**
     * 生产分页参数，从 `ConversationRepository` 的 `private const val` 反射读回。
     * 不抄数字：抄一份就会和生产漂移，抄错时测试还会假装通过。
     */
    private val prodPageSize: Int = readRepositoryStaticInt("PAGE_SIZE")
    private val prodInitialLoadSize: Int = readRepositoryStaticInt("INITIAL_LOAD_SIZE")

    // ---------------- 固定 id ----------------
    // UI 夹具：f3xx（2 群 + 2 单聊）
    private val groupAId = Uuid.parse("0c1c0de5-0000-0000-0000-00000000f3a1")
    private val groupBId = Uuid.parse("0c1c0de5-0000-0000-0000-00000000f3a2")
    private val directAId = Uuid.parse("0c1c0de5-0000-0000-0000-00000000f3b1")
    private val directBId = Uuid.parse("0c1c0de5-0000-0000-0000-00000000f3b2")

    // 分页夹具：一个**专属助手 id**（conversation.assistant_id 没有外键约束，见 32.json），
    // 于是 `WHERE assistant_id = :dedicated` 的计数不会被用户真实数据污染。
    private val pagingAssistantId = "0c1c0de5-0000-0000-0000-00000000f4a0"
    private val pagingGroupIds: List<Uuid> = (0 until PAGING_GROUP_COUNT).map { pagingId(0x4000 + it) }
    private val pagingDirectIds: List<Uuid> = (0 until PAGING_DIRECT_COUNT).map { pagingId(0x4100 + it) }

    @Before
    fun setUp() {
        database = AppDatabaseFactory.create(context)
        repository = ConversationRepository(
            conversationDAO = database.conversationDao(),
            messageNodeDAO = database.messageNodeDao(),
            database = database,
            filesManager = FilesManager(context, FilesRepository(database.managedFileDao()), AppScope()),
            messageFtsManager = MessageFtsManager(database),
        )
        settingsStore = SettingsRepository(
            context = context,
            scope = AppScope(),
            pebbleEngine = dagger.Lazy { PebbleEngine.Builder().build() },
        )
        if (EVIDENCE_CLEARED.compareAndSet(false, true)) {
            listOf("chips", "mixed", "paging").forEach { case ->
                context.getExternalFilesDir(null)?.let { File(it, "c1-round6-$case.txt").delete() }
            }
        }
        seedUiFixtures()
        seedPagingFixtures()
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        if (::database.isInitialized) database.close()
    }

    // ==================================================================
    // 测 1：逐 chip 进入 + 来回切换 + 精确复原
    // ==================================================================

    @Test
    fun typeFilter_allChipsFilterIndividuallyAndRestoreExactly() {
        openGroupChat(groupAId)
        openDrawer()
        val list = compose.onNodeWithTag(DRAWER_LIST_TAG)

        // 初始「全部」：4 条 UI 夹具（2 群 + 2 单聊）都可达。
        assertReachable(list, GROUP_A)
        assertReachable(list, GROUP_B)
        assertReachable(list, DIRECT_A)
        assertReachable(list, DIRECT_B)
        val initialReachableSet = FIXTURE_TITLES.filter { isReachableQuietly(list, it) }.toSet()
        assertEquals(
            "初始「全部」下四条夹具必须全部可达",
            FIXTURE_TITLES.toSet(),
            initialReachableSet,
        )

        // ---- 单聊：只有单聊可达（逐个进入，不依赖上一步状态） ----
        clickChip(CHIP_DIRECT)
        compose.waitUntil(WAIT_MS) { !isReachableQuietly(list, GROUP_A) }
        assertFalse("「单聊」下群甲不可达", isReachableQuietly(list, GROUP_A))
        assertFalse("「单聊」下群乙不可达", isReachableQuietly(list, GROUP_B))
        assertEquals("「单聊」下不得出现群甲", 0, countInDrawer(GROUP_A))
        assertEquals("「单聊」下不得出现群乙", 0, countInDrawer(GROUP_B))
        assertReachable(list, DIRECT_A)
        assertReachable(list, DIRECT_B)
        assertFalse("「单聊」下不得出现任何群徽标", hasGroupBadgeNode())

        // ---- 群聊：只有群可达 ----
        clickChip(CHIP_GROUP)
        compose.waitUntil(WAIT_MS) { !isReachableQuietly(list, DIRECT_A) }
        assertFalse("「群聊」下单聊甲不可达", isReachableQuietly(list, DIRECT_A))
        assertFalse("「群聊」下单聊乙不可达", isReachableQuietly(list, DIRECT_B))
        assertEquals("「群聊」下不得出现单聊甲", 0, countInDrawer(DIRECT_A))
        assertEquals("「群聊」下不得出现单聊乙", 0, countInDrawer(DIRECT_B))
        assertReachable(list, GROUP_A)
        assertReachable(list, GROUP_B)
        assertTrue("「群聊」下必须出现群徽标", hasGroupBadgeNode())

        // ---- 来回切：再回单聊，必须与第一次单聊完全一致 ----
        clickChip(CHIP_DIRECT)
        compose.waitUntil(WAIT_MS) { !isReachableQuietly(list, GROUP_A) }
        assertFalse(isReachableQuietly(list, GROUP_A))
        assertFalse(isReachableQuietly(list, GROUP_B))
        assertReachable(list, DIRECT_A)
        assertReachable(list, DIRECT_B)
        assertFalse("回到「单聊」后群徽标不得出现", hasGroupBadgeNode())

        // ---- 切回「全部」：与初始可达集合精确相等 ----
        clickChip(CHIP_ALL)
        compose.waitUntil(WAIT_MS) { isReachableQuietly(list, GROUP_A) }
        val restoredSet = FIXTURE_TITLES.filter { isReachableQuietly(list, it) }.toSet()
        assertEquals(
            "切回「全部」后可达集合必须与初始逐字相同（筛选只切换视图，不改变数据）",
            initialReachableSet,
            restoredSet,
        )

        emit(
            "chips",
            listOf(
                "case=all_chips_individually",
                "chips_verified=ALL|DIRECT|GROUP|DIRECT|ALL",
                "initial_reachable=$initialReachableSet",
                "restored_reachable=$restoredSet",
                "restored_equals_initial=${initialReachableSet == restoredSet}",
            ),
        )
    }

    // ==================================================================
    // 测 2：来回切 chip 前后，直读主库计数必须逐字不变
    // ==================================================================

    @Test
    fun typeFilter_chipSwitchNeverLosesData_dbCountsStable() {
        openGroupChat(groupAId)
        openDrawer()
        val list = compose.onNodeWithTag(DRAWER_LIST_TAG)

        val before = runBlocking { dbCountsOf(pagingAssistantId) + dbCountsAll() }

        // 来回切两轮，覆盖每条 chip 的进入与退出。
        listOf(CHIP_DIRECT, CHIP_GROUP, CHIP_DIRECT, CHIP_ALL, CHIP_GROUP, CHIP_ALL).forEach { chip ->
            clickChip(chip)
            compose.waitForIdle()
        }
        compose.waitUntil(WAIT_MS) { isReachableQuietly(list, DIRECT_A) }

        val after = runBlocking { dbCountsOf(pagingAssistantId) + dbCountsAll() }
        assertEquals(
            "切 chip 不得增删任何会话：全表计数与按 type 分组计数前后必须逐字相同",
            before,
            after,
        )

        // 四条 UI 夹具仍能从主库原样读回（筛选只过滤不删）。
        val fixtureRows = runBlocking {
            listOf(groupAId, groupBId, directAId, directBId).map { id ->
                val c = repository.getConversationById(id)
                assertNotNull("切 chip 后夹具会话 $id 必须仍在主库", c)
                "${c!!.title}=${c.type}"
            }
        }
        assertEquals(
            "UI 夹具在库里必须原样存在",
            listOf(
                "$GROUP_A=${GroupChat.TYPE_GROUP}",
                "$GROUP_B=${GroupChat.TYPE_GROUP}",
                "$DIRECT_A=${GroupChat.TYPE_DIRECT}",
                "$DIRECT_B=${GroupChat.TYPE_DIRECT}",
            ),
            fixtureRows,
        )

        emit(
            "chips",
            listOf(
                "case=no_data_loss",
                "before=$before",
                "after=$after",
                "stable=${before == after}",
                "fixture_rows=$fixtureRows",
            ),
        )
    }

    // ==================================================================
    // 测 3：混合列表渲染分支 —— 群徽标只挂在群条目上
    // ==================================================================

    @Test
    fun mixedList_groupBadgeRendersOnlyOnGroupRows() {
        openGroupChat(groupAId)
        openDrawer()
        val list = compose.onNodeWithTag(DRAWER_LIST_TAG)

        // 群条目：合并语义节点同时带标题与「群」徽标。
        assertTrue(
            "群甲条目的合并语义节点应同时含标题与「群」徽标",
            hasTitleAndBadge(GROUP_A),
        )
        assertTrue(
            "群乙条目的合并语义节点应同时含标题与「群」徽标",
            hasTitleAndBadge(GROUP_B),
        )
        // 单聊条目：绝不带「群」徽标（同一节点上不得同时命中）。
        assertFalse(
            "单聊甲条目不得带「群」徽标",
            hasTitleAndBadge(DIRECT_A),
        )
        assertFalse(
            "单聊乙条目不得带「群」徽标",
            hasTitleAndBadge(DIRECT_B),
        )

        // 抽屉列表作用域内，至少出现与夹具群数相当的「群」徽标节点。
        val badgeNodes = compose
            .onAllNodes(hasAnyAncestor(hasTestTag(DRAWER_LIST_TAG)) and hasText(GROUP_BADGE))
            .fetchSemanticsNodes()
            .size
        assertTrue(
            "抽屉列表内「群」徽标节点数应 >= UI 夹具群数（$GROUP_FIXTURE_COUNT），实际=$badgeNodes",
            badgeNodes >= GROUP_FIXTURE_COUNT,
        )

        emit(
            "mixed",
            listOf(
                "case=group_badge",
                "group_a_badged=${hasTitleAndBadge(GROUP_A)}",
                "group_b_badged=${hasTitleAndBadge(GROUP_B)}",
                "direct_a_badged=${hasTitleAndBadge(DIRECT_A)}",
                "direct_b_badged=${hasTitleAndBadge(DIRECT_B)}",
                "badge_node_count=$badgeNodes",
            ),
        )
    }

    // ==================================================================
    // 测 4：真实分页 —— 首屏 = 生产 initialLoadSize，后续页 = 生产 pageSize
    // ==================================================================

    @Test
    fun pagingSource_firstScreenUsesInitialLoadSize_thenPageSize() = runBlocking {
        val dao = database.conversationDao()
        val source = dao.getUnfiledConversationsOfAssistantByType(pagingAssistantId, "")

        val first = loadPage(source, prodInitialLoadSize)
        assertEquals(
            "首屏条数必须等于生产 initialLoadSize（$prodInitialLoadSize）",
            prodInitialLoadSize,
            first.data.size,
        )
        assertNotNull("分页表总行数 > 首屏时，首屏必须给出 nextKey", first.nextKey)

        val second = loadPage(source, prodPageSize, key = first.nextKey)
        assertEquals(
            "第二屏必须恰好是剩余行数（总行数 $PAGING_TOTAL - 首屏 $prodInitialLoadSize）",
            PAGING_TOTAL - prodInitialLoadSize,
            second.data.size,
        )
        assertEquals(
            "翻到最后一屏时 nextKey 必须为 null（没有下一页）",
            null,
            second.nextKey,
        )

        emit(
            "paging",
            listOf(
                "case=first_screen",
                "prod_page_size=$prodPageSize",
                "prod_initial_load_size=$prodInitialLoadSize",
                "total_rows=$PAGING_TOTAL",
                "first_size=${first.data.size}",
                "second_size=${second.data.size}",
            ),
        )
    }

    // ==================================================================
    // 测 5：逐页累计 == DB 行数，且无重复 / 无遗漏
    // ==================================================================

    @Test
    fun pagingSource_allPagesSumToDbCount_withoutDuplicates() = runBlocking {
        val dao = database.conversationDao()
        val source = dao.getUnfiledConversationsOfAssistantByType(pagingAssistantId, "")

        val (all, pageSizes) = loadAllPages(source, prodPageSize)

        assertEquals(
            "累计加载行数必须等于专属助手未归档的 DB 行数（$PAGING_TOTAL）",
            PAGING_TOTAL,
            all.size,
        )
        assertEquals(
            "末页之前每一页都必须恰好是 $prodPageSize 行",
            List(pageSizes.size - 1) { prodPageSize },
            pageSizes.dropLast(1),
        )
        assertEquals(
            "id 去重后数量必须等于总行数（分页不重复）",
            PAGING_TOTAL,
            all.map { it.id }.toSet().size,
        )
        assertEquals(
            "分页取回的 id 集合必须与 DB 未归档集合逐字相同（分页不遗漏）",
            dbUnfiledIds(pagingAssistantId).toSet(),
            all.map { it.id }.toSet(),
        )

        emit(
            "paging",
            listOf(
                "case=all_pages",
                "page_sizes=$pageSizes",
                "loaded=${all.size}",
                "distinct_ids=${all.map { it.id }.toSet().size}",
                "db_unfiled=${dbUnfiledIds(pagingAssistantId).size}",
            ),
        )
    }

    // ==================================================================
    // 测 6：type 筛选在 SQL 层收窄 —— 每一页都只出该类型
    // ==================================================================

    @Test
    fun pagingSource_typeFilterNarrowsWithinSql() = runBlocking {
        val dao = database.conversationDao()

        val groupSource = dao.getUnfiledConversationsOfAssistantByType(pagingAssistantId, GroupChat.TYPE_GROUP)
        val (groups, groupPageSizes) = loadAllPages(groupSource, prodPageSize)
        assertEquals(
            "群聊筛选累计行数必须等于 DB 里该助手的群聊未归档数",
            PAGING_GROUP_COUNT,
            groups.size,
        )
        assertTrue(
            "群聊筛选每一页都只能是 GROUP，实际脏数据=${groups.filter { it.type != GroupChat.TYPE_GROUP }.map { it.id }}",
            groups.all { it.type == GroupChat.TYPE_GROUP },
        )
        assertEquals(
            "群聊筛选页大小序列应跨页（首屏满页 + 末页余数）",
            listOf(prodPageSize, PAGING_GROUP_COUNT - prodPageSize),
            groupPageSizes,
        )

        val directSource = dao.getUnfiledConversationsOfAssistantByType(pagingAssistantId, GroupChat.TYPE_DIRECT)
        val (directs, directPageSizes) = loadAllPages(directSource, prodPageSize)
        assertEquals(
            "单聊筛选累计行数必须等于 DB 里该助手的单聊未归档数",
            PAGING_DIRECT_COUNT,
            directs.size,
        )
        assertTrue(
            "单聊筛选每一页都只能是 DIRECT",
            directs.all { it.type == GroupChat.TYPE_DIRECT },
        )
        assertEquals(
            "单聊筛选页大小序列",
            listOf(prodPageSize, PAGING_DIRECT_COUNT - prodPageSize),
            directPageSizes,
        )

        emit(
            "paging",
            listOf(
                "case=type_filter",
                "group_page_sizes=$groupPageSizes",
                "group_rows=${groups.size}",
                "direct_page_sizes=$directPageSizes",
                "direct_rows=${directs.size}",
            ),
        )
    }

    // ==================================================================
    // 测 7：「全部」chip 的参数折算 = 空串 = SQL 不筛，与 DB 全量一致
    // ==================================================================

    @Test
    fun pagingSource_filterAllArgumentIsEmpty_equivalentToSqlNoFilter() = runBlocking {
        val typeArg = GroupChat.FILTER_ALL.asConversationTypeArgument()
        assertEquals("「全部」chip 折算给 SQL 的参数必须是空串（不筛）", "", typeArg)

        val dao = database.conversationDao()
        val (all, _) = loadAllPages(
            dao.getUnfiledConversationsOfAssistantByType(pagingAssistantId, typeArg),
            prodPageSize,
        )
        assertEquals(
            "「全部」参数下累计行数必须等于该助手未归档全量",
            dbUnfiledIds(pagingAssistantId).size,
            all.size,
        )
        // 两种空值表达（FILTER_ALL 折算 / 直接空串）必须落到同一结果。
        val (all2, _) = loadAllPages(
            dao.getUnfiledConversationsOfAssistantByType(pagingAssistantId, ""),
            prodPageSize,
        )
        assertEquals("FILTER_ALL 折算与直接空串必须等价", all.map { it.id }, all2.map { it.id })

        emit(
            "paging",
            listOf(
                "case=filter_all",
                "type_arg='$typeArg'",
                "rows=${all.size}",
            ),
        )
    }

    // ==================================================================
    // 测 8：搜索路同样把 type 带进同一条 SQL，且真实分页
    // ==================================================================

    @Test
    fun pagingSource_searchPathCarriesTypeAndPages() = runBlocking {
        val dao = database.conversationDao()

        val (groupHits, groupPageSizes) = loadAllPages(
            dao.searchConversationsOfAssistantByType(pagingAssistantId, SEARCH_TOKEN, GroupChat.TYPE_GROUP),
            prodPageSize,
        )
        assertEquals("搜索 + 群聊筛选命中数必须等于群夹偶数", PAGING_GROUP_COUNT, groupHits.size)
        assertTrue(groupHits.all { it.type == GroupChat.TYPE_GROUP })
        assertTrue("搜索命中的标题必须都含关键字", groupHits.all { it.title.contains(SEARCH_TOKEN) })
        assertEquals(
            "搜索路也必须真实分页",
            listOf(prodPageSize, PAGING_GROUP_COUNT - prodPageSize),
            groupPageSizes,
        )

        val (allHits, _) = loadAllPages(
            dao.searchConversationsOfAssistantByType(pagingAssistantId, SEARCH_TOKEN, ""),
            prodPageSize,
        )
        assertEquals("搜索不筛类型命中数必须等于全量夹具", PAGING_TOTAL, allHits.size)

        emit(
            "paging",
            listOf(
                "case=search_path",
                "group_hits=${groupHits.size}",
                "group_page_sizes=$groupPageSizes",
                "all_hits=${allHits.size}",
            ),
        )
    }

    // ==================================================================
    // 分页断言工具
    // ==================================================================

    /**
     * 按**生产 `Pager` 的页类型**驱动一个 `PagingSource`：
     * - `key == null`（首屏）→ [PagingSource.LoadParams.Refresh]（生产 `Pager` 的首请求就是 Refresh）；
     * - `key != null`（续读）→ [PagingSource.LoadParams.Append]（生产 `Pager` 的后续页一律是 Append）。
     *
     * ⚠️ 后续页**不能**也用带 key 的 `Refresh`。Room 2.8.5 的 `LimitOffsetPagingSource`
     * 对 `Refresh` 是「按刷新窗口算 offset」，而 `Append` 才是「从 key 续读」：
     *
     * ```
     * // androidx.room.paging.util.RoomPagingUtil.getOffset(params, key, itemCount)
     * // （反编译自 room-paging-2.8.5，见报告）
     * Prepend -> if (key < loadSize) 0 else key - loadSize
     * Append  -> key
     * Refresh -> if (key < itemCount - loadSize) key else max(0, itemCount - loadSize)
     * ```
     *
     * 于是 `Refresh(key=40, loadSize=20, itemCount=55)` 的 offset 被夹到 `max(0, 55-20)=35`，
     * 第二屏从 35 回读 20 行（而非从 40 续读 15 行），造成**重叠/超取**；
     * `Append(key=40, loadSize=20)` 的 offset 恒为 40，才是真续读。首屏（key=null）走
     * 一次性 `initialLoad`（先 COUNT 再 `queryDatabase`），语义与 `Pager` 首屏一致。
     */
    private suspend fun loadPage(
        source: PagingSource<Int, LightConversationEntity>,
        loadSize: Int,
        key: Int? = null,
    ): PagingSource.LoadResult.Page<Int, LightConversationEntity> {
        val params: PagingSource.LoadParams<Int> = if (key == null) {
            PagingSource.LoadParams.Refresh(key, loadSize, false)
        } else {
            PagingSource.LoadParams.Append(key, loadSize, false)
        }
        return when (val result = source.load(params)) {
            is PagingSource.LoadResult.Page -> result
            is PagingSource.LoadResult.Error -> throw result.throwable
            is PagingSource.LoadResult.Invalid -> error("PagingSource 在首次加载即失效")
        }
    }

    /** 顺序翻页直到 nextKey == null，返回（全部行, 每页行数）。 */
    private suspend fun loadAllPages(
        source: PagingSource<Int, LightConversationEntity>,
        pageSize: Int,
    ): Pair<List<LightConversationEntity>, List<Int>> {
        val all = mutableListOf<LightConversationEntity>()
        val pageSizes = mutableListOf<Int>()
        var key: Int? = null
        while (true) {
            val page = loadPage(source, pageSize, key)
            all += page.data
            pageSizes += page.data.size
            if (page.nextKey == null) break
            key = page.nextKey
        }
        return all to pageSizes
    }

    /** 主库未归档（folder_id = ''）指定助手的全部 id。 */
    private suspend fun dbUnfiledIds(assistantId: String): List<String> =
        database.conversationDao().getAll().first()
            .filter { it.assistantId == assistantId && it.folderId.isEmpty() }
            .map { it.id }

    /** 指定助手的未归档行数，按 type 分组（排序后便于逐字比较）。 */
    private suspend fun dbCountsOf(assistantId: String): List<String> =
        database.conversationDao().getAll().first()
            .filter { it.assistantId == assistantId && it.folderId.isEmpty() }
            .groupingBy { it.type }
            .eachCount()
            .toSortedMap()
            .map { (type, count) -> "$type=$count" }

    /** 全表计数，按 type 分组。 */
    private suspend fun dbCountsAll(): List<String> =
        database.conversationDao().getAll().first()
            .groupingBy { it.type }
            .eachCount()
            .toSortedMap()
            .map { (type, count) -> "$type=$count" }

    // ==================================================================
    // 夹具
    // ==================================================================

    private fun seedUiFixtures() = runBlocking {
        val currentAssistantId = settingsStore.settingsFlow.first().assistantId
        val roleAssistantId = settingsStore.settingsFlow.first().assistants.firstOrNull()?.id
        fixtureConfig = GroupConfig(
            roles = listOf(
                GroupRole(id = "alice", name = "Alice Johnson", assistantId = roleAssistantId.toString()),
                GroupRole(id = "alpha", name = "阿尔法", assistantId = roleAssistantId.toString()),
                GroupRole(id = "gamma", name = "伽马", assistantId = roleAssistantId.toString(), chair = true),
            ),
            mode = GroupChat.MODE_PIPELINE,
            chairRoleId = "gamma",
            tokenBudgetPerRound = 200_000,
            revision = 1,
        )

        listOf(groupAId, groupBId, directAId, directBId).forEach { id ->
            repository.deleteConversation(
                Conversation(id = id, assistantId = currentAssistantId, title = "", messageNodes = emptyList()),
            )
        }

        val now = java.time.Instant.now()
        listOf(
            Triple(groupAId, GROUP_A, now),
            Triple(groupBId, GROUP_B, now.minusSeconds(1)),
            Triple(directAId, DIRECT_A, now.minusSeconds(2)),
            Triple(directBId, DIRECT_B, now.minusSeconds(3)),
        ).forEachIndexed { index, (id, title, updateAt) ->
            val isGroup = index < 2
            repository.insertConversation(
                Conversation(
                    id = id,
                    assistantId = currentAssistantId,
                    title = title,
                    messageNodes = listOf(
                        UIMessage(
                            id = Uuid.random(),
                            role = MessageRole.USER,
                            parts = listOf(UIMessagePart.Text("$title 夹具消息")),
                        ).toMessageNode(),
                    ),
                    updateAt = updateAt,
                    type = if (isGroup) GroupChat.TYPE_GROUP else GroupChat.TYPE_DIRECT,
                    groupConfig = if (isGroup) fixtureConfig else null,
                ),
            )
        }
        listOf(groupAId, groupBId, directAId, directBId).forEach { id ->
            assertNotNull("UI 夹具 seed 后必须能从主库读回", repository.getConversationById(id))
        }
    }

    private fun seedPagingFixtures() = runBlocking {
        (pagingGroupIds + pagingDirectIds).forEach { id ->
            repository.deleteConversation(
                Conversation(id = id, assistantId = Uuid.parse(pagingAssistantId), title = "", messageNodes = emptyList()),
            )
        }
        val now = java.time.Instant.now()
        // 群夹偶数：标题带公共搜索 token；单聊夹具同理。updateAt 递减保证分页顺序确定。
        pagingGroupIds.forEachIndexed { index, id ->
            repository.insertConversation(
                Conversation(
                    id = id,
                    assistantId = Uuid.parse(pagingAssistantId),
                    title = "$SEARCH_TOKEN 群聊 #$index",
                    messageNodes = emptyList(),
                    updateAt = now.minusMillis(index.toLong()),
                    type = GroupChat.TYPE_GROUP,
                ),
            )
        }
        pagingDirectIds.forEachIndexed { index, id ->
            repository.insertConversation(
                Conversation(
                    id = id,
                    assistantId = Uuid.parse(pagingAssistantId),
                    title = "$SEARCH_TOKEN 单聊 #$index",
                    messageNodes = emptyList(),
                    updateAt = now.minusSeconds(60).minusMillis(index.toLong()),
                    type = GroupChat.TYPE_DIRECT,
                ),
            )
        }
        assertEquals(
            "分页夹具 seed 后未归档行数必须等于预期",
            PAGING_TOTAL,
            dbUnfiledIds(pagingAssistantId).size,
        )
    }

    private fun pagingId(seed: Int): Uuid =
        Uuid.parse("0c1c0de5-0000-0000-0000-%012x".format(seed))

    private fun readRepositoryStaticInt(name: String): Int {
        val field = ConversationRepository::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getInt(null)
    }

    // ==================================================================
    // UI 宿主与断言工具
    // ==================================================================

    private fun openGroupChat(conversationId: Uuid) {
        val intent = Intent(context, RouteActivity::class.java).apply {
            putExtra("conversationId", conversationId.toString())
        }
        scenario = ActivityScenario.launch<RouteActivity>(intent)
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodesWithTag(CHAT_INPUT_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 左缘向右滑打开群聊页抽屉；等到 chip 行可见。 */
    private fun openDrawer() {
        compose.onRoot().performTouchInput {
            swipe(
                start = Offset(1f, centerY),
                end = Offset(width - 1f, centerY),
                durationMillis = 250,
            )
        }
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodesWithText(CHIP_ALL).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickChip(label: String) {
        compose.onNodeWithText(label).performClick()
    }

    private fun assertReachable(list: SemanticsNodeInteraction, text: String) {
        list.performScrollToNode(hasText(text))
        assertTrue("抽屉列表里必须能滚动到条目「$text」", countInDrawer(text) > 0)
    }

    private fun countInDrawer(text: String): Int = compose
        .onAllNodes(hasAnyAncestor(hasTestTag(DRAWER_LIST_TAG)) and hasText(text))
        .fetchSemanticsNodes()
        .size

    private fun isReachableQuietly(list: SemanticsNodeInteraction, text: String): Boolean =
        runCatching { list.performScrollToNode(hasText(text)) }.isSuccess

    /** 抽屉列表作用域内是否存在「群」徽标节点（精确文本，chip 的「群聊」不算）。 */
    private fun hasGroupBadgeNode(): Boolean =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(DRAWER_LIST_TAG)) and hasText(GROUP_BADGE))
            .fetchSemanticsNodes()
            .isNotEmpty()

    /**
     * 合并语义节点上是否同时命中标题与「群」徽标。
     *
     * 行是 `combinedClickable`（mergeDescendants），群条目的合并节点会把「群」与标题
     * 一起收进同一节点的 Text 列表；单聊条目只有标题，故同一节点不可能同时命中两者。
     */
    private fun hasTitleAndBadge(title: String): Boolean = compose
        .onAllNodes(
            hasAnyAncestor(hasTestTag(DRAWER_LIST_TAG)) and hasText(title) and hasText(GROUP_BADGE),
        )
        .fetchSemanticsNodes()
        .isNotEmpty()

    private fun emit(case: String, lines: List<String>) {
        val dir = context.getExternalFilesDir(null) ?: return
        File(dir, "c1-round6-$case.txt").appendText(
            lines.joinToString("\n") + "\n----\n",
            Charsets.UTF_8,
        )
    }

    private companion object {
        const val WAIT_MS = 15_000L

        val EVIDENCE_CLEARED = java.util.concurrent.atomic.AtomicBoolean(false)

        const val CHAT_INPUT_TAG = "chat_input"
        const val DRAWER_LIST_TAG = "drawer_conversation_list"

        const val CHIP_ALL = "全部"
        const val CHIP_DIRECT = "单聊"
        const val CHIP_GROUP = "群聊"
        const val GROUP_BADGE = "群"

        const val GROUP_A = "C1-R6 群聊甲"
        const val GROUP_B = "C1-R6 群聊乙"
        const val DIRECT_A = "C1-R6 单聊甲"
        const val DIRECT_B = "C1-R6 单聊乙"

        val FIXTURE_TITLES = listOf(GROUP_A, GROUP_B, DIRECT_A, DIRECT_B)
        const val GROUP_FIXTURE_COUNT = 2

        /** 分页夹具规模：25 群 + 30 单聊 = 55，跨过 initialLoadSize=40 与 pageSize=20。 */
        const val PAGING_GROUP_COUNT = 25
        const val PAGING_DIRECT_COUNT = 30
        const val PAGING_TOTAL = PAGING_GROUP_COUNT + PAGING_DIRECT_COUNT

        const val SEARCH_TOKEN = "C1-R6"
    }
}
