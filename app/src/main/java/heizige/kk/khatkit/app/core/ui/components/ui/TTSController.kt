package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.core.ui.context.LocalTTSState
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.hooks.CustomTtsState
import heizige.kk.khatkit.tts.model.PlaybackState
import heizige.kk.khatkit.tts.model.PlaybackStatus
import heizige.kk.khatkit.app.core.ui.icons.arrowBack
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.forward
import heizige.kk.khatkit.app.core.ui.icons.pause
import heizige.kk.khatkit.app.core.ui.icons.playArrow

@Composable
fun TTSController() {
    val ttsState = LocalTTSState.current
    val toaster = LocalToaster.current

    val isSpeaking by ttsState.isSpeaking.collectAsState()
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(ttsState, toaster) {
        ttsState.error.collect { error ->
            if (error != null) {
                Toast.show(message = error, isError = true)
            }
        }
    }

    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            // 如果开启，显示悬浮窗
            isVisible = true
        }
    }

    FloatingWindow(
        tag = "tts_controller",
        visibility = isVisible
    ) {
        val playbackState by ttsState.playbackState.collectAsState()
        var expand by remember { mutableStateOf(false) }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.padding(8.dp),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayPauseButton(playbackState = playbackState, ttsState = ttsState)

                IconButton(
                    onClick = {
                        ttsState.stop()
                        isVisible = false
                    }
                ) {
                    Icon(
                        imageVector = close,
                        contentDescription = null,
                    )
                }

                AnimatedVisibility(expand) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpeedButton(playbackState, ttsState)

                        FastForwardButton(ttsState = ttsState)
                    }
                }

                IconButton(
                    onClick = {
                        expand = !expand
                    }
                ) {
                    Icon(
                        imageVector = if (expand) arrowBack else arrowForward,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun FastForwardButton(ttsState: CustomTtsState) {
    IconButton(
        onClick = {
            ttsState.fastForward(5000)
        }
    ) {
        Icon(
            imageVector = forward,
            contentDescription = null,
        )
    }
}

@Composable
private fun PlayPauseButton(
    playbackState: PlaybackState,
    ttsState: CustomTtsState
) {
    FilledTonalIconButton(
        onClick = {
            when (playbackState.status) {
                PlaybackStatus.Playing -> {
                    ttsState.pause()
                }

                else -> {
                    ttsState.resume()
                }
            }
        },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Icon(
            imageVector = if (playbackState.status == PlaybackStatus.Playing) pause else playArrow,
            contentDescription = null,
        )
        if (playbackState.status == PlaybackStatus.Playing || playbackState.status == PlaybackStatus.Buffering || playbackState.status == PlaybackStatus.Paused) {
            CircularProgressIndicator(
                progress = {
                    if (playbackState.status == PlaybackStatus.Playing) {
                        playbackState.positionMs.toFloat() / playbackState.durationMs
                    } else {
                        0f
                    }
                },
                color = MaterialTheme.colorScheme.tertiary,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent
            )
            CircularProgressIndicator(
                progress = {
                    if (playbackState.status == PlaybackStatus.Playing) {
                        playbackState.currentChunkIndex.toFloat() / playbackState.totalChunks
                    } else {
                        0f
                    }
                },
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(2.dp),
                strokeWidth = 2.dp,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun SpeedButton(
    playbackState: PlaybackState,
    ttsState: CustomTtsState
) {
    TextButton(
        onClick = {
            when (playbackState.speed) {
                0.8f -> {
                    ttsState.setSpeed(1.0f)
                }

                1.0f -> {
                    ttsState.setSpeed(1.2f)
                }

                1.2f -> {
                    ttsState.setSpeed(1.5f)
                }

                1.5f -> {
                    ttsState.setSpeed(0.8f)
                }

                else -> {
                    ttsState.setSpeed(1.0f)
                }
            }
        }
    ) {
        Text(text = "x${"%.1f".format(playbackState.speed)}")
    }
}
