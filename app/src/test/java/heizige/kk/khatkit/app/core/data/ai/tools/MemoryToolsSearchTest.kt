package heizige.kk.khatkit.app.core.data.ai.tools

import heizige.kk.khatkit.app.core.data.model.AssistantMemory
import heizige.kk.khatkit.app.core.data.repository.MemoryRetrievalEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryToolsSearchTest {
    @Test
    fun `search ranks memories by matching terms`() {
        val memories = listOf(
            AssistantMemory(1, "User prefers concise answers"),
            AssistantMemory(2, "User is learning Kotlin and Android"),
            AssistantMemory(3, "Weekend plans include hiking"),
        )
        val result = memories
            .map { memory -> memory to listOf("user", "kotlin").count { memory.content.lowercase().contains(it) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
        assertEquals(listOf(2, 1), result.map { it.id })
    }

    @Test
    fun `memory model carries provenance fields`() {
        val m = AssistantMemory(
            id = 42,
            content = "fact",
            spaceId = "__global__",
            sourceMessageId = "msg-1",
            sourceKind = "EXTRACTED",
            confidence = 0.85f,
            extractedAt = 1000L,
        )
        assertEquals("msg-1", m.sourceMessageId)
        assertEquals(0.85f, m.confidence)
        assertEquals("__global__", m.spaceId)
    }

    @Test
    fun `rrf fusion is deterministic for identical input`() {
        val a = MemoryRetrievalEngine.rrfFuse(listOf(listOf(1, 2), listOf(2, 3)))
        val b = MemoryRetrievalEngine.rrfFuse(listOf(listOf(1, 2), listOf(2, 3)))
        assertEquals(a.map { it.chunkId }, b.map { it.chunkId })
        assertEquals(a.map { it.rrfScore }, b.map { it.rrfScore })
    }
}
