package heizige.kk.khatkit.app.navigation

import heizige.kk.khatkit.app.Screen
import java.io.ByteArrayOutputStream

/**
 * [Screen] ⇄ Mochi 路由字符串（[ui.feice.mochi.navigation.MochiNavigator] 只认 String）的**纯函数**映射表。
 *
 * 约定：
 * - 名字是 kebab-case 的确定性常量，一个 [Screen] 子类一个名字，全表唯一（[ScreenRoutesTest] 守着）；
 * - 参数写在 `?` 后面，`key=value` 用 `&` 分隔，值做百分号编码（非 unreserved 字节一律 `%XX`），
 *   所以 id/URL 里的 `/`、`?`、`&`、`%`、空格、中文都不会破坏路由；
 * - **null 参数不写**（读回时仍是默认的 null），非 null 参数一律写（包括空串），列表为空时不写、
 *   非空时按项编码后用 `,` 连接 —— 因此 [routeOf] 与 [screenOf] 互为逆运算；
 * - 不认识的 route 返回 null（进程恢复时版本变化不崩，交给上层决定回退）。
 *
 * 本对象只做字符串转换，不碰导航栈、不碰 UI；RouteActivity 现有行为不受影响。
 */
object ScreenRoutes {

    /** 所有路由名（不含参数）。测试用来核对「一个 Screen 子类一个名字」。 */
    internal val routeNames: Set<String> = setOf(
        "greeting", "chat", "share-handler", "role-chat-panel", "trace-audit",
        "assistant", "assistant-detail", "assistant-basic", "assistant-prompt",
        "assistant-memory", "assistant-request", "assistant-mcp", "assistant-local-tool",
        "assistant-injections", "translator", "setting", "backup", "image-gen", "web-view",
        "setting-preferences-theme", "setting-preferences-notification",
        "setting-preferences-general", "setting-preferences-ui", "setting-preferences-ui-message",
        "setting-preferences-ui-font", "setting-preferences-ui-code", "setting-permissions",
        "setting-preferences-network", "setting-provider", "setting-provider-detail",
        "setting-models", "setting-about", "setting-search", "setting-search-detail",
        "setting-speech", "setting-mcp", "explore-market", "explore-cards", "explore-local",
        "card-runs", "setting-donate", "setting-files", "setting-memory", "setting-web",
        "setting-triggers", "create-workflow", "setting-package", "debug", "log", "extensions",
        "quick-messages", "prompts", "skills", "workspaces", "workspace-detail",
        "workspace-terminal", "workspace-file-editor", "skill-detail", "message-search",
        "download-center", "stats",
    )

