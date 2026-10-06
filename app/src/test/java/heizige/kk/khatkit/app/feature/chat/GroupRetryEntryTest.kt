package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.model.GroupChat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * C1 —— 群聊「失败续跑」入口判定 [GroupRetryEntry.canResume] 的 JVM 护栏。
 *
 * ## 为什么是纯函数测试
 *
 * 契约 `docs/beyond-operit-client-changes.md:204`（同一 `round_id` 重试跳过已提交 turn）与
 * `:227-228`（run token 持久化后才可执行）在代码里由 `ChatManager.regenerateAtMessage` +
 * `pendingSpeakers(plan, committedRoleIds)` 实现。UI 侧要做的只是**把入口收窄到失败节点的
 * 正确形状**：一旦放开到任意消息，重新生成会把已提交角色的发言顶成候选分支，而
 * `committed_role_ids` 不会跟着修订 ⇒ 续跑永久跳过那个角色（详见 [GroupRetryEntry] KDoc）。
 *
 * 因此判定必须可穷举：本测试把「组群/单聊、是否最后一个节点、角色、turn_kind、role_id」
 * 五维形状逐条钉死，任一维松掉都红。
 *
 * ## 覆盖不到的
 *
 * 这是**判定层**的测试，不是「点了真的有反应」的运行时证据。那需要 `group_runs` 真库 +
 * mock 网关，见 `C1GroupRetryResumeDeviceTest`（本轮不跑真机）。
 */
class GroupRetryEntryTest {

    // ------------------------------------------------------------------
    // 1. 真失败节点 = 可续跑
    // ------------------------------------------------------------------

    @Test
    fun `group last error node of a real role is resumable`() {
        assertTrue(
            "群聊里失败角色的错误节点（最后一条）必须给出续跑入口：" +
                "该角色不在 committedRoleIds 里，pendingSpeakers 会补上它并跳过已提交角色。",
            GroupRetryEntry.canResume(
                isGroup = true,
                isLastMessage = true,
                message = assistant(roleId = "b", turnKind = GroupChat.TURN_ERROR),
            ),
        )
    }

    // ------------------------------------------------------------------
    // 2. 任一门径松掉都不可续跑
    // ------------------------------------------------------------------

    @Test
    fun `single chat is never a group resume target`() {
        assertFalse(
            "单聊不该走群聊续跑入口（单聊的重新生成由既有 onRegenerate 路径负责）。",
            GroupRetryEntry.canResume(
                isGroup = false,
                isLastMessage = true,
                message = assistant(roleId = "b", turnKind = GroupChat.TURN_ERROR),
            ),
        )
    }

    @Test
    fun `an error node that is no longer last is not resumable`() {
        // 典型反例：失败后用户又发了新 USER 消息，旧错误节点不再是最后一个节点。
        // 此时再拿它触发会把产出记到「最后一条 USER」派生出的新轮，正是判死残留旧 job 的错位。
        assertFalse(
            "错误节点已不是最后一个节点时不许给入口（否则会用旧节点触发到新轮）。",
            GroupRetryEntry.canResume(
                isGroup = true,
                isLastMessage = false,
                message = assistant(roleId = "b", turnKind = GroupChat.TURN_ERROR),
            ),
        )
    }

    @Test
    fun `vote failure summary is not resumable`() {
        // 投票未决的失败摘要也是 TURN_ERROR + 最后一条，但它是 SUMMARY_ID 合成节点，
        // 没有待发言角色，续跑只会是 no-op。
        assertFalse(
            "SUMMARY_ID 的投票失败摘要不是可续跑的失败角色节点。",
            GroupRetryEntry.canResume(
                isGroup = true,
                isLastMessage = true,
                message = assistant(roleId = GroupChat.SUMMARY_ID, turnKind = GroupChat.TURN_ERROR),
            ),
        )
    }

