package heizige.kk.khatkit.app.core.data.ai.tavern

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * C1-09「Tavern/QR 往返」的**导出哈希证据**（零设备）。
 *
 * 契约 `docs/beyond-operit-client-changes.md` 要求每个验收用例保存「导出哈希」。
 * 之前这条一直记「无证据」，理由是「要真机导出才能算哈希」——那个理由不成立：
 * [TavernChatCodec.exportGroupJsonl] / [TavernChatCodec.exportGroup] /
 * [GroupChat.encodeQr] 全是**纯函数**，输入只有 `List<MessageNode>` + `GroupConfig` +
 * `List<RoleCardMeta>` + 几个字符串，不碰数据库、不碰 `Context`、不碰设备。所以这里在
 * JVM 里用**真实的生产编解码器**把文件真的落盘、真的算 SHA-256。
 *
 * 配套脚本 `tools/verification/c1p_group_export_hash.py` 连起**两个独立 JVM 运行**
 * 比对本类打印的哈希，因此「同进程两次一致」与「跨进程两次一致」是两回事、都测。
 *
 * ## 证明什么 / 不证明什么
 *
 * 证明：
 * - 导出是**确定性**的：同一份输入连跑两次字节逐字节相同（同一个 JVM 内算两遍），
 *   跨 JVM 由脚本比对。
 * - 落盘文件的字节数与 SHA-256 可复算（脚本用 `hashlib` 独立再算一遍落盘文件，
 *   与本类打印的值对照，不一致就失败）。
 * - 导出 → 导入 → 再导出**幂等**：往返后 `role_id` / `round_id` / `turn_kind` /
 *   `mention_role_ids` / `name` / `mes` / `swipes` / `swipe_id` / 双层 `extras` /
 *   `RoleCardMeta` 六字段 / `groupName` / `userName` / `characterNames` 逐条相等，
 *   再导出的 SHA-256 与第一次**完全相同**。
 * - 结构能被 SillyTavern 认出来：首行表头带 `spec: "st_chat_v1"` 与
 *   `chat_metadata.is_group = true`，逐条消息带 `name` / `is_user` / `is_system` /
 *   `mes` / `swipes` / `swipe_id`；酒馆不解释的字段另落在契约键与 `khatkit_` 私有命名空间。
 *
 * 不证明：
 * - **没证明酒馆真机能打开这个文件**。只做结构层对照（依据已核实并登记在
 *   `docs/beyond-operit-open-source-references.md` 的 SillyTavern `release`
 *   @ `06bde939` 源码行为，本轮**没有**联网重新 clone），是离线结构比对。
 * - 没证明文件经过真实 IO 分发（`writeExportTempFile` + `ACTION_SEND` 分享面板）。
 * - 没证明扫码链路（`QrScannerSheet` 需要相机）。
 * - KDoc 已声明的不保证项不在这份断言里：非文本 part 只留 `toText()`；同一 node
 *   多 swipe 的契约字段以选中 swipe 为准；消息 `id` / `createdAt` 不导出
 *   （这正是「算得出稳定哈希」的前提）。
 */
class C1pGroupExportHashTest {

    private val json = Json { encodeDefaults = false }

    /**
     * 变体清单：三种 `mode` 各一份（`pipeline` / `roundtable` / `vote`）、三种载体
     * （JSONL / 数组 / QR 载荷），加四个边界变体（空消息列表、非文本 part、
     * 显式 `createDate`、QR 的两种 mode）。
     *
     * 每个 Tavern 变体都跑同一份**内容真实**的节点集合：3 个角色（其中一个
     * `chair = true`）、2 个 `round_id`、`user` / `speaker` / `chair` /
     * `vote_summary` / `error` 五种 `turn_kind` 齐全、两条带不同 `mention_role_ids`
     * 的用户消息、一个 `selectIndex != 0` 的多 swipe 节点，以及需要 JSON 转义的
     * 中文 / emoji / 引号 / 反斜杠 / 换行。
     */
    private fun variants(): List<Variant> = listOf(
        Variant(name = "pipeline_3roles_2rounds_jsonl", config = pipelineConfig(), form = Form.JSONL),
        Variant(name = "roundtable_3roles_2rounds_jsonl", config = roundtableConfig(), form = Form.JSONL),
        Variant(name = "vote_3roles_2rounds_jsonl", config = voteConfig(), form = Form.JSONL),
        Variant(name = "pipeline_3roles_2rounds_array", config = pipelineConfig(), form = Form.ARRAY),
        Variant(
            name = "empty_messages_jsonl",
            config = pipelineConfig(),
            nodes = emptyList(),
            form = Form.JSONL,
            coversFullTurnKindSet = false,
        ),
        Variant(
            name = "image_part_jsonl",
            config = pipelineConfig(),
            nodes = nodesWithImagePart(),
            form = Form.JSONL,
            coversFullTurnKindSet = false,
        ),
        Variant(
            // 唯一一处「时间」入口：只有调用方显式传 createDate 才会写 `create_date`，
            // 不传就整个键不存在。这个变体证明导出里没有我们自己偷偷塞的时间戳。
            name = "pipeline_with_explicit_create_date_jsonl",
            config = pipelineConfig(),
            createDate = "2026-10-05@09h30m00s",
            form = Form.JSONL,
        ),
        Variant(name = "qr_payload_pipeline", config = pipelineConfig(), form = Form.QR),
        Variant(name = "qr_payload_vote", config = voteConfig(), form = Form.QR),
    )

