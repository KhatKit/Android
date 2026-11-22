package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.core.TokenUsage
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.feature.chat.helper.lastReportedContextUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatContextUsageTest {
    private val model = Model(modelId = "test")
    private val answer = UIMessage.assistant("result").copy(modelId = model.id,
        usage = TokenUsage(6000, 1000), lastUsage = TokenUsage(3000, 200), stepCount = 4)

    @Test fun `uses last request instead of cumulative usage`() {
        val usage = requireNotNull(lastReportedContextUsage(listOf(answer), model) { 8000 })
        assertEquals(3200L, usage.tokens)
        assertEquals(0.4f, usage.fraction)
    }

    @Test fun `handles unknown limits and excludes unrelated model usage`() {
        assertNull(lastReportedContextUsage(listOf(answer), model) { null }?.fraction)
        assertNull(lastReportedContextUsage(listOf(answer), Model(modelId = "same-name-different-config")) { 8000 })
        assertNull(lastReportedContextUsage(listOf(answer.copy(lastUsage = null)), model) { 8000 })
    }
}
