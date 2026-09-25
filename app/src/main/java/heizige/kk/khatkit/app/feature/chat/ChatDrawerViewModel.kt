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

    fun updateSearchKeyword(keyword: String) {
        _searchKeyword.value = keyword
    }

    // 当前助手的文件夹列表（Room Flow，增删改自动刷新）
    val folders: StateFlow<List<Folder>> = assistantIdFlow
        .flatMapLatest { folderRepo.getFoldersOfAssistant(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 主列表展示当前助手下未归入任何文件夹的会话，按时间分组
    val conversations: Flow<PagingData<ConversationListItem>> =
        combine(assistantIdFlow, _searchKeyword) { assistantId, keyword -> assistantId to keyword }
            .flatMapLatest { (assistantId, keyword) ->
                if (keyword.isNotBlank()) {
                    conversationRepo.searchConversationsOfAssistantPaging(assistantId, keyword)
                } else {
                    conversationRepo.getUnfiledConversationsOfAssistantPaging(assistantId)
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
