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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.core.graphics.ColorUtils
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.components.ui.RikkaConfirmDialog
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixEmptyHint
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceDropdown
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixItemSpacing
import heizige.kk.khatkit.app.core.ui.hooks.rememberAmoledDarkMode
import heizige.kk.khatkit.app.core.ui.hooks.rememberColorMode
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.contentCopy
import heizige.kk.khatkit.app.core.ui.icons.deleteForever
import heizige.kk.khatkit.app.core.ui.icons.editSquare
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.icons.uploadFile
import heizige.kk.khatkit.app.core.ui.theme.ColorMode as UiColorMode
import heizige.kk.khatkit.app.core.ui.theme.CustomTheme
import heizige.kk.khatkit.app.core.ui.theme.LocalDarkMode
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.ui.theme.PresetThemes
import heizige.kk.khatkit.app.feature.settings.components.BlurToggleGroupMiuix
import heizige.kk.khatkit.app.feature.settings.components.ListCardStyleGroupMiuix
import heizige.kk.khatkit.app.feature.settings.components.ThemeColorSettingGroupMiuix
import heizige.kk.khatkit.app.feature.settings.components.ThemeCustomColorSheet
import heizige.kk.khatkit.app.feature.settings.components.UiStyleGroupMiuix
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeSlider
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.theme.KedgeTextStyles
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val themeMiuixJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

/**
 * 主题偏好页的 Miuix 风格版（KernelSU 双文件架构）。
 *
 * 自定义主题管理（列表 / 新建 / 编辑 / 复制 JSON / 删除 / 导入）原在 Miuix 主题独立页，
 * 现已整体并入本页；导入与新建两个入口挪到本页顶栏 [MiuixSettingsPage] 的 `actions`。
 * 小节顺序：主题模式 → 主题配色 → 自定义主题（仅非动态取色）→ AMOLED → 界面风格 → 毛玻璃 → 列表卡片样式。
 */
