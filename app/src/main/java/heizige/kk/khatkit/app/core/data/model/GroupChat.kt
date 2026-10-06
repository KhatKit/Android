package heizige.kk.khatkit.app.core.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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
    /**
     * 未知字段原样保留。角色层以前只读下面 11 个已知键，别人文档里多写的字段
     * 会在往返时静默丢失；这里兜住：读进 [extras]，写回时平铺回角色对象顶层。
     */
    @SerialName("extras")
    val extras: JsonObject = JsonObject(emptyMap()),
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

/**
 * 受闸门导入的结果（[GroupChat.importShare]）。
 *
 * 存在的理由：[decodeSharePayload] 是**裸解码器**——任何 `schema_version` 都照解，也不看密钥，
 * 直接拿来当 UI 导入入口就等于把别人文档里高版本的字段盲解进来、把带密钥的载荷吃进内存。
 * 导入路径必须走带闸门的 [GroupChat.importShare]。
 */
sealed interface GroupImportResult {
    /**
     * 校验通过。[payload] 是**完整**载荷：`config` 与角色卡元数据 `cards` 都在，
     * 导入方拿得到 [RoleCardMeta]（[decodeQr] 会把 cards 丢掉，不能用于导入）。
     */
    data class Accepted(val payload: GroupSharePayload) : GroupImportResult

