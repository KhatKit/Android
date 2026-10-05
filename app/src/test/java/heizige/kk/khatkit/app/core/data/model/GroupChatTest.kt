package heizige.kk.khatkit.app.core.data.model

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    /**
     * `parseCandidates` 的直接 JVM 覆盖。此前所有投票用例都显式给 `config.voteCandidates`，
     * 这条正则分支（`newRound` 里 `voteCandidates.ifEmpty { parseCandidates(userText) }`）
     * 从没被直接测过。
     */
    @Test
    fun `candidate declaration is parsed from the explicit forms only`() {
        // 正常形态：中文半角/全角冒号、逗号/顿号/竖线分隔、去空白、去 Markdown 修饰。
        assertEquals(listOf("a", "b", "c"), GroupChat.parseCandidates("候选：a,b,c"))
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("候选:a，b"))
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("候选项：a|b"))
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("  候选 ：  a 、  b  "))
        // 重复项去重。
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("候选：a,b,a"))

        // 英文形态与大小写：`(?i)` 让 `candidates` / `Candidate` 同样算数（`CANDIDATES?` 的
        // `?` 只容许末尾一个 S，所以单数 `Candidate` 也算数、复数再多个 S 就不算）。
        assertEquals(listOf("a", "b", "c"), GroupChat.parseCandidates("CANDIDATES: a|b|c"))
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("candidates: a, b"))
        assertEquals(listOf("a"), GroupChat.parseCandidates("Candidate: a"))

        // 空文本 / 纯空白 → 空候选集（`collectBallots` 见到空候选集直接不收票）。
        assertEquals(emptyList<String>(), GroupChat.parseCandidates(""))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("   \n  \t "))

        // 格式错误：缺冒号、只有关键字没值、值全是分隔符。
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("候选"))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("候选："))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("候选：   "))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("候选：,,,|、、"))
        // `CANDIDATES?` 的 `?` 只容许末尾一个 S，所以 `CANDIDATESS` 不算数。
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("CANDIDATESS: a"))
        // 缺冒号的一行不算声明（冒号是必需的）。
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("候选 a,b"))
    }

    /**
     * 缺陷② 已修：候选 id 的 Markdown 清理改成**逐个 id 规范化**（剥成对包裹 + 剥列表
     * 符号），不再是 `String.trim('-', '*', '"')` 那种**按字符集 trim、遇非集合字符即停**。
     *
     * 修前实测：`- **a**` 的 `- ` 后面那个空格让字符集 trim 提前收工，只去掉末尾的 `*`，
     * 留下 `" **a"` 这种带前导空格和残缺星号的 id——「按 markdown 列表声明候选」这条路
     * 永远配不上票面。
     */
    @Test
    fun `markdown decoration is normalised per candidate instead of by char set trim`() {
        // 旧字符集 trim 覆盖到的两种写法，逐字不变。
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("候选：-a , b"))
        assertEquals(listOf("a"), GroupChat.parseCandidates("候选：*a*"))
        // 缺陷本体：`- ` 的空格不再让清理提前收工。
        assertEquals(listOf("a"), GroupChat.parseCandidates("候选：- **a**"))
        // 成对引号 / 成对强调 / 同一次声明里叠加两种修饰。
        assertEquals(listOf("a"), GroupChat.parseCandidates("候选：- \"a\""))
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("候选：**a**, - *b*"))
        // 多层装饰循环到稳定。
        assertEquals(listOf("a"), GroupChat.parseCandidates("候选：- ***a***"))
        // 刻意**不**修的两种：不成对引号（宁可配不上票也不静默改写）与 `_`（snake_case
        // 远比 markdown 下划线强调常见，剥它会毁掉 `_private_` 这类合法 id）。
        assertEquals(listOf("\"a"), GroupChat.parseCandidates("候选：\"a"))
        assertEquals(listOf("_a_"), GroupChat.parseCandidates("候选：_a_"))
    }

    /**
     * 「首个非空行」的判据用例：前导空行与纯空白行都被跳过，所以**前导空行不耽误声明
     * 算数**；真正被排除的是「声明不在第一个非空行上」。
     */
    @Test
    fun `leading blank and whitespace only lines are skipped before the declaration`() {
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("\n候选：a,b"))
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("\n\n   \t \n  候选：a,b"))
        // 全是空白 → 空候选集。
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("   \n  \t \n "))
    }

    /**
     * 缺陷① 已修：声明只认**首个非空行**。
     *
     * 修前 `(?im)` 的 `m` 让 `^` 逐行匹配，正文**任意一行**只要以（可空白前缀的）`候选：`
     * 开头就被当声明。真实后果不是越权注入（`userText` 只来自最后一条 USER 消息，模型正文
     * 到不了这里），而是**用户意图错位**：用户粘一段引用了别人发言的文本、引用块里恰好有
     * 一行 `候选：…`，那行会决定本轮候选集，而不是用户自己写的那行，且没有任何提示。
     *
     * `firstOrNull` 语义保持：只认第一条声明，绝不跨行收集。
     */
    @Test
    fun `only the first non blank line may declare candidates`() {
        // 修前实测拿到 `[evil-a, evil-b]`；修后正文深处那一行不再算声明。
        assertEquals(
            emptyList<String>(),
            GroupChat.parseCandidates("这是正文第一行\n候选：evil-a,evil-b\n这是正文最后一行"),
        )
        // 首行与深处都有 → 只认首行（不收集全部）。
        assertEquals(
            listOf("first"),
            GroupChat.parseCandidates("候选：first\n引用：\n> 候选：second"),
        )
        // 首行没有声明、深处有 → 判无候选集（本轮判失败），不猜。
        assertEquals(
            emptyList<String>(),
            GroupChat.parseCandidates("请大家讨论\n引用：\n> 候选：second"),
        )
        // 行内（非行首）出现仍然不算声明——这条口径本来就没变。
        assertEquals(
            emptyList<String>(),
            GroupChat.parseCandidates("译注：原文提到“候选：red”"),
        )
    }

    /**
     * 跨平台行分隔符：修后判据走 `lineSequence().firstOrNull { it.isNotBlank() }`，所以
     * `lineSequence()` 的真实切行口径必须钉住，否则「声明只认首行」在 Windows 粘贴上悄悄
     * 失效。实测见 `/tmp/opencode/candidates-fix2/probe-out.txt`（`kotlinc` + 独立复刻的
     * 修后实现），结论是 `\n` / `\r\n` / 裸 `\r` **都**能切开。
     *
     * `\r\n` 尤其要钉：Windows 剪贴板粘进来就是它，切不出行的话上面「首个非空行」那条
     * 判据在 Windows 上等于没加。
     *
     * 已知且**刻意不修**的假阴性：`lineSequence()` 只认 `\n` / `\r\n` / `\r`，**不认**
     * Unicode 行/段分隔符 U+2028 / U+2029。所以「首行声明 + U+2028/U+2029 + 后续文本」
     * 解析不出候选，返回空候选集。方向上是**失败关闭**而不是泄漏——下面两条断言钉住
     * 「正文里的声明照样赢不了」——只是漏认不是越权。U+2028/9 在聊天输入里基本不出现
     * （Android `EditText` / IME / 剪贴板都不产生）。要认它们就得先定义「U+2028/9 算不
     * 算行分隔符」这条新规则，**修它要先经确认**，本轮只钉现状。
     *
     * 顺带实测到 Kotlin `Char.isWhitespace()` 与 Java `Character.isWhitespace` 在
     * U+00A0 / U+202F 上**不一致**（Kotlin `true` / Java `false`，NBSP 类窄不换行空格）。
     * 本实现只用 Kotlin 的 `isNotBlank()`，方向无害（NBSP 当空白更符合直觉），不影响
     * 上面任何断言，故不在此钉桩。
     */
    @Test
    fun `line separators are cut consistently so the first line rule survives cross platform pastes`() {
        fun tag(sep: String) = "sep=" + sep.replace("\r", "<CR>").replace("\n", "<LF>")
        for (sep in listOf("\n", "\r\n", "\r")) {
            // 声明在首行算数，引用块深处那行不算数。
            assertEquals(
                tag(sep),
                listOf("a", "b"),
                GroupChat.parseCandidates("候选：a,b${sep}引用：${sep}> 候选：second"),
            )
            // 反向：声明不在首行（只隔一行）→ 空候选集，引用块赢不了。
            assertEquals(
                tag(sep),
                emptyList<String>(),
                GroupChat.parseCandidates("请大家讨论${sep}候选：second"),
            )
            // CRLF 的 `\r` 被分隔符吃掉，不进候选 id。
            assertEquals(
                tag(sep),
                listOf("a", "b"),
                GroupChat.parseCandidates("候选：a , b${sep}"),
            )
            // 前导空行（纯空白行）跳过，声明照样算数。
            assertEquals(tag(sep), listOf("a", "b"), GroupChat.parseCandidates("${sep}  ${sep}候选：a,b"))
        }

        // ---- U+2028 / U+2029：钉「失败关闭」，不钉成「能认」 ----
        assertEquals(
            "U+2028 首行声明 + 后续文本：已知假阴性，解析不出候选（刻意不修）",
            emptyList<String>(),
            GroupChat.parseCandidates("候选：a,b\u2028引用：\u2028> 候选：second"),
        )
        assertEquals(
            "U+2029 同上",
            emptyList<String>(),
            GroupChat.parseCandidates("候选：a,b\u2029引用：\u2029> 候选：second"),
        )
        // 关键：方向是失败关闭而非泄漏——正文深处的声明赢不了（U+2028/9 不当分隔符时，
        // 整段文本是「一行」，首行无声明 + `$` 锚不到 → 空候选集）。
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("请大家讨论\u2028候选：second"))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("请大家讨论\u2029候选：second"))
        // 单个 U+2028/9 顶在最前面时，`trim()`（Kotlin 视其为空白）会把它去掉，声明仍算数。
        assertEquals(listOf("a"), GroupChat.parseCandidates("\u2028候选：a"))
        assertEquals(listOf("a"), GroupChat.parseCandidates("\u2029候选：a"))
    }

    /**
     * 反向对照：这些输入**改前改后逐字一致**，证明这次只动了「声明位置」与「id 规范化」
     * 两处，没有顺手改掉别的行为。显式形态、大小写、空文本、格式错误全部照旧。
     */
    @Test
    fun `unrelated inputs keep exactly the behaviour they had before the tightening`() {
        assertEquals(listOf("a", "b", "c"), GroupChat.parseCandidates("候选：a,b,c"))
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("候选:a，b"))
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("候选项：a|b"))
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("  候选 ：  a 、  b  "))
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("候选：a,b,a"))
        assertEquals(listOf("a", "b", "c"), GroupChat.parseCandidates("CANDIDATES: a|b|c"))
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("candidates: a, b"))
        assertEquals(listOf("a"), GroupChat.parseCandidates("Candidate: a"))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates(""))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("   \n  \t "))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("候选"))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("候选："))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("候选：   "))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("候选：,,,|、、"))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("CANDIDATESS: a"))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("候选 a,b"))
        // 单行声明后面跟空行/换行：老写法照旧算数（第一个非空行就是它）。
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("候选：a,b\n\n"))
    }

    /**
     * **已知假阳性，本轮只钉现状、不改行为。**
     *
     * `parseBallot` 不感知 markdown fence，也不感知引用：代码块里独占一行的示例 `VOTE: opt-a`
     * 会被当成真票。契约没有要求区分代码块，修它要先定义一整套 markdown 感知规则
     * （fence 配对、行内引用、缩进代码块…），所以这里只把现状钉住。
     */
    @Test
    fun `a vote line inside a code fence is still counted as a ballot`() {
        val candidates = listOf("opt-a", "opt-b")
        val fenced = """
            这是示例格式，请照抄：
            ```text
            VOTE: opt-a
            ```
            我选 opt-b。
        """.trimIndent()

        // 已知假阳性：fence 里的示例行被当真票。
        assertEquals("opt-a", GroupChat.parseBallot(fenced, "alice", candidates)?.candidateId)
        // 更糟的形状：模型**后文里真的投了** opt-b，但 fence 里的示例行排在前面，
        // `firstOrNull` 取的是第一条，于是真票被示例行顶替。
        assertEquals(
            "opt-a",
            GroupChat.parseBallot("示例：\n```text\nVOTE: opt-a\n```\nVOTE: opt-b", "alice", candidates)?.candidateId,
        )
        // 顺带钉住另一个相邻事实：`startsWith` 是整行前缀匹配，行内出现不算票。
        assertNull(GroupChat.parseBallot("我选 VOTE: opt-b", "alice", candidates))
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

    // ------------------------------------------------------------------
    // 保留 role_id：SUMMARY_ID 不许被普通角色占用
    // ------------------------------------------------------------------

    /**
     * P0 回归：[SUMMARY_ID] 是合成节点的保留 `role_id`，[visibleMessages] 对它无条件放行给
     * 所有视角。配置里一旦出现 `role_id = "__summary__"` 的普通角色，那个角色的每一条发言
     * 都会被当成轮次小结广播给全群——视角隔离在配置层被绕过。
     *
     * 收口在 [GroupChat.validate]：导入 / UI 保存 / `createGroup` 三条路都过它。
     */
    @Test
    fun `validate rejects a role whose id is the reserved summary id`() {
        val forged = GroupConfig(
            roles = listOf(GroupRole(GroupChat.SUMMARY_ID, "冒充小结", "asst-x")),
            mode = GroupChat.MODE_PIPELINE,
            tokenBudgetPerRound = 1000,
        )

        val errors = GroupChat.validate(forged)

        assertTrue("保留 role_id 必须被拒", errors.isNotEmpty())
        val hit = errors.firstOrNull { it.field == "roles[].role_id" }
        assertNotNull("field 应为 roles[].role_id，实际=${errors.map { it.field }}", hit)
        assertTrue(hit!!.message.contains(GroupChat.SUMMARY_ID))
        assertFalse(GroupChat.isValid(forged))
    }

    /**
     * 同一个禁令必须把**导入路径**也堵上：[importShare] 第 5 道闸门跑 [validate]，
     * 所以外部 JSON 里的 `role_id = "__summary__"` 到不了内存、更到不了库。
     */
    @Test
    fun `importShare rejects a payload whose role id is the reserved summary id`() {
        val raw = payloadJson(
            GroupChat.SCHEMA_VERSION,
            roles = """[{"role_id":"${GroupChat.SUMMARY_ID}","name":"冒充","assistant_id":"asst-x"}]""",
        )

        val result = GroupChat.importShare(raw)

        assertTrue("保留 role_id 的载荷必须被拒", result is GroupImportResult.Rejected)
        val rejected = result as GroupImportResult.Rejected
        assertEquals("群配置校验未通过", rejected.reason)
        assertTrue(
            "fieldErrors 应点名 roles[].role_id，实际=${rejected.fieldErrors.map { it.field }}",
            rejected.fieldErrors.any { it.field == "roles[].role_id" },
        )
    }

    /**
     * 反向证据（防「一刀切拦过头」）：正常角色 id 一律照旧通过，且 `SUMMARY_ID` 常量本身
     * 没有被改写——禁令落在配置校验上，不是落在常量上。
     */
    @Test
    fun `ordinary role ids still validate and the summary constant is untouched`() {
        val normal = GroupConfig(
            roles = roles,
            mode = GroupChat.MODE_PIPELINE,
            tokenBudgetPerRound = 1000,
        )

        assertTrue(GroupChat.validate(normal).isEmpty())
        assertEquals("__summary__", GroupChat.SUMMARY_ID)
        // 带下划线的普通 id 不是保留值，不该被误伤。
        val underscore = GroupConfig(
            roles = listOf(GroupRole("__not_summary__", "普通", "asst-a")),
            mode = GroupChat.MODE_PIPELINE,
            tokenBudgetPerRound = 1000,
        )
        assertTrue(GroupChat.validate(underscore).isEmpty())
    }

    /**
     * 合成节点本身仍然对**所有**视角可见——这是禁令不能碰的那一半。
     *
     * 投票小结（`TURN_VOTE_SUMMARY`）与投票失败摘要（`TURN_ERROR`，
     * `ChatManager.voteFailureNode`）两个来源共用 `SUMMARY_ID`，所以
     * [visibleMessages] 的放行分支不能按 `turnKind` 收窄，否则失败摘要会对所有人消失。
     */
    @Test
    fun `both synthetic summary kinds stay visible to every viewer`() {
        val messages = listOf(
            UIMessage.user("投票").copy(turnKind = GroupChat.TURN_USER),
            UIMessage.assistant("本轮投票结果：甲").copy(
                roleId = GroupChat.SUMMARY_ID,
                turnKind = GroupChat.TURN_VOTE_SUMMARY,
            ),
            UIMessage.assistant("[投票] 本轮未能得出结论：平票").copy(
                roleId = GroupChat.SUMMARY_ID,
                turnKind = GroupChat.TURN_ERROR,
            ),
        )

        listOf("a", "b", "c").forEach { viewerId ->
            val seen = GroupChat.visibleMessages(config(GroupChat.MODE_VOTE), messages, viewerId).map { it.toText() }
            assertTrue("$viewerId 应看见投票小结", seen.any { it.contains("本轮投票结果：甲") })
            assertTrue("$viewerId 应看见投票失败摘要", seen.any { it.contains("本轮未能得出结论") })
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
