package heizige.kk.khatkit.web

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class McpToolDescriptor(
    val name: String,
    val description: String,
    val inputSchema: JsonObject?,
)

data class McpToolResult(
    val text: String,
    val isError: Boolean = false,
)

/** 把卡片运行时暴露成 MCP tool 的宿主接口（设计文档 11.2 的 MCP Server 出口）。 */
interface McpToolHost {
    suspend fun listTools(): List<McpToolDescriptor>
    suspend fun callTool(name: String, arguments: JsonObject): McpToolResult
}

/**
 * MCP Streamable HTTP 的 JSON-RPC 分发（纯逻辑，便于单测）。
 *
 * 支持 initialize / notifications/initialized / ping / tools/list / tools/call。
 */
class McpDispatcher(private val host: McpToolHost?) {

    sealed interface Outcome {
        /** 通知类消息：HTTP 202，无 body。 */
        data object Accepted : Outcome

        /** 需要返回 JSON-RPC 响应。 */
        data class Reply(val payload: JsonObject) : Outcome
    }

    suspend fun dispatch(raw: String): Outcome {
        val request = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return Outcome.Reply(error(JsonNull, -32700, "Parse error"))
        val id = request["id"] ?: JsonNull
        val method = request["method"]?.jsonPrimitive?.content
        val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())

        if (id == JsonNull && method?.startsWith("notifications/") == true) return Outcome.Accepted

        val toolHost = host ?: return Outcome.Reply(error(id, -32000, "KhatKit tool host unavailable"))

        return when (method) {
            "initialize" -> Outcome.Reply(
                success(
                    id,
                    buildJsonObject {
                        put(
                            "protocolVersion",
                            params["protocolVersion"]?.jsonPrimitive?.content ?: "2025-06-18",
                        )
                        putJsonObject("capabilities") { putJsonObject("tools") {} }
                        putJsonObject("serverInfo") {
                            put("name", "khatkit")
                            put("version", "1.0.0")
                        }
                    },
                )
            )

            "ping" -> Outcome.Reply(success(id, buildJsonObject {}))

            "tools/list" -> {
                val descriptors = toolHost.listTools()
                Outcome.Reply(
                    success(
                        id,
                        buildJsonObject {
                            put("tools", JsonArray(descriptors.map { descriptor(it) }))
                        },
                    )
                )
            }

            "tools/call" -> {
                val name = params["name"]?.jsonPrimitive?.content
                    ?: return Outcome.Reply(error(id, -32602, "Missing tool name"))
                val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                val result = toolHost.callTool(name, arguments)
                Outcome.Reply(
                    success(
                        id,
                        buildJsonObject {
                            putJsonArray("content") {
                                add(
                                    buildJsonObject {
                                        put("type", "text")
                                        put("text", result.text)
                                    }
                                )
                            }
                            put("isError", result.isError)
                        },
                    )
                )
            }

            else -> Outcome.Reply(error(id, -32601, "Method not found: $method"))
        }
    }

    private fun descriptor(tool: McpToolDescriptor): JsonObject = buildJsonObject {
        put("name", tool.name)
        put("description", tool.description)
        put("inputSchema", tool.inputSchema ?: buildJsonObject { put("type", "object") })
    }

    private fun success(id: JsonElement, result: JsonElement): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    private fun error(id: JsonElement, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }
}
