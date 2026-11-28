package heizige.kk.khatkit.app.feature.settings

import androidx.compose.ui.platform.LocalResources
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
import heizige.kk.khatkit.app.feature.settings.data.CustomFontMimeTypes
import heizige.kk.khatkit.app.feature.settings.data.importCustomChatFont
import heizige.kk.khatkit.app.feature.settings.data.deleteCustomChatFont

@Composable
fun SettingPreferencesUIFontPage(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val displaySetting = settings.displaySetting
    val context = LocalContext.current
    val resources = LocalResources.current
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
                val detail = when (error.message) {
                    "Unable to open selected font" -> resources.getString(R.string.setting_font_open_error)
                    "Invalid font file" -> resources.getString(R.string.setting_font_invalid_error)
                    "Unable to prepare existing font for replacement" -> resources.getString(R.string.setting_font_replace_error)
                    "Unable to save selected font" -> resources.getString(R.string.setting_font_save_error)
                    else -> error.localizedMessage.orEmpty()
                }
                Toast.show(importFailedMsg.format(detail), isError = true)
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
                val percentSuffix = stringResource(R.string.setting_unit_percent)
                CardGroup(title = { Text(stringResource(R.string.setting_preferences_font_chat)) }) {
                    mochiThemePicker(
                        title = fontFamilyTitle,
                        options = fontOptions,
                        selected = displaySetting.chatFontFamily,
                        anchor = settingAnchor(R.string.setting_display_page_chat_font_family_title),
                        optionIcon = { family, tint ->
                            Text(
                                text = stringResource(R.string.setting_font_preview_sample),
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
                        suffix = percentSuffix,
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
@Composable

internal fun ChatFontFamily.label(): String = when (this) {
    ChatFontFamily.DEFAULT -> stringResource(R.string.setting_display_page_chat_font_family_default)
    ChatFontFamily.SERIF -> stringResource(R.string.setting_display_page_chat_font_family_serif)
    ChatFontFamily.MONOSPACE -> stringResource(R.string.setting_display_page_chat_font_family_monospace)
    ChatFontFamily.CUSTOM -> stringResource(R.string.setting_display_page_chat_font_family_custom)
}
