package heizige.kk.khatkit.app.ui.pages.setting.components

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.ui.icons.brightnessAuto
import heizige.kk.khatkit.app.ui.icons.check
import heizige.kk.khatkit.app.ui.icons.darkMode
import heizige.kk.khatkit.app.ui.icons.lightMode
import heizige.kk.khatkit.app.ui.icons.palette
import heizige.kk.khatkit.app.ui.theme.ColorMode
import heizige.kk.khatkit.app.ui.theme.MorphPolygonShape
import heizige.kk.khatkit.app.ui.theme.PresetThemes
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.khromia.components.PrimaryBottomSheet
import heizige.kk.kedge.theme.KedgeColors
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeSwitch
import heizige.kk.khatkit.app.ui.theme.listCardStyle
import heizige.kk.khromia.components.ExpandableOptionItem
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.core.graphics.toColorInt
import heizige.kk.khromia.components.EditDialog
import heizige.kk.khromia.components.EditFieldConfig
import heizige.kk.khromia.helper.fadingEdge
import heizige.kk.khromia.components.SquareColorPicker
import heizige.kk.khromia.text.OptionsText

/** 主题配色设置块：与引导页样式一致（动态取色行 + 配色圆点面板）。 */
@Composable
fun ThemeColorSettingGroup(
    dynamicColor: Boolean,
    themeId: String,
    onUpdateDynamicColor: (Boolean) -> Unit,
    onSelectTheme: (String) -> Unit,
    onCustomColorClick: () -> Unit,
    modifier: Modifier = Modifier,
    dynamicColorEnabled: Boolean = true,
) {
    val cards = listCardStyle()
    Column(modifier = modifier) {
        OptionsText(stringResource(R.string.greeting_settings_theme_color))
        Spacer(Modifier.height(8.dp))
        ExpandableOptionItem(
            imageVector = palette,
            title = stringResource(R.string.greeting_settings_theme_color),
            contentColor = Color.Transparent,
        ) {
            Column {
                KedgeOptionItem(
                    onClick = {
                        if (dynamicColorEnabled) onUpdateDynamicColor(!dynamicColor)
                    },
                    modifier = if (dynamicColorEnabled) Modifier else Modifier.alpha(0.5f),
                    shape = cards.groupItemShape(isFirst = true, isLast = false),
                    leadingContent = {
                        Icon(
                            imageVector = palette,
                            contentDescription = null,
                            tint = KedgeColors.onSurfaceVariant.copy(alpha = 0.87f),
                        )
                    },
                    titleContent = { Text(stringResource(R.string.greeting_settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.greeting_settings_dynamic_color_desc)) },
                    trailingContent = {
                        KedgeSwitch(
                            checked = dynamicColorEnabled && dynamicColor,
                            enabled = dynamicColorEnabled,
                            onCheckedChange = { enabled ->
                                if (dynamicColorEnabled) onUpdateDynamicColor(enabled)
                            },
                        )
                    },
                )

                AnimatedVisibility(
                    visible = !dynamicColor,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow,
                        )
                    ) + fadeIn(),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow,
                        )
                    ) + fadeOut(),
                ) {
                    PresetThemeColorDots(
                        modifier = Modifier
                            .padding(top = cards.gap)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(vertical = 12.dp),
                        selectedThemeId = themeId,
                        onSelectTheme = onSelectTheme,
                        onCustomColorClick = onCustomColorClick,
                    )
                }
            }
        }
    }
}

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

