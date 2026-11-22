package heizige.kk.khatkit.app.feature.unicode

import android.icu.lang.UCharacter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.feature.unicode.helper.UNICODE_INPUT_LIMIT
import heizige.kk.khatkit.app.feature.unicode.helper.UnicodeGlyphKind
import heizige.kk.khatkit.app.feature.unicode.helper.UnicodeInputException
import heizige.kk.khatkit.app.feature.unicode.helper.decodeUnicode
import heizige.kk.khatkit.app.feature.unicode.helper.unicodeGlyphKind
import heizige.kk.khatkit.app.feature.unicode.helper.unicodeLabel
import heizige.kk.khatkit.app.feature.unicode.helper.unicodeScalars
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.Text
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.theme.MochiTheme

@Composable
internal fun UnicodeGlyphsPage() {
    var fromCode by rememberSaveable { mutableStateOf(false) }
    var characters by rememberSaveable { mutableStateOf("") }
    var codes by rememberSaveable { mutableStateOf("") }
    val input = if (fromCode) codes else characters
    val result = remember(input, fromCode) {
        runCatching {
            val text = if (fromCode) input.decodeUnicode() else input
            text to text.unicodeScalars()
        }
    }
    val preview = result.getOrNull()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            MochiChipGroup {
                MochiChip("按字符", !fromCode, { fromCode = false })
                MochiChip("按码点", fromCode, { fromCode = true })
            }
        }
        item {
            MochiTextField(
                value = input,
                onValueChange = {
                    if (it.length <= UNICODE_INPUT_LIMIT) {
                        if (fromCode) codes = it else characters = it
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("unicode_glyph_input"),
                placeholder = if (fromCode) "U+4E2D U+1F600" else "字符",
                singleLine = false,
                maxLines = 4,
            )
        }
        (result.exceptionOrNull() as? UnicodeInputException)?.let { error ->
            item { UnicodeError(error.error) }
        }
        if (preview != null && preview.second.isNotEmpty()) {
            item { UnicodeOutput("序列预览", preview.first, monospace = false) }
            itemsIndexed(preview.second, key = { index, _ -> index }) { _, code ->
                UnicodeGlyphRow(code)
            }
        }
    }
}

@Composable
private fun UnicodeGlyphRow(code: Int) {
    val (glyph, category) = remember(code) {
        val character = String(Character.toChars(code))
        when (code.unicodeGlyphKind()) {
            UnicodeGlyphKind.VISIBLE -> character to null
            UnicodeGlyphKind.MARK -> "\u25CC$character" to "组合标记"
            UnicodeGlyphKind.SPACE -> null to "空白字符"
            UnicodeGlyphKind.CONTROL -> null to "不可见字符"
            UnicodeGlyphKind.UNASSIGNED -> null to "未分配码点"
        }
    }
    val name = remember(code) { UCharacter.getName(code) }
    SelectionContainer {
        MochiListRow(
            title = code.unicodeLabel(),
            subtitle = listOfNotNull(name, category).joinToString(" · ").ifEmpty { null },
            leading = if (glyph == null) null else ({ Text(glyph, style = MochiTheme.textStyles.titleLarge) }),
        )
    }
}
