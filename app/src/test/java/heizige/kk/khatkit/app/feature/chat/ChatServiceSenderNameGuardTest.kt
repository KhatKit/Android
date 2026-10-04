package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `ChatService.handleMessageComplete` 的通知标题护栏。
 *
 * ## 这条护栏防的是什么
 *
 * 后台「生成完成」通知的标题是**唯一**会把模型/助手名字显示到系统通知栏里的地方
 * （消费方 `ChatNotificationManager.sendGenerationDoneNotification`：`title = senderName`），
 * 它的值由 [resolveNotificationSenderName] 按**本轮实际使用**的 (assistant, model) 求。
 *
 * 仓库里有**两个**生成入口都消费 `AppEvent.ChatGenerationEnded(conversationId, senderName, …)`：
 *
 * | 入口 | 文件 | 群聊路径 | 现状 |
 * |---|---|---|---|
 * | 主入口 | `ChatManager.kt` | 有（`takeGroupTurn`） | 群聊分支里重算 `senderName`（`ed21db6e`） |
 * | 次入口 | `ChatService.kt` | 无（`grep -c groupConfig` = 0） | 只求一次，单聊等价 |
 *
 * 定时炸弹就在次入口：它原先**逐字抄了一份**内联的 `if (assistant.useAssistantAvatar) …`，
 * 于是同一个公式有两份实现、两份可能漂移。已经发生的漂移就是 B4 —— 通知标题显示会话级
 * 模型而非本轮发言角色的模型，在 `ChatManager` 侧于 `ed21db6e` 修掉了，但**次入口那份
 * 副本不会被那次修复覆盖**。它当下无害，只因为 `ChatService` 里没有群聊路径；一旦有人
 * 把群聊能力搬进 `ChatService`（或给它加一个群聊分支），`assistant` / `model` 会在群聊
 * 分支里被换成发言角色那一套，而在这之前求出的 `senderName` 不会跟着重算 —— 同一个 bug
 * 在次入口复现，且没有任何编译期信号。
 *
 * 纯函数只能证明**公式**，证明不了「调用点在正确的位置 / 只有一份实现」。这里三件事都
 * 必须在源码层钉住，故用文本护栏（与 `ConversationDrawerFolderScopeTest`、
 * `ChatManagerNotificationSenderNameTest` 同一取舍；`handleMessageComplete` 是
 * `private suspend` 且依赖一堆 Hilt 注入的协作者，JVM 单测构造不出来）。
 *
 * 分工：
 * - [senderNameFormula_isImplementedExactlyOnce_inAllMainSources] —— 结构层：公式不允许有第二份实现
 * - [bothGenerationEntryPoints_consumeTheSharedFunction] —— 两个入口都调共享纯函数
 * - [chatService_gainsGroupPath_onlyIfItRecomputesSenderName] —— 触发器：真给次入口加群聊路径时，重算义务随之而来
 *
 * 前两条让「重复」不可能悄悄回来；第三条让「搬群聊」这件事必须先面对重算义务。
 */
class ChatServiceSenderNameGuardTest {

    // ---------- 1. 结构层：公式全仓只有一份实现 ----------

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

    // ---------- 2. 两个生成入口都消费共享纯函数 ----------

    @Test
    fun bothGenerationEntryPoints_consumeTheSharedFunction() {
        for (entry in listOf(CHAT_MANAGER_FILE, CHAT_SERVICE_FILE)) {
            val source = code(File(repoRoot(), entry).readText())
            assertTrue(
                "$entry 必须调用共享纯函数 resolveNotificationSenderName(" +
                    "，而不是自己求值：两个生成入口消费同一个 AppEvent.ChatGenerationEnded，" +
                    "标题口径必须同源。",
                source.contains("resolveNotificationSenderName("),
            )
        }
    }

    // ---------- 3. 触发器：给次入口加群聊路径 ⇒ 必须重算 ----------

    /**
     * `ChatService.kt` 里**一旦出现群聊上下文**，就必须同时承担「按本轮发言角色重算
     * `senderName`」的义务，否则这条断言立刻红。
     *
     * 今天 `ChatService` 里没有任何群聊标记（`groupConfig` / `takeGroupTurn` /
     * `GroupTurnEntry` / `SpeakerStep` / `resolveGroupTurnModelId`），所以这个分支现在是
     * 空条件 —— 它钉的**不是**「次入口不许有群聊」，而是「有群聊就必须重算」，与
     * `ChatManagerNotificationSenderNameTest` 里那条群聊重算护栏同口径。这样一次**正确**
     * 的「把群聊能力搬进 `ChatService`」仍然能过，而一次**漏了重算**的搬运必然被拦下，
     * 失败信息直接指出要做什么。
     */
    @Test
    fun chatService_gainsGroupPath_onlyIfItRecomputesSenderName() {
        val source = code(File(repoRoot(), CHAT_SERVICE_FILE).readText())
        val groupMarkers = GROUP_MARKERS.filter { source.contains(it) }

        // 现状：无群聊路径。这条断言记录「炸弹尚未引爆」这个前提本身。
        assertEquals(
            "$CHAT_SERVICE_FILE 不该有群聊上下文。若这是有意新增的群聊路径，" +
                "请先确认本测试的「重算」分支对新的标记词生效，并把标记词补进 GROUP_MARKERS。",
            emptyList<String>(),
            groupMarkers,
        )

        // 触发器：下面这段在今天不可达，但它就是这条护栏的牙齿所在 —— 一旦有人写了群聊
        // 分支而没重算，第一个命中它的就是它。
        if (groupMarkers.isNotEmpty()) {
            assertTrue(
                "$CHAT_SERVICE_FILE 出现群聊上下文（$groupMarkers）后，senderName 必须改成 " +
                    "var 并在群聊分支里重算，否则通知标题显示会话级模型而非本轮发言角色的模型。" +
                    "照抄 ChatManager.handleMessageComplete 群聊分支的做法：先 " +
                    "model = TaskRoutes.resolve(...)，再 senderName = resolveNotificationSenderName(...)",
                source.contains("var senderName"),
            )
            val groupIndex = groupMarkers.minOf { source.indexOf(it) }
            assertTrue(
                "$CHAT_SERVICE_FILE 里群聊分支之后必须重算 senderName（命中标记 $groupMarkers）",
                source.indexOf("senderName = resolveNotificationSenderName(", groupIndex) > groupIndex,
            )
        }
    }

    companion object {
        private const val CHAT_MANAGER_FILE =
            "app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt"
        private const val CHAT_SERVICE_FILE =
            "app/src/main/java/heizige/kk/khatkit/app/core/service/ChatService.kt"

        /** 唯一允许实现公式的文件：`resolveNotificationSenderName` 定义所在处。 */
        private const val SHARED_FN_FILE = CHAT_MANAGER_FILE

        /** 「按助手头像开关分支」的形状。允许空 `assistant?.` 以覆盖等价写法。 */
        private val INLINE_FORMULA =
            Regex("""if\s*\(\s*assistant[?!]?\s*\.\s*useAssistantAvatar\s*\)""")

        /** `ChatService` 里出现任一词就算「有群聊上下文」。 */
        private val GROUP_MARKERS = listOf(
            "groupConfig",
            "takeGroupTurn",
            "GroupTurnEntry",
            "SpeakerStep",
            "resolveGroupTurnModelId",
            "groupStep",
        )

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