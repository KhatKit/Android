package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 群轮次判死之后，**残留的旧生成 job** 不得改动新一轮的账目。
 *
 * 两条性质各有各的断言位置，这里只守**执行层的接线**：
 *
 * 1. **归属判定必须排在任何副作用之前**（问题二）。`commitGroupTurn` 拿到的 `plan` 是
 *    `onSuccess` 里当场现算的，判死之后它属于**新轮**；而 `stampGroupTurn` 会
 *    `saveConversation`，是一次不可逆的写。所以「先判归属再盖戳」的**先后关系**本身就是
 *    要守的不变量 —— 一旦排回去，产出就已经落进新轮了，后面的 `return` 什么也救不回来。
 * 2. **被拒的提交不许留副作用**（问题一）。`Advance.Halted` 分支不得调
 *    `completeGroupRound`（会给新一轮提前收尾 / 计票）、不得 `persistRoundState`、
 *    也不得 `groupRunsInFlight.remove`（那会顺手抹掉同会话正在跑的新一轮的镜像，
 *    让它再也取消不掉、也续不下去）。
 *
 * ⚠️ **这是源码文本护栏，不是行为断言。** `commitGroupTurn` 与 `handleMessageComplete`
 * 都是 `ChatManager` 的 private suspend 成员，要 `Application` + `AppScope` + Room DAO，
 * 仓库 testImplementation 只有 junit，跑不起来。**判定本身**（终态不推进、run token 归属）
 * 是纯函数，已在 `GroupTurnCoordinatorTest` 里用真单测覆盖：
 * `advance halts on every terminal status and mutates nothing` /
 * `advance halts before picking a next speaker on a dead round` /
 * `a stale job cannot stamp its output onto the round that replaced it` /
 * `commit admission judges identity only and never liveness` /
 * `resuming a cancelled or failed round still admits and advances`。
 * 这里补的是那些断言碰不到的**调用点顺序**这一半。
 *
 * 为什么抽不出纯函数判据：这里的每条断言都不是「某个值等某个值」，而是「这几个调用在源码里
 * 谁排在谁前面」。返回值语义已经在内核里钉死了；把顺序也硬塞进纯函数，只会造出一个
 * 返回常量的空壳，断言不出任何新东西 —— 而顺序恰恰就是本文件要守的东西本身。
 *
 * 漏掉守卫为什么严重：跨轮盖戳会把**上一轮本该作废的产出**记进**新轮**的
 * `committed_role_ids`，于是新轮的账目错位（多出一个谁都没轮到过的角色），
 * `roundOutputPresent` 还会把它当成该角色的有效产出，导致新轮跳过这个角色；
 * 若再走到 `completeGroupRound`，`resolveVote` 的 `roundMessages` 里就会混进
 * 上一轮的票，投票结论直接算错。
 */
class GroupStaleJobCommitSourceGuardTest {

    private val lines by lazy {
        File("src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt")
            .readText()
            .split("\n")
    }

    /**
     * 「按 key 无条件删」那个形状：`remove(conversationId)` 后面**紧跟**右括号。
     *
     * 必须区分单参 / 两参：两参是 `ConcurrentHashMap.remove(key, value)`，值相等才删，
     * 正是 `clearGroupRunMirrorIfMine` 要的安全语义。所以右括号前不允许出现逗号 ——
     * 否则两参那个安全写法也会被当成违规。
     */
    private val BARE_MIRROR_REMOVE =
        Regex("""groupRunsInFlight\.remove\(\s*conversationId\s*\)""")

    /**
     * 取某个 private 成员函数的整块函数体（成员函数缩进 4，闭合大括号也正好 4 空格）。
     *
     * `suspend` 可有可无：`clearGroupRunMirrorIfMine` 是普通 `private fun`。
     */
    private fun bodyOf(name: String): String {
        val start = lines.indexOfFirst {
            it.startsWith("    private suspend fun $name(") || it.startsWith("    private fun $name(")
        }
        assertTrue("函数没找到：$name", start >= 0)
        val end = lines.indexOfFirstFrom(start + 1) { it == "    }" }
        assertTrue("函数闭合大括号没找到：$name", end > start)
        return lines.subList(start, end + 1).joinToString("\n")
    }

