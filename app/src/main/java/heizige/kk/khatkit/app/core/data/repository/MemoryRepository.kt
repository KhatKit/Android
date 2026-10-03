package heizige.kk.khatkit.app.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.sqlite.db.SupportSQLiteDatabase
import heizige.kk.khatkit.app.core.data.db.dao.MemoryDAO
import heizige.kk.khatkit.app.core.data.db.entity.MemoryEntity
import heizige.kk.khatkit.app.core.data.model.AssistantMemory

class MemoryRepository(
    private val memoryDAO: MemoryDAO,
    private val database: SupportSQLiteDatabase? = null,
) {
    companion object {
        const val GLOBAL_MEMORY_ID = "__global__"
    }

    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(assistantId)
            .map { entities ->
                entities.map { AssistantMemory(it.id, it.content) }
            }

    suspend fun getMemoriesOfAssistant(assistantId: String): List<AssistantMemory> {
        return memoryDAO.getMemoriesOfAssistant(assistantId)
            .map { AssistantMemory(it.id, it.content) }
    }

    fun getGlobalMemoriesFlow(): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(GLOBAL_MEMORY_ID)
            .map { entities ->
                entities.map { AssistantMemory(it.id, it.content) }
            }

    suspend fun searchMemories(assistantId: String, query: String, limit: Int = 8): List<AssistantMemory> =
        withContext(Dispatchers.IO) {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isBlank() || limit <= 0) return@withContext emptyList()

            val indexed = database?.let { db ->
                val results = mutableListOf<AssistantMemory>()
                val cursor = runCatching {
                    db.query(
                        """
                        SELECT memory_id, content
                        FROM memory_fts
                        WHERE memory_fts MATCH ?
                          AND assistant_id = ?
                        ORDER BY rank
                        LIMIT ?
                        """.trimIndent(),
                        arrayOf(toFtsQuery(normalizedQuery), assistantId, limit.coerceIn(1, 50).toString()),
                    )
                }.getOrNull() ?: return@let null
                cursor.use {
                    while (it.moveToNext()) {
                        results += AssistantMemory(
                            id = it.getString(0).toIntOrNull() ?: continue,
                            content = it.getString(1),
                        )
                    }
                }
                results
            }
            if (indexed != null) return@withContext indexed

            getMemoriesOfAssistant(assistantId)
                .filter { it.content.contains(normalizedQuery, ignoreCase = true) }
                .take(limit)
        }

    suspend fun getGlobalMemories(): List<AssistantMemory> {
        return memoryDAO.getMemoriesOfAssistant(GLOBAL_MEMORY_ID)
            .map { AssistantMemory(it.id, it.content) }
    }

    suspend fun copyMemories(fromAssistantId: String, toAssistantId: String) {
        val memories = memoryDAO.getMemoriesOfAssistant(fromAssistantId)
        if (memories.isEmpty()) return
        memoryDAO.insertMemories(
            memories.map { MemoryEntity(assistantId = toAssistantId, content = it.content) },
        )
    }

    suspend fun deleteMemoriesOfAssistant(assistantId: String) {
        memoryDAO.deleteMemoriesOfAssistant(assistantId)
    }

    suspend fun updateContent(id: Int, content: String): AssistantMemory {
        val old = memoryDAO.getMemoryById(id) ?: error("Memory record #$id not found")
        val newMemory = old.copy(
            content = content
        )
        memoryDAO.updateMemory(newMemory)
        updateMemoryFts(newMemory)
        return AssistantMemory(
            id = newMemory.id,
            content = newMemory.content,
        )
    }

    suspend fun addMemory(assistantId: String, content: String): AssistantMemory {
        val memory = AssistantMemory(
            id = 0,
            content = content,
        )
        val id = memoryDAO.insertMemory(
            MemoryEntity(
                assistantId = assistantId,
                content = memory.content
            )
        )
        val newMemory = memory.copy(id = id.toInt())
        updateMemoryFts(
            MemoryEntity(
                id = newMemory.id,
                assistantId = assistantId,
                content = newMemory.content,
            )
        )
        return newMemory
    }

    suspend fun deleteMemory(id: Int) {
        database?.execSQL("DELETE FROM memory_fts WHERE memory_id = ?", arrayOf(id.toString()))
        memoryDAO.deleteMemory(id)
    }

    private fun updateMemoryFts(memory: MemoryEntity) {
        database?.execSQL("DELETE FROM memory_fts WHERE memory_id = ?", arrayOf(memory.id.toString()))
        database?.execSQL(
            "INSERT INTO memory_fts(content, assistant_id, memory_id) VALUES (?, ?, ?)",
            arrayOf(memory.content, memory.assistantId, memory.id.toString()),
        )
    }

    private fun toFtsQuery(query: String): String =
        query.split(Regex("\\s+"))
            .map { it.trim().replace("\"", "\"\"") }
            .filter { it.isNotBlank() }
            .joinToString(" AND ") { "\"$it\"" }
}