    /** 变体定义。导出与「再导出」都由 [form] 决定，两边必须同形才谈幂等。 */
    private data class Variant(
        val name: String,
        val config: GroupConfig,
        val form: Form,
        val cards: List<RoleCardMeta> = cards(),
        val nodes: List<MessageNode> = nodes(),
        val userName: String = USER_NAME,
        val groupName: String = GROUP_NAME,
        val createDate: String? = null,
        /** 该变体是否覆盖全部五种 `turn_kind`（边界变体天然不覆盖）。 */
        val coversFullTurnKindSet: Boolean = true,
    )

    private enum class Form { JSONL, ARRAY, QR }

    private fun Variant.export(): String = when (form) {
        Form.JSONL -> TavernChatCodec.exportGroupJsonl(
            nodes = nodes,
            config = config,
            cards = cards,
            userName = userName,
            groupName = groupName,
            createDate = createDate,
        )

        Form.ARRAY -> TavernChatCodec.exportGroup(
            nodes = nodes,
            config = config,
            cards = cards,
            userName = userName,
            groupName = groupName,
            createDate = createDate,
        )

        Form.QR -> GroupChat.encodeQr(config, cards)
    }

    /**
     * 往返之后再导出。`nodes` / `config` / `cards` / `userName` / `groupName` 一律用
     * **导入回来的值**，这样「再导出」才真的是往返的产物，而不是拿原 fixture 重算一遍。
     */
    private fun Variant.reexport(document: TavernGroupChatDocument): String = when (form) {
        Form.JSONL -> TavernChatCodec.exportGroupJsonl(
            nodes = document.messages.map { it.node },
            config = document.config ?: config,
            cards = document.cards,
            userName = document.userName,
            groupName = document.groupName,
            createDate = createDate,
        )

        Form.ARRAY -> TavernChatCodec.exportGroup(
            nodes = document.messages.map { it.node },
            config = document.config ?: config,
            cards = document.cards,
            userName = document.userName,
            groupName = document.groupName,
            createDate = createDate,
        )

        Form.QR -> GroupChat.encodeQr(document.config ?: config, document.cards)
    }

    // ---------------- 用例 ----------------

    /**
     * 落盘 + 打印哈希 + **同一个 JVM 进程内**两次调用一致。
     *
     * 打印格式固定为 `C1P-HASH  <variant>  <bytes>  <sha256>`，供
     * `tools/verification/c1p_group_export_hash.py` 抓取并跨两次独立 JVM 运行比对。
     */
    @Test
    fun `every variant writes a real file whose sha256 is identical across two calls in one jvm`() {
        println("C1P-HASH-BEGIN")
        variants().forEach { variant ->
            val first = variant.export().toByteArray(Charsets.UTF_8)
            val second = variant.export().toByteArray(Charsets.UTF_8)

            assertEquals(
                "${variant.name}: 同一 JVM 内两次导出字节不同",
                first.toList(),
                second.toList(),
            )
            assertEquals(
                "${variant.name}: 同一 JVM 内两次导出 SHA-256 不同",
                sha256(first),
                sha256(second),
            )

            val file = writeArtifact(variant.name, first)
            assertEquals(
                "${variant.name}: 落盘文件长度与内存字节数不一致（$file）",
                first.size.toLong(),
                file.length(),
            )
            println("C1P-HASH  ${variant.name}  ${first.size}  ${sha256(first)}")
        }
        println("C1P-HASH-END")
    }

