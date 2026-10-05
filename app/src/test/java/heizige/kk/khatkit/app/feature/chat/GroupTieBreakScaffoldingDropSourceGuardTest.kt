package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 平票裁决脚手架（`role = SYSTEM` + `turn_kind = chair` 的指令）必须在**每一条**轮次终态路径上被回收。
 *
 * ⚠️ **这是源码文本护栏，不是行为断言。** `failGroupTurn` / `cancelActiveGroupRun` /
 * `abandonDanglingGroupRuns` / `completeGroupRound` 都是 `ChatManager` 的 private suspend 成员，
 * 要 `Application` + `AppScope` + Room DAO，仓库 testImplementation 只有 junit，跑不起来。
 * 纯函数那一半（`GroupTurnCoordinator.withoutTieBreakInstruction`）已在
 * `GroupTurnCoordinatorTest` 里覆盖；这里补的是**调用点**这一半。
 *
 * 为什么抽不出纯函数判据：这几条路径的「该不该回收」全都不是纯函数问题 ——
 * [cancelActiveGroupRun] 的答案取决于 `groupRunsInFlight` 里有没有 runToken、
 * `groupRunDAO.getByRunToken` 查得到的 status 是不是终态（全是非纯的 DAO 读），
 * [abandonDanglingGroupRuns] 的答案取决于 `listByConversationAndStatus` 返回几行 RUNNING；
 * 两者最终都是无条件的「这条路径把轮次判死了 → 回收」，抽成一个返回常量的纯函数
 * 只会凭空多一层间接、断言不出新东西。这里真正要守的不变量
 * 「**这些函数体内必须有那处调用**」本身就是源码属性，只能在源码上断言。
 *
 * 漏掉调用点为什么严重：那条指令是 `role = SYSTEM` + `isSynthetic = true`，
 * `GroupChat.visibleMessages` 的第一分支对 SYSTEM / 合成消息一律 `true`
 * （且排在 `index >= roundStart` 判断之前，对任何 viewer、任何轮次都放行），
 * 所以只要它还在 `currentMessages` 里，**之后每一轮的所有角色**都会读到
 * 「你是本群议长……本轮投票出现平票……」这段与当前轮次无关的 SYSTEM 指令。
 */
class GroupTieBreakScaffoldingDropSourceGuardTest {

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

    private inline fun List<String>.indexOfFirstFrom(from: Int, predicate: (String) -> Boolean): Int {
        for (index in from until size) {
            if (predicate(this[index])) return index
        }
        return -1
    }

    /**
     * P1：议长裁决那一轮生成失败 / 超时 / 没产出时走 [failGroupTurn]，它把轮次写成
     * FAILED / TIMEOUT 并清掉进程内镜像，但**原来不回收**平票脚手架 ——
     * 于是指令永久留在会话里，每一轮每个角色都看到。
     */
    @Test
    fun `failGroupTurn drops the tie break scaffolding`() {
        val body = bodyOf("failGroupTurn")
        assertTrue(
            "failGroupTurn 必须回收平票裁决脚手架，否则议长裁决失败后那条 SYSTEM 指令永久留在会话里",
            body.contains("dropTieBreakScaffolding("),
        )
    }

    /**
     * 回收要排在写完本轮收尾之后：与 [completeGroupRound] 的既有顺序一致
     * （先 append 结论 / 落运行日志，最后统一回收脚手架），别在 append 之前回收。
     */
    @Test
    fun `failGroupTurn drops scaffolding after persisting the terminal round`() {
        val body = bodyOf("failGroupTurn")
        val dropAt = body.indexOf("dropTieBreakScaffolding(")
        val appendAt = body.indexOf("appendGroupMessages(")
        val persistAt = body.indexOf("persistRoundState(")
        assertTrue("failGroupTurn 应先 append 错误节点", appendAt >= 0)
        assertTrue("failGroupTurn 应先落运行日志终态", persistAt >= 0)
        assertTrue(
            "回收平票脚手架必须排在 appendGroupMessages 之后（与 completeGroupRound 同序）",
            dropAt > appendAt,
        )
        assertTrue(
            "回收平票脚手架必须排在 persistRoundState 之后（与 completeGroupRound 同序）",
            dropAt > persistAt,
        )
    }

    /**
     * P1（同批第二条）：用户中途取消议长裁决那一轮时走 [cancelActiveGroupRun]。
     * 它把轮次写成 CANCELLED（终态，`persistRoundState` 内部 `isTerminal` → `finish` 写 `ended_at`）
     * 并清掉进程内镜像，之后**再没有代码路径会为这一轮调 `completeGroupRound`**
     * （它全仓只有一个调用点，在 `onSuccess` 里，而 `stopGeneration` 是先 `join` 完所有 job
     * 才走到这里），所以原本也必须回收 —— 否则指令永久留在会话里，每一轮每个角色都看到。
     */
    @Test
    fun `cancelActiveGroupRun drops the tie break scaffolding`() {
        val body = bodyOf("cancelActiveGroupRun")
        assertTrue(
            "cancelActiveGroupRun 必须回收平票裁决脚手架，否则议长裁决被用户掐掉后" +
                "那条 SYSTEM 指令永久留在会话里",
            body.contains("dropTieBreakScaffolding("),
        )
    }

