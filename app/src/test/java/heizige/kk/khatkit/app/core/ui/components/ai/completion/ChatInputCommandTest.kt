package heizige.kk.khatkit.app.core.ui.components.ai.completion

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInputCommandTest {
    private val compress = ChatInputCommand("compress", "Compress") {}
    private val skills = ChatInputCommand("skills", "Skills") {}
    private val commands = listOf(compress, skills)

    @Test
    fun suggestsCommandsAtTheEndOfStandalonePrefix() {
        assertEquals(commands, suggest("/"))
        assertEquals(listOf(compress), suggest("/Co"))
        assertEquals(listOf(skills), suggest("/skills"))
    }

    @Test
    fun doesNotSuggestForPathsProseOrSelections() {
        listOf("/sdcard", "/skills/example", "see /skills", " /skills", "/skills now", "/skills\n").forEach {
            assertTrue(it, suggest(it).isEmpty())
        }
        assertTrue(suggest("/skills", TextRange(2)).isEmpty())
        assertTrue(suggest("/skills", TextRange(2, 7)).isEmpty())
    }

    @Test
    fun submitRequiresExactStandaloneCommand() {
        assertEquals(compress, commands.findCommand("/COMPRESS "))
        assertEquals(skills, commands.findCommand("/skills"))
        listOf("/", "/ski", "hello /skills", "/skills file", "/skills\n", "/skills/path", "/unknown").forEach {
            assertNull(it, commands.findCommand(it))
        }
    }

    private fun suggest(text: String, selection: TextRange = TextRange(text.length)) =
        commands.suggestCommands(ChatCompletionContext(text, selection))
}
