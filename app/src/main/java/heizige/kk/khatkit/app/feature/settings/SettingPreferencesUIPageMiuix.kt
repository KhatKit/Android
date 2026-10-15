package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSlider
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.feature.settings.components.CodeDisplaySettingsGroupMiuix
import heizige.kk.khatkit.app.core.ui.theme.rememberChatFontFamily
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import androidx.compose.material3.LocalTextStyle
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import heizige.kk.khatkit.app.core.data.datastore.ChatFontFamily
import heizige.kk.khatkit.app.core.data.files.FileFolders
import heizige.kk.khatkit.app.core.data.files.FileUtils
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceDropdown
import heizige.kk.khatkit.app.core.ui.icons.deleteForever
import heizige.kk.khatkit.app.core.ui.icons.uploadFile
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.khromia.helper.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import java.io.File
import kotlin.math.roundToInt

/**
 * 界面偏好页的 Miuix 风格版。
 *
 * 字体/代码块/列表卡片样式三组的开关与滑块全部走 [PreferenceSwitch] / [PreferenceSlider]；
 * 依赖 ChatFontFamily、MarkdownBlock 等 MD3 专属组件的部分保留在各自子组件里。
 */
@Composable
fun SettingPreferencesUIPageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val chatFontFamily = rememberChatFontFamily(displaySetting)

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    // 自定义字体（#2）：与 MD3 `SettingPreferencesUIPage.kt:87-109` 同一套做法 —— 同一个
    // `ActivityResultContracts.OpenDocument` 选择器、同一批落库字段、同一个 `updateDisplaySetting`
    // （→ `SettingViewModel.updateSettings` → SettingsRepository）。落盘位置/命名见文件末尾
    // [importCustomChatFontMiuix] 的说明：仍是 `filesDir/<FileFolders.FONTS>/chat_font.<时间戳>.<扩展名>`，
    // 没有新存储路径。
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importSuccessMsg = stringResource(R.string.setting_display_page_custom_font_import_success)
    val importFailedMsg = stringResource(R.string.setting_display_page_custom_font_import_failed)
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    importCustomChatFontMiuix(context, uri)
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

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_preferences_ui),
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        navigationIcon = { BackButton() },
    ) {
        miuixGroup {
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_user_avatar_title),
                checked = displaySetting.showUserAvatar,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showUserAvatar = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_assistant_bubble_title),
                checked = displaySetting.showAssistantBubble,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showAssistantBubble = it)) },
            )
            PreferenceSlider(
                title = stringResource(R.string.setting_display_page_bubble_opacity_title),
                value = displaySetting.bubbleOpacity,
                valueRange = 0.1f..1.0f,
                steps = 8,
                valueText = "${(displaySetting.bubbleOpacity * 100).roundToInt()}%",
                onValueChange = { updateDisplaySetting(displaySetting.copy(bubbleOpacity = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_chat_list_model_icon_title),
                checked = displaySetting.showModelIcon,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showModelIcon = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_model_name_title),
                checked = displaySetting.showModelName,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showModelName = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_datetime_in_message_title),
                checked = displaySetting.showDateTimeInMessage,
                onCheckedChange = {
                    updateDisplaySetting(displaySetting.copy(showDateTimeInMessage = it))
                },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_token_usage_title),
                checked = displaySetting.showTokenUsage,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showTokenUsage = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_show_thinking_content_title),
                checked = displaySetting.showThinkingContent,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(showThinkingContent = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_auto_collapse_thinking_title),
                checked = displaySetting.autoCloseThinking,
                onCheckedChange = { updateDisplaySetting(displaySetting.copy(autoCloseThinking = it)) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_enable_latex_rendering_title),
                checked = displaySetting.enableLatexRendering,
                onCheckedChange = {
                    updateDisplaySetting(displaySetting.copy(enableLatexRendering = it))
                },
            )
        }

        miuixGroup {
            // #1 字体族四选：可选值、默认值与持久化字段都与 MD3 完全一致
            // （同一个 `ChatFontFamily` 枚举 + `DisplaySetting.chatFontFamily`）；
            // 选「自定义」而尚未导入字体时先拉起字体选择器（与 MD3 :248-254 同款分支）。
            PreferenceDropdown(
                title = stringResource(R.string.setting_display_page_chat_font_family_title),
                // 与同仓 Miuix 页的下拉一致（`SettingPreferencesThemePageMiuix.kt:44-50`）：
                // 当前值显示在副行，与 MD3 `Select` 控件露出当前选项同义。
                summary = displaySetting.chatFontFamily.labelMiuix(),
                items = ChatFontFamily.entries.map { it.labelMiuix() },
                selectedIndex = ChatFontFamily.entries.indexOf(displaySetting.chatFontFamily),
                onSelectedIndexChange = { index ->
                    val family = ChatFontFamily.entries[index]
                    if (family == ChatFontFamily.CUSTOM && displaySetting.chatCustomFontPath.isBlank()) {
                        fontPickerLauncher.launch(CustomFontMimeTypesMiuix)
                    } else {
                        updateDisplaySetting(displaySetting.copy(chatFontFamily = family))
                    }
                },
            )
            // #2 自定义字体：导入（行点击与右侧上传按钮都可以）/ 移除（右侧删除按钮，仅已导入时出现），
            // 与 MD3 :268-316 同款两个动作、同一批字段（chatCustomFontPath / chatCustomFontName）。
            PreferenceArrow(
                title = stringResource(R.string.setting_display_page_custom_font_title),
                summary = displaySetting.chatCustomFontName.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.setting_display_page_custom_font_not_imported),
                onClick = { fontPickerLauncher.launch(CustomFontMimeTypesMiuix) },
                endAction = {
                    KedgeIconButton(
                        onClick = { fontPickerLauncher.launch(CustomFontMimeTypesMiuix) },
                    ) {
                        Icon(
                            imageVector = uploadFile,
                            contentDescription = stringResource(
                                R.string.setting_display_page_custom_font_import
                            ),
                        )
                    }
                    if (displaySetting.chatCustomFontPath.isNotBlank()) {
                        KedgeIconButton(
                            onClick = {
                                deleteCustomChatFontMiuix(context, displaySetting.chatCustomFontPath)
                                updateDisplaySetting(
                                    displaySetting.copy(
                                        chatFontFamily = ChatFontFamily.DEFAULT,
                                        chatCustomFontPath = "",
                                        chatCustomFontName = "",
                                    )
                                )
                            },
                        ) {
                            Icon(
                                imageVector = deleteForever,
                                contentDescription = stringResource(
                                    R.string.setting_display_page_custom_font_remove
                                ),
                            )
                        }
                    }
                },
            )
            PreferenceSlider(
                title = stringResource(R.string.setting_display_page_font_size_title),
                value = displaySetting.fontSizeRatio,
                valueRange = 0.5f..2f,
                steps = 11,
                valueText = "${(displaySetting.fontSizeRatio * 100).toInt()}%",
                onValueChange = { updateDisplaySetting(displaySetting.copy(fontSizeRatio = it)) },
            )
            // 预览块沿用 MD3 的 MarkdownBlock：它是纯排版组件，与风格无关。
            Column {
                MarkdownBlock(
                    content = stringResource(R.string.setting_display_page_font_size_preview),
                    style = LocalTextStyle.current.copy(
                        fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
                        lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio,
                        fontFamily = chatFontFamily,
                    ),
                )
            }
        }

        miuixGroup {
            CodeDisplaySettingsGroupMiuix(
                codeBlockAutoWrap = displaySetting.codeBlockAutoWrap,
                codeBlockAutoCollapse = displaySetting.codeBlockAutoCollapse,
                showLineNumbers = displaySetting.showLineNumbers,
                onUpdateCodeBlockAutoWrap = {
                    updateDisplaySetting(displaySetting.copy(codeBlockAutoWrap = it))
                },
                onUpdateCodeBlockAutoCollapse = {
                    updateDisplaySetting(displaySetting.copy(codeBlockAutoCollapse = it))
                },
                onUpdateShowLineNumbers = {
                    updateDisplaySetting(displaySetting.copy(showLineNumbers = it))
                },
            )
        }
    }
}

