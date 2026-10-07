package heizige.kk.khatkit.app.feature.chat

import android.content.Intent
import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.paging.PagingSource
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.RouteActivity
import heizige.kk.khatkit.app.core.data.ai.GenerationLoop
import heizige.kk.khatkit.app.core.data.ai.TranslationHandler
import heizige.kk.khatkit.app.core.data.ai.tavern.TavernChatCodec
import heizige.kk.khatkit.app.core.data.ai.tools.ChatToolFactory
import heizige.kk.khatkit.app.core.data.ai.tools.local.LocalTools
import heizige.kk.khatkit.app.core.data.ai.transformers.Base64ImageToLocalFileTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.OcrTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.PlaceholderTransformer
import heizige.kk.khatkit.app.core.data.datastore.DEFAULT_PROVIDERS
import heizige.kk.khatkit.app.core.data.datastore.NetworkSetting
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.db.AppDatabase
import heizige.kk.khatkit.app.core.data.db.AppDatabaseFactory
import heizige.kk.khatkit.app.core.data.db.dao.GroupRunDAO
import heizige.kk.khatkit.app.core.data.db.entity.GroupRunEntity
import heizige.kk.khatkit.app.core.data.db.fts.MessageFtsManager
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.SpeakerStep
import heizige.kk.khatkit.app.core.data.model.toMessageNode
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FilesRepository
import heizige.kk.khatkit.app.core.data.repository.FolderRepository
import heizige.kk.khatkit.app.core.data.repository.LightConversationEntity
import heizige.kk.khatkit.app.core.data.repository.MemoryExtractor
import heizige.kk.khatkit.app.core.di.appEntryPoint
import heizige.kk.khatkit.app.core.util.JsonInstant
import heizige.kk.khatkit.common.android.appTempFolder
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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
import java.security.MessageDigest
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
    private val appContext = resolveAppContext()

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

    // ==================================================================
    // C1-10 真实网关群聊轮夹具（口径与 C1LiveModelSequenceTest 的 real-provider 一致）
    //
    // 这是一段**附加**的真实模型调用：在既有夹具会话（`groupAId`）上真跑一轮三方
    // pipeline，从而产出契约 `:206` / `:232-235` 点名的四类产物。既有 8 条筛选/分页
    // 断言在各自的方法里，一行不动。
    // ==================================================================

    /** 内置「极客猫」OpenAI 兼容 provider（与 `C1LiveModelSequenceTest` 同一个 id）。 */
    private val realProviderId = Uuid.parse("5197b3ae-21fd-4924-abb0-2aa70ff4ac42")

    private val realProvider: ProviderSetting.OpenAI =
        requireNotNull(DEFAULT_PROVIDERS.filterIsInstance<ProviderSetting.OpenAI>().firstOrNull { it.id == realProviderId }) {
            "DEFAULT_PROVIDERS 里找不到 id=$realProviderId 的内置 provider"
        }

    private val realFlashModel = requireNotNull(realProvider.models.firstOrNull { it.modelId == "deepseek-v4-flash" }) {
        "内置 provider 里没有 deepseek-v4-flash"
    }
    private val realGlmModel = requireNotNull(realProvider.models.firstOrNull { it.modelId == "glm-5.2" }) {
        "内置 provider 里没有 glm-5.2"
    }

    private val realAssistantAId = Uuid.parse("0c1c0de5-0000-0000-0000-00000000c1a1")
    private val realAssistantBId = Uuid.parse("0c1c0de5-0000-0000-0000-00000000c1b1")
    private val realAssistantCId = Uuid.parse("0c1c0de5-0000-0000-0000-00000000c1c1")

    private val realCaseName = "c1-ten-real-gateway"

    /** 真实网关轮超时：推理模型可能比 mock 慢两个数量级。 */
    private val realTimeoutMillis = 300_000L

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
    // 测 9：真实网关群聊轮 —— **从「群聊」筛选结果里打开群**，再在该群上跑真实一轮
    // ==================================================================

    /**
     * C1-10 的**真实网关**证据，且这条群必须是**从筛选路径内部**抵达的。
     *
     * 契约 `:206` 要求 10 个用例「每例保存输入、各角色可见消息集合、实际模型调用序列、
     * token 计数和导出哈希」；`:232-235` 明写「只看截图或只看 UI 状态均标记 `unverified`」。
     *
     * ## 为什么不能平行挂载
     *
     * 旧写法只是 `openGroupChat(groupAId) → sendMessage(...)`：它产的 8 个字段全部来自
     * 「群聊生成路径」，与 C1-10 点名的「单聊/群聊筛选」路径**正交** —— 既不点 chip、
     * 也不驱动筛选。所以本方法把两者缝成一条真实用户流程：
     *
     * 1. **先走筛选路径**：从一个非目标会话（单聊甲）进入 → 左滑开抽屉 → 点「群聊」chip
     *    → 断言筛选后的列表里只有群条目（沿用前 8 条相同的 `isReachableQuietly` /
     *    `countInDrawer` / 群徽标判定）；
     * 2. **从筛选结果里取第一条群条目的 id**（生产同一条 DAO 查询就是抽屉 PagingSource
     *    的数据源），显式断言它确实在筛选结果里；不硬编码 `groupAId`；
     * 3. **从筛选列表里点开它**（点抽屉行 → `navigateToChatPage`/`clearAndNavigate`），
     *    再反射读 `NavViewModel.currentPage` 断言导航栈顶就是那条群 id —— 这是「筛选」
     *    与「跑一轮」缝合的关键；
     * 4. 在这个**从筛选结果打开的群**上真跑一轮三方 pipeline，产出契约点名的：
     *    - 用例输入（触发文本）；
     *    - 各 viewer 可见消息 ID 台账（生产 `GroupChat.visibleMessages`）；
     *    - 实际模型调用序列（模型名 + prompt/completion token）；
     *    - prompt+completion 总 token；
     *    - 生产 `TavernChatCodec.exportGroupJsonl` → `writeExportTempFile` 的导出 SHA-256。
     *
     * 证据 JSON 里显式记录 `opened_from_filtered_list=true` +
     * `filtered_group_ids_before_open` + `opened_group_id` + `navigated_chat_id`，
     * 使「这条群来自筛选结果」可复核。
     *
     * 既有 8 条筛选/分页断言在各自方法里，一行未动。
     */
    @Test
    fun realGatewayRoundInFixtureGroupProducesContractArtifacts() = runBlocking {
        val originalSettings = settingsStore.settingsFlow.first()
        val groupRunDao: GroupRunDAO = database.groupRunDao()

        // 前置自检：必须打真实公网网关，绝不能退化成回环 mock。
        assertTrue(
            "本用例必须打真实公网网关，baseUrl 却是 ${realProvider.baseUrl}",
            realProvider.baseUrl.startsWith("https://") &&
                !realProvider.baseUrl.contains("127.0.0.1") &&
                !realProvider.baseUrl.contains("localhost"),
        )

        // ============================================================
        // 阶段 1：走真实筛选路径 —— 非目标会话进入 → 开抽屉 → 点「群聊」chip
        // ============================================================
        // 从单聊甲进入，这样随后「从筛选列表打开群」是一次真实导航（不是点当前会话）。
        openGroupChat(directAId)
        openDrawer()
        val list = compose.onNodeWithTag(DRAWER_LIST_TAG)

        // 切 chip 前的基线：两个夹具群在「全部」下都可达。
        assertReachable(list, GROUP_A)
        assertReachable(list, GROUP_B)

        clickChip(CHIP_GROUP)
        compose.waitUntil(WAIT_MS) { !isReachableQuietly(list, DIRECT_A) }

        // 筛选态断言（沿用前 8 条相同判定方式）：只有群，没有单聊。
        assertFalse("「群聊」筛选下单聊甲必须不可达", isReachableQuietly(list, DIRECT_A))
        assertFalse("「群聊」筛选下单聊乙必须不可达", isReachableQuietly(list, DIRECT_B))
        assertEquals("「群聊」筛选下不得出现单聊甲", 0, countInDrawer(DIRECT_A))
        assertEquals("「群聊」筛选下不得出现单聊乙", 0, countInDrawer(DIRECT_B))
        assertReachable(list, GROUP_A)
        assertReachable(list, GROUP_B)
        assertTrue("「群聊」筛选下必须出现群徽标", hasGroupBadgeNode())
        val filteredDirectACount = countInDrawer(DIRECT_A)
        val filteredDirectBCount = countInDrawer(DIRECT_B)

        // ============================================================
        // 阶段 2：从**筛选结果**里取第一条群条目的 id（不硬编码 groupAId）
        // 生产同一条 DAO 查询就是抽屉 PagingSource 的数据源。
        // ============================================================
        val currentAssistantId = settingsStore.settingsFlow.first().assistantId.toString()
        val filteredGroups = loadPage(
            database.conversationDao()
                .getUnfiledConversationsOfAssistantByType(currentAssistantId, GroupChat.TYPE_GROUP),
            prodInitialLoadSize,
        ).data
        assertTrue("群聊筛选结果不得为空", filteredGroups.isNotEmpty())
        assertTrue(
            "群聊筛选结果每一条都必须是 GROUP 类型，实际脏数据=" +
                filteredGroups.filter { it.type != GroupChat.TYPE_GROUP }.map { it.id },
            filteredGroups.all { it.type == GroupChat.TYPE_GROUP },
        )
        val filteredGroupIds = filteredGroups.map { it.id }
        val firstFilteredGroup = filteredGroups.first()
        val openedGroupId = Uuid.parse(firstFilteredGroup.id)
        // 关键缝合断言：取到的 id 确实在筛选结果里。
        assertTrue(
            "从筛选结果取到的第一条群 id 必须确实在该筛选结果里：$openedGroupId",
            openedGroupId.toString() in filteredGroupIds,
        )
        // 防御：只允许打开本案夹具群，绝不误打用户真实群。
        assertTrue(
            "筛选结果第一条群必须是本案夹具群（$groupAId 或 $groupBId），" +
                "实际 id=$openedGroupId title=${firstFilteredGroup.title}",
            openedGroupId == groupAId || openedGroupId == groupBId,
        )
        val openedTitle = firstFilteredGroup.title
        // UI 与 DB 同源：这条群在筛选后的抽屉列表里必须可达。
        assertReachable(list, openedTitle)

        // ============================================================
        // 阶段 3：从筛选列表里点开这条群（真实用户流程：点抽屉行 → 导航到该会话）
        // ============================================================
        compose.onNode(
            hasAnyAncestor(hasTestTag(DRAWER_LIST_TAG)) and hasText(openedTitle),
        ).performClick()

        // 等导航栈顶变成从筛选结果取到的那条群 id（反射读生产 NavViewModel.currentPage）。
        compose.waitUntil(WAIT_MS) { currentChatPageId() == openedGroupId.toString() }
        val navigatedId = currentChatPageId()
        assertEquals(
            "点开筛选列表里的群后，导航栈顶会话必须正是从筛选结果取到的那条群 id",
            openedGroupId.toString(),
            navigatedId,
        )
        val groupInfoChipVisible = compose
            .onAllNodes(hasText(GROUP_INFO_SUFFIX, substring = true))
            .fetchSemanticsNodes()
            .isNotEmpty()
        assertTrue("打开后必须落到群聊页（群配置胶囊可见），实际未看到", groupInfoChipVisible)

        emit(
            "chips",
            listOf(
                "case=c1_10_opened_from_filtered_list",
                "opened_from_filtered_list=true",
                "filtered_group_ids_before_open=$filteredGroupIds",
                "opened_group_id=$openedGroupId",
                "opened_title=$openedTitle",
                "filtered_only_groups=true",
                "filtered_direct_a_count=$filteredDirectACount",
                "filtered_direct_b_count=$filteredDirectBCount",
                "navigated_chat_id=$navigatedId",
                "group_info_chip_visible=$groupInfoChipVisible",
            ),
        )

        // 打开态已取证：释放 UI，避免它的 ChatViewModel 与测试随后自建的 ChatManager 抢写同一会话。
        scenario?.close()
        scenario = null

        // ============================================================
        // 阶段 4：在这个「从筛选结果打开的群」上真跑一轮，产出契约 8 字段
        // ============================================================
        val originalConversation = requireNotNull(repository.getConversationById(openedGroupId)) {
            "从筛选结果打开的群会话 $openedGroupId 必须能从真库读回"
        }
        val config = realConfig()
        val invalid = GroupChat.validate(config, openedGroupId.toString())
        assertTrue("真实网关群配置必须合法，实际违规：$invalid", invalid.isEmpty())

        settingsStore.update(realSettings())
        // 把这个群会话换成真实群配置 + 清空旧夹具消息，跑真实一轮。
        repository.updateConversation(
            originalConversation.copy(
                type = GroupChat.TYPE_GROUP,
                groupConfig = config,
                messageNodes = emptyList(),
            ),
        )

        val chatManager = buildChatManager()
        var started = false
        try {
            chatManager.addConversationReference(openedGroupId)
            chatManager.initializeConversation(openedGroupId)
            started = true
            chatManager.sendMessage(
                conversationId = openedGroupId,
                content = listOf(UIMessagePart.Text(TRIGGER_TEXT)),
                answer = true,
            )
            val stamped = awaitStampedTerminalRound(
                chatManager = chatManager,
                groupRunDao = groupRunDao,
                conversationId = openedGroupId,
                expected = 3,
                expectedStatus = GroupRunEntity.STATUS_COMPLETED,
                timeoutMillis = realTimeoutMillis,
            )
            val messages = stamped.messages
            val run = stamped.run

            val assistants = messages.filter { it.role == MessageRole.ASSISTANT }
            assertEquals(
                "真实网关轮必须产出 a、b、c 三条助手消息，实际=" + messages.map { "${it.role}/${it.roleId}" },
                listOf("a", "b", "c"),
                assistants.map { it.roleId },
            )
            assertEquals("三条助手消息必须同属一轮", setOf(run.roundId), assistants.map { it.roundId }.toSet())

            // ---------- 真实 usage + 调用序列 ----------
            val usages = assistants.map { message ->
                val usage = requireNotNull(message.usage) {
                    "角色 ${message.roleId} 没有 usage —— 真实响应没带回 usage，token 证据不成立"
                }
                assertTrue("角色 ${message.roleId} 的 prompt_tokens 必须为正", usage.promptTokens > 0)
                assertTrue("角色 ${message.roleId} 的 completion_tokens 必须为正", usage.completionTokens > 0)
                usage
            }
            val sumPromptCompletion = usages.sumOf { it.promptTokens + it.completionTokens }
            assertEquals(
                "group_runs.spent_tokens 必须等于三条发言 (prompt+completion) 之和",
                sumPromptCompletion,
                run.spentTokens,
            )
            assertEquals("本轮应正常完成", GroupRunEntity.STATUS_COMPLETED, run.status)
            assertEquals("三个角色都必须进 committed 名单", listOf("a", "b", "c"), run.committedRoleIds)

            // 调用序列（wire 优先，缺失则回落 uuid 反查）。
            val sequence = assistants.map { message ->
                val wire = message.wireModelName?.takeIf { it.isNotBlank() }
                val reverse = message.modelId?.let { uuid -> realProvider.models.firstOrNull { it.id == uuid }?.modelId }
                assertTrue(
                    "角色 ${message.roleId} 的模型名必须至少有一个来源（wire 或 uuid 反查）",
                    wire != null || reverse != null,
                )
                wire ?: requireNotNull(reverse)
            }
            assertEquals(
                "期望的模型调用序列（按发言顺序）应为 deepseek-v4-flash → glm-5.2 → deepseek-v4-flash",
                listOf("deepseek-v4-flash", "glm-5.2", "deepseek-v4-flash"),
                sequence,
            )

            // ---------- viewer 可见消息 ID 台账 ----------
            val plans = planViewerLedgerPlans(config, GroupChat.plan(config, emptyList()))
            val ledger = viewerVisibilityLedger(messages, config, plans)
            assertViewerVisibilityLedger(ledger, messages, config, plans)

            // ---------- 生产导出 SHA-256 ----------
            val exportEvidence = exportGroupJsonlEvidence(openedGroupId, "c1-export-c1-10-real-gateway.jsonl")

            writeEvidence(
                "c1-round6-real-gateway.json",
                buildJsonObject {
                    put("evidence_kind", "real-gateway-call-direct-from-device-no-proxy")
                    put("generated_at_device", System.currentTimeMillis())
                    put("device_model", android.os.Build.MODEL)
                    put("device_sdk", android.os.Build.VERSION.SDK_INT)
                    put("device_abi", android.os.Build.SUPPORTED_ABIS.joinToString(","))
                    put("case", realCaseName)
                    put("conversation_id", openedGroupId.toString())
                    // ---------- 「这条群来自筛选结果」的显式证据 ----------
                    put("opened_from_filtered_list", true)
                    putJsonArray("filtered_group_ids_before_open") {
                        filteredGroupIds.forEach { add(JsonPrimitive(it)) }
                    }
                    put("opened_group_id", openedGroupId.toString())
                    put("opened_group_title", openedTitle)
                    put("opened_via", "drawer:chip(群聊)→row(openedTitle)→navigateToChatPage→currentPage.id")
                    put("navigated_chat_id", navigatedId)
                    put("filter_drawer_direct_a_count", filteredDirectACount)
                    put("filter_drawer_direct_b_count", filteredDirectBCount)
                    put("filter_drawer_groups_only", true)
                    put("group_info_chip_visible_after_open", groupInfoChipVisible)
                    put("conversation_type", GroupChat.TYPE_GROUP)
                    put("mode", config.mode)
                    put("token_budget_per_round", config.tokenBudgetPerRound)
                    put("input_user_text", TRIGGER_TEXT)
                    putJsonObject("group_run") {
                        put("round_id", run.roundId)
                        put("status", run.status)
                        put("spent_tokens", run.spentTokens)
                        put("committed_role_ids", run.committedRoleIds.joinToString(","))
                        put("skipped_role_ids", run.skippedRoleIds.joinToString(","))
                    }
                    putJsonArray("actual_model_call_sequence") {
                        assistants.forEachIndexed { index, message ->
                            add(
                                buildJsonObject {
                                    put("seq", index + 1)
                                    put("role_id", message.roleId)
                                    put("turn_kind", message.turnKind)
                                    put("wire_model_name", message.wireModelName)
                                    put("resolved_model_name", sequence[index])
                                    put("usage_prompt_tokens", usages[index].promptTokens)
                                    put("usage_completion_tokens", usages[index].completionTokens)
                                    put("usage_total_tokens", usages[index].totalTokens)
                                },
                            )
                        }
                    }
                    put("sum_prompt_plus_completion", sumPromptCompletion)
                    put("viewer_visibility", ledger)
                    exportEvidence.forEach { (key, value) -> put(key, value) }
                },
            )
        } finally {
            if (started) {
                try { chatManager.stopGeneration(openedGroupId) } catch (_: Throwable) { }
                try { chatManager.removeConversationReference(openedGroupId) } catch (_: Throwable) { }
            }
            try { groupRunDao.deleteFinishedOfConversation(openedGroupId.toString()) } catch (_: Throwable) { }
            // 还原夹具会话，别把真实轮消息留在用户库里。
            try { repository.updateConversation(originalConversation) } catch (_: Throwable) { }
            try { settingsStore.update(originalSettings) } catch (_: Throwable) { }
        }
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

    /**
     * 读生产 `RouteActivity` 的导航栈顶会话 id —— 用来证明「点开的群正是从筛选结果里
     * 取到的那一条」。
     *
     * `RouteActivity.navViewModel` 是 `by viewModels()` 委托属性，编译后落地成私有字段
     * `navViewModel$delegate`（`kotlin.Lazy<NavViewModel>`，见 `javap` 实测）。取值后调
     * 生产 `NavViewModel.currentPage`（栈顶），若是 `Screen.Chat` 就返回它的 `id`。
     * 只用公开成员与字段名反射，不触碰被禁改的 `app/src/main`。
     */
    private fun currentChatPageId(): String? {
        val s = scenario ?: return null
        var id: String? = null
        s.onActivity { activity ->
            val field = RouteActivity::class.java.getDeclaredField("navViewModel\$delegate")
            field.isAccessible = true
            val delegate = field.get(activity)
            val vm = (delegate as? Lazy<*>)?.value ?: delegate
            if (vm != null) {
                val page = vm.javaClass.getMethod("getCurrentPage").invoke(vm)
                id = (page as? heizige.kk.khatkit.app.Screen.Chat)?.id
            }
        }
        return id
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

    // ==================================================================
    // 真实网关轮：配置 / 夹具 / 等待 / 台账 / 导出
    // ==================================================================

    private fun realPersona(code: String) = buildString {
        append("你是 KhatKit C1 群聊验证角色。你的代号是 ").append(code).append("。")
        append("ROLECODE:").append(code).append(" ")
        append("你正在参加一场三人 pipeline 群聊。")
        append("输出规则（必须严格遵守）：")
        append("1. 只输出一行。")
        append("2. 这一行必须以 ROLECODE:").append(code).append(" 开头，后面跟一句不超过20字的中文。")
        append("3. 极其重要：对话历史里别人的发言也带 ROLECODE: 前缀，但那是别人的代号。")
        append("你必须始终使用你自己的代号 ").append(code)
        append("，绝对不能沿用或模仿历史里出现的任何其它代号。")
        append("4. 禁止模拟其它角色，禁止列表、标题、markdown、思考过程。")
        append("正确示例：ROLECODE:").append(code).append(" 我已就位。")
    }

    private fun realAssistant(id: Uuid, name: String, code: String, modelId: Uuid) = Assistant(
        id = id,
        name = name,
        chatModelId = modelId,
        systemPrompt = realPersona(code),
        enableMemory = false,
        useGlobalMemory = false,
        autoExtractMemory = false,
        enableWebSearch = false,
        localTools = emptyList(),
        enableTimeReminder = false,
        enableRecentChatsReference = false,
    )

    private fun realConfig() = GroupConfig(
        roles = listOf(
            GroupRole(
                id = "a",
                name = "角色甲",
                assistantId = realAssistantAId.toString(),
                modelId = realFlashModel.id.toString(),
                cardId = "c1-ten-a",
            ),
            GroupRole(
                id = "b",
                name = "角色乙",
                assistantId = realAssistantBId.toString(),
                modelId = realGlmModel.id.toString(),
                cardId = "c1-ten-b",
            ),
            GroupRole(
                id = "c",
                name = "角色丙",
                assistantId = realAssistantCId.toString(),
                chair = true,
                modelId = realFlashModel.id.toString(),
                cardId = "c1-ten-c",
            ),
        ),
        mode = GroupChat.MODE_PIPELINE,
        chairRoleId = "c",
        tokenBudgetPerRound = 100_000,
    )

    private fun realSettings() = Settings(
        init = false,
        chatModelId = realFlashModel.id,
        fastModelId = realFlashModel.id,
        // 关掉 autoRetry：关掉内层网络重试与外层 ProviderFailover，确保打的是显式配置的模型。
        networkSetting = NetworkSetting(enableAutoRetry = false),
        providers = listOf(realProvider),
        assistants = listOf(
            realAssistant(realAssistantAId, "角色甲", "A", realFlashModel.id),
            realAssistant(realAssistantBId, "角色乙", "B", realGlmModel.id),
            realAssistant(realAssistantCId, "角色丙", "C", realFlashModel.id),
        ),
    )

    private fun buildChatManager(): ChatManager {
        val entry = appEntryPoint(appContext)
        val json = JsonInstant
        val memoryRepository = entry.memoryRepository()
        return ChatManager(
            context = appContext,
            appScope = AppScope(),
            appEventBus = entry.appEventBus(),
            settingsStore = settingsStore,
            conversationRepo = repository,
            memoryRepository = memoryRepository,
            memoryExtractor = MemoryExtractor(memoryRepository, entry.providerManager(), json),
            generationLoop = GenerationLoop(appContext, entry.providerManager(), json),
            translationHandler = TranslationHandler(entry.providerManager()),
            templateTransformer = entry.templateTransformer(),
            providerManager = entry.providerManager(),
            chatToolFactory = ChatToolFactory(
                json = json,
                memoryRepository = memoryRepository,
                conversationRepository = repository,
                localTools = LocalTools(appContext, entry.appEventBus(), entry.ttsManager(), settingsStore),
                mcpManager = entry.mcpManager(),
                skillManager = entry.skillManager(),
                workspaceRepository = entry.workspaceRepository(),
                filesManager = entry.filesManager(),
                khatKitToolProvider = entry.khatKitToolProvider(),
            ),
            mcpManager = entry.mcpManager(),
            filesManager = entry.filesManager(),
            workspaceRepository = entry.workspaceRepository(),
            folderRepository = FolderRepository(database.folderDao(), database.conversationDao()),
            placeholderTransformer = PlaceholderTransformer(settingsStore),
            ocrTransformer = OcrTransformer(appContext, settingsStore, entry.providerManager()),
            base64ImageToLocalFileTransformer = Base64ImageToLocalFileTransformer(entry.filesManager()),
        )
    }

    private data class StampedRound(
        val messages: List<UIMessage>,
        val run: GroupRunEntity,
    )

    /** 等「条数 + 全部助手盖章 + group_runs 到指定终态」三个正向信号同时成立。 */
    private suspend fun awaitStampedTerminalRound(
        chatManager: ChatManager,
        groupRunDao: GroupRunDAO,
        conversationId: Uuid,
        expected: Int,
        expectedStatus: String,
        timeoutMillis: Long,
    ): StampedRound {
        val liveFlow = chatManager.getConversationFlow(conversationId)
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last: List<UIMessage> = emptyList()
        var lastRun: GroupRunEntity? = null
        while (System.currentTimeMillis() < deadline) {
            val live = liveFlow.value.currentMessages
            last = live
            val assistants = live.filter { it.role == MessageRole.ASSISTANT }
            val allStamped = assistants.size >= expected && assistants.all { it.roleId != null }
            val trigger = live.lastOrNull { it.role == MessageRole.USER }
            val roundId = trigger?.let { GroupChat.roundIdFor(it.id.toString()) }
            val run = roundId?.let { groupRunDao.findByRound(conversationId.toString(), it) }
            if (run != null) lastRun = run
            if (allStamped && run?.status == expectedStatus) return StampedRound(live, requireNotNull(run))
            Thread.sleep(250)
        }
        throw AssertionError(
            "等待真实网关轮盖章 + run=$expectedStatus 超时（${timeoutMillis}ms）：" +
                "实际助手=${last.count { it.role == MessageRole.ASSISTANT }} 条，" +
                "最后状态=${lastRun?.status}；app 错误=${chatManager.errors.value.map { it.title to it.error }}",
        )
    }

    // ---------------- viewer 台账（契约 :232-235） ----------------

    private data class ViewerLedgerPlan(
        val key: String,
        val viewerRoleId: String,
        val predecessorId: String? = null,
        val chairRound: Boolean = false,
        val allowedOtherRoleIds: Set<String> = emptySet(),
    )

    private fun planViewerLedgerPlans(
        config: GroupConfig,
        planSteps: List<SpeakerStep>,
    ): List<ViewerLedgerPlan> {
        val byViewer = LinkedHashMap<String, ViewerLedgerPlan>()
        planSteps.forEach { step ->
            byViewer[step.role.id] = ViewerLedgerPlan(
                key = step.role.id,
                viewerRoleId = step.role.id,
                predecessorId = step.predecessorId,
                chairRound = step.chairRound,
                allowedOtherRoleIds = when {
                    step.chairRound -> config.roles.map { it.id }.filter { it != step.role.id }.toSet()
                    step.predecessorId != null -> setOf(step.predecessorId)
                    else -> emptySet()
                },
            )
        }
        config.roles.forEach { role ->
            byViewer.putIfAbsent(role.id, ViewerLedgerPlan(key = role.id, viewerRoleId = role.id))
        }
        return byViewer.values.toList()
    }

    private fun viewerVisibilityLedger(
        messages: List<UIMessage>,
        config: GroupConfig,
        plans: List<ViewerLedgerPlan>,
    ): JsonObject = buildJsonObject {
        plans.forEach { plan ->
            val visible = GroupChat.visibleMessages(
                config = config,
                messages = messages,
                viewerId = plan.viewerRoleId,
                predecessorId = plan.predecessorId,
                chairRound = plan.chairRound,
            )
            putJsonObject(plan.key) {
                put("viewer_role_id", plan.viewerRoleId)
                putJsonArray("visible_message_ids") {
                    visible.forEach { add(JsonPrimitive(it.id.toString())) }
                }
                put("visible_count", visible.size)
                put("predecessor_id", plan.predecessorId)
                put("chair_round", plan.chairRound)
                putJsonArray("visible_assistant_role_ids") {
                    visible.filter { it.role == MessageRole.ASSISTANT }
                        .mapNotNull { it.roleId }
                        .forEach { add(JsonPrimitive(it)) }
                }
            }
        }
    }

    private fun assertViewerVisibilityLedger(
        ledger: JsonObject,
        messages: List<UIMessage>,
        config: GroupConfig,
        plans: List<ViewerLedgerPlan>,
    ) {
        val byId = messages.associateBy { it.id.toString() }
        val userMessageIds = messages.filter { it.role == MessageRole.USER }.map { it.id.toString() }
        val summaryIds = messages.filter { it.roleId == GroupChat.SUMMARY_ID }.map { it.id.toString() }
        plans.forEach { plan ->
            val entry = requireNotNull(ledger[plan.key]) { "台账缺少 viewer=${plan.key}" }.jsonObject
            val visibleIds = entry.getValue("visible_message_ids").jsonArray
                .map { it.jsonPrimitive.content }
                .toSet()
            val expected = GroupChat.visibleMessages(
                config = config,
                messages = messages,
                viewerId = plan.viewerRoleId,
                predecessorId = plan.predecessorId,
                chairRound = plan.chairRound,
            ).map { it.id.toString() }.toSet()
            assertEquals(
                "viewer=${plan.key} 的台账必须等于生产 visibleMessages 的输出",
                expected,
                visibleIds,
            )
            userMessageIds.forEach { id ->
                assertTrue("viewer=${plan.key} 必须可见 user 触发消息 $id", id in visibleIds)
            }
            messages.filter { it.role == MessageRole.ASSISTANT && it.roleId == plan.viewerRoleId }
                .forEach { own ->
                    assertTrue(
                        "viewer=${plan.key} 必须可见自己发的助手消息 ${own.id}",
                        own.id.toString() in visibleIds,
                    )
                }
            summaryIds.forEach { id ->
                assertTrue("viewer=${plan.key} 必须可见轮次摘要 $id", id in visibleIds)
            }
            visibleIds.mapNotNull { byId[it] }
                .filter { it.role == MessageRole.ASSISTANT }
                .mapNotNull { it.roleId }
                .filter { it != plan.viewerRoleId && it != GroupChat.SUMMARY_ID }
                .forEach { otherRole ->
                    assertTrue(
                        "viewer=${plan.key} 的可见集里出现未授权角色 $otherRole 的助手消息",
                        otherRole in plan.allowedOtherRoleIds,
                    )
                }
        }
    }

    // ---------------- 生产导出 SHA-256（契约 :206） ----------------

    private suspend fun exportGroupJsonlEvidence(conversationId: Uuid, exportFileName: String): JsonObject {
        val stored = requireNotNull(repository.getConversationById(conversationId)) {
            "会话 $conversationId 必须能从真库读回后才能导出"
        }
        val config = requireNotNull(stored.groupConfig) {
            "群会话必须带 groupConfig 才能走 Tavern 群聊导出"
        }
        val exported = TavernChatCodec.exportGroupJsonl(
            nodes = stored.messageNodes,
            config = config,
            cards = stored.groupCards.orEmpty(),
            userName = EXPORT_USER_NAME,
            groupName = "C1-10 real gateway ${stored.title}",
            createDate = null,
        )
        val bytes = exported.toByteArray(Charsets.UTF_8)
        assertTrue("导出字节不应为空：$exportFileName", bytes.isNotEmpty())
        assertTrue(
            "导出的首行必须是带 chat_metadata 的表头（生产 JSONL 形态）",
            exported.lineSequence().first().contains("chat_metadata"),
        )
        val sha = sha256Hex(bytes)

        // 生产 IO 分发：真写进 app 临时目录，返回 FileProvider URI。
        val uri = writeExportTempFile(context, exportFileName) { it.write(bytes) }
        val productionFile = File(context.appTempFolder, exportFileName)
        assertEquals("生产 IO 落盘字节数必须等于导出字节数", bytes.size.toLong(), productionFile.length())
        assertTrue(
            "生产 IO 落盘内容必须逐字节等于导出字节",
            productionFile.readBytes().contentEquals(bytes),
        )

        val pullDir = File(
            requireNotNull(context.getExternalFilesDir(null)) { "external files dir 为 null" },
            EXPORT_PULL_DIR,
        )
        assertTrue("导出 pull 目录建不出来：$pullDir", pullDir.mkdirs() || pullDir.isDirectory)
        val pullFile = File(pullDir, exportFileName)
        pullFile.writeBytes(bytes)
        assertEquals("pull 副本哈希必须与导出字节哈希一致", sha, sha256Hex(pullFile.readBytes()))

        return buildJsonObject {
            put("export_sha256", sha)
            put("export_bytes", bytes.size)
            put("export_line_count", exported.lines().count { it.isNotBlank() })
            put(
                "export_sha256_source",
                "same-process MessageDigest(\"SHA-256\") over the bytes produced by production " +
                    "TavernChatCodec.exportGroupJsonl; re-written through production writeExportTempFile",
            )
            put("export_path", pullFile.absolutePath)
            put("export_file_name", exportFileName)
            put("export_production_io_file", productionFile.absolutePath)
            put("export_production_io_uri", uri.toString())
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeEvidence(name: String, payload: JsonObject) {
        val dir = requireNotNull(context.getExternalFilesDir(null)) { "getExternalFilesDir(null) 不应为 null" }
        val file = File(dir, name)
        file.writeText(PRETTY.encodeToString(JsonObject.serializer(), payload))
        assertTrue("证据文件不应为空：${file.absolutePath}", file.length() > 0)
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

        /** 群聊页专有的群配置胶囊/副标题尾巴（`GroupInfoChip` / `GroupTopBar` 都含它）。 */
        const val GROUP_INFO_SUFFIX = "个角色"

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

        /** 真实网关轮的触发文本。 */
        const val TRIGGER_TEXT = "请三位依次发言，每位一句话。"

        /** 导出证据 JSONL 里写的「用户名」（导出器必填参数，不是隐私数据）。 */
        const val EXPORT_USER_NAME = "C1 验证用户"

        /** `adb pull` 导出副本的目录（挂在 external files dir 下）。 */
        const val EXPORT_PULL_DIR = "c1-live-export-c1-10"

        val PRETTY = Json { prettyPrint = true; encodeDefaults = true }
    }
}

private fun resolveAppContext(): Application =
    InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
