package heizige.kk.khatkit.app.core.data.model

import kotlin.uuid.Uuid

data class MessageAnchor(
    val conversationId: Uuid,
    val nodeId: Uuid,
    val selectIndex: Int,
)

fun locateMessage(conversationId: Uuid, node: MessageNode, messageId: Uuid): MessageAnchor? {
    val index = node.messages.indexOfFirst { it.id == messageId }
    if (index < 0) return null
    return MessageAnchor(conversationId, node.id, index)
}

fun locateMessage(conversationId: Uuid, nodes: List<MessageNode>, messageId: Uuid): MessageAnchor? {
    nodes.forEach { node ->
        locateMessage(conversationId, node, messageId)?.let { return it }
    }
    return null
}
