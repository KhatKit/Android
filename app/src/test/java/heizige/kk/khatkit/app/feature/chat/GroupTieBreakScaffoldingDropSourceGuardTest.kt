package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 平票裁决脚手架（`turn_kind = tie_break` 的 SYSTEM 指令）必须在**每一条**轮次终态路径上被回收。
 *
 * ⚠️ **这是源码文本护栏，不是行为断言。** `failGroupTurn` / `completeGroupRound` 是 `ChatManager`
 * 的 private suspend 成员，要 `Application` + `AppScope` + Room DAO，仓库 testImplementation 只有
 * junit，跑不起来。纯函数那一半（`GroupTurnCoordinator.withoutTieBreakInstruction`）已在
 * `GroupTurnCoordinatorTest` 里覆盖；这里补的是**调用点**这一半。
 *
 * 漏掉调用点为什么严重：那条指令是 `role = SYSTEM` + `isSynthetic = true`，
 * `GroupChat.visibleMessages` 的第一分支对 SYSTEM / 合成消息一律 `true`，
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