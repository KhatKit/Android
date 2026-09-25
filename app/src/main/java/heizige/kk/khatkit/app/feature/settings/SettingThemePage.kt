package heizige.kk.khatkit.app.feature.settings

import heizige.kk.kedge.components.KedgeOptionItem
import android.content.ClipData
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import heizige.kk.khromia.components.FancySlider
import heizige.kk.khromia.components.PrimaryBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.Select
import heizige.kk.khatkit.app.core.ui.components.ui.RikkaConfirmDialog
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.hooks.rememberColorMode
import heizige.kk.khatkit.app.feature.settings.components.PresetThemeColorDots
import heizige.kk.khatkit.app.feature.settings.components.ThemeCustomColorSheet
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.ui.theme.CustomTheme
import heizige.kk.khatkit.app.core.ui.theme.LocalDarkMode
import heizige.kk.khatkit.app.core.ui.theme.PresetThemes
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khromia.text.OptionsText
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.contentCopy
import heizige.kk.khatkit.app.core.ui.icons.deleteForever
import heizige.kk.khatkit.app.core.ui.icons.editSquare
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.icons.uploadFile

private val themeJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingThemePage(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val clipboardManager = LocalClipboard.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val colorMode = rememberColorMode()
    val exportSuccessMsg = stringResource(R.string.setting_theme_page_export_success)
    val importSuccessMsg = stringResource(R.string.setting_theme_page_import_success)

    var showEditSheet by remember { mutableStateOf(false) }
    var editingTheme by remember { mutableStateOf<CustomTheme?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var deletingTheme by remember { mutableStateOf<CustomTheme?>(null) }
    var showCustomColor by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(PresetThemes.first().standardLight.primary) }

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.setting_page_theme_setting),
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("themeMode") {
                val selectedColorModeText = when (colorMode.value) {
                    heizige.kk.khatkit.app.core.ui.theme.ColorMode.SYSTEM -> stringResource(R.string.greeting_settings_theme_system)
                    heizige.kk.khatkit.app.core.ui.theme.ColorMode.LIGHT -> stringResource(R.string.greeting_settings_theme_light)
                    heizige.kk.khatkit.app.core.ui.theme.ColorMode.DARK -> stringResource(R.string.greeting_settings_theme_dark)
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.greeting_settings_theme_mode)) },
                        supportingContent = { Text(selectedColorModeText) },
                        trailingContent = {
                            Select(
                                options = heizige.kk.khatkit.app.core.ui.theme.ColorMode.entries,
                                selectedOption = colorMode.value,
                                onOptionSelected = { colorMode.value = it },
                                optionToString = { mode ->
                                    stringResource(
                                        when (mode) {
                                            heizige.kk.khatkit.app.core.ui.theme.ColorMode.SYSTEM -> R.string.greeting_settings_theme_system
                                            heizige.kk.khatkit.app.core.ui.theme.ColorMode.LIGHT -> R.string.greeting_settings_theme_light
                                            heizige.kk.khatkit.app.core.ui.theme.ColorMode.DARK -> R.string.greeting_settings_theme_dark
                                        }
                                    )
                                },
                                modifier = Modifier.width(150.dp),
                            )
                        },
                    )
                }
            }

            item("themeColor") {
                heizige.kk.khatkit.app.feature.settings.components.ThemeColorSettingGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    dynamicColor = settings.dynamicColor,
                    themeId = settings.themeId,
                    onUpdateDynamicColor = { vm.updateSettings(settings.copy(dynamicColor = it)) },
                    onSelectTheme = { vm.updateSettings(settings.copy(themeId = it)) },
                    onCustomColorClick = { showCustomColor = true },
                )
            }

            if (!settings.dynamicColor) {
                item("customThemesHeader") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        OptionsText(stringResource(R.string.setting_theme_page_custom_themes))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { showImportDialog = true },
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Icon(uploadFile, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.setting_theme_page_import_theme))
                            }
                            FilledTonalButton(
                                onClick = {
                                    editingTheme = null
                                    showEditSheet = true
                                },
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Icon(add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.setting_theme_page_add_theme))
                            }
                        }
                    }
                }

                if (settings.customThemes.isEmpty()) {
                    item("emptyCustomThemes") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.setting_theme_page_no_custom_themes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(settings.customThemes, key = { it.id }) { theme ->
                    CustomThemeItem(
                        theme = theme,
                        isSelected = settings.themeId == theme.id,
                        onSelect = {
                            vm.updateSettings(settings.copy(themeId = theme.id))
                        },
                        onExport = {
                            val json = themeJson.encodeToString(theme)
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
                        onDelete = {
                            deletingTheme = theme
                        }
                    )
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
                        themeId = theme.id
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
private fun CustomThemeItem(
    theme: CustomTheme,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val darkMode = LocalDarkMode.current
    val scheme = theme.generateColorScheme(darkMode)

    KedgeOptionItem(
        modifier = Modifier.padding(horizontal = 8.dp),
        shape = heizige.kk.khatkit.app.core.ui.theme.listCardStyle().indexedShape(0, 1),
        backgroundColor = CustomColors.listItemColors.containerColor,
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
                        check,
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
                IconButton(onClick = onExport, shapes = IconButtonDefaults.shapes()) {
                    Icon(contentCopy, null)
                }
                IconButton(onClick = onEdit, shapes = IconButtonDefaults.shapes()) {
                    Icon(editSquare, null)
                }
                IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        deleteForever,
                        null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
    )
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
                OutlinedTextField(
                    value = currentTheme.name,
                    onValueChange = { currentTheme = currentTheme.copy(name = it) },
                    label = { Text(stringResource(R.string.setting_theme_page_theme_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Text(
                    text = stringResource(R.string.setting_theme_page_primary_color),
                    style = MaterialTheme.typography.titleSmall,
                )
                ColorPickerRow(
                    color = Color(currentTheme.primaryColorArgb.toInt()),
                    onColorChange = {
                        currentTheme = currentTheme.copy(primaryColorArgb = it.toArgb().toLong() and 0xFFFFFFFFL)
                    }
                )

                Text(
                    text = stringResource(R.string.setting_theme_page_secondary_color),
                    style = MaterialTheme.typography.titleSmall,
                )
                ColorPickerRow(
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
                    style = MaterialTheme.typography.titleSmall,
                )
                ColorPickerRow(
                    color = if (currentTheme.tertiaryColorArgb != null) {
                        Color(currentTheme.tertiaryColorArgb!!.toInt())
                    } else {
                        Color(currentTheme.generateColorScheme(false).tertiary.toArgb())
                    },
                    onColorChange = {
                        currentTheme = currentTheme.copy(tertiaryColorArgb = it.toArgb().toLong() and 0xFFFFFFFFL)
                    }
                )

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

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_theme_page_import_theme)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
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
                )
            }
        },
        confirmButton = {
            Button(
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
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun ColorPickerRow(
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
                    Text("H", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
                    FancySlider(
                        value = hue,
                        onValueChange = {
                            updateColor(it, saturation, lightness)
                        },
                        valueRange = 0f..360f,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("S", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
                    FancySlider(
                        value = saturation,
                        onValueChange = {
                            updateColor(hue, it, lightness)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("L", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
                    FancySlider(
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

        OutlinedTextField(
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
        )
    }
}

private val hslNumberRegex = Regex("""[-+]?\d*\.?\d+""")

private fun parseHslCode(value: String): FloatArray? {
    val values = buildList {
        for (match in hslNumberRegex.findAll(value)) {
            add(match.value.toFloatOrNull() ?: return null)
            if (size == 3) break
        }
    }

    if (values.size != 3) return null

    val hue = values[0].coerceIn(0f, 360f)
    val saturation = parseHslPercentOrFraction(values[1]) ?: return null
    val lightness = parseHslPercentOrFraction(values[2]) ?: return null

    return floatArrayOf(hue, saturation, lightness)
}

private fun parseHslPercentOrFraction(value: Float): Float? {
    if (!value.isFinite()) return null
    return if (value > 1f) {
        (value / 100f).coerceIn(0f, 1f)
    } else {
        value.coerceIn(0f, 1f)
    }
}

private fun formatHslCode(hue: Float, saturation: Float, lightness: Float): String {
    return "hsl(${hue.roundToInt()} ${(saturation * 100).roundToInt()}% ${(lightness * 100).roundToInt()}%)"
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
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ColorSwatch(scheme.primary, "P")
            ColorSwatch(scheme.secondary, "S")
            ColorSwatch(scheme.tertiary, "T")
            ColorSwatch(scheme.primaryContainer, "PC")
            ColorSwatch(scheme.secondaryContainer, "SC")
            ColorSwatch(scheme.surface, "Sf")
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
