package heizige.kk.khatkit.bridge.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhereParserTest {

    private val row = mapOf("id" to 3L, "name" to "alpha", "score" to 88.5)

    @Test
    fun emptyWhereMatchesAll() {
        assertTrue(WhereParser.compile("", emptyList())(row))
    }

    @Test
    fun equalityOnNumbers() {
        assertTrue(WhereParser.compile("id = ?", listOf(3))(row))
        assertFalse(WhereParser.compile("id = ?", listOf(4))(row))
    }

    @Test
    fun comparison() {
        assertTrue(WhereParser.compile("score >= ?", listOf(80))(row))
        assertFalse(WhereParser.compile("score < ?", listOf(80))(row))
    }

    @Test
    fun andClauses() {
        assertTrue(WhereParser.compile("name = ? AND score > ?", listOf("alpha", 10))(row))
        assertFalse(WhereParser.compile("name = ? AND score > ?", listOf("beta", 10))(row))
    }

    @Test
    fun like() {
        assertTrue(WhereParser.compile("name like ?", listOf("ph"))(row))
        assertFalse(WhereParser.compile("name like ?", listOf("zz"))(row))
    }
}
