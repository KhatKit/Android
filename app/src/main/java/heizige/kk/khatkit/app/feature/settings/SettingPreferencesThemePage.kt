package heizige.kk.khatkit.app.feature.settings

import android.content.ClipData
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.ui.AppDialog
import heizige.kk.khatkit.app.core.ui.components.theme.parseHexColor
import heizige.kk.khatkit.uikit.mochi.KkEditDialog
import heizige.kk.khatkit.uikit.mochi.KkEditField
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet
import heizige.kk.khatkit.app.core.data.datastore.MochiAppearance
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.utility.add
import heizige.kk.khatkit.app.core.ui.icons.utility.check
import heizige.kk.khatkit.app.core.ui.icons.utility.contentCopy
import heizige.kk.khatkit.app.core.ui.icons.utility.deleteForever
import heizige.kk.khatkit.app.core.ui.icons.utility.editSquare
import heizige.kk.khatkit.app.core.ui.icons.settings.palette
import heizige.kk.khatkit.app.core.ui.icons.files.uploadFile
import heizige.kk.khatkit.app.core.ui.theme.CustomTheme
import heizige.kk.khatkit.app.core.ui.theme.LocalDarkMode
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.picker.MochiColorWell
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import heizige.kk.khatkit.uikit.mochi.MochiButton
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.Toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.effect.content.crossfadeOrChange
import ui.feice.mochi.theme.MochiHdr
import ui.feice.mochi.theme.MochiTheme
import heizige.kk.khatkit.app.core.ui.theme.LocalKkThemeReveal

private val themeJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

/**
 * 主题偏好页，布局照 Mochi demo 的主题标签页：
 * - 顶栏走设置页统一的 MochiSettingsPageScaffold（紧凑标题，左上返回与「设置 / 界面」同一枚），
 *   正文是懒加载列表
 * - 每个分组一张 iOS 26 分组卡片（CardGroup）：卡片外小标题、卡片内行、卡片下说明
 * - 行是 Mochi 列表行（52dp 行高、16dp 内边距、分隔线自文字起点画到卡片右边），
 *   进二级页的行带「›」；行高变化走 Mochi 同款尺寸弹簧
 *
 * 设置项按 Mochi 主题标签页的分组拆成三个二级页：配色（明暗 / 纯黑 / 系统取色 / 主题色）、
 * 材质（玻璃 / 按钮样式 / 模糊）、列表卡片（圆角与间距）。本页只留入口和自定义主题管理。
 */
