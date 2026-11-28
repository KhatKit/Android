package heizige.kk.khatkit.app.feature.explore.helper

import heizige.kk.khatkit.app.feature.explore.data.McpCardStatus
import heizige.kk.khatkit.app.feature.explore.data.McpInstallPlan

import heizige.kk.khatkit.app.core.data.ai.mcp.McpCommonOptions
import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.data.ai.mcp.McpStatus
import heizige.kk.khatkit.app.core.data.ai.mcp.serverUrl
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.hub.HubResourceMcpServer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal val PLACEHOLDER_WHOLE_VALUES = setOf(
    "api_key", "apikey", "key", "token", "your_api_key", "your_key", "your_token",
    "your_token_here", "key_here", "token_here", "replace_me", "change_me", "todo",
    "xxx", "none", "null", "n/a", "secret", "password", "bearer_token",
)

internal val PLACEHOLDER_PREFIXES = listOf("your_", "your-", "my_", "insert_", "replace_", "change_", "<", "{{", "\${")

internal val PLACEHOLDER_SUFFIXES = listOf("_here", "_key", "_token", ">", "}}")

internal val SECRET_NAME_HINTS = listOf(
    "key", "token", "secret", "password", "passwd", "authorization", "auth",
    "cookie", "credential", "signature", "session", "bearer",
)

/** 值是不是「还没填」的占位串。空串也算。 */
fun mcpValueIsPlaceholder(value: String): Boolean {
    val v = value.trim()
    if (v.isEmpty()) return true
    val lower = v.lowercase()
    if (lower in PLACEHOLDER_WHOLE_VALUES) return true
    if (PLACEHOLDER_PREFIXES.any { lower.startsWith(it) }) return true
    if (PLACEHOLDER_SUFFIXES.any { lower.endsWith(it) }) return true
    // 全是同一个字符（xxxx / 1111 / ****）也是占位
    return v.length >= 4 && v.toSet().size == 1
}

/** 从请求头里挑出必填（值还是占位）的 key，保持声明顺序。 */
fun mcpMissingRequiredHeaders(headers: List<Pair<String, String>>): List<String> =
    headers.filter { (name, value) -> name.isNotBlank() && mcpValueIsPlaceholder(value) }
        .map { it.first }

