package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import heizige.kk.khromia.components.PrimaryBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.ai.core.ReasoningLevel
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ui.ToggleSurface
import heizige.kk.khatkit.app.core.ui.icons.ReasoningHigh
import heizige.kk.khatkit.app.core.ui.icons.ReasoningLow
import heizige.kk.khatkit.app.core.ui.icons.ReasoningMedium
import kotlin.math.roundToInt
import heizige.kk.khatkit.app.core.ui.icons.emojiObjects
import heizige.kk.khatkit.app.core.ui.icons.lightbulb
import heizige.kk.khatkit.app.core.ui.icons.neurology

private val levels = ReasoningLevel.entries
private val levelCount = levels.size

@Composable
fun ReasoningButton(
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    chip: Boolean = false,
    reasoningLevel: ReasoningLevel,
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        ReasoningPicker(
            reasoningLevel = reasoningLevel,
            onDismissRequest = { showPicker = false },
            onUpdateReasoningLevel = onUpdateReasoningLevel
        )
    }

    if (chip) {
        Surface(
            onClick = { showPicker = true },
            modifier = modifier,
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            contentColor = if (reasoningLevel.isEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            border = BorderStroke(
                1.dp,
                if (reasoningLevel.isEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier.size(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ReasoningIcon(reasoningLevel)
                }
                Text("推理", style = MaterialTheme.typography.labelMedium)
            }
        }
    } else {
        ToggleSurface(
            checked = reasoningLevel.isEnabled,
            onClick = { showPicker = true },
            modifier = modifier,
        ) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ReasoningIcon(reasoningLevel)
                }
                if (!onlyIcon) Text(stringResource(R.string.setting_provider_page_reasoning))
            }
        }
    }
}

@Composable
fun ReasoningPicker(
    reasoningLevel: ReasoningLevel,
    onDismissRequest: () -> Unit = {},
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
) {
    val currentIndex = levels.indexOf(reasoningLevel).coerceAtLeast(0)
    val sliderState = remember {
        SliderState(
            value = currentIndex.toFloat(),
            trackRange = 0f..(levelCount - 1).toFloat(),
            steps = levelCount - 2,
        )
    }

    LaunchedEffect(currentIndex) {
        sliderState.value = currentIndex.toFloat()
    }

    PrimaryBottomSheet(
        visible = true,
        title = stringResource(R.string.reasoning_picker_title),
        imageVector = neurology,
        onDismiss = onDismissRequest,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 标题
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.reasoning_picker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // 当前等级展示
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val iconColor by animateColorAsState(
                    if (reasoningLevel.isEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = when (reasoningLevel) {
                        ReasoningLevel.OFF -> lightbulb
                        ReasoningLevel.AUTO -> emojiObjects
                        ReasoningLevel.LOW -> ReasoningLow
                        ReasoningLevel.MEDIUM -> ReasoningMedium
                        ReasoningLevel.HIGH -> ReasoningHigh
                        ReasoningLevel.XHIGH -> ReasoningHigh
                        ReasoningLevel.MAX -> ReasoningHigh
                    },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = iconColor,
                )
                Text(
                    text = reasoningLevel.label(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Slider(
                state = sliderState,
                onValueChange = { sliderState.value = it },
                onValueChangeFinished = {
                    val snappedIndex = sliderState.value.roundToInt().coerceIn(0, levelCount - 1)
                    sliderState.value = snappedIndex.toFloat()
                    onUpdateReasoningLevel(levels[snappedIndex])
                },
                modifier = Modifier.fillMaxWidth(),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimary)
                        )
                    }
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        drawStopIndicator = null,
                        thumbTrackGapSize = 0.dp,
                    )
                }
            )
        }
    }
}

@Composable
private fun ReasoningIcon(level: ReasoningLevel) {
    when (level) {
        ReasoningLevel.OFF -> Icon(lightbulb, null)
        ReasoningLevel.AUTO -> Icon(emojiObjects, null)
        ReasoningLevel.LOW -> Icon(ReasoningLow, null)
        ReasoningLevel.MEDIUM -> Icon(ReasoningMedium, null)
        ReasoningLevel.HIGH -> Icon(ReasoningHigh, null)
        ReasoningLevel.XHIGH -> Icon(ReasoningHigh, null)
        ReasoningLevel.MAX -> Icon(ReasoningHigh, null)
    }
}

@Composable
private fun ReasoningLevel.label(): String = when (this) {
    ReasoningLevel.OFF -> stringResource(R.string.reasoning_off)
    ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto)
    ReasoningLevel.LOW -> stringResource(R.string.reasoning_light)
    ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium)
    ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy)
    ReasoningLevel.XHIGH -> stringResource(R.string.reasoning_xhigh)
    ReasoningLevel.MAX -> stringResource(R.string.reasoning_max)
}

@Composable
@Preview(showBackground = true)
private fun ReasoningPickerPreview() {
    MaterialTheme {
        var level by remember { mutableStateOf(ReasoningLevel.AUTO) }
        ReasoningPicker(
            reasoningLevel = level,
            onUpdateReasoningLevel = { level = it }
        )
    }
}