    private inline fun List<String>.indexOfFirstFrom(from: Int, predicate: (String) -> Boolean): Int {
        for (index in from until size) {
            if (predicate(this[index])) return index
        }
        return -1
    }

    /**
     * 取以 [marker] 开头的那一条分支（`when` 分支）。
     *
     * 两种形态都要认：`is X -> { … }` 的带块分支，以及 `is X -> return y` /
     * `null -> expr()` 这种单行表达式分支。范围从标记行起，到**同缩进的闭合大括号**
     * 或**下一个兄弟分支** whichever 先到 —— 后者必须停，否则会把后面的兄弟分支
     * 一起吞进来，`!contains(...)` 那几条断言就会因为别人的代码而恒真。
     */
    private fun branchOf(body: String, marker: String): String {
        val bodyLines = body.split("\n")
        val start = bodyLines.indexOfFirst { it.trimStart().startsWith(marker) }
        assertTrue("分支没找到：$marker", start >= 0)
        val indent = bodyLines[start].takeWhile { it == ' ' }
        var end = start
        for (index in start + 1 until bodyLines.size) {
            val line = bodyLines[index]
            if (line == "$indent}") {
                end = index
                break
            }
            val trimmed = line.trimStart()
            if (line.startsWith(indent) &&
                (trimmed.startsWith("is ") || trimmed.startsWith("null ->") || trimmed.startsWith("}"))
            ) {
                break
            }
        }
        return bodyLines.subList(start, end + 1).joinToString("\n")
    }

    // ------------------------------------------------------------------
    // 1. 归属判定排在盖戳之前
    // ------------------------------------------------------------------

    /**
     * 核心顺序守卫：`checkCommitAdmission` 必须在 `stampGroupTurn` **之前**。
     *
     * 排回去就等于把「先盖戳、再查库」的顺序恢复成原样 —— 那一版里产出已经被
     * `saveConversation` 写进新轮了，后面 `findByRound` 为空 return null 只是
     * 恰好止损，而且只在新轮尚未被抢占那一个窗口里成立。
     */
    @Test
    fun `commitGroupTurn judges ownership before stamping`() {
        val body = bodyOf("commitGroupTurn")
        val admissionAt = body.indexOf("checkCommitAdmission(")
        val stampAt = body.indexOf("stampGroupTurn(")
        assertTrue("commitGroupTurn 必须调 checkCommitAdmission 判归属", admissionAt >= 0)
        assertTrue("commitGroupTurn 必须盖戳", stampAt >= 0)
        assertTrue(
            "归属判定必须排在 stampGroupTurn 之前：盖戳是不可逆的写，判完再写才追得回来",
            admissionAt < stampAt,
        )
    }

    /**
     * 被拒的那一条必须在盖戳之前**真的 return**，不能只是算个结果然后继续往下走。
     */
    @Test
    fun `a denied commit returns before any side effect`() {
        val body = bodyOf("commitGroupTurn")
        val denied = branchOf(body, "if (admission is GroupTurnCoordinator.CommitAdmission.Denied)")
        assertTrue(
            "拒收分支必须直接 return，不许落库",
            denied.contains("return GroupTurnCoordinator.Advance.Halted("),
        )
        val deniedAt = body.indexOf("CommitAdmission.Denied)")
        val stampAt = body.indexOf("stampGroupTurn(")
        val persistAt = body.indexOf("persistRoundState(")
        assertTrue("拒收分支必须排在盖戳之前", deniedAt < stampAt)
        assertTrue("拒收分支必须排在落库之前", deniedAt < persistAt)
    }

    /**
     * `advance` 判死之后（[GroupTurnCoordinator.Advance.Halted]）不许落库。
     *
     * 注意这与「拒收」是**两处不同的早退**：一处是归属不对（连盖戳都不许），
     * 一处是轮次已终态（盖戳是对的、归属没问题，只是不许推进）。
     */
    @Test
    fun `a halted advance is never persisted`() {
        val body = bodyOf("commitGroupTurn")
        val halted = branchOf(body, "is GroupTurnCoordinator.Advance.Halted -> return advance")
        assertTrue(
            "advance 判死后必须原样交回 Halted，不许落库",
            halted.contains("return advance"),
        )
        assertTrue(
            "Halted 分支里不许调 persistRoundState",
            !halted.contains("persistRoundState("),
        )
        assertTrue(
            "persistRoundState 必须排在 Halted 早退之后",
            body.indexOf("persistRoundState(") > body.indexOf("Advance.Halted -> return advance"),
        )
    }

