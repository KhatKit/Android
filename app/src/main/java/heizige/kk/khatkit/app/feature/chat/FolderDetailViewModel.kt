package heizige.kk.khatkit.app.feature.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.Folder
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FolderRepository
import kotlin.uuid.Uuid

/**
 * 文件夹二级页：展示该文件夹内的会话（按时间分组）。
 */
@HiltViewModel(assistedFactory = FolderDetailViewModel.Factory::class)
class FolderDetailViewModel @AssistedInject constructor(
    @Assisted folderId: String,
    private val context: Application,
    private val conversationRepo: ConversationRepository,
    private val folderRepo: FolderRepository,
    private val chatService: ChatManager,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(folderId: String): FolderDetailViewModel
    }

    private val id = Uuid.parse(folderId)

    private val _folder = MutableStateFlow<Folder?>(null)
    val folder: StateFlow<Folder?> = _folder.asStateFlow()

    val conversations: Flow<PagingData<ConversationListItem>> = conversationRepo
        .getConversationsOfFolderPaging(id)
        .map { pagingData ->
            pagingData.insertConversationSections { group -> context.conversationGroupLabel(group) }
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            _folder.value = folderRepo.getFolderById(id)
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationRepo.deleteConversation(conversation)
        }
    }

    fun togglePinned(conversation: Conversation) {
        viewModelScope.launch {
            chatService.toggleConversationPinned(conversation.id)
        }
    }

    fun removeFromFolder(conversation: Conversation) {
        viewModelScope.launch {
            chatService.moveConversationToFolder(conversation.id, null)
        }
    }

    fun generateTitle(conversation: Conversation) {
        viewModelScope.launch {
            val full = conversationRepo.getConversationById(conversation.id) ?: return@launch
            chatService.generateTitle(conversation.id, full, force = true)
        }
    }
}
