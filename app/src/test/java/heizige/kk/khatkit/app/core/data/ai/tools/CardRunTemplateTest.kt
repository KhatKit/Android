package heizige.kk.khatkit.app.core.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CardRunTemplateTest {
    @Test
    fun keepsBoundedSettingsButNotSecretsOrFreeformText() {
        val template = reusableCardArgs(
            mapOf(
                "width" to 640,
                "quality" to 0.85,
                "format" to "png",
                "text" to "private user text",
                "apiKey" to "top-secret",
                "access_token" to "bearer",
                "__khatkit_trigger" to "AI",
                "options" to mapOf("mode" to "crop", "sessionId" to 12345),
            ),
        )

        assertEquals(
            mapOf(
                "width" to 640,
                "quality" to 0.85,
                "format" to "png",
                "options" to mapOf("mode" to "crop"),
            ),
            template,
        )
    }

    @Test
    fun rejectsLinksPathsAndOversizedValues() {
        val template = reusableCardArgs(
            mapOf(
                "format" to "https://host.example/?token=secret",
                "mode" to "file:///tmp/output",
                "operation" to "a".repeat(101),
                "inputPath" to 42,
                "count" to 2,
            ),
        )

        assertEquals(mapOf("count" to 2), template)
        assertFalse(template.toString().contains("secret"))
    }

    @Test
    fun indexesOnlyBoundedTopLevelTextResults() {
        val artifacts = collectCardTextArtifacts(
            mapOf(
                "text" to "  Finished processing  ",
                "result" to "https://example.com/output",
                "output" to "/tmp/secret.txt",
                "summary" to "x".repeat(8193),
                "log" to "private transcript",
                "content" to mapOf("secret" to "hidden"),
            ),
        )
        assertEquals(listOf("text" to "Finished processing"), artifacts)
    }
}
