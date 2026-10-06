package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.SpeakerStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 目标里那条具名验收标准的**穷举证据**：
 * 「过滤器做 JVM 纯函数测试，断言**三角色任意组合下不存在越权消息**」
 * （`docs/beyond-operit-client-changes.md:200`）。
 *
 * ## 为什么需要这一个类（既有 `GroupTurnCoordinatorTest` 不够）
 *
 * 既有用例里有大量按 viewer 参数化的断言（`GroupTurnCoordinatorTest` 的 viewer 段，
 * 外加三个针对盲区的用例：`连发多条` `:1735` / `上一位连发` `:1768` / `上一轮` `:1798`，
 * 以及 `GroupChatTest:34/52/77/1146`），但**每一份夹具都是手工挑的**——既有类里既没有
 * 排列穷举，也没有「模式 × 顺序 × 轮次 × viewer」的矩阵扫描
 * （`grep -E 'exhaustive|combination|combinatorial|permutat'` 在该测试目录零命中）。
 * 后果是**任何一个新组合都没被验过**，而「任意组合」恰恰是契约点名的那条。
 * 本类把那三个维度接成一张可数的矩阵，并把每格的结果钉成**精确的有序可见消息 id 列表**。
 *
 * ## 枚举空间（合计 **36 份转录 / 216 个 viewer case**）
 *
 * | 维度 | 取值 | 份数 |
 * |---|---|---:|
 * | 角色 | `alice` / `bob` / `carol` | 3 |
 * | 模式 | `MODE_PIPELINE` / `MODE_ROUNDTABLE` / `MODE_VOTE` | 3 |
 * | 发言顺序 | 三角色的全部排列 | 6 |
 * | 轮次 | 两轮；轮次间顺序分「同序 / 逆序」两族 | 2 |
 * | viewer | pipeline 3 步 + vote 3 步 + roundtable（议长取遍三角色）12 步 | 18 / 每组顺序 |
 *
 * ⇒ `6 排列 × 2 顺序族 = 12 组`；每组造 **1 份 pipeline 转录（3 viewer）+
 * 1 份 vote 转录（3 viewer）+ 1 份 roundtable 转录（议长取 3 个角色 × 4 步 = 12 viewer）**
 * ⇒ `12 × (3 + 3 + 12) = 216` 个 viewer case。矩阵按 `5 条断言 / case` 计
 * （下面主用例），按代码结构推算 1080 条。
 *
 * **两轮是必需的，不是冗余**：`GroupChat.visibleMessages` 的 `roundStart` 取「最后一条
 * USER 消息的下标」，`predecessorId` 与 `chairRound` 两条分支都带 `index >= roundStart`
 * 守卫。单轮转录里 `roundStart` 恒为 0，守卫是死代码——这一点既有类的 KDoc 自己记着
 * （`GroupTurnCoordinatorTest:1793-1795`：「把该守卫删掉时**没有任何**用例失败」）。
 * 所以本类的每份转录都带**第二条 USER 消息**。
 *
 * ## 三种模式的允许集（逐条从契约推出，不是从实现反推）
 *
 * `GroupChat.visibleMessages` 的函数体**不读 `config`**——模式不进入过滤器，
 * 模式只通过 `GroupChat.plan`（`GroupChat.kt:868-889`）决定**哪个步**带
 * `predecessorId` / `chairRound` 进来。所以允许集 = 通用五条 ＋ 按步而定的两条放宽：
 *
 * 通用五条（契约 `:172-173`、`:200`）：
 * 1. **用户消息**（`:172`「用户消息」／`:200`「用户消息」）
 * 2. **系统/合成消息**（`:200`「系统/合成消息」，由 `role == SYSTEM || isSynthetic`
 *    表达；平票裁决指令与失败节点都走这条）
 * 3. **轮次摘要**（`:172`「+ 轮次摘要」／`:200`「和轮次摘要」）——`role_id = SUMMARY_ID`
 *    的合成节点，**对所有视角无条件可见**
 * 4. **自己发出的消息**（`:172`「自己发出的消息」）——**不带轮次守卫**，所以自己
 *    两轮的发言都看得见
 * 5. **@自己的消息**（`:172`「**@自己**的消息」，落在 `mention_role_ids` 上）
 *
 * 按步而定的两条放宽：
 * 6. `predecessorId` —— 契约 `:179`「pipeline：成员按 `group_config.roles[]` 顺序接力，
 *    **上一角色输出作为下一角色输入**」，而 `:200` 的可见集合只给本轮。
 *    ⇒ **只有 pipeline 的步带 `predecessorId`**（`GroupChat.kt:883-885`；roundtable 与
 *    vote 的步是 `SpeakerStep(role)`，`predecessorId = null`），且**只放开本轮**那条
 *    上一位的输出。
 * 7. `chairRound` —— 契约 `:179`「roundtable：**每人发言一轮后，由指定议长角色汇总**」。
 *    ⇒ **只有 roundtable 的议长步带 `chairRound = true`**（`GroupChat.kt:876-881`），
 *    放开的是**本轮**全员发言，**且仍不得看到上一轮**。
 *
 * 按模式写成允许集（这就是本类断言的形状）：
 * - `MODE_PIPELINE`：一个角色只应额外拿到**本轮上一位**的输出；其他角色的输出一律不可见。
 * - `MODE_ROUNDTABLE`：非议长步只看自己（＋ 用户 / 合成 / 摘要 / @自己）；
 *   `chairRound = true` 才放开**本轮**全部成员发言，**上一轮仍然不可见**。
 * - `MODE_VOTE`：只看自己的票（＋ 用户 / 合成 / 摘要 / @自己）；摘要节点是合成消息，
 *   **对所有人可见**。
 *
 * ## 为什么断言必须是「精确集合相等」而不是「不含 X」
 *
 * 只写 `assertFalse(visible.contains("别人的发言"))` 的实现，**一个把所有消息都放行的
 * 函数也能全绿**——而那正好是越权的极端形状。所以本类对每个 case 都断言
 * **完整的有序可见消息 id 列表 == 契约算出的列表**（外加可读的正文列表）：
 * 少一条和多一条都会红。
 *
 * ## 三条通用不变量（对**每个** case 都断言，且与上面那张允许集表**独立写法**）
 *
 * 1. **自可见**：viewer 一定能看到自己本轮的全部发言（漏一条就是 bug）。断言形状是
 *    `实际可见 ∩ 自己那条集合 == 自己那条集合`（等号，不是 `containsAll`）。
 * 2. **越权为零**：viewer 看到的集合 **⊆** 契约允许集——写成**集合相等**
 *    `实际可见.toSet() == 白名单口径算出的允许集`。白名单是**七个具名桶的并集**，
 *    与主用例里那条 `if` 链是**两种不同写法**，不是同一个函数抄两遍。
 * 3. **议长标志单调**：`chairRound = false` 的可见集合 ⊆ `chairRound = true` 的可见集合
 *    （放宽标志只增不减）。对非议长步还断**严格增**（放开后至少多一条），
 *    对议长步断**恰好相等**（本来就开着）。
 *
 * ## 枚举空间里**刻意不包含**的形状（要说清，否则是隐瞒）
 *
 * `role_id` **不是配置成员**的消息（例如 `role_id = "mallory"`）在本矩阵里**没有**。
 * 理由不是它不重要，而是它**在生产路径上不可达**（`ChatManager` 的盖戳用
 * `SpeakerStep.role.id`，它一定来自 `config.roles`），**且契约没有给出这种消息该有的
 * 答案**：`visibleMessages` 的 `chairRound` 分支只判 `role == ASSISTANT &&
 * index >= roundStart`，不看 `role_id` 是否是成员，所以它在议长步上会被放开——
 * 而「放开本轮全员发言」是契约 `:179` 明确许可的行为。也就是说这一格是**契约没定义的
 * 状态**，不是「扫出来的越权 bug」。
 * ⚠️ 这是本轮**观察到、但既没有修也没有断言**的一个口径缺口，记在这里备查。
 *
 * ## 零设备说明
 *
 * 两个被测函数（[GroupChat.visibleMessages] / [GroupTurnCoordinator.viewerMessages]）
 * 都是**纯函数**：不读数据库、不碰设备、不发模型请求、不写库。所以这 216 个 case
 * 与真机 / 真实模型输出 / UI 渲染**全无关系**——它证明的是「过滤判定本身在三角色
 * 任意组合下不越权」，**不**证明任何一条链路端到端正确。
 */
