package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 欢迎页 AI 提示的解析/组 prompt 契约。
 *
 * 这两段是纯函数，也是**唯一**能防住「模型不守规矩」的地方：
 * 解析宽一点就会把「Sure, here are 3 ideas:」当成一条可点的提示；
 * 解析严一点就整批退回静态文案 —— 所以断言都钉在「脏行不会被当成提示」上。
 *
 * 2026-10 追加两条硬约定：**每条都必须是「标题 :: 说明」**（否则整条丢掉）。
 * 原因是欢迎页选项行是两行高的，只有标题的那条会比别的矮一截，
 * 生成前后整组行高会跳。
 */
class WelcomeIdeasTest {

    private fun idea(title: String, hint: String) = WelcomeIdeas.Idea(title, hint)

    @Test
    fun parse_readsTitleAndHint() {
        val raw = """
            整理本周会议纪要 :: 输出待办清单
            把这个网页压缩成三条要点 :: 只留结论
            读一下这个 PDF 并挑出风险 :: 标出红线条款
        """.trimIndent()
        assertEquals(
            listOf(
                idea("整理本周会议纪要", "输出待办清单"),
                idea("把这个网页压缩成三条要点", "只留结论"),
                idea("读一下这个 PDF 并挑出风险", "标出红线条款"),
            ),
            WelcomeIdeas.parse(raw),
        )
    }

    @Test
    fun parse_stripsNumberingBulletsAndQuotes() {
        val raw = """
            1. 帮我写一封请假邮件 :: 「一封 100 字邮件」
            2) 把这个表格转成 CSV :: 保留表头
            - 用一句话总结这段话 :: `不超过 20 字`
            * 把截图里的数字抄出来 :: 逐行列出
        """.trimIndent()
        assertEquals(
            listOf(
                idea("帮我写一封请假邮件", "一封 100 字邮件"),
                idea("把这个表格转成 CSV", "保留表头"),
                idea("用一句话总结这段话", "不超过 20 字"),
                idea("把截图里的数字抄出来", "逐行列出"),
            ),
            WelcomeIdeas.parse(raw, count = 4),
        )
    }

    @Test
    fun parse_dropsPreambleAndLinesWithoutSeparator() {
        // 模型爱先来一句「Sure, here are 3 ideas:」——这句不能变成可点的提示。
        // 没带 `::` 的行一律丢：它是引导句，或者模型没守格式。
        val raw = """
            Sure, here are 3 ideas:
            帮我整理收件箱 :: 按发件人分组
            把这段英文翻成中文
            总结这个链接 :: 三条要点
        """.trimIndent()
        assertEquals(
            listOf(idea("帮我整理收件箱", "按发件人分组"), idea("总结这个链接", "三条要点")),
            WelcomeIdeas.parse(raw),
        )
    }

    @Test
    fun parse_toleratesAlternateSeparators() {
        // 全角竖线 / 半角竖线 / 两个短横都是模型常见漂移，按同一个分隔符处理。
        val raw = "拆解这份需求 ｜ 列成任务\n列出依赖--标出先后"
        assertEquals(
            listOf(idea("拆解这份需求", "列成任务"), idea("列出依赖", "标出先后")),
            WelcomeIdeas.parse(raw),
        )
    }

    @Test
    fun parse_needsAtLeastTwoUsableItems() {
        // 只剩一条就整批作废：一条 AI 提示 + 两条静态文案混排比全静态更割裂。
        assertEquals(emptyList<WelcomeIdeas.Idea>(), WelcomeIdeas.parse("只有这一条能用 :: 说明"))
        assertEquals(emptyList<WelcomeIdeas.Idea>(), WelcomeIdeas.parse(""))
        assertEquals(emptyList<WelcomeIdeas.Idea>(), WelcomeIdeas.parse("   \n\n  "))
    }

    @Test
    fun parse_requiresHintSoRowHeightsMatch() {
        // 缺说明的那条要整条丢掉，而不是渲染成矮一行的选项。
        val raw = """
            帮我做第一件事 :: 结果一
            帮我做第二件事
            帮我做第三件事 :: 结果三
        """.trimIndent()
        assertEquals(
            listOf(idea("帮我做第一件事", "结果一"), idea("帮我做第三件事", "结果三")),
            WelcomeIdeas.parse(raw),
        )
    }

    @Test
    fun parse_capsAtRequestedCountAndDedupes() {
        val raw = (1..6).joinToString("\n") { "帮我做第 $it 件事 :: 结果 $it" }
        assertEquals(3, WelcomeIdeas.parse(raw).size)
        assertEquals(2, WelcomeIdeas.parse(raw, count = 2).size)
        assertEquals(
            2,
            WelcomeIdeas.parse("帮我做同一件事 :: 甲\n帮我做同一件事 :: 乙\n帮我做另一件事 :: 丙").size,
        )
    }

    @Test
    fun parse_dropsOverlongLines() {
        val longTitle = "帮我" + "把这段非常长的内容".repeat(6)
        val ideas = WelcomeIdeas.parse("$longTitle :: 总结\n总结这个链接 :: 三条要点\n列出待办清单 :: 按优先级")
        assertTrue(ideas.none { it.title == longTitle })
    }

    @Test
    fun prompt_pinsCountLocaleRoleAndFormat() {
        val prompt = WelcomeIdeas.prompt(
            localeTag = "zh-CN",
            count = 3,
            assistantName = "默认角色",
            persona = "你是一个严谨的助理。",
        )
        assertTrue(prompt.contains("Write 3 concrete requests"))
        assertTrue(prompt.contains("\"zh-CN\""))
        assertTrue(prompt.contains("默认角色"))
        assertTrue(prompt.contains("你是一个严谨的助理。"))
        // 分隔符格式必须写进 prompt，否则解析层会把所有行都丢掉。
        assertTrue(prompt.contains("::"))
    }

    @Test
    fun prompt_truncatesPersonaAndFallsBackToEnglishLocale() {
        val prompt = WelcomeIdeas.prompt(
            localeTag = "",
            count = 2,
            assistantName = null,
            persona = "人设".repeat(400),
        )
        assertTrue(prompt.contains("\"en\""))
        assertTrue(prompt.contains("Write 2 concrete requests"))
        assertTrue(prompt.length < 1_800)
    }

    @Test
    fun icon_matchesByKeyword() {
        assertEquals(WelcomeIdeas.Icon.Search, WelcomeIdeas.icon(idea("搜索最新的论文", "只给标题")))
        assertEquals(WelcomeIdeas.Icon.Code, WelcomeIdeas.icon(idea("写一段查询代码", "带注释")))
        assertEquals(WelcomeIdeas.Icon.Translate, WelcomeIdeas.icon(idea("把这段英文翻译成中文", "保留术语")))
        assertEquals(WelcomeIdeas.Icon.Schedule, WelcomeIdeas.icon(idea("排一下本周日程", "避开会议")))
        assertEquals(WelcomeIdeas.Icon.Data, WelcomeIdeas.icon(idea("summarize this csv", "three rows")))
    }

    @Test
    fun icon_fallsBackToIdea() {
        assertEquals(WelcomeIdeas.Icon.Idea, WelcomeIdeas.icon(idea("随便聊两句", "轻松一点")))
    }
}
