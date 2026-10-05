package heizige.kk.khatkit.app.core.data.model

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入筛查（[GroupChat.screenImportedConfig]）与角色卡去重（[GroupChat.dedupeCards]）的
 * 纯函数测试。
 *
 * 契约出处：`docs/beyond-orit-client-changes.md:205`
 * > 导入先 schema 校验与去重，再创建新 conversation；恢复失败不留下半成品会话。
 *
 * 上一版 `TavernChatCodec.importGroup` 两件事都没做，于是「导入群配置」在仓库里有两条口径：
 * `GroupChat.importShare` 第 5 道闸门跑 `validate` 并拒收，`importGroup` 直接把解出来的
 * config 交出去。本类把「该拒的拒 / 该归一化的归一化」钉死，并额外钉一条**结构性不变式**：
 *
 * > `screenImportedConfig` 放行的（`Accepted` / `Normalized`）配置，`validate` 必须也判它合法。
 *
 * 那条不变式是「降级」这件事唯一的保险：降级只允许把非法变成合法，绝不可能变成一条
 * 「保存路径拦住、导入却放行」的旁路——正是 `GroupChat.schemaVersionErrors` 的 KDoc 警告的漂移。
 */
class GroupImportScreeningTest {

    private val alice = GroupRole("a", "Alice", "asst-a")
    private val bob = GroupRole("b", "Bob", "asst-b")
    private val cara = GroupRole("c", "Cara", "asst-c", chair = true)

    /** 基准配置：合法，且只碰 `revision` / `tie_policy` 之外的字段都规整。 */
    private fun base(
        roles: List<GroupRole> = listOf(alice, bob, cara),
        mode: String = GroupChat.MODE_PIPELINE,
        chairRoleId: String? = null,
        budget: Int = 4_000,
        revision: Int = 3,
        tiePolicy: String = GroupChat.TIE_FAIL,
        schemaVersion: Int = GroupChat.SCHEMA_VERSION,
    ) = GroupConfig(
        roles = roles,
        mode = mode,
        chairRoleId = chairRoleId,
        tokenBudgetPerRound = budget,
        revision = revision,
        tiePolicy = tiePolicy,
        schemaVersion = schemaVersion,
    )

    // ---------------- 通过 ----------------

    @Test
    fun `a clean config passes unchanged and is not rewritten`() {
        val config = base()
        val screening = GroupChat.screenImportedConfig(config)
        assertTrue("合法配置必须 Accepted，实际 $screening", screening is GroupChat.GroupConfigScreening.Accepted)
        // 必须是同一个实例：合法文件一个字节都不许动，往返不变式就靠这一条。
        assertTrue(config === (screening as GroupChat.GroupConfigScreening.Accepted).config)
        assertEquals(emptyList<GroupConfigError>(), GroupChat.validate(config))
    }

    @Test
    fun `a roundtable config whose chair is a member passes`() {
        val screening = GroupChat.screenImportedConfig(
            base(mode = GroupChat.MODE_ROUNDTABLE, chairRoleId = "c")
        )
        assertTrue(screening is GroupChat.GroupConfigScreening.Accepted)
    }

    // ---------------- 结构性错误：拒绝 ----------------

    @Test
    fun `a duplicated role id is rejected with a field level error`() {
        val screening = GroupChat.screenImportedConfig(
            base(roles = listOf(alice, GroupRole("a", "另一个 Alice", "asst-x"), cara))
        )
        val rejected = screening as? GroupChat.GroupConfigScreening.Rejected
        assertNotNull("重复 role_id 必须拒绝，实际 $screening", rejected)
        val error = rejected!!.fieldErrors.single { it.field == "roles[].role_id" }
        assertTrue(error.message, error.message.contains("role_id 重复"))
        assertTrue("错误信息要指名道姓，不能只说「非法」", error.message.contains("a"))
    }

    @Test
    fun `a duplicated role id is never collapsed into one role`() {
        // 反向钉法：去重会**丢掉一个成员**（导入 3 人群静默变 2 人），所以这里必须是拒绝而不是
        // 「保留首条」。本断言在实现退化成去重时会红。
        val screening = GroupChat.screenImportedConfig(
            base(roles = listOf(alice, GroupRole("a", "另一个 Alice", "asst-x"), cara))
        )
        assertTrue(screening is GroupChat.GroupConfigScreening.Rejected)
    }