class GroupViewerExhaustiveMatrixTest {

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private val alice = GroupRole(id = "alice", name = "爱丽丝", assistantId = "asst-1")
    private val bob = GroupRole(id = "bob", name = "鲍勃", assistantId = "asst-2")
    private val carol = GroupRole(id = "carol", name = "卡罗尔", assistantId = "asst-3")

    /**
     * 三角色的**全部 6 种发言顺序**，手写而不是 `permutations()`——这样「6」这个数
     * 本身被 [the enumerated space really is the size this class claims] 钉住，
     * 而不是「恰好调用了一个我记错参数的库函数」。
     */
    private val orders: List<List<String>> = listOf(
        listOf(alice.id, bob.id, carol.id),
        listOf(alice.id, carol.id, bob.id),
        listOf(bob.id, alice.id, carol.id),
        listOf(bob.id, carol.id, alice.id),
        listOf(carol.id, alice.id, bob.id),
        listOf(carol.id, bob.id, alice.id),
    )

    /** 轮次间顺序两族：同序（逼出「上一位在上一轮」那条守卫）/ 逆序（逼出顺序依赖）。 */
    private val families: List<Pair<String, (List<String>) -> List<String>>> = listOf(
        "同序" to { it },
        "逆序" to { it.reversed() },
    )

    private val candidates = listOf("opt-a", "opt-b")

    /** 固定票型，让 vote 模式的正文也过一次 `parseBallot` 的形状。 */
    private val ballots = mapOf(alice.id to "opt-a", bob.id to "opt-b", carol.id to "opt-a")

    private val roleById = mapOf(alice.id to alice, bob.id to bob, carol.id to carol)

    /**
     * ⚠️ **角色顺序是 `config.roles` 的顺序，不是转录里发言的顺序**——
     * `GroupChat.plan`（`GroupChat.kt:883-885`）的 pipeline 分支按
     * `selected.getOrNull(index - 1)` 取上一位，而 `selected` 就是 `config.roles`
     * （`:868-873`）。所以要让那 6 种排列真的换掉 pipeline 的接力顺序，必须**排列
     * `config.roles`**，只重排转录里的发言顺序是不起作用的（这一条是本类第一版
     * 写错的地方，编译器不报错、`assertEquals` 会直接红）。
     */
    private fun pipelineConfig(order: List<String>) = GroupConfig(
        roles = order.map { roleById.getValue(it) },
        mode = GroupChat.MODE_PIPELINE,
        tokenBudgetPerRound = 1000,
    )

    private fun roundtableConfig(order: List<String>, chairRoleId: String) = GroupConfig(
        roles = order.map { roleById.getValue(it) },
        mode = GroupChat.MODE_ROUNDTABLE,
        chairRoleId = chairRoleId,
        tokenBudgetPerRound = 1000,
    )

    private fun voteConfig(order: List<String>) = GroupConfig(
        roles = order.map { roleById.getValue(it) },
        mode = GroupChat.MODE_VOTE,
        tokenBudgetPerRound = 1000,
        voteCandidates = candidates,
        tiePolicy = GroupChat.TIE_FAIL,
    )

