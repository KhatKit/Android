package heizige.kk.khatkit.app.feature.unicode.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class UnicodeGlyphExtensionsTest {
    @Test
    fun distinguishesVisibleCombiningAndInvisibleCharacters() {
        assertEquals(UnicodeGlyphKind.VISIBLE, 0x1F600.unicodeGlyphKind())
        assertEquals(UnicodeGlyphKind.MARK, 0x0301.unicodeGlyphKind())
        assertEquals(UnicodeGlyphKind.SPACE, 0x0020.unicodeGlyphKind())
        assertEquals(UnicodeGlyphKind.CONTROL, 0x000A.unicodeGlyphKind())
        assertEquals(UnicodeGlyphKind.CONTROL, 0x200D.unicodeGlyphKind())
        assertEquals(UnicodeGlyphKind.UNASSIGNED, 0x0378.unicodeGlyphKind())
    }
}
