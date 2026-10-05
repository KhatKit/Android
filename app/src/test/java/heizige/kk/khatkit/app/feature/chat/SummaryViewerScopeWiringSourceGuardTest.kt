package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 标题/摘要生成接上 viewer 过滤的**接线**护栏。
 *
 * ## 这条护栏防的是什么
 *
 * 缺陷本体：`ChatManager.generateTitle` / `generateSuggestion` / `compressConversation` 把
 * `conversation.currentMessages`（或其切片）原样送进 TITLE / SUMMARY 模型。判定本身抽成纯
 * 函数 [SummaryViewerScope] 并在 [SummaryViewerScopeTest] 里钉死了，但**执行层有没有真的
 * 转调它**是另一件事 —— `generateTitle` / `compressConversation` 是 `ChatManager` 的 suspend
 * 成员，仓库 `testImplementation` 只有 junit，构造不出来（要 `Application` + `AppScope` +
 * Room DAO），所以只能在源码层钉。
 *
 * ## 为什么这层必须有
 *
 * 漏接的后果与不接过滤完全一样（其他角色最近 4 条进 TITLE 模型），而**没有任何行为断言会红**：
 * 纯函数测试只覆盖判定，不覆盖调用点。把判定抽成纯函数很容易让人误以为「已经修好了」——
 * 纯函数返回正确的 viewer 但没人调用它，泄漏照旧。
 *
 * ## 真实可达性（为什么标题那条不是理论问题）
 *
 * `generateTitle` 不只由长按菜单触发，它还在**每一轮群聊结束时自动跑**
 * （`ChatManager.handleMessageComplete` 的 `onSuccess` 末尾），所以群里跑完一轮就泄漏一次。
 * 手动那条路：`ConversationList` 的长按菜单不判会话 type，而抽屉有群聊筛选项、会话列表有
 * 「群」徽标，群会话必然进列表。`compressConversation` 今天不可从群聊页触发（压缩对话框只
 * 在 `ChatPage.kt`，`GroupChatPage.kt` 里 `compress` 出现 0 次），属潜伏漏洞，一并钉住。
 *
 * ## 护栏自身的反空跑断言（C8）
 *
 * 所有切分都用 [indexOf] 硬切、**锚点缺失返空串**，并对每个切分结果断言非空。
 * 不用 `substringAfter` / `substringBefore`：锚点缺失时它们返回**整个字符串**（不抛异常），
 * 于是源码扫描护栏会恒绿 —— 这条已在前几轮踩过。
 */
class SummaryViewerScopeWiringSourceGuardTest {

    // ------------------------------------------------------------------
    // 1. 三个调用点都接了过滤
    // ------------------------------------------------------------------

    /**
     * `generateTitle` / `compressConversation` 两个函数体里都必须出现
     * [SummaryViewerScope.messages]。
     *
     * 只查「调用了」不够：在函数开头算好过滤结果却没接到 prompt 上，那条断言照样成立而泄漏
     * 照旧。所以标题侧额外用一条正则把「`"content" to SummaryViewerScope.messages(...)`」
     * 这个实参位置钉死。
     */
    @Test
    fun `title and compress funnels are wired to the viewer scope`() {
        for (name in listOf("generateTitle", "compressConversation")) {
            val body = bodyOf(name)

            // 反空跑：函数体必须真的被切出来了，不能是空串。空串会让下面两条断言**恒假**，
            // 恒假的断言在任何回归下都不会变红 —— 护栏变成「谁都不违规而恒绿」。
            assertTrue("$name 的函数体切分退化成空串（bodyOf 没切到内容）", body.isNotEmpty())

            assertTrue(
                "$CHAT_MANAGER_FILE 的 $name 必须调用 SummaryViewerScope.messages(" +
                    "作为送进模型的上下文（契约：过滤必须发生在 prompt 组装层，禁止 UI 层" +
                    "隐藏但仍发送）。实际没找到。",
                body.contains("SummaryViewerScope.messages("),
            )
        }

        // `generateTitle` 额外钉一条：prompt 的 `content` 实参必须**直接**是过滤结果，
        // 不许出现裸 `conversation.currentMessages`。这是本题最容易被「半修」掉的形状 ——
        // 在函数开头算好 `SummaryViewerScope.messages(conversation)` 却没接到 prompt 上，
        // 上面那条 `contains` 照样成立，而泄漏照旧。
        //
        // ⚠️ `compressConversation` **不能**套用这条：它必须保留
        // `val allMessages = conversation.currentMessages` 作为完整库存（切分与
        // `messagesToKeep` 都从那里来，见下一条测试）。所以这一条只对标题成立。
        val titleBody = bodyOf("generateTitle")
        assertTrue("generateTitle 的函数体切分退化成空串", titleBody.isNotEmpty())
        assertTrue(
            "generateTitle 的 prompt content 必须直接来自 SummaryViewerScope.messages(" +
                "conversation)，不许出现裸 conversation.currentMessages。" +
                "（单聊下二者逐字等价，群聊下前者才带过滤。）",
            !titleBody.contains("conversation.currentMessages"),
        )
        assertTrue(
            "generateTitle 必须在 `titlePrompt.applyPlaceholders` 的 content 实参位置上使用" +
                "过滤结果，而不是把过滤结果算完丢掉。",
            Regex(""""content" to SummaryViewerScope\.messages\(conversation\)""")
                .containsMatchIn(titleBody),
        )
    }

