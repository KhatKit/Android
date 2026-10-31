package heizige.kk.khatkit.app.core.data.model

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.ai.util.InstantSerializer
import heizige.kk.khatkit.app.core.data.datastore.DEFAULT_ASSISTANT_ID
import heizige.kk.khatkit.app.core.data.datastore.Settings
import java.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Conversation(
    val id: Uuid = Uuid.random(),
    val assistantId: Uuid,
    val title: String = "",
    val messageNodes: List<MessageNode>,
    val chatSuggestions: List<String> = emptyList(),
    val isPinned: Boolean = false,
    @Serializable(with = InstantSerializer::class)
    val createAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updateAt: Instant = Instant.now(),
    val customSystemPrompt: String? = null,
    val modeInjectionIds: Set<Uuid> = emptySet(),
    val lorebookIds: Set<Uuid> = emptySet(),
    // Absolute path inside the workspace rootfs
    val workspaceCwd: String? = null,
    // 所属文件夹（助手内分组），null 表示未归入任何文件夹
    val folderId: Uuid? = null,
    val type: String = GroupChat.TYPE_DIRECT,
    val groupConfig: GroupConfig? = null,
    /**
     * 群聊**导入**时带进来的角色卡元数据（`GroupSharePayload.cards`）。
     *
     * 与 [groupConfig] 是两个来源、两种语义：[groupConfig] 是用户可编辑的活配置，
     * 本字段是「扫一次码带进来的附属快照」，只在导入那一刻被整体替换一次。
     * `null` = 从没导入过角色卡（Room 31 及更早版本的存量行迁移后就是 `null`），
     * 空列表 = 导入过但载荷里确实一张卡都没有。
     *
     * 导出（二维码 / 文本分享 / 酒馆群聊文件）**不读**本字段：那三条路径一律用
     * `groupExportRoleCards` 从当前群配置与本地助手现场生成，导入快照里的 persona
     * 属于导出方那台机器，搬到本机只会是过期的、还可能指向不存在的助手。
     */
    val groupCards: List<RoleCardMeta>? = null,
    @Transient
    val newConversation: Boolean = false
) {
    val files: List<Uri>
        get() = messageNodes
            .flatMap { node -> node.messages.flatMap { it.parts } }
            .localFileUrls()
            .map { it.toUri() }

    /**
     *  当前选中的 message
     */
    val currentMessages
        get(): List<UIMessage> {
            return messageNodes.map { node -> node.messages[node.selectIndex] }
        }

    fun getMessageNodeByMessage(message: UIMessage): MessageNode? {
        return messageNodes.firstOrNull { node -> node.messages.contains(message) }
    }

    fun getMessageNodeByMessageId(messageId: Uuid): MessageNode? {
        return messageNodes.firstOrNull { node -> node.messages.any { it.id == messageId } }
    }

    fun updateCurrentMessages(messages: List<UIMessage>): Conversation {
        val newNodes = this.messageNodes.toMutableList()

        messages.forEachIndexed { index, message ->
            val node = newNodes
                .getOrElse(index) { message.toMessageNode() }

            val newMessages = node.messages.toMutableList()
            var newMessageIndex = node.selectIndex
            if (newMessages.any { it.id == message.id }) {
                newMessages[newMessages.indexOfFirst { it.id == message.id }] = message
            } else {
                newMessages.add(message)
                newMessageIndex = newMessages.lastIndex
            }

            val newNode = node.copy(
                messages = newMessages,
                selectIndex = newMessageIndex
            )

            // 更新newNodes
            if (index > newNodes.lastIndex) {
                newNodes.add(newNode)
            } else {
                newNodes[index] = newNode
            }
        }

        return this.copy(
            messageNodes = newNodes
        )
    }

    companion object {
        fun ofId(
            id: Uuid,
            assistantId: Uuid = DEFAULT_ASSISTANT_ID,
            messages: List<MessageNode> = emptyList(),
            newConversation: Boolean = false
        ) = Conversation(
            id = id,
            assistantId = assistantId,
            messageNodes = messages,
            newConversation = newConversation,
        )
    }
}

