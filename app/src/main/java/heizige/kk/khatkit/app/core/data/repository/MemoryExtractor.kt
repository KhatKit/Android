package heizige.kk.khatkit.app.core.data.repository

import android.util.Log
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.data.datastore.findProvider
import heizige.kk.khatkit.app.core.data.db.entity.MemoryChunkEntity
import heizige.kk.khatkit.app.core.data.db.entity.MemoryEdgeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

/**
 * A2 自动记忆抽取：对话 → LLM 提取事实三元组 → 写入分块 / 图谱边 / 提及。
 *
 * 参考 mem0 的 ADD-only 抽取：只新增不覆盖，重复由后续 memory_search + merge 处理。
 * 用便宜模型（ModelTaskType.MEMORY → fastModelId）。
 */
class MemoryExtractor(
    private val memoryRepository: MemoryRepository,
    private val providerManager: ProviderManager,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        private const val TAG = "MemoryExtractor"
        private const val MAX_EXTRACT_CHARS = 4_000

        fun parseFacts(response: String, json: Json = Json { ignoreUnknownKeys = true }): List<ExtractedFact> {
            val text = response.trim()
            val start = text.indexOf('[')
            val end = text.lastIndexOf(']')
            if (start < 0 || end <= start) return emptyList()
            val body = text.substring(start, end + 1)
            return try {
                val arr = json.parseToJsonElement(body).jsonArray
                arr.mapNotNull { el ->
                    val obj = el.jsonObject
                    val content = obj["content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    ExtractedFact(
                        content = content,
                        entity = obj["entity"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                        relation = obj["relation"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: MemoryEdgeEntity.REL_RELATED,
                        relatedEntity = obj["related_entity"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                        confidence = obj["confidence"]?.jsonPrimitive?.floatOrNull?.coerceIn(0f, 1f) ?: 0.8f,
                    )
                }
            } catch (e: Throwable) {
                Log.w(TAG, "parse extracted facts failed: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * 从一轮对话提取记忆。
     *
     * @param spaceId 记忆空间
     * @param messages 最近若干条消息（user + assistant）
     * @param settings 用于解析 fast model
     * @return 本次写入的记忆数
     */
    suspend fun extractFromTurn(
        spaceId: String,
        messages: List<UIMessage>,
        settings: Settings,
    ): Int = withContext(Dispatchers.IO) {
        val text = messages.joinToString("\n") { m ->
            "${m.role.name.lowercase()}: ${m.toText().take(500)}"
        }.takeLast(MAX_EXTRACT_CHARS)
        if (text.isBlank()) return@withContext 0

        val model = resolveFastModel(settings) ?: return@withContext 0
        val (provider, modelDef) = model
        return@withContext try {
            val result = providerManager.getProviderByType(provider).generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.system(prompt = EXTRACT_PROMPT),
                    UIMessage.user(prompt = text),
                ),
                params = TextGenerationParams(
                    model = modelDef,
                    temperature = 0.2f,
                    maxTokens = 512,
                ),
            )
            parseAndStore(spaceId, result.message.toText(), sourceMessageId = messages.lastOrNull()?.id?.toString())
        } catch (e: Throwable) {
            Log.w(TAG, "memory extraction failed: ${e.message}")
            0
        }
    }

    private fun resolveFastModel(settings: Settings): Pair<ProviderSetting, Model>? {
        val model = settings.findModelById(settings.fastModelId) ?: return null
        val provider = model.findProvider(settings.providers) ?: return null
        return provider to model
    }

    internal suspend fun parseAndStore(spaceId: String, response: String, sourceMessageId: String?): Int {
        val facts = parseFacts(response, json)
        if (facts.isEmpty()) return 0
        var count = 0
        for (fact in facts) {
            try {
                val chunk = memoryRepository.addMemory(
                    assistantId = spaceId,
                    content = fact.content,
                    sourceKind = MemoryChunkEntity.SOURCE_EXTRACTED,
                    sourceMessageId = sourceMessageId,
                    confidence = fact.confidence,
                )
                count++
                // 图谱边（实体关系）
                if (fact.entity != null && fact.relatedEntity != null) {
                    memoryRepository.addEdge(
                        spaceId = spaceId,
                        sourceName = fact.entity,
                        relType = fact.relation,
                        targetName = fact.relatedEntity,
                        confidence = fact.confidence,
                        evidenceChunkId = chunk.id,
                    )
                }
                // 提及（来源跳转）
                fact.entity?.let {
                    memoryRepository.addMention(chunk.id, it, sourceMessageId)
                }
                fact.relatedEntity?.let {
                    memoryRepository.addMention(chunk.id, it, sourceMessageId)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "store extracted fact failed: ${e.message}")
            }
        }
        return count
    }

    data class ExtractedFact(
        val content: String,
        val entity: String? = null,
        val relation: String = MemoryEdgeEntity.REL_RELATED,
        val relatedEntity: String? = null,
        val confidence: Float = 0.8f,
    )

    private val EXTRACT_PROMPT = """
        Extract durable facts from the conversation as a JSON array.
        Each fact: {"content":"self-contained sentence","entity":"subject or null","relation":"RELATED|CAUSES|PART_OF|HAPPENED_AT|MENTIONS","related_entity":"object or null","confidence":0..1}
        Rules: only durable facts (preferences, plans, personal info, events) — not greetings or transient chatter.
        Do not extract sensitive data (ethnicity, religion, sexual orientation, political views, sex life, criminal records).
        Output ONLY the JSON array, no prose. If nothing worth storing, output [].
    """.trimIndent()
}
