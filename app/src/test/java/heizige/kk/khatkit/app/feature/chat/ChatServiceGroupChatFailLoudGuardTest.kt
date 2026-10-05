package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ① `ChatService.handleMessageComplete` 的 **fail-loud** 护栏。
 *
 * ## 这条护栏防的是什么
 *
 * 仓库里有**两个**都叫「生成管线」的类，其中群聊契约**只**由 `ChatManager` 一处实现：
 *
 * | 入口 | 文件 | 群聊感知 |
 * |---|---|---|
 * | 真正在用的 | `feature/chat/ChatManager.kt` | 有（`takeGroupTurn` + viewer 过滤） |
 * | 历史重复实现 | `core/service/ChatService.kt` | **无**（`grep -c groupConfig` = 0） |
 *
 * `ChatService` 那份的 `handleMessageComplete` 四个泄漏点全中：传完整 `currentMessages`
 * （不经 `GroupPerspectiveTransformer`）、`inputTransformers` 不挂群聊 transformer、
 * 工具不走 `viewerScopedTools`、记忆空间用助手/全局空间。若它被接上，**整轮所有角色互相
 * 可见全部历史**，契约「禁止在 UI 层隐藏但仍发送」当场失效 —— 而且**没有任何测试会红**：
 * 现有群聊用例全部直接调 `viewerMessages`，从不经过任何生成入口。
 *
 * 危险的是它今天**不可达但看不出来**：`AppHiltModule` 里那个名叫 `provideChatService`
 * 的 `@Provides` 返回的其实是 `ChatManager`，而标识符 `chatService` 在
 * `ConversationRoutes` 等处指向 `ChatManager`、在 `HistoryVM` 处指向 `ChatService`。
 *
 * ## 为什么钉「不许出现群聊逻辑」+「必须 fail-loud」，而不是钉「必须实现群聊」
 *
 * 群聊契约是全项目最安全攸关的代码，且它的正确性**只能靠真机证据**验收（见
 * `docs/eval/c1-group-chat.md`，十例仍全部 `unverified`）。复制一份到 `ChatService`
 * 等于造出第二个真相来源：两处必然漂移，且漂移无声。这里的目标是让
 * 「群聊经 `ChatService` 生成」**在结构上不可能**，而不是让它「也能跑」。
 *
 * ## 分工
 *
 * - [chatServiceHasNoGroupChatLogic] —— 现状锚点：没有群聊上下文，将来要加必须显式改这条
 * - [handleMessageCompleteFailsLoudOnGroupChat] —— 不变量本体：群聊会话必须被当场拒收
 * - [handleMessageCompleteIsTheOnlyChatTurnFunnel] —— 闸门放这里就够（结构论证）
 * - [diProviderNameMatchesItsProduct] —— 命名陷阱本身不许回来
 */
class ChatServiceGroupChatFailLoudGuardTest {

    /**
     * 现状锚点：`ChatService.kt` 里不得出现任何群聊生成上下文。
     *
     * 与 `ChatServiceSenderNameGuardTest.GROUP_MARKERS` 刻意重叠：那边钉的是「一旦出现
     * 就必须重算 `senderName`」，这边钉的是「今天一个都不许有」。两条同时在，才既不会
     * 让今天悄悄长出群聊路径，也不会在有人**有意**搬运时给出误导性的重算义务。
     */
    @Test
    fun chatServiceHasNoGroupChatLogic() {
        val source = code(File(repoRoot(), CHAT_SERVICE_FILE).readText())
        val markers = GROUP_MARKERS.filter { source.contains(it) }
        assertEquals(
            "$CHAT_SERVICE_FILE 不该有群聊生成上下文（$markers）。" +
                "群聊契约只由 $CHAT_MANAGER_FILE 一处实现；要接群聊生成请把注入点改成 " +
                "ChatManager，而不是在这里复制一份 —— 复制会造出第二个真相来源且漂移无声。",
            emptyList<String>(),
            markers,
        )
    }

    /**
     * 不变量本体：`handleMessageComplete` 必须对群聊会话 **fail-loud**。
     *
     * 钉两件事，缺一不可：
     * 1. 真的判了群聊（用了共享判据 `isGroupConversation`，不是自己另立一套）；
     * 2. 判完之后是**抛错**，而不是打个日志继续跑 —— 「静默跑错逻辑」正是这条缺陷的原形状。
     */
    @Test
    fun handleMessageCompleteFailsLoudOnGroupChat() {
        val body = bodyOf(File(repoRoot(), CHAT_SERVICE_FILE), "handleMessageComplete")
        assertTrue(
            "ChatService.handleMessageComplete 必须判群聊会话。群聊一旦从这里发起生成，" +
                "整轮所有角色互相可见全部历史，且没有任何测试会红。",
            body.contains("isGroupConversation("),
        )
        assertTrue(
            "ChatService.handleMessageComplete 必须对群聊会话 fail-loud（require/error 抛错），" +
                "不能只记日志继续跑 —— 静默跑错逻辑就是这条缺陷的原形状。",
            Regex("""\b(require|error)\s*\(\s*!\s*isGroupConversation\s*\(""").containsMatchIn(body),
        )
    }

