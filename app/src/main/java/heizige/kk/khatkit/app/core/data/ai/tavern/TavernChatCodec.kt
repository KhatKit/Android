package heizige.kk.khatkit.app.core.data.ai.tavern

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupConfigError
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import heizige.kk.khatkit.app.feature.chat.GroupSpeakerResolver
import heizige.kk.khatkit.app.feature.chat.GroupTurnCoordinator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * SillyTavern chat files are JSONL: the first line is metadata without `mes`,
 * and each later line is one message. A JSON array with the same objects is also accepted.
 * Swipes map onto [MessageNode] branches. Fields outside this subset are copied back.
 *
 * 群聊（[exportGroup] / [importGroup]）在上面的子集上加一层：C1 契约字段
 * `role_id` / `round_id` / `turn_kind` / `mention_role_ids` 逐条消息平铺，群配置与角色卡
 * 收在顶层 [GROUP_FIELD] 里。
 *
 * 字段命名的依据（SillyTavern `release` @ `06bde939`，AGPL-3.0 同协议可参照，只按行为对齐）：
 *
 * - `name` / `is_user` / `is_system` / `mes` / `swipes` / `swipe_id` / `user_name` /
 *   `character_name` / `create_date` / `spec` 都是酒馆聊天文件的真实字段，沿用。
 * - 群聊文件首行放 `chat_metadata`：`public/scripts/group-chats.js:268,272` 读
 *   `data[0].chat_metadata`，L272 只在首行**带这个键**时才 `shift()` 掉表头，
 *   否则整行会被当成一条消息渲染。所以群聊导出必须带它，带了酒馆也才认这是群聊文件。
 * - 群名与成员名单**不在**聊天文件里：`/api/chats/group/save` 把它们存在服务端的
 *   `groups/<id>.json`（`{id, name, avatar, members, chats}`），该 SHA 的聊天文件
 *   没有 `group_name` / `character_names` 字段。因此这两项落在私有命名空间
 *   [GROUP_FIELD] 与 [GROUP_CHARACTER_NAMES_FIELD]，不借用酒馆字段名，免得臆造。
 * - 消息级 `role_id` / `round_id` / `turn_kind` / `mention_role_ids` 是 C1 契约键，
 *   酒馆不解释但会原样保留在消息对象里；它们也是 KhatKit 自己的落库键，沿用同一拼写。
 */
object TavernChatCodec {
    private val json = Json { encodeDefaults = false }

    /**
     * 角色卡元数据专用编解码：`encodeDefaults` 保证默认字段显式写出（形状稳定），
     * `explicitNulls = false` 让 null 字段整个键消失——否则 `JsonNull` 会被
     * `(x as? JsonPrimitive).content` 读成字符串 `"null"`。
     */
    private val cardJson = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    /** 顶层群配置字段。私有命名空间，不与酒馆任何字段重名。 */
    const val GROUP_FIELD = "khatkit_group"

    /** 群成员显示名（按发言顺序）。酒馆聊天文件里没有对应字段，见类注释。 */
    const val GROUP_CHARACTER_NAMES_FIELD = "khatkit_character_names"

    /** 消息级契约键，与 [UIMessage] 的群聊字段一一对应。 */
    const val FIELD_ROLE_ID = "role_id"

    const val FIELD_ROUND_ID = "round_id"
    const val FIELD_TURN_KIND = "turn_kind"
    const val FIELD_MENTION_ROLE_IDS = "mention_role_ids"

