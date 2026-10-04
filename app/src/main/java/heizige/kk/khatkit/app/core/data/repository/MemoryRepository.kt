package heizige.kk.khatkit.app.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.sqlite.db.SupportSQLiteDatabase
import heizige.kk.khatkit.app.core.data.db.MemoryVectorIndex
import heizige.kk.khatkit.app.core.data.db.dao.MemoryChunkDAO
import heizige.kk.khatkit.app.core.data.db.dao.MemoryGraphDAO
import heizige.kk.khatkit.app.core.data.db.dao.MemorySpaceDAO
import heizige.kk.khatkit.app.core.data.db.entity.MemoryChunkEntity
import heizige.kk.khatkit.app.core.data.db.entity.MemoryEdgeEntity
import heizige.kk.khatkit.app.core.data.db.entity.MemoryMentionEntity
import heizige.kk.khatkit.app.core.data.db.entity.MemorySpaceEntity
import heizige.kk.khatkit.app.core.data.model.AssistantMemory

/**
 * A2 记忆系统：空间 / 分块 / 图谱 / 提及 + 混合检索。
 *
 * 检索 = FTS5 + 向量（sqlite-vector 或 Kotlin 暴力余弦）+ 图谱扩展 + 时间衰减，RRF 融合。
 * 每条结果带 `sourceMessageId / confidence / extractedAt`，可溯源。
 */
