package heizige.kk.khatkit.app.core.ui.components.message

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ④ `ChatMessage.groupChat` 参数 KDoc 的 **fork 禁因** 护栏。
 *
 * ## 这条护栏防的是什么
 *
 * `ChatMessage(groupChat = …)` 在群聊会话里关掉「重新生成 / 删除 / 创建分支」三个动作。
 * 前两个的理由是站得住的：`regenerateAtMessage` / `deleteMessage` 不群聊感知，顶掉或删掉
 * 一条角色发言后，`group_runs.committed_role_ids` 仍声称该角色已提交，续跑就会跳过
 * 没人再发言的角色 —— 账面与实际错位。
 *
 * **fork 的理由不一样，而它一度被写成了同一个理由。** 按实读：`createForkConversation`
 * 造出新 `Uuid` 之后只 `saveConversation(新 id)`，对源会话**只读**；而且它既不复制
 * `group_config` 也不复制 `type`，产物是一条**单聊**会话。所以 fork：
 *
 * - **破坏不了**群运行日志的账面（源会话一个字都没回写）；
 * - 真实问题是**静默换会话类型** —— 调用点只拿到新会话 id 就直接 `navigateToChatPage`，
 *   界面没有任何提示说明「这不是群聊分支」。
 *
 * 于是那段 KDoc 在**教用户一个不存在的风险**：照它去理解 fork，读者会去推演
 * 「fork 会让 `committed_role_ids` 错位」，而这条因果链在代码里根本不存在。HTTP 侧那道
 * 门禁（`GroupConversationOperationGuard`）也已经把 fork 的理由改成了「静默换会话类型」，
 * 两处口径必须一致。
 *
 * 这条护栏钉的是**禁因的归属**，不是逐字文案 —— 措辞可以改，但「fork 的理由不许再被
 * 说成账目错位」不许回退。
 */
class GroupForkDisableReasonSourceGuardTest {

    /**
     * KDoc 的 fork 段落必须**与前两个动作的禁因区分开**，且不得把账目错位算到 fork 头上。
     *
     * 判据落在「fork 那一段」而不是整个 KDoc：前两个动作的段落里**应当**出现
     * `committed_role_ids` / 错位这类词（那是对的），所以只有把范围切到 fork 段落，
     * 这条断言才有意义。
     */
    @Test
    fun forkIsNotBlamedForLedgerMisalignment() {
        val fork = forkParagraph()
        assertTrue(
            "$CHAT_MESSAGE_FILE 的 KDoc 里 fork 段落必须存在，才能检查它的禁因。" +
                "找不到以「创建分支」/「fork」起头的说明段。",
            fork.isNotBlank(),
        )
        assertTrue(
            "fork 段落必须明确写出「不写源会话 / 不碰群运行日志，破坏不了账目」这一事实，" +
                "否则读者仍会把前两个动作的账目错位理由顺延到 fork 上。" +
                "实际段落：\n$fork",
            Regex("""(破坏不了|账\s*目|committed_role_ids|不写源会话|只读)""").containsMatchIn(fork),
        )
        assertTrue(
            "fork 段落必须写出真实禁因：静默换会话类型（既不复制 group_config 也不复制 type，" +
                "产物是单聊）。缺了这条，禁掉 fork 就只剩一个讲不通的理由。" +
                "实际段落：\n$fork",
            Regex("""(静默|会话类型|单聊|group_config)""").containsMatchIn(fork),
        )
    }

    /**
     * 前两个动作的账目错位理由**必须留着** —— 别把这条守成「不许提账目」。
     *
     * `regenerateAtMessage` / `deleteMessage` 的禁因是成立的，KDoc 要继续解释清楚，
     * 否则下一个人会去「顺手放开」它们。分成两个测试就是为了让「fork 不算账目错位」
     * 与「那三个动作仍然算账目错位」可以各自独立地红。
     */
    @Test
    fun regenerateAndDeleteKeepTheirLedgerReason() {
        val kdoc = kdocOfGroupChat()
        val start = forkParagraphStart(kdoc)
        assertTrue(
            "$CHAT_MESSAGE_FILE 的 KDoc 里找不到讲 fork 的那段（" +
                "`forkConversationAtMessage` 或末尾那次「创建分支」）。",
            start > 0,
        )
        val beforeFork = kdoc.substring(0, start)
        assertTrue(
            "$CHAT_MESSAGE_FILE 的 KDoc 必须继续解释「重新生成 / 删除」的账目错位理由" +
                "（`regenerateAtMessage` / `deleteMessage` 不群聊感知，会让 " +
                "committed_role_ids 与实际消息错位，续跑跳过没人发言的角色）。" +
                "实际前半段：\n$beforeFork",
            beforeFork.contains("regenerateAtMessage") && beforeFork.contains("deleteMessage"),
        )
        assertTrue(
            "「重新生成 / 删除」的禁因段必须提到 committed_role_ids 层面的错位。实际前半段：\n$beforeFork",
            Regex("""(committed_role_ids|错位)""").containsMatchIn(beforeFork),
        )
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** `groupChat` 参数的整段 KDoc（从它上方的 `/**` 到 `*/`）。 */
    private fun kdocOfGroupChat(): String {
        val lines = File(repoRoot(), CHAT_MESSAGE_FILE).readText().split("\n")
        val end = lines.indexOfFirst { it.trimStart().startsWith("groupChat: Boolean") }
        assertTrue("$CHAT_MESSAGE_FILE 里找不到 `groupChat: Boolean =` 参数声明", end >= 0)
        val start = (0 until end).lastOrNull { lines[it].trim() == "/**" }
        assertTrue("找不到 groupChat 参数的 KDoc 起始", start != null)
        return lines.subList(start!!, end).joinToString("\n")
    }

    /**
     * 讲 fork 的那一段在整份 KDoc 里的**起始下标**（-1 = 没找到）。
     *
     * 切这一段而不是整份 KDoc，是因为「账目错位」这个词在同一份 KDoc 的**前半段是
     * 正确的**（那是在讲重新生成 / 删除）。只有把范围切到 fork 段，
     * 「不许把账目错位算到 fork 头上」这条断言才不会被正确的那半段顶成假阳性。
     *
     * ⚠️ 切点不能用「创建分支」的首现位置：摘要行「true 时关掉『重新生成 / 删除 /
     * 创建分支』」里就有它，用首现会把正确的前半段一起切掉。这里认的是
     * `forkConversationAtMessage`（真正展开 fork 理由那段），退化时取**末次**出现的
     * 「创建分支」。两者都找不到就返回 -1，让断言红掉，而不是静默通过。
     */
    private fun forkParagraphStart(kdoc: String): Int {
        val symbol = kdoc.indexOf("forkConversationAtMessage")
        if (symbol >= 0) return symbol
        return kdoc.lastIndexOf("创建分支")
    }

    /** 讲 fork 的那一段（从切点到 KDoc 结束）。 */
    private fun forkParagraph(): String {
        val kdoc = kdocOfGroupChat()
        val start = forkParagraphStart(kdoc)
        return if (start < 0) "" else kdoc.substring(start)
    }

    companion object {
        private const val CHAT_MESSAGE_FILE =
            "app/src/main/java/heizige/kk/khatkit/app/core/ui/components/message/ChatMessage.kt"

        private fun repoRoot(): File {
            var dir = File("").absoluteFile
            while (true) {
                if (File(dir, "settings.gradle.kts").isFile) return dir
                dir = dir.parentFile ?: error("找不到仓库根（向上没有 settings.gradle.kts）")
            }
        }
    }
}
