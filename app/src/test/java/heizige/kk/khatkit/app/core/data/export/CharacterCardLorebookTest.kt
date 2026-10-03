package heizige.kk.khatkit.app.core.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CharacterCardLorebookTest {
    @Test
    fun `maps CCv3 character book into lorebook entries`() {
        val lorebook = parseCharacterCardLorebook(
            """
            {
              "spec": "chara_card_v3",
              "data": {
                "name": "Ada",
                "character_book": {
                  "name": "World",
                  "entries": [{
                    "keys": ["castle"],
                    "content": "The castle is ancient.",
                    "constant": true,
                    "insertion_order": 7
                  }]
                }
              }
            }
            """.trimIndent(),
        )

        assertNotNull(lorebook)
        assertEquals("World", lorebook?.name)
        assertEquals(1, lorebook?.entries?.size)
        assertEquals(listOf("castle"), lorebook?.entries?.single()?.keywords)
        assertEquals(7, lorebook?.entries?.single()?.priority)
        assertEquals(true, lorebook?.entries?.single()?.constantActive)
    }
}
