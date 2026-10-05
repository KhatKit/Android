package heizige.kk.khatkit.app.core.data.ai.tavern

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupConfigError
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TavernChatCodec.importGroup] 的契约闸门：契约 `docs/beyond-orit-client-changes.md:205`
 * > 导入先 schema 校验与去重，再创建新 conversation；恢复失败不留下半成品会话。
 *
 * 上一版这一句在 `importGroup` 上是**空的**：它只把 `khatkit_group.config` 解出来就交出去，
 * 既不跑 [GroupChat.validate] 也不去重，而分享/扫码那条路径（[GroupChat.importShare]
 * 第 5 道闸门）是校验并拒收的 —— 同一句「导入群配置」在仓库里有两条口径。本类把
 * `importGroup` 那条补齐，并钉住四条不该被顺手改掉的性质：
 *
 * 1. 合法文件的**逐字节往返不变式**不变（去重与归一化只在文件本身非法时才动手）。
 * 2. `config == null` **必须**与 [GroupImportReport] 一起读，且坏配置绝不会被交出去。
 * 3. 双层 `extras`（配置层 + 角色层未知字段）在 `decodeConfigObject` 这条路上无损往返。
 * 4. `swipes` **不**去重（swipe 是 [MessageNode] 的分支身份，不是消息内容）。
 *
 * 分流口径（拒 / 降级）的逐条理由写在 [GroupChat.screenImportedConfig] 的 KDoc 里，
 * 纯函数侧的测试见 `GroupImportScreeningTest`。
 */
class TavernGroupImportGateTest {

    private val json = Json { encodeDefaults = false }

    // ---------------- 1. 合法文件：往返不变式与口径不动 ----------------

    @Test
    fun `a clean group file is reported clean and keeps its config untouched`() {
        val document = import(exportGroup(baseConfig()))
        assertEquals(GroupImportReport.Clean, document.importReport)
        assertEquals(baseConfig(), document.config)
        assertEquals("Clean 时不该有修正记录", emptyList<Any>(), document.cardFixes)
    }

    @Test
    fun `the byte level round trip survives the new gates`() {
        // exportGroup → importGroup → exportGroup 必须逐字节相同。去重与归一化只在文件非法时
        // 动手，因此这条断言就是「合法文件的往返不变式没被新闸门碰坏」的证明。
        val first = TavernChatCodec.exportGroup(
            nodes = nodes(),
            config = baseConfig(),
            cards = cards(),
            userName = USER_NAME,
            groupName = GROUP_NAME,
        )
        val document = requireNotNull(TavernChatCodec.importGroup(first))
        val again = TavernChatCodec.exportGroup(
            nodes = document.messages.map { it.node },
            config = requireNotNull(document.config),
            cards = document.cards,
            userName = document.userName,
            groupName = document.groupName,
        )
        assertEquals("往返后二次导出必须逐字节相同", first, again)
    }

    @Test
    fun `the gate leaves an already valid file's report and fixes empty`() {
        // 顺带钉住「合法文件一个字节都不许动」：报告是 Clean、修正项为空、config 原样。
        val document = import(exportGroup(baseConfig()))
        assertEquals(GroupImportReport.Clean, document.importReport)
        assertEquals(emptyList<Any>(), document.cardFixes)
        assertEquals(cards(), document.cards)
    }

    // ---------------- 2. 结构性错误：config 被判掉，消息仍在 ----------------

    @Test
    fun `a duplicated role id makes the config null and the document survives`() {
        val broken = baseConfig().copy(
            roles = listOf(
                GroupRole(id = "a", name = "Alice", assistantId = "asst-a"),
                GroupRole(id = "a", name = "另一个 Alice", assistantId = "asst-x"),
            )
        )
        val document = import(exportGroup(broken))
        val report = document.importReport as? GroupImportReport.Rejected
        assertNotNull("重复 role_id 必须拒收，实际 ${document.importReport}", report)
        assertEquals("群配置校验未通过", report!!.reason)
        assertTrue(
            "必须给出 field 级别错误",
            report.fieldErrors.any { it.field == "roles[].role_id" },
        )
        // 坏配置绝不被交出去
        assertNull("坏配置绝不能交到调用方手上", document.config)
        // 但聊天记录照常拿得到 —— 契约要的是「别造出半个群」，不是「把记录一起扔了」
        assertEquals(nodes().size, document.messages.size)
        assertEquals(GROUP_NAME, document.groupName)
    }

