package heizige.kk.khatkit.uikit.mochi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import ui.feice.mochi.anim.MochiSprings
import ui.feice.mochi.effect.content.mochiBlur

/**
 * 流式文本的计数器式进场：文本变长时，**新冒出来的字符**从基线下方 [rise] 处升入（MochiCounterText
 * 的数字滚轮同款：位置、透明度、模糊都跟着运动量走——还在飞的时候糊，落定才清晰）。
 *
 * 只有最后一行逐字画（前缀一次画完）：逐字排版会丢字距对，逐字画行是唯一能保留
 * 换行、行高、两端对齐的折中。文本没变长时是零开销的静态绘制，不建模糊图层、不跑动画。
 *
 * @param animate false 时整段一次画完（减弱动效 / FX 总开关关掉）。
 */
@Composable
fun KkGrowRiseText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    rise: Dp = 10.dp,
    driftX: Dp = 0.dp,
    blur: Dp = 3.dp,
    animate: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val ink = if (color == Color.Unspecified) LocalContentColor.current else color
    val density = LocalDensity.current
    val risePx = with(density) { rise.toPx() }
    val driftXPx = with(density) { driftX.toPx() }
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val info = remember(text, style, maxWidth) { measureRise(measurer, text, style, maxWidth) }
        // 只比较前后两次文本的插入区，删除、替换和光标移动都不会让整段文字重播。
        var previousText by remember { mutableStateOf(text) }
        val progress = remember { mutableStateMapOf<Int, Animatable<Float, AnimationVector1D>>() }
        LaunchedEffect(text, animate) {
            val addedRange = insertedRange(previousText, text)
            previousText = text
            progress.clear()
            if (!animate || addedRange == null) {
                return@LaunchedEffect
            }
            for (i in addedRange) {
                val anim = Animatable(0f)
                progress[i] = anim
                launch { anim.animateTo(1f, MochiSprings.snappy(260)) }
            }
        }
        val w = with(density) { info.width.toDp() }
        val h = with(density) { info.height.toDp() }
        Box(
            Modifier
                .size(w, h)
                // 最后一行之前的行一次画完，不逐字、不建图层
                .drawBehind { drawText(info.prefix, color = ink, topLeft = Offset.Zero) }
        ) {
            info.glyphs.forEachIndexed { index, glyph ->
                val tracked = progress[glyph.start]
                Text(
                    text = glyph.text,
                    style = style,
                    color = ink,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .offset { IntOffset(glyph.topLeft.x.roundToInt(), glyph.topLeft.y.roundToInt()) }
                        .graphicsLayer {
                            val p = tracked?.value ?: 1f
                            val hidden = 1f - p.coerceIn(0f, 1f)
                            // 正值表示从右下方进入，落定时向左上方收束。
                            translationX = hidden * driftXPx
                            translationY = hidden * risePx
                            alpha = p.coerceIn(0f, 1f)
                        }
                        // 飞得越远越糊，落定半径归 0（0 半径不建离屏层）
                        .mochiBlur { blur * (1f - (tracked?.value ?: 1f).coerceIn(0f, 1f)) },
                )
            }
        }
    }
}

private class RiseGlyph(val text: String, val topLeft: Offset, val start: Int)

private class RiseLayout(
    val width: Int,
    val height: Int,
    val lineStart: Int,
    val prefix: TextLayoutResult,
    val glyphs: List<RiseGlyph>,
)

private fun measureRise(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidth: Int,
): RiseLayout {
    val constraints = if (maxWidth == Constraints.Infinity) Constraints() else Constraints(maxWidth = maxWidth)
    val full = measurer.measure(
        text = AnnotatedString(text),
        style = style,
        overflow = TextOverflow.Visible,
        constraints = constraints,
    )
    val lastLine = full.lineCount - 1
    val lineStart = if (lastLine >= 0) full.getLineStart(lastLine) else text.length
    // 前缀 = 最后一行之前的内容，单独量一次（结束在行首，折行与整段一致）
    val prefix = measurer.measure(
        text = AnnotatedString(text.substring(0, lineStart)),
        style = style,
        overflow = TextOverflow.Visible,
        constraints = constraints,
    )
    val glyphs = ArrayList<RiseGlyph>(text.length - lineStart)
    var i = lineStart
    while (i < text.length) {
        val count = Character.charCount(text.codePointAt(i))
        val end = (i + count).coerceAtMost(text.length)
        val box = full.getBoundingBox(i)
        glyphs += RiseGlyph(text.substring(i, end), box.topLeft, i)
        i = end
    }
    return RiseLayout(full.size.width, full.size.height, lineStart, prefix, glyphs)
}

/**
 * Returns the UTF-16 range that was inserted between two text snapshots.
 * A null result means that the new value only removed or replaced text, so
 * existing glyphs should remain still.
 */
private fun insertedRange(previous: String, current: String): IntRange? {
    if (current.length <= previous.length) return null

    var prefix = 0
    while (
        prefix < previous.length &&
        prefix < current.length &&
        previous[prefix] == current[prefix]
    ) {
        prefix++
    }

    var previousEnd = previous.length - 1
    var currentEnd = current.length - 1
    while (
        previousEnd >= prefix &&
        currentEnd >= prefix &&
        previous[previousEnd] == current[currentEnd]
    ) {
        previousEnd--
        currentEnd--
    }

    return if (currentEnd >= prefix) prefix..currentEnd else null
}
