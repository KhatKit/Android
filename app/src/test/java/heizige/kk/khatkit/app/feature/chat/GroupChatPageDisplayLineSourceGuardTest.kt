package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `GroupChatPage.kt` 里两条重复展示行的护栏。
 *
 * ## 这条护栏防的是什么
 *
 * 群配置校验错误与角色卡摘要在本文件各有**两条**展示路径，且四条路径的文案与样式逐字相同：
 *
 * | 展示行 | 路径一 | 路径二 |
 * |---|---|---|
 * | `· <field>：<message>` | `GroupConfigSheet` 保存后的 `saveErrors` | `ImportResultView` 里 `Rejected` 的 `result.fieldErrors` |
 * | `· <name>（<role_id>）· 含 persona` | `ImportedRoleCardsView`（已落库的导入快照） | `ImportResultView` 里 `Accepted` 的 `payload.cards` |
 *
 * 四条路径此前各内联了一份 [androidx.compose.material3.Text]。抄副本的直接后果不是编译错误
 * 而是**只改到一半**：把错误措辞或角色卡那行的「含 persona」标记改掉时，四条里只改了两条，
 * 同一份文件在「保存被拒」和「导入被拒」两处显示不同的话术。已收敛成
 * `GroupConfigErrorLine` / `RoleCardLine` 两个私有 composable，各一份。
 *
 * 文案护栏而非渲染快照测试：这里要钉的是「同一段文案在源码里只有一份」，不是「渲染成什么样」
 * （后者已有截图 / 仪器测试的地基，而这里是纯文本行，抽成 composable 后渲染逐字不变）。
 *
 * 分工：
 * - [errorLine_isWrittenExactlyOnce_andBothPathsCallIt] —— 校验错误行唯一 + 两条路径都在用
 * - [roleCardLine_isWrittenExactlyOnce_andBothPathsCallIt] —— 角色卡行同理
 */
class GroupChatPageDisplayLineSourceGuardTest {

    // ---------- 1. 校验错误行 ----------

    @Test
    fun errorLine_isWrittenExactlyOnce_andBothPathsCallIt() {
        val source = code(File(repoRoot(), GROUP_CHAT_PAGE).readText())

        assertEquals(
            "$GROUP_CHAT_PAGE 里校验错误行只允许有一份。文案 $ERROR_TEXT_TEMPLATE 只应在 " +
                "GroupConfigErrorLine() 里出现一次；另一条展示路径（导入被拒的 " +
                "result.fieldErrors）请改成调用它，不要就地再抄一份 Text —— 抄出来的副本" +
                "不会跟着改措辞/配色，同一份文件会在「保存被拒」和「导入被拒」两处显示不同话术。" +
                "现在出现 ${count(source, ERROR_TEXT_TEMPLATE)} 次。",
            1,
            count(source, ERROR_TEXT_TEMPLATE),
        )
        assertEquals(
            "$GROUP_CHAT_PAGE 里 GroupConfigErrorLine 应恰好是「1 处定义 + 2 处调用」",
            3,
            count(source, "GroupConfigErrorLine("),
        )
        for (path in listOf("saveErrors", "result.fieldErrors")) {
            assertTrue(
                "$GROUP_CHAT_PAGE 的 $path 这条展示路径必须调 GroupConfigErrorLine(",
                Regex("""$path\.forEach\s*\{\s*error\s*->\s*GroupConfigErrorLine\s*\(\s*error\s*\)\s*}""")
                    .containsMatchIn(source),
            )
        }
    }

    // ---------- 2. 角色卡摘要行 ----------

    @Test
    fun roleCardLine_isWrittenExactlyOnce_andBothPathsCallIt() {
        val source = code(File(repoRoot(), GROUP_CHAT_PAGE).readText())

        assertEquals(
            "$GROUP_CHAT_PAGE 里角色卡摘要行只允许有一份。文案骨架 $ROLE_CARD_TEXT_TEMPLATE 只应在 " +
                "RoleCardLine() 里出现一次；另一条展示路径（当场导入结果的 payload.cards）" +
                "请改成调用它，不要就地再抄一份 Text。现在出现 " +
                "${count(source, ROLE_CARD_TEXT_TEMPLATE)} 次。",
            1,
            count(source, ROLE_CARD_TEXT_TEMPLATE),
        )
        assertEquals(
            "$GROUP_CHAT_PAGE 里 RoleCardLine 应恰好是「1 处定义 + 2 处调用」",
            3,
            count(source, "RoleCardLine("),
        )
        for (path in listOf("cards", "payload.cards")) {
            assertTrue(
                "$GROUP_CHAT_PAGE 的 $path 这条展示路径必须调 RoleCardLine(",
                Regex("""${Regex.escape(path)}\.forEach\s*\{\s*card\s*->\s*RoleCardLine\s*\(\s*card\s*\)\s*}""")
                    .containsMatchIn(source),
            )
        }
    }

    companion object {
        private const val GROUP_CHAT_PAGE =
            "app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupChatPage.kt"

        /** 校验错误行的 `text = ` 右边整段字面量（含 Kotlin 模板占位符）。 */
        private const val ERROR_TEXT_TEMPLATE = """text = "· ${'$'}{error.field}：${'$'}{error.message}","""

        /** 角色卡摘要行的骨架：`name` 空则回落到 `role_id`。 */
        private const val ROLE_CARD_TEXT_TEMPLATE = "card.name.ifBlank { card.roleId }"

        /** 数一段字面量在剥掉注释后的代码里出现几次（非重叠计数）。 */
        private fun count(source: String, needle: String): Int {
            var n = 0
            var i = source.indexOf(needle)
            while (i >= 0) {
                n++
                i = source.indexOf(needle, i + needle.length)
            }
            return n
        }

        /**
         * 去掉注释，只留代码，**并保持行数不变**。
         *
         * 两个 composable 的 KDoc 都复述了旧写法（`· <field>：<message>`、名字回落 `role_id`
         * 这些字面量都在注释里），不剥注释时它们会被当成真命中 —— 护栏因为解释它自己而变红。
         * 块注释用等量空格回填而不是折成一行，好让失败信息里的行号仍与真实源码对齐。
         */
        private fun code(source: String): String = source
            .replace(BLOCK_COMMENT) { m -> m.value.replace(NON_NEWLINE, " ") }
            .replace(LINE_COMMENT, " ")

        private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        private val LINE_COMMENT = Regex("""//[^\n]*""")
        private val NON_NEWLINE = Regex("""[^\n]""")

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
