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
import heizige.kk.khromia.components.PrimaryBottomSheet
import heizige.kk.khromia.components.SquareColorPicker
import heizige.kk.khromia.text.OptionsText

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
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f))
                    .clickable { onCustomColorClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = palette,
                    contentDescription = stringResource(R.string.greeting_settings_custom_color),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.87f),
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

    PrimaryBottomSheet(
        visible = visible,
        title = stringResource(R.string.greeting_settings_custom_color),
        imageVector = palette,
        confirmText = stringResource(R.string.greeting_settings_custom_color_confirm),
        onConfirm = { onConfirm(customColor) },
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

            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                OptionsText(stringResource(R.string.greeting_settings_history_colors))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    history.takeLast(6).reversed().forEach { argb ->
                        val color = Color(argb.toInt())
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    customColor = color
                                    onColorChanged(color)
                                },
                        )
                    }
                }
            }
        }
    }
}
