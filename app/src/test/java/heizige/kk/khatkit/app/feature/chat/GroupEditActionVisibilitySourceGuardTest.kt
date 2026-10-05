package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * C6 —— 群聊下「编辑」入口的**口径**护栏。
 *
 * ## 这条护栏防的是什么
 *
 * `ChatMessageActionsSheet` 里另外三个会改写消息的动作（重新生成 / 创建分支 / 删除）都是
 * `if (!groupChat)` **整条入口消失**。唯独「编辑」不是：
 *
 * - 群聊下**改角色发言**会被 `ChatPage` 的 `canEditMessage` 拦下（`GroupChatPage` 传入
 *   `it.role == MessageRole.USER`）；
 * - 但**改用户自己那条提问是放行的**，而且是合法且常用的路径 —— 群轮次由新的 user 消息
 *   重新派生，不改写 `committed_role_ids`，不会错位。
 *
 * 这两半合起来推出一个洞：卡片**不按消息收**，只靠最后一层 `canEditMessage` 兜。
 * 于是用户在群聊里长按一条**角色发言**，「更多」弹层里照样能看到「编辑」，
 * 点下去先 `dismiss()` 再调 `onEdit()`，然后被 `canEditMessage` 静默丢弃 ——
 * **「点了没反应」**。这不是理论推导，`ChatList` 的长按 wiring 里当时
 * `onEdit = { onEdit(currentMessage) }` 也是裸的（另外三个都带 `if (!groupChat)` 双保险）。
 *
 * ## 为什么不能照抄成 `if (!groupChat)`
 *
 * 那会把合法的那一半（改用户提问）一起关掉，是对群聊功能的**真实回退**，
 * 而且与 `GroupChatPage` KDoc 里写明的口径（「改用户提问是合法且常用的」）直接冲突。
 * 所以正确的门禁是**按消息**的 `!groupChat || <这条是用户提问>`。
 *
 * ## 这条护栏钉的是什么
 *
 * 钉的是**三处门禁必须同口径**（展示层 `ChatMessageActions` / 触发层 `ChatList` /
 * 最终裁决 `ChatPage.canEditMessage`），以及另外三个动作**必须保持整条 `if (!groupChat)`**——
 * 免得有人为「统一风格」把编辑也改成整条关门。
 *
 * ⚠️ 仓库 `testImplementation` 只有 junit，`groupChat` 布尔在 JVM 上跑不起来，
 * 所以这是**源码文本**护栏（同 `GroupOperationApiGuardSourceGuardTest` 的口径）。
 * 它钉不住「用户点下去真的有反应」这个运行时事实 —— 那要仪器测试。
 */
class GroupEditActionVisibilitySourceGuardTest {

    // ------------------------------------------------------------------
    // 1. 展示层：编辑卡片按消息收门禁
    // ------------------------------------------------------------------

    /**
     * `ChatMessageActionsSheet` 的 Edit 卡片必须被**按消息**的门禁包住，且门禁是
     * `!groupChat || <角色是 USER>`，不是整条 `if (!groupChat)`。
     *
     * 切分方式按深度 0 的大括号配平（不是 `substringAfter`）：找不到配平的大括号时
     * [blockAfter] 返回空串，由下面那条 `isNotEmpty` 断言先红 —— 绝不让它退化成
     * 「拿到整份文件、于是断言恒真」。
     */
    @Test
    fun `the edit card is gated per message, not closed entirely in group chat`() {
        val source = code(File(repoRoot(), CHAT_MESSAGE_ACTIONS).readText())

        val gateAt = source.indexOf(EDIT_GATE)
        assertTrue(
            "$CHAT_MESSAGE_ACTIONS 里找不到编辑卡片的群聊门禁 `$EDIT_GATE`。" +
                "缺了它，群聊长按角色发言会看到「编辑」而点下去没反应。",
            gateAt >= 0,
        )

        val gated = blockAfter(source, gateAt)
        assertTrue(
            "编辑卡片的门禁块切分退化了（没切到任何内容）：`$EDIT_GATE` 后面必须是一整块 KedgeCard。" +
                "块内容=<<$gated>>",
            gated.isNotEmpty(),
        )
        assertTrue(
            "被门禁包住的必须确实是 Edit 卡片（KedgeCard 里用 edit 图标 + R.string.edit），" +
                "否则门禁包错了东西。块内容：\n$gated",
            Regex("""\bedit\b""").containsMatchIn(gated) &&
                gated.contains("stringResource(R.string.edit)"),
        )
assertTrue(
            "门禁块里必须真的调 onEdit()，否则这是一条什么都不做的死路。块内容：\n$gated",
            gated.contains("onEdit()"),
        )
    }