    // ------------------------------------------------------------------
    // 2. run token 必须是从抢占结果捕获的，不能事后反查
    // ------------------------------------------------------------------

    /**
     * `commitGroupTurn` 的令牌必须来自**发起模型调用之前捕获**的 `groupRunToken`。
     *
     * 反过来写成 `groupRunsInFlight[conversationId]?.runToken` 会静默失效：判死路径
     * （`cancelActiveGroupRun` / `abandonDanglingGroupRuns`）都把进程内镜像一起清掉了，
     * 残留 job 事后反查到的要么是 null（血缘不明 → 拒收，等于把正常路径也打死），
     * 要么是**新轮**的令牌（恰好把这份旧产出放行，正是要防的那个 bug）。
     * 这两种都是编译通过、行为错误，所以只能在源码上钉。
     */
    @Test
    fun `the commit call passes the captured run token and never re-reads the in flight mirror`() {
        val body = bodyOf("handleMessageComplete")
        val callAt = body.indexOf("commitGroupTurn(")
        assertTrue("handleMessageComplete 必须调 commitGroupTurn", callAt >= 0)
        val call = body.substring(callAt, body.indexOf('\n', callAt))
        assertTrue(
            "commitGroupTurn 必须收到发起模型调用前捕获的 groupRunToken",
            call.contains("groupRunToken"),
        )
        assertTrue(
            "令牌不许事后从 groupRunsInFlight 反查：判死路径已把镜像清掉，反查等于放行残留 job",
            !call.contains("groupRunsInFlight"),
        )
        assertTrue(
            "groupRunToken 必须与 groupStep 一样在抢占结果那一行捕获",
            body.contains("groupRunToken = (groupEntry as? GroupTurnEntry.Speak)?.runToken"),
        )
    }

    // ------------------------------------------------------------------
    // 3. Halted 分支不许动新一轮的账，也不得罪同会话在跑的新一轮
    // ------------------------------------------------------------------

    /**
     * `onSuccess` 必须显式处理 `Advance.Halted`（`when` 穷尽只是编译期的事，
     * 这里守的是「它被当成一条正经出口处理过」）。
     */
    @Test
    fun `onSuccess handles the halted branch explicitly`() {
        val body = bodyOf("handleMessageComplete")
        val halted = branchOf(body, "is GroupTurnCoordinator.Advance.Halted ->")
        assertTrue(
            "Halted 分支必须留下可排查的运行日志",
            halted.contains("Logging.log("),
        )
        assertTrue(
            "Halted 分支绝不许给这一轮收尾 / 计票：轮次已经作废",
            !halted.contains("completeGroupRound("),
        )
        assertTrue(
            "Halted 分支绝不许落库",
            !halted.contains("persistRoundState("),
        )
    }

    /**
     * `Halted` 分支不得 `groupRunsInFlight.remove`。
     *
     * 这条最容易顺手写成 `null ->` 分支那个样子：残留 job 被判死时，同会话里
     * 正在跑的新一轮在镜像里留着自己的令牌，这里一 remove，那一轮就再也取消不掉、
     * 续跑时也拿不到 `expectedRunToken`（`takeGroupTurn` 会退化成
     * 「本进程已经在跑这一群 → Rejected(ALREADY_RUNNING)」，整轮卡住）。
     */
    @Test
    fun `the halted branch does not wipe the in flight mirror of a newer round`() {
        val body = bodyOf("handleMessageComplete")
        val halted = branchOf(body, "is GroupTurnCoordinator.Advance.Halted ->")
        assertTrue(
            "Halted 分支不许清 groupRunsInFlight：镜像里可能是同会话正在跑的新一轮的令牌",
            !halted.contains("groupRunsInFlight.remove("),
        )
        assertTrue(
            "Halted 分支不许调 clearGroupRunMirrorIfMine：它同样是清镜像，哪怕按令牌比对也一样",
            !halted.contains("clearGroupRunMirrorIfMine("),
        )
        // 反向对照：`BudgetStopped` 轮次确实结束了，仍要照原样清进程内镜像 ——
        // 别把这条护栏写成「一律不许清」。
        val budgetStopped = branchOf(body, "is GroupTurnCoordinator.Advance.BudgetStopped ->")
        assertTrue(
            "BudgetStopped 分支仍应照原样清进程内镜像（别被这条护栏顺手改掉）",
            budgetStopped.contains("groupRunsInFlight.remove("),
        )
    }

