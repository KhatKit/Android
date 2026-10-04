package heizige.kk.khatkit.app.core.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage

/**
 * 群聊调度内核（C1-R）。
 *
 * 对照 SillyTavern `public/scripts/group-chats.js`（AGPL-3.0，release 分支，2026-10-04 阅读）：
 * List Order 按成员名单顺序发言；Natural Order 用整词名字识别 @。
 * 酒馆默认共享同一份历史。这里不照搬共享历史：每个角色只看见自己的消息、用户消息、
 * @自己的消息和轮次摘要。pipeline 只把上一角色的本轮输出交给下一位；
 * roundtable 的议长在汇总时才看见本轮其他人的发言。
 *
 * 本文件只做纯函数：接收不可变消息快照与配置，返回可见集合、发言顺序、预算判定、
 * 投票结果与运行日志所需的判定结果。不新增执行器，不读密钥，不写库。
 */

/** 群成员。字段名与契约 `roles[]` 对齐。 */
@Serializable
data class GroupRole(
    @SerialName("role_id")
    val id: String,
    val name: String = "",
    @SerialName("assistant_id")
    val assistantId: String = "",
    val chair: Boolean = false,
    /** Tavern 角色卡引用（只存引用，不内嵌密钥）。 */
    @SerialName("card_id")
    val cardId: String? = null,
    /** 模型绑定；null 表示跟随该角色所属助手。 */
    @SerialName("model_id")
    val modelId: String? = null,
    /**
     * 记忆空间 id。契约允许携带，但硬约束要求键固定为
     * `group:<conversationId>:role:<roleId>`，因此 [GroupChat.validate] 会拒绝
     * 与派生值不一致的写法，避免回退到全局或助手空间。
     */
    @SerialName("memory_space_id")
    val memorySpaceId: String? = null,
    /** 只保存引用；未上线的 ToolPkg/Skill/MCP 入口不在 UI 暴露。 */
    @SerialName("tool_package_ids")
    val toolPackageIds: List<String> = emptyList(),
    @SerialName("skill_ids")
    val skillIds: List<String> = emptyList(),
    @SerialName("mcp_server_ids")
    val mcpServerIds: List<String> = emptyList(),
)

/**
 * 群配置（契约 v1）。走 [GroupConfigSerializer] 以便未知字段原样保留，
 * 因此 `decode(encode(x)) == x` 且带未知字段的旧/新版本文档都能无损往返。
 */
@Serializable(with = GroupConfigSerializer::class)
data class GroupConfig(
    val schemaVersion: Int = GroupChat.SCHEMA_VERSION,
    val roles: List<GroupRole> = emptyList(),
    val mode: String = GroupChat.MODE_PIPELINE,
    val chairRoleId: String? = null,
    val tokenBudgetPerRound: Int = 0,
    val revision: Int = 1,
    /** `vote` 模式的候选集；为空时从用户消息严格解析，仍为空则本轮判失败。 */
    val voteCandidates: List<String> = emptyList(),
    /** `vote` 平票策略：`fail` 按契约失败；`chair` 交议长裁决。 */
    val tiePolicy: String = GroupChat.TIE_FAIL,
    /** 未知字段原样保留，导出/恢复不得丢失。 */
    val extras: JsonObject = JsonObject(emptyMap()),
)

/** QR/分享载荷。只带群配置与角色卡最小元数据，不带密钥、记忆、工具授权 token。 */
@Serializable
data class RoleCardMeta(
    @SerialName("role_id")
    val roleId: String,
    val name: String = "",
    @SerialName("assistant_id")
    val assistantId: String = "",
    @SerialName("card_id")
    val cardId: String? = null,
    val persona: String = "",
    @SerialName("avatar_ref")
    val avatarRef: String? = null,
)

@Serializable
data class GroupSharePayload(
    val kind: String = GroupChat.QR_KIND,
    @SerialName("schema_version")
    val schemaVersion: Int = GroupChat.SCHEMA_VERSION,
    val config: GroupConfig,
    val cards: List<RoleCardMeta> = emptyList(),
)