    /**
     * `compressConversation` 的「切分/落库按完整库存、只有送模型的那份过滤」这条不许被改坏。
     *
     * 具体钉三件事：`messagesToKeep` 仍取自完整 `allMessages`（落库存保持完整，不落第二套
     * 消息库这条硬约束）、`splitMessages` 收的是过滤后的 `compressScope`（送模型的确实是
     * 过滤后那份）、以及那条 fail-closed 早退还在（viewer 在压缩窗口里一条都看不到时不许
     * 发空内容让模型凭空编摘要）。
     */
    @Test
    fun `compress keeps the inventory intact and only filters the copy sent to the model`() {
        val body = bodyOf("compressConversation")

        assertTrue(
            "compressConversation 必须保留 messagesToKeep = allMessages.takeLast(...)，" +
                "落库那份必须来自完整库存（硬约束：过滤只发生在发给模型的副本上）。",
            body.contains("messagesToKeep = allMessages.takeLast(keepRecentMessages)"),
        )
        assertTrue(
            "compressConversation 必须把过滤后的 compressScope 送进 splitMessages，" +
                "而不是 messagesToCompress —— 后者含其他角色的发言。",
            body.contains("splitMessages(compressScope)"),
        )
        assertTrue(
            "compressConversation 必须保留 fail-closed 早退：viewer 在压缩窗口里一条都看不到时" +
                "抛错，而不是发空内容给模型。",
            body.contains("compressScope.isEmpty()"),
        )
        assertTrue("compressConversation 的函数体切分退化成空串", body.isNotEmpty())
    }

    // ------------------------------------------------------------------
    // 2. 反向锚点：真实触发路径
    // ------------------------------------------------------------------

    /**
     * `handleMessageComplete` 的 `onSuccess` 末尾必须调 `generateTitle` —— 即群聊跑完一轮
     * 就会自动触发标题生成。
     *
     * 这条是为了让「generateTitle 的过滤不可摘」这个事实留在测试里：将来若有人把群聊分支的
     * 标题生成删掉、或反过来把 `generateTitle` 挪出群聊路径，这条会红，提示口径又变了。
     */
    @Test
    fun `group rounds still auto trigger title generation through the same funnel`() {
        val source = File(repoRoot(), CHAT_MANAGER_FILE).readText()
        val complete = blockOf(source, "private suspend fun handleMessageComplete(")

        assertTrue(
            "ChatManager.handleMessageComplete 里必须有 generateTitle( 调用（群聊每轮结束" +
                "自动生成标题，正是这条路径让标题越权从「理论问题」变成每轮必犯）。",
            complete.contains("generateTitle(conversationId, finalConversation)"),
        )
        assertTrue(
            "handleMessageComplete 块切分退化成空串",
            complete.isNotEmpty(),
        )
    }

    /**
     * `SummaryViewerScope` 自己不许绕过 `GroupTurnCoordinator.viewerMessages` 另写一套过滤。
     *
     * 契约要求「工具调用、检索与记忆注入均使用同一 viewer 过滤结果」。若标题/摘要这一侧
     * 自己写一个 `filter { it.roleId == ... }`，两处判定必然漂移，而漂移无声。
     */
    @Test
    fun `summary scope delegates to the shared viewer messages kernel`() {
        val source = code(File(repoRoot(), SUMMARY_SCOPE_FILE).readText())

        assertTrue(
            "$SUMMARY_SCOPE_FILE 必须转调 GroupTurnCoordinator.viewerMessages(" +
                "（同一份过滤结果），不许自己写一份 filter。",
            source.contains("GroupTurnCoordinator.viewerMessages("),
        )
        // 退化防护：`source` 因路径错误读空时，上面的 contains 会恒假（恒红，安全方向）；
        // 这里再钉一条「确实读到了文件」，避免护栏因读错路径而假装在跑。
        assertTrue(
            "$SUMMARY_SCOPE_FILE 读不到或为空，护栏在空跑。" +
                "工作目录：${File("").absolutePath}",
            source.isNotEmpty(),
        )
    }