    @Test
    fun `an occupied summary role id makes the config null`() {
        val broken = baseConfig().copy(
            roles = listOf(GroupRole(id = GroupChat.SUMMARY_ID, name = "假的汇总", assistantId = "asst-x"))
        )
        val document = import(exportGroup(broken))
        assertNull(document.config)
        val report = document.importReport as? GroupImportReport.Rejected
        assertNotNull(report)
        assertTrue(report!!.fieldErrors.any { it.field == "roles[].role_id" })
    }

    @Test
    fun `a roundtable config without a chair makes the config null`() {
        val broken = baseConfig(mode = GroupChat.MODE_ROUNDTABLE)
        val document = import(exportGroup(broken))
        assertNull(document.config)
        val report = document.importReport as? GroupImportReport.Rejected
        assertNotNull(report)
        assertTrue(report!!.fieldErrors.any { it.field == "chair_role_id" })
    }

    @Test
    fun `a zero budget makes the config null instead of being silently clamped`() {
        listOf(0, -1, GroupChat.MAX_TOKEN_BUDGET_PER_ROUND + 1).forEach { budget ->
            val document = import(exportGroup(baseConfig(budget = budget)))
            assertNull("budget=$budget 的配置不该被交出去", document.config)
            val report = document.importReport as? GroupImportReport.Rejected
            assertNotNull("budget=$budget 必须拒收，实际 ${document.importReport}", report)
            assertEquals(
                "token_budget_per_round",
                report!!.fieldErrors.single { it.field == "token_budget_per_round" }.field,
            )
        }
    }

    @Test
    fun `a newer schema version makes the config null instead of being read as the current one`() {
        val future = baseConfig().copy(schemaVersion = GroupChat.SCHEMA_VERSION + 1)
        val document = import(exportGroup(future))
        assertNull(document.config)
        val report = document.importReport as? GroupImportReport.Rejected
        assertNotNull("高版本必须拒收，实际 ${document.importReport}", report)
        assertTrue(report!!.fieldErrors.any { it.field == "schema_version" })
    }

    @Test
    fun `a config whose roles key is missing makes the config null`() {
        // `decodeConfigObject` 对缺 roles 的对象返回 null —— 那是「解不出来」，走 Rejected
        // 而不是 NoConfig，否则「没有群配置块」和「群配置坏了」又混成一种。
        val document = importWithRawConfig(buildJsonObject { put("schema_version", 1) })
        assertNull(document.config)
        val report = document.importReport as? GroupImportReport.Rejected
        assertNotNull("解不出来的 config 必须拒收，实际 ${document.importReport}", report)
        assertTrue(report!!.fieldErrors.any { it.field == "config" })
    }

    // ---------------- 3. 越界项：降级 + 记录 ----------------

    @Test
    fun `a non positive revision is normalized and reported as a fix`() {
        val document = import(exportGroup(baseConfig(revision = 0)))
        val report = document.importReport as? GroupImportReport.Normalized
        assertNotNull("revision 越界应降级，实际 ${document.importReport}", report)
        val fix = report!!.fixes.single { it.field == "revision" }
        assertEquals("0", fix.original)
        assertEquals(GroupChat.DEFAULT_REVISION.toString(), fix.corrected)
        // 降级后的配置必须真的合法（否则它建不出会话，降级就白降了）
        assertEquals(GroupChat.DEFAULT_REVISION, requireNotNull(document.config).revision)
        assertEquals(emptyList<GroupConfigError>(), GroupChat.validate(requireNotNull(document.config)))
    }

    @Test
    fun `an unknown tie policy is normalized and reported as a fix`() {
        val document = import(exportGroup(baseConfig(tiePolicy = "coin_flip")))
        val report = document.importReport as? GroupImportReport.Normalized
        assertNotNull("未知 tie_policy 应降级，实际 ${document.importReport}", report)
        val fix = report!!.fixes.single { it.field == "tie_policy" }
        assertEquals("coin_flip", fix.original)
        assertEquals(GroupChat.TIE_FAIL, requireNotNull(document.config).tiePolicy)
    }

    // ---------------- 4. 去重 ----------------

