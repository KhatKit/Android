package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 群轮次判死之后，**残留的旧生成 job 的失败**（超时 / 单角色报错）不得改动新一轮的账目。
 *
 * 这是 [GroupStaleJobCommitSourceGuardTest]（提交路 / `onSuccess`）的对偶：那边守
 * `commitGroupTurn`，这边守 [failGroupTurn] —— 也就是 `handleMessageComplete` 的 `onFailure`
 * 里那两条调用（超时 / `it !is CancellationException`）与 `commitGroupTurn` 里「本轮没有产出
 * 内容」那一条。判死路径（`cancelActiveGroupRun` / `abandonDanglingGroupRuns`）**不取消任何
 * job**，所以残留 job 跑完时照样会进 `onFailure`，而 `failGroupTurn` 里的 `plan` 是现算的。
 *
 * ⚠️ **这是源码文本护栏，不是行为断言。** `failGroupTurn` 是 `ChatManager` 的 private suspend
 * 成员，要 `Application` + `AppScope` + Room DAO，仓库 `testImplementation` 只有 junit，
 * 跑不起来。**判定本身**（身份 + 终态两条判据、以及正常失败必须放行）是纯函数，已在
 * `GroupTurnCoordinatorTest` 里用真单测覆盖：
 * `a stale job failure cannot be charged to the round that replaced it` /
 * `a dead round refuses a failure closeout while the commit side still admits it` /
 * `a normal role failure is admitted and still writes its own round` /
 * `a normal timeout is admitted and still writes its own round` /
 * `resuming a dead round still admits its failure` /
 * `failure admission reuses the commit side identity criteria`。
 * 这里补的是那些断言碰不到的**执行层接线**这一半：守卫的位置、拒收分支里不许有的副作用、
 * 以及令牌有没有真的从抢占结果一路透传到这三个调用点。
 *
 * 为什么这些只能钉在源码上：这里要守的每一条都不是「某个值等于某个值」，而是「这几个调用在
 * 源码里谁排在谁前面」「令牌是从哪条路径透传过来的」。返回值语义已经在内核里钉死；把顺序也
 * 硬塞进纯函数只会造出一个返回常量的空壳，断言不出新东西 —— 而顺序恰恰就是本文件要守的东西。
 *
 * 漏掉守卫为什么严重：`failGroupTurn` 往下是四个不可逆动作 —— 写一条 `errorNode`（`roundId`
 * 取的是现算 plan 的轮次）、落运行日志终态（把那一轮写成 FAILED/TIMEOUT）、**全会话范围**地
 * 回收平票脚手架、清进程内镜像（同会话里那一份可能是新轮的令牌，新轮于是再也取消不掉、
 * 续跑时也拿不到 `expectedRunToken`，整轮卡住）。
 */
class GroupStaleJobFailureSourceGuardTest {

    private val lines by lazy {
        File("src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt")
            .readText()
            .split("\n")
    }

    /** 取某个 private suspend fun 的整块函数体（成员函数缩进 4，闭合大括号也正好 4 空格）。 */
    private fun bodyOf(name: String): String {
        val start = lines.indexOfFirst { it.startsWith("    private suspend fun $name(") }
        assertTrue("函数没找到：$name", start >= 0)
        val end = lines.indexOfFirstFrom(start + 1) { it == "    }" }
        assertTrue("函数闭合大括号没找到：$name", end > start)
        return lines.subList(start, end + 1).joinToString("\n")
    }

    /** 拒收早退那一行的标记（顺序断言以它为准，见 `judges ownership before any side effect`）。 */
    private val DENIED_MARKER = "if (admission is GroupTurnCoordinator.CommitAdmission.Denied)"

    private inline fun List<String>.indexOfFirstFrom(from: Int, predicate: (String) -> Boolean): Int {
        for (index in from until size) {
            if (predicate(this[index])) return index
        }
        return -1
    }