    /** 把一页编成确定性路由。同名同参数 ⇒ 同字符串。 */
    fun routeOf(screen: Screen): String = when (screen) {
        Screen.Greeting -> "greeting"
        is Screen.Chat -> Route("chat").apply {
            str("id", screen.id)
            str("text", screen.text)
            list("files", screen.files)
            str("nodeId", screen.nodeId)
            str("messageId", screen.messageId)
        }.build()
        is Screen.ShareHandler -> Route("share-handler").apply {
            str("text", screen.text)
            str("streamUri", screen.streamUri)
        }.build()
        is Screen.RoleChatPanel -> Route("role-chat-panel").apply {
            str("roleId", screen.roleId)
            str("conversationId", screen.conversationId)
        }.build()
        Screen.TraceAudit -> "trace-audit"
        Screen.Assistant -> "assistant"
        is Screen.AssistantDetail -> Route("assistant-detail").str("id", screen.id).build()
        is Screen.AssistantBasic -> Route("assistant-basic").str("id", screen.id).build()
        is Screen.AssistantPrompt -> Route("assistant-prompt").str("id", screen.id).build()
        is Screen.AssistantMemory -> Route("assistant-memory").str("id", screen.id).build()
        is Screen.AssistantRequest -> Route("assistant-request").str("id", screen.id).build()
        is Screen.AssistantMcp -> Route("assistant-mcp").str("id", screen.id).build()
        is Screen.AssistantLocalTool -> Route("assistant-local-tool").str("id", screen.id).build()
        is Screen.AssistantInjections -> Route("assistant-injections").str("id", screen.id).build()
        Screen.Translator -> "translator"
        Screen.Setting -> "setting"
        Screen.Backup -> "backup"
        Screen.ImageGen -> "image-gen"
        is Screen.WebView -> Route("web-view").apply {
            str("url", screen.url)
            str("contentId", screen.contentId)
        }.build()
        Screen.SettingPreferencesTheme -> "setting-preferences-theme"
        Screen.SettingPreferencesNotification -> "setting-preferences-notification"
        Screen.SettingPreferencesGeneral -> "setting-preferences-general"
        Screen.SettingPreferencesUI -> "setting-preferences-ui"
        Screen.SettingPreferencesUIMessage -> "setting-preferences-ui-message"
        Screen.SettingPreferencesUIFont -> "setting-preferences-ui-font"
        Screen.SettingPreferencesUICode -> "setting-preferences-ui-code"
        Screen.SettingPermissions -> "setting-permissions"
        Screen.SettingPreferencesNetwork -> "setting-preferences-network"
        Screen.SettingProvider -> "setting-provider"
        is Screen.SettingProviderDetail -> Route("setting-provider-detail").str("providerId", screen.providerId).build()
        Screen.SettingModels -> "setting-models"
        Screen.SettingAbout -> "setting-about"
        Screen.SettingSearch -> "setting-search"
        is Screen.SettingSearchDetail -> Route("setting-search-detail").str("serviceId", screen.serviceId).build()
        Screen.SettingSpeech -> "setting-speech"
        Screen.SettingMcp -> "setting-mcp"
        Screen.ExploreMarket -> "explore-market"
        is Screen.ExploreCards -> Route("explore-cards").str("initialQuery", screen.initialQuery).build()
        Screen.ExploreLocal -> "explore-local"
        Screen.CardRuns -> "card-runs"
        Screen.SettingDonate -> "setting-donate"
        Screen.SettingFiles -> "setting-files"
        Screen.SettingMemory -> "setting-memory"
        Screen.SettingWeb -> "setting-web"
        Screen.SettingTriggers -> "setting-triggers"
        is Screen.CreateWorkflow -> Route("create-workflow").str("workflowId", screen.workflowId).build()
        Screen.SettingPackage -> "setting-package"
        Screen.Debug -> "debug"
        Screen.Log -> "log"
        Screen.Extensions -> "extensions"
        Screen.QuickMessages -> "quick-messages"
        Screen.Prompts -> "prompts"
        Screen.Skills -> "skills"
        Screen.Workspaces -> "workspaces"
        is Screen.WorkspaceDetail -> Route("workspace-detail").str("id", screen.id).build()
        is Screen.WorkspaceTerminal -> Route("workspace-terminal").str("id", screen.id).build()
        is Screen.WorkspaceFileEditor -> Route("workspace-file-editor").apply {
            str("id", screen.id)
            str("area", screen.area)
            str("path", screen.path)
        }.build()
        is Screen.SkillDetail -> Route("skill-detail").str("skillName", screen.skillName).build()
        Screen.MessageSearch -> "message-search"
        Screen.DownloadCenter -> "download-center"
        Screen.Stats -> "stats"
    }