@Composable
fun SettingPreferencesThemePageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var amoledDarkMode by rememberAmoledDarkMode()
    val colorMode = rememberColorMode()
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val exportSuccessMsg = stringResource(R.string.setting_theme_page_export_success)
    val importSuccessMsg = stringResource(R.string.setting_theme_page_import_success)
    var showCustomColor by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(PresetThemes.first().standardLight.primary) }
    var showEditSheet by remember { mutableStateOf(false) }
    var editingTheme by remember { mutableStateOf<CustomTheme?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var deletingTheme by remember { mutableStateOf<CustomTheme?>(null) }

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_preferences_theme),
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        navigationIcon = { BackButton() },
        // 导入 / 新建自定义主题：MD3 是「自定义主题」小节标题右侧的两个按钮，
        // Miuix 走顶栏 actions（与 SettingMcpPageMiuix 的导入/新建同款）。
        actions = {
            KedgeIconButton(onClick = { showImportDialog = true }) {
                Icon(
                    imageVector = uploadFile,
                    contentDescription = stringResource(R.string.setting_theme_page_import_theme),
                )
            }
            KedgeIconButton(
                onClick = {
                    editingTheme = null
                    showEditSheet = true
                },
            ) {
                Icon(
                    imageVector = add,
                    contentDescription = stringResource(R.string.setting_theme_page_add_theme),
                )
            }
        },
    ) {
        miuixGroup {
            PreferenceDropdown(
                title = stringResource(R.string.greeting_settings_theme_mode),
                summary = colorModeLabel(colorMode.value),
                items = UiColorMode.entries.map { colorModeLabel(it) },
                selectedIndex = UiColorMode.entries.indexOf(colorMode.value),
                onSelectedIndexChange = { colorMode.value = UiColorMode.entries[it] },
            )
        }
        miuixGroup {
            ThemeColorSettingGroupMiuix(
                dynamicColor = settings.dynamicColor,
                themeId = settings.themeId,
                onUpdateDynamicColor = { vm.updateSettings(settings.copy(dynamicColor = it)) },
                onSelectTheme = { vm.updateSettings(settings.copy(themeId = it)) },
                onCustomColorClick = { showCustomColor = true },
            )
        }

        // 动态取色下自定义主题不可用 —— 与 MD3 页完全同一条件。
        if (!settings.dynamicColor) {
            item(key = "customThemesHeader") {
                Text(
                    text = stringResource(R.string.setting_theme_page_custom_themes),
                    modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            if (settings.customThemes.isEmpty()) {
                item(key = "emptyCustomThemes") {
                    MiuixEmptyHint(text = stringResource(R.string.setting_theme_page_no_custom_themes))
                }
            }
            items(settings.customThemes, key = { it.id }) { theme ->
                MiuixCustomThemeItem(
                    theme = theme,
                    isSelected = settings.themeId == theme.id,
                    modifier = Modifier.miuixItemSpacing(),
                    onSelect = { vm.updateSettings(settings.copy(themeId = theme.id)) },
                    onExport = {
                        val json = themeMiuixJson.encodeToString(theme)
                        scope.launch {
                            clipboardManager.setClipEntry(
                                ClipEntry(ClipData.newPlainText("theme", json))
                            )
                        }
                        Toast.show(exportSuccessMsg, isError = false)
                    },
                    onEdit = {
                        editingTheme = theme
                        showEditSheet = true
                    },
                    onDelete = { deletingTheme = theme },
                )
            }
        }

        miuixGroup {
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_amoled_dark_mode_title),
                summary = stringResource(R.string.setting_display_page_amoled_dark_mode_desc),
                checked = amoledDarkMode,
                onCheckedChange = { amoledDarkMode = it },
            )
        }
        // 下面三组原先在「设置 → 偏好设置 → 界面」（`SettingPreferencesUIPageMiuix.kt`），
        // 按外观归属迁进主题偏好页：界面风格、毛玻璃、列表卡片样式。三个组件本身没动，
        // 仍是 `SettingPreferencesUIComponentsMiuix.kt` 里那一份（风格/毛玻璃写
        // `KhatKitToolProvider`，卡片样式写 `Settings.listCard*`）。
        miuixGroup {
            UiStyleGroupMiuix()
        }
        miuixGroup {
            BlurToggleGroupMiuix()
        }
        miuixGroup {
            ListCardStyleGroupMiuix(
                largeCorner = settings.listCardLargeCorner,
                smallCorner = settings.listCardSmallCorner,
                gap = settings.listCardGap,
                onUpdateLargeCorner = { large ->
                    vm.updateSettings(
                        settings.copy(
                            listCardLargeCorner = large,
                            listCardSmallCorner = settings.listCardSmallCorner.coerceAtMost(large),
                        )
                    )
                },
                onUpdateSmallCorner = { small ->
                    vm.updateSettings(settings.copy(listCardSmallCorner = small))
                },
                onUpdateGap = { gap ->
                    vm.updateSettings(settings.copy(listCardGap = gap))
                },
            )
        }
    }

    if (showEditSheet) {
        MiuixCustomThemeEditSheet(
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
                        themeId = theme.id
                    )
                )
                showEditSheet = false
            }
        )
    }

    if (showImportDialog) {
        MiuixImportThemeDialog(
            onDismiss = { showImportDialog = false },
            onImport = { theme ->
                val importedTheme = theme.copy(id = Uuid.random().toString())
                vm.updateSettings(
                    settings.copy(
                        customThemes = settings.customThemes + importedTheme,
                        themeId = importedTheme.id
                    )
                )
                showImportDialog = false
                Toast.show(importSuccessMsg, isError = false)
            }
        )
    }

    RikkaConfirmDialog(
        show = deletingTheme != null,
        title = stringResource(R.string.setting_theme_page_delete_theme_title),
        confirmText = stringResource(android.R.string.ok),
        dismissText = stringResource(android.R.string.cancel),
        onConfirm = {
            deletingTheme?.let { theme ->
                val newThemes = settings.customThemes.filter { it.id != theme.id }
                val newThemeId = if (settings.themeId == theme.id) "sakura" else settings.themeId
                vm.updateSettings(settings.copy(customThemes = newThemes, themeId = newThemeId))
            }
            deletingTheme = null
        },
        onDismiss = { deletingTheme = null },
        text = {
            Text(stringResource(R.string.setting_theme_page_delete_theme_message))
        }
    )

    ThemeCustomColorSheet(
        visible = showCustomColor,
        initialColor = customColor,
        history = settings.customColorHistory,
        onDismiss = { showCustomColor = false },
        onColorChanged = { customColor = it },
        onConfirm = { color ->
            val argb = color.toArgb().toLong() and 0xFFFFFFFFL
            val custom = CustomTheme(
                name = String.format("#%08X", color.toArgb()),
                primaryColorArgb = argb,
            )
            vm.updateSettings(
                settings.copy(
                    dynamicColor = false,
                    customThemes = settings.customThemes + custom,
                    themeId = custom.id,
                    customColorHistory = (
                        listOf(argb) +
                            settings.customColorHistory.filterNot { it == argb }
                        ).take(8),
                )
            )
            showCustomColor = false
        },
    )
}

