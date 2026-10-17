package heizige.kk.khatkit.app.feature.chat.data
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import kotlin.uuid.Uuid

internal fun selectRoleChat(
    roleId: Uuid,
    current: Conversation,
    chats: List<Conversation>,
    running: Set<Uuid>,
): Uuid? {
    fun Conversation.isDirectRoleChat() = assistantId == roleId && type == GroupChat.TYPE_DIRECT
    if (current.isDirectRoleChat()) return current.id
    val own = chats.filter { it.isDirectRoleChat() }.sortedByDescending { it.updateAt }
    return own.firstOrNull { it.id in running }?.id ?: own.firstOrNull()?.id
}
