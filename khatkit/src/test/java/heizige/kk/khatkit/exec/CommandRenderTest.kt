package heizige.kk.khatkit.exec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommandRenderTest {

    @Test
    fun rendersPlaceholders() {
        assertEquals(
            "pm disable com.example.bloat",
            CardExecutor.renderCommand("pm disable {pkg}", mapOf("pkg" to "com.example.bloat")),
        )
    }

    @Test
    fun rejectsShellInjection() {
        assertThrows(IllegalArgumentException::class.java) {
            CardExecutor.renderCommand("pm disable {pkg}", mapOf("pkg" to "com.x; rm -rf /"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CardExecutor.renderCommand("echo {v}", mapOf("v" to "\$(id)"))
        }
    }

    @Test
    fun rejectsMissingArg() {
        assertThrows(IllegalArgumentException::class.java) {
            CardExecutor.renderCommand("pm disable {pkg}", emptyMap())
        }
    }
}
