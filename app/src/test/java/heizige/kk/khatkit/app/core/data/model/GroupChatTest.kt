package heizige.kk.khatkit.app.core.data.model

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatTest {
    /** 拼 JSON 片段用的引号占位，避免一堆四连引号把 raw string 边界搞糊。 */
    private val Q = "\""

    private val alice = GroupRole("a", "Alice", "asst-a")
    private val bob = GroupRole("b", "Bob", "asst-b")
    private val cara = GroupRole("c", "Cara", "asst-c", chair = true)
    private val roles = listOf(alice, bob, cara)

    @Test
    fun `mention routes only the named role and does not leak to the third`() {
        val mentions = GroupChat.parseMentions("@Bob 看一下", roles)
        assertEquals(listOf("b"), mentions)
        val messages = listOf(
            UIMessage.user("@Bob 看一下").copy(mentionRoleIds = mentions),
            UIMessage.assistant("Bob 的回复").copy(roleId = "b"),
            UIMessage.assistant("Alice 的私话").copy(roleId = "a"),
        )
        val bobSees = GroupChat.visibleMessages(config(GroupChat.MODE_VOTE), messages, "b").map { it.toText() }
        val caraSees = GroupChat.visibleMessages(config(GroupChat.MODE_VOTE), messages, "c").map { it.toText() }
        assertTrue(bobSees.any { it.contains("Bob 的回复") })
        assertFalse(bobSees.any { it.contains("Alice 的私话") })
        assertTrue(caraSees.any { it.contains("@Bob") })
        assertFalse(caraSees.any { it.contains("Bob 的回复") })
        assertFalse(caraSees.any { it.contains("Alice 的私话") })
    }

    @Test
    fun `pipeline hands only the previous role output to the next`() {
        val plan = GroupChat.plan(config(GroupChat.MODE_PIPELINE), emptyList())
        assertEquals(listOf("a", "b", "c"), plan.map { it.role.id })
        assertEquals("a", plan[1].predecessorId)
        val messages = listOf(
            UIMessage.user("开始"),
            UIMessage.assistant("Alice 说").copy(roleId = "a"),
        )
        val bobSees = GroupChat.visibleMessages(
            config(GroupChat.MODE_PIPELINE),
            messages,
            "b",
            predecessorId = "a",
        ).map { it.toText() }
        val caraSees = GroupChat.visibleMessages(
            config(GroupChat.MODE_PIPELINE),
            messages,
            "c",
        ).map { it.toText() }
        assertTrue(bobSees.any { it.contains("Alice 说") })
        assertFalse(caraSees.any { it.contains("Alice 说") })
    }

    @Test
    fun `roundtable chair sees the round and vote uses majority`() {
        val plan = GroupChat.plan(config(GroupChat.MODE_ROUNDTABLE), emptyList())
        assertEquals("c", plan.last().role.id)
        assertTrue(plan.last().chairRound)
        val messages = listOf(
            UIMessage.user("选题"),
            UIMessage.assistant("甲").copy(roleId = "a"),
            UIMessage.assistant("乙").copy(roleId = "b"),
        )
        val chairSees = GroupChat.visibleMessages(
            config(GroupChat.MODE_ROUNDTABLE),
            messages,
            "c",
            chairRound = true,
        ).map { it.toText() }
        assertTrue(chairSees.any { it.contains("甲") })
        assertTrue(chairSees.any { it.contains("乙") })
        // 投票改成结构化选票：正文里的 `VOTE:<候选id>|<理由>` 才是票，候选集外的行不算票。
        val candidates = listOf("甲", "乙")
        assertNull(GroupChat.parseBallot("VOTE:丙|不在候选集", "a", candidates))
        val ballots = listOf("a" to "甲", "b" to "甲", "c" to "乙")
            .mapNotNull { (roleId, picked) -> GroupChat.parseBallot("VOTE:$picked", roleId, candidates) }
        val decided = GroupChat.tally(ballots, candidates, GroupChat.TIE_FAIL)
        assertTrue(decided is VoteOutcome.Decided)
        assertEquals("甲", (decided as VoteOutcome.Decided).winner)
        // 甲乙各一票：没有多数，平票按 fail 判本轮失败，不产出胜者。
        val tied = GroupChat.tally(
            listOf(VoteBallot("a", "甲"), VoteBallot("b", "乙")),
            candidates,
            GroupChat.TIE_FAIL,
        )
        assertTrue(tied is VoteOutcome.Tie)
        // 平票名单按 id 字典序返回，不依赖中文码位顺序，所以按集合断言。
        assertEquals(2, (tied as VoteOutcome.Tie).candidates.size)
        assertEquals(setOf("甲", "乙"), tied.candidates.toSet())
    }

    @Test
    fun `budget stops later speakers and keeps earlier output`() {
        val remaining = listOf("c")
        assertEquals(
            RoundBudget.Continue,
            GroupChat.budgetDecision(spent = 0, limit = 100, remainingRoleIds = remaining),
        )
        assertEquals(
            RoundBudget.Continue,
            GroupChat.budgetDecision(spent = 80, limit = 100, remainingRoleIds = remaining),
        )
        val stopped = GroupChat.budgetDecision(spent = 100, limit = 100, remainingRoleIds = remaining)
        assertTrue(stopped is RoundBudget.Stop)
        assertEquals(remaining, (stopped as RoundBudget.Stop).skippedRoleIds)
        // 新契约把 limit<=0 视为「不限预算」而不是「立刻停」；这种配置由 validate 直接拒收。
        assertEquals(
            RoundBudget.Continue,
            GroupChat.budgetDecision(spent = 0, limit = 0, remainingRoleIds = remaining),
        )
        assertTrue(
            GroupChat.validate(config(GroupChat.MODE_PIPELINE).copy(tokenBudgetPerRound = 0))
                .any { it.field == "token_budget_per_round" },
        )
        assertEquals(listOf("c"), GroupChat.pendingSpeakers(
            GroupChat.plan(config(GroupChat.MODE_PIPELINE), emptyList()),
            setOf("a", "b"),
        ).map { it.role.id })
    }

    @Test
    fun `qr round trip and per role memory spaces`() {
        val config = config(GroupChat.MODE_PIPELINE).copy(tokenBudgetPerRound = 400)
        val restored = GroupChat.decodeQr(GroupChat.encodeQr(config))
        assertEquals(config, restored)
        assertNull(GroupChat.decodeQr("""{"kind":"other"}"""))
        val spaces = roles.map { GroupChat.memorySpaceId("conv-1", it.id) }
        assertEquals(3, spaces.toSet().size)
        assertTrue(spaces.all { it.startsWith("group:conv-1:role:") })
    }

    @Test
    fun `list filter keeps the other type in the source`() {
        val source = listOf(GroupChat.TYPE_DIRECT, GroupChat.TYPE_GROUP, GroupChat.TYPE_DIRECT)
        val groups = GroupChat.filterType(source, GroupChat.TYPE_GROUP)
        assertEquals(listOf(GroupChat.TYPE_GROUP), groups)
        assertEquals(3, source.size)
        assertEquals(source, GroupChat.filterType(source, GroupChat.FILTER_ALL))
    }

    @Test
    fun `role level unknown fields survive a full round trip`() {
        val raw = """
            {
              "schema_version": 1,
              "mode": "pipeline",
              "token_budget_per_round": 1000,
              "roles": [
                {
                  "role_id": "a",
                  "name": "Alice",
                  "assistant_id": "asst-a",
                  "persona": {"tone": "cold", "taboos": ["emoji"]},
                  "warmup_lines": ["你好", "在吗"],
                  "sampled_seed": 42
                }
              ]
            }
        """.trimIndent()
        val decoded = GroupChat.decodeConfig(raw) ?: error("配置未解出")
        val extras = decoded.roles.single().extras
        // 未知键一个都不能少，嵌套对象与数组都要原样留住。
        assertEquals(setOf("persona", "warmup_lines", "sampled_seed"), extras.keys)
        assertEquals(
            buildJsonObject {
                put("tone", "cold")
                put("taboos", JsonArray(listOf(JsonPrimitive("emoji"))))
            },
            extras["persona"],
        )
        assertEquals(JsonArray(listOf(JsonPrimitive("你好"), JsonPrimitive("在吗"))), extras["warmup_lines"])
        assertEquals(JsonPrimitive(42), extras["sampled_seed"])
        // 平铺回角色顶层后再解一次，整份配置与 extras 都必须一模一样（无损往返闭环）。
        val again = GroupChat.decodeConfig(GroupChat.encodeConfig(decoded)) ?: error("配置未解出")
        assertEquals(decoded, again)
        assertEquals(extras, again.roles.single().extras)
    }

    @Test
    fun `several unknown role keys of mixed types all survive`() {
        val decoded = GroupChat.decodeConfig(
            """
            {"roles":[{"role_id":"a","assistant_id":"asst-a","retry":{"max":2,"backoff_ms":[100,200]},
            "enabled":true,"note":null,"tags":["x","y"],"legacy_blob":{"deep":{"k":"v"}}}]}
            """.trimIndent()
        ) ?: error("配置未解出")
        val extras = decoded.roles.single().extras
        assertEquals(5, extras.size)
        assertEquals(setOf("retry", "enabled", "note", "tags", "legacy_blob"), extras.keys)
        assertEquals(JsonPrimitive(true), extras["enabled"])
        assertEquals(JsonArray(listOf(JsonPrimitive("x"), JsonPrimitive("y"))), extras["tags"])
        assertEquals(decoded, GroupChat.decodeConfig(GroupChat.encodeConfig(decoded)))
    }

    @Test
    fun `known role fields are not damaged by unknown keys`() {
        val decoded = GroupChat.decodeConfig(
            """
            {"roles":[{"role_id":"a","name":"Alice","assistant_id":"asst-a","chair":true,
            "card_id":"card-1","model_id":"m-1","memory_space_id":"group:conv-1:role:a",
            "tool_package_ids":["p1"],"skill_ids":["s1","s2"],"mcp_server_ids":["mcp1"],
            "mood":"calm"}]}
            """.trimIndent()
        ) ?: error("配置未解出")
        val role = decoded.roles.single()
        assertEquals("a", role.id)
        assertEquals("Alice", role.name)
        assertEquals("asst-a", role.assistantId)
        assertTrue(role.chair)
        assertEquals("card-1", role.cardId)
        assertEquals("m-1", role.modelId)
        assertEquals("group:conv-1:role:a", role.memorySpaceId)
        assertEquals(listOf("p1"), role.toolPackageIds)
        assertEquals(listOf("s1", "s2"), role.skillIds)
        assertEquals(listOf("mcp1"), role.mcpServerIds)
        // 未知键只进 extras，已知字段一个都不许被顶掉。
        assertEquals(setOf("mood"), role.extras.keys)
        assertEquals(GroupChat.memorySpaceId("conv-1", "a"), role.memorySpaceId)
    }

    @Test
    fun `legacy camelCase role keys land on known fields and never duplicate into extras`() {
        val decoded = GroupChat.decodeConfig(
            """
            {"roles":[{"id":"a","name":"Alice","assistantId":"asst-a","chair":false,
            "cardId":"card-1","modelId":"m-1","memorySpaceId":"group:conv-1:role:a",
            "toolPackageIds":["p1"],"skillIds":["s1"],"mcpServerIds":["mcp1"],
            "temperature":0.7}]}
            """.trimIndent()
        ) ?: error("配置未解出")
        val role = decoded.roles.single()
        assertEquals("a", role.id)
        assertEquals("asst-a", role.assistantId)
        assertEquals("card-1", role.cardId)
        assertEquals("m-1", role.modelId)
        assertEquals("group:conv-1:role:a", role.memorySpaceId)
        assertEquals(listOf("p1"), role.toolPackageIds)
        assertEquals(listOf("s1"), role.skillIds)
        assertEquals(listOf("mcp1"), role.mcpServerIds)
        // 旧键只算一次：值搬到契约键后旧键本身不能又落进 extras。
        assertEquals(setOf("temperature"), role.extras.keys)
        // 归一化之后再往返，extras 仍然只有那一个真未知键。
        val again = GroupChat.decodeConfig(GroupChat.encodeConfig(decoded)) ?: error("配置未解出")
        assertEquals(setOf("temperature"), again.roles.single().extras.keys)
    }

    @Test
    fun `unknown and invalid schema versions are rejected`() {
        val base = config(GroupChat.MODE_PIPELINE)
        // 高于当前支持版本：不能盲解，整份配置判非法。
        val future = GroupChat.validate(base.copy(schemaVersion = GroupChat.SCHEMA_VERSION + 1))
        assertTrue(future.any { it.field == "schema_version" })
        assertFalse(GroupChat.isValid(base.copy(schemaVersion = GroupChat.SCHEMA_VERSION + 1)))
        assertTrue(future.first { it.field == "schema_version" }.message.contains("${GroupChat.SCHEMA_VERSION}"))
        // 0 与负数同样拒收。
        assertTrue(GroupChat.validate(base.copy(schemaVersion = 0)).any { it.field == "schema_version" })
        assertTrue(GroupChat.validate(base.copy(schemaVersion = -7)).any { it.field == "schema_version" })
        // 当前版本本身不该被版本闸门误伤。
        assertFalse(GroupChat.validate(base).any { it.field == "schema_version" })
    }

    @Test
    fun `role extras survive the qr share payload end to end`() {
        val config = config(GroupChat.MODE_PIPELINE).copy(
            tokenBudgetPerRound = 400,
            roles = listOf(
                alice.copy(
                    extras = buildJsonObject {
                        put("tone", "cold")
                        put("taboos", JsonArray(listOf(JsonPrimitive("emoji"))))
                    },
                ),
            ),
        )
        val payload = GroupChat.decodeSharePayload(GroupChat.encodeQr(config))
        assertEquals(config, payload?.config)
        assertEquals(
            config.roles.single().extras,
            payload?.config?.roles?.single()?.extras,
        )
        assertEquals(config, GroupChat.decodeQr(GroupChat.encodeQr(config)))
    }

    @Test
    fun `importShare rejects a payload from a newer schema version and names the supported one`() {
        val future = GroupChat.SCHEMA_VERSION + 1
        val result = GroupChat.importShare(payloadJson(future))
        assertTrue(result is GroupImportResult.Rejected)
        val rejected = result as GroupImportResult.Rejected
        // reason 必须写明「载荷版本 X，当前只支持到 Y」，两个版本号都要在里面。
        assertTrue(rejected.reason.contains("$future"))
        assertTrue(rejected.reason.contains("${GroupChat.SCHEMA_VERSION}"))
        assertTrue(rejected.fieldErrors.any { it.field == "schema_version" })
        // 口径与 validate 一致：validate 同样判非法。
        assertTrue(
            GroupChat.validate(config(GroupChat.MODE_PIPELINE).copy(schemaVersion = future))
                .any { it.field == "schema_version" },
        )
    }

    @Test
    fun `importShare rejects zero and negative schema versions`() {
        listOf(0, -1, -7).forEach { bad ->
            val result = GroupChat.importShare(payloadJson(bad))
            assertTrue("schema_version=$bad 应被拒", result is GroupImportResult.Rejected)
            val rejected = result as GroupImportResult.Rejected
            assertTrue(rejected.fieldErrors.any { it.field == "schema_version" })
            assertTrue(
                GroupChat.validate(config(GroupChat.MODE_PIPELINE).copy(schemaVersion = bad))
                    .any { it.field == "schema_version" },
            )
        }
    }

    @Test
    fun `importShare keeps the role cards that decodeQr drops`() {
        val config = config(GroupChat.MODE_PIPELINE).copy(roles = listOf(alice, bob))
        val cards = listOf(
            RoleCardMeta(roleId = "a", name = "Alice", assistantId = "asst-a", persona = "冷面顾问"),
            RoleCardMeta(roleId = "b", name = "Bob", assistantId = "asst-b", persona = "热血解说"),
        )
        val raw = GroupChat.encodeQr(config, cards)

        // 这是 B1 的核心证据：导入方拿得到角色卡元数据。
        val result = GroupChat.importShare(raw)
        assertTrue(result is GroupImportResult.Accepted)
        val payload = (result as GroupImportResult.Accepted).payload
        assertEquals(2, payload.cards.size)
        assertEquals(listOf("a", "b"), payload.cards.map { it.roleId })
        assertEquals(listOf("Alice", "Bob"), payload.cards.map { it.name })
        assertEquals(listOf("asst-a", "asst-b"), payload.cards.map { it.assistantId })
        assertEquals(listOf("冷面顾问", "热血解说"), payload.cards.map { it.persona })
        // 整份 cards 逐字段等价：cardId / avatarRef 为 null 时不许被读成字符串 "null"。
        assertEquals(cards, payload.cards)
        // config 也照样回来了。
        assertEquals(config, payload.config)
        // 反证：老解码器 decodeQr 拿得到 config，但 cards 全被丢掉。
        assertEquals(config, GroupChat.decodeQr(raw))
    }

    @Test
    fun `importShare rejects wrong kind empty string and malformed json`() {
        assertTrue(GroupChat.importShare("") is GroupImportResult.Rejected)
        assertTrue(GroupChat.importShare("   ") is GroupImportResult.Rejected)
        assertTrue(GroupChat.importShare("not json at all") is GroupImportResult.Rejected)
        assertTrue(GroupChat.importShare("""{"kind":""") is GroupImportResult.Rejected)
        assertTrue(GroupChat.importShare("[1,2,3]") is GroupImportResult.Rejected)
        // kind 不是自家的：合法 JSON 也不行。
        val wrongKind = GroupChat.importShare("""{"kind":"other","schema_version":1}""")
        assertTrue(wrongKind is GroupImportResult.Rejected)
        assertEquals("不是 KhatKit 群聊分享载荷", (wrongKind as GroupImportResult.Rejected).reason)
        // 空串与脏 JSON 的 reason 要能让人看懂不是「校验失败」。
        assertTrue(
            (GroupChat.importShare("") as GroupImportResult.Rejected).reason.contains("无法解析"),
        )
    }

    @Test
    fun `importShare rejects a payload carrying a forbidden key`() {
        // api_key 在 FORBIDDEN_EXPORT_KEYS 名单里，藏在 config 的未知键（落进 extras）里。
        // extraConfigKeys 由调用方自带前导逗号（见 payloadJson 的 roles 之后那一行）。
        val apiKeyPair = ",\n" + Q + "api_key" + Q + ":" + Q + "sk-should-never-be-read" + Q
        val leaked = GroupChat.importShare(
            payloadJson(GroupChat.SCHEMA_VERSION, extraConfigKeys = apiKeyPair)
        )
        assertTrue(leaked is GroupImportResult.Rejected)
        val rejected = leaked as GroupImportResult.Rejected
        // reason 里必须点名那个键，否则用户不知道该删什么。
        assertTrue(rejected.reason.contains("api_key"))
        // 同名单里的其它键、且藏在角色层也一样拦住。
        val leakedInRole = GroupChat.importShare(
            payloadJson(
                GroupChat.SCHEMA_VERSION,
                roles = """[{"role_id":"a","name":"Alice","assistant_id":"asst-a","refresh_token":"rt-1"}]""",
            )
        )
        assertTrue(leakedInRole is GroupImportResult.Rejected)
        assertTrue((leakedInRole as GroupImportResult.Rejected).reason.contains("refresh_token"))
    }

    @Test
    fun `importShare reports field level errors for an invalid config`() {
        // roles 为空。
        val noRoles = GroupChat.importShare(
            payloadJson(GroupChat.SCHEMA_VERSION, roles = "[]")
        )
        assertTrue(noRoles is GroupImportResult.Rejected)
        var rejected = noRoles as GroupImportResult.Rejected
        assertEquals("群配置校验未通过", rejected.reason)
        assertTrue(rejected.fieldErrors.isNotEmpty())
        assertTrue(rejected.fieldErrors.any { it.field == "roles" })

        // 每轮预算超上限。
        val tooBig = GroupChat.importShare(
            payloadJson(
                GroupChat.SCHEMA_VERSION,
                extraConfigKeys = ""","token_budget_per_round":${GroupChat.MAX_TOKEN_BUDGET_PER_ROUND + 1}""",
            )
        )
        assertTrue(tooBig is GroupImportResult.Rejected)
        rejected = tooBig as GroupImportResult.Rejected
        assertEquals("群配置校验未通过", rejected.reason)
        assertTrue(rejected.fieldErrors.isNotEmpty())
        // field 必须是契约 snake_case 键，不是 Kotlin 属性名。
        val budgetError = rejected.fieldErrors.firstOrNull { it.field == "token_budget_per_round" }
        assertTrue("field 应为契约键 token_budget_per_round", budgetError != null)
        assertTrue(budgetError!!.message.contains("${GroupChat.MAX_TOKEN_BUDGET_PER_ROUND}"))
        // 每个 field 都得是契约里的小写 snake_case，不许冒出 camelCase。
        rejected.fieldErrors.forEach { error ->
            assertTrue(
                "field 应为 snake_case：${error.field}",
                error.field.none { it.isUpperCase() },
            )
        }
    }

    /**
     * 造一份指定 `schema_version` 的分享载荷。
     *
     * 不能用 [GroupChat.encodeQr]：它总是写当前版本，改不了版本号，也塞不进
     * 违规键（命中密钥黑名单会直接抛）。
     *
     * @param extraConfigKeys 追加到 `config` 末尾的原始 JSON 片段，**自带前导逗号**。
     */
    private fun payloadJson(
        schemaVersion: Int,
        roles: String = """[{"role_id":"a","name":"Alice","assistant_id":"asst-a"}]""",
        extraConfigKeys: String = "",
    ): String = """
        {
          "kind": "${GroupChat.QR_KIND}",
          "schema_version": $schemaVersion,
          "config": {
            "schema_version": $schemaVersion,
            "mode": "pipeline",
            "token_budget_per_round": 1000,
            "revision": 1,
            "tie_policy": "fail",
            "roles": $roles
            $extraConfigKeys
          }
        }
    """.trimIndent()

    private fun config(mode: String) = GroupConfig(
        roles = roles,
        mode = mode,
        tokenBudgetPerRound = 1000,
    )
}
