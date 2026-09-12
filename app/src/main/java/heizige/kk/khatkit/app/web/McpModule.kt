package heizige.kk.khatkit.web

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.JsonObject
import heizige.kk.khatkit.app.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.data.datastore.SettingsStore
import java.util.UUID

/** 把 KhatKit 卡片运行时适配成 MCP tool 宿主。 */
class KhatKitMcpToolHost(private val provider: KhatKitToolProvider) : McpToolHost {
    override suspend fun listTools(): List<McpToolDescriptor> = provider.mcpToolDescriptors()

    override suspend fun callTool(name: String, arguments: JsonObject): McpToolResult =
        provider.callMcpTool(name, arguments)
}

/**
 * MCP Server 出口：`POST /mcp`（Streamable HTTP + JSON-RPC）。
 *
 * 暴露的是本机已安装卡片，Claude Desktop / Cursor 等客户端可直接调用手机能力。
 * web 服务开启访问密码时，MCP 同样要求 `Authorization: Bearer <password>`。
 */
fun Application.configureMcp(dispatcher: McpDispatcher, settingsStore: SettingsStore) {
    routing {
        post("/mcp") {
            val settings = settingsStore.settingsFlow.value
            if (settings.webServerJwtEnabled && settings.webServerAccessPassword.isNotBlank()) {
                val token = call.request.headers[HttpHeaders.Authorization]
                    ?.removePrefix("Bearer ")
                    ?.trim()
                    ?: call.request.queryParameters["access_token"]
                if (token != settings.webServerAccessPassword) {
                    call.respondText(
                        """{"jsonrpc":"2.0","error":{"code":-32001,"message":"Unauthorized"}}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Unauthorized,
                    )
                    return@post
                }
            }

            call.response.header(
                "Mcp-Session-Id",
                call.request.headers["Mcp-Session-Id"] ?: UUID.randomUUID().toString(),
            )
            when (val outcome = dispatcher.dispatch(call.receiveText())) {
                is McpDispatcher.Outcome.Accepted -> call.respond(HttpStatusCode.Accepted)
                is McpDispatcher.Outcome.Reply -> call.respondText(
                    outcome.payload.toString(),
                    ContentType.Application.Json,
                )
            }
        }
    }
}
