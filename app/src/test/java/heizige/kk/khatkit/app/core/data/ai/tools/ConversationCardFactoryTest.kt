package heizige.kk.khatkit.app.core.data.ai.tools

import heizige.kk.khatkit.card.CardParser
import heizige.kk.khatkit.card.CardValidator
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCardFactoryTest {

    private fun message(role: MessageRole, text: String) =
        UIMessage(role = role, parts = listOf(UIMessagePart.Text(text)))

    @Test
    fun generatedCardPassesValidator() {
        val card = ConversationCardFactory.create(
            title = "测试对话",
            messages = listOf(
                message(MessageRole.USER, "你好"),
                message(MessageRole.ASSISTANT, "在的"),
            ),
        )

        assertTrue(card.name.startsWith("chat_"))
        val manifest = CardParser.parse(card.manifestJson).getOrThrow()
        assertTrue(
            "生成的卡片应通过校验",
            CardValidator.isValid(manifest, mapOf("main.js" to card.scriptText)),
        )
        assertEquals("text", manifest.tags?.domain)
        assertEquals("read", manifest.tags?.action)
        assertTrue(manifest.requires.bridges.contains("ui"))
    }

    @Test
    fun scriptEscapesQuotesAndNewlines() {
        val card = ConversationCardFactory.create(
            title = "quote",
            messages = listOf(message(MessageRole.USER, "he said \"hi\"\nline2")),
        )
        assertTrue(card.scriptText.contains("ui.show"))
        assertTrue(card.scriptText.contains("\\\"hi\\\""))
        assertTrue(card.scriptText.contains("\\n"))
    }

    @Test
    fun transcriptKeepsRoles() {
        val markdown = ConversationCardFactory.buildTranscript(
            title = "t",
            messages = listOf(
                message(MessageRole.USER, "u"),
                message(MessageRole.ASSISTANT, "a"),
            ),
        )
        assertTrue(markdown.contains("**user**"))
        assertTrue(markdown.contains("**assistant**"))
        assertTrue(markdown.contains("# t"))
    }
}
