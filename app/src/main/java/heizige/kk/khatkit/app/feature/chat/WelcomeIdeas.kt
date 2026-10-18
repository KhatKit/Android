package heizige.kk.khatkit.app.feature.chat

/**
 * 欢迎页「试试这些」的 AI 开场提示：把一次模型调用拆成两段**纯函数**。
 *
 * 拆出来是为了两件事：
 * 1. prompt 与解析都能在 JVM 单测里钉死（模型爱回编号、引号、说明句，靠模型守规矩不可靠）；
 * 2. 生成逻辑（走哪颗模型、怎么兜底）留在 [ChatManager]，这里不碰任何状态。
 */
object WelcomeIdeas {

    /** 一屏显示几条。和 `ChatWelcome.kt` 里静态兜底取的条数保持一致。 */
    const val COUNT = 3

    /** 人设截断长度：prompt 要短、便宜，而且开场提示只需要角色的大致定位。 */
    private const val PERSONA_LIMIT = 300

    /** 标题长度上限：超过就不是「点一下就能发」的短句了。 */
    private const val TITLE_LIMIT = 40

    /** 说明行上限：它只是给标题补一句结果，行宽放不下更长的。 */
    private const val HINT_LIMIT = 40

    /**
     * 一条提示 = 标题 + 一句说明。
     *
     * 为什么非得有 [hint]：欢迎页的选项行是 `KedgeOptionItem`，两行（标题 + body 说明）的
     * 行高比只有标题的高出一截。AI 那批以前只有标题，静态兜底那批有说明，于是
     * 「生成出来之前 / 之后」整组的行距和行高会变一次 —— 生成完看着比之前「更挤」。
     * 现在两批都是两行，切换时行高不再跳。
     */
    data class Idea(val title: String, val hint: String)

    /**
     * 图标语义。真正的 `ImageVector` 在 UI 层映射（这里保持纯数据，单测不用碰 Compose）。
     *
     * 取值按「先做哪类事」分，而不是按内容分：模型写的标题五花八门，
     * 归到十来个槽位上比四条轮转更像有意图的图标。
     */
    enum class Icon {
        Explore,
        Write,
        Files,
        Schedule,
        Code,
        Search,
        Translate,
        Learn,
        Data,
        Money,
        Music,
        Video,
        Chat,
        Idea,
    }

    /**
     * 组 prompt。刻意用英文指令 + 「用 locale 的语言回答」：模型对英文格式约束更听话，
     * 而输出语言由 locale 决定，中文用户拿到的仍是中文短句。
     *
     * 一行一条、`标题 :: 说明` 用分隔符硬切 —— 让模型输出「标题一行、说明一行」的块
     * 看似更自然，但一旦它只给一行，解析就分不清「说明」和「下一条标题」。
     */
    fun prompt(
        localeTag: String,
        count: Int = COUNT,
        assistantName: String? = null,
        persona: String? = null,
    ): String = buildString {
        appendLine("You write starter prompts for the empty state of a chat app.")
        appendLine("Write $count concrete requests the user could send right now.")
        if (!assistantName.isNullOrBlank()) appendLine("Role: ${assistantName.trim()}")
        val trimmedPersona = persona?.trim()?.take(PERSONA_LIMIT).orEmpty()
        if (trimmedPersona.isNotBlank()) {
            appendLine("Role persona (may be empty):")
            appendLine(trimmedPersona)
        }
        appendLine("Format: exactly $count lines, every line is `<request> :: <hint>`.")
        appendLine("- request: what the user asks, starts with a verb, at most 16 CJK chars / 10 words")
        appendLine("- hint: a shorter phrase (at most 12 CJK chars / 6 words) naming the result")
        appendLine("- separate them with one ` :: ` and nothing else, e.g.")
        appendLine("整理本周会议纪要 :: 输出待办清单")
        appendLine("Rules:")
        appendLine("- no numbering, bullets, quotes, emoji, markdown or trailing punctuation")
        appendLine("- no explanation, no preamble, no headings")
        append(
            "Write in the language of locale \"${localeTag.ifBlank { "en" }}\" only, " +
                "never mix languages.",
        )
    }

    /**
     * 解析模型输出。宁可少给也不给脏数据：不足 [COUNT] 条或少于 2 条时返回空列表，
     * 调用方据此退回内置静态提示 —— 三条里混一条「Sure! Here are 3 ideas:」比全静态更糟。
     *
     * 没有 `::` 的行直接丢：它要么是引导句，要么是模型没守格式。拿它当标题会重新引入
     * 「有的行两行、有的行一行」的行高抖动。
     */
    fun parse(raw: String, count: Int = COUNT): List<Idea> {
        val ideas = raw.lineSequence()
            .map(::cleanLine)
            .filter { it.isNotBlank() }
            // 「Sure, here are 3 ideas:」这类引导句以冒号收尾，不是可点的提示。
            .filterNot { it.endsWith(':') || it.endsWith('：') }
            .mapNotNull(::parseLine)
            .distinctBy { it.title }
            .take(count)
            .toList()
        return if (ideas.size >= 2) ideas else emptyList()
    }