    /**
     * 导出 → 导入 → 再导出，断言逐字段相等**且**再导出的 SHA-256 与第一次相同。
     *
     * 这是「幂等」的硬证据：文件里没写进去的东西不可能在往返后凭空长出来，所以第二次
     * 导出只可能与第一次逐字节相同（除非生产代码里有非确定性来源，那正是这条要抓的）。
     */
    @Test
    fun `group export round trips and the second export has the very same sha256`() {
        variants().filter { it.form == Form.QR }.forEach { variant ->
            val first = variant.export().toByteArray(Charsets.UTF_8)
            // QR 载荷不是群聊文件，importGroup 按设计返回 null（不猜）；它走
            // decodeSharePayload 那条解码路径。
            assertNull(
                "${variant.name}: QR 载荷不该被当群聊文件导入",
                TavernChatCodec.importGroup(first.decodeToString()),
            )
            val payload = GroupChat.decodeSharePayload(first.decodeToString())
            assertNotNull("${variant.name}: QR 载荷解不出来", payload)
            assertEquals("${variant.name}: 群配置往返不等", variant.config, payload?.config)
            assertEquals("${variant.name}: 角色卡往返不等", variant.cards, payload?.cards)
            val again = GroupChat.encodeQr(payload!!.config, payload.cards).toByteArray(Charsets.UTF_8)
            assertEquals("${variant.name}: QR 再编码哈希不同", sha256(first), sha256(again))
            writeArtifact(variant.name, first)
        }

        variants().filter { it.form != Form.QR }.forEach { variant ->
            val first = variant.export().toByteArray(Charsets.UTF_8)
            val document = TavernChatCodec.importGroup(first.decodeToString())
            assertNotNull("${variant.name}: 导入失败", document)
            val restored = document!!

            // ---- 群名 / 用户名 / 成员显示名 ----
            assertEquals("${variant.name}: groupName", variant.groupName, restored.groupName)
            assertEquals("${variant.name}: userName", variant.userName, restored.userName)
            assertEquals(
                "${variant.name}: characterNames",
                listOf("阿尔法", "贝塔", "伽马"),
                restored.characterNames,
            )

            // ---- 群配置：双层 extras 都不许丢 ----
            assertEquals("${variant.name}: GroupConfig 往返不等", variant.config, restored.config)
            val restoredConfig = restored.config!!
            assertEquals(
                "${variant.name}: 群配置层 extras 丢失",
                variant.config.extras["vendor_note"],
                restoredConfig.extras["vendor_note"],
            )
            assertEquals(
                "${variant.name}: 群配置层嵌套 extras 丢失",
                variant.config.extras["nested"],
                restoredConfig.extras["nested"],
            )
            assertEquals(
                "${variant.name}: 角色层 extras 丢失",
                variant.config.roles.first { it.id == "a" }.extras,
                restoredConfig.roles.first { it.id == "a" }.extras,
            )
            assertTrue(
                "${variant.name}: chair 标记丢失",
                restoredConfig.roles.first { it.id == "c" }.chair,
            )

            // ---- 角色卡：六字段逐个 ----
            assertEquals("${variant.name}: RoleCardMeta 往返不等", variant.cards, restored.cards)
            val cardA = restored.cards.first { it.roleId == "a" }
            assertEquals("阿尔法", cardA.name)
            assertEquals("asst-a", cardA.assistantId)
            assertEquals("card-a", cardA.cardId)
            assertTrue(
                "persona 里的非 ASCII / 转义字符没原样回来：${cardA.persona}",
                cardA.persona.contains("「引号」") &&
                    cardA.persona.contains("\\") &&
                    cardA.persona.contains("\n") &&
                    cardA.persona.contains("🎭"),
            )
            assertNull(
                "${variant.name}: avatarRef=null 必须仍是 null（不能变成字符串 \"null\"）",
                cardA.avatarRef,
            )
            assertEquals("file://avatar-b", restored.cards.first { it.roleId == "b" }.avatarRef)
            assertNull(
                "${variant.name}: cardId=null 必须仍是 null",
                restored.cards.first { it.roleId == "c" }.cardId,
            )

            // ---- 逐条消息：契约四件套 + 酒馆字段 + swipe ----
            assertEquals(
                "${variant.name}: 契约四件套不等",
                expectedTurns(variant),
                restored.messages.map {
                    listOf(it.roleId, it.roundId, it.turnKind, it.mentionRoleIds)
                },
            )
            assertEquals(
                "${variant.name}: mes 文本不等",
                expectedSelectedTexts(variant),
                restored.messages.map { it.node.currentMessage.toText() },
            )
            assertEquals(
                "${variant.name}: name 不等",
                expectedNames(variant),
                restored.messages.map { it.name },
            )
            restored.messages.forEachIndexed { index, message ->
                assertTrue("${variant.name}: 第 $index 行缺 swipes", message.raw["swipes"] is JsonArray)
                assertNotNull("${variant.name}: 第 $index 行缺 swipe_id", message.raw["swipe_id"])
                assertEquals(
                    "${variant.name}: 第 $index 行的契约字段没写回消息本身",
                    message.roleId,
                    message.node.currentMessage.roleId,
                )
            }
            // 多 swipe：契约字段取选中那条，swipes 全量保留
            if (variant.name == "pipeline_3roles_2rounds_jsonl") {
                val swiped = restored.messages[2]
                assertEquals(
                    listOf("贝塔：初版 \"方案\"，展开讲三段", "贝塔：重掷后的短版 \"方案\" 🎯"),
                    swiped.node.messages.map { it.toText() },
                )
                assertEquals(1, swiped.node.selectIndex)
                assertEquals("贝塔：重掷后的短版 \"方案\" 🎯", swiped.node.currentMessage.toText())
            }

            // 非文本 part 只留 toText()（KDoc 已声明的不保证项，这里钉住实际行为）。
            // 注意 `toText()` 用 "\n" 连接所有 part、非文本 part 贡献空串，所以
            // [Text, Image] 的结果是 "带图发言\n" —— 图片既没写出去、也没占位。
            if (variant.name == "image_part_jsonl") {
                assertEquals(
                    listOf("带图发言\n"),
                    restored.messages.map { it.node.currentMessage.toText() },
                )
                assertTrue(
                    "${variant.name}: 图片不该被写成任何字段",
                    restored.messages.all { message ->
                        message.raw.keys.none { key -> key.contains("image", ignoreCase = true) }
                    },
                )
            }

            // 空消息列表边界：文件里只有一行表头，往返后仍然只有表头
            if (variant.nodes.isEmpty()) {
                assertEquals("${variant.name}: 空消息列表应只剩表头", 0, restored.messages.size)
                assertEquals(
                    "${variant.name}: 空消息列表的文件应只有一行",
                    1,
                    first.decodeToString().trim().lines().size,
                )
            }

            // ---- 再导出：SHA-256 必须与第一次完全相同 ----
            val again = variant.reexport(restored).toByteArray(Charsets.UTF_8)
            assertEquals(
                "${variant.name}: 往返再导出的 SHA-256 与第一次不同（${sha256(first)} vs ${sha256(again)}）",
                sha256(first),
                sha256(again),
            )
            assertEquals(
                "${variant.name}: 往返再导出的字节数不同",
                first.size.toLong(),
                again.size.toLong(),
            )
        }
    }

