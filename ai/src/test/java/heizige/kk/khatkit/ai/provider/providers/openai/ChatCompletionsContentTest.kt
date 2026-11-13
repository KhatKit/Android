package heizige.kk.khatkit.ai.provider.providers.openai

import heizige.kk.khatkit.ai.util.json
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCompletionsContentTest {
    @Test
    fun `mistral thinking and text chunks are accumulated in order`() {
        val content = json.parseToJsonElement(
            """
            [
              {"type":"thinking","thinking":[{"type":"text","text":"先分析"},{"type":"text","text":"一下"}]},
              {"type":"text","text":"答案"}
            ]
            """.trimIndent()
        )

        val parsed = parseChatCompletionsContent(content)

        assertEquals("先分析一下", parsed.reasoning)
        assertEquals("答案", parsed.text)
    }
}
