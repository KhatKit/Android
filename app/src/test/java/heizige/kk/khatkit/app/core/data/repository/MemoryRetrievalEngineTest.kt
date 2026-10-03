package heizige.kk.khatkit.app.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRetrievalEngineTest {

    @Test
    fun `rrf fuse combines ranked lists with reciprocal scores`() {
        // list1: [1, 2, 3], list2: [2, 4]
        val fused = MemoryRetrievalEngine.rrfFuse(listOf(listOf(1, 2, 3), listOf(2, 4)))
        // id=2 appears in both lists → highest score
        assertEquals(2, fused.first().chunkId)
        assertTrue(fused.first().rrfScore > fused[1].rrfScore)
    }

    @Test
    fun `rrf fuse marks signals`() {
        val fused = MemoryRetrievalEngine.rrfFuse(listOf(listOf(1), listOf(1), listOf(), listOf(2)))
        val hit1 = fused.first { it.chunkId == 1 }
        assertTrue("fts" in hit1.signals)
        assertTrue("vector" in hit1.signals)
        val hit2 = fused.first { it.chunkId == 2 }
        assertTrue("recent" in hit2.signals)
    }

    @Test
    fun `rrf fuse handles empty lists`() {
        val fused = MemoryRetrievalEngine.rrfFuse(listOf(emptyList(), emptyList()))
        assertTrue(fused.isEmpty())
    }

    @Test
    fun `rrf fuse deduplicates same chunk across lists`() {
        val fused = MemoryRetrievalEngine.rrfFuse(listOf(listOf(5, 6), listOf(6, 5)))
        assertEquals(2, fused.size)
    }

    @Test
    fun `recency factor halves at half-life`() {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val fresh = MemoryRetrievalEngine.recencyFactor(now, now)
        assertEquals(1.0, fresh, 0.001)

        val thirtyDaysAgo = now - 30 * dayMs
        val half = MemoryRetrievalEngine.recencyFactor(thirtyDaysAgo, now)
        assertEquals(0.5, half, 0.01)
    }

    @Test
    fun `recency factor is zero-ish for very old items`() {
        val now = System.currentTimeMillis()
        val yearAgo = now - 365 * 86_400_000L
        val f = MemoryRetrievalEngine.recencyFactor(yearAgo, now)
        assertTrue(f < 0.01)
    }

    @Test
    fun `fts query sanitizes special characters`() {
        val q = MemoryRetrievalEngine.toFtsQuery("hello \"world\" NEAR(test)")
        // tokens extracted as words, joined with OR
        assertTrue(q.contains("\"hello\""))
        assertTrue(q.contains("\"world\""))
        assertTrue(q.contains(" OR "))
    }

    @Test
    fun `fts query returns empty for blank input`() {
        assertEquals("", MemoryRetrievalEngine.toFtsQuery("   "))
        assertEquals("", MemoryRetrievalEngine.toFtsQuery("\"\""))
    }

    @Test
    fun `fts query handles chinese unicode words`() {
        val q = MemoryRetrievalEngine.toFtsQuery("用户偏好")
        assertTrue(q.contains("用户偏好"))
    }
}