    /**
     * 「按 mode 选 viewer」的口径必须写在 KDoc 里并显式标注待产品确认。
     *
     * 这条不是形式主义：口径本身是判断（见 [SummaryViewerScope] 类 KDoc），而它决定了群聊
     * 标题的质量。将来有人改口径时，必须同时看到「这里原来是待确认状态」这句话。
     */
    @Test
    fun `the mode based viewer rule is documented as pending product confirmation`() {
        val raw = File(repoRoot(), SUMMARY_SCOPE_FILE).readText()

        assertTrue(
            "$SUMMARY_SCOPE_FILE 的 KDoc 必须包含「待产品确认」标注（按 mode 选 viewer 是" +
                "产品判断，不是实证结论）。",
            raw.contains("待产品确认"),
        )
        for (mode in listOf("roundtable", "pipeline")) {
            assertTrue(
                "$SUMMARY_SCOPE_FILE 的 KDoc 必须写明 $mode 模式下的 viewer 取法，" +
                    "否则改口径的人看不到原来的依据。",
                raw.contains(mode),
            )
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private val chatManagerLines by lazy {
        File(repoRoot(), CHAT_MANAGER_FILE).readText().split("\n")
    }

    /**
     * 取 `ChatManager` 里某个成员函数的整块函数体。
     *
     * 成员函数缩进 4，闭合大括号也正好 4 空格 —— 用**行内容**而不是缩进猜，避免函数体里有
     * 缩进恰为 4 的局部函数时切错。
     */
    private fun bodyOf(name: String): String {
        val lines = chatManagerLines
        val start = lines.indexOfFirst {
            it.startsWith("    suspend fun $name(") ||
                it.startsWith("    private suspend fun $name(") ||
                it.startsWith("    private fun $name(") ||
                it.startsWith("    fun $name(")
        }
        assertTrue("函数没找到：$name（$CHAT_MANAGER_FILE）", start >= 0)
        val end = (start + 1 until lines.size).firstOrNull { lines[it] == "    }" }
        assertTrue("函数闭合大括号没找到：$name", end != null)
        return lines.subList(start, end!! + 1).joinToString("\n")
    }

    /** 同 [bodyOf]，但从整份源码里找 `private suspend fun handleMessageComplete(`。 */
    private fun blockOf(source: String, declaration: String): String {
        val lines = source.split("\n")
        val start = lines.indexOfFirst { it.trimStart().startsWith(declaration.trimStart()) }
        assertTrue("块没找到：$declaration", start >= 0)
        // 这个函数体很长且内部有嵌套的 4 空格闭合（对象字面量 / when），所以从声明行往后找
        // **最后一个**恰好是 `    }` 的行不行不通。改为：从声明行起逐字符做花括号配平。
        var depth = 0
        var started = false
        for (index in start until lines.size) {
            for (ch in lines[index]) {
                when (ch) {
                    '{' -> { depth++; started = true }
                    '}' -> depth--
                }
            }
            if (started && depth == 0) return lines.subList(start, index + 1).joinToString("\n")
        }
        assertTrue("块的花括号没配平：$declaration", false)
        return ""
    }

    companion object {
        private const val CHAT_MANAGER_FILE =
            "app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt"
        private const val SUMMARY_SCOPE_FILE =
            "app/src/main/java/heizige/kk/khatkit/app/feature/chat/SummaryViewerScope.kt"

        private fun repoRoot(): File {
            var dir = File("").absoluteFile
            while (true) {
                if (File(dir, "settings.gradle.kts").isFile) return dir
                dir = dir.parentFile ?: error("找不到仓库根（向上没有 settings.gradle.kts）")
            }
        }

        /** 去掉注释，只留代码（KDoc 断言走 `readText()` 原样，不剥）。 */
        private fun code(source: String): String = source
            .replace(BLOCK_COMMENT, " ")
            .replace(LINE_COMMENT, " ")

        private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        private val LINE_COMMENT = Regex("""//[^\n]*""")
    }
}