    /**
     * P1（同批第三条）：发新消息时上一轮还挂 RUNNING 被判死，走 [abandonDanglingGroupRuns]。
     * 它在存新 USER 消息**之前**调用，判死之后 `roundPlanFor` 派生出的 `roundId` 已是新轮，
     * 被判死那一轮再也回不来 → 同样没有路径会为它调 `completeGroupRound`，脚手架必然孤立。
     */
    @Test
    fun `abandonDanglingGroupRuns drops the tie break scaffolding`() {
        val body = bodyOf("abandonDanglingGroupRuns")
        assertTrue(
            "abandonDanglingGroupRuns 必须回收平票裁决脚手架，否则上一轮被判死时" +
                "那条 SYSTEM 指令永久留在会话里",
            body.contains("dropTieBreakScaffolding("),
        )
    }

    /**
     * 顺序护栏：两处都要**先落运行日志终态、再回收**，与 [completeGroupRound] / [failGroupTurn] 同序。
     */
    @Test
    fun `cancel and abandon drop scaffolding after persisting the terminal round`() {
        listOf("cancelActiveGroupRun", "abandonDanglingGroupRuns").forEach { name ->
            val body = bodyOf(name)
            val dropAt = body.indexOf("dropTieBreakScaffolding(")
            val persistAt = body.indexOf("persistRoundState(")
            assertTrue("$name 应先落运行日志终态", persistAt >= 0)
            assertTrue(
                "$name 必须先落运行日志终态、再回收平票脚手架（与 completeGroupRound / failGroupTurn 同序）",
                dropAt > persistAt,
            )
        }
    }

    /**
     * 条件护栏（防「提前销毁」回归）：[cancelActiveGroupRun] 前面有一串守卫提前 `return`，
     * 回收必须排在**最后一个** `return` 之后 —— 排到守卫之前就意味着「压根没有活跃轮次 /
     * 轮次已终态」时也会去清扫，那是提前销毁而不是回收。
     * [abandonDanglingGroupRuns] 没有守卫，两条路径都要求**恰好回收一次**（不多不少）。
     */
    @Test
    fun `cancel and abandon drop scaffolding unconditionally exactly once`() {
        val cancel = bodyOf("cancelActiveGroupRun")
        val dropAt = cancel.indexOf("dropTieBreakScaffolding(")
        assertTrue(
            "cancelActiveGroupRun 的回收必须排在全部守卫之后，别在守卫之前清扫",
            dropAt > cancel.lastIndexOf("return"),
        )
        assertEquals(
            "cancelActiveGroupRun 应恰好回收一次平票脚手架",
            1,
            cancel.split("dropTieBreakScaffolding(").size - 1,
        )

        val abandon = bodyOf("abandonDanglingGroupRuns")
        assertEquals(
            "abandonDanglingGroupRuns 应恰好回收一次平票脚手架",
            1,
            abandon.split("dropTieBreakScaffolding(").size - 1,
        )
    }

    /**
     * 回归对照：[completeGroupRound] 是本来就做对了的那个 —— 两条终态分支
     * （Decided / Undecided）各回收一次，NeedsChairTieBreak 分支**故意不回收**
     * （议长还没裁决，指令还得留着）。
     */
    @Test
    fun `completeGroupRound drops scaffolding on both terminal branches only`() {
        val body = bodyOf("completeGroupRound")
        val decided = branchOf(body, "VoteResolution.Decided")
        val needsChair = branchOf(body, "VoteResolution.NeedsChairTieBreak")
        val undecided = branchOf(body, "VoteResolution.Undecided")

        assertTrue("Decided 分支应回收平票脚手架", decided.contains("dropTieBreakScaffolding("))
        assertTrue("Undecided 分支应回收平票脚手架", undecided.contains("dropTieBreakScaffolding("))
        assertEquals(
            "NeedsChairTieBreak 分支必须保留指令：议长还没裁决，提前回收等于让它无据可裁",
            0,
            needsChair.split("dropTieBreakScaffolding(").size - 1,
        )
        assertEquals(
            "completeGroupRound 只应在 Decided / Undecided 两条终态分支各回收一次",
            2,
            body.split("dropTieBreakScaffolding(").size - 1,
        )
    }

    /** 取 `when` 里某个 `is ... -> {` 分支的整块源码（分支缩进 12，闭合大括号也正好 12 空格）。 */
    private fun branchOf(body: String, marker: String): String {
        val bodyLines = body.split("\n")
        val start = bodyLines.indexOfFirst { it.trimStart().startsWith("is ") && it.contains(marker) }
        assertTrue("分支没找到：$marker", start >= 0)
        val end = bodyLines.indexOfFirstFrom(start + 1) { it == "            }" }
        assertTrue("分支闭合大括号没找到：$marker", end > start)
        return bodyLines.subList(start, end + 1).joinToString("\n")
    }
}