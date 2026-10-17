package heizige.kk.khatkit.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.ChatFontFamily
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.data.files.FileFolders
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceDropdown
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSlider
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.ui.icons.deleteForever
import heizige.kk.khatkit.app.core.ui.icons.uploadFile
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.ui.theme.rememberChatFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon

/**
 * 「字体」二级页的 Miuix 风格版。
 *
 * 由原「界面偏好设置」页（`SettingPreferencesUIPageMiuix.kt`）的第二个 miuixGroup 拆出：
 * 聊天字体四选、自定义字体导入/移除、字体大小与预览；依赖 ChatFontFamily、MarkdownBlock
 * 等 MD3 专属组件的部分保留在各自子组件里；字体相关的落盘/标签 helper 已与 MD3 版收敛为
 * 同一份实现（`SettingPreferencesUIFontPage.kt` 末尾的 internal `importCustomChatFont` /
 * `deleteCustomChatFont` / `CustomFontMimeTypes` / `ChatFontFamily.label()`）。
 */
@Composable
fun SettingPreferencesUIFontPageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val chatFontFamily = rememberChatFontFamily(displaySetting)

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    // 自定义字体：与 MD3 同一套做法 —— 同一个
    // `ActivityResultContracts.OpenDocument` 选择器、同一批落库字段、同一个 `updateDisplaySetting`
    // （→ `SettingViewModel.updateSettings` → SettingsRepository）。落盘位置/命名沿用共用的
    // `importCustomChatFont`：`filesDir/<FileFolders.FONTS>/chat_font.<时间戳>.<扩展名>`，
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

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_preferences_ui_font),
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        navigationIcon = { BackButton() },
    ) {
        miuixGroup {
            // #1 字体族四选：可选值、默认值与持久化字段都与 MD3 完全一致
            // （同一个 `ChatFontFamily` 枚举 + `DisplaySetting.chatFontFamily`）；
            // 选「自定义」而尚未导入字体时先拉起字体选择器（与 MD3 :248-254 同款分支）。
            PreferenceDropdown(
                title = stringResource(R.string.setting_display_page_chat_font_family_title),
                // 与同仓 Miuix 页的下拉一致（`SettingPreferencesThemePageMiuix.kt:44-50`）：
                // 当前值显示在副行，与 MD3 `Select` 控件露出当前选项同义。
                summary = displaySetting.chatFontFamily.label(),
                items = ChatFontFamily.entries.map { it.label() },
                selectedIndex = ChatFontFamily.entries.indexOf(displaySetting.chatFontFamily),
                onSelectedIndexChange = { index ->
                    val family = ChatFontFamily.entries[index]
                    if (family == ChatFontFamily.CUSTOM && displaySetting.chatCustomFontPath.isBlank()) {
                        fontPickerLauncher.launch(CustomFontMimeTypes)
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
                onClick = { fontPickerLauncher.launch(CustomFontMimeTypes) },
                endAction = {
                    KedgeIconButton(
                        onClick = { fontPickerLauncher.launch(CustomFontMimeTypes) },
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
                                deleteCustomChatFont(context, displaySetting.chatCustomFontPath)
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
    }
}
