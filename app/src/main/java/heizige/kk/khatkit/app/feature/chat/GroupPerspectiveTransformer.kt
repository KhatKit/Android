package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.ai.transformers.InputMessageTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.TransformerContext
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupChat

/** 只裁剪发给模型的副本。会话里的完整消息仍保留。 */
class GroupPerspectiveTransformer(
    private val config: GroupConfig,
    private val viewerId: String,
    private val predecessorId: String? = null,
    private val chairRound: Boolean = false,
) : InputMessageTransformer {
    override suspend fun transform(ctx: TransformerContext, messages: List<UIMessage>): List<UIMessage> {
        return GroupChat.buildContext(
            viewerRoleId = viewerId,
            messages = messages,
            config = config,
            predecessorId = predecessorId,
            chairRound = chairRound,
        )
    }
}