    @Test
    fun `duplicate card role ids are collapsed and the drop is recorded`() {
        val raw = payloadJson(config = baseConfigObject(), cards = """[
            {"role_id":"a","name":"Alice","assistant_id":"asst-a","card_id":"card-a","persona":"第一个"},
            {"role_id":"a","name":"Alice","assistant_id":"asst-a","card_id":"card-a2","persona":"第二个"},
            {"role_id":"b","name":"Bob","assistant_id":"asst-b"}
        ]""")
        val document = import(raw)
        assertEquals(
            listOf("a", "b"),
            document.cards.map { it.roleId },
        )
        assertEquals("第一个", document.cards.first { it.roleId == "a" }.persona)
        val fix = document.cardFixes.single()
        assertEquals("cards[1].role_id", fix.field)
        assertTrue(fix.message, fix.message.contains("保留首条"))
        // 配置没被碰过，所以报告仍然是 Clean：去重与筛查是两件事
        assertEquals(GroupImportReport.Clean, document.importReport)
    }

    @Test
    fun `a card without a role id is dropped because it belongs to nobody`() {
        val raw = payloadJson(
            config = baseConfigObject(),
            cards = """[
                {"role_id":"","name":"孤儿","card_id":"card-x"},
                {"role_id":"a","name":"Alice"}
            ]""",
        )
        val document = import(raw)
        assertEquals(listOf("a"), document.cards.map { it.roleId })
        assertEquals(
            listOf("cards[0].role_id"),
            document.cardFixes.map { it.field },
        )
        assertTrue(document.cardFixes.single().message, document.cardFixes.single().message.contains("丢弃"))
    }

    @Test
    fun `duplicate role ids in cards do not stop a legal config from being kept`() {
        // 去重与筛查必须能同时成立：卡片的重复不该把一份合法的群配置也判掉。
        val raw = payloadJson(
            config = baseConfigObject(),
            cards = """[
                {"role_id":"a","name":"Alice"},
                {"role_id":"a","name":"Alice"}
            ]""",
        )
        val document = import(raw)
        assertEquals(GroupImportReport.Clean, document.importReport)
        assertEquals(baseConfig(), document.config)
        assertEquals(1, document.cards.size)
        assertEquals(1, document.cardFixes.size)
    }

    @Test
    fun `the same card id backing two roles survives dedup`() {
        val raw = payloadJson(
            config = baseConfigObject(),
            cards = """[
                {"role_id":"a","name":"Alice","card_id":"shared"},
                {"role_id":"b","name":"Bob","card_id":"shared"}
            ]""",
        )
        val document = import(raw)
        assertEquals(listOf("a", "b"), document.cards.map { it.roleId })
        assertEquals("共享 card_id 不算重复", emptyList<Any>(), document.cardFixes)
    }

    @Test
    fun `duplicate swipe content is not collapsed because a swipe is a branch`() {
        // 两条文本相同的分支在数据模型里是两个节点（selectIndex 是下标）。合并要重排下标，
        // 并且会改变再导出的字节 —— 契约那句「去重」说的是建会话前的群配置与角色引用，
        // 不是消息分支。这里把「不去重」钉死。
        val twinned = listOf(
            MessageNode(
                messages = listOf(
                    UIMessage.assistant("同一句").copy(roleId = "a", roundId = "r1", turnKind = GroupChat.TURN_SPEAKER),
                    UIMessage.assistant("同一句").copy(roleId = "a", roundId = "r1", turnKind = GroupChat.TURN_SPEAKER),
                ),
                selectIndex = 1,
            )
        )
        val first = TavernChatCodec.exportGroup(
            nodes = twinned,
            config = baseConfig(),
            cards = cards(),
            userName = USER_NAME,
            groupName = GROUP_NAME,
        )
        val document = requireNotNull(TavernChatCodec.importGroup(first))
        val branch = document.messages.single()
        assertEquals("两条相同文本的分支必须都在", 2, branch.node.messages.size)
        assertEquals(1, branch.node.selectIndex)
        assertEquals("分支去重不在这条路径上，修正项应为空", emptyList<Any>(), document.cardFixes)
        // 再导出逐字节相同
        val again = TavernChatCodec.exportGroup(
            nodes = document.messages.map { it.node },
            config = requireNotNull(document.config),
            cards = document.cards,
            userName = document.userName,
            groupName = document.groupName,
        )
        assertEquals(first, again)
    }

    // ---------------- 5. extras 无损往返 ----------------

