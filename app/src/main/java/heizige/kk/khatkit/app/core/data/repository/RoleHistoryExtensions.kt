package heizige.kk.khatkit.app.core.data.repository

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import kotlin.uuid.Uuid

data class RoleHistoryHit(
    val conversationId: String,
    val messageId: String,
    val title: String,
    val date: String,
    val text: String,
)

fun Conversation.includesRole(roleId: Uuid): Boolean =
    if (groupConfig != null) groupConfig.roles.any { it.assistantId == roleId.toString() }
    else type == GroupChat.TYPE_DIRECT && assistantId == roleId

fun Conversation.messagesVisibleToRole(roleId: Uuid): List<UIMessage> {
    if (!includesRole(roleId)) return emptyList()
    val config = groupConfig ?: return currentMessages
    val ids = config.roles.filter { it.assistantId == roleId.toString() }.map { it.id }
    val visibleIds = ids.flatMap { id -> GroupChat.buildContext(id, currentMessages, config) }
        .mapTo(hashSetOf()) { it.id }
    return currentMessages.filter { it.id in visibleIds }
}
