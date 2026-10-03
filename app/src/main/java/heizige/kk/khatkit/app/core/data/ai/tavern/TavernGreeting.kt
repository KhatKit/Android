package heizige.kk.khatkit.app.core.data.ai.tavern

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.toMessageNode

/** Seeds a new conversation. Alternate greetings become swipes on the first node. */
fun tavernSeedNodes(assistant: Assistant): List<MessageNode> {
    val greetings = assistant.tavernCardJson?.let { raw ->
        runCatching { CharacterCardCodec.greetingSwipes(raw) }.getOrNull()
    }.orEmpty()
    if (greetings.size > 1) {
        return listOf(
            MessageNode(
                messages = greetings.map { UIMessage.assistant(it) },
                selectIndex = 0,
            )
        )
    }
    return assistant.presetMessages.map { it.toMessageNode() }
}
