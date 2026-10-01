package heizige.kk.khatkit.app.core.ui.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * 返回一个随帧推进的秒数计时器，供流光着色器的 `uAnimTime` 使用。
 * 移植自词幕 `app/compose/effect/FrameTimeSeconds.kt`。
 *
 * [playing] 为 false 时冻结在当前值（保留 startOffset，重新开启不会跳变）。
 */
@Composable
fun rememberFrameTimeSeconds(playing: Boolean = true): () -> Float {
    var time by remember { mutableFloatStateOf(0f) }
    var startOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing) {
        if (!playing) {
            startOffset = time
            return@LaunchedEffect
        }

        val start = withFrameNanos { it }

        while (playing) {
            val now = withFrameNanos { it }
            time = startOffset + (now - start) / 1_000_000_000f
        }
    }

    return { time }
}
