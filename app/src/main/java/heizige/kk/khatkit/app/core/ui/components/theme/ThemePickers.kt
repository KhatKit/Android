package heizige.kk.khatkit.app.core.ui.components.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.graphics.shapes.Morph
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.icons.settings.brightnessAuto
import heizige.kk.khatkit.app.core.ui.icons.settings.darkMode
import heizige.kk.khatkit.app.core.ui.icons.settings.lightMode
import heizige.kk.khatkit.app.core.ui.theme.ColorMode
import heizige.kk.khatkit.app.core.ui.theme.MorphPolygonShape
import heizige.kk.khatkit.app.core.ui.theme.PresetThemes
import heizige.kk.khatkit.uikit.mochi.KkEditDialog
import heizige.kk.khatkit.uikit.mochi.KkEditField
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiSheetDetent
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiSheetState
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.picker.MochiColorPickerSheet
import ui.feice.mochi.picker.MochiSeedPalette

@Composable
fun MorphThemeModeSelector(
    selected: ColorMode,
    onSelect: (ColorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        listOf(ColorMode.SYSTEM, ColorMode.DARK, ColorMode.LIGHT).forEach { mode ->
            val isSelected = selected == mode
            val morph = remember { Morph(MaterialShapes.Square, MaterialShapes.Sunny) }
            val progress by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "morphProgress",
            )
            val animatedScale by animateFloatAsState(
                targetValue = if (isSelected) 1.3f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "iconScale",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .aspectRatio(1f)
                    .clip(MorphPolygonShape(morph, progress))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.87f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.87f)
                    )
                    .clickable { onSelect(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (mode) {
                        ColorMode.SYSTEM -> brightnessAuto
                        ColorMode.LIGHT -> lightMode
                        ColorMode.DARK -> darkMode
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .scale(animatedScale),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.87f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.54f),
                )

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedVisibility(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)),
                        visible = !isSelected,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Text(
                            text = when (mode) {
                                ColorMode.SYSTEM -> stringResource(R.string.greeting_settings_theme_system)
                                ColorMode.LIGHT -> stringResource(R.string.greeting_settings_theme_light)
                                ColorMode.DARK -> stringResource(R.string.greeting_settings_theme_dark)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                                .alpha(0.87f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 主题配色调色板：直接用 Mochi 的莫奈色板 [MochiSeedPalette]。
 *
 * 每个色块画的是「种子色生成的整套配色」（主色 + 辅助色 + 第三色），不是一个孤零零的圆点；
 * 选中的色块内缩出一圈缝、外面亮主色环，切换时轻震。末尾的自定义色块是彩虹圆环，
 * 点开就是 Mochi 自己的选色面板（网格 / 色谱 / 滑块），不再自己拼 HSV 方块。
 *
 * [selectedSeedColor] 给当前主题的种子色（自定义主题也能回显选中）；为 null 时按 [selectedThemeId]
 * 在预设里查。
 */
@Composable
fun PresetThemeColorDots(
    selectedThemeId: String,
    onSelectTheme: (String) -> Unit,
    onCustomColor: (Color) -> Unit,
    modifier: Modifier = Modifier,
    selectedSeedColor: Color? = null,
) {
    val seeds = remember { PresetThemes.map { it.standardLight.primary }.toImmutableList() }
    val selected = selectedSeedColor
        ?: PresetThemes.firstOrNull { it.id == selectedThemeId }?.standardLight?.primary
    MochiSeedPalette(
        selected = selected,
        onSelect = { color ->
            val preset = PresetThemes.firstOrNull { it.standardLight.primary == color }
            if (preset != null) onSelectTheme(preset.id) else onCustomColor(color)
        },
        modifier = modifier,
        seeds = seeds,
        custom = true,
    )
}

/** Native Mochi picker with the existing draft/confirm contract for persisted colors. */
@Composable
fun ThemeCustomColorSheet(
    visible: Boolean,
    sheetState: MochiSheetState? = null,
    initialColor: Color,
    history: List<Long>,
    onDismiss: () -> Unit,
    onColorChanged: (Color) -> Unit = {},
    onClear: (() -> Unit)? = null,
    onConfirm: (Color) -> Unit,
) {
    var customColor by remember { mutableStateOf(initialColor) }
    var showHexDialog by remember { mutableStateOf(false) }
    val internalSheet = rememberMochiSheetState(listOf(MochiSheetDetent.Medium, MochiSheetDetent.Large))
    val effectiveSheet = sheetState ?: internalSheet
    LaunchedEffect(visible) {
        if (visible) customColor = initialColor
        showHexDialog = false
        if (sheetState == null) {
            if (visible) effectiveSheet.show() else effectiveSheet.hide()
        }
    }
    val title = stringResource(R.string.greeting_settings_custom_color)
    val hexLabel = stringResource(R.string.setting_theme_picker_hex_label)
    val hexError = stringResource(R.string.setting_theme_picker_hex_invalid)

    MochiColorPickerSheet(
        state = effectiveSheet,
        color = customColor,
        onColorChange = { color ->
            customColor = color
            onColorChanged(color)
        },
        title = title,
        supportsAlpha = true,
        recents = remember(history) { history.asReversed().map { Color(it.toInt()) } },
        onDismiss = {
            showHexDialog = false
            onDismiss()
        },
        header = {
            MochiSheetHeader(
                title = title,
                navigationIcon = {
                    MochiBarButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { effectiveSheet.hide() },
                    )
                },
                actions = {
                    MochiBarButton(
                        text = stringResource(R.string.greeting_settings_custom_color_confirm),
                        onClick = {
                            effectiveSheet.hide()
                            onConfirm(customColor)
                        },
                    )
                },
            )
        },
        footer = {
            MochiListSection {
                MochiListRow(
                    title = hexLabel,
                    value = String.format("#%08X", customColor.toArgb()),
                    subtitle = stringResource(R.string.setting_theme_picker_hex_hint),
                    onClick = { showHexDialog = true },
                )
                if (onClear != null) {
                    MochiListRow(
                        title = stringResource(R.string.avatar_reset),
                        onClick = {
                            effectiveSheet.hide()
                            onClear()
                        },
                    )
                }
            }
        },
    )

    KkEditDialog(
        visible = showHexDialog,
        title = title,
        fields = listOf(
            KkEditField(
                label = hexLabel,
                initialValue = String.format("%08X", customColor.toArgb()),
                placeholder = stringResource(R.string.setting_theme_picker_hex_placeholder),
                maxLength = 10,
                onValidate = { input -> if (parseHexColor(input) == null) hexError else null },
            ),
        ),
        onDismiss = { showHexDialog = false },
        onConfirm = { results ->
            parseHexColor(results.first())?.let { color ->
                customColor = color
                onColorChanged(color)
            }
            showHexDialog = false
        },
    )
}

internal fun parseHexColor(input: String): Color? {
    return try {
        val normalized = input.trim()
            .replace(" ", "")
            .replace("0x", "")
            .replace("0X", "")
            .let { if (!it.startsWith("#") && it.isNotEmpty()) "#$it" else it }
        val finalHex = when (normalized.length) {
            7 -> "#FF${normalized.substring(1)}"
            9 -> normalized
            6 -> "#FF$normalized"
            8 -> "#$normalized"
            else -> return null
        }
        Color(finalHex.toColorInt())
    } catch (_: Exception) {
        null
    }
}