    /**
     * 与 SillyTavern `release` @ `06bde939` 的**结构层**对照。
     *
     * ⚠️ 本轮**没有**联网重新 clone 上游（也不该由这个任务做）：结论来自已核实并
     * 登记在 `docs/beyond-operit-open-source-references.md` 的源码行为
     * （`public/scripts/group-chats.js:268,272` 读 `data[0].chat_metadata`，且只在首行
     * **带这个键**时才 `shift()` 掉表头）。因此这是离线结构比对，**不是**真机打开。
     *
     * 酒馆解释不了的字段（`role_id` / `round_id` / `turn_kind` / `mention_role_ids` /
     * `khatkit_group` / `khatkit_character_names`）我们原样保留：后两个落在
     * `khatkit_` 私有命名空间，前四个是消息对象顶层的 C1 契约键，全部与酒馆任何
     * 已知字段名零重名（下面逐行断言这一点）。
     */
    @Test
    fun `the file structure is what sillytavern group chats read and private keys stay namespaced`() {
        val tavernHeaderKeys = setOf("spec", "user_name", "character_name", "create_date", "chat_metadata")
        val tavernMessageKeys = setOf("name", "is_user", "is_system", "mes", "swipes", "swipe_id")
        val privateKeys = setOf(
            TavernChatCodec.FIELD_ROLE_ID,
            TavernChatCodec.FIELD_ROUND_ID,
            TavernChatCodec.FIELD_TURN_KIND,
            TavernChatCodec.FIELD_MENTION_ROLE_IDS,
            TavernChatCodec.GROUP_FIELD,
            TavernChatCodec.GROUP_CHARACTER_NAMES_FIELD,
        )

        variants().filter { it.form != Form.QR }.forEach { variant ->
            val body = variant.export()
            val lines = when (variant.form) {
                // JSONL：首行表头，其余行是消息
                Form.JSONL -> body.trim().lines().filter { it.isNotBlank() }
                // 数组：整体是一个 JSON 数组，首元素是表头
                Form.ARRAY -> json.parseToJsonElement(body).jsonArray.map { it.toString() }
                Form.QR -> emptyList()
            }
            assertTrue("${variant.name}: 一行都没有", lines.isNotEmpty())

            val header = json.parseToJsonElement(lines.first()).jsonObject
            assertEquals(
                "${variant.name}: 首行 spec 必须是酒馆认的 st_chat_v1",
                "st_chat_v1",
                header["spec"]?.jsonPrimitive?.content,
            )
            assertEquals(
                "${variant.name}: 首行 chat_metadata.is_group 必须为 true，否则酒馆不把首行当表头丢掉",
                true,
                header["chat_metadata"]?.jsonObject?.get("is_group")?.jsonPrimitive?.booleanOrNull,
            )
            assertTrue(
                "${variant.name}: 表头不该带 mes/swipes（带了酒馆会把表头当消息渲染）",
                header["mes"] == null && header["swipes"] == null,
            )
            assertEquals(
                "${variant.name}: user_name",
                variant.userName,
                header["user_name"]?.jsonPrimitive?.content,
            )
            // 酒馆只有 {{char}} 一个宏位，群聊里给它群名
            assertEquals(
                "${variant.name}: character_name 应是群名",
                variant.groupName,
                header["character_name"]?.jsonPrimitive?.content,
            )
            // 未显式传 createDate 时不得有任何时间戳字段
            if (variant.createDate == null) {
                assertNull("${variant.name}: 没人传 createDate 就不该写 create_date", header["create_date"])
            } else {
                assertEquals(
                    "${variant.name}: createDate",
                    variant.createDate,
                    header["create_date"]?.jsonPrimitive?.content,
                )
            }

            // 私有命名空间：与酒馆已知字段零重名
            header.keys.forEach { key ->
                assertTrue(
                    "${variant.name}: 私有键 $key 与酒馆表头字段重名",
                    tavernHeaderKeys.contains(key) || key in privateKeys,
                )
            }
            assertEquals(
                "${variant.name}: GROUP_FIELD 必须在表头",
                true,
                header.containsKey(TavernChatCodec.GROUP_FIELD),
            )
            assertEquals(
                "${variant.name}: GROUP_CHARACTER_NAMES_FIELD 必须在表头",
                true,
                header.containsKey(TavernChatCodec.GROUP_CHARACTER_NAMES_FIELD),
            )
            val payload = header[TavernChatCodec.GROUP_FIELD]!!.jsonObject
            assertEquals(GroupChat.QR_KIND, payload["kind"]?.jsonPrimitive?.content)
            assertEquals(variant.cards.size, (payload["cards"] as? JsonArray)?.size)

            // 逐条消息：酒馆认的六字段一个都不能少，私有的四个契约键原样保留
            val messages = lines.drop(1).map { json.parseToJsonElement(it).jsonObject }
            assertEquals("${variant.name}: 消息条数", variant.nodes.size, messages.size)
            messages.forEachIndexed { index, message ->
                tavernMessageKeys.forEach { key ->
                    assertTrue("${variant.name}: 第 $index 行缺酒馆字段 $key", message.containsKey(key))
                }
                message.keys.forEach { key ->
                    assertTrue(
                        "${variant.name}: 第 $index 行的键 $key 既不是酒馆字段也不是契约键",
                        tavernMessageKeys.contains(key) || key in privateKeys,
                    )
                }
                assertEquals(
                    "${variant.name}: 第 $index 行 swipe_id 必须落在 swipes 范围内",
                    true,
                    message["swipe_id"]!!.jsonPrimitive.content.toInt() in
                        0 until message["swipes"]!!.jsonArray.size,
                )
                assertEquals(
                    "${variant.name}: 第 $index 行 is_user / is_system 不该同时为真",
                    true,
                    !(message["is_user"]!!.jsonPrimitive.booleanOrNull == true &&
                        message["is_system"]!!.jsonPrimitive.booleanOrNull == true),
                )
            }

            // 全部五种 turn_kind 都在文件里看得见
            val kinds = messages.mapNotNull { it[TavernChatCodec.FIELD_TURN_KIND]?.jsonPrimitive?.content }.toSet()
            if (variant.coversFullTurnKindSet) {
                assertEquals(
                    "${variant.name}: turn_kind 取值不齐：$kinds",
                    setOf(
                        GroupChat.TURN_USER,
                        GroupChat.TURN_SPEAKER,
                        GroupChat.TURN_CHAIR,
                        GroupChat.TURN_VOTE_SUMMARY,
                        GroupChat.TURN_ERROR,
                    ),
                    kinds,
                )
            }

            // 两条不同的 mention 必须都在文件里看得见
            val mentions = messages.map {
                (it[TavernChatCodec.FIELD_MENTION_ROLE_IDS] as? JsonArray).orEmpty().map { e -> e.jsonPrimitive.content }
            }
            if (variant.coversFullTurnKindSet) {
                assertEquals(
                    "${variant.name}: mention_role_ids 丢失：$mentions",
                    setOf(listOf("a", "b"), listOf("c")),
                    mentions.filter { it.isNotEmpty() }.toSet(),
                )
            }

            if (variant.coversFullTurnKindSet) {
                // 议长那一行的 name 必须是角色名，不是裸 role_id
                assertEquals(
                    "${variant.name}: 议长行的 name",
                    "伽马",
                    messages.first { it[TavernChatCodec.FIELD_ROLE_ID]?.jsonPrimitive?.content == "c" }
                        ?.get("name")?.jsonPrimitive?.content,
                )
                // 合成节点（__summary__）没有角色名，用统一的显示名兜底
                assertEquals(
                    "${variant.name}: 合成节点的显示名",
                    "多数决",
                    messages.first { it[TavernChatCodec.FIELD_ROLE_ID]?.jsonPrimitive?.content == GroupChat.SUMMARY_ID }
                        ?.get("name")?.jsonPrimitive?.content,
                )
                // 至少两个不同 round_id
                assertEquals(
                    "${variant.name}: round_id 只有一个",
                    2,
                    messages.mapNotNull { it[TavernChatCodec.FIELD_ROUND_ID]?.jsonPrimitive?.content }.toSet().size,
                )
            }
        }
    }

