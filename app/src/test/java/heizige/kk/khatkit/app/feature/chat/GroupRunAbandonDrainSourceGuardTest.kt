package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ② `abandonDanglingGroupRuns` 的**判死必须清空**护栏。
 *
 * ## 这条护栏防的是什么
 *
 * 契约说「**任何群聊轮次都不会永久悬挂**」。`abandonDanglingGroupRuns` 是用户发新消息时
 * 把上一轮判死的那条路径，它原来直接
 * `listByConversationAndStatus(..., status = RUNNING, limit = 8)` 然后判死这一页 ——
 * 于是**第 9 条起永远停在 `RUNNING` 占位**，与契约正面冲突。
 *
 * 那 8 被当成了「一次最多判死 8 条」，而 DAO 那一行的语义是
 * `ORDER BY started_at ASC LIMIT :limit`（一页），本来就不是「总量上限」。
 *
 * ⚠️ 与 `handleMessageComplete` 里 `searchMemories(limit = 8)` 的那个 8 **无关**，
 * 两者只是碰巧同值 —— 所以这里钉的是「limit 必须是具名分页常量 + 必须有循环」，
 * 而不是钉某个数字。
 *
 * ## 为什么不钉「不许有上限」
 *
 * 循环必须有硬上限才保证收敛：同一会话在本函数执行期间又抢占成功一轮时（残留旧 job 走到
 * `takeGroupTurn`），`RUNNING` 行数不单调减少，没有上限就会自旋。所以这里钉的是
 * **「循环存在」+「上限是个具名常量 +「KDoc 写明残留条件」**，而不是「上限不存在」。
 */
class GroupRunAbandonDrainSourceGuardTest {

    /**
     * 不变量本体：判死必须**循环读到清空**，而不是只判死一页。
     *
     * 钉「循环」这个形状本身（`while` / `do..while`），因为它是「读到空为止」的唯一证据；
     * 只钉「有 limit 常量」不够 —— 只把 `limit = 8` 换成 `limit = ABANDON_BATCH_SIZE`
     * 就骗过去了，行为一字未改。
     */
    @Test
    fun abandonDrainsUntilEmpty() {
        val body = bodyOf("abandonDanglingGroupRuns")
        assertTrue(
            "abandonDanglingGroupRuns 必须循环判死直到读空。只判死一页时，第 9 条起永远停在 " +
                "RUNNING 占位，与契约「任何群聊轮次都不会永久悬挂」冲突。实际函数体：\n$body",
            Regex("""\b(while|do)\b""").containsMatchIn(body),
        )
        assertTrue(
            "abandonDanglingGroupRuns 的循环里必须重新读一页（`= danglingGroupRunBatch(` 或 " +
                "再次调 DAO），否则读完第一批就退出，等于只判死一页。实际函数体：\n$body",
            Regex("""=\s*danglingGroupRunBatch\s*\(""").containsMatchIn(body) ||
                Regex("""listByConversationAndStatus\s*\(""").findAll(body).count() >= 2,
        )
    }

    /**
     * 反向对照：循环必须**有退出条件**，不许写成无界 `while (true)`。
     *
     * 没有上限时，「循环读到空」在并发抢占的场景下会自旋 —— 那比原来的 `limit = 8`
     * 更糟（后者只是留下占位，前者会卡死发消息的协程）。
     */
    @Test
    fun theDrainLoopIsBounded() {
        val body = bodyOf("abandonDanglingGroupRuns")
        assertTrue(
            "abandonDanglingGroupRuns 的循环必须同时有「批数上限」和「读空即退出」两个出口，" +
                "否则并发抢占一轮时行数不减少、无界循环会自旋。实际函数体：\n$body",
            body.contains("ABANDON_MAX_BATCHES") && body.contains("batch.isNotEmpty()"),
        )
        assertTrue(
            "abandonDanglingGroupRuns 不许写无界循环 `while (true)`。实际函数体：\n$body",
            !Regex("""while\s*\(\s*true\s*\)""").containsMatchIn(body),
        )
    }

