package heizige.kk.khatkit.app.core.data.ai.tools

import heizige.kk.khatkit.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

/**
 * Core 层向会话补发卡片交互结果的最小接口。
 *
 * 只暴露「把迟到交互结果写回原会话」这一条能力；实现方 [heizige.kk.khatkit.app.feature.chat.helper.ChatManager]
 * 在 feature/chat 的 Hilt 模块绑定，避免 core/data -> feature.chat 的反向 import。
 */
fun interface CardChatGateway {
    fun deliverCardMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean)
}