    /** QR 载荷的 SHA-256：同样跑 [GroupChat.encodeQr]，同样两次一致。 */
    @Test
    fun `qr payload sha256 is identical across two calls in one jvm`() {
        listOf(pipelineConfig(), voteConfig()).forEach { config ->
            val first = GroupChat.encodeQr(config, cards()).toByteArray(Charsets.UTF_8)
            val second = GroupChat.encodeQr(config, cards()).toByteArray(Charsets.UTF_8)
            assertEquals(sha256(first), sha256(second))
            assertEquals(first.size.toLong(), writeArtifact("qr_${config.mode}", first).length())
            // 闸门：载荷里不含任何黑名单键（encodeQr 自己会抛，但这里再钉一次）
            assertEquals(
                emptySet<String>(),
                GroupChat.findForbiddenKeys(json.parseToJsonElement(first.decodeToString())),
            )
            println("C1P-QR  ${config.mode}  ${first.size}  ${sha256(first)}")
        }
    }

    /** 本 fixture 刻意不含黑名单键名，所以导出路径必须一路畅通。 */
    @Test
    fun `the fixture carries no forbidden key so the export path stays open`() {
        variants().forEach { variant ->
            // JSONL 不是一个 JSON 文档，逐行扫；数组 / QR 载荷整份扫。
            val elements = when (variant.form) {
                Form.JSONL -> variant.export().trim().lines().filter { it.isNotBlank() }
                    .map { json.parseToJsonElement(it) }

                else -> listOf(json.parseToJsonElement(variant.export()))
            }
            assertEquals(
                "${variant.name}: fixture 里出现了禁止导出的键名",
                emptyList<String>(),
                elements.flatMap { GroupChat.findForbiddenKeys(it) }.sorted(),
            )
        }
    }