    /** [routeOf] 的逆运算；不认识 / 参数缺失或损坏时返回 null。 */
    fun screenOf(route: String): Screen? {
        val mark = route.indexOf('?')
        val name = if (mark < 0) route else route.substring(0, mark)
        val params = if (mark < 0) EMPTY_PARAMS else parseQuery(route.substring(mark + 1)) ?: return null
        return when (name) {
            "greeting" -> Screen.Greeting
            "chat" -> Screen.Chat(
                id = params.str("id") ?: return null,
                text = params.str("text"),
                files = params.list("files"),
                nodeId = params.str("nodeId"),
                messageId = params.str("messageId"),
            )
            "share-handler" -> Screen.ShareHandler(
                text = params.str("text") ?: return null,
                streamUri = params.str("streamUri"),
            )
            "role-chat-panel" -> Screen.RoleChatPanel(
                roleId = params.str("roleId") ?: return null,
                conversationId = params.str("conversationId"),
            )
            "trace-audit" -> Screen.TraceAudit
            "assistant" -> Screen.Assistant
            "assistant-detail" -> Screen.AssistantDetail(params.str("id") ?: return null)
            "assistant-basic" -> Screen.AssistantBasic(params.str("id") ?: return null)
            "assistant-prompt" -> Screen.AssistantPrompt(params.str("id") ?: return null)
            "assistant-memory" -> Screen.AssistantMemory(params.str("id") ?: return null)
            "assistant-request" -> Screen.AssistantRequest(params.str("id") ?: return null)
            "assistant-mcp" -> Screen.AssistantMcp(params.str("id") ?: return null)
            "assistant-local-tool" -> Screen.AssistantLocalTool(params.str("id") ?: return null)
            "assistant-injections" -> Screen.AssistantInjections(params.str("id") ?: return null)
            "translator" -> Screen.Translator
            "setting" -> Screen.Setting
            "backup" -> Screen.Backup
            "image-gen" -> Screen.ImageGen
            "web-view" -> Screen.WebView(
                url = params.str("url") ?: return null,
                contentId = params.str("contentId") ?: return null,
            )
            "setting-preferences-theme" -> Screen.SettingPreferencesTheme
            "setting-preferences-notification" -> Screen.SettingPreferencesNotification
            "setting-preferences-general" -> Screen.SettingPreferencesGeneral
            "setting-preferences-ui" -> Screen.SettingPreferencesUI
            "setting-preferences-ui-message" -> Screen.SettingPreferencesUIMessage
            "setting-preferences-ui-font" -> Screen.SettingPreferencesUIFont
            "setting-preferences-ui-code" -> Screen.SettingPreferencesUICode
            "setting-permissions" -> Screen.SettingPermissions
            "setting-preferences-network" -> Screen.SettingPreferencesNetwork
            "setting-provider" -> Screen.SettingProvider
            "setting-provider-detail" -> Screen.SettingProviderDetail(params.str("providerId") ?: return null)
            "setting-models" -> Screen.SettingModels
            "setting-about" -> Screen.SettingAbout
            "setting-search" -> Screen.SettingSearch
            "setting-search-detail" -> Screen.SettingSearchDetail(params.str("serviceId") ?: return null)
            "setting-speech" -> Screen.SettingSpeech
            "setting-mcp" -> Screen.SettingMcp
            "explore-market" -> Screen.ExploreMarket
            "explore-cards" -> Screen.ExploreCards(initialQuery = params.str("initialQuery") ?: return null)
            "explore-local" -> Screen.ExploreLocal
            "card-runs" -> Screen.CardRuns
            "setting-donate" -> Screen.SettingDonate
            "setting-files" -> Screen.SettingFiles
            "setting-memory" -> Screen.SettingMemory
            "setting-web" -> Screen.SettingWeb
            "setting-triggers" -> Screen.SettingTriggers
            "create-workflow" -> Screen.CreateWorkflow(workflowId = params.str("workflowId"))
            "setting-package" -> Screen.SettingPackage
            "debug" -> Screen.Debug
            "log" -> Screen.Log
            "extensions" -> Screen.Extensions
            "quick-messages" -> Screen.QuickMessages
            "prompts" -> Screen.Prompts
            "skills" -> Screen.Skills
            "workspaces" -> Screen.Workspaces
            "workspace-detail" -> Screen.WorkspaceDetail(params.str("id") ?: return null)
            "workspace-terminal" -> Screen.WorkspaceTerminal(params.str("id") ?: return null)
            "workspace-file-editor" -> Screen.WorkspaceFileEditor(
                id = params.str("id") ?: return null,
                area = params.str("area") ?: return null,
                path = params.str("path") ?: return null,
            )
            "skill-detail" -> Screen.SkillDetail(params.str("skillName") ?: return null)
            "message-search" -> Screen.MessageSearch
            "download-center" -> Screen.DownloadCenter
            "stats" -> Screen.Stats
            else -> null
        }
    }