    private val chairChoices = listOf(alice.id, bob.id, carol.id)

    // ------------------------------------------------------------------
    // 转录
    // ------------------------------------------------------------------

    /**
     * 一份两轮转录。[tags] 是**有序 tag 列表**，也就是消息顺序；[byTag] 反查。
     *
     * 用 tag 当主键而不是靠 `toText()` 反查，是因为 tag 同时是可读的失败输出，
     * 又是「期望 id 列表」的来源——`UIMessage.id` 是 `Uuid.random()`，同一份转录内
     * 稳定，但跨次运行不同，所以期望值必须由**同一个对象**取出来。
     */
    private class Transcript(
        val mode: String,
        val family: String,
        val order: List<String>,
        val tags: List<String>,
        val byTag: Map<String, UIMessage>,
    ) {
        fun message(tag: String): UIMessage = byTag.getValue(tag)
        fun messages(): List<UIMessage> = tags.map(::message)
        fun indexOf(tag: String): Int = tags.indexOf(tag)

        /** 本轮起点：构造期就知道是第二轮那条 USER 消息，不去复现生产的重算口径。 */
        val roundStart: Int get() = tags.indexOf("u2")

        fun idsOf(tagList: List<String>): List<String> = tagList.map { message(it).id.toString() }
        fun textsOf(tagList: List<String>): List<String> = tagList.map { message(it).toText() }
        fun id(tag: String): String = message(tag).id.toString()

        /** 断言消息里的可读渲染（失败输出要能一眼看出漏了谁）。 */
        fun labelsOf(tagList: List<String>): String =
            tagList.joinToString(" / ") { tag -> "${message(tag).toText()}" }

        /** 可见消息回写成 tag（可见列表必须是这份转录的子序列，天然可反查）。 */
        fun tagsOf(messages: List<UIMessage>): List<String> = messages.map { visible ->
            tags.first { tag -> message(tag).id == visible.id }
        }

        fun roleIdOf(tag: String): String? = message(tag).roleId
    }

