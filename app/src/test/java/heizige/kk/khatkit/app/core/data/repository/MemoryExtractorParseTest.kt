package heizige.kk.khatkit.app.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryExtractorParseTest {

    @Test
    fun `parseFacts extracts valid json array`() {
        val response = """[{"content":"User likes Kotlin","entity":"User","relation":"RELATED","related_entity":"Kotlin","confidence":0.9}]"""
        val facts = MemoryExtractor.parseFacts(response)
        assertEquals(1, facts.size)
        assertEquals("User likes Kotlin", facts[0].content)
        assertEquals("User", facts[0].entity)
        assertEquals("Kotlin", facts[0].relatedEntity)
        assertEquals(0.9f, facts[0].confidence)
    }

    @Test
    fun `parseFacts handles wrapped response text`() {
        val response = "Here are the facts:\n[{\"content\":\"fact one\"}]\nDone."
        val facts = MemoryExtractor.parseFacts(response)
        assertEquals(1, facts.size)
        assertEquals("fact one", facts[0].content)
    }

    @Test
    fun `parseFacts returns empty for invalid json`() {
        assertTrue(MemoryExtractor.parseFacts("not json at all").isEmpty())
        assertTrue(MemoryExtractor.parseFacts("").isEmpty())
    }

    @Test
    fun `parseFacts defaults missing fields`() {
        val response = """[{"content":"some fact"}]"""
        val facts = MemoryExtractor.parseFacts(response)
        assertEquals(1, facts.size)
        assertNull(facts[0].entity)
        assertEquals(0.8f, facts[0].confidence)
    }

    @Test
    fun `parseFacts skips entries without content`() {
        val response = """[{"entity":"X"},{"content":"valid"}]"""
        val facts = MemoryExtractor.parseFacts(response)
        assertEquals(1, facts.size)
        assertEquals("valid", facts[0].content)
    }

    @Test
    fun `parseFacts handles empty array`() {
        assertTrue(MemoryExtractor.parseFacts("[]").isEmpty())
    }

    @Test
    fun `parseFacts handles chinese content`() {
        val response = """[{"content":"用户偏好中文回复","entity":"用户","relation":"RELATED","related_entity":"中文","confidence":0.85}]"""
        val facts = MemoryExtractor.parseFacts(response)
        assertEquals(1, facts.size)
        assertEquals("用户偏好中文回复", facts[0].content)
    }
}
