package heizige.kk.khatkit.app.feature.chat


import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchResult
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchSort
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.Folder
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FolderRepository
import heizige.kk.khatkit.app.feature.chat.ChatManager
import kotlin.uuid.Uuid

@HiltViewModel
class ChatDrawerViewModel @Inject constructor(
    private val settingsStore: SettingsRepository,
    private val conversationRepo: ConversationRepository,
) : ViewModel() {

    private val assistantIdFlow = settingsStore.settingsFlow
        .map { it.assistantId }
        .distinctUntilChanged()

    // 抽屉顶栏搜索关键字（命中当前角色会话的**消息正文**，见 [messageSearch]）
    private val _searchKeyword = MutableStateFlow("")
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()

    fun updateSearchKeyword(keyword: String) {
        _searchKeyword.value = keyword
    }

    /**
     * 防抖后的关键字：非空白关键字等 [SEARCH_DEBOUNCE_MS] 才往下游发（避免每敲一个键打一次
     * FTS / 一次分页查询），空白关键字**立即**发出——清空搜索框要瞬时恢复原列表，不能等。
     *
     * 用 `flatMapLatest` + `delay` 而不是 Flow 的 `debounce`：新关键字进来会取消上一条内部
     * flow，等价于防抖，且与 `SearchViewModel` 里同一套写法保持一致（那条是 collectLatest
     * + delay），不额外引 `@FlowPreview` 的 API。
     */
    private val debouncedKeyword: StateFlow<String> = _searchKeyword
        .flatMapLatest { keyword ->
            if (keyword.isBlank()) {
                flowOf(keyword)
            } else {
                flow {
                    delay(SEARCH_DEBOUNCE_MS)
                    emit(keyword)
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /**
     * 抽屉搜索态：关键字非空时检索**当前角色（settings.assistantId）下所有会话的消息正文**，
     * 命中项带会话标题、FTS 片段（`[关键词]` 标记）与时间，由 UI 侧高亮并跳转到 nodeId。
     *
     * 降级（FTS 索引为空 / 没建 / 查询抛错都不能让抽屉崩）：
     * - 消息命中为空 → 回退按会话标题匹配（[DrawerMessageSearchState.titleFallback]）。
     *   这条降级路在 UI 里有**可见的分节标题**（「未在消息正文中找到，以下为标题匹配」）
     *   与可点击的会话行，不是藏在背后的隐式行为。
     * - 两条都空 → 空态，UI 给中性文案（不指引跳去任何页面）。
     *
     * 关键字为空 → 空状态，UI 完全走原来的角色列表 / 会话列表（文件夹、类型筛选都不受影响）。
     *
     * 命中条数上限：[DRAWER_SEARCH_HIT_LIMIT]（与 FTS 查询的 LIMIT 对齐，UI 文案标注「最多 N 条」）。
     */
    val messageSearch: StateFlow<DrawerMessageSearchState> =
        combine(assistantIdFlow, debouncedKeyword) { assistantId, keyword -> assistantId to keyword }
            .distinctUntilChanged()
            .flatMapLatest { (assistantId, keyword) ->
                if (keyword.isBlank()) {
                    flowOf(DrawerMessageSearchState())
                } else {
                    flow {
                        emit(DrawerMessageSearchState(keyword = keyword, searching = true))
                        // FTS 表可能还没建/没索引：searchMessages 抛错也只降级，不冒泡到 UI
                        val hits = runCatching {
                            conversationRepo.searchMessages(
                                keyword = keyword,
                                sort = MessageSearchSort.NEWEST_FIRST,
                                assistantId = assistantId,
                            )
                        }.getOrDefault(emptyList())
                        val fallback =
                            if (hits.isEmpty()) titleMatches(keyword, assistantId) else emptyList()
                        emit(
                            DrawerMessageSearchState(
                                keyword = keyword,
                                searching = false,
                                hits = hits,
                                titleFallback = fallback,
                            )
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DrawerMessageSearchState())

    /** 降级路：当前角色下标题包含关键字的会话（只在消息正文命中为空时兜底，UI 有可见分节标题）。 */
    private suspend fun titleMatches(keyword: String, assistantId: Uuid): List<Conversation> =
        runCatching {
            conversationRepo.getConversationsOfAssistant(assistantId)
                .first()
                .asSequence()
                .filter { it.title.contains(keyword, ignoreCase = true) }
                .take(MAX_TITLE_FALLBACK)
                .toList()
        }.getOrDefault(emptyList())

    val roleChats = conversationRepo.getRoleConversationSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}


/**
 * 抽屉搜索态的一次快照（消息正文检索 + 标题降级）。
 *
 * - [keyword]：本次命中对应的关键字（UI 用它判断「这份状态是不是当前输入框的」，避免
 *   防抖窗口内把上一轮结果当成本轮结果渲染）
 * - [searching]：查询进行中（UI 显示「正在检索…」而不是闪一下空态）
 * - [hits]：FTS 命中（[MessageSearchResult] 带 nodeId / conversationId / 标题 / 片段 / 时间），
 *   最多 [DRAWER_SEARCH_HIT_LIMIT] 条（与 FTS 查询的 LIMIT 对齐）
 * - [titleFallback]：FTS 没命中时的标题兜底（索引未建 / 老数据没进索引时不至于一条都没有）。
 *   这是**降级路**，UI 用一个可见的分节标题明确标注「以下为标题匹配」，不与消息命中混淆。
 */
data class DrawerMessageSearchState(
    val keyword: String = "",
    val searching: Boolean = false,
    val hits: List<MessageSearchResult> = emptyList(),
    val titleFallback: List<Conversation> = emptyList(),
)

/** 抽屉搜索防抖窗口（毫秒）。 */
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * 抽屉一次搜索最多返回的消息命中条数。
 *
 * 镜像 `MessageFtsManager` 里 FTS 查询的 `LIMIT 50`：那个文件在 `core/data`，不在本任务
 * scope，所以这里不构成查询的真源，只用来让 UI 文案和实际上限对齐（「最多 N 条」）。
 * 改 `MessageFtsManager` 的 LIMIT 时同步改这里，否则文案会撒谎。
 */
internal const val DRAWER_SEARCH_HIT_LIMIT = 50

/** 降级路最多列出的标题匹配会话数。 */
private const val MAX_TITLE_FALLBACK = 30

/**
 * 抽屉 chip 的筛选值 → DAO 的 type 参数。
 *
 * `FILTER_ALL`（以及任何空白值）折成空串，即 DAO 里 `AND (:type = '' OR type = :type)`
 * 的「不筛」分支——和未归档路同一套语义，不在这里发明第二种表达。
 */
internal fun String.asConversationTypeArgument(): String =
    takeUnless { it == GroupChat.FILTER_ALL || it.isBlank() }.orEmpty()