    /**
     * 拒收。[reason] 是面向人的一句话原因；[fieldErrors] 是字段级明细，供 UI 逐条显示。
     * `field` 一律用契约 snake_case 键（如 `token_budget_per_round`），不是 Kotlin 属性名。
     */
    data class Rejected(
        val reason: String,
        val fieldErrors: List<GroupConfigError> = emptyList(),
    ) : GroupImportResult
}

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
     * 读库时算「已识别」的键 = 契约键 + 旧 camelCase 键。
     * [normalizeKeys] 会把旧键的值搬到契约键上但**不删旧键**，所以收 [GroupRole.extras]
     * 时必须把旧键一起排掉，否则同一份值既落进已知字段又落进 extras，等于写了两遍。
     */
    private val RESERVED_ROLE_KEYS = KNOWN_ROLE_KEYS + LEGACY_ROLE_KEYS.keys

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

    /**
     * 写库用的群配置编码。与 [encodeQr] 同一道密钥闸门：命中
     * [FORBIDDEN_EXPORT_KEYS] 的配置直接拒收。不补这道检查的话，分享路径拦得住、
     * 写库路径却照样把 `extras` 里的密钥写进 `conversation.groupConfig`。
     */
    fun encodeConfig(config: GroupConfig): String {
        val element = json.encodeToString(GroupConfigSerializer, config)
        val offending = findForbiddenKeys(json.parseToJsonElement(element))
        check(offending.isEmpty()) { "群配置包含禁止导出的字段：$offending" }
        return element
    }

    fun decodeConfig(raw: String?): GroupConfig? {
        if (raw.isNullOrBlank()) return null
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        return decodeConfigObject(element as? JsonObject)
    }

    // ---------------- 角色卡元数据落库编解码（`conversationentity.group_cards`） ----------------

    /**
     * 角色卡元数据落库用的编码，与 [encodeQr] 用**同一个** `json` 实例和同一份
     * [RoleCardMeta] 序列化器，所以这一列的 blob 与分享载荷里的 `cards` 逐字节同形，
     * 导入 → 落库 → 再导出/再分享不会因为换了一套编码而变形。
     *
     * 与 [encodeConfig] 同一道密钥闸门（命中 [FORBIDDEN_EXPORT_KEYS] 的键直接拒收）：
     * 分享路径拦得住、写库路径也必须拦得住，否则带密钥的角色卡元数据会绕过分享入口
     * 直接进库。
     *
     * ⚠️ 闸门只扫**键名**不扫值（见 [findForbiddenKeys]），因此 persona 这类自由文本
     * 不会被误伤——哪怕正文里就写着 `api_key`。卡片键名固定是
     * `role_id / name / assistant_id / card_id / persona / avatar_ref` 六个，都不在黑名单里
     * （由 `GroupRoleCardsPersistenceTest` 把这条钉死）。
     *
     * 空列表编码成 `"[]"`，与「这一列从来没有角色卡」（空串 → [decodeRoleCards] 回 null）
     * 区分得开：导入过但载荷里一张卡都没有，是一个必须能表达的状态。
     */
    fun encodeRoleCards(cards: List<RoleCardMeta>): String {
        val element = json.encodeToString(ListSerializer(RoleCardMeta.serializer()), cards)
        val offending = findForbiddenKeys(json.parseToJsonElement(element))
        check(offending.isEmpty()) { "角色卡元数据包含禁止保存的字段：$offending" }
        return element
    }

    /**
     * 角色卡元数据落库用的解码。空串 / 空白 / 非法 JSON / 不是数组一律回 `null`：
     * `null` = 「这一列没有角色卡」（Room 31 及更早版本的存量行迁移后就是空串），
     * 与「导入过、载荷里确实一张卡都没有」（`"[]"` → 空列表）不是一回事。
     * 条目级的丢弃口径复用 [decodeCards]（缺 `role_id` 的条目直接丢，不猜它属于谁）。
     */
    fun decodeRoleCards(raw: String?): List<RoleCardMeta>? {
        if (raw.isNullOrBlank()) return null
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val array = element as? JsonArray ?: return null
        return decodeCards(array)
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
        // 未知字段平铺回角色对象顶层，与 encodeConfigObject 对 config 的做法同一口径；
        // 命中已知键的以已知字段为准，一个键只写一次。
        role.extras.forEach { (key, value) ->
            if (key !in KNOWN_ROLE_KEYS) put(key, value)
        }
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
            // 归一化之后剩下的键原样保留，导出/恢复不得丢失。
            extras = JsonObject(normalized.filterKeys { it !in RESERVED_ROLE_KEYS }),
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
        // 版本闸门放最前面：未知版本不能盲解，宁可拒收也不要按 v1 语义猜字段。
        // 判定口径收敛在 [schemaVersionErrors]，importShare 与这里共用同一份实现。
        addAll(schemaVersionErrors(config.schemaVersion))
        if (config.roles.isEmpty()) {
            add(GroupConfigError("roles", "至少需要一个成员"))
        }
        config.roles.forEachIndexed { index, role ->
            if (role.id.isBlank()) add(GroupConfigError("roles[$index].role_id", "role_id 不能为空"))
            if (role.assistantId.isBlank()) {
                add(GroupConfigError("roles[$index].assistant_id", "助手引用不能为空"))
            }
            // [SUMMARY_ID] 是合成节点（投票小结 / 投票失败摘要）的保留 role_id，
            // [visibleMessages] 对它**无条件放行**给所有视角。若允许普通角色占用它，
            // 那个角色的每一条发言都会被当成合成小结广播给全群——视角隔离从配置层被绕过。
            // 收口在这里而不是 visibleMessages：导入（[importShare] 第 5 道闸门）、UI 保存
            // （`GroupConfigSheet.onSave`）、`ChatManager.createGroup` 三条路都过 [validate]，
            // 一处禁令三处生效。field 用 `roles[].role_id`（与下面重复值那条同口径：
            // 禁的是取值本身，不是某一个下标）。
            if (role.id == SUMMARY_ID) {
                add(GroupConfigError("roles[].role_id", "role_id 不能是保留值 $SUMMARY_ID"))
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

    /**
     * 版本闸门的**唯一**判定口径。[validate]（写库/保存路径）与 [importShare]（导入路径）
     * 都调它，不允许两套判断各判一次——否则迟早出现「保存拦住了、导入却放行」的漂移。
     *
     * `field` 用契约键 `schema_version`。
     */
    private fun schemaVersionErrors(schemaVersion: Int): List<GroupConfigError> = buildList {
        if (schemaVersion < 1) {
            add(GroupConfigError("schema_version", "schema_version 必须为正整数，当前 $schemaVersion"))
        } else if (schemaVersion > SCHEMA_VERSION) {
            add(
                GroupConfigError(
                    "schema_version",
                    "schema_version $schemaVersion 高于当前支持的 $SCHEMA_VERSION，拒绝盲解",
                )
            )
        }
    }

    // ---------------- 导入筛查（恢复路径） ----------------

    /**
     * 恢复路径上的一条修正记录。`field` 用契约 snake_case 键；`original` / `corrected`
     * 保留**改前改后的字面值**，调用方要能把原值显式展示给用户——悄悄改掉一个用户看得
     * 见的字段而只留一句「已修正」，等于把改写伪装成无损。
     */
    data class GroupConfigFix(
        val field: String,
        val message: String,
        val original: String,
        val corrected: String,
    )

    /**
     * 导入筛查（去重/校验）的结论。
     *
     * **两条硬不变式**（都有测试钉死，见 `GroupImportScreeningTest`）：
     * 1. [Accepted.config] 与 [Normalized.config] 都**必然通过 [validate]**——「降级」只可能
     *    把非法配置改成合法配置，绝不可能放行一份保存路径会拒绝的配置。
     * 2. [Normalized] 与 [Accepted] 之外没有第三条路：想不清楚的一律 [Rejected]。
     */
    sealed interface GroupConfigScreening {
        /** 原样通过 [validate]，`config` 就是文件里的那一份，未被改写。 */
        data class Accepted(val config: GroupConfig) : GroupConfigScreening

        /** 结构性不合法：`fieldErrors` 是字段级明细，调用方必须拒绝落库。 */
        data class Rejected(val fieldErrors: List<GroupConfigError>) : GroupConfigScreening

        /** 越界但可归一化：`config` 是归一化后的那一份（已过 [validate]），`fixes` 是修正项。 */
        data class Normalized(val config: GroupConfig, val fixes: List<GroupConfigFix>) : GroupConfigScreening
    }

    /** 去重后的角色卡与去重记录。 */
    data class CardDedupResult(
        val cards: List<RoleCardMeta>,
        val fixes: List<GroupConfigFix>,
    )

    /**
     * 只有这两个字段允许**归一化**，因为它们各自在本文件里已经有一个**确定无疑**的合法缺省值：
     * [decodeConfigObject] 把缺失的 `revision` 读成 [DEFAULT_REVISION]、把缺失/未知的
     * `tie_policy` 读成 [TIE_FAIL]，契约也把 `fail` 定为平票默认。归一化到「读的时候本来会给的
     * 值」不引入任何新信息，因此安全。
     *
     * 其余字段一律拒绝，理由逐条写在 [screenImportedConfig] 的 KDoc 里。
     */
    private val IMPORT_NORMALIZABLE_FIELDS = setOf("revision", "tie_policy")

    /** `revision` 的合法缺省值，与 [decodeConfigObject] 里 `?: 1` 同一口径。 */
    const val DEFAULT_REVISION = 1

    /**
     * 导入筛查：`validate` 的 13 条可达校验（`conversationId` 为 null 时记忆空间键那条不可达）
     * 逐条分流成「归一化」与「拒绝」。这是**纯函数**，不碰 IO，可直接单测。
     *
     * ## 为什么不统一降级
     *
     * 「降级」只在能**不臆造信息**的前提下成立。逐条理由：
     *
     * - `schema_version` 未知 → **拒绝**。版本闸门在本文件里有明文约束（[schemaVersionErrors]
     *   「不允许两套判断各判一次——否则迟早出现『保存拦住了、导入却放行』的漂移」），
     *   [importShare] 第 3 道闸门也是拒收。在这里把它改写成 1 正是制造那条漂移，且等于对
     *   高版本字段**盲解**：契约写的是「读取未知版本按兼容字段保留策略处理，不静默丢弃」。
     * - `mode` 未知 → **拒绝**。pipeline / roundtable / vote 的路由语义完全不同，默认成
     *   pipeline 会静默丢掉 `vote_candidates`，用户看不出自己导入的群被换了玩法。
     * - `token_budget_per_round` 越界 → **拒绝**。契约两处写死「预算为 0、负数或超过宿主上限
     *   时配置保存失败」，且**不存在**安全的缺省值：夹到 1 会让群聊基本发不出话，夹到上限
     *   是一次成本事故。悄悄换掉用户看不见的预算，比拒绝更糟。
     * - `chair_role_id` 缺失/不在成员里 → **拒绝**。补一个议长要臆造身份，清空则 roundtable
     *   直接失去汇总者，两条都是静默改语义。
     * - `assistant_id` 为空 → **拒绝**。引用缺失没有缺省助手可填。
     * - `role_id` 重复 / 占用 [SUMMARY_ID] / `roles` 为空 → **拒绝**。去重会**丢掉一个成员**
     *   （用户导入 3 人群静默变 2 人），SUMMARY_ID 则是视角隔离的保留值，改掉它必须连带
     *   重写每条消息的 `role_id` 与记忆空间键。这类问题必须让用户看见，不许悄悄吞掉。
     *
     * 归一化完成后**再跑一遍 [validate]**（`residual`）：仍有错就退回 [Rejected]。这条兜底让
     * 「降级不放行保存路径会拒绝的东西」成为结构性保证，而不只是一条测试。
     *
     * 注意：本函数**不改** [importShare] 的行为——分享/扫码那条生产路径仍然对上述任何一条
     * 直接拒收，本函数只服务群聊文件恢复路径。
     */
    fun screenImportedConfig(config: GroupConfig): GroupConfigScreening {
        val errors = validate(config)
        if (errors.isEmpty()) return GroupConfigScreening.Accepted(config)
        if (errors.any { it.field !in IMPORT_NORMALIZABLE_FIELDS }) {
            return GroupConfigScreening.Rejected(errors)
        }
        val normalized = config.copy(
            revision = if (errors.any { it.field == "revision" }) DEFAULT_REVISION else config.revision,
            tiePolicy = if (errors.any { it.field == "tie_policy" }) TIE_FAIL else config.tiePolicy,
        )
        val fixes = buildList {
            if (normalized.revision != config.revision) {
                add(
                    GroupConfigFix(
                        "revision",
                        "revision 不是正整数，已归一化为 ${normalized.revision}",
                        config.revision.toString(),
                        normalized.revision.toString(),
                    )
                )
            }
            if (normalized.tiePolicy != config.tiePolicy) {
                add(
                    GroupConfigFix(
                        "tie_policy",
                        "未知平票策略，已归一化为 $TIE_FAIL（契约默认）",
                        config.tiePolicy,
                        normalized.tiePolicy,
                    )
                )
            }
        }
        // 兜底：归一化后仍不合法就退回拒绝。降级只允许把非法变成合法，不允许反向。
        if (validate(normalized).isNotEmpty()) return GroupConfigScreening.Rejected(errors)
        return GroupConfigScreening.Normalized(normalized, fixes)
    }

    /**
     * 角色卡去重。**去重键是 `role_id`**，因为那才是 `cards[]` 与 `roles[]` 的连接键
     * （`roleDisplayNames` 用 `putIfAbsent(card.roleId, …)` 取显示名，也是首个优先）。
     * 同一 `role_id` 出现多条时保留**首条**并记一条修正项。
     *
     * 刻意**不**做的事：
     * - 不按 `card_id` 去重。同一张角色卡被多个角色共用是合法用法，契约从未禁止；按 `card_id`
     *   去重会凭空删掉某个成员的角色卡。
     * - 不动 `swipes`。swipe 是 [MessageNode] 的**分支身份**（`selectIndex` 是下标），
     *   两条文本相同的分支在数据模型里是两个节点，合并要重排下标并会改变再导出的字节。
     *   契约那句「导入先 schema 校验与去重」说的是建会话前的群配置与角色引用，不是消息分支。
     * - 不丢弃 `card_id` 为 null 的条目：`cardId` 可空，null 不是重复。
     *
     * `role_id` 为空的条目按 [decodeCards] 的既有口径丢弃——它无法连到任何成员，留着只会
     * 在调用方那儿变成一个查不到人的孤儿引用。
     */
    fun dedupeCards(cards: List<RoleCardMeta>): CardDedupResult {
        val fixes = mutableListOf<GroupConfigFix>()
        val kept = mutableListOf<RoleCardMeta>()
        val keptIndexByRoleId = mutableMapOf<String, Int>()
        cards.forEachIndexed { index, card ->
            if (card.roleId.isBlank()) {
                fixes += GroupConfigFix(
                    "cards[$index].role_id",
                    "缺 role_id 的角色卡条目无法连到成员，已丢弃",
                    card.roleId,
                    "（丢弃）",
                )
                return@forEachIndexed
            }
            val existing = keptIndexByRoleId[card.roleId]
            if (existing == null) {
                keptIndexByRoleId[card.roleId] = kept.size
                kept += card
                return@forEachIndexed
            }
            val keptCard = kept[existing]
            fixes += GroupConfigFix(
                "cards[$index].role_id",
                if (keptCard == card) {
                    "重复的角色卡条目已去重"
                } else {
                    "同一 role_id 出现多张角色卡，保留首条（card_id=${keptCard.cardId}）"
                },
                card.roleId,
                keptCard.roleId,
            )
        }
        return CardDedupResult(kept, fixes)
    }

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

    /**
     * 契约口径的可见集合：`viewerId` 只看见自己的消息、用户消息、@自己的消息、
     * 系统/合成消息和轮次摘要；`predecessorId` 只带 pipeline 上一位的本轮输出，
     * `chairRound` 只在议长汇总时放开本轮其他角色输出。
     *
     * [SUMMARY_ID] 那条分支**故意不看 `turnKind`**：`role_id = SUMMARY_ID` 的合成节点有两个
     * 生产来源——`GroupTurnCoordinator.voteSummaryMessage`（`TURN_VOTE_SUMMARY`）与
     * `ChatManager.voteFailureNode`（`TURN_ERROR`，即用户可见的「[投票] 本轮未能得出结论：…」）。
     * 加上 `&& turnKind == TURN_VOTE_SUMMARY` 会让投票失败节点对**所有**视角一起消失，
     * 那是拿一个可见性回归换一条本来就堵死的伪造路径。伪造路径改在 [validate] 收口：
     * 任何角色都不许把 `role_id` 取成 `SUMMARY_ID`，于是这条分支只可能落在真正的合成节点上。
     *
     * [roundStart] 的 `coerceAtLeast(0)` 同理是**有意的**「本轮为空就等于全部都属于本轮」口径，
     * 与 [GroupTurnCoordinator.roundMessages] 的 `lastUser < 0 -> messages` 一致；把它改成
     * `Int.MAX_VALUE` 在生产可达路径上没有收益（列表里没有 USER 消息时，它必然整体落在最后一轮之内）。
     */
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

    /**
     * 严格解析候选：仅接受 `候选：a,b,c` / `CANDIDATES: a|b|c` 这类显式声明，
     * 且**声明必须落在首个非空行**。
     *
     * 「首个非空行」的判据：按 [unicodeLines] 的 6 个 Unicode 行终止符切行
     * （`\n` / `\r\n` / `\r` / `\u0085` / `\u2028` / `\u2029`），取第一个 `isNotBlank()` 为真的行。
     * 空行与纯空白（只有空格/制表符）行都算「空」而被跳过，被跳过的行不参与匹配——
     * 也就是说前导空行不耽误声明算数，但声明本身必须在那第一个非空行上。
     *
     * 为什么限定首行：调用方 [newRound] 的 `userText` 只来自触发消息（最后一条 USER
     * 消息，见 `GroupTurnCoordinator.roundPlanFor`），模型正文到不了这里，所以这不是越权
     * 注入；真问题是**用户意图错位**——用户粘一段引用了别人发言的文本时，引用块里恰好有
     * 一行 `候选：…`，旧实现的 `(?im)` 让 `^` 逐行匹配，那一行会决定本轮候选集，而不是
     * 用户自己写的那行，且没有任何提示。限定首行后引用块里的声明再深也赢不了；首行有
     * 声明就只认它，首行没有就判无候选集（本轮判失败），不猜。
     *
     * `firstOrNull` 语义不变：只取一条声明，绝不跨行收集。
     *
     * **为什么这里不需要 [linesOutsideCodeFences] 那种围栏状态机**（问过、结论记在这）：
     * 判据只看 `unicodeLines(text).firstOrNull { it.isNotBlank() }`，也就是**第一个非空行**。而某一行要
     * 落在围栏**里面**，必须由**更早的一行**开围栏；第一个非空行之前只有空行，空行里藏不下围栏标记
     * （含反引号或波浪号的行不是空行）。所以「第一个非空行落在围栏内部」这个状态**根本到不了**：
     * 要么第一个非空行**就是**开围栏那行（匹配不上声明正则 → 空候选集），要么它前面没有开围栏。
     * 两条路都已经是失败关闭。对照面：`parseBallot` 扫的是**整条消息**取第一条命中，所以它结构上
     * **是**暴露的——这正是它那边要上状态机的原因。两条路径的暴露面不同，这就是处置不同的全部理由。
     *
     * ⚠️ 唯一**真的**漏到这里的形状是**闭围栏那一行自己**带着声明（`~~~ 候选：a,b ~~~`）——所以下面
     * 只加一条「声明行自己不能是围栏标记」的窄守卫。它够不到围栏**内部**，但堵住了「围栏分隔行被
     * 当正文读」这个与 `parseBallot` **完全同类**的假阳性；跑完整状态机会是死代码。
     */
    fun parseCandidates(text: String): List<String> {
        // 正则只作用在**首个非空行**这一行上。不能改成 `(?i)\A\s*`——`\s` 含 `\n`，
        // `\A\s*` 会跨过空行把后面某一行的声明照样捞进来，等于没改。
        // 行从 [unicodeLines] 来（6 个 Unicode 行终止符，与 `Pattern` 对齐），与
        // [linesOutsideCodeFences] **共用**同一套切分——否则两处口径漂移，
        // 「切分行」与「正则行终止符」又会不对称（那正是本方法修掉的缺陷）。
        val declaration = unicodeLines(text).firstOrNull { it.isNotBlank() } ?: return emptyList()
        val trimmed = declaration.trim()
        // 声明行自己就是围栏标记 → 不是声明（`~~~ 候选：a,b ~~~` 修前会被收成 `[a, b]`）。
        // `CODE_FENCE_MARKER` 是 `^` 锚定的，所以这条判据就是「这一行以围栏标记开头」。
        if (CODE_FENCE_MARKER.containsMatchIn(trimmed)) return emptyList()
        val match = Regex(
            "^(?:候选|候选项|CANDIDATES?)\\s*[:：]\\s*(.+)$",
            RegexOption.IGNORE_CASE,
        ).find(trimmed) ?: return emptyList()
        return match.groupValues[1]
            .split(',', '，', '|', '、')
            .map(::normalizeCandidateId)
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * 单个候选 id 的规范化：剥**成对**的包裹符号，再剥**列表符号**前缀/后缀。
     *
     * 为什么不再用 `String.trim('-', '*', '"')`：那是**按字符集 trim、遇非集合字符即停**，
     * 所以 `- **a**` 只会去掉末尾的 `*`，留下 `" **a"` 这种带前导空格和残缺星号的 id，
     * 于是「按 markdown 列表声明候选」这条路永远配不上票面。
     *
     * 口径（重复应用到不再变化为止；每轮都严格变短，必然终止）：
     *  1. 去首尾空白（沿用旧行为）。
     *  2. 剥**成对**包裹：前后同为 `*` / `"` / `'`，剥完非空才剥（`**x**`、`*x*`、
     *     `"x"`、`'x'` → `x`）。不成对就不动。
     *  3. 剥列表符号：前缀处连续的 `-` / `*` / `+`（`-a` 与 `- a` 都算），
     *     后缀处连续的 `-` / `*`。
     *
     * 会不会引入新的歧义：**会，但都小于现状**，逐条说清：
     *  - 步骤 3 与旧实现的字符集 trim 同源——以 `-` / `*` 开头的 id 旧实现照样把首字符
     *    吃掉，所以没有新增「本来能写、现在写不了」的 id。
     *  - 步骤 2 是**新增**的：`"a"`、`'a'` 现在能规范化成 `a`（旧实现只能剥 `"`、
     *    处理不了 `'`）。代价是字面就叫 `"a"` 的 id 无法再用这条路径表达；而票面
     *    （`VOTE:` 行）比的是裸 id，两边口径一致，不产生「声明 a、票投 "a"」的错配。
     *  - **不成对**的引号（`"a`）现在原样保留（旧实现会静默补成 `a`）。这是刻意收紧，
     *    与「严格解析」一致：宁可让畸形 id 配不上票，也不静默改写用户写的东西。
     *  - `_` / `__` **有意不剥**：`_` 在 snake_case id 里出现得远比 markdown 下划线强调
     *    频繁，剥它会把 `_private_` 这类合法 id 改成 `private`。`__a__` 因此仍规范化为
     *    `__a__`，这是已知且刻意保留的边界。
     */
    private fun normalizeCandidateId(raw: String): String {
        var id = raw.trim()
        while (true) {
            val before = id
            // 成对包裹：前后同为一种符号、剥完非空才剥。
            if (id.length >= 2) {
                val head = id.first()
                val tail = id.last()
                if (head == tail && (head == '*' || head == '"' || head == '\'')) {
                    val inner = id.substring(1, id.length - 1).trim()
                    if (inner.isNotEmpty()) id = inner
                }
            }
            // 列表符号前缀（可连续）与后缀。
            id = id.trimStart('-', '*', '+').trimEnd('-', '*').trim()
            if (id == before) return id
        }
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

    /**
     * 只接受候选集内的 `VOTE: <candidate_id>`；其余一律视为无票，不猜。
     *
     * 扫描范围是**围栏代码块之外**的全部行（见 [linesOutsideCodeFences]）。修前扫的是整条消息，
     * 于是 markdown 代码块里独占一行的**示例** `VOTE: opt-a` 会被当成真票；更糟的形状是
     * `firstOrNull` 取**第一条**命中，示例行排在前面时会把模型后文里**真的投了**的那票顶替掉——
     * 群里就这么记下了一票它没投过的选择。群聊 prompt 本来就要把投票格式告诉模型
     * （`候选：` / `VOTE: <id>`），模型**很可能照抄格式示例**，所以这不是学术问题。
     *
     * **不改**的两处：行内出现（`我选 VOTE: opt-b`）与行内代码（`` `VOTE: <id>` ``）本来就不满足
     * `startsWith`，照旧不算票——没有为它们加任何规则。
     */
    fun parseBallot(text: String, roleId: String, candidates: List<String>): VoteBallot? {
        val line = linesOutsideCodeFences(text)
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

    /**
     * markdown 围栏标记行：**3 个及以上**连续的 ` 或 ~，其后允许跟 info string
     * （` ```text ` / ` ```python `）。`^` 锚定，所以 `containsMatchIn` 的含义就是「该行以围栏标记开头」。
     */
    private val CODE_FENCE_MARKER = Regex("^(`{3,}|~{3,})(.*)$")

    /**
     * 按 **Unicode 行终止符**切行（[unicodeLines]），供 [parseCandidates] 与
     * [linesOutsideCodeFences] **共用**——这两处若各写各的切分，口径必然漂移。
     *
     * 分隔符集合 = **6 个**，与 Java `Pattern` 的行终止符集合逐条对齐：
     * `\n`(LF) / `\r\n`(CRLF) / `\r`(CR) / `\u0085`(NEL) / `\u2028`(LS) / `\u2029`(PS)。
     *
     * 依据（`kotlinc` 探针实测，输出见汇报与 `Pattern` 的 `(?m)` / `(?s)` 反证）：
     *  - Java `Pattern` 默认把 `.` 视为**不匹配行终止符**，而它的行终止符集合**正是这 6 个**。
     *    实测 `(?s)候选：a.b` 能跨 `\u0085` / `\u2028` / `\u2029` 命中（说明三者本来都是默认
     *    `.` 的行终止符），`(?m)^b$` 也能在 `a<sep>b` 上命中（说明三者都被 `(?m)` 当行终止符）。
     *  - 所以**必须**认这 6 个：声明行正则 `^(?:候选|…)\\s*[:：]\\s*(.+)$` 的 `.` 跨不过行终止符、
     *    `$` 又只锚行尾，模型一旦输出 `候选：a\u2028b`，整条正则**必然失配**。
     *  - 而 Kotlin 的 `String.lineSequence()` / `String.lines()` **只切** `\n` / `\r\n` / `\r`，
     *    `\u0085` / `\u2028` / `\u2029` 三个**都当普通字符**（实测三者切出来仍是 1 行）。
     *    ⇒ 「切分行」与「正则行终止符」口径不对称，就是本 helper 要消灭的那个缺陷。
     *
     * 刻意**不**用现成 API 蒙混：`String.lines()` 与 `lineSequence()` 口径**完全一样**
     * （都不切上面那 4 个），用它等于没修。
     *
     * 手写扫描而不是 `Regex("\\R")`：`\R` 虽然 6 个全切，但它会把连续行终止符折叠成**空串**元素
     * 混进行列（`"a\n\nb"` → 3 段含一个空串），还得再 `filter` 一次才能回到「空行存在」的语义。
     *
     * 空行语义保持不变：连续分隔符之间仍然产生**空行**元素（`"a\n\nb"` → `["a","","b"]`），
     * 所以 [parseCandidates] 的 `firstOrNull { it.isNotBlank() }` 与 [linesOutsideCodeFences]
     * 的逐行 `trim()` 行为都**一字未改**。
     *
     * ⚠️ 与 `trim()` 的一处**已知不一致**（有意保留，护栏见测试
     * `a ballot with a leading unicode separator is no longer accepted`）：
     * Kotlin 的 `Char.isWhitespace('\u2028')` / `('\u2029')` = **true**（实测，二者的 Unicode
     * 类别是 13/14 = SPACE_SEPARATOR），所以 `trim()` 会**剥掉**它们；但
     * `Char.isWhitespace('\u0085')` = **false**（类别 15 = CONTROL），`trim()` 不剥。
     * 于是 `VOTE: \u2028opt-a` 修**前**靠 `trim()` 巧合地解析成 `opt-a`、修**后**因为
     * `\u2028` 成了行终止符而变成「一条空 body 的票行」→ 集外 → `null`。这是**有意的收紧**
     * （行边界必须与正则一致；宁可让畸形 id 配不上票，也不静默改写用户写的东西），
     * 不是回归事故。`\u0085` 方向相反：它修**前**让 id 变成 `\u0085opt-a` 而静默丢票，
     * 修**后**才解析对。
     */
    private fun unicodeLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = ArrayList<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            // `\r\n` 是一个分隔符（吃掉两个字符），不是两个分隔符。
            if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') {
                lines.add(current.toString())
                current.setLength(0)
                i += 2
                continue
            }
            if (c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029') {
                lines.add(current.toString())
                current.setLength(0)
                i++
                continue
            }
            current.append(c)
            i++
        }
        // 末尾分隔符不产生**额外**的空行元素——`"a\n"` 是 1 行，与 `lineSequence()` 一致。
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    /**
     * 只返回**围栏代码块之外**的行（逐行 `trim()`）；围栏内的行、以及开/闭围栏行本身都丢掉。
     *
     * 存在的理由见 [parseBallot] 的 KDoc。口径逐条说清（含代价）：
     *  - **闭合**条件（CommonMark）：与开围栏**同字符**、**不短于**开围栏、其后**只有空白**。
     *    所以 `` ```python `` 只是块内容而不是闭围栏，4 个反引号开的块闭不掉在 3 个上。
     *  - **开围栏**条件故意比 CommonMark **松**（后者只容许 3 个以内前导缩进，且反引号 info string
     *    里不许再出现反引号）：票面匹配本来就逐行 `trim()`，若围栏判据更严，就会出现「票照样收、
     *    块里的示例照样收」的自相矛盾。判严的收益只是少跳过一点代码，代价却是两条路都可能命中，
     *    收益不抵代价。
     *  - **未闭合的围栏按「一直开到文本结尾」处理，即失败关闭（fail-closed）**。选它的理由写在
     *    `an unclosed code fence swallows the rest of the message fail closed` 那条用例的 KDoc 里：
     *    少一票会落到 [VoteOutcome.Invalid]「没有有效选票」这种响亮失败，而多一票可能被算成某个
     *    角色的真票并产出错误胜者，且链路上没有任何一处还能把它认出来。
     *  - **行从 [unicodeLines] 来**（6 个 Unicode 行终止符，与 `Pattern` 对齐）：围栏开/闭判定
     *    本身**一字未改**，只改了「行从哪来」。修前 `\u2028` 分隔的围栏整段被当成**一行**，
     *    开围栏那行 `group(2)` 不空于是把后文全吞了，围栏因此在 Unicode 分隔符下**形同失效**。
     *  - **刻意不处理缩进代码块（四空格）与引用块（`>`）**：它们同样可能藏着示例，但补进去要再定一
     *    整套规则，缩进代码块还要求「前一行是空行」，判据更脆。本轮只处理围栏——围栏是模型贴格式
     *    示例时用得最多、也是漏票代价最高的那种。
     */
    private fun linesOutsideCodeFences(text: String): List<String> = buildList {
        // null = 当前不在围栏里；非 null = 围栏字符（` 或 ~）。
        var openChar: Char? = null
        var openLength = 0
        for (raw in unicodeLines(text)) {
            val line = raw.trim()
            val fence = CODE_FENCE_MARKER.find(line)
            val marker = fence?.groupValues?.get(1)
            if (openChar == null) {
                if (marker == null) {
                    add(line)
                } else {
                    openChar = marker[0]
                    openLength = marker.length
                }
            } else if (marker != null && marker[0] == openChar &&
                marker.length >= openLength && fence.groupValues[2].isBlank()
            ) {
                openChar = null
                openLength = 0
            }
            // 围栏内的其它行、以及开/闭围栏行本身，都不进正文。
        }
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

    /**
     * 只要群配置的便捷解码，等价于 `decodeSharePayload(raw)?.config`。
     *
     * ⚠️ **UI 导入路径禁止直接调用本函数**，请改用 [importShare]。本函数：
     * - 不校验 `schema_version`（任何版本号都照解，别人文档里高版本的字段会被盲解进来）；
     * - 不跑 [FORBIDDEN_EXPORT_KEYS] 密钥黑名单（带密钥的载荷会被吃进内存）；
     * - **不返回 `cards`**，载荷里的角色卡元数据在这里被直接丢掉，导入方拿不到 [RoleCardMeta]。
     *
     * 保留它是为了已有调用与纯单元测试，行为一律不变。
     */
    fun decodeQr(raw: String): GroupConfig? = decodeSharePayload(raw)?.config

    fun decodeSharePayload(raw: String): GroupSharePayload? {
        if (raw.isBlank()) return null
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val obj = element as? JsonObject ?: return null
        val kind = (obj["kind"] as? JsonPrimitive)?.content ?: return null
        if (kind != QR_KIND) return null
        val config = decodeConfigObject(obj["config"] as? JsonObject) ?: return null
        return GroupSharePayload(
            kind = kind,
            schemaVersion = (obj["schema_version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: SCHEMA_VERSION,
            config = config,
            cards = decodeCards(obj["cards"]),
        )
    }

    /**
     * 载荷里的角色卡元数据。缺 `role_id` 的条目直接丢掉，不猜它属于谁。
     *
     * 取值一律走 [string] 而不是裸 `content`：`JsonNull` 本身也是 `JsonPrimitive`
     * 且 `content == "null"`，裸取会把 `encodeQr` 写出的 `card_id: null` 读成字符串
     * `"null"`，再导出一次就把这个假引用带进下一份载荷。
     */
    private fun decodeCards(raw: JsonElement?): List<RoleCardMeta> =
        (raw as? JsonArray)?.mapNotNull { element ->
            val card = element as? JsonObject ?: return@mapNotNull null
            val roleId = card.string("role_id") ?: return@mapNotNull null
            RoleCardMeta(
                roleId = roleId,
                name = card.string("name").orEmpty(),
                assistantId = card.string("assistant_id").orEmpty(),
                cardId = card.string("card_id"),
                persona = card.string("persona").orEmpty(),
                avatarRef = card.string("avatar_ref"),
            )
        }.orEmpty()

    /**
     * **受闸门的导入入口。UI 导入路径（扫码、粘贴、文件）只允许调本函数。**
     *
     * 依次过五道闸门，任一不过立刻返回 [GroupImportResult.Rejected]，不会把不安全的数据
     * 带进内存、更不会交给上层写库：
     *
     * 1. 空串 / JSON 解析失败 / 不是 JSON 对象 → 「无法解析分享载荷：…」
     * 2. `kind != QR_KIND` → 「不是 KhatKit 群聊分享载荷」（不接受任意 JSON）
     * 3. `schema_version` 闸门：`< 1` 或 `> SCHEMA_VERSION` 一律拒收，reason 写明
     *    「载荷版本 X，当前只支持到 Y」。判定复用 [schemaVersionErrors]，与 [validate] 同一份实现。
     * 4. 密钥黑名单：对**整个原始 JSON 元素**跑 [findForbiddenKeys]（含 `config.extras`、
     *    `roles[].extras`、顶层自定义键与 `cards`，递归下钻），命中即拒收并把键名写进 reason。
     *    先于解 `config`，避免带密钥的载荷被解进内存。
     * 5. `config` 解不出来 → 拒收；解出来后再跑 [validate]，非空则返回
     *    reason =「群配置校验未通过」+ 完整 fieldErrors，供 UI 逐条显示。
     *
     * 只有 [GroupImportResult.Accepted] 才允许写库。返回的 [GroupSharePayload] 里 `cards`
     * 完整保留角色卡元数据（[decodeQr] 会丢掉它们，因此不能用于导入）。
     *
     * @param conversationId 传入后会额外核对 `memory_space_id` 是否等于派生值
     *   `group:<conversationId>:role:<roleId>`；为 null 时跳过该项（群配置本身得知不到会话 id）。
     */
    fun importShare(raw: String, conversationId: String? = null): GroupImportResult {
        if (raw.isBlank()) {
            return GroupImportResult.Rejected("无法解析分享载荷：内容为空")
        }
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            ?: return GroupImportResult.Rejected("无法解析分享载荷：不是合法 JSON")
        val obj = element as? JsonObject
            ?: return GroupImportResult.Rejected("无法解析分享载荷：不是 JSON 对象")

        // 闸门 2：只认自家载荷，不接受任意 JSON。
        val kind = (obj["kind"] as? JsonPrimitive)?.content
        if (kind != QR_KIND) {
            return GroupImportResult.Rejected("不是 KhatKit 群聊分享载荷")
        }

        // 闸门 3：版本闸门。缺 schema_version 视为无法判断版本，不猜。
        val schemaVersion = (obj["schema_version"] as? JsonPrimitive)?.content?.toIntOrNull()
        if (schemaVersion == null) {
            return GroupImportResult.Rejected(
                "无法解析分享载荷：缺少 schema_version",
                listOf(GroupConfigError("schema_version", "缺少 schema_version，无法判断载荷版本")),
            )
        }
        val versionErrors = schemaVersionErrors(schemaVersion)
        if (versionErrors.isNotEmpty()) {
            return GroupImportResult.Rejected(
                "载荷版本 $schemaVersion，当前只支持到 $SCHEMA_VERSION",
                versionErrors,
            )
        }

        // 闸门 4：密钥黑名单，跑整个原始元素而不是解出来的 config——
        // 解码会丢掉未知结构，先扫后解才不会漏。
        val offending = findForbiddenKeys(obj)
        if (offending.isNotEmpty()) {
            return GroupImportResult.Rejected("载荷包含禁止导入的字段：${offending.joinToString()}")
        }

        // 闸门 5：解 config + 字段级校验。
        val config = decodeConfigObject(obj["config"] as? JsonObject)
            ?: return GroupImportResult.Rejected("无法解析分享载荷：缺少或无法解析 config")
        val configErrors = validate(config, conversationId)
        if (configErrors.isNotEmpty()) {
            return GroupImportResult.Rejected("群配置校验未通过", configErrors)
        }

        return GroupImportResult.Accepted(
            GroupSharePayload(
                kind = kind,
                schemaVersion = schemaVersion,
                config = config,
                cards = decodeCards(obj["cards"]),
            ),
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