    /**
     * `null ->` 分支（`commitGroupTurn` 返回 null，即 `produced == null`）**不许**无条件
     * `groupRunsInFlight.remove(conversationId)`，必须走按令牌比对的
     * [clearGroupRunMirrorIfMine]。
     *
     * 这条断言与 `Halted` 那条同源、但落在另一条分支上，所以要单独钉：这两条分支以前
     * 是同一个形状（都能拿到 `groupRunsInFlight.remove(`），后来 `Halted` 被摘出去走了
     * 「什么都不做」，`null ->` 还留在原地 —— 而它才是**唯一**会被
     * `failGroupTurn` 提前 return 后落到的那条。
     *
     * 残留旧 job（判死之后还在飞的那个）走到这里时，`failGroupTurn` 因归属拒收而**不碰**
     * 镜像，镜像里装的是同会话**新轮**的令牌；无条件 remove 会抹掉它，新轮于是再也
     * `cancelActiveGroupRun` 不掉、`takeGroupTurn` 拿不到 `expectedRunToken`，整轮卡死。
     */
    @Test
    fun `the null branch clears the mirror by run token instead of unconditionally`() {
        val body = bodyOf("handleMessageComplete")
        val nullBranch = branchOf(body, "null ->")
        assertTrue(
            "null -> 分支必须改走 clearGroupRunMirrorIfMine（按 runToken 比对后再清），" +
                "无条件 remove 会抹掉同会话新轮的令牌。实际分支：$nullBranch",
            nullBranch.contains("clearGroupRunMirrorIfMine("),
        )
        assertTrue(
            "null -> 分支不许再出现无条件 groupRunsInFlight.remove(conversationId)。" +
                "实际分支：$nullBranch",
            !BARE_MIRROR_REMOVE.containsMatchIn(nullBranch),
        )
    }

    /**
     * 清镜像那个 helper 本身必须**按令牌比对**：值相等才删。
     *
     * 这是 `null ->` 分支唯一的安全依据，也是「`produced == null` 时靠这一行兜底清镜像」
     * 仍然成立的根据 —— 镜像装的确实是本轮令牌时照清（既有兜底行为逐字不变），
     * 装的不是就不清（新轮不被误伤）。把两参 `remove` 写成单参 `remove` 就等于退回无条件删。
     */
    @Test
    fun `the mirror helper removes by value not by key alone`() {
        val helper = bodyOf("clearGroupRunMirrorIfMine")
        assertTrue(
            "clearGroupRunMirrorIfMine 必须用 ConcurrentHashMap 的两参 remove（值相等才删）",
            helper.contains("groupRunsInFlight.remove(conversationId, InFlightGroupRun("),
        )
        assertTrue(
            "clearGroupRunMirrorIfMine 不许出现单参 remove（那等于无条件删）",
            !BARE_MIRROR_REMOVE.containsMatchIn(helper),
        )
        // 令牌缺失时不删：血缘不明就分不清镜像是谁的，新轮被误伤会整轮卡死。
        assertTrue(
            "clearGroupRunMirrorIfMine 必须在令牌为 null 时提前返回（宁可不删，也不误伤新轮）",
            Regex("""val\s+\w+\s*=\s*\w+\s*\?:""").containsMatchIn(helper) ||
                helper.contains("?: return"),
        )
    }

    /**
     * 轮次终态出口清单不许因为这次改动而变动：判死 / 失败 / 取消这三条路径各自
     * 恰好回收一次平票脚手架（行为逐字不变，由既有的
     * `GroupTieBreakScaffoldingDropSourceGuardTest` 独立覆盖；这里只钉住「没被动过」）。
     */
    @Test
    fun `the terminal round exits are untouched`() {
        listOf("cancelActiveGroupRun", "abandonDanglingGroupRuns", "failGroupTurn").forEach { name ->
            val body = bodyOf(name)
            assertEquals(
                "$name 应恰好回收一次平票脚手架",
                1,
                body.split("dropTieBreakScaffolding(").size - 1,
            )
        }
    }
}