    // —— 编码 ——

    private val EMPTY_PARAMS: Map<String, String> = emptyMap()
    private const val HEX = "0123456789ABCDEF"

    /** 只保留 RFC 3986 unreserved，其余按 UTF-8 字节写成 `%XX`。 */
    internal fun encode(value: String): String {
        val out = StringBuilder(value.length)
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val ch = code.toChar()
            val unreserved = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
                ch == '-' || ch == '_' || ch == '.' || ch == '~'
            if (unreserved) {
                out.append(ch)
            } else {
                out.append('%').append(HEX[code shr 4]).append(HEX[code and 0x0F])
            }
        }
        return out.toString()
    }

    /** [encode] 的逆运算；格式不对返回 null。 */
    internal fun decode(value: String): String? {
        val text = StringBuilder(value.length)
        val bytes = ByteArrayOutputStream(value.length)
        fun flush() {
            if (bytes.size() > 0) {
                text.append(String(bytes.toByteArray(), CharSets))
                bytes.reset()
            }
        }
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '%') {
                if (i + 2 >= value.length) return null
                val hi = hexDigit(value[i + 1])
                val lo = hexDigit(value[i + 2])
                if (hi < 0 || lo < 0) return null
                bytes.write((hi shl 4) or lo)
                i += 3
            } else {
                flush()
                text.append(ch)
                i++
            }
        }
        flush()
        return text.toString()
    }

    private val CharSets = Charsets.UTF_8

    private fun hexDigit(ch: Char): Int = when (ch) {
        in '0'..'9' -> ch - '0'
        in 'a'..'f' -> ch - 'a' + 10
        in 'A'..'F' -> ch - 'A' + 10
        else -> -1
    }

    private fun parseQuery(raw: String): Map<String, String>? {
        val params = LinkedHashMap<String, String>()
        if (raw.isEmpty()) return params
        for (pair in raw.split('&')) {
            if (pair.isEmpty()) return null
            val eq = pair.indexOf('=')
            if (eq <= 0) return null
            val key = pair.substring(0, eq)
            if (key in params) return null
            params[key] = pair.substring(eq + 1)
        }
        return params
    }

    /** 参数读取（值仍是编码态，这里解一次）。缺失和格式错都算 null。 */
    private fun Map<String, String>.str(key: String): String? =
        this[key]?.let { decode(it) }

    /** 空列表不写参数；非空时项按 `,` 连接，每项单独编码。 */
    private fun Map<String, String>.list(key: String): List<String> {
        if (!containsKey(key)) return emptyList()
        val raw = getValue(key)
        if (raw.isEmpty()) return listOf("")
        return raw.split(',').map { decode(it) ?: return emptyList() }
    }

    /** 按「null 不写、其余一律写」的规则拼路由。 */
    private class Route(private val name: String) {
        private val params = StringBuilder()

        fun str(key: String, value: String?): Route = apply {
            if (value != null) {
                append(key, encode(value))
            }
        }

        fun list(key: String, values: List<String>): Route = apply {
            if (values.isNotEmpty()) {
                append(key, values.joinToString(",") { encode(it) })
            }
        }

        private fun append(key: String, encoded: String) {
            params.append(if (params.isEmpty()) '?' else '&').append(key).append('=').append(encoded)
        }

        fun build(): String = name + params
    }
}
