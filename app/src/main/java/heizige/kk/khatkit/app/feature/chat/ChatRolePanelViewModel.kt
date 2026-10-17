package heizige.kk.khatkit.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import kotlin.uuid.Uuid

/**
 * 「重命名当前聊天」这一行的可用性。
 *
 * @param title 当前标题（弹窗初始值）。
 * @param canRename 会话已落库**且**至少有一条消息。旧行为（顶栏长按标题）在空会话上给的是
 *   「请先聊天再编辑标题吧」的提示而不是把入口藏掉，这里用 [canRename] 表达同一件事。
 */
data class RolePanelRenameTarget(
    val title: String,
    val canRename: Boolean,
)

/**
 * 角色聊天面板（[ChatRolePanelPage]）的状态。
 *
 * 只做三件事：给页面当前设置、按角色过滤历史聊天、把「新建聊天 / 重命名当前聊天」落到
 * 数据层。UI 结构（option item 分区）全在页面里，这里不持有任何 Compose 状态。
 *
 * @param roleId 面板对应的角色（assistant id）。由聊天页顶栏**已解析出的那个角色**的 id
 *   传入（导航与查询同源），面板不自己再猜一次。
 */
@HiltViewModel(assistedFactory = ChatRolePanelViewModel.Factory::class)
class ChatRolePanelViewModel @AssistedInject constructor(
    @Assisted private val roleId: String,
    private val settingsStore: SettingsRepository,
    private val conversationRepo: ConversationRepository,
    private val chatService: ChatManager,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(roleId: String): ChatRolePanelViewModel
    }

    // 初始值直接取 settingsFlow 的当前值：面板首帧就要用角色名/头像/模型名，
    // 用 `Settings.dummy()` 当初始值会让顶部先闪一下「默认助手」再变成真角色。
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsStore.settingsFlow.value)

    /** 角色 id 解析一次：历史聊天过滤与「新建聊天」都用它，不要每次重新 parse。 */
    private val roleUuid: Uuid? = runCatching { Uuid.parse(roleId) }.getOrNull()

    /**
     * 该角色的历史聊天。
     *
     * 过滤**下推到 DAO**：`getConversationsOfAssistant` 是 `WHERE assistant_id = :id`
     * （`ORDER BY is_pinned DESC, update_at DESC, id ASC`），不再像上一版那样拉全表摘要
     * 再在内存里逐行比 assistantId。ViewModel 只剩「direct」这一条语义过滤 —— 群聊是另一条
     * 路径（群聊里的角色不等于该角色自己的会话），混进来会让「历史聊天」点开一个成员发言
     * 不全是自己的会话。
     */
    private val directChats: Flow<List<Conversation>> = roleUuid
        ?.let { id ->
            conversationRepo.getConversationsOfAssistant(id)
                .map { rows -> rows.filter { it.type == GroupChat.TYPE_DIRECT } }
        }
        ?: flowOf(emptyList())

    val roleChats: StateFlow<List<Conversation>> = directChats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 「新建聊天」：先把这个角色设成当前角色，再由调用方 `navigateToChatPage` 开一条新会话。
     *
     * 新会话是在 ChatPage 的 [ChatManager.initializeConversation] 里按
     * `settings.assistantId` 现造的（`Conversation.ofId(assistantId = 当前角色)`），
     * 所以「为某个角色新建」= 切当前角色 + 跳新 id 两个动作，不需要在这里先插一条空会话 ——
     * 先插空会话会留下「点开又退出」的空行，历史聊天里多一条无消息记录。
     *
     * 角色已不存在（被删掉）时**不静默返回**：回 [onResult] false，由页面给出可见提示。
     */
    fun startNewChat(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val id = roleUuid
        val current = settingsStore.settingsFlow.value
        if (id == null || current.assistants.none { it.id == id }) {
            onResult(false)
            return@launch
        }
        if (current.assistantId != id) {
            settingsStore.updateAssistant(id)
        }
        onResult(true)
    }

    /**
     * 「重命名当前聊天」的可用性 / 初始标题。
     *
     * 取 DB 全量会话（带 messageNodes）而不是历史列表那些行：历史行没有节点，既判断不了
     * 「有没有消息」，也不能直接拿来 saveConversation（会把节点删空）。
     */
    suspend fun loadRenameTarget(conversationId: Uuid): RolePanelRenameTarget? =
        conversationRepo.getConversationById(conversationId)?.let { conversation ->
            RolePanelRenameTarget(
                title = conversation.title,
                canRename = conversation.messageNodes.isNotEmpty(),
            )
        }

    /**
     * 「重命名当前聊天」：调 [renameConversationTitle]（唯一实现：活内存态优先，未加载回退 DB）。
     * 面板只是把 conversationId 补上，不再自己写一份 rename。
     */
    fun renameConversation(conversationId: Uuid, title: String) = viewModelScope.launch {
        renameConversationTitle(chatService, conversationRepo, conversationId, title)
    }
}
