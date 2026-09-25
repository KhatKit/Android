package heizige.kk.khatkit.app.core.data.model

import heizige.kk.khatkit.app.core.util.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarSerializationTest {

    @Test
    fun `decodes assistant avatar with legacy fully qualified dummy discriminator`() {
        val json = """
            [
              {
                "name": "legacy",
                "avatar": { "type": "heizige.kk.khatkit.app.data.model.Avatar.Dummy" }
              }
            ]
        """.trimIndent()

        val assistants = JsonInstant.decodeFromString<List<Assistant>>(json)

        assertEquals(1, assistants.size)
        assertEquals(Avatar.Dummy, assistants.first().avatar)
    }

    @Test
    fun `decodes legacy emoji and image discriminators`() {
        val json = """
            [
              { "avatar": { "type": "heizige.kk.khatkit.app.data.model.Avatar.Emoji", "content": "cat" } },
              { "avatar": { "type": "heizige.kk.khatkit.app.data.model.Avatar.Image", "url": "https://example.com/a.png" } }
            ]
        """.trimIndent()

        val assistants = JsonInstant.decodeFromString<List<Assistant>>(json)

        assertEquals(Avatar.Emoji("cat"), assistants[0].avatar)
        assertEquals(Avatar.Image("https://example.com/a.png"), assistants[1].avatar)
    }

    @Test
    fun `encodes legacy fully qualified discriminator so persisted data stays readable`() {
        val encoded = JsonInstant.encodeToString<Assistant>(Assistant(name = "legacy"))

        assertTrue(
            encoded.contains("\"type\":\"heizige.kk.khatkit.app.data.model.Avatar.Dummy\"")
        )
    }
}