    /**
     * 页大小必须用具名常量，不许回退成内联字面量。
     *
     * 内联 `limit = 8` 正是原始缺陷的形状 —— 一个看起来像「总量」的裸数字，实际是页大小。
     * 钉住常量名之后，「这个数字到底是上限还是页大小」在代码里就有名字可问了。
     */
    @Test
    fun thePageSizeIsANamedConstantNotAnInlineLiteral() {
        val body = bodyOf("danglingGroupRunBatch")
        assertTrue(
            "danglingGroupRunBatch 的 limit 必须用具名常量（当前是 ABANDON_BATCH_SIZE）。" +
                "内联裸数字会被读成「总量上限」，正是原始缺陷的形状。实际函数体：\n$body",
            body.contains("limit = ABANDON_BATCH_SIZE"),
        )
        assertTrue(
            "abandonDanglingGroupRuns 内部不许再出现内联的 `limit = <数字>`。实际函数体：\n$body",
            !Regex("""limit\s*=\s*\d""").containsMatchIn(body),
        )
    }

    /**
     * 循环里每判死一行之前必须**重读一次并跳过已终态的行**。
     *
     * 读出来的是一页快照，写回去时中间可能已被别的路径收尾；而
     * `GroupTurnCoordinator.terminal()` **不判终态**（它无条件覆盖 `status`），
     * 盲目写下去会把别人的终态（COMPLETED / TIMEOUT / FAILED）抹成 `CANCELLED`，
     * 账目再次错位。这条把循环带来的新窗口钉住。
     */
    @Test
    fun theDrainRereadsEachRowBeforeCancelling() {
        val body = bodyOf("abandonDanglingGroupRuns")
        assertTrue(
            "分批循环里判死前必须重读该行（`findByRound(`），否则会把期间已被收尾的行" +
                "的终态覆盖成 CANCELLED —— terminal() 不判终态。实际函数体：\n$body",
            body.contains("findByRound("),
        )
        assertTrue(
            "重读之后必须跳过已终态的行（`isTerminal(`）。实际函数体：\n$body",
            body.contains("GroupRunEntity.isTerminal("),
        )
    }

    /**
     * 收敛上限必须是个**具名常量**且带 KDoc 说明残留条件。
     *
     * 只钉「有一个上限」不够 —— 那个上限就是契约与实现之间唯一的缝隙，必须让人能读到
     * 它在什么条件下会被撞到、以及撞到之后会怎样。
     */
    @Test
    fun theConvergenceCapIsNamedAndDocumented() {
        val source = File(repoRoot(), CHAT_MANAGER_FILE).readText()
        val decl = Regex(
            """private const val ABANDON_MAX_BATCHES = \d+""",
        ).find(source)
        assertTrue(
            "$CHAT_MANAGER_FILE 必须有 `private const val ABANDON_MAX_BATCHES = <数字>`。" +
                "循环的收敛上限必须具名，否则读代码的人无法判断「这个数字会不会被撞到」。",
            decl != null,
        )
        val declLine = decl!!.range.first
        val kdoc = source.substring(0, declLine)
            .takeLast(KDOC_WINDOW)
        assertTrue(
            "ABANDON_MAX_BATCHES 的 KDoc 必须写明它存在的理由（保证收敛）与撞上限后的行为。" +
                "这个数字是契约「任何群聊轮次都不会永久悬挂」与实现之间唯一的缝隙，" +
                "不留文档就等于没人知道它会怎么被撞到。实际 KDoc：\n$kdoc",
            kdoc.contains("收敛") && kdoc.contains("上限"),
        )
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private val lines by lazy {
        File(repoRoot(), CHAT_MANAGER_FILE).readText().split("\n")
    }

    /** 取某个 private 成员函数的整块函数体（成员函数缩进 4，闭合大括号也正好 4 空格）。 */
    private fun bodyOf(name: String): String {
        val start = lines.indexOfFirst {
            it.startsWith("    private suspend fun $name(") || it.startsWith("    private fun $name(")
        }
        assertTrue("函数没找到：$name", start >= 0)
        val end = (start + 1 until lines.size).firstOrNull { lines[it] == "    }" }
        assertTrue("函数闭合大括号没找到：$name", end != null)
        return lines.subList(start, end!! + 1).joinToString("\n")
    }

    companion object {
        private const val CHAT_MANAGER_FILE =
            "app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt"

        /** 取常量声明之前这么多字符当 KDoc 窗口。 */
        private const val KDOC_WINDOW = 700

        private fun repoRoot(): File {
            var dir = File("").absoluteFile
            while (true) {
                if (File(dir, "settings.gradle.kts").isFile) return dir
                dir = dir.parentFile ?: error("找不到仓库根（向上没有 settings.gradle.kts）")
            }
        }
    }
}
