package heizige.kk.khatkit.app.feature.unicode.helper

internal enum class UnicodeGlyphKind { VISIBLE, MARK, SPACE, CONTROL, UNASSIGNED }

internal fun Int.unicodeGlyphKind(): UnicodeGlyphKind = when (Character.getType(this)) {
    Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
    Character.ENCLOSING_MARK.toInt() -> UnicodeGlyphKind.MARK
    Character.SPACE_SEPARATOR.toInt(), Character.LINE_SEPARATOR.toInt(),
    Character.PARAGRAPH_SEPARATOR.toInt() -> UnicodeGlyphKind.SPACE
    Character.CONTROL.toInt(), Character.FORMAT.toInt() -> UnicodeGlyphKind.CONTROL
    Character.UNASSIGNED.toInt() -> UnicodeGlyphKind.UNASSIGNED
    else -> UnicodeGlyphKind.VISIBLE
}
