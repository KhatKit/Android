package heizige.kk.khatkit.app.feature.tasks.data

import heizige.kk.khatkit.app.core.data.db.entity.CardRunEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRunInputsTest {
    private val run = CardRunEntity(
        id = "run",
        cardName = "fixture",
        cardVersion = "1.0.0",
        trigger = CardRunEntity.USER_TRIGGER,
        status = CardRunEntity.SUCCEEDED,
        startedAt = 0,
        updatedAt = 0,
        argsTemplate = """{"width":640,"format":"png","options":{"mode":"crop","token":"secret"}}""",
    )

    @Test
    fun allowsOnlyTerminalManualRuns() {
        assertEquals(640L, run.savedInputs()["width"])
        assertTrue(run.copy(trigger = "AI").savedInputs().isEmpty())
        assertTrue(run.copy(trigger = "schedule").savedInputs().isEmpty())
        assertTrue(run.copy(status = CardRunEntity.RUNNING).savedInputs().isEmpty())
    }

    @Test
    fun filtersOlderTemplatesAgainBeforeDisplayOrReuse() {
        assertEquals(
            mapOf("width" to 640L, "format" to "png", "options" to mapOf("mode" to "crop")),
            run.savedInputs(),
        )
    }

    @Test
    fun ignoresInvalidOrOversizedTemplates() {
        listOf("", "{}", "{invalid", "[123]", "x".repeat(4097)).forEach {
            assertTrue(run.copy(argsTemplate = it).savedInputs().isEmpty())
        }
    }
}