    /**
     * 整条关门（`if (!groupChat)`）会把「改用户提问」这条合法路径一起关掉，所以不许回退成那样。
     *
     * ⚠️ 这里不需要另写一条 `!startsWith("if (!groupChat) {")` —— 那样的断言对 KDoc 措辞
     * 太敏感、且在锚点仍能找到时是恒真的空检查。真正的守卫是上面那条：一旦有人把门禁改回
     * 整条 `if (!groupChat) {`，[EDIT_GATE] 锚点立刻找不到，第一条测试先红。
     * 下面只补一句「门禁确实是按消息的」，避免它被改成别的按消息条件（例如按 part 类型）。
     */
    @Test
    fun `the edit gate keys off the message role, not something message local`() {
        val source = code(File(repoRoot(), CHAT_MESSAGE_ACTIONS).readText())
        val gateAt = source.indexOf(EDIT_GATE)
        assertTrue(
            "$CHAT_MESSAGE_ACTIONS 里找不到编辑卡片的群聊门禁 `$EDIT_GATE`。",
            gateAt >= 0,
        )

        // 取门禁所在那一整行的注释清理后文本：判据必须是「按消息角色」，不许是别的按消息条件
        // （例如按 part 类型、按 isPendingDelete）。
        val gateLine = source.substring(gateAt, source.indexOf('\n', gateAt).takeIf { it > 0 } ?: source.length)
        assertTrue(
            "群聊编辑门禁必须是 `!groupChat || <消息>.role == MessageRole.USER` 这种按消息角色的写法。" +
                "实际那一行：${gateLine.trim()}",
            Regex("""^if\s*\(\s*!\s*groupChat\s*\|\|\s*\w+\.role\s*==\s*MessageRole\.USER\s*\)\s*\{?\s*$""")
                .containsMatchIn(gateLine.trim()),
        )
    }

    // ------------------------------------------------------------------
    // 2. 触发层：ChatList 的 onEdit 也带同一门禁
    // ------------------------------------------------------------------

    /**
     * `ChatList` 的 `onEdit = { … }` 必须带同一判定（展示层之外的「双保险」那一半）。
     *
     * 原来这里是裸的 `onEdit(currentMessage)`，而紧挨着的 `onRegenerate` / `onFork` /
     * `onDelete` 三条都带 `if (!groupChat)` —— 一眼就能看出编辑是被漏掉的那一个。
     */
    @Test
    fun `the ChatList onEdit wiring carries the same per message gate`() {
        val source = code(File(repoRoot(), CHAT_LIST).readText())

        val wiringAt = source.indexOf(ON_EDIT_WIRING)
        assertTrue(
            "$CHAT_LIST 里找不到 `onEdit = {` 那段 wiring（锚点 `$ON_EDIT_WIRING`）。",
            wiringAt >= 0,
        )

        val wiring = blockAfter(source, wiringAt)
        assertTrue(
            "onEdit wiring 切分退化了（没切到任何内容）。",
            wiring.isNotEmpty(),
        )
        assertTrue(
            "ChatList 的 onEdit 必须带与展示层同一判定 `!groupChat || currentMessage.role == " +
                "MessageRole.USER`，否则展示层漏收的那条仍会被触发、照样「点了没反应」。" +
                "实际 wiring：\n$wiring",
            wiring.contains("if (!groupChat || currentMessage.role == MessageRole.USER)"),
        )
    }

    // ------------------------------------------------------------------
    // 3. 三处同口径 + 另外三个动作保持整条关门
    // ------------------------------------------------------------------

    /**
     * 三处门禁的角色判定必须逐字一致，否则中间任何一处改了都会重新出现「点了没反应」。
     *
     * 反空跑：写死的 role 判定串必须**恰好**在三处源码里各出现一次，
     * 少一处说明少了一道门（展示/触发/裁决），多一处说明有人复制了一份会漂移的判定。
     */
    @Test
    fun `all three gates agree on which messages stay editable`() {
        val actions = code(File(repoRoot(), CHAT_MESSAGE_ACTIONS).readText())
        val list = code(File(repoRoot(), CHAT_LIST).readText())
        val page = code(File(repoRoot(), GROUP_CHAT_PAGE).readText())
        val chatPage = code(File(repoRoot(), CHAT_PAGE).readText())

        val sites = listOf(
            "ChatMessageActions（展示层）" to (actions to EDIT_ROLE_CHECK),
            "ChatList（触发层）" to (list to LIST_ROLE_CHECK),
            "GroupChatPage（最终裁决）" to (page to CAN_EDIT_ROLE_CHECK),
        )

        assertEquals("本测试登记的门禁处数变了：新增/删除门禁请一并登记", 3, sites.size)

        sites.forEach { (where, pair) ->
            val (text, needle) = pair
            assertEquals(
                "$where 必须恰好有一处角色判定 `$needle`。" +
                    "0 = 那道门没了（点了没反应会回来）；多于 1 = 复制了一份会漂移的判定。",
                1,
                text.split(needle).size - 1,
            )
        }

        // `canEditMessage` 的默认值必须是「恒放行」—— 这是单聊行为逐字不变的前提。
        assertTrue(
            "$CHAT_PAGE 的 canEditMessage 默认值必须是 `{ true }`（单聊默认恒放行）。" +
                "改成别的就等于改了非群聊路径的行为。",
            Regex("""canEditMessage:\s*\(\s*UIMessage\s*\)\s*->\s*Boolean\s*=\s*\{\s*true\s*}""")
                .containsMatchIn(chatPage),
        )
    }

