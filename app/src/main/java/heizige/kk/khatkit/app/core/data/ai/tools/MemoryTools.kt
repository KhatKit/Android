package heizige.kk.khatkit.app.core.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.builtins.ListSerializer
import heizige.kk.khatkit.ai.core.InputSchema
import heizige.kk.khatkit.ai.core.Tool
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.model.AssistantMemory
import heizige.kk.khatkit.app.core.util.toLocalString
import java.time.LocalDate

/**
 * A2 记忆工具：`memory_search` / `memory_add` / `memory_link` / `memory_forget`。
 *
 * 检索结果带 `id` 与来源（sourceMessageId/confidence/extractedAt），100% 可溯源。
 */
fun buildMemorySearchTool(
    json: Json,
    onSearch: suspend (String, Int) -> List<AssistantMemory>,
): Tool = Tool(
    name = "memory_search",
    description = """
        Search long-term memories with hybrid retrieval (semantic + keyword + graph).
        Returns memories with `id`, `content`, `sourceMessageId`, `confidence`, `extractedAt`.
        Use `id` for memory_link / memory_forget. Always search before deciding to add or forget.
        Only your own space is searched; in a group chat you never see the other roles' memories.
    """.trimIndent(),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Natural-language query or keywords")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum results, 1 to 20 (default 8)")
                })
            },
            required = listOf("query")
        )
    },
    execute = {
        val params = it.jsonObject
        val query = params["query"]?.jsonPrimitive?.contentOrNull ?: error("query is required")
        val limit = (params["limit"]?.jsonPrimitive?.intOrNull ?: 8).coerceIn(1, 20)
        val results = onSearch(query, limit)
        listOf(UIMessagePart.Text(json.encodeToJsonElement(ListSerializer(AssistantMemory.serializer()), results).toString()))
    }
)

fun buildMemoryAddTool(
    json: Json,
    onAdd: suspend (content: String, sourceMessageId: String?, confidence: Float) -> AssistantMemory,
): Tool = Tool(
    name = "memory_add",
    description = """
        Store a new long-term memory (fact, preference, plan, event).
        Do not store sensitive information (ethnicity, religion, sexual orientation, political views, sex life, criminal records).
        Do not show memory content in the conversation unless the user explicitly asks.
        Similar memories should be merged; prefer memory_search first to avoid duplicates.
        Memories are written to your own private space: in a group chat each role has its own space and cannot see or write the other roles' memories.
        Today is ${LocalDate.now().toLocalString(true)}.
        Examples:
        {"content":"User prefers brief replies and is more active on weekends."}
        {"content":"User's preferred name is A-Xing.","confidence":0.9}
    """.trimIndent(),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "The memory content to store")
                })
                put("confidence", buildJsonObject {
                    put("type", "number")
                    put("description", "Confidence 0..1 (default 1.0)")
                })
            },
            required = listOf("content")
        )
    },
    execute = {
        val params = it.jsonObject
        val content = params["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")
        val confidence = params["confidence"]?.jsonPrimitive?.floatOrNull ?: 1f
        // C1-M：`source_message_id` 只在能定位到具体消息时才填。模型主动 `memory_add`
        // 的内容是对「当前可见上下文」的自主断言，归因不到某一条消息；编一个 message id
        // 会让 `GroupChat.filterMemoryForViewer` 按假来源判可见性——假来源若恰好对
        // viewer 可见，越权内容就被放行了，比留 null 更危险。按该函数的口径
        // `source == null` 放行；群聊隔离不依赖这一列，靠的是空间键 + `role_id`
        // （由 [heizige.kk.khatkit.app.core.data.ai.tools.MemoryToolScopeResolver] 决定）。
        // 真正「群消息写入记忆带 source_message_id」的路径是 MemoryExtractor：
        // 那里能按消息窗口逐条归因到真实消息。
        val sourceMessageId: String? = null
        val result = onAdd(content, sourceMessageId, confidence.coerceIn(0f, 1f))
        listOf(UIMessagePart.Text(json.encodeToJsonElement(AssistantMemory.serializer(), result).toString()))
    }
)

fun buildMemoryLinkTool(
    json: Json,
    onLink: suspend (sourceId: Int, targetId: Int, relType: String) -> Int,
): Tool = Tool(
    name = "memory_link",
    description = """
        Create a typed relation between two stored memories (by their `id` from memory_search).
        This builds a lightweight knowledge graph used to expand retrieval.
        Example: {"source_id":3,"target_id":7,"relation":"RELATED"}
    """.trimIndent(),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("source_id", buildJsonObject {
                    put("type", "integer")
                    put("description", "Source memory id")
                })
                put("target_id", buildJsonObject {
                    put("type", "integer")
                    put("description", "Target memory id")
                })
                put("relation", buildJsonObject {
                    put("type", "string")
                    put("description", "Relation type: RELATED, CAUSES, PART_OF, HAPPENED_AT, MENTIONS, or a custom label")
                })
            },
            required = listOf("source_id", "target_id")
        )
    },
    execute = {
        val params = it.jsonObject
        val sourceId = params["source_id"]?.jsonPrimitive?.intOrNull ?: error("source_id is required")
        val targetId = params["target_id"]?.jsonPrimitive?.intOrNull ?: error("target_id is required")
        val relType = params["relation"]?.jsonPrimitive?.contentOrNull ?: "RELATED"
        val edgeId = onLink(sourceId, targetId, relType)
        listOf(UIMessagePart.Text(
            buildJsonObject {
                put("success", true)
                put("edge_id", edgeId)
                put("source_id", sourceId)
                put("target_id", targetId)
                put("relation", relType)
            }.toString()
        ))
    }
)

fun buildMemoryForgetTool(
    json: Json,
    onForget: suspend (Int) -> Unit,
): Tool = Tool(
    name = "memory_forget",
    description = """
        Forget (soft-delete) a memory that is outdated or wrong. The record is kept for
        provenance but no longer retrieved or injected. Use the `id` from memory_search.
        Example: {"id":7}
    """.trimIndent(),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", "integer")
                    put("description", "Memory id to forget")
                })
            },
            required = listOf("id")
        )
    },
    execute = {
        val params = it.jsonObject
        val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
        onForget(id)
        listOf(UIMessagePart.Text(
            buildJsonObject {
                put("success", true)
                put("id", id)
                put("forgotten", true)
            }.toString()
        ))
    }
)

fun buildMemoryTools(
    json: Json,
    onCreation: suspend (String) -> AssistantMemory,
    onUpdate: suspend (Int, String) -> AssistantMemory,
    onDelete: suspend (Int) -> Unit,
    onSearch: suspend (String, Int) -> List<AssistantMemory>,
    onLink: suspend (Int, Int, String) -> Int,
): List<Tool> = listOf(
    buildMemorySearchTool(json, onSearch),
    buildMemoryAddTool(json) { content, _, confidence ->
        if (confidence < 1f) onCreation(content) else onCreation(content)
    },
    buildMemoryLinkTool(json, onLink),
    buildMemoryForgetTool(json, onDelete),
)
