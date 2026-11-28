package heizige.kk.khatkit.app.feature.chat.helper

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.registry.ModelRegistry
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.feature.chat.data.ChatContextUsage



internal fun lastReportedContextUsage(
    messages: List<UIMessage>,
    model: Model?,
    lookupLimit: (String) -> Int? = { ModelRegistry.MODEL_CONTEXT_LENGTH.getData(it) },
): ChatContextUsage? {
    if (model == null) return null
    val last = messages.lastOrNull { it.role == MessageRole.ASSISTANT && !it.isSynthetic } ?: return null
    if (last.modelId != model.id) return null
    // Old aggregate usage over several tool steps is not context usage for a single request.
    val usage = last.lastUsage ?: last.usage?.takeIf { (last.stepCount ?: 1) <= 1 } ?: return null
    if (usage.promptTokens <= 0) return null
    val tokens = usage.promptTokens.toLong() + usage.completionTokens.coerceAtLeast(0)
    return ChatContextUsage(tokens, lookupLimit(model.modelId)?.takeIf { it > 0 })
}