@Composable
fun PresetThemeColorDots(
    selectedThemeId: String,
    onSelectTheme: (String) -> Unit,
    onCustomColorClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val presetCount = ((maxWidth - 48.dp).value / 64f).toInt()
            .coerceIn(2, PresetThemes.size)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PresetThemes.take(presetCount).forEach { theme ->
                val color = theme.standardLight.primary
                val isSelected = selectedThemeId == theme.id
                val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Sunny) }
                val progress by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                    label = "colorMorph",
                )

                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 24.dp, maxWidth = 48.dp)
                        .aspectRatio(1f)
                        .weight(1f, fill = false)
                        .clip(MorphPolygonShape(morph, progress))
                        .background(color.copy(alpha = 0.87f))
                        .clickable { onSelectTheme(theme.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = check,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.87f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .sizeIn(maxWidth = 48.dp)
                    .aspectRatio(1f)
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(50))
                    .background(KedgeColors.surfaceVariant.copy(alpha = 0.54f))
                    .clickable { onCustomColorClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = palette,
                    contentDescription = stringResource(R.string.greeting_settings_custom_color),
                    tint = KedgeColors.onSurfaceVariant.copy(alpha = 0.87f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun ThemeCustomColorSheet(
    visible: Boolean,
    initialColor: Color,
    history: List<Long>,
    onDismiss: () -> Unit,
    onColorChanged: (Color) -> Unit = {},
    onConfirm: (Color) -> Unit,
) {
    var customColor by remember(visible) { mutableStateOf(initialColor) }
    var showHexDialog by remember { mutableStateOf(false) }

    PrimaryBottomSheet(
        visible = visible,
        title = stringResource(R.string.greeting_settings_custom_color),
        imageVector = palette,
        onDismiss = onDismiss,
    ) { _ ->
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(customColor)
                    .clickable { showHexDialog = true }
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = String.format("#%08X", customColor.toArgb()),
                    color = if (customColor.luminance() > 0.5f) Color.Black else Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SquareColorPicker(
                modifier = Modifier.padding(horizontal = 16.dp),
                initialColor = customColor,
                onColorChanged = {
                    customColor = it
                    onColorChanged(it)
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (history.isNotEmpty()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            text = stringResource(R.string.greeting_settings_history_colors),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fadingEdge(left = 20.dp, right = 20.dp, strength = 1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                history.reversed().forEach { argb ->
                                    val color = Color(argb.toInt())
                                    val isSelected = customColor.toArgb() == argb.toInt()
                                    val morph = remember {
                                        Morph(MaterialShapes.Circle, MaterialShapes.Sunny)
                                    }
                                    val progress by animateFloatAsState(
                                        targetValue = if (isSelected) 1f else 0f,
                                        animationSpec = spring(
                                            Spring.DampingRatioMediumBouncy,
                                            Spring.StiffnessLow,
                                        ),
                                        label = "historyColorMorph",
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(MorphPolygonShape(morph, progress))
                                            .background(color)
                                            .clickable {
                                                customColor = color
                                                onColorChanged(color)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = check,
                                                contentDescription = null,
                                                tint = if (color.luminance() > 0.5f) {
                                                    Color.Black.copy(alpha = 0.87f)
                                                } else {
                                                    Color.White.copy(alpha = 0.87f)
                                                },
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                KedgeButton(
                    onClick = { onConfirm(customColor) },
                ) {
                    Text(stringResource(R.string.greeting_settings_custom_color_confirm))
                }
            }
        }
    }

    if (showHexDialog) {
        EditDialog(
            visible = true,
            title = stringResource(R.string.greeting_settings_custom_color),
            fields = listOf(
                EditFieldConfig(
                    label = "Hex Code",
                    initialValue = String.format("%08X", customColor.toArgb()),
                    placeholder = "#AARRGGBB, #RRGGBB, AARRGGBB, RRGGBB",
                    maxLength = 10,
                    onValidate = { input -> if (parseHexColor(input) == null) "Invalid hex" else null },
                )
            ),
            onDismiss = { showHexDialog = false },
            onConfirm = { results ->
                parseHexColor(results.first())?.let {
                    customColor = it
                    onColorChanged(it)
                }
                showHexDialog = false
            },
        )
    }
}

private fun parseHexColor(input: String): Color? {
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
