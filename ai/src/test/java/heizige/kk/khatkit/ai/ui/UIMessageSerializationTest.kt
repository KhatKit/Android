package heizige.kk.khatkit.ai.ui

import heizige.kk.khatkit.ai.core.TokenUsage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UIMessageSerializationTest {

    @Test
    fun `synthetic marker is not serialized`() {
        val message = UIMessage.user("internal").copy(isSynthetic = true)

        val encoded = Json.encodeToString(message)
        val decoded = Json.decodeFromString<UIMessage>(encoded)

        assertTrue(message.isSynthetic)
        assertFalse(encoded.contains("isSynthetic"))
        assertFalse(decoded.isSynthetic)
    }

    @Test
    fun `step stats default to null and contextTokens falls back to usage`() {
        // 旧消息没有 lastUsage/stepCount：反序列化后应回落到 usage，行为不退化
        val message = UIMessage.assistant("hi").copy(
            usage = TokenUsage(promptTokens = 100, completionTokens = 20, totalTokens = 120),
        )

        val encoded = Json.encodeToString(message)
        assertFalse(encoded.contains("lastUsage"))
        assertFalse(encoded.contains("stepCount"))

        val decoded = Json.decodeFromString<UIMessage>(encoded)
        assertNull(decoded.lastUsage)
        assertNull(decoded.stepCount)
        assertEquals(120, decoded.contextTokens())
    }

    @Test
    fun `lastUsage wins over cumulative usage for context size`() {
        val message = UIMessage.assistant("hi").copy(
            usage = TokenUsage(promptTokens = 300, completionTokens = 60, totalTokens = 360),
            lastUsage = TokenUsage(promptTokens = 100, completionTokens = 20, totalTokens = 120),
            stepCount = 3,
        )

        val decoded = Json.decodeFromString<UIMessage>(Json.encodeToString(message))
        assertEquals(120, decoded.contextTokens())
        assertEquals(3, decoded.stepCount)
    }
}