/** 一次发言安排。 */
data class SpeakerStep(
    val role: GroupRole,
    val predecessorId: String? = null,
    val chairRound: Boolean = false,
)

/** 一轮的完整计划。`roundId` 由触发消息派生，因此重试必然复用同一个轮次。 */
data class RoundPlan(
    val roundId: String,
    val plan: List<SpeakerStep>,
    val candidates: List<String>,
)

/** 结构化选票：只接受候选集内的 id。 */
@Serializable
data class VoteBallot(
    @SerialName("role_id")
    val roleId: String,
    @SerialName("candidate_id")
    val candidateId: String,
    val reason: String = "",
)

sealed interface VoteOutcome {
    data class Decided(
        val winner: String,
        val tally: Map<String, Int>,
        val ballots: List<VoteBallot>,
    ) : VoteOutcome

    data class Tie(
        val candidates: List<String>,
        val tally: Map<String, Int>,
        val ballots: List<VoteBallot>,
    ) : VoteOutcome

    /** 平票且策略为 `chair`：不由本函数裁决，交议长。 */
    data class ChairDecides(
        val candidates: List<String>,
        val tally: Map<String, Int>,
        val ballots: List<VoteBallot>,
    ) : VoteOutcome

    data class Invalid(val reason: String) : VoteOutcome
}

sealed interface RoundBudget {
    data object Continue : RoundBudget

    data class Stop(
        val spent: Int,
        val limit: Int,
        val skippedRoleIds: List<String>,
        val reason: String,
    ) : RoundBudget
}

data class GroupConfigError(val field: String, val message: String)

object GroupChat {
    const val TYPE_DIRECT = "DIRECT"
    const val TYPE_GROUP = "GROUP"

    const val MODE_PIPELINE = "pipeline"
    const val MODE_ROUNDTABLE = "roundtable"
    const val MODE_VOTE = "vote"
    val SUPPORTED_MODES = setOf(MODE_PIPELINE, MODE_ROUNDTABLE, MODE_VOTE)

    const val SUMMARY_ID = "__summary__"
    const val QR_KIND = "khatkit-group"
    const val FILTER_ALL = "ALL"

    const val SCHEMA_VERSION = 1

    /** 宿主上限：超过此值的每轮预算一律拒绝保存。 */
    const val MAX_TOKEN_BUDGET_PER_ROUND = 200_000

    const val TIE_FAIL = "fail"
    const val TIE_CHAIR = "chair"
    val SUPPORTED_TIE_POLICIES = setOf(TIE_FAIL, TIE_CHAIR)

    /** 契约 `turn_kind`。 */
    const val TURN_USER = "user"
    const val TURN_SPEAKER = "speaker"
    const val TURN_CHAIR = "chair"
    const val TURN_VOTE_SUMMARY = "vote_summary"
    const val TURN_ERROR = "error"

    /** 选票前缀。严格整行匹配，不做自由文本猜测。 */
    const val BALLOT_PREFIX = "VOTE:"

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val KNOWN_CONFIG_KEYS = setOf(
        "schema_version", "mode", "chair_role_id", "token_budget_per_round",
        "revision", "tie_policy", "vote_candidates", "roles",
    )

    /** C1 之前写库时用的 camelCase 键，读取时映射到契约键。 */
    private val LEGACY_CONFIG_KEYS = mapOf(
        "schemaVersion" to "schema_version",
        "tokenBudget" to "token_budget_per_round",
        "chairRoleId" to "chair_role_id",
        "tiePolicy" to "tie_policy",
        "voteCandidates" to "vote_candidates",
    )

    private val LEGACY_ROLE_KEYS = mapOf(
        "id" to "role_id",
        "assistantId" to "assistant_id",
        "cardId" to "card_id",
        "modelId" to "model_id",
        "memorySpaceId" to "memory_space_id",
        "toolPackageIds" to "tool_package_ids",
        "skillIds" to "skill_ids",
        "mcpServerIds" to "mcp_server_ids",
    )

