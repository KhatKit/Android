package heizige.kk.khatkit.app.feature.chat.helper

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.feature.chat.floating.FloatingChatUiState
import heizige.kk.khatkit.app.feature.chat.data.GenerationPhase
import heizige.kk.khatkit.app.feature.chat.data.generationPhase
import heizige.kk.khatkit.app.feature.chat.data.MessageQueueState

/** 汇总一份悬浮窗状态；reply 取最后一个助手消息（本 fork 还没记录本轮 reply id）。 */
internal fun buildFloatingChatUiState(
    conversation: Conversation,
    generating: Boolean,
    processingStatus: String?,
    queue: MessageQueueState,
    errorMessage: String?,
): FloatingChatUiState {
    val reply = conversation.messageNodes.asReversed().asSequence()
        .flatMap { node -> node.messages.asReversed().asSequence() }
        .firstOrNull { it.role == MessageRole.ASSISTANT }
    val text = reply?.parts?.filterIsInstance<UIMessagePart.Text>()?.lastOrNull()?.text?.trim().orEmpty()
    val reasoning = reply?.parts?.filterIsInstance<UIMessagePart.Reasoning>()?.lastOrNull()?.reasoning?.trim().orEmpty()
    return FloatingChatUiState(
        conversationId = conversation.id,
        title = conversation.title,
        generating = generating,
        processingStatus = processingStatus,
        phase = reply?.generationPhase() ?: GenerationPhase.Waiting,
        hasReply = reply != null,
        preview = text.ifEmpty { reasoning },
        previewIsReasoning = text.isEmpty(),
        pendingToolCount = 0,
        queuedCount = queue.messages.size,
        queuePaused = queue.paused,
        errorMessage = errorMessage,
    )
}