    // ---------------- 期望值（从 fixture 反推，不手抄第二份） ----------------

    /** 契约四件套：`role_id` / `round_id` / `turn_kind` / `mention_role_ids`。 */
    private fun expectedTurns(variant: Variant): List<List<Any?>> = variant.nodes.map { node ->
        val message = selectedMessage(node)
        listOf(
            message?.roleId,
            message?.roundId,
            message?.turnKind ?: if (message?.role == MessageRole.USER) {
                GroupChat.TURN_USER
            } else {
                GroupChat.TURN_SPEAKER
            },
            message?.mentionRoleIds.orEmpty(),
        )
    }

    /** `mes` 取选中 swipe 的 `toText()`（生产代码同一口径）。 */
    private fun expectedSelectedTexts(variant: Variant): List<String> = variant.nodes.map { node ->
        selectedMessage(node)?.toText().orEmpty()
    }

    /** `name`：用户消息取 `user_name`，合成节点取「多数决」，其余取角色名。 */
    private fun expectedNames(variant: Variant): List<String> {
        val names = variant.config.roles.associate { role -> role.id to role.name }
        return variant.nodes.map { node ->
            val message = selectedMessage(node)
            val roleId = message?.roleId
            when {
                message == null -> variant.groupName
                message.role == MessageRole.USER -> variant.userName
                roleId == null -> variant.groupName
                roleId == GroupChat.SUMMARY_ID -> SUMMARY_DISPLAY_NAME
                else -> names[roleId] ?: roleId
            }
        }
    }

