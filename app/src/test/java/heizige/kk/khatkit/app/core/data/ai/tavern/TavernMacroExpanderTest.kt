package heizige.kk.khatkit.app.core.data.ai.tavern

import org.junit.Assert.assertEquals
import org.junit.Test

class TavernMacroExpanderTest {
    @Test
    fun `expands supported identity and random macros`() {
        assertEquals(
            "Hi Ada, I am KhatKit. Lucky: seven.",
            expandTavernMacros(
                "Hi {{user}}, I am {{char}}. Lucky: {{random}}.",
                userName = "Ada",
                characterName = "KhatKit",
                randomValue = { "seven" },
            ),
        )
    }

    @Test
    fun `uses injected dice roller for stable results`() {
        assertEquals(
            "d20=17 d6=9 default=42",
            expandTavernMacros("d20={{roll:1d20}} d6={{roll 2d6}} default={{roll}}", "u", "c", roll = { count, sides ->
                when (count to sides) {
                    1 to 20 -> 17
                    2 to 6 -> 9
                    else -> 42
                }
            }),
        )
    }

    @Test
    fun `keeps unknown and malformed macros unchanged`() {
        val source = "{{foo}} {{roll:bad}} {{user"
        assertEquals(source, expandTavernMacros(source, "Ada", "KhatKit"))
    }
}