    @Test
    fun `both layers of unknown fields survive the import gate`() {
        // `decodeConfigObject` 已经兜住 extras（GroupChat.kt 的 KNOWN_CONFIG_KEYS /
        // RESERVED_ROLE_KEYS 过滤），这一条是把这个「已修好的事实」在 importGroup 这条路上
        // 再钉一遍：加了闸门之后别把未知字段顺手丢了。
        val config = baseConfig(
            extras = buildJsonObject {
                put("vendor_note", "群配置层未知字段")
                put("nested", buildJsonObject { put("depth", 2) })
            },
        ).copy(
            roles = listOf(
                GroupRole(
                    id = "a",
                    name = "Alice",
                    assistantId = "asst-a",
                    extras = buildJsonObject {
                        put("tone", "cold")
                        put("vendor_note", "角色层未知字段")
                    },
                ),
            )
        )
        val document = import(exportGroup(config))
        assertEquals(GroupImportReport.Clean, document.importReport)
        val restored = requireNotNull(document.config)
        assertEquals(config.extras, restored.extras)
        assertEquals(config.roles.single().extras, restored.roles.single().extras)
        assertEquals("未知字段必须在往返后原样还在", config, restored)
    }

    // ---------------- 6. config == null 的两种原因必须分得开 ----------------

    @Test
    fun `a file without a config block is NoConfig and not an error`() {
        // 纯酒馆群聊文件：别人导出的文件里根本没有 khatkit_group.config。这是**不是错误**，
        // 不能与「配置被判掉」混成一种 —— 那会让 UI 对着正常的文件弹错误框。
        val document = import(payloadJson(config = null))
        assertNull("没有配置块时 config 应为 null", document.config)
        assertEquals(GroupImportReport.NoConfig, document.importReport)
        assertEquals("消息照常拿得到", 1, document.messages.size)
        assertEquals(GROUP_NAME, document.groupName)
    }

    @Test
    fun `NoConfig and Rejected are different reports even though both give a null config`() {
        val noConfig = importWithRawConfig(null)
        val rejected = importWithRawConfig(buildJsonObject { put("schema_version", 1) })
        assertEquals(GroupImportReport.NoConfig, noConfig.importReport)
        assertTrue(rejected.importReport is GroupImportReport.Rejected)
        assertTrue(
            "两种 config==null 必须能被 importReport 分开",
            noConfig.importReport != rejected.importReport,
        )
    }

    // ---------------- 7. 密钥闸门（恢复路径同样不许把密钥吃进内存） ----------------

    @Test
    fun `a smuggled api key in the group payload makes the config null`() {
        val raw = payloadJson(
            config = buildJsonObject {
                baseConfigObject().forEach { (key, value) -> put(key, value) }
                put("api_key", "sk-should-never-be-read")
            },
        )
        val document = import(raw)
        assertNull(document.config)
        val report = document.importReport as? GroupImportReport.Rejected
        assertNotNull("带密钥的载荷必须拒收，实际 ${document.importReport}", report)
        assertTrue(report!!.reason, report.reason.contains("api_key"))
        assertTrue(report.fieldErrors.any { it.field == "api_key" })
    }

    @Test
    fun `a role level forbidden key also makes the config null`() {
        val raw = payloadJson(
            config = buildJsonObject {
                baseConfigObject().forEach { (key, value) -> put(key, value) }
                put(
                    "roles",
                    buildJsonArray {
                        add(buildJsonObject { put("role_id", "a"); put("assistant_id", "asst-a") })
                        add(
                            buildJsonObject {
                                put("role_id", "b")
                                put("assistant_id", "asst-b")
                                put("memoryContent", "私有记忆")
                            }
                        )
                    },
                )
            },
        )
        val document = import(raw)
        assertNull(document.config)
        val report = document.importReport as? GroupImportReport.Rejected
        assertNotNull("角色层带隐私记忆必须拒收，实际 ${document.importReport}", report)
        assertTrue(report!!.reason, report.reason.contains("memoryContent"))
    }

    @Test
    fun `a clean group payload carries no forbidden key so the gate stays open`() {
        val raw = payloadJson(config = baseConfigObject(), cards = """[{"role_id":"a","name":"Alice"}]""")
        val payload = json.parseToJsonElement(raw.split("\n").first())
            .jsonObject[TavernChatCodec.GROUP_FIELD]!!.jsonObject
        assertEquals("干净载荷不该被密钥闸门拦下", GroupImportReport.Clean, import(raw).importReport)
        assertEquals(emptySet<String>(), GroupChat.findForbiddenKeys(payload))
    }