@Composable
private fun colorModeLabel(mode: UiColorMode): String = stringResource(
    when (mode) {
        UiColorMode.SYSTEM -> R.string.greeting_settings_theme_system
        UiColorMode.LIGHT -> R.string.greeting_settings_theme_light
        UiColorMode.DARK -> R.string.greeting_settings_theme_dark
    }
)

/**
 * 自定义主题行：与 MD3 的 `CustomThemeItem` 同构（四色色轮 + 选中勾 + 复制/编辑/删除），
 * 但底色与圆角交给 [KedgeOptionItem] 的 Miuix 分支（KedgeCard 行），不传 MD3 的卡片色。
 */
@Composable
private fun MiuixCustomThemeItem(
    theme: CustomTheme,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkMode = LocalDarkMode.current
    val scheme = theme.generateColorScheme(darkMode)

    KedgeOptionItem(
        modifier = modifier,
        onClick = { onSelect() },
        leadingContent = {
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
                        imageVector = check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        titleContent = { Text(theme.name.ifEmpty { "Unnamed" }) },
        trailingContent = {
            Row {
                KedgeIconButton(onClick = onExport) {
                    Icon(imageVector = contentCopy, contentDescription = null)
                }
                KedgeIconButton(onClick = onEdit) {
                    Icon(imageVector = editSquare, contentDescription = null)
                }
                KedgeIconButton(onClick = onDelete) {
                    Icon(
                        imageVector = deleteForever,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.error
                    )
                }
            }
        },
    )
}

/**
 * 新建/编辑自定义主题的底部弹层（Miuix 版）。弹层本身是双风格 [PrimaryBottomSheet]，
 * 表单用双风格 [KedgeOutlinedTextFieldWithSlots] / [KedgeSlider]。
 */
@Composable
private fun MiuixCustomThemeEditSheet(
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                KedgeOutlinedTextFieldWithSlots(
                    value = currentTheme.name,
                    onValueChange = { currentTheme = currentTheme.copy(name = it) },
                    label = { Text(stringResource(R.string.setting_theme_page_theme_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Text(
                    text = stringResource(R.string.setting_theme_page_primary_color),
                    style = KedgeTextStyles.title(),
                )
                MiuixColorPickerRow(
                    color = Color(currentTheme.primaryColorArgb.toInt()),
                    onColorChange = {
                        currentTheme = currentTheme.copy(primaryColorArgb = it.toArgb().toLong() and 0xFFFFFFFFL)
                    }
                )

                Text(
                    text = stringResource(R.string.setting_theme_page_secondary_color),
                    style = KedgeTextStyles.title(),
                )
                MiuixColorPickerRow(
                    color = if (currentTheme.secondaryColorArgb != null) {
                        Color(currentTheme.secondaryColorArgb!!.toInt())
                    } else {
                        Color(currentTheme.generateColorScheme(false).secondary.toArgb())
                    },
                    onColorChange = {
                        currentTheme = currentTheme.copy(secondaryColorArgb = it.toArgb().toLong() and 0xFFFFFFFFL)
                    }
                )

                Text(
                    text = stringResource(R.string.setting_theme_page_tertiary_color),
                    style = KedgeTextStyles.title(),
                )
                MiuixColorPickerRow(
                    color = if (currentTheme.tertiaryColorArgb != null) {
                        Color(currentTheme.tertiaryColorArgb!!.toInt())
                    } else {
                        Color(currentTheme.generateColorScheme(false).tertiary.toArgb())
                    },
                    onColorChange = {
                        currentTheme = currentTheme.copy(tertiaryColorArgb = it.toArgb().toLong() and 0xFFFFFFFFL)
                    }
                )

                MiuixThemePreview(currentTheme)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 导入主题 JSON 的弹窗（Miuix 版）。[AppAlertDialog] 已按风格分流。 */
@Composable
private fun MiuixImportThemeDialog(
    onDismiss: () -> Unit,
    onImport: (CustomTheme) -> Unit,
) {
    var jsonText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_theme_page_import_theme)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KedgeOutlinedTextFieldWithSlots(
                    value = jsonText,
                    onValueChange = {
                        jsonText = it
                        errorMessage = null
                    },
                    label = { Text("JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { msg -> { Text(msg) } },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        confirmButton = {
            KedgeButton(
                onClick = {
                    try {
                        val theme = themeMiuixJson.decodeFromString<CustomTheme>(jsonText)
                        onImport(theme)
                    } catch (e: Exception) {
                        errorMessage = e.message
                    }
                },
                enabled = jsonText.isNotBlank(),
            ) {
                Text(stringResource(R.string.setting_theme_page_import_theme))
            }
        },
        dismissButton = {
            KedgeTextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

/** HSL 调色行（Miuix 版，控件全部是双风格 Kedge 组件）。 */
@Composable
private fun MiuixColorPickerRow(
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    val hsl = remember(color) {
        FloatArray(3).also { ColorUtils.colorToHSL(color.toArgb(), it) }
    }
    var hue by remember(color) { mutableFloatStateOf(hsl[0]) }
    var saturation by remember(color) { mutableFloatStateOf(hsl[1]) }
    var lightness by remember(color) { mutableFloatStateOf(hsl[2]) }
    var hslCode by remember(color) { mutableStateOf(formatHslCode(hsl[0], hsl[1], hsl[2])) }
    var hslCodeError by remember(color) { mutableStateOf(false) }

    fun updateColor(newHue: Float, newSaturation: Float, newLightness: Float) {
        hue = newHue
        saturation = newSaturation
        lightness = newLightness
        hslCode = formatHslCode(newHue, newSaturation, newLightness)
        hslCodeError = false
        onColorChange(Color(ColorUtils.HSLToColor(floatArrayOf(newHue, newSaturation, newLightness))))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                drawCircle(color = color)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("H", style = KedgeTextStyles.footnoteSmall(), modifier = Modifier.width(16.dp))
                    KedgeSlider(
                        value = hue,
                        onValueChange = {
                            updateColor(it, saturation, lightness)
                        },
                        valueRange = 0f..360f,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("S", style = KedgeTextStyles.footnoteSmall(), modifier = Modifier.width(16.dp))
                    KedgeSlider(
                        value = saturation,
                        onValueChange = {
                            updateColor(hue, it, lightness)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("L", style = KedgeTextStyles.footnoteSmall(), modifier = Modifier.width(16.dp))
                    KedgeSlider(
                        value = lightness,
                        onValueChange = {
                            updateColor(hue, saturation, it)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        KedgeOutlinedTextFieldWithSlots(
            value = hslCode,
            onValueChange = { value ->
                hslCode = value
                val parsedHsl = parseHslCode(value)
                hslCodeError = parsedHsl == null
                if (parsedHsl != null) {
                    hue = parsedHsl[0]
                    saturation = parsedHsl[1]
                    lightness = parsedHsl[2]
                    onColorChange(Color(ColorUtils.HSLToColor(parsedHsl)))
                }
            },
            label = { Text("HSL") },
            placeholder = { Text("hsl(267 36% 48%)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = hslCodeError,
            supportingText = if (hslCodeError) {
                { Text("Use hsl(267 36% 48%)") }
            } else {
                null
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// HSL 文本解析/格式化（parseHslCode / parseHslPercentOrFraction / formatHslCode）
// 已与 MD3 版收敛为同一份实现，见 SettingPreferencesThemePage.kt 末尾的 internal 声明。

/** 主题色预览（Miuix 版）。 */
@Composable
private fun MiuixThemePreview(theme: CustomTheme) {
    val darkMode = LocalDarkMode.current
    val scheme = theme.generateColorScheme(darkMode)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.setting_theme_page_preview),
            style = KedgeTextStyles.title(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MiuixColorSwatch(scheme.primary, "P")
            MiuixColorSwatch(scheme.secondary, "S")
            MiuixColorSwatch(scheme.tertiary, "T")
            MiuixColorSwatch(scheme.primaryContainer, "PC")
            MiuixColorSwatch(scheme.secondaryContainer, "SC")
            MiuixColorSwatch(scheme.surface, "Sf")
        }
    }
}

@Composable
private fun MiuixColorSwatch(color: Color, label: String) {
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
            style = KedgeTextStyles.footnoteSmall(),
            color = MiuixTheme.colorScheme.onBackgroundVariant
        )
    }
}
