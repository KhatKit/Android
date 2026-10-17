package heizige.kk.khatkit.app.core.network.routes

import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.network.ConflictException
import heizige.kk.khatkit.app.feature.chat.LEGACY_GROUP_ARCHIVE_MESSAGE
import heizige.kk.khatkit.app.feature.chat.isArchivedGroupConversation

internal enum class ConversationOperation {
    SendMessage,
    EditMessage,
    Fork,
    DeleteMessage,
    SelectNode,
    Regenerate,
}

internal fun groupConversationOperationRejection(
    conversation: Conversation,
    operation: ConversationOperation,
): String? {
    if (!conversation.isArchivedGroupConversation()) return null
    return "${operation.name}: $LEGACY_GROUP_ARCHIVE_MESSAGE。" +
        "可用 GET /api/conversations/{id} 读取历史，或新建角色聊天。"
}

/** Call after initializing the session so an unloaded placeholder cannot bypass the archive. */
internal fun requireConversationOperationAllowed(
    conversation: Conversation,
    operation: ConversationOperation,
) {
    val reason = groupConversationOperationRejection(conversation, operation) ?: return
    throw ConflictException(reason)
}