    private val KNOWN_ROLE_KEYS = LEGACY_ROLE_KEYS.values.toSet() + setOf("name", "chair")

    /**
     * 导出/分享载荷里禁止出现的键。命中即视为不安全载荷，不允许写出。
     * 覆盖密钥、记忆内容与工具授权三类硬约束。
     */
    val FORBIDDEN_EXPORT_KEYS = setOf(
        "api_key", "apikey", "apiKey", "authorization", "auth", "token", "access_token",
        "accessToken", "refresh_token", "refreshToken", "secret", "password", "passwd",
        "sessionKey", "session_key", "cookie", "cookies", "credential", "credentials",
        "bearer", "privateKey", "private_key", "clientSecret", "client_secret",
        "memory", "memories", "memoryChunks", "memory_chunks", "memoryContent",
        "toolToken", "tool_token", "toolAuthorization", "tool_authorization",
        "mcpToken", "mcp_token", "authToken", "auth_token",
    )

    // ---------------- 配置编解码（无损，未知字段保留） ----------------

    fun encodeConfig(config: GroupConfig): String = json.encodeToString(GroupConfigSerializer, config)

    fun decodeConfig(raw: String?): GroupConfig? {
        if (raw.isNullOrBlank()) return null
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        return decodeConfigObject(element as? JsonObject)
    }