/** 字体族标签：与 MD3 `SettingPreferencesUIPage.kt:396-401` 同键同义（默认/衬线/等宽/自定义）。 */
@Composable
private fun ChatFontFamily.labelMiuix(): String = when (this) {
    ChatFontFamily.DEFAULT -> stringResource(R.string.setting_display_page_chat_font_family_default)
    ChatFontFamily.SERIF -> stringResource(R.string.setting_display_page_chat_font_family_serif)
    ChatFontFamily.MONOSPACE -> stringResource(R.string.setting_display_page_chat_font_family_monospace)
    ChatFontFamily.CUSTOM -> stringResource(R.string.setting_display_page_chat_font_family_custom)
}

/** 与 MD3 `SettingPreferencesUIPage.kt:379-386` 同一个 MIME 过滤表。 */
private val CustomFontMimeTypesMiuix = arrayOf(
    "font/*",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/vnd.ms-opentype",
    "application/octet-stream",
    "*/*",
)

/** 与 MD3 `SettingPreferencesUIPage.kt:388` 同一批扩展名。 */
private val CustomFontExtensionsMiuix = setOf("ttf", "otf", "ttc")

private data class ImportedChatFontMiuix(
    val relativePath: String,
    val displayName: String,
)

/**
 * 自定义字体的落盘实现（Miuix 侧镜像）。
 *
 * **与 MD3 是同一套存储契约，不是新存储路径**：目录同为 `filesDir/<FileFolders.FONTS>`、
 * 文件名同为 `chat_font.<时间戳>.<扩展名>`、校验方式同为 `Typeface.createFromFile`、结果同样写回
 * `DisplaySetting.chatCustomFontPath/chatCustomFontName` 并经 `SettingViewModel.updateSettings`
 * 落库（因此 [heizige.kk.khatkit.app.core.ui.theme.rememberChatFontFamily] 的读取链路两风格共用）。
 *
 * 为什么要镜像：MD3 那份实现（`SettingPreferencesUIPage.kt:379-476` 的
 * `CustomFontMimeTypesUI` / `importCustomChatFontInternal` / `deleteCustomChatFontInternal`）
 * 是**文件级 private**，本任务 inScope 不含那个文件，所以只能在此照抄同一套契约。
 * 若后续把这三个符号提到 core（或把 MD3 文件纳入范围），应删掉这里的镜像、两风格共用一份。
 */