    private fun selectedMessage(node: MessageNode): UIMessage? =
        node.messages.getOrNull(node.selectIndex.coerceIn(0, node.messages.lastIndex.coerceAtLeast(0)))

    // ---------------- 落盘与哈希 ----------------

    /**
     * 产物写到 `app/build/c1p-group-export-hash/`，**绝不落进仓库源码目录**。
     * 硬断言挡一下：路径里必须含一段 `build`，否则直接失败，防止有人改了相对路径
     * 就把导出文件写进 `app/src/`。
     */
    private fun writeArtifact(name: String, bytes: ByteArray): File {
        val dir = File("build/c1p-group-export-hash")
        val canonical = dir.absoluteFile.normalize()
        assertTrue(
            "导出产物目录必须落在 build/ 下，实际是 $canonical",
            canonical.path.replace('\\', '/').contains("/build/"),
        )
        assertTrue("导出产物目录建不出来：$canonical", dir.mkdirs() || dir.isDirectory)
        val file = File(dir, "$name.txt")
        file.writeBytes(bytes)
        println("C1P-FILE  $name  ${canonical.path}")
        return file
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ---------------- fixture ----------------

    private companion object {
        const val USER_NAME = "阿达"
        const val GROUP_NAME = "三人议事厅 🎭"

        /**
         * 合成节点显示名。硬编码在测试里是**故意的**：它是
         * `GroupSpeakerResolver.SUMMARY_DISPLAY_NAME` 的期望值，写成字面量才能在
         * 有人改掉那个常量时让这条断言失败。
         */
        const val SUMMARY_DISPLAY_NAME = "多数决"

        /** 需要 JSON 转义的字符全放进 persona 与 `mes`：引号 / 反斜杠 / 换行 / emoji / 中文。 */
        const val PERSONA_A =
            "冷静的分析者，说话带「引号」，路径写 C:\\Users\\ada\n第二行 🎭，tab\there"

        fun nodes(): List<MessageNode> = listOf(
            MessageNode.of(
                UIMessage.user("先说方案 \"草案\"，路径 C:\\tmp\\plan，@阿尔法 @贝塔 看一下 🎭")
                    .copy(
                        roundId = "r1",
                        turnKind = GroupChat.TURN_USER,
                        mentionRoleIds = listOf("a", "b"),
                    )
            ),
            MessageNode.of(
                UIMessage.assistant("阿尔法：先给结论 ✅ 细节见附注 \"第 1 条\"")
                    .copy(
                        roleId = "a",
                        roundId = "r1",
                        turnKind = GroupChat.TURN_SPEAKER,
                    )
            ),
            MessageNode(
                // 多 swipe，且 selectIndex 非 0：契约字段取选中那条
                messages = listOf(
                    UIMessage.assistant("贝塔：初版 \"方案\"，展开讲三段")
                        .copy(
                            roleId = "b",
                            roundId = "r1",
                            turnKind = GroupChat.TURN_SPEAKER,
                            mentionRoleIds = listOf("c"),
                        ),
                    UIMessage.assistant("贝塔：重掷后的短版 \"方案\" 🎯")
                        .copy(
                            roleId = "b",
                            roundId = "r1",
                            turnKind = GroupChat.TURN_SPEAKER,
                            mentionRoleIds = listOf("c"),
                        ),
                ),
                selectIndex = 1,
            ),
            MessageNode.of(
                UIMessage.assistant("伽马（议长）汇总：第一轮结束 🎉，分歧在 \"预算\"")
                    .copy(
                        roleId = "c",
                        roundId = "r1",
                        turnKind = GroupChat.TURN_CHAIR,
                    )
            ),
            MessageNode.of(
                UIMessage.user("@伽马 汇总投票 \"选项\"")
                    .copy(
                        roundId = "r2",
                        turnKind = GroupChat.TURN_USER,
                        mentionRoleIds = listOf("c"),
                    )
            ),
            MessageNode.of(
                UIMessage.assistant("本轮投票结果：a")
                    .copy(
                        roleId = GroupChat.SUMMARY_ID,
                        roundId = "r2",
                        turnKind = GroupChat.TURN_VOTE_SUMMARY,
                    )
            ),
            MessageNode.of(
                UIMessage.assistant("[贝塔] 本轮生成失败：超时")
                    .copy(
                        roleId = "b",
                        roundId = "r2",
                        turnKind = GroupChat.TURN_ERROR,
                    )
            ),
            MessageNode.of(
                UIMessage.assistant("伽马：汇总结论 \"采纳 a\" 🎓")
                    .copy(
                        roleId = "c",
                        roundId = "r2",
                        turnKind = GroupChat.TURN_CHAIR,
                    )
            ),
        )

        /**
         * 非文本 part 的边界变体：图片 part 只在导出时退成 `toText()`（KDoc 已声明
         * 不保证）。留在这里是因为它能证明「导出的字节里没有任何图片痕迹」——也就是
         * 图片既没被写出去、也不会在往返后凭空出现。
         */
        fun nodesWithImagePart(): List<MessageNode> = listOf(
            MessageNode.of(
                UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(
                        UIMessagePart.Text("带图发言"),
                        UIMessagePart.Image("file:///data/user/0/khatkit/secret-image.png"),
                    ),
                ).copy(
                    roleId = "a",
                    roundId = "r1",
                    turnKind = GroupChat.TURN_SPEAKER,
                )
            ),
        )

        /** 三个角色，其中 `c` 是议长；`a` 带角色层未知字段 `extras`。 */
        fun roles(): List<GroupRole> = listOf(
            GroupRole(
                id = "a",
                name = "阿尔法",
                assistantId = "asst-a",
                cardId = "card-a",
                extras = buildJsonObject {
                    put("tone", "cold")
                    put("vendor_note", "角色层未知字段")
                },
            ),
            GroupRole(id = "b", name = "贝塔", assistantId = "asst-b", cardId = "card-b"),
            GroupRole(id = "c", name = "伽马", assistantId = "asst-c", chair = true),
        )

        /** 群配置层未知字段 `extras`（含一个嵌套对象），往返必须原样带回。 */
        fun baseConfig(mode: String) = GroupConfig(
            roles = roles(),
            mode = mode,
            chairRoleId = null,
            tokenBudgetPerRound = 4_000,
            revision = 3,
            extras = buildJsonObject {
                put("vendor_note", "群配置层未知字段")
                put("nested", buildJsonObject { put("depth", 2) })
            },
        )

        fun pipelineConfig() = baseConfig(GroupChat.MODE_PIPELINE)

        fun roundtableConfig() = baseConfig(GroupChat.MODE_ROUNDTABLE).copy(chairRoleId = "c")

        fun voteConfig() = baseConfig(GroupChat.MODE_VOTE)
            .copy(voteCandidates = listOf("a", "b"), tiePolicy = GroupChat.TIE_CHAIR)

        /**
         * 角色卡六字段齐全：非 ASCII persona、`card_id` 有值、`avatar_ref` 有值与为 null
         * 两种都覆盖（为 null 的必须整个键消失，不能变成字符串 `"null"`）。
         */
        fun cards(): List<RoleCardMeta> = listOf(
            RoleCardMeta(
                roleId = "a",
                name = "阿尔法",
                assistantId = "asst-a",
                cardId = "card-a",
                persona = PERSONA_A,
            ),
            RoleCardMeta(
                roleId = "b",
                name = "贝塔",
                assistantId = "asst-b",
                cardId = "card-b",
                persona = "热情的执行者，偏爱 emoji 🎯",
                avatarRef = "file://avatar-b",
            ),
            RoleCardMeta(roleId = "c", name = "伽马", assistantId = "asst-c"),
        )
    }
}