@Composable
fun SettingPreferencesThemePage(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val display = settings.displaySetting
    val themeReveal = LocalKkThemeReveal.current
    val scroll = rememberLazyListState()
    val navController = LocalNavController.current
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val exportSuccessMsg = stringResource(R.string.setting_theme_page_export_success)
    val importSuccessMsg = stringResource(R.string.setting_theme_page_import_success)
    var showEditSheet by remember { mutableStateOf(false) }
    var editingTheme by remember { mutableStateOf<CustomTheme?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var deletingTheme by remember { mutableStateOf<CustomTheme?>(null) }

    fun selectTheme(themeId: String) {
        // 选自定义主题 = 不再跟随壁纸取色，否则选了也不生效（原来这块被 dynamicColor 挡着，看着像没用）
        vm.updateSettings {
            it.copy(
                themeId = themeId,
                dynamicColor = false,
                displaySetting = it.displaySetting.withMochiAppearance(MochiAppearance.MOCHI),
            )
        }
    }

    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_page_preferences_theme),
        scrollUnderTopBar = true,
    ) { innerPadding ->
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 16.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
        ) {
        item(key = "appearance") {
            val themeTitle = stringResource(R.string.setting_theme_theme)
            val appearanceOptions = listOf(
                MochiAppearance.MOCHI to stringResource(R.string.setting_theme_mochi),
                MochiAppearance.IOS to stringResource(R.string.setting_theme_ios),
                MochiAppearance.NOTHING to stringResource(R.string.setting_theme_nothing),
            )
            CardGroup(
                title = { Text(stringResource(R.string.setting_theme_appearance)) },
            ) {
                mochiThemePicker(
                    title = themeTitle,
                    options = appearanceOptions,
                    selected = display.resolvedMochiAppearance,
                ) { appearance ->
                    themeReveal.crossfadeOrChange {
                        vm.updateSettings { it.copy(displaySetting = it.displaySetting.withMochiAppearance(appearance)) }
                    }
                }
                item(
                    chevron = true,
                    onClick = { navController.navigate(Screen.SettingThemeColors) },
                    headlineContent = { Text(stringResource(R.string.setting_theme_colors)) },
                    supportingContent = { Text(stringResource(R.string.setting_theme_colors_desc)) },
                )
                item(
                    chevron = true,
                    onClick = { navController.navigate(Screen.SettingThemeMaterials) },
                    headlineContent = { Text(stringResource(R.string.setting_theme_materials)) },
                    supportingContent = { Text(stringResource(R.string.setting_theme_materials_desc)) },
                )
                item(
                    chevron = true,
                    onClick = { navController.navigate(Screen.SettingThemeListCards) },
                    headlineContent = { Text(stringResource(R.string.setting_theme_list_cards)) },
                    supportingContent = { Text(stringResource(R.string.setting_theme_list_cards_desc)) },
                )
            }
        }

        item(key = "experimental") {
            val hdrTitle = stringResource(R.string.setting_theme_hdr)
            val hdrDescription = stringResource(R.string.setting_theme_hdr_desc)
            val hdrUnsupportedDescription = stringResource(R.string.setting_theme_hdr_unsupported)
            CardGroup(title = { Text(stringResource(R.string.setting_theme_experimental)) }) {
                val supported = MochiHdr.supported
                mochiThemeSwitch(
                    title = hdrTitle,
                    description = if (supported) hdrDescription else hdrUnsupportedDescription,
                    checked = supported && display.mochiHdrHighlights,
                    enabled = supported,
                ) { enabled ->
                    vm.updateSettings { it.copy(displaySetting = it.displaySetting.copy(mochiHdrHighlights = enabled)) }
                }
            }
        }

        // 自定义主题：新建/导入两行动作 + 已有主题各一行；选中即换整套配色（同时关掉系统取色）。
        // 卡片圆角/项间距读 listCardStyle()，在「列表卡片」页调完这里立刻变。
        item(key = "customThemes") {
            CardGroup(
                title = { Text(stringResource(R.string.setting_theme_page_custom_themes)) },
                footer = if (settings.customThemes.isEmpty()) {
                    stringResource(R.string.setting_theme_custom_empty)
                } else {
                    stringResource(R.string.setting_theme_custom_desc)
                },
            ) {
                item(
                    onClick = {
                        editingTheme = null
                        showEditSheet = true
                    },
                    leadingContent = {
                        Icon(add, null, modifier = Modifier.size(22.dp), tint = MochiTheme.colorScheme.primary)
                    },
                    headlineContent = { Text(stringResource(R.string.setting_theme_page_add_theme)) },
                )
                item(
                    onClick = { showImportDialog = true },
                    leadingContent = {
                        Icon(uploadFile, null, modifier = Modifier.size(22.dp), tint = MochiTheme.colorScheme.primary)
                    },
                    headlineContent = { Text(stringResource(R.string.setting_theme_page_import_theme)) },
                )
                settings.customThemes.forEach { theme ->
                    item(
                        onClick = { selectTheme(theme.id) },
                        leadingContent = {
                            CustomThemeColorDot(theme = theme, isSelected = settings.themeId == theme.id)
                        },
                        headlineContent = { Text(theme.name.ifEmpty { stringResource(R.string.setting_theme_unnamed) }) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MochiIconButton(
                                    onClick = {
                                        val json = themeJson.encodeToString(theme)
                                        scope.launch {
                                            clipboardManager.setClipEntry(
                                                ClipEntry(ClipData.newPlainText("theme", json))
                                            )
                                        }
                                        Toast.show(exportSuccessMsg, isError = false)
                                    },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(contentCopy, null)
                                }
                                MochiIconButton(
                                    onClick = {
                                        editingTheme = theme
                                        showEditSheet = true
                                    },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(editSquare, null)
                                }
                                MochiIconButton(
                                    onClick = { deletingTheme = theme },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(deleteForever, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                    )
                }
            }
        }
        }
    }

    if (showEditSheet) {
        CustomThemeEditSheet(
            theme = editingTheme,
            onDismiss = { showEditSheet = false },
            onSave = { theme ->
                val newThemes = if (editingTheme != null) {
                    settings.customThemes.map { if (it.id == theme.id) theme else it }
                } else {
                    settings.customThemes + theme
                }
                vm.updateSettings(
                    settings.copy(
                        customThemes = newThemes,
                        themeId = theme.id,
                        dynamicColor = false,
                        displaySetting = settings.displaySetting.withMochiAppearance(MochiAppearance.MOCHI),
                    )
                )
                showEditSheet = false
            }
        )
    }

    if (showImportDialog) {
        ImportThemeDialog(
            onDismiss = { showImportDialog = false },
            onImport = { theme ->
                val importedTheme = theme.copy(id = Uuid.random().toString())
                vm.updateSettings(
                    settings.copy(
                        customThemes = settings.customThemes + importedTheme,
                        themeId = importedTheme.id,
                        dynamicColor = false,
                        displaySetting = settings.displaySetting.withMochiAppearance(MochiAppearance.MOCHI),
                    )
                )
                showImportDialog = false
                Toast.show(importSuccessMsg, isError = false)
            }
        )
    }

    MochiDialog(
        visible = deletingTheme != null,
        onDismissRequest = { deletingTheme = null },
        title = stringResource(R.string.setting_theme_page_delete_theme_title),
        message = stringResource(R.string.setting_theme_page_delete_theme_message),
        actions = persistentListOf(
            MochiDialogAction(
                label = stringResource(android.R.string.cancel),
                role = MochiDialogActionRole.Cancel,
            ),
            MochiDialogAction(
                label = stringResource(android.R.string.ok),
                role = MochiDialogActionRole.Destructive,
                onClick = {
                    deletingTheme?.let { theme ->
                        val newThemes = settings.customThemes.filter { it.id != theme.id }
                        val newThemeId = if (settings.themeId == theme.id) "sakura" else settings.themeId
                        vm.updateSettings(settings.copy(customThemes = newThemes, themeId = newThemeId))
                    }
                    deletingTheme = null
                },
            ),
        ),
    )
}

/** 自定义主题的四色预览（选中有勾）：三块容器色拼成的圆，中间是主色圆点。 */
@Composable
private fun CustomThemeColorDot(theme: CustomTheme, isSelected: Boolean) {
    val darkMode = LocalDarkMode.current
    val scheme = theme.generateColorScheme(darkMode)
    Box(contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .clip(CircleShape)
                .size(24.dp)
        ) {
            drawRect(color = scheme.primaryContainer, size = size)
            drawRect(
                color = scheme.secondaryContainer,
                size = size,
                topLeft = Offset(x = size.width / 2, y = 0f)
            )
            drawRect(
                color = scheme.tertiaryContainer,
                size = size,
                topLeft = Offset(x = size.width / 2, y = size.height / 2)
            )
            drawCircle(
                color = scheme.primary,
                radius = if (isSelected) 10.dp.toPx() else 6.dp.toPx(),
                center = Offset(x = size.width / 2, y = size.height / 2)
            )
        }
        if (isSelected) {
            Icon(
                check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomThemeEditSheet(
    theme: CustomTheme?,
    onDismiss: () -> Unit,
    onSave: (CustomTheme) -> Unit,
) {
    var currentTheme by remember {
        mutableStateOf(theme ?: CustomTheme())
    }

    PrimaryBottomSheet(
        visible = true,
        title = if (theme == null) stringResource(R.string.setting_theme_page_create_theme)
        else stringResource(R.string.setting_theme_page_edit_theme),
        imageVector = palette,
        confirmText = stringResource(R.string.setting_theme_page_save),
        onConfirm = {
            if (currentTheme.name.isNotBlank()) {
                onSave(currentTheme)
            }
        },
        onDismiss = onDismiss,
        scrollable = false,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 32.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Md3OutlinedTextFieldWithSlots(
                    value = currentTheme.name,
                    onValueChange = { currentTheme = currentTheme.copy(name = it) },
                    label = { Text(stringResource(R.string.setting_theme_page_theme_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                MochiListSection {
                    ThemeColorPickerRow(
                        title = stringResource(R.string.setting_theme_page_primary_color),
                        color = Color(currentTheme.primaryColorArgb.toInt()),
                        onColorChange = {
                            currentTheme = currentTheme.copy(primaryColorArgb = it.toArgb().toLong() and 0xFFFFFFFFL)
                        },
                    )
                    ThemeColorPickerRow(
                        title = stringResource(R.string.setting_theme_page_secondary_color),
                        color = currentTheme.secondaryColorArgb?.let { Color(it.toInt()) }
                            ?: currentTheme.generateColorScheme(false).secondary,
                        onColorChange = {
                            currentTheme = currentTheme.copy(secondaryColorArgb = it.toArgb().toLong() and 0xFFFFFFFFL)
                        },
                    )
                    ThemeColorPickerRow(
                        title = stringResource(R.string.setting_theme_page_tertiary_color),
                        color = currentTheme.tertiaryColorArgb?.let { Color(it.toInt()) }
                            ?: currentTheme.generateColorScheme(false).tertiary,
                        onColorChange = {
                            currentTheme = currentTheme.copy(tertiaryColorArgb = it.toArgb().toLong() and 0xFFFFFFFFL)
                        },
                    )
                }

                ThemePreview(currentTheme)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ImportThemeDialog(
    onDismiss: () -> Unit,
    onImport: (CustomTheme) -> Unit,
) {
    var jsonText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_theme_page_import_theme)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Md3OutlinedTextFieldWithSlots(
                    value = jsonText,
                    onValueChange = {
                        jsonText = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.setting_theme_json)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    isError = errorMessage != null,
                    supportingText = { errorMessage?.let { msg -> Text(msg) } },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        confirmButton = {
            MochiButton(
                onClick = {
                    try {
                        val theme = themeJson.decodeFromString<CustomTheme>(jsonText)
                        onImport(theme)
                    } catch (e: Exception) {
                        errorMessage = e.message
                    }
                },
                enabled = jsonText.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.setting_theme_page_import_theme))
            }
        },
        dismissButton = {
            MochiTextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

/** Matches Mochi's color-picker demo; edits remain local until the theme is saved. */
@Composable
private fun ThemeColorPickerRow(
    title: String,
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    var showHexDialog by remember { mutableStateOf(false) }
    val hexError = stringResource(R.string.setting_theme_picker_hex_invalid)
    MochiListRow(
        title = title,
        value = String.format("#%06X", color.toArgb() and 0xFFFFFF),
        subtitle = stringResource(R.string.setting_theme_picker_hex_hint),
        onClick = { showHexDialog = true },
        trailing = {
            MochiColorWell(
                color = color,
                onColorChange = onColorChange,
                title = title,
                supportsAlpha = false,
                size = 44.dp,
            )
        },
    )
    KkEditDialog(
        visible = showHexDialog,
        title = title,
        fields = listOf(
            KkEditField(
                label = stringResource(R.string.setting_theme_picker_hex_label),
                initialValue = String.format("%06X", color.toArgb() and 0xFFFFFF),
                placeholder = stringResource(R.string.setting_theme_picker_hex_placeholder),
                maxLength = 10,
                onValidate = { if (parseHexColor(it) == null) hexError else null },
            ),
        ),
        onDismiss = { showHexDialog = false },
        onConfirm = { values ->
            parseHexColor(values.first())?.let { onColorChange(it.copy(alpha = 1f)) }
            showHexDialog = false
        },
    )
}

@Composable
private fun ThemePreview(theme: CustomTheme) {
    val darkMode = LocalDarkMode.current
    val scheme = theme.generateColorScheme(darkMode)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.setting_theme_page_preview),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ColorSwatch(scheme.primary, stringResource(R.string.setting_theme_swatch_primary))
            ColorSwatch(scheme.secondary, stringResource(R.string.setting_theme_s))
            ColorSwatch(scheme.tertiary, stringResource(R.string.setting_theme_swatch_tertiary))
            ColorSwatch(scheme.primaryContainer, stringResource(R.string.setting_theme_swatch_primary_container))
            ColorSwatch(scheme.secondaryContainer, stringResource(R.string.setting_theme_swatch_secondary_container))
            ColorSwatch(scheme.surface, stringResource(R.string.setting_theme_swatch_surface))
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
        ) {
            drawCircle(color = color)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