@Serializable
data class MessageNode(
    val id: Uuid = Uuid.random(),
    val messages: List<UIMessage>,
    val selectIndex: Int = 0,
    /**
     * 消息 id（字符串）→ 生成它的模型名快照。
     *
     * 对齐上游 4a7a39c47：消息只存 modelId，名称要拿"当前"提供商配置现查，模型一删就查不到。
     * 这里在节点上留一份快照，查不到模型时用它显示。放在节点而不是消息上，是因为
     * UIMessage 在 ai 模块（分工上不归本棒改，AI-1 负责加 modelSnapshot 字段）；
     * 等 ai 侧字段到位后这里的读写点可平滑并过去（key 都是消息 id）。
     */
    val modelSnapshots: Map<String, ModelSnapshot> = emptyMap(),
) {
    val currentMessage get() = if (messages.isEmpty() || selectIndex !in messages.indices) {
        throw IllegalStateException("MessageNode has no valid current message: messages.size=${messages.size}, selectIndex=$selectIndex")
    } else {
        messages[selectIndex]
    }

    val role get() = messages.firstOrNull()?.role ?: MessageRole.USER

    /** 这条消息的模型名快照（没有就 null）。展示层查不到模型时用它回退。 */
    fun snapshotFor(messageId: Uuid): ModelSnapshot? = modelSnapshots[messageId.toString()]

    /** 展示用：模型名快照的 displayName。 */
    fun snapshotDisplayName(messageId: Uuid): String? = snapshotFor(messageId)?.displayName

    companion object {
        fun of(message: UIMessage) = MessageNode(
            messages = listOf(message),
            selectIndex = 0
        )
    }
}

/** 生成消息的模型名快照，对应 [Model.modelId] 与 [Model.displayName]。 */
@Serializable
data class ModelSnapshot(
    val modelId: String,
    val displayName: String,
)

/**
 * 给还没有模型快照的消息补上快照，没有可补的消息时原样返回。
 *
 * 在保存会话时调用（ChatManager.saveConversation）：新生成的消息由此带上快照，引入快照之前的
 * 旧消息也在会话下次保存时补齐。模型已经被删掉的旧消息无从补起，保持原样。
 */
fun Conversation.fillModelSnapshots(settings: Settings): Conversation {
    val models = settings.providers.flatMap { it.models }.associateBy { it.id }
    val nodes = messageNodes.map { node ->
        var changed = false
        val snapshots = buildMap {
            putAll(node.modelSnapshots)
            node.messages.forEach { message ->
                val key = message.id.toString()
                if (containsKey(key)) return@forEach
                val model = message.modelId?.let(models::get) ?: return@forEach
                changed = true
                put(key, ModelSnapshot(modelId = model.modelId, displayName = model.displayName))
            }
        }
        if (changed) node.copy(modelSnapshots = snapshots) else node
    }
    return if (nodes == messageNodes) this else copy(messageNodes = nodes)
}

fun UIMessage.toMessageNode(): MessageNode {
    return MessageNode(
        messages = listOf(this),
        selectIndex = 0
    )
}

/** 本地附件引用，包含工具结果中的嵌套附件。 */
internal fun List<UIMessagePart>.localFileUrls(): Set<String> = buildSet {
    this@localFileUrls.forEach { part ->
        val url = when (part) {
            is UIMessagePart.Image -> part.url
            is UIMessagePart.Document -> part.url
            is UIMessagePart.Video -> part.url
            is UIMessagePart.Audio -> part.url
            is UIMessagePart.Tool -> {
                addAll(part.output.localFileUrls())
                null
            }

            else -> null
        }
        if (url?.startsWith("file://") == true) add(url)
    }
}
