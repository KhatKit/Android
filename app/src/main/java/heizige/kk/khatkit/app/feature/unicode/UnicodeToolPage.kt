package heizige.kk.khatkit.app.feature.unicode

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.feature.unicode.helper.UNICODE_INPUT_LIMIT
import heizige.kk.khatkit.app.feature.unicode.helper.UnicodeInputError
import heizige.kk.khatkit.app.feature.unicode.helper.UnicodeInputException
import heizige.kk.khatkit.app.feature.unicode.helper.decodeUnicode
import heizige.kk.khatkit.app.feature.unicode.helper.toUtf16Escapes
import heizige.kk.khatkit.app.feature.unicode.helper.unicodeLabel
import heizige.kk.khatkit.app.feature.unicode.helper.unicodeScalars
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.theme.MochiTheme

@Composable
internal fun UnicodeToolPage() {
    MochiSettingsPageScaffold(title = "Unicode 工具") { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            UnicodeConvertPage()
        }
    }
}

@Composable
private fun UnicodeConvertPage() {
    var encode by rememberSaveable { mutableStateOf(true) }
    var text by rememberSaveable { mutableStateOf("") }
    var encoded by rememberSaveable { mutableStateOf("") }
    val source = if (encode) text else encoded
    val result = remember(encode, source) {
        runCatching {
            if (encode) listOf(
                "Unicode 码点" to source.unicodeScalars().joinToString(" ") { it.unicodeLabel() },
                "UTF-16 转义" to source.toUtf16Escapes(),
            ) else listOf("文本" to source.decodeUnicode())
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MochiChipGroup {
            MochiChip("文本转编码", encode, { encode = true })
            MochiChip("编码转文本", !encode, { encode = false })
        }
        MochiTextField(
            value = source,
            onValueChange = {
                if (it.length <= UNICODE_INPUT_LIMIT) {
                    if (encode) text = it else encoded = it
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("unicode_input"),
            placeholder = if (encode) "文本" else "U+4E2D U+1F600 或 \\u4E2D",
            singleLine = false,
            maxLines = 5,
        )
        (result.exceptionOrNull() as? UnicodeInputException)?.let { UnicodeError(it.error) }
        result.getOrNull().orEmpty().forEach { (label, value) ->
            if (value.isNotEmpty()) UnicodeOutput(label, value, monospace = encode)
        }
    }
}

@Composable
private fun UnicodeOutput(label: String, value: String, monospace: Boolean) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MochiTheme.textStyles.titleMedium)
            MochiIconButton(
                onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value))) } },
                style = MochiButtonStyle.Text,
            ) { MochiIonicon("copy", contentDescription = "复制$label") }
        }
        SelectionContainer {
            val style = MochiTheme.textStyles.bodyMedium
            Text(value, style = if (monospace) style.copy(fontFamily = FontFamily.Monospace) else style)
        }
    }
}

@Composable
private fun UnicodeError(error: UnicodeInputError) {
    Text(
        when (error) {
            UnicodeInputError.TOO_LONG -> "最多转换 512 个 Unicode 码点"
            UnicodeInputError.INVALID_ENCODING -> "编码格式无效"
            UnicodeInputError.INVALID_SCALAR -> "存在超出范围的码点或未配对的 UTF-16 代理项"
        },
        color = MochiTheme.colorScheme.error,
    )
}
