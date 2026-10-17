package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat

internal const val LEGACY_GROUP_ARCHIVE_MESSAGE = "群聊功能已移除，历史记录仅供查看和导出"

internal fun Conversation.isArchivedGroupConversation(): Boolean = type == GroupChat.TYPE_GROUP

internal fun requireActiveRoleConversation(conversation: Conversation) {
    check(!conversation.isArchivedGroupConversation()) { LEGACY_GROUP_ARCHIVE_MESSAGE }
}
