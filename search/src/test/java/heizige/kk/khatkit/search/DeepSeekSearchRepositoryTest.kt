package heizige.kk.khatkit.search

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekSearchRepositoryTest {
    @Test
    fun `search request maps DeepSeek fields`() {
        val body = DeepSeekSearchRepository.buildSearchRequestBody(
            buildJsonObject {
                put("query", "latest android release")
                put("count", 20)
                put("ori_question", "What is the latest Android release?")
                put("localization", "127.0.0.1")
                put("need_content", true)
            },
            resultSize = 10,
        )

        assertEquals("latest android release", body["query"]!!.jsonPrimitive.content)
        assertEquals("20", body["count"]!!.jsonPrimitive.content)
        assertEquals("What is the latest Android release?", body["ori_question"]!!.jsonPrimitive.content)
        assertEquals("127.0.0.1", body["localization"]!!.jsonPrimitive.content)
        assertEquals("true", body["need_content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `search response maps summary and date`() {
        val response = SearchRepository.json.decodeFromString<DeepSeekSearchRepository.DeepSeekSearchResponse>(
            """
            {"results":[{"title":"Release","url":"https://example.com/release",
            "snippet":"short","summary":"long summary","content":"full",
            "date":"2026-10-17T00:00:00Z"}]}
            """.trimIndent()
        )
        val item = response.results.single()
        assertEquals("long summary", item.summary)
        assertEquals("2026-10-17T00:00:00Z", item.date)
    }

    @Test
    fun `fetch URL validation rejects credentials and non-http schemes`() {
        assertTrue(DeepSeekSearchRepository.isValidFetchUrl("https://example.com/article"))
        assertFalse(DeepSeekSearchRepository.isValidFetchUrl("https://user:pass@example.com/article"))
        assertFalse(DeepSeekSearchRepository.isValidFetchUrl("file:///etc/passwd"))
    }
}