    /** 标题 + 说明都按关键词归类；一个都没命中时落到 [Icon.Idea]（灯泡）。 */
    fun icon(idea: Idea): Icon {
        val haystack = "${idea.title} ${idea.hint}".lowercase()
        return ICON_KEYWORDS.firstOrNull { (_, keywords) -> keywords.any(haystack::contains) }?.first
            ?: Icon.Idea
    }

    /** 一行 `标题 :: 说明`。分隔符容忍模型写成全角竖线 / 两个短横 / 制表符。 */
    private fun parseLine(line: String): Idea? {
        val parts = SEPARATOR.split(line, limit = 2)
        if (parts.size < 2) return null
        val title = parts[0].trimTrailingPunctuation()
        val hint = parts[1].trimTrailingPunctuation()
        if (title.isBlank() || title.length > TITLE_LIMIT) return null
        if (hint.isBlank() || hint.length > HINT_LIMIT) return null
        return Idea(title, hint)
    }

    /** 去行首编号/项目符号 —— 模型最常犯的脏。 */
    private fun cleanLine(line: String): String = line.trim()
        .trimStart('-', '*', '•', '·', '>', '#', ' ', '\t')
        .replace(NUMBERING, "")
        .trim()

    /** 去包裹引号 + 尾部标点。 */
    private fun String.trimTrailingPunctuation(): String =
        trim().trim('"', '「', '」', '“', '”', '`', '\'', '。', '，', ',', '.', ';', '；', '、')

    /** `1.` / `1)` / `(1)` / `一、` 这类行首编号。 */
    private val NUMBERING = Regex("""^(?:\(\d{1,2}\)|\d{1,2}\s*[.)、:：]|[一二三四五六七八九十]{1,2}\s*[、.)])\s*""")

    /** 标题与说明的分隔符：`::` 是约定的那个，其余是模型的常见漂移。 */
    private val SEPARATOR = Regex("""\s*(?:::|[|｜]|\t|—{2,}|--)\s*""")

    /**
     * 关键词 → 图标。顺序即优先级：越具体的意图排越前，
     * 免得「写一段查询代码」被「写」抢走（它该是代码图标）。
     */
    private val ICON_KEYWORDS: List<Pair<Icon, List<String>>> = listOf(
        Icon.Translate to listOf("翻译", "译成", "translate", "translation"),
        Icon.Code to listOf("代码", "脚本", "函数", "正则", "报错", "调试", "code", "script", "sql", "regex", "debug", "bug", "api"),
        Icon.Data to listOf("数据", "统计", "表格", "图表", "报表", "分析", "data", "table", "chart", "analytics", "csv", "spreadsheet"),
        Icon.Files to listOf("文件", "文件夹", "文档", "归档", "重命名", "file", "folder", "document", "organize", "rename"),
        Icon.Money to listOf("价格", "账单", "发票", "预算", "支出", "报销", "price", "bill", "invoice", "budget", "expense"),
        Icon.Music to listOf("音乐", "歌曲", "歌单", "music", "song", "playlist"),
        Icon.Video to listOf("视频", "字幕", "剪辑", "video", "subtitle", "clip"),
        Icon.Schedule to listOf("日程", "提醒", "计划", "安排", "待办", "日历", "会议", "schedule", "remind", "plan", "todo", "calendar", "meeting"),
        Icon.Explore to listOf("旅行", "路线", "去哪", "攻略", "附近", "推荐", "travel", "route", "trip", "nearby", "recommend"),
        Icon.Search to listOf("搜索", "查找", "搜一下", "查一下", "检索", "search", "find", "look up", "google"),
        Icon.Learn to listOf("解释", "讲解", "教我", "为什么", "原理", "科普", "explain", "teach", "learn", "why"),
        Icon.Chat to listOf("聊天", "对话", "闲聊", "chat", "conversation"),
        Icon.Write to listOf("写", "起草", "润色", "改写", "总结", "摘要", "邮件", "文案", "报告", "write", "draft", "rewrite", "summar", "email", "polish", "report"),
        Icon.Idea to listOf("点子", "创意", "头脑风暴", "起名", "idea", "brainstorm", "name"),
    )
}