    /**
     * 另外三个动作**必须保持整条 `if (!groupChat)`**。
     *
     * 这条看着像「不许改」，其实是防止有人为了「和编辑统一风格」把三个整条关门的
     * 改成按消息的 —— 它们的 `onXxx` 在群聊下对**任何**消息都是禁的，没有合法的一半。
     * 同时反空跑地钉住「这三个门禁此刻真的各存在一处」。
     */
    @Test
    fun `regenerate fork and delete keep closing entirely in group chat`() {
        val actions = code(File(repoRoot(), CHAT_MESSAGE_ACTIONS).readText())
        val list = code(File(repoRoot(), CHAT_LIST).readText())

        // 展示层：三条 if (!groupChat) —— 加上单聊分支的 regenerate 正好 4 处。
        assertEquals(
            "$CHAT_MESSAGE_ACTIONS 里 `if (!groupChat)` 的门禁数变了：" +
                "重新生成 / 创建分支 / 删除 三个动作必须各自保持整条关门。" +
                "数量变化时请更新本数字并说明改动。",
            4,
            actions.split("if (!groupChat)").size - 1,
        )
        // 触发层：三条。
        assertEquals(
            "$CHAT_LIST 里三个动作的 `if (!groupChat)` 双保险数变了。",
            3,
            list.split("if (!groupChat) ").size - 1,
        )
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * 从 [at]（一段条件的起点）起，按深度 0 的大括号配平切出它包住的整块。
     *
     * ⚠️ 刻意**不用** `substringAfter`：锚点找不到时 Kotlin 的 `substringAfter` 返回
     * **整个字符串**而不是抛异常，于是调用方拿到一份全文、断言恒真（切分退化的经典形态）。
     * 这里返回空串，由调用方的 `isNotEmpty` 断言把退化变成红灯。
     */
    private fun blockAfter(src: String, at: Int): String {
        val open = src.indexOf('{', at)
        if (open < 0) return ""
        var depth = 0
        for (index in open until src.length) {
            when (src[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(open + 1, index)
                }
            }
        }
        return ""
    }

    /** 去注释，保持行数不变，避免 KDoc 里提到某个标识符就被当成真代码。 */
    private fun code(source: String): String = source
        .replace(BLOCK_COMMENT) { m -> m.value.replace(NON_NEWLINE, " ") }
        .replace(LINE_COMMENT, " ")

    private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    private val LINE_COMMENT = Regex("""//[^\n]*""")
    private val NON_NEWLINE = Regex("""[^\n]""")

    private companion object {
        private const val CHAT_MESSAGE_ACTIONS =
            "app/src/main/java/heizige/kk/khatkit/app/core/ui/components/message/ChatMessageActions.kt"
        private const val CHAT_LIST =
            "app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatList.kt"
        private const val CHAT_PAGE =
            "app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatPage.kt"
        private const val GROUP_CHAT_PAGE =
            "app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupChatPage.kt"

        /** 展示层的门禁锚点。 */
        private const val EDIT_GATE = "if (!groupChat || message.role == MessageRole.USER)"

        /** 触发层的 wiring 锚点。 */
        private const val ON_EDIT_WIRING = "onEdit = {"

        private const val EDIT_ROLE_CHECK = "!groupChat || message.role == MessageRole.USER"
        private const val LIST_ROLE_CHECK = "!groupChat || currentMessage.role == MessageRole.USER"
        private const val CAN_EDIT_ROLE_CHECK = "it.role == MessageRole.USER"

        private fun repoRoot(): File {
            var dir = File("").absoluteFile
            while (true) {
                if (File(dir, "settings.gradle.kts").isFile) return dir
                dir = dir.parentFile ?: error("找不到仓库根（向上没有 settings.gradle.kts）")
            }
        }
    }
}