private fun importCustomChatFontMiuix(context: Context, uri: Uri): ImportedChatFontMiuix {
    val displayName = FileUtils.getFileNameFromUri(context, uri)?.takeIf { it.isNotBlank() } ?: "custom_font"
    val extension = displayName.substringAfterLast('.', "")
        .lowercase()
        .takeIf { it in CustomFontExtensionsMiuix }
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

        // 只有能被 Typeface 解析的文件才允许落盘（与 MD3 同一道校验）
        runCatching {
            Typeface.createFromFile(tempFile)
        }.onFailure { error ->
            throw IllegalArgumentException(error.message ?: "Invalid font file", error)
        }

        check(tempFile.renameTo(targetFile)) { "Unable to save selected font" }
        // 与 MD3「替换语义」一致：只保留当前这一份自定义字体，旧文件不留在 filesDir 里
        fontDir.listFiles { file ->
            file.isFile && file.name.startsWith("chat_font.") && file != targetFile
        }?.forEach { it.delete() }
    } catch (error: Throwable) {
        tempFile.delete()
        throw error
    }

    val relativePath = FileUtils.getRelativePathInFilesDir(context.filesDir, targetFile)
        ?: "${FileFolders.FONTS}/${targetFile.name}"
    return ImportedChatFontMiuix(relativePath = relativePath, displayName = displayName)
}

/** 与 MD3 `deleteCustomChatFontInternal` 同款：只删 filesDir 之内的那个文件。 */
private fun deleteCustomChatFontMiuix(context: Context, relativePath: String) {
    val filesDir = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return
    val fontFile = runCatching { File(filesDir, relativePath).canonicalFile }.getOrNull() ?: return
    if (fontFile.path.startsWith("${filesDir.path}${File.separator}")) {
        fontFile.delete()
    }
}
