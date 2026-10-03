package heizige.kk.khatkit.app.core.data.browser

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import heizige.kk.khatkit.ai.core.InputSchema
import heizige.kk.khatkit.ai.core.Tool
import heizige.kk.khatkit.ai.ui.UIMessagePart

/** Tool surface for a host-provided browser controller. */
fun buildBrowserTools(
    controller: BrowserToolController,
): List<Tool> = listOf(
    Tool(
        name = "browser_sessions",
        description = "List browser tabs and the active tab.",
        execute = { listOf(UIMessagePart.Text(controller.listSessions())) },
    ),
    Tool(
        name = "browser_open",
        description = "Open a URL in a new browser tab.",
        parameters = { urlSchema() },
        needsApproval = { true },
        execute = {
            val url = it.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: error("url is required")
            listOf(UIMessagePart.Text(controller.open(url)))
        },
    ),
    Tool(
        name = "browser_snapshot",
        description = "Read a bounded text snapshot of the active browser tab.",
        execute = { listOf(UIMessagePart.Text(controller.snapshot())) },
    ),
    Tool(
        name = "browser_navigate",
        description = "Navigate the active browser tab to a URL.",
        parameters = { urlSchema() },
        needsApproval = { true },
        execute = {
            val url = it.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: error("url is required")
            listOf(UIMessagePart.Text(controller.navigate(url)))
        },
    ),
)

private fun urlSchema() = InputSchema.Obj(
    properties = buildJsonObject {
        put("url", buildJsonObject {
            put("type", "string")
            put("description", "Absolute http(s) URL")
        })
    },
    required = listOf("url"),
)

interface BrowserToolController {
    suspend fun listSessions(): String
    suspend fun open(url: String): String
    suspend fun snapshot(): String
    suspend fun navigate(url: String): String
}

class BrowserSessionController(
    private val sessions: BrowserSessionManager,
    private val load: suspend (String, String) -> BrowserSession = { id, url ->
        sessions.navigate(id, url)
    },
) : BrowserToolController {
    private val json = Json

    override suspend fun listSessions(): String =
        json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(BrowserSessionDto.serializer()),
            sessions.listSessions().map(BrowserSessionDto::from),
        )

    override suspend fun open(url: String): String {
        BrowserSessionManager.validateBrowserUrl(url)
        return BrowserSessionDto.from(sessions.createSession(url)).toJson()
    }

    override suspend fun snapshot(): String =
        sessions.activeSession()?.let { BrowserSessionDto.from(it).toJson() }
            ?: error("No active browser session")

    override suspend fun navigate(url: String): String {
        val session = sessions.activeSession() ?: sessions.createSession()
        return BrowserSessionDto.from(load(session.id, url)).toJson()
    }
}

@kotlinx.serialization.Serializable
private data class BrowserSessionDto(
    val id: String,
    val url: String,
    val title: String? = null,
    val snapshot: String? = null,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json
        fun from(session: BrowserSession) = BrowserSessionDto(
            id = session.id,
            url = session.currentUrl,
            title = session.title,
            snapshot = session.snapshot,
        )
    }
}
