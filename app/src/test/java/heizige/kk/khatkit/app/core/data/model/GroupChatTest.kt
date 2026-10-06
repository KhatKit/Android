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
     * ⚠️ **上面这段是历史记述，已被下面的订正块取代，但保留原文不删**（它记录了为什么这条
     * 缺陷会被挂起，以及挂起时的判断）。订正见下。
     *
     * ### 订正：该规则已定，U+2028/U+2029（以及 U+0085）已修
     *
     * 「U+2028/9 算不算行分隔符」这条新规则**已经定义并确认**：分隔符集合取 **6 个**——
     * `\n` / `\r\n` / `\r` / `\u0085`(NEL) / `\u2028`(LS) / `\u2029`(PS)——与 Java `Pattern`
     * 的行终止符集合逐条对齐。实现是 `GroupChat.unicodeLines`（**私有**），`parseCandidates`
     * 与 `linesOutsideCodeFences` **共用**它。
     *
     * 依据（`kotlinc` 探针实测）：`Pattern` 默认的 `.` 不匹配行终止符，而 `(?s)候选：a.b`
     * 能跨 `\u0085`/`\u2028`/`\u2029` 命中、`(?m)^b$` 也能在 `a<sep>b` 上命中 ⇒ 三者都是
     * `Pattern` 的行终止符；而 `String.lineSequence()` / `String.lines()` 只切 `\n`/`\r\n`/`\r`，
     * `\u0085`/`\u2028`/`\u2029` 三个**都当普通字符** ⇒ 「切分行」与「正则行终止符」口径
     * 不对称，那就是本缺陷的成因。两个现成 API 口径**完全一样**，都不能拿来蒙混。
     *
     * 顺带订正上面那段的一处事实错误：原文写「`trim()` 视 U+2028 为空白」是对的，但更早
     * 一次讨论里有人说过「`Character.isWhitespace('\u2028')` 为 false」——**实测为 true**
     * （U+2028/U+2029 的 Unicode 类别是 13/14 = SPACE_SEPARATOR，两个重载都返回 true）。
     * 只有 `\u0085` 是 false（类别 15 = CONTROL）。这个差别直接决定了下面两条钉桩的走向。
     *
     * 下面 6 条断言里**只有 2 条改了期望值**（U+2028/U+2029 那两条「首行声明 + 后续文本」），
     * 另外 4 条**实测一字未变**、断言一个字符都没动：
     *  - 改：`候选：a,b<LS>引用：<LS>> 候选：second` 由 `emptyList()` 变 `[a, b]`（U+2029 同）。
     *    这就是缺陷本体——声明行不再被 `\u2028` 撑成一整行而整条正则失配。
     *  - 不变：`请大家讨论<LS>候选：second` 仍是 `emptyList()`。**方向没变**：声明仍在
     *    第二行，首行「请大家讨论」没有声明 → 仍判无候选集，仍然失败关闭，仍然不泄漏。
     *  - 不变：`<LS>候选：a` 仍是 `[a]`。`<LS>` 现在切出一个空行，空行被 `isNotBlank()`
     *    跳过，声明照样落在第一个非空行上。
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

        // ---- U+2028 / U+2029：已修，期望值从 `emptyList()` 改为 `[a, b]`（见上面订正块）----
        assertEquals(
            "U+2028 首行声明 + 后续文本：已修——<LS> 是行终止符，声明行不再被撑成整行而失配",
            listOf("a", "b"),
            GroupChat.parseCandidates("候选：a,b\u2028引用：\u2028> 候选：second"),
        )
        assertEquals(
            "U+2029 同上",
            listOf("a", "b"),
            GroupChat.parseCandidates("候选：a,b\u2029引用：\u2029> 候选：second"),
        )
        // 关键：方向是失败关闭而非泄漏——正文深处的声明赢不了（<LS>/<PS> 现在切行，
        // 首行「请大家讨论」无声明 → 空候选集）。修前修后都是 `emptyList()`，一字未变。
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("请大家讨论\u2028候选：second"))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("请大家讨论\u2029候选：second"))
        // <LS>/<PS> 顶在最前面时切出一个空行，空行被跳过，声明仍落在第一个非空行上。
        assertEquals(listOf("a"), GroupChat.parseCandidates("\u2028候选：a"))
        assertEquals(listOf("a"), GroupChat.parseCandidates("\u2029候选：a"))
    }

    /**
     * `parseCandidates` 在 **U+2028 / U+2029 / U+0085** 三种分隔符下都能解析出**多个**候选。
     *
     * 覆盖 `unicode line separators cut candidate declarations the same way the regex does`
     * 里已铺开的 `\n`/`\r\n`/`\r` 回归护栏之外的另一半：这三个分隔符修前**全部**让
     * `parseCandidates` 返回 `emptyList()`——模型输出 `候选：a,b\u2028引用：` 时，声明行被
     * `\u2028` 撑成一整行，`.` 跨不过行终止符、`$` 又只锚行尾，整条正则必然失配，于是
     * **一个候选都读不出来**，本轮直接落到「本轮没有候选」。
     *
     * `\u0085`(NEL) 值得单独点名：它和 `\u2028` 不同，`trim()` **不**剥它
     * （`Character.isWhitespace('\u0085')` = false，Unicode 类别 15 = CONTROL），所以修前
     * 它连 `VOTE:` 票面都会被静默丢票（见下面票面那条用例）。
     *
     * 「首个非空行 + `firstOrNull`」语义不变：这里只认**第一条**声明，绝不跨行收集——
     * 第二个 `候选：` 出现在哪一行都不算数。
     */
    @Test
    fun `unicode line separators cut candidate declarations the same way the regex does`() {
        fun tag(sep: String) = "sep=" + sep.replace("\u0085", "<NEL>").replace("\u2028", "<LS>").replace("\u2029", "<PS>")
        // 6 个分隔符逐个过：声明在首行 → 解析出多个候选；声明后的 Unicode 行不污染 id。
        for (sep in listOf("\n", "\r\n", "\r", "\u0085", "\u2028", "\u2029")) {
            assertEquals(tag(sep), listOf("a", "b"), GroupChat.parseCandidates("候选：a,b${sep}引用："))
            assertEquals(tag(sep), listOf("a", "b"), GroupChat.parseCandidates("候选：a , b${sep}"))
            // 方向仍是失败关闭：声明不在首行 → 空候选集，Unicode 分隔符下的深处声明赢不了。
            assertEquals(tag(sep), emptyList<String>(), GroupChat.parseCandidates("请大家讨论${sep}候选：second"))
            // 前导分隔符切出的空行被 `isNotBlank()` 跳过，声明照样算数。
            assertEquals(tag(sep), listOf("a", "b"), GroupChat.parseCandidates("${sep}  ${sep}候选：a,b"))
        }
        // 三个 Unicode 分隔符各自的「声明 + 后文」形状（修前实测全部是 `emptyList()`）。
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("候选：a,b\u2028引用："))
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("候选：a,b\u2029引用："))
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("候选：a,b\u0085引用："))
        // 单个分隔符把声明切成两行时只认首行（`firstOrNull`），不是跨行收集。
        assertEquals(listOf("a"), GroupChat.parseCandidates("候选：a\u2028b"))
        assertEquals(listOf("a"), GroupChat.parseCandidates("候选：a\u0085b"))
    }

    /**
     * `parseBallot` 的行边界：`\u0085` / `\u2028` / `\u2029` 分隔的**多条** `VOTE:` 行各自
     * 成行为独立的一行，于是**第一条命中**能被识别（修前整段被当成一行而丢票）。
     *
     * **语义明确钉住：保持现有「取第一条命中」，不是多票。** 模型连着写两行 `VOTE:` 时
     * 仍只记第一条——本次只修「行从哪来」，没碰「取第几条」这条既定语义。
     *
     * 修前实测：`VOTE: opt-a\u2028VOTE: opt-b`（候选 `[opt-a]`）返回 **null**，因为整段是
     * 一行、body 变成 `opt-a\u2028VOTE: opt-b`、`substringBefore('|')` 之后不在候选集里。
     * 修后返回 `opt-a`。
     */
    @Test
    fun `unicode separated VOTE lines are found and the first one still wins`() {
        fun tag(sep: String) = "sep=" + sep.replace("\u0085", "<NEL>").replace("\u2028", "<LS>").replace("\u2029", "<PS>")
        val two = listOf("opt-a", "opt-b")
        // 6 个分隔符逐个过：两条 `VOTE:` 行 → 命中**第一条**（不是第二条，也不是两票）。
        for (sep in listOf("\n", "\r\n", "\r", "\u0085", "\u2028", "\u2029")) {
            assertEquals(
                tag(sep),
                "opt-a",
                GroupChat.parseBallot("VOTE: opt-a${sep}VOTE: opt-b", "r1", two)?.candidateId,
            )
        }
        // 三种 Unicode 形状各自点名单独断言（修前实测全部是 null）。
        assertEquals("opt-a", GroupChat.parseBallot("VOTE: opt-a\u2028VOTE: opt-b", "r1", two)?.candidateId)
        assertEquals("opt-a", GroupChat.parseBallot("VOTE: opt-a\u2029VOTE: opt-b", "r1", two)?.candidateId)
        assertEquals("opt-a", GroupChat.parseBallot("VOTE: opt-a\u0085VOTE: opt-b", "r1", two)?.candidateId)
        // 理由跟在竖线后，仍然只认第一条那条票的理由（不是把两条的理由并起来）。
        assertEquals(
            "只认第一条那条票的理由",
            "第一条",
            GroupChat.parseBallot("VOTE: opt-a|第一条\u2028VOTE: opt-b|第二条", "r1", two)?.reason,
        )
    }

    /**
     * 代码围栏在 Unicode 分隔符下**仍然生效**（围栏开/闭判定本身一字未改，只改了行从哪来）。
     *
     * 修前实测：` ```<LS>VOTE: opt-a<LS>```<LS>VOTE: opt-b ` 返回 **null**——整段被当成
     * **一行**，开围栏那行的 info string（`group(2)`）不空，于是状态机认定围栏**开了却没关**，
     * 把后文全吞了。围栏因此在 Unicode 分隔符下**形同失效**（这里恰好与期望同为 null，
     * 但理由完全不同：一个是吞掉了本该收的真票，一个是正确跳过块内示例）。
     *
     * 修后：块内的示例 `VOTE: opt-a` 被跳过，围栏后的真票 `VOTE: opt-b` 被收下。
     * `\n` 那条是回归护栏（修前修后都必须收到 `opt-b`）。
     */
    @Test
    fun `code fences are still skipped when the fence lines are unicode separated`() {
        val two = listOf("opt-a", "opt-b")
        for (sep in listOf("\n", "\r\n", "\r", "\u0085", "\u2028", "\u2029")) {
            assertEquals(
                "sep=" + sep.replace("\u0085", "<NEL>").replace("\u2028", "<LS>").replace("\u2029", "<PS>"),
                "opt-b",
                GroupChat.parseBallot("```${sep}VOTE: opt-a${sep}```${sep}VOTE: opt-b", "r1", two)?.candidateId,
            )
            // 块内示例仍**不**算票：围栏一直开到结尾，失败关闭 → 无票。
            assertNull(
                GroupChat.parseBallot("```${sep}VOTE: opt-a${sep}```", "r1", two),
            )
        }
        // 三种 Unicode 形状点名单独断言。
        assertEquals("opt-b", GroupChat.parseBallot("```\u2028VOTE: opt-a\u2028```\u2028VOTE: opt-b", "r1", two)?.candidateId)
        assertEquals("opt-b", GroupChat.parseBallot("```\u2029VOTE: opt-a\u2029```\u2029VOTE: opt-b", "r1", two)?.candidateId)
        assertEquals("opt-b", GroupChat.parseBallot("```\u0085VOTE: opt-a\u0085```\u0085VOTE: opt-b", "r1", two)?.candidateId)
    }

    /**
     * **有意收紧的护栏**：票面前面带 Unicode 行终止符（`VOTE: <LS>opt-a`）现在判**无票**。
     *
     * 这是从「能解析」到「null」的**行为变化**，**刻意为之**，不是回归事故：
     *  - 修**前**实测：`parseBallot("VOTE: \u2028opt-a", "r1", listOf("opt-a"))` 返回
     *    `candidateId = "opt-a"`——**票收到了**。
     *  - 修**后**返回 `null`。理由：`Char.isWhitespace('\u2028')` 为 true，所以 `trim()`
     *    会剥掉 `\u2028`，而 `lineSequence()` 不剥——修前能解析**纯粹是 JDK 两个 API 口径
     *    不一致的巧合**，不是设计。统一成行终止符后，`VOTE: ` 就是一条**空 body** 的票行，
     *    空 id 不在候选集 → 无票。
     *  - 这正是本文件既定立场的延伸：「宁可让畸形 id 配不上票，也不静默改写用户写的东西」。
     *    票面格式是 `VOTE: <id>`，id 必须与前缀**同行**；跨行接续 id 是另一种格式，
     *    本实现不认（也不为此开特例）。
     *
     * 三个分隔符一律如此——**包括 `\u0085`**：它虽然 `trim()` 剥不掉（修前因此真的丢票），
     * 但把它当行终止符之后，形状与 `\u2028` 完全同构，所以结论相同。
     *
     * 将来谁想改回去（例如给「空 body 的 `VOTE:` 行接续下一行」开特例），会先撞到这条断言。
     */
    @Test
    fun `a ballot with a leading unicode separator is no longer accepted`() {
        val one = listOf("opt-a")
        assertNull("U+2028 前置分隔符：有意收紧，判无票", GroupChat.parseBallot("VOTE: \u2028opt-a", "r1", one))
        assertNull("U+2029 同上", GroupChat.parseBallot("VOTE: \u2029opt-a", "r1", one))
        assertNull("U+0085 同上（形状与前两者同构）", GroupChat.parseBallot("VOTE: \u0085opt-a", "r1", one))
    }

    /**
     * **真·回归护栏**：票面后面**尾随** `\u0085`(NEL) 时，id 必须干净、票必须收到。
     *
     * 这是 `\u0085` 唯一一个「修前真的丢票、修后修好」的形状，也是 NEL 值得进那 6 个
     * 分隔符的直接证据：
     *  - `Character.isWhitespace('\u0085')` = **false**（Unicode 类别 15 = CONTROL），所以
     *    `trim()` **不**剥 NEL。
     *  - 修**前**实测：`parseBallot("VOTE: opt-a\u0085", "r1", listOf("opt-a"))` 返回
     *    **null**——id 变成 `"opt-a\u0085"`、不在候选集里，于是这一票被**静默丢掉**，
     *    且链路上没有任何一处能指出是编码问题；票数不足最后落到 [VoteOutcome.Invalid]。
     *  - 修**后**返回 `candidateId = "opt-a"`。
     *
     * 对照：`\u2028` / `\u2029` 的尾随形状修前就能解析（靠 `trim()` 剥掉），修后照样能解析，
     * 所以那两条是「一字不变」的回归护栏，不是修好的证据。
     */
    @Test
    fun `a trailing NEL no longer corrupts the candidate id`() {
        val one = listOf("opt-a")
        // 缺陷本体：修前 null，修后 opt-a。
        assertEquals("opt-a", GroupChat.parseBallot("VOTE: opt-a\u0085", "r1", one)?.candidateId)
        // 尾随分隔符不污染理由：`|` 在首条命中那条票**之内**，分隔符切出的空行排在它后面。
        assertEquals(
            "尾随 NEL 不污染理由",
            "理由",
            GroupChat.parseBallot("VOTE: opt-a|理由\u0085", "r1", one)?.reason,
        )
        assertEquals(
            "尾随 LS 不污染理由",
            "理由",
            GroupChat.parseBallot("VOTE: opt-a|理由\u2028", "r1", one)?.reason,
        )
        // `\u2028` / `\u2029` 尾随：修前修后一致（修前靠 `trim()` 剥掉），回归护栏。
        assertEquals("opt-a", GroupChat.parseBallot("VOTE: opt-a\u2028", "r1", one)?.candidateId)
        assertEquals("opt-a", GroupChat.parseBallot("VOTE: opt-a\u2029", "r1", one)?.candidateId)
        // 纯 `\n` / `\r\n` / `\r` 尾随：回归护栏。
        for (sep in listOf("\n", "\r\n", "\r")) {
            assertEquals("opt-a", GroupChat.parseBallot("VOTE: opt-a$sep", "r1", one)?.candidateId)
        }
    }

    /**
     * 非法 id 仍被拒：这条**不能**因为切分口径统一而被放松。
     *
     * 统一到 6 个分隔符后，「集外票返回 null」这条判据在三种新分隔符下同样成立——
     * 修前其中两种是**因为别的理由**返回 null（整段被当成一行、id 被 `\u0085` 污染），
     * 修后必须是因为**正确理由**（id 干净、但确实不在候选集里）返回 null。
     */
    @Test
    fun `out of set and malformed ballots are still rejected under every separator`() {
        val two = listOf("opt-a", "opt-b")
        for (sep in listOf("\n", "\r\n", "\r", "\u0085", "\u2028", "\u2029")) {
            // 集外 id → 无票。
            assertNull(GroupChat.parseBallot("VOTE: opt-z${sep}", "r1", two))
            assertNull(GroupChat.parseBallot("VOTE: opt-z${sep}VOTE: opt-a", "r1", two))
            // 行内（非行首）出现照旧不算票。
            assertNull(GroupChat.parseBallot("我选 VOTE: opt-a${sep}", "r1", two))
            assertNull(GroupChat.parseBallot("前缀VOTE: opt-a${sep}", "r1", two))
            // 空 body → 无票。
            assertNull(GroupChat.parseBallot("VOTE:${sep}", "r1", two))
        }
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
     * 缺陷③ 已修：[parseBallot] 跳过 markdown 围栏代码块，块里的**示例** `VOTE:` 行不再算票。
     *
     * 修前实测（`df560180` 钉的就是这两条现状）：第一段拿到的是 `"opt-a"`，而那是 fence 里的
     * **示例**行。第二条形状更糟：模型后文里**真的投了** `opt-b`，`firstOrNull` 取的却是排在
     * 前面的示例行——**真票被示例行顶替**，群里就这么记下了一票它没投过的选择。
     *
     * 为什么要修（不是学术问题）：群聊 prompt 本来就要把投票格式告诉模型（`候选：` / `VOTE: <id>`），
     * 模型**很可能照抄格式示例**。它一旦在正文里带一个 ``` 格式说明块，那个示例就成了一票。
     *
     * 围栏的识别口径写在 [GroupChat.linesOutsideCodeFences] 的 KDoc 里。
     */
    @Test
    fun `a vote line inside a code fence never becomes a ballot`() {
        val candidates = listOf("opt-a", "opt-b")
        val fenced = """
            这是示例格式，请照抄：
            ```text
            VOTE: opt-a
            ```
            我选 opt-b。
        """.trimIndent()

        // 修前拿到 `"opt-a"`（示例行被当真票）；修后这段输出没有独立的 `VOTE:` 行 → 无票。
        assertNull(GroupChat.parseBallot(fenced, "alice", candidates))
        // 更糟的形状：示例行在前、真票在后。修前真票被顶替成 `opt-a`，修后拿到 `opt-b`。
        assertEquals(
            "opt-b",
            GroupChat.parseBallot("示例：\n```text\nVOTE: opt-a\n```\nVOTE: opt-b", "alice", candidates)
                ?.candidateId,
        )
        // 波浪线围栏同样跳过，`~~~` 以上（4 个及以上）的等价标记一样算围栏。
        assertNull(GroupChat.parseBallot("~~~\nVOTE: opt-a\n~~~", "alice", candidates))
        assertNull(GroupChat.parseBallot("~~~~\nVOTE: opt-a\n~~~~", "alice", candidates))
        assertNull(GroupChat.parseBallot("````text\nVOTE: opt-a\n````", "alice", candidates))
        // 下面三条钉围栏文法本身。判别式有两种形状，分别说明：
        // ① 闭围栏必须**同字符**——反引号闭不掉波浪线围栏。若把它错判成闭围栏，块内的
        //    `VOTE: opt-a` 就跑到围栏外被收走，`firstOrNull` 会返回 `opt-a` 而不是 `opt-b`。
        assertEquals(
            "opt-b",
            GroupChat.parseBallot("~~~\n```\nVOTE: opt-a\n~~~\nVOTE: opt-b", "alice", candidates)
                ?.candidateId,
        )
        // ② 闭围栏**不得短于**开围栏（CommonMark）：4 个反引号开的块，3 个闭不掉。这条的
        //    判别式**反过来**是 null——正确实现下整个块一直开到文本结尾（失败关闭），末尾
        //    那票真票被一起吞掉；若错判成闭围栏，末尾那票就会被收走而暴露成 `opt-b`。
        assertNull(GroupChat.parseBallot("````text\nVOTE: opt-a\n```\nVOTE: opt-b", "alice", candidates))
        // ③ 闭围栏后面只许有空白：`` ```python `` 是块内容、不是闭围栏，后面真正的 ``` 才闭围栏。
        //    若把 `` ```python `` 错判成闭围栏，块内的 `VOTE: opt-a` 就会赢。
        assertEquals(
            "opt-b",
            GroupChat.parseBallot("```text\n```python\nVOTE: opt-a\n```\nVOTE: opt-b", "alice", candidates)
                ?.candidateId,
        )
        // 反证：把内层标记换成**合法的**闭围栏（长度相同、其后只有空白），那一票立刻算数。
        assertEquals(
            "opt-b",
            GroupChat.parseBallot("````text\nVOTE: opt-a\n````\nVOTE: opt-b", "alice", candidates)
                ?.candidateId,
        )
    }

    /**
     * 围栏配对不完整时**失败关闭**（fail-closed）：只有开围栏、没有闭围栏时，开围栏之后的行
     * 一律当代码丢掉。
     *
     * 两种口径都有代价，选 fail-closed 的理由逐条：
     *  1. **损害不同量级**。伪造出来的一票会被 [GroupChat.tally] 照单全收，可能直接产出一个
     *     **错误的胜者**并署名给某个真实角色，而链路上没有任何一处还能把它认出来。丢一票则是
     *     本模块**早就在生产**的结果：`GroupTurnCoordinator.collectBallots` 对
     *     `turnKind == TURN_ERROR` 的节点、对候选集外的票，本来就是静默不收票。
     *  2. **未闭合的主因是截断**，而截断恰恰意味着「模型还没来得及说真票」。要构造出
     *     「围栏没闭合、后面还跟着一票真票」，得先假设输出**没有**被截断——可那样它就该把
     *     围栏闭上。两种场景近乎互斥，所以 fail-open 要防的那种损害，在真实输入里主要就落在
     *     「模型正在演示格式、正文被截断」这一种上，而那恰恰是 fail-closed 要挡的。
     *  3. **损害有界**。fail-closed 最多让一个角色在这一轮没有票（开围栏之后的全部内容被忽略），
     *     不跨轮传播；全轮都没票时 [GroupChat.tally] 判 [VoteOutcome.Invalid]「没有有效选票」，
     *     是一次**响亮、可归因、可重试**的失败。
     *  4. **与本模块既有默认一致**。平票按 [GroupChat.TIE_FAIL] 判本轮失败而不猜、预算到点
     *     [RoundBudget.Stop] 而不是 `Continue`、上一轮 `parseCandidates` 首行没有声明就判无候选集
     *     （本轮判失败）而不去正文深处搜——全是「宁可判失败，也不猜」。
     *
     * 第 1 条的代价要说清楚：只有**全轮都没票**时才会落到 `Invalid`；别的角色有票的话本轮照样
     * 按剩下的票计，只是不含这一票——这与「失败节点不投票」那条既有口径同一种损害。
     */
    @Test
    fun `an unclosed code fence swallows the rest of the message fail closed`() {
        val candidates = listOf("opt-a", "opt-b")
        // 典型输入：输出被 max_tokens 截断，只有开围栏没有闭围栏。示例行不得成为票。
        assertNull(
            GroupChat.parseBallot("格式如下：\n```text\nVOTE: opt-a\nVOTE: opt-b|我的理由", "alice", candidates),
        )
        // fail-closed 的代价也显式钉住：开围栏**之后**的合法票一样丢（不是「真票优先」）。
        assertNull(GroupChat.parseBallot("我先说结论\n```\n\n```忘了关\nVOTE: opt-b", "alice", candidates))
        // 方向是失败关闭：本轮一个角色都没票时 `tally` 判 Invalid「没有有效选票」，
        // 而不是把示例行算成票再产出一个胜者。
        assertEquals(
            VoteOutcome.Invalid("没有有效选票"),
            GroupChat.tally(emptyList(), candidates, GroupChat.TIE_FAIL),
        )
    }

    /**
     * fail-closed 的作用域**只到开围栏那一行为止**：它之前的行照旧扫，闭围栏之后的行也照旧扫。
     * 所以「先投票、后贴格式示例」这个最常见的正常形状一点没受影响。
     */
    @Test
    fun `votes before and after a closed code fence still count`() {
        val candidates = listOf("opt-a", "opt-b")
        // 票在开围栏之前 → 照收（哪怕后面的围栏压根没闭合）。
        assertEquals(
            "opt-a",
            GroupChat.parseBallot("VOTE: opt-a\n格式如下：\n```text\nVOTE: opt-b", "alice", candidates)
                ?.candidateId,
        )
        // 票在闭围栏之后 → 照收，且**优先于**块内的示例行。
        assertEquals(
            "opt-b",
            GroupChat.parseBallot("```text\nVOTE: opt-a\n```\nVOTE: opt-b", "alice", candidates)
                ?.candidateId,
        )
        // 反过来：块后没有票就只有块内示例 → 无票（钉「示例不顶替真票」这条）。
        assertNull(GroupChat.parseBallot("```text\nVOTE: opt-a\n```\n我选 opt-b。", "alice", candidates))
    }

    /**
     * `parseCandidates` 的围栏处置：**不上状态机，只加一条窄守卫**。
     *
     * 为什么不需要状态机（结构上证明，不是「懒得改」）：判据只看
     * `lineSequence().firstOrNull { it.isNotBlank() }`，也就是**第一个非空行**。而某一行要落在围栏
     * **里面**，必须由**更早的一行**开围栏；第一个非空行之前只有空行，空行里藏不下围栏标记
     * （含反引号或波浪号的行不是空行）。所以「第一个非空行落在围栏内部」这个状态**根本到不了**：
     * 要么第一个非空行**就是**开围栏那行（它自己匹配不上声明正则 → 空候选集），要么它前面没有开围栏。
     * 两条路都已经是失败关闭，跑完整状态机会是**够不到的死代码**。
     *
     * 对照面很清楚：`parseBallot` 扫的是**整条消息**取第一条命中，所以它结构上**是**暴露的——
     * 这正是它那边要上状态机的原因。两条路径的暴露面不同，这就是处置不同的全部理由。
     *
     * ⚠️ 但**真有一个**同类假阳性漏到了这边，就在本组用例的中间那几条：声明写在**闭围栏那一行
     * 自己身上**（`~~~ 候选：a,b\n~~~`）。修前实测拿到的是 `[a, b]`——块里的示例成了本轮候选集。
     * 它够不到围栏**内部**，但确实是「围栏分隔行被当正文读」，与 `parseBallot` 修掉的那一个**完全同类**，
     * 所以用一条「声明行自己不能是围栏标记」的窄守卫堵掉。危害本来就小（形态怪、且方向是失败关闭：
     * 判成空候选集 → 本轮判失败），但两条路径的围栏文法必须一致，否则下一个「顺手也加个围栏判断」
     * 的人会踩到互相矛盾的两套口径。
     */
    @Test
    fun `a candidate declaration can never sit inside a code fence`() {
        // 开围栏那行本身就是第一个非空行 → 匹配不上声明正则 → 空候选集。
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("```\n候选：a,b\n```"))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("```text\n候选：a,b\n```"))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("~~~\n候选：a,b\n~~~"))
        // 只有开围栏、没有闭围栏，同上（空候选集而不是去块里捞）。
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("```\n候选：a,b"))
        // 声明与开围栏挤在同一行 → 前缀是反引号，`^(?:候选|…)` 锚不上 → 空候选集。
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("```候选：a,b"))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("~~~候选：a,b"))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("~~~ 候选：a,b"))
        // ⚠️ 本轮新堵的那一个：**闭围栏那一行自己**带着声明。修前实测 `= [a, b]`。
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("~~~ 候选：a,b\n~~~"))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("``` 候选：a,b\n```"))
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("```` 候选：a,b\n````"))
        // 波浪线之外的等价标记同样算围栏标记（4 个及以上），所以这条窄守卫对两者一视同仁。
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("~~~~ 候选：a\n~~~~"))
        // 反向对照：围栏**外面**、且落在第一个非空行的声明照旧算数（证明上面不是把声明掐了）。
        assertEquals(listOf("a", "b"), GroupChat.parseCandidates("候选：a,b\n```\n候选：c,d\n```"))
        // 反过来，闭围栏**之后**那行声明同样够不到——判据只看第一个非空行，开围栏那行已经占掉了它。
        // ⚠️ 这是「首个非空行」那条**既有主判据**带来的限制，不是围栏感知带来的新限制。
        assertEquals(emptyList<String>(), GroupChat.parseCandidates("```\n候选：a,b\n```\n候选：c"))
    }

    /**
     * 反向对照（防「一刀切拦过头」）：围栏之外的语义**一条都没动**，下面这些输入改前改后逐字一致。
     */
    @Test
    fun `votes outside code fences keep exactly the behaviour they had before`() {
        val candidates = listOf("opt-a", "opt-b")
        // 独占一行的 `VOTE: <id>` 照收，含小写 `vote:`（`ignoreCase`）与缩进（本来就逐行 trim）。
        assertEquals("opt-a", GroupChat.parseBallot("VOTE: opt-a", "alice", candidates)?.candidateId)
        assertEquals("opt-b", GroupChat.parseBallot("vote: opt-b", "alice", candidates)?.candidateId)
        assertEquals("opt-a", GroupChat.parseBallot("    VOTE: opt-a", "alice", candidates)?.candidateId)
        // 竖线后的理由照旧解析。
        assertEquals(
            VoteBallot("alice", "opt-b", "因为乙更稳"),
            GroupChat.parseBallot("VOTE: opt-b|因为乙更稳", "alice", candidates),
        )
        // 行内出现仍不算票（`startsWith` 是整行前缀匹配）。
        assertNull(GroupChat.parseBallot("我选 VOTE: opt-b", "alice", candidates))
        assertNull(GroupChat.parseBallot("请用 VOTE: <id> 的格式", "alice", candidates))
        // 行内代码里的 `VOTE:` 仍不算票——两条都不满足 `startsWith`，所以**没有为它改任何行为**。
        assertNull(GroupChat.parseBallot("格式是 `VOTE: <id>` 这样", "alice", candidates))
        assertNull(GroupChat.parseBallot("``VOTE: opt-a``", "alice", candidates))
        // 候选集外的票照旧丢；压根没有 `VOTE:` 行也照旧是票。
        assertNull(GroupChat.parseBallot("VOTE: opt-z", "alice", candidates))
        assertNull(GroupChat.parseBallot("我选 opt-b", "alice", candidates))
        // 反引号不足 3 个不构成围栏标记 → 那行之后的内容照旧被扫。
        assertEquals("opt-a", GroupChat.parseBallot("``\nVOTE: opt-a", "alice", candidates)?.candidateId)
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