    fun encodeConfigObject(config: GroupConfig): JsonObject = buildJsonObject {
        put("schema_version", JsonPrimitive(config.schemaVersion))
        put("mode", JsonPrimitive(config.mode))
        put("chair_role_id", config.chairRoleId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("token_budget_per_round", JsonPrimitive(config.tokenBudgetPerRound))
        put("revision", JsonPrimitive(config.revision))
        put("tie_policy", JsonPrimitive(config.tiePolicy))
        put("vote_candidates", JsonArray(config.voteCandidates.map { JsonPrimitive(it) }))
        put("roles", JsonArray(config.roles.map { encodeRole(it) }))
        // 未知字段原样带回，已知键永远由上面的结构化字段决定。
        config.extras.forEach { (key, value) ->
            if (key !in KNOWN_CONFIG_KEYS) put(key, value)
        }
    }

    fun decodeConfigObject(raw: JsonObject?): GroupConfig? {
        if (raw == null) return null
        val normalized = normalizeKeys(raw, LEGACY_CONFIG_KEYS)
        val roles = (normalized["roles"] as? JsonArray)
            ?.mapNotNull { element -> (element as? JsonObject)?.let(::decodeRole) }
            ?: return null
        return GroupConfig(
            schemaVersion = normalized.int("schema_version") ?: SCHEMA_VERSION,
            roles = roles,
            mode = normalized.string("mode") ?: MODE_PIPELINE,
            chairRoleId = normalized.string("chair_role_id"),
            tokenBudgetPerRound = normalized.int("token_budget_per_round") ?: 0,
            revision = normalized.int("revision") ?: 1,
            voteCandidates = normalized.stringList("vote_candidates"),
            tiePolicy = normalized.string("tie_policy") ?: TIE_FAIL,
            extras = JsonObject(normalized.filterKeys { it !in KNOWN_CONFIG_KEYS }),
        )
    }

    private fun encodeRole(role: GroupRole): JsonObject = buildJsonObject {
        put("role_id", JsonPrimitive(role.id))
        put("name", JsonPrimitive(role.name))
        put("assistant_id", JsonPrimitive(role.assistantId))
        put("chair", JsonPrimitive(role.chair))
        put("card_id", role.cardId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("model_id", role.modelId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("memory_space_id", role.memorySpaceId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("tool_package_ids", JsonArray(role.toolPackageIds.map { JsonPrimitive(it) }))
        put("skill_ids", JsonArray(role.skillIds.map { JsonPrimitive(it) }))
        put("mcp_server_ids", JsonArray(role.mcpServerIds.map { JsonPrimitive(it) }))
    }

    private fun decodeRole(raw: JsonObject): GroupRole? {
        val normalized = normalizeKeys(raw, LEGACY_ROLE_KEYS)
        val id = normalized.string("role_id")?.takeIf { it.isNotBlank() } ?: return null
        return GroupRole(
            id = id,
            name = normalized.string("name").orEmpty(),
            assistantId = normalized.string("assistant_id").orEmpty(),
            chair = (normalized["chair"] as? JsonPrimitive)?.booleanOrNull ?: false,
            cardId = normalized.string("card_id"),
            modelId = normalized.string("model_id"),
            memorySpaceId = normalized.string("memory_space_id"),
            toolPackageIds = normalized.stringList("tool_package_ids"),
            skillIds = normalized.stringList("skill_ids"),
            mcpServerIds = normalized.stringList("mcp_server_ids"),
        )
    }

    /** 旧键映射到契约键；两键同时存在时以契约键为准。 */
    private fun normalizeKeys(raw: JsonObject, legacy: Map<String, String>): JsonObject {
        if (legacy.keys.none { raw.containsKey(it) }) return raw
        val mapped = legacy.mapNotNull { (oldKey, newKey) ->
            val value = raw[oldKey]
            if (value != null && !raw.containsKey(newKey)) newKey to value else null
        }.toMap()
        if (mapped.isEmpty()) return raw
        return JsonObject(mapped + raw)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()

    // ---------------- 校验 ----------------

    /**
     * 字段级校验。返回空列表才允许保存。
     *
     * `conversationId` 只在需要核对角色记忆空间键时传入；为 null 时跳过该项，
     * 因为群配置本身无法得知会话 id。
     */
    fun validate(config: GroupConfig, conversationId: String? = null): List<GroupConfigError> = buildList {
        if (config.roles.isEmpty()) {
            add(GroupConfigError("roles", "至少需要一个成员"))
        }
        config.roles.forEachIndexed { index, role ->
            if (role.id.isBlank()) add(GroupConfigError("roles[$index].role_id", "role_id 不能为空"))
            if (role.assistantId.isBlank()) {
                add(GroupConfigError("roles[$index].assistant_id", "助手引用不能为空"))
            }
            if (conversationId != null && role.memorySpaceId != null) {
                val expected = memorySpaceId(conversationId, role.id)
                if (role.memorySpaceId != expected) {
                    add(GroupConfigError("roles[$index].memory_space_id", "记忆空间必须是 $expected"))
                }
            }
        }
        val duplicated = config.roles.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        if (duplicated.isNotEmpty()) {
            add(GroupConfigError("roles[].role_id", "role_id 重复：${duplicated.joinToString()}"))
        }
        if (config.mode !in SUPPORTED_MODES) {
            add(GroupConfigError("mode", "未知协作模式：${config.mode}"))
        }
        if (config.tokenBudgetPerRound <= 0) {
            add(GroupConfigError("token_budget_per_round", "每轮预算必须是正整数"))
        } else if (config.tokenBudgetPerRound > MAX_TOKEN_BUDGET_PER_ROUND) {
            add(GroupConfigError("token_budget_per_round", "每轮预算不得超过 $MAX_TOKEN_BUDGET_PER_ROUND"))
        }
        if (config.tiePolicy !in SUPPORTED_TIE_POLICIES) {
            add(GroupConfigError("tie_policy", "未知平票策略：${config.tiePolicy}"))
        }
        if (config.mode == MODE_ROUNDTABLE) {
            val chair = config.chairRoleId
            when {
                chair.isNullOrBlank() -> add(GroupConfigError("chair_role_id", "roundtable 必须指定议长"))
                chair !in config.roles.map { it.id } ->
                    add(GroupConfigError("chair_role_id", "议长不在成员列表：$chair"))
            }
        }
        if (config.revision < 1) {
            add(GroupConfigError("revision", "revision 必须为正整数"))
        }
    }

    fun isValid(config: GroupConfig, conversationId: String? = null): Boolean =
        validate(config, conversationId).isEmpty()

    // ---------------- 视角过滤 ----------------

    fun memorySpaceId(conversationId: String, roleId: String): String =
        "group:$conversationId:role:$roleId"

    /**
     * 契约口径的上下文入口：viewer 只看见自己的消息、用户消息、@自己的消息、
     * 系统/合成消息和轮次摘要；`predecessorId` 只带 pipeline 上一位的本轮输出，
     * `chairRound` 只在议长汇总时放开本轮其他角色输出。
     */
    fun buildContext(
        viewerRoleId: String,
        messages: List<UIMessage>,
        config: GroupConfig,
        predecessorId: String? = null,
        chairRound: Boolean = false,
    ): List<UIMessage> = visibleMessages(config, messages, viewerRoleId, predecessorId, chairRound)

    fun visibleMessages(
        config: GroupConfig,
        messages: List<UIMessage>,
        viewerId: String,
        predecessorId: String? = null,
        chairRound: Boolean = false,
    ): List<UIMessage> {
        val roundStart = messages.indexOfLast { it.role == MessageRole.USER }.coerceAtLeast(0)
        return messages.filterIndexed { index, message ->
            when {
                message.role == MessageRole.SYSTEM || message.isSynthetic -> true
                message.role == MessageRole.USER -> true
                message.roleId == SUMMARY_ID -> true
                message.roleId == viewerId -> true
                viewerId in message.mentionRoleIds -> true
                predecessorId != null && message.roleId == predecessorId && index >= roundStart -> true
                chairRound && message.role == MessageRole.ASSISTANT && index >= roundStart -> true
                else -> false
            }
        }
    }

    /** 记忆检索结果再过一遍同样的 viewer 口径，避免提示词注入越权内容。 */
    fun filterMemoryForViewer(
        viewerId: String,
        viewerMessageIds: Set<String>,
        sourceMessageIdOf: (String) -> String?,
        candidateMessageIds: List<String>,
    ): List<String> = candidateMessageIds.filter { id ->
        val source = sourceMessageIdOf(id)
        // 无法定位来源的记忆保留（可能来自用户显式 memory_add），能定位的必须可见。
        source == null || source in viewerMessageIds
    }

    // ---------------- 路由与轮次 ----------------

    /** 由触发消息派生轮次 id：同一触发消息的重试必然落回同一轮。 */
    fun roundIdFor(triggerMessageId: String): String = "round-$triggerMessageId"

    fun parseMentions(text: String, roles: List<GroupRole>): List<String> = roles
        .filter { role -> mentioned(text, role.name) || mentioned(text, role.id) }
        .map { it.id }
        .distinct()

    /** 严格解析候选：仅接受 `候选：a,b,c` / `CANDIDATES: a|b|c` 这类显式声明。 */
    fun parseCandidates(text: String): List<String> {
        val match = Regex(
            "(?im)^\\s*(?:候选|候选项|CANDIDATES?)\\s*[:：]\\s*(.+)$",
        ).find(text) ?: return emptyList()
        return match.groupValues[1]
            .split(',', '，', '|', '、')
            .map { it.trim().trim('-', '*', '"') }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun plan(config: GroupConfig, mentionRoleIds: List<String>): List<SpeakerStep> {
        val selected = if (mentionRoleIds.isEmpty()) {
            config.roles
        } else {
            config.roles.filter { it.id in mentionRoleIds }
        }
        if (selected.isEmpty()) return emptyList()
        return when (config.mode) {
            MODE_ROUNDTABLE -> {
                val chairId = config.chairRoleId ?: selected.firstOrNull { it.chair }?.id
                val chair = selected.firstOrNull { it.id == chairId } ?: selected.last()
                selected.filter { it.id != chair.id }.map { SpeakerStep(it) } +
                    SpeakerStep(chair, chairRound = true)
            }

            MODE_PIPELINE -> selected.mapIndexed { index, role ->
                SpeakerStep(role, predecessorId = selected.getOrNull(index - 1)?.id)
            }

            else -> selected.map { SpeakerStep(it) }
        }
    }

    fun newRound(
        triggerMessageId: String,
        config: GroupConfig,
        mentionRoleIds: List<String>,
        userText: String = "",
    ): RoundPlan = RoundPlan(
        roundId = roundIdFor(triggerMessageId),
        plan = plan(config, mentionRoleIds),
        candidates = config.voteCandidates.ifEmpty { parseCandidates(userText) },
    )

    fun pendingSpeakers(plan: List<SpeakerStep>, committedRoleIds: Set<String>): List<SpeakerStep> =
        plan.filter { it.role.id !in committedRoleIds }

    /**
     * 预算判定：达到 `tokenBudgetPerRound` 即停止剩余角色。
     * `spent` 必须是本轮 prompt + completion 累计值；下一轮重新计数。
     */
    fun budgetDecision(spent: Int, limit: Int, remainingRoleIds: List<String>): RoundBudget {
        if (remainingRoleIds.isEmpty()) return RoundBudget.Continue
        if (limit <= 0 || spent < limit) return RoundBudget.Continue
        return RoundBudget.Stop(
            spent = spent,
            limit = limit,
            skippedRoleIds = remainingRoleIds,
            reason = "token_budget_exceeded",
        )
    }

    fun roundTotalTokens(usages: List<Pair<Int, Int>>): Int =
        usages.sumOf { (prompt, completion) -> prompt + completion }

    // ---------------- 结构化投票 ----------------

    /** 只接受候选集内的 `VOTE: <candidate_id>`；其余一律视为无票，不猜。 */
    fun parseBallot(text: String, roleId: String, candidates: List<String>): VoteBallot? {
        val line = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(BALLOT_PREFIX, ignoreCase = true) }
            ?: return null
        // 前缀是忽略大小写匹配的，截断也必须同样忽略大小写：否则 `vote: opt-a` 会因为
        // removePrefix 大小写敏感而整行留在 body 里，被判成集外票静默丢弃。
        val body = line.substring(BALLOT_PREFIX.length).trim()
        val id = body.substringBefore('|').trim()
        if (id !in candidates) return null
        val reason = body.substringAfter('|', "").trim()
        return VoteBallot(roleId = roleId, candidateId = id, reason = reason)
    }

    /** 多数决。同一角色重复投票只算最后一票；平票按 `tiePolicy`。 */
    fun tally(
        ballots: List<VoteBallot>,
        candidates: List<String>,
        tiePolicy: String = TIE_FAIL,
    ): VoteOutcome {
        if (candidates.isEmpty()) return VoteOutcome.Invalid("本轮没有候选")
        val effective = ballots
            .filter { it.candidateId in candidates }
            .associateBy { it.roleId }
            .values
            .sortedBy { it.roleId }
        if (effective.isEmpty()) return VoteOutcome.Invalid("没有有效选票")
        val counts = effective.groupingBy { it.candidateId }.eachCount()
        val top = counts.values.max()
        val leaders = counts.filterValues { it == top }.keys.sorted()
        return when {
            leaders.size == 1 -> VoteOutcome.Decided(leaders.first(), counts, effective)
            tiePolicy == TIE_CHAIR -> VoteOutcome.ChairDecides(leaders, counts, effective)
            // 契约默认：平票即本轮失败，不猜、不按自由文本裁决。
            else -> VoteOutcome.Tie(leaders, counts, effective)
        }
    }

    // ---------------- 分享 / QR ----------------

    /**
     * 编码分享载荷。只写群配置与角色卡最小元数据；
     * 命中 [FORBIDDEN_EXPORT_KEYS] 的载荷直接拒绝，避免密钥/记忆外泄。
     */
    fun encodeQr(config: GroupConfig, cards: List<RoleCardMeta> = emptyList()): String {
        val payload = GroupSharePayload(config = config, cards = cards)
        val element = json.encodeToString(GroupSharePayload.serializer(), payload)
        val obj = json.parseToJsonElement(element) as JsonObject
        val offending = findForbiddenKeys(obj)
        check(offending.isEmpty()) { "分享载荷包含禁止导出的字段：$offending" }
        return element
    }

    fun decodeQr(raw: String): GroupConfig? = decodeSharePayload(raw)?.config

    fun decodeSharePayload(raw: String): GroupSharePayload? {
        if (raw.isBlank()) return null
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val obj = element as? JsonObject ?: return null
        val kind = (obj["kind"] as? JsonPrimitive)?.content ?: return null
        if (kind != QR_KIND) return null
        val config = decodeConfigObject(obj["config"] as? JsonObject) ?: return null
        val cards = (obj["cards"] as? JsonArray)?.mapNotNull { element ->
            val card = element as? JsonObject ?: return@mapNotNull null
            val roleId = (card["role_id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            RoleCardMeta(
                roleId = roleId,
                name = (card["name"] as? JsonPrimitive)?.content.orEmpty(),
                assistantId = (card["assistant_id"] as? JsonPrimitive)?.content.orEmpty(),
                cardId = (card["card_id"] as? JsonPrimitive)?.content,
                persona = (card["persona"] as? JsonPrimitive)?.content.orEmpty(),
                avatarRef = (card["avatar_ref"] as? JsonPrimitive)?.content,
            )
        }.orEmpty()
        return GroupSharePayload(
            kind = kind,
            schemaVersion = (obj["schema_version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: SCHEMA_VERSION,
            config = config,
            cards = cards,
        )
    }

    /** 深度扫描 JSON，找出任何命中禁止名单的键（忽略大小写）。 */
    fun findForbiddenKeys(element: JsonElement): Set<String> = buildSet {
        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> node.forEach { (key, value) ->
                    if (FORBIDDEN_EXPORT_KEYS.any { it.equals(key, ignoreCase = true) }) add(key)
                    walk(value)
                }

                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }
        walk(element)
    }

    // ---------------- 列表筛选 ----------------

    /** 筛选是过滤不是互斥：返回新列表，源集合不变。 */
    fun filterType(types: List<String>, filter: String): List<String> {
        if (filter.isBlank() || filter == FILTER_ALL) return types
        return types.filter { it == filter }
    }

    private fun mentioned(text: String, token: String): Boolean {
        if (token.isBlank()) return false
        val pattern = Regex("(?<![\\p{L}\\p{N}_])@${Regex.escape(token)}(?![\\p{L}\\p{N}_])")
        return pattern.containsMatchIn(text)
    }
}

object GroupConfigSerializer : KSerializer<GroupConfig> {
    override val descriptor = buildClassSerialDescriptor("heizige.kk.khatkit.app.core.data.model.GroupConfig")

    // kotlinx-serialization 1.11 起 `Encoder.encodeJsonElement` / `Decoder.decodeJsonElement`
    // 不再是顶层扩展函数，只剩 JsonEncoder / JsonDecoder 的接口成员，因此显式取 JSON 编码器。
    override fun serialize(encoder: Encoder, value: GroupConfig) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("GroupConfigSerializer 只支持 JSON 编码器，实际为 ${encoder::class.simpleName}")
        jsonEncoder.encodeJsonElement(GroupChat.encodeConfigObject(value))
    }

    override fun deserialize(decoder: Decoder): GroupConfig {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("GroupConfigSerializer 只支持 JSON 解码器，实际为 ${decoder::class.simpleName}")
        val element = jsonDecoder.decodeJsonElement()
        return GroupChat.decodeConfigObject(element as? JsonObject) ?: GroupConfig()
    }
}