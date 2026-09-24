package heizige.kk.khatkit.hub

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 卡片 Hub 的客户端（设计文档第 9 节）。
 *
 * 网络层用 Ktor（CIO 引擎），刻意不依赖 OkHttp。
 *
 * 千万级规模注意（v1 不必实现全部，但接口要留）：
 *   - 索引分片：/index/shard/{tag}/{page}.json，不拉全量
 *   - 卡片懒加载：AI 选中后才下载对应包
 *   - 共享 lib 独立缓存，不进卡片包
 */
class HubClient(
    private val baseUrl: String,
    engine: HttpClientEngine = CIO.create(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 8_000
            socketTimeoutMillis = 8_000
        }
    }

    private fun resolve(path: String): String = resolveOn(baseUrl, path)

    private fun resolveOn(base: String, path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path
        else base.trimEnd('/') + "/" + path.trimStart('/')

    /** 语义召回：服务端 embedding + 硬过滤，返回 top-K 候选喂给 AI。 */
    suspend fun search(
        query: String,
        capabilities: Set<String> = emptySet(),
        limit: Int = 20,
    ): CardIndex {
        val response = client.post(resolve("/api/khatkit/search")) {
            contentType(ContentType.Application.Json)
            setBody(KhatKitSearchRequest(query, capabilities.toList(), limit))
        }
        return response.body()
    }

    suspend fun fetchLibIndex(): LibIndex =
        client.get(resolve("/api/khatkit/libs/index.json")).body()

    /** 下载卡片包到 destDir，校验 sha256。 */
    suspend fun downloadCard(entry: CardIndexEntry, destDir: File): File =
        download(resolve(entry.url), File(destDir, "${entry.name}-${entry.version}.zip"), entry.hash)

    suspend fun downloadLib(lib: LibIndexEntry, destDir: File): File =
        download(resolve(lib.url), File(destDir, "${lib.name}-${lib.version}"), lib.hash)

    private suspend fun download(url: String, target: File, expectedHash: String): File =
        withContext(Dispatchers.IO) {
            val response = client.get(url)
            if (!response.status.isSuccess()) {
                error("下载失败 ${response.status.value}: $url")
            }
            val bytes = response.readBytes()
            if (expectedHash.isNotBlank()) {
                val actual = sha256(bytes)
                require(actual.equals(expectedHash, ignoreCase = true)) {
                    "卡片包 hash 不匹配：期望 $expectedHash，实际 $actual"
                }
            }
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            target
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    // ===== KhatKitHub 账户 / 套餐 / 市场（v2 API）=====

    /**
     * 激活套餐：POST /api/activate。允许指定与客户端默认地址不同的 [baseUrl]
     * （激活页可能刚改过 Hub 地址）。响应里没有显式 token 时沿用激活令牌本身。
     */
    suspend fun activate(baseUrl: String, request: HubActivateRequest): HubActivation =
        withContext(Dispatchers.IO) {
            val call = runCatching {
                postJson(resolveOn(baseUrl, "/api/activate"), json.encodeToJsonElement(request))
            }.getOrElse {
                return@withContext HubActivation(false, message = networkError(it))
            }
            val body = call.body?.asObj()
            if (call.code == 401 || call.code == 403) {
                return@withContext HubActivation(false, message = httpError(call.code, body))
            }
            if (call.code !in 200..299) {
                return@withContext HubActivation(false, message = httpError(call.code, body))
            }
            val ok = body?.boolAny("ok", "success") ?: true
            val responseToken = body?.strAny("token", "session_token", "access_token", "api_key", "apiKey")
                ?: request.token
            val sessionKey = body?.strAny("session_key", "sessionKey", "session", "secret").orEmpty()
            HubActivation(
                ok = ok && responseToken.isNotBlank(),
                token = responseToken,
                sessionKey = sessionKey,
                message = body?.errorText().orEmpty(),
                account = body?.toAccountInfo(activeFallback = true) ?: HubAccountInfo(active = true),
            )
        }

    /** 账户与剩余额度：GET /api/me（Bearer token）。 */
    suspend fun me(token: String): HubMeResult = withContext(Dispatchers.IO) {
        val call = runCatching { getJson(resolve("/api/me"), token) }.getOrElse {
            return@withContext HubMeResult(false, message = networkError(it))
        }
        if (call.code !in 200..299) {
            return@withContext HubMeResult(false, message = httpError(call.code, call.body?.asObj()))
        }
        val body = call.body?.asObj()
        HubMeResult(
            ok = true,
            info = body?.toAccountInfo(activeFallback = true) ?: HubAccountInfo(active = true),
            message = body?.errorText().orEmpty(),
        )
    }

    /**
     * 工具调用授权：POST /api/tools/authorize（price 单位为分）。
     *
     * 只有服务器「明确拒绝」才返回 [HubAuthorizeOutcome.Denied]；
     * 网络错误、超时、404/5xx、无法解析的响应一律 [HubAuthorizeOutcome.Unavailable]，
     * 由调用方决定降级为本地免费运行（失败开放）。
     */
    suspend fun authorizeTool(
        token: String,
        cardName: String,
        priceCents: Long,
    ): HubAuthorizeOutcome = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("card_name", cardName)
            put("price", priceCents.coerceAtLeast(0))
        }
        val call = runCatching { postJson(resolve("/api/tools/authorize"), payload, token) }.getOrElse {
            return@withContext HubAuthorizeOutcome.Unavailable(networkError(it))
        }
        val body = call.body?.asObj()
        when (call.code) {
            401 -> HubAuthorizeOutcome.Denied(
                body?.errorText() ?: "令牌无效或已过期，请重新激活套餐"
            )
            402, 403, 429 -> HubAuthorizeOutcome.Denied(
                body?.errorText()
                    ?: if (call.code == 429) "请求过于频繁，请稍后再试" else "套餐工具次数不足，请充值或续费"
            )
            in 200..299 -> when (body?.boolAny("allow", "allowed", "ok", "authorized", "granted", "success")) {
                false -> HubAuthorizeOutcome.Denied(
                    body.errorText() ?: "套餐工具次数不足，本次调用被拒绝"
                )
                true -> HubAuthorizeOutcome.Allowed(
                    body.longAny("remaining_tool_calls", "remaining", "tool_calls_remaining", "quota_remaining", "balance")
                )
                // 2xx 但没有明确字段：有 error 视为拒绝，否则按放行处理（不阻塞本地卡片）
                null -> body?.let { it["error"] }?.let { HubAuthorizeOutcome.Denied(body.errorText() ?: "调用被拒绝") }
                    ?: HubAuthorizeOutcome.Allowed()
            }
            else -> HubAuthorizeOutcome.Unavailable(httpError(call.code, body))
        }
    }

    /** 运行结果上报（best-effort）：POST /api/tools/report。 */
    suspend fun reportTool(token: String, request: HubToolReportRequest): HubActionResult =
        mutation(resolve("/api/tools/report"), json.encodeToJsonElement(request), token)

    /** 市场卡片列表（公开）：GET /api/cards，展示价格 / 协议 / 优选徽标。 */
    suspend fun listCards(token: String? = null): HubCardListResult = withContext(Dispatchers.IO) {
        val call = runCatching { getJson(resolve("/api/cards"), token) }.getOrElse {
            return@withContext HubCardListResult(false, message = networkError(it))
        }
        if (call.code !in 200..299) {
            return@withContext HubCardListResult(false, message = httpError(call.code, call.body?.asObj()))
        }
        val cards = when (val body = call.body) {
            is JsonArray -> parseMarketCards(body)
            is JsonObject -> {
                val array = body["cards"] as? JsonArray
                    ?: body["data"] as? JsonArray
                    ?: body["list"] as? JsonArray
                array?.let { parseMarketCards(it) }.orEmpty()
            }
            else -> emptyList()
        }
        HubCardListResult(true, cards)
    }

    /** 发布卡片：POST /api/cards/publish。 */
    suspend fun publishCard(token: String, request: HubCardPublishRequest): HubActionResult =
        mutation(resolve("/api/cards/publish"), json.encodeToJsonElement(request), token)

    /** 提交改进（fork）：POST /api/cards/{name}/fork，带父版本与更新说明。 */
    suspend fun forkCard(token: String, name: String, request: HubCardForkRequest): HubActionResult =
        mutation(resolve("/api/cards/${encodeSegment(name)}/fork"), json.encodeToJsonElement(request), token)

    /** 给卡片投票（upvote）：POST /api/cards/{name}/vote。 */
    suspend fun voteCard(token: String, name: String): HubActionResult =
        mutation(
            resolve("/api/cards/${encodeSegment(name)}/vote"),
            buildJsonObject { put("vote", "up") },
            token,
        )

    private suspend fun mutation(url: String, body: JsonElement, token: String): HubActionResult {
        val call = runCatching { postJson(url, body, token) }.getOrElse {
            return HubActionResult(false, networkError(it))
        }
        if (call.code !in 200..299) {
            return HubActionResult(false, httpError(call.code, call.body?.asObj()))
        }
        val obj = call.body?.asObj()
        val ok = obj?.boolAny("ok", "success", "allowed") ?: true
        return HubActionResult(ok, obj?.strAny("message", "error", "reason", "detail").orEmpty())
    }

    private data class HubHttpCall(val code: Int, val body: JsonElement?)

    private suspend fun postJson(url: String, body: JsonElement, token: String? = null): HubHttpCall {
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
            token?.takeIf { it.isNotBlank() }?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        return HubHttpCall(response.status.value, parseElement(text))
    }

    private suspend fun getJson(url: String, token: String? = null): HubHttpCall {
        val response = client.get(url) {
            token?.takeIf { it.isNotBlank() }?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        return HubHttpCall(response.status.value, parseElement(text))
    }

    private fun parseElement(text: String): JsonElement? {
        if (text.isBlank()) return null
        return runCatching { json.parseToJsonElement(text) }.getOrNull()
    }

    private fun networkError(error: Throwable): String =
        "网络错误：${error.message ?: error.javaClass.simpleName}（请检查网络或 Hub 地址）"

    private fun httpError(code: Int, body: JsonObject?): String {
        body?.errorText()?.let { return it }
        return when (code) {
            400 -> "请求参数有误（400）"
            401 -> "令牌无效或已过期，请重新激活套餐"
            402, 403 -> "套餐工具次数不足或权益不可用"
            404 -> "服务端未提供该接口（Hub 可能尚未部署）"
            408 -> "请求超时"
            429 -> "请求过于频繁，请稍后再试"
            in 500..599 -> "服务器错误（$code），请稍后重试"
            else -> "请求失败（$code）"
        }
    }

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun parseMarketCards(array: JsonArray): List<HubCardMarketInfo> = array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val name = obj.strAny("name", "card_name") ?: return@mapNotNull null
        val pricing = obj["pricing"] as? JsonObject
        HubCardMarketInfo(
            name = name,
            version = obj.strAny("preferred_version", "version") ?: "",
            author = obj.strAny("author", "author_name") ?: "",
            summary = obj.strAny("summary", "description", "desc") ?: "",
            priceCents = obj.longAny("price_per_call", "price", "price_cents", "unit_price")
                ?: pricing?.longAny("price", "price_per_call") ?: 0L,
            currency = obj.strAny("currency") ?: pricing?.strAny("currency") ?: "CNY",
            license = obj.strAny("license", "license_name") ?: "",
            preferred = obj.boolAny("has_preferred", "preferred", "featured", "recommended", "is_preferred") == true,
            votes = (obj.longAny("upvotes", "votes", "vote_count", "likes") ?: 0L).toInt(),
            score = obj.longAny("score") ?: 0L,
        )
    }

    private fun JsonObject.toAccountInfo(activeFallback: Boolean): HubAccountInfo {
        val aiTokens = this["ai_tokens"] as? JsonObject
        val toolCalls = this["tool_calls"] as? JsonObject
        val statusActive = strAny("status")?.equals("active", ignoreCase = true) == true
        return HubAccountInfo(
            active = boolAny("active", "activated", "valid", "enabled", "is_active") ?: statusActive || activeFallback,
            plan = strAny("package_name", "plan_name", "plan", "package", "套餐") ?: "",
            aiTokensRemaining = aiTokens?.longAny("remaining", "left", "balance")
                ?: longAny(
                    "ai_tokens_remaining", "ai_tokens", "tokens_remaining", "ai_token_balance",
                    "ai_token_left", "token_quota_remaining",
                ),
            toolCallsRemaining = toolCalls?.longAny("remaining", "left", "balance")
                ?: longAny(
                    "tool_calls_remaining", "tools_remaining", "tool_calls", "tool_quota_remaining",
                    "tool_calls_left", "tool_call_balance",
                ),
            expiresAt = timeAny("expires_at", "expiresAt", "expire_at", "expiry", "expired_at", "valid_until"),
        )
    }

    // ===== 宽容字段提取：兼容后端字段命名与嵌套结构 =====

    private fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

    /** 错误文案：支持 Hub 的 {"error":{"message":"…"}} 包装与根级 message/reason。 */
    private fun JsonObject?.errorText(): String? {
        strAny("message", "reason", "detail")?.let { return it }
        return when (val error = this?.get("error")) {
            is JsonObject -> error.strAny("message", "reason", "detail")
            is JsonPrimitive -> error.content.takeIf { it.isNotBlank() && error.isString }
            else -> null
        }
    }

    private fun JsonObject?.candidates(): List<JsonObject> {
        if (this == null) return emptyList()
        val nested = listOf("quota", "account", "data", "subscription", "package", "plan", "user", "result")
            .mapNotNull { this[it] as? JsonObject }
        return listOf(this) + nested
    }

    private fun JsonObject?.strAny(vararg keys: String): String? {
        candidates().forEach { obj ->
            keys.forEach { key ->
                val value = obj[key]
                if (value is JsonPrimitive && value.isString && value.content.isNotBlank()) {
                    return value.content
                }
            }
        }
        return null
    }

    private fun JsonObject?.longAny(vararg keys: String): Long? {
        candidates().forEach { obj ->
            keys.forEach { key ->
                (obj[key] as? JsonPrimitive)?.longOrNull?.let { return it }
            }
        }
        return null
    }

    private fun JsonObject?.boolAny(vararg keys: String): Boolean? {
        candidates().forEach { obj ->
            keys.forEach { key ->
                (obj[key] as? JsonPrimitive)?.booleanOrNull?.let { return it }
            }
        }
        return null
    }

    private fun JsonObject?.timeAny(vararg keys: String): Long? {
        candidates().forEach { obj ->
            keys.forEach { key ->
                val value = obj[key] as? JsonPrimitive ?: return@forEach
                value.longOrNull?.let { return normalizeEpoch(it) }
                parseIsoTime(value.content)?.let { return it }
            }
        }
        return null
    }

    /** 秒级时间戳统一成毫秒。 */
    private fun normalizeEpoch(value: Long): Long =
        if (value in 1..9_999_999_999L) value * 1000 else value

    private fun parseIsoTime(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(trimmed.replace(' ', 'T'))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
            ?: runCatching {
                LocalDate.parse(trimmed).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
    }

    fun close() = client.close()
}