    /**
     * 闸门位置的结构论证：`handleMessageComplete` 必须是 `ChatService` 里
     * `generationLoop.generateText` 的**唯一**调用点。
     *
     * 这条不是为了好看：如果将来有人在 `ChatService` 里**再开**一个聊天轮次入口（而不是走
     * 改好的 `handleMessageComplete`），上面那条 fail-loud 断言就管不到它了。这里的
     * `generationLoop` 与 `providerHandler` 是两个不同的生成器：聊天轮次走前者，
     * 而 `providerHandler.generateText` 只出现在 `finishInterruptedPendingTools`
     * （取消后续跑工具的旁路），所以只查 `generationLoop` 不会误伤。
     */
    @Test
    fun handleMessageCompleteIsTheOnlyChatTurnFunnel() {
        val source = File(repoRoot(), CHAT_SERVICE_FILE).readText()
        val callSites = Regex("""generationLoop\.generateText\s*\(""").findAll(source).count()
        assertEquals(
            "$CHAT_SERVICE_FILE 里 `generationLoop.generateText` 应只有一个调用点（" +
                "handleMessageComplete）。多出来的调用点就是第二个聊天轮次入口，" +
                "fail-loud 闸门管不到它。",
            1,
            callSites,
        )
    }

    /**
     * 命名陷阱本身：`AppHiltModule` 里不得存在「名字带 ChatService、返回 ChatManager」
     * 的 `@Provides`。
     *
     * 这个方法原来就叫 `provideChatService`，返回的却是 `ChatManager`。按名字看注入点的人
     * 会以为拿到的是 `ChatService`，于是把 `core/service/ChatService.kt` 里那份无群聊感知
     * 的重复实现接上去 —— 这就是 ① 的由来。
     */
    @Test
    fun diProviderNameMatchesItsProduct() {
        val source = code(File(repoRoot(), DI_MODULE_FILE).readText())
        assertTrue(
            "$DI_MODULE_FILE 里必须有 `fun provideChatManager(`。" +
                "名字叫 provideChatService 却返回 ChatManager，会让人以为注入到的就是 " +
                "ChatService，从而把 core/service 里那份无群聊感知的重复实现接上去。",
            source.contains("fun provideChatManager("),
        )
        val offenders = Regex("""fun\s+\w*ChatService\w*\s*\(""")
            .findAll(source)
            .map { it.value }
            .toList()
        assertEquals(
            "$DI_MODULE_FILE 里不许再有名为 provideChatService 的 @Provides（返回的却是 ChatManager）",
            emptyList<String>(),
            offenders,
        )
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 取某个成员函数的整块函数体（缩进 4，闭合大括号也正好 4 空格）。 */
    private fun bodyOf(file: File, name: String): String {
        val lines = file.readText().split("\n")
        val start = lines.indexOfFirst {
            it.startsWith("    private suspend fun $name(") || it.startsWith("    private fun $name(")
        }
        assertTrue("函数没找到：$name（$file）", start >= 0)
        val end = (start + 1 until lines.size).firstOrNull { lines[it] == "    }" }
        assertTrue("函数闭合大括号没找到：$name（$file）", end != null)
        return lines.subList(start, end!! + 1).joinToString("\n")
    }

    companion object {
        private const val CHAT_SERVICE_FILE =
            "app/src/main/java/heizige/kk/khatkit/app/core/service/ChatService.kt"
        private const val CHAT_MANAGER_FILE =
            "app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt"
        private const val DI_MODULE_FILE =
            "app/src/main/java/heizige/kk/khatkit/app/core/di/AppHiltModule.kt"

        /**
         * 「出现群聊生成上下文」的标记词。
         *
         * ⚠️ **刻意不含 `isGroupConversation`**：fail-loud 闸门必须调用这个共享判据
         * （它是群聊会话的唯一口径，与 `GroupConversationOperationGuard` 同源），
         * 所以它出现在 `ChatService` 里是**正确**的，不能算标记。
         */
        private val GROUP_MARKERS = listOf(
            "groupConfig",
            "takeGroupTurn",
            "GroupTurnEntry",
            "SpeakerStep",
            "resolveGroupTurnModelId",
            "groupStep",
            "viewerMessages",
            "viewerScopedTool",
            "GroupTurnCoordinator",
            "groupRunDAO",
        )

        /**
         * 去掉注释，只留代码。
         *
         * 这条护栏量的是「代码里有没有群聊逻辑」，不是「文档里有没有提过这个形状」——
         * `ChatService` 的类 KDoc 就要把这些词全写一遍解释为什么不许有。剥掉注释后，
         * 注释可以放心写全。
         */
        private fun code(source: String): String = source
            .replace(BLOCK_COMMENT, " ")
            .replace(LINE_COMMENT, " ")

        private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        private val LINE_COMMENT = Regex("""//[^\n]*""")

        /**
         * 从工作目录向上找带 `settings.gradle.kts` 的那层当仓库根。
         *
         * 不写死 `..`：Gradle JVM 单测的工作目录是模块目录（`:app`），但那是个约定而非
         * 契约，约定变了整条护栏会静默读错文件路径退化成「什么都没检查」。
         */
        private fun repoRoot(): File {
            var dir = File("").absoluteFile
            while (true) {
                if (File(dir, "settings.gradle.kts").isFile) return dir
                dir = dir.parentFile ?: error("找不到仓库根（向上没有 settings.gradle.kts）")
            }
        }
    }
}
