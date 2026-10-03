package heizige.kk.khatkit.app.core.data.ai.tools

import heizige.kk.khatkit.app.core.data.model.AssistantMemory
import org.junit.Assert.assertEquals
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
}