    /**
     * 取以 [marker] 开头的那一条分支（`if (...) { … }` 或 `when` 分支）。
     *
     * 两种形态都要认：
     *
     * - **单行表达式分支**：`is X -> return y`、`null -> expr()`。整条分支就在标记行里，
     *   **没有闭合大括号可找**。
     * - **多行块分支**：`if (…) { … }`、`is X -> { … }`。范围从标记行起，到**同缩进的闭合大括号**
     *   或**下一个兄弟分支** whichever 先到 —— 后者必须停，否则会把后面的兄弟分支一起吞进来，
     *   `!contains(...)` 那几条断言就会因为别人的代码而恒真。
     *
     * ## 两种形态靠什么区分
     *
     * 判据**不能**是「marker 之后同一行还有没有内容」：块分支的条件后面紧跟的就是 `{`，
     * 照样有内容，用那条判据会把**所有块分支误判成单行**，块分支就只切出一行、
     * `!contains(...)` 全成空检查。正确判据是「**这一行是否以 `{` 结尾**」。
     *
     * ⚠️ **切分退化必须显式拦掉**（C8）。块分支若两个锚点都没找到，循环走完 [end] 仍是
     * [start]，本函数**只返回标记行本身**，于是拒收分支里那四条 `!denied.contains(forbidden)`
     * 必然全为真 —— 护栏变成「谁都不违规而恒绿」。所以块分支必须 `assertTrue(end > start)`。
     */
    private fun branchOf(body: String, marker: String): String {
        val bodyLines = body.split("\n")
        val start = bodyLines.indexOfFirst { it.trimStart().startsWith(marker) }
        assertTrue("分支没找到：$marker", start >= 0)
        val indent = bodyLines[start].takeWhile { it == ' ' }

        // 形态一：单行表达式分支，整条分支就是这一行。
        if (!bodyLines[start].trimStart().endsWith("{")) return bodyLines[start]

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
        assertTrue(
            "分支块切分退化：$marker 是一个以 `{` 结尾的块分支，但同缩进的闭合大括号与" +
                "兄弟分支都没找到，于是只切出了标记行本身（start=end=$start）。" +
                "这会让拒收分支里的 `!contains(...)` 断言恒真。标记行：${bodyLines[start]}",
            end > start,
        )
        return bodyLines.subList(start, end + 1).joinToString("\n")
    }

    /**
     * 护栏自身的反空跑断言（C8）：[branchOf] 必须真的切出拒收分支的**真实内容**。
     *
     * 为什么要单独钉：[a denied failure closeout writes nothing at all] 那条的主体是四条
     * `!denied.contains(forbidden)`（「拒收分支里不许有某个副作用」）。这类断言的天敌不是
     * 写错，而是**切分退化** —— 切分失败时 [branchOf] 只返回标记行本身，于是「不许有」的
     * 那半边必然为真，看起来在守其实什么都没守（本仓库已因此踩过两次）。
     *
     * 正面内容（`Logging.log(` 与那句 `return`）才是防退化的那道闸；行数写死只是结构变更的
     * 复核提示。本文件这个分支是**块分支**（以 `{` 结尾），所以正确切出来必然多于 1 行。
     */
    @Test
    fun `branch split resolves the denied marker to its real content`() {
        val denied = branchOf(bodyOf("failGroupTurn"), DENIED_MARKER)

        assertTrue(
            "拒收分支块里必须含有 `Logging.log(`（可排查性）：切分退化时切出来的只有 " +
                "DENIED_MARKER 那一行，上面四条 `!contains(...)` 就全成了空检查。块内容：\n$denied",
            denied.contains("Logging.log("),
        )
        assertTrue(
            "拒收分支块里必须含有最后那句 `return`（真的早退，而不是算完判据继续往下走）。" +
                "块内容：\n$denied",
            denied.trim().trimEnd('}').trim().endsWith("return"),
        )
        assertEquals(
            "拒收分支块切出的行数变了：它是块分支，正确切出来必然多于 1 行；退化成" +
                "「只有标记行」时这里是 1。正常的结构改动请更新本数字并说明拒收分支改了什么。",
            8,
            denied.lines().size,
        )
    }

    // ------------------------------------------------------------------
    // 1. 守卫必须排在任何不可逆副作用之前
    // ------------------------------------------------------------------