    @Test
    fun `a normal committed speaker message is not resumable`() {
        assertFalse(
            "已提交角色的正常发言（turn_kind = speaker）不许给续跑入口：" +
                "重新生成会把它顶掉并造成账面错位。",
            GroupRetryEntry.canResume(
                isGroup = true,
                isLastMessage = true,
                message = assistant(roleId = "a", turnKind = GroupChat.TURN_SPEAKER),
            ),
        )
    }

    @Test
    fun `a user message is not resumable`() {
        assertFalse(
            "用户消息不走群聊续跑入口。",
            GroupRetryEntry.canResume(
                isGroup = true,
                isLastMessage = true,
                message = UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text("你好")),
                    turnKind = GroupChat.TURN_USER,
                ),
            ),
        )
    }

    @Test
    fun `a non assistant error message is not resumable`() {
        assertFalse(
            "非 ASSISTANT 的消息即使带 TURN_ERROR 也不是群聊续跑入口。",
            GroupRetryEntry.canResume(
                isGroup = true,
                isLastMessage = true,
                message = UIMessage(
                    role = MessageRole.SYSTEM,
                    parts = listOf(UIMessagePart.Text("系统错误")),
                    turnKind = GroupChat.TURN_ERROR,
                ),
            ),
        )
    }

    // ------------------------------------------------------------------
    // 3. 接线护栏：入口挂在 onGroupResume 上，且群聊「重新生成」保持关闭
    // ------------------------------------------------------------------

    /**
     * 三处接线必须同时存在，否则判定对了也点不到（或点到了别的动作）。
     *
     * ⚠️ 这是**源码文本**护栏，不是运行时证据；它只防「把判定删了/接错参数」这类漂移。
     */
    @Test
    fun `the resume entry is wired through a dedicated onGroupResume action`() {
        val list = File(repoRoot(), CHAT_LIST).readText()
        val actions = File(repoRoot(), CHAT_MESSAGE_ACTIONS).readText()
        val message = File(repoRoot(), CHAT_MESSAGE).readText()

        assertTrue(
            "$CHAT_LIST 必须用 GroupRetryEntry.canResume 计算 onGroupResume，否则入口条件会漂。",
            list.contains("GroupRetryEntry.canResume(") && list.contains("onGroupResume ="),
        )
        assertTrue(
            "$CHAT_MESSAGE_ACTIONS 的续跑按钮必须由 onGroupResume != null 门控，",
            actions.contains("if (onGroupResume != null)"),
        )
        assertTrue(
            "$CHAT_MESSAGE 必须声明 onGroupResume 参数并透传给 ChatMessageActionButtons。",
            message.contains("onGroupResume: (() -> Unit)? = null") &&
                message.contains("onGroupResume = onGroupResume"),
        )

        // 群聊「重新生成」必须保持整条关闭——续跑是另一条通道，不是把这条放开。
        assertTrue(
            "$CHAT_LIST 的群聊 onRegenerate 必须保持 `if (!groupChat)` 关闭。",
            list.contains("if (!groupChat) onRegenerate(currentMessage)"),
        )
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun assistant(roleId: String?, turnKind: String?) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text("内容")),
        roleId = roleId,
        roundId = "round-trigger",
        turnKind = turnKind,
    )

    private companion object {
        private const val CHAT_LIST =
            "app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatList.kt"
        private const val CHAT_MESSAGE =
            "app/src/main/java/heizige/kk/khatkit/app/core/ui/components/message/ChatMessage.kt"
        private const val CHAT_MESSAGE_ACTIONS =
            "app/src/main/java/heizige/kk/khatkit/app/core/ui/components/message/ChatMessageActions.kt"

        private fun repoRoot(): File {
            var dir = File("").absoluteFile
            while (true) {
                if (File(dir, "settings.gradle.kts").isFile) return dir
                dir = dir.parentFile ?: error("找不到仓库根（向上没有 settings.gradle.kts）")
            }
        }
    }
}
