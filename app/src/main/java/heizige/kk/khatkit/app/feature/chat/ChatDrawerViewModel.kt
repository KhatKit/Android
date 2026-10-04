package heizige.kk.khatkit.app.feature.chat


import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.model.Folder
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FolderRepository
import heizige.kk.khatkit.app.feature.chat.ChatManager
import kotlin.uuid.Uuid

@HiltViewModel
class ChatDrawerViewModel @Inject constructor(
    private val context: Application,
    private val settingsStore: SettingsRepository,
    conversationRepo: ConversationRepository,
    private val folderRepo: FolderRepository,
    private val chatService: ChatManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val assistantIdFlow = settingsStore.settingsFlow
        .map { it.assistantId }
        .distinctUntilChanged()

    // 抽屉顶栏搜索关键字（按标题过滤会话）
    private val _searchKeyword = MutableStateFlow("")
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()

    private val _typeFilter = MutableStateFlow(GroupChat.FILTER_ALL)
    val typeFilter: StateFlow<String> = _typeFilter.asStateFlow()

    fun updateTypeFilter(filter: String) {
        _typeFilter.value = filter
    }

    fun updateSearchKeyword(keyword: String) {
        _searchKeyword.value = keyword
    }

    // 当前助手的文件夹列表（Room Flow，增删改自动刷新）
    val folders: StateFlow<List<Folder>> = assistantIdFlow
        .flatMapLatest { folderRepo.getFoldersOfAssistant(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 主列表展示当前助手下未归入任何文件夹的会话，按时间分组。
    // 类型筛选整个下沉给 SQL（见 planConversationListQuery）：type 只作为查询参数传下去，
    // 不再对 PagingData 做内存过滤——内存过滤只作用在已加载的那几页，翻页会漏出别的类型，
    // 而且 PagingData 的 itemCount 会算错。
    val conversations: Flow<PagingData<ConversationListItem>> =
        combine(assistantIdFlow, _searchKeyword, _typeFilter) { assistantId, keyword, type ->
                Triple(assistantId, keyword, type)
            }
            .flatMapLatest { (assistantId, keyword, type) ->
                val plan = planConversationListQuery(keyword, type)
                when (plan.query) {
                    ConversationListQuery.SEARCH -> conversationRepo.searchConversationsOfAssistantPaging(
                        assistantId,
                        keyword,
                        plan.typeArgument,
                    )

                    ConversationListQuery.UNFILED -> conversationRepo.getUnfiledConversationsOfAssistantPaging(
                        assistantId,
                        plan.typeArgument,
                    )
                }
            }
            .map { pagingData ->
                pagingData.insertConversationSections { group ->
                    context.conversationGroupLabel(group)
                }
            }
            .cachedIn(viewModelScope)

    val scrollIndex: Int get() = savedStateHandle["scrollIndex"] ?: 0
    val scrollOffset: Int get() = savedStateHandle["scrollOffset"] ?: 0

    fun saveScrollPosition(index: Int, offset: Int) {
        savedStateHandle["scrollIndex"] = index
        savedStateHandle["scrollOffset"] = offset
    }

    fun createGroup(onCreated: (Uuid) -> Unit) {
        viewModelScope.launch {
            onCreated(chatService.createGroup())
        }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val assistantId = assistantIdFlow.first()
            folderRepo.createFolder(assistantId, trimmed)
        }
    }

    fun renameFolder(folderId: Uuid, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            folderRepo.renameFolder(folderId, trimmed)
        }
    }

    /**
     * 删除文件夹。若文件夹内有正在生成回复的会话，拒绝删除并返回 false（UI 层据此提示用户）。
     */
    fun deleteFolder(folderId: Uuid): Boolean {
        if (chatService.hasGeneratingConversationInFolder(folderId)) {
            return false
        }
        viewModelScope.launch {
            // 经 ChatManager 删除：会同步清空活跃 session 内存态的 folderId，避免整对象保存写回已删文件夹
            chatService.deleteFolder(folderId)
        }
        return true
    }

    fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        viewModelScope.launch {
            // 经 ChatManager 移动：活跃会话会先同步内存态，避免后续整对象保存覆盖 folder_id
            chatService.moveConversationToFolder(conversationId, folderId)
        }
    }
}

/** 抽屉会话列表走哪条 DB 查询：搜索路，还是未归档路。 */
internal enum class ConversationListQuery {
    SEARCH,
    UNFILED,
}

/**
 * 抽屉会话列表的一次查询判定结果：[query] 是走哪条 DB 查询，[typeArgument] 是传给它的 type 参数。
 */
internal data class ConversationListQueryPlan(
    val query: ConversationListQuery,
    val typeArgument: String,
)

/**
 * 抽屉会话列表的查询判定，纯函数（不碰 Repository / Room），抽出来是为了能在 JVM 单测里
 * 直接钉死契约「type 只作为 SQL 参数传下去，筛选只过滤、不丢数据」。
 *
 * 判定规则：
 * - 关键字非空 → [ConversationListQuery.SEARCH]（与改动前一致）
 * - 关键字为空 → [ConversationListQuery.UNFILED]（与改动前一致）
 * - 切类型筛选**不改变**走哪条查询，只改变传下去的 type 参数，所以切 chip 不会把
 *   底层数据源换成另一张表/另一个 DAO 查询
 */
internal fun planConversationListQuery(
    keyword: String,
    typeFilter: String,
): ConversationListQueryPlan = ConversationListQueryPlan(
    query = if (keyword.isNotBlank()) ConversationListQuery.SEARCH else ConversationListQuery.UNFILED,
    typeArgument = typeFilter.asConversationTypeArgument(),
)

/**
 * 抽屉 chip 的筛选值 → DAO 的 type 参数。
 *
 * `FILTER_ALL`（以及任何空白值）折成空串，即 DAO 里 `AND (:type = '' OR type = :type)`
 * 的「不筛」分支——和未归档路同一套语义，不在这里发明第二种表达。
 */
internal fun String.asConversationTypeArgument(): String =
    takeUnless { it == GroupChat.FILTER_ALL || it.isBlank() }.orEmpty()