    @Test
    fun `a role id equal to the reserved summary id is rejected`() {
        val screening = GroupChat.screenImportedConfig(
            base(roles = listOf(alice, GroupRole(GroupChat.SUMMARY_ID, "假的汇总", "asst-x"), cara))
        )
        val rejected = screening as? GroupChat.GroupConfigScreening.Rejected
        assertNotNull("占用保留 role_id 必须拒绝，实际 $screening", rejected)
        assertTrue(rejected!!.fieldErrors.any { it.field == "roles[].role_id" })
    }

    @Test
    fun `an empty roles list is rejected`() {
        val screening = GroupChat.screenImportedConfig(base(roles = emptyList()))
        val rejected = screening as? GroupChat.GroupConfigScreening.Rejected
        assertNotNull("roles 为空必须拒绝，实际 $screening", rejected)
        assertEquals("roles", rejected!!.fieldErrors.single { it.field == "roles" }.field)
    }

    @Test
    fun `a blank assistant reference is rejected`() {
        val screening = GroupChat.screenImportedConfig(
            base(roles = listOf(alice, GroupRole("b", "Bob", ""), cara))
        )
        val rejected = screening as? GroupChat.GroupConfigScreening.Rejected
        assertNotNull("缺助手引用必须拒绝，实际 $screening", rejected)
        assertTrue(rejected!!.fieldErrors.any { it.field == "roles[1].assistant_id" })
    }

    @Test
    fun `a roundtable config without a chair is rejected`() {
        val screening = GroupChat.screenImportedConfig(base(mode = GroupChat.MODE_ROUNDTABLE))
        val rejected = screening as? GroupChat.GroupConfigScreening.Rejected
        assertNotNull("roundtable 缺议长必须拒绝，实际 $screening", rejected)
        assertTrue(rejected!!.fieldErrors.any { it.field == "chair_role_id" })
    }

    @Test
    fun `a roundtable config whose chair is not a member is rejected`() {
        val screening = GroupChat.screenImportedConfig(
            base(mode = GroupChat.MODE_ROUNDTABLE, chairRoleId = "z")
        )
        val rejected = screening as? GroupChat.GroupConfigScreening.Rejected
        assertNotNull("议长不在成员里必须拒绝，实际 $screening", rejected)
        assertTrue(rejected!!.fieldErrors.any { it.field == "chair_role_id" })
    }

    @Test
    fun `an unknown mode is rejected rather than defaulted to pipeline`() {
        // 默认成 pipeline 会静默丢掉 vote_candidates：用户看不出自己导入的群被换了玩法。
        val screening = GroupChat.screenImportedConfig(base(mode = "committee"))
        val rejected = screening as? GroupChat.GroupConfigScreening.Rejected
        assertNotNull("未知 mode 必须拒绝，实际 $screening", rejected)
        assertEquals("mode", rejected!!.fieldErrors.single { it.field == "mode" }.field)
    }

    // ---------------- 版本闸门：拒绝，绝不降级 ----------------

    @Test
    fun `a newer schema version is rejected instead of being read as the current one`() {
        val future = base(schemaVersion = GroupChat.SCHEMA_VERSION + 1)
        val screening = GroupChat.screenImportedConfig(future)
        val rejected = screening as? GroupChat.GroupConfigScreening.Rejected
        assertNotNull("高版本必须拒绝，实际 $screening", rejected)
        val error = rejected!!.fieldErrors.single { it.field == "schema_version" }
        assertTrue(error.message, error.message.contains("拒绝盲解"))
        // 降级成当前版本就是把高版本字段**盲解**一遍，正是 `schemaVersionErrors` 的 KDoc
        // 禁止的漂移（「保存拦住了、导入却放行」）。这里钉死它不许发生。
        assertTrue("schema_version 绝不能被归一化", screening !is GroupChat.GroupConfigScreening.Normalized)
    }

    @Test
    fun `a zero or negative schema version is rejected`() {
        listOf(0, -1).forEach { version ->
            val screening = GroupChat.screenImportedConfig(base(schemaVersion = version))
            val rejected = screening as? GroupChat.GroupConfigScreening.Rejected
            assertNotNull("schema_version=$version 必须拒绝，实际 $screening", rejected)
            assertTrue(rejected!!.fieldErrors.any { it.field == "schema_version" })
        }
    }

    // ---------------- 预算：拒绝（契约明文「配置保存失败」） ----------------

