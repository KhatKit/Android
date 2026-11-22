package heizige.kk.khatkit.app.core.data.db.fts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTextQueryTest {
    @Test fun `multi text matches each part independently with literal punctuation`() {
        val query = requireNotNull(MessageTextQuery.compile("版本 12，BETA,[OK]", MessageSearchMode.ANY_TEXT))
        assertEquals(2 until 7, query.find("目前版本 12"))
        assertEquals(0 until 4, query.find("beta only"))
        assertEquals(7 until 11, query.find("status [ok]"))
        assertNull(query.find("版本 13"))
        assertNull(MessageTextQuery.compile("， , ", MessageSearchMode.ANY_TEXT))
    }

    @Test fun `regex honors syntax and rejects invalid expressions`() {
        val query = requireNotNull(MessageTextQuery.compile("订单[0-9]{2,4}", MessageSearchMode.REGEX))
        assertEquals(1 until 6, query.find("新订单123"))
        assertNull(query.find("订单A"))
        assertThrows(IllegalArgumentException::class.java) { MessageTextQuery.compile("([", MessageSearchMode.REGEX) }
        assertThrows(IllegalArgumentException::class.java) { MessageTextQuery.compile("(.)\\1", MessageSearchMode.REGEX) }
    }

    @Test fun `snippet keeps actual brackets and bounds long matches`() {
        val text = "a".repeat(100) + "[ok]" + "z".repeat(1000)
        val snippet = messageTextSnippet(text, 100 until text.length)
        assertTrue(snippet.length <= 142)
        assertTrue(snippet.startsWith('…') && snippet.endsWith('…'))
        assertTrue("[ok]" in snippet)
        assertEquals("abc", messageTextSnippet("abc", 3 until 3))
    }
}
