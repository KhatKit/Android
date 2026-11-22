package heizige.kk.khatkit.app.core.data.db.fts

import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException

enum class MessageSearchMode { KEYWORDS, REGEX, ANY_TEXT }

internal fun interface MessageTextQuery {
    fun find(text: String): IntRange?

    companion object {
        fun compile(input: String, mode: MessageSearchMode): MessageTextQuery? {
            require(input.length <= 512) { "搜索内容不能超过 512 个字符" }
            if (input.isBlank()) return null
            return when (mode) {
                MessageSearchMode.KEYWORDS -> null
                MessageSearchMode.REGEX -> {
                    val pattern = try {
                        Pattern.compile(input)
                    } catch (_: PatternSyntaxException) {
                        throw IllegalArgumentException("正则表达式无效或包含不支持的语法")
                    }
                    MessageTextQuery { text ->
                        val match = pattern.matcher(text)
                        if (match.find()) match.start() until match.end() else null
                    }
                }
                MessageSearchMode.ANY_TEXT -> {
                    val terms = input.split(',', '，', '\n', '\r').map(String::trim)
                        .filter(String::isNotEmpty).distinct()
                    if (terms.isEmpty()) return null
                    require(terms.size <= 16) { "最多搜索 16 段文本" }
                    MessageTextQuery { text ->
                        terms.mapNotNull { term ->
                            text.indexOf(term, ignoreCase = true).takeIf { it >= 0 }?.let { it until it + term.length }
                        }.minByOrNull(IntRange::first)
                    }
                }
            }
        }
    }
}

internal fun messageTextSnippet(text: String, match: IntRange): String {
    // Keep snippets compact even when the pattern itself matches the entire message.
    val start = (match.first - 36).coerceAtLeast(0)
    val end = (match.first + 104).coerceAtMost(text.length)
    return buildString {
        if (start > 0) append('…')
        append(text.substring(start, end))
        if (end < text.length) append('…')
    }
}