    // ---------------- fixture ----------------

    private fun import(raw: String): TavernGroupChatDocument =
        requireNotNull(TavernChatCodec.importGroup(raw)) { "群聊文件导不出来：$raw" }

    private fun exportGroup(config: GroupConfig): String = TavernChatCodec.exportGroup(
        nodes = nodes(),
        config = config,
        cards = cards(),
        userName = USER_NAME,
        groupName = GROUP_NAME,
    )

    private fun baseConfig(
        roles: List<GroupRole> = listOf(
            GroupRole(id = "a", name = "Alice", assistantId = "asst-a"),
            GroupRole(id = "b", name = "Bob", assistantId = "asst-b"),
            GroupRole(id = "c", name = "Cara", assistantId = "asst-c", chair = true),
        ),
        mode: String = GroupChat.MODE_PIPELINE,
        chairRoleId: String? = null,
        budget: Int = 4_000,
        revision: Int = 3,
        tiePolicy: String = GroupChat.TIE_FAIL,
        schemaVersion: Int = GroupChat.SCHEMA_VERSION,
        extras: JsonObject = JsonObject(emptyMap()),
    ) = GroupConfig(
        roles = roles,
        mode = mode,
        chairRoleId = chairRoleId,
        tokenBudgetPerRound = budget,
        revision = revision,
        tiePolicy = tiePolicy,
        schemaVersion = schemaVersion,
        extras = extras,
    )

    private fun baseConfigObject(): JsonObject = GroupChat.encodeConfigObject(baseConfig())

    private fun cards() = listOf(
        RoleCardMeta(roleId = "a", name = "Alice", assistantId = "asst-a", cardId = "card-a", persona = "画师 A"),
        RoleCardMeta(roleId = "b", name = "Bob", assistantId = "asst-b", cardId = "card-b"),
        RoleCardMeta(roleId = "c", name = "Cara", assistantId = "asst-c"),
    )

    private fun nodes(): List<MessageNode> = listOf(
        MessageNode.of(
            UIMessage.user("群聊导出").copy(roundId = "r1", turnKind = GroupChat.TURN_USER)
        ),
        MessageNode.of(
            UIMessage.assistant("Alice 发言").copy(
                roleId = "a",
                roundId = "r1",
                turnKind = GroupChat.TURN_SPEAKER,
            )
        ),
        MessageNode.of(
            UIMessage.assistant("Cara 收尾").copy(
                roleId = "c",
                roundId = "r1",
                turnKind = GroupChat.TURN_CHAIR,
                mentionRoleIds = listOf("a"),
            )
        ),
    )

    /** 手拼一份群聊文件：`config` 传 null 表示「文件里没有配置块」。 */
    private fun payloadJson(config: JsonObject?, cards: String? = null): String {
        val payload = buildJsonObject {
            put("kind", GroupChat.QR_KIND)
            put("schema_version", GroupChat.SCHEMA_VERSION)
            put("name", GROUP_NAME)
            config?.let { put("config", it) }
            cards?.let { put("cards", json.parseToJsonElement(it)) }
        }
        val header = buildJsonObject {
            put("spec", "st_chat_v1")
            put("user_name", USER_NAME)
            put("character_name", GROUP_NAME)
            put("chat_metadata", buildJsonObject { put("is_group", true) })
            put(TavernChatCodec.GROUP_FIELD, payload)
        }
        val message = buildJsonObject {
            put("name", "Alice")
            put("is_user", false)
            put("is_system", false)
            put("mes", JsonPrimitive("Alice 发言"))
            put(TavernChatCodec.FIELD_ROLE_ID, JsonPrimitive("a"))
            put(TavernChatCodec.FIELD_ROUND_ID, JsonPrimitive("r1"))
            put(TavernChatCodec.FIELD_TURN_KIND, JsonPrimitive(GroupChat.TURN_SPEAKER))
            put(TavernChatCodec.FIELD_MENTION_ROLE_IDS, buildJsonArray { })
        }
        return listOf(header, message)
            .joinToString("\n") {
                json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), it)
            }
    }

    /** 只带一条最小消息，够 `import` 认出「有表头 + 有消息」。 */
    private fun importWithRawConfig(config: JsonObject?): TavernGroupChatDocument =
        import(payloadJson(config))

    private companion object {
        const val USER_NAME = "阿达"
        const val GROUP_NAME = "三人议事厅"
    }
}