    private fun transcript(
        mode: String,
        family: String,
        order: List<String>,
        round2: List<String>,
    ): Transcript {
        val byTag = LinkedHashMap<String, UIMessage>()

        fun user(tag: String, text: String) = UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text(text)),
            turnKind = GroupChat.TURN_USER,
        )

        fun speaker(
            tag: String,
            roleId: String,
            label: String,
            roundId: String,
            mentions: List<String> = emptyList(),
        ) {
            val head = if (mode == GroupChat.MODE_VOTE) {
                "${GroupChat.BALLOT_PREFIX} ${ballots.getValue(roleId)}\n"
            } else {
                ""
            }
            val tail = if (mentions.isEmpty()) "" else "（@${mentions.joinToString(" @")}）"
            byTag[tag] = UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("$head$roleId $label$tail")),
                roleId = roleId,
                mentionRoleIds = mentions,
                roundId = roundId,
                turnKind = GroupChat.TURN_SPEAKER,
            )
        }

        byTag["u1"] = user("u1", "第一轮议题")
        order.forEach { role -> speaker("r1-$role", role, "第一轮发言", roundId = "round-1") }
        byTag["u2"] = user("u2", "第二轮议题")
        round2.forEach { role -> speaker("r2-$role", role, "第二轮发言", roundId = "round-2") }
        // `mention_role_ids` 上的 @：命中通用规则⑤。两条都放在第二轮，于是议长步也会
        // （按契约 `:179` 合法地）看到它们——这是有意的，也给「非议长步不许看到本轮
        // 别人的发言」制造两个反例（alice 看得到 `mt-carol`，因为它 @ 了 carol）。
        speaker("mt-bob", alice.id, "补充结论", roundId = "round-2", mentions = listOf(bob.id))
        speaker("mt-carol", bob.id, "补充结论", roundId = "round-2", mentions = listOf(carol.id))

        if (mode == GroupChat.MODE_VOTE) {
            // 契约 `:172`「轮次摘要」：`role_id = SUMMARY_ID` 的合成节点，对所有视角可见。
            byTag["sum"] = UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("本轮投票结果：opt-b\n票数：opt-b 2 / opt-a 1")),
                roleId = GroupChat.SUMMARY_ID,
                roundId = "round-2",
                turnKind = GroupChat.TURN_VOTE_SUMMARY,
            )
        }

        // 系统/合成消息：通用规则②，平票裁决指令那条形状。
        byTag["sys"] = UIMessage(
            role = MessageRole.SYSTEM,
            parts = listOf(UIMessagePart.Text("[平票] 本轮出现平票，请裁决（合成脚手架）")),
            roleId = alice.id,
            roundId = "round-2",
            turnKind = GroupChat.TURN_CHAIR,
            isSynthetic = true,
        )

        return Transcript(mode, family, order, byTag.keys.toList(), byTag)
    }

    // ------------------------------------------------------------------
    // 契约口径的可见判定（**独立重写**，不复用生产代码任何一行）
    // ------------------------------------------------------------------

    /**
     * 契约 `:172-173` ＋ `:179` ＋ `:200` 的逐条判定。
     *
     * 与 [GroupChat.visibleMessages] 的差别只有一处刻意的：**按契约句子分组**，
     * 而不是按实现里 `when` 分支的书写顺序。判定结果必须逐格相同。
     */
    private fun contractVisible(
        transcript: Transcript,
        viewerId: String,
        predecessorId: String?,
        chairRound: Boolean,
        tag: String,
    ): Boolean {
        val message = transcript.message(tag)
        val index = transcript.indexOf(tag)
        val roundStart = transcript.roundStart
        // 契约 `:172` / `:200`：用户消息。
        if (message.role == MessageRole.USER) return true
        // 契约 `:200`：系统/合成消息。
        if (message.role == MessageRole.SYSTEM || message.isSynthetic) return true
        // 契约 `:172`「+ 轮次摘要」/ `:200`「和轮次摘要」：合成摘要节点对所有视角可见。
        if (message.roleId == GroupChat.SUMMARY_ID) return true
        // 契约 `:172`「自己发出的消息」——**不带轮次守卫**，所以自己两轮都看得见。
        if (message.roleId == viewerId) return true
        // 契约 `:172`「**@自己**的消息」。
        if (viewerId in message.mentionRoleIds) return true
        // 契约 `:179`「上一角色输出作为下一角色输入」+ `:200` 的本轮限定。
        if (predecessorId != null && message.roleId == predecessorId && index >= roundStart) return true
        // 契约 `:179`「roundtable 全员完成后仅议长汇总」+ `:200` 的本轮限定。
        if (chairRound && message.role == MessageRole.ASSISTANT && index >= roundStart) return true
        return false
    }

    /** 契约算出的**有序**可见 tag 列表。 */
    private fun expectedTags(
        transcript: Transcript,
        viewerId: String,
        predecessorId: String?,
        chairRound: Boolean,
    ): List<String> = transcript.tags.filter {
        contractVisible(transcript, viewerId, predecessorId, chairRound, it)
    }

    /**
     * 不变量②的**独立口径**：七个具名桶的**并集**（不是 [contractVisible] 那条 `if` 链）。
     *
     * 两种写法各写一遍，是为了让「越权为零」这一条不被单一实现的笔误带着走：
     * 其中一处写错时另一处仍然是干净的期望值，失败会指向具体哪一条桶。
     */
    private fun allowedTagsByBuckets(
        transcript: Transcript,
        viewerId: String,
        predecessorId: String?,
        chairRound: Boolean,
    ): Set<String> {
        val t = transcript
        val roundScoped = t.tags.filter { t.indexOf(it) >= t.roundStart }
        val buckets: List<Pair<String, List<String>>> = listOf(
            "①用户消息" to t.tags.filter { t.message(it).role == MessageRole.USER },
            "②系统/合成消息" to t.tags.filter {
                t.message(it).role == MessageRole.SYSTEM || t.message(it).isSynthetic
            },
            "③轮次摘要" to t.tags.filter { t.message(it).roleId == GroupChat.SUMMARY_ID },
            "④自己发出的消息" to t.tags.filter { t.message(it).roleId == viewerId },
            "⑤@自己的消息" to t.tags.filter { viewerId in t.message(it).mentionRoleIds },
            "⑥上一位的本轮输出" to roundScoped.filter {
                predecessorId != null && t.message(it).roleId == predecessorId
            },
            "⑦议长步的本轮全员发言" to roundScoped.filter {
                chairRound && t.message(it).role == MessageRole.ASSISTANT
            },
        )
        return buckets.flatMap { it.second }.toSet()
    }

    // ------------------------------------------------------------------
    // viewer case 枚举
    // ------------------------------------------------------------------

    private class ViewerCase(val label: String, val step: SpeakerStep)

    /**
     * 一个配置下**生产可达**的全部 viewer 步：
     * - pipeline：`plan()` 给 3 个步，只有第 2、3 个带 `predecessorId`；
     * - vote：3 个步，`predecessorId` 全为 `null`、`chairRound` 全为 `false`
     *   （`GroupChat.kt:887` 的 `else` 分支）；
     * - roundtable：非议长步 2 个 ＋ 议长步 1 个（`chairRound = true`），
     *   **外加议长那个角色的非议长步**——契约 `:179` 说议长也是「每人发言一轮」
     *   的一员，所以它也必须有一个只看自己的步。生产 `plan()` 不产出这个组合，
     *   这里按 `copy(chairRound = false)` 补出来，好让「三个角色都当过非议长步」。
     */
    private fun viewerCases(mode: String, config: GroupConfig): List<ViewerCase> = when (mode) {
        GroupChat.MODE_PIPELINE, GroupChat.MODE_VOTE ->
            GroupChat.plan(config, emptyList()).map { ViewerCase("$mode/plan/${it.role.id}", it) }

        GroupChat.MODE_ROUNDTABLE -> {
            val steps = GroupChat.plan(config, emptyList())
            val chairId = config.chairRoleId
            steps.map { ViewerCase("$mode/$chairId/plan/${it.role.id}", it) } +
                steps.filter { it.role.id == chairId }.map {
                    ViewerCase("$mode/$chairId/nonChairStep/${it.role.id}", it.copy(chairRound = false))
                }
        }

        else -> error("未知模式：$mode")
    }

    private fun transcriptLabel(t: Transcript): String =
        "${t.mode}/order=${t.order.joinToString("-")}/${t.family}"

    private fun where(t: Transcript, vc: ViewerCase): String = "${transcriptLabel(t)} viewer=${vc.label}"

    /** 枚举空间的全部 (转录, 配置, viewer case)，共 216 组。 */
    private fun matrix(): List<Triple<Transcript, GroupConfig, ViewerCase>> {
        val out = mutableListOf<Triple<Transcript, GroupConfig, ViewerCase>>()
        orders.forEach { order ->
            families.forEach { (familyName, round2Of) ->
                val round2 = round2Of(order)
                listOf(GroupChat.MODE_PIPELINE, GroupChat.MODE_VOTE).forEach { mode ->
                    val t = transcript(mode, familyName, order, round2)
                    val config = if (mode == GroupChat.MODE_PIPELINE) {
                        pipelineConfig(order)
                    } else {
                        voteConfig(order)
                    }
                    viewerCases(mode, config).forEach { out += Triple(t, config, it) }
                }
                // roundtable 一份转录配三个议长：换的是配置（`chair_role_id`），不是消息。
                val roundtable = transcript(GroupChat.MODE_ROUNDTABLE, familyName, order, round2)
                chairChoices.forEach { chairId ->
                    val config = roundtableConfig(order, chairId)
                    viewerCases(GroupChat.MODE_ROUNDTABLE, config).forEach {
                        out += Triple(roundtable, config, it)
                    }
                }
            }
        }
        return out
    }

    /**
     * `closed` 是否是 `open` 的**保序子序列**。
     *
     * ⚠️ 刻意**不是前缀**：`filterIndexed` 保的是**相对次序**，而新增进来的那些消息
     * 会**插在**原有两条之间。本类第一版把这条写成前缀断言，直接红——正是它把
     * 「⊆」和「前缀 ⊆」的区别钉了出来：放宽议长标志只保证**不隐藏、不重排**，
     * 不保证新内容都排在末尾。
     */
    private fun isSubsequence(closed: List<String>, open: List<String>): Boolean {
        var i = 0
        for (element in open) {
            if (i < closed.size && closed[i] == element) i++
        }
        return i == closed.size
    }

    // ------------------------------------------------------------------
    // 用例
    // ------------------------------------------------------------------

    /**
     * 主断言：216 个 case 里每一个的**完整有序可见消息 id 列表**都等于契约算出的那份。
     *
     * 每个 case 四条断言：
     * 1. 精确有序 **id 列表**相等（少一条 / 多一条都红）；
     * 2. 精确有序**正文列表**相等（可读的那一半，失败时直接看得出漏了谁）；
     * 3. 不变量②：实际可见集合 == 七个具名桶并集算出的允许集（**集合相等**）；
     * 4. 不变量①：实际可见 ∩ 自己那条 == 自己那条（自可见，漏一条就红）。
     */
    @Test
    fun `every viewer of every mode sees exactly the contract visible set`() {
        var cases = 0
        matrix().forEach { (t, config, vc) ->
            val step = vc.step
            val viewerId = step.role.id
            val actual = GroupTurnCoordinator.viewerMessages(config, t.messages(), step)
            val expected = expectedTags(t, viewerId, step.predecessorId, step.chairRound)
            val actualIds = actual.map { it.id.toString() }

            assertEquals(
                "${where(t, vc)}：可见消息 id 列表（按契约有序）",
                t.idsOf(expected),
                actualIds,
            )
            assertEquals(
                "${where(t, vc)}：可见消息正文列表（按契约有序）",
                t.textsOf(expected),
                actual.map { it.toText() },
            )
            assertEquals(
                "${where(t, vc)}：可见集合必须等于契约允许集（不变量② 越权为零）",
                allowedTagsByBuckets(t, viewerId, step.predecessorId, step.chairRound)
                    .map { t.id(it) }
                    .toSortedSet(),
                actualIds.toSortedSet(),
            )
            val ownIds = t.tags.filter { t.roleIdOf(it) == viewerId }.map { t.id(it) }.toSet()
            assertEquals(
                "${where(t, vc)}：自己两轮的全部发言都必须可见（不变量① 自可见）",
                ownIds,
                actualIds.toSet().intersect(ownIds),
            )
            cases++
        }
        assertEquals("枚举空间必须是 216 个 viewer case（12 组顺序 × 18 个 viewer）", 216, cases)
    }

    /**
     * 不变量③：议长标志单调。
     *
     * `chairRound = false` 的可见集合 ⊆ `chairRound = true` 的可见集合，且顺序必须是
     * **前缀**关系——过滤器是 `filterIndexed`，只可能变长，不会重排。
     * 非议长步额外断**严格增**：放开议长步之后，本轮别人的发言必须真的进来几条。
     */
    @Test
    fun `opening the chair flag never hides anything`() {
        var cases = 0
        matrix().forEach { (t, config, vc) ->
            val step = vc.step
            val closedTags = t.tagsOf(
                GroupTurnCoordinator.viewerMessages(config, t.messages(), step.copy(chairRound = false)),
            )
            val openTags = t.tagsOf(
                GroupTurnCoordinator.viewerMessages(config, t.messages(), step.copy(chairRound = true)),
            )

            assertEquals(
                "${where(t, vc)}：chairRound=true 的可见列表必须保序包含 chairRound=false 的全部内容",
                closedTags,
                closedTags.filter { it in openTags },
            )
            assertTrue(
                "${where(t, vc)}：放宽议长标志只增不减、不重排；关=${t.labelsOf(closedTags)} 开=${t.labelsOf(openTags)}",
                isSubsequence(t.idsOf(closedTags), t.idsOf(openTags)),
            )
            if (step.chairRound) {
                // 生产步本身开着议长标志，所以它的**实际**输出必须等于 `open` 探针——
                // 这条把「`viewerMessages` 真的把 `step.chairRound` 传下去了」也断掉。
                assertEquals(
                    "${where(t, vc)}：本来就是议长步，它的实际输出必须等于 chairRound=true 探针",
                    t.idsOf(openTags),
                    t.idsOf(t.tagsOf(GroupTurnCoordinator.viewerMessages(config, t.messages(), step))),
                )
            } else {
                assertEquals(
                    "${where(t, vc)}：非议长步的实际输出必须等于 chairRound=false 探针",
                    t.idsOf(closedTags),
                    t.idsOf(t.tagsOf(GroupTurnCoordinator.viewerMessages(config, t.messages(), step))),
                )
                assertTrue(
                    "${where(t, vc)}：放开议长步必须严格多出本轮别人的发言" +
                        "（关=${t.labelsOf(closedTags)} / 开=${t.labelsOf(openTags)}）",
                    closedTags.size < openTags.size,
                )
                assertEquals(
                    "${where(t, vc)}：议长步的允许集也要满足七桶口径",
                    allowedTagsByBuckets(t, step.role.id, step.predecessorId, true)
                        .map { t.id(it) }
                        .toSortedSet(),
                    t.idsOf(openTags).toSortedSet(),
                )
            }
            cases++
        }
        assertEquals("不变量③ 必须覆盖全部 216 个 case", 216, cases)
    }

    /**
     * 跨轮越权的单独一条：上一轮任何别的角色的发言，**连议长步也不许出现**；
     * 非议长步更进一步——本轮别人的发言也不许出现（pipeline 的「本轮上一位」除外）。
     *
     * 主用例那条是集合相等（负责「不多不少」），这条是可读的「交集为空」
     * （负责「最关键的那一类越权一条都不许有」），两条互为对照。
     */
    @Test
    fun `no viewer ever sees another role output from a previous round`() {
        var cases = 0
        matrix().forEach { (t, config, vc) ->
            val step = vc.step
            val viewerId = step.role.id
            val visibleTags = t.tagsOf(GroupTurnCoordinator.viewerMessages(config, t.messages(), step))

            val foreignRound1 = t.tags.filter {
                it.startsWith("r1-") && t.roleIdOf(it) != viewerId
            }
            assertTrue(
                "${where(t, vc)}：上一轮别人的发言一个都不许出现；" +
                    "禁止=${t.labelsOf(foreignRound1)} 实际=${t.labelsOf(visibleTags)}",
                visibleTags.none { it in foreignRound1 },
            )

            if (!step.chairRound) {
                val foreignRound2 = t.tags.filter {
                    it.startsWith("r2-") && t.roleIdOf(it) != viewerId && t.roleIdOf(it) != step.predecessorId
                }
                assertTrue(
                    "${where(t, vc)}：非议长步不许看到本轮别人的发言（本轮上一位除外）；" +
                        "禁止=${t.labelsOf(foreignRound2)} 实际=${t.labelsOf(visibleTags)}",
                    visibleTags.none { it in foreignRound2 },
                )
            }
            cases++
        }
        assertEquals("跨轮越权那条必须覆盖全部 216 个 case", 216, cases)
    }

    /**
     * `MODE_PIPELINE` 的允许集单独钉一遍：一条角色只应额外拿到**本轮上一位**的输出。
     *
     * 顺带把两件「oracle 前提」本身也断掉——pipeline 的步**拿不到**议长那条放宽
     * （`plan()` 不给 pipeline 设 `chairRound`），第 i 步的上一位**就是配置顺序里
     * 第 i−1 个角色**。这两条是上面那个 `if` 链能成立的前提，不断掉的话，
     * 「oracle 写错了」会伪装成「实现有 bug」。
     */
    @Test
    fun `a pipeline viewer gets exactly the previous speaker of the current round`() {
        var cases = 0
        orders.forEach { order ->
            families.forEach { (familyName, round2Of) ->
                val t = transcript(GroupChat.MODE_PIPELINE, familyName, order, round2Of(order))
                val config = pipelineConfig(order)
                GroupChat.plan(config, emptyList()).forEachIndexed { index, step ->
                    val previous = order.getOrNull(index - 1)
                    assertEquals("pipeline 第 $index 步的上一位", previous, step.predecessorId)
                    assertFalse("pipeline 的步不许带议长标志（契约 :179 只在 roundtable 放开）", step.chairRound)

                    val expected = t.tags.filter { tag ->
                        val message = t.message(tag)
                        message.role == MessageRole.USER ||
                            message.role == MessageRole.SYSTEM ||
                            message.isSynthetic ||
                            message.roleId == GroupChat.SUMMARY_ID ||
                            message.roleId == step.role.id ||
                            step.role.id in message.mentionRoleIds ||
                            (previous != null &&
                                message.roleId == previous &&
                                t.indexOf(tag) >= t.roundStart)
                    }
                    val visibleTags = t.tagsOf(GroupTurnCoordinator.viewerMessages(config, t.messages(), step))
                    assertEquals(
                        "${where(t, vc = ViewerCase("plan/${step.role.id}", step))}：" +
                            "pipeline 可见集 == 用户两条 + 自己两轮 + @自己的 + 系统合成 + 本轮上一位",
                        t.idsOf(expected),
                        t.idsOf(visibleTags),
                    )
                    assertEquals(
                        "${transcriptLabel(t)} viewer=${step.role.id}：pipeline 可见的正文必须逐条对上",
                        t.textsOf(expected),
                        t.textsOf(visibleTags),
                    )
                    cases++
                }
            }
        }
        assertEquals("pipeline 的 viewer case 数必须是 12 组 × 3 步", 36, cases)
    }

    /**
     * `MODE_ROUNDTABLE` 的允许集单独钉一遍：非议长步只看自己；议长步放开**本轮**
     * 全员发言，且**仍不得看到上一轮**。议长角色取遍三个角色。
     */
    @Test
    fun `a roundtable member step sees only itself while the chair step opens the round`() {
        var cases = 0
        orders.forEach { order ->
            families.forEach { (familyName, round2Of) ->
                val t = transcript(GroupChat.MODE_ROUNDTABLE, familyName, order, round2Of(order))
                chairChoices.forEach { chairId ->
                    val config = roundtableConfig(order, chairId)
                    GroupChat.plan(config, emptyList()).forEach { step ->
                        val visibleTags = t.tagsOf(GroupTurnCoordinator.viewerMessages(config, t.messages(), step))
                        val label = "${transcriptLabel(t)} chair=$chairId viewer=${step.role.id} chairRound=${step.chairRound}"
                        if (step.chairRound) {
                            assertEquals("$label：议长必须是配置指定的议长", chairId, step.role.id)
                            // 「本轮全部成员发言 ＋ 用户两条 ＋ 系统合成 ＋ **议长自己历轮的发言**」。
                            // ⚠️ 最后那半句不是笔误：通用规则④「自己发出的消息」**不带轮次
                            // 守卫**，所以议长看得见自己第一轮那条，看不见别人第一轮那些。
                            val roundOnly = t.tags.filter { tag ->
                                !(tag.startsWith("r1-") && t.roleIdOf(tag) != step.role.id)
                            }
                            assertEquals(
                                "$label：议长步必须放开本轮全部成员发言（上一轮仍然不可见）",
                                t.idsOf(roundOnly),
                                t.idsOf(visibleTags),
                            )
                            assertEquals(
                                "$label：议长步的可见正文必须逐条对上",
                                t.textsOf(roundOnly),
                                t.textsOf(visibleTags),
                            )
                        } else {
                            val ownOnly = t.tags.filter { tag ->
                                val message = t.message(tag)
                                message.role == MessageRole.USER ||
                                    message.roleId == step.role.id ||
                                    step.role.id in message.mentionRoleIds ||
                                    message.role == MessageRole.SYSTEM ||
                                    message.isSynthetic
                            }
                            assertEquals(
                                "$label：非议长步只应看到 用户两条 + 自己两轮 + @自己的 + 系统合成",
                                t.idsOf(ownOnly),
                                t.idsOf(visibleTags),
                            )
                            assertEquals(
                                "$label：非议长步的可见正文必须逐条对上",
                                t.textsOf(ownOnly),
                                t.textsOf(visibleTags),
                            )
                        }
                        cases++
                    }
                }
            }
        }
        assertEquals("roundtable 的 plan 步数必须是 12 组 × 3 议长 × 3 步", 108, cases)
    }

    /**
     * `MODE_VOTE` 的允许集单独钉一遍：只看自己的票；摘要节点对**每个**视角可见。
     *
     * 「对所有人可见」在这里是**逐 viewer** 断的（三个角色各断一次），因为
     * `role_id = SUMMARY_ID` 的合成节点一旦被收窄，**三个视角会一起消失**——
     * `visibleMessages` 的 KDoc（`GroupChat.kt:716-721`）记的就是这件事。
     * 顺带断可见的发言**真能被 `parseBallot` 收成票、且收的是本角色的票面**，
     * 这才让「只看自己的票」不只是「只看自己的字」。
     */
    @Test
    fun `a vote viewer sees only its own ballot and everybody sees the summary node`() {
        var cases = 0
        orders.forEach { order ->
            families.forEach { (familyName, round2Of) ->
                val t = transcript(GroupChat.MODE_VOTE, familyName, order, round2Of(order))
                val config = voteConfig(order)
                GroupChat.plan(config, emptyList()).forEach { step ->
                    assertNull("vote 的步没有上一位（契约 :201 只接受结构化票，不接力）", step.predecessorId)
                    assertFalse("vote 没有议长步", step.chairRound)

                    val visible = GroupTurnCoordinator.viewerMessages(config, t.messages(), step)
                    val visibleTags = t.tagsOf(visible)
                    assertTrue(
                        "${transcriptLabel(t)} viewer=${step.role.id}：摘要节点对每个视角都必须可见；" +
                            "实际=${t.labelsOf(visibleTags)}",
                        "sum" in visibleTags,
                    )

                    val speakers = visible.filter {
                        it.role == MessageRole.ASSISTANT && it.roleId != GroupChat.SUMMARY_ID
                    }
                    val expectedSpeakers = t.tags.filter { tag ->
                        t.message(tag).role == MessageRole.ASSISTANT &&
                            (t.roleIdOf(tag) == step.role.id || step.role.id in t.message(tag).mentionRoleIds)
                    }
                    assertEquals(
                        "${transcriptLabel(t)} viewer=${step.role.id}：" +
                            "可见的发言 == 自己两轮 + @自己的那条，别的角色一条都不许出现",
                        t.idsOf(expectedSpeakers),
                        speakers.map { it.id.toString() },
                    )
                    assertEquals(
                        "${transcriptLabel(t)} viewer=${step.role.id}：可见发言必须逐条解析成票，且票面固定",
                        expectedSpeakers.map { ballots.getValue(t.roleIdOf(it)!!) },
                        speakers.map {
                            GroupChat.parseBallot(it.toText(), it.roleId!!, candidates)!!.candidateId
                        },
                    )
                    cases++
                }
            }
        }
        assertEquals("vote 的 viewer case 数必须是 12 组 × 3 步", 36, cases)
    }

    /**
     * 枚举空间本身的护栏：把矩阵的每个维度各断一个数，免得矩阵被无声改小。
     *
     * 这条不是凑数——「穷举」这条验收标准本身需要**可数**：如果哪天有人删掉
     * 「逆序」那一族、或者议长只取一个角色、或者某处 `viewerCases` 少列了一个步，
     * 上面所有断言**一条都不会红**，但覆盖面已经掉了一维。
     */
    @Test
    fun `the enumerated space really is the size this class claims`() {
        assertEquals("三角色的发言顺序必须是全部 6 种排列", 6, orders.size)
        assertEquals("6 种排列必须两两不同", 6, orders.distinct().size)
        orders.forEach { order ->
            assertEquals(
                "每个排列必须恰好三个角色各一次：$order",
                listOf(alice.id, bob.id, carol.id).sorted(),
                order.sorted(),
            )
        }
        assertEquals("轮次顺序必须两族：同序 / 逆序", 2, families.size)
        assertEquals("同序族必须真的不变", orders[0], families[0].second(orders[0]))
        assertEquals("逆序族必须真的把顺序翻过来", listOf(carol.id, bob.id, alice.id), families[1].second(orders[0]))

        val all = matrix()
        assertEquals("矩阵必须是 216 个 viewer case", 216, all.size)
        assertEquals(
            "模式分布：pipeline 36 / vote 36 / roundtable 144",
            mapOf(
                GroupChat.MODE_PIPELINE to 36,
                GroupChat.MODE_VOTE to 36,
                GroupChat.MODE_ROUNDTABLE to 144,
            ),
            all.groupingBy { it.first.mode }.eachCount(),
        )
        assertEquals(
            "viewer 格必须两两不同（不能有重复扫描）",
            all.size,
            all.map { "${it.first.mode}|${it.first.family}|${it.first.order}|${it.third.label}" }.distinct().size,
        )
        assertEquals(
            "转录份数：12 组顺序 × 3 种模式 = 36（roundtable 一份转录配三个议长，不重复造）",
            36,
            all.map { it.first }.distinct().size,
        )
        assertEquals(
            "每份转录的两轮形状固定：USER 2 条 + 第一轮发言 3 条 + 第二轮发言 3 条 + @ 2 条 + 系统合成 1 条" +
                "（vote 另加一个摘要节点 → 12 条）",
            mapOf(
                GroupChat.MODE_PIPELINE to 11,
                GroupChat.MODE_ROUNDTABLE to 11,
                GroupChat.MODE_VOTE to 12,
            ),
            all.map { it.first }.distinct().associate { t -> t.mode to t.tags.size },
        )
        assertEquals(
            "vote 之外的转录都不许带摘要节点（role_id = SUMMARY_ID 只由投票产出）",
            mapOf(GroupChat.MODE_PIPELINE to 0, GroupChat.MODE_ROUNDTABLE to 0, GroupChat.MODE_VOTE to 1),
            all.map { it.first }.distinct().associate { t -> t.mode to t.tags.count { it == "sum" } },
        )
    }

    /**
     * 契约 `:200`「过滤器做 JVM 纯函数测试」＋「工具调用、检索与记忆注入均使用同一
     * viewer 过滤结果」：**两个入口必须逐格相同**。
     *
     * `GroupTurnCoordinator.viewerMessages` 只是把 `SpeakerStep` 拆成四个参数再转调
     * `GroupChat.buildContext`（`GroupTurnCoordinator.kt:787-797`）。哪天它在中间动了
     * 任何一处参数，两个入口就会分叉——那正是契约 `:200` 禁止的「同一份 viewer
     * 过滤结果」被拆成两份。
     */
    @Test
    fun `both viewer entry points return exactly the same set`() {
        var cases = 0
        matrix().forEach { (t, config, vc) ->
            val step = vc.step
            val viaStep = GroupTurnCoordinator.viewerMessages(config, t.messages(), step)
            val viaArgs = GroupChat.visibleMessages(
                config = config,
                messages = t.messages(),
                viewerId = step.role.id,
                predecessorId = step.predecessorId,
                chairRound = step.chairRound,
            )
            val viaContext = GroupChat.buildContext(
                viewerRoleId = step.role.id,
                messages = t.messages(),
                config = config,
                predecessorId = step.predecessorId,
                chairRound = step.chairRound,
            )
            assertEquals("${where(t, vc)}：viewerMessages 与 visibleMessages 必须逐条相等", viaArgs, viaStep)
            assertEquals("${where(t, vc)}：buildContext 与 visibleMessages 必须逐条相等", viaArgs, viaContext)
            cases++
        }
        assertEquals("两个入口的一致性必须覆盖全部 216 个 case", 216, cases)
    }

    /**
     * 模式**不进入过滤器**：`visibleMessages` 的函数体不读 `config`，模式只经由
     * `plan()` 决定哪个步带 `predecessorId` / `chairRound`。
     *
     * 这一条把上面那句话变成可证伪的：**同一份消息** ＋ **同一组参数**
     * （`viewerId` / `predecessorId` / `chairRound`），只换 `config.mode`
     * （pipeline / roundtable × 三个议长 / vote），结果必须**逐条相等**。
     * 如果哪天有人把 `mode` 加进过滤条件（例如「vote 模式额外放开别人的票」），
     * 这条会红——而那正是「只看自己的票」那条契约的实现形态。
     */
    @Test
    fun `the filter never reads the mode out of the config`() {
        orders.forEach { order ->
            families.forEach { (familyName, round2Of) ->
                val t = transcript(GroupChat.MODE_PIPELINE, familyName, order, round2Of(order))
                val configs = listOf(pipelineConfig(order), voteConfig(order)) +
                    chairChoices.map { roundtableConfig(order, it) }
                configs.drop(1).forEach { other ->
                    listOf(alice.id, bob.id, carol.id).forEach { viewerId ->
                        listOf(null, alice.id, bob.id, carol.id).forEach { predecessorId ->
                            listOf(false, true).forEach { chairRound ->
                                val expected = GroupChat.visibleMessages(
                                    config = configs.first(),
                                    messages = t.messages(),
                                    viewerId = viewerId,
                                    predecessorId = predecessorId,
                                    chairRound = chairRound,
                                )
                                val actual = GroupChat.visibleMessages(
                                    config = other,
                                    messages = t.messages(),
                                    viewerId = viewerId,
                                    predecessorId = predecessorId,
                                    chairRound = chairRound,
                                )
                                assertEquals(
                                    "模式不得进入过滤器：mode=${other.mode} viewer=$viewerId " +
                                        "pred=$predecessorId chair=$chairRound " +
                                        "order=${order.joinToString("-")}/$familyName",
                                    t.textsOf(expectedTags(t, viewerId, predecessorId, chairRound)),
                                    actual.map { it.toText() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}