    @Test
    fun `a zero negative or over cap budget is rejected and never silently rewritten`() {
        listOf(0, -1, GroupChat.MAX_TOKEN_BUDGET_PER_ROUND + 1).forEach { budget ->
            val screening = GroupChat.screenImportedConfig(base(budget = budget))
            val rejected = screening as? GroupChat.GroupConfigScreening.Rejected
            assertNotNull(
                "token_budget_per_round=$budget 必须拒绝，实际 $screening",
                rejected,
            )
            assertEquals(
                "token_budget_per_round",
                rejected!!.fieldErrors.single { it.field == "token_budget_per_round" }.field,
            )
            assertTrue(
                "预算不许被夹到合法区间（夹到 1 会让群聊发不出话，夹到上限是成本事故）：$screening",
                screening !is GroupChat.GroupConfigScreening.Normalized,
            )
        }
    }

    // ---------------- 越界项：降级 + 记录 ----------------

    @Test
    fun `a non positive revision is normalized and the fix records the original value`() {
        val screening = GroupChat.screenImportedConfig(base(revision = 0))
        val normalized = screening as? GroupChat.GroupConfigScreening.Normalized
        assertNotNull("revision 越界应降级，实际 $screening", normalized)
        assertEquals(GroupChat.DEFAULT_REVISION, normalized!!.config.revision)
        val fix = normalized.fixes.single { it.field == "revision" }
        // 改前值必须留在修正项里：悄悄改掉用户看得见的字段而只说「已修正」，等于把改写伪装成无损。
        assertEquals("0", fix.original)
        assertEquals(GroupChat.DEFAULT_REVISION.toString(), fix.corrected)
        assertTrue(fix.message, fix.message.contains("归一化"))
    }

    @Test
    fun `an unknown tie policy is normalized to the contract default`() {
        val screening = GroupChat.screenImportedConfig(base(tiePolicy = "coin_flip"))
        val normalized = screening as? GroupChat.GroupConfigScreening.Normalized
        assertNotNull("未知 tie_policy 应降级，实际 $screening", normalized)
        assertEquals(GroupChat.TIE_FAIL, normalized!!.config.tiePolicy)
        val fix = normalized.fixes.single { it.field == "tie_policy" }
        assertEquals("coin_flip", fix.original)
        assertEquals(GroupChat.TIE_FAIL, fix.corrected)
    }

    @Test
    fun `both normalizable fields at once produce one fix each and still validate clean`() {
        val screening = GroupChat.screenImportedConfig(base(revision = -3, tiePolicy = "whatever"))
        val normalized = screening as? GroupChat.GroupConfigScreening.Normalized
        assertNotNull("两项越界应一起降级，实际 $screening", normalized)
        assertEquals(
            setOf("revision", "tie_policy"),
            normalized!!.fixes.map { it.field }.toSet(),
        )
        assertEquals(emptyList<GroupConfigError>(), GroupChat.validate(normalized.config))
    }

    // ---------------- 结构性不变式 ----------------

    @Test
    fun `everything the screening lets through also passes validate`() {
        // 「降级」唯一的保险。凡是 screenImportedConfig 放行的配置，validate 必须也判它合法。
        val candidates = listOf(
            base(),
            base(mode = GroupChat.MODE_ROUNDTABLE, chairRoleId = "c"),
            base(revision = 0),
            base(tiePolicy = "coin_flip"),
            base(revision = -3, tiePolicy = "whatever"),
            base(budget = 0),
            base(roles = listOf(alice, GroupRole("a", "x", "asst-x"))),
            base(mode = "committee"),
            base(schemaVersion = GroupChat.SCHEMA_VERSION + 1),
            base(roles = emptyList()),
        )
        candidates.forEach { config ->
            when (val screening = GroupChat.screenImportedConfig(config)) {
                is GroupChat.GroupConfigScreening.Accepted ->
                    assertEquals("Accepted 的配置 validate 必须为空：$config", emptyList<GroupConfigError>(), GroupChat.validate(screening.config))

                is GroupChat.GroupConfigScreening.Normalized -> {
                    assertEquals(
                        "Normalized 之后的配置 validate 必须为空：${screening.fixes}",
                        emptyList<GroupConfigError>(),
                        GroupChat.validate(screening.config),
                    )
                    // 每一项修正都必须留下可展示的改前值
                    screening.fixes.forEach { fix ->
                        assertTrue("$fix 必须记下改前值", fix.original.isNotEmpty())
                    }
                }

                is GroupChat.GroupConfigScreening.Rejected ->
                    assertTrue("Rejected 必须带字段级错误", screening.fieldErrors.isNotEmpty())
            }
        }
    }