/** 从 P0-3 的 `config_schema` 里挑出必填字段。 */
fun mcpRequiredKeysFromSchema(schema: JsonObject): List<String> {
    if (schema.isEmpty()) return emptyList()
    val keys = LinkedHashSet<String>()
    (schema["required"] as? JsonArray)?.forEach { element ->
        (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { keys += it }
    }
    (schema["properties"] as? JsonObject)?.forEach { (name, raw) ->
        val prop = raw as? JsonObject ?: return@forEach
        val declared = prop["x-required"]?.jsonPrimitive?.booleanOrNull == true ||
            prop["required"]?.jsonPrimitive?.booleanOrNull == true ||
            prop["x-secret"]?.jsonPrimitive?.booleanOrNull == true ||
            prop["format"]?.jsonPrimitive?.contentOrNull == "password"
        if (declared && name.isNotBlank()) keys += name
    }
    return keys.toList()
}

/**
 * 把资源包里的一个 MCP 服务（+ 可选 `config_schema`）折成安装计划：
 * 预填名称 / 传输 / URL / 头，缺失的必填写进 [McpInstallPlan.missingRequired]，
 * 此时 config 的 `enable` 已置 false。
 */
fun planMcpInstall(
    server: HubResourceMcpServer,
    schema: JsonObject = JsonObject(emptyMap()),
): McpInstallPlan {
    val headers = server.headers
        .mapNotNull { row -> row.takeIf { it.size >= 2 }?.let { it[0].trim() to it[1] } }
        .filter { it.first.isNotBlank() }
    val missing = LinkedHashSet<String>()
    missing += mcpRequiredKeysFromSchema(schema)
    missing += mcpMissingRequiredHeaders(headers)
    val options = McpCommonOptions(
        enable = missing.isEmpty(),
        name = server.name.ifBlank { server.url },
        headers = headers,
    )
    val config = if (server.transport.equals("sse", ignoreCase = true)) {
        McpServerConfig.SseTransportServer(commonOptions = options, url = server.url)
    } else {
        McpServerConfig.StreamableHTTPServer(commonOptions = options, url = server.url)
    }
    return McpInstallPlan(config = config, missingRequired = missing.toList())
}

/**
 * 已保存配置的必填缺失（用户在表单里删空某个头，重新落盘时要用同一判定）。
 */
fun mcpMissingRequiredKeys(config: McpServerConfig): List<String> =
    mcpMissingRequiredHeaders(config.commonOptions.headers)

/** 用当前的表单值重算计划（保存路径：判定依据来自用户刚填的 config）。 */
fun rePlanMcpInstall(config: McpServerConfig): McpInstallPlan {
    val missing = mcpMissingRequiredKeys(config)
    return McpInstallPlan(config = config.withEnabled(missing.isEmpty()), missingRequired = missing)
}

/** 安装与保存共用的落盘决策：缺必填的候选配置不会启用或挂入助手。 */
internal fun applyMcpInstallDecision(settings: Settings, plan: McpInstallPlan): Settings {
    val incoming = plan.config
    val index = settings.mcpServers.indexOfFirst { existing ->
        existing.id == incoming.id ||
            (incoming.serverUrl.isNotBlank() && existing.serverUrl == incoming.serverUrl)
    }
    val servers = if (index >= 0) {
        settings.mcpServers.toMutableList().also { it[index] = incoming }
    } else {
        settings.mcpServers + incoming
    }
    val assistants = if (plan.enable) {
        settings.assistants.map { assistant ->
            if (assistant.id != settings.assistantId) assistant
            else assistant.copy(mcpServers = assistant.mcpServers + incoming.id)
        }
    } else {
        settings.assistants
    }
    return settings.copy(mcpServers = servers, assistants = assistants)
}

/** 不改其它字段，只改启用位。 */
fun McpServerConfig.withEnabled(enabled: Boolean): McpServerConfig = when (this) {
    is McpServerConfig.SseTransportServer -> copy(
        commonOptions = commonOptions.copy(enable = enabled),
    )

    is McpServerConfig.StreamableHTTPServer -> copy(
        commonOptions = commonOptions.copy(enable = enabled),
    )
}

/**
 * 卡片状态三态：缺必填 -> [McpCardStatus.NEEDS_CONFIG]（优先级最高，缺必填时
 * 就算底层报了连上也不认）；连不上 -> [McpCardStatus.CONNECT_FAILED]；其余 -> 已配置。
 * 函数对全部 [McpStatus] 分支都有返回值，不存在第四种状态。
 */
fun mcpCardStatus(config: McpServerConfig, status: McpStatus?): McpCardStatus {
    if (mcpMissingRequiredKeys(config).isNotEmpty()) return McpCardStatus.NEEDS_CONFIG
    return when (status) {
        is McpStatus.Error, McpStatus.NeedsAuthorization -> McpCardStatus.CONNECT_FAILED
        McpStatus.Idle, McpStatus.Connecting, McpStatus.Connected,
        is McpStatus.Reconnecting, McpStatus.Authorizing, null,
        -> McpCardStatus.CONFIGURED
    }
}

/** 头名是不是敏感字段（Authorization / X-Api-Key / Cookie …）。 */
private const val MASK = "••••••"

/** 头/字段是否敏感：命中名字线索才算，避免把普通字段也打码。 */
fun isSecretHeaderName(name: String): Boolean {
    val lower = name.lowercase()
    return SECRET_NAME_HINTS.any { lower.contains(it) }
}

/** 脱敏：长度 <= 8 全遮，更长则只留末 4 位，避免日志/UI 里出现可直接使用的凭据。 */
fun maskSecretValue(value: String): String = when {
    value.isEmpty() -> ""
    value.length <= 8 -> MASK
    else -> MASK + value.takeLast(4)
}

/** URL 里的敏感 query（`?token=` / `&api_key=` / `access_token` …）同样遮掉。 */
fun maskMcpUrl(url: String): String {
    val queryStart = url.indexOf('?')
    if (queryStart < 0) return url
    val base = url.substring(0, queryStart)
    val query = url.substring(queryStart + 1)
    val masked = query.split('&').joinToString("&") { pair ->
        val eq = pair.indexOf('=')
        if (eq <= 0) {
            pair
        } else {
            val key = pair.substring(0, eq)
            if (isSecretHeaderName(key)) "$key=${maskSecretValue(pair.substring(eq + 1))}" else pair
        }
    }
    return "$base?$masked"
}

/** 市场详情 / 提示文案里用的脱敏摘要：名称 + 脱敏 URL + 脱敏后的头。 */
fun maskedMcpSummary(config: McpServerConfig): String {
    val parts = mutableListOf<String>()
    val name = config.commonOptions.name.trim()
    if (name.isNotEmpty()) parts += name
    val url = maskMcpUrl(config.serverUrl)
    if (url.isNotEmpty()) parts += url
    val headers = config.commonOptions.headers
        .filter { it.first.isNotBlank() }
        .joinToString(", ") { (key, value) ->
            "$key: ${if (isSecretHeaderName(key)) maskSecretValue(value) else value}"
        }
    if (headers.isNotEmpty()) parts += headers
    return parts.joinToString(" · ")
}

/** 展示用的脱敏头列表（表单预览行）。 */
fun maskedMcpHeaders(headers: List<Pair<String, String>>): List<Pair<String, String>> =
    headers.map { (key, value) -> key to if (isSecretHeaderName(key)) maskSecretValue(value) else value }
