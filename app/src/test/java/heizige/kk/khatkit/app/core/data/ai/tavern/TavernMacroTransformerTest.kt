package heizige.kk.khatkit.app.core.data.ai.tavern

import heizige.kk.khatkit.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class TavernMacroTransformerTest {
    @Test
    fun `expands macros in text parts without changing message identity`() {
        val message = UIMessage.user("Hello {{user}}, meet {{char}}.")
        val result = expandTavernMacrosInMessages(
            messages = listOf(message),
            userName = "Ada",
            characterName = "KhatKit",
        )

        assertEquals("Hello Ada, meet KhatKit.", result.single().toText())
        assertEquals(message.id, result.single().id)
    }
}