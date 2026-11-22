package heizige.kk.khatkit.app.feature.unicode.helper

import java.util.Locale

internal const val UNICODE_INPUT_LIMIT = 8192
private const val SCALAR_LIMIT = 512
private val TokenSeparator = Regex("[\\s,，]+")
private val CodePointToken = Regex("(?:U\\+|0x)?([0-9a-f]{1,6})", RegexOption.IGNORE_CASE)
private val Utf16Escape = Regex("\\\\u([0-9a-fA-F]{4})")

internal enum class UnicodeInputError { TOO_LONG, INVALID_ENCODING, INVALID_SCALAR }

internal class UnicodeInputException(val error: UnicodeInputError) : IllegalArgumentException()

internal fun String.unicodeScalars(): List<Int> {
    if (length > UNICODE_INPUT_LIMIT) throw UnicodeInputException(UnicodeInputError.TOO_LONG)
    return buildList {
        var offset = 0
        while (offset < length) {
            val point = codePointAt(offset)
            if (!point.isUnicodeScalar()) throw UnicodeInputException(UnicodeInputError.INVALID_SCALAR)
            if (size == SCALAR_LIMIT) throw UnicodeInputException(UnicodeInputError.TOO_LONG)
            add(point)
            offset += Character.charCount(point)
        }
    }
}

internal fun String.decodeUnicode(): String {
    if (length > UNICODE_INPUT_LIMIT) throw UnicodeInputException(UnicodeInputError.TOO_LONG)
    val input = trim()
    if (input.isEmpty()) return ""
    if (input.startsWith("\\u")) {
        val decoded = buildString {
            var offset = 0
            while (offset < input.length) {
                if (input[offset].isWhitespace() || input[offset] == ',' || input[offset] == '，') {
                    offset++
                    continue
                }
                val match = Utf16Escape.matchAt(input, offset)
                    ?: throw UnicodeInputException(UnicodeInputError.INVALID_ENCODING)
                append(match.groupValues[1].toInt(16).toChar())
                offset = match.range.last + 1
            }
        }
        decoded.unicodeScalars() // Adjacent UTF-16 escapes may form a supplementary code point.
        return decoded
    }
    return buildString {
        input.splitToSequence(TokenSeparator).filter { it.isNotEmpty() }.forEachIndexed { index, token ->
            if (index == SCALAR_LIMIT) throw UnicodeInputException(UnicodeInputError.TOO_LONG)
            val match = CodePointToken.matchEntire(token)
                ?: throw UnicodeInputException(UnicodeInputError.INVALID_ENCODING)
            val point = match.groupValues[1].toInt(16)
            if (!point.isUnicodeScalar()) throw UnicodeInputException(UnicodeInputError.INVALID_SCALAR)
            appendCodePoint(point)
        }
        if (isEmpty()) throw UnicodeInputException(UnicodeInputError.INVALID_ENCODING)
    }
}

internal fun Int.unicodeLabel(): String = "U+${toString(16).uppercase(Locale.ROOT).padStart(4, '0')}"

internal fun String.toUtf16Escapes(): String = buildString(length * 6) {
    for (unit in this@toUtf16Escapes) {
        append("\\u")
        append(unit.code.toString(16).uppercase(Locale.ROOT).padStart(4, '0'))
    }
}

private fun Int.isUnicodeScalar() = this in 0..Character.MAX_CODE_POINT && this !in 0xD800..0xDFFF
