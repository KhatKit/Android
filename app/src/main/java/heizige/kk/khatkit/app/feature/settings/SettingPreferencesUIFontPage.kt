package heizige.kk.khatkit.app.feature.settings

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.ChatFontFamily
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.data.files.FileFolders
import heizige.kk.khatkit.app.core.data.files.FileHelper
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.settingAnchor
import heizige.kk.khatkit.app.core.ui.components.ui.sliderItem
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.theme.LocalKkThemeReveal
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.ui.theme.rememberChatFontFamily
import heizige.kk.khatkit.app.core.util.plus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.content.crossfadeOrChange
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.theme.MochiTheme
import java.io.File

@Composable
fun SettingPreferencesUIFontPage(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val displaySetting = settings.displaySetting
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatFontFamily = rememberChatFontFamily(displaySetting)
    val customFontFamily = rememberChatFontFamily(displaySetting.copy(chatFontFamily = ChatFontFamily.CUSTOM))
    val scroll = rememberLazyListState()
    val themeReveal = LocalKkThemeReveal.current
    val serifTitle = stringResource(R.string.setting_preferences_font_serif_title)
    val serifDescription = stringResource(R.string.setting_preferences_font_serif_desc)
    val fontFamilyTitle = stringResource(R.string.setting_display_page_chat_font_family_title)
    val fontOptions = ChatFontFamily.entries.map { it to it.label() }

    fun updateDisplaySetting(transform: (DisplaySetting) -> DisplaySetting) {
        vm.updateSettings { current -> current.copy(displaySetting = transform(current.displaySetting)) }
    }

    val importSuccessMsg = stringResource(R.string.setting_display_page_custom_font_import_success)
    val importFailedMsg = stringResource(R.string.setting_display_page_custom_font_import_failed)
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    importCustomChatFont(context, uri)
                }
            }.onSuccess { importedFont ->
                updateDisplaySetting {
                    it.copy(
                        chatFontFamily = ChatFontFamily.CUSTOM,
                        chatCustomFontPath = importedFont.relativePath,
                        chatCustomFontName = importedFont.displayName,
                    )
                }
                Toast.show(importSuccessMsg, isError = false)
            }.onFailure { error ->
                Toast.show(importFailedMsg.format(error.message.orEmpty()), isError = true)
            }
        }
    }

    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_page_preferences_ui_font),
        scrollUnderTopBar = true,
    ) { contentPadding ->
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 16.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
        ) {
            item(key = "titles") {
                CardGroup(title = { Text(stringResource(R.string.setting_preferences_font_titles)) }) {
                    mochiThemeSwitch(
                        title = serifTitle,
                        description = serifDescription,
                        checked = displaySetting.mochiSerifTitles,
                        anchor = settingAnchor(R.string.setting_preferences_font_serif_title),
                    ) { enabled ->
                        themeReveal.crossfadeOrChange {
                            updateDisplaySetting { it.copy(mochiSerifTitles = enabled) }
                        }
                    }
                }
            }
            item(key = "chat") {
                CardGroup(title = { Text(stringResource(R.string.setting_preferences_font_chat)) }) {
                    mochiThemePicker(
                        title = fontFamilyTitle,
                        options = fontOptions,
                        selected = displaySetting.chatFontFamily,
                        anchor = settingAnchor(R.string.setting_display_page_chat_font_family_title),
                        optionIcon = { family, tint ->
                            Text(
                                text = "Aa",
                                color = tint,
                                style = MochiTheme.textStyles.body.copy(
                                    fontFamily = when (family) {
                                        ChatFontFamily.DEFAULT -> FontFamily.Default
                                        ChatFontFamily.SERIF -> FontFamily.Serif
                                        ChatFontFamily.MONOSPACE -> FontFamily.Monospace
                                        ChatFontFamily.CUSTOM -> customFontFamily
                                    },
                                ),
                            )
                        },
                    ) { family ->
                        if (family == ChatFontFamily.CUSTOM && displaySetting.chatCustomFontPath.isBlank()) {
                            fontPickerLauncher.launch(CustomFontMimeTypes)
                        } else {
                            updateDisplaySetting { it.copy(chatFontFamily = family) }
                        }
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_custom_font_title)) },
                        supportingContent = {
                            Text(
                                text = displaySetting.chatCustomFontName.ifBlank {
                                    stringResource(R.string.setting_display_page_custom_font_not_imported)
                                },
                                style = MochiTheme.textStyles.footnote,
                                color = MochiTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MochiIconButton(
                                    onClick = { fontPickerLauncher.launch(CustomFontMimeTypes) },
                                    size = 44.dp,
                                ) {
                                    MochiIonicon(
                                        "cloud-upload",
                                        size = 20.dp,
                                        contentDescription = stringResource(
                                            R.string.setting_display_page_custom_font_import
                                        )
                                    )
                                }
                                if (displaySetting.chatCustomFontPath.isNotBlank()) {
                                    MochiIconButton(
                                        onClick = {
                                            deleteCustomChatFont(context, displaySetting.chatCustomFontPath)
                                            updateDisplaySetting { current ->
                                                current.copy(
                                                    chatFontFamily = if (current.chatFontFamily == ChatFontFamily.CUSTOM) {
                                                        ChatFontFamily.DEFAULT
                                                    } else {
                                                        current.chatFontFamily
                                                    },
                                                    chatCustomFontPath = "",
                                                    chatCustomFontName = "",
                                                )
                                            }
                                        },
                                        size = 44.dp,
                                    ) {
                                        MochiIonicon(
                                            "trash",
                                            size = 20.dp,
                                            tint = MochiTheme.colorScheme.error,
                                            contentDescription = stringResource(
                                                R.string.setting_display_page_custom_font_remove
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        anchor = settingAnchor(R.string.setting_display_page_custom_font_title),
                    )
                    sliderItem(
                        title = { stringResource(R.string.setting_display_page_font_size_title) },
                        value = displaySetting.fontSizeRatio,
                        onValueChange = { size -> updateDisplaySetting { it.copy(fontSizeRatio = size) } },
                        valueRange = 0.5f..2f,
                        defaultValue = 1.0f,
                        steps = 11,
                        displayScale = 100f,
                        suffix = "%",
                        anchor = settingAnchor(R.string.setting_display_page_font_size_title),
                    )
                }
            }
            item(key = "preview") {
                CardGroup(title = { Text(stringResource(R.string.setting_preferences_font_preview)) }) {
                    item(
                        headlineContent = {
                            val body = MochiTheme.textStyles.body
                            MarkdownBlock(
                                content = stringResource(R.string.setting_display_page_font_size_preview),
                                modifier = Modifier.padding(vertical = 4.dp),
                                style = body.copy(
                                    fontSize = body.fontSize * displaySetting.fontSizeRatio,
                                    lineHeight = body.lineHeight * displaySetting.fontSizeRatio,
                                    fontFamily = chatFontFamily,
                                    color = MochiTheme.colorScheme.onSurface,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 自定义聊天字体保存在 filesDir/<FileFolders.FONTS>。
// ---------------------------------------------------------------------------

internal val CustomFontMimeTypes = arrayOf(
    "font/*",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/vnd.ms-opentype",
    "application/octet-stream",
    "*/*",
)

internal val CustomFontExtensions = setOf("ttf", "otf", "ttc")

internal data class ImportedChatFont(
    val relativePath: String,
    val displayName: String,
)

@Composable
internal fun ChatFontFamily.label(): String = when (this) {
    ChatFontFamily.DEFAULT -> stringResource(R.string.setting_display_page_chat_font_family_default)
    ChatFontFamily.SERIF -> stringResource(R.string.setting_display_page_chat_font_family_serif)
    ChatFontFamily.MONOSPACE -> stringResource(R.string.setting_display_page_chat_font_family_monospace)
    ChatFontFamily.CUSTOM -> stringResource(R.string.setting_display_page_chat_font_family_custom)
}

internal fun importCustomChatFont(context: Context, uri: Uri): ImportedChatFont {
    val displayName = FileHelper.getFileNameFromUri(context, uri)?.takeIf { it.isNotBlank() } ?: "custom_font"
    val extension = displayName.substringAfterLast('.', "")
        .lowercase()
        .takeIf { it in CustomFontExtensions }
        ?: "ttf"
    val fontDir = File(context.filesDir, FileFolders.FONTS).apply { mkdirs() }
    val targetFile = File(fontDir, "chat_font.${System.currentTimeMillis()}.$extension")
    val tempFile = File(fontDir, "chat_font_import.tmp")

    try {
        tempFile.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open selected font")

        // 只有能被 Typeface 解析的文件才允许落盘
        runCatching {
            Typeface.createFromFile(tempFile)
        }.onFailure { error ->
            throw IllegalArgumentException(error.message ?: "Invalid font file", error)
        }

        replaceCustomChatFont(fontDir, tempFile, targetFile)
    } catch (error: Throwable) {
        tempFile.delete()
        throw error
    }

    val relativePath = FileHelper.getRelativePathInFilesDir(context.filesDir, targetFile)
        ?: "${FileFolders.FONTS}/${targetFile.name}"
    return ImportedChatFont(relativePath = relativePath, displayName = displayName)
}

/** 替换语义：旧的 chat_font.* 先备份，替换成功后再删备份；失败回滚，旧字体不丢。 */
internal fun replaceCustomChatFont(fontDir: File, tempFile: File, targetFile: File) {
    val existingFiles = fontDir.listFiles { file ->
        file.isFile && file.name.startsWith("chat_font.") && file != tempFile
    }?.toList().orEmpty()
    val backups = existingFiles.map { file ->
        file to File(fontDir, "previous_${file.name}").also { it.delete() }
    }

    try {
        backups.forEach { (file, backup) ->
            check(file.renameTo(backup)) { "Unable to prepare existing font for replacement" }
        }
        check(tempFile.renameTo(targetFile)) { "Unable to save selected font" }
        backups.forEach { (_, backup) -> backup.delete() }
    } catch (error: Throwable) {
        tempFile.delete()
        backups.forEach { (file, backup) ->
            if (!file.exists() && backup.exists()) {
                backup.renameTo(file)
            }
        }
        throw error
    }
}

/** 只删 filesDir 之内的那个文件。 */
internal fun deleteCustomChatFont(context: Context, relativePath: String) {
    val filesDir = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return
    val fontFile = runCatching { File(filesDir, relativePath).canonicalFile }.getOrNull() ?: return
    if (fontFile.path.startsWith("${filesDir.path}${File.separator}")) {
        fontFile.delete()
    }
}
