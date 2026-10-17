package heizige.kk.khatkit.app.feature.settings

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.khromia.components.FancySlider
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.ChatFontFamily
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.data.files.FileFolders
import heizige.kk.khatkit.app.core.data.files.FileUtils
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.Select
import heizige.kk.khatkit.app.core.ui.icons.deleteForever
import heizige.kk.khatkit.app.core.ui.icons.uploadFile
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.ui.theme.rememberChatFontFamily
import heizige.kk.khatkit.app.core.util.plus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingPreferencesUIFontPage(vm: SettingViewModel = hiltViewModel()) {
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        SettingPreferencesUIFontPageMiuix(vm)
        return
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatFontFamily = rememberChatFontFamily(displaySetting)

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
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
                updateDisplaySetting(
                    displaySetting.copy(
                        chatFontFamily = ChatFontFamily.CUSTOM,
                        chatCustomFontPath = importedFont.relativePath,
                        chatCustomFontName = importedFont.displayName,
                    )
                )
                Toast.show(importSuccessMsg, isError = false)
            }.onFailure { error ->
                Toast.show(importFailedMsg.format(error.message.orEmpty()), isError = true)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.setting_page_preferences_ui_font),
        scrollBehavior = scrollBehavior,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(
                start = 8.dp,
                top = PageMetrics.TopContentPadding,
                end = 8.dp,
                bottom = 8.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_chat_font_family_title)) },
                        supportingContent = {
                            Select(
                                options = ChatFontFamily.entries,
                                selectedOption = displaySetting.chatFontFamily,
                                onOptionSelected = { family ->
                                    if (family == ChatFontFamily.CUSTOM && displaySetting.chatCustomFontPath.isBlank()) {
                                        fontPickerLauncher.launch(CustomFontMimeTypes)
                                    } else {
                                        updateDisplaySetting(displaySetting.copy(chatFontFamily = family))
                                    }
                                },
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .fillMaxWidth(),
                                optionToString = { it.label() },
                                optionLeading = { family ->
                                    Text(
                                        text = "Aa",
                                        fontFamily = family.toFontFamilyUI(chatFontFamily),
                                    )
                                }
                            )
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_custom_font_title)) },
                        supportingContent = {
                            Text(
                                if (displaySetting.chatCustomFontName.isNotBlank()) {
                                    displaySetting.chatCustomFontName
                                } else {
                                    stringResource(R.string.setting_display_page_custom_font_not_imported)
                                }
                            )
                        },
                        trailingContent = {
                            Row {
                                KedgeIconButton(
                                    onClick = { fontPickerLauncher.launch(CustomFontMimeTypes) },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        uploadFile,
                                        contentDescription = stringResource(
                                            R.string.setting_display_page_custom_font_import
                                        )
                                    )
                                }
                                if (displaySetting.chatCustomFontPath.isNotBlank()) {
                                    KedgeIconButton(
                                        onClick = {
                                            deleteCustomChatFont(context, displaySetting.chatCustomFontPath)
                                            updateDisplaySetting(
                                                displaySetting.copy(
                                                    chatFontFamily = ChatFontFamily.DEFAULT,
                                                    chatCustomFontPath = "",
                                                    chatCustomFontName = "",
                                                )
                                            )
                                        },
                                        shapes = IconButtonDefaults.shapes(),
                                    ) {
                                        Icon(
                                            deleteForever,
                                            contentDescription = stringResource(
                                                R.string.setting_display_page_custom_font_remove
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_font_size_title)) },
                        supportingContent = {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FancySlider(
                                        value = displaySetting.fontSizeRatio,
                                        onValueChange = {
                                            updateDisplaySetting(displaySetting.copy(fontSizeRatio = it))
                                        },
                                        valueRange = 0.5f..2f,
                                        steps = 11,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(text = "${(displaySetting.fontSizeRatio * 100).toInt()}%")
                                }
                                MarkdownBlock(
                                    content = stringResource(R.string.setting_display_page_font_size_preview),
                                    style = LocalTextStyle.current.copy(
                                        fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
                                        lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio,
                                        fontFamily = chatFontFamily
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 自定义聊天字体：MD3 与 Miuix 两个设置页共用的同一份实现（internal，不新增对外 API）。
// 原 Miuix 字体页里逐字镜像的 MIME 表 / 扩展名集合 / ImportedChatFont / 导入 / 删除 / 标签
// 六件套已删除，两页共用本文件的这份实现。
// 落盘契约不变：目录 filesDir/<FileFolders.FONTS>、文件名 chat_font.<时间戳>.<扩展名>、
// 写回 DisplaySetting.chatCustomFontPath / chatCustomFontName。
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

/** 字体族标签：默认 / 衬线 / 等宽 / 自定义，两风格同键同义。 */
@Composable
internal fun ChatFontFamily.label(): String = when (this) {
    ChatFontFamily.DEFAULT -> stringResource(R.string.setting_display_page_chat_font_family_default)
    ChatFontFamily.SERIF -> stringResource(R.string.setting_display_page_chat_font_family_serif)
    ChatFontFamily.MONOSPACE -> stringResource(R.string.setting_display_page_chat_font_family_monospace)
    ChatFontFamily.CUSTOM -> stringResource(R.string.setting_display_page_chat_font_family_custom)
}

private fun ChatFontFamily.toFontFamilyUI(customFontFamily: FontFamily): FontFamily = when (this) {
    ChatFontFamily.DEFAULT -> FontFamily.Default
    ChatFontFamily.SERIF -> FontFamily.Serif
    ChatFontFamily.MONOSPACE -> FontFamily.Monospace
    ChatFontFamily.CUSTOM -> customFontFamily
}

internal fun importCustomChatFont(context: Context, uri: Uri): ImportedChatFont {
    val displayName = FileUtils.getFileNameFromUri(context, uri)?.takeIf { it.isNotBlank() } ?: "custom_font"
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

    val relativePath = FileUtils.getRelativePathInFilesDir(context.filesDir, targetFile)
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
