package heizige.kk.khatkit.app.core.util

/**
 * 移除字符串中的 Markdown / HTML 格式，得到用于朗读的纯文本。
 *
 * 与上游 db4982283（tts: 修复朗读时把 HTML 标签一并读出）行为对齐：
 * - 先剥 HTML 标签/实体/注释/style/script，再走 Markdown 清理；
 * - 标签识别用「已知标签名 + 其后必须跟空白/斜杠/尖括号」的保守规则，
 *   因此 x<y && y>0、<p@example.com> 这类正文不会被当成标签删掉；
 * - 实现在**剥离之后**解码实体，&lt;tag&gt; 解出的文字不会被二次当成标签；
 * - 代码块、行内代码、图片不朗读；链接保留文字。
 *
 * 注意：输出已是解码后的纯文本，不要再调用一次；TTS 侧只在入口清洗一次。
 * @return 移除 Markdown/HTML 格式后的纯文本
 */
fun String.stripMarkdown(): String {
    return this
        .stripHtmlForSpeech()
        // 代码块与行内代码不朗读
        .replace(Regex("```[\\s\\S]*?```"), "")
        .replace(Regex("`[^`]*?`"), "")
        // 图片整段丢弃（alt 也不朗读）
        .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "")
        // 链接保留文字
        .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
        // 加粗/斜体/删除线/下划线
        .replace(Regex("\\*\\*([^*]+?)\\*\\*"), "$1")
        .replace(Regex("\\*([^*]+?)\\*"), "$1")
        .replace(Regex("__([^_]+?)__"), "$1")
        .replace(Regex("_([^_]+?)_"), "$1")
        .replace(Regex("~~([^~]+?)~~"), "$1")
        // 标题/列表/引用/分割线
        .replace(Regex("(?m)^#+\\s*"), "")
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        .replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
        .replace(Regex("(?m)^>\\s*"), "")
        .replace(Regex("(?m)^(\\s*[-*_]){3,}\\s*$"), "")
        // 去空白：保留段落，压缩行内连续空格
        .replace(Regex("[ \\t]*\\n\\s*"), "\n")
        .replace(Regex("[ \\t]{2,}"), " ")
        .trim()
}

/** HTML 行内标签（保守白名单 + 边界要求，避免误伤正文里的 < 与 >）。 */
private const val HTML_INLINE_TAGS =
    "a|b|i|u|s|em|strong|span|code|img|sub|sup|mark|del|ins|font|small|big|hr|" +
        "svg|path|g|circle|rect|line|polygon|polyline|text|tspan|math|" +
        "iframe|video|audio|source|input|button|label|select|option"

private const val HTML_BLOCK_TAGS =
    "p|div|li|tr|h[1-6]|details|summary|blockquote|pre|table|thead|tbody|" +
        "ul|ol|figure|figcaption|section|article|header|footer"

private fun String.stripHtmlForSpeech(): String {
    var text = this
    // 注释、style/script 整段丢弃
    text = text.replace(Regex("(?is)<!--.*?-->"), "")
    text = text.replace(Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>"), "")
    // 换行类标签 → 换行；其余块标签 → 换行
    text = text.replace(Regex("(?is)<br\\s*/?>"), "\n")
    text = text.replace(
        Regex("(?is)</?(?:$HTML_BLOCK_TAGS)(?=[\\s/>])[^>]*>"),
        "\n",
    )
    // 行内标签直接删除
    text = text.replace(
        Regex("(?is)</?(?:$HTML_INLINE_TAGS)(?=[\\s/>])[^>]*>"),
        "",
    )
    return text.decodeHtmlEntities()
}

/** 只解码常见实体与数字实体；顺序保证 &amp;lt; 只解一层。 */
private fun String.decodeHtmlEntities(): String {
    if ('&' !in this) return this
    var text = this
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", 34.toChar().toString())
        .replace("&#39;", 39.toChar().toString())
        .replace("&apos;", 39.toChar().toString())
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
    text = Regex("&#(\\d{1,7});").replace(text) { m ->
        m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
    }
    text = Regex("&#[xX]([0-9a-fA-F]{1,6});").replace(text) { m ->
        m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
    }
    return text
}

fun String.extractThinkingTitle(): String? {
    // 按行分割文本
    val lines = this.lines()

    // 从后往前查找最后一个符合条件的加粗文本行
    for (i in lines.indices.reversed()) {
        val line = lines[i].trim()

        // 检查是否为加粗格式且独占一整行
        val boldPattern = Regex("^\\*\\*(.+?)\\*\\*$")
        val match = boldPattern.find(line)

        if (match != null) {
            // 返回加粗标记内的文本内容
            return match.groupValues[1].trim().takeUnless { it.isBlank() }
        }
    }

    return null
}
