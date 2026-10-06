package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 通知标题公式（`resolveNotificationSenderName`）的**全仓唯一性**护栏。
 *
 * ## 这条护栏防的是什么
 *
 * 后台「生成完成」通知的标题是**唯一**会把模型/助手名字显示到系统通知栏里的地方
 * （消费方 `ChatNotificationManager.sendGenerationDoneNotification`：`title = senderName`），
 * 它的值由 [resolveNotificationSenderName] 按**本轮实际使用**的 (assistant, model) 求。
 *
 * 纯函数只能证明**公式**，证明不了「全仓只有一份实现」。历史上仓库里有**两个**生成入口
 * 都消费 `AppEvent.ChatGenerationEnded(conversationId, senderName, …)`：`ChatManager.kt`
 * 与 `core/service/ChatService.kt`。后者**逐字抄了一份**内联的
 * `if (assistant.useAssistantAvatar) …`，于是同一个公式有两份实现、两份可能漂移。
 * 已经发生的漂移就是 B4 —— 通知标题显示会话级模型而非本轮发言角色的模型，在 `ChatManager`
 * 侧于 `ed21db6e` 修掉了，但那份副本不会被那次修复覆盖。
 *
 * 纯函数只能证明**公式**，证明不了「只有一份实现」，故用文本护栏（与
 * `ConversationDrawerFolderScopeTest`、`ChatManagerNotificationSenderNameTest` 同一取舍；
 * `handleMessageComplete` 是 `private suspend` 且依赖一堆 Hilt 注入的协作者，JVM 单测构造不出来）。
 *
 * ## 2026-10-06：`core/service/` 已整体删除后的现状
 *
 * `core/service/ChatService.kt` 是 2026-09-25 `f7463f814` **主动搬走**、2026-10-01 同步
 * 上游 RikkaHub（`8cf9bec2d`）时被 vendor merge 当新文件整包搬回来的死副本，现已删除。
 * 于是：
 *
 * - [bothGenerationEntryPoints_consumeTheSharedFunction] —— **已删**（两个入口里少了一个）
 * - [chatService_gainsGroupPath_onlyIfItRecomputesSenderName] —— **已删**（保护对象不存在了）
 *
 * ⚠️ 剩下这一条**不是**「收窄版」：它扫的是**全仓所有 `src/main`** 的 Kotlin 源码
 * （[mainSourceFiles]），断言公式只允许在 `ChatManager.kt` 一处实现。所以「有人在别处
 * 再抄一份内联 if」这个原始风险**仍然被完整覆盖**，覆盖面与删前完全相同。
 *
 * `ChatManager` 侧的群聊重算另有行为护栏，见 `ChatManagerNotificationSenderNameTest`
 * 的 `groupBranch_recomputesSenderName_afterResolvingModel` 与
 * `groupTurnTitle_followsTheSpeakerNotTheConversation`。
 */
class ChatServiceSenderNameGuardTest {

    // ---------- 结构层：公式全仓只有一份实现 ----------

    /**
     * 任何 `src/main` 源码里都**不允许**再出现第二处 `if (assistant.useAssistantAvatar)` 分支。
     *
     * 匹配的是「直接按助手头像开关分支」这一形状，正是通知标题公式的骨架。气泡侧
     * （`ChatMessageAvatar.kt`）合法地也有一个 `if (useAssistantAvatar)`，但它先取了局部量
     * `val useAssistantAvatar = assistant?.useAssistantAvatar == true`，本正则要求**在 if 条件里
     * 直接取属性**，因此不匹配；助手设置页（`AssistantBasicPage.kt`）是 `checked =` 开关，
     * 也不是 `if`。换句话说，这条断言不会把这两处正常的用法误伤。
     */
    @Test
    fun senderNameFormula_isImplementedExactlyOnce_inAllMainSources() {
        val offenders = mainSourceFiles()
            .filter { INLINE_FORMULA.containsMatchIn(code(it.readText())) }
            .map { it.relativeTo(repoRoot()).path }
            .sorted()

        assertEquals(
            "通知标题公式（按 useAssistantAvatar 分支）只允许在 " +
                "$SHARED_FN_FILE 这一处实现。请把其它地方改成调用共享纯函数 " +
                "resolveNotificationSenderName(assistant, model, defaultAssistantName)，" +
                "不要就地再抄一份内联 if —— 抄出来的副本不会被 ChatManager 侧的修复覆盖，" +
                "群聊下一轮就会显示错模型。实际命中：$offenders",
            listOf(SHARED_FN_FILE),
            offenders,
        )
    }

    // ---------- 3. 触发器：给次入口加群聊路径 ⇒ 必须重算 ----------

    companion object {
        private const val CHAT_MANAGER_FILE =
            "app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt"
        /** 唯一允许实现公式的文件：`resolveNotificationSenderName` 定义所在处。 */
        private const val SHARED_FN_FILE = CHAT_MANAGER_FILE

        /** 「按助手头像开关分支」的形状。允许空 `assistant?.` 以覆盖等价写法。 */
        private val INLINE_FORMULA =
            Regex("""if\s*\(\s*assistant[?!]?\s*\.\s*useAssistantAvatar\s*\)""")

        private val SKIP_DIRS = setOf(
            "build", ".git", ".gradle", ".idea", "node_modules", "out", ".kotlin",
        )

        /**
         * 去掉注释，只留代码。
         *
         * 这条护栏要量的是「代码里有没有第二份公式」，不是「文档里有没有提过这个形状」。
         * 抽共享纯函数那笔 commit 自己在 `ChatService.kt` 里留了一段注释复述旧写法
         * `if (assistant.useAssistantAvatar) …`，没剥注释时它就被当成真命中 —— 护栏因为
         * 解释它自己而变红，只好逼着人把解释删掉。剥掉注释后，注释可以放心写全。
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

        /** 全仓所有 `src/main` 下的 Kotlin 源码（不碰 `src/test` / `src/androidTest`）。 */
        private fun mainSourceFiles(): List<File> {
            val out = mutableListOf<File>()
            fun walk(dir: File) {
                for (child in dir.listFiles() ?: return) {
                    if (!child.isDirectory) continue
                    if (child.name in SKIP_DIRS) continue
                    if (child.name == "src") {
                        // 命中源码集就不再往里钻 `src/test` / `src/androidTest`，省掉整棵树的遍历。
                        File(child, "main").takeIf { it.isDirectory }
                            ?.walkTopDown()
                            ?.filter { it.isFile && it.extension == "kt" }
                            ?.forEach { out += it }
                    } else {
                        walk(child)
                    }
                }
            }
            walk(repoRoot())
            return out
        }
    }
}