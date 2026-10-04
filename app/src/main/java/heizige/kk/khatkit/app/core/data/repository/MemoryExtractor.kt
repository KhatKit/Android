package heizige.kk.khatkit.app.core.data.repository

import android.util.Log
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.ai.ModelTaskType
import heizige.kk.khatkit.app.core.data.ai.TaskRoutes
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
import kotlinx.serialization.json.intOrNull

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

        /** 抽取窗口最多取多少条消息（调用方一般已截断，这里是兜底上限）。 */
        const val MAX_EXTRACT_WINDOW = 6

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
                        // C1-M：模型自报的事实来源行（对应提示词里的 `[n]` 编号）。
                        // 报不出来就 null，由调用方决定兜底，绝不猜。
                        sourceLine = obj["source_line"]?.jsonPrimitive?.intOrNull,
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
     * @param spaceId 记忆空间键。群聊由调用方给 `group:<conversationId>:role:<roleId>`。
     * @param messages **本视角可见**的最近若干条消息（user + assistant）。群聊必须传
     *   viewer 过滤后的集合，否则会把同群其他角色的发言抽进本角色的记忆。
     * @param settings 用于解析 fast model
     * @param roleId C1 群聊：发言角色 id，透传进 `memory_chunks.role_id`，检索结果据此
     *   再做 viewer 过滤。默认 null ⇒ 单聊 / 助手路径一行不用改。
     * @return 本次写入的记忆数
     */
    suspend fun extractFromTurn(
        spaceId: String,
        messages: List<UIMessage>,
        settings: Settings,
        roleId: String? = null,
    ): Int = withContext(Dispatchers.IO) {
        val window = messages.takeLast(MAX_EXTRACT_WINDOW)
        val text = buildExtractWindow(window)
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
            parseAndStore(
                spaceId = spaceId,
                response = result.message.toText(),
                sourceMessageId = window.lastOrNull()?.id?.toString(),
                roleId = roleId,
                messageIdOfLine = { line -> window.getOrNull(line - 1)?.id?.toString() },
            )
        } catch (e: Throwable) {
            Log.w(TAG, "memory extraction failed: ${e.message}")
            0
        }
    }

    private fun resolveFastModel(settings: Settings): Pair<ProviderSetting, Model>? {
        val model = runCatching { TaskRoutes.resolve(settings, ModelTaskType.MEMORY) }.getOrNull() ?: return null
        val provider = model.findProvider(settings.providers) ?: return null
        return provider to model
    }

    internal suspend fun parseAndStore(
        spaceId: String,
        response: String,
        sourceMessageId: String?,
        roleId: String? = null,
        messageIdOfLine: (Int) -> String? = { null },
    ): Int {
        val facts = parseFacts(response, json)
        if (facts.isEmpty()) return 0
        var count = 0
        for (fact in facts) {
            try {
                val attribution = MemoryAttribution.forExtractedFact(
                    spaceId = spaceId,
                    roleId = roleId,
                    fact = fact,
                    fallbackMessageId = sourceMessageId,
                    messageIdOfLine = messageIdOfLine,
                )
                val chunk = memoryRepository.addMemory(
                    // `assistantId` 这个参数名是 A2 遗留：它承载的就是空间键
                    //（addMemory 内部 `spaceId = assistantId`）。C1 起空间键由群侧给定，
                    // 角色不再由它冒充，而是走下面的 roleId。
                    assistantId = attribution.spaceId,
                    content = fact.content,
                    sourceKind = MemoryChunkEntity.SOURCE_EXTRACTED,
                    sourceMessageId = attribution.sourceMessageId,
                    confidence = fact.confidence,
                    roleId = attribution.roleId,
                )
                count++
                // 图谱边（实体关系）
                if (fact.entity != null && fact.relatedEntity != null) {
                    memoryRepository.addEdge(
                        spaceId = attribution.spaceId,
                        sourceName = fact.entity,
                        relType = fact.relation,
                        targetName = fact.relatedEntity,
                        confidence = fact.confidence,
                        evidenceChunkId = chunk.id,
                    )
                }
                // 提及（来源跳转）
                fact.entity?.let {
                    memoryRepository.addMention(chunk.id, it, attribution.sourceMessageId)
                }
                fact.relatedEntity?.let {
                    memoryRepository.addMention(chunk.id, it, attribution.sourceMessageId)
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
        /** 模型自报的来源行号（1 起，对应 [buildExtractWindow] 的 `[n]`）；null = 未知。 */
        val sourceLine: Int? = null,
        val confidence: Float = 0.8f,
    )

    /**
     * 把消息窗口编成带 `[n]` 行号的文本，行号即模型要回填的 `source_line`。
     *
     * 超长时从**头部**截断（保留最近的消息 = 行号大的那些，编号仍然对得上原窗口）。
     */
    internal fun buildExtractWindow(window: List<UIMessage>): String = window
        .mapIndexed { index, message -> "[${index + 1}] ${message.role.name.lowercase()}: ${message.toText().take(500)}" }
        .joinToString("\n")
        .takeLast(MAX_EXTRACT_CHARS)

    private val EXTRACT_PROMPT = """
        Extract durable facts from the conversation as a JSON array.
        Each fact: {"content":"self-contained sentence","entity":"subject or null","relation":"RELATED|CAUSES|PART_OF|HAPPENED_AT|MENTIONS","related_entity":"object or null","source_line":<line number or null>,"confidence":0..1}
        The conversation lines are prefixed with [n]. Set "source_line" to the [n] of the message the fact comes from; use null when unsure — never guess.
        Rules: only durable facts (preferences, plans, personal info, events) — not greetings or transient chatter.
        Do not extract sensitive data (ethnicity, religion, sexual orientation, political views, sex life, criminal records).
        Output ONLY the JSON array, no prose. If nothing worth storing, output [].
    """.trimIndent()
}

/**
 * C1-M：抽取写入的归属判定（纯函数，可 JVM 单测）。
 *
 * 契约：「群消息写入记忆时带 `source_message_id` 与 `role_id`」。旧实现拿「窗口最后一条
 * 消息」当所有 fact 的来源，于是窗口里第 2 条消息里的事实也被记成最后一条的来源——
 * 溯源字段是假的。这里改成：模型能报出 `source_line` 就用那一行的真实消息 id，
 * 报不出才落到调用方给的兜底消息；两者都没有就留 null（`filterMemoryForViewer`
 * 对 null 放行）。
 */
internal object MemoryAttribution {

    data class Write(
        val spaceId: String,
        val roleId: String?,
        val sourceMessageId: String?,
    )

    fun forExtractedFact(
        spaceId: String,
        roleId: String?,
        fact: MemoryExtractor.ExtractedFact,
        fallbackMessageId: String?,
        messageIdOfLine: (Int) -> String?,
    ): Write = Write(
        spaceId = spaceId,
        roleId = roleId,
        sourceMessageId = fact.sourceLine?.let(messageIdOfLine) ?: fallbackMessageId,
    )
}