    @Test
    fun `a fix never changes any field the config carried besides the two normalizable ones`() {
        // 归一化只许动 revision / tie_policy：群成员、模式、议长、预算、extras 一个字节都不许变。
        val original = base(revision = 0, tiePolicy = "weird").copy(
            extras = buildJsonObject { put("vendor_note", "未知字段要原样带回") },
        )
        val normalized = GroupChat.screenImportedConfig(original) as GroupChat.GroupConfigScreening.Normalized
        assertEquals(original.roles, normalized.config.roles)
        assertEquals(original.mode, normalized.config.mode)
        assertEquals(original.chairRoleId, normalized.config.chairRoleId)
        assertEquals(original.tokenBudgetPerRound, normalized.config.tokenBudgetPerRound)
        assertEquals(original.extras, normalized.config.extras)
        assertEquals(original.schemaVersion, normalized.config.schemaVersion)
    }

    // ---------------- 角色卡去重 ----------------

    @Test
    fun `cards are deduplicated on role id keeping the first occurrence`() {
        val first = RoleCardMeta(roleId = "a", name = "Alice", cardId = "card-a", persona = "第一个")
        // 同 role_id 但内容不同：仍然保留首条，第二条被丢掉并记账
        val rival = RoleCardMeta(roleId = "a", name = "Alice", cardId = "card-a2", persona = "第二个")
        val bob = RoleCardMeta(roleId = "b", name = "Bob", cardId = "card-b")
        val result = GroupChat.dedupeCards(listOf(first, rival, bob))
        assertEquals(listOf(first, bob), result.cards)
        val fix = result.fixes.single()
        assertEquals("cards[1].role_id", fix.field)
        assertTrue(fix.message, fix.message.contains("保留首条"))
    }

    @Test
    fun `an exactly duplicated card is collapsed and says so`() {
        val card = RoleCardMeta(roleId = "a", name = "Alice", cardId = "card-a", persona = "同一条")
        val result = GroupChat.dedupeCards(listOf(card, card))
        assertEquals(listOf(card), result.cards)
        assertEquals("重复的角色卡条目已去重", result.fixes.single().message)
    }

    @Test
    fun `cards without a role id are dropped because they belong to nobody`() {
        // 与 `GroupChat.decodeCards` 的既有口径一致（「缺 role_id 的条目直接丢掉，不猜它属于谁」）：
        // 留着只会变成一个查不到人的孤儿引用。
        val orphan = RoleCardMeta(roleId = "", name = "孤儿", cardId = "card-x")
        val blank = RoleCardMeta(roleId = "  ", name = "空白", cardId = "card-y")
        val alice = RoleCardMeta(roleId = "a", name = "Alice")
        val result = GroupChat.dedupeCards(listOf(orphan, alice, blank))
        assertEquals(listOf(alice), result.cards)
        assertEquals(
            listOf("cards[0].role_id", "cards[2].role_id"),
            result.fixes.map { it.field },
        )
    }

    @Test
    fun `the same card id backing two different roles is kept on both`() {
        // 明确否掉「按 card_id 去重」：同一张角色卡被多个角色共用是合法用法，契约从未禁止，
        // 按 card_id 去重会凭空删掉某个成员的角色卡。
        val a = RoleCardMeta(roleId = "a", name = "Alice", cardId = "shared")
        val b = RoleCardMeta(roleId = "b", name = "Bob", cardId = "shared")
        val result = GroupChat.dedupeCards(listOf(a, b))
        assertEquals(listOf(a, b), result.cards)
        assertEquals("不同 role_id 的同一张卡不算重复", emptyList<GroupChat.GroupConfigFix>(), result.fixes)
    }

    @Test
    fun `cards with a null card id are not treated as duplicates of each other`() {
        // `cardId` 可空，null == null 不是重复。
        val a = RoleCardMeta(roleId = "a", name = "Alice")
        val b = RoleCardMeta(roleId = "b", name = "Bob")
        assertEquals(listOf(a, b), GroupChat.dedupeCards(listOf(a, b)).cards)
    }

    @Test
    fun `deduping an already unique card list changes nothing`() {
        val cards = listOf(
            RoleCardMeta(roleId = "a", name = "Alice", cardId = "card-a"),
            RoleCardMeta(roleId = "b", name = "Bob", cardId = "card-b"),
            RoleCardMeta(roleId = "c", name = "Cara"),
        )
        val result = GroupChat.dedupeCards(cards)
        assertEquals(cards, result.cards)
        assertEquals(emptyList<GroupChat.GroupConfigFix>(), result.fixes)
    }

    @Test
    fun `an empty card list stays empty`() {
        val result = GroupChat.dedupeCards(emptyList())
        assertEquals(emptyList<RoleCardMeta>(), result.cards)
        assertEquals(emptyList<GroupChat.GroupConfigFix>(), result.fixes)
    }
}