    /**
     * 核心顺序守卫：判定调用与**拒收早退**都必须排在**四个**不可逆动作之前。
     *
     * 两处都要钉：`checkFailureAdmission(` 之前挪掉就没法判；只挪调用而把
     * `if (admission is ...Denied) return` 留在原地更阴险 —— 判据算出来了却没人执行早退，
     * 拒收分支形同虚设，文本上「守卫还在第一个副作用之前」也照样成立。所以顺序以**早退那一行**
     * 为准。
     *
     * 排到任何一个之后就已经晚了：错误节点与运行日志终态是 `saveConversation` / `updateXxx`
     * 级别的写，追不回来；`groupRunsInFlight.remove` 更狠，它抹掉的可能是同会话**新轮**的令牌。
     * 注意这与提交路同源但更严：提交路只要排在 `stampGroupTurn` 之前即可（`advance` 之后还有
     * `Halted` 兜底），失败路**没有** `advance` 这一层，所以四个都得排在守卫之后。
     */
    @Test
    fun `failGroupTurn judges ownership before any side effect`() {
        val body = bodyOf("failGroupTurn")
        val admissionAt = body.indexOf("checkFailureAdmission(")
        val deniedAt = body.indexOf(DENIED_MARKER)
        assertTrue("failGroupTurn 必须调 checkFailureAdmission 判归属与生死", admissionAt >= 0)
        assertTrue("failGroupTurn 必须真的在拒收时早退（不能只算不判）", deniedAt >= 0)
        assertTrue("判定调用必须早于拒收早退", admissionAt < deniedAt)
        listOf(
            "appendGroupMessages(" to "写错误节点（roundId 取现算 plan 的轮次）",
            "persistRoundState(" to "落运行日志终态",
            "dropTieBreakScaffolding(" to "全会话范围回收平票脚手架",
            "groupRunsInFlight.remove(" to "清进程内镜像（可能是新轮的令牌）",
        ).forEach { (call, why) ->
            val at = body.indexOf(call)
            assertTrue("failGroupTurn 应调 $call（$why）", at >= 0)
            assertTrue(
                "判定调用必须排在 $call 之前（$why）：守卫排在写之后就追不回来了",
                admissionAt < at,
            )
            assertTrue(
                "拒收早退必须排在 $call 之前（$why）：判据算出来却没执行早退等于没守",
                deniedAt < at,
            )
        }
    }

    /**
     * 被拒的那一条必须**真的 return**，而且分支体内一个副作用都不许有。
     *
     * `branchOf` 把范围截在同缩进的闭合大括号，所以这些 `!contains` 断言只覆盖拒收分支本身，
     * 不会因为下面的正常路径代码而恒真。
     */
    @Test
    fun `a denied failure closeout writes nothing at all`() {
        val body = bodyOf("failGroupTurn")
        val denied = branchOf(body, DENIED_MARKER)
        assertTrue(
            "拒收分支必须留下可排查的运行日志",
            denied.contains("Logging.log("),
        )
        val deniedStatements = denied.trim().trimEnd('}').trim().lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        assertEquals(
            "拒收分支的最后一句必须是 return，不许往下走（实际：$deniedStatements）",
            "return",
            deniedStatements.last(),
        )
        listOf(
            "appendGroupMessages(",
            "persistRoundState(",
            "dropTieBreakScaffolding(",
            "groupRunsInFlight.remove(",
        ).forEach { forbidden ->
            assertTrue(
                "拒收分支里不许出现 $forbidden：一个副作用都不许做",
                !denied.contains(forbidden),
            )
        }
    }

    // ------------------------------------------------------------------
    // 2. 正常路径不许被守卫吞掉
    // ------------------------------------------------------------------

    /**
     * 放行之后那四个动作必须**一个不少、各一次**（别把守卫写成无条件早退）。
     *
     * 正常群聊轮次里某个角色真的生成失败 / 超时时，`failGroupTurn` 必须照常执行：
     * 写错误节点、落 FAILED/TIMEOUT、回收平票脚手架、清进程内镜像。
     * 少任何一条都是回归 —— 尤其 `dropTieBreakScaffolding`：议长裁决那一轮失败时不回收，
     * 那条 SYSTEM 指令会永久留在会话里（这一条另有
     * `GroupTieBreakScaffoldingDropSourceGuardTest` 独立守，这里只钉「没被守卫吞掉」）。
     */
    @Test
    fun `an admitted failure closeout still runs every side effect exactly once`() {
        val body = bodyOf("failGroupTurn")
        val guardAt = body.indexOf(DENIED_MARKER)
        assertTrue("必须有拒收守卫", guardAt >= 0)
        listOf(
            "appendGroupMessages(",
            "persistRoundState(",
            "dropTieBreakScaffolding(",
            "groupRunsInFlight.remove(",
        ).forEach { call ->
            assertEquals(
                "放行路径上 $call 应恰好出现一次",
                1,
                body.split(call).size - 1,
            )
            assertTrue(
                "放行路径上的 $call 必须排在守卫之后",
                body.indexOf(call) > guardAt,
            )
        }
    }