class MemoryRepository(
    private val spaceDao: MemorySpaceDAO,
    private val chunkDao: MemoryChunkDAO,
    private val graphDao: MemoryGraphDAO,
    private val database: SupportSQLiteDatabase? = null,
) {
    companion object {
        const val GLOBAL_MEMORY_ID = MemorySpaceEntity.GLOBAL_SPACE_ID
    }

    // ==================== 空间 ====================

    suspend fun ensureSpace(spaceId: String, name: String = ""): MemorySpaceEntity {
        val existing = spaceDao.getSpaceById(spaceId)
        if (existing != null) return existing
        val space = MemorySpaceEntity(
            id = spaceId,
            kind = MemorySpaceEntity.kindOf(spaceId),
            name = name,
            createdAt = System.currentTimeMillis(),
        )
        spaceDao.insertSpace(space)
        return space
    }

    fun getAllSpacesFlow(): Flow<List<MemorySpaceEntity>> = spaceDao.getAllSpacesFlow()

    suspend fun getSpace(spaceId: String): MemorySpaceEntity? = spaceDao.getSpaceById(spaceId)

    // ==================== 记忆（分块）CRUD ====================

    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<AssistantMemory>> =
        chunkDao.getChunksOfSpaceFlow(assistantId).map { list -> list.map { it.toModel() } }

    suspend fun getMemoriesOfAssistant(assistantId: String): List<AssistantMemory> {
        ensureSpace(assistantId)
        return chunkDao.getChunksOfSpace(assistantId).map { it.toModel() }
    }

    fun getGlobalMemoriesFlow(): Flow<List<AssistantMemory>> =
        getMemoriesOfAssistantFlow(GLOBAL_MEMORY_ID)

    suspend fun getGlobalMemories(): List<AssistantMemory> =
        getMemoriesOfAssistant(GLOBAL_MEMORY_ID)

    suspend fun addMemory(
        assistantId: String,
        content: String,
        sourceKind: String = MemoryChunkEntity.SOURCE_MANUAL,
        sourceMessageId: String? = null,
        sourceRefId: String? = null,
        confidence: Float = 1f,
        embedding: FloatArray? = null,
        // C1-D：群聊记忆写入时带上发言角色 id（契约「群消息写入记忆时带 source_message_id 与
        // role_id」）。默认 null ⇒ 现有调用点（单聊 / 助手 / 全局空间）一行不用改，行为不变。
        roleId: String? = null,
    ): AssistantMemory {
        ensureSpace(assistantId)
        val now = System.currentTimeMillis()
        val id = chunkDao.insertChunk(
            MemoryChunkEntity(
                spaceId = assistantId,
                content = content,
                sourceKind = sourceKind,
                sourceMessageId = sourceMessageId,
                sourceRefId = sourceRefId,
                confidence = confidence,
                extractedAt = now,
                lastHitAt = 0L,
                createdAt = now,
                updatedAt = now,
                embedding = embedding?.let { MemoryVectorIndex.floatArrayToBlob(it) },
                roleId = roleId,
            )
        ).toInt()
        return chunkDao.getChunkById(id)!!.toModel()
    }

    suspend fun updateContent(id: Int, content: String): AssistantMemory {
        val old = chunkDao.getChunkById(id) ?: error("Memory record #$id not found")
        val updated = old.copy(content = content, updatedAt = System.currentTimeMillis())
        chunkDao.updateChunk(updated)
        return updated.toModel()
    }

    /** 遗忘 = 软删 + 使以该分块为证据的图谱边失效（保留溯源）。 */
    suspend fun forgetMemory(id: Int) {
        val now = System.currentTimeMillis()
        chunkDao.softDelete(id, now)
        graphDao.invalidateEdgesOfChunk(id, now)
    }

    suspend fun deleteMemory(id: Int) {
        graphDao.deleteMentionsOfChunk(id)
        graphDao.invalidateEdgesOfChunk(id, System.currentTimeMillis())
        chunkDao.hardDelete(id)
    }

    suspend fun deleteMemoriesOfAssistant(assistantId: String) {
        val now = System.currentTimeMillis()
        chunkDao.softDeleteSpace(assistantId, now)
    }

    suspend fun copyMemories(fromAssistantId: String, toAssistantId: String) {
        val memories = chunkDao.getChunksOfSpace(fromAssistantId)
        if (memories.isEmpty()) return
        ensureSpace(toAssistantId)
        val now = System.currentTimeMillis()
        chunkDao.insertChunks(
            memories.map {
                it.copy(
                    id = 0,
                    spaceId = toAssistantId,
                    createdAt = now,
                    updatedAt = now,
                    lastHitAt = 0L,
                )
            }
        )
    }

    // ==================== 图谱 ====================

    suspend fun linkMemories(
        sourceChunkId: Int,
        targetChunkId: Int,
        relType: String = MemoryEdgeEntity.REL_RELATED,
        confidence: Float = 1f,
    ): Int {
        val src = chunkDao.getChunkById(sourceChunkId) ?: error("chunk #$sourceChunkId not found")
        val dst = chunkDao.getChunkById(targetChunkId) ?: error("chunk #$targetChunkId not found")
        val now = System.currentTimeMillis()
        return graphDao.insertEdge(
            MemoryEdgeEntity(
                spaceId = src.spaceId,
                sourceName = "chunk:$sourceChunkId",
                relType = relType,
                targetName = "chunk:$targetChunkId",
                confidence = confidence,
                evidenceChunkId = sourceChunkId,
                createdAt = now,
            )
        ).toInt()
    }

    /** 以实体名连接两条记忆（供抽取器 / memory_link 工具写入三元组）。 */
    suspend fun addEdge(
        spaceId: String,
        sourceName: String,
        relType: String,
        targetName: String,
        confidence: Float = 1f,
        evidenceChunkId: Int? = null,
    ): Int {
        ensureSpace(spaceId)
        return graphDao.insertEdge(
            MemoryEdgeEntity(
                spaceId = spaceId,
                sourceName = sourceName,
                relType = relType,
                targetName = targetName,
                confidence = confidence,
                evidenceChunkId = evidenceChunkId,
                createdAt = System.currentTimeMillis(),
            )
        ).toInt()
    }

    suspend fun addMention(chunkId: Int, entityName: String, messageId: String? = null) {
        graphDao.insertMention(
            MemoryMentionEntity(
                chunkId = chunkId,
                entityName = entityName,
                messageId = messageId,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun getEdgesOfChunk(chunkId: Int): List<MemoryEdgeEntity> =
        graphDao.getEdgesOfChunk(chunkId)

    suspend fun getMentionsOfChunk(chunkId: Int): List<MemoryMentionEntity> =
        graphDao.getMentionsOfChunk(chunkId)

    // ==================== 检索 ====================

    /**
     * 混合检索：FTS5 + 向量 + 图谱扩展 + 时间衰减，RRF 融合。
     *
     * @param spaceId 检索空间；传 null 表示不限空间（全局检索）
     * @return 排序后的记忆，带来源与置信度
     */
    suspend fun searchMemories(
        assistantId: String,
        query: String,
        limit: Int = MemoryRetrievalEngine.DEFAULT_LIMIT,
    ): List<AssistantMemory> = searchHybrid(spaceId = assistantId, query = query, limit = limit)

    suspend fun searchHybrid(
        spaceId: String?,
        query: String,
        limit: Int = MemoryRetrievalEngine.DEFAULT_LIMIT,
    ): List<AssistantMemory> = searchHybridWithEmbedding(spaceId, query, queryEmbedding = null, limit = limit)

    private suspend fun searchFtsIds(query: String, spaceId: String?, limit: Int): List<Int> {
        val db = database ?: return emptyList()
        val ftsQuery = MemoryRetrievalEngine.toFtsQuery(query)
        if (ftsQuery.isEmpty()) return emptyList()
        return try {
            val results = mutableListOf<Int>()
            val sql = if (spaceId != null) {
                """
                SELECT c.id FROM memory_chunk_fts
                JOIN memory_chunks c ON c.id = memory_chunk_fts.rowid
                WHERE memory_chunk_fts MATCH ?
                  AND c.space_id = ? AND c.deleted_at IS NULL
                ORDER BY bm25(memory_chunk_fts)
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT c.id FROM memory_chunk_fts
                JOIN memory_chunks c ON c.id = memory_chunk_fts.rowid
                WHERE memory_chunk_fts MATCH ?
                  AND c.deleted_at IS NULL
                ORDER BY bm25(memory_chunk_fts)
                LIMIT ?
                """.trimIndent()
            }
            val args = if (spaceId != null) arrayOf(ftsQuery, spaceId, limit.toString())
            else arrayOf(ftsQuery, limit.toString())
            db.query(sql, args).use { c ->
                while (c.moveToNext()) results += c.getInt(0)
            }
            results
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun searchGraphIds(seedIds: List<Int>, spaceId: String?, limit: Int): List<Int> {
        if (seedIds.isEmpty()) return emptyList()
        return try {
            val entityNames = mutableSetOf<String>()
            for (id in seedIds.take(5)) {
                graphDao.getMentionsOfChunk(id).forEach { entityNames += it.entityName }
                graphDao.getEdgesOfChunk(id).forEach {
                    entityNames += it.sourceName
                    entityNames += it.targetName
                }
            }
            if (entityNames.isEmpty()) return emptyList()
            val names = entityNames.take(20).toList()
            val expanded = mutableSetOf<Int>()
            // 共享实体名的其他分块
            for (name in names) {
                graphDao.getMentionsOfEntity(name).forEach { expanded += it.chunkId }
            }
            // 图谱边证据分块
            if (spaceId != null) {
                graphDao.getEdgesOfEntities(spaceId, names).forEach { edge ->
                    edge.evidenceChunkId?.let { expanded += it }
                }
            }
            (expanded - seedIds.toSet()).take(limit).toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * 带外部 query 向量的混合检索（由 ChatManager / 工具层传入 embedding）。
     * 向量信号用暴力余弦 / sqlite-vector 二选一。
     */
    suspend fun searchHybridWithEmbedding(
        spaceId: String?,
        query: String,
        queryEmbedding: FloatArray?,
        limit: Int = MemoryRetrievalEngine.DEFAULT_LIMIT,
    ): List<AssistantMemory> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val fetchK = (limit * MemoryRetrievalEngine.FETCH_MULTIPLIER).coerceIn(10, 60)
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank() || limit <= 0) return@withContext emptyList()

        val ftsIds = searchFtsIds(normalizedQuery, spaceId, fetchK)
        val vectorIds = searchVectorIdsInternal(queryEmbedding, spaceId, fetchK)
        val graphIds = searchGraphIds(ftsIds, spaceId, fetchK)
        val candidateIds = (ftsIds + vectorIds + graphIds).toSet()
        val candidates = if (candidateIds.isEmpty()) emptyList()
        else chunkDao.getChunksByIds(candidateIds.toList()).filter { it.deletedAt == null }
        val recentIds = MemoryRetrievalEngine.rankByRecency(candidates, now)

        val fused = MemoryRetrievalEngine.rrfFuse(listOf(ftsIds, vectorIds, graphIds, recentIds))
        val orderedIds = fused.take(limit).map { it.chunkId }
        orderedIds.forEach { chunkDao.touchHit(it, now) }

        val hits = chunkDao.getChunksByIds(orderedIds)
            .filter { it.deletedAt == null }
            .associateBy { it.id }
        orderedIds.mapNotNull { hits[it]?.toModel() }
    }

    private suspend fun searchVectorIdsInternal(
        queryEmbedding: FloatArray?,
        spaceId: String?,
        limit: Int,
    ): List<Int> {
        if (queryEmbedding == null || queryEmbedding.isEmpty()) return emptyList()
        // sqlite-vector 快速路径
        if (MemoryVectorIndex.isAvailable()) {
            val db = database
            if (db != null) {
                try {
                    val blob = MemoryVectorIndex.floatArrayToBlob(queryEmbedding)
                    val results = mutableListOf<Pair<Int, Double>>()
                    val sql = """
                        SELECT c.id, v.distance FROM memory_chunks c
                        JOIN vector_full_scan('memory_chunks', 'embedding', ?, ${limit * 3}) AS v
                        ON c.id = v.rowid
                        WHERE c.deleted_at IS NULL ${if (spaceId != null) "AND c.space_id = ?" else ""}
                        ORDER BY v.distance
                        LIMIT ?
                    """.trimIndent()
                    val args = if (spaceId != null) arrayOf(blob, spaceId, limit.toString())
                    else arrayOf(blob, limit.toString())
                    db.query(sql, args).use { c ->
                        while (c.moveToNext()) results += c.getInt(0) to c.getDouble(1)
                    }
                    return results.sortedBy { it.second }.map { it.first }
                } catch (_: Throwable) {
                    // fall through to brute force
                }
            }
        }
        // Kotlin 暴力余弦
        return bruteForceCosine(queryEmbedding, spaceId, limit)
    }

    private suspend fun bruteForceCosine(
        queryEmbedding: FloatArray,
        spaceId: String?,
        limit: Int,
    ): List<Int> {
        val rows = chunkDao.getChunksWithEmbedding().filter {
            it.deletedAt == null && (spaceId == null || it.spaceId == spaceId)
        }
        if (rows.isEmpty()) return emptyList()
        val qNorm = norm(queryEmbedding)
        if (qNorm == 0f) return emptyList()
        return rows.map { row ->
            val v = MemoryVectorIndex.blobToFloatArray(row.embedding!!)
            val score = cosineSimilarity(queryEmbedding, qNorm, v)
            row.id to score
        }.sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun norm(v: FloatArray): Float {
        var s = 0f
        for (x in v) s += x * x
        return kotlin.math.sqrt(s)
    }

    private fun cosineSimilarity(a: FloatArray, aNorm: Float, b: FloatArray): Float {
        var dot = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) dot += a[i] * b[i]
        val bNorm = norm(b)
        if (bNorm == 0f) return 0f
        return dot / (aNorm * bNorm)
    }

    /** 取回单条记忆（含溯源字段）。 */
    suspend fun getMemory(id: Int): AssistantMemory? =
        chunkDao.getChunkById(id)?.takeIf { it.deletedAt == null }?.toModel()

    /** 统计：空间规模（用于评测与 UI）。 */
    suspend fun countMemories(spaceId: String): Int = chunkDao.countChunksOfSpace(spaceId)

    suspend fun countAllMemories(): Int = chunkDao.countAllChunks()

    // ==================== 兼容桥接（旧接口调用点） ====================

    suspend fun searchMemoriesGlobal(query: String, limit: Int = 8): List<AssistantMemory> =
        searchHybrid(spaceId = GLOBAL_MEMORY_ID, query = query, limit = limit)
}

internal fun MemoryChunkEntity.toModel(): AssistantMemory = AssistantMemory(
    id = id,
    content = content,
    spaceId = spaceId,
    sourceMessageId = sourceMessageId,
    sourceKind = sourceKind,
    confidence = confidence,
    extractedAt = extractedAt,
    // C1-D：群聊记忆的发言角色 id 必须带出来，否则检索结果无法再做 viewer 过滤。
    // 存量单聊/助手记忆为 null，与库里的 NULL 一致。
    roleId = roleId,
)
