package heizige.kk.khatkit.app.feature.unicode.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UnicodeExtensionsTest {
    @Test
    fun encodesScalarsAndUtf16Separately() {
        val text = "\u4E2D\uD83D\uDE00\u0301"
        assertEquals(listOf("U+4E2D", "U+1F600", "U+0301"), text.unicodeScalars().map(Int::unicodeLabel))
        assertEquals("\\u4E2D\\uD83D\\uDE00\\u0301", text.toUtf16Escapes())
    }

    @Test
    fun decodesPointAndEscapeFormats() {
        val expected = "\u4E2D\uD83D\uDE00 A"
        assertEquals(expected, "U+4e2d,0x1F600\n0020，U+0041".decodeUnicode())
        assertEquals(expected, "\\u4E2D \\uD83D\\uDE00\\u0020\\u0041".decodeUnicode())
        assertEquals(listOf(0, 0x10FFFF), "U+0000 U+10FFFF".decodeUnicode().unicodeScalars())
    }

    @Test
    fun rejectsBrokenPairsAndNonScalars() {
        assertError(UnicodeInputError.INVALID_SCALAR) { "\\uD800".decodeUnicode() }
        assertError(UnicodeInputError.INVALID_SCALAR) { "U+D83D U+DE00".decodeUnicode() }
        assertError(UnicodeInputError.INVALID_SCALAR) { "U+110000".decodeUnicode() }
        assertError(UnicodeInputError.INVALID_SCALAR) { "\uDC00".unicodeScalars() }
    }

    @Test
    fun rejectsInvalidTokensAndOversizedInputs() {
        assertError(UnicodeInputError.INVALID_ENCODING) { "\\u12".decodeUnicode() }
        assertError(UnicodeInputError.INVALID_ENCODING) { "U+nothex".decodeUnicode() }
        assertError(UnicodeInputError.TOO_LONG) { "41 ".repeat(513).decodeUnicode() }
        assertError(UnicodeInputError.TOO_LONG) { "a".repeat(513).unicodeScalars() }
        assertEquals(512, "\uD83D\uDE00".repeat(512).unicodeScalars().size)
    }

    private fun assertError(error: UnicodeInputError, block: () -> Unit) {
        assertEquals(error, assertThrows(UnicodeInputException::class.java) { block() }.error)
    }
}