    // ------------------------------------------------------------------
    // 3. run token 必须一路透传到三个调用点
    // ------------------------------------------------------------------

    /**
     * 守卫要判身份就必须拿到令牌，所以 [failGroupTurn] 的签名里必须有它。
     *
     * 没有这一条的话，执行层就只能回退到「现算 plan 的轮次 + 库里那一行」，
     * 那正是残留 job 与新轮混淆的根源。
     */
    @Test
    fun `failGroupTurn takes the run token as a parameter`() {
        val body = bodyOf("failGroupTurn")
        assertTrue(
            "failGroupTurn 必须收 runToken 参数，否则无从判归属",
            body.contains("runToken: String?"),
        )
    }

    /**
     * 三个调用点都必须把**发起模型调用之前捕获**的令牌透传进来。
     *
     * - `handleMessageComplete` 的两条（超时 / 非取消异常）：传 `groupRunToken`，
     *   它在 [takeGroupTurn] 抢占之后、发起模型调用之前捕获；
     * - `commitGroupTurn` 里「本轮没有产出内容」那一条：传它自己的 `runToken` 形参
     *   （同一个来源）。
     *
     * 反过来写成 `groupRunsInFlight[conversationId]?.runToken` 会静默失效：判死路径
     * （`cancelActiveGroupRun` / `abandonDanglingGroupRuns`）都把进程内镜像一起清掉了，
     * 残留 job 事后反查到的要么是 null（血缘不明 → 等于把正常路径也打死），
     * 要么是**新轮**的令牌（恰好把这次失败放行，正是要防的那个 bug）。
     * 这两种都是编译通过、行为错误，只能在源码上钉。
     */
    @Test
    fun `every failGroupTurn call site passes the captured run token`() {
        val completion = bodyOf("handleMessageComplete")
        val callLines = completion.split("\n").filter { it.contains("failGroupTurn(") }
        assertEquals(
            "handleMessageComplete 里应有两条 failGroupTurn 调用（超时 / 非取消异常）",
            2,
            callLines.size,
        )
        callLines.forEach { call ->
            assertTrue(
                "onFailure 的调用必须透传 groupRunToken（实际：$call）",
                call.contains("groupRunToken"),
            )
            assertTrue(
                "不许事后从 groupRunsInFlight 反查令牌：判死路径已把镜像清掉",
                !call.contains("groupRunsInFlight"),
            )
        }

        val commit = bodyOf("commitGroupTurn")
        val emptyOutputAt = commit.indexOf("failGroupTurn(")
        assertTrue("commitGroupTurn 里有「本轮没有产出内容」那一条 failGroupTurn", emptyOutputAt >= 0)
        val emptyOutputCall = commit.substring(emptyOutputAt, commit.indexOf('\n', emptyOutputAt))
        assertTrue(
            "「本轮没有产出内容」必须透传 runToken（实际：$emptyOutputCall）",
            emptyOutputCall.contains("runToken"),
        )
        assertTrue(
            "不许从 groupRunsInFlight 反查令牌",
            !emptyOutputCall.contains("groupRunsInFlight"),
        )
    }

    /**
     * `groupRunToken` 仍必须在 [takeGroupTurn] 抢占结果那一行捕获（与提交路同源）。
     *
     * 这一条上一轮已经为提交路钉过（`GroupStaleJobCommitSourceGuardTest`），这里复述一遍是因为
     * 失败路现在**也**依赖它：捕获点一旦挪走或改成事后反查，上面那条透传断言虽然还在
     * （文本上仍写着 `groupRunToken`），语义上却已经错了。
     */
    @Test
    fun `the failure path token is still captured from the claim result`() {
        val body = bodyOf("handleMessageComplete")
        assertTrue(
            "groupRunToken 必须与 groupStep 一样在抢占结果那一行捕获",
            body.contains("groupRunToken = (groupEntry as? GroupTurnEntry.Speak)?.runToken"),
        )
    }
}