    fun import(raw: String): TavernChatDocument {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            throw IllegalArgumentException("unsupported_chat")
        }
        if (!trimmed.startsWith("[")) {
            val asObject = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
            val wrapped = asObject?.get("messages")?.jsonArray
            if (asObject != null && wrapped != null) {
                return importObjects(listOf(asObject) + wrapped.map { it.jsonObject })
            }
            return importLines(trimmed.lineSequence().filter { it.isNotBlank() }.toList())
        }
        val root = json.parseToJsonElement(trimmed)
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root["messages"]?.jsonArray ?: throw IllegalArgumentException("missing_messages")
            else -> throw IllegalArgumentException("unsupported_chat")
        }
        if (array.isEmpty()) return TavernChatDocument(JsonObject(emptyMap()), emptyList())
        val first = array.first().jsonObject
        val header = if (first["mes"] == null && first["swipes"] == null) first else JsonObject(emptyMap())
        val messages = if (header.isEmpty()) array else JsonArray(array.drop(1))
        // 消息解码只此一份（`documentFrom`）。本函数此前逐字抄了一份
        // `swipes` / `swipe_id` / `is_system` / `is_user` → MessageNode 的解析，那是个定时
        // 炸弹：JSONL / `{messages:[…]}` 包装两条入口走的是 `documentFrom`（经
        // `importLines` / `importObjects`），数组入口走的是这份副本，而 [importGroup] 读的是
        // **数组入口**。也就是说群聊导入与单聊导入读的是同一批酒馆字段、却走两份代码，任何一
        // 侧改了 role 判定或 swipe 夹取，另一侧不会跟着变 —— 同一份文件按入口不同解出不同的
        // `role` / `selectIndex`，且没有任何编译期信号。护栏见
        // `TavernChatMessageDecodeGuardTest`。
        return documentFrom(header, messages.map { it.jsonObject })
    }

    fun export(document: TavernChatDocument): String {
        val messages = document.messages.map { message ->
            val swipes = message.node.messages.map { it.toText() }
            if (swipes.isEmpty()) return@map message.raw
            val selectedIndex = message.node.selectIndex.coerceIn(0, swipes.lastIndex)
            val selected = message.node.messages[selectedIndex]
            val updated = message.raw.toMutableMap()
            updated["name"] = JsonPrimitive(message.raw.string("name") ?: selected.role.name)
            updated["is_user"] = JsonPrimitive(selected.role == MessageRole.USER)
            updated["is_system"] = JsonPrimitive(selected.role == MessageRole.SYSTEM)
            updated["mes"] = JsonPrimitive(selected.toText())
            updated["swipes"] = JsonArray(swipes.map { JsonPrimitive(it) })
            updated["swipe_id"] = JsonPrimitive(selectedIndex)
            JsonObject(updated)
        }
        val header = document.header.ifEmpty {
            buildJsonObject {
                put("spec", "st_chat_v1")
            }
        }
        return json.encodeToString(JsonElement.serializer(), JsonArray(listOf(header) + messages))
    }

    /** One JSON object per line, the form SillyTavern writes under `chats/`. */
    fun exportJsonl(document: TavernChatDocument): String {
        val array = json.parseToJsonElement(export(document)).jsonArray
        return array.joinToString("\n") { json.encodeToString(JsonElement.serializer(), it) }
    }

    private fun importLines(lines: List<String>): TavernChatDocument {
        if (lines.isEmpty()) return TavernChatDocument(JsonObject(emptyMap()), emptyList())
        val objects = lines.map { json.parseToJsonElement(it).jsonObject }
        return importObjects(objects)
    }

    private fun importObjects(objects: List<JsonObject>): TavernChatDocument {
        if (objects.isEmpty()) return TavernChatDocument(JsonObject(emptyMap()), emptyList())
        val header = if (objects.first()["mes"] == null && objects.first()["swipes"] == null) {
            objects.first()
        } else {
            JsonObject(emptyMap())
        }
        val messages = if (header.isEmpty()) objects else objects.drop(1)
        return documentFrom(header, messages)
    }

    private fun documentFrom(header: JsonObject, messages: List<JsonObject>): TavernChatDocument =
        TavernChatDocument(
            header = header,
            messages = messages.map { obj ->
                val swipes = obj["swipes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.ifEmpty { null }
                    ?: listOf(obj.string("mes").orEmpty())
                val selected = obj["swipe_id"]?.jsonPrimitive?.intOrNull?.coerceIn(0, swipes.lastIndex) ?: 0
                val role = when {
                    obj["is_system"]?.jsonPrimitive?.booleanOrNull == true -> MessageRole.SYSTEM
                    obj["is_user"]?.jsonPrimitive?.booleanOrNull == true -> MessageRole.USER
                    else -> MessageRole.ASSISTANT
                }
                TavernChatMessage(
                    node = MessageNode(
                        messages = swipes.map { text -> UIMessage.of(role, text) },
                        selectIndex = selected,
                    ),
                    raw = obj,
                )
            },
        )

    fun exportNodes(
        nodes: List<MessageNode>,
        userName: String,
        characterName: String,
    ): String = export(
        TavernChatDocument(
            header = buildJsonObject {
                put("spec", "st_chat_v1")
                put("user_name", userName)
                put("character_name", characterName)
            },
            messages = nodes.map { node ->
                val role = node.messages.firstOrNull()?.role
                TavernChatMessage(
                    node = node,
                    raw = buildJsonObject {
                        put("name", if (role == MessageRole.USER) userName else characterName)
                    },
                )
            },
        )
    )

    // ---------------- 群聊 ----------------

    /**
     * 群聊导出：酒馆能打开的群聊 JSON（数组形态，与 [exportNodes] 同族）。
     *
     * 相对 [exportNodes] 的单角色语义，这里把「谁在说」拆成四个契约字段而不是塞进
     * `name`：`role_id` 是发言者、`round_id` 是轮次、`turn_kind` 是发言类型、
     * `mention_role_ids` 是被 @ 的角色。`name` 只负责显示，取 `GroupRole.name`，
     * 空名回落到 role id，再回落到群名。
     *
     * 群配置与角色卡最小元数据（[RoleCardMeta] 六个字段）收在顶层 [GROUP_FIELD]，
     * 成员显示名单收在 [GROUP_CHARACTER_NAMES_FIELD]。
     *
     * **确定性**：不写时间戳、不写消息 id、不写任何随机值，`createDate` 由调用方显式传入
     * （默认不写）。同样的输入连跑两次字节完全相同，可以直接算 SHA-256。
     *
     * 密钥闸门：整份导出再扫一遍 [GroupChat.findForbiddenKeys]，命中即抛异常，与
     * [GroupChat.encodeQr] 同一口径。因此 [GroupConfig.extras] 里塞了 `api_key` 之类的键
     * 时这里写不出去。
     *
     * UI 入口（本子包不接）：`ConversationExport.kt` 的 `ChatExportSheet` 里，
     * `conversation.groupConfig != null` 时加一个「Tavern 群聊」选项调本函数，
     * `selectedMessages` 反查成 [MessageNode] 后传进来。
     */
    fun exportGroup(
        nodes: List<MessageNode>,
        config: GroupConfig,
        cards: List<RoleCardMeta> = emptyList(),
        userName: String,
        groupName: String,
        createDate: String? = null,
    ): String = json.encodeToString(
        JsonElement.serializer(),
        groupArray(nodes, config, cards, userName, groupName, createDate),
    )

    /** 群聊导出的 JSONL 形态，即酒馆写在 `groupChats/<id>.jsonl` 下的原样。 */
    fun exportGroupJsonl(
        nodes: List<MessageNode>,
        config: GroupConfig,
        cards: List<RoleCardMeta> = emptyList(),
        userName: String,
        groupName: String,
        createDate: String? = null,
    ): String = groupArray(nodes, config, cards, userName, groupName, createDate)
        .joinToString("\n") { json.encodeToString(JsonElement.serializer(), it) }

    /**
     * 群聊导入。只认带 [GROUP_FIELD] 的导出：不是 KhatKit 群聊导出（含解析失败）一律返回
     * null，语义与 [GroupChat.decodeQr] 一致，不猜。
     *
     * 消息复用 [import] 的解析，因此 `mes` / `swipes` / `swipe_id` / `is_user` / `is_system`
     * 的读法与单聊完全一致，四个契约字段再从原对象上取回并写回 [UIMessage]，因此
     * `exportGroup` → `importGroup` 之后 `role_id` / `round_id` / `turn_kind` /
     * `mention_role_ids` / 群配置 / 角色卡元数据都相等。
     *
     * ## 契约要求的「先 schema 校验与去重，再创建新 conversation」
     *
     * 这一句以前是空的：本函数只把 `config` 解出来就交出去，从不跑 [GroupChat.validate]，
     * 也不做任何去重，于是同一句「导入群配置」的语义在仓库里有两条口径——分享/扫码那条
     * （[GroupChat.importShare] 第 5 道闸门）校验并拒收，**本条不校验**。调用方拿到一份
     * 结构上就建不出群的配置（比如两个角色共用一个 `role_id`），后果是造出一个视角过滤
     * 从配置层就被绕过的群。现在两道闸门都补在这里：
     *
     * 1. **密钥黑名单**：对整个原始 [GROUP_FIELD] 跑 [GroupChat.findForbiddenKeys]。恢复路径
     *    同样是「把别人写的字节吃进内存」这一侧，与 [GroupChat.importShare] 第 4 道闸门同
     *    一口径，且**先扫后解**——解码会丢掉未知结构，扫解完的对象就漏了。
     * 2. **去重 + 筛查**：[GroupChat.dedupeCards] 按 `role_id` 折叠角色卡，
     *    [GroupChat.screenImportedConfig] 跑 [GroupChat.validate] 并把结论落到 [importReport]。
     *
     * ## 「不留下半成品会话」怎么落到返回值上
     *
     * 坏配置一律**不交出去**：`config` 为 null，同时 [importReport] 带上字段级错误。
     * 但消息、群名、角色卡照常返回——契约要的是「别造出半个群」，不是「把好好的聊天记录
     * 一起扔了」。因此 [config] == null 有两种原因，**必须和 [importReport] 一起读**：
     * 文件里本来就没有群配置块（纯酒馆群聊，不是错误），或者配置被判掉。
     *
     * 去重与归一化都只在「这份文件本身非法」时才动手，所以合法文件的往返逐字节不变：
     * [exportGroup] → [importGroup] → [exportGroup] 仍然完全相同。
     */
    fun importGroup(raw: String): TavernGroupChatDocument? {
        val document = runCatching { import(raw) }.getOrNull() ?: return null
        val payload = document.header[GROUP_FIELD] as? JsonObject ?: return null

        val decodedCards = (payload["cards"] as? JsonArray).orEmpty().mapNotNull { element ->
            val card = element as? JsonObject ?: return@mapNotNull null
            runCatching { cardJson.decodeFromJsonElement(RoleCardMeta.serializer(), card) }
                .getOrNull()
        }
        val deduped = GroupChat.dedupeCards(decodedCards)
        val cardFixes = deduped.fixes

        // 闸门 1：密钥 / 隐私记忆 / 工具授权 token，跑原始 JSON 而不是解出来的对象。
        val offending = GroupChat.findForbiddenKeys(payload)
        // 「有没有配置块」与「配置块解不解得出来」必须分开判：`config` 键整个不存在（纯酒馆
        // 群聊文件）不是错误，而键在却解不出来（缺 `roles`、类型不对）是坏文件。两者都让
        // `config` 变成 null，报告是唯一能把它们分开的东西。
        val rawConfig = payload["config"]
        val hasConfigBlock = rawConfig != null && rawConfig !is JsonNull
        val decodedConfig = GroupChat.decodeConfigObject(rawConfig as? JsonObject)
        val config: GroupConfig?
        val report: GroupImportReport
        when {
            offending.isNotEmpty() -> {
                config = null
                report = GroupImportReport.Rejected(
                    reason = "群聊载荷包含禁止导入的字段：${offending.joinToString()}",
                    fieldErrors = offending.sorted().map {
                        GroupConfigError(it, "禁止导入的字段")
                    },
                )
            }

            !hasConfigBlock -> {
                // 文件里根本没有群配置块。纯酒馆群聊文件属于这一类，不是错误。
                config = null
                report = GroupImportReport.NoConfig
            }

            decodedConfig == null -> {
                config = null
                report = GroupImportReport.Rejected(
                    reason = "群聊载荷里的 config 无法解析",
                    fieldErrors = listOf(GroupConfigError("config", "缺少或无法解析 roles")),
                )
            }

            else -> when (val screening = GroupChat.screenImportedConfig(decodedConfig)) {
                is GroupChat.GroupConfigScreening.Accepted -> {
                    config = screening.config
                    report = GroupImportReport.Clean
                }

                is GroupChat.GroupConfigScreening.Normalized -> {
                    config = screening.config
                    report = GroupImportReport.Normalized(screening.fixes)
                }

                is GroupChat.GroupConfigScreening.Rejected -> {
                    config = null
                    report = GroupImportReport.Rejected(
                        reason = "群配置校验未通过",
                        fieldErrors = screening.fieldErrors,
                    )
                }
            }
        }
        return TavernGroupChatDocument(
            header = document.header,
            messages = document.messages.map(::groupMessageOf),
            groupName = payload.string("name").orEmpty(),
            userName = document.header.string("user_name").orEmpty(),
            characterNames = (document.header[GROUP_CHARACTER_NAMES_FIELD] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty(),
            config = config,
            cards = deduped.cards,
            importReport = report,
            cardFixes = cardFixes,
        )
    }

    private fun groupArray(
        nodes: List<MessageNode>,
        config: GroupConfig,
        cards: List<RoleCardMeta>,
        userName: String,
        groupName: String,
        createDate: String?,
    ): JsonArray {
        val names = roleDisplayNames(config, cards)
        val header = buildJsonObject {
            put("spec", "st_chat_v1")
            put("user_name", userName)
            // 酒馆只有 {{char}} 一个宏位，群聊里给它群名，角色名走每条消息的 name。
            put("character_name", groupName)
            createDate?.let { put("create_date", it) }
            // 酒馆 `group-chats.js:272` 只在首行带 chat_metadata 时才把这行当表头丢掉，
            // 缺了它整行会被渲染成一条空消息。
            put("chat_metadata", buildJsonObject { put("is_group", true) })
            put(GROUP_FIELD, groupPayload(config, cards, groupName))
            put(GROUP_CHARACTER_NAMES_FIELD, JsonArray(names.values.map { JsonPrimitive(it) }))
        }
        val array = JsonArray(listOf(header) + nodes.map { groupMessageOf(it, names, userName, groupName) })
        val offending = GroupChat.findForbiddenKeys(array)
        check(offending.isEmpty()) { "群聊导出包含禁止导出的字段：$offending" }
        return array
    }

    private fun groupPayload(config: GroupConfig, cards: List<RoleCardMeta>, groupName: String): JsonObject =
        buildJsonObject {
            put("kind", GroupChat.QR_KIND)
            put("schema_version", config.schemaVersion)
            put("name", groupName)
            // 群配置走 GroupChat 自己的编解码，未知字段（extras）平铺在 config 里，
            // encodeConfigObject 与 decodeConfigObject 互为逆运算。
            put("config", GroupChat.encodeConfigObject(config))
            put("cards", JsonArray(cards.map { cardJson.encodeToJsonElement(RoleCardMeta.serializer(), it) }))
        }

    /** 群成员显示名。`GroupRole.name` 是显示名的唯一来源，角色卡只在配置缺名时兜底。 */
    private fun roleDisplayNames(config: GroupConfig, cards: List<RoleCardMeta>): Map<String, String> =
        buildMap {
            config.roles.forEach { role ->
                // 角色名口径只有一份（与群聊页、气泡层同一个 displayNameOf）。
                if (role.id.isNotBlank()) put(role.id, GroupTurnCoordinator.displayNameOf(config, role.id))
            }
            cards.forEach { card ->
                if (card.roleId.isNotBlank()) putIfAbsent(card.roleId, card.name.ifBlank { card.roleId })
            }
        }

    private fun groupMessageOf(
        node: MessageNode,
        names: Map<String, String>,
        userName: String,
        groupName: String,
    ): JsonObject {
        val index = node.selectIndex.coerceIn(0, node.messages.lastIndex.coerceAtLeast(0))
        val selected = node.messages.getOrNull(index)
        val roleId = selected?.roleId
        val turnKind = selected?.let {
            it.turnKind ?: if (it.role == MessageRole.USER) GroupChat.TURN_USER else GroupChat.TURN_SPEAKER
        }
        return buildJsonObject {
            put("name", groupMessageName(selected, roleId, names, userName, groupName))
            put("is_user", selected?.role == MessageRole.USER)
            put("is_system", selected?.role == MessageRole.SYSTEM)
            put("mes", selected?.toText().orEmpty())
            if (node.messages.isNotEmpty()) {
                put("swipes", JsonArray(node.messages.map { JsonPrimitive(it.toText()) }))
                put("swipe_id", index)
            }
            roleId?.let { put(FIELD_ROLE_ID, it) }
            selected?.roundId?.let { put(FIELD_ROUND_ID, it) }
            turnKind?.let { put(FIELD_TURN_KIND, it) }
            put(
                FIELD_MENTION_ROLE_IDS,
                JsonArray((selected?.mentionRoleIds ?: emptyList()).map { JsonPrimitive(it) }),
            )
        }
    }

    private fun groupMessageName(
        selected: UIMessage?,
        roleId: String?,
        names: Map<String, String>,
        userName: String,
        groupName: String,
    ): String = when {
        selected == null -> groupName
        selected.role == MessageRole.USER -> userName
        roleId == null -> groupName
        // 合成节点（[GroupChat.SUMMARY_ID]）没有角色名，用与群聊页一致的显示名兜底：
        // 唯一一份定义在 GroupSpeakerResolver.SUMMARY_DISPLAY_NAME。
        roleId == GroupChat.SUMMARY_ID -> names[roleId] ?: GroupSpeakerResolver.SUMMARY_DISPLAY_NAME
        else -> names[roleId] ?: roleId
    }

    private fun groupMessageOf(message: TavernChatMessage): TavernGroupMessage {
        val raw = message.raw
        val roleId = raw.string(FIELD_ROLE_ID)
        val roundId = raw.string(FIELD_ROUND_ID)
        val turnKind = raw.string(FIELD_TURN_KIND)
        val mentions = (raw[FIELD_MENTION_ROLE_IDS] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        return TavernGroupMessage(
            // 契约字段同时写回消息本身：调用方拿 node 就能直接用，不必再解一遍 raw。
            node = MessageNode(
                messages = message.node.messages.map {
                    it.copy(
                        roleId = roleId,
                        roundId = roundId,
                        turnKind = turnKind,
                        mentionRoleIds = mentions,
                    )
                },
                selectIndex = message.node.selectIndex,
            ),
            name = raw.string("name").orEmpty(),
            roleId = roleId,
            roundId = roundId,
            turnKind = turnKind,
            mentionRoleIds = mentions,
            raw = raw,
        )
    }
}

data class TavernChatDocument(
    val header: JsonObject,
    val messages: List<TavernChatMessage>,
)

data class TavernChatMessage(
    val node: MessageNode,
    val raw: JsonObject,
)

/**
 * 群聊文件恢复时的配置筛查结论（[TavernChatCodec.importGroup] 产出）。
 *
 * 分四类而不是「通过 / 不通过」两类，是因为**「没有群配置块」与「配置被判掉」必须能分开**：
 * 前者是纯酒馆群聊文件（那种文件本来就没有 KhatKit 的群配置），不是错误；后者是这份文件
 * 的配置建不出群，调用方必须拒绝落库。两者在返回值上都表现为 `config == null`。
 *
 * 判定与归一化的口径全在 [GroupChat.screenImportedConfig]，本类型只负责把它带出来。
 */
sealed interface GroupImportReport {
    /** 文件里没有 `khatkit_group.config` 块（纯酒馆群聊文件）。[TavernGroupChatDocument.config] 为 null。 */
    data object NoConfig : GroupImportReport

    /** 配置原样通过校验，未被改写。[TavernGroupChatDocument.config] 就是文件里的那一份。 */
    data object Clean : GroupImportReport

    /**
     * 配置不合法：[TavernGroupChatDocument.config] **一律为 null**——坏配置绝不会被交出去，
     * 契约「恢复失败不留下半成品会话」因此有了返回值层面的保证。消息 / 群名 / 角色卡
     * 照常返回，用户至少拿得到聊天记录。
     */
    data class Rejected(
        val reason: String,
        val fieldErrors: List<GroupConfigError> = emptyList(),
    ) : GroupImportReport

    /**
     * 配置越界但已归一化：[TavernGroupChatDocument.config] 是归一化后的那一份（已通过
     * [GroupChat.validate]），[fixes] 逐条记着改前改后的字面值，供调用方展示给用户。
     */
    data class Normalized(val fixes: List<GroupChat.GroupConfigFix>) : GroupImportReport
}

/**
 * 群聊导入结果。[config] 为 null 表示这份文件没有**可用**的群配置。
 *
 * ⚠️ **必须与 [importReport] 一起读**：`config == null` 有两种原因，报告才区分得开——
 * 文件里本来就没有群配置块（[GroupImportReport.NoConfig]），或者配置被判掉
 * （[GroupImportReport.Rejected]）。只看 `config` 会把这两种混成一种。
 *
 * [cardFixes] 是角色卡去重记录，与配置筛查正交：配置干净时也可能去了重（重复 `role_id`），
 * 所以它挂在文档上而不是报告里。
 */
data class TavernGroupChatDocument(
    val header: JsonObject,
    val messages: List<TavernGroupMessage>,
    val groupName: String,
    val userName: String,
    val characterNames: List<String>,
    val config: GroupConfig?,
    val cards: List<RoleCardMeta>,
    val importReport: GroupImportReport,
    val cardFixes: List<GroupChat.GroupConfigFix> = emptyList(),
)

/** 一条群消息：酒馆字段在 [raw]，C1 契约字段既在 [raw] 也已写回 [node] 的消息上。 */
data class TavernGroupMessage(
    val node: MessageNode,
    val name: String,
    val roleId: String?,
    val roundId: String?,
    val turnKind: String?,
    val mentionRoleIds: List<String>,
    val raw: JsonObject,
)

private fun UIMessage.Companion.of(role: MessageRole, text: String): UIMessage = when (role) {
    MessageRole.USER -> user(text)
    MessageRole.SYSTEM -> system(text)
    else -> assistant(text)
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
