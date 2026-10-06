package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 生成管线**唯一性**的结构护栏（历史为 `ChatServiceGroupChatFailLoudGuardTest`）。
 *
 * ## 这条护栏防的是什么
 *
 * 群聊契约**只**由 `feature/chat/ChatManager.kt` 一处实现。曾经存在过第二份「生成管线」
 * —— `core/service/ChatService.kt`，它无群聊感知（`grep -c groupConfig` = 0），`handleMessageComplete`
 * 四个泄漏点全中：传完整 `currentMessages`（不经 `GroupPerspectiveTransformer`）、
 * `inputTransformers` 不挂群聊 transformer、工具不走 `viewerScopedTools`、记忆空间用助手/全局空间。
 *
 * ⚠️ 那份是 2026-09-25 `f7463f814` **主动搬走**、2026-10-01 同步上游 RikkaHub（`8cf9bec2d`）
 * 时被 vendor merge 当新文件整包搬回来的死副本（`git diff --stat 8cf9bec2d^1 8cf9bec2d --
 * app/src/main/java/heizige/kk/khatkit/app/core/service/` = 5 files changed / 1994 insertions(+) /
 * **0 deletions**）。
 *
 * **现状**：`core/service/` 整个目录已删除（主人拍板「不要那个消息建议了」，即认定聊天建议功能
 * 已废弃、连同死副本一起清理）。因此原先三条护栏
 * （[chatServiceHasNoGroupChatLogic] / [handleMessageCompleteFailsLoudOnGroupChat] /
 * [handleMessageCompleteIsTheOnlyChatTurnFunnel]）**保护对象已不存在**，一并删除。
 * 「群聊经 `ChatService` 生成」现在由**文件根本不存在**来保证，比任何文本护栏都强。
 *
 * ⚠️ 注意 `ChatManager` 侧**不需要** fail-loud 闸门：它原生支持群聊（`ChatManager.kt:787`
 * `takeGroupTurn`），「拒绝群聊会话」才是错的契约。
 *
 * ## 保留下来的这条
 *
 * - [diProviderNameMatchesItsProduct] —— 命名陷阱本身不许回来：那个 `@Provides` 原来叫
 *   `provideChatService` 却返回 `ChatManager`，正是当初让人把无群聊感知的重复实现接上去的源头。
 *   它护的是 `AppHiltModule`，与被删的 `ChatService.kt` 无关，**必须保留**。
 */
class ChatServiceGroupChatFailLoudGuardTest {

    /**
     * 命名陷阱本身：`AppHiltModule` 里不得存在「名字带 ChatService、返回 ChatManager」
     * 的 `@Provides`。
     *
     * 这个方法原来就叫 `provideChatService`，返回的却是 `ChatManager`。按名字看注入点的人
     * 会以为拿到的是 `ChatService`，于是把 `core/service/ChatService.kt` 里那份无群聊感知
     * 的重复实现接上去 —— 这就是历史上那份死副本的由来。
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

    companion object {
        private const val DI_MODULE_FILE =
            "app/src/main/java/heizige/kk/khatkit/app/core/di/AppHiltModule.kt"

        /**
         * 去掉注释，只留代码。
         *
         * 这条护栏量的是「DI 模块的代码里有没有那个误导性的 provider 名」，不是
         * 「文档里有没有提过这个名字」—— 上面那段解释为什么它危险的话就在 KDoc 里。
         * 剥掉注释后，注